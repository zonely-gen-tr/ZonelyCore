package dev.zonely.whiteeffect.menus.reports;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.menu.PagedPlayerMenu;
import dev.zonely.whiteeffect.report.Report;
import dev.zonely.whiteeffect.report.ReportPunishAction;
import dev.zonely.whiteeffect.report.ReportService;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import dev.zonely.whiteeffect.utils.menu.ChatPrompt;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class MenuReportPunish extends PagedPlayerMenu {

    private final Core plugin;
    private final Player moderator;
    private final ReportService service;
    private final Report report;
    private final MenuReportDetail parent;
    private final Map<ItemStack, ReportPunishAction> mapping = new HashMap<>();
    private final Map<ReportPunishAction, String> actionKeys = new EnumMap<>(ReportPunishAction.class);

    public MenuReportPunish(Core plugin, Player moderator, ReportService service,
                            Report report, MenuReportDetail parent) {
        super(moderator, LanguageManager.get("menus.reports.punish.title", "&8Apply Punishment"), 3);
        this.plugin = plugin;
        this.moderator = moderator;
        this.service = service;
        this.report = report;
        this.parent = parent;

        this.onlySlots(java.util.Arrays.asList(10, 12, 14, 16, 22));
        populate();
        this.register(plugin);
        this.open();
    }

    private void populate() {
        mapping.clear();
        List<ItemStack> items = new ArrayList<>();
        items.add(createItem(ReportPunishAction.BAN, "menus.reports.punish.actions.ban",
                "IRON_AXE : 1 : name>&cBan : desc>&7Ban the player with a custom duration.", resolveMaterial("IRON_AXE", "IRON_AXE")));
        items.add(createItem(ReportPunishAction.MUTE, "menus.reports.punish.actions.mute",
                "BLAZE_POWDER : 1 : name>&6Mute : desc>&7Mute the player for chat offences.", resolveMaterial("BLAZE_POWDER", "BLAZE_POWDER")));
        items.add(createItem(ReportPunishAction.WARNING, "menus.reports.punish.actions.warning",
                "PAPER : 1 : name>&eWarning : desc>&7Issue a written warning.", resolveMaterial("PAPER", "PAPER")));
        items.add(createItem(ReportPunishAction.KICK, "menus.reports.punish.actions.kick",
                "FEATHER : 1 : name>&bKick : desc>&7Kick the player immediately.", resolveMaterial("FEATHER", "FEATHER")));
        items.add(createItem(ReportPunishAction.NONE, "menus.reports.punish.actions.none",
                "INK_SACK:8 : 1 : name>&7Mark Reviewed : desc>&7Mark this report as handled without punishment.",
                resolveMaterial("INK_SAC", "INK_SACK")));

        setItems(items);
    }

    private ItemStack createItem(ReportPunishAction action, String key, String def, Material fallback) {
        ItemStack item = parseItem(key, def, fallback, "target", report.getTarget());
        mapping.put(item, action);
        actionKeys.put(action, key);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(this.player)) return;
        if (!event.getInventory().equals(getCurrentInventory())) return;
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        ReportPunishAction action = mapping.get(item);
        if (action == null) return;

        EnumSound.CLICK.play(player, 0.5F, 1.3F);
        handleAction(action);
    }

    private void handleAction(ReportPunishAction action) {
        switch (action) {
            case BAN:
            case MUTE:
                promptDuration(action);
                break;
            case WARNING:
                promptReason(action, null);
                break;
            case KICK:
                confirmAndApply(action, null, LanguageManager.get("menus.reports.punish.default-reason.kick",
                        "Kicked by staff for report #{id}", "id", report.getId()));
                break;
            case NONE:
                confirmAndApply(action, null, report.getReason());
                break;
        }
    }

    private void promptDuration(ReportPunishAction action) {
        String defaultValue = plugin.getConfig("config").getString(
                "reports.punishment.defaults." + action.getId() + "-duration",
                action == ReportPunishAction.BAN ? "1d" : "30m");
        ChatPrompt.request(moderator,
                LanguageManager.get("menus.reports.punish.prompts.duration-title", "&7Enter duration"),
                LanguageManager.get("menus.reports.punish.prompts.duration-hint", "&7Default: {value}", "value", defaultValue),
                input -> promptReason(action, input.isEmpty() ? defaultValue : input),
                this::reopenParent);
    }

    private void promptReason(ReportPunishAction action, String duration) {
        String defaultReason = report.getReason();
        ChatPrompt.request(moderator,
                LanguageManager.get("menus.reports.punish.prompts.reason-title", "&7Enter reason"),
                LanguageManager.get("menus.reports.punish.prompts.reason-hint", "&7Default: {value}", "value", defaultReason),
                input -> confirmAndApply(action, duration, input.isEmpty() ? defaultReason : input),
                this::reopenParent);
    }

    private void confirmAndApply(ReportPunishAction action, String duration, String reason) {
        boolean success = service.executePunishment(report, moderator, action, duration, reason);
        if (success) {
            EnumSound.LEVEL_UP.play(moderator, 0.6F, 1.4F);
            moderator.sendMessage(LanguageManager.get("menus.reports.punish.success",
                    "&aPunishment applied successfully."));
            if (parent != null) {
                parent.refreshAfterDecision();
            } else {
                moderator.closeInventory();
            }
        } else {
            EnumSound.ENDERMAN_TELEPORT.play(moderator, 0.7F, 0.6F);
            moderator.sendMessage(LanguageManager.get("menus.reports.punish.failed",
                    "&cUnable to apply the selected punishment."));
            reopenParent();
        }
    }

    private void reopenParent() {
        if (parent != null) {
            parent.reopen();
        } else {
            moderator.closeInventory();
        }
    }

    public void cancel() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(this.player) && event.getInventory().equals(getCurrentInventory())) {
            cancel();
            reopenParent();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(this.player)) cancel();
    }

    private ItemStack parseItem(String key, String def, Material fallback, Object... placeholders) {
        String definition = LanguageManager.get(key, def, placeholders);
        if (definition == null || definition.trim().isEmpty() || definition.startsWith("MemorySection")) {
            definition = LanguageManager.get(key + ".item", def, placeholders);
        }
        if (definition == null || definition.trim().isEmpty() || definition.startsWith("MemorySection")) {
            definition = def;
        }
        try {
            return BukkitUtils.deserializeItemStack(definition);
        } catch (IllegalArgumentException ex) {
            Core.getInstance().getLogger().log(Level.WARNING,
                    "[ReportPunishMenu] Failed to parse item for {0}: {1}",
                    new Object[]{key, definition});
            return new ItemStack(fallback);
        }
    }

    private Material resolveMaterial(String modernName, String legacyName) {
        Material modern = Material.matchMaterial(modernName);
        if (modern != null) {
            return modern;
        }
        if (legacyName != null) {
            Material legacy = Material.matchMaterial(legacyName);
            if (legacy != null) {
                return legacy;
            }
        }
        return Material.PAPER;
    }
}






