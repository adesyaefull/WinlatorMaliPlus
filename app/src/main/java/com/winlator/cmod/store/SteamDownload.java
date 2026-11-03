package com.winlator.cmod.store;

final class SteamDownload {
    final int appId;
    final String name;
    final String status;
    final long downloadedBytes;
    final int currentDepotProgress;
    final boolean paused;

    SteamDownload(int appId, String name, String status, long downloadedBytes,
                  int currentDepotProgress, boolean paused) {
        this.appId = appId;
        this.name = name;
        this.status = status;
        this.downloadedBytes = downloadedBytes;
        this.currentDepotProgress = currentDepotProgress;
        this.paused = paused;
    }
}
