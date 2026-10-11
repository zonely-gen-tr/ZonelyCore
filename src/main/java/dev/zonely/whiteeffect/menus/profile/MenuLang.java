package dev.zonely.whiteeffect.menus.profile;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.lang.LanguageManager.LanguageMeta;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MenuLang extends PlayerMenu {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("d MMMM yyyy HH:mm", Locale.ENGLISH);
    private static final int[] LANGUAGE_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final Map<Integer, String> localeBySlot = new HashMap<>();
    private volatile boolean profileSummaryRefreshScheduled = false;

    public MenuLang(Profile profile) {
        super(profile.getPlayer(),
                LanguageManager.get(profile,
                        "menus.language-menu.title",
                        localize(profile, "&8Profile &0&l> &8Language", "&8Profil &0&l> &8Dil")),
                6);

        populateBackground(profile);
        populateProfileSummary(profile);
        populateLanguages(profile);
        populateBackButton(profile);

        this.register(Core.getInstance());
        this.open();
    }

    private void populateBackground(Profile profile) {
        ItemStack filler = BukkitUtils.deserializeItemStack(LanguageManager.get(profile,
                "menus.common.filler", "STAINED_GLASS_PANE:7 : 1 : name>&r"));
        for (int slot = 0; slot < this.getInventory().getSize(); slot++) {
            this.setItem(slot, filler.clone());
        }
    }

    private void populateProfileSummary(Profile profile) {
        long credits = resolveInitialCredits(profile);
        updateProfileSummary(profile, credits);
        scheduleProfileSummaryRefresh(profile);
    }

    private long resolveInitialCredits(Profile profile) {
        if (profile == null) {
            return 0L;
        }
        String playerName = profile.getName();
        if (CashManager.isCached(playerName)) {
            return CashManager.getCash(playerName);
        }
        try {
            Object raw = profile.getDataContainer("ZonelyCoreProfile", "credit").get();
            if (raw instanceof Number) {
                return ((Number) raw).longValue();
            }
            if (raw != null) {
                return Long.parseLong(String.valueOf(raw));
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    private void scheduleProfileSummaryRefresh(Profile profile) {
        if (profile == null || profileSummaryRefreshScheduled || CashManager.isCached(profile.getName())) {
            return;
        }
        profileSummaryRefreshScheduled = true;
        Bukkit.getScheduler().runTaskAsynchronously(Core.getInstance(), () -> {
            long loaded = CashManager.loadCashFromDB(profile.getName());
            CashManager.cacheCredit(profile.getName(), loaded);
            Bukkit.getScheduler().runTask(Core.getInstance(), () -> {
                profileSummaryRefreshScheduled = false;
                if (!this.player.isOnline()) {
                    return;
                }
                try {
                    if (!this.player.getOpenInventory().getTopInventory().equals(this.getInventory())) {
                        return;
                    }
                } catch (Throwable ignored) {
                    return;
                }
                updateProfileSummary(profile, loaded);
            });
        });
    }

    private void updateProfileSummary(Profile profile, long credits) {
        ItemStack base = dev.zonely.whiteeffect.utils.MaterialResolver.createItemStack("SKULL_ITEM:3");
        ItemStack head = BukkitUtils.putProfileOnSkull(this.player, base);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LanguageManager.get(profile,
                    "menus.language-menu.profile.display-name",
                    localize(profile, "&e&l{player}", "&e&l{player}"),
                    "player", profile.getName()));

            String currentLocale = LanguageManager.resolveProfileLocale(profile);
            String currentLanguage = resolveLanguageDisplayName(profile,
                    currentLocale,
                    LanguageManager.getMeta(currentLocale));
            List<String> lore = LanguageManager.getList(profile,
                    "menus.language-menu.profile.lore",
                    defaultProfileLore(profile),
                    "role", Role.getRoleByName(profile.getDataContainer("ZonelyCoreProfile", "role").getAsString()).getName(),
                    "credits", StringUtils.formatNumber(credits),
                    "fragments", profile.getFormatedStats("ZonelyMysteryBox", "mystery_frags"),
                    "boxes", StringUtils.formatNumber(MysteryBoxAPI.getMysteryBoxes(profile)),
                    "first_login", DATE_FORMAT.format(profile.getDataContainer("ZonelyCoreProfile", "created").getAsLong()),
                    "last_login", DATE_FORMAT.format(profile.getDataContainer("ZonelyCoreProfile", "lastlogin").getAsLong()),
                    "current_language", currentLanguage);
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        this.setItem(4, head);
    }

    private void populateLanguages(Profile profile) {
        localeBySlot.clear();

        String currentLocale = LanguageManager.resolveProfileLocale(profile);
        Set<String> locales = new LinkedHashSet<>(LanguageManager.getAvailableLocales());
        if (locales.isEmpty()) {
            locales.add(LanguageManager.getDefaultLocale());
        }
        locales.add(LanguageManager.getDefaultLocale());

        int index = 0;
        for (String locale : locales) {
            if (index >= LANGUAGE_SLOTS.length) {
                break;
            }
            int slot = LANGUAGE_SLOTS[index++];
            LanguageMeta languageMeta = LanguageManager.getMeta(locale);

            ItemStack icon = buildLanguageIcon(profile, locale, languageMeta, index - 1);
            ItemMeta iconMeta = icon.getItemMeta();
            if (iconMeta != null) {
                String languageName = resolveLanguageDisplayName(profile, locale, languageMeta);
                iconMeta.setDisplayName(LanguageManager.get(profile,
                        "menus.language-menu.entry.display-name",
                        "&f&l{language}",
                        "language", languageName,
                        "locale", locale));

                List<String> lore = new ArrayList<>();
                List<String> configuredLore = LanguageManager.getList(profile,
                        "menus.language-menu.entry.base-lore",
                        defaultLanguageLore(profile),
                        "language", languageName,
                        "locale", locale);
                lore.addAll(configuredLore);
                lore.add(LanguageManager.get(profile,
                        "menus.language-menu.entry.code-line",
                        localize(profile, "&8Code: &f{locale}", "&8Kod: &f{locale}"),
                        "locale", locale));
                lore.add("");

                if (locale.equalsIgnoreCase(currentLocale)) {
                    lore.add(LanguageManager.get(profile,
                            "menus.language-menu.entry.selected",
                            localize(profile, "&aSelected", "&aSecili")));
                } else {
                    lore.add(LanguageManager.get(profile,
                            "menus.language-menu.entry.click",
                            localize(profile, "&7Click to select.", "&7Secmek icin tikla.")));
                }

                iconMeta.setLore(lore);
                icon.setItemMeta(iconMeta);
            }

            this.setItem(slot, icon);
            localeBySlot.put(slot, locale);
        }
    }

    private ItemStack buildLanguageIcon(Profile profile, String locale, LanguageMeta languageMeta, int index) {
        boolean useSkullIcons = LanguageManager.getBoolean(
                "menus.language-menu.entry.use-skull-icons", false);
        String iconSpec = LanguageManager.get(profile,
                "menus.language-menu.entry.icons." + locale,
                languageMeta.icon());
        iconSpec = sanitizeIconSpec(iconSpec);

        if (!useSkullIcons && isSkullSpec(iconSpec)) {
            iconSpec = LanguageManager.get(profile,
                    "menus.language-menu.entry.fallback-icons." + locale,
                    defaultFallbackIcon(index));
        } else if (languageMeta.skin() != null
                && !languageMeta.skin().trim().isEmpty()
                && !iconSpec.contains("skin>")) {
            iconSpec = iconSpec + " : skin>" + languageMeta.skin().trim();
        }

        ItemStack icon;
        try {
            icon = BukkitUtils.deserializeItemStack(iconSpec);
        } catch (IllegalArgumentException ex) {
            icon = null;
        }
        if (icon == null || icon.getType() == Material.AIR) {
            try {
                icon = BukkitUtils.deserializeItemStack(defaultFallbackIcon(index));
            } catch (IllegalArgumentException ignored) {
                icon = new ItemStack(Material.PAPER);
            }
        }
        return icon;
    }

    private static String sanitizeIconSpec(String iconSpec) {
        if (iconSpec == null) {
            return "";
        }
        String trimmed = iconSpec.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.startsWith("EMPTY_MAP")) {
            return "MAP" + trimmed.substring("EMPTY_MAP".length());
        }
        return trimmed;
    }

    private static boolean isSkullSpec(String iconSpec) {
        if (iconSpec == null) {
            return false;
        }
        String normalized = iconSpec.toUpperCase(Locale.ROOT);
        return normalized.contains("SKULL_ITEM") || normalized.contains("PLAYER_HEAD");
    }

    private static String defaultFallbackIcon(int index) {
        String[] icons = new String[]{
                "PAPER : 1",
                "BOOK : 1",
                "MAP : 1",
                "MAP : 1",
                "NAME_TAG : 1",
                "COMPASS : 1",
                "FEATHER : 1",
                "ARROW : 1",
                "INK_SACK:4 : 1",
                "INK_SACK:11 : 1"
        };
        int safe = Math.floorMod(index, icons.length);
        return icons[safe];
    }

    private void populateBackButton(Profile profile) {
        String fallback = localize(profile,
                "ARROW : 1 : name>&e<- Back : desc>&7Return to profile overview.",
                "ARROW : 1 : name>&e<- Geri : desc>&7Profil genel bakisina don.");
        ItemStack back = BukkitUtils.deserializeItemStack(LanguageManager.get(profile,
                "menus.language-menu.back.item",
                fallback));
        this.setItem(49, back);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(this.player)) {
            return;
        }
        if (!event.getView().getTopInventory().equals(this.getInventory())) {
            return;
        }
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
        event.setCursor(null);
        event.getWhoClicked().setItemOnCursor(null);

        Profile profile = Profile.getProfile(this.player.getName());
        if (profile == null) {
            this.player.closeInventory();
            return;
        }

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(this.getInventory())) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 49) {
            EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
            new MenuProfile(profile);
            return;
        }

        String locale = localeBySlot.get(slot);
        if (locale == null) {
            return;
        }

        EnumSound.ITEM_PICKUP.play(this.player, 0.5F, 2.0F);
        profile.setLanguage(locale);
        LanguageManager.invalidateProfileLocale(profile);
        LanguageMeta languageMeta = LanguageManager.getMeta(locale);
        LanguageManager.send(this.player,
                "menus.language-menu.changed",
                localize(profile, "&aLanguage updated to {language}.", "&aDil {language} olarak guncellendi."),
                "language", resolveLanguageDisplayName(profile, locale, languageMeta));
        if (this.player.isOnline()) {
            this.player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClickFinal(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(this.player)) {
            return;
        }
        if (!event.getView().getTopInventory().equals(this.getInventory())) {
            return;
        }
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
        event.setCursor(null);
        event.getWhoClicked().setItemOnCursor(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getWhoClicked().equals(this.player)) {
            return;
        }
        if (!event.getView().getTopInventory().equals(this.getInventory())) {
            return;
        }
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
        event.setCursor(null);
        event.getWhoClicked().setItemOnCursor(null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryDragFinal(InventoryDragEvent event) {
        if (!event.getWhoClicked().equals(this.player)) {
            return;
        }
        if (!event.getView().getTopInventory().equals(this.getInventory())) {
            return;
        }
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
        event.setCursor(null);
        event.getWhoClicked().setItemOnCursor(null);
    }

    public void cancel() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(this.player)) {
            this.cancel();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(this.player) && event.getInventory().equals(this.getInventory())) {
            this.cancel();
        }
    }

    private String resolveLanguageDisplayName(Profile profile, String locale, LanguageMeta meta) {
        String fallback = defaultLanguageName(locale, meta.displayName());
        return LanguageManager.get(profile,
                "menus.language-menu.entry.names." + locale,
                fallback,
                "locale", locale,
                "language", fallback);
    }

    private static String defaultLanguageName(String localeCode, String fallback) {
        if (localeCode == null || localeCode.trim().isEmpty()) {
            return fallback;
        }
        try {
            String[] parts = localeCode.split("[_-]", 3);
            Locale locale;
            if (parts.length >= 2) {
                locale = new Locale(parts[0], parts[1]);
            } else {
                locale = new Locale(parts[0]);
            }
            String languageName = locale.getDisplayLanguage(locale);
            if (languageName == null || languageName.trim().isEmpty()) {
                return fallback;
            }
            String country = locale.getDisplayCountry(locale);
            if (country == null || country.trim().isEmpty()) {
                return capitalize(languageName);
            }
            return capitalize(languageName) + " (" + country + ")";
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private static String localize(Profile profile, String en, String tr) {
        String locale = LanguageManager.resolveProfileLocale(profile);
        if (locale != null && locale.toLowerCase(Locale.ROOT).startsWith("tr")) {
            return tr;
        }
        return en;
    }

    private static List<String> defaultProfileLore(Profile profile) {
        return profile != null && LanguageManager.resolveProfileLocale(profile).toLowerCase(Locale.ROOT).startsWith("tr")
                ? Arrays.asList(
                "&8- &fRol: &b{role}",
                "&8- &fKredi: &6{credits}",
                "&8- &fGizem Parcalari: &d{fragments}",
                "&8- &fGizemli Kutular: &b{boxes}",
                " ",
                "&8- &fIlk Giris: &f{first_login}",
                "&8- &fSon Giris: &f{last_login}",
                "&8- &fAktif Dil: &a{current_language}")
                : Arrays.asList(
                "&8- &fRole: &b{role}",
                "&8- &fCredits: &6{credits}",
                "&8- &fMystery Fragments: &d{fragments}",
                "&8- &fMystery Boxes: &b{boxes}",
                " ",
                "&8- &fFirst Login: &f{first_login}",
                "&8- &fLast Login: &f{last_login}",
                "&8- &fCurrent Language: &a{current_language}");
    }

    private static List<String> defaultLanguageLore(Profile profile) {
        return profile != null && LanguageManager.resolveProfileLocale(profile).toLowerCase(Locale.ROOT).startsWith("tr")
                ? Arrays.asList("&7Bu dili secmek icin tiklayin.")
                : Arrays.asList("&7Click to use this language.");
    }

}
