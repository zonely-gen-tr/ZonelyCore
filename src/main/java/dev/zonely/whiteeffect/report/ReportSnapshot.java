package dev.zonely.whiteeffect.report;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class ReportSnapshot {

    private final int reportId;
    private final double health;
    private final int food;
    private final float saturation;
    private final int level;
    private final float exp;
    private final float walkSpeed;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final long capturedAt;

    public ReportSnapshot(int reportId, double health, int food, float saturation,
                          int level, float exp, float walkSpeed,
                          String world, double x, double y, double z,
                          float yaw, float pitch, long capturedAt) {
        this.reportId = reportId;
        this.health = health;
        this.food = food;
        this.saturation = saturation;
        this.level = level;
        this.exp = exp;
        this.walkSpeed = walkSpeed;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.capturedAt = capturedAt;
    }

    public int getReportId() {
        return reportId;
    }

    public double getHealth() {
        return health;
    }

    public int getFood() {
        return food;
    }

    public float getSaturation() {
        return saturation;
    }

    public int getLevel() {
        return level;
    }

    public float getExp() {
        return exp;
    }

    public float getWalkSpeed() {
        return walkSpeed;
    }

    public String getWorldName() {
        return world;
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

    public long getCapturedAt() {
        return capturedAt;
    }

    public Location toLocation() {
        if (world == null) {
            return null;
        }
        World w = Bukkit.getWorld(world);
        if (w == null) {
            return null;
        }
        return new Location(w, x, y, z, yaw, pitch);
    }
}
