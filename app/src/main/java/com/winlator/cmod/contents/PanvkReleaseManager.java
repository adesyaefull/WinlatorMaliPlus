package com.winlator.cmod.contents;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PanvkReleaseManager {
    private static final long MAX_DOWNLOAD_SIZE = 100L * 1024 * 1024;
    private static final Repository[] REPOSITORIES = {
            new Repository("zenithblue-oss", "panvk-kbase-android",
                    "Poco X6 Pro (Mali-G615); Lenovo Tab TB336FU (G57); Pixel 7/7a (G710); "
                            + "Pixel 8/9 (G715); Galaxy Tab S10 Ultra (G720). Latest RC retested on G615.",
                    "-adrenotools.zip"),
            new Repository("GunaCharanTeja", "panvk-kbase-android",
                    "OPPO CPH2763 — Mali-G615 MC2",
                    "-adrenotools.zip"),
            new Repository("FristOneRR", "FristOneRR-Panvk-Driver",
                    "Poco M6 Pro 4G, Galaxy A15/A17, Realme 10 4G, Infinix Note 50 Pro — Mali-G57 MC2",
                    "panvk-mali-g57-")
    };

    public interface ProgressListener {
        void onProgress(long currentBytes, long totalBytes);
    }

    public static final class ReleaseAsset {
        public final String owner;
        public final String repository;
        public final String releaseName;
        public final String tag;
        public final String publishedAt;
        public final String deviceSupport;
        public final String fileName;
        public final String downloadUrl;
        public final String sha256;
        public final long size;
        public final boolean prerelease;

        private ReleaseAsset(String owner, String repository, String releaseName, String tag,
                             String publishedAt, String deviceSupport, String fileName,
                             String downloadUrl, String sha256, long size, boolean prerelease) {
            this.owner = owner;
            this.repository = repository;
            this.releaseName = releaseName;
            this.tag = tag;
            this.publishedAt = publishedAt;
            this.deviceSupport = deviceSupport;
            this.fileName = fileName;
            this.downloadUrl = downloadUrl;
            this.sha256 = sha256;
            this.size = size;
            this.prerelease = prerelease;
        }
    }

    private static final class Repository {
        final String owner;
        final String name;
        final String deviceSupport;
        final String zipFilter;

        Repository(String owner, String name, String deviceSupport, String zipFilter) {
            this.owner = owner;
            this.name = name;
            this.deviceSupport = deviceSupport;
            this.zipFilter = zipFilter;
        }

        boolean accepts(String fileName) {
            String lowerName = fileName.toLowerCase(Locale.ROOT);
            if (!lowerName.endsWith(".zip")) return false;
            if (zipFilter.startsWith("panvk-")) return lowerName.startsWith(zipFilter);
            return lowerName.endsWith(zipFilter);
        }
    }

    private PanvkReleaseManager() {}

    public static List<ReleaseAsset> fetchReleases() throws IOException {
        ArrayList<ReleaseAsset> releases = new ArrayList<>();
        for (Repository repository : REPOSITORIES) {
            String endpoint = "https://api.github.com/repos/" + repository.owner + "/"
                    + repository.name + "/releases?per_page=100";
            JSONArray releaseArray;
            try {
                releaseArray = new JSONArray(readUrl(endpoint));
            } catch (JSONException e) {
                throw new IOException("Invalid GitHub releases response for " + repository.owner
                        + "/" + repository.name, e);
            }
            for (int i = 0; i < releaseArray.length(); i++) {
                JSONObject release = releaseArray.optJSONObject(i);
                if (release == null || release.optBoolean("draft", false)) continue;
                JSONArray assets = release.optJSONArray("assets");
                if (assets == null) continue;
                for (int assetIndex = 0; assetIndex < assets.length(); assetIndex++) {
                    JSONObject asset = assets.optJSONObject(assetIndex);
                    if (asset == null) continue;
                    String fileName = asset.optString("name", "").trim();
                    long size = asset.optLong("size", 0L);
                    String downloadUrl = asset.optString("browser_download_url", "").trim();
                    if (!repository.accepts(fileName) || size <= 0 || size > MAX_DOWNLOAD_SIZE
                            || !isAllowedAssetUrl(repository, downloadUrl)) continue;
                    String digest = asset.optString("digest", "");
                    String sha256 = digest.startsWith("sha256:")
                            ? digest.substring("sha256:".length()).toLowerCase(Locale.ROOT) : "";
                    releases.add(new ReleaseAsset(repository.owner, repository.name,
                            release.optString("name", release.optString("tag_name", "")),
                            release.optString("tag_name", ""),
                            release.optString("published_at", ""),
                            repository.deviceSupport, fileName, downloadUrl, sha256, size,
                            release.optBoolean("prerelease", false)));
                }
            }
        }
        return releases;
    }

    public static File download(Context context, ReleaseAsset asset, ProgressListener listener)
            throws IOException {
        HttpURLConnection connection = null;
        File destination = File.createTempFile("panvk-", ".zip", context.getCacheDir());
        boolean completed = false;
        try {
            URL url = new URL(asset.downloadUrl);
            if (!"https".equalsIgnoreCase(url.getProtocol())
                    || !"github.com".equalsIgnoreCase(url.getHost())) {
                throw new IOException("Unexpected PanVK download host");
            }
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent", "Winlator-Mali-Plus");
            connection.setRequestProperty("Accept", "application/octet-stream");
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub download HTTP " + responseCode);
            }
            long responseSize = connection.getContentLengthLong();
            if (responseSize > MAX_DOWNLOAD_SIZE || (responseSize > 0 && responseSize != asset.size)) {
                throw new IOException("PanVK ZIP size does not match the release");
            }

            MessageDigest digest = newSha256();
            long downloaded = 0;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[32768];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    downloaded += count;
                    if (downloaded > MAX_DOWNLOAD_SIZE || downloaded > asset.size) {
                        throw new IOException("PanVK ZIP exceeds its published size");
                    }
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                    if (listener != null) listener.onProgress(downloaded, asset.size);
                }
                output.getFD().sync();
            }
            if (downloaded != asset.size) throw new IOException("PanVK ZIP download was incomplete");
            if (!asset.sha256.isEmpty() && !asset.sha256.equals(toHex(digest.digest()))) {
                throw new IOException("PanVK ZIP SHA-256 verification failed");
            }
            completed = true;
            return destination;
        } finally {
            if (connection != null) connection.disconnect();
            if (!completed && destination.exists() && !destination.delete()) {
                throw new IOException("Unable to remove incomplete PanVK ZIP");
            }
        }
    }

    private static String readUrl(String address) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(25000);
            connection.setRequestProperty("User-Agent", "Winlator-Mali-Plus");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub release list HTTP " + responseCode);
            }
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                ByteArrayOutputStream response = new ByteArrayOutputStream();
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (response.size() + count > 8 * 1024 * 1024) {
                        throw new IOException("GitHub release list is too large");
                    }
                    response.write(buffer, 0, count);
                }
                return new String(response.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean isAllowedAssetUrl(Repository repository, String address) {
        try {
            URL url = new URL(address);
            return "https".equalsIgnoreCase(url.getProtocol())
                    && "github.com".equalsIgnoreCase(url.getHost())
                    && url.getPath().startsWith("/" + repository.owner + "/" + repository.name
                    + "/releases/download/");
        } catch (Exception e) {
            return false;
        }
    }

    private static MessageDigest newSha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }
}
