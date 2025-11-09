package dev.zonely.whiteeffect.support;

import java.util.HashMap;
import java.util.Map;

final class SupportStaffSession {

   
    private final Map<Integer, Integer> ticketProgress = new HashMap<>();

    private final Map<Integer, Long> lastAlert = new HashMap<>();
    private int activeTicketId = -1;
    private int activeTicketPage = 0;
    private int activePageCount = 1;

    public int getKnownMessageId(int ticketId) {
        return ticketProgress.getOrDefault(ticketId, 0);
    }

    public void setKnownMessageId(int ticketId, int messageId) {
        ticketProgress.put(ticketId, messageId);
    }

    public long getLastAlert(int ticketId) {
        return lastAlert.getOrDefault(ticketId, 0L);
    }

    public void setLastAlert(int ticketId, long timestamp) {
        lastAlert.put(ticketId, timestamp);
    }

    public int getActiveTicketId() {
        return activeTicketId;
    }

    public void setActiveTicketId(int activeTicketId) {
        this.activeTicketId = activeTicketId;
    }

    public int getActiveTicketPage() {
        return activeTicketPage;
    }

    public void setActiveTicketPage(int activeTicketPage) {
        this.activeTicketPage = Math.max(0, activeTicketPage);
    }

    public int getActivePageCount() {
        return activePageCount;
    }

    public void setActivePageCount(int activePageCount) {
        this.activePageCount = Math.max(1, activePageCount);
    }
}
