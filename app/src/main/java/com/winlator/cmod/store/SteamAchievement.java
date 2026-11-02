package com.winlator.cmod.store;

final class SteamAchievement {
    final String name;
    final String displayName;
    final String description;
    final String iconUrl;
    final String lockedIconUrl;
    final boolean unlocked;
    final boolean hidden;
    final String unlockedAt;

    SteamAchievement(String name, String displayName, String description, String iconUrl,
                     String lockedIconUrl, boolean unlocked, boolean hidden, String unlockedAt) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.iconUrl = iconUrl;
        this.lockedIconUrl = lockedIconUrl;
        this.unlocked = unlocked;
        this.hidden = hidden;
        this.unlockedAt = unlockedAt;
    }
}
