package dev.zonely.whiteeffect.esports.model;

import com.google.gson.JsonObject;
import dev.zonely.whiteeffect.utils.JsonUtils;

import java.util.Collections;
import java.util.List;

public final class EsportsTournamentData {

    private final int id;
    private final String name;
    private final String slug;
    private final String state;
    private final String eventType;
    private final String startsAt;
    private final String endsAt;
    private final JsonObject settings;
    private final JsonObject rewards;
    private final List<EsportsPotionEffectSpec> potionEffects;
    private final List<EsportsClubEntry> clubs;
    private final List<EsportsMatchData> matches;
    private final List<EsportsSpawnSlot> spawnSlots;
    private final JsonObject defaultKit;

    public EsportsTournamentData(
            int id,
            String name,
            String slug,
            String state,
            String eventType,
            String startsAt,
            String endsAt,
            JsonObject settings,
            JsonObject rewards,
            List<EsportsPotionEffectSpec> potionEffects,
            List<EsportsClubEntry> clubs,
            List<EsportsMatchData> matches,
            List<EsportsSpawnSlot> spawnSlots,
            JsonObject defaultKit
    ) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.state = state;
        this.eventType = eventType;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.settings = settings;
        this.rewards = rewards;
        this.potionEffects = potionEffects == null ? Collections.emptyList() : Collections.unmodifiableList(potionEffects);
        this.clubs = clubs == null ? Collections.emptyList() : Collections.unmodifiableList(clubs);
        this.matches = matches == null ? Collections.emptyList() : Collections.unmodifiableList(matches);
        this.spawnSlots = spawnSlots == null ? Collections.emptyList() : Collections.unmodifiableList(spawnSlots);
        this.defaultKit = JsonUtils.deepCopy(defaultKit);
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

    public JsonObject getSettings() {
        return settings;
    }

    public JsonObject getRewards() {
        return rewards;
    }

    public List<EsportsPotionEffectSpec> getPotionEffects() {
        return potionEffects;
    }

    public List<EsportsClubEntry> getClubs() {
        return clubs;
    }

    public List<EsportsMatchData> getMatches() {
        return matches;
    }

    public List<EsportsSpawnSlot> getSpawnSlots() {
        return spawnSlots;
    }

    public JsonObject getDefaultKit() {
        return JsonUtils.deepCopy(defaultKit);
    }

    public EsportsSpawnSlot getSpawnSlotByIndex(int slotIndex) {
        if (slotIndex < 0 || spawnSlots.isEmpty()) {
            return null;
        }
        for (EsportsSpawnSlot slot : spawnSlots) {
            if (slot.getSlotIndex() == slotIndex) {
                return slot;
            }
        }
        return null;
    }
}
