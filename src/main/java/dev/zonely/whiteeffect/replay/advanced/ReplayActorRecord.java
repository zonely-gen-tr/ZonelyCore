package dev.zonely.whiteeffect.replay.advanced;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

public final class ReplayActorRecord {

    private final int id;
    private final int sessionId;
    private final UUID uuid;
    private final String name;
    private final ReplayActorType actorType;

    public ReplayActorRecord(int id, int sessionId, UUID uuid, String name, ReplayActorType actorType) {
        this.id = id;
        this.sessionId = sessionId;
        this.uuid = uuid;
        this.name = Objects.requireNonNull(name, "name");
        this.actorType = Objects.requireNonNull(actorType, "actorType");
    }

    public int getId() {
        return id;
    }

    public int getSessionId() {
        return sessionId;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public ReplayActorType getActorType() {
        return actorType;
    }

    public static ReplayActorRecord fromResultSet(ResultSet rs) throws SQLException {
        String uuidRaw = rs.getString("uuid");
        UUID uuid = null;
        if (uuidRaw != null && !uuidRaw.isEmpty()) {
            try {
                uuid = UUID.fromString(uuidRaw);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new ReplayActorRecord(
                rs.getInt("id"),
                rs.getInt("session_id"),
                uuid,
                rs.getString("name"),
                ReplayActorType.valueOf(rs.getString("type"))
        );
    }
}
