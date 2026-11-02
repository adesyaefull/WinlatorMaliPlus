package com.winlator.cmod.store;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import in.dragonbra.javasteam.enums.EOSType;
import in.dragonbra.javasteam.steam.authentication.AuthPollResult;
import in.dragonbra.javasteam.steam.authentication.AuthSessionDetails;
import in.dragonbra.javasteam.steam.authentication.CredentialsAuthSession;
import in.dragonbra.javasteam.steam.authentication.IAuthenticator;
import in.dragonbra.javasteam.steam.authentication.SteamAuthentication;

final class SteamAuthManager {
    interface Listener {
        void onGuardCodeRequired(String destination, boolean codeWasWrong);
        void onDeviceApprovalRequired();
        void onAuthenticated(String username);
        void onAuthenticationFailed(String reason);
    }

    private static final String TAG = "SteamAuth";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicLong attempts = new AtomicLong();
    private volatile CompletableFuture<String> waitingForGuardCode;
    private volatile Thread authThread;

    void login(String username, String password, Listener listener) {
        cancel();
        long attempt = attempts.incrementAndGet();
        Thread thread = new Thread(() -> {
            AuthSessionDetails details = null;
            try {
                SteamRepository repository = SteamRepository.getInstance();
                if (repository.getClient() == null || !repository.isConnected()) {
                    postFailure(listener, attempt, "Steam is not connected yet. Try again in a moment.");
                    return;
                }

                details = new AuthSessionDetails();
                details.username = username;
                details.password = password;
                details.clientOSType = EOSType.AndroidUnknown;
                details.deviceFriendlyName = "Winlator Mali+";
                details.persistentSession = true;
                details.authenticator = new IAuthenticator() {
                    @Override
                    public CompletableFuture<Boolean> acceptDeviceConfirmation() {
                        mainHandler.post(() -> {
                            if (isCurrent(attempt)) listener.onDeviceApprovalRequired();
                        });
                        return CompletableFuture.completedFuture(true);
                    }

                    @Override
                    public CompletableFuture<String> getEmailCode(String email, boolean previousCodeWrong) {
                        return requestGuardCode(listener, attempt, email, previousCodeWrong);
                    }

                    @Override
                    public CompletableFuture<String> getDeviceCode(boolean previousCodeWrong) {
                        return requestGuardCode(listener, attempt, "Steam Guard authenticator",
                                previousCodeWrong);
                    }
                };

                SteamAuthentication authentication =
                        new SteamAuthentication(repository.getClient());
                CredentialsAuthSession session =
                        authentication.beginAuthSessionViaCredentials(details).get();
                details.password = null;
                AuthPollResult result = session.pollingWaitForResult().get();
                if (!isCurrent(attempt)) return;
                String accountName = result.getAccountName();
                String finalUsername = accountName == null || accountName.isEmpty()
                        ? username : accountName;
                repository.saveSession(finalUsername, result.getRefreshToken());
                repository.loginWithToken(finalUsername, result.getRefreshToken());
                mainHandler.post(() -> {
                    if (isCurrent(attempt)) listener.onAuthenticated(finalUsername);
                });
            } catch (ExecutionException e) {
                if (!isCurrent(attempt)) return;
                Throwable cause = e.getCause() == null ? e : e.getCause();
                Log.e(TAG, "Steam authentication was rejected", cause);
                postFailure(listener, attempt, message(cause));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (isCurrent(attempt)) postFailure(listener, attempt,
                        "Steam authentication was interrupted.");
            } catch (CancellationException e) {
                if (!isCurrent(attempt)) return;
                Log.w(TAG, "Steam cancelled the authentication request", e);
                postFailure(listener, attempt,
                        "Steam cancelled the sign-in request. Check your connection and try again.");
            } catch (Exception e) {
                if (!isCurrent(attempt)) return;
                Log.e(TAG, "Steam authentication failed", e);
                postFailure(listener, attempt, message(e));
            } finally {
                if (details != null) details.password = null;
                if (isCurrent(attempt)) {
                    waitingForGuardCode = null;
                    authThread = null;
                }
            }
        }, "SteamCredentialAuth");
        authThread = thread;
        thread.start();
    }

    void submitGuardCode(String code) {
        CompletableFuture<String> future = waitingForGuardCode;
        if (future != null) future.complete(code);
    }

    void cancel() {
        attempts.incrementAndGet();
        CompletableFuture<String> future = waitingForGuardCode;
        waitingForGuardCode = null;
        if (future != null) future.completeExceptionally(
                new InterruptedException("Authentication cancelled"));
        Thread thread = authThread;
        authThread = null;
        if (thread != null) thread.interrupt();
    }

    private CompletableFuture<String> requestGuardCode(Listener listener, long attempt, String destination,
                                                        boolean codeWasWrong) {
        CompletableFuture<String> future = new CompletableFuture<>();
        waitingForGuardCode = future;
        mainHandler.post(() -> {
            if (isCurrent(attempt)) listener.onGuardCodeRequired(destination, codeWasWrong);
        });
        return future;
    }

    private void postFailure(Listener listener, long attempt, String reason) {
        mainHandler.post(() -> {
            if (isCurrent(attempt)) listener.onAuthenticationFailed(reason);
        });
    }

    private boolean isCurrent(long attempt) {
        return attempts.get() == attempt;
    }

    private static String message(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message;
    }
}
