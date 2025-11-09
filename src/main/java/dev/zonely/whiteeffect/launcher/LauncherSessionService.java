package dev.zonely.whiteeffect.launcher;

import dev.zonely.whiteeffect.Core;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.file.FileConfiguration;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public final class LauncherSessionService {

    private static final String DEFAULT_BASE_URL = "https://zonely.gen.tr";
    private static final String VERIFY_SUFFIX = "/Files/Api/launchercontrollers.php?endpoint=LauncherAuthAPI&zone=SessionConsumeController";

    private final Core plugin;
    private final boolean enabled;
    private final String endpoint;
    private final String apiToken;
    private final int timeoutMs;
    private final boolean failOpen;
    private final boolean logDenied;
    private final boolean allowLoopback;
    private final JSONParser parser = new JSONParser();
    private final Set<String> loggedDenials = ConcurrentHashMap.newKeySet();

    public LauncherSessionService(Core plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("modules.launcher-auth", false);
        String base = config.getString("launcher-auth.base-url", DEFAULT_BASE_URL);
        this.endpoint = buildEndpoint(base);
        this.apiToken = config.getString("launcher-auth.api-token", "").trim();
        this.timeoutMs = Math.max(1000, config.getInt("launcher-auth.timeout-ms", 4500));
        this.failOpen = config.getBoolean("launcher-auth.fail-open", true);
        this.logDenied = config.getBoolean("launcher-auth.log-denied", true);
        this.allowLoopback = config.getBoolean("launcher-auth.allow-loopback", false);
    }

    public boolean isEnabled() {
        return enabled && !endpoint.isEmpty();
    }

    public boolean isPlayerAuthorized(String nickname, String ipAddress) {
        if (!isEnabled()) {
            return true;
        }

        String trimmedName = nickname == null ? "" : nickname.trim();
        String trimmedIp = ipAddress == null ? "" : ipAddress.trim();

        if (allowLoopback && isLoopback(trimmedIp)) {
            return true;
        }

        if (trimmedName.isEmpty() || trimmedIp.isEmpty()) {
            return false;
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(buildUrl(trimmedName, trimmedIp));
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "ZonelyCore/" + plugin.getDescription().getVersion());
            if (!apiToken.isEmpty()) {
                connection.setRequestProperty("X-Zonely-Launcher-Token", apiToken);
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) {
                return failOpen;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                Object parsed = parser.parse(reader);
                if (parsed instanceof JSONObject) {
                    JSONObject json = (JSONObject) parsed;
                    Object successObj = json.get("success");
                    boolean success = !(successObj instanceof Boolean) || (Boolean) successObj;
                    if (!success) {
                        logDenied(trimmedName, trimmedIp, "service rejection");
                        return false;
                    }
                    Object authorizedObj = json.get("authorized");
                    boolean authorized = !(authorizedObj instanceof Boolean) || (Boolean) authorizedObj;
                    if (!authorized) {
                        logDenied(trimmedName, trimmedIp, "missing session");
                    } else {
                        loggedDenials.remove(trimmedName.toLowerCase(Locale.ROOT));
                    }
                    return authorized;
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "[LauncherAuth] Verification failed for " + trimmedName + ": " + ex.getMessage());
            return failOpen;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return failOpen;
    }

    private String buildUrl(String nickname, String ipAddress) throws IOException {
        StringBuilder builder = new StringBuilder(endpoint);
        builder.append(endpoint.contains("?") ? "&" : "?");
        builder.append("nickname=").append(URLEncoder.encode(nickname, StandardCharsets.UTF_8.name()));
        builder.append("&ip=").append(URLEncoder.encode(ipAddress, StandardCharsets.UTF_8.name()));
        if (!apiToken.isEmpty()) {
            builder.append("&token=").append(URLEncoder.encode(apiToken, StandardCharsets.UTF_8.name()));
        }
        return builder.toString();
    }

    private void logDenied(String nickname, String ip, String reason) {
        if (!logDenied) {
            return;
        }

        String key = nickname.toLowerCase(Locale.ROOT);
        if (loggedDenials.add(key)) {
            plugin.getLogger().info("[LauncherAuth] Denied connection for " + nickname + " (" + ip + ") - " + reason);
        }
    }

    private String buildEndpoint(String rawBase) {
        String candidate = rawBase == null ? "" : rawBase.trim();
        if (candidate.isEmpty()) {
            candidate = DEFAULT_BASE_URL;
        }

        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
            candidate = "https://" + candidate;
        }

        if (candidate.endsWith("/")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }

        return candidate + VERIFY_SUFFIX;
    }

    private static boolean isLoopback(String ip) {
        if (ip == null) {
            return false;
        }
        String normalized = ip.trim();
        return "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "0:0:0:0:0:0:0:1".equalsIgnoreCase(normalized);
    }
}
