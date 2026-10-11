package dev.zonely.whiteeffect.esports.menu;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.esports.EsportsLang;
import dev.zonely.whiteeffect.esports.EsportsManager;
import dev.zonely.whiteeffect.esports.model.EsportsTournamentSummary;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EsportsTournamentBrowserMenu extends PlayerMenu {

    private static final int SIZE = 54;
    private static final int PAGE_OFF = 9;
    private static final int PAGE_MAX = 36;
    private static final int INFO_SLOT = 4;
    private static final int FILTER_SLOT = 46;
    private static final int REFRESH_SLOT = 49;
    private static final int CLOSE_SLOT = 45;
    private static final int CREATE_SLOT = 53;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.ENGLISH);
    private static final String[] STATES = {"live", "upcoming", "completed", "all"};

    private final Core plugin;
    private final EsportsManager manager;
    private final boolean admin;
    private int stateIndex;
    private final Map<Integer, EsportsTournamentSummary> mapping = new HashMap<>();

    public EsportsTournamentBrowserMenu(Core plugin, Player player, EsportsManager manager, String state, boolean admin) {
        super(player, EsportsLang.get("menus.browser.title", "&8Tournaments"), 6);
        this.plugin = plugin;
        this.manager = manager;
        this.admin = admin;
        this.stateIndex = resolveIndex(state);
        fillBackground();
        render();
        this.register(plugin);
        this.open();
    }

    private int resolveIndex(String state) {
        if (state == null) {
            return 0;
        }
        String lower = state.toLowerCase(Locale.ROOT);
        for (int i = 0; i < STATES.length; i++) {
            if (STATES[i].equalsIgnoreCase(lower)) {
                return i;
            }
        }
        return 0;
    }

    private void fillBackground() {
        for (int slot = 0; slot < getInventory().getSize(); slot++) {
            if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8) {
                setItem(slot, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:3 : 1 : name>&r"));
            } else {
                setItem(slot, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1 : name>&r"));
            }
        }
        setItem(INFO_SLOT, BukkitUtils.deserializeItemStack(
                "NETHER_STAR : 1 : name>" + EsportsLang.get("menus.browser.info.name", "&b&lTournament Browser")
                        + " : desc>" + EsportsLang.get("menus.browser.info.lore.1", "&7View live, upcoming and completed events.")
                        + "\\n" + EsportsLang.get("menus.browser.info.lore.2", "&7Click a card for details.")));
        setItem(CLOSE_SLOT, BukkitUtils.deserializeItemStack(
                "BARRIER : 1 : name>" + EsportsLang.get("menus.browser.close.name", "&cClose")
                        + " : desc>" + EsportsLang.get("menus.browser.close.lore", "&7Close this menu.")));
    }

    private void render() {
        mapping.clear();
        for (int slot = 0; slot < getInventory().getSize(); slot++) {
            if (slot == INFO_SLOT || slot == CLOSE_SLOT || slot == FILTER_SLOT || slot == REFRESH_SLOT || (admin && slot == CREATE_SLOT)) {
                continue;
            }
            setItem(slot, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1 : name>&r"));
        }

        String filter = STATES[stateIndex];
        List<EsportsTournamentSummary> tournaments = manager.listTournaments(filter, PAGE_MAX);
        for (int i = 0; i < tournaments.size() && i < PAGE_MAX; i++) {
            EsportsTournamentSummary summary = tournaments.get(i);
            int slot = PAGE_OFF + i;
            mapping.put(slot, summary);
            setItem(slot, buildCard(summary));
        }

        setItem(FILTER_SLOT, BukkitUtils.deserializeItemStack(
                "COMPASS : 1 : name>" + EsportsLang.get("menus.browser.filter.name", "&eFilter")
                        + " : desc>" + EsportsLang.get("menus.browser.filter.current", "&7Current: &f{filter}", "filter", filter)
                        + "\\n" + EsportsLang.get("menus.browser.filter.click", "&7Click to cycle.")));
        setItem(REFRESH_SLOT, BukkitUtils.deserializeItemStack(
                "SLIME_BALL : 1 : name>" + EsportsLang.get("menus.browser.refresh.name", "&aRefresh")
                        + " : desc>" + EsportsLang.get("menus.browser.refresh.lore", "&7Reload tournament list.")));
        if (admin) {
            setItem(CREATE_SLOT, BukkitUtils.deserializeItemStack(
                    "NETHER_STAR : 1 : name>" + EsportsLang.get("menus.browser.create.name", "&aNew Tournament")
                            + " : desc>" + EsportsLang.get("menus.browser.create.lore", "&7Open creation wizard.")));
        }
    }

    private String format(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "-";
        }
        try {
            LocalDateTime time = LocalDateTime.parse(raw.replace(" ", "T"));
            return FORMATTER.format(time);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private Material resolveMaterial(String state) {
        String key;
        switch (state.toLowerCase(Locale.ROOT)) {
            case "live":
                key = "LIME_CONCRETE";
                break;
            case "upcoming":
                key = "LIGHT_BLUE_CONCRETE";
                break;
            case "completed":
                key = "RED_CONCRETE";
                break;
            default:
                key = "WHITE_CONCRETE";
                break;
        }
        Material mat = MaterialResolver.resolveMaterial(key);
        if (mat == null) {
            mat = MaterialResolver.resolveMaterial("WOOL");
        }
        if (mat == null) {
            mat = Material.PAPER;
        }
        return mat;
    }

    private org.bukkit.inventory.ItemStack buildCard(EsportsTournamentSummary summary) {
        Material mat = resolveMaterial(summary.getState() == null ? "" : summary.getState());
        String start = format(summary.getStartsAt());
        String end = format(summary.getEndsAt());
        return BukkitUtils.deserializeItemStack(mat.name() + " : 1 : name>&f" + summary.getName()
                + " : desc>" + EsportsLang.get("menus.browser.card.state", "&7State: &f{state}", "state", summary.getState())
                + "\\n" + EsportsLang.get("menus.browser.card.event", "&7Event: &f{event}", "event", summary.getEventType())
                + "\\n" + EsportsLang.get("menus.browser.card.start", "&7Start: &f{start}", "start", start)
                + "\\n" + EsportsLang.get("menus.browser.card.end", "&7End: &f{end}", "end", end));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(this.player)) return;
        if (!event.getView().getTopInventory().equals(getInventory())) return;
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (slot == CLOSE_SLOT) {
            EnumSound.CLICK.play(player, 1.0F, 1.0F);
            player.closeInventory();
            HandlerList.unregisterAll(this);
            return;
        }
        if (slot == FILTER_SLOT) {
            stateIndex = (stateIndex + 1) % STATES.length;
            EnumSound.CLICK.play(player, 1.0F, 1.1F);
            render();
            return;
        }
        if (slot == REFRESH_SLOT) {
            EnumSound.CLICK.play(player, 1.0F, 1.0F);
            render();
            return;
        }
        if (admin && slot == CREATE_SLOT) {
            EnumSound.CLICK.play(player, 1.0F, 1.2F);
            player.closeInventory();
            if (plugin.getEsportsCreationService() != null) {
                plugin.getEsportsCreationService().begin(player);
            } else {
                player.sendMessage(EsportsLang.get("menus.browser.creation-unavailable", "&cCreation service unavailable."));
            }
            HandlerList.unregisterAll(this);
            return;
        }
        EsportsTournamentSummary summary = mapping.get(slot);
        if (summary != null) {
            EnumSound.CLICK.play(player, 1.0F, 1.0F);
            new EsportsTournamentDetailMenu(plugin, player, manager, summary, admin);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(this.player) && event.getInventory().equals(getInventory())) {
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!event.getWhoClicked().equals(this.player)) return;
        if (!event.getView().getTopInventory().equals(getInventory())) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                event.setCancelled(true);
                event.setResult(org.bukkit.event.Event.Result.DENY);
                return;
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
