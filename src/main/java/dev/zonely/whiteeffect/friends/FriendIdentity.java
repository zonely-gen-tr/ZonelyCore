package dev.zonely.whiteeffect.friends;

import java.util.UUID;

public final class FriendIdentity {

    private final UUID uuid;
    private final String name;

    public FriendIdentity(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }
}
