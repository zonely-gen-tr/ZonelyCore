package dev.zonely.whiteeffect.nms.universal;

import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import dev.zonely.whiteeffect.nms.NMS;
import dev.zonely.whiteeffect.nms.interfaces.entity.IArmorStand;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.NamespacedKey;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

public class UniversalArmorStand implements IArmorStand {
    private static final String HOLOGRAM_TAG = "zonely_hologram";
    private final ArmorStand entity;
    private final HologramLine line;

    private UniversalArmorStand(ArmorStand stand, HologramLine line) {
        this.entity = stand;
        this.line = line;
    }

    public static UniversalArmorStand spawn(Location location, String name, HologramLine line) {
        ArmorStand as = location.getWorld().spawn(location, ArmorStand.class);
        try { as.getClass().getMethod("setGravity", boolean.class).invoke(as, false); } catch (Throwable ignored) {}
        try { as.getClass().getMethod("setMarker", boolean.class).invoke(as, true); } catch (Throwable ignored) {}
        try { as.setSmall(true); } catch (Throwable ignored) {}
        as.setVisible(false);
        as.setCustomNameVisible(name != null && !name.isEmpty());
        as.setCustomName(name);
        try { as.getClass().getMethod("setCollidable", boolean.class).invoke(as, false); } catch (Throwable ignored) {}
        try {
            as.getClass().getMethod("addScoreboardTag", String.class).invoke(as, HOLOGRAM_TAG);
        } catch (Throwable ignored) {}
        try {
            Object container = as.getClass().getMethod("getPersistentDataContainer").invoke(as);
            if (container != null) {
                NamespacedKey key = new NamespacedKey(dev.zonely.whiteeffect.Core.getInstance(), HOLOGRAM_TAG);
                container.getClass()
                        .getMethod("set", NamespacedKey.class, PersistentDataType.class, Object.class)
                        .invoke(container, key, PersistentDataType.BYTE, (byte) 1);
            }
        } catch (Throwable ignored) {}
        if (line != null) {
            as.setMetadata(HOLOGRAM_TAG, new FixedMetadataValue(dev.zonely.whiteeffect.Core.getInstance(), line.getHologram()));
        }
        return new UniversalArmorStand(as, line);
    }

    @Override
    public int getId() { return entity.getEntityId(); }

    @Override
    public void setName(String name) {
        entity.setCustomName(name);
        entity.setCustomNameVisible(name != null && !name.isEmpty());
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
    public ArmorStand getEntity() { return entity; }

    @Override
    public HologramLine getLine() { return line; }
}
