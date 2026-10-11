package dev.zonely.whiteeffect.esports.model;

import com.google.gson.JsonObject;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class EsportsPotionEffectSpec {

    private final String type;
    private final int durationTicks;
    private final int amplifier;
    private final boolean ambient;
    private final boolean particles;

    public EsportsPotionEffectSpec(String type, int durationTicks, int amplifier, boolean ambient, boolean particles) {
        this.type = type;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
        this.ambient = ambient;
        this.particles = particles;
    }

    public static EsportsPotionEffectSpec fromJson(JsonObject json) {
        if (json == null || !json.has("type")) {
            return null;
        }
        String type = json.get("type").getAsString();
        int duration = json.has("durationTicks") ? json.get("durationTicks").getAsInt()
                : (json.has("durationSeconds") ? json.get("durationSeconds").getAsInt() * 20 : 20 * 60);
        int amplifier = json.has("amplifier") ? json.get("amplifier").getAsInt() : 0;
        boolean ambient = json.has("ambient") && json.get("ambient").getAsBoolean();
        boolean particles = !json.has("particles") || json.get("particles").getAsBoolean();
        return new EsportsPotionEffectSpec(type, duration, amplifier, ambient, particles);
    }

    public PotionEffect toBukkitEffect() {
        PotionEffectType effectType = PotionEffectType.getByName(type);
        if (effectType == null) {
            return null;
        }
        return new PotionEffect(effectType, Math.max(20, durationTicks), Math.max(0, amplifier), ambient, particles);
    }

    public String getType() {
        return type;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public int getAmplifier() {
        return amplifier;
    }

    public boolean isAmbient() {
        return ambient;
    }

    public boolean hasParticles() {
        return particles;
    }
}
