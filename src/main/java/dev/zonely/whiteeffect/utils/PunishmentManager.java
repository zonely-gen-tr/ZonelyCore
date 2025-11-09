package dev.zonely.whiteeffect.utils;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date; 
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PunishmentManager {

    public enum BanType {
        NORMAL, IP, HWID
    }

    public enum MuteType {
        NORMAL, IP, HWID
    }

    public enum WarningType {
        NORMAL, IP, HWID
    }

    public static class PunishmentEntry {
        private final String date;
        private final String type;
        private final String duration;
        private final String reason;
        private final String executor;

        public PunishmentEntry(String date, String type, String duration, String reason, String executor) {
            this.date = date;
            this.type = type;
            this.duration = duration;
            this.reason = reason;
            this.executor = executor;
        }

        public String getDate() {
            return date;
        }

        public String getType() {
            return type;
        }

        public String getDuration() {
            return duration;
        }

        public String getReason() {
            return reason;
        }

        public String getExecutor() {
            return executor;
        }
    }

    private final Map<String, String> lastKnownIps = new ConcurrentHashMap<>();
    private final Map<String, Long> mutedUntil = new ConcurrentHashMap<>();

    private Connection connection;

    public PunishmentManager() {
        try {
            this.connection = Database.getInstance().getConnection();
            if (this.connection == null || this.connection.isClosed()) {
                throw new SQLException("Database connection is null or closed.");
            }

            try (Statement stmt = this.connection.createStatement()) {
                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS punishments (" +
                                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                                "  target VARCHAR(255) NOT NULL," +
                                "  type ENUM('BAN','IP_BAN','HWID_BAN') NOT NULL," +
                                "  duration VARCHAR(50) NULL," +
                                "  reason TEXT NULL," +
                                "  executor VARCHAR(255) NOT NULL," +
                                "  date DATETIME NOT NULL" +
                                ") ENGINE=InnoDB CHARSET=utf8;");

                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS mutes (" +
                                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                                "  target VARCHAR(255) NOT NULL," +
                                "  type ENUM('MUTE','IP_MUTE','HWID_MUTE') NOT NULL," +
                                "  duration VARCHAR(50) NULL," +
                                "  reason TEXT NULL," +
                                "  executor VARCHAR(255) NOT NULL," +
                                "  date DATETIME NOT NULL" +
                                ") ENGINE=InnoDB CHARSET=utf8;");

                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS warnings (" +
                                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                                "  target VARCHAR(255) NOT NULL," +
                                "  type ENUM('WARNING','IP_WARNING','HWID_WARNING') NOT NULL," +
                                "  reason TEXT NULL," +
                                "  executor VARCHAR(255) NOT NULL," +
                                "  date DATETIME NOT NULL" +
                                ") ENGINE=InnoDB CHARSET=utf8;");

                stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS player_ips (" +
                                "  player VARCHAR(255) NOT NULL," +
                                "  ip VARCHAR(45) NOT NULL," +
                                "  updated DATETIME NOT NULL," +
                                "  PRIMARY KEY (player)" +
                                ") ENGINE=InnoDB CHARSET=utf8;");
            }

            Bukkit.getScheduler().runTaskAsynchronously(Core.getInstance(), () -> {
                try {
                    reloadMuteCache();
                    reapplyActiveBans();
                } catch (Exception ex) {
                    Bukkit.getLogger().severe("Failed to refresh punishment caches: " + ex.getMessage());
                }
            });
        } catch (SQLException e) {
            Bukkit.getLogger().severe("PunishmentManager MySQL connection error: " + e.getMessage());
        }
    }

    public void recordLastIp(String playerName, String ip) {
        if (playerName == null || ip == null) {
            return;
        }
        String trimmedIp = ip.trim();
        if (trimmedIp.isEmpty()) {
            return;
        }
        String nameKey = playerName.toLowerCase(Locale.ROOT);
        lastKnownIps.put(nameKey, trimmedIp);

        Runnable task = () -> {
            String sql = "INSERT INTO player_ips (player, ip, updated) VALUES (?, ?, NOW()) "
                    + "ON DUPLICATE KEY UPDATE ip = VALUES(ip), updated = NOW()";
            try (Connection conn = Database.getInstance().getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, nameKey);
                ps.setString(2, trimmedIp);
                ps.executeUpdate();
            } catch (SQLException e) {
                Bukkit.getLogger().warning("Failed to persist IP for " + playerName + ": " + e.getMessage());
            }
        };

        if (Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(Core.getInstance(), task);
        } else {
            task.run();
        }
    }

    public String resolveIpTarget(String rawTarget) {
        if (rawTarget == null) {
            return null;
        }
        String target = rawTarget.trim();
        if (target.isEmpty()) {
            return null;
        }
        if (looksLikeIp(target)) {
            return target;
        }

        Player online = Bukkit.getPlayerExact(target);
        if (online != null && online.getAddress() != null && online.getAddress().getAddress() != null) {
            String detected = online.getAddress().getAddress().getHostAddress();
            if (detected != null && !detected.isEmpty()) {
                recordLastIp(online.getName(), detected);
                return detected;
            }
        }

        String cached = lastKnownIps.get(target.toLowerCase(Locale.ROOT));
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        return lookupStoredIp(target);
    }

        public boolean banPlayer(String target, String duration, String reason, String executor, BanType type) {
        Date now = new Date();

        Date expireDate = null;
        if (duration != null) {
            expireDate = calculateExpireDate(duration);
        }

        BanList.Type banListType = (type == BanType.IP) ? BanList.Type.IP : BanList.Type.NAME;
        String banReason = (reason != null && !reason.isEmpty()) ? reason : getDefaultReason();

        String sql = "INSERT INTO punishments (target, type, duration, reason, executor, date) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, target);
            ps.setString(2, (type == BanType.NORMAL) ? "BAN" : (type == BanType.IP) ? "IP_BAN" : "HWID_BAN");
            ps.setString(3, duration);
            ps.setString(4, banReason);
            ps.setString(5, executor);
            ps.setTimestamp(6, new Timestamp(now.getTime()));
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error while inserting ban record: " + e.getMessage());
            return false;
        }

        Player player = Bukkit.getPlayerExact(target);
        Profile profile = player != null ? Profile.getProfile(player.getName()) : Profile.getProfile(target);
        String durationLabel = (duration != null)
                ? formatDurationHuman(duration, profile)
                : getTimePhrase(profile, "permanent", "Permanent");
        String kickMessage = buildBanDisplayMessage(profile, banReason, durationLabel);

        Bukkit.getBanList(banListType).addBan(target, kickMessage, expireDate, executor);

        if (player != null && player.isOnline()) {
            player.kickPlayer(kickMessage);
        }

        String broadcast = LanguageManager.get(
                "punishments.ban.broadcast",
                "&c[Punish] &7{executor} &cbanned: &e{target} &7({duration}) &7Reason: &f{reason}",
                "executor", executor,
                "target", target,
                "duration", durationLabel,
                "reason", banReason);
        Bukkit.broadcastMessage(broadcast);

        return true;
    }

        public boolean mutePlayer(String target, String duration, String reason, String executor, MuteType type) {
        Date now = new Date();

        long expireMillis;
        if (duration != null) {
            expireMillis = calculateExpireDate(duration).getTime();
        } else {
            expireMillis = Long.MAX_VALUE;
        }

        mutedUntil.put(target.toLowerCase(), expireMillis);

        String muteReason = (reason != null && !reason.isEmpty()) ? reason : getDefaultReason();
        String sql = "INSERT INTO mutes (target, type, duration, reason, executor, date) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, target);
            ps.setString(2, (type == MuteType.NORMAL) ? "MUTE" : (type == MuteType.IP) ? "IP_MUTE" : "HWID_MUTE");
            ps.setString(3, duration);
            ps.setString(4, muteReason);
            ps.setString(5, executor);
            ps.setTimestamp(6, new Timestamp(now.getTime()));
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error while inserting mute record: " + e.getMessage());
            return false;
        }

        Player player = Bukkit.getPlayerExact(target);
        Profile profile = player != null ? Profile.getProfile(player.getName()) : Profile.getProfile(target);
        String durationLabel = (duration != null)
                ? formatDurationHuman(duration, profile)
                : getTimePhrase(profile, "permanent", "Permanent");

        if (player != null && player.isOnline()) {
            String prefix = LanguageManager.get(profile, "prefix.punish", "");
            String notify = LanguageManager.get(profile,
                    "punishments.mute.notify",
                    "{prefix}&cYour chat access has been restricted! &7({duration})",
                    "prefix", prefix,
                    "duration", durationLabel);
            String reasonLine = LanguageManager.get(profile,
                    "punishments.mute.reason",
                    "&7Reason: &f{reason}",
                    "reason", muteReason);
            player.sendMessage(notify + "\n" + reasonLine);
        }

        String broadcast = LanguageManager.get(
                "punishments.mute.broadcast",
                "&e[Punish] &7{executor} &emuted: &f{target} &7({duration}) &7Reason: &f{reason}",
                "executor", executor,
                "target", target,
                "duration", durationLabel,
                "reason", muteReason);
        Bukkit.broadcastMessage(broadcast);

        return true;
    }

        public boolean warnPlayer(String target, String reason, String executor, WarningType type) {
        Date now = new Date();

        String warnReason = (reason != null && !reason.isEmpty()) ? reason : getDefaultReason();
        String sql = "INSERT INTO warnings (target, type, reason, executor, date) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = Database.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, target);
            ps.setString(2, (type == WarningType.NORMAL) ? "WARNING" : (type == WarningType.IP) ? "IP_WARNING" : "HWID_WARNING");
            ps.setString(3, warnReason);
            ps.setString(4, executor);
            ps.setTimestamp(5, new Timestamp(now.getTime()));
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error while inserting warning record: " + e.getMessage());
            return false;
        }

        Player player = Bukkit.getPlayerExact(target);
        Profile profile = player != null ? Profile.getProfile(player.getName()) : Profile.getProfile(target);
        if (player != null && player.isOnline()) {
            String prefix = LanguageManager.get(profile, "prefix.punish", "");
            String notify = LanguageManager.get(profile,
                    "punishments.warn.notify",
                    "{prefix}&eYou have received a warning.",
                    "prefix", prefix,
                    "reason", warnReason);
            player.sendMessage(notify);
        }

        String broadcast = LanguageManager.get(
                "punishments.warn.broadcast",
                "&e[Punish] &7{executor} &ewarned: &f{target} &7Reason: &f{reason}",
                "executor", executor,
                "target", target,
                "reason", warnReason);
        Bukkit.broadcastMessage(broadcast);

        return true;
    }

    public boolean unbanPlayer(String target, BanType type) {
        BanList.Type banListType = (type == BanType.IP) ? BanList.Type.IP : BanList.Type.NAME;
        Bukkit.getBanList(banListType).pardon(target);

        String sql = "DELETE FROM punishments WHERE target = ? AND type = ?";
        String dbType = (type == BanType.NORMAL) ? "BAN" : (type == BanType.IP) ? "IP_BAN" : "HWID_BAN";
        try (Connection conn = Database.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, target);
            ps.setString(2, dbType);
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error during unban: " + e.getMessage());
            return false;
        }
    }

    public boolean unmutePlayer(String target, MuteType type) {
        mutedUntil.remove(target.toLowerCase());

        String sql = "DELETE FROM mutes WHERE target = ? AND type = ?";
        String dbType = (type == MuteType.NORMAL) ? "MUTE" : (type == MuteType.IP) ? "IP_MUTE" : "HWID_MUTE";
        try (Connection conn = Database.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, target);
            ps.setString(2, dbType);
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error during unmute: " + e.getMessage());
            return false;
        }
    }

    public boolean unwarnPlayer(String target, WarningType type) {
        String sql = "DELETE FROM warnings WHERE target = ? AND type = ?";
        String dbType = (type == WarningType.NORMAL) ? "WARNING"
                : (type == WarningType.IP) ? "IP_WARNING" : "HWID_WARNING";
        try (Connection conn = Database.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, target);
            ps.setString(2, dbType);
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error while deleting warning: " + e.getMessage());
            return false;
        }
    }

    public List<PunishmentEntry> getHistory(String target, boolean ownRequest) {
        List<PunishmentEntry> list = new ArrayList<>();

        String banSql = ownRequest
                ? "SELECT date, type, duration, reason, executor FROM punishments WHERE target = ?"
                : "SELECT date, type, duration, reason, executor FROM punishments WHERE target = ? AND type = 'BAN'";
        try (PreparedStatement ps = connection.prepareStatement(banSql)) {
            ps.setString(1, target);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String date = new SimpleDateFormat("yyyy-MM-dd HH:mm")
                            .format(rs.getTimestamp("date"));
                    String type = rs.getString("type");
                    String duration = rs.getString("duration");
                    String reason = rs.getString("reason");
                    String executor = rs.getString("executor");
                    list.add(new PunishmentEntry(date, type, duration, reason, executor));
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error reading punishment history (punishments): " + e.getMessage());
        }

        String muteSql = ownRequest
                ? "SELECT date, type, duration, reason, executor FROM mutes WHERE target = ?"
                : "SELECT date, type, duration, reason, executor FROM mutes WHERE target = ? AND type = 'MUTE'";
        try (PreparedStatement ps = connection.prepareStatement(muteSql)) {
            ps.setString(1, target);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String date = new SimpleDateFormat("yyyy-MM-dd HH:mm")
                            .format(rs.getTimestamp("date"));
                    String type = rs.getString("type");
                    String duration = rs.getString("duration");
                    String reason = rs.getString("reason");
                    String executor = rs.getString("executor");
                    list.add(new PunishmentEntry(date, type, duration, reason, executor));
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error reading mute history (mutes): " + e.getMessage());
        }

        String warnSql = "SELECT date, type, reason, executor FROM warnings WHERE target = ?";
        try (PreparedStatement ps = connection.prepareStatement(warnSql)) {
            ps.setString(1, target);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String date = new SimpleDateFormat("yyyy-MM-dd HH:mm")
                            .format(rs.getTimestamp("date"));
                    String type = rs.getString("type");
                    String duration = ""; 
                    String reason = rs.getString("reason");
                    String executor = rs.getString("executor");
                    list.add(new PunishmentEntry(date, type, duration, reason, executor));
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Error reading warning history (warnings): " + e.getMessage());
        }

        return list;
    }

    public PunishmentEntry getActiveBanEntry(String targetName) {
        if (targetName == null || targetName.trim().isEmpty()) {
            return null;
        }

        String sql = "SELECT date, type, duration, reason, executor FROM punishments "
                + "WHERE LOWER(target) = ? AND type = 'BAN' ORDER BY date DESC";

        String normalized = targetName.toLowerCase(Locale.ROOT);

        try (Connection conn = Database.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalized);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp appliedAt = rs.getTimestamp("date");
                    String duration = rs.getString("duration");
                    if (!isPunishmentActive(appliedAt, duration)) {
                        continue;
                    }

                    String type = rs.getString("type");
                    String reason = rs.getString("reason");
                    String executor = rs.getString("executor");
                    String date = new SimpleDateFormat("yyyy-MM-dd HH:mm")
                            .format(appliedAt);
                    return new PunishmentEntry(date, type, duration, reason, executor);
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("Failed to verify active ban for " + targetName + ": " + e.getMessage());
        }
        return null;
    }

    public boolean isPlayerMuted(String playerName) {
        long now = System.currentTimeMillis();
        Long until = mutedUntil.get(playerName.toLowerCase());
        return (until != null && until > now);
    }

    public String getRemainingMuteTime(String playerName) {
        Long until = mutedUntil.get(playerName.toLowerCase());
        if (until == null) {
            return TimeUtils.getPhrase("no-duration", "No duration available");
        }
        if (until == Long.MAX_VALUE) {
            return TimeUtils.getPhrase("permanent", "Permanent");
        }

        long diff = until - System.currentTimeMillis();
        if (diff <= 0) {
            return TimeUtils.getPhrase("expired", "Expired");
        }

        String formatted = TimeUtils.getTime(diff);
        if (formatted.isEmpty()) {
            return TimeUtils.formatWithUnit(0, "second", "second", "seconds");
        }
        return formatted;
    }

    private Date calculateExpireDate(String duration) {
        long base = System.currentTimeMillis();
        long delta = parseDurationMillis(duration);
        if (delta <= 0L) {
            return new Date(base);
        }
        return new Date(base + delta);
    }

    private long parseDurationMillis(String duration) {
        if (duration == null) {
            return -1L;
        }
        String trimmed = duration.trim();
        if (trimmed.length() < 2) {
            return -1L;
        }
        char unit = Character.toLowerCase(trimmed.charAt(trimmed.length() - 1));
        long amount;
        try {
            amount = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
        } catch (NumberFormatException e) {
            return -1L;
        }
        if (amount < 0) {
            return -1L;
        }
        switch (unit) {
            case 's':
                return amount * 1000L;
            case 'm':
                return amount * 60_000L;
            case 'h':
                return amount * 3_600_000L;
            case 'd':
                return amount * 86_400_000L;
            default:
                return -1L;
        }
    }

    private boolean isPunishmentActive(Timestamp appliedAt, String duration) {
        if (appliedAt == null) {
            return false;
        }
        if (duration == null) {
            return true;
        }
        String trimmed = duration.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        long delta = parseDurationMillis(trimmed);
        if (delta < 0L) {
            return true;
        }
        long expiresAt = appliedAt.getTime() + delta;
        return expiresAt > System.currentTimeMillis();
    }

    private long computeExpiryMillis(Timestamp issuedAt, String duration) {
        if (duration == null || duration.trim().isEmpty()) {
            return Long.MAX_VALUE;
        }
        long delta = parseDurationMillis(duration);
        if (delta <= 0L) {
            return issuedAt != null ? issuedAt.getTime() : System.currentTimeMillis();
        }
        long base = issuedAt != null ? issuedAt.getTime() : System.currentTimeMillis();
        return base + delta;
    }

    private void reloadMuteCache() {
        Map<String, Long> refreshed = new ConcurrentHashMap<>();
        String sql = "SELECT target, duration, date FROM mutes";
        try (Connection conn = Database.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            long now = System.currentTimeMillis();
            while (rs.next()) {
                String target = rs.getString("target");
                if (target == null || target.trim().isEmpty()) {
                    continue;
                }
                Timestamp issuedAt = rs.getTimestamp("date");
                String duration = rs.getString("duration");
                long expiry = computeExpiryMillis(issuedAt, duration);
                if (expiry != Long.MAX_VALUE && expiry <= now) {
                    continue;
                }
                refreshed.put(target.toLowerCase(Locale.ROOT), expiry);
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Failed to load mute cache: " + e.getMessage());
            return;
        }
        mutedUntil.clear();
        mutedUntil.putAll(refreshed);
    }

    private void reapplyActiveBans() {
        List<PendingBan> pending = collectActiveBans();
        if (pending.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(Core.getInstance(), () -> {
            for (PendingBan ban : pending) {
                BanList.Type listType = ban.type == BanType.IP ? BanList.Type.IP : BanList.Type.NAME;
                Date expiry = ban.expiresAt == Long.MAX_VALUE ? null : new Date(ban.expiresAt);
                String durationLabel;
                if (ban.duration == null || ban.duration.trim().isEmpty()) {
                    durationLabel = LanguageManager.get(LanguageManager.getDefaultLocale(),
                            "listeners.ban-login.duration-fallback",
                            "&cPermanent");
                } else {
                    durationLabel = formatDurationHuman(ban.duration);
                }
                String message = buildBanDisplayMessage(null, ban.reason, durationLabel);
                Bukkit.getBanList(listType).addBan(ban.target, message, expiry, ban.executor);
            }
        });
    }

    private List<PendingBan> collectActiveBans() {
        List<PendingBan> pending = new ArrayList<>();
        String sql = "SELECT target, type, duration, reason, executor, date FROM punishments";
        try (Connection conn = Database.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            long now = System.currentTimeMillis();
            while (rs.next()) {
                String target = rs.getString("target");
                if (target == null || target.trim().isEmpty()) {
                    continue;
                }

                BanType banType = resolveBanType(rs.getString("type"));
                if (banType == null || banType == BanType.HWID) {
                    continue;
                }

                Timestamp issuedAt = rs.getTimestamp("date");
                String duration = rs.getString("duration");
                long expiresAt = computeExpiryMillis(issuedAt, duration);
                if (expiresAt != Long.MAX_VALUE && expiresAt <= now) {
                    continue;
                }

                String reason = rs.getString("reason");
                String executor = rs.getString("executor");
                pending.add(new PendingBan(
                        target,
                        banType,
                        (reason == null || reason.isEmpty()) ? getDefaultReason() : reason,
                        (executor == null || executor.isEmpty()) ? "Console" : executor,
                        expiresAt,
                        duration));
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("Failed to load active bans: " + e.getMessage());
        }
        return pending;
    }

    private BanType resolveBanType(String rawType) {
        if (rawType == null) {
            return BanType.NORMAL;
        }
        switch (rawType.toUpperCase(Locale.ROOT)) {
            case "BAN":
                return BanType.NORMAL;
            case "IP_BAN":
                return BanType.IP;
            case "HWID_BAN":
                return BanType.HWID;
            default:
                return null;
        }
    }

    private boolean looksLikeIp(String value) {
        if (value == null) {
            return false;
        }
        return value.contains(".") || value.contains(":");
    }

    private String lookupStoredIp(String playerName) {
        String lookup = playerName.toLowerCase(Locale.ROOT);
        String sql = "SELECT ip FROM player_ips WHERE player = ? LIMIT 1";
        try (Connection conn = Database.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lookup);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String ip = rs.getString("ip");
                    if (ip != null && !ip.isEmpty()) {
                        lastKnownIps.put(lookup, ip);
                        return ip;
                    }
                }
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("Failed to look up IP for " + playerName + ": " + e.getMessage());
        }
        return null;
    }

    public String formatDurationHuman(String duration) {
        return formatDurationHuman(duration, null);
    }

    public String buildBanDisplayMessage(Profile profile, String reason, String durationLabel) {
        String localeReason = (reason != null && !reason.trim().isEmpty()) ? reason : getDefaultReason();
        if (profile != null) {
            return LanguageManager.get(profile,
                    "punishments.ban.kick",
                    "&cYou have been banned from the network!"
                            + "\n&7Reason: &f{reason}"
                            + "\n&7Duration: &f{duration}",
                    "reason", localeReason,
                    "duration", durationLabel);
        }
        return LanguageManager.get(LanguageManager.getDefaultLocale(),
                "punishments.ban.kick",
                "&cYou have been banned from the network!"
                        + "\n&7Reason: &f{reason}"
                        + "\n&7Duration: &f{duration}",
                "reason", localeReason,
                "duration", durationLabel);
    }

    private String formatDurationHuman(String duration, Profile profile) {
        if (duration == null || duration.isEmpty()) {
            return "";
        }
        char unit = duration.charAt(duration.length() - 1);
        String numPart = duration.substring(0, duration.length() - 1);
        long amount;
        try {
            amount = Long.parseLong(numPart);
        } catch (NumberFormatException ex) {
            return duration;
        }
        switch (unit) {
            case 's':
                return formatUnit(profile, amount, "second", "second", "seconds");
            case 'm':
                return formatUnit(profile, amount, "minute", "minute", "minutes");
            case 'h':
                return formatUnit(profile, amount, "hour", "hour", "hours");
            case 'd':
                return formatUnit(profile, amount, "day", "day", "days");
            default:
                return duration;
        }
    }

    private String formatUnit(Profile profile, long value, String unitKey,
                              String singularDefault, String pluralDefault) {
        if (profile != null) {
            String key = "time.unit." + unitKey + (value == 1 ? ".singular" : ".plural");
            String fallback = value == 1 ? singularDefault : pluralDefault;
            String label = LanguageManager.get(profile, key, fallback);
            return value + " " + label;
        }
        return TimeUtils.formatWithUnit(value, unitKey, singularDefault, pluralDefault);
    }

    private String getTimePhrase(Profile profile, String key, String def) {
        if (profile != null) {
            return LanguageManager.get(profile, "time.phrase." + key, def);
        }
        return TimeUtils.getPhrase(key, def);
    }

    private String getDefaultReason() {
        return Core.getInstance()
                .getConfig("config")
                .getString("punishments.default-reason", "Not specified");
    }

    private static final class PendingBan {
        private final String target;
        private final BanType type;
        private final String reason;
        private final String executor;
        private final long expiresAt;
        private final String duration;

        private PendingBan(String target, BanType type, String reason, String executor, long expiresAt, String duration) {
            this.target = target;
            this.type = type;
            this.reason = reason;
            this.executor = executor;
            this.expiresAt = expiresAt;
            this.duration = duration;
        }
    }
}
