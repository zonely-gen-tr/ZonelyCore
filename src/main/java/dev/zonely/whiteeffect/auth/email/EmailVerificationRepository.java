package dev.zonely.whiteeffect.auth.email;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.auth.AuthDataSource;
import dev.zonely.whiteeffect.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;

public final class EmailVerificationRepository {

    private final Database database;
    private final AuthDataSource.Tables tables;

    public EmailVerificationRepository(Database database, AuthDataSource dataSource) {
        this.database = database;
        this.tables = dataSource.getTables();
    }

    public Optional<EmailVerificationRecord> findByUserId(int userId) {
        String sql = "SELECT `token`, `verified`, `created_at` FROM `" + tables.emailVerification() + "` WHERE `userid` = ? LIMIT 1";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String token = rs.getString("token");
                    boolean verified = rs.getInt("verified") == 1;
                    long createdAt = 0L;
                    try {
                        createdAt = rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").getTime() : 0L;
                    } catch (SQLException ignored) {
                    }
                    return Optional.of(new EmailVerificationRecord(token, verified, createdAt));
                }
            }
        } catch (SQLException ex) {
            Core.getInstance().getLogger().log(Level.WARNING, "[Auth] Failed to read email verification status for user " + userId, ex);
        }
        return Optional.empty();
    }

    public EmailVerificationRecord upsertToken(int userId, String token) {
        String sql = "INSERT INTO `" + tables.emailVerification() + "` (`userid`, `token`, `created_at`, `verified`) VALUES (?, ?, NOW(), 0) "
                + "ON DUPLICATE KEY UPDATE `token` = VALUES(`token`), `created_at` = VALUES(`created_at`), `verified` = 0";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, token);
            ps.executeUpdate();
        } catch (SQLException ex) {
            Core.getInstance().getLogger().log(Level.WARNING, "[Auth] Failed to upsert email verification token for user " + userId, ex);
        }
        return new EmailVerificationRecord(token, false, System.currentTimeMillis());
    }

    public void markVerified(int userId) {
        String sql = "UPDATE `" + tables.emailVerification() + "` SET `verified` = 1 WHERE `userid` = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            Core.getInstance().getLogger().log(Level.WARNING, "[Auth] Failed to mark email verification as verified for user " + userId, ex);
        }
    }

    public static final class EmailVerificationRecord {
        private final String token;
        private final boolean verified;
        private final long createdAt;

        EmailVerificationRecord(String token, boolean verified, long createdAt) {
            this.token = token == null ? "" : token;
            this.verified = verified;
            this.createdAt = createdAt;
        }

        String token() {
            return token;
        }

        boolean verified() {
            return verified;
        }

        long createdAt() {
            return createdAt;
        }
    }
}
