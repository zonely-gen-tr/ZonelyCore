package dev.zonely.whiteeffect.menus.reports;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.menu.PagedPlayerMenu;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.report.ReportService;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MenuReportReason extends PagedPlayerMenu {

    private final Core plugin;
    private final ReportService service;
    private final Player reporter;
    private final String targetName;
    private final Profile profile;
    private final Map<ItemStack, ReasonDefinition> mapping = new HashMap<>();

    private static final List<Integer> DEFAULT_CONTENT_SLOTS = Arrays.asList(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    );
    private static final List<Integer> DEFAULT_FILLER_SLOTS = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8);
    private static final String DEFAULT_FILLER_ITEM = "STAINED_GLASS_PANE:7 : 1 : name>&r";
    private static final String DEFAULT_EMPTY_ITEM = "BARRIER : 1 : name>&cNo reasons configured";

    private static final Map<String, DefaultReason> DEFAULT_REASONS = new LinkedHashMap<>();

    static {
        DEFAULT_REASONS.put("cheating", new DefaultReason(
                "IRON_SWORD : 1 : name>&cCheating/Hacks : desc>&7Use if the player is suspected of hacking.",
                "Cheating / Hacks",
                null
        ));
        DEFAULT_REASONS.put("macro", new DefaultReason(
                "BOW : 1 : name>&6Macro/AutoClicker : desc>&7Excessive CPS or macro usage.",
                "Macro / AutoClicker",
                null
        ));
        DEFAULT_REASONS.put("bug-abuse", new DefaultReason(
                "REDSTONE_COMPARATOR : 1 : name>&eBug Abuse : desc>&7Taking advantage of glitches or exploits.",
                "Bug Abuse",
                null
        ));
        DEFAULT_REASONS.put("griefing", new DefaultReason(
                "FLINT_AND_STEEL : 1 : name>&dGriefing/Sabotage : desc>&7Breaking or sabotaging builds.",
                "Griefing / Sabotage",
                null
        ));
        DEFAULT_REASONS.put("scam", new DefaultReason(
                "EMERALD : 1 : name>&aScam/Fraud : desc>&7Scamming or stealing items.",
                "Scam / Fraud",
                null
        ));
        DEFAULT_REASONS.put("chat", new DefaultReason(
                "BOOK_AND_QUILL : 1 : name>&bChat Offence : desc>&7Insults, spam or profanity in chat.",
                "Chat Offence",
                null
        ));
    }

    public MenuReportReason(Core plugin, Player reporter, String targetName, ReportService service) {
        super(reporter,
                LanguageManager.get(Profile.getProfile(reporter.getName()), "menus.reports.create.title", "&8Report {target}", "target", targetName),
                Math.max(1, Math.min(6, LanguageManager.getInt(Profile.getProfile(reporter.getName()), "menus.reports.create.rows", 3))));
        this.plugin = plugin;
        this.service = service;
        this.reporter = reporter;
        this.targetName = targetName;
        this.profile = Profile.getProfile(reporter.getName());

        int maxSlots = this.rows * 9;
        List<Integer> contentSlots = sanitizeSlots(LanguageManager.getIntegerList(profile, "menus.reports.create.layout.content-slots", DEFAULT_CONTENT_SLOTS), maxSlots);
        if (contentSlots.isEmpty()) {
            contentSlots = DEFAULT_CONTENT_SLOTS;
        }
        this.onlySlots(contentSlots);

        List<Integer> fillerSlots = sanitizeSlots(LanguageManager.getIntegerList(profile, "menus.reports.create.layout.filler.slots", DEFAULT_FILLER_SLOTS), maxSlots);
        ItemStack filler = parseItem("menus.reports.create.items.filler", DEFAULT_FILLER_ITEM, Material.GLASS,
                "target", targetName);
        if (filler != null && !fillerSlots.isEmpty()) {
            this.removeSlotsWith(filler, toIntArray(fillerSlots));
            for (int slot : fillerSlots) {
                setItem(slot, filler.clone());
            }
        }

        populate();
        this.register(plugin);
        this.open();
    }

    private void populate() {
        mapping.clear();
        List<ItemStack> items = new ArrayList<>();
        for (ReasonDefinition definition : loadDefinitions()) {
            ItemStack button = definition.createButton();
            if (button == null || button.getType() == Material.AIR) continue;
            items.add(button);
            mapping.put(button, definition);
        }
        if (items.isEmpty()) {
            ItemStack empty = parseItem("menus.reports.create.items.empty", DEFAULT_EMPTY_ITEM, Material.BARRIER,
                    "target", targetName);
            if (empty != null) {
                items.add(empty);
            }
        }
        setItems(items);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(this.player)) return;
        if (!event.getInventory().equals(getCurrentInventory())) return;
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        ReasonDefinition selected = null;
        for (Map.Entry<ItemStack, ReasonDefinition> entry : mapping.entrySet()) {
            if (entry.getKey().isSimilar(item)) {
                selected = entry.getValue();
                break;
            }
        }
        if (selected == null) {
            return;
        }

        EnumSound.CLICK.play(player, 0.5F, 1.4F);
        new MenuReportConfirm(plugin, reporter, targetName, service, selected);
    }

    public void cancel() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(this.player) && event.getInventory().equals(getCurrentInventory())) {
            cancel();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(this.player)) {
            cancel();
        }
    }

    private List<ReasonDefinition> loadDefinitions() {
        List<ReasonDefinition> result = new ArrayList<>();
        ConfigurationSection section = LanguageManager.getSection(profile, "menus.reports.create.reasons");
        Set<String> keys = new LinkedHashSet<>();
        if (section != null) {
            keys.addAll(section.getKeys(false));
        }
        keys.addAll(DEFAULT_REASONS.keySet());

        for (String key : keys) {
            DefaultReason defaults = DEFAULT_REASONS.get(key);
            String basePath = "menus.reports.create.reasons." + key;
            String permission = LanguageManager.get(profile, basePath + ".permission", defaults != null ? defaults.permission : null);
            if (permission != null && !permission.isEmpty() && !reporter.hasPermission(permission)) {
                continue;
            }
            String reasonText = LanguageManager.get(profile, basePath + ".reason",
                    defaults != null ? defaults.reason : formatReasonKey(key));
            String itemDefinition = LanguageManager.get(profile, basePath + ".item",
                    defaults != null ? defaults.item : null,
                    "target", targetName,
                    "reason", reasonText);
            ItemStack button = parseDefinitionItem(key, itemDefinition);
            if (button == null || button.getType() == Material.AIR) {
                continue;
            }
            result.add(new ReasonDefinition(key, reasonText, permission, button));
        }
        return result;
    }

    private ItemStack parseDefinitionItem(String key, String definition) {
        if (definition == null || definition.trim().isEmpty()) {
            Core.getInstance().getLogger().warning("[ReportGUI] Missing item definition for reason '" + key + "'");
            return null;
        }
        try {
            return BukkitUtils.deserializeItemStack(definition);
        } catch (IllegalArgumentException ex) {
            Core.getInstance().getLogger().warning("[ReportGUI] Invalid item definition for reason '" + key + "': " + definition);
            return null;
        }
    }

    private ItemStack parseItem(String key, String def, Material fallback, Object... placeholders) {
        String definition = LanguageManager.get(profile, key, def, placeholders);
        if (definition == null || definition.trim().isEmpty()) {
            return null;
        }
        try {
            return BukkitUtils.deserializeItemStack(definition);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[ReportGUI] Failed to parse item for " + key + ": " + definition);
            return new ItemStack(fallback);
        }
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

    private int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static String formatReasonKey(String key) {
        return key.replace('-', ' ').replace('_', ' ');
    }

    public static final class ReasonDefinition {
        private final String key;
        private final String reasonText;
        private final String permission;
        private final ItemStack button;

        private ReasonDefinition(String key, String reasonText, String permission, ItemStack button) {
            this.key = key;
            this.reasonText = reasonText;
            this.permission = permission;
            this.button = button;
        }

        public String getKey() {
            return key;
        }

        public String getReason() {
            return reasonText;
        }

        public String getPermission() {
            return permission;
        }

        private ItemStack createButton() {
            return button == null ? null : button.clone();
        }
    }

    private static final class DefaultReason {
        private final String item;
        private final String reason;
        private final String permission;

        private DefaultReason(String item, String reason, String permission) {
            this.item = item;
            this.reason = reason;
            this.permission = permission;
        }
    }
}
