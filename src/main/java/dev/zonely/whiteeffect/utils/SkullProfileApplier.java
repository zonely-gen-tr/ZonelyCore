package dev.zonely.whiteeffect.utils;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;
import org.json.JSONObject;

final class SkullProfileApplier {
    private static final Class<?> PROFILE = findClass("org.bukkit.profile.PlayerProfile");

    private SkullProfileApplier() { }

    static boolean apply(SkullMeta skull, GameProfile source) {
        if (PROFILE == null) return false;
        try {
            UUID id = source.getId() == null ? UUID.randomUUID() : source.getId();
            String name = source.getName();
            if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) name = "ZonelyHead";
            Object profile = Bukkit.class.getMethod("createPlayerProfile", UUID.class, String.class).invoke(null, id, name);
            Class<?> texturesType = Class.forName("org.bukkit.profile.PlayerTextures");
            Object textures = PROFILE.getMethod("getTextures").invoke(profile);
            for (Object property : source.getProperties().get("textures")) {
                Method value;
                try { value = property.getClass().getMethod("value"); }
                catch (NoSuchMethodException older) { value = property.getClass().getMethod("getValue"); }
                JSONObject data = new JSONObject(new String(Base64.getDecoder().decode(String.valueOf(value.invoke(property))), StandardCharsets.UTF_8));
                JSONObject maps = data.optJSONObject("textures");
                if (maps == null) continue;
                JSONObject skin = maps.optJSONObject("SKIN");
                if (skin != null) {
                    Class<?> modelType = Class.forName("org.bukkit.profile.PlayerTextures$SkinModel");
                    JSONObject metadata = skin.optJSONObject("metadata");
                    String model = metadata != null && "slim".equalsIgnoreCase(metadata.optString("model")) ? "SLIM" : "CLASSIC";
                    Object skinModel = modelType.getField(model).get(null);
                    texturesType.getMethod("setSkin", URL.class, modelType).invoke(textures, new URL(skin.getString("url")), skinModel);
                }
                JSONObject cape = maps.optJSONObject("CAPE");
                if (cape != null) texturesType.getMethod("setCape", URL.class).invoke(textures, new URL(cape.getString("url")));
                break;
            }
            PROFILE.getMethod("setTextures", texturesType).invoke(profile, textures);
            SkullMeta.class.getMethod("setOwnerProfile", PROFILE).invoke(skull, profile);
        } catch (ReflectiveOperationException | java.io.IOException | org.json.JSONException | IllegalArgumentException failure) {
            // Leave invalid texture data unset instead of writing an inconsistent native profile.
        }
        return true;
    }

    private static Class<?> findClass(String name) {
        try { return Class.forName(name); }
        catch (ClassNotFoundException unavailable) { return null; }
    }
}
