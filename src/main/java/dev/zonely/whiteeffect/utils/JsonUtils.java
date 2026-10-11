package dev.zonely.whiteeffect.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class JsonUtils {

    private JsonUtils() {
    }

    public static JsonObject deepCopy(JsonObject source) {
        if (source == null) {
            return null;
        }
        JsonParser parser = new JsonParser();
        return parser.parse(source.toString()).getAsJsonObject();
    }
}
