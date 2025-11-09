package dev.zonely.whiteeffect.menus.reports;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.menu.Menu;
import dev.zonely.whiteeffect.libraries.menu.PagedPlayerMenu;
import dev.zonely.whiteeffect.report.Report;
import dev.zonely.whiteeffect.report.ReportService;
import dev.zonely.whiteeffect.menus.reports.MenuReportPunish;
import dev.zonely.whiteeffect.replay.NPCReplayManager;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;

public class MenuReplayControls extends PagedPlayerMenu {

    private final Report activeReport;
    private final ReportService reportService;

    public MenuReplayControls(Player viewer) {
        super(viewer, LanguageManager.get("menus.reports.replay.title", "&8NPC Replay Controls"), 3);
        this.reportService = Core.getInstance().getReportService();
        if (this.reportService == null) {
            LanguageManager.send(viewer, "commands.reports.disabled",
                    "{prefix}&cReports module is disabled.",
                    "prefix", LanguageManager.get("prefix.punish", ""));
            this.activeReport = null;
            return;
        }
        this.activeReport = NPCReplayManager.getActiveReport(viewer);

        Menu menu = new Menu(LanguageManager.get("menus.reports.replay.title", "&8NPC Replay Controls"), 3);

        Material pauseMat = resolveMaterial("INK_SACK:8", "GRAY_DYE", "INK_SACK", Material.STONE_BUTTON);
        ItemStack pause = parseItem("menus.reports.replay.pause",
                "INK_SACK:8 : 1 : name>&ePause/Resume : desc>&7Toggle playback",
                pauseMat);
        ItemStack slower = parseItem("menus.reports.replay.slower",
                "FEATHER : 1 : name>&aSlow Down : desc>&7Reduce speed",
                Material.FEATHER);
        ItemStack faster = parseItem("menus.reports.replay.faster",
                "SUGAR : 1 : name>&cSpeed Up : desc>&7Increase speed",
                Material.SUGAR);
        ItemStack restart = parseItem("menus.reports.replay.restart",
                "ARROW : 1 : name>&bRestart : desc>&7Start from beginning",
                Material.ARROW);
        ItemStack stop = parseItem("menus.reports.replay.stop",
                "BARRIER : 1 : name>&cStop : desc>&7Stop and despawn NPC",
                Material.BARRIER);
        Material approveMat = resolveMaterial("INK_SACK:10", "GREEN_DYE", "INK_SACK", Material.PAPER);
        ItemStack approve = activeReport == null ? null : parseItem("menus.reports.replay.approve",
                "INK_SACK:10 : 1 : name>&aApprove Report : desc>&7Open the punishment menu for this report.",
                approveMat);

        menu.setItem(10, pause);
        menu.setItem(12, slower);
        menu.setItem(14, faster);
        menu.setItem(16, restart);
        menu.setItem(22, stop);
        if (approve != null) {
            menu.setItem(24, approve);
        }

        this.menus.clear();
        this.menus.add(menu);
        this.currentPage = 1;

        this.register(Core.getInstance());
        this.open();
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getInventory().equals(getCurrentInventory())) return;
        if (!e.getWhoClicked().equals(this.player)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
        int slot = e.getSlot();
        if (slot == 10) {
            boolean paused = NPCReplayManager.togglePause(player);
            EnumSound.CLICK.play(player, 0.5F, paused ? 0.6F : 1.4F);
            player.sendMessage(LanguageManager.get("commands.reports.replay.paused",
                    paused ? "&eReplay paused." : "&aReplay resumed."));
        } else if (slot == 12) {
            double sp = NPCReplayManager.changeSpeed(player, -0.5);
            player.sendMessage(LanguageManager.get("commands.reports.replay.speed",
                    "&eSpeed: &f{speed}x", "speed", sp));
        } else if (slot == 14) {
            double sp = NPCReplayManager.changeSpeed(player, +0.5);
            player.sendMessage(LanguageManager.get("commands.reports.replay.speed",
                    "&eSpeed: &f{speed}x", "speed", sp));
        } else if (slot == 16) {
            NPCReplayManager.restart(player);
            EnumSound.CLICK.play(player, 0.5F, 1.0F);
            player.sendMessage(LanguageManager.get("commands.reports.replay.restart",
                    "&aRestarted replay from the beginning."));
        } else if (slot == 22) {
            NPCReplayManager.stopReplay(player);
            player.closeInventory();
        } else if (slot == 24 && activeReport != null) {
            NPCReplayManager.stopReplay(player);
            new MenuReportPunish(Core.getInstance(), player, reportService, activeReport, null);
        }
    }

    public void cancel() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer().equals(this.player) && e.getInventory().equals(getCurrentInventory())) cancel();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (e.getPlayer().equals(this.player)) cancel();
    }

    private ItemStack parseItem(String key, String def, Material fallback, Object... placeholders) {
        String definition = LanguageManager.get(key, def, placeholders);
        try {
            return BukkitUtils.deserializeItemStack(definition);
        } catch (IllegalArgumentException ex) {
            Core.getInstance().getLogger().log(Level.WARNING,
                    "[ReplayControlsMenu] Failed to parse item for {0}: {1}",
                    new Object[]{key, definition});
            return new ItemStack(fallback);
        }
    }

    private Material resolveMaterial(String legacyToken, String modernName, String fallbackLegacy, Material ultimateFallback) {
        Material material = MaterialResolver.resolveMaterial(legacyToken);
        if (material == null && modernName != null) {
            material = MaterialResolver.resolveMaterial(modernName);
        }
        if (material == null && fallbackLegacy != null) {
            material = Material.matchMaterial(fallbackLegacy);
        }
        if (material == null && modernName != null) {
            material = Material.matchMaterial(modernName);
        }
        return material != null ? material : ultimateFallback;
    }
}

