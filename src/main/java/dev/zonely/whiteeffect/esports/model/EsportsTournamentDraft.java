package dev.zonely.whiteeffect.esports.model;

import com.google.gson.JsonObject;

import java.time.LocalDateTime;

public final class EsportsTournamentDraft {

    private final String name;
    private final String slug;
    private final String eventType;
    private final String state;
    private final String description;
    private final LocalDateTime startsAt;
    private final LocalDateTime endsAt;
    private final JsonObject settings;
    private final JsonObject rewards;
    private final JsonObject featuredStream;

    public EsportsTournamentDraft(String name,
                                  String slug,
                                  String eventType,
                                  String state,
                                  String description,
                                  LocalDateTime startsAt,
                                  LocalDateTime endsAt,
                                  JsonObject settings,
                                  JsonObject rewards,
                                  JsonObject featuredStream) {
        this.name = name;
        this.slug = slug;
        this.eventType = eventType;
        this.state = state;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.settings = settings;
        this.rewards = rewards;
        this.featuredStream = featuredStream;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getEventType() {
        return eventType;
    }

    public String getState() {
        return state;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public JsonObject getSettings() {
        return settings;
    }

    public JsonObject getRewards() {
        return rewards;
    }

    public JsonObject getFeaturedStream() {
        return featuredStream;
    }
}
