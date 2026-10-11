package dev.zonely.whiteeffect.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.json.JSONArray;
import org.json.JSONObject;

public class GitHubReleaseClient {
    public static final String REPOSITORY = "zonely-gen-tr/ZonelyCore";
    public static final String LATEST_URL = "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
    private static final String ASSET_PREFIX = "https://github.com/" + REPOSITORY + "/releases/download/";
    private static final long MAX_JAR_BYTES = 100L * 1024 * 1024;
    private static final Pattern VERSION = Pattern.compile("[vV]?([0-9]+(?:\\.[0-9]+){0,3})");
    private static final Pattern VERSIONED_JAR = Pattern.compile("ZonelyCore-([0-9]+(?:\\.[0-9]+){0,3})\\.jar");

    public static final class Release {
        public final String version;
        public final String downloadUrl;
        public final long size;
        public final String sha256;

        Release(String version, String downloadUrl, long size, String sha256) {
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    public Release fetchLatest() throws IOException {
        return parseRelease(readText(LATEST_URL, 1024 * 1024));
    }

    static Release parseRelease(String json) throws IOException {
        try {
            JSONObject release = new JSONObject(json);
            if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false))
                throw new IOException("Only published stable releases can be installed.");
            String version = normalizeVersion(release.optString("tag_name", ""));
            JSONArray assets = release.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null || !"uploaded".equals(asset.optString("state", ""))) continue;
                    String name = asset.optString("name", "");
                    Matcher namedVersion = VERSIONED_JAR.matcher(name);
                    String candidate = version;
                    if (candidate == null && namedVersion.matches()) candidate = namedVersion.group(1);
                    if (candidate == null || !(name.equals("ZonelyCore.jar")
                            || name.equals("ZonelyCore-" + candidate + ".jar"))) continue;
                    String url = asset.optString("browser_download_url", "");
                    if (!url.startsWith(ASSET_PREFIX) || !new URL(url).getPath().endsWith("/" + name)) continue;
                    long size = asset.optLong("size", 0);
                    if (size <= 0 || size > MAX_JAR_BYTES) continue;
                    String digest = asset.optString("digest", "");
                    if ("null".equals(digest)) digest = "";
                    if (!digest.isEmpty() && !digest.matches("sha256:[a-fA-F0-9]{64}"))
                        throw new IOException("Invalid release asset checksum.");
                    return new Release(candidate, url, size,
                            digest.isEmpty() ? null : digest.substring(7).toLowerCase(Locale.ROOT));
                }
            }
            throw new IOException("The latest release has no matching ZonelyCore JAR asset.");
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Invalid GitHub release response.", ex);
        }
    }

    static String normalizeVersion(String version) {
        Matcher match = VERSION.matcher(version.trim());
        return match.matches() ? match.group(1) : null;
    }

    static boolean isNewer(String candidate, String current) throws IOException {
        String left = normalizeVersion(candidate);
        String right = normalizeVersion(current);
        if (left == null || right == null) throw new IOException("Unsupported plugin version format.");
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            BigInteger av = new BigInteger(i < a.length ? a[i] : "0");
            BigInteger bv = new BigInteger(i < b.length ? b[i] : "0");
            int comparison = av.compareTo(bv);
            if (comparison != 0) return comparison > 0;
        }
        return false;
    }

    public void download(Release release, File target) throws IOException {
        Path destination = target.toPath().toAbsolutePath();
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "ZonelyCore-", ".part");
        HttpURLConnection connection = null;
        try {
            connection = connect(release.downloadUrl);
            long declared = connection.getContentLengthLong();
            if (declared > MAX_JAR_BYTES || (declared >= 0 && declared != release.size))
                throw new IOException("Release download size does not match GitHub metadata.");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytes = 0;
            long deadline = System.nanoTime() + 120000000000L;
            try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    bytes += count;
                    if (bytes > MAX_JAR_BYTES || bytes > release.size || System.nanoTime() > deadline)
                        throw new IOException("Release download exceeded its size or time limit.");
                    output.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                }
            }
            if (bytes != release.size) throw new IOException("Incomplete release download.");
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", value & 255));
            if (release.sha256 != null && !release.sha256.equals(hash.toString()))
                throw new IOException("Release SHA-256 verification failed.");
            validateJar(temporary.toFile(), release.version);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 is unavailable.", ex);
        } finally {
            if (connection != null) connection.disconnect();
            Files.deleteIfExists(temporary);
        }
    }

    static void validateJar(File file, String version) throws IOException {
        try (ZipFile jar = new ZipFile(file)) {
            ZipEntry entry = jar.getEntry("plugin.yml");
            if (entry == null) throw new IOException("Release JAR has no plugin.yml.");
            String metadata;
            try (InputStream input = jar.getInputStream(entry)) {
                metadata = readBounded(input, 65536);
            }
            if (!matchesField(metadata, "name", "ZonelyCore") || !matchesField(metadata, "version", version))
                throw new IOException("Release JAR plugin name or version does not match the release.");
        }
    }

    private static boolean matchesField(String yaml, String field, String value) {
        return Pattern.compile("(?m)^" + field + ":\\s*[\"']?" + Pattern.quote(value)
                + "[\"']?\\s*(?:#.*)?$").matcher(yaml).find();
    }

    public String readText(String url, int limit) throws IOException {
        HttpURLConnection connection = connect(url);
        try (InputStream input = connection.getInputStream()) {
            return readBounded(input, limit);
        } finally {
            connection.disconnect();
        }
    }

    private static String readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > limit) throw new IOException("Response is too large.");
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    protected HttpURLConnection openConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    private HttpURLConnection connect(String address) throws IOException {
        URL url = new URL(address);
        for (int redirect = 0; redirect <= 5; redirect++) {
            if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("HTTPS is required.");
            HttpURLConnection connection = openConnection(url);
            try {
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "ZonelyCore-Updater");
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                int status = connection.getResponseCode();
                if (status == 200) return connection;
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Release redirect has no target.");
                    url = new URL(url, location);
                } else {
                    throw new IOException("GitHub request failed (HTTP " + status + ").");
                }
            } catch (IOException | RuntimeException ex) {
                connection.disconnect();
                throw ex;
            }
            connection.disconnect();
        }
        throw new IOException("Too many release download redirects.");
    }
}
