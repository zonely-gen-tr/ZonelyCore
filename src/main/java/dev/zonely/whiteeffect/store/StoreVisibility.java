package dev.zonely.whiteeffect.store;

import java.util.Locale;
import org.json.JSONObject;

public final class StoreVisibility {
    private StoreVisibility() { }

    public static boolean isVisible(JSONObject settings) {
        if (settings == null) return false;
        Object value = settings.opt("showInGame");
        if (Boolean.TRUE.equals(value)) return true;
        if (value == null || JSONObject.NULL.equals(value)) return false;
        String flag = value.toString().trim().toLowerCase(Locale.ROOT);
        return "1".equals(flag) || "true".equals(flag) || "on".equals(flag);
    }
}
