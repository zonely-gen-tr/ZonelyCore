package dev.zonely.whiteeffect.replay.advanced;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public final class ReplayActionRecord {

    private static final Gson GSON = new Gson();

    private final long id;
    private final int sessionId;
    private final int actorId;
    private final int tick;
    private final ReplayActionType actionType;
    private final String payloadJson;

    public ReplayActionRecord(long id,
                              int sessionId,
                              int actorId,
                              int tick,
                              ReplayActionType actionType,
                              String payloadJson) {
        this.id = id;
        this.sessionId = sessionId;
        this.actorId = actorId;
        this.tick = tick;
        this.actionType = Objects.requireNonNull(actionType, "actionType");
        this.payloadJson = payloadJson == null ? "{}" : payloadJson;
    }

    public long getId() {
        return id;
    }

    public int getSessionId() {
        return sessionId;
    }

    public int getActorId() {
        return actorId;
    }

    public int getTick() {
        return tick;
    }

    public ReplayActionType getActionType() {
        return actionType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public JsonObject getPayload() {
        JsonElement element = GSON.fromJson(payloadJson, JsonElement.class);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return new JsonObject();
    }

    public static ReplayActionRecord fromResultSet(ResultSet rs) throws SQLException {
        return new ReplayActionRecord(
                rs.getLong("id"),
                rs.getInt("session_id"),
                rs.getInt("actor_id"),
                rs.getInt("tick"),
                ReplayActionType.valueOf(rs.getString("action_type")),
                rs.getString("payload")
        );
    }

    public static ReplayActionRecord of(int sessionId,
                                        int actorId,
                                        int tick,
                                        ReplayActionType type,
                                        JsonObject payload) {
        return new ReplayActionRecord(-1L, sessionId, actorId, tick, type,
                payload == null ? "{}" : GSON.toJson(payload));
    }

    public static ReplayActionRecord of(int sessionId,
                                        int actorId,
                                        int tick,
                                        ReplayActionType type,
                                        String payloadJson) {
        return new ReplayActionRecord(-1L, sessionId, actorId, tick, type, payloadJson);
    }
}
