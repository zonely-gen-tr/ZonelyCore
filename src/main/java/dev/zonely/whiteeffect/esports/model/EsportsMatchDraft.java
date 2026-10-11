package dev.zonely.whiteeffect.esports.model;

import com.google.gson.JsonObject;

public final class EsportsMatchDraft {

    private final int round;
    private final int slot;
    private final Integer clubAId;
    private final Integer clubBId;
    private final String state;
    private final JsonObject metadata;

    public EsportsMatchDraft(int round,
                             int slot,
                             Integer clubAId,
                             Integer clubBId,
                             String state,
                             JsonObject metadata) {
        this.round = round;
        this.slot = slot;
        this.clubAId = clubAId;
        this.clubBId = clubBId;
        this.state = state == null ? "pending" : state;
        this.metadata = metadata;
    }

    public int getRound() {
        return round;
    }

    public int getSlot() {
        return slot;
    }

    public Integer getClubAId() {
        return clubAId;
    }

    public Integer getClubBId() {
        return clubBId;
    }

    public String getState() {
        return state;
    }

    public JsonObject getMetadata() {
        return metadata;
    }
}
