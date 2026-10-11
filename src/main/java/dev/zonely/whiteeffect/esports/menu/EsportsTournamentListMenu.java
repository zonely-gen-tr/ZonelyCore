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
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EsportsTournamentListMenu extends PlayerMenu {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.ENGLISH);
    private static final String[] STATES = {"all", "live", "upcoming", "completed"};
    private static final int[] FILTER_SLOTS = {1, 3, 5, 7};
    private static final int INFO_SLOT = 4;
    private static final int CLOSE_SLOT = 45;
    private static final int REFRESH_SLOT = 49;
    private static final int CREATE_SLOT = 53;
    private static final int[] CARD_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final Core plugin;
    private final EsportsManager manager;
    private final String filter;
    private final boolean admin;
    private final Map<Integer, EsportsTournamentSummary> mapping = new HashMap<>();

    public EsportsTournamentListMenu(Core plugin, Player player, EsportsManager manager, String filter, boolean admin) {
        super(player, EsportsLang.get("menus.list.title", "&8Tournament Hub"), 6);
        this.plugin = plugin;
        this.manager = manager;
        this.filter = filter == null ? "all" : filter.toLowerCase(Locale.ROOT);
        this.admin = admin;
        fillBackground();
        populate();
        this.register(plugin);
        this.open();
    }

    private void fillBackground() {
        ItemStack filler = BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1 : name>&r");
        for (int slot = 0; slot < getInventory().getSize(); slot++) {
            boolean border = slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8;
            if (border) {
                setItem(slot, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:11 : 1 : name>&r"));
            } else {
                setItem(slot, filler.clone());
            }
        }
        setItem(INFO_SLOT, BukkitUtils.deserializeItemStack(
                "NETHER_STAR : 1 : name>" + EsportsLang.get("menus.list.info.name", "&b&lTournament Selector")
                        + " : desc>" + EsportsLang.get("menus.list.info.lore.1", "&7Tournament browser.")
                        + "\\n" + EsportsLang.get("menus.list.info.lore.2", "&7Filter by state and click a card")
                        + "\\n" + EsportsLang.get("menus.list.info.lore.3", "&7to open tournament details.")
        ));
        setItem(CLOSE_SLOT, BukkitUtils.deserializeItemStack(
                "BARRIER : 1 : name>" + EsportsLang.get("menus.list.close.name", "&cClose")
                        + " : desc>" + EsportsLang.get("menus.list.close.lore", "&7Exit this menu.")
        ));
        setItem(REFRESH_SLOT, BukkitUtils.deserializeItemStack(
                "SLIME_BALL : 1 : name>" + EsportsLang.get("menus.list.refresh.name", "&aRefresh")
                        + " : desc>" + EsportsLang.get("menus.list.refresh.lore", "&7Reload tournament cards.")
        ));
    }

    private void populate() {
        mapping.clear();
        for (int i = 0; i < FILTER_SLOTS.length; i++) {
            int slot = FILTER_SLOTS[i];
            String state = STATES[i];
            boolean selected = state.equalsIgnoreCase(filter);
            Material material = resolveFilterMaterial(state);
            String label = state.equals("all")
                    ? EsportsLang.get("menus.list.filter.all", "All Tournaments")
                    : state.substring(0, 1).toUpperCase(Locale.ROOT) + state.substring(1);
            ItemStack item = BukkitUtils.deserializeItemStack(material.name() + " : 1 : name>"
                    + (selected ? "&a&l" : "&e") + label
                    + " : desc>" + EsportsLang.get("menus.list.filter.view", "&7View: &f{state}", "state", state)
                    + "\\n" + EsportsLang.get("menus.list.filter.state", "&7State: {state}", "state",
                    selected
                            ? EsportsLang.get("menus.list.filter.state-selected", "&aSelected")
                            : EsportsLang.get("menus.list.filter.state-inactive", "&cInactive"))
                    + "\\n" + EsportsLang.get("menus.list.filter.click", "&eClick -> Apply filter."));
            setItem(slot, item);
        }

        List<EsportsTournamentSummary> tournaments = manager.listTournaments(filter, CARD_SLOTS.length);
        for (int i = 0; i < tournaments.size() && i < CARD_SLOTS.length; i++) {
            int slot = CARD_SLOTS[i];
            EsportsTournamentSummary summary = tournaments.get(i);
            if (slot < 0 || slot >= getInventory().getSize()) {
                break;
            }
            setItem(slot, buildCard(summary));
            mapping.put(slot, summary);
        }

        if (tournaments.isEmpty()) {
            setItem(31, BukkitUtils.deserializeItemStack(
                    "MAP : 1 : name>" + EsportsLang.get("menus.list.empty.name", "&cNo Tournaments Found")
                            + " : desc>" + EsportsLang.get("menus.list.empty.lore", "&7No cards match the selected filter.")
            ));
        }

        if (admin) {
            ItemStack create = BukkitUtils.deserializeItemStack("NETHER_STAR : 1 : name>"
                    + EsportsLang.get("menus.list.create.name", "&aCreate Tournament")
                    + " : desc>" + EsportsLang.get("menus.list.create.lore", "&7Open creation wizard."));
            setItem(CREATE_SLOT, create);
        }
    }

    private ItemStack buildCard(EsportsTournamentSummary summary) {
        String state = summary.getState() == null ? "unknown" : summary.getState().toLowerCase(Locale.ROOT);
        String material;
        String color;
        switch (state) {
            case "live":
                material = "EMERALD";
                color = "&a";
                break;
            case "upcoming":
                material = "CLOCK";
                color = "&b";
                break;
            case "completed":
                material = "NETHER_STAR";
                color = "&c";
                break;
            default:
                material = "PAPER";
                color = "&7";
                break;
        }
        String start = format(summary.getStartsAt());
        String end = format(summary.getEndsAt());
        String timer = buildTimer(summary.getStartsAt(), state);
        Material resolved = MaterialResolver.resolveMaterial(material);
        if (resolved == null) {
            if ("CLOCK".equalsIgnoreCase(material)) {
                resolved = MaterialResolver.resolveMaterial("WATCH");
            }
        }
        if (resolved == null) {
            resolved = Material.PAPER;
        }
        return BukkitUtils.deserializeItemStack(resolved.name() + " : 1 : name>" + color + "&l" + summary.getName()
                + " : desc>" + EsportsLang.get("menus.list.card.status", "&7Status: {state}", "state", color + safe(summary.getState()))
                + "\\n" + EsportsLang.get("menus.list.card.event", "&7Event: &f{event}", "event", summary.getEventType())
                + "\\n" + EsportsLang.get("menus.list.card.start", "&7Start: &f{start}", "start", start)
                + "\\n" + EsportsLang.get("menus.list.card.end", "&7End: &f{end}", "end", end)
                + "\\n" + EsportsLang.get("menus.list.card.timer", "&7Timer: &f{timer}", "timer", timer)
                + "\\n"
                + "\\n" + EsportsLang.get("menus.list.card.click", "&eClick for details."));
    }

    private Material resolveFilterMaterial(String state) {
        if ("live".equalsIgnoreCase(state)) {
            Material dye = MaterialResolver.resolveMaterial("LIME_DYE");
            return dye == null ? Material.SLIME_BALL : dye;
        }
        if ("upcoming".equalsIgnoreCase(state)) {
            Material clock = MaterialResolver.resolveMaterial("CLOCK");
            if (clock == null) {
                clock = MaterialResolver.resolveMaterial("WATCH");
            }
            return clock == null ? Material.BOOK : clock;
        }
        if ("completed".equalsIgnoreCase(state)) {
            Material nether = MaterialResolver.resolveMaterial("NETHER_STAR");
            return nether == null ? Material.REDSTONE : nether;
        }
        return Material.COMPASS;
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
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

    private String buildTimer(String rawStart, String state) {
        if ("live".equalsIgnoreCase(state)) {
            return EsportsLang.get("menus.list.timer.live", "Live now");
        }
        if ("completed".equalsIgnoreCase(state)) {
            return EsportsLang.get("menus.list.timer.finished", "Finished");
        }
        if (rawStart == null || rawStart.trim().isEmpty()) {
            return "-";
        }
        try {
            LocalDateTime startsAt = LocalDateTime.parse(rawStart.replace(" ", "T"));
            Duration duration = Duration.between(LocalDateTime.now(), startsAt);
            long seconds = duration.getSeconds();
            if (seconds <= 0L) {
                return EsportsLang.get("menus.list.timer.starting", "Starting...");
            }
            long days = seconds / 86400L;
            long hours = (seconds % 86400L) / 3600L;
            long minutes = (seconds % 3600L) / 60L;
            if (days > 0L) {
                return days + "d " + hours + "h";
            }
            if (hours > 0L) {
                return hours + "h " + minutes + "m";
            }
            return Math.max(1L, minutes) + "m";
        } catch (Exception ignored) {
            return "-";
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(this.player)) {
            return;
        }
        if (!event.getView().getTopInventory().equals(getInventory())) {
            return;
        }
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        for (int i = 0; i < FILTER_SLOTS.length; i++) {
            if (slot == FILTER_SLOTS[i]) {
                String newFilter = STATES[i];
                if (!newFilter.equalsIgnoreCase(filter)) {
                    EnumSound.CLICK.play(this.player, 1.0F, 1.0F);
                    new EsportsTournamentListMenu(plugin, player, manager, newFilter, admin);
                }
                return;
            }
        }

        if (slot == CLOSE_SLOT) {
            EnumSound.CLICK.play(this.player, 1.0F, 0.9F);
            this.player.closeInventory();
            HandlerList.unregisterAll(this);
            return;
        }

        if (slot == REFRESH_SLOT) {
            EnumSound.CLICK.play(this.player, 1.0F, 1.1F);
            new EsportsTournamentListMenu(plugin, player, manager, filter, admin);
            return;
        }

        if (admin && slot == CREATE_SLOT) {
            player.closeInventory();
            if (plugin.getEsportsCreationService() != null) {
                plugin.getEsportsCreationService().begin(player);
            } else {
                player.sendMessage(EsportsLang.get("menus.list.setup-service-unavailable", "&cSetup service is unavailable."));
            }
            return;
        }

        EsportsTournamentSummary summary = mapping.get(slot);
        if (summary != null) {
            EnumSound.CLICK.play(this.player, 1.0F, 1.0F);
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
        if (!event.getWhoClicked().equals(this.player)) {
            return;
        }
        if (!event.getView().getTopInventory().equals(getInventory())) {
            return;
        }
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

