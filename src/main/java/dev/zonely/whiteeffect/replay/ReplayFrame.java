package dev.zonely.whiteeffect.replay;

import org.bukkit.Bukkit;
import org.bukkit.Location;

public class ReplayFrame {
    public final String world;
    public final double x, y, z;
    public final float yaw, pitch;
    public final int tick; 

    public ReplayFrame(String world, double x, double y, double z, float yaw, float pitch, int tick) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.tick = tick;
    }

    public Location toLocation() {
        return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
    }
}

