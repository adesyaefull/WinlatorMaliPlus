package com.winlator.cmod.store;

final class SteamBadge {
    final String name;
    final String imageUrl;
    final String xp;
    final String unlockedAt;

    SteamBadge(String name, String imageUrl, String xp, String unlockedAt) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.xp = xp;
        this.unlockedAt = unlockedAt;
    }
}
