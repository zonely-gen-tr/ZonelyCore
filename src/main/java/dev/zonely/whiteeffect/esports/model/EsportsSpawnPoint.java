package dev.zonely.whiteeffect.esports.model;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class EsportsSpawnPoint {

    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public EsportsSpawnPoint(String worldName, double x, double y, double z, float yaw, float pitch) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static EsportsSpawnPoint fromJson(JsonObject json) {
        if (json == null) {
            return null;
        }
        String world = json.has("world") && !json.get("world").isJsonNull()
                ? json.get("world").getAsString()
                : Bukkit.getWorlds().isEmpty() ? "world" : Bukkit.getWorlds().get(0).getName();
        double x = json.has("x") ? json.get("x").getAsDouble() : 0.0D;
        double y = json.has("y") ? json.get("y").getAsDouble() : 0.0D;
        double z = json.has("z") ? json.get("z").getAsDouble() : 0.0D;
        float yaw = json.has("yaw") ? json.get("yaw").getAsFloat() : 0.0F;
        float pitch = json.has("pitch") ? json.get("pitch").getAsFloat() : 0.0F;
        return new EsportsSpawnPoint(world, x, y, z, yaw, pitch);
    }

    public Location toLocation(World fallbackWorld) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = fallbackWorld != null ? fallbackWorld : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
        }
        if (world == null) {
            return null;
        }
        Location location = new Location(world, x, y, z, yaw, pitch);
        return location;
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("world", worldName);
        object.addProperty("x", x);
        object.addProperty("y", y);
        object.addProperty("z", z);
        object.addProperty("yaw", yaw);
        object.addProperty("pitch", pitch);
        return object;
    }

    public static EsportsSpawnPoint fromLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new EsportsSpawnPoint(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public static JsonObject toJson(Location location) {
        EsportsSpawnPoint point = fromLocation(location);
        return point == null ? null : point.toJson();
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
