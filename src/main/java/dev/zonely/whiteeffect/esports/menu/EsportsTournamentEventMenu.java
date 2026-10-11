package dev.zonely.whiteeffect.esports.menu;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.esports.EsportsLang;
import dev.zonely.whiteeffect.esports.menu.EsportsCreationService.CreationSession;
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
import org.bukkit.event.player.PlayerQuitEvent;

public final class EsportsTournamentEventMenu extends PlayerMenu {

    private static final int SLOT_PVP = 11;
    private static final int SLOT_BLOCK_BREAK = 13;
    private static final int SLOT_BLOCK_PLACE = 15;
    private static final int SLOT_ROUND_DOWN = 29;
    private static final int SLOT_ROUND_INFO = 31;
    private static final int SLOT_ROUND_UP = 33;
    private static final int SLOT_CLUB_DOWN = 38;
    private static final int SLOT_CLUB_INFO = 40;
    private static final int SLOT_CLUB_UP = 42;
    private static final int SLOT_BACK = 49;

    private final Core plugin;
    private final EsportsCreationService service;
    private final CreationSession session;

    public EsportsTournamentEventMenu(Core plugin,
                                      Player player,
                                      EsportsCreationService service,
                                      CreationSession session) {
        super(player, EsportsLang.get("menus.event.title", "&3Event & Rounds"), 6);
        this.plugin = plugin;
        this.service = service;
        this.session = session;
        render();
        this.register(plugin);
        this.open();
    }

    private void render() {

        setItem(SLOT_PVP, buildEventButton("pvp",
                mat("DIAMOND_SWORD", "IRON_SWORD"),
                EsportsLang.get("menus.event.types.pvp.name", "&fPvP"),
                EsportsLang.get("menus.event.types.pvp.lore", "&7Classic combat-focused tournaments.")));

        setItem(SLOT_BLOCK_BREAK, buildEventButton("block-break",
                mat("IRON_PICKAXE", "IRON_PICKAXE"),
                EsportsLang.get("menus.event.types.block-break.name", "&fBlock Break"),
                EsportsLang.get("menus.event.types.block-break.lore", "&7Mining or parkour-focused events.")));

        setItem(SLOT_BLOCK_PLACE, buildEventButton("block-place",
                mat("STONE_BRICKS", "STONE"),
                EsportsLang.get("menus.event.types.block-place.name", "&fBlock Place"),
                EsportsLang.get("menus.event.types.block-place.lore", "&7Build or defense-focused events.")));

        setItem(SLOT_ROUND_DOWN, BukkitUtils.deserializeItemStack(
                mat("RED_DYE", "INK_SACK:1") + " : 1 : name>"
                        + EsportsLang.get("menus.event.round.down.name", "&cRounds -1")
                        + " : desc>" + EsportsLang.get("menus.event.round.down.lore", "&7Decrease total rounds by 1.")));

        setItem(SLOT_ROUND_INFO, BukkitUtils.deserializeItemStack(
                mat("PAPER", "PAPER") + " : 1 : name>"
                        + EsportsLang.get("menus.event.round.info.name", "&eRound Info")
                        + " : desc>" + EsportsLang.get("menus.event.round.info.total", "&8 - &7Total rounds: &f{count}",
                        "count", session.getRoundCount())
                        + "\\n" + EsportsLang.get("menus.event.round.info.bracket", "&8 - &7First bracket slots: &f{count} clubs.",
                        "count", session.getBracketParticipantSlots())));

        setItem(SLOT_ROUND_UP, BukkitUtils.deserializeItemStack(
                mat("LIME_DYE", "INK_SACK:10") + " : 1 : name>"
                        + EsportsLang.get("menus.event.round.up.name", "&aRounds +1")
                        + " : desc>" + EsportsLang.get("menus.event.round.up.lore", "&7Increase total rounds by 1.")));

        setItem(SLOT_CLUB_DOWN, BukkitUtils.deserializeItemStack(
                mat("REDSTONE", "REDSTONE") + " : 1 : name>"
                        + EsportsLang.get("menus.event.club.down.name", "&cClubs -1")
                        + " : desc>" + EsportsLang.get("menus.event.club.down.lore", "&7Decrease clubs per match by 1.")));

        setItem(SLOT_CLUB_INFO, BukkitUtils.deserializeItemStack(
                mat("MAP", "MAP") + " : 1 : name>"
                        + EsportsLang.get("menus.event.club.info.name", "&eClubs Per Match")
                        + " : desc>" + EsportsLang.get("menus.event.club.info.count", "&8 - &7Clubs in each match: &f{count}",
                        "count", session.getMatchClubCount())
                        + "\\n" + EsportsLang.get("menus.event.club.info.scale", "&8 - &7Spawn slots scale with this.")));

        setItem(SLOT_CLUB_UP, BukkitUtils.deserializeItemStack(
                mat("ENDER_EYE", "ENDER_EYE") + " : 1 : name>"
                        + EsportsLang.get("menus.event.club.up.name", "&aClubs +1")
                        + " : desc>" + EsportsLang.get("menus.event.club.up.lore", "&7Increase clubs per match by 1.")));

        setItem(SLOT_BACK, BukkitUtils.deserializeItemStack(
                mat("ARROW", "ARROW") + " : 1 : name>"
                        + EsportsLang.get("menus.event.back.name", "&7Back")
                        + " : desc>" + EsportsLang.get("menus.event.back.lore", "&7Return to main setup.")));

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

    private org.bukkit.inventory.ItemStack buildEventButton(String key, String material, String title, String lore) {
        boolean selected = session.getEventType().equalsIgnoreCase(key);
        String colour = selected ? "&a" : "&7";
        return BukkitUtils.deserializeItemStack(material + " : 1 : name>" + colour + title
                + " : desc>" + EsportsLang.get("menus.event.status", "&7Status: {status}",
                "status", selected
                        ? EsportsLang.get("menus.event.status-selected", "&aSelected")
                        : EsportsLang.get("menus.event.status-inactive", "&cInactive"))
                + "\\n" + lore);
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
        if (slot == SLOT_PVP) {
            session.setEventType("pvp");
            render();
            return;
        }
        if (slot == SLOT_BLOCK_BREAK) {
            session.setEventType("block-break");
            render();
            return;
        }
        if (slot == SLOT_BLOCK_PLACE) {
            session.setEventType("block-place");
            render();
            return;
        }
        if (slot == SLOT_ROUND_DOWN) {
            if (session.adjustRounds(-1)) {
                render();
                player.sendMessage(EsportsLang.get("event.round-count",
                        "&eRound count set to {count}.",
                        "count", session.getRoundCount()));
            }
            return;
        }
        if (slot == SLOT_ROUND_UP) {
            if (session.adjustRounds(1)) {
                render();
                player.sendMessage(EsportsLang.get("event.round-count",
                        "&eRound count set to {count}.",
                        "count", session.getRoundCount()));
            }
            return;
        }
        if (slot == SLOT_CLUB_DOWN) {
            if (session.adjustMatchClubCount(-1)) {
                render();
                player.sendMessage(EsportsLang.get("event.clubs-per-match",
                        "&eClubs per match set to {count}.",
                        "count", session.getMatchClubCount()));
            }
            return;
        }
        if (slot == SLOT_CLUB_UP) {
            if (session.adjustMatchClubCount(1)) {
                render();
                player.sendMessage(EsportsLang.get("event.clubs-per-match",
                        "&eClubs per match set to {count}.",
                        "count", session.getMatchClubCount()));
            }
            return;
        }
        if (slot == SLOT_BACK) {
            service.openWizard(player, session);
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
}
