package dev.zonely.whiteeffect.clubs.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class ClubData {
    private final int id;
    private final String name;
    private final String tag;
    private final UUID ownerUuid;
    private final String ownerName;
    private final String colorHex;
    private final String bannerJson;
    private final Instant createdAt;
    private final List<ClubMember> members;

    public ClubData(int id,
                    String name,
                    String tag,
                    UUID ownerUuid,
                    String ownerName,
                    String colorHex,
                    String bannerJson,
                    Instant createdAt,
                    List<ClubMember> members) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.colorHex = colorHex;
        this.bannerJson = bannerJson;
        this.createdAt = createdAt;
        this.members = members == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(members));
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

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getColorHex() {
        return colorHex;
    }

    public String getBannerJson() {
        return bannerJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ClubMember> getMembers() {
        return members;
    }
}
