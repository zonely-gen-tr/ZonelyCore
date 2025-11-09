package dev.zonely.whiteeffect.menus.profile;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.menus.MenuProfile;
import dev.zonely.whiteeffect.mysterybox.api.MysteryBoxAPI;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.StringUtils;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MenuColor extends PlayerMenu {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("d MMMM yyyy HH:mm", Locale.ENGLISH);

    private static final String TITLE_PATH = "menus.profile.color.title";
    private static final String FILLER_PATH = "menus.profile.color.filler";
    private static final String BACK_PATH = "menus.profile.color.back";
    private static final String PROFILE_SPEC_PATH = "menus.profile.color.profile-item.spec";
    private static final String PREVIEW_SAMPLE_PATH = "menus.profile.color.preview-sample";
    private static final String LORE_HEADER_PATH = "menus.profile.color.lore.header";
    private static final String PREVIEW_LINE_PATH = "menus.profile.color.lore.preview";
    private static final String STATUS_PATH = "menus.profile.color.status.";
    private static final String OPTIONS_ROOT = "menus.profile.color.options.";

    private static final String DEFAULT_TITLE = "&8Profile &0&l> &8Chat Colors";
    private static final String DEFAULT_FILLER_SPEC = "STAINED_GLASS_PANE:7 : 1";
    private static final String DEFAULT_BACK_SPEC = "ARROW : 1 : name>&e<--";
    private static final String DEFAULT_PROFILE_SPEC =
            "SKULL_ITEM:3 : 1 : name>&e&l{player} : desc>\n"
                    + "&8 -> &fRank: &b{role}\n"
                    + "&8 -> &fCredits: &6{credits}\n"
                    + "&7 &8-> &fFragments: &d{fragments}\n"
                    + "&7 &8-> &fMystery Boxes: &b{mystery_boxes}\n"
                    + " \n"
                    + "&8 -> &fFirst Login: &f{first_login}\n"
                    + "&8 -> &fLast Login: &f{last_login}";
    private static final String DEFAULT_PREVIEW_SAMPLE = "&fSample Message";
    private static final String DEFAULT_PREVIEW_LINE = "&7Preview: {preview}";
    private static final List<String> DEFAULT_LORE_HEADER = Arrays.asList(
            "&8[Chat Color]",
            "&7Apply this style to your chat messages.");
    private static final Map<OptionState, List<String>> DEFAULT_STATUS_LINES = new EnumMap<>(OptionState.class);

    private static final Map<Integer, ColorOption> OPTION_BY_SLOT = buildOptionIndex();

    static {
        DEFAULT_STATUS_LINES.put(
                OptionState.SELECTED,
                Arrays.asList("", "&cClick to clear your chat color."));
        DEFAULT_STATUS_LINES.put(
                OptionState.AVAILABLE,
                Arrays.asList("", "&eClick to select this chat color."));
        DEFAULT_STATUS_LINES.put(
                OptionState.LOCKED,
                Arrays.asList("", "&cUnlock via Mystery Boxes or the required VIP rank."));
    }

    private final Profile profile;
    private final String activePattern;

    public MenuColor(Profile profile) {
        super(profile.getPlayer(), LanguageManager.get(profile, TITLE_PATH, DEFAULT_TITLE), 6);
        this.profile = profile;
        this.activePattern = PlaceholderAPI.setPlaceholders(this.player, "%chatcolor_pattern_name%");
        fillBackground();
        placeProfileItem();
        placeBackButton();
        renderOptions();
        this.register(Core.getInstance());
        this.open();
    }

    private void fillBackground() {
        ItemStack filler = deserialize(LanguageManager.get(profile, FILLER_PATH, DEFAULT_FILLER_SPEC));
        for (int slot = 0; slot < this.getInventory().getSize(); slot++) {
            this.setItem(slot, filler.clone());
        }
    }

    private void placeProfileItem() {
        Role role = Role.getRoleByName(profile.getDataContainer("ZonelyCoreProfile", "role").getAsString());
        String roleName = role != null ? role.getName() : "&7Player";
        long created = profile.getDataContainer("ZonelyCoreProfile", "created").getAsLong();
        long lastLogin = profile.getDataContainer("ZonelyCoreProfile", "lastlogin").getAsLong();
        String spec = LanguageManager.get(
                profile,
                PROFILE_SPEC_PATH,
                DEFAULT_PROFILE_SPEC,
                "player", profile.getName(),
                "role", roleName,
                "credits", StringUtils.formatNumber(CashManager.getCash(profile)),
                "fragments", profile.getFormatedStats("ZonelyMysteryBox", "mystery_frags"),
                "mystery_boxes", StringUtils.formatNumber(MysteryBoxAPI.getMysteryBoxes(profile)),
                "first_login", DATE_FORMAT.format(created),
                "last_login", DATE_FORMAT.format(lastLogin));
        ItemStack skull = BukkitUtils.putProfileOnSkull(this.player, deserialize(spec));
        this.setItem(4, skull);
    }

    private void placeBackButton() {
        ItemStack back = deserialize(LanguageManager.get(profile, BACK_PATH, DEFAULT_BACK_SPEC));
        this.setItem(49, back);
    }

    private void renderOptions() {
        for (ColorOption option : ColorOption.values()) {
            OptionState state = determineState(option);
            ItemStack item = createOptionItem(option, state);
            this.setItem(option.slot, item);
        }
    }

    private OptionState determineState(ColorOption option) {
        String current = activePattern == null ? "" : activePattern.toLowerCase(Locale.ROOT);
        if (option.matches(current)) {
            return OptionState.SELECTED;
        }
        Player player = this.player;
        if (player != null && player.hasPermission(option.permission)) {
            return OptionState.AVAILABLE;
        }
        return OptionState.LOCKED;
    }

    private ItemStack createOptionItem(ColorOption option, OptionState state) {
        String iconSpec = LanguageManager.get(profile, option.path("icon"), option.defaultIcon);
        ItemStack item = deserialize(iconSpec);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String displayName = StringUtils.formatColors(
                PlaceholderAPI.setPlaceholders(
                        this.player,
                        LanguageManager.get(profile, option.path("name"), option.defaultName)));
        meta.setDisplayName(displayName);

        String previewColor = LanguageManager.get(profile, option.path("preview-color"), option.defaultPreview);
        String previewSample = LanguageManager.get(profile, PREVIEW_SAMPLE_PATH, DEFAULT_PREVIEW_SAMPLE);
        String previewValue = previewColor + previewSample;

        List<String> lore = new ArrayList<>();
        lore.addAll(applyPlaceholders(
                LanguageManager.getList(profile, LORE_HEADER_PATH, DEFAULT_LORE_HEADER),
                displayName,
                previewValue,
                option.permission));

        lore.addAll(applyPlaceholders(
                Collections.singletonList(LanguageManager.get(profile, PREVIEW_LINE_PATH, DEFAULT_PREVIEW_LINE,
                        "preview", previewValue)),
                displayName,
                previewValue,
                option.permission));

        List<String> statusDefaults = DEFAULT_STATUS_LINES.get(state);
        lore.addAll(applyPlaceholders(
                LanguageManager.getList(profile, STATUS_PATH + state.key, statusDefaults),
                displayName,
                previewValue,
                option.permission));

        meta.setLore(lore);

        if (state == OptionState.SELECTED) {
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private List<String> applyPlaceholders(List<String> source, String name, String preview, String permission) {
        List<String> resolved = new ArrayList<>(source.size());
        for (String line : source) {
            String withData = line
                    .replace("{name}", name)
                    .replace("{preview}", preview)
                    .replace("{permission}", permission);
            resolved.add(PlaceholderAPI.setPlaceholders(this.player, withData));
        }
        return resolved;
    }

    private ItemStack deserialize(String spec) {
        String resolved = PlaceholderAPI.setPlaceholders(this.player, spec);
        ItemStack item = BukkitUtils.deserializeItemStack(resolved);
        return item != null ? item : new ItemStack(org.bukkit.Material.AIR);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(this.getInventory())) {
            return;
        }
        event.setCancelled(true);
        if (!event.getWhoClicked().equals(this.player)) {
            return;
        }

        int slot = event.getSlot();
        if (slot == 49) {
            EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
            new MenuProfile(profile);
            return;
        }

        ColorOption option = OPTION_BY_SLOT.get(slot);
        if (option != null) {
            EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
            String current = activePattern == null ? "" : activePattern.toLowerCase(Locale.ROOT);
            if (option.matches(current)) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chatcoloradmin disable " + profile.getName());
            } else if (this.player.hasPermission(option.permission)) {
                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        "chatcoloradmin set " + profile.getName() + " " + option.pattern);
            }
            new MenuColor(profile);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(this.player) && event.getInventory().equals(this.getInventory())) {
            cancel();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(this.player)) {
            cancel();
        }
    }

    public void cancel() {
        HandlerList.unregisterAll(this);
    }

    private static Map<Integer, ColorOption> buildOptionIndex() {
        Map<Integer, ColorOption> map = new HashMap<>();
        for (ColorOption option : ColorOption.values()) {
            map.put(option.slot, option);
        }
        return Collections.unmodifiableMap(map);
    }

    private enum OptionState {
        SELECTED("selected"),
        AVAILABLE("available"),
        LOCKED("locked");

        private final String key;

        OptionState(String key) {
            this.key = key;
        }
    }

    private enum ColorOption {
        DARK_RED(19, "dark_red", "zcore.chat.dark_red", "dark-red", "336 : 1", "&4&lDark Red", "&4"),
        RED(20, "red", "zcore.chat.red", "red", "351:1 : 1", "&c&lRed", "&c"),
        GOLD(21, "gold", "zcore.chat.gold", "gold", "351:14 : 1", "&6&lGold", "&6"),
        YELLOW(22, "yellow", "zcore.chat.yellow", "yellow", "351:11 : 1", "&e&lYellow", "&e"),
        GREEN(23, "green", "zcore.chat.green", "green", "351:2 : 1", "&2&lDark Green", "&2"),
        LIME(24, "lime", "zcore.chat.lime", "lime", "351:10 : 1", "&a&lLime", "&a"),
        AQUA(25, "aqua", "zcore.chat.aqua", "aqua", "351:12 : 1", "&b&lAqua", "&b"),
        DARK_AQUA(28, "dark_aqua", "zcore.chat.dark_aqua", "dark-aqua", "351:6 : 1", "&3&lDark Aqua", "&3"),
        BLUE(29, "blue", "zcore.chat.blue", "blue", "351:4 : 1", "&9&lBlue", "&9"),
        DARK_BLUE(30, "dark_blue", "zcore.chat.dark_blue", "dark-blue", "349 : 1", "&1&lDark Blue", "&1"),
        PINK(31, "pink", "zcore.chat.pink", "pink", "351:9 : 1", "&d&lPink", "&d"),
        PURPLE(32, "purple", "zcore.chat.purple", "purple", "351:5 : 1", "&5&lPurple", "&5"),
        MONOCHROMATIC(33, "monochromatic", "zcore.chat.monochromatic", "monochromatic", "52 : 1", "&f&lMonochrome", "&7"),
        RAINBOW(34, "rainbow", "zcore.chat.rainbow", "rainbow", "47 : 1", "&d&lRainbow", "&cR&6a&ei&an&bb&do&5w&f ");

        private final int slot;
        private final String pattern;
        private final String permission;
        private final String languageKey;
        private final String defaultIcon;
        private final String defaultName;
        private final String defaultPreview;

        ColorOption(int slot, String pattern, String permission, String languageKey,
                    String defaultIcon, String defaultName, String defaultPreview) {
            this.slot = slot;
            this.pattern = pattern;
            this.permission = permission;
            this.languageKey = languageKey;
            this.defaultIcon = defaultIcon;
            this.defaultName = defaultName;
            this.defaultPreview = defaultPreview;
        }

        private String path(String suffix) {
            return OPTIONS_ROOT + languageKey + "." + suffix;
        }

        private boolean matches(String patternName) {
            return Objects.equals(pattern, patternName);
        }
    }
}
