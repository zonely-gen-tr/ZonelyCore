package dev.zonely.whiteeffect.cash;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.logging.Level;

public final class CreditRepository {

    private CreditRepository() {
    }

    public static OptionalInt findUserId(String rawNick) {
        String nick = normalize(rawNick);
        if (nick.isEmpty()) {
            return OptionalInt.empty();
        }

        Database database = Database.getInstance();
        if (database == null) {
            logSevere("Database instance is null; cannot resolve user id.", null);
            return OptionalInt.empty();
        }

        try (Connection conn = database.getConnection()) {
            if (conn == null) {
                logSevere("Failed to obtain SQL connection; cannot resolve user id.", null);
                return OptionalInt.empty();
            }

            Integer id = queryUserId(conn, nick, false);
            if (id != null) {
                return OptionalInt.of(id);
            }

            id = queryUserId(conn, nick, true);
            return id != null ? OptionalInt.of(id) : OptionalInt.empty();
        } catch (SQLException ex) {
            logSevere("Error fetching user id for '" + nick + '\'', ex);
            return OptionalInt.empty();
        }
    }

    public static OptionalLong loadCredit(int userId) {
        Database database = Database.getInstance();
        if (database == null) {
            logSevere("Database instance is null; cannot load credit.", null);
            return OptionalLong.empty();
        }

        try (Connection conn = database.getConnection();
                PreparedStatement ps = conn != null
                        ? conn.prepareStatement("SELECT credit FROM userslist WHERE id = ?")
                        : null) {
            if (ps == null) {
                logSevere("Failed to obtain SQL statement; cannot load credit.", null);
                return OptionalLong.empty();
            }

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return OptionalLong.of(rs.getLong("credit"));
                }
            }
        } catch (SQLException ex) {
            logSevere("Error fetching credit for user id " + userId, ex);
        }
        return OptionalLong.empty();
    }

    public static OptionalLong loadCredit(String rawNick) {
        OptionalInt userId = findUserId(rawNick);
        return userId.isPresent() ? loadCredit(userId.getAsInt()) : OptionalLong.empty();
    }

    public static boolean updateCredit(int userId, long newCredit) {
        Database database = Database.getInstance();
        if (database == null) {
            logSevere("Database instance is null; cannot update credit.", null);
            return false;
        }

        try (Connection conn = database.getConnection();
                PreparedStatement ps = conn != null
                        ? conn.prepareStatement("UPDATE userslist SET credit = ? WHERE id = ?")
                        : null) {
            if (ps == null) {
                logSevere("Failed to obtain SQL statement; cannot update credit.", null);
                return false;
            }

            ps.setLong(1, newCredit);
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            logSevere("Error updating credit for user id " + userId, ex);
            return false;
        }
    }

    private static Integer queryUserId(Connection conn, String nick, boolean ignoreCase) throws SQLException {
        String sql = ignoreCase
                ? "SELECT id FROM userslist WHERE LOWER(nick) = LOWER(?) LIMIT 1"
                : "SELECT id FROM userslist WHERE nick = ? LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nick);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("id") : null;
            }
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static void logSevere(String message, Throwable throwable) {
        Core core = Core.getInstance();
        if (core != null) {
            if (throwable == null) {
                core.getLogger().severe(message);
            } else {
                core.getLogger().log(Level.SEVERE, message, throwable);
            }
        }
    }
}
