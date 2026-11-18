package com.winlator.cmod.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Stores DXVK shader and state caches in a stable per-shortcut directory.
 * Clearing a game's cache never traverses symbolic links or sibling game caches.
 */
public final class DxvkCacheManager {
    private static final String CACHE_ROOT = "usr/var/cache/dxvk";

    private DxvkCacheManager() {}

    public static String createIdentity(String shortcutUuid, String executablePath,
                                         String shortcutName, int containerId) {
        String identity = shortcutUuid == null ? "" : shortcutUuid.trim();
        if (identity.isEmpty()) identity = executablePath == null ? "" : executablePath.trim();
        if (identity.isEmpty()) identity = shortcutName == null ? "" : shortcutName.trim();
        if (identity.isEmpty()) identity = "container";
        return "container-" + containerId + ":" + identity;
    }

    private static String cacheKey(String identity) {
        String value = identity == null || identity.isEmpty() ? "unknown" : identity;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(32);
            for (int i = 0; i < 16; i++) result.append(String.format(java.util.Locale.ROOT, "%02x", digest[i] & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by the Java runtime; this fallback remains path-safe.
            return Integer.toHexString(value.hashCode());
        }
    }

    public static CachePaths prepareGameCache(File imageFsRoot, String identity) {
        if (imageFsRoot == null) return null;
        try {
            File gameDir = new File(new File(imageFsRoot, CACHE_ROOT), cacheKey(identity));
            File shaderDir = new File(gameDir, "shader");
            File stateDir = new File(gameDir, "state");
            if ((!shaderDir.isDirectory() && !shaderDir.mkdirs())
                    || (!stateDir.isDirectory() && !stateDir.mkdirs())) return null;
            return new CachePaths(shaderDir, stateDir);
        } catch (SecurityException e) {
            return null;
        }
    }

    public static boolean clearGameCache(File imageFsRoot, String identity) {
        if (imageFsRoot == null) return false;
        try {
            File cacheRoot = new File(imageFsRoot, CACHE_ROOT).getCanonicalFile();
            File target = new File(cacheRoot, cacheKey(identity)).getCanonicalFile();
            String allowedPrefix = cacheRoot.getPath() + File.separator;
            if (!target.getPath().startsWith(allowedPrefix)) return false;
            if (!target.exists()) return true;
            return deleteTreeWithoutFollowingLinks(target);
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    /** Returns -1 when the cache path cannot be inspected; symbolic links are never traversed. */
    public static long getGameCacheSizeBytes(File imageFsRoot, String identity) {
        if (imageFsRoot == null) return -1L;
        try {
            File cacheRoot = new File(imageFsRoot, CACHE_ROOT).getCanonicalFile();
            File target = new File(cacheRoot, cacheKey(identity)).getCanonicalFile();
            String allowedPrefix = cacheRoot.getPath() + File.separator;
            if (!target.getPath().startsWith(allowedPrefix)) return -1L;
            if (!target.exists()) return 0L;
            return measureTreeWithoutFollowingLinks(target);
        } catch (IOException | SecurityException e) {
            return -1L;
        }
    }

    private static long measureTreeWithoutFollowingLinks(File file) throws IOException {
        Path path = file.toPath();
        if (Files.isSymbolicLink(path)) return 0L;
        if (!file.isDirectory()) return file.isFile() ? file.length() : 0L;
        File[] children = file.listFiles();
        if (children == null) throw new IOException("Unable to list cache directory");
        long total = 0L;
        for (File child : children) {
            long size = measureTreeWithoutFollowingLinks(child);
            if (Long.MAX_VALUE - total < size) return Long.MAX_VALUE;
            total += size;
        }
        return total;
    }

    private static boolean deleteTreeWithoutFollowingLinks(File file) throws IOException {
        Path path = file.toPath();
        if (Files.isSymbolicLink(path)) return Files.deleteIfExists(path);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return false;
            for (File child : children) {
                if (!deleteTreeWithoutFollowingLinks(child)) return false;
            }
        }
        Files.deleteIfExists(path);
        return true;
    }

    public static final class CachePaths {
        public final File shader;
        public final File state;

        private CachePaths(File shader, File state) {
            this.shader = shader;
            this.state = state;
        }
    }
}
