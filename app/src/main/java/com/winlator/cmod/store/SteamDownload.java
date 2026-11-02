package com.winlator.cmod.store;

final class SteamDownload {
    final int appId;
    final String name;
    final String status;
    final long downloadedBytes;
    final long totalBytes;
    final boolean paused;

    SteamDownload(int appId, String name, String status, long downloadedBytes,
                  long totalBytes, boolean paused) {
        this.appId = appId;
        this.name = name;
        this.status = status;
        this.downloadedBytes = downloadedBytes;
        this.totalBytes = totalBytes;
        this.paused = paused;
    }

    int getProgressPercent() {
        if ("Complete".equals(status)) return 100;
        if (totalBytes <= 0) return 0;
        return (int) Math.min(99, downloadedBytes * 100.0 / totalBytes);
    }
}
