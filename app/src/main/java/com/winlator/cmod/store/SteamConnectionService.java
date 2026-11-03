package com.winlator.cmod.store;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;

import com.winlator.cmod.R;

public final class SteamConnectionService extends Service implements SteamRepository.Listener {
    public static final String ACTION_IN_GAME_FRIEND_MESSAGE =
            "com.winlator.cmod.store.IN_GAME_FRIEND_MESSAGE";
    public static final String EXTRA_CHAT_SENDER_NAME = "chat_sender_name";
    public static final String EXTRA_CHAT_MESSAGE = "chat_message";
    private static final String TAG = "SteamService";
    private static final String CHANNEL_ID = "steam";
    public static final String CHAT_CHANNEL_ID = "steam_messages";
    private static final int NOTIFICATION_ID = 2010;
    private static final String ACTION_CONNECT = "com.winlator.cmod.store.CONNECT";
    private static final String ACTION_INSTALL = "com.winlator.cmod.store.INSTALL";
    private static final String ACTION_CANCEL = "com.winlator.cmod.store.CANCEL";
    private static final String ACTION_PAUSE = "com.winlator.cmod.store.PAUSE";
    private static final String ACTION_RESUME = "com.winlator.cmod.store.RESUME";
    private static final String ACTION_UNINSTALL = "com.winlator.cmod.store.UNINSTALL";
    private static final String ACTION_REPLY_TO_FRIEND =
            "com.winlator.cmod.store.REPLY_TO_FRIEND";
    private static final String EXTRA_APP_ID = "app_id";
    private static final String EXTRA_FRIEND_STEAM_ID = "friend_steam_id";
    private static final String REMOTE_INPUT_REPLY_KEY = "steam_friend_message";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String notificationText = "Connecting to Steam";
    private long lastProgressNotification;
    private boolean foregroundStarted;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Network activeDefaultNetwork;
    private boolean networkValidated;
    private final Runnable networkMonitor = new Runnable() {
        @Override
        public void run() {
            if (connectivityManager == null) return;
            checkDefaultNetwork();
            mainHandler.postDelayed(this, 10_000L);
        }
    };

    public static void start(Context context) {
        Intent intent = new Intent(context, SteamConnectionService.class)
                .setAction(ACTION_CONNECT);
        ContextCompat.startForegroundService(context, intent);
    }

    static void install(Context context, int appId) {
        Intent intent = new Intent(context, SteamConnectionService.class)
                .setAction(ACTION_INSTALL)
                .putExtra(EXTRA_APP_ID, appId);
        ContextCompat.startForegroundService(context, intent);
    }

    static void cancel(Context context, int appId) {
        Intent intent = new Intent(context, SteamConnectionService.class)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_APP_ID, appId);
        ContextCompat.startForegroundService(context, intent);
    }

    static void pause(Context context, int appId) {
        Intent intent = new Intent(context, SteamConnectionService.class)
                .setAction(ACTION_PAUSE)
                .putExtra(EXTRA_APP_ID, appId);
        ContextCompat.startForegroundService(context, intent);
    }

    static void resume(Context context, int appId) {
        Intent intent = new Intent(context, SteamConnectionService.class)
                .setAction(ACTION_RESUME)
                .putExtra(EXTRA_APP_ID, appId);
        ContextCompat.startForegroundService(context, intent);
    }

    static void uninstall(Context context, int appId) {
        Intent intent = new Intent(context, SteamConnectionService.class)
                .setAction(ACTION_UNINSTALL)
                .putExtra(EXTRA_APP_ID, appId);
        ContextCompat.startForegroundService(context, intent);
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, SteamConnectionService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        foregroundStarted = true;
        SteamRepository repository = SteamRepository.getInstance();
        repository.addListener(this);
        try {
            repository.initialize(this);
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not start Steam connection", e);
            notificationText = "Steam connection failed: " + message(e);
            updateNotification();
        }
        registerNetworkCallback(repository);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, buildNotification());
            foregroundStarted = true;
        }
        if (intent == null) return START_STICKY;
        String action = intent.getAction();
        int appId = intent.getIntExtra(EXTRA_APP_ID, 0);
        if (ACTION_REPLY_TO_FRIEND.equals(action)) {
            handleFriendReply(intent);
        } else if (ACTION_CONNECT.equals(action)) {
            try {
                SteamRepository.getInstance().connect();
            } catch (RuntimeException e) {
                Log.e(TAG, "Could not reconnect to Steam", e);
                notificationText = "Steam connection failed: " + message(e);
                updateNotification();
            }
        } else if (ACTION_INSTALL.equals(action)) {
            try {
                SteamDepotDownloadManager.start(this, appId);
                notificationText = "Preparing Steam download";
            } catch (Exception e) {
                Log.e(TAG, "Could not start Steam download for app " + appId, e);
                notificationText = "Download could not start: " + message(e);
                SteamRepository.getInstance().emit(
                        "DownloadFailed:" + appId + ":" + message(e));
            }
            updateNotification();
        } else if (ACTION_CANCEL.equals(action)) {
            SteamDepotDownloadManager.cancel(appId);
            notificationText = "Steam download cancelled";
            updateNotification();
        } else if (ACTION_PAUSE.equals(action)) {
            SteamDepotDownloadManager.pause(appId);
        } else if (ACTION_RESUME.equals(action)) {
            try {
                SteamDepotDownloadManager.resume(appId);
            } catch (Exception e) {
                Log.e(TAG, "Could not resume Steam download for app " + appId, e);
                SteamRepository.getInstance().emit(
                        "DownloadFailed:" + appId + ":" + message(e));
            }
        } else if (ACTION_UNINSTALL.equals(action)) {
            new Thread(() -> {
                try {
                    SteamDepotDownloadManager.uninstall(getApplicationContext(), appId);
                } catch (Exception e) {
                    Log.e(TAG, "Could not remove installed Steam game " + appId, e);
                    SteamRepository.getInstance().emit(
                            "GameUninstallFailed:" + appId + ":" + message(e));
                }
            }, "SteamGameUninstall").start();
        }
        return START_STICKY;
    }

    @Override
    public void onSteamEvent(String event) {
        if (event.startsWith("FriendMessage:incoming:")) {
            showFriendMessageNotification(event);
            return;
        } else if (event.startsWith("DownloadProgress:")) {
            long now = System.currentTimeMillis();
            if (now - lastProgressNotification < 1500L) return;
            lastProgressNotification = now;
            String[] parts = event.split(":", 4);
            notificationText = parts.length == 4
                    ? "Current depot " + parts[3] + "% · Steam transfer "
                    + formatSize(parseLong(parts[2])) + " (compressed)"
                    : "Downloading game";
        } else if (event.startsWith("DownloadStatus:")) {
            notificationText = "Installing Steam game";
        } else if (event.startsWith("DownloadPaused:")) {
            notificationText = "Steam download paused";
        } else if (event.startsWith("DownloadComplete:")) {
            notificationText = "Steam game installed";
        } else if (event.startsWith("DownloadFailed:")) {
            int appIdEnd = event.indexOf(':', "DownloadFailed:".length());
            notificationText = "Steam download failed: "
                    + (appIdEnd < 0 ? "Unknown error" : event.substring(appIdEnd + 1));
        } else if (event.startsWith("GameUninstalled:")) {
            notificationText = "Steam game removed";
        } else if ("LoggedIn".equals(event)) {
            notificationText = "Connected to Steam";
        } else if (event.startsWith("ConnectionError:") || event.startsWith("SessionError:")) {
            notificationText = event.substring(event.indexOf(':') + 1);
        } else if ("Disconnected".equals(event)) {
            notificationText = "Steam disconnected";
        } else {
            return;
        }
        updateNotification();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.i(TAG, "Winlator task removed; pausing Steam downloads and stopping notification");
        SteamRepository.getInstance().removeListener(this);
        SteamDepotDownloadManager.pauseAll();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        SteamRepository repository = SteamRepository.getInstance();
        unregisterNetworkCallback();
        repository.removeListener(this);
        SteamDepotDownloadManager.pauseAll();
        repository.disconnect();
        super.onDestroy();
    }

    private void registerNetworkCallback(SteamRepository repository) {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                mainHandler.post(SteamConnectionService.this::checkDefaultNetwork);
            }

            @Override
            public void onLost(Network network) {
                mainHandler.post(SteamConnectionService.this::checkDefaultNetwork);
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                mainHandler.post(SteamConnectionService.this::checkDefaultNetwork);
            }
        };
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
            checkDefaultNetwork();
            mainHandler.postDelayed(networkMonitor, 10_000L);
        } catch (RuntimeException e) {
            networkCallback = null;
            Log.e(TAG, "Could not monitor network recovery for Steam", e);
            repository.emit("ConnectionError:Network recovery monitoring failed: " + message(e));
        }
    }

    private void checkDefaultNetwork() {
        ConnectivityManager manager = connectivityManager;
        if (manager == null) return;
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = network == null
                ? null : manager.getNetworkCapabilities(network);
        boolean validated = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        SteamRepository repository = SteamRepository.getInstance();
        if (validated) {
            boolean networkChanged = !networkValidated || !network.equals(activeDefaultNetwork);
            activeDefaultNetwork = network;
            networkValidated = true;
            if (networkChanged) repository.onNetworkAvailable();
        } else if (networkValidated) {
            activeDefaultNetwork = null;
            networkValidated = false;
            repository.onNetworkLost();
        }
    }

    private void unregisterNetworkCallback() {
        mainHandler.removeCallbacks(networkMonitor);
        if (connectivityManager == null) return;
        try {
            if (networkCallback != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Steam network callback was already unregistered", e);
        } finally {
            networkCallback = null;
            activeDefaultNetwork = null;
            networkValidated = false;
            connectivityManager = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID, "Steam integration", NotificationManager.IMPORTANCE_LOW));
                NotificationChannel messages = new NotificationChannel(
                        CHAT_CHANNEL_ID, "Steam friend messages", NotificationManager.IMPORTANCE_HIGH);
                messages.setDescription("Incoming Steam friend messages and quick replies");
                messages.enableVibration(true);
                manager.createNotificationChannel(messages);
            }
        }
    }

    private void showFriendMessageNotification(String event) {
        String prefix = "FriendMessage:incoming:";
        int separator = event.indexOf(':', prefix.length());
        if (separator < 0) {
            Log.w(TAG, "Ignoring malformed incoming Steam friend message event");
            return;
        }
        final long friendSteamId;
        final String message;
        try {
            friendSteamId = Long.parseUnsignedLong(
                    event.substring(prefix.length(), separator));
            message = new String(android.util.Base64.decode(
                    event.substring(separator + 1), android.util.Base64.NO_WRAP),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Ignoring malformed incoming Steam friend message", e);
            return;
        }
        if (message.trim().isEmpty()) return;

        SteamRepository repository = SteamRepository.getInstance();
        String friendName = repository.getFriendPersonaName(friendSteamId);
        Intent inGameMessage = new Intent(ACTION_IN_GAME_FRIEND_MESSAGE)
                .setPackage(getPackageName())
                .putExtra(EXTRA_CHAT_SENDER_NAME, friendName)
                .putExtra(EXTRA_CHAT_MESSAGE, message);
        sendBroadcast(inGameMessage);

        int notificationId = 4000 + (Long.hashCode(friendSteamId) & 0x3FFFFFFF);
        int requestCode = Long.hashCode(friendSteamId) & 0x3FFFFFFF;
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntentFlags |= PendingIntent.FLAG_MUTABLE;
        }

        Intent open = new Intent(this, SteamStoreActivity.class);
        PendingIntent openIntent = PendingIntent.getActivity(this, requestCode, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent reply = new Intent(this, SteamConnectionService.class)
                .setAction(ACTION_REPLY_TO_FRIEND)
                .putExtra(EXTRA_FRIEND_STEAM_ID, friendSteamId);
        PendingIntent replyIntent = PendingIntent.getService(this, requestCode, reply,
                pendingIntentFlags);
        RemoteInput remoteInput = new RemoteInput.Builder(REMOTE_INPUT_REPLY_KEY)
                .setLabel("Reply to " + friendName)
                .build();
        NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send, "Reply", replyIntent)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build();

        Notification notification = new NotificationCompat.Builder(this, CHAT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ab_gear_0011)
                .setContentTitle(friendName)
                .setContentText(message)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .addAction(replyAction)
                .build();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(notificationId, notification);
    }

    private void handleFriendReply(Intent intent) {
        long friendSteamId = intent.getLongExtra(EXTRA_FRIEND_STEAM_ID, 0);
        android.os.Bundle results = RemoteInput.getResultsFromIntent(intent);
        CharSequence reply = results == null ? null : results.getCharSequence(REMOTE_INPUT_REPLY_KEY);
        if (friendSteamId == 0 || reply == null || reply.toString().trim().isEmpty()) return;
        try {
            SteamRepository.getInstance().sendFriendMessage(
                    friendSteamId, reply.toString());
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not send Steam friend quick reply", e);
            notificationText = "Could not send reply: " + message(e);
            updateNotification();
        }
    }

    private Notification buildNotification() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        Intent open = new Intent(this, SteamStoreActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, open, flags);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ab_gear_0011)
                .setContentTitle("Steam")
                .setContentText(notificationText)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification() {
        mainHandler.post(() -> {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
        });
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1_073_741_824L) return String.format(java.util.Locale.US, "%.1f GB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576L) return String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0);
        return String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0);
    }

    private static String message(Throwable error) {
        String text = error.getMessage();
        return text == null || text.trim().isEmpty()
                ? error.getClass().getSimpleName() : text;
    }
}
