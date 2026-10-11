package dev.zonely.whiteeffect.esports.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.zonely.whiteeffect.utils.JsonUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EsportsMatchData {

    private final int id;
    private final int tournamentId;
    private final int round;
    private final Integer clubAId;
    private final Integer clubBId;
    private final String state;
    private final String scheduledAt;
    private final String startedAt;
    private final String endedAt;
    private final JsonObject metadata;
    private final String replayUrl;
    private final Integer winnerClubId;
    private final int bracketSlot;
    private final int sourceRound;
    private final List<Integer> sourceSlots;

    public EsportsMatchData(int id,
                            int tournamentId,
                            int round,
                            Integer clubAId,
                            Integer clubBId,
                            String state,
                            String scheduledAt,
                            String startedAt,
                            String endedAt,
                            JsonObject metadata,
                            String replayUrl,
                            Integer winnerClubId) {
        this.id = id;
        this.tournamentId = tournamentId;
        this.round = round;
        this.clubAId = clubAId;
        this.clubBId = clubBId;
        this.state = state;
        this.scheduledAt = scheduledAt;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.metadata = JsonUtils.deepCopy(metadata);
        this.replayUrl = replayUrl;
        this.winnerClubId = winnerClubId;
        int slot = -1;
        int parentRound = -1;
        List<Integer> parents = new ArrayList<>();
        if (metadata != null) {
            if (metadata.has("slot")) {
                slot = metadata.get("slot").getAsInt();
            }
            if (metadata.has("sourceRound")) {
                parentRound = metadata.get("sourceRound").getAsInt();
            }
            if (metadata.has("sourceSlots") && metadata.get("sourceSlots").isJsonArray()) {
                JsonArray array = metadata.getAsJsonArray("sourceSlots");
                for (JsonElement element : array) {
                    if (element != null && element.isJsonPrimitive()) {
                        parents.add(element.getAsInt());
                    }
                }
            }
        }
        this.bracketSlot = slot;
        this.sourceRound = parentRound;
        this.sourceSlots = parents.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(parents);
    }

    public int getId() {
        return id;
    }

    public int getTournamentId() {
        return tournamentId;
    }

    public int getRound() {
        return round;
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

    public String getScheduledAt() {
        return scheduledAt;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getEndedAt() {
        return endedAt;
    }

    public JsonObject getMetadata() {
        return JsonUtils.deepCopy(metadata);
    }

    public String getReplayUrl() {
        return replayUrl;
    }

    public Integer getWinnerClubId() {
        return winnerClubId;
    }

    public int getBracketSlot() {
        return bracketSlot;
    }

    public int getSourceRound() {
        return sourceRound;
    }

    public List<Integer> getSourceSlots() {
        return sourceSlots;
    }
}
