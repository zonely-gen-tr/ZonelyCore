package dev.zonely.whiteeffect.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class GitHubReleaseClientTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String URL_PREFIX = "https://github.com/zonely-gen-tr/ZonelyCore/releases/download/v5.0.1/";

    @Test public void numericVersionComparisonPreventsDowngradesAndDecimalConcatenationErrors() throws Exception {
        assertTrue(GitHubReleaseClient.isNewer("v5.0.1", "5.0"));
        assertTrue(GitHubReleaseClient.isNewer("5.10.0", "5.9.99"));
        assertFalse(GitHubReleaseClient.isNewer("1.4", "5.0.1"));
        assertFalse(GitHubReleaseClient.isNewer("5.0.0", "5.0"));
        assertFalse(GitHubReleaseClient.isNewer("5.0.1", "5.0.1"));
        assertTrue(GitHubReleaseClient.isNewer("99999999999999.1", "6.0"));
        fails(() -> GitHubReleaseClient.isNewer("5.0.1-rc1", "5.0"));
    }

    @Test public void matchingUploadedJarAndGitHubDigestAreSelected() throws Exception {
        JSONObject release = metadata("v5.0.1", "ZonelyCore-5.0.1.jar");
        asset(release).put("digest", "sha256:" + repeat('a', 64));
        GitHubReleaseClient.Release selected = GitHubReleaseClient.parseRelease(release.toString());
        assertEquals("5.0.1", selected.version);
        assertEquals(URL_PREFIX + "ZonelyCore-5.0.1.jar", selected.downloadUrl);
        assertEquals(repeat('a', 64), selected.sha256);
    }

    @Test public void legacyUpdateTagUsesVersionedAssetAndNeverReleaseTitle() throws Exception {
        JSONObject release = metadata("Update", "ZonelyCore-1.4.jar");
        release.put("name", "Big Update v999.0");
        assertEquals("1.4", GitHubReleaseClient.parseRelease(release.toString()).version);
    }

    @Test public void stableCanonicalAssetNameIsSupported() throws Exception {
        assertEquals("5.0.1", GitHubReleaseClient.parseRelease(metadata("v5.0.1", "ZonelyCore.jar").toString()).version);
    }

    @Test public void draftPrereleaseWrongVersionSourcesAndMissingJarAreRejected() throws Exception {
        JSONObject draft = metadata("v5.0.1", "ZonelyCore-5.0.1.jar").put("draft", true);
        fails(() -> GitHubReleaseClient.parseRelease(draft.toString()));
        JSONObject prerelease = metadata("v5.0.1", "ZonelyCore-5.0.1.jar").put("prerelease", true);
        fails(() -> GitHubReleaseClient.parseRelease(prerelease.toString()));
        fails(() -> GitHubReleaseClient.parseRelease(metadata("v5.0.1", "ZonelyCore-5.0.jar").toString()));
        fails(() -> GitHubReleaseClient.parseRelease(metadata("v5.0.1", "ZonelyCore-5.0.1-sources.jar").toString()));
        fails(() -> GitHubReleaseClient.parseRelease("{\"tag_name\":\"v5.0.1\",\"assets\":[]}"));
    }

    @Test public void UntrustedAssetLinksAndInvalidSizesOrDigestsAreRejected() throws Exception {
        for (String link : new String[]{"https://example.com/ZonelyCore-5.0.1.jar",
                "https://github.com/other/repo/releases/download/v5.0.1/ZonelyCore-5.0.1.jar",
                "http://github.com/zonely-gen-tr/ZonelyCore/releases/download/v5.0.1/ZonelyCore-5.0.1.jar"}) {
            JSONObject release = metadata("v5.0.1", "ZonelyCore-5.0.1.jar");
            asset(release).put("browser_download_url", link);
            fails(() -> GitHubReleaseClient.parseRelease(release.toString()));
        }
        for (long size : new long[]{0, -1, 101L * 1024 * 1024}) {
            JSONObject release = metadata("v5.0.1", "ZonelyCore-5.0.1.jar");
            asset(release).put("size", size);
            fails(() -> GitHubReleaseClient.parseRelease(release.toString()));
        }
        JSONObject release = metadata("v5.0.1", "ZonelyCore-5.0.1.jar");
        asset(release).put("digest", "sha256:invalid");
        fails(() -> GitHubReleaseClient.parseRelease(release.toString()));
    }

    @Test public void downloadValidatesDigestAndJarThenReplacesStagedFile() throws Exception {
        byte[] jar = jar("ZonelyCore", "5.0.1");
        Path target = temporary.newFile("ZonelyCore-custom-name.jar").toPath();
        Files.write(target, new byte[]{1, 2, 3});
        new FakeClient(new Response(jar)).download(release(jar), target.toFile());
        assertArrayEquals(jar, Files.readAllBytes(target));
        assertEquals(1, temporary.getRoot().list().length);
    }

    @Test public void checksumFailurePreservesStagedFileAndCanRetry() throws Exception {
        byte[] jar = jar("ZonelyCore", "5.0.1");
        Path target = temporary.newFile("ZonelyCore.jar").toPath();
        byte[] previous = new byte[]{1, 2, 3};
        Files.write(target, previous);
        GitHubReleaseClient.Release invalid = new GitHubReleaseClient.Release("5.0.1", URL_PREFIX + "ZonelyCore.jar", jar.length, repeat('0', 64));
        fails(() -> new FakeClient(new Response(jar)).download(invalid, target.toFile()));
        assertArrayEquals(previous, Files.readAllBytes(target));
        assertEquals(1, temporary.getRoot().list().length);
        new FakeClient(new Response(jar)).download(release(jar), target.toFile());
        assertArrayEquals(jar, Files.readAllBytes(target));
    }

    @Test public void truncatedHtmlAndWrongPluginOrVersionNeverReplaceStagedJar() throws Exception {
        Path target = temporary.newFile("ZonelyCore.jar").toPath();
        byte[] previous = new byte[]{9};
        Files.write(target, previous);
        byte[] expected = jar("ZonelyCore", "5.0.1");
        fails(() -> new FakeClient(new Response(new byte[]{1})).download(release(expected), target.toFile()));
        for (byte[] invalid : new byte[][]{jar("OtherPlugin", "5.0.1"), jar("ZonelyCore", "5.0"), "<html>error</html>".getBytes(StandardCharsets.UTF_8)}) {
            fails(() -> new FakeClient(new Response(invalid)).download(release(invalid), target.toFile()));
            assertArrayEquals(previous, Files.readAllBytes(target));
            assertEquals(1, temporary.getRoot().list().length);
        }
    }

    @Test public void httpsRedirectsAreFollowedWithBoundedTimeouts() throws Exception {
        byte[] jar = jar("ZonelyCore", "5.0.1");
        Response redirect = new Response(new byte[0]);
        redirect.status = 302;
        redirect.location = "https://release-assets.githubusercontent.com/test.jar";
        Response body = new Response(jar);
        FakeClient client = new FakeClient(redirect, body);
        client.download(release(jar), temporary.newFile("ZonelyCore.jar"));
        assertTrue(redirect.disconnected);
        assertTrue(body.disconnected);
        assertEquals(10000, body.getConnectTimeout());
        assertEquals(15000, body.getReadTimeout());
        assertFalse(body.getInstanceFollowRedirects());
    }

    @Test public void rateLimitDowngradeRedirectAndRedirectLoopAreRejected() throws Exception {
        Response limited = new Response(new byte[0]);
        limited.status = 403;
        fails(() -> new FakeClient(limited).fetchLatest());
        assertTrue(limited.disconnected);
        Response insecure = new Response(new byte[0]);
        insecure.status = 302;
        insecure.location = "http://example.com/test.jar";
        fails(() -> new FakeClient(insecure).fetchLatest());
        Response[] loop = new Response[6];
        for (int i = 0; i < loop.length; i++) {
            loop[i] = new Response(new byte[0]);
            loop[i].status = 302;
            loop[i].location = GitHubReleaseClient.LATEST_URL;
        }
        fails(() -> new FakeClient(loop).fetchLatest());
    }

    @Test public void metadataResponsesAreBoundedAndMalformedJsonIsRejected() throws Exception {
        fails(() -> new FakeClient(new Response(new byte[1024 * 1024 + 1])).fetchLatest());
        fails(() -> GitHubReleaseClient.parseRelease("not json"));
    }

    private JSONObject metadata(String tag, String name) throws Exception {
        JSONObject asset = new JSONObject().put("name", name).put("size", 1234).put("state", "uploaded")
                .put("browser_download_url", URL_PREFIX + name);
        return new JSONObject().put("tag_name", tag).put("draft", false).put("prerelease", false)
                .put("assets", new JSONArray().put(asset));
    }

    private JSONObject asset(JSONObject release) throws Exception { return release.getJSONArray("assets").getJSONObject(0); }

    private GitHubReleaseClient.Release release(byte[] bytes) throws Exception {
        StringBuilder hash = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) hash.append(String.format("%02x", b & 255));
        return new GitHubReleaseClient.Release("5.0.1", URL_PREFIX + "ZonelyCore-5.0.1.jar", bytes.length, hash.toString());
    }

    private byte[] jar(String name, String version) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("plugin.yml"));
            zip.write(("name: " + name + "\nversion: '" + version + "'\nmain: example.Plugin\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static String repeat(char c, int count) { return new String(new char[count]).replace('\0', c); }

    private interface IOAction { void run() throws Exception; }
    private void fails(IOAction action) throws Exception {
        try { action.run(); fail("Expected IOException"); } catch (IOException expected) { }
    }

    private static class FakeClient extends GitHubReleaseClient {
        final Deque<Response> responses = new ArrayDeque<>();
        FakeClient(Response... responses) { for (Response response : responses) this.responses.add(response); }
        @Override protected HttpURLConnection openConnection(URL url) throws IOException {
            if (responses.isEmpty()) throw new IOException("No more mock responses.");
            return responses.removeFirst();
        }
    }

    private static class Response extends HttpURLConnection {
        final byte[] bytes;
        int status = 200;
        String location;
        boolean disconnected;
        Response(byte[] bytes) throws Exception { super(new URL(GitHubReleaseClient.LATEST_URL)); this.bytes = bytes; }
        @Override public int getResponseCode() { return status; }
        @Override public String getHeaderField(String name) { return "Location".equals(name) ? location : null; }
        @Override public long getContentLengthLong() { return bytes.length; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
}
