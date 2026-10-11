package dev.zonely.whiteeffect.friends;

import java.util.UUID;

public final class FriendMailboxEntry {

    private final long id;
    private final UUID targetUuid;
    private final String kind;
    private final String payload;

    public FriendMailboxEntry(long id, UUID targetUuid, String kind, String payload) {
        this.id = id;
        this.targetUuid = targetUuid;
        this.kind = kind;
        this.payload = payload;
    }

    public long getId() {
        return id;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getKind() {
        return kind;
    }

    public String getPayload() {
        return payload;
    }
}
