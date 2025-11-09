package dev.zonely.whiteeffect.cash;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.player.Profile;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CashManager {

    private static final ConcurrentMap<String, Long> CACHE = new ConcurrentHashMap<>();

    private CashManager() {}

    public static void cacheCredit(String playerName, long amount) {
        CACHE.put(playerName, amount);
    }

    public static boolean isCached(String playerName) {
        return playerName != null && CACHE.containsKey(playerName);
    }

    public static void invalidateCache(String playerName) {
        if (playerName != null) {
            CACHE.remove(playerName);
        }
    }

    public static long loadCashFromDB(String playerName) {
        try {
            return fetchCreditFromDB(playerName);
        } catch (SQLException ex) {
            Core.getInstance().getLogger().severe("loadCashFromDB error: " + ex.getMessage());
            return 0L;
        }
    }

    public static long getCash(String playerName) {
        if (playerName == null) {
            return 0L;
        }
        Long cached = CACHE.get(playerName);
        if (cached != null) {
            return cached;
        }
        long loaded = loadCashFromDB(playerName);
        CACHE.put(playerName, loaded);
        return loaded;
    }

    public static long getCash(Profile profile) {
        return profile == null ? 0L : getCash(profile.getName());
    }

    public static long getCash(Player player) {
        return getCash(player.getName());
    }

    public static void setCash(String playerName, long amount) throws CashException {
        try {
            writeCreditToDB(playerName, amount);
            cacheCredit(playerName, amount);
        } catch (SQLException e) {
            throw new CashException("DB hatasi: " + e.getMessage());
        }
    }

    public static void setCash(Profile profile, long amount) throws CashException {
        if (profile == null) {
            throw new CashException("Player profile required.");
        }
        setCash(profile.getName(), amount);
    }

    public static void setCash(Player player, long amount) throws CashException {
        setCash(player.getName(), amount);
    }

    public static long addCash(String playerName, long amount) throws CashException {
        long current = getCash(playerName);
        long updated = current + amount;
        setCash(playerName, updated);
        return updated;
    }

    public static long addCash(Profile profile, long amount) throws CashException {
        return addCash(profile.getName(), amount);
    }

    public static long addCash(Player player, long amount) throws CashException {
        return addCash(player.getName(), amount);
    }

    public static long removeCash(String playerName, long amount) throws CashException {
        long current = getCash(playerName);
        if (current < amount) {
            throw new CashException("Insufficient balance.");
        }
        long updated = current - amount;
        setCash(playerName, updated);
        return updated;
    }

    public static long removeCash(Profile profile, long amount) throws CashException {
        return removeCash(profile.getName(), amount);
    }

    public static long removeCash(Player player, long amount) throws CashException {
        return removeCash(player.getName(), amount);
    }

    private static long fetchCreditFromDB(String playerName) throws SQLException {
        try (Connection conn = Database.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT credit FROM userslist WHERE nick = ? LIMIT 1"
             )) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("credit") : 0L;
            }
        }
    }

    private static void writeCreditToDB(String playerName, long newCredit) throws SQLException {
        try (Connection conn = Database.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE userslist SET credit = ? WHERE nick = ?"
             )) {
            ps.setLong(1, newCredit);
            ps.setString(2, playerName);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Credit update failed for " + playerName);
            }
        }
    }
}
