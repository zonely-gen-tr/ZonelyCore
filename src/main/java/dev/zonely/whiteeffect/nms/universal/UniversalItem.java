package dev.zonely.whiteeffect.nms.universal;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import dev.zonely.whiteeffect.nms.NMS;
import dev.zonely.whiteeffect.nms.interfaces.entity.IItem;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

public class UniversalItem implements IItem {
    private final Item entity;
    private final HologramLine line;

    private UniversalItem(Item item, HologramLine line) {
        this.entity = item;
        this.line = line;
    }

    public static UniversalItem spawn(Location location, ItemStack stack, HologramLine line) {
        Item dropped = location.getWorld().dropItem(location, stack);
        dropped.setPickupDelay(Integer.MAX_VALUE);
        try { dropped.getClass().getMethod("setGravity", boolean.class).invoke(dropped, false); } catch (Throwable ignored) {}
        try { dropped.getClass().getMethod("setCanMobPickup", boolean.class).invoke(dropped, false); } catch (Throwable ignored) {}
        try { dropped.getClass().getMethod("setCanPlayerPickup", boolean.class).invoke(dropped, false); } catch (Throwable ignored) {}
        Core core = Core.getInstance();
        if (line != null && core != null) {
            dropped.setMetadata("zonely_hologram", new FixedMetadataValue(core, line.getHologram()));
        }
        try { dropped.getClass().getMethod("addScoreboardTag", String.class).invoke(dropped, "zonely_hologram"); } catch (Throwable ignored) {}
        if (core != null) {
            try {
                NamespacedKey key = new NamespacedKey(core, "zonely_hologram");
                Object container = dropped.getClass().getMethod("getPersistentDataContainer").invoke(dropped);
                if (container != null) {
                    container.getClass()
                            .getMethod("set", NamespacedKey.class, PersistentDataType.class, Object.class)
                            .invoke(container, key, PersistentDataType.BYTE, (byte) 1);
                }
            } catch (Throwable ignored) {}
        }
        return new UniversalItem(dropped, line);
    }

    @Override
    public void setPassengerOf(Entity holder) {
        try {
            holder.getClass().getMethod("addPassenger", Entity.class).invoke(holder, entity);
        } catch (Throwable t) {
            try { holder.getClass().getMethod("setPassenger", Entity.class).invoke(holder, entity); } catch (Throwable ignored) {}
        }
    }

    @Override
    public void setItemStack(ItemStack var1) { entity.setItemStack(var1); }

    @Override
    public void setLocation(double x, double y, double z) {
        entity.teleport(new Location(entity.getWorld(), x, y, z, entity.getLocation().getYaw(), entity.getLocation().getPitch()));
    }

    @Override
    public boolean isDead() { return !entity.isValid() || entity.isDead(); }

    @Override
    public void killEntity() { NMS.removeFromWorld(entity); }

    @Override
    public Item getEntity() { return entity; }

    @Override
    public HologramLine getLine() { return line; }
}
