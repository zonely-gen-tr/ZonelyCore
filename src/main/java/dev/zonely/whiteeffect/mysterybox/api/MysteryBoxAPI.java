package dev.zonely.whiteeffect.mysterybox.api;

import dev.zonely.whiteeffect.player.Profile;


public final class MysteryBoxAPI {
    private MysteryBoxAPI() {}

    public static int getMysteryBoxes(Profile profile) {
        if (profile == null) return 0;
        try {
            return 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
