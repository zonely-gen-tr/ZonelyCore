package dev.zonely.whiteeffect.replay.advanced;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class ReplaySessionRecord {

    private final int id;
    private final int reportId;
    private final String world;
    private final long startedAt;
    private final int durationMs;
    private final String notes;

    public ReplaySessionRecord(int id, int reportId, String world, long startedAt, int durationMs, String notes) {
        this.id = id;
        this.reportId = reportId;
        this.world = world;
        this.startedAt = startedAt;
        this.durationMs = durationMs;
        this.notes = notes;
    }

    public int getId() {
        return id;
    }

    public int getReportId() {
        return reportId;
    }

    public String getWorld() {
        return world;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public String getNotes() {
        return notes;
    }

    public static ReplaySessionRecord fromResultSet(ResultSet rs) throws SQLException {
        return new ReplaySessionRecord(
                rs.getInt("id"),
                rs.getInt("report_id"),
                rs.getString("world"),
                rs.getLong("started_at"),
                rs.getInt("duration_ms"),
                rs.getString("notes")
        );
    }
}
