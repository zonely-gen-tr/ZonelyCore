package dev.zonely.whiteeffect.store.menu;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.menu.MenuInventoryFactory;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.store.dao.AuctionDao;
import dev.zonely.whiteeffect.store.dao.AuctionDao.MenuData;
import dev.zonely.whiteeffect.store.dao.AuctionDao.PurchaseResult;
import dev.zonely.whiteeffect.store.model.AuctionItem;
import dev.zonely.whiteeffect.utils.ItemSerializer;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import dev.zonely.whiteeffect.utils.StringUtils;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AuctionMenuManager implements Listener {

    private static final String PREFIX_KEY = "prefix.lobby";
    private static final String PREFIX_DEFAULT = "&3Lobby &8» ";

    private static final int SIZE = 54;
    private static final int PAGE_OFF = 9;
    private static final int PAGE_MAX = 36;

    private final Core plugin;
    private final AuctionDao dao;

    public AuctionMenuManager(Core plugin) {
        this.plugin = plugin;
        this.dao = new AuctionDao(plugin.getConfig().getInt("auction-server-id"));
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public AuctionDao getDao() {
        return dao;
    }

    public void openAuctionMenu(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MenuData data = dao.loadMenuData(player.getName());
                List<AuctionItem> items = data.getItems();
                int credit = data.getCredit();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Profile profile = Profile.getProfile(player.getName());
                    Holder holder = new Holder(MenuType.MAIN);
                    Inventory inventory = MenuInventoryFactory.create(holder, SIZE,
                            LanguageManager.get(profile, "menus.auction.main-title", "&8Auctions"));

                    for (int i = 0; i < items.size() && i < PAGE_MAX; i++) {
                        AuctionItem item = items.get(i);
                        int slot = PAGE_OFF + i;
                        holder.map.put(slot, item);
                        try {
                            ItemStack stack = ItemSerializer.deserialize(item.getItemData());
                            ItemMeta meta = stack.getItemMeta();
                            if (meta != null) {
                                meta.setDisplayName(LanguageManager.get(profile,
                                        "menus.auction.item.display-name",
                                        "&e{title}",
                                        "title", item.getCustomTitle()));
                                meta.setLore(buildMainLore(profile, item));
                                stack.setItemMeta(meta);
                            }
                            inventory.setItem(slot, stack);
                        } catch (Exception ex) {
                            plugin.getLogger().warning("Item could not be loaded: " + ex.getMessage());
                        }
                    }

                    fillNavigationItems(profile, inventory, credit);
                    player.openInventory(inventory);
                });
            } catch (SQLException ex) {
                plugin.getLogger().severe("Menu data could not be loaded: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> send(player,
                        "menus.auction.error.load-main",
                        "{prefix}&cUnable to load auctions.",
                        "prefix", LanguageManager.get(PREFIX_KEY, PREFIX_DEFAULT)));
            }
        });
    }

    public void openSoldMenu(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int userId = dao.fetchUserId(player.getName());
                List<AuctionItem> sold = dao.listByCreator(userId);
                int credit = dao.loadMenuData(player.getName()).getCredit();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Profile profile = Profile.getProfile(player.getName());
                    Holder holder = new Holder(MenuType.SOLD);
                    Inventory inventory = MenuInventoryFactory.create(holder, SIZE,
                            LanguageManager.get(profile, "menus.auction.sold-title", "&8My Sales"));

                    for (int i = 0; i < sold.size() && i < PAGE_MAX; i++) {
                        AuctionItem item = sold.get(i);
                        int slot = PAGE_OFF + i;
                        holder.map.put(slot, item);
                        try {
                            ItemStack stack = ItemSerializer.deserialize(item.getItemData());
                            ItemMeta meta = stack.getItemMeta();
                            if (meta != null) {
                                meta.setDisplayName(LanguageManager.get(profile,
                                        "menus.auction.sold.item.display-name",
                                        "&f{title}",
                                        "title", item.getCustomTitle()));
                                meta.setLore(buildSoldLore(profile, item));
                                stack.setItemMeta(meta);
                            }
                            inventory.setItem(slot, stack);
                        } catch (Exception ex) {
                            plugin.getLogger().warning("Could not render sold item: " + ex.getMessage());
                        }
                    }

                    inventory.setItem(45, named(new ItemStack(Material.ARROW),
                            LanguageManager.get(profile, "menus.auction.nav.back.name", "&eâ† Back"), null));
                    fillNavigationItems(profile, inventory, credit);
                    player.openInventory(inventory);
                });
            } catch (SQLException ex) {
                plugin.getLogger().severe("Failed to load sale history: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> send(player,
                        "menus.auction.error.load-sold",
                        "{prefix}&cUnable to load sold items.",
                        "prefix", LanguageManager.get(PREFIX_KEY, PREFIX_DEFAULT)));
            }
        });
    }

    public void openPurchasedMenu(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int userId = dao.fetchUserId(player.getName());
                List<AuctionItem> bought = dao.listByBuyer(userId);
                int credit = dao.loadMenuData(player.getName()).getCredit();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Profile profile = Profile.getProfile(player.getName());
                    Holder holder = new Holder(MenuType.BOUGHT);
                    Inventory inventory = MenuInventoryFactory.create(holder, SIZE,
                            LanguageManager.get(profile, "menus.auction.bought-title", "&8My Purchases"));

                    for (int i = 0; i < bought.size() && i < PAGE_MAX; i++) {
                        AuctionItem item = bought.get(i);
                        int slot = PAGE_OFF + i;
                        holder.map.put(slot, item);
                        try {
                            ItemStack stack = ItemSerializer.deserialize(item.getItemData());
                            ItemMeta meta = stack.getItemMeta();
                            if (meta != null) {
                                meta.setDisplayName(LanguageManager.get(profile,
                                        "menus.auction.bought.item.display-name",
                                        "&f{title}",
                                        "title", item.getCustomTitle()));
                                meta.setLore(buildBoughtLore(profile, item));
                                stack.setItemMeta(meta);
                            }
                            inventory.setItem(slot, stack);
                        } catch (Exception ex) {
                            plugin.getLogger().warning("Could not render purchased item: " + ex.getMessage());
                        }
                    }

                    inventory.setItem(45, named(new ItemStack(Material.ARROW),
                            LanguageManager.get(profile, "menus.auction.nav.back.name", "&eâ† Back"), null));
                    fillNavigationItems(profile, inventory, credit);
                    player.openInventory(inventory);
                });
            } catch (SQLException ex) {
                plugin.getLogger().severe("Failed to load purchased history: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> send(player,
                        "menus.auction.error.load-bought",
                        "{prefix}&cUnable to load purchased items.",
                        "prefix", LanguageManager.get(PREFIX_KEY, PREFIX_DEFAULT)));
            }
        });
    }

    public void openPurchaseMenu(Player player, AuctionItem item) {
        Profile profile = Profile.getProfile(player.getName());
        Holder holder = new Holder(MenuType.PURCHASE);
        holder.pending = item;
        Inventory inventory = MenuInventoryFactory.create(holder, 27,
                LanguageManager.get(profile, "menus.auction.purchase.title",
                        "&8Purchasing: {item}", "item", item.getCustomTitle()));

        ItemStack cancel = MaterialResolver.createItemStack("STAINED_CLAY:14");
        ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(LanguageManager.get(profile,
                    "menus.auction.purchase.cancel",
                    "&cCancel"));
            cancel.setItemMeta(cancelMeta);
        }
        inventory.setItem(11, cancel);

        ItemStack accept = MaterialResolver.createItemStack("STAINED_CLAY:13");
        ItemMeta acceptMeta = accept.getItemMeta();
        if (acceptMeta != null) {
            acceptMeta.setDisplayName(LanguageManager.get(profile,
                    "menus.auction.purchase.confirm",
                    "&aConfirm"));
            accept.setItemMeta(acceptMeta);
        }
        inventory.setItem(15, accept);

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof Holder)) {
            return;
        }
        Holder holder = (Holder) inventory.getHolder();
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        switch (holder.type) {
            case MAIN:
                handleMainClick(player, holder, slot);
                break;
            case PURCHASE:
                handlePurchaseClick(player, holder, slot);
                break;
            case SOLD:
            case BOUGHT:
                if (slot == 45) {
                    openAuctionMenu(player);
                }
                break;
            default:
                break;
        }
    }

    private void handleMainClick(Player player, Holder holder, int slot) {
        if (slot == 48) {
            openSoldMenu(player);
            return;
        }
        if (slot == 50) {
            openPurchasedMenu(player);
            return;
        }

        AuctionItem item = holder.map.get(slot);
        if (item != null) {
            openPurchaseMenu(player, item);
        }
    }

    private void handlePurchaseClick(Player player, Holder holder, int slot) {
        if (slot == 11) {
            player.closeInventory();
            return;
        }
        AuctionItem item = holder.pending;
        if (item == null || slot != 15) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int buyerId = dao.fetchUserId(player.getName());
                if (buyerId <= 0) {
                    Bukkit.getScheduler().runTask(plugin, () -> send(player,
                            "menus.auction.error.user-not-found",
                            "{prefix}&cUnable to resolve your account.",
                            "prefix", LanguageManager.get(PREFIX_KEY, PREFIX_DEFAULT)));
                    return;
                }

                PurchaseResult result = dao.finalizePurchase(item, buyerId, player.getName());
                switch (result.getStatus()) {
                    case INSUFFICIENT_FUNDS:
                        Bukkit.getScheduler().runTask(plugin, () -> send(player,
                                "menus.auction.error.insufficient-funds",
                                "{prefix}&cYou do not have enough credits.",
                                "prefix", LanguageManager.get(PREFIX_KEY, PREFIX_DEFAULT)));
                        return;
                    case BUYER_NOT_FOUND:
                        Bukkit.getScheduler().runTask(plugin, () -> send(player,
                                "menus.auction.error.buyer-data",
                                "{prefix}&cYour account information could not be read.",
                                "prefix", LanguageManager.get(PREFIX_KEY, PREFIX_DEFAULT)));
                        return;
                    case CREATOR_NOT_FOUND:
                        Bukkit.getScheduler().runTask(plugin, () -> send(player,
                                "menus.auction.error.creator-data",
                                "{prefix}&cThe buyer could not be reached.",
                                "prefix", LanguageManager.get(PREFIX_KEY, PREFIX_DEFAULT)));
                        return;
                    case SUCCESS:
                        break;
                    default:
                        Bukkit.getScheduler().runTask(plugin, () -> send(player,
                                "menus.auction.error.purchase-failed",
                                "{prefix}&cThe purchase could not be completed.",
                                "prefix", LanguageManager.get(PREFIX_KEY, PREFIX_DEFAULT)));
                        return;
                }

                ItemStack stack;
                try {
                    stack = ItemSerializer.deserialize(item.getItemData());
                } catch (Exception deserializeEx) {
                    plugin.getLogger().severe("Item deserialize error: " + deserializeEx.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () -> send(player,
                            "menus.auction.error.add-item",
                            "{prefix}&cThe item could not be added to your inventory.",
                            "prefix", LanguageManager.get(PREFIX_KEY, PREFIX_DEFAULT)));
                    return;
                }

                ItemStack finalStack = stack;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.getInventory().addItem(finalStack);
                    send(player,
                            "menus.auction.success.purchase",
                            "{prefix}&aPurchase successful.",
                            "prefix", LanguageManager.get(PREFIX_KEY, PREFIX_DEFAULT));
                    player.closeInventory();
                    openAuctionMenu(player);
                });
            } catch (Exception ex) {
                plugin.getLogger().severe("Purchase error: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> send(player,
                        "menus.auction.error.purchase-exception",
                        "{prefix}&cAn error occurred while completing the purchase.",
                        "prefix", LanguageManager.get(PREFIX_KEY, PREFIX_DEFAULT)));
            }
        });
    }

    private List<String> buildMainLore(Profile profile, AuctionItem item) {
        List<String> lore = new ArrayList<>();
        String description = item.getDescription();
        if (description == null || description.isEmpty()) {
            lore.add(LanguageManager.get(profile,
                    "menus.auction.item.no-description",
                    "&7No description provided."));
        } else {
            lore.add(StringUtils.formatColors(description));
        }
        lore.add(LanguageManager.get(profile,
                "menus.auction.item.price",
                "&8 - &fPrice: &b{price} Credits",
                "price", item.getCreditCost()));
        lore.add("");
        lore.add(LanguageManager.get(profile,
                "menus.auction.item.action",
                "&6&l> &eClick to purchase."));
        return lore;
    }

    private List<String> buildSoldLore(Profile profile, AuctionItem item) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(LanguageManager.get(profile,
                "menus.auction.sold.item.buyer",
                "&8 - &fBuyer: &a{buyer}",
                "buyer", item.getBuyerName() == null ? "-" : item.getBuyerName()));
        lore.add(LanguageManager.get(profile,
                "menus.auction.sold.item.price",
                "&8 - &fPrice: &b{price} Credits",
                "price", item.getCreditCost()));
        return lore;
    }

    private List<String> buildBoughtLore(Profile profile, AuctionItem item) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(LanguageManager.get(profile,
                "menus.auction.bought.item.buyer",
                "&8 - &fBuyer: &a{buyer}",
                "buyer", item.getBuyerName() == null ? "-" : item.getBuyerName()));
        lore.add(LanguageManager.get(profile,
                "menus.auction.bought.item.price",
                "&8 - &fPrice: &b{price} Credits",
                "price", item.getCreditCost()));
        return lore;
    }

    private void fillNavigationItems(Profile profile, Inventory inventory, int credit) {
        inventory.setItem(48, named(new ItemStack(Material.CHEST),
                LanguageManager.get(profile, "menus.auction.nav.sold.name", "&6&lMy Sales"),
                LanguageManager.getList(profile, "menus.auction.nav.sold.lore", defaultSoldLore())));

        inventory.setItem(49, named(MaterialResolver.createItemStack("DOUBLE_PLANT:0"),
                LanguageManager.get(profile, "menus.auction.nav.balance.name", "&eCredits: &6{credit}",
                        "credit", StringUtils.formatNumber(credit)),
                LanguageManager.getList(profile, "menus.auction.nav.balance.lore", Collections.emptyList())));

        inventory.setItem(50, named(new ItemStack(Material.HOPPER),
                LanguageManager.get(profile, "menus.auction.nav.bought.name", "&2&lMy Purchases"),
                LanguageManager.getList(profile, "menus.auction.nav.bought.lore", defaultBoughtLore())));
    }

    private static ItemStack named(ItemStack base, String name, List<String> lore) {
        ItemStack clone = base.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(StringUtils.formatColors(name));
            if (lore != null) {
                meta.setLore(formatColors(lore));
            }
            clone.setItemMeta(meta);
        }
        return clone;
    }

    private static List<String> formatColors(List<String> lore) {
        if (lore == null) {
            return null;
        }
        List<String> formatted = new ArrayList<>(lore.size());
        for (String line : lore) {
            formatted.add(StringUtils.formatColors(line));
        }
        return formatted;
    }

    private void send(Player player, String key, String def, Object... placeholders) {
        LanguageManager.send(player, key, def, placeholders);
    }

    private static List<String> defaultSoldLore() {
        return Arrays.asList(
                "",
                "&fView the auctions you have listed.",
                "",
                "&fListing an item is free of charge.",
                "&fAuctions can be purchased with credits",
                "&fon the website and in game.",
                "",
                "&8 - &f/auction &b<credits>",
                "",
                "&6&l> &eClick to view."
        );
    }

    private static List<String> defaultBoughtLore() {
        return Arrays.asList(
                "",
                "&fReview the auctions you have purchased.",
                "",
                "&2&l> &aClick to view."
        );
    }

    private enum MenuType {
        MAIN,
        SOLD,
        BOUGHT,
        PURCHASE
    }

    private static class Holder implements InventoryHolder {
        final Map<Integer, AuctionItem> map = new HashMap<>();
        AuctionItem pending;
        final MenuType type;

        Holder(MenuType type) {
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
