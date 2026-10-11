package dev.zonely.whiteeffect.friends;

public final class FriendEntry {

    private final FriendIdentity identity;
    private final boolean online;
    private final String server;
    private final long lastSeen;

    public FriendEntry(FriendIdentity identity, boolean online, String server, long lastSeen) {
        this.identity = identity;
        this.online = online;
        this.server = server;
        this.lastSeen = lastSeen;
    }

    public FriendIdentity getIdentity() {
        return identity;
    }

    public boolean isOnline() {
        return online;
    }

    public String getServer() {
        return server;
    }

    public long getLastSeen() {
        return lastSeen;
    }
}
