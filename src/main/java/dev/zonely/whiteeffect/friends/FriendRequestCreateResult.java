package dev.zonely.whiteeffect.friends;

public final class FriendRequestCreateResult {

    public enum Status {
        CREATED,
        AUTO_ACCEPTED,
        ALREADY_FRIENDS,
        ALREADY_PENDING
    }

    private final Status status;
    private final long requestId;

    public FriendRequestCreateResult(Status status, long requestId) {
        this.status = status;
        this.requestId = requestId;
    }

    public Status getStatus() {
        return status;
    }

    public long getRequestId() {
        return requestId;
    }
}
