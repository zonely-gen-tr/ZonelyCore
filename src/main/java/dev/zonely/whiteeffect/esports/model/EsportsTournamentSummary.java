package dev.zonely.whiteeffect.esports.model;

import com.google.gson.JsonObject;

public final class EsportsTournamentSummary {

    private final int id;
    private final String name;
    private final String slug;
    private final String state;
    private final String eventType;
    private final String startsAt;
    private final String endsAt;

    public EsportsTournamentSummary(int id, String name, String slug, String state, String eventType,
                                    String startsAt, String endsAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.state = state;
        this.eventType = eventType;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public static EsportsTournamentSummary fromJson(JsonObject object) {
        if (object == null) {
            return null;
        }
        int id = object.has("id") ? object.get("id").getAsInt() : 0;
        String name = object.has("name") && !object.get("name").isJsonNull()
                ? object.get("name").getAsString() : "";
        String slug = object.has("slug") && !object.get("slug").isJsonNull()
                ? object.get("slug").getAsString() : "";
        String state = object.has("state") && !object.get("state").isJsonNull()
                ? object.get("state").getAsString() : "";
        String eventType = object.has("eventType") && !object.get("eventType").isJsonNull()
                ? object.get("eventType").getAsString()
                : (object.has("event_type") && !object.get("event_type").isJsonNull()
                ? object.get("event_type").getAsString() : "");
        String startsAt = object.has("startsAt") && !object.get("startsAt").isJsonNull()
                ? object.get("startsAt").getAsString()
                : object.has("starts_at") && !object.get("starts_at").isJsonNull()
                ? object.get("starts_at").getAsString() : null;
        String endsAt = object.has("endsAt") && !object.get("endsAt").isJsonNull()
                ? object.get("endsAt").getAsString()
                : object.has("ends_at") && !object.get("ends_at").isJsonNull()
                ? object.get("ends_at").getAsString() : null;
        return new EsportsTournamentSummary(id, name, slug, state, eventType, startsAt, endsAt);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getState() {
        return state;
    }

    public String getEventType() {
        return eventType;
    }

    public String getStartsAt() {
        return startsAt;
    }

    public String getEndsAt() {
        return endsAt;
    }
}
