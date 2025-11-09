package dev.zonely.whiteeffect.debug;

import dev.zonely.whiteeffect.Core;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

public final class InventoryUnlocker implements Listener {

    private static final EnumSet<InventoryType> UNLOCKED_TYPES;

    static {
        UNLOCKED_TYPES = EnumSet.allOf(InventoryType.class);
        UNLOCKED_TYPES.remove(InventoryType.PLAYER);
        UNLOCKED_TYPES.remove(InventoryType.CRAFTING);
        try {
            UNLOCKED_TYPES.remove(InventoryType.valueOf("UNKNOWN"));
        } catch (IllegalArgumentException ignored) {
        }
        try {
            UNLOCKED_TYPES.remove(InventoryType.valueOf("CREATIVE"));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static final Set<String> BLOCKED_HOLDER_PREFIXES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("dev.zonely", "net.zonely")));

    private final Plugin plugin;

    private InventoryUnlocker(Plugin plugin) {
        this.plugin = plugin;
    }

    public static void install() {
        Plugin plugin = Core.getInstance();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getPluginManager().registerEvents(new InventoryUnlocker(plugin), plugin);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent e) {
        HumanEntity who = e.getWhoClicked();
        if (!(who instanceof Player))
            return;
        Inventory clicked = e.getClickedInventory();
        if (clicked == null)
            return;
        Player player = (Player) who;

        if (isPlayersOwnUI(e)) {
            e.setCancelled(false);
            e.setResult(Event.Result.ALLOW);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
            return;
        }

        if (allowVanillaContainer(e.getView())) {
            e.setCancelled(false);
            e.setResult(Event.Result.ALLOW);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClickMonitor(InventoryClickEvent e) {
        HumanEntity who = e.getWhoClicked();
        if (!(who instanceof Player))
            return;
        if (allowVanillaContainer(e.getView())) {
            e.setCancelled(false);
            e.setResult(Event.Result.ALLOW);
            Bukkit.getScheduler().runTask(plugin, ((Player) who)::updateInventory);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent e) {
        HumanEntity who = e.getWhoClicked();
        if (!(who instanceof Player))
            return;
        Player player = (Player) who;
        InventoryView view = e.getView();
        if (!isPlayersOwnUI(view)) {
            if (allowVanillaContainer(view)) {
                e.setCancelled(false);
                e.setResult(Event.Result.ALLOW);
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
            }
            return;
        }

        final int topSize = e.getView().getTopInventory().getSize();
        boolean touchesTopCrafting = e.getRawSlots().stream().anyMatch(raw -> raw < topSize);
        boolean touchesPlayerInv = e.getRawSlots().stream().anyMatch(raw -> raw >= topSize);
        if (!(touchesTopCrafting || touchesPlayerInv))
            return;
        e.setCancelled(false);
        e.setResult(Event.Result.ALLOW);
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragMonitor(InventoryDragEvent e) {
        HumanEntity who = e.getWhoClicked();
        if (!(who instanceof Player))
            return;
        if (allowVanillaContainer(e.getView())) {
            e.setCancelled(false);
            e.setResult(Event.Result.ALLOW);
            Bukkit.getScheduler().runTask(plugin, ((Player) who)::updateInventory);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent e) {
        e.setCancelled(false);
        Bukkit.getScheduler().runTask(plugin, e.getPlayer()::updateInventory);
    }

    private boolean allowVanillaContainer(InventoryView view) {
        if (view == null)
            return false;

        Inventory top = view.getTopInventory();
        if (top == null)
            return false;

        String typeName;
        try {
            typeName = top.getType().name();
        } catch (Throwable ignored) {
            return false;
        }

        InventoryType type;
        try {
            type = top.getType();
        } catch (Throwable ignored) {
            return false;
        }
        if (type == null || !UNLOCKED_TYPES.contains(type)) {
            return false;
        }

        InventoryHolder holder = top.getHolder();
        if (holder == null)
            return true;

        String holderClass = holder.getClass().getName();
        for (String prefix : BLOCKED_HOLDER_PREFIXES) {
            if (holderClass.startsWith(prefix)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isPlayersOwnUI(InventoryClickEvent e) {
        return isPlayersOwnUI(e.getView()) &&
                (e.getClickedInventory().getType() == InventoryType.PLAYER ||
                        e.getClickedInventory().getType() == InventoryType.CRAFTING);
    }

    private static boolean isPlayersOwnUI(InventoryView view) {
        if (view == null)
            return false;
        InventoryType top = view.getTopInventory() == null ? null : view.getTopInventory().getType();
        InventoryType bottom = view.getBottomInventory() == null ? null : view.getBottomInventory().getType();
        return top == InventoryType.CRAFTING && bottom == InventoryType.PLAYER;
    }
}
