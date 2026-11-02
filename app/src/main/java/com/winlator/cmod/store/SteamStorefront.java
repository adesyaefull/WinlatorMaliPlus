package com.winlator.cmod.store;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class SteamStorefront {
    interface Listener {
        void onSectionLoaded(int requestId, String section, List<StoreItem> items);
        void onSearchLoaded(int requestId, String query, List<StoreItem> items);
        void onSearchItemUpdated(int requestId, String query, StoreItem item);
        void onStoreError(int requestId, String section, String message);
    }

    static final class StoreItem {
        final int appId;
        String name;
        String imageUrl;
        String price;
        String originalPrice;
        int discountPercent;
        boolean free;
        boolean owned;

        StoreItem(int appId, String name, String imageUrl, String price,
                  String originalPrice, int discountPercent, boolean free) {
            this.appId = appId;
            this.name = name;
            this.imageUrl = imageUrl;
            this.price = price;
            this.originalPrice = originalPrice;
            this.discountPercent = discountPercent;
            this.free = free;
        }
    }

    private static final String TAG = "SteamStorefront";
    private static final String BASE_URL = "https://store.steampowered.com/";
    private static final String COUNTRY_CODE = countryCode();
    private static final Pattern APP_ID_IN_URL = Pattern.compile("/apps/(\\d+)/");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;

    SteamStorefront(Listener listener) {
        this.listener = listener;
    }

    void loadLandingPage(int requestId) {
        getJson(BASE_URL + "api/featuredcategories/?cc=" + COUNTRY_CODE + "&l=en", "store",
                requestId,
                response -> {
                    parseFeaturedSection(requestId, response, "new_releases", "New releases");
                    parseFeaturedSection(requestId, response, "specials", "Hot promotions");
                });
        getJson(BASE_URL + "search/results/?query=&start=0&count=30&maxprice=free"
                + "&category1=998&json=1&cc=" + COUNTRY_CODE + "&l=en", "free", requestId,
                response -> {
                    JSONArray results = response.optJSONArray("items");
                    List<StoreItem> items = new ArrayList<>();
                    if (results != null) {
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject result = results.optJSONObject(i);
                            if (result == null) continue;
                            String image = result.optString("logo", "");
                            int appId = parseAppId(image);
                            String name = result.optString("name", "").trim();
                            if (appId > 0 && !name.isEmpty()) {
                                items.add(new StoreItem(appId, name, image, "Free to play",
                                        "", 0, true));
                            }
                        }
                    }
                    post(() -> listener.onSectionLoaded(requestId, "Free games", items));
                });
    }

    void search(String query, int requestId) {
        String url = BASE_URL + "api/storesearch/?term=" + Uri.encode(query)
                + "&cc=" + COUNTRY_CODE + "&l=en";
        getJson(url, "search", requestId, response -> {
            JSONArray results = response.optJSONArray("items");
            List<StoreItem> items = new ArrayList<>();
            if (results != null) {
                for (int i = 0; i < results.length() && items.size() < 12; i++) {
                    JSONObject result = results.optJSONObject(i);
                    if (result == null || !"app".equals(result.optString("type", "app"))) continue;
                    int appId = result.optInt("id", 0);
                    String name = result.optString("name", "").trim();
                    if (appId <= 0 || name.isEmpty()) continue;
                    String image = result.optString("tiny_image", "");
                    StoreItem item = new StoreItem(appId, name, image,
                            "Loading price…", "", 0, false);
                    items.add(item);
                }
            }
            post(() -> listener.onSearchLoaded(requestId, query, items));
            for (StoreItem item : items) loadAppDetails(requestId, query, item);
        });
    }

    private void loadAppDetails(int requestId, String query, StoreItem item) {
        getJson(BASE_URL + "api/appdetails/?appids=" + item.appId
                        + "&cc=" + COUNTRY_CODE + "&l=en", "search", requestId,
                response -> {
                    JSONObject app = response.optJSONObject(String.valueOf(item.appId));
                    JSONObject data = app == null ? null : app.optJSONObject("data");
                    if (data == null) {
                        item.price = "Price unavailable";
                        post(() -> listener.onSearchItemUpdated(requestId, query, item));
                        return;
                    }
                    item.name = data.optString("name", item.name);
                    String header = data.optString("header_image", "");
                    if (!header.isEmpty()) item.imageUrl = header;
                    item.free = data.optBoolean("is_free", false);
                    JSONObject priceOverview = data.optJSONObject("price_overview");
                    if (item.free) {
                        item.price = "Free to play";
                        item.originalPrice = "";
                        item.discountPercent = 0;
                    } else if (priceOverview != null) {
                        item.price = priceOverview.optString("final_formatted", "Price unavailable");
                        item.originalPrice = priceOverview.optString("initial_formatted", "");
                        item.discountPercent = priceOverview.optInt("discount_percent", 0);
                    } else {
                        item.price = "Price unavailable";
                        item.originalPrice = "";
                        item.discountPercent = 0;
                    }
                    post(() -> listener.onSearchItemUpdated(requestId, query, item));
                });
    }

    private void parseFeaturedSection(int requestId, JSONObject response,
                                      String key, String title) {
        JSONObject category = response.optJSONObject(key);
        JSONArray results = category == null ? null : category.optJSONArray("items");
        List<StoreItem> items = new ArrayList<>();
        if (results != null) {
            for (int i = 0; i < results.length() && items.size() < 20; i++) {
                JSONObject result = results.optJSONObject(i);
                if (result == null) continue;
                int appId = result.optInt("id", 0);
                String name = result.optString("name", "").trim();
                if (appId <= 0 || name.isEmpty()) continue;
                String image = result.optString("header_image", "");
                if (image.isEmpty()) image = result.optString("large_capsule_image", "");
                int finalPrice = result.optInt("final_price", -1);
                boolean free = result.optBoolean("is_free", false) || finalPrice == 0;
                String price = free ? "Free to play"
                        : finalPrice < 0 ? "Price unavailable"
                        : formatPrice(finalPrice, result.optString("currency", "USD"));
                String originalPrice = result.optInt("original_price", 0) > finalPrice
                        ? formatPrice(result.optInt("original_price", 0),
                        result.optString("currency", "USD")) : "";
                items.add(new StoreItem(appId, name, image, price, originalPrice,
                        result.optInt("discount_percent", 0), free));
            }
        }
        post(() -> listener.onSectionLoaded(requestId, title, items));
    }

    private void getJson(String url, String section, int requestId, JsonConsumer consumer) {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "WinlatorMali Steam Store")
                .build();
        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                Log.w(TAG, "Steam Store request failed for " + section, error);
                reportError(requestId, section, error.getMessage() == null
                        ? "Network request failed." : error.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response closeableResponse = response) {
                    if (!closeableResponse.isSuccessful() || closeableResponse.body() == null) {
                        reportError(requestId, section, "Steam Store returned HTTP "
                                + closeableResponse.code() + ".");
                        return;
                    }
                    JSONObject json = new JSONObject(closeableResponse.body().string());
                    consumer.accept(json);
                } catch (IOException | JSONException error) {
                    Log.w(TAG, "Could not read Steam Store response for " + section, error);
                    reportError(requestId, section, error.getMessage() == null
                            ? "Steam Store returned invalid data." : error.getMessage());
                }
            }
        });
    }

    private void reportError(int requestId, String section, String message) {
        post(() -> listener.onStoreError(requestId, section, message));
    }

    private void post(Runnable runnable) {
        mainHandler.post(runnable);
    }

    private static int parseAppId(String imageUrl) {
        Matcher matcher = APP_ID_IN_URL.matcher(imageUrl == null ? "" : imageUrl);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String formatPrice(int minorUnits, String currencyCode) {
        try {
            Currency currency = Currency.getInstance(currencyCode);
            NumberFormat format = NumberFormat.getCurrencyInstance(Locale.getDefault());
            format.setCurrency(currency);
            format.setMaximumFractionDigits(Math.max(0, currency.getDefaultFractionDigits()));
            format.setMinimumFractionDigits(Math.max(0, currency.getDefaultFractionDigits()));
            return format.format(BigDecimal.valueOf(minorUnits, 2));
        } catch (IllegalArgumentException e) {
            return currencyCode + " " + BigDecimal.valueOf(minorUnits, 2).toPlainString();
        }
    }

    private static String countryCode() {
        String country = Locale.getDefault().getCountry();
        return country == null || country.trim().isEmpty()
                ? "us" : country.toLowerCase(Locale.ROOT);
    }

    private interface JsonConsumer {
        void accept(JSONObject response);
    }
}
