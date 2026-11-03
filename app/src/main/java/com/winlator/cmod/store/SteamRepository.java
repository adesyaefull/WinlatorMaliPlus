package com.winlator.cmod.store;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.networking.steam3.ProtocolTypes;
import in.dragonbra.javasteam.steam.handlers.steamapps.License;
import in.dragonbra.javasteam.steam.handlers.steamapps.PICSProductInfo;
import in.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest;
import in.dragonbra.javasteam.steam.handlers.steamapps.SteamApps;
import in.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback;
import in.dragonbra.javasteam.steam.handlers.steamapps.callback.FreeLicenseCallback;
import in.dragonbra.javasteam.steam.handlers.steamapps.callback.PICSProductInfoCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.SteamUser;
import in.dragonbra.javasteam.steam.handlers.steamfriends.SteamFriends;
import in.dragonbra.javasteam.enums.EChatEntryType;
import in.dragonbra.javasteam.steam.handlers.steamfriends.callback.FriendsListCallback;
import in.dragonbra.javasteam.steam.handlers.steamfriends.callback.PersonaStateCallback;
import in.dragonbra.javasteam.steam.handlers.steamfriends.callback.FriendMsgCallback;
import in.dragonbra.javasteam.steam.handlers.steamfriends.callback.FriendMsgEchoCallback;
import in.dragonbra.javasteam.steam.handlers.steamuserstats.SteamUserStats;
import in.dragonbra.javasteam.steam.handlers.steamuserstats.AchievementBlocks;
import in.dragonbra.javasteam.steam.handlers.steamuserstats.callback.UserStatsCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback;
import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager;
import in.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration;
import in.dragonbra.javasteam.types.KeyValue;
import in.dragonbra.javasteam.types.SteamID;
final class SteamRepository {
    private static final String TAG = "SteamRepository";
    private static final SteamRepository INSTANCE = new SteamRepository();
    private static final long CONNECTION_TIMEOUT_MS = 40_000L;

    interface Listener {
        void onSteamEvent(String event);
    }

    private static final int PHASE_IDLE = 0;
    private static final int PHASE_PACKAGES = 1;
    private static final int PHASE_APPS = 2;

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean pumping = new AtomicBoolean();
    private final AtomicLong syncGeneration = new AtomicLong();
    private final Map<Integer, PICSProductInfo> packageInfo = new ConcurrentHashMap<>();
    private final Map<Integer, PICSProductInfo> appInfo = new ConcurrentHashMap<>();
    private final Map<Integer, List<SteamAchievement>> achievements = new ConcurrentHashMap<>();
    private final Set<Integer> achievementRequests = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Runnable> achievementTimeouts = new ConcurrentHashMap<>();
    private final List<License> licenses = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicLong clientGeneration = new AtomicLong();

    private volatile Context appContext;
    private volatile SteamClient client;
    private volatile CallbackManager callbackManager;
    private volatile SteamUser steamUser;
    private volatile SteamApps steamApps;
    private volatile SteamFriends steamFriends;
    private volatile SteamUserStats steamUserStats;
    private volatile SteamDatabase database;
    private volatile SteamSessionStore sessionStore;
    private volatile HandlerThread pumpThread;
    private volatile Handler pumpHandler;
    private volatile boolean connected;
    private volatile boolean connecting;
    private volatile boolean loggedIn;
    private volatile int reconnectAttempts;
    private volatile boolean shutdownRequested;
    private volatile Runnable reconnectTask;
    private volatile Runnable connectionTimeoutTask;
    private volatile int syncPhase = PHASE_IDLE;
    private volatile String pendingUsername;
    private volatile String pendingToken;
    private volatile Runnable syncTimeout;
    private volatile int pendingFreeLicenseAppId;

    private SteamRepository() {}

    static SteamRepository getInstance() {
        return INSTANCE;
    }

    synchronized void initialize(Context context) {
        if (appContext == null) {
            appContext = context.getApplicationContext();
            database = new SteamDatabase(appContext);
            sessionStore = new SteamSessionStore(appContext);
        }
        if (client != null) return;

        ensureSteamHashProvider();
        createSteamClient();
    }

    private synchronized void createSteamClient() {
        SteamConfiguration configuration = SteamConfiguration.create(builder -> {
            builder.withProtocolTypes(EnumSet.of(ProtocolTypes.TCP));
            builder.withConnectionTimeout(30_000L);
            builder.withDirectoryFetch(true);
        });
        SteamClient newClient = new SteamClient(configuration);
        CallbackManager newCallbackManager = new CallbackManager(newClient);
        long generation = clientGeneration.incrementAndGet();
        client = newClient;
        callbackManager = newCallbackManager;
        steamUser = newClient.getHandler(SteamUser.class);
        steamApps = newClient.getHandler(SteamApps.class);
        steamFriends = newClient.getHandler(SteamFriends.class);
        steamUserStats = newClient.getHandler(SteamUserStats.class);

        newCallbackManager.subscribe(ConnectedCallback.class,
                callback -> ifCurrentClient(generation, this::onConnected));
        newCallbackManager.subscribe(DisconnectedCallback.class,
                callback -> ifCurrentClient(generation, () -> onDisconnected(callback)));
        newCallbackManager.subscribe(LoggedOnCallback.class,
                callback -> ifCurrentClient(generation, () -> onLoggedOn(callback)));
        newCallbackManager.subscribe(LoggedOffCallback.class,
                callback -> ifCurrentClient(generation, () -> onLoggedOff(callback)));
        newCallbackManager.subscribe(LicenseListCallback.class,
                callback -> ifCurrentClient(generation, () -> onLicenseList(callback)));
        newCallbackManager.subscribe(FreeLicenseCallback.class,
                callback -> ifCurrentClient(generation, () -> onFreeLicense(callback)));
        newCallbackManager.subscribe(PICSProductInfoCallback.class,
                callback -> ifCurrentClient(generation, () -> onProductInfo(callback)));
        newCallbackManager.subscribe(FriendsListCallback.class,
                callback -> ifCurrentClient(generation, () -> emit("FriendsUpdated")));
        newCallbackManager.subscribe(PersonaStateCallback.class,
                callback -> ifCurrentClient(generation, () -> emit("FriendsUpdated")));
        newCallbackManager.subscribe(FriendMsgCallback.class,
                callback -> ifCurrentClient(generation, () -> onFriendMessage(callback)));
        newCallbackManager.subscribe(FriendMsgEchoCallback.class,
                callback -> ifCurrentClient(generation, () -> onFriendMessageEcho(callback)));
        newCallbackManager.subscribe(UserStatsCallback.class,
                callback -> ifCurrentClient(generation, () -> onUserStats(callback)));
    }

    private void ifCurrentClient(long generation, Runnable callback) {
        if (clientGeneration.get() == generation) callback.run();
    }

    private synchronized void replaceSteamClient() {
        clientGeneration.incrementAndGet();
        SteamClient oldClient = client;
        if (oldClient != null) {
            try {
                oldClient.disconnect();
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not close the previous Steam connection", e);
            }
        }
        createSteamClient();
    }

    private static void ensureSteamHashProvider() {
        if (!hasSteamSha1Provider()) {
            Security.removeProvider("BC");
            int position = Security.insertProviderAt(
                    new org.bouncycastle.jce.provider.BouncyCastleProvider(), 1);
            if (position < 0 && Security.getProvider("BC") == null) {
                throw new IllegalStateException("Could not register Bouncy Castle for Steam downloads.");
            }
        }
        if (hasSteamSha1Provider()) return;
        throw new IllegalStateException("The BC provider cannot supply SHA-1 for Steam depot downloads.");
    }

    private static boolean hasSteamSha1Provider() {
        try {
            MessageDigest.getInstance("SHA-1", "BC");
            return true;
        } catch (java.security.NoSuchAlgorithmException | java.security.NoSuchProviderException e) {
            return false;
        }
    }

    void connect() {
        if (client == null) throw new IllegalStateException("SteamRepository must be initialized first");
        startPump();
        synchronized (this) {
            shutdownRequested = false;
            if (connected || connecting) return;
            connecting = true;
        }
        cancelReconnectTask();
        scheduleConnectionTimeout();
        Handler handler = pumpHandler;
        SteamClient clientToConnect = client;
        Runnable connectTask = () -> {
            if (shutdownRequested || !connecting || clientToConnect != client) return;
            try {
                clientToConnect.connect();
            } catch (RuntimeException e) {
                Log.e(TAG, "Steam connection attempt failed", e);
                emit("ConnectionError:" + errorMessage(e));
                connecting = false;
                cancelConnectionTimeout();
                replaceSteamClient();
                scheduleReconnect();
            }
        };
        if (handler == null || !handler.post(connectTask)) {
            synchronized (this) {
                connecting = false;
            }
            cancelConnectionTimeout();
            throw new IllegalStateException("Steam callback worker is not available.");
        }
    }

    void disconnect() {
        shutdownRequested = true;
        cancelReconnectTask();
        cancelConnectionTimeout();
        connected = false;
        connecting = false;
        loggedIn = false;
        replaceSteamClient();
        stopPump();
    }

    void onNetworkLost() {
        if (shutdownRequested || client == null) return;
        connected = false;
        connecting = false;
        loggedIn = false;
        syncPhase = PHASE_IDLE;
        cancelConnectionTimeout();
        cancelLibrarySyncTimeout();
        packageInfo.clear();
        appInfo.clear();
        emit("Disconnected");
        replaceSteamClient();
        scheduleReconnect();
    }

    void onNetworkAvailable() {
        if (shutdownRequested || client == null || connected || connecting) return;
        Handler handler = pumpHandler;
        if (handler == null || !pumping.get()) return;
        cancelReconnectTask();
        reconnectAttempts = 0;
        handler.post(() -> {
            if (shutdownRequested || connected || connecting) return;
            try {
                connect();
            } catch (RuntimeException e) {
                Log.e(TAG, "Steam reconnect after network recovery failed", e);
                emit("ConnectionError:" + errorMessage(e));
            }
        });
    }

    boolean isConnected() {
        return connected;
    }

    boolean isLoggedIn() {
        return loggedIn;
    }

    SteamClient getClient() {
        return client;
    }

    SteamDatabase getDatabase() {
        if (database == null) throw new IllegalStateException("SteamRepository is not initialized");
        return database;
    }

    boolean isInitialized() {
        return database != null;
    }

    List<License> getLicenses() {
        synchronized (licenses) {
            return new ArrayList<>(licenses);
        }
    }

    void requestFreeLicense(int appId) {
        if (appId <= 0) throw new IllegalArgumentException("Invalid Steam app ID.");
        if (!loggedIn || steamApps == null) {
            throw new IllegalStateException("Sign in to Steam before adding a free game.");
        }
        Handler handler = pumpHandler;
        if (handler == null) throw new IllegalStateException("Steam connection is not active.");
        synchronized (this) {
            if (pendingFreeLicenseAppId != 0) {
                throw new IllegalStateException("Another free-game request is still in progress.");
            }
            pendingFreeLicenseAppId = appId;
        }
        boolean accepted = handler.post(() -> {
            try {
                steamApps.requestFreeLicense(appId);
            } catch (RuntimeException e) {
                synchronized (SteamRepository.this) {
                    if (pendingFreeLicenseAppId == appId) pendingFreeLicenseAppId = 0;
                }
                Log.e(TAG, "Could not request free Steam license for app " + appId, e);
                emit("FreeLicenseFailed:" + appId + ":" + errorMessage(e));
            }
        });
        if (!accepted) {
            synchronized (this) {
                if (pendingFreeLicenseAppId == appId) pendingFreeLicenseAppId = 0;
            }
            throw new IllegalStateException("Steam connection is shutting down.");
        }
    }

    void requestAchievements(int appId) {
        if (appId <= 0) throw new IllegalArgumentException("Invalid Steam app ID.");
        if (!loggedIn || steamUserStats == null || client == null) {
            throw new IllegalStateException("Sign in to Steam to load achievements.");
        }
        Handler handler = pumpHandler;
        if (handler == null) throw new IllegalStateException("Steam connection is not active.");
        if (!achievementRequests.add(appId)) return;
        Runnable timeout = () -> {
            if (achievementRequests.remove(appId)) {
                achievementTimeouts.remove(appId);
                emit("AchievementsFailed:" + appId + ":Steam did not return achievement data.");
            }
        };
        achievementTimeouts.put(appId, timeout);
        mainHandler.postDelayed(timeout, 30_000L);
        boolean accepted = handler.post(() -> {
            try {
                SteamID steamId = client.getSteamID();
                if (steamId == null) throw new IllegalStateException("Steam user ID is unavailable.");
                steamUserStats.getUserStats(appId, steamId);
            } catch (RuntimeException e) {
                achievementRequests.remove(appId);
                Runnable pendingTimeout = achievementTimeouts.remove(appId);
                if (pendingTimeout != null) mainHandler.removeCallbacks(pendingTimeout);
                Log.e(TAG, "Could not request achievements for app " + appId, e);
                emit("AchievementsFailed:" + appId + ":" + errorMessage(e));
            }
        });
        if (!accepted) {
            achievementRequests.remove(appId);
            Runnable pendingTimeout = achievementTimeouts.remove(appId);
            if (pendingTimeout != null) mainHandler.removeCallbacks(pendingTimeout);
            throw new IllegalStateException("Steam connection is shutting down.");
        }
    }

    List<SteamAchievement> getAchievements(int appId) {
        List<SteamAchievement> result = achievements.get(appId);
        return result == null ? Collections.emptyList() : new ArrayList<>(result);
    }

    void addListener(Listener listener) {
        if (listener != null) listeners.addIfAbsent(listener);
    }

    void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    String getUsername() {
        return sessionStore == null ? "" : sessionStore.getUsername();
    }

    void saveSession(String username, String refreshToken) throws GeneralSecurityException {
        if (sessionStore == null) throw new IllegalStateException("SteamRepository is not initialized");
        sessionStore.save(username, refreshToken);
    }

    void loginWithToken(String username, String refreshToken) {
        pendingUsername = username;
        pendingToken = refreshToken;
        if (!connected || steamUser == null) return;
        Handler handler = pumpHandler;
        Runnable login = () -> {
            LogOnDetails details = new LogOnDetails();
            details.setUsername(username);
            details.setAccessToken(refreshToken);
            details.setShouldRememberPassword(true);
            steamUser.logOn(details);
        };
        if (handler != null) handler.post(login);
        else new Thread(login, "SteamLogin").start();
    }

    void logout() {
        if (sessionStore != null) sessionStore.clear();
        pendingUsername = null;
        pendingToken = null;
        loggedIn = false;
        synchronized (licenses) {
            licenses.clear();
        }
        syncPhase = PHASE_IDLE;
        cancelLibrarySyncTimeout();
        packageInfo.clear();
        appInfo.clear();
        if (database != null) database.clearUnownedGames();
        SteamUser current = steamUser;
        Handler handler = pumpHandler;
        if (current != null && connected) {
            if (handler != null) handler.post(current::logOff);
            else current.logOff();
        }
        emit("LoggedOut");
    }

    String getSteamId64() {
        SteamID steamId = client == null ? null : client.getSteamID();
        return steamId == null ? "" : Long.toUnsignedString(steamId.convertToUInt64());
    }

    String getPersonaName() {
        SteamFriends friends = steamFriends;
        String name = friends == null ? null : friends.getPersonaName();
        return name == null || name.trim().isEmpty() ? getUsername() : name;
    }

    String getFriendPersonaName(long steamId64) {
        SteamFriends friends = steamFriends;
        if (friends == null) return "Steam friend";
        String name = friends.getFriendPersonaName(
                new SteamID(Long.toUnsignedString(steamId64)));
        return name == null || name.trim().isEmpty() ? "Steam friend" : name;
    }

    String getPersonaStatus() {
        SteamFriends friends = steamFriends;
        if (friends == null || friends.getPersonaState() == null) return "Unknown";
        return friends.getPersonaState().name();
    }

    String getCurrentGame() {
        SteamFriends friends = steamFriends;
        SteamID steamId = client == null ? null : client.getSteamID();
        if (friends == null || steamId == null) return "";
        String game = friends.getFriendGamePlayedName(steamId);
        return game == null ? "" : game;
    }

    int getCurrentGameAppId() {
        SteamFriends friends = steamFriends;
        SteamID steamId = client == null ? null : client.getSteamID();
        return friends == null || steamId == null ? 0 : friends.getFriendGameAppId(steamId);
    }

    byte[] getPersonaAvatar() {
        SteamFriends friends = steamFriends;
        if (friends == null) return null;
        byte[] avatar = friends.getPersonaAvatar();
        boolean hasAvatarHash = false;
        if (avatar != null) {
            for (byte value : avatar) {
                hasAvatarHash |= value != 0;
            }
        }
        if (hasAvatarHash) return avatar;
        SteamID steamId = client == null ? null : client.getSteamID();
        return steamId == null ? null : friends.getFriendAvatar(steamId);
    }

    List<SteamFriend> getFriends() {
        List<SteamFriend> result = new ArrayList<>();
        SteamFriends friends = steamFriends;
        if (friends == null) return result;
        for (SteamID id : friends.getFriendsList()) {
            if (id == null) continue;
            String name = friends.getFriendPersonaName(id);
            if (name == null || name.trim().isEmpty()) name = Long.toUnsignedString(id.convertToUInt64());
            String status = friends.getFriendPersonaState(id) == null
                    ? "Unknown" : friends.getFriendPersonaState(id).name();
            String game = friends.getFriendGamePlayedName(id);
            result.add(new SteamFriend(id.convertToUInt64(), name, status,
                    game == null ? "" : game, friends.getFriendAvatar(id)));
        }
        result.sort((left, right) -> {
            boolean leftOnline = !"Offline".equals(left.status);
            boolean rightOnline = !"Offline".equals(right.status);
            if (leftOnline != rightOnline) return leftOnline ? -1 : 1;
            return left.name.compareToIgnoreCase(right.name);
        });
        return result;
    }

    void requestFriendsRefresh() {
        SteamFriends friends = steamFriends;
        if (friends == null || !loggedIn) return;
        Runnable refresh = () -> {
            List<SteamID> ids = friends.getFriendsList();
            if (!ids.isEmpty()) friends.requestFriendInfo(ids);
            emit("FriendsUpdated");
        };
        Handler handler = pumpHandler;
        if (handler != null) handler.post(refresh);
    }

    void sendFriendMessage(long steamId64, String message) {
        if (!loggedIn || steamFriends == null) {
            throw new IllegalStateException("Sign in to Steam before sending a message.");
        }
        if (message == null || message.trim().isEmpty()) return;
        Handler handler = pumpHandler;
        if (handler == null) throw new IllegalStateException("Steam connection is not active.");
        String text = message.trim();
        boolean accepted = handler.post(() -> {
            try {
                SteamID recipient = new SteamID(Long.toUnsignedString(steamId64));
                steamFriends.sendChatMessage(recipient, EChatEntryType.ChatMsg, text);
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not send Steam friend message", e);
                emit("FriendMessageSendFailed:" + errorMessage(e));
            }
        });
        if (!accepted) throw new IllegalStateException("Steam connection is shutting down.");
    }

    private void onFriendMessage(FriendMsgCallback callback) {
        if (callback.getMessage() == null
                || (callback.getEntryType() != EChatEntryType.ChatMsg
                && callback.getEntryType() != EChatEntryType.HistoricalChat)) return;
        emit("FriendMessage:incoming:"
                + Long.toUnsignedString(callback.getSender().convertToUInt64())
                + ":" + Base64.encodeToString(callback.getMessage().getBytes(
                java.nio.charset.StandardCharsets.UTF_8), Base64.NO_WRAP));
    }

    private void onFriendMessageEcho(FriendMsgEchoCallback callback) {
        if (callback.getMessage() == null
                || (callback.getEntryType() != EChatEntryType.ChatMsg
                && callback.getEntryType() != EChatEntryType.HistoricalChat)) return;
        emit("FriendMessage:outgoing:"
                + Long.toUnsignedString(callback.getRecipient().convertToUInt64())
                + ":" + Base64.encodeToString(callback.getMessage().getBytes(
                java.nio.charset.StandardCharsets.UTF_8), Base64.NO_WRAP));
    }

    void requestLibrarySync() {
        if (!loggedIn || steamApps == null) {
            emit("LibraryFailed:Not logged in");
            return;
        }
        if (syncPhase != PHASE_IDLE) {
            emit("LibraryProgress:Sync already running");
            return;
        }
        List<License> snapshot = getLicenses();
        if (snapshot.isEmpty()) {
            emit("LibraryProgress:Waiting for Steam license data");
            return;
        }
        Handler handler = pumpHandler;
        if (handler == null) {
            startLibrarySync(snapshot);
        } else {
            handler.post(() -> startLibrarySync(snapshot));
        }
    }

    private void startPump() {
        if (!pumping.compareAndSet(false, true)) return;
        HandlerThread thread = new HandlerThread("SteamCallbacks");
        thread.start();
        pumpThread = thread;
        pumpHandler = new Handler(thread.getLooper());
        schedulePump();
    }

    private void schedulePump() {
        Handler handler = pumpHandler;
        if (!pumping.get() || handler == null) return;
        handler.post(() -> {
            try {
                CallbackManager current = callbackManager;
                if (current != null) current.runWaitCallbacks(500L);
            } catch (RuntimeException e) {
                Log.e(TAG, "Steam callback pump failed", e);
                emit("ConnectionError:" + errorMessage(e));
            } finally {
                schedulePump();
            }
        });
    }

    private void stopPump() {
        pumping.set(false);
        HandlerThread thread = pumpThread;
        pumpThread = null;
        pumpHandler = null;
        if (thread != null) thread.quitSafely();
    }

    private void onConnected() {
        connected = true;
        connecting = false;
        reconnectAttempts = 0;
        cancelConnectionTimeout();
        cancelReconnectTask();
        emit("Connected");
        String username = pendingUsername;
        String token = pendingToken;
        if (username == null || token == null) {
            try {
                username = sessionStore.getUsername();
                token = sessionStore.getRefreshToken();
            } catch (GeneralSecurityException e) {
                Log.e(TAG, "Could not decrypt saved Steam session", e);
                sessionStore.clear();
                emit("SessionError:" + errorMessage(e));
                return;
            }
        }
        if (!username.isEmpty() && !token.isEmpty()) loginWithToken(username, token);
    }

    private void onDisconnected(DisconnectedCallback callback) {
        connected = false;
        connecting = false;
        loggedIn = false;
        syncPhase = PHASE_IDLE;
        cancelConnectionTimeout();
        cancelLibrarySyncTimeout();
        packageInfo.clear();
        appInfo.clear();
        emit("Disconnected");
        if (!callback.isUserInitiated() && !shutdownRequested) {
            replaceSteamClient();
            scheduleReconnect();
        }
    }

    private void scheduleConnectionTimeout() {
        Handler handler = pumpHandler;
        if (handler == null) return;
        Runnable previous = connectionTimeoutTask;
        if (previous != null) handler.removeCallbacks(previous);
        Runnable timeout = () -> {
            connectionTimeoutTask = null;
            if (shutdownRequested || connected || !connecting) return;
            connecting = false;
            emit("ConnectionError:Steam connection timed out. Retrying automatically.");
            replaceSteamClient();
            scheduleReconnect();
        };
        connectionTimeoutTask = timeout;
        handler.postDelayed(timeout, CONNECTION_TIMEOUT_MS);
    }

    private void cancelConnectionTimeout() {
        Handler handler = pumpHandler;
        Runnable timeout = connectionTimeoutTask;
        connectionTimeoutTask = null;
        if (handler != null && timeout != null) handler.removeCallbacks(timeout);
    }

    private void scheduleReconnect() {
        if (shutdownRequested || !pumping.get() || client == null) return;
        Handler handler = pumpHandler;
        if (handler == null) return;
        Runnable existing = reconnectTask;
        if (existing != null) return;

        reconnectAttempts = Math.min(reconnectAttempts + 1, 5);
        int attempt = reconnectAttempts;
        long delay = Math.min(30_000L, 2_000L << (attempt - 1));
        Runnable retry = () -> {
            reconnectTask = null;
            if (shutdownRequested || !pumping.get() || connected || connecting || client == null) {
                return;
            }
            try {
                connect();
            } catch (RuntimeException e) {
                Log.e(TAG, "Steam reconnect attempt failed", e);
                emit("ConnectionError:" + errorMessage(e));
            }
        };
        reconnectTask = retry;
        if (!handler.postDelayed(retry, delay)) reconnectTask = null;
    }

    private void cancelReconnectTask() {
        Handler handler = pumpHandler;
        Runnable retry = reconnectTask;
        reconnectTask = null;
        if (handler != null && retry != null) handler.removeCallbacks(retry);
    }

    private void onLoggedOn(LoggedOnCallback callback) {
        if (callback.getResult() != EResult.OK) {
            loggedIn = false;
            emit("LoginFailed:" + callback.getResult().name());
            return;
        }
        loggedIn = true;
        SteamID steamId = client == null ? null : client.getSteamID();
        if (steamId != null && sessionStore != null) {
            sessionStore.saveSteamId64(Long.toUnsignedString(steamId.convertToUInt64()));
        }
        emit("LoggedIn");
        requestFriendsRefresh();
        requestLibrarySync();
    }

    private void onLoggedOff(LoggedOffCallback callback) {
        loggedIn = false;
        emit("LoggedOut");
    }

    private void onLicenseList(LicenseListCallback callback) {
        if (callback.getResult() != EResult.OK) {
            finishLibrarySync();
            emit("LibraryFailed:Steam returned " + callback.getResult().name()
                    + " while loading account licenses");
            return;
        }
        List<License> licenseList = callback.getLicenseList();
        synchronized (licenses) {
            licenses.clear();
            licenses.addAll(licenseList);
        }
        if (!loggedIn) return;
        startLibrarySync(licenseList);
    }

    private void onFreeLicense(FreeLicenseCallback callback) {
        int appId;
        synchronized (this) {
            appId = pendingFreeLicenseAppId;
            pendingFreeLicenseAppId = 0;
        }
        if (appId == 0) return;
        if (callback.getResult() != EResult.OK || !callback.getGrantedApps().contains(appId)) {
            emit("FreeLicenseFailed:" + appId + ":Steam did not confirm the free-game license ("
                    + callback.getResult().name() + ").");
            return;
        }
        emit("FreeLicenseAdded:" + appId);
        mainHandler.postDelayed(this::requestLibrarySync, 1000L);
    }

    private void onUserStats(UserStatsCallback callback) {
        int appId = (int) callback.getGameId();
        achievementRequests.remove(appId);
        Runnable timeout = achievementTimeouts.remove(appId);
        if (timeout != null) mainHandler.removeCallbacks(timeout);
        if (callback.getResult() != EResult.OK) {
            emit("AchievementsFailed:" + appId + ":Steam returned " + callback.getResult().name());
            return;
        }
        List<SteamAchievement> result = new ArrayList<>();
        for (AchievementBlocks block : callback.getExpandedAchievements("english")) {
            String name = block.getDisplayName();
            if (name == null || name.trim().isEmpty()) name = block.getName();
            result.add(new SteamAchievement(block.getName(),
                    name == null ? "" : name,
                    block.getDescription() == null ? "" : block.getDescription(),
                    block.getIcon(), block.getIconGray(), block.isUnlocked(), block.getHidden(),
                    block.getFormattedUnlockTime() == null ? "" : block.getFormattedUnlockTime()));
        }
        achievements.put(appId, result);
        emit("AchievementsUpdated:" + appId);
    }

    private synchronized void startLibrarySync(List<License> licenseList) {
        if (syncPhase != PHASE_IDLE) return;
        if (licenseList.isEmpty()) {
            finishLibrarySync();
            database.replaceOwnedGames(Collections.emptyList());
            emit("LibrarySynced:0");
            return;
        }

        List<PICSRequest> requests = new ArrayList<>(licenseList.size());
        for (License license : licenseList) {
            requests.add(new PICSRequest(license.getPackageID(), license.getAccessToken()));
        }
        packageInfo.clear();
        syncPhase = PHASE_PACKAGES;
        beginLibrarySyncTimeout();
        steamApps.picsGetProductInfo(Collections.emptyList(), requests, false);
        emit("LibraryProgress:Loading owned packages");
    }

    private void onProductInfo(PICSProductInfoCallback callback) {
        if (syncPhase == PHASE_PACKAGES) {
            packageInfo.putAll(callback.getPackages());
            if (callback.isResponsePending()) return;

            List<Integer> ids = new ArrayList<>();
            Set<Integer> seenIds = new HashSet<>();
            for (PICSProductInfo product : packageInfo.values()) {
                KeyValue appIds = child(product.getKeyValues(), "appids");
                List<KeyValue> children = appIds == null ? null : appIds.getChildren();
                if (children == null) continue;
                for (KeyValue value : children) {
                    try {
                        String raw = value.getValue();
                        if (raw != null && !raw.isEmpty()) {
                            int id = Integer.parseInt(raw);
                            if (seenIds.add(id)) ids.add(id);
                        }
                    } catch (NumberFormatException ignored) {
                        Log.w(TAG, "Ignoring malformed app ID in Steam package info");
                    }
                }
            }
            if (ids.isEmpty()) {
                finishLibrarySync();
                database.replaceOwnedGames(Collections.emptyList());
                emit("LibrarySynced:0");
                return;
            }

            appInfo.clear();
            syncPhase = PHASE_APPS;
            List<PICSRequest> requests = new ArrayList<>(ids.size());
            for (int id : ids) requests.add(new PICSRequest(id));
            steamApps.picsGetProductInfo(requests, Collections.emptyList(), false);
            emit("LibraryProgress:Loading game details");
            return;
        }

        if (syncPhase == PHASE_APPS) {
            appInfo.putAll(callback.getApps());
            if (callback.isResponsePending()) return;

            List<SteamGame> games = new ArrayList<>();
            for (PICSProductInfo product : appInfo.values()) {
                SteamGame game = parseGame(product);
                if (game != null) games.add(game);
            }
            database.replaceOwnedGames(games);
            finishLibrarySync();
            emit("LibrarySynced:" + games.size());
        }
    }

    private void beginLibrarySyncTimeout() {
        long generation = syncGeneration.incrementAndGet();
        Runnable previous = syncTimeout;
        if (previous != null) mainHandler.removeCallbacks(previous);
        Runnable timeout = () -> {
            if (syncGeneration.get() != generation || syncPhase == PHASE_IDLE) return;
            syncTimeout = null;
            syncPhase = PHASE_IDLE;
            packageInfo.clear();
            appInfo.clear();
            emit("LibraryFailed:Steam library request timed out");
        };
        syncTimeout = timeout;
        mainHandler.postDelayed(timeout, 120_000L);
    }

    private void finishLibrarySync() {
        syncPhase = PHASE_IDLE;
        cancelLibrarySyncTimeout();
        packageInfo.clear();
        appInfo.clear();
    }

    private void cancelLibrarySyncTimeout() {
        syncGeneration.incrementAndGet();
        Runnable timeout = syncTimeout;
        syncTimeout = null;
        if (timeout != null) mainHandler.removeCallbacks(timeout);
    }

    private SteamGame parseGame(PICSProductInfo product) {
        KeyValue root = product.getKeyValues();
        KeyValue common = child(root, "common");
        String type = value(child(common, "type")).toLowerCase(java.util.Locale.ROOT);
        if (type.isEmpty() || "tool".equals(type) || "hardware".equals(type)
                || "music".equals(type) || "video".equals(type)
                || "advertising".equals(type) || "dlc".equals(type)
                || "beta".equals(type)) return null;

        String name = value(child(common, "name"));
        if (name.isEmpty()) return null;
        KeyValue config = child(root, "config");
        String installDirectory = value(child(config, "installdir"));
        KeyValue depots = child(root, "depots");
        List<KeyValue> depotChildren = depots == null ? null : depots.getChildren();
        StringBuilder depotIds = new StringBuilder();
        if (depotChildren != null) {
            for (KeyValue depot : depotChildren) {
                try {
                    int depotId = Integer.parseInt(depot.getName());
                    if (depotIds.length() > 0) depotIds.append(',');
                    depotIds.append(depotId);
                } catch (NumberFormatException ignored) {
                    Log.w(TAG, "Ignoring malformed Steam depot metadata for app " + product.getId());
                }
            }
        }
        return new SteamGame(product.getId(), name, installDirectory, type,
                depotIds.toString(), 0L, 0L, false, "");
    }

    private static KeyValue child(KeyValue parent, String key) {
        return parent == null ? null : parent.get(key);
    }

    private static String value(KeyValue value) {
        if (value == null || value.getValue() == null) return "";
        return value.getValue();
    }

    void emit(String event) {
        mainHandler.post(() -> {
            for (Listener listener : listeners) listener.onSteamEvent(event);
        });
    }

    private static String errorMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message;
    }
}
