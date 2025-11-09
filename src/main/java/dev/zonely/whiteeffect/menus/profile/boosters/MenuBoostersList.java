package dev.zonely.whiteeffect.menus.profile.boosters;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.booster.Booster;
import dev.zonely.whiteeffect.booster.Booster.BoosterType;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.menu.PagedPlayerMenu;
import dev.zonely.whiteeffect.menus.profile.MenuBoosters;
import dev.zonely.whiteeffect.mysterybox.api.MysteryBoxAPI;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.StringUtils;
import dev.zonely.whiteeffect.utils.TimeUtils;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import dev.zonely.whiteeffect.cash.CashManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class MenuBoostersList extends PagedPlayerMenu {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("d MMMM yyyy HH:mm", Locale.ENGLISH);

    private final BoosterType type;
    private final Map<ItemStack, Booster> boosters = new HashMap<>();

    public MenuBoostersList(Profile profile, BoosterType type) {
        super(profile.getPlayer(),
                LanguageManager.get(profile,
                        "menus.boosters.list.title",
                        "&8Profile &0&l> &8{category} Boosters",
                        "category", resolveCategory(profile, type)),
                6);

        this.type = type;
        this.previousPage = 45;
        this.nextPage = 53;
        this.onlySlots(new Integer[]{
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        });

        ItemStack filler = BukkitUtils.deserializeItemStack(
                LanguageManager.get("menus.common.filler",
                        "STAINED_GLASS_PANE:7 : 1 : name>&r"));
        ItemStack back = BukkitUtils.deserializeItemStack(
                LanguageManager.get(profile,
                        "menus.common.back",
                        "ARROW : 1 : name>&e<-"));
        ItemStack profileHead = buildProfileHead(profile);

        this.removeSlotsWith(back, new int[]{49});
        this.removeSlotsWith(profileHead, new int[]{4});
        this.removeSlotsWith(filler, new int[]{0, 1, 2, 3, 5, 6, 7, 8});

        this.setItem(49, back);
        this.setItem(4, profileHead);
        int[] fillerSlots = {0, 1, 2, 3, 5, 6, 7, 8};
        for (int slot : fillerSlots) {
            this.setItem(slot, filler.clone());
        }

        populateItems(profile);

        this.register(Core.getInstance());
        this.open();
    }

    private static String resolveCategory(Profile profile, BoosterType type) {
        String key = type == BoosterType.NETWORK ? "network" : "personal";
        String fallback = type == BoosterType.NETWORK ? "Server" : "Personal";
        return LanguageManager.get(profile,
                "menus.boosters.list.category." + key,
                fallback);
    }

    private ItemStack buildProfileHead(Profile profile) {
        String rank = Role.getRoleByName(
                profile.getDataContainer("ZonelyCoreProfile", "role").getAsString()).getName();
        String credits = StringUtils.formatNumber(CashManager.getCash(profile));
        String fragments = profile.getFormatedStats("ZonelyMysteryBox", "mystery_frags");
        String boxes = StringUtils.formatNumber(MysteryBoxAPI.getMysteryBoxes(profile));
        String firstLogin = DATE_FORMAT.format(
                profile.getDataContainer("ZonelyCoreProfile", "created").getAsLong());
        String lastLogin = DATE_FORMAT.format(
                profile.getDataContainer("ZonelyCoreProfile", "lastlogin").getAsLong());

        LanguageManager.MenuItemDefinition definition = LanguageManager.getMenuItem(profile,
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

        ItemStack base = BukkitUtils.deserializeItemStack(definition.item());
        return BukkitUtils.putProfileOnSkull(this.player, base);
    }

    private void populateItems(Profile profile) {
        List<ItemStack> icons = new ArrayList<>();
        List<Booster> owned = profile.getBoostersContainer().getBoosters(type);

        String typeKey = type == BoosterType.NETWORK ? "network" : "personal";
        for (Booster booster : owned) {
            String duration = TimeUtils.getTime(TimeUnit.HOURS.toMillis(booster.getHours()));
            ItemStack icon = BukkitUtils.deserializeItemStack(
                    LanguageManager.get(profile,
                            "menus.boosters.list.items.booster." + typeKey,
                            "175 : 1 : name>&e&l{category} Booster : desc>&f\n"
                                    + "&7- &fMultiplier: &6{multiplier}x\n"
                                    + "&7- &fDuration: &e{duration}\n \n"
                                    + "&6&l-> &eClick to activate."
                                    + (type == BoosterType.NETWORK ? "" : " : enchant>LURE:1"),
                            "category", resolveCategory(profile, type),
                            "multiplier", booster.getMultiplier(),
                            "duration", duration));
            icons.add(icon);
            boosters.put(icon, booster);
        }

        if (icons.isEmpty()) {
            ItemStack placeholder = BukkitUtils.deserializeItemStack(
                    LanguageManager.get(profile,
                            "menus.boosters.list.items.empty",
                            "STAINED_GLASS_PANE:14 : 1 : name>&c&lYou do not own any boosters."));
            this.removeSlotsWith(placeholder, new int[]{31});
        }

        this.setItems(icons);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(this.getCurrentInventory())) {
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
                || !event.getClickedInventory().equals(this.getCurrentInventory())) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        int slot = event.getSlot();
        if (slot == this.previousPage) {
            EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
            this.openPrevious();
            return;
        }

        if (slot == this.nextPage) {
            EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
            this.openNext();
            return;
        }

        if (slot == 49) {
            EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
            new MenuBoosters(profile);
            return;
        }

        Booster booster = boosters.get(item);
        if (booster == null) {
            return;
        }

        String prefix = LanguageManager.get(profile,
                "prefix.lobby", "&3Lobby &8>> ");

        if (type == BoosterType.NETWORK) {
            if (!Core.minigames.contains(Core.minigame)) {
                this.player.sendMessage(LanguageManager.get(profile,
                        "menus.boosters.messages.network-require-game",
                        "{prefix}&cYou must be on a game server to activate a network booster.",
                        "prefix", prefix));
                return;
            }

            if (!Booster.setNetworkBooster(Core.minigame, profile, booster)) {
                EnumSound.ENDERMAN_TELEPORT.play(this.player, 0.5F, 1.0F);
                this.player.sendMessage(LanguageManager.get(profile,
                        "menus.boosters.messages.network-already-active",
                        "{prefix}&cA network booster for {minigame} is already running.",
                        "prefix", prefix,
                        "minigame", Core.minigame));
                this.player.closeInventory();
                return;
            }

            EnumSound.LEVEL_UP.play(this.player, 0.5F, 1.0F);
            String duration = TimeUtils.getTime(TimeUnit.HOURS.toMillis(booster.getHours()));
            this.player.sendMessage(LanguageManager.get(profile,
                    "menus.boosters.messages.network-activated-self",
                    "{prefix}&dActivated a {multiplier}x booster on {minigame} for {duration}.",
                    "prefix", prefix,
                    "minigame", Core.minigame,
                    "multiplier", booster.getMultiplier(),
                    "duration", duration));

            for (Profile target : Profile.listProfiles()) {
                if (target.getPlayer() == null || !target.getPlayer().isOnline()) {
                    continue;
                }
                target.getPlayer().sendMessage(LanguageManager.get(target,
                        "menus.boosters.messages.network-activated-broadcast",
                        "{prefix}{player} activated a {multiplier}x booster on {minigame}! "
                                + "Enjoy the bonus for {duration}.",
                        "prefix", LanguageManager.get(target,
                                "prefix.lobby", "&3Lobby &8>> "),
                        "player", Role.getColored(profile.getName()),
                        "minigame", Core.minigame,
                        "multiplier", booster.getMultiplier(),
                        "duration", duration));
            }
            this.player.closeInventory();
            return;
        }

        if (!profile.getBoostersContainer().enable(booster)) {
            EnumSound.ENDERMAN_TELEPORT.play(this.player, 0.5F, 1.0F);
            this.player.sendMessage(LanguageManager.get(profile,
                    "menus.boosters.messages.personal-already-active",
                    "{prefix}&cYou already have an active personal booster.",
                    "prefix", prefix));
            this.player.closeInventory();
            return;
        }

        EnumSound.LEVEL_UP.play(this.player, 0.5F, 1.0F);
        this.player.sendMessage(LanguageManager.get(profile,
                "menus.boosters.messages.personal-activated",
                "{prefix}&dPersonal booster activated: {multiplier}x for {duration}.",
                "prefix", prefix,
                "multiplier", booster.getMultiplier(),
                "duration", TimeUtils.getTime(TimeUnit.HOURS.toMillis(booster.getHours()))));
        new MenuBoosters(profile);
    }

    public void cancel() {
        boosters.clear();
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
                && event.getInventory().equals(this.getCurrentInventory())) {
            this.cancel();
        }
    }
}
