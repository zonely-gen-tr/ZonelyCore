package dev.zonely.whiteeffect.menus.profile;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.booster.Booster;
import dev.zonely.whiteeffect.booster.NetworkBooster;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.lang.LanguageManager.MenuItemDefinition;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.menus.MenuProfile;
import dev.zonely.whiteeffect.menus.profile.boosters.MenuBoostersList;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.mysterybox.api.MysteryBoxAPI;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.StringUtils;
import dev.zonely.whiteeffect.utils.TimeUtils;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class MenuBoosters extends PlayerMenu {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("d MMMM yyyy HH:mm", Locale.ENGLISH);

    private final Map<Integer, BoosterAction> actions = new HashMap<>();

    public MenuBoosters(Profile profile) {
        super(profile.getPlayer(),
                LanguageManager.get(profile,
                        "menus.boosters.title",
                        "&8Profile &0&l> &8Boosters"),
                6);

        fillBackground();
        populate(profile);

        this.register(Core.getInstance());
        this.open();
    }

    private void fillBackground() {
        ItemStack filler = BukkitUtils.deserializeItemStack(
                LanguageManager.get("menus.common.filler",
                        "STAINED_GLASS_PANE:7 : 1 : name>&r"));
        for (int slot = 0; slot < this.getInventory().getSize(); slot++) {
            this.setItem(slot, filler.clone());
        }
    }

    private void populate(Profile profile) {
        actions.clear();

        String rank = Role.getRoleByName(
                profile.getDataContainer("ZonelyCoreProfile", "role").getAsString()).getName();
        String credits = StringUtils.formatNumber(CashManager.getCash(profile));
        String fragments = profile.getFormatedStats("ZonelyMysteryBox", "mystery_frags");
        String boxes = StringUtils.formatNumber(MysteryBoxAPI.getMysteryBoxes(profile));
        String firstLogin = DATE_FORMAT.format(
                profile.getDataContainer("ZonelyCoreProfile", "created").getAsLong());
        String lastLogin = DATE_FORMAT.format(
                profile.getDataContainer("ZonelyCoreProfile", "lastlogin").getAsLong());

        MenuItemDefinition profileInfo = LanguageManager.getMenuItem(profile,
                "menus.profile.items.player-info", 4,
                "SKULL_ITEM:3 : 1 : name>&e&l{player} : desc>\n"
                        + "&8- &fRank: &b{role}\n"
                        + "&8- &fCredits: &6{credits}\n"
                        + "&8- &fMystery Fragments: &d{fragments}\n"
                        + "&8- &fMystery Boxes: &b{boxes}\n \n"
                        + "&8- &fFirst Login: &f{first_login}\n"
                        + "&8- &fLast Login: &f{last_login}",
                "player", profile.getName(),
                "role", rank,
                "credits", credits,
                "fragments", fragments,
                "boxes", boxes,
                "first_login", firstLogin,
                "last_login", lastLogin);
        this.setItem(profileInfo.slot(), BukkitUtils.putProfileOnSkull(this.player,
                BukkitUtils.deserializeItemStack(profileInfo.item())));

        placeMenuItem(profile, BoosterAction.BACK, "menus.boosters.items.back", 49,
                "ARROW : 1 : name>&e<-");
        placeMenuItem(profile, BoosterAction.OPEN_PERSONAL, "menus.boosters.items.global-button", 29,
                "27 : 1 : name>&4&lGlobal Boosters : desc>&7\n"
                        + "&fBoost coins you earn across every server.\n \n"
                        + "&4&l-> &cClick to view. : enchant>LURE:1");
        placeMenuItem(profile, BoosterAction.OPEN_NETWORK, "menus.boosters.items.network-button", 33,
                "28 : 1 : hide>genel : name>&3&lServer Boosters : desc>&7\n"
                        + "&fBoost coins on a specific game server.\n \n"
                        + "&3&l-> &bClick to view.");

        String networkSummary = buildNetworkSummary(profile);
        String personalSummary = buildPersonalSummary(profile);
        MenuItemDefinition summary = LanguageManager.getMenuItem(profile,
                "menus.boosters.items.summary", 50,
                "BOOK : 1 : name>&d&lBoosters : desc>&f\n"
                        + "&fMultiply the coins you earn in games with boosters.\n \n"
                        + "&f* Network Boosters:\n{network}\n \n"
                        + "&f* Personal Boosters:\n{personal}",
                "network", networkSummary,
                "personal", personalSummary);
        this.setItem(summary.slot(), BukkitUtils.deserializeItemStack(summary.item()));
    }

    private void placeMenuItem(Profile profile, BoosterAction action, String key,
                               int defaultSlot, String def, Object... placeholders) {
        MenuItemDefinition definition = LanguageManager.getMenuItem(profile, key, defaultSlot, def, placeholders);
        this.setItem(definition.slot(), BukkitUtils.deserializeItemStack(definition.item()));
        if (action != null) {
            actions.put(definition.slot(), action);
        }
    }

    private String buildNetworkSummary(Profile profile) {
        List<String> entries = new ArrayList<>();
        for (String minigame : Core.minigames) {
            NetworkBooster booster = Booster.getNetworkBooster(minigame);
            String status;
            if (booster == null) {
                status = LanguageManager.get(profile,
                        "menus.boosters.summary.network.status.inactive",
                        "&cInactive");
            } else {
                status = LanguageManager.get(profile,
                        "menus.boosters.summary.network.status.active",
                        "&e{multiplier}x {player} &8({time})",
                        "multiplier", booster.getMultiplier(),
                        "player", Role.getColored(booster.getBooster()),
                        "time", TimeUtils.getTimeUntil(booster.getExpires()));
            }
            entries.add(LanguageManager.get(profile,
                    "menus.boosters.summary.network.entry",
                    "&7 - &f{name}: {status}",
                    "name", minigame,
                    "status", status));
        }
        if (entries.isEmpty()) {
            return LanguageManager.get(profile,
                    "menus.boosters.summary.network.empty",
                    "&7(no network boosters configured)");
        }
        return entries.stream().collect(Collectors.joining("\n"));
    }

    private String buildPersonalSummary(Profile profile) {
        String active = profile.getBoostersContainer().getEnabled();
        if (active == null || active.isEmpty()) {
            return LanguageManager.get(profile,
                    "menus.boosters.summary.personal.none",
                    "&cYou do not have an active personal booster.");
        }

        String[] parts = active.split(":");
        if (parts.length < 2) {
            return LanguageManager.get(profile,
                    "menus.boosters.summary.personal.none",
                    "&cYou do not have an active personal booster.");
        }

        String multiplier = parts[0];
        long expiry;
        try {
            expiry = Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            return LanguageManager.get(profile,
                    "menus.boosters.summary.personal.none",
                    "&cYou do not have an active personal booster.");
        }

        double baseMultiplier;
        try {
            baseMultiplier = Double.parseDouble(multiplier);
        } catch (NumberFormatException ex) {
            baseMultiplier = 1.0D;
        }
        int projected = (int) Math.round(50.0D * baseMultiplier);

        return LanguageManager.get(profile,
                "menus.boosters.summary.personal.active",
                "&7 - &6{multiplier}x &f({time})\n \n"
                        + "&8Example: &650 &fcoins -> &6{projected} &fcoins.",
                "multiplier", multiplier,
                "time", TimeUtils.getTimeUntil(expiry),
                "projected", projected);
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

        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(this.getInventory())) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        int slot = event.getSlot();
        BoosterAction action = actions.get(slot);
        if (action == null) {
            EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
            return;
        }

        switch (action) {
            case OPEN_PERSONAL:
                EnumSound.ITEM_PICKUP.play(this.player, 0.5F, 2.0F);
                new MenuBoostersList(profile, Booster.BoosterType.PRIVATE);
                break;
            case OPEN_NETWORK:
                EnumSound.ITEM_PICKUP.play(this.player, 0.5F, 2.0F);
                new MenuBoostersList(profile, Booster.BoosterType.NETWORK);
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
        if (event.getPlayer().equals(this.player)
                && event.getInventory().equals(this.getInventory())) {
            this.cancel();
        }
    }

    private enum BoosterAction {
        OPEN_PERSONAL,
        OPEN_NETWORK,
        BACK
    }
}
