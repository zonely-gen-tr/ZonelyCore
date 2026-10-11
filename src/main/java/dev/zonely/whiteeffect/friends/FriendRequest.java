package dev.zonely.whiteeffect.friends;

import java.util.UUID;

public final class FriendRequest {

    private final long id;
    private final UUID requesterUuid;
    private final String requesterName;
    private final UUID targetUuid;
    private final String targetName;
    private final long createdAt;

    public FriendRequest(long id,
                         UUID requesterUuid,
                         String requesterName,
                         UUID targetUuid,
                         String targetName,
                         long createdAt) {
        this.id = id;
        this.requesterUuid = requesterUuid;
        this.requesterName = requesterName;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public UUID getRequesterUuid() {
        return requesterUuid;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
