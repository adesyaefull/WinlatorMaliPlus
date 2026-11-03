package com.winlator.cmod.store;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class SteamDatabase extends SQLiteOpenHelper {
    private static final String NAME = "steam_library.db";
    private static final int VERSION = 2;

    SteamDatabase(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE games (" +
                "app_id INTEGER PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "install_directory TEXT NOT NULL," +
                "type TEXT NOT NULL," +
                "depot_ids TEXT NOT NULL," +
                "size_bytes INTEGER NOT NULL," +
                "installed_size_bytes INTEGER NOT NULL DEFAULT 0," +
                "owned INTEGER NOT NULL DEFAULT 1," +
                "installed INTEGER NOT NULL DEFAULT 0," +
                "installed_path TEXT NOT NULL DEFAULT '')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2 && newVersion >= 2) {
            db.execSQL("ALTER TABLE games ADD COLUMN installed_size_bytes INTEGER NOT NULL DEFAULT 0");
            oldVersion = 2;
        }
        if (oldVersion != newVersion) {
            throw new IllegalStateException("Unsupported Steam library database upgrade: "
                    + oldVersion + " -> " + newVersion);
        }
    }

    synchronized void replaceOwnedGames(List<SteamGame> games) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("UPDATE games SET owned = 0");
            for (SteamGame game : games) {
                ContentValues values = new ContentValues();
                values.put("app_id", game.appId);
                values.put("name", game.name);
                values.put("install_directory", game.installDirectory);
                values.put("type", game.type);
                values.put("depot_ids", game.depotIds);
                values.put("size_bytes", game.sizeBytes);
                values.put("owned", 1);
                db.insertWithOnConflict("games", null, values, SQLiteDatabase.CONFLICT_IGNORE);
                db.update("games", values, "app_id = ?", new String[]{String.valueOf(game.appId)});
            }
            db.delete("games", "owned = 0 AND installed = 0", null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    synchronized List<SteamGame> getOwnedGames() {
        List<SteamGame> games = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT app_id,name,install_directory,type,depot_ids,size_bytes,installed_size_bytes,installed,installed_path " +
                        "FROM games WHERE owned = 1 AND lower(type) = 'game' " +
                        "ORDER BY name COLLATE NOCASE", null)) {
            while (cursor.moveToNext()) {
                games.add(new SteamGame(cursor.getInt(0), cursor.getString(1),
                        cursor.getString(2), cursor.getString(3), cursor.getString(4),
                        cursor.getLong(5), cursor.getLong(6), cursor.getInt(7) != 0,
                        cursor.getString(8)));
            }
        }
        return games;
    }

    synchronized SteamGame getGame(int appId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT app_id,name,install_directory,type,depot_ids,size_bytes,installed_size_bytes,installed,installed_path " +
                        "FROM games WHERE app_id = ? AND lower(type) = 'game'",
                new String[]{String.valueOf(appId)})) {
            if (!cursor.moveToFirst()) return null;
            return new SteamGame(cursor.getInt(0), cursor.getString(1),
                    cursor.getString(2), cursor.getString(3), cursor.getString(4),
                    cursor.getLong(5), cursor.getLong(6), cursor.getInt(7) != 0,
                    cursor.getString(8));
        }
    }

    synchronized boolean isOwned(int appId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT 1 FROM games WHERE app_id = ? AND owned = 1",
                new String[]{String.valueOf(appId)})) {
            return cursor.moveToFirst();
        }
    }

    synchronized void markInstalled(int appId, String path, long actualSizeBytes) {
        ContentValues values = new ContentValues();
        values.put("installed", 1);
        values.put("installed_path", path);
        values.put("installed_size_bytes", Math.max(0L, actualSizeBytes));
        getWritableDatabase().update("games", values, "app_id = ?",
                new String[]{String.valueOf(appId)});
    }

    synchronized void markUninstalled(int appId) {
        ContentValues values = new ContentValues();
        values.put("installed", 0);
        values.put("installed_path", "");
        values.put("installed_size_bytes", 0L);
        getWritableDatabase().update("games", values, "app_id = ?",
                new String[]{String.valueOf(appId)});
    }

    synchronized void clearUnownedGames() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("UPDATE games SET owned = 0");
            db.delete("games", "installed = 0", null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
