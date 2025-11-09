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
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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

    public MenuLang(Profile profile) {
        super(profile.getPlayer(),
                LanguageManager.get(profile, "menus.language.title", "&8Profile &0&l> &8Language"), 6);

        populateBackground();
        populateProfileSummary(profile);
        populateLanguages(profile);
        populateBackButton(profile);

        this.register(Core.getInstance());
        this.open();
    }

    private void populateBackground() {
        ItemStack filler = BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1 : name>&r");
        for (int slot = 0; slot < this.getInventory().getSize(); slot++) {
            this.setItem(slot, filler.clone());
        }
    }

    private void populateProfileSummary(Profile profile) {
        ItemStack base = dev.zonely.whiteeffect.utils.MaterialResolver.createItemStack("SKULL_ITEM:3");
        ItemStack head = BukkitUtils.putProfileOnSkull(this.player, base);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LanguageManager.get(profile, "menus.language.profile.display-name",
                    "&e&l{player}", "player", profile.getName()));

            List<String> lore = LanguageManager.getList(profile, "menus.language.profile.lore", defaultProfileLore(),
                    "role", Role.getRoleByName(profile.getDataContainer("ZonelyCoreProfile", "role").getAsString()).getName(),
                    "credits", StringUtils.formatNumber(CashManager.getCash(profile)),
                    "fragments", profile.getFormatedStats("ZonelyMysteryBox", "mystery_frags"),
                    "boxes", StringUtils.formatNumber(MysteryBoxAPI.getMysteryBoxes(profile)),
                    "first_login", DATE_FORMAT.format(profile.getDataContainer("ZonelyCoreProfile", "created").getAsLong()),
                    "last_login", DATE_FORMAT.format(profile.getDataContainer("ZonelyCoreProfile", "lastlogin").getAsLong()));
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        this.setItem(4, head);
    }

    private void populateLanguages(Profile profile) {
        String currentLocale = profile.getLanguage();
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
            ItemStack icon = BukkitUtils.deserializeItemStack(languageMeta.icon());
            ItemMeta iconMeta = icon.getItemMeta();
            if (iconMeta != null) {
                iconMeta.setDisplayName(LanguageManager.get(profile, "menus.language.entry.display-name",
                        languageMeta.displayName(), "locale", locale));
                List<String> lore = new ArrayList<>(languageMeta.description());
                if (!lore.isEmpty()) {
                    lore.add("");
                }
                if (locale.equalsIgnoreCase(currentLocale)) {
                    lore.add(LanguageManager.get(profile, "menus.language.entry.selected", "&aSelected"));
                } else {
                    lore.add(LanguageManager.get(profile, "menus.language.entry.click", "&7Click to select."));
                }
                iconMeta.setLore(lore);
                icon.setItemMeta(iconMeta);
            }
            this.setItem(slot, icon);
            localeBySlot.put(slot, locale);
        }
    }

    private void populateBackButton(Profile profile) {
        ItemStack back = BukkitUtils.deserializeItemStack("ARROW : 1");
        ItemMeta meta = back.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LanguageManager.get(profile, "menus.language.back.display-name", "&e← Back"));
            meta.setLore(LanguageManager.getList(profile, "menus.language.back.lore",
                    Arrays.asList("&7Return to your profile settings.")));
            back.setItemMeta(meta);
        }
        this.setItem(49, back);
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
        LanguageMeta languageMeta = LanguageManager.getMeta(locale);
        LanguageManager.send(this.player, "menus.language.changed",
                "&aLanguage updated to {language}.", "language", languageMeta.displayName());
        new MenuLang(profile);
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

    private static List<String> defaultProfileLore() {
        return Arrays.asList(
                "&8• &fRole: &b{role}",
                "&8• &fCredits: &6{credits}",
                "&8• &fMystery Fragments: &d{fragments}",
                "&8• &fMystery Boxes: &b{boxes}",
                " ",
                "&8• &fFirst Login: &f{first_login}",
                "&8• &fLast Login: &f{last_login}"
        );
    }
}
