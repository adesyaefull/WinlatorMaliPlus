package com.winlator.cmod.store;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import in.dragonbra.javasteam.depotdownloader.DepotDownloader;
import in.dragonbra.javasteam.depotdownloader.IDownloadListener;
import in.dragonbra.javasteam.depotdownloader.data.AppItem;
import in.dragonbra.javasteam.depotdownloader.data.DownloadItem;
import in.dragonbra.javasteam.steam.handlers.steamapps.License;

final class SteamDepotDownloadManager {
    private static final String TAG = "SteamDepot";
    private static final ConcurrentHashMap<Integer, DownloadTask> TASKS =
            new ConcurrentHashMap<>();
    private static final ScheduledExecutorService WATCHDOG =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "SteamDownloadWatchdog");
                thread.setDaemon(true);
                return thread;
            });
    private static final long STALL_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(4);

    static final class DownloadTask {
        final int appId;
        final String name;
        final File installDirectory;
        final long totalBytes;
        final AtomicLong downloadedBytes = new AtomicLong();
        final Map<Integer, Long> depotBytes = new ConcurrentHashMap<>();
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicBoolean failureReported = new AtomicBoolean();
        volatile DepotDownloader downloader;
        volatile String status = "Queued";
        volatile boolean paused;
        volatile int runGeneration;
        volatile String failure;
        volatile long lastActivityAt;
        volatile ScheduledFuture<?> watchdog;

        DownloadTask(int appId, String name, File installDirectory, long totalBytes) {
            this.appId = appId;
            this.name = name;
            this.installDirectory = installDirectory;
            this.totalBytes = totalBytes;
        }
    }

    private SteamDepotDownloadManager() {}

    static boolean isDownloading(int appId) {
        DownloadTask task = TASKS.get(appId);
        return task != null && !task.status.startsWith("Failed")
                && !"Complete".equals(task.status);
    }

    static boolean isPaused(int appId) {
        DownloadTask task = TASKS.get(appId);
        return task != null && task.paused;
    }

    static List<SteamDownload> getDownloads() {
        java.util.ArrayList<SteamDownload> result = new java.util.ArrayList<>();
        for (DownloadTask task : TASKS.values()) {
            result.add(new SteamDownload(task.appId, task.name, task.status,
                    task.downloadedBytes.get(), task.totalBytes, task.paused));
        }
        result.sort(java.util.Comparator.comparing(download -> download.name.toLowerCase(
                java.util.Locale.ROOT)));
        return result;
    }

    static boolean hasActiveDownloads() {
        for (DownloadTask task : TASKS.values()) {
            if (!task.status.startsWith("Failed") && !"Complete".equals(task.status)) return true;
        }
        return false;
    }

    static void start(Context context, int appId) throws Exception {
        SteamRepository repository = SteamRepository.getInstance();
        if (!repository.isLoggedIn()) {
            throw new IllegalStateException("Sign in to Steam before installing a game.");
        }
        SteamGame game = repository.getDatabase().getGame(appId);
        if (game == null) throw new IllegalArgumentException("The selected game is not in the library.");
        if (isDownloading(appId)) return;

        List<License> licenses = repository.getLicenses();
        if (licenses.isEmpty()) {
            throw new IllegalStateException("Steam has not provided the account's game licenses yet.");
        }
        File root = new File(context.getFilesDir(), "imagefs/steam_games");
        String directoryName = game.installDirectory == null || game.installDirectory.trim().isEmpty()
                ? game.name : game.installDirectory;
        directoryName = directoryName.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
        if (directoryName.isEmpty() || ".".equals(directoryName) || "..".equals(directoryName)) {
            throw new IllegalArgumentException("Steam returned an invalid install directory.");
        }
        if (!root.exists() && !root.mkdirs()) {
            throw new java.io.IOException("Could not create the shared Steam library directory.");
        }
        File installDirectory = new File(root, directoryName).getCanonicalFile();
        if (!installDirectory.getParentFile().equals(root.getCanonicalFile())) {
            throw new SecurityException("Steam install directory escaped the library root.");
        }
        if (!installDirectory.exists() && !installDirectory.mkdirs()) {
            throw new java.io.IOException("Could not create the game's install directory.");
        }

        DownloadTask task = new DownloadTask(appId, game.name, installDirectory, game.sizeBytes);
        DownloadTask previous = TASKS.get(appId);
        if (previous != null && (previous.status.startsWith("Failed")
                || "Complete".equals(previous.status))) {
            TASKS.remove(appId, previous);
            previous = null;
        }
        if (previous == null) previous = TASKS.putIfAbsent(appId, task);
        if (previous != null) {
            return;
        }
        try {
            startRun(task, repository, licenses);
        } catch (RuntimeException e) {
            stopWatchdog(task);
            TASKS.remove(appId, task);
            throw e;
        }
    }

    static void pause(int appId) {
        DownloadTask task = TASKS.get(appId);
        if (task == null || task.paused) return;
        synchronized (task) {
            if (task.paused || TASKS.get(appId) != task) return;
            task.paused = true;
            task.status = "Paused";
            task.runGeneration++;
            stopWatchdog(task);
            DepotDownloader downloader = task.downloader;
            task.downloader = null;
            if (downloader != null) downloader.close();
        }
        SteamRepository.getInstance().emit("DownloadPaused:" + appId);
    }

    static void resume(int appId) throws Exception {
        DownloadTask task = TASKS.get(appId);
        if (task == null || !task.paused) return;
        SteamRepository repository = SteamRepository.getInstance();
        List<License> licenses = repository.getLicenses();
        if (licenses.isEmpty()) throw new IllegalStateException("Steam licenses are not available.");
        synchronized (task) {
            if (!task.paused || TASKS.get(appId) != task) return;
            task.paused = false;
            task.status = "Preparing to resume";
            task.downloadedBytes.set(0L);
            task.depotBytes.clear();
            task.failure = null;
            task.failureReported.set(false);
            try {
                startRun(task, repository, licenses);
            } catch (RuntimeException e) {
                stopWatchdog(task);
                task.paused = true;
                task.status = "Paused";
                throw e;
            }
        }
    }

    private static void startRun(DownloadTask task, SteamRepository repository,
                                 List<License> licenses) {
        DepotDownloader downloader = new DepotDownloader(
                repository.getClient(), licenses, false, false, 4, 4, 1, true, null);
        task.downloader = downloader;
        task.status = "Preparing download";
        int generation = ++task.runGeneration;
        task.lastActivityAt = System.currentTimeMillis();
        task.watchdog = WATCHDOG.scheduleWithFixedDelay(
                () -> detectStalledDownload(task, generation, repository),
                1, 1, TimeUnit.MINUTES);
        downloader.addListener(new IDownloadListener() {
            @Override
            public void onItemAdded(DownloadItem item) {}

            @Override
            public void onDownloadStarted(DownloadItem item) {
                if (!isCurrentRun(task, generation)) return;
                recordActivity(task);
                task.status = "Downloading";
                repository.emit("DownloadStarted:" + task.appId);
            }

            @Override
            public void onDownloadCompleted(DownloadItem item) {
                if (!isCurrentRun(task, generation)) return;
                recordActivity(task);
                task.status = "Finishing";
                repository.emit("DownloadFilesReady:" + task.appId);
            }

            @Override
            public void onDownloadFailed(DownloadItem item, Throwable error) {
                if (!isCurrentRun(task, generation)) return;
                stopWatchdog(task);
                task.failure = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                task.status = "Failed: " + task.failure;
                Log.e(TAG, "Depot download failed for app " + task.appId, error);
                reportFailure(task, repository, task.failure);
            }

            @Override
            public void onStatusUpdate(String message) {
                if (!isCurrentRun(task, generation)) return;
                task.status = message;
                repository.emit("DownloadStatus:" + task.appId + ":" + message);
            }

            @Override
            public void onFileCompleted(int depotId, String fileName, float depotPercentComplete) {
                if (isCurrentRun(task, generation)) recordActivity(task);
            }

            @Override
            public void onChunkCompleted(int depotId, float depotPercentComplete,
                                         long compressedBytes, long uncompressedBytes) {
                if (!isCurrentRun(task, generation)) return;
                recordActivity(task);
                task.depotBytes.merge(depotId, Math.max(0L, compressedBytes), Long::sum);
                updateDownloadedBytes(task, repository);
            }

            @Override
            public void onDepotCompleted(int depotId, long compressedBytes, long uncompressedBytes) {
                if (!isCurrentRun(task, generation)) return;
                recordActivity(task);
                task.depotBytes.put(depotId, Math.max(0L, compressedBytes));
                updateDownloadedBytes(task, repository);
            }
        });

        repository.emit("DownloadQueued:" + task.appId);
        try {
            AppItem item = new AppItem(task.appId, false, task.installDirectory.getAbsolutePath(),
                    "public", null, false, "windows", true, null,
                    false, null, false, Collections.emptyList(),
                    Collections.emptyList(), false, false);
            downloader.add(item);
            downloader.finishAdding();
            CompletableFuture<Void> completion = downloader.getCompletion();
            completion.whenComplete((ignored, error) -> {
                try {
                    if (task.cancelled.get() || !isCurrentRun(task, generation)) return;
                    if (error != null) {
                        String reason = error.getMessage() == null
                                ? error.getClass().getSimpleName() : error.getMessage();
                        task.failure = reason;
                        task.status = "Failed: " + reason;
                        stopWatchdog(task);
                        reportFailure(task, repository, reason);
                    } else if (task.failure != null) {
                        task.status = "Failed: " + task.failure;
                        stopWatchdog(task);
                        reportFailure(task, repository, task.failure);
                    } else {
                        task.downloadedBytes.set(sumDepotBytes(task));
                        task.status = "Complete";
                        repository.getDatabase().markInstalled(task.appId,
                                task.installDirectory.getAbsolutePath());
                        TASKS.remove(task.appId, task);
                        repository.emit("DownloadComplete:" + task.appId);
                    }
                } finally {
                    stopWatchdog(task);
                    downloader.close();
                    task.downloader = null;
                }
            });
        } catch (RuntimeException e) {
            stopWatchdog(task);
            TASKS.remove(task.appId, task);
            downloader.close();
            throw e;
        }
    }

    private static boolean isCurrentRun(DownloadTask task, int generation) {
        return !task.cancelled.get() && !task.paused && task.runGeneration == generation;
    }

    private static void reportFailure(DownloadTask task, SteamRepository repository,
                                      String reason) {
        if (task.failureReported.compareAndSet(false, true)) {
            repository.emit("DownloadFailed:" + task.appId + ":" + reason);
        }
    }

    private static void recordActivity(DownloadTask task) {
        task.lastActivityAt = System.currentTimeMillis();
    }

    private static void stopWatchdog(DownloadTask task) {
        ScheduledFuture<?> watchdog = task.watchdog;
        task.watchdog = null;
        if (watchdog != null) watchdog.cancel(false);
    }

    private static void detectStalledDownload(DownloadTask task, int generation,
                                              SteamRepository repository) {
        if (!isCurrentRun(task, generation)
                || System.currentTimeMillis() - task.lastActivityAt < STALL_TIMEOUT_MS) return;
        DepotDownloader downloader;
        synchronized (task) {
            if (!isCurrentRun(task, generation)
                    || System.currentTimeMillis() - task.lastActivityAt < STALL_TIMEOUT_MS) return;
            task.failure = "Download stalled with no Steam depot activity. Retry to continue.";
            task.status = "Failed: " + task.failure;
            task.runGeneration++;
            stopWatchdog(task);
            downloader = task.downloader;
            task.downloader = null;
        }
        if (downloader != null) downloader.close();
        Log.w(TAG, "Steam download stalled for app " + task.appId);
        reportFailure(task, repository, task.failure);
    }

    private static void updateDownloadedBytes(DownloadTask task, SteamRepository repository) {
        long downloaded = sumDepotBytes(task);
        task.downloadedBytes.set(downloaded);
        repository.emit("DownloadProgress:" + task.appId + ":" + downloaded
                + ":" + task.totalBytes);
    }

    private static long sumDepotBytes(DownloadTask task) {
        synchronized (task.depotBytes) {
            long total = 0L;
            for (long bytes : task.depotBytes.values()) {
                total = total > Long.MAX_VALUE - bytes ? Long.MAX_VALUE : total + bytes;
            }
            return total;
        }
    }

    static void cancel(int appId) {
        DownloadTask task = TASKS.remove(appId);
        if (task == null) return;
        task.cancelled.set(true);
        stopWatchdog(task);
        DepotDownloader downloader = task.downloader;
        task.downloader = null;
        if (downloader != null) downloader.close();
        SteamRepository.getInstance().emit("DownloadCancelled:" + appId);
    }

    static void uninstall(Context context, int appId) throws Exception {
        SteamRepository repository = SteamRepository.getInstance();
        if (isDownloading(appId)) {
            throw new IllegalStateException("Pause or cancel the download before removing the game.");
        }
        SteamGame game = repository.getDatabase().getGame(appId);
        if (game == null || !game.installed || game.installedPath.trim().isEmpty()) {
            throw new IllegalStateException("This Steam game is not installed.");
        }
        File root = new File(context.getFilesDir(), "imagefs/steam_games").getCanonicalFile();
        File installDirectory = new File(game.installedPath).getCanonicalFile();
        if (!root.equals(installDirectory.getParentFile())) {
            throw new SecurityException("The game install path is outside the Steam library.");
        }
        if (installDirectory.exists()) deleteTree(installDirectory, installDirectory, true);
        repository.getDatabase().markUninstalled(appId);
        repository.emit("GameUninstalled:" + appId);
    }

    private static void deleteTree(File target, File installRoot, boolean rootEntry)
            throws java.io.IOException {
        File canonical = target.getCanonicalFile();
        String rootPath = installRoot.getPath();
        if ((!rootEntry && !canonical.getPath().startsWith(rootPath + File.separator))
                || (rootEntry && !canonical.equals(installRoot))) {
            throw new SecurityException("Refusing to delete files outside the Steam library.");
        }
        if (canonical.isDirectory()) {
            File[] children = canonical.listFiles();
            if (children == null) throw new java.io.IOException("Could not read " + canonical);
            for (File child : children) deleteTree(child, installRoot, false);
        }
        if (!canonical.delete()) throw new java.io.IOException("Could not delete " + canonical);
    }

    static void cancelAll() {
        for (Integer appId : TASKS.keySet()) cancel(appId);
    }
}
