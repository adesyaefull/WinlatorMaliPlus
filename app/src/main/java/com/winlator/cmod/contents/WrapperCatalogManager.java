package com.winlator.cmod.contents;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.core.StreamUtils;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class WrapperCatalogManager {
    public static final String CATALOG_URL =
            "https://raw.githubusercontent.com/The412Banner/winlator-contents/main/wrappers.json";
    private static final String TAG = "WrapperCatalogManager";
    private static final String CATALOG_CACHE = "wrapper_catalog.json";
    private static final String WRAPPER_DIR = "graphics_driver/catalog";
    private static final String LEGACY_WRAPPER_DIR = "graphics_driver";
    private static final Pattern SAFE_ID = Pattern.compile("[a-zA-Z0-9._-]+");
    private static final long MAX_UNCOMPRESSED_SIZE = 512L * 1024 * 1024;

    public static final class Entry {
        public final String id;
        public final String name;
        public final String description;
        public final String author;
        public final String license;
        public final String url;
        public final long size;
        public final String checksum;
        public final List<String> gpuTargets;

        private Entry(String id, String name, String description, String author, String license,
                      String url, long size, String checksum, List<String> gpuTargets) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.author = author;
            this.license = license;
            this.url = url;
            this.size = size;
            this.checksum = checksum;
            this.gpuTargets = gpuTargets;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private WrapperCatalogManager() {}

    public static List<Entry> fetchCatalog(Context context) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(CATALOG_URL).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", "Winlator-Mali-Plus");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Wrapper catalog HTTP " + connection.getResponseCode());
            }
            String json;
            try (InputStream input = connection.getInputStream()) {
                json = new String(StreamUtils.copyToByteArray(input), java.nio.charset.StandardCharsets.UTF_8);
            }
            List<Entry> entries = parseCatalog(json);
            if (entries.isEmpty()) throw new IOException("Bannerlator wrapper catalog is empty or invalid");
            File cache = new File(context.getFilesDir(), CATALOG_CACHE);
            try (FileOutputStream output = new FileOutputStream(cache)) {
                output.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            return entries;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static List<Entry> getCachedCatalog(Context context) {
        File cache = new File(context.getFilesDir(), CATALOG_CACHE);
        if (!cache.isFile()) return Collections.emptyList();
        try (InputStream input = new FileInputStream(cache)) {
            return parseCatalog(new String(StreamUtils.copyToByteArray(input),
                    java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "Unable to read cached wrapper catalog", e);
            return Collections.emptyList();
        }
    }

    private static List<Entry> parseCatalog(String json) throws IOException {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray wrappers = root.optJSONArray("wrappers");
            if (wrappers == null) throw new IOException("Catalog does not contain wrappers");
            ArrayList<Entry> entries = new ArrayList<>(wrappers.length());
            for (int i = 0; i < wrappers.length(); i++) {
                JSONObject item = wrappers.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "").trim();
                String url = item.optString("url", "").trim();
                if (!SAFE_ID.matcher(id).matches() || !isAllowedDownloadUrl(url)) continue;
                String name = item.optString("name", id).trim();
                if (name.isEmpty()) name = id;
                ArrayList<String> gpuTargets = new ArrayList<>();
                JSONArray targets = item.optJSONArray("gpuTargets");
                if (targets != null) {
                    for (int targetIndex = 0; targetIndex < targets.length(); targetIndex++) {
                        String target = targets.optString(targetIndex, "").trim().toLowerCase(Locale.ROOT);
                        if (!target.isEmpty()) gpuTargets.add(target);
                    }
                }
                if (gpuTargets.isEmpty()) gpuTargets.add("all");
                entries.add(new Entry(id, name, item.optString("description", ""),
                        item.optString("author", ""), item.optString("license", ""), url,
                        parseLong(item.optString("file_size", "")),
                        item.optString("file_checksum", "").trim().toUpperCase(Locale.ROOT),
                        Collections.unmodifiableList(gpuTargets)));
            }
            return entries;
        } catch (org.json.JSONException e) {
            throw new IOException("Invalid Bannerlator wrapper catalog", e);
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static boolean isAllowedDownloadUrl(String value) {
        try {
            URL url = new URL(value);
            return "https".equalsIgnoreCase(url.getProtocol())
                    && "github.com".equalsIgnoreCase(url.getHost())
                    && url.getPath().contains("/The412Banner/winlator-contents/releases/download/");
        } catch (Exception e) {
            return false;
        }
    }

    public static File getInstalledWrapperFile(Context context, String id) {
        if (id == null || !SAFE_ID.matcher(id).matches()) return null;
        File driverDirectory = new File(context.getFilesDir(), LEGACY_WRAPPER_DIR);
        File catalogFile = new File(new File(driverDirectory, "catalog"), id + ".tzst");
        if (catalogFile.isFile()) return catalogFile;
        File directFile = new File(driverDirectory, id + ".tzst");
        if (directFile.isFile()) return directFile;

        File[] directories = driverDirectory.listFiles(File::isDirectory);
        if (directories != null) {
            for (File directory : directories) {
                File wrapperFile = new File(directory, id + ".tzst");
                if (wrapperFile.isFile()) return wrapperFile;
            }
        }
        return null;
    }

    public static List<Entry> getInstalledWrappers(Context context) {
        ArrayList<Entry> installed = new ArrayList<>();
        List<Entry> catalog = getCachedCatalog(context);
        java.util.HashSet<String> installedIds = new java.util.HashSet<>();
        for (Entry entry : catalog) {
            if (getInstalledWrapperFile(context, entry.id) != null) {
                installed.add(entry);
                installedIds.add(entry.id);
            }
        }

        File driverDirectory = new File(context.getFilesDir(), LEGACY_WRAPPER_DIR);
        appendUncataloguedWrappers(driverDirectory, installedIds, installed);
        File[] directories = driverDirectory.listFiles(File::isDirectory);
        if (directories != null) {
            for (File directory : directories) {
                appendUncataloguedWrappers(directory, installedIds, installed);
            }
        }
        return installed;
    }

    private static void appendUncataloguedWrappers(File directory, java.util.Set<String> installedIds,
                                                    List<Entry> installed) {
        File[] archives = directory.listFiles((dir, name) -> name.endsWith(".tzst")
                && SAFE_ID.matcher(name.substring(0, name.length() - 5)).matches());
        if (archives == null) return;
        for (File archive : archives) {
            String id = archive.getName().substring(0, archive.getName().length() - 5);
            if ("wrapper".equals(id) || "leegao_bcn".equals(id)
                    || "gladio-1.1".equals(id) || "extra_libs".equals(id)) continue;
            if (installedIds.add(id)) {
                installed.add(new Entry(id, id, "", "", "", "", archive.length(), "",
                        Collections.singletonList("all")));
            }
        }
    }

    public static String getInstalledWrapperName(Context context, String id) {
        if ("apex-wrapper".equals(id)) {
            return "Apex-Wrapper";
        }
        for (Entry entry : getCachedCatalog(context)) {
            if (entry.id.equals(id)) return entry.name;
        }
        return id == null || id.isEmpty() ? "Apex-Wrapper" : id;
    }

    public static void downloadWrapper(Context context, Entry entry) throws IOException {
        if (entry == null || !SAFE_ID.matcher(entry.id).matches()
                || !isAllowedDownloadUrl(entry.url)) {
            throw new IOException("Invalid wrapper catalog entry");
        }
        File directory = new File(context.getFilesDir(), WRAPPER_DIR);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Unable to create wrapper download directory");
        }
        File destination = new File(directory, entry.id + ".tzst");
        File temporary = new File(directory, entry.id + ".tzst.download");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(entry.url).openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent", "Winlator-Mali-Plus");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Wrapper download HTTP " + connection.getResponseCode());
            }

            MessageDigest digest = newDigest();
            long downloaded = 0;
            try (InputStream input = new DigestInputStream(
                    new BufferedInputStream(connection.getInputStream()), digest);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    downloaded += count;
                    if (downloaded > 512L * 1024 * 1024) {
                        throw new IOException("Wrapper archive exceeds 512 MB");
                    }
                }
                output.getFD().sync();
            }

            if (entry.size > 0 && downloaded != entry.size) {
                throw new IOException("Wrapper size mismatch: expected " + entry.size + ", got " + downloaded);
            }
            String actualChecksum = toHex(digest.digest());
            if (!entry.checksum.isEmpty() && !entry.checksum.equalsIgnoreCase(actualChecksum)) {
                throw new IOException("Wrapper checksum mismatch");
            }
            validateArchive(temporary);
            try {
                java.nio.file.Files.move(temporary.toPath(), destination.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(temporary.toPath(), destination.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (connection != null) connection.disconnect();
            if (temporary.exists() && !temporary.delete()) {
                Log.w(TAG, "Unable to remove incomplete download " + temporary);
            }
        }
    }

    private static MessageDigest newDigest() throws IOException {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("MD5 checksum support unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format(Locale.ROOT, "%02X", item & 0xFF));
        return value.toString();
    }

    private static void validateArchive(File archive) throws IOException {
        boolean hasWrapper = false;
        long totalUncompressedSize = 0;
        try (InputStream compressed = new ZstdCompressorInputStream(
                new BufferedInputStream(new FileInputStream(archive)));
             TarArchiveInputStream tar = new TarArchiveInputStream(compressed)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
                    throw new IOException("Wrapper contains an absolute archive path");
                }
                for (String segment : name.split("/")) {
                    if ("..".equals(segment)) throw new IOException("Wrapper contains a parent path");
                }
                if (entry.getSize() > 0) {
                    totalUncompressedSize += entry.getSize();
                    if (totalUncompressedSize > MAX_UNCOMPRESSED_SIZE) {
                        throw new IOException("Wrapper archive expands beyond 512 MB");
                    }
                }
                if (entry.isLink()) {
                    throw new IOException("Wrapper contains an unsupported hard link");
                }
                if (entry.isSymbolicLink()) {
                    String linkTarget = entry.getLinkName().replace('\\', '/');
                    if (linkTarget.startsWith("/") || linkTarget.matches("^[A-Za-z]:.*")) {
                        throw new IOException("Wrapper contains an absolute symbolic link");
                    }
                    java.nio.file.Path parent = java.nio.file.Paths.get(name).getParent();
                    java.nio.file.Path resolvedTarget = (parent == null
                            ? java.nio.file.Paths.get(linkTarget)
                            : parent.resolve(linkTarget)).normalize();
                    if (resolvedTarget.startsWith("..")) {
                        throw new IOException("Wrapper contains a symbolic link outside the archive");
                    }
                }
                if (name.equals("usr/lib/libvulkan_wrapper.so")) hasWrapper = true;
            }
        } catch (IOException e) {
            throw new IOException("Wrapper archive is invalid: " + e.getMessage(), e);
        }
        if (!hasWrapper) throw new IOException("Archive does not contain usr/lib/libvulkan_wrapper.so");
    }
}
