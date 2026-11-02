package com.winlator.cmod.store;

final class SteamGame {
    final int appId;
    final String name;
    final String installDirectory;
    final String type;
    final String depotIds;
    final long sizeBytes;
    final boolean installed;
    final String installedPath;

    SteamGame(int appId, String name, String installDirectory, String type,
              String depotIds, long sizeBytes, boolean installed, String installedPath) {
        this.appId = appId;
        this.name = name;
        this.installDirectory = installDirectory;
        this.type = type;
        this.depotIds = depotIds;
        this.sizeBytes = sizeBytes;
        this.installed = installed;
        this.installedPath = installedPath;
    }
}
