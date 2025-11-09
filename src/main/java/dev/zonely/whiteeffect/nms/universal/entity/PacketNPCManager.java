package dev.zonely.whiteeffect.nms.universal.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;


public final class PacketNPCManager {
    private static final Set<HumanControllerPackets> CONTROLLERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Entity, HumanControllerPackets> CONTROLLER_LOOKUP = Collections.synchronizedMap(new WeakHashMap<>());

    private PacketNPCManager() {
    }

    static void register(HumanControllerPackets controller, Entity anchor) {
        if (controller != null) {
            CONTROLLERS.add(controller);
        }
        if (anchor != null) {
            CONTROLLER_LOOKUP.put(anchor, controller);
        }
    }

    static void unregister(HumanControllerPackets controller, Entity anchor) {
        if (controller != null) {
            CONTROLLERS.remove(controller);
        }
        if (anchor != null) {
            CONTROLLER_LOOKUP.remove(anchor);
        }
    }

    public static void resendFor(Player player) {
        World w = player.getWorld();
        for (HumanControllerPackets c : CONTROLLERS) {
            Entity anchor = c.getBukkitEntity();
            if (anchor != null && anchor.isValid() && anchor.getWorld() == w) {
                c.sendSpawnPackets(player);
                c.scheduleTabHide(player);
            }
        }
    }

    public static void handleTeleport(Entity anchor, Location target, boolean updateRotation) {
        if (anchor == null || target == null) {
            return;
        }
        HumanControllerPackets controller = CONTROLLER_LOOKUP.get(anchor);
        if (controller == null) {
            return;
        }
        if (anchor.isValid()) {
            try {
                anchor.teleport(target, TeleportCause.PLUGIN);
            } catch (Throwable ignored) {
                try {
                    anchor.teleport(target);
                } catch (Throwable ignored2) {
                }
            }
        }
        controller.handleTeleport(target, updateRotation);
    }

    public static void syncEquipment(Entity anchor, int slotIndex, ItemStack item) {
        if (anchor == null) {
            return;
        }
        HumanControllerPackets controller = CONTROLLER_LOOKUP.get(anchor);
        if (controller == null) {
            return;
        }
        controller.handleEquipment(slotIndex, item);
    }
}
