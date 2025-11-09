package dev.zonely.whiteeffect.menus.reports;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.menu.Menu;
import dev.zonely.whiteeffect.libraries.menu.PagedPlayerMenu;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.report.ReportService;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class MenuReportConfirm extends PagedPlayerMenu {

    private final Core plugin;
    private final ReportService service;
    private final Player reporter;
    private final String targetName;
    private final MenuReportReason.ReasonDefinition reasonDefinition;
    private final Profile profile;
    private final int confirmSlot;
    private final int cancelSlot;
    private final int infoSlot;

    private static final List<Integer> DEFAULT_FILLER_SLOTS = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8);
    private static final String DEFAULT_FILLER_ITEM = "STAINED_GLASS_PANE:7 : 1 : name>&r";
    private static final String DEFAULT_INFO_ITEM = "SKULL_ITEM:3 : 1 : name>&c{target} : desc>&7Reason: &f{reason}\\n&7Reporter: &f{reporter}";
    private static final String DEFAULT_CONFIRM_ITEM = "INK_SACK:10 : 1 : name>&aSubmit Report : desc>&7Click to file the report.";
    private static final String DEFAULT_CANCEL_ITEM = "INK_SACK:1 : 1 : name>&cCancel : desc>&7Click to abort.";

    public MenuReportConfirm(Core plugin, Player reporter, String targetName,
                             ReportService service, MenuReportReason.ReasonDefinition definition) {
        super(reporter,
                LanguageManager.get(Profile.getProfile(reporter.getName()), "menus.reports.create.confirm.title", "&8Confirm Report"),
                Math.max(1, Math.min(6, LanguageManager.getInt(Profile.getProfile(reporter.getName()), "menus.reports.create.confirm.rows", 3))));
        this.plugin = plugin;
        this.service = service;
        this.reporter = reporter;
        this.targetName = targetName;
        this.reasonDefinition = definition;
        this.profile = Profile.getProfile(reporter.getName());

        int maxSlots = this.rows * 9;
        this.confirmSlot = clampSlot(LanguageManager.getInt(profile, "menus.reports.create.confirm.layout.controls.confirm-slot", 14), maxSlots);
        this.cancelSlot = clampSlot(LanguageManager.getInt(profile, "menus.reports.create.confirm.layout.controls.cancel-slot", 12), maxSlots);
        this.infoSlot = clampSlot(LanguageManager.getInt(profile, "menus.reports.create.confirm.layout.controls.info-slot", 13), maxSlots);

        populate();
        this.register(plugin);
        this.open();
    }

    private void populate() {
        Menu menu = new Menu(LanguageManager.get(profile,
                "menus.reports.create.confirm.title", "&8Confirm Report"), this.rows);

        int maxSlots = this.rows * 9;
        List<Integer> fillerSlots = sanitizeSlots(LanguageManager.getIntegerList(profile,
                "menus.reports.create.confirm.layout.filler.slots", DEFAULT_FILLER_SLOTS), maxSlots);
        ItemStack filler = parseItem("menus.reports.create.confirm.items.filler", DEFAULT_FILLER_ITEM, Material.GLASS);
        if (filler != null && !fillerSlots.isEmpty()) {
            for (int slot : fillerSlots) {
                menu.setItem(slot, filler.clone());
            }
        }

        Material infoFallback = fallbackMaterial("SKULL_ITEM", "PLAYER_HEAD", Material.PAPER);
        ItemStack info = parseItem("menus.reports.create.confirm.items.info", DEFAULT_INFO_ITEM, infoFallback,
                "target", targetName,
                "reason", reasonDefinition.getReason(),
                "reporter", reporter.getName());
        if (info != null && infoSlot >= 0) {
            menu.setItem(infoSlot, info);
        }

        Material confirmFallback = fallbackMaterial("INK_SACK", "GREEN_DYE", Material.BOOK);
        ItemStack confirm = parseItem("menus.reports.create.confirm.items.confirm", DEFAULT_CONFIRM_ITEM, confirmFallback);
        if (confirm != null && confirmSlot >= 0) {
            menu.setItem(confirmSlot, confirm);
        }

        Material cancelFallback = fallbackMaterial("INK_SACK", "RED_DYE", Material.BARRIER);
        ItemStack cancel = parseItem("menus.reports.create.confirm.items.cancel", DEFAULT_CANCEL_ITEM, cancelFallback);
        if (cancel != null && cancelSlot >= 0) {
            menu.setItem(cancelSlot, cancel);
        }

        this.menus.clear();
        this.menus.add(menu);
        this.currentPage = 1;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getInventory().equals(getCurrentInventory())) return;
        if (!e.getWhoClicked().equals(this.player)) return;
        e.setCancelled(true);

        int slot = e.getSlot();
        if (slot == cancelSlot) {
            EnumSound.CLICK.play(player, 0.5F, 0.9F);
            player.closeInventory();
            return;
        }
        if (slot == confirmSlot) {
            EnumSound.CLICK.play(player, 0.6F, 1.3F);
            handleSubmit();
        }
    }

    private void handleSubmit() {
        String reasonText = reasonDefinition.getReason();
        String contextServer;
        try {
            contextServer = plugin.getServer().getServerName();
        } catch (NoSuchMethodError ignored) {
            contextServer = plugin.getServer().getName();
        }

        reporter.closeInventory();
        reporter.sendMessage(LanguageManager.get(profile,
                "commands.reports.processing",
                "{prefix}&7Submitting report, please wait...",
                "prefix", LanguageManager.get(profile, "prefix.punish", "&4Punish &8->> ")));

        service.createReportAsync(reporter.getName(), targetName, reasonDefinition.getKey(), reasonText,
                contextServer, null, id -> {
                    if (!reporter.isOnline()) {
                        return;
                    }
                    if (id > 0) {
                        reporter.sendMessage(LanguageManager.get(profile,
                                "commands.reports.created",
                                "{prefix}&aReport submitted. Ticket ID: &e#{id}",
                                "prefix", LanguageManager.get(profile, "prefix.punish", "&4Punish &8->> "),
                                "id", id));
                        EnumSound.LEVEL_UP.play(reporter, 0.8F, 1.6F);
                    } else {
                        reporter.sendMessage(LanguageManager.get(profile,
                                "commands.reports.error",
                                "{prefix}&cYour report could not be saved. Please try again later.",
                                "prefix", LanguageManager.get(profile, "prefix.punish", "&4Punish &8->> ")));
                        EnumSound.ENDERMAN_TELEPORT.play(reporter, 0.6F, 0.5F);
                    }
                });
    }

    public void cancel() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer().equals(this.player) && e.getInventory().equals(getCurrentInventory())) {
            cancel();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (e.getPlayer().equals(this.player)) cancel();
    }

    private ItemStack parseItem(String key, String def, Material fallback, Object... placeholders) {
        String definition = LanguageManager.get(profile, key, def, placeholders);
        if (definition == null || definition.trim().isEmpty()) {
            return null;
        }
        try {
            return BukkitUtils.deserializeItemStack(definition);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[ReportConfirm] Failed to parse item for " + key + ": " + definition);
            return new ItemStack(fallback);
        }
    }

    private int clampSlot(int slot, int limit) {
        if (slot < 0) {
            return -1;
        }
        return slot >= limit ? -1 : slot;
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

    private Material fallbackMaterial(String legacyToken, String modernToken, Material defaultMaterial) {
        Material material = MaterialResolver.resolveMaterial(legacyToken);
        if (material == null && modernToken != null) {
            material = MaterialResolver.resolveMaterial(modernToken);
        }
        return material != null ? material : defaultMaterial;
    }
}

