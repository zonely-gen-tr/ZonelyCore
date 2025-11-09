package dev.zonely.whiteeffect.utils;

import java.util.ArrayList;
import java.util.List;


public final class CommandMessageUtils {

    private CommandMessageUtils() {
    }

    public static List<String> withSignature(List<String> source) {
        return (source == null) ? new ArrayList<>() : new ArrayList<>(source);
    }
}
