package dev.zonely.whiteeffect.clubs.model;

import java.time.Instant;
import java.util.UUID;

public final class ClubMember {
    private final int id;
    private final int clubId;
    private final UUID uniqueId;
    private final String username;
    private final ClubMemberRole role;
    private final Instant joinedAt;

    public ClubMember(int id,
                      int clubId,
                      UUID uniqueId,
                      String username,
                      ClubMemberRole role,
                      Instant joinedAt) {
        this.id = id;
        this.clubId = clubId;
        this.uniqueId = uniqueId;
        this.username = username;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public int getId() {
        return id;
    }

    public int getClubId() {
        return clubId;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public String getUsername() {
        return username;
    }

    public ClubMemberRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
