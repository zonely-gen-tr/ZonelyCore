package dev.zonely.whiteeffect.report;

import java.sql.Timestamp;
import java.util.UUID;

public class Report {
    private final int id;
    private final String reporter;
    private final UUID reporterUuid;
    private final String target;
    private final UUID targetUuid;
    private final String category;
    private final String reason;
    private final String content;
    private final String server;
    private final Timestamp createdAt;
    private final String replayKey;
    private ReportStatus status;
    private String resolvedBy;
    private Timestamp resolvedAt;
    private final String resolvedAction;
    private final String resolvedReason;
    private final String resolvedDuration;

    public Report(int id, String reporter, UUID reporterUuid, String target, UUID targetUuid, String category, String reason, String content, String server,
                  Timestamp createdAt, ReportStatus status, String replayKey,
                  String resolvedBy, Timestamp resolvedAt,
                  String resolvedAction, String resolvedReason, String resolvedDuration) {
        this.id = id;
        this.reporter = reporter;
        this.reporterUuid = reporterUuid;
        this.target = target;
        this.targetUuid = targetUuid;
        this.category = category;
        this.reason = reason;
        this.content = content;
        this.server = server;
        this.createdAt = createdAt;
        this.status = status;
        this.replayKey = replayKey;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
        this.resolvedAction = resolvedAction;
        this.resolvedReason = resolvedReason;
        this.resolvedDuration = resolvedDuration;
    }

    public int getId() { return id; }
    public String getReporter() { return reporter; }
    public UUID getReporterUuid() { return reporterUuid; }
    public String getTarget() { return target; }
    public UUID getTargetUuid() { return targetUuid; }
    public String getCategory() { return category; }
    public String getReason() { return reason; }
    public String getContent() { return content; }
    public String getServer() { return server; }
    public Timestamp getCreatedAt() { return createdAt; }
    public ReportStatus getStatus() { return status; }
    public String getReplayKey() { return replayKey; }
    public boolean hasReplay() { return replayKey != null && !replayKey.isEmpty(); }
    public String getResolvedBy() { return resolvedBy; }
    public Timestamp getResolvedAt() { return resolvedAt; }
    public String getResolvedAction() { return resolvedAction; }
    public String getResolvedReason() { return resolvedReason; }
    public String getResolvedDuration() { return resolvedDuration; }

    public void setStatus(ReportStatus status) { this.status = status; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public void setResolvedAt(Timestamp resolvedAt) { this.resolvedAt = resolvedAt; }
}
