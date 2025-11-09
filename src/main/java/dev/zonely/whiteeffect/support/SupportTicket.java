package dev.zonely.whiteeffect.support;

import java.sql.Timestamp;
import java.util.Objects;

public final class SupportTicket {

    private final int id;
    private final Integer userId;
    private final String userName;
    private final String ipAddress;
    private final String title;
    private final String initialMessage;
    private final int categoryId;
    private final SupportStatus status;
    private final boolean read;
    private final Timestamp updatedAt;
    private final Timestamp createdAt;

    public SupportTicket(int id,
                         Integer userId,
                         String userName,
                         String ipAddress,
                         String title,
                         String initialMessage,
                         int categoryId,
                         SupportStatus status,
                         boolean read,
                         Timestamp updatedAt,
                         Timestamp createdAt) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.ipAddress = ipAddress;
        this.title = title;
        this.initialMessage = initialMessage;
        this.categoryId = categoryId;
        this.status = status;
        this.read = read;
        this.updatedAt = updatedAt;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getTitle() {
        return title;
    }

    public String getInitialMessage() {
        return initialMessage;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public SupportStatus getStatus() {
        return status;
    }

    public boolean isRead() {
        return read;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public boolean isClosed() {
        return status.isClosed();
    }

    public boolean isAwaitingStaff() {
        return status.isAwaitingStaff();
    }

    public SupportTicket withStatus(SupportStatus newStatus, boolean readFlag, Timestamp updatedTimestamp) {
        return new SupportTicket(
                id,
                userId,
                userName,
                ipAddress,
                title,
                initialMessage,
                categoryId,
                newStatus,
                readFlag,
                updatedTimestamp,
                createdAt
        );
    }

    @Override
    public String toString() {
        return "SupportTicket{" +
                "id=" + id +
                ", userId=" + userId +
                ", status=" + status +
                ", updatedAt=" + updatedAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupportTicket that = (SupportTicket) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
