package dev.zonely.whiteeffect.menus.reports;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.menu.Menu;
import dev.zonely.whiteeffect.libraries.menu.PagedPlayerMenu;
import dev.zonely.whiteeffect.report.Report;
import dev.zonely.whiteeffect.report.ReportSnapshot;
import dev.zonely.whiteeffect.report.ReportStatus;
import dev.zonely.whiteeffect.report.ReportService;
import dev.zonely.whiteeffect.replay.NPCReplayManager;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.logging.Level;

public class MenuReportDetail extends PagedPlayerMenu {

    private final Core plugin;
    private final ReportService service;
    private final int reportId;
    private Report report;
    private ReportSnapshot snapshot;

    public MenuReportDetail(Core plugin, Player viewer, ReportService service, int reportId) {
        super(viewer, LanguageManager.get("menus.reports.detail.title", "&8Report Details"), 3);
        this.plugin = plugin;
        this.service = service;
        this.reportId = reportId;
        this.onlySlots(Arrays.asList(10, 12, 14, 16));

        reload();
        this.register(plugin);
        this.open();
    }

    private void reload() {
        this.report = service.getById(reportId);
        if (this.report == null) {
            player.closeInventory();
            return;
        }
        this.snapshot = service.getSnapshot(reportId);
        Menu menu = new Menu(LanguageManager.get("menus.reports.detail.title", "&8Report Details"), 3);
        String health = snapshot != null ? String.format(java.util.Locale.ROOT, "%.1f", snapshot.getHealth()) : "-";
        String food = snapshot != null ? Integer.toString(snapshot.getFood()) : "-";
        String xpLevel = snapshot != null ? Integer.toString(snapshot.getLevel()) : "-";
        String xpProgress = snapshot != null ? String.format(java.util.Locale.ROOT, "%.0f%%", snapshot.getExp() * 100.0F) : "-";
        String speed = snapshot != null ? String.format(java.util.Locale.ROOT, "%.2f", snapshot.getWalkSpeed()) : "-";

        Material headMaterial = MaterialResolver.resolveMaterial("SKULL_ITEM");
        if (headMaterial == null) {
            headMaterial = Material.matchMaterial("PLAYER_HEAD");
        }
        if (headMaterial == null) {
            headMaterial = Material.matchMaterial("SKULL_ITEM");
        }
        if (headMaterial == null) {
            headMaterial = Material.STONE;
        }
        String statusColored;
        switch (report.getStatus()) {
            case APPROVED:
                statusColored = "&aApproved";
                break;
            case REJECTED:
                statusColored = "&cRejected";
                break;
            default:
                statusColored = "&ePending";
                break;
        }

        String resolvedBy = report.getResolvedBy() == null ? "-" : report.getResolvedBy();
        String resolvedAction = report.getResolvedAction() == null ? "-" : report.getResolvedAction();
        String resolvedDuration = report.getResolvedDuration() == null ? "-" : report.getResolvedDuration();
        String resolvedReason = report.getResolvedReason() == null ? "-" : report.getResolvedReason();

        ItemStack head = parseItem("menus.reports.detail.target",
                "SKULL_ITEM:3 : 1 : name>&c{player} : desc>&7Reporter: &f{reporter}\n&7Status: &f{status}\n&7Reason: &f{reason}\n&7Server: &f{server}\n&7Moderator: &f{moderator}\n&7Action: &f{action}\n&7Duration: &f{duration}\n&7Punish Reason: &f{punish_reason}\n \n&7Health: &f{health}\n&7Food: &f{food}\n&7XP: &fLvl {xp_level} ({xp_progress})\n&7Speed: &f{speed}",
                headMaterial,
                "player", report.getTarget(),
                "reporter", report.getReporter(),
                "status", statusColored,
                "reason", report.getReason(),
                "server", report.getServer() == null ? "Unknown" : report.getServer(),
                "moderator", resolvedBy,
                "action", resolvedAction,
                "duration", resolvedDuration,
                "punish_reason", resolvedReason,
                "health", health,
                "food", food,
                "xp_level", xpLevel,
                "xp_progress", xpProgress,
                "speed", speed);
        Material approveMat = MaterialResolver.resolveMaterial("INK_SACK:10");
        if (approveMat == null) approveMat = MaterialResolver.resolveMaterial("GREEN_DYE");
        if (approveMat == null) approveMat = Material.matchMaterial("GREEN_DYE");
        if (approveMat == null) approveMat = Material.matchMaterial("INK_SACK");
        if (approveMat == null) approveMat = Material.PAPER;
        Material rejectMat = MaterialResolver.resolveMaterial("INK_SACK:1");
        if (rejectMat == null) rejectMat = MaterialResolver.resolveMaterial("RED_DYE");
        if (rejectMat == null) rejectMat = Material.matchMaterial("RED_DYE");
        if (rejectMat == null) rejectMat = Material.matchMaterial("INK_SACK");
        if (rejectMat == null) rejectMat = Material.PAPER;
        ItemStack approve = parseItem("menus.reports.detail.approve",
                "INK_SACK:10 : 1 : name>&aApprove : desc>&7Approve report and punish the player.\n \n&6>> &eClick: Approve",
                approveMat);
        ItemStack reject = parseItem("menus.reports.detail.reject",
                "INK_SACK:1 : 1 : name>&cReject : desc>&7Reject this report.\n \n&6>> &eClick: Reject",
                rejectMat);
        Material replayMat = MaterialResolver.resolveMaterial("EYE_OF_ENDER");
        if (replayMat == null) replayMat = MaterialResolver.resolveMaterial("ENDER_EYE");
        if (replayMat == null) replayMat = Material.matchMaterial("ENDER_EYE");
        if (replayMat == null) replayMat = Material.ENDER_PEARL;
        ItemStack replay = parseItem("menus.reports.detail.replay",
                "ENDER_EYE : 1 : name>&dWatch Replay : desc>&7Open the attached replay (if any).\n \n&6>> &eRight-Click: Controls",
                replayMat);
        ItemStack history = parseItem("menus.reports.detail.history",
                "BOOK : 1 : name>&eDetails : desc>&7Reporter: &f{reporter}\n&7Reason: &f{reason}\n&7Server: &f{server}\n \n&6>> &eClick: History",
                Material.BOOK,
                "reporter", report.getReporter(),
                "reason", report.getReason(),
                "server", report.getServer() == null ? "Unknown" : report.getServer());

        menu.setItem(10, head);
        menu.setItem(12, approve);
        menu.setItem(14, reject);
        menu.setItem(16, replay);
        menu.setItem(22, history);

        this.menus.clear();
        this.menus.add(menu);
        this.currentPage = 1;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getInventory().equals(getCurrentInventory())) return;
        if (!e.getWhoClicked().equals(this.player)) return;
        e.setCancelled(true);

        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
        int slot = e.getSlot();
        if (slot == 12) {
            EnumSound.CLICK.play(player, 0.5F, 1.2F);
            if (report.getStatus() != ReportStatus.PENDING) {
                player.sendMessage(LanguageManager.get("commands.reports.action.failed",
                        "&cUnable to process this report."));
                return;
            }
            new MenuReportPunish(plugin, player, service, report, this);
            return;
        }
        if (slot == 14) {
            boolean ok = service.rejectReport(report.getId(), player);
            EnumSound.CLICK.play(player, 0.5F, 1.2F);
            player.sendMessage(LanguageManager.get("commands.reports.action." + (ok ? "rejected" : "failed"),
                    ok ? "&eReport rejected." : "&cUnable to reject this report."));
            if (ok) {
                refreshAfterDecision();
            } else {
                player.closeInventory();
            }
            return;
        }
        if (slot == 16) {
            if (!service.hasReplayFrames(report.getId())) {
                player.sendMessage(LanguageManager.get("commands.reports.replay.missing",
                        "&cNo replay is attached to this report."));
                return;
            }
            if (!NPCReplayManager.isReplaying(player)) {
                boolean ok = NPCReplayManager.startReplay(Core.getInstance(), player, report);
                if (ok) {
                } else {
                    player.sendMessage(LanguageManager.get("commands.reports.replay.missing",
                            "&cNo replay is attached to this report."));
                }
            }
            if (e.isRightClick()) {
                new MenuReplayControls(player);
            }
            return;
        }
        if (slot == 22 || slot == 10) {
            new MenuReportHistory(player, service, report.getTarget());
        }
    }


    void refreshAfterDecision() {
        cancel();
        player.closeInventory();
    }

    void reopen() {
        reload();
        this.open();
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
            plugin.getLogger().log(Level.WARNING,
                    "[ReportDetailMenu] Failed to parse item for {0}: {1}",
                    new Object[]{key, definition});
            return new ItemStack(fallback);
        }
    }
}



