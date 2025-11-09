package dev.zonely.whiteeffect.menus.reports;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.menu.PagedPlayerMenu;
import dev.zonely.whiteeffect.report.Report;
import dev.zonely.whiteeffect.report.ReportService;
import dev.zonely.whiteeffect.utils.BukkitUtils;
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

public class MenuReportHistory extends PagedPlayerMenu {

    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private final ReportService service;
    private final Map<ItemStack, Report> mapping = new HashMap<>();

    public MenuReportHistory(Player viewer, ReportService service, String target) {
        super(viewer, LanguageManager.get("menus.reports.history.title",
                "&8Reports for &c{player}", "player", target), 6);
        this.service = service;

        this.previousPage = 45;
        this.nextPage = 53;
        this.onlySlots(Arrays.asList(
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34,
                37,38,39,40,41,42,43
        ));

        ItemStack filler = parseItem("menus.common.filler",
                "STAINED_GLASS_PANE:7 : 1 : name>&r",
                Material.GLASS);
        for (int i : new int[]{0,1,2,3,4,5,6,7,8}) {
            setItem(i, filler.clone());
        }

        populate(target);
        this.register(Core.getInstance());
        this.open();
    }

    private void populate(String target) {
        mapping.clear();
        List<ItemStack> items = new ArrayList<>();
        List<Report> list = service.listReportsForTarget(target);
        for (Report r : list) {
            ItemStack icon = parseItem("menus.reports.history.entry",
                    "PAPER : 1 : name>&6Report #{id} : desc>&7Reporter: &f{reporter}\n&7Reason: &f{reason}\n&7Date: &f{date}\n&7Server: &f{server}\n&7Status: &e{status}",
                    Material.PAPER,
                    "id", r.getId(),
                    "reporter", r.getReporter(),
                    "reason", r.getReason(),
                    "date", DATE.format(r.getCreatedAt()),
                    "server", r.getServer() == null ? "Unknown" : r.getServer(),
                    "status", r.getStatus().name());
            items.add(icon);
            mapping.put(icon, r);
        }
        if (items.isEmpty()) {
            items.add(parseItem("menus.reports.history.empty",
                    "BARRIER : 1 : name>&cNo reports on this player.",
                    Material.BARRIER));
        }
        setItems(items);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getInventory().equals(getCurrentInventory())) return;
        if (!e.getWhoClicked().equals(this.player)) return;
        e.setCancelled(true);
        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        Report r = mapping.get(item);
        if (r == null) return;
        EnumSound.CLICK.play(player, 0.5F, 1.4F);
        new MenuReportDetail(Core.getInstance(), player, service, r.getId());
    }

    public void cancel() {
        mapping.clear();
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
                    "[ReportHistoryMenu] Failed to parse item for {0}: {1}",
                    new Object[]{key, definition});
            return new ItemStack(fallback);
        }
    }
}
