package com.winlator.cmod.store;

import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class SteamNewsService {
    interface Listener {
        void onNewsLoaded(List<SteamNewsItem> items);
        void onNewsFailed(String message);
    }

    private static final String TAG = "SteamNewsService";
    private static final String FEED_URL = "https://store.steampowered.com/feeds/news.xml";
    private static final Pattern IMAGE_URL = Pattern.compile(
            "<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile Call activeCall;

    void load(Listener listener) {
        cancel();
        Request request = new Request.Builder()
                .url(FEED_URL)
                .header("User-Agent", "WinlatorMali Steam News")
                .build();
        Call call = CLIENT.newCall(request);
        activeCall = call;
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call ignored, IOException error) {
                if (activeCall != call || call.isCanceled()) return;
                postFailure(call, listener, error.getMessage() == null
                        ? "Could not connect to Steam News." : error.getMessage());
            }

            @Override
            public void onResponse(Call ignored, Response response) {
                String xml;
                try (Response closeableResponse = response) {
                    if (!closeableResponse.isSuccessful() || closeableResponse.body() == null) {
                        postFailure(call, listener, "Steam News returned HTTP "
                                + closeableResponse.code() + ".");
                        return;
                    }
                    xml = closeableResponse.body().string();
                } catch (IOException error) {
                    postFailure(call, listener, error.getMessage() == null
                            ? "Could not read the Steam News feed." : error.getMessage());
                    return;
                }

                try {
                    List<SteamNewsItem> items = parse(xml);
                    mainHandler.post(() -> {
                        if (activeCall != call || call.isCanceled()) return;
                        activeCall = null;
                        if (items.isEmpty()) listener.onNewsFailed("Steam News returned no articles.");
                        else listener.onNewsLoaded(items);
                    });
                } catch (Exception error) {
                    Log.e(TAG, "Unable to parse the Steam News RSS feed", error);
                    postFailure(call, listener, "Steam News returned invalid article data.");
                }
            }
        });
    }

    void cancel() {
        Call call = activeCall;
        activeCall = null;
        if (call != null) call.cancel();
    }

    private void postFailure(Call call, Listener listener, String message) {
        mainHandler.post(() -> {
            if (activeCall != call || call.isCanceled()) return;
            activeCall = null;
            listener.onNewsFailed(message);
        });
    }

    private static List<SteamNewsItem> parse(String xml) throws Exception {
        List<SteamNewsItem> articles = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader(xml));
        SteamNewsItemBuilder item = null;
        String currentTag = null;
        StringBuilder text = new StringBuilder();
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                currentTag = parser.getName();
                if ("item".equalsIgnoreCase(currentTag)) item = new SteamNewsItemBuilder();
                text.setLength(0);
            } else if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) {
                if (item != null && currentTag != null) text.append(parser.getText());
            } else if (event == XmlPullParser.END_TAG) {
                String tag = parser.getName();
                if (item != null && currentTag != null && tag.equalsIgnoreCase(currentTag)) {
                    item.set(tag, text.toString());
                    text.setLength(0);
                }
                if ("item".equalsIgnoreCase(tag) && item != null) {
                    SteamNewsItem parsed = item.build();
                    if (parsed != null && seenUrls.add(normalizeUrl(parsed.url))) {
                        articles.add(parsed);
                    }
                    item = null;
                }
                currentTag = null;
            }
            event = parser.next();
        }
        Map<String, List<SteamNewsItem>> groups = new LinkedHashMap<>();
        for (SteamNewsItem article : articles) {
            groups.computeIfAbsent(normalizeTitle(article.title), key -> new ArrayList<>())
                    .add(article);
        }
        List<SteamNewsItem> items = new ArrayList<>(groups.size());
        for (List<SteamNewsItem> group : groups.values()) {
            items.add(SteamNewsItem.group(group));
        }
        return items;
    }

    private static String normalizeUrl(String url) {
        return url.trim().replaceAll("#.*$", "").replaceAll("/+$", "");
    }

    private static String normalizeTitle(String title) {
        return Html.fromHtml(title, Html.FROM_HTML_MODE_COMPACT).toString()
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static final class SteamNewsItemBuilder {
        private String title = "";
        private String link = "";
        private String published = "";
        private String description = "";
        private String encodedContent = "";

        void set(String tag, String value) {
            String content = value == null ? "" : value.trim();
            switch (tag.toLowerCase(java.util.Locale.ROOT)) {
                case "title": title = content; break;
                case "link": link = content; break;
                case "pubdate": published = content; break;
                case "description": description = content; break;
                case "encoded":
                case "content:encoded": encodedContent = content; break;
                default: break;
            }
        }

        SteamNewsItem build() {
            String html = encodedContent.isEmpty() ? description : encodedContent;
            String imageUrl = "";
            Matcher image = IMAGE_URL.matcher(html);
            if (image.find()) imageUrl = image.group(1);
            String content = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim();
            String summary = content.replaceAll("\\s+", " ").trim();
            if (title.isEmpty() || link.isEmpty()) return null;
            if (summary.length() > 320) summary = summary.substring(0, 317).trim() + "…";
            return new SteamNewsItem(title, link, published, summary, content, imageUrl);
        }
    }
}
