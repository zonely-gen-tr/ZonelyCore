package dev.zonely.whiteeffect.report;

public enum ReportPunishAction {
    BAN("ban"),
    MUTE("mute"),
    WARNING("warning"),
    KICK("kick"),
    NONE("none");

    private final String id;

    ReportPunishAction(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static ReportPunishAction fromId(String id) {
        for (ReportPunishAction action : values()) {
            if (action.id.equalsIgnoreCase(id)) {
                return action;
            }
        }
        return null;
    }
}
