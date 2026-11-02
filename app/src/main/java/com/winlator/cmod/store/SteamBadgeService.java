package com.winlator.cmod.store;

import android.os.Handler;
import android.os.Looper;
import android.text.Html;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class SteamBadgeService {
    interface Listener {
        void onBadgesLoaded(List<SteamBadge> badges, String level, String totalXp);
        void onBadgesFailed(String reason);
    }

    private static final Pattern PLAYER_LEVEL = Pattern.compile(
            "class=\"friendPlayerLevelNum\"[^>]*>\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_XP = Pattern.compile(
            "class=\"profile_xp_block\"[^>]*>(.*?)</", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern XP_VALUE = Pattern.compile("(\\d[\\d,]*)\\s*XP",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BADGE_ROW = Pattern.compile(
            "<div\\s+id=\"badge_badge_[^\"]+\"[^>]*class=\"badge_row[^>]*>(.*?)(?=<div\\s+id=\"badge_badge_[^\"]+\"[^>]*class=\"badge_row|$)",
            Pattern.DOTALL);
    private static final Pattern BADGE_NAME = Pattern.compile(
            "class=\"badge_info_title\">(.*?)</div>", Pattern.DOTALL);
    private static final Pattern BADGE_ICON = Pattern.compile(
            "data-delayed-image=\"([^\"]+)\"");
    private static final Pattern BADGE_XP = Pattern.compile(
            "class=\"badge_info_description\".*?<div[^>]*>\\s*(\\d+)\\s*XP",
            Pattern.DOTALL);
    private static final Pattern BADGE_UNLOCKED = Pattern.compile(
            "class=\"badge_info_unlocked\">(.*?)</div>", Pattern.DOTALL);
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile Call activeCall;

    void load(String steamId, Listener listener) {
        if (steamId == null || !steamId.matches("\\d{17}")) {
            listener.onBadgesFailed("Steam profile ID is invalid.");
            return;
        }
        cancel();
        Request request = new Request.Builder()
                .url("https://steamcommunity.com/profiles/" + steamId + "/badges/?l=english")
                .header("User-Agent", "Winlator Steam integration")
                .build();
        Call call = CLIENT.newCall(request);
        activeCall = call;
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                if (activeCall != call || call.isCanceled()) return;
                mainHandler.post(() -> {
                    if (activeCall == call) {
                        activeCall = null;
                        listener.onBadgesFailed(error.getMessage() == null
                                ? "Steam badge request failed." : error.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                String body;
                try (Response closeableResponse = response) {
                    if (!closeableResponse.isSuccessful() || closeableResponse.body() == null) {
                        throw new IOException("Steam returned HTTP " + closeableResponse.code() + ".");
                    }
                    body = closeableResponse.body().string();
                } catch (IOException error) {
                    if (activeCall != call || call.isCanceled()) return;
                    mainHandler.post(() -> {
                        if (activeCall == call) {
                            activeCall = null;
                            listener.onBadgesFailed(error.getMessage() == null
                                    ? "Could not read Steam badge data." : error.getMessage());
                        }
                    });
                    return;
                }

                List<SteamBadge> badges = parseBadges(body);
                String level = find(PLAYER_LEVEL, body);
                String xpBlock = find(PLAYER_XP, body);
                String totalXp = find(XP_VALUE, android.text.Html
                        .fromHtml(xpBlock, android.text.Html.FROM_HTML_MODE_LEGACY).toString());
                mainHandler.post(() -> {
                    if (activeCall != call || call.isCanceled()) return;
                    activeCall = null;
                    if (badges.isEmpty() && level.isEmpty() && totalXp.isEmpty()) {
                        listener.onBadgesFailed("Steam returned no public badge data for this profile.");
                    } else {
                        listener.onBadgesLoaded(badges, level, totalXp);
                    }
                });
            }
        });
    }

    void cancel() {
        Call call = activeCall;
        activeCall = null;
        if (call != null) call.cancel();
    }

    private static List<SteamBadge> parseBadges(String html) {
        List<SteamBadge> badges = new ArrayList<>();
        Matcher rows = BADGE_ROW.matcher(html);
        while (rows.find()) {
            String row = rows.group(1);
            String name = decodeText(BADGE_NAME, row);
            String icon = find(BADGE_ICON, row);
            if (name.isEmpty() || icon.isEmpty()) continue;
            String xp = find(BADGE_XP, row);
            String unlocked = decodeText(BADGE_UNLOCKED, row);
            badges.add(new SteamBadge(name, icon, xp, unlocked));
        }
        return badges;
    }

    private static String find(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String decodeText(Pattern pattern, String input) {
        String text = find(pattern, input);
        return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }
}
