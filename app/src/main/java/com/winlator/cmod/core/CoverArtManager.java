package com.winlator.cmod.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.steamgrid.SteamGridDBApi;
import com.winlator.cmod.core.steamgrid.SteamGridGridsResponse;
import com.winlator.cmod.core.steamgrid.SteamGridHeroesResponse;
import com.winlator.cmod.core.steamgrid.SteamGridSearchResponse;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class CoverArtManager {
    private static final String BASE_URL = "https://www.steamgriddb.com/api/v2/";
    private static final String DEFAULT_API_KEY = "0324c52513634547a7b32d6d323635d0";
    private static Retrofit retrofit;
    private static final java.util.concurrent.ExecutorService executorService = Executors.newSingleThreadExecutor();

    public enum ErrorReason {
        NETWORK_UNAVAILABLE,
        NOT_FOUND,
        UNAUTHORIZED,
        RATE_LIMITED,
        UNKNOWN
    }

    public interface DownloadCallback {
        void onCompleted(Bitmap bitmap);
        void onFailed(ErrorReason reason);
    }

    public interface GridOptionsCallback {
        void onOptionsAvailable(java.util.List<SteamGridGridsResponse.GridData> options);
        void onFailed(ErrorReason reason);
    }

    public interface HeroOptionsCallback {
        void onOptionsAvailable(java.util.List<SteamGridHeroesResponse.HeroData> options);
        void onFailed(ErrorReason reason);
    }

    public static synchronized Retrofit getRetrofit() {
        if (retrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    private static final String PCGW_BASE_URL = "https://www.pcgamingwiki.com/w/";
    private static Retrofit pcgwRetrofit;

    public static synchronized Retrofit getPCGWRetrofit() {
        if (pcgwRetrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                            .header("User-Agent", "Winlator/7.0.0 (https://github.com/winlator; contact@winlator.com)")
                            .build()))
                    .build();

            pcgwRetrofit = new Retrofit.Builder()
                    .baseUrl(PCGW_BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return pcgwRetrofit;
    }

    private static final String STEAM_STORE_URL = "https://store.steampowered.com/";
    private static Retrofit steamRetrofit;

    public static synchronized Retrofit getSteamRetrofit() {
        if (steamRetrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            steamRetrofit = new Retrofit.Builder()
                    .baseUrl(STEAM_STORE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return steamRetrofit;
    }

    public static void fetchCoverArtOptions(Context context, Shortcut shortcut, String searchQuery, GridOptionsCallback callback) {
        if (context == null || shortcut == null) return;

        if (!AppUtils.isNetworkAvailable(context)) {
            if (callback != null) callback.onFailed(ErrorReason.NETWORK_UNAVAILABLE);
            return;
        }

        SteamGridDBApi api = getRetrofit().create(SteamGridDBApi.class);
        String searchName = (searchQuery != null && !searchQuery.isEmpty()) ? searchQuery :
                (shortcut.name != null ? shortcut.name : "");
        searchName = searchName.replaceAll("\\(.*?\\)", "")
                .replaceAll("\\[.*?\\]", "").trim();

        if (searchName.isEmpty()) {
            if (callback != null) callback.onFailed(ErrorReason.NOT_FOUND);
            return;
        }

        searchGame(api, "Bearer " + getApiKey(context), buildSearchQueries(searchName), 0, callback);
    }

    private static String getApiKey(Context context) {
        String apiKey = DEFAULT_API_KEY;
        try {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("enable_custom_api_key", false)) {
                String customKey = PreferenceManager.getDefaultSharedPreferences(context).getString("custom_api_key", "");
                if (!customKey.isEmpty()) apiKey = customKey;
            }
        } catch (Exception e) {}
        return apiKey;
    }

    private static List<String> buildSearchQueries(String searchName) {
        List<String> queries = new ArrayList<>();
        addSearchQuery(queries, searchName);

        String withoutLauncher = searchName.replaceAll(
                "(?i)(?:launcher|bootstrapper|setup|installer|\\.exe)$", "").trim();
        addSearchQuery(queries, withoutLauncher);
        addSearchQuery(queries, withoutLauncher.replaceAll("(?<=[a-z])(?=[A-Z])", " "));

        String normalized = withoutLauncher.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(java.util.Locale.ROOT);
        if (normalized.matches("gta(v|5)")) addSearchQuery(queries, "Grand Theft Auto V");
        else if (normalized.matches("gta(iii|3)")) addSearchQuery(queries, "Grand Theft Auto III");
        else if (normalized.matches("gta(iv|4)")) addSearchQuery(queries, "Grand Theft Auto IV");
        else if (normalized.matches("gta(vi|6)")) addSearchQuery(queries, "Grand Theft Auto VI");
        return queries;
    }

    private static void addSearchQuery(List<String> queries, String query) {
        String trimmed = query.trim();
        if (!trimmed.isEmpty() && !queries.contains(trimmed)) queries.add(trimmed);
    }

    private static void searchGame(SteamGridDBApi api, String auth, List<String> queries,
                                   int queryIndex, GridOptionsCallback callback) {
        if (queryIndex >= queries.size()) {
            if (callback != null) callback.onFailed(ErrorReason.NOT_FOUND);
            return;
        }
        api.searchGame(auth, queries.get(queryIndex)).enqueue(new Callback<SteamGridSearchResponse>() {
            @Override
            public void onResponse(Call<SteamGridSearchResponse> call, Response<SteamGridSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                    fetchGrids(api, auth, response.body().data.get(0).id, callback);
                } else if (response.code() == 401) {
                    if (callback != null) callback.onFailed(ErrorReason.UNAUTHORIZED);
                } else if (response.code() == 429) {
                    if (callback != null) callback.onFailed(ErrorReason.RATE_LIMITED);
                } else {
                    searchGame(api, auth, queries, queryIndex + 1, callback);
                }
            }

            @Override
            public void onFailure(Call<SteamGridSearchResponse> call, Throwable t) {
                Log.e("CoverArtManager", "SteamGridDB search failed for " + queries.get(queryIndex), t);
                if (callback != null) callback.onFailed(ErrorReason.NETWORK_UNAVAILABLE);
            }
        });
    }

    private static void fetchGrids(SteamGridDBApi api, String auth, int gameId,
                                   GridOptionsCallback callback) {
        api.getGridsByGameId(auth, gameId, "no_logo,alternate,material,blurred",
                "600x900,342x482", "static").enqueue(new Callback<SteamGridGridsResponse>() {
            @Override
            public void onResponse(Call<SteamGridGridsResponse> call, Response<SteamGridGridsResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().data != null && !response.body().data.isEmpty()) {
                    if (callback != null) callback.onOptionsAvailable(response.body().data);
                } else if (response.code() == 429) {
                    if (callback != null) callback.onFailed(ErrorReason.RATE_LIMITED);
                } else {
                    api.getGridsByGameId(auth, gameId, null, null, "static").enqueue(new Callback<SteamGridGridsResponse>() {
                        @Override
                        public void onResponse(Call<SteamGridGridsResponse> call, Response<SteamGridGridsResponse> response) {
                            if (response.isSuccessful() && response.body() != null
                                    && response.body().data != null && !response.body().data.isEmpty()) {
                                if (callback != null) callback.onOptionsAvailable(response.body().data);
                            } else if (callback != null) {
                                if (response.code() == 429) callback.onFailed(ErrorReason.RATE_LIMITED);
                                else callback.onFailed(ErrorReason.NOT_FOUND);
                            }
                        }

                        @Override
                        public void onFailure(Call<SteamGridGridsResponse> call, Throwable t) {
                            Log.e("CoverArtManager", "SteamGridDB artwork lookup failed", t);
                            if (callback != null) callback.onFailed(ErrorReason.UNKNOWN);
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<SteamGridGridsResponse> call, Throwable t) {
                Log.e("CoverArtManager", "SteamGridDB artwork lookup failed", t);
                if (callback != null) callback.onFailed(ErrorReason.UNKNOWN);
            }
        });
    }

    public static void fetchDefaultCoverArt(Context context, Shortcut shortcut, DownloadCallback callback) {
        if (context == null || shortcut == null) return;
        if (!shortcut.getCustomCoverArtPath().isEmpty()
                || !shortcut.getExtra("coverArtSelectionPending").isEmpty()
                || shortcut.hasPrimaryCoverArt()) {
            if (callback != null) callback.onFailed(ErrorReason.UNKNOWN);
            return;
        }
        long coverArtVersion = shortcut.getCoverArtVersion();
        if (!AppUtils.isNetworkAvailable(context)) {
            if (callback != null) callback.onFailed(ErrorReason.NETWORK_UNAVAILABLE);
            return;
        }

        SteamGridDBApi api = getRetrofit().create(SteamGridDBApi.class);
        String auth = "Bearer " + getApiKey(context);
        searchDefaultCoverArt(api, auth, shortcut, buildSearchQueries(shortcut.name), 0,
                coverArtVersion, callback);
    }

    private static void searchDefaultCoverArt(SteamGridDBApi api, String auth, Shortcut shortcut,
                                              List<String> queries, int queryIndex, long coverArtVersion,
                                              DownloadCallback callback) {
        if (!isDefaultCoverRequestCurrent(shortcut, coverArtVersion, callback)) return;
        if (queryIndex >= queries.size()) {
            Log.w("CoverArtManager", "No SteamGridDB no-logo cover found for " + shortcut.name);
            if (callback != null) callback.onFailed(ErrorReason.NOT_FOUND);
            return;
        }
        api.searchGame(auth, queries.get(queryIndex)).enqueue(new Callback<SteamGridSearchResponse>() {
            @Override
            public void onResponse(Call<SteamGridSearchResponse> call, Response<SteamGridSearchResponse> response) {
                if (response.code() == 401) {
                    if (callback != null) callback.onFailed(ErrorReason.UNAUTHORIZED);
                    return;
                }
                if (response.code() == 429) {
                    if (callback != null) callback.onFailed(ErrorReason.RATE_LIMITED);
                    return;
                }
                if (!response.isSuccessful() || response.body() == null
                        || response.body().data == null || response.body().data.isEmpty()) {
                    searchDefaultCoverArt(api, auth, shortcut, queries, queryIndex + 1,
                            coverArtVersion, callback);
                    return;
                }
                fetchDefaultGrid(api, auth, shortcut, response.body().data.get(0).id,
                        queries, queryIndex, coverArtVersion, callback);
            }

            @Override
            public void onFailure(Call<SteamGridSearchResponse> call, Throwable t) {
                Log.e("CoverArtManager", "SteamGridDB search failed for " + queries.get(queryIndex), t);
                if (callback != null) callback.onFailed(ErrorReason.NETWORK_UNAVAILABLE);
            }
        });
    }

    private static void fetchDefaultGrid(SteamGridDBApi api, String auth, Shortcut shortcut,
                                         int gameId, List<String> queries, int queryIndex,
                                         long coverArtVersion,
                                         DownloadCallback callback) {
        if (!isDefaultCoverRequestCurrent(shortcut, coverArtVersion, callback)) return;
        api.getGridsByGameId(auth, gameId, "no_logo", "600x900,342x482", "static")
                .enqueue(new Callback<SteamGridGridsResponse>() {
                    @Override
                    public void onResponse(Call<SteamGridGridsResponse> call,
                                           Response<SteamGridGridsResponse> response) {
                        if (response.code() == 401) {
                            if (callback != null) callback.onFailed(ErrorReason.UNAUTHORIZED);
                            return;
                        }
                        if (response.code() == 429) {
                            if (callback != null) callback.onFailed(ErrorReason.RATE_LIMITED);
                            return;
                        }
                        SteamGridGridsResponse.GridData cover = findNoLogoGrid(response.body());
                        if (cover != null) {
                            if (isDefaultCoverRequestCurrent(shortcut, coverArtVersion, callback)) {
                                downloadDefaultCoverArt(cover.url, shortcut, coverArtVersion, callback);
                            }
                            return;
                        }
                        api.getGridsByGameId(auth, gameId, "no_logo", null, "static")
                                .enqueue(new Callback<SteamGridGridsResponse>() {
                                    @Override
                                    public void onResponse(Call<SteamGridGridsResponse> call,
                                                           Response<SteamGridGridsResponse> response) {
                                        SteamGridGridsResponse.GridData cover = findNoLogoGrid(response.body());
                                        if (cover != null) {
                                            if (isDefaultCoverRequestCurrent(shortcut,
                                                    coverArtVersion, callback)) {
                                                downloadDefaultCoverArt(cover.url, shortcut,
                                                        coverArtVersion, callback);
                                            }
                                        } else {
                                            searchDefaultCoverArt(api, auth, shortcut, queries,
                                                    queryIndex + 1, coverArtVersion, callback);
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<SteamGridGridsResponse> call, Throwable t) {
                                        Log.e("CoverArtManager", "SteamGridDB no-logo cover lookup failed", t);
                                        if (callback != null) callback.onFailed(ErrorReason.UNKNOWN);
                                    }
                                });
                    }

                    @Override
                    public void onFailure(Call<SteamGridGridsResponse> call, Throwable t) {
                        Log.e("CoverArtManager", "SteamGridDB no-logo cover lookup failed", t);
                        if (callback != null) callback.onFailed(ErrorReason.UNKNOWN);
                    }
                });
    }

    private static boolean isDefaultCoverRequestCurrent(Shortcut shortcut, long coverArtVersion,
                                                        DownloadCallback callback) {
        if (shortcut.getCoverArtVersion() == coverArtVersion
                && shortcut.getCustomCoverArtPath().isEmpty()
                && shortcut.getExtra("coverArtSelectionPending").isEmpty()
                && !shortcut.hasPrimaryCoverArt()) return true;
        if (callback != null) callback.onFailed(ErrorReason.UNKNOWN);
        return false;
    }

    private static SteamGridGridsResponse.GridData findNoLogoGrid(SteamGridGridsResponse response) {
        if (response == null || response.data == null) return null;
        for (SteamGridGridsResponse.GridData option : response.data) {
            if (option.url != null && !option.url.isEmpty()
                    && "no_logo".equalsIgnoreCase(option.style)) return option;
        }
        return null;
    }

    public static void downloadSelectedCoverArt(String url, Shortcut shortcut, DownloadCallback callback) {
        long coverArtVersion = shortcut.beginCoverArtSelection();
        downloadSelectedCoverArt(url, shortcut, coverArtVersion, new DownloadCallback() {
            @Override
            public void onCompleted(Bitmap bitmap) {
                if (callback != null) callback.onCompleted(bitmap);
            }

            @Override
            public void onFailed(ErrorReason reason) {
                shortcut.cancelCoverArtSelection(coverArtVersion);
                if (callback != null) callback.onFailed(reason);
            }
        });
    }

    public static void downloadSelectedCoverArt(String url, Shortcut shortcut, long coverArtVersion,
                                                DownloadCallback callback) {
        downloadInto(url, bitmap -> shortcut.saveCustomCoverArt(bitmap, coverArtVersion), callback);
    }

    private static void downloadDefaultCoverArt(String url, Shortcut shortcut, long coverArtVersion,
                                                DownloadCallback callback) {
        downloadInto(url, bitmap -> shortcut.saveDefaultCoverArtIfNone(bitmap, coverArtVersion), callback);
    }

    public static void fetchHeroArtOptions(Context context, Shortcut shortcut, String searchQuery, HeroOptionsCallback callback) {
        if (context == null || shortcut == null) return;

        if (!AppUtils.isNetworkAvailable(context)) {
            if (callback != null) callback.onFailed(ErrorReason.NETWORK_UNAVAILABLE);
            return;
        }

        String tempApiKey = DEFAULT_API_KEY;
        try {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("enable_custom_api_key", false)) {
                String customKey = PreferenceManager.getDefaultSharedPreferences(context).getString("custom_api_key", "");
                if (!customKey.isEmpty()) tempApiKey = customKey;
            }
        } catch (Exception e) {}

        final String apiKey = tempApiKey;
        SteamGridDBApi api = getRetrofit().create(SteamGridDBApi.class);

        String searchName = (searchQuery != null && !searchQuery.isEmpty()) ? searchQuery :
                           (shortcut.name != null ? shortcut.name.replaceAll("\\(.*?\\)", "").replaceAll("\\[.*?\\]", "").trim() : "");

        if (searchName.isEmpty()) {
            if (callback != null) callback.onFailed(ErrorReason.NOT_FOUND);
            return;
        }

        api.searchGame("Bearer " + apiKey, searchName).enqueue(new Callback<SteamGridSearchResponse>() {
            @Override
            public void onResponse(Call<SteamGridSearchResponse> call, Response<SteamGridSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                    int gameId = response.body().data.get(0).id;
                    // 1920x620 is SteamGridDB's standard wide hero size (also offered at 2x/other wide ratios); our
                    // fullscreen loading screen center-crops whatever comes back, so the exact ratio is not critical.
                    api.getHeroesByGameId("Bearer " + apiKey, gameId, "alternate,blurred,material", "1920x620,3840x1240,1600x650", "static").enqueue(new Callback<SteamGridHeroesResponse>() {
                        @Override
                        public void onResponse(Call<SteamGridHeroesResponse> call, Response<SteamGridHeroesResponse> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                                if (callback != null) callback.onOptionsAvailable(response.body().data);
                            } else {
                                if (response.code() == 429) {
                                    if (callback != null) callback.onFailed(ErrorReason.RATE_LIMITED);
                                    return;
                                }
                                api.getHeroesByGameId("Bearer " + apiKey, gameId, null, null, "static").enqueue(new Callback<SteamGridHeroesResponse>() {
                                    @Override
                                    public void onResponse(Call<SteamGridHeroesResponse> call, Response<SteamGridHeroesResponse> response) {
                                        if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                                            if (callback != null) callback.onOptionsAvailable(response.body().data);
                                        } else if (callback != null) {
                                            if (response.code() == 429) callback.onFailed(ErrorReason.RATE_LIMITED);
                                            else callback.onFailed(ErrorReason.NOT_FOUND);
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<SteamGridHeroesResponse> call, Throwable t) {
                                        if (callback != null) callback.onFailed(ErrorReason.UNKNOWN);
                                    }
                                });
                            }
                        }

                        @Override
                        public void onFailure(Call<SteamGridHeroesResponse> call, Throwable t) {
                            if (callback != null) callback.onFailed(ErrorReason.UNKNOWN);
                        }
                    });
                } else if (callback != null) {
                    if (response.code() == 401) callback.onFailed(ErrorReason.UNAUTHORIZED);
                    else if (response.code() == 429) callback.onFailed(ErrorReason.RATE_LIMITED);
                    else callback.onFailed(ErrorReason.NOT_FOUND);
                }
            }

            @Override
            public void onFailure(Call<SteamGridSearchResponse> call, Throwable t) {
                if (callback != null) callback.onFailed(ErrorReason.NETWORK_UNAVAILABLE);
            }
        });
    }

    public static void downloadSelectedHeroArt(String url, Shortcut shortcut, DownloadCallback callback) {
        downloadInto(url, bitmap -> {
            shortcut.saveCustomHeroArt(bitmap);
            return true;
        }, callback);
    }

    private interface BitmapSink {
        boolean accept(Bitmap bitmap);
    }

    private static void downloadInto(String url, BitmapSink sink, DownloadCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (InputStream input = connection.getInputStream()) {
                        Bitmap bitmap = BitmapFactory.decodeStream(input);
                        if (bitmap != null) {
                            if (sink.accept(bitmap)) {
                                if (callback != null) callback.onCompleted(bitmap);
                            } else if (callback != null) {
                                callback.onFailed(ErrorReason.UNKNOWN);
                            }
                        } else if (callback != null) callback.onFailed(ErrorReason.UNKNOWN);
                    }
                } else if (callback != null) {
                    callback.onFailed(responseCode == 429 ? ErrorReason.RATE_LIMITED : ErrorReason.UNKNOWN);
                }
            } catch (Exception e) {
                Log.e("CoverArtManager", "Failed to download art", e);
                if (callback != null) {
                    callback.onFailed(e instanceof java.io.IOException ? ErrorReason.NETWORK_UNAVAILABLE : ErrorReason.UNKNOWN);
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
