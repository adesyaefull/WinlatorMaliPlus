package com.winlator.cmod.store;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

final class SteamSessionStore {
    private static final String KEY_ALIAS = "winlator.steam.session";
    private static final String PREFS = "steam_session";
    private static final String USERNAME = "username";
    private static final String TOKEN = "refresh_token";
    private static final String STEAM_ID64 = "steam_id64";

    private final SharedPreferences preferences;

    SteamSessionStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized void save(String username, String refreshToken) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(refreshToken.getBytes(StandardCharsets.UTF_8));
        ByteBuffer payload = ByteBuffer.allocate(Integer.BYTES + iv.length + encrypted.length);
        payload.putInt(iv.length).put(iv).put(encrypted);
        preferences.edit()
                .putString(USERNAME, username)
                .putString(TOKEN, Base64.encodeToString(payload.array(), Base64.NO_WRAP))
                .remove(STEAM_ID64)
                .apply();
    }

    synchronized String getUsername() {
        return preferences.getString(USERNAME, "");
    }

    synchronized String getSteamId64() {
        return preferences.getString(STEAM_ID64, "");
    }

    synchronized void saveSteamId64(String steamId64) {
        preferences.edit().putString(STEAM_ID64, steamId64).apply();
    }

    synchronized String getRefreshToken() throws GeneralSecurityException {
        String encoded = preferences.getString(TOKEN, "");
        if (encoded.isEmpty()) return "";
        byte[] payload;
        try {
            payload = Base64.decode(encoded, Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Stored Steam session is malformed", e);
        }
        if (payload.length < Integer.BYTES + 12 + 16) {
            throw new GeneralSecurityException("Stored Steam session is incomplete");
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int ivLength = buffer.getInt();
        if (ivLength < 12 || ivLength > buffer.remaining() - 16) {
            throw new GeneralSecurityException("Stored Steam session has an invalid IV");
        }
        byte[] iv = new byte[ivLength];
        buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                new javax.crypto.spec.GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    synchronized void clear() {
        preferences.edit().clear().apply();
    }

    private SecretKey getOrCreateKey() throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
            if (existing instanceof SecretKey) return (SecretKey) existing;

            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            return generator.generateKey();
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException("Unable to access the Android keystore", e);
        } catch (java.security.KeyStoreException | java.security.cert.CertificateException e) {
            throw new GeneralSecurityException("Unable to load the Android keystore", e);
        }
    }
}
