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

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

public final class EsportsTournamentWizardMenu extends PlayerMenu {

    private static final int NAME_SLOT = 10;
    private static final int EVENT_SLOT = 12;
    private static final int START_SLOT = 14;
    private static final int SPECTATOR_SLOT = 16;
    private static final int SPAWN_SLOT = 28;
    private static final int KIT_SLOT = 30;
    private static final int POTION_SLOT = 32;
    private static final int REWARD_SLOT = 34;
    private static final int REPEAT_SLOT = 42;
    private static final int INFO_SLOT = 40;
    private static final int SAVE_SLOT = 49;

    private final Core plugin;
    private final EsportsCreationService service;
    private final CreationSession session;

    public EsportsTournamentWizardMenu(Core plugin,
                                       Player player,
                                       EsportsCreationService service,
                                       CreationSession session) {
        super(player, EsportsLang.get("menus.wizard.title", "&8Tournament Setup"), 6);
        this.plugin = plugin;
        this.service = service;
        this.session = session;
        render();
        this.register(plugin);
        this.open();
    }

    private void render() {
        setItem(INFO_SLOT, BukkitUtils.deserializeItemStack(
                "BOOK : 1 : name>" + EsportsLang.get("menus.wizard.info.name", "&bTournament Setup")
                        + " : desc>" + EsportsLang.get("menus.wizard.info.lore.1", "&7Complete settings and save the tournament.")
                        + "\\n" + EsportsLang.get("menus.wizard.info.status", "&8 - &7Status: {status}",
                        "status", session.isReady()
                                ? EsportsLang.get("menus.wizard.info.status-ready", "&aReady")
                                : EsportsLang.get("menus.wizard.info.status-missing", "&cMissing fields"))
                        + "\\n" + EsportsLang.get("menus.wizard.info.cancel", "&8 - &7Cancel: &c/esports admin cancel")));

        setItem(NAME_SLOT, BukkitUtils.deserializeItemStack(
                mat("NAME_TAG", "NAME_TAG") + " : 1 : name>"
                        + EsportsLang.get("menus.wizard.name.name", "&eTournament Name")
                        + " : desc>" + EsportsLang.get("menus.wizard.name.current", "&8 - &7Current: &f{value}",
                        "value", colorized(session.getDisplayName()))
                        + "\\n\\n" + EsportsLang.get("menus.wizard.name.click", "&6&l> &eClick to enter a name.")));

        setItem(EVENT_SLOT, BukkitUtils.deserializeItemStack(
                mat("DIAMOND_SWORD", "IRON_SWORD") + " : 1 : name>"
                        + EsportsLang.get("menus.wizard.event.name", "&dEvent & Rounds")
                        + " : desc>" + EsportsLang.get("menus.wizard.event.type", "&8 - &7Type: &f{value}",
                        "value", safe(session.getEventType()))
                        + "\\n" + EsportsLang.get("menus.wizard.event.rounds",
                        "&8 - &7Rounds: &f{rounds} &7(&f{clubs} clubs&7)",
                        "rounds", session.getRoundCount(),
                        "clubs", session.getBracketParticipantSlots())
                        + "\\n" + EsportsLang.get("menus.wizard.event.per-match", "&8 - &7Per match: &f{count} clubs",
                        "count", session.getMatchClubCount())
                        + "\\n\\n" + EsportsLang.get("menus.wizard.event.click", "&6&l> &eClick to change.")));

        setItem(START_SLOT, BukkitUtils.deserializeItemStack(
                mat("CLOCK", "WATCH") + " : 1 : name>"
                        + EsportsLang.get("menus.wizard.start.name", "&6Start Date")
                        + " : desc>" + EsportsLang.get("menus.wizard.start.planned", "&8 - &7Planned: &f{value}",
                        "value", formatDate(session))
                        + "\\n\\n" + EsportsLang.get("menus.wizard.start.click", "&6&l> &eSet date and time.")));

        String spectatorStatus = session.hasSpectatorSpawn()
                ? "&a" + formatSpectator(session)
                : EsportsLang.get("menus.wizard.spectator.not-set", "&cNot set");
        setItem(SPECTATOR_SLOT, BukkitUtils.deserializeItemStack(
                mat("ENDER_EYE", "ENDER_EYE") + " : 1 : name>"
                        + EsportsLang.get("menus.wizard.spectator.name", "&bWaiting Lobby")
                        + " : desc>" + EsportsLang.get("menus.wizard.spectator.status", "&8 - &7Status: &f{value}",
                        "value", spectatorStatus)
                        + "\\n\\n" + EsportsLang.get("menus.wizard.spectator.left", "&7Left click: &fSave your location.")
                        + "\\n" + EsportsLang.get("menus.wizard.spectator.right", "&7Right click: &cClear.")));

        setItem(SPAWN_SLOT, BukkitUtils.deserializeItemStack(
                mat("COMPASS", "COMPASS") + " : 1 : name>"
                        + EsportsLang.get("menus.wizard.spawn.name", "&bSpawn Points")
                        + " : desc>" + EsportsLang.get("menus.wizard.spawn.ready",
                        "&8 - &7Ready: &f{defined}&7 / &f{required}",
                        "defined", session.getDefinedSpawnSlotCount(),
                        "required", session.getRequiredSpawnSlots())
                        + "\\n" + EsportsLang.get("menus.wizard.spawn.per-match", "&8 - &7Per match: &f{count} clubs",
                        "count", session.getMatchClubCount())
                        + "\\n\\n" + EsportsLang.get("menus.wizard.spawn.left", "&7Left click: &fAdd your location.")
                        + "\\n" + EsportsLang.get("menus.wizard.spawn.right", "&7Right click: &cClear.")));

        setItem(KIT_SLOT, BukkitUtils.deserializeItemStack(
                mat("CHEST", "CHEST") + " : 1 : name>"
                        + EsportsLang.get("menus.wizard.kit.name", "&aKit Template")
                        + " : desc>" + EsportsLang.get("menus.wizard.kit.status", "&8 - &7Status: &f{status}",
                        "status", session.hasKit()
                                ? EsportsLang.get("menus.wizard.kit.status-ready", "&aReady")
                                : EsportsLang.get("menus.wizard.kit.status-missing", "&cMissing"))
                        + "\\n\\n" + EsportsLang.get("menus.wizard.kit.click", "&6&l> &eOpen kit editor.")));

        setItem(POTION_SLOT, BukkitUtils.deserializeItemStack(
                mat("POTION", "POTION") + " : 1 : name>"
                        + EsportsLang.get("menus.wizard.potion.name", "&9Potion Effects")
                        + " : desc>" + EsportsLang.get("menus.wizard.potion.active", "&8 - &7Active: &f{count}",
                        "count", session.getPotionCount())
                        + "\\n\\n" + EsportsLang.get("menus.wizard.potion.click", "&6&l> &eEdit effects.")));

        String rewardSummary = session.describeRewardSummary()
                .replace("\n", "\\n&8 - ");
        setItem(REWARD_SLOT, BukkitUtils.deserializeItemStack(
                mat("EMERALD", "EMERALD") + " : 1 : name>"
                        + EsportsLang.get("menus.wizard.reward.name", "&6Rewards")
                        + " : desc>&8 - " + rewardSummary
                        + "\\n\\n" + EsportsLang.get("menus.wizard.reward.click", "&6&l> &eLeft click: edit.")));

        setItem(REPEAT_SLOT, BukkitUtils.deserializeItemStack(
                mat("REPEATER", "DIODE") + " : 1 : name>"
                        + EsportsLang.get("menus.wizard.repeat.name", "&eRepeatable")
                        + " : desc>" + EsportsLang.get("menus.wizard.repeat.mode", "&8 - &7Mode: &f{mode}",
                        "mode", session.getRepeatLabel())
                        + "\\n" + EsportsLang.get("menus.wizard.repeat.interval", "&8 - &7Interval: &f{interval}",
                        "interval", session.isRepeatable()
                                ? EsportsLang.get("menus.wizard.repeat.interval-days", "{days} day(s)",
                                "days", session.getRepeatIntervalDays())
                                : EsportsLang.get("menus.wizard.repeat.not-repeating", "Not repeating"))
                        + "\\n\\n" + EsportsLang.get("menus.wizard.repeat.click", "&6&l> &eClick to cycle mode.")));

        boolean ready = session.isReady();
        String saveName = ready
                ? EsportsLang.get("menus.wizard.save.ready-name", "&a&lSave")
                : EsportsLang.get("menus.wizard.save.missing-name", "&c&lMissing Fields");
        String saveDesc = ready
                ? EsportsLang.get("menus.wizard.save.ready-desc", "&8 - &7All fields are ready.")
                + "\\n\\n" + EsportsLang.get("menus.wizard.save.ready-click", "&6&l> &aCreate tournament.")
                : EsportsLang.get("menus.wizard.save.missing-desc", "&8 - &7Missing: &f{fields}",
                "fields", String.join(", ", session.describeMissingFields()))
                + "\\n\\n" + EsportsLang.get("menus.wizard.save.missing-click", "&6&l> &eComplete before saving.");
        setItem(SAVE_SLOT, BukkitUtils.deserializeItemStack(
                mat("EMERALD_BLOCK", "EMERALD_BLOCK") + " : 1 : name>" + saveName
                        + " : desc>" + saveDesc));

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

    private String colorized(String value) {
        if (value == null || value.trim().isEmpty()) {
            return EsportsLang.get("menus.wizard.common.not-set", "&cNot set");
        }
        return value.replace(org.bukkit.ChatColor.COLOR_CHAR, '&');
    }

    private String safe(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private void fillGaps() {
        String filler = "STAINED_GLASS_PANE:7 : 1 : name>&r";
        for (int i = 0; i < getInventory().getSize(); i++) {
            if (getInventory().getItem(i) == null) {
                setItem(i, BukkitUtils.deserializeItemStack(filler));
            }
        }
    }

    private String formatDate(CreationSession session) {
        if (session.getStartDate() == null) {
            return EsportsLang.get("menus.wizard.common.not-set", "&cNot set");
        }
        return session.getStartDate().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.ENGLISH));
    }

    private String formatSpectator(CreationSession session) {
        org.bukkit.Location location = session.getSpectatorSpawn();
        if (location == null || location.getWorld() == null) {
            return EsportsLang.get("menus.wizard.common.not-set-plain", "Not set");
        }
        return location.getWorld().getName() + " "
                + (int) location.getX() + ", "
                + (int) location.getY() + ", "
                + (int) location.getZ();
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
        EnumSound.CLICK.play(this.player, 1.0F, 1.6F);
        if (slot == NAME_SLOT) {
            player.closeInventory();
            service.requestNameInput(session);
            return;
        }
        if (slot == EVENT_SLOT) {
            service.openEventMenu(player, session);
            return;
        }
        if (slot == START_SLOT) {
            service.openStartMenu(player, session);
            return;
        }
        if (slot == SPECTATOR_SLOT) {
            player.closeInventory();
            if (event.isRightClick()) {
                service.clearSpectatorLobby(player, session);
            } else {
                service.setSpectatorLobby(player, session);
            }
            return;
        }
        if (slot == SPAWN_SLOT) {
            service.openSpawnMenu(player, session);
            return;
        }
        if (slot == KIT_SLOT) {
            player.closeInventory();
            service.openKitEditor(player, session);
            return;
        }
        if (slot == POTION_SLOT) {
            service.openPotionMenu(player, session);
            return;
        }
        if (slot == REWARD_SLOT) {
            player.closeInventory();
            service.openRewardMenu(player, session);
            return;
        }
        if (slot == REPEAT_SLOT) {
            session.cycleRepeatMode();
            player.sendMessage(EsportsLang.get("wizard.repeat-mode",
                    "&aRepeat mode: &6{mode}",
                    "mode", session.getRepeatLabel()));
            render();
            return;
        }
        if (slot == SAVE_SLOT) {
            player.closeInventory();
            service.finalizeSession(session);
        }
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
