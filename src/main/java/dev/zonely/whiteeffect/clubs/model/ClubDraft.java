package dev.zonely.whiteeffect.clubs.model;

import java.util.UUID;

public final class ClubDraft {
    private final String name;
    private final String tag;
    private final String colorHex;
    private final String bannerJson;
    private final UUID ownerUuid;
    private final String ownerName;

    public ClubDraft(String name,
                     String tag,
                     String colorHex,
                     String bannerJson,
                     UUID ownerUuid,
                     String ownerName) {
        this.name = name;
        this.tag = tag;
        this.colorHex = colorHex;
        this.bannerJson = bannerJson;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public String getColorHex() {
        return colorHex;
    }

    public String getBannerJson() {
        return bannerJson;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }
}
