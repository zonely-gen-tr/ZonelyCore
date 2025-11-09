package dev.zonely.whiteeffect.support;

import java.time.Instant;

final class SupportPlayerSession {

    private SupportTicket ticket;
    private int lastKnownMessageId;
    private Instant lastReminderAt = Instant.EPOCH;

    SupportPlayerSession(SupportTicket ticket, int lastKnownMessageId) {
        this.ticket = ticket;
        this.lastKnownMessageId = lastKnownMessageId;
    }

    public SupportTicket getTicket() {
        return ticket;
    }

    public void setTicket(SupportTicket ticket) {
        this.ticket = ticket;
    }

    public int getLastKnownMessageId() {
        return lastKnownMessageId;
    }

    public void setLastKnownMessageId(int lastKnownMessageId) {
        this.lastKnownMessageId = lastKnownMessageId;
    }

    public Instant getLastReminderAt() {
        return lastReminderAt;
    }

    public void setLastReminderAt(Instant lastReminderAt) {
        this.lastReminderAt = lastReminderAt;
    }
}
