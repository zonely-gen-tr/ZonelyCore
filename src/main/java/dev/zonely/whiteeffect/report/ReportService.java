package dev.zonely.whiteeffect.report;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.utils.PunishmentManager;
import dev.zonely.whiteeffect.replay.advanced.AdvancedReplayRecorder;
import dev.zonely.whiteeffect.replay.ReplayFrame;
import dev.zonely.whiteeffect.replay.ReplayRecorder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.IntConsumer;
import java.util.logging.Level;

public class ReportService {

    private static final String REPORTS_TABLE = "reports";
    private static final Set<String> EXPECTED_REPORT_COLUMNS = new LinkedHashSet<>(Arrays.asList(
            "id",
            "reporter",
            "userid",
            "target",
            "target_uuid",
            "reportcategory",
            "reason",
            "reportcontent",
            "server",
            "issue_id",
            "created_at",
            "status",
            "replay_key",
            "resolved_by",
            "resolved_at",
            "resolved_action",
            "resolved_reason",
            "resolved_duration",
            "feedback_sent"
    ));

    private final Core plugin;
    private final PunishmentManager punishmentManager;

    public ReportService(Core plugin, PunishmentManager punishmentManager) {
        this.plugin = plugin;
        this.punishmentManager = punishmentManager;
        ensureTables();
    }

    public void createReportAsync(String reporter, String target, String category, String reason, String server,
                                  String replayKey, IntConsumer callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int reportId = createReport(reporter, target, category, reason, server, replayKey);
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(reportId));
            }
        });
    }

    private void ensureTables() {
        String sql = "CREATE TABLE IF NOT EXISTS reports (" +
                " id INT AUTO_INCREMENT PRIMARY KEY," +
                " reporter VARCHAR(36) NOT NULL," +
                " userid VARCHAR(36) NULL," +
                " target VARCHAR(36) NOT NULL," +
                " target_uuid VARCHAR(36) NULL," +
                " reportCategory VARCHAR(64) NOT NULL DEFAULT ''," +
                " reason VARCHAR(255) NOT NULL," +
                " reportContent VARCHAR(255) NOT NULL DEFAULT ''," +
                " server VARCHAR(64) NULL," +
                " issue_id INT NOT NULL DEFAULT 0," +
                " created_at DATETIME NOT NULL," +
                " status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING'," +
                " replay_key VARCHAR(128) NULL," +
                " resolved_by VARCHAR(36) NULL," +
                " resolved_at DATETIME NULL," +
                " resolved_action VARCHAR(32) NULL," +
                " resolved_reason TEXT NULL," +
                " resolved_duration VARCHAR(32) NULL," +
                " feedback_sent TINYINT(1) NOT NULL DEFAULT 0" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8;";
        try (Connection c = Database.getInstance().getConnection();
             Statement st = c.createStatement()) {
            Set<String> existingColumns = fetchExistingColumns(c, REPORTS_TABLE);
            boolean reportsRebuilt = false;
            if (!existingColumns.isEmpty() && !existingColumns.equals(EXPECTED_REPORT_COLUMNS)) {
                Set<String> missing = new LinkedHashSet<>(EXPECTED_REPORT_COLUMNS);
                missing.removeAll(existingColumns);
                Set<String> unexpected = new LinkedHashSet<>(existingColumns);
                unexpected.removeAll(EXPECTED_REPORT_COLUMNS);
                plugin.getLogger().warning(String.format(
                        "[Reports] Unexpected reports table schema detected. Missing=%s, Extra=%s. Rebuilding table.",
                        missing.isEmpty() ? "-" : missing.toString(),
                        unexpected.isEmpty() ? "-" : unexpected.toString()));
                st.executeUpdate("DROP TABLE IF EXISTS replay_actions");
                st.executeUpdate("DROP TABLE IF EXISTS replay_actors");
                st.executeUpdate("DROP TABLE IF EXISTS replay_sessions");
                st.executeUpdate("DROP TABLE IF EXISTS report_replay_frames");
                st.executeUpdate("DROP TABLE IF EXISTS report_snapshots");
                st.executeUpdate("DROP TABLE IF EXISTS " + REPORTS_TABLE);
                reportsRebuilt = true;
            }

            st.executeUpdate(sql);
            if (reportsRebuilt) {
                plugin.getLogger().info("[Reports] reports table recreated with default schema.");
            }
            st.executeUpdate("CREATE TABLE IF NOT EXISTS report_replay_frames (" +
                    " id INT AUTO_INCREMENT PRIMARY KEY," +
                    " report_id INT NOT NULL," +
                    " tick INT NOT NULL," +
                    " world VARCHAR(64) NOT NULL," +
                    " x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL," +
                    " yaw FLOAT NOT NULL, pitch FLOAT NOT NULL," +
                    " INDEX (report_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8;");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS report_snapshots (" +
                    " report_id INT PRIMARY KEY," +
                    " captured_at BIGINT NOT NULL," +
                    " health DOUBLE NOT NULL," +
                    " food INT NOT NULL," +
                    " saturation FLOAT NOT NULL," +
                    " xp_level INT NOT NULL," +
                    " xp_progress FLOAT NOT NULL," +
                    " walk_speed FLOAT NOT NULL," +
                    " world VARCHAR(64) NULL," +
                    " x DOUBLE NULL," +
                    " y DOUBLE NULL," +
                    " z DOUBLE NULL," +
                    " yaw FLOAT NULL," +
                    " pitch FLOAT NULL" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8;");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS replay_sessions (" +
                    " id INT AUTO_INCREMENT PRIMARY KEY," +
                    " report_id INT NOT NULL," +
                    " world VARCHAR(64) NULL," +
                    " started_at BIGINT NOT NULL," +
                    " duration_ms INT NOT NULL DEFAULT 0," +
                    " notes TEXT NULL," +
                    " INDEX idx_replay_session_report (report_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8;");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS replay_actors (" +
                    " id INT AUTO_INCREMENT PRIMARY KEY," +
                    " session_id INT NOT NULL," +
                    " uuid VARCHAR(36) NULL," +
                    " name VARCHAR(36) NOT NULL," +
                    " type VARCHAR(32) NOT NULL," +
                    " INDEX idx_replay_actor_session (session_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8;");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS replay_actions (" +
                    " id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    " session_id INT NOT NULL," +
                    " actor_id INT NOT NULL," +
                    " tick INT NOT NULL," +
                    " action_type VARCHAR(32) NOT NULL," +
                    " payload LONGTEXT NULL," +
                    " INDEX idx_replay_action_session (session_id)," +
                    " INDEX idx_replay_action_actor (actor_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8;");

            ensureColumn(c, "reports", "reporter", "VARCHAR(36) NOT NULL DEFAULT ''");
            ensureColumn(c, "reports", "userid", "VARCHAR(36) NULL");
            ensureColumn(c, "reports", "target", "VARCHAR(36) NOT NULL DEFAULT ''");
            ensureColumnWithDefault(c, "reports", "reportCategory", "VARCHAR(64) NOT NULL DEFAULT ''");
            ensureColumn(c, "reports", "reason", "VARCHAR(255) NOT NULL DEFAULT ''");
            ensureColumnWithDefault(c, "reports", "reportContent", "VARCHAR(255) NOT NULL DEFAULT ''");
            ensureColumn(c, "reports", "server", "VARCHAR(64) NULL DEFAULT NULL");
            ensureColumnWithDefault(c, "reports", "issue_id", "INT NOT NULL DEFAULT 0");
            ensureColumn(c, "reports", "created_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
            ensureColumn(c, "reports", "status",
                    "ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING'");
            ensureColumn(c, "reports", "replay_key", "VARCHAR(128) NULL");
            ensureColumn(c, "reports", "resolved_by", "VARCHAR(36) NULL");
            ensureColumn(c, "reports", "resolved_at", "DATETIME NULL");
            ensureColumn(c, "reports", "resolved_action", "VARCHAR(32) NULL");
            ensureColumn(c, "reports", "resolved_reason", "TEXT NULL");
            ensureColumn(c, "reports", "resolved_duration", "VARCHAR(32) NULL");
            ensureColumn(c, "reports", "feedback_sent", "TINYINT(1) NOT NULL DEFAULT 0");
            ensureColumn(c, "reports", "target_uuid", "VARCHAR(36) NULL");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed creating reports table", e);
        }
    }

    private Set<String> fetchExistingColumns(Connection connection, String table) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        String query = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String column = rs.getString(1);
                    if (column != null && !column.isEmpty()) {
                        columns.add(column.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return columns;
    }

    private void ensureColumn(Connection connection, String table, String column, String definition) {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getColumns(connection.getCatalog(), null, table, column)) {
                if (rs.next()) {
                    return;
                }
            }
            String ddl = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition;
            try (Statement st = connection.createStatement()) {
                st.executeUpdate(ddl);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Unable to ensure column " + table + "." + column + " exists (skipping upgrade).", ex);
        }
    }

    private void ensureColumnWithDefault(Connection connection, String table, String column, String definition) {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            boolean exists = false;
            boolean needsAlter = false;
            try (ResultSet rs = meta.getColumns(connection.getCatalog(), null, table, column)) {
                if (rs.next()) {
                    exists = true;
                    String defaultValue = rs.getString("COLUMN_DEF");
                    String nullable = rs.getString("IS_NULLABLE");
                    if (defaultValue == null || "YES".equalsIgnoreCase(nullable)) {
                        needsAlter = true;
                    }
                }
            }
            try (Statement st = connection.createStatement()) {
                if (!exists) {
                    st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                } else if (needsAlter) {
                    st.executeUpdate("ALTER TABLE " + table + " MODIFY " + column + " " + definition);
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Unable to normalize column " + table + "." + column + " (skipping upgrade).", ex);
        }
    }

    public int createReport(String reporter, String target, String category, String reason, String server, String replayKey) {
        String sql = "INSERT INTO reports (reporter, userid, target, target_uuid, reportCategory, reason, reportContent, server, issue_id, created_at, status, replay_key) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), 'PENDING', ?)";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            String reporterUuid = resolveUuid(reporter);
            String targetUuid = resolveUuid(target);
            ps.setString(1, reporter);
            if (reporterUuid != null) {
                ps.setString(2, reporterUuid);
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setString(3, target);
            if (targetUuid != null) {
                ps.setString(4, targetUuid);
            } else {
                ps.setNull(4, Types.VARCHAR);
            }
            ps.setString(5, category == null ? "" : category);
            ps.setString(6, reason);
            ps.setString(7, reason); 
            ps.setString(8, server);
            ps.setInt(9, 0);
            ps.setString(10, replayKey);
            ps.executeUpdate();
            int reportId = -1;
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    reportId = rs.getInt(1);
                }
            }
            if (reportId > 0) {
                UUID reporterUuidValue = null;
                if (reporterUuid != null) {
                    try {
                        reporterUuidValue = UUID.fromString(reporterUuid);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                UUID targetUuidValue = null;
                if (targetUuid != null) {
                    try {
                        targetUuidValue = UUID.fromString(targetUuid);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                Report reportStub = new Report(reportId, reporter, reporterUuidValue, target, targetUuidValue, category,
                        reason, reason,
                        server, new Timestamp(System.currentTimeMillis()),
                        ReportStatus.PENDING, replayKey, null, null, null, null, null);
                AdvancedReplayRecorder recorder = AdvancedReplayRecorder.get();
                if (recorder != null) {
                    int captureSeconds = plugin.getConfig("config")
                            .getInt("reports.replay.capture-seconds", 15);
                    final int finalReportId = reportId;
                    recorder.startSessionAsync(reportStub, captureSeconds)
                            .thenAccept(sessionId -> {
                                if (sessionId != null && sessionId > 0) {
                                    updateReplayKey(finalReportId, "advanced");
                                }
                            })
                            .exceptionally(ex -> {
                                plugin.getLogger().log(Level.WARNING, "Advanced replay session failed for report " + finalReportId, ex);
                                return null;
                            });
                }
                List<ReplayFrame> frames = collectReplayFrames(target);
                saveReplayFrames(reportId, frames);
                if (replayKey == null && frames != null && !frames.isEmpty()) {
                    updateReplayKey(reportId, "frames");
                }
                SnapshotData snapshot = captureSnapshot(target);
                if (snapshot != null) {
                    saveSnapshot(reportId, snapshot);
                }
            }
            return reportId;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to insert report", e);
        }
        return -1;
    }

    public List<Report> listActiveReports() {
        List<Report> list = new ArrayList<>();
        String sql = "SELECT * FROM reports WHERE status = 'PENDING' ORDER BY id DESC";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed fetching active reports", e);
        }
        return list;
    }

    public List<Report> listApprovedReports() {
        List<Report> list = new ArrayList<>();
        String sql = "SELECT * FROM reports WHERE status = 'APPROVED' ORDER BY resolved_at DESC, id DESC";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed fetching approved reports", e);
        }
        return list;
    }

    public List<Report> listReportsForTarget(String target) {
        List<Report> list = new ArrayList<>();
        String sql = "SELECT * FROM reports WHERE target = ? ORDER BY id DESC";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, target);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed fetching reports for target", e);
        }
        return list;
    }

    public Report getById(int id) {
        String sql = "SELECT * FROM reports WHERE id = ?";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed reading report by id", e);
        }
        return null;
    }

    private List<ReplayFrame> collectReplayFrames(String target) {
        ReplayRecorder recorder = ReplayRecorder.get();
        if (recorder == null) {
            return Collections.emptyList();
        }
        int seconds = plugin.getConfig("config").getInt("reports.replay.capture-seconds", 120);
        return recorder.exportRecent(target, seconds);
    }

    private String resolveUuid(String playerName) {
        return callSync(() -> resolveUuidSync(playerName));
    }

    private String resolveUuidSync(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return online.getUniqueId().toString();
        }
        try {
            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
            if (offline != null && offline.getUniqueId() != null) {
                return offline.getUniqueId().toString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public void saveReplayFrames(int reportId, List<ReplayFrame> frames) {
        if (frames == null || frames.isEmpty()) return;
        String sql = "INSERT INTO report_replay_frames (report_id, tick, world, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (ReplayFrame f : frames) {
                ps.setInt(1, reportId);
                ps.setInt(2, f.tick);
                ps.setString(3, f.world);
                ps.setDouble(4, f.x);
                ps.setDouble(5, f.y);
                ps.setDouble(6, f.z);
                ps.setFloat(7, f.yaw);
                ps.setFloat(8, f.pitch);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save replay frames for report " + reportId, e);
        }
    }

    private SnapshotData captureSnapshot(String target) {
        return callSync(() -> captureSnapshotSync(target));
    }

    private SnapshotData captureSnapshotSync(String target) {
        Player player = Bukkit.getPlayerExact(target);
        if (player == null) {
            return null;
        }
        SnapshotData data = new SnapshotData();
        data.capturedAt = System.currentTimeMillis();
        data.health = Math.min(player.getHealth(), player.getMaxHealth());
        data.food = player.getFoodLevel();
        data.saturation = player.getSaturation();
        data.level = player.getLevel();
        data.exp = player.getExp();
        data.walkSpeed = player.getWalkSpeed();
        data.world = player.getWorld().getName();
        org.bukkit.Location location = player.getLocation();
        if (location != null) {
            data.x = location.getX();
            data.y = location.getY();
            data.z = location.getZ();
            data.yaw = location.getYaw();
            data.pitch = location.getPitch();
        }
        return data;
    }

    private void saveSnapshot(int reportId, SnapshotData snapshot) {
        if (snapshot == null) {
            return;
        }

        String sql = "REPLACE INTO report_snapshots (report_id, captured_at, health, food, saturation, xp_level, xp_progress," +
                " walk_speed, world, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, reportId);
            ps.setLong(2, snapshot.capturedAt);
            ps.setDouble(3, snapshot.health);
            ps.setInt(4, snapshot.food);
            ps.setFloat(5, snapshot.saturation);
            ps.setInt(6, snapshot.level);
            ps.setFloat(7, snapshot.exp);
            ps.setFloat(8, snapshot.walkSpeed);
            if (snapshot.world != null) {
                ps.setString(9, snapshot.world);
            } else {
                ps.setNull(9, Types.VARCHAR);
            }
            if (snapshot.x != null) {
                ps.setDouble(10, snapshot.x);
            } else {
                ps.setNull(10, Types.DOUBLE);
            }
            if (snapshot.y != null) {
                ps.setDouble(11, snapshot.y);
            } else {
                ps.setNull(11, Types.DOUBLE);
            }
            if (snapshot.z != null) {
                ps.setDouble(12, snapshot.z);
            } else {
                ps.setNull(12, Types.DOUBLE);
            }
            if (snapshot.yaw != null) {
                ps.setFloat(13, snapshot.yaw);
            } else {
                ps.setNull(13, Types.FLOAT);
            }
            if (snapshot.pitch != null) {
                ps.setFloat(14, snapshot.pitch);
            } else {
                ps.setNull(14, Types.FLOAT);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save report snapshot for " + reportId, e);
        }
    }

    public List<ReplayFrame> loadReplayFrames(int reportId) {
        List<ReplayFrame> frames = new ArrayList<>();
        String sql = "SELECT tick, world, x, y, z, yaw, pitch FROM report_replay_frames WHERE report_id = ? ORDER BY tick ASC";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, reportId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    frames.add(new ReplayFrame(
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"),
                            rs.getInt("tick")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load replay frames for report " + reportId, e);
        }
        return frames;
    }

    public boolean hasReplayFrames(int reportId) {
        if (hasLegacyReplayFrames(reportId)) {
            return true;
        }
        return dev.zonely.whiteeffect.replay.advanced.AdvancedReplayStorage.get().hasReplayData(reportId);
    }

    private boolean hasLegacyReplayFrames(int reportId) {
        String sql = "SELECT 1 FROM report_replay_frames WHERE report_id = ? LIMIT 1";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, reportId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to check replay frames for report " + reportId, e);
        }
        return false;
    }

    public ReportSnapshot getSnapshot(int reportId) {
        String sql = "SELECT report_id, captured_at, health, food, saturation, xp_level, xp_progress, walk_speed," +
                " world, x, y, z, yaw, pitch FROM report_snapshots WHERE report_id = ?";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, reportId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ReportSnapshot(
                            rs.getInt("report_id"),
                            rs.getDouble("health"),
                            rs.getInt("food"),
                            rs.getFloat("saturation"),
                            rs.getInt("xp_level"),
                            rs.getFloat("xp_progress"),
                            rs.getFloat("walk_speed"),
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"),
                            rs.getLong("captured_at")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load snapshot for report " + reportId, e);
        }
        return null;
    }

    private void updateReplayKey(int reportId, String key) {
        String sql = "UPDATE reports SET replay_key=? WHERE id=?";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setInt(2, reportId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update replay key for report " + reportId, e);
        }
    }

    public boolean approveReport(int id, CommandSender moderator) {
        Report report = getById(id);
        if (report == null || report.getStatus() != ReportStatus.PENDING) {
            return false;
        }
        String reason = LanguageManager.get("commands.reports.punish.reason",
                "Report Approved: {reason}", "reason", report.getReason());
        String duration = plugin.getConfig("config").getString("reports.default-punishment.duration", "1d");
        return executePunishment(report, moderator, ReportPunishAction.BAN, duration, reason);
    }

    public boolean executePunishment(Report report, CommandSender moderator, ReportPunishAction action,
                                     String duration, String reason) {
        if (report == null || report.getStatus() != ReportStatus.PENDING) {
            return false;
        }
        if (reason == null || reason.trim().isEmpty()) {
            reason = report.getReason();
        }
        boolean success = false;
        String moderatorName = moderator == null ? "Console" : moderator.getName();
        switch (action) {
            case BAN:
                success = punishmentManager.banPlayer(report.getTarget(), duration, reason, moderatorName,
                        PunishmentManager.BanType.NORMAL);
                break;
            case MUTE:
                success = punishmentManager.mutePlayer(report.getTarget(), duration, reason, moderatorName,
                        PunishmentManager.MuteType.NORMAL);
                break;
            case WARNING:
                success = punishmentManager.warnPlayer(report.getTarget(), reason, moderatorName,
                        PunishmentManager.WarningType.NORMAL);
                break;
            case KICK:
                Player target = Bukkit.getPlayerExact(report.getTarget());
                if (target != null && target.isOnline()) {
                    target.kickPlayer(reason);
                }
                success = true;
                break;
            case NONE:
                success = true;
                break;
        }

        if (!success) {
            return false;
        }

        String storeReason = (reason == null || reason.isEmpty()) ? report.getReason() : reason;
        String storeDuration = (duration == null || duration.isEmpty()) ? "-" : duration;
        markApproved(report.getId(), moderatorName, action, storeReason, storeDuration);
        notifyReporter(report, true);
        runApprovalCommands(report, moderatorName, reason, duration, action);
        return true;
    }

    public boolean rejectReport(int id, CommandSender moderator) {
        Report r = getById(id);
        if (r == null || r.getStatus() != ReportStatus.PENDING) {
            return false;
        }
        String sql = "UPDATE reports SET status='REJECTED', resolved_by=?, resolved_at=NOW(), resolved_action=NULL, resolved_reason=NULL, resolved_duration=NULL WHERE id=?";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moderator.getName());
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed updating report status to REJECTED", e);
        }
        notifyReporter(r, false);
        return true;
    }

    private void markApproved(int reportId, String moderator, ReportPunishAction action, String reason, String duration) {
        String sql = "UPDATE reports SET status='APPROVED', resolved_by=?, resolved_at=NOW(), resolved_action=?, resolved_reason=?, resolved_duration=? WHERE id=?";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, moderator);
            ps.setString(2, action == null ? null : action.name());
            ps.setString(3, reason);
            ps.setString(4, duration);
            ps.setInt(5, reportId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed updating report status to APPROVED", e);
        }
    }

    private void runApprovalCommands(Report report, String moderator, String reason, String duration,
                                     ReportPunishAction action) {
        dev.zonely.whiteeffect.plugin.config.WConfig cfg = plugin.getConfig("config");
        if (!cfg.getBoolean("reports.notifications.approve-commands.enabled", false)) {
            return;
        }
        List<String> commands = cfg.getStringList("reports.notifications.approve-commands.list");
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (String raw : commands) {
            String parsed = raw
                    .replace("{reporter}", report.getReporter())
                    .replace("{target}", report.getTarget())
                    .replace("{moderator}", moderator == null ? "Console" : moderator)
                    .replace("{reason}", reason == null ? report.getReason() : reason)
                    .replace("{duration}", duration == null ? "-" : duration)
                    .replace("{id}", String.valueOf(report.getId()))
                    .replace("{action}", action == null ? "NONE" : action.getId());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    private void notifyReporter(Report report, boolean approved) {
        Player reporter = Bukkit.getPlayerExact(report.getReporter());
        if (reporter == null) {
            return;
        }
        String key = approved ? "commands.reports.notify.approved" : "commands.reports.notify.rejected";
        String def = approved ? "{prefix}&aYour report for {target} was approved. Thank you!"
                : "{prefix}&eYour report for {target} was reviewed and rejected.";
        String msg = LanguageManager.get(key, def,
                "prefix", LanguageManager.get("prefix.punish", "&4Punish &8->> "),
                "target", report.getTarget());
        reporter.sendMessage(msg);
    }

    private Report map(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String reporter = rs.getString("reporter");
        String reporterUuidRaw;
        try {
            reporterUuidRaw = rs.getString("userid");
        } catch (SQLException ignored) {
            reporterUuidRaw = null;
        }
        UUID reporterUuid = null;
        if (reporterUuidRaw != null && !reporterUuidRaw.isEmpty()) {
            try {
                reporterUuid = UUID.fromString(reporterUuidRaw);
            } catch (IllegalArgumentException ignored) {
            }
        }
        String target = rs.getString("target");
        String targetUuidRaw;
        try {
            targetUuidRaw = rs.getString("target_uuid");
        } catch (SQLException ignored) {
            targetUuidRaw = null;
        }
        UUID targetUuid = null;
        if (targetUuidRaw != null && !targetUuidRaw.isEmpty()) {
            try {
                targetUuid = UUID.fromString(targetUuidRaw);
            } catch (IllegalArgumentException ignored) {
            }
        }
        String category = rs.getString("reportCategory");
        String reason = rs.getString("reason");
        String content = rs.getString("reportContent");
        String server = rs.getString("server");
        Timestamp created = rs.getTimestamp("created_at");
        ReportStatus status = ReportStatus.valueOf(rs.getString("status"));
        String replayKey = rs.getString("replay_key");
        if ((replayKey == null || replayKey.isEmpty()) && hasReplayFrames(id)) {
            replayKey = "frames";
        }
        String resolvedBy = rs.getString("resolved_by");
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        String resolvedAction = rs.getString("resolved_action");
        String resolvedReason = rs.getString("resolved_reason");
        String resolvedDuration = rs.getString("resolved_duration");
        return new Report(id, reporter, reporterUuid, target, targetUuid,
                category == null ? "" : category,
                reason,
                content == null ? "" : content,
                server, created, status, replayKey, resolvedBy, resolvedAt,
                resolvedAction, resolvedReason, resolvedDuration);
    }

    private <T> T callSync(Callable<T> task) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        try {
            return plugin.getServer().getScheduler().callSyncMethod(plugin, task).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class SnapshotData {
        long capturedAt;
        double health;
        int food;
        float saturation;
        int level;
        float exp;
        float walkSpeed;
        String world;
        Double x;
        Double y;
        Double z;
        Float yaw;
        Float pitch;
    }
}

