package dev.zonely.whiteeffect.nms.universal;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import dev.zonely.whiteeffect.nms.NMS;
import dev.zonely.whiteeffect.nms.interfaces.entity.ISlime;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Slime;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

public class UniversalSlime implements ISlime {
    private final Slime entity;
    private final HologramLine line;

    private UniversalSlime(Slime slime, HologramLine line) {
        this.entity = slime;
        this.line = line;
    }

    public static UniversalSlime spawn(Location location, HologramLine line) {
        Slime slime = location.getWorld().spawn(location, Slime.class);
        try { slime.setSize(1); } catch (Throwable ignored) {}
        try { slime.getClass().getMethod("setAI", boolean.class).invoke(slime, false); } catch (Throwable ignored) {}
    try { slime.getClass().getMethod("setInvisible", boolean.class).invoke(slime, true); } catch (Throwable ignored) {}
        try { slime.getClass().getMethod("setCollidable", boolean.class).invoke(slime, false); } catch (Throwable ignored) {}
    try { slime.getClass().getMethod("setSilent", boolean.class).invoke(slime, true); } catch (Throwable ignored) {}
        try { slime.getClass().getMethod("setInvulnerable", boolean.class).invoke(slime, true); } catch (Throwable ignored) {}
        Core core = Core.getInstance();
        if (line != null && core != null) {
            slime.setMetadata("zonely_hologram", new FixedMetadataValue(core, line.getHologram()));
        }
        try {
            slime.getClass().getMethod("addScoreboardTag", String.class).invoke(slime, "zonely_hologram");
        } catch (Throwable ignored) {}
        if (core != null) {
            try {
                NamespacedKey key = new NamespacedKey(core, "zonely_hologram");
                Object container = slime.getClass().getMethod("getPersistentDataContainer").invoke(slime);
                if (container != null) {
                    container.getClass()
                            .getMethod("set", NamespacedKey.class, PersistentDataType.class, Object.class)
                            .invoke(container, key, PersistentDataType.BYTE, Byte.valueOf((byte) 1));
                }
            } catch (Throwable ignored) {}
        }
        return new UniversalSlime(slime, line);
    }

    @Override
    public void setPassengerOf(Entity holder) {
        try {
            holder.getClass().getMethod("addPassenger", org.bukkit.entity.Entity.class).invoke(holder, entity);
        } catch (Throwable t) {
            try {
                holder.setPassenger(entity);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void setLocation(double x, double y, double z) {
        entity.teleport(new Location(entity.getWorld(), x, y, z, entity.getLocation().getYaw(), entity.getLocation().getPitch()));
    }

    @Override
    public boolean isDead() { return !entity.isValid() || entity.isDead(); }

    @Override
    public void killEntity() { NMS.removeFromWorld(entity); }

    @Override
    public Slime getEntity() { return entity; }

    @Override
    public HologramLine getLine() { return line; }
}
