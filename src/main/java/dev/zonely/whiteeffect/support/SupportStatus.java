package dev.zonely.whiteeffect.support;

import org.bukkit.ChatColor;

public enum SupportStatus {

    OPEN(1, ChatColor.RED + "Cevap Bekliyor"),
    ANSWERED(2, ChatColor.GREEN + "Cevaplandı"),
    AWAITING_CUSTOMER(3, ChatColor.GOLD + "Kullanıcı Yanıtı"),
    CLOSED(4, ChatColor.DARK_RED + "Kapalı");

    private final int id;
    private final String legacyLabel;

    SupportStatus(int id, String legacyLabel) {
        this.id = id;
        this.legacyLabel = legacyLabel;
    }

    public int getId() {
        return id;
    }

    public String getLegacyLabel() {
        return legacyLabel;
    }

    public boolean isClosed() {
        return this == CLOSED;
    }

    public boolean isAwaitingStaff() {
        return this == OPEN || this == AWAITING_CUSTOMER;
    }

    public static SupportStatus fromId(int id) {
        for (SupportStatus value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        return OPEN;
    }
}
