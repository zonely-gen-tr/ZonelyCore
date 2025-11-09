package dev.zonely.whiteeffect.support;

import java.sql.Timestamp;

public final class SupportMessage {

    private final int id;
    private final int ticketId;
    private final Integer userId;
    private final String authorName;
    private final String message;
    private final int location;
    private final Timestamp createdAt;

    public SupportMessage(int id,
                          int ticketId,
                          Integer userId,
                          String authorName,
                          String message,
                          int location,
                          Timestamp createdAt) {
        this.id = id;
        this.ticketId = ticketId;
        this.userId = userId;
        this.authorName = authorName;
        this.message = message;
        this.location = location;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getTicketId() {
        return ticketId;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getMessage() {
        return message;
    }

    public int getLocation() {
        return location;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public boolean isFromCustomer() {
        return location == 2;
    }
}
