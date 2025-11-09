package dev.zonely.whiteeffect.replay.advanced;

import com.google.gson.JsonObject;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;

public final class AdvancedReplayStorage {

    private static final AdvancedReplayStorage INSTANCE = new AdvancedReplayStorage(Core.getInstance());

    public static AdvancedReplayStorage get() {
        return INSTANCE;
    }

    private final Core plugin;
    private final ExecutorService executor;

    private AdvancedReplayStorage(Core plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "ZonelyCore-AdvReplay");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public int createSession(int reportId, String world) {
        String sql = "INSERT INTO replay_sessions (report_id, world, started_at, duration_ms) VALUES (?,?,?,0)";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, reportId);
            ps.setString(2, world);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create replay session for report " + reportId, ex);
        }
        return -1;
    }

    public CompletableFuture<Integer> createSessionAsync(int reportId, String world) {
        return CompletableFuture.supplyAsync(() -> createSession(reportId, world), executor);
    }

    public void closeSession(int sessionId, int durationMs) {
        String sql = "UPDATE replay_sessions SET duration_ms=?, notes=NULL WHERE id=?";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(durationMs, 0));
            ps.setInt(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to close replay session " + sessionId, ex);
        }
    }

    public CompletableFuture<Void> closeSessionAsync(int sessionId, int durationMs) {
        return CompletableFuture.runAsync(() -> closeSession(sessionId, durationMs), executor);
    }

    public int ensureActor(int sessionId, UUID uuid, String name, ReplayActorType type) {
        String lookupSql = "SELECT id FROM replay_actors WHERE session_id=? AND (uuid=? OR (uuid IS NULL AND name=?)) LIMIT 1";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement lookup = c.prepareStatement(lookupSql)) {
            lookup.setInt(1, sessionId);
            lookup.setString(2, uuid == null ? null : uuid.toString());
            lookup.setString(3, name);
            try (ResultSet rs = lookup.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to lookup replay actor for session " + sessionId, ex);
        }

        String insertSql = "INSERT INTO replay_actors (session_id, uuid, name, type) VALUES (?,?,?,?)";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sessionId);
            ps.setString(2, uuid == null ? null : uuid.toString());
            ps.setString(3, name);
            ps.setString(4, type.name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to insert replay actor for session " + sessionId, ex);
        }
        return -1;
    }

    public CompletableFuture<Integer> ensureActorAsync(int sessionId, UUID uuid, String name, ReplayActorType type) {
        return CompletableFuture.supplyAsync(() -> ensureActor(sessionId, uuid, name, type), executor);
    }

    public void recordAction(ReplayActionRecord record) {
        String sql = "INSERT INTO replay_actions (session_id, actor_id, tick, action_type, payload) VALUES (?,?,?,?,?)";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, record.getSessionId());
            ps.setInt(2, record.getActorId());
            ps.setInt(3, record.getTick());
            ps.setString(4, record.getActionType().name());
            ps.setString(5, record.getPayloadJson());
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to persist replay action for session " + record.getSessionId(), ex);
        }
    }

    public CompletableFuture<Void> recordActionAsync(ReplayActionRecord record) {
        return CompletableFuture.runAsync(() -> recordAction(record), executor);
    }

    public List<ReplaySessionRecord> fetchSessionsForReport(int reportId) {
        List<ReplaySessionRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM replay_sessions WHERE report_id = ? ORDER BY id ASC";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, reportId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(ReplaySessionRecord.fromResultSet(rs));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch replay sessions for report " + reportId, ex);
        }
        return list;
    }

    public List<ReplayActorRecord> fetchActors(int sessionId) {
        List<ReplayActorRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM replay_actors WHERE session_id = ?";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(ReplayActorRecord.fromResultSet(rs));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch replay actors for session " + sessionId, ex);
        }
        return list;
    }

    public List<ReplayActionRecord> fetchActions(int sessionId) {
        List<ReplayActionRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM replay_actions WHERE session_id = ? ORDER BY tick ASC, id ASC";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(ReplayActionRecord.fromResultSet(rs));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch replay actions for session " + sessionId, ex);
        }
        return list;
    }

    public Optional<ReplaySessionRecord> findSessionByReport(int reportId) {
        String sql = "SELECT * FROM replay_sessions WHERE report_id = ? ORDER BY id DESC LIMIT 1";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, reportId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(ReplaySessionRecord.fromResultSet(rs));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to find replay session for report " + reportId, ex);
        }
        return Optional.empty();
    }

    public boolean hasReplayData(int reportId) {
        String sql = "SELECT 1 FROM replay_actions a JOIN replay_sessions s ON a.session_id = s.id WHERE s.report_id = ? LIMIT 1";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, reportId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to check replay data for report " + reportId, ex);
        }
        return false;
    }

    public void deleteSessionsForReport(int reportId) {
        String sql = "DELETE FROM replay_sessions WHERE report_id = ?";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, reportId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete replay sessions for report " + reportId, ex);
        }
    }

    public void appendNote(int sessionId, String note) {
        String sql = "UPDATE replay_sessions SET notes = ? WHERE id = ?";
        try (Connection c = Database.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, note);
            ps.setInt(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to update replay session note for session " + sessionId, ex);
        }
    }

    public int recordActorSnapshot(int sessionId, UUID uuid, String name, ReplayActorType type, JsonObject snapshotPayload) {
        int actorId = ensureActor(sessionId, uuid, name, type);
        if (actorId == -1) {
            return -1;
        }
        recordAction(ReplayActionRecord.of(sessionId, actorId, 0, ReplayActionType.STATE_CHANGE, snapshotPayload));
        return actorId;
    }

    public CompletableFuture<Integer> recordActorSnapshotAsync(int sessionId, UUID uuid, String name, ReplayActorType type, JsonObject snapshotPayload) {
        return ensureActorAsync(sessionId, uuid, name, type).thenApply(actorId -> {
            if (actorId == -1) {
                return -1;
            }
            recordAction(ReplayActionRecord.of(sessionId, actorId, 0, ReplayActionType.STATE_CHANGE, snapshotPayload));
            return actorId;
        });
    }
}
