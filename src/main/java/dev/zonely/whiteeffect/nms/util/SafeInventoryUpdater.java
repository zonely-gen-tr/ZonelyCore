package dev.zonely.whiteeffect.nms.util;

import dev.zonely.whiteeffect.Core;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;


public final class SafeInventoryUpdater {

    private static final long RETRY_DELAY_TICKS = 2L;

    private SafeInventoryUpdater() {
    }

    public static void update(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        schedule(player);
    }

    private static void schedule(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        InventoryView view = player.getOpenInventory();
        ItemStack cursor = view != null ? view.getCursor() : null;
        boolean dragging = cursor != null && cursor.getType() != Material.AIR;
        if (dragging) {
            Bukkit.getScheduler().runTaskLater(
                    Core.getInstance(),
                    () -> schedule(player),
                    RETRY_DELAY_TICKS);
            return;
        }
        try {
            player.updateInventory();
        } catch (Throwable ignored) {
        }
    }
}
