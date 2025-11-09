package dev.zonely.whiteeffect.auth.recovery;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.auth.AuthAccount;
import dev.zonely.whiteeffect.auth.AuthDataSource;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class AuthRecoveryService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneId.systemDefault());

    private final Database database;
    private final AuthDataSource.Tables tables;
    private final boolean passwordResetEnabled;
    private final boolean twoFactorRecoveryEnabled;
    private final long passwordResetExpiryMillis;
    private final long twoFactorExpiryMillis;
    private final String baseUrl;
    private final String passwordResetPath;
    private final String twoFactorLoginPath;

    public AuthRecoveryService(Database database, AuthDataSource dataSource, WConfig config) {
        this.database = database;
        this.tables = dataSource.getTables();

        ConfigurationSection siteSection = config.getSection("auth.site");
        this.baseUrl = normalizeBaseUrl(siteSection != null ? siteSection.getString("base-url", "") : "");
        this.passwordResetPath = normalizePath(siteSection != null ? siteSection.getString("password-reset-path", "/recovery") : "/recovery");
        this.twoFactorLoginPath = normalizePath(siteSection != null ? siteSection.getString("twofactor-login-path", "/login") : "/login");

        ConfigurationSection passwordSection = config.getSection("auth.password-reset");
        this.passwordResetEnabled = passwordSection == null || passwordSection.getBoolean("enabled", true);
        long passwordMinutes = passwordSection == null ? 60L : passwordSection.getLong("token-expiry-minutes", 60L);
        this.passwordResetExpiryMillis = Math.max(1L, passwordMinutes) * 60_000L;

        ConfigurationSection twoFactorSection = config.getSection("auth.two-factor");
        ConfigurationSection recoverySection = twoFactorSection != null ? twoFactorSection.getConfigurationSection("recovery") : null;
        this.twoFactorRecoveryEnabled = recoverySection == null || recoverySection.getBoolean("enabled", true);
        long twoFactorMinutes = recoverySection == null ? 60L : recoverySection.getLong("token-expiry-minutes", 60L);
        this.twoFactorExpiryMillis = Math.max(1L, twoFactorMinutes) * 60_000L;
    }

    public boolean isPasswordResetEnabled() {
        return passwordResetEnabled && !baseUrl.isEmpty();
    }

    public boolean isTwoFactorRecoveryEnabled() {
        return twoFactorRecoveryEnabled && !baseUrl.isEmpty();
    }

    public Optional<String> createPasswordReset(AuthAccount account, String ipAddress) {
        if (!isPasswordResetEnabled()) {
            return Optional.empty();
        }
        if (account == null || account.getId() == null) {
            return Optional.empty();
        }

        String token = generateToken();
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(passwordResetExpiryMillis);
        upsertToken(tables.passwordRecovery(), account.getId(), token, ipAddress, now, expiry);
        return Optional.of(buildUrl(passwordResetPath, account.getId(), token));
    }

    public Optional<String> createTwoFactorRecovery(AuthAccount account, String ipAddress) {
        if (!isTwoFactorRecoveryEnabled()) {
            return Optional.empty();
        }
        if (account == null || account.getId() == null) {
            return Optional.empty();
        }

        String token = generateToken();
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(twoFactorExpiryMillis);
        upsertToken(tables.twoFactorRecovery(), account.getId(), token, ipAddress, now, expiry);
        return Optional.of(buildUrl(twoFactorLoginPath, account.getId(), token));
    }

    private void upsertToken(String table, int userId, String token, String ipAddress, Instant createdAt, Instant expiryAt) {
        String select = "SELECT `userid` FROM `" + table + "` WHERE `userid` = ? LIMIT 1";
        try (Connection connection = database.getConnection();
             PreparedStatement selectPs = connection.prepareStatement(select)) {
            selectPs.setInt(1, userId);
            try (ResultSet rs = selectPs.executeQuery()) {
                if (rs.next()) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE `" + table + "` SET `token` = ?, `created_ip` = ?, `expiry_at` = ?, `created_at` = ? WHERE `userid` = ?")) {
                        update.setString(1, token);
                        update.setString(2, ipAddress);
                        update.setString(3, format(expiryAt));
                        update.setString(4, format(createdAt));
                        update.setInt(5, userId);
                        update.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO `" + table + "` (`userid`, `token`, `created_ip`, `expiry_at`, `created_at`) VALUES (?, ?, ?, ?, ?)")) {
                        insert.setInt(1, userId);
                        insert.setString(2, token);
                        insert.setString(3, ipAddress);
                        insert.setString(4, format(expiryAt));
                        insert.setString(5, format(createdAt));
                        insert.executeUpdate();
                    }
                }
            }
        } catch (SQLException ex) {
            Core.getInstance().getLogger().log(Level.WARNING, "[Auth] Failed to persist recovery token for user " + userId + " in table " + table, ex);
        }
    }

    private String buildUrl(String path, int userId, String token) {
        StringBuilder builder = new StringBuilder(baseUrl);
        if (!path.isEmpty()) {
            builder.append(path);
        }
        if (!path.endsWith("/")) {
            builder.append('/');
        }
        builder.append(userId).append('/').append(token);
        return builder.toString();
    }

    private static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String format(Instant instant) {
        return FORMATTER.format(instant);
    }

    private static String normalizeBaseUrl(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        String url = raw.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String normalizePath(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        String value = raw.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        return value;
    }
}
