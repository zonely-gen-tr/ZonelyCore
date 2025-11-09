package dev.zonely.whiteeffect.player.enums;

public enum PrivateMessages {
    GENEL,
    HICBIRI;

    private static final PrivateMessages[] VALUES = values();

    public static PrivateMessages getByOrdinal(long ordinal) {
        return ordinal < 2L && ordinal > -1L ? VALUES[(int) ordinal] : null;
    }

    public String getInkSack() {
        return this == GENEL ? "13" : "14";
    }

    public String getName() {
        return this == GENEL ? "&aENABLED" : "&cDISABLED";
    }

    public PrivateMessages next() {
        return this == HICBIRI ? GENEL : HICBIRI;
    }
}
