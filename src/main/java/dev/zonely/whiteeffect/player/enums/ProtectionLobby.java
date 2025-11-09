package dev.zonely.whiteeffect.player.enums;

public enum ProtectionLobby {
    AKTIF,
    DEAKTIF;

    private static final ProtectionLobby[] VALUES = values();

    public static ProtectionLobby getByOrdinal(long ordinal) {
        return ordinal < 2L && ordinal > -1L ? VALUES[(int) ordinal] : null;
    }

    public String getInkSack() {
        return this == AKTIF ? "13" : "14";
    }

    public String getName() {
        return this == AKTIF ? "&aENABLED" : "&cDISABLED";
    }

    public ProtectionLobby next() {
        return this == DEAKTIF ? AKTIF : DEAKTIF;
    }
}
