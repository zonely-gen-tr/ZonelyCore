package dev.zonely.whiteeffect.launcher;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.nms.NMS;
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
import org.bukkit.entity.Player;
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

    public AuthorizationResult authorizePlayer(String nickname, String ipAddress) {
        if (!isEnabled()) {
            return AuthorizationResult.allowed(null);
        }

        String trimmedName = nickname == null ? "" : nickname.trim();
        String trimmedIp = ipAddress == null ? "" : ipAddress.trim();

        if (allowLoopback && isLoopback(trimmedIp)) {
            return AuthorizationResult.allowed(null);
        }

        if (trimmedName.isEmpty() || trimmedIp.isEmpty()) {
            return AuthorizationResult.denied();
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
                return failOpen ? AuthorizationResult.allowed(null) : AuthorizationResult.denied();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                Object parsed = parser.parse(reader);
                if (parsed instanceof JSONObject) {
                    JSONObject json = (JSONObject) parsed;
                    boolean success = asBoolean(json.get("success"), true);
                    if (!success) {
                        logDenied(trimmedName, trimmedIp, "service rejection");
                        return AuthorizationResult.denied();
                    }

                    boolean authorized = asBoolean(json.get("authorized"), false);
                    SkinPayload skin = SkinPayload.fromJson(json.get("skin"));
                    if (!authorized) {
                        logDenied(trimmedName, trimmedIp, "missing session");
                        return AuthorizationResult.of(false, null);
                    }

                    loggedDenials.remove(trimmedName.toLowerCase(Locale.ROOT));
                    return AuthorizationResult.of(true, skin);
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "[LauncherAuth] Verification failed for " + trimmedName + ": " + ex.getMessage());
            return failOpen ? AuthorizationResult.allowed(null) : AuthorizationResult.denied();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return failOpen ? AuthorizationResult.allowed(null) : AuthorizationResult.denied();
    }

    public void applySkin(Player player, SkinPayload payload) {
        if (player == null || payload == null || !payload.isValid()) {
            return;
        }

        NMS.setValueAndSignature(player, payload.value(), payload.signature());
        NMS.refreshPlayer(player);
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

    private boolean asBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String str = (String) value;
            return "true".equalsIgnoreCase(str) || "1".equals(str);
        }
        return defaultValue;
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

    public static final class AuthorizationResult {
        private final boolean authorized;
        private final SkinPayload skin;

        private AuthorizationResult(boolean authorized, SkinPayload skin) {
            this.authorized = authorized;
            this.skin = skin;
        }

        public static AuthorizationResult of(boolean authorized, SkinPayload skin) {
            return new AuthorizationResult(authorized, skin);
        }

        public static AuthorizationResult allowed(SkinPayload skin) {
            return new AuthorizationResult(true, skin);
        }

        public static AuthorizationResult denied() {
            return new AuthorizationResult(false, null);
        }

        public boolean isAuthorized() {
            return authorized;
        }

        public SkinPayload skin() {
            return skin;
        }
    }

    public static final class SkinPayload {
        private final String value;
        private final String signature;
        private final String model;
        private final String uuid;
        private final String url;

        private SkinPayload(String value, String signature, String model, String uuid, String url) {
            this.value = value;
            this.signature = signature;
            this.model = model;
            this.uuid = uuid;
            this.url = url;
        }

        public static SkinPayload fromJson(Object source) {
            if (!(source instanceof JSONObject)) {
                return null;
            }
            JSONObject json = (JSONObject) source;

            Object valueObj = json.get("value");
            String value = valueObj instanceof String ? (String) valueObj : null;
            Object signatureObj = json.get("signature");
            String signature = signatureObj instanceof String ? (String) signatureObj : null;
            if (value == null || signature == null) {
                return null;
            }

            Object modelObj = json.get("model");
            String model = modelObj instanceof String ? (String) modelObj : "classic";
            Object uuidObj = json.get("uuid");
            String uuid = uuidObj instanceof String ? (String) uuidObj : null;
            Object urlObj = json.get("url");
            String url = urlObj instanceof String ? (String) urlObj : null;
            return new SkinPayload(value, signature, model, uuid, url);
        }

        public boolean isValid() {
            return value != null && !value.isEmpty() && signature != null && !signature.isEmpty();
        }

        public String value() {
            return value;
        }

        public String signature() {
            return signature;
        }

        public String model() {
            return model;
        }

        public String uuid() {
            return uuid;
        }

        public String url() {
            return url;
        }
    }
}
