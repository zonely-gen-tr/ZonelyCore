package dev.zonely.whiteeffect.menus;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.database.data.container.PreferencesContainer;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.lang.LanguageManager.LanguageMeta;
import dev.zonely.whiteeffect.lang.LanguageManager.MenuItemDefinition;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.menus.MenuDeliveriesProfile;
import dev.zonely.whiteeffect.menus.profile.MenuBoosters;
import dev.zonely.whiteeffect.menus.profile.MenuColor;
import dev.zonely.whiteeffect.menus.profile.MenuLang;
import dev.zonely.whiteeffect.menus.profile.MenuLevels;
import dev.zonely.whiteeffect.menus.profile.MenuPreferences;
import dev.zonely.whiteeffect.menus.profile.MenuTitles;
import dev.zonely.whiteeffect.mysterybox.api.MysteryBoxAPI;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.enums.BloodAndGore;
import dev.zonely.whiteeffect.player.enums.PlayerVisibility;
import dev.zonely.whiteeffect.player.enums.PrivateMessages;
import dev.zonely.whiteeffect.player.enums.ProtectionLobby;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.StringUtils;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import dev.zonely.whiteeffect.store.LastCreditsMenuManager;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class MenuProfile extends PlayerMenu {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("d MMMM yyyy HH:mm", Locale.ENGLISH);

    private final String colorData;
    private final String levelName;
    private final Map<Integer, MenuAction> actions = new HashMap<>();

    public MenuProfile(Profile profile) {
        super(profile.getPlayer(),
                LanguageManager.get(profile, "menus.profile.title",
                        "&8Profile &0&l> &8Overview"),
                6);

        this.colorData = PlaceholderAPI.setPlaceholders(this.player, "%chatcolor_pattern_name%");
        this.levelName = PlaceholderAPI.setPlaceholders(this.player, "%alonsoleagues_league_display%");

        fillBackground();
        populateMenu(profile);

        this.register(Core.getInstance());
        this.open();
    }

    private void fillBackground() {
        ItemStack filler = BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1 : name>&r");
        for (int slot = 0; slot < this.getInventory().getSize(); slot++) {
            this.setItem(slot, filler.clone());
        }
    }

    private void populateMenu(Profile profile) {
        actions.clear();
        PreferencesContainer preferences = profile.getPreferencesContainer();
        PlayerVisibility visibility = preferences.getPlayerVisibility();
        PrivateMessages privateMessages = preferences.getPrivateMessages();
        BloodAndGore bloodAndGore = preferences.getBloodAndGore();
        ProtectionLobby protectionLobby = preferences.getProtectionLobby();

        String rank = Role.getRoleByName(profile.getDataContainer("ZonelyCoreProfile", "role").getAsString()).getName();
        String credits = StringUtils.formatNumber(CashManager.getCash(profile));
        String fragments = profile.getFormatedStats("ZonelyMysteryBox", "mystery_frags");
        String boxes = StringUtils.formatNumber(MysteryBoxAPI.getMysteryBoxes(profile));
        String firstLogin = DATE_FORMAT.format(profile.getDataContainer("ZonelyCoreProfile", "created").getAsLong());
        String lastLogin = DATE_FORMAT.format(profile.getDataContainer("ZonelyCoreProfile", "lastlogin").getAsLong());

        String locale = profile.getLanguage();
        if (locale == null || locale.isEmpty()) {
            locale = LanguageManager.getDefaultLocale();
        }
        LanguageMeta languageMeta = LanguageManager.getMeta(locale);

        MenuItemDefinition playerInfo = LanguageManager.getMenuItem(profile,
                "menus.profile.items.player-info", 4,
                "SKULL_ITEM:3 : 1 : name>&e&l{player} : desc>\n"
                        + "&8- &fRank: &b{role}\n"
                        + "&8- &fCredits: &6{credits}\n"
                        + "&8- &fMystery Fragments: &d{fragments}\n"
                        + "&8- &fMystery Boxes: &b{boxes}\n"
                        + " \n"
                        + "&8- &fFirst Login: &f{first_login}\n"
                        + "&8- &fLast Login: &f{last_login}",
                "player", profile.getName(),
                "role", rank,
                "credits", credits,
                "fragments", fragments,
                "boxes", boxes,
                "first_login", firstLogin,
                "last_login", lastLogin);
        this.setItem(playerInfo.slot(), BukkitUtils.putProfileOnSkull(this.player,
                BukkitUtils.deserializeItemStack(playerInfo.item())));

        placeMenuItem(profile, MenuAction.CLOSE, "menus.profile.items.close", 49,
                "324 : 1 : name>&cClose");

        placeMenuItem(profile, MenuAction.PREFERENCES, "menus.profile.items.preferences", 19,
                "404 : 1 : name>&4&lSettings : desc>&8[Player Preferences]\n\n"
                        + "&fConfigure personal settings across the network.\n\n"
                        + "&8- &7Player Visibility: {player_visibility}\n"
                        + "&8- &7Private Messages: {private_messages}\n"
                        + "&8- &7Hit Effect: {blood_effect}\n"
                        + "&8- &7Transfer Countdown: {lobby_protection}\n\n"
                        + "&4&l-> &cClick to open.",
                "player_visibility", visibility.getName(),
                "private_messages", privateMessages.getName(),
                "blood_effect", bloodAndGore.getName(),
                "lobby_protection", protectionLobby.getName());

        placeMenuItem(profile, MenuAction.TITLES, "menus.profile.items.titles", 20,
                "323 : 1 : name>&3&lTitles : desc>&8[Chat Prefixes]\n\n"
                        + "&fUnlock rare titles to showcase after your name in chat.\n\n"
                        + "&3&l-> &9Click to open.");

        placeMenuItem(profile, MenuAction.BOOSTERS, "menus.profile.items.boosters", 22,
                "384 : 1 : name>&d&lBoosters : desc>&8[Coin Booster]\n\n"
                        + "&fMultiply the rewards you earn with active boosters.\n\n"
                        + "&5&l-> &dClick to open.");

        placeMenuItem(profile, MenuAction.DELIVERIES, "menus.profile.items.deliveries", 24,
                "407 : 1 : name>&2&lDaily Rewards : desc>&8[Timed Rewards]\n\n"
                        + "&fClaim time-based rewards from this courier.\n\n"
                        + "&2&l-> &aClick to open.");

        placeMenuItem(profile, MenuAction.STORE, "menus.profile.items.store", 25,
                "EMERALD : 1 : name>&a&lStore : desc>&8[Cosmetics & Packages]\n\n"
                        + "&fBrowse purchasable items and categories.\n\n"
                        + "&a&l-> &2Click to open.");

        placeMenuItem(profile, MenuAction.LEVEL, "menus.profile.items.level", 29,
                "158 : 1 : name>&5&lLevel : desc>&8[Ranks]\n\n"
                        + "&fSpend earned experience to advance your level.\n\n"
                        + "&8- &7Current Level: {level}\n\n"
                        + "&5&l-> &dClick to open.",
                "level", this.levelName);

        placeMenuItem(profile, MenuAction.LAST_CREDITS, "menus.profile.items.last-credits", 30,
                "BOOKSHELF : 1 : name>&6&lLatest Top-Ups : desc>&8[Credit Activity]\n\n"
                        + "&fSee the most recent players who loaded credits.\n\n"
                        + "&6&l-> &eClick to open.");

        placeMenuItem(profile, MenuAction.LANGUAGE, "menus.profile.items.language", 31,
                "380 : 1 : name>&3&lLanguage : desc>&8[Translation]\n\n"
                        + "&fChoose the language you prefer to use in game.\n\n"
                        + "&8- &7Current: {language}\n\n"
                        + "&3&l-> &9Click to open.",
                "language", languageMeta.displayName());

        placeMenuItem(profile, MenuAction.VIP_COLOR, "menus.profile.items.vip-color.data", 32,
                "357 : 1 : name>&4&lVIP Color : desc>&8[Name Colors]\n\n"
                        + "&fChoose the colour applied to your chat name.\n\n"
                        + "&8- &fSelected Color: {selected_color}\n\n"
                        + "&4&l-> &cClick to open.",
                "selected_color", resolveVipColor(profile));

        placeMenuItem(profile, MenuAction.SOCIAL, "menus.profile.items.social", 33,
                "379 : 1 : name>&2&lSocial Media : desc>&8[Links]\n\n"
                        + "&fManage the social media links visible to other players.\n\n"
                        + "&2&l-> &aClick to open.");
    }

    private void placeMenuItem(Profile profile, MenuAction action, String key,
                               int defaultSlot, String def, Object... placeholders) {
        MenuItemDefinition definition = LanguageManager.getMenuItem(profile, key, defaultSlot, def, placeholders);
        ItemStack item = BukkitUtils.deserializeItemStack(definition.item());
        this.setItem(definition.slot(), item);
        if (action != null) {
            actions.put(definition.slot(), action);
        }
    }

    private String resolveVipColor(Profile profile) {
        String pattern = colorData == null ? "" : colorData.toLowerCase(Locale.ROOT);
        switch (pattern) {
            case "dark_red":
                return LanguageManager.get(profile, "menus.profile.items.vip-color.status.dark_red", "&4Crimson");
            case "red":
                return LanguageManager.get(profile, "menus.profile.items.vip-color.status.red", "&cRed");
            case "gold":
                return LanguageManager.get(profile, "menus.profile.items.vip-color.status.gold", "&6Gold");
            case "yellow":
                return LanguageManager.get(profile, "menus.profile.items.vip-color.status.yellow", "&eYellow");
            case "green":
                return LanguageManager.get(profile, "menus.profile.items.vip-color.status.green", "&2Dark Green");
            case "lime":
                return LanguageManager.get(profile, "menus.profile.items.vip-color.status.lime", "&aLime");
            case "aqua":
                return LanguageManager.get(profile, "menus.profile.items.vip-color.status.aqua", "&bAqua");
            case "dark_aqua":
                return LanguageManager.get(profile, "menus.profile.items.vip-color.status.dark_aqua", "&3Dark Aqua");
            case "blue":
                return LanguageManager.get(profile, "menus.profile.items.vip-color.status.blue", "&9Blue");
            case "dark_blue":
                return LanguageManager.get(profile, "menus.profile.items.vip-color.status.dark_blue", "&1Dark Blue");
            case "pink":
                return LanguageManager.get(profile, "menus.profile.items.vip-color.status.pink", "&dPink");
            case "purple":
                return LanguageManager.get(profile, "menus.profile.items.vip-color.status.purple", "&5Purple");
            case "monochromatic":
                return LanguageManager.get(profile, "menus.profile.items.vip-color.status.monochromatic", "&7Monochrome");
            case "rainbow":
                return LanguageManager.get(profile, "menus.profile.items.vip-color.status.rainbow", "&5Rain&dbow");
            default:
                return LanguageManager.get(profile, "menus.profile.items.vip-color.status.default", "&7Default");
        }
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

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        int slot = event.getSlot();
        MenuAction action = actions.get(slot);
        if (action == null) {
            EnumSound.ITEM_PICKUP.play(this.player, 0.5F, 2.0F);
            return;
        }

        switch (action) {
            case CLOSE:
                this.player.closeInventory();
                break;
            case PREFERENCES:
                EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                new MenuPreferences(profile);
                break;
            case TITLES:
                EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                new MenuTitles(profile);
                break;
            case BOOSTERS:
                EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                new MenuBoosters(profile);
                break;
            case DELIVERIES:
                EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                new MenuDeliveriesProfile(profile);
                break;
            case STORE:
                EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                Core.getInstance().getProductMenuManager().openCategoryMenu(this.player, 0);
                break;
            case LEVEL:
                EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                new MenuLevels(profile);
                break;
            case LAST_CREDITS:
                EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                if (Core.getInstance().isLastCreditsEnabled() && Core.getInstance().getCreditsManager() != null) {
                    Core.getInstance().getCreditsManager().openMenu(this.player, LastCreditsMenuManager.Category.RECENT, 0);
                } else {
                    String prefix = LanguageManager.get(profile, "prefix.lobby", "&3Lobby &8>> ");
                    LanguageManager.send(this.player,
                            "menus.last-credits.disabled",
                            "{prefix}&cLast credits module is disabled.",
                            "prefix", prefix);
                }
                break;
            case LANGUAGE:
                EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                new MenuLang(profile);
                break;
            case VIP_COLOR:
                EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                new MenuColor(profile);
                break;
            case SOCIAL:
                EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "social edit " + profile.getName());
                break;
            default:
                EnumSound.ITEM_PICKUP.play(this.player, 0.5F, 2.0F);
                break;
        }
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

    private enum MenuAction {
        CLOSE,
        PREFERENCES,
        TITLES,
        BOOSTERS,
        DELIVERIES,
        STORE,
        LEVEL,
        LAST_CREDITS,
        LANGUAGE,
        VIP_COLOR,
        SOCIAL
    }
}
