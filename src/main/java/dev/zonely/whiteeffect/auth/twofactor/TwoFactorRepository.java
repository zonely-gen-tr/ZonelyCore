package dev.zonely.whiteeffect.auth.twofactor;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.auth.AuthDataSource;
import dev.zonely.whiteeffect.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;

final class TwoFactorRepository {

    private final Database database;
    private final AuthDataSource.Columns columns;
    private final AuthDataSource.Tables tables;

    TwoFactorRepository(Database database, AuthDataSource dataSource) {
        this.database = database;
        this.columns = dataSource.getColumns();
        this.tables = dataSource.getTables();
    }

    Optional<String> findSecret(int userId) {
        String sql = "SELECT `secretKey` FROM `" + tables.twoFactorKeys() + "` WHERE `userid` = ? LIMIT 1";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String secret = rs.getString("secretKey");
                    if (secret != null && !secret.isEmpty()) {
                        return Optional.of(secret.trim());
                    }
                }
            }
        } catch (SQLException ex) {
            Core.getInstance().getLogger().log(Level.WARNING, "[Auth] Unable to read two-factor secret for user " + userId, ex);
        }
        return Optional.empty();
    }

    void upsertSecret(int userId, String secret) {
        String select = "SELECT `userid` FROM `" + tables.twoFactorKeys() + "` WHERE `userid` = ? LIMIT 1";
        try (Connection connection = database.getConnection();
             PreparedStatement selectStmt = connection.prepareStatement(select)) {
            selectStmt.setInt(1, userId);
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE `" + tables.twoFactorKeys() + "` SET `secretKey` = ? WHERE `userid` = ?")) {
                        update.setString(1, secret);
                        update.setInt(2, userId);
                        update.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO `" + tables.twoFactorKeys() + "` (`userid`, `secretKey`) VALUES (?, ?)")) {
                        insert.setInt(1, userId);
                        insert.setString(2, secret);
                        insert.executeUpdate();
                    }
                }
            }
        } catch (SQLException ex) {
            Core.getInstance().getLogger().log(Level.WARNING, "[Auth] Unable to persist two-factor secret for user " + userId, ex);
        }
    }

    void deleteSecret(int userId) {
        String sql = "DELETE FROM `" + tables.twoFactorKeys() + "` WHERE `userid` = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            Core.getInstance().getLogger().log(Level.WARNING, "[Auth] Unable to delete two-factor secret for user " + userId, ex);
        }
    }

    void setStatus(int userId, boolean enabled) {
        String sql = "UPDATE `" + columns.table() + "` SET `" + columns.twoFactorStatus() + "` = ? WHERE `" + columns.id() + "` = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, enabled ? 1 : 0);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            Core.getInstance().getLogger().log(Level.WARNING, "[Auth] Unable to update two-factor status for user " + userId, ex);
        }
    }
}
