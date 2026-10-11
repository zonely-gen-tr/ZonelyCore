package dev.zonely.whiteeffect.esports.model;

import com.google.gson.JsonObject;

public final class EsportsClubEntry {

    private final int clubId;
    private final String name;
    private final String tag;
    private final Integer seed;
    private final String status;
    private final Integer placement;
    private final String primaryColor;
    private final String secondaryColor;
    private final String accentColor;
    private final String iconUrl;
    private final EsportsSpawnPoint spawnPoint;
    private final JsonObject kitConfig;

    public EsportsClubEntry(
            int clubId,
            String name,
            String tag,
            Integer seed,
            String status,
            Integer placement,
            String primaryColor,
            String secondaryColor,
            String accentColor,
            String iconUrl,
            EsportsSpawnPoint spawnPoint,
            JsonObject kitConfig
    ) {
        this.clubId = clubId;
        this.name = name;
        this.tag = tag;
        this.seed = seed;
        this.status = status;
        this.placement = placement;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.accentColor = accentColor;
        this.iconUrl = iconUrl;
        this.spawnPoint = spawnPoint;
        this.kitConfig = kitConfig;
    }

    public int getClubId() {
        return clubId;
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

    public String getPrimaryColor() {
        return primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public EsportsSpawnPoint getSpawnPoint() {
        return spawnPoint;
    }

    public JsonObject getKitConfig() {
        return kitConfig;
    }
}
