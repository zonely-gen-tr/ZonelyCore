package dev.zonely.whiteeffect.store;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.database.HikariDatabase;
import dev.zonely.whiteeffect.database.MySQLDatabase;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.sql.rowset.CachedRowSet;


public class LastCreditPlaceholderService {

    private static final int LIMIT = 10;
    private static final long REFRESH_INTERVAL_TICKS = 20L * 60; 

    private final Core plugin;
    private volatile List<LastCreditRecord> cache = Collections.emptyList();
    private volatile long lastUpdated = 0L;
    private BukkitTask refreshTask;

    public LastCreditPlaceholderService(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshSafely);
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::refreshSafely, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public LastCreditRecord getRecord(int position) {
        if (position < 1 || position > LIMIT) {
            return null;
        }
        List<LastCreditRecord> snapshot = cache;
        return position <= snapshot.size() ? snapshot.get(position - 1) : null;
    }

    public List<LastCreditRecord> getSnapshot() {
        return cache;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    private void refreshSafely() {
        final String sql = "SELECT lc.userid, ul.nick, lc.gain, lc.created_at " +
                "FROM lastcredits lc " +
                "JOIN userslist ul ON lc.userid = ul.id " +
                "ORDER BY lc.created_at DESC " +
                "LIMIT ?";

        try {
            Database db = Database.getInstance();

            CachedRowSet rowSet = null;
            if (db instanceof HikariDatabase) {
                rowSet = ((HikariDatabase) db).query(sql, LIMIT);
            } else if (db instanceof MySQLDatabase) {
                rowSet = ((MySQLDatabase) db).query(sql, LIMIT);
            }

            List<LastCreditRecord> latest = new ArrayList<>();

            if (rowSet != null) {
                rowSet.beforeFirst();
                while (rowSet.next()) {
                    Timestamp createdAt = coerceTimestamp(rowSet.getObject("created_at"));
                    if (createdAt == null) {
                        continue;
                    }
                    latest.add(new LastCreditRecord(
                            rowSet.getInt("userid"),
                            rowSet.getString("nick"),
                            rowSet.getLong("gain"),
                            createdAt
                    ));
                }
                try { rowSet.close(); } catch (SQLException ignore) {}
            } else if (!(db instanceof HikariDatabase) && !(db instanceof MySQLDatabase)) {
                try {
                    Connection conn = db.getConnection();
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, LIMIT);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                Timestamp createdAt = coerceTimestamp(rs.getObject("created_at"));
                                if (createdAt == null) {
                                    continue;
                                }
                                latest.add(new LastCreditRecord(
                                        rs.getInt("userid"),
                                        rs.getString("nick"),
                                        rs.getLong("gain"),
                                        createdAt
                                ));
                            }
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to refresh last credit cache (fallback path)", e);
                    return;
                }
            }

            cache = Collections.unmodifiableList(latest);
            lastUpdated = System.currentTimeMillis();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to refresh last credit cache", ex);
        }
    }

    public static class LastCreditRecord {
        private final int userId;
        private final String username;
        private final long amount;
        private final Timestamp createdAt;

        public LastCreditRecord(int userId, String username, long amount, Timestamp createdAt) {
            this.userId = userId;
            this.username = username;
            this.amount = amount;
            this.createdAt = createdAt;
        }

        public int getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public long getAmount() {
            return amount;
        }

        public Timestamp getCreatedAt() {
            return createdAt;
        }
    }

    private static Timestamp coerceTimestamp(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Timestamp) {
            return (Timestamp) raw;
        }
        if (raw instanceof java.sql.Date) {
            return new Timestamp(((java.sql.Date) raw).getTime());
        }
        if (raw instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) raw).getTime());
        }
        if (raw instanceof LocalDateTime) {
            return Timestamp.valueOf((LocalDateTime) raw);
        }
        if (raw instanceof Instant) {
            return Timestamp.from((Instant) raw);
        }
        if (raw instanceof String) {
            try {
                return Timestamp.valueOf((String) raw);
            } catch (IllegalArgumentException ignored) {
            }
        }
        Core instance = Core.getInstance();
        if (instance != null) {
            instance.getLogger().log(Level.WARNING, "Unsupported timestamp type returned for lastcredits.created_at: "
                    + raw.getClass().getName());
        }
        return null;
    }
}
