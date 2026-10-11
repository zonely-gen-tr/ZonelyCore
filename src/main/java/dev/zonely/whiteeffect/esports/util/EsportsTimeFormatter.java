package dev.zonely.whiteeffect.esports.util;

public final class EsportsTimeFormatter {

    private EsportsTimeFormatter() {
    }

    public static String formatCompact(long seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long sec = seconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, sec);
        }
        return String.format("%02d:%02d", minutes, sec);
    }
}
