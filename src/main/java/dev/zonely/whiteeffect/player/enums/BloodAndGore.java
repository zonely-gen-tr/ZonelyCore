package dev.zonely.whiteeffect.player.enums;

public enum BloodAndGore {
    AKTIF,
    DEAKTIF;

    private static final BloodAndGore[] VALUES = values();

    public static BloodAndGore getByOrdinal(long ordinal) {
        return ordinal < 2L && ordinal > -1L ? VALUES[(int) ordinal] : null;
    }

    public String getInkSack() {
        return this == AKTIF ? "13" : "14";
    }

    public String getName() {
        return this == AKTIF ? "&aENABLED" : "&cDISABLED";
    }

    public BloodAndGore next() {
        return this == DEAKTIF ? AKTIF : DEAKTIF;
    }
}
