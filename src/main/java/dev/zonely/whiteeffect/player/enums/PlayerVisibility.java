package dev.zonely.whiteeffect.player.enums;

public enum PlayerVisibility {
    GENEL,
    HICBIRI;

    private static final PlayerVisibility[] VALUES = values();

    public static PlayerVisibility getByOrdinal(long ordinal) {
        return ordinal < 2L && ordinal > -1L ? VALUES[(int) ordinal] : null;
    }

    public String getInkSack() {
        return this == GENEL ? "13" : "14";
    }

    public String getName() {
        return this == GENEL ? "&aENABLED" : "&cDISABLED";
    }

    public PlayerVisibility next() {
        return this == HICBIRI ? GENEL : HICBIRI;
    }
}
