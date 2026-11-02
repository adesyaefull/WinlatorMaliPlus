package com.winlator.cmod.store;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;

import in.dragonbra.javasteam.enums.EOSType;
import in.dragonbra.javasteam.steam.authentication.AuthPollResult;
import in.dragonbra.javasteam.steam.authentication.AuthSessionDetails;
import in.dragonbra.javasteam.steam.authentication.QrAuthSession;
import in.dragonbra.javasteam.steam.authentication.SteamAuthentication;

final class SteamQrAuthManager {
    interface Listener {
        void onQrReady(String challengeUrl);
        void onQrRefreshed(String challengeUrl);
        void onQrAuthenticated(String username);
        void onQrAuthenticationFailed(String reason);
    }

    private static final String TAG = "SteamQrAuth";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicLong attempts = new AtomicLong();
    private volatile Thread authThread;
    private volatile Thread watcherThread;

    void login(Listener listener) {
        cancel();
        long attempt = attempts.incrementAndGet();
        Thread thread = new Thread(() -> runLogin(listener, attempt), "SteamQrAuth");
        authThread = thread;
        thread.start();
    }

    void cancel() {
        attempts.incrementAndGet();
        Thread thread = authThread;
        authThread = null;
        if (thread != null) thread.interrupt();
        Thread watcher = watcherThread;
        watcherThread = null;
        if (watcher != null) watcher.interrupt();
    }

    private void runLogin(Listener listener, long attempt) {
        Thread attemptWatcher = null;
        try {
            SteamRepository repository = SteamRepository.getInstance();
            if (repository.getClient() == null || !repository.isConnected()) {
                postFailure(listener, attempt,
                        "Steam is not connected yet. Try again in a moment.");
                return;
            }
            AuthSessionDetails details = new AuthSessionDetails();
            details.clientOSType = EOSType.AndroidUnknown;
            details.deviceFriendlyName = "Winlator Mali+";
            details.persistentSession = true;
            SteamAuthentication authentication = new SteamAuthentication(repository.getClient());
            QrAuthSession session = authentication.beginAuthSessionViaQR(details).get();
            if (!isCurrent(attempt)) return;

            String firstUrl = session.getChallengeUrl();
            mainHandler.post(() -> {
                if (isCurrent(attempt)) listener.onQrReady(firstUrl);
            });
            attemptWatcher = new Thread(() -> watchUrl(session, listener, firstUrl, attempt),
                    "SteamQrChallengeWatcher");
            watcherThread = attemptWatcher;
            attemptWatcher.start();

            AuthPollResult result = session.pollingWaitForResult().get();
            attemptWatcher.interrupt();
            if (!isCurrent(attempt)) return;
            String username = result.getAccountName();
            if (username == null || username.isEmpty()) username = "Steam account";
            repository.saveSession(username, result.getRefreshToken());
            repository.loginWithToken(username, result.getRefreshToken());
            String finalUsername = username;
            mainHandler.post(() -> {
                if (isCurrent(attempt)) listener.onQrAuthenticated(finalUsername);
            });
        } catch (ExecutionException e) {
            if (!isCurrent(attempt)) return;
            Throwable cause = e.getCause() == null ? e : e.getCause();
            Log.e(TAG, "QR authentication was rejected", cause);
            postFailure(listener, attempt, message(cause));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (isCurrent(attempt)) postFailure(listener, attempt, "QR sign-in was interrupted.");
        } catch (CancellationException e) {
            if (!isCurrent(attempt)) return;
            Log.w(TAG, "Steam cancelled the QR authentication request", e);
            postFailure(listener, attempt,
                    "Steam cancelled the QR sign-in. Check your connection and try again.");
        } catch (Exception e) {
            if (isCurrent(attempt)) {
                Log.e(TAG, "QR authentication failed", e);
                postFailure(listener, attempt, message(e));
            }
        } finally {
            if (attemptWatcher != null) {
                attemptWatcher.interrupt();
                if (watcherThread == attemptWatcher) watcherThread = null;
            }
            if (authThread == Thread.currentThread()) authThread = null;
        }
    }

    private void watchUrl(QrAuthSession session, Listener listener, String initialUrl, long attempt) {
        String previous = initialUrl;
        while (isCurrent(attempt) && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(2_000L);
                String current = session.getChallengeUrl();
                if (current != null && !current.equals(previous)) {
                    previous = current;
                    String updatedUrl = current;
                    mainHandler.post(() -> {
                        if (isCurrent(attempt)) listener.onQrRefreshed(updatedUrl);
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not refresh QR challenge", e);
                return;
            }
        }
    }

    private void postFailure(Listener listener, long attempt, String reason) {
        mainHandler.post(() -> {
            if (isCurrent(attempt)) listener.onQrAuthenticationFailed(reason);
        });
    }

    private boolean isCurrent(long attempt) {
        return attempts.get() == attempt;
    }

    private static String message(Throwable error) {
        String text = error.getMessage();
        return text == null || text.trim().isEmpty()
                ? error.getClass().getSimpleName() : text;
    }
}
