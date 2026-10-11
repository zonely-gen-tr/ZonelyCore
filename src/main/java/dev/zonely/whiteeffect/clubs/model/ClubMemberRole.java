package dev.zonely.whiteeffect.clubs.model;

public enum ClubMemberRole {
    OWNER,
    MANAGER,
    MEMBER;

    public boolean canManageMembers() {
        return this == OWNER || this == MANAGER;
    }
}
