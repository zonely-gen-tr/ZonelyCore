package dev.zonely.whiteeffect.esports.menu;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.esports.EsportsLang;
import dev.zonely.whiteeffect.esports.EsportsManager;
import dev.zonely.whiteeffect.esports.game.EsportsGameService;
import dev.zonely.whiteeffect.esports.model.EsportsClubEntry;
import dev.zonely.whiteeffect.esports.model.EsportsMatchData;
import dev.zonely.whiteeffect.esports.model.EsportsTournamentData;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class EsportsBracketMenu extends PlayerMenu {

    private static final int INFO_SLOT = 4;
    private static final int CLOSE_SLOT = 49;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int FILTER_SLOT = 40;
    private static final int[] ROUND_SLOTS = {10, 12, 14, 16};

    private final Core plugin;
    private final EsportsManager manager;
    private final ItemStack fillerPane;
    private final List<Integer> roundOrder = new ArrayList<>();
    private final Map<Integer, Integer> slotRoundMap = new TreeMap<>();
    private int pageIndex;
    private boolean liveFilter;

    public EsportsBracketMenu(Core plugin, Player player, EsportsManager manager) {
        super(player, EsportsLang.get("menus.bracket.title", "&3Tournament Bracket"), 6);
        this.plugin = plugin;
        this.manager = manager;
        this.fillerPane = BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1 : name>&r");
        this.pageIndex = 0;
        this.liveFilter = false;
        fillBackground();
        render();
        this.register(plugin);
        this.open();
    }

    private void fillBackground() {
        for (int slot = 0; slot < getInventory().getSize(); slot++) {
            if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8) {
                setItem(slot, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:3 : 1 : name>&r"));
            } else {
                setItem(slot, fillerPane.clone());
            }
        }
        setItem(INFO_SLOT, BukkitUtils.deserializeItemStack(
                "NETHER_STAR : 1 : name>" + EsportsLang.get("menus.bracket.info.name", "&b&lTournament Bracket")
                        + " : desc>" + EsportsLang.get("menus.bracket.info.lore.1", "&7Track matches round by round.")
                        + "\\n" + EsportsLang.get("menus.bracket.info.lore.2", "&7Filter live rounds,")
                        + "\\n" + EsportsLang.get("menus.bracket.info.lore.3", "&7or click a round card to spectate.")
        ));
        setItem(CLOSE_SLOT, BukkitUtils.deserializeItemStack(
                "BARRIER : 1 : name>" + EsportsLang.get("menus.bracket.close.name", "&cClose")
                        + " : desc>" + EsportsLang.get("menus.bracket.close.lore", "&7Exit this menu.")
        ));
    }

    private void render() {
        clearRoundSlots();
        EsportsTournamentData tournament = manager.getActiveTournament();
        if (tournament == null) {
            setItem(22, BukkitUtils.deserializeItemStack(
                    "MAP : 1 : name>" + EsportsLang.get("menus.bracket.no-active.name", "&cNo Active Tournament")
                            + " : desc>" + EsportsLang.get("menus.bracket.no-active.lore", "&7There is no bracket data to display.")
            ));
            setNavigationDisabled();
            return;
        }
        Map<Integer, List<EsportsMatchData>> byRound = new TreeMap<>();
        for (EsportsMatchData match : tournament.getMatches()) {
            byRound.computeIfAbsent(match.getRound(), ignored -> new ArrayList<>()).add(match);
        }
        roundOrder.clear();
        roundOrder.addAll(byRound.keySet());
        if (roundOrder.isEmpty()) {
            setItem(22, BukkitUtils.deserializeItemStack(
                    "MAP : 1 : name>" + EsportsLang.get("menus.bracket.no-matches.name", "&cNo Match Data")
                            + " : desc>" + EsportsLang.get("menus.bracket.no-matches.lore", "&7No bracket matches were found.")
            ));
            setNavigationDisabled();
            return;
        }
        int liveRound = resolveLiveRound(byRound);
        if (liveFilter && (liveRound <= 0 || !byRound.containsKey(liveRound))) {
            liveFilter = false;
        }
        List<Integer> visibleRounds = resolveVisibleRounds(byRound, liveRound);
        if (visibleRounds.isEmpty()) {
            setItem(22, BukkitUtils.deserializeItemStack(
                    "MAP : 1 : name>" + EsportsLang.get("menus.bracket.no-filtered.name", "&cNo Match Data")
                            + " : desc>" + EsportsLang.get("menus.bracket.no-filtered.lore", "&7No rounds match the selected filter.")
            ));
        } else {
            setItem(22, fillerPane.clone());
        }
        int slotIndex = 0;
        for (Integer round : visibleRounds) {
            if (slotIndex >= ROUND_SLOTS.length) {
                break;
            }
            List<EsportsMatchData> matches = new ArrayList<>(byRound.getOrDefault(round, Collections.emptyList()));
            matches.sort(Comparator.comparingInt(EsportsMatchData::getBracketSlot));
            boolean highlight = liveRound > 0 && liveRound == round;
            int slot = ROUND_SLOTS[slotIndex];
            setItem(slot, buildRoundItem(round, matches, highlight));
            slotRoundMap.put(slot, round);
            slotIndex++;
        }
        while (slotIndex < ROUND_SLOTS.length) {
            setItem(ROUND_SLOTS[slotIndex], fillerPane.clone());
            slotIndex++;
        }
        updateNavigationControls(liveRound);
    }

    private void clearRoundSlots() {
        slotRoundMap.clear();
        for (int slot : ROUND_SLOTS) {
            setItem(slot, fillerPane.clone());
        }
    }

    private void setNavigationDisabled() {
        liveFilter = false;
        pageIndex = 0;
        setItem(PREV_SLOT, buildNavItem(false, false));
        setItem(NEXT_SLOT, buildNavItem(true, false));
        setItem(FILTER_SLOT, buildFilterItem(0));
    }

    private int resolveLiveRound(Map<Integer, List<EsportsMatchData>> byRound) {
        EsportsGameService service = manager.getGameService();
        if (service != null && service.hasActiveMatchContext()) {
            EsportsMatchData current = manager.findMatchById(service.getCurrentMatchId()).orElse(null);
            if (current != null) {
                return current.getRound();
            }
        }
        for (Map.Entry<Integer, List<EsportsMatchData>> entry : byRound.entrySet()) {
            boolean hasPending = entry.getValue().stream()
                    .anyMatch(match -> match.getState() == null || !"completed".equalsIgnoreCase(match.getState()));
            if (hasPending) {
                return entry.getKey();
            }
        }
        return 0;
    }

    private List<Integer> resolveVisibleRounds(Map<Integer, List<EsportsMatchData>> byRound, int liveRound) {
        List<Integer> visible = new ArrayList<>();
        if (roundOrder.isEmpty()) {
            return visible;
        }
        if (liveFilter && liveRound > 0 && byRound.containsKey(liveRound)) {
            visible.add(liveRound);
            return visible;
        }
        int maxPage = getMaxPage();
        pageIndex = Math.max(0, Math.min(pageIndex, maxPage));
        int start = pageIndex * ROUND_SLOTS.length;
        int end = Math.min(start + ROUND_SLOTS.length, roundOrder.size());
        visible.addAll(roundOrder.subList(start, end));
        return visible;
    }

    private void updateNavigationControls(int liveRound) {
        setItem(PREV_SLOT, buildNavItem(false, !liveFilter && pageIndex > 0));
        boolean hasNext = !liveFilter && pageIndex < getMaxPage();
        setItem(NEXT_SLOT, buildNavItem(true, hasNext));
        setItem(FILTER_SLOT, buildFilterItem(liveRound));
    }

    private int getMaxPage() {
        return roundOrder.isEmpty() ? 0 : (roundOrder.size() - 1) / ROUND_SLOTS.length;
    }

    private ItemStack buildRoundItem(int round, List<EsportsMatchData> matches, boolean liveRound) {
        ItemStack item = new ItemStack(liveRound ? Material.ENCHANTED_BOOK : Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        ChatColor titleColor = liveRound ? ChatColor.GREEN : ChatColor.AQUA;
        String title = liveRound
                ? EsportsLang.get("menus.bracket.round.live-title", "LIVE ROUND #{round}", "round", round)
                : EsportsLang.get("menus.bracket.round.title", "ROUND #{round}", "round", round);
        meta.setDisplayName(titleColor + "" + ChatColor.BOLD + title);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + EsportsLang.get("menus.bracket.round.separator", "--------------------"));
        if (liveRound) {
            lore.add(ChatColor.GREEN + EsportsLang.get("menus.bracket.round.live-now", ">> Currently live <<"));
        }
        if (matches.isEmpty()) {
            lore.add(ChatColor.GRAY + EsportsLang.get("menus.bracket.round.no-matches", "No matches."));
        } else {
            for (EsportsMatchData match : matches) {
                String state = match.getState() == null ? "-" : match.getState().toUpperCase(Locale.ROOT);
                lore.add(ChatColor.YELLOW + "#" + match.getBracketSlot()
                        + ChatColor.GRAY + " " + formatClub(match.getClubAId())
                        + ChatColor.DARK_GRAY + " vs " + formatClub(match.getClubBId()));
                String winner = match.getWinnerClubId() == null
                        ? ChatColor.DARK_GRAY + EsportsLang.get("menus.bracket.round.status", "   Status: ")
                        + ChatColor.WHITE + state
                        : ChatColor.GREEN + EsportsLang.get("menus.bracket.round.winner", "   Winner: ")
                        + formatClub(match.getWinnerClubId());
                lore.add(winner);
            }
        }
        lore.add(ChatColor.DARK_GRAY + EsportsLang.get("menus.bracket.round.separator", "--------------------"));
        lore.add(ChatColor.YELLOW + EsportsLang.get("menus.bracket.round.click", "Click -> Enter spectator mode"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildNavItem(boolean forward, boolean enabled) {
        ItemStack item = enabled
                ? new ItemStack(Material.ARROW)
                : BukkitUtils.deserializeItemStack("GRAY_STAINED_GLASS_PANE : 1 : name>&r");
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String label = forward
                ? EsportsLang.get("menus.bracket.nav.next", "Next Rounds")
                : EsportsLang.get("menus.bracket.nav.previous", "Previous Rounds");
        meta.setDisplayName((enabled ? ChatColor.YELLOW : ChatColor.DARK_GRAY) + label);
        List<String> lore = new ArrayList<>();
        if (liveFilter) {
            lore.add(ChatColor.RED + EsportsLang.get("menus.bracket.nav.disabled-live", "Disabled while live filter is active."));
        } else if (!enabled) {
            lore.add(ChatColor.GRAY + EsportsLang.get("menus.bracket.nav.no-more", "No more rounds to show."));
        } else {
            lore.add(ChatColor.GRAY + EsportsLang.get("menus.bracket.nav.page",
                    "Page {current}/{max}",
                    "current", pageIndex + 1,
                    "max", getMaxPage() + 1));
            lore.add(ChatColor.YELLOW + EsportsLang.get("menus.bracket.nav.click",
                    "Click -> {direction}",
                    "direction", forward
                            ? EsportsLang.get("menus.bracket.nav.direction-next", "next")
                            : EsportsLang.get("menus.bracket.nav.direction-back", "back")));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildFilterItem(int liveRound) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.AQUA + EsportsLang.get("menus.bracket.filter.name", "Live Round Filter"));
        List<String> lore = new ArrayList<>();
        if (liveRound > 0) {
            lore.add(ChatColor.GRAY + EsportsLang.get("menus.bracket.filter.live-round", "Live round: #{round}", "round", liveRound));
        } else {
            lore.add(ChatColor.DARK_GRAY + EsportsLang.get("menus.bracket.filter.no-live", "No live round found."));
        }
        lore.add(liveFilter
                ? ChatColor.GREEN + EsportsLang.get("menus.bracket.filter.status-enabled", "Status: Enabled")
                : ChatColor.RED + EsportsLang.get("menus.bracket.filter.status-disabled", "Status: Disabled"));
        lore.add(ChatColor.YELLOW + EsportsLang.get("menus.bracket.filter.click", "Click -> Toggle"));
        meta.setLore(lore);
        if (liveFilter && liveRound > 0) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String formatClub(Integer clubId) {
        if (clubId == null) {
            return ChatColor.DARK_GRAY + EsportsLang.get("menus.bracket.club.empty", "EMPTY");
        }
        return manager.getClubById(clubId)
                .map(entry -> ChatColor.YELLOW + entry.getName() + ChatColor.DARK_GRAY + " [" + entry.getTag() + "]")
                .orElse(ChatColor.YELLOW + EsportsLang.get("menus.bracket.club.unknown", "Unknown"));
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
        if (slot == CLOSE_SLOT) {
            EnumSound.CLICK.play(player, 1.0F, 1.0F);
            player.closeInventory();
            HandlerList.unregisterAll(this);
            return;
        }
        if (slot == PREV_SLOT) {
            handlePrevPage();
            return;
        }
        if (slot == NEXT_SLOT) {
            handleNextPage();
            return;
        }
        if (slot == FILTER_SLOT) {
            toggleLiveFilter();
            return;
        }
        if (slotRoundMap.containsKey(slot)) {
            handleRoundSelect(slotRoundMap.get(slot));
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

    private void handlePrevPage() {
        if (liveFilter || pageIndex <= 0) {
            return;
        }
        pageIndex--;
        EnumSound.CLICK.play(player, 1.0F, 0.9F);
        render();
    }

    private void handleNextPage() {
        if (liveFilter || pageIndex >= getMaxPage()) {
            return;
        }
        pageIndex++;
        EnumSound.CLICK.play(player, 1.0F, 1.1F);
        render();
    }

    private void toggleLiveFilter() {
        boolean attemptedEnable = !liveFilter;
        liveFilter = attemptedEnable;
        if (liveFilter) {
            pageIndex = 0;
        }
        EnumSound.CLICK.play(player, 1.0F, attemptedEnable ? 1.2F : 0.8F);
        render();
        if (liveFilter) {
            player.sendMessage(EsportsLang.get("menus.bracket.messages.filter-enabled", "&bLive round filter enabled."));
        } else if (attemptedEnable) {
            player.sendMessage(EsportsLang.get("menus.bracket.messages.no-live", "&cNo live round found. Showing all rounds."));
        } else {
            player.sendMessage(EsportsLang.get("menus.bracket.messages.show-all", "&eShowing all rounds."));
        }
    }

    private void handleRoundSelect(int round) {
        EsportsGameService service = manager != null ? manager.getGameService() : null;
        if (service == null) {
            player.sendMessage(EsportsLang.get("menus.bracket.messages.spectator-unavailable", "&cSpectator service is not ready."));
            return;
        }
        if (!service.hasActiveMatchContext()) {
            player.sendMessage(EsportsLang.get("menus.bracket.messages.no-live-match",
                    "&eThere is no live match right now. Showing round #{round} info.",
                    "round", round));
            return;
        }
        boolean moved = service.teleportToSpectator(player, true);
        if (moved) {
            EnumSound.CLICK.play(player, 1.0F, 1.15F);
            player.sendMessage(EsportsLang.get("menus.bracket.messages.moving-spectator", "&bMoving you to live spectator mode..."));
            player.closeInventory();
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(this.player) && event.getInventory().equals(getInventory())) {
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(this.player)) {
            HandlerList.unregisterAll(this);
        }
    }
}
