package com.winlator.cmod.store;

final class SteamFriend {
    final long steamId;
    final String name;
    final String status;
    final String game;
    final byte[] avatar;

    SteamFriend(long steamId, String name, String status, String game, byte[] avatar) {
        this.steamId = steamId;
        this.name = name;
        this.status = status;
        this.game = game;
        this.avatar = avatar;
    }
}
