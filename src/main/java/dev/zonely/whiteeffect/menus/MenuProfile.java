package dev.zonely.whiteeffect.menus;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.lang.LanguageManager.MenuItemDefinition;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.menus.MenuDeliveriesProfile;
import dev.zonely.whiteeffect.mysterybox.api.MysteryBoxAPI;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.StringUtils;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import dev.zonely.whiteeffect.store.LastCreditsMenuManager;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class MenuProfile extends PlayerMenu {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("d MMMM yyyy HH:mm", Locale.ENGLISH);

    private final Map<Integer, MenuAction> actions = new HashMap<>();

    public MenuProfile(Profile profile) {
        super(profile.getPlayer(),
                LanguageManager.get(profile, "menus.profile.title",
                        "&8Profile &0&l> &8Overview"),
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

        String rank = Role.getRoleByName(profile.getDataContainer("ZonelyCoreProfile", "role").getAsString()).getName();
        String credits = StringUtils.formatNumber(CashManager.getCash(profile));
        String fragments = profile.getFormatedStats("ZonelyMysteryBox", "mystery_frags");
        String boxes = StringUtils.formatNumber(MysteryBoxAPI.getMysteryBoxes(profile));
        String firstLogin = DATE_FORMAT.format(profile.getDataContainer("ZonelyCoreProfile", "created").getAsLong());
        String lastLogin = DATE_FORMAT.format(profile.getDataContainer("ZonelyCoreProfile", "lastlogin").getAsLong());

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

        placeMenuItemAt(profile, MenuAction.DELIVERIES, "menus.profile.items.deliveries", 21,
                "407 : 1 : name>&2&lDaily Rewards : desc>&8[Timed Rewards]\n\n"
                        + "&fClaim time-based rewards from this courier.\n\n"
                        + "&2&l-> &aClick to open.");

        placeMenuItemAt(profile, MenuAction.STORE, "menus.profile.items.store", 23,
                "EMERALD : 1 : name>&a&lStore : desc>&8[Cosmetics & Packages]\n\n"
                        + "&fBrowse purchasable items and categories.\n\n"
                        + "&a&l-> &2Click to open.");

        placeMenuItemAt(profile, MenuAction.LAST_CREDITS, "menus.profile.items.last-credits", 31,
                "BOOKSHELF : 1 : name>&6&lLatest Top-Ups : desc>&8[Credit Activity]\n\n"
                        + "&fSee the most recent players who loaded credits.\n\n"
                        + "&6&l-> &eClick to open.");

        // Social/Settings entries intentionally omitted by request.
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

    private void placeMenuItemAt(Profile profile, MenuAction action, String key,
                                 int forcedSlot, String def, Object... placeholders) {
        MenuItemDefinition definition = LanguageManager.getMenuItem(profile, key, forcedSlot, def, placeholders);
        ItemStack item = BukkitUtils.deserializeItemStack(definition.item());
        this.setItem(forcedSlot, item);
        if (action != null) {
            actions.put(forcedSlot, action);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(this.player)) {
            return;
        }
        if (!event.getView().getTopInventory().equals(this.getInventory())) {
            return;
        }
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot >= topSize) {
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
            case DELIVERIES:
                EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                new MenuDeliveriesProfile(profile);
                break;
            case STORE:
                EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                Core.getInstance().getProductMenuManager().openCategoryMenu(this.player, 0);
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
            default:
                EnumSound.ITEM_PICKUP.play(this.player, 0.5F, 2.0F);
                break;
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getWhoClicked().equals(this.player)) {
            return;
        }
        if (!event.getView().getTopInventory().equals(this.getInventory())) {
            return;
        }
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
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
        DELIVERIES,
        STORE,
        LAST_CREDITS
    }
}
