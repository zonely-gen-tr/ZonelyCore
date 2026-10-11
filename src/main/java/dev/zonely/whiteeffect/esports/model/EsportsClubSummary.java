package dev.zonely.whiteeffect.esports.model;

import com.google.gson.JsonObject;

public final class EsportsClubSummary {

    private final int id;
    private final String name;
    private final String tag;
    private final Integer seed;
    private final String status;
    private final Integer placement;

    public EsportsClubSummary(int id, String name, String tag, Integer seed, String status, Integer placement) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.seed = seed;
        this.status = status;
        this.placement = placement;
    }

    public static EsportsClubSummary fromJson(JsonObject object) {
        if (object == null) {
            return null;
        }
        int id = object.has("id") ? object.get("id").getAsInt() : 0;
        String name = object.has("name") && !object.get("name").isJsonNull()
                ? object.get("name").getAsString() : "";
        String tag = object.has("tag") && !object.get("tag").isJsonNull()
                ? object.get("tag").getAsString() : "";
        Integer seed = object.has("seed") && !object.get("seed").isJsonNull()
                ? object.get("seed").getAsInt() : null;
        String status = object.has("status") && !object.get("status").isJsonNull()
                ? object.get("status").getAsString() : "";
        Integer placement = object.has("placement") && !object.get("placement").isJsonNull()
                ? object.get("placement").getAsInt() : null;
        return new EsportsClubSummary(id, name, tag, seed, status, placement);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public Integer getSeed() {
        return seed;
    }

    public String getStatus() {
        return status;
    }

    public Integer getPlacement() {
        return placement;
    }
}
