package dev.zonely.whiteeffect.esports.menu;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.esports.EsportsLang;
import dev.zonely.whiteeffect.esports.menu.EsportsCreationService.CreationSession;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class EsportsTournamentStartMenu extends PlayerMenu {

    private static final int SLOT_INFO = 13;
    private static final int SLOT_DAY_DOWN = 11;
    private static final int SLOT_DAY_UP = 15;
    private static final int SLOT_HOUR_DOWN = 20;
    private static final int SLOT_HOUR_UP = 24;
    private static final int SLOT_MIN_DOWN = 29;
    private static final int SLOT_MIN_UP = 33;
    private static final int SLOT_RESET = 47;
    private static final int SLOT_MANUAL = 51;
    private static final int SLOT_BACK = 49;

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
            .withLocale(Locale.ENGLISH);

    private final Core plugin;
    private final EsportsCreationService service;
    private final CreationSession session;

    public EsportsTournamentStartMenu(Core plugin,
                                      Player player,
                                      EsportsCreationService service,
                                      CreationSession session) {
        super(player, EsportsLang.get("menus.start.title", "&6Start Time"), 6);
        this.plugin = plugin;
        this.service = service;
        this.session = session;
        render();
        this.register(plugin);
        this.open();
    }

    private void render() {
        getInventory().clear();
        LocalDateTime selected = session.ensureStartDate();
        setItem(SLOT_INFO, BukkitUtils.deserializeItemStack(
                mat("CLOCK", "WATCH") + " : 1 : name>" + EsportsLang.get("menus.start.info.name", "&6Planned Start")
                        + " : desc>" + EsportsLang.get("menus.start.info.current", "&8 - &7Current: &f{value}", "value", format(selected))
                        + "\\n" + EsportsLang.get("menus.start.info.shift", "&8 - &7Shift-click applies larger steps.")
                        + "\\n" + EsportsLang.get("menus.start.info.minimum", "&8 - &7Minimum: &f{value}",
                        "value", format(LocalDateTime.now().plusMinutes(5)))));

        setItem(SLOT_DAY_DOWN, adjustItem(EsportsLang.get("menus.start.adjust.day-down.name", "&c-1 Day"), -1440,
                EsportsLang.get("menus.start.adjust.day-down.lore", "&7Left click: -1 day\\n&7Shift+Click: -7 days")));
        setItem(SLOT_DAY_UP, adjustItem(EsportsLang.get("menus.start.adjust.day-up.name", "&a+1 Day"), 1440,
                EsportsLang.get("menus.start.adjust.day-up.lore", "&7Left click: +1 day\\n&7Shift+Click: +7 days")));
        setItem(SLOT_HOUR_DOWN, adjustItem(EsportsLang.get("menus.start.adjust.hour-down.name", "&c-1 Hour"), -60,
                EsportsLang.get("menus.start.adjust.hour-down.lore", "&7Left click: -1 hour\\n&7Shift+Click: -6 hours")));
        setItem(SLOT_HOUR_UP, adjustItem(EsportsLang.get("menus.start.adjust.hour-up.name", "&a+1 Hour"), 60,
                EsportsLang.get("menus.start.adjust.hour-up.lore", "&7Left click: +1 hour\\n&7Shift+Click: +6 hours")));
        setItem(SLOT_MIN_DOWN, adjustItem(EsportsLang.get("menus.start.adjust.minute-down.name", "&c-15 Minutes"), -15,
                EsportsLang.get("menus.start.adjust.minute-down.lore", "&7Left click: -15m\\n&7Shift+Click: -60m")));
        setItem(SLOT_MIN_UP, adjustItem(EsportsLang.get("menus.start.adjust.minute-up.name", "&a+15 Minutes"), 15,
                EsportsLang.get("menus.start.adjust.minute-up.lore", "&7Left click: +15m\\n&7Shift+Click: +60m")));

        setItem(SLOT_RESET, BukkitUtils.deserializeItemStack(
                mat("NETHER_STAR", "COMPASS") + " : 1 : name>" + EsportsLang.get("menus.start.reset.name", "&dReset")
                        + " : desc>" + EsportsLang.get("menus.start.reset.lore", "&7Set back to default (30 minutes later).")));
        setItem(SLOT_MANUAL, BukkitUtils.deserializeItemStack(
                mat("BOOK_AND_QUILL", "BOOK") + " : 1 : name>" + EsportsLang.get("menus.start.manual.name", "&eManual Input")
                        + " : desc>" + EsportsLang.get("menus.start.manual.lore", "&7Enter date via chat/sign prompt.")));
        setItem(SLOT_BACK, BukkitUtils.deserializeItemStack(
                mat("ARROW", "ARROW") + " : 1 : name>" + EsportsLang.get("menus.start.back.name", "&7Back")
                        + " : desc>" + EsportsLang.get("menus.start.back.lore", "&7Return to main setup.")));

        fillGaps();
    }

    private String mat(String preferred, String legacy) {
        Material material = MaterialResolver.resolveMaterial(preferred);
        if (material == null && legacy != null) {
            material = MaterialResolver.resolveMaterial(legacy);
        }
        if (material == null) {
            material = Material.BOOK;
        }
        return material.name();
    }

    private void fillGaps() {
        String filler = "STAINED_GLASS_PANE:7 : 1 : name>&r";
        for (int i = 0; i < getInventory().getSize(); i++) {
            if (getInventory().getItem(i) == null) {
                setItem(i, BukkitUtils.deserializeItemStack(filler));
            }
        }
    }

    private org.bukkit.inventory.ItemStack adjustItem(String title, int minutes, String lore) {
        return BukkitUtils.deserializeItemStack(
                "ARROW : 1 : name>" + title + " : desc>" + lore);
    }

    private String format(LocalDateTime value) {
        return value == null ? "-" : value.format(DISPLAY_FORMAT);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(this.player)) return;
        if (!event.getView().getTopInventory().equals(getInventory())) return;
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);
        event.setCursor(null);
        event.getWhoClicked().setItemOnCursor(null);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        EnumSound.CLICK.play(this.player, 1.0F, 1.4F);
        if (slot == SLOT_DAY_DOWN) {
            adjustTime(event, -1440, 7);
            return;
        }
        if (slot == SLOT_DAY_UP) {
            adjustTime(event, 1440, 7);
            return;
        }
        if (slot == SLOT_HOUR_DOWN) {
            adjustTime(event, -60, 6);
            return;
        }
        if (slot == SLOT_HOUR_UP) {
            adjustTime(event, 60, 6);
            return;
        }
        if (slot == SLOT_MIN_DOWN) {
            adjustTime(event, -15, 4);
            return;
        }
        if (slot == SLOT_MIN_UP) {
            adjustTime(event, 15, 4);
            return;
        }
        if (slot == SLOT_RESET) {
            session.resetStartDateToDefault();
            sendUpdateMessage();
            render();
            return;
        }
        if (slot == SLOT_MANUAL) {
            player.closeInventory();
            service.requestStartDateInput(session);
            return;
        }
        if (slot == SLOT_BACK) {
            service.openWizard(player, session);
        }
    }

    private void adjustTime(InventoryClickEvent event, int baseMinutes, int shiftMultiplier) {
        int minutes = baseMinutes;
        if (event.isShiftClick()) {
            minutes *= shiftMultiplier;
        }
        if (session.adjustStartDateMinutes(minutes)) {
            sendUpdateMessage();
            render();
        } else {
            EnumSound.ITEM_BREAK.play(this.player, 1.0F, 0.8F);
        }
    }

    private void sendUpdateMessage() {
        LocalDateTime selected = session.ensureStartDate();
        player.sendMessage(EsportsLang.get("start.date-set",
                "&aStart date set to: &6{date}",
                "date", format(selected)));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(this.player) && event.getInventory().equals(getInventory())) {
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!event.getWhoClicked().equals(this.player)) return;
        if (!event.getView().getTopInventory().equals(getInventory())) return;
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < getInventory().getSize()) {
                event.setCancelled(true);
                event.setResult(org.bukkit.event.Event.Result.DENY);
                break;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(this.player)) {
            HandlerList.unregisterAll(this);
        }
    }
}
