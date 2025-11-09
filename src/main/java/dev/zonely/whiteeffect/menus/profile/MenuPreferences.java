package dev.zonely.whiteeffect.menus.profile;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.database.data.container.PreferencesContainer;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.lang.LanguageManager.MenuItemDefinition;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.menus.MenuProfile;
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
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class MenuPreferences extends PlayerMenu {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("d MMMM yyyy HH:mm", Locale.ENGLISH);

    private final Map<Integer, PreferenceAction> actions = new HashMap<>();

    public MenuPreferences(Profile profile) {
        super(profile.getPlayer(),
                LanguageManager.get(profile, "menus.preferences.title",
                        "&8Profile &0&l> &8Preferences"),
                6);

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

        MenuItemDefinition info = LanguageManager.getMenuItem(profile,
                "menus.preferences.items.player-info", 4,
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
        this.setItem(info.slot(), BukkitUtils.putProfileOnSkull(this.player,
                BukkitUtils.deserializeItemStack(info.item())));

        placeStaticItem(profile, "menus.preferences.items.visibility.info", 20,
                "ITEM_FRAME : 1 : name>&e&lPlayer Visibility : desc>&8[Preference]\n \n"
                        + "&fHide or show other players globally.\n");
        placeMenuItem(profile, PreferenceAction.TOGGLE_VISIBILITY,
                "menus.preferences.items.visibility.state", 29,
                "159:{ink} : 1 : name>&e&lPlayer Visibility : desc>&f\n"
                        + "&fRefer to the panel above for details.\n \n"
                        + "&7- &fStatus: {status}\n \n"
                        + "&6&l-> &eClick to toggle.",
                "ink", visibility.getInkSack(),
                "status", visibility.getName());

        placeStaticItem(profile, "menus.preferences.items.private-messages.info", 21,
                "PAPER : 1 : name>&e&lPrivate Messages : desc>&8[Preference]\n \n"
                        + "&fEnable or disable private messages from players.\n");
        placeMenuItem(profile, PreferenceAction.TOGGLE_MESSAGES,
                "menus.preferences.items.private-messages.state", 30,
                "159:{ink} : 1 : name>&e&lPrivate Messages : desc>&f\n"
                        + "&fRefer to the panel above for details.\n \n"
                        + "&7- &fStatus: {status}\n \n"
                        + "&6&l-> &eClick to toggle.",
                "ink", privateMessages.getInkSack(),
                "status", privateMessages.getName());

        placeStaticItem(profile, "menus.preferences.items.blood.info", 23,
                "REDSTONE : 1 : name>&e&lHit Effect : desc>&8[Preference]\n \n"
                        + "&fToggle the blood effect that appears when you hit players.\n");
        placeMenuItem(profile, PreferenceAction.TOGGLE_BLOOD,
                "menus.preferences.items.blood.state", 32,
                "159:{ink} : 1 : name>&e&lHit Effect : desc>&f\n"
                        + "&fRefer to the panel above for details.\n \n"
                        + "&7- &fStatus: {status}\n \n"
                        + "&6&l-> &eClick to toggle.",
                "ink", bloodAndGore.getInkSack(),
                "status", bloodAndGore.getName());

        placeStaticItem(profile, "menus.preferences.items.protection.info", 24,
                "NETHER_STAR : 1 : name>&e&lLobby Confirmation : desc>&8[Preference]\n \n"
                        + "&fRequire the /lobby command to be typed twice to confirm.\n");
        placeMenuItem(profile, PreferenceAction.TOGGLE_PROTECTION,
                "menus.preferences.items.protection.state", 33,
                "159:{ink} : 1 : name>&e&lLobby Confirmation : desc>&f\n"
                        + "&fRefer to the panel above for details.\n \n"
                        + "&7- &fStatus: {status}\n \n"
                        + "&6&l-> &eClick to toggle.",
                "ink", protectionLobby.getInkSack(),
                "status", protectionLobby.getName());

        placeMenuItem(profile, PreferenceAction.BACK,
                "menus.preferences.items.back", 49,
                "ARROW : 1 : name>&e<-");
    }

    private void placeStaticItem(Profile profile, String key, int defaultSlot,
                                 String def, Object... placeholders) {
        MenuItemDefinition definition = LanguageManager.getMenuItem(profile, key, defaultSlot, def, placeholders);
        this.setItem(definition.slot(), BukkitUtils.deserializeItemStack(definition.item()));
    }

    private void placeMenuItem(Profile profile, PreferenceAction action, String key,
                               int defaultSlot, String def, Object... placeholders) {
        MenuItemDefinition definition = LanguageManager.getMenuItem(profile, key, defaultSlot, def, placeholders);
        this.setItem(definition.slot(), BukkitUtils.deserializeItemStack(definition.item()));
        if (action != null) {
            actions.put(definition.slot(), action);
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
        PreferenceAction action = actions.get(slot);
        if (action == null) {
            EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
            return;
        }

        switch (action) {
            case TOGGLE_VISIBILITY:
                EnumSound.ITEM_PICKUP.play(this.player, 0.5F, 2.0F);
                profile.getPreferencesContainer().changePlayerVisibility();
                if (!profile.playingGame()) {
                    profile.refreshPlayers();
                }
                new MenuPreferences(profile);
                break;
            case TOGGLE_MESSAGES:
                EnumSound.ITEM_PICKUP.play(this.player, 0.5F, 2.0F);
                profile.getPreferencesContainer().changePrivateMessages();
                new MenuPreferences(profile);
                break;
            case TOGGLE_BLOOD:
                EnumSound.ITEM_PICKUP.play(this.player, 0.5F, 2.0F);
                profile.getPreferencesContainer().changeBloodAndGore();
                new MenuPreferences(profile);
                break;
            case TOGGLE_PROTECTION:
                EnumSound.ITEM_PICKUP.play(this.player, 0.5F, 2.0F);
                profile.getPreferencesContainer().changeProtectionLobby();
                new MenuPreferences(profile);
                break;
            case BACK:
                EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                new MenuProfile(profile);
                break;
            default:
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

    private enum PreferenceAction {
        TOGGLE_VISIBILITY,
        TOGGLE_MESSAGES,
        TOGGLE_BLOOD,
        TOGGLE_PROTECTION,
        BACK
    }
}
