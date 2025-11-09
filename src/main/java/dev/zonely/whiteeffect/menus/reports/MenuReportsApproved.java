package dev.zonely.whiteeffect.menus.reports;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.menu.PagedPlayerMenu;
import dev.zonely.whiteeffect.report.Report;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class MenuReportsApproved extends PagedPlayerMenu {

    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static final List<Integer> DEFAULT_CONTENT_SLOTS = Arrays.asList(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );
    private static final List<Integer> DEFAULT_FILLER_SLOTS = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8);
    private static final String DEFAULT_FILLER_ITEM = "STAINED_GLASS_PANE:7 : 1 : name>&r";
    private static final String DEFAULT_INFO_ITEM = "BOOK : 1 : name>&6Approved Reports : desc>&7Total: &e{total}\\n&7Page: &e{page}";
    private static final String DEFAULT_BACK_BUTTON = "ARROW : 1 : name>&e<- Active Reports";
    private static final String DEFAULT_PREVIOUS_ITEM = "ARROW : 1 : name>&ePrevious Page";
    private static final String DEFAULT_NEXT_ITEM = "ARROW : 1 : name>&eNext Page";
    private static final String DEFAULT_ENTRY_ITEM =
            "SKULL_ITEM:3 : 1 : name>&a{player} : desc>&7ID: &f#{id}\\n&7Reporter: &f{reporter}\\n&7Reason: &f{reason}\\n"
                    + "&7Created: &f{created}\\n&7Resolved: &f{resolved}\\n&7Server: &f{server}\\n&7Approved By: &a{moderator}\\n"
                    + "&7Action: &f{action}\\n&7Punish Reason: &f{punish_reason}\\n&7Duration: &f{duration}\\n \\n&2[+] &aReplay: {record}\\n \\n"
                    + "&6>> &eLeft-Click: Replay\\n&6>> &eRight-Click: Details";
    private static final String DEFAULT_EMPTY_ITEM = "BARRIER : 1 : name>&cNo approved reports";

    private final Core plugin;
    private final ReportService service;
    private final Map<ItemStack, Report> mapping = new HashMap<>();

    private ItemStack backButton;
    private int backSlot;
    private int infoSlot;

    public MenuReportsApproved(Core plugin, Player viewer, ReportService service) {
        super(viewer, LanguageManager.get("menus.reports.approved.title", "&8Approved Reports"), Math.max(1, Math.min(6, LanguageManager.getInt("menus.reports.approved.rows", 6))));
        this.plugin = plugin;
        this.service = service;

        int maxSlots = this.rows * 9;
        List<Integer> contentSlots = sanitizeSlots(LanguageManager.getIntegerList("menus.reports.approved.layout.content-slots", DEFAULT_CONTENT_SLOTS), maxSlots);
        if (contentSlots.isEmpty()) {
            contentSlots = DEFAULT_CONTENT_SLOTS;
        }
        this.onlySlots(contentSlots);

        List<Integer> fillerSlots = sanitizeSlots(LanguageManager.getIntegerList("menus.reports.approved.layout.filler.slots", DEFAULT_FILLER_SLOTS), maxSlots);
        ItemStack filler = parseItem("menus.reports.approved.items.filler", DEFAULT_FILLER_ITEM, Material.GLASS);
        if (filler != null && !fillerSlots.isEmpty()) {
            this.removeSlotsWith(filler, toIntArray(fillerSlots));
            for (int slot : fillerSlots) {
                setItem(slot, filler.clone());
            }
        }

        this.infoSlot = clampSlot(LanguageManager.getInt("menus.reports.approved.layout.controls.info-slot", 49), maxSlots);
        ItemStack info = parseItem("menus.reports.approved.items.info", DEFAULT_INFO_ITEM,
                Material.BOOK, "total", 0, "page", 1);
        if (infoSlot >= 0 && info != null) {
            setItem(infoSlot, info);
        }

        this.backSlot = clampSlot(LanguageManager.getInt("menus.reports.approved.layout.controls.back-slot", 48), maxSlots);
        this.backButton = parseItem("menus.reports.approved.items.back-button", DEFAULT_BACK_BUTTON, Material.ARROW);
        if (backSlot >= 0 && backButton != null) {
            setItem(backSlot, backButton.clone());
        } else {
            this.backSlot = -1;
        }

        int configuredPrevious = clampSlot(LanguageManager.getInt("menus.reports.approved.layout.navigation.previous.slot", 45), maxSlots);
        String previousItem = LanguageManager.getRaw("menus.reports.approved.layout.navigation.previous.item", DEFAULT_PREVIOUS_ITEM);
        if (configuredPrevious >= 0 && previousItem != null && !previousItem.trim().isEmpty()) {
            this.previousPage = configuredPrevious;
            this.previousStack = previousItem;
        } else {
            this.previousPage = -1;
        }

        int configuredNext = clampSlot(LanguageManager.getInt("menus.reports.approved.layout.navigation.next.slot", 53), maxSlots);
        String nextItem = LanguageManager.getRaw("menus.reports.approved.layout.navigation.next.item", DEFAULT_NEXT_ITEM);
        if (configuredNext >= 0 && nextItem != null && !nextItem.trim().isEmpty()) {
            this.nextPage = configuredNext;
            this.nextStack = nextItem;
        } else {
            this.nextPage = -1;
        }

        populate();
        this.register(plugin);
        this.open();
    }

    private void populate() {
        mapping.clear();
        List<Report> list = service.listApprovedReports();
        List<ItemStack> items = new ArrayList<>(list.size());
        for (Report r : list) {
            boolean hasReplay = r.hasReplay() || service.hasReplayFrames(r.getId());
            String recordText = hasReplay
                    ? LanguageManager.get("menus.reports.common.record-yes", "&aAvailable")
                    : LanguageManager.get("menus.reports.common.record-no", "&cNot Available");

            String action = r.getResolvedAction() == null ? "-" : r.getResolvedAction().replace('_', ' ');
            String resolvedReason = r.getResolvedReason() == null || r.getResolvedReason().isEmpty() ? "-" : r.getResolvedReason();
            String duration = r.getResolvedDuration() == null || r.getResolvedDuration().isEmpty() ? "-" : r.getResolvedDuration();
            String createdAt = DATE.format(r.getCreatedAt());
            String resolvedAt = r.getResolvedAt() == null ? "-" : DATE.format(r.getResolvedAt());

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

            ItemStack head = parseItem("menus.reports.approved.items.entry",
                    DEFAULT_ENTRY_ITEM,
                    headMaterial,
                    "player", r.getTarget(),
                    "id", r.getId(),
                    "reporter", r.getReporter(),
                    "reason", r.getReason(),
                    "created", createdAt,
                    "resolved", resolvedAt,
                    "server", r.getServer() == null ? "Unknown" : r.getServer(),
                    "moderator", r.getResolvedBy() == null ? "Unknown" : r.getResolvedBy(),
                    "action", action,
                    "punish_reason", resolvedReason,
                    "duration", duration,
                    "record", recordText);
            items.add(head);
            mapping.put(head, r);
        }
        if (items.isEmpty()) {
            ItemStack empty = parseItem("menus.reports.approved.items.empty", DEFAULT_EMPTY_ITEM, Material.BARRIER);
            if (empty != null) {
                items.add(empty);
            }
        }
        setItems(items);
        ItemStack info = parseItem("menus.reports.approved.items.info",
                DEFAULT_INFO_ITEM,
                Material.BOOK,
                "total", list.size(),
                "page", this.currentPage);
        if (infoSlot >= 0 && info != null) {
            setItem(infoSlot, info);
        }
        if (backSlot >= 0 && backButton != null) {
            setItem(backSlot, backButton.clone());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getInventory().equals(getCurrentInventory())) return;
        if (!e.getWhoClicked().equals(this.player)) return;
        e.setCancelled(true);

        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        int slot = e.getSlot();
        if (slot == this.previousPage && this.previousPage >= 0) {
            EnumSound.CLICK.play(player, 0.5F, 2.0F);
            openPrevious();
            return;
        }
        if (slot == this.nextPage && this.nextPage >= 0) {
            EnumSound.CLICK.play(player, 0.5F, 2.0F);
            openNext();
            return;
        }
        if (slot == this.backSlot && this.backSlot >= 0 && backButton != null && backButton.isSimilar(item)) {
            EnumSound.CLICK.play(player, 0.5F, 1.8F);
            new MenuReportsList(plugin, player, service);
            return;
        }

        Report report = null;
        for (Map.Entry<ItemStack, Report> entry : mapping.entrySet()) {
            if (entry.getKey().isSimilar(item)) {
                report = entry.getValue();
                break;
            }
        }
        if (report == null) return;

        switch (e.getClick()) {
            case LEFT:
            case SHIFT_LEFT:
                if (!service.hasReplayFrames(report.getId())) {
                    player.sendMessage(LanguageManager.get("commands.reports.replay.missing",
                            "&cNo replay is attached to this report."));
                    return;
                }
                if (NPCReplayManager.startReplay(Core.getInstance(), player, report)) {
                } else {
                    player.sendMessage(LanguageManager.get("commands.reports.replay.missing",
                            "&cNo replay is attached to this report."));
                }
                break;
            case RIGHT:
            case SHIFT_RIGHT:
                new MenuReportDetail(plugin, player, service, report.getId());
                break;
            default:
                break;
        }
    }

    public void cancel() {
        mapping.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer().equals(this.player) && e.getInventory().equals(getCurrentInventory())) {
            cancel();
        }
    }

    private ItemStack parseItem(String key, String def, Material fallback, Object... placeholders) {
        String definition = LanguageManager.get(key, def, placeholders);
        if (definition == null || definition.trim().isEmpty()) {
            return null;
        }
        try {
            return BukkitUtils.deserializeItemStack(definition);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "[ReportsApprovedMenu] Failed to parse item for {0}: {1}",
                    new Object[]{key, definition});
            return new ItemStack(fallback);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (e.getPlayer().equals(this.player)) cancel();
    }

    private List<Integer> sanitizeSlots(List<Integer> input, int limit) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> sanitized = new ArrayList<>();
        for (Integer value : input) {
            if (value == null) continue;
            int slot = value;
            if (slot >= 0 && slot < limit && !sanitized.contains(slot)) {
                sanitized.add(slot);
            }
        }
        return sanitized;
    }

    private int clampSlot(int slot, int limit) {
        if (slot < 0) {
            return -1;
        }
        return slot >= limit ? -1 : slot;
    }

    private int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
