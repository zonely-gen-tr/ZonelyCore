package dev.zonely.whiteeffect.store;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.hologram.LastCreditHologramManager;
import dev.zonely.whiteeffect.libraries.menu.MenuInventoryFactory;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class LastCreditsMenuManager implements Listener {
    private static final int PAGE_SIZE = 36;
    private static final int ENTRIES_START_SLOT = 9;
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public enum Category {
        RECENT("recent", "Recent"),
        ALL_TIME("all-time", "Top (All Time)"),
        YEARLY("yearly", "Top (Yearly)"),
        MONTHLY("monthly", "Top (Monthly)"),
        DAILY("daily", "Top (Daily)");

        public final String key;
        public final String defTitle;

        Category(String key, String defTitle) {
            this.key = key;
            this.defTitle = defTitle;
        }
    }

    private final Core plugin;
    private final Map<UUID, MenuState> openMenus = new ConcurrentHashMap<>();
    private final Map<Category, Map<Integer, List<CreditEntry>>> cache = new ConcurrentHashMap<>();

    private static class MenuState {
        final Category category;
        final int page;
        final Map<Integer, Category> categorySlots;
        final int backSlot;
        final int prevSlot;
        final int nextSlot;

        MenuState(Category c, int p, Map<Integer, Category> categorySlots, int backSlot, int prevSlot, int nextSlot) {
            this.category = c;
            this.page = p;
            this.categorySlots = categorySlots;
            this.backSlot = backSlot;
            this.prevSlot = prevSlot;
            this.nextSlot = nextSlot;
        }
    }

    private static class CreditEntry {
        final int userId;
        final String username;
        final long total;
        final Timestamp createdAt;

        CreditEntry(int userId, String username, long total, Timestamp createdAt) {
            this.userId = userId;
            this.username = username;
            this.total = total;
            this.createdAt = createdAt;
        }
    }

    public LastCreditsMenuManager(Core plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        for (Category c : Category.values()) {
            cache.put(c, new ConcurrentHashMap<>());
            try {
                List<CreditEntry> firstPage = fetchEntries(c, 0, PAGE_SIZE);
                cache.get(c).put(0, firstPage);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to preload LastCredits for " + c, ex);
            }
        }
    }

    public void openMenu(Player p, Category cat, int page) {
        Profile profile = Profile.getProfile(p.getName());

        String catName = LanguageManager.get(profile,
                "menus.last-credits.categories." + cat.key + ".name",
                cat.defTitle);
        String title = LanguageManager.get(profile,
                "menus.last-credits.title",
                "&8Last Credits &0&l> &8{category} &7Page {page}",
                "category", catName,
                "page", page + 1);
        Inventory inv = MenuInventoryFactory.create(new LastCreditsHolder(cat, page), 54, title);

        LanguageManager.MenuItemDefinition backDef = LanguageManager.getMenuItem(profile,
                "menus.last-credits.items.back", 4,
                "BARRIER : 1 : name>&cBack");
        inv.setItem(backDef.slot(), BukkitUtils.deserializeItemStack(backDef.item()));

        LanguageManager.MenuItemDefinition prevDef = LanguageManager.getMenuItem(profile,
                "menus.last-credits.items.prev", 45,
                "ARROW : 1 : name>&e<-");
        LanguageManager.MenuItemDefinition nextDef = LanguageManager.getMenuItem(profile,
                "menus.last-credits.items.next", 53,
                "ARROW : 1 : name>&e->");

        if (page > 0) {
            inv.setItem(prevDef.slot(), BukkitUtils.deserializeItemStack(prevDef.item()));
        }

        Map<Integer, Category> catSlots = new HashMap<>();
        for (Category c : Category.values()) {
            String keyBase = "menus.last-credits.categories." + c.key;
            String cName = LanguageManager.get(profile, keyBase + ".name", c.defTitle);
            LanguageManager.MenuItemDefinition def = LanguageManager.getMenuItem(profile,
                    keyBase, categoryDefaultSlot(c),
                    "COMPASS : 1 : name>&f&l{category}",
                    "category", cName);
            String selected = LanguageManager.get(profile,
                    keyBase + ".selected-item",
                    "COMPASS : 1 : name>&a&l{category}",
                    "category", cName);
            ItemStack item = BukkitUtils.deserializeItemStack(c == cat ? selected : def.item());
            inv.setItem(def.slot(), item);
            catSlots.put(def.slot(), c);
        }

        List<CreditEntry> cached = cache.get(cat).get(page);
        if (cached != null) {
            for (int i = 0; i < cached.size(); i++) {
                inv.setItem(ENTRIES_START_SLOT + i, buildEntryItem(profile, cached.get(i), i));
            }
            if (cached.size() == PAGE_SIZE) {
                inv.setItem(nextDef.slot(), BukkitUtils.deserializeItemStack(nextDef.item()));
            }
        }

        p.openInventory(inv);
        openMenus.put(p.getUniqueId(), new MenuState(cat, page, catSlots, backDef.slot(), prevDef.slot(), nextDef.slot()));

        LastCreditHologramManager hologramManager = LastCreditHologramManager.getInstance();
        if (hologramManager != null) {
            hologramManager.setViewerCategory(p, cat);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<CreditEntry> fresh = fetchEntries(cat, page, PAGE_SIZE);
            cache.get(cat).put(page, fresh);

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int i = 0; i < fresh.size(); i++) {
                    inv.setItem(ENTRIES_START_SLOT + i, buildEntryItem(profile, fresh.get(i), i));
                }
                for (int i = fresh.size(); i < PAGE_SIZE; i++) {
                    inv.setItem(ENTRIES_START_SLOT + i, null);
                }
                inv.setItem(prevDef.slot(), page > 0 ? BukkitUtils.deserializeItemStack(prevDef.item()) : null);
                inv.setItem(nextDef.slot(), fresh.size() == PAGE_SIZE ? BukkitUtils.deserializeItemStack(nextDef.item()) : null);

                dev.zonely.whiteeffect.nms.util.SafeInventoryUpdater.update(p);
            });
        });
    }

    private ItemStack buildEntryItem(Profile profile, CreditEntry e, int index) {
        String template = LanguageManager.get(profile,
                "menus.last-credits.items.entry",
                "SKULL_ITEM:3 : 1 : name>&e&l#{position} &b{player} : desc>&8- &fLoaded: &a{amount} Credits\\n&8- &fDate: &b{date} : owner>{player}",
                "position", index + 1,
                "player", e.username,
                "amount", e.total,
                "date", DATE_FMT.format(e.createdAt));
        return BukkitUtils.deserializeItemStack(template);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof LastCreditsHolder)) {
            return;
        }
        ClickType click = e.getClick();
        Inventory clicked = e.getClickedInventory();
        Inventory top = e.getView().getTopInventory();
        boolean clickingTop = clicked != null && clicked.equals(top);
        boolean shift = e.isShiftClick();
        boolean doubleClick = click == ClickType.DOUBLE_CLICK;

        if (!clickingTop && !shift && !doubleClick) {
            return; 
        }

        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();

        if (!clickingTop) {
            if (shift || doubleClick) {
                try { dev.zonely.whiteeffect.nms.util.SafeInventoryUpdater.update(p); } catch (Throwable ignored) {}
            }
            return;
        }
        MenuState state = openMenus.get(p.getUniqueId());
        int slot = e.getRawSlot();
        if (state != null) {
            if (state.categorySlots.containsKey(slot)) {
                openMenu(p, state.categorySlots.get(slot), 0);
                return;
            }
            if (slot == state.prevSlot && state.page > 0) {
                openMenu(p, state.category, state.page - 1);
                return;
            }
            if (slot == state.nextSlot) {
                openMenu(p, state.category, state.page + 1);
                return;
            }
            if (slot == state.backSlot) {
                ProductMenuManager mgr = plugin.getProductMenuManager();
                mgr.openCategoryMenu(p, 0);
                return;
            }
        }
    }

    private int categoryDefaultSlot(Category c) {
        switch (c) {
            case RECENT: return 47;
            case ALL_TIME: return 48;
            case YEARLY: return 49;
            case MONTHLY: return 50;
            case DAILY: return 51;
            default: return 47;
        }
    }

    private List<CreditEntry> fetchEntries(Category cat, int page, int limit) {
        String clause;
        switch (cat) {
            case RECENT:
                clause = "ORDER BY lc.created_at DESC";
                break;
            case ALL_TIME:
                clause = "GROUP BY lc.userid ORDER BY SUM(lc.gain) DESC";
                break;
            case YEARLY:
                clause = "WHERE YEAR(lc.created_at)=YEAR(CURDATE()) GROUP BY lc.userid ORDER BY SUM(lc.gain) DESC";
                break;
            case MONTHLY:
                clause = "WHERE YEAR(lc.created_at)=YEAR(CURDATE()) AND MONTH(lc.created_at)=MONTH(CURDATE()) " +
                        "GROUP BY lc.userid ORDER BY SUM(lc.gain) DESC";
                break;
            case DAILY:
                clause = "WHERE DATE(lc.created_at)=CURDATE() GROUP BY lc.userid ORDER BY SUM(lc.gain) DESC";
                break;
            default:
                clause = "";
        }

        String sql;
        if (cat == Category.RECENT) {
            sql = "SELECT lc.userid, ul.nick AS username, lc.gain AS total, lc.created_at AS createdAt " +
                    "FROM lastcredits lc JOIN userslist ul ON lc.userid = ul.id " +
                    clause + " LIMIT ? OFFSET ?";
        } else {
            sql = "SELECT lc.userid, ul.nick AS username, SUM(lc.gain) AS total, MAX(lc.created_at) AS createdAt " +
                    "FROM lastcredits lc JOIN userslist ul ON lc.userid = ul.id " +
                    clause + " LIMIT ? OFFSET ?";
        }

        try (Connection conn = Database.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, page * limit);
            ResultSet rs = ps.executeQuery();
            List<CreditEntry> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new CreditEntry(
                        rs.getInt("userid"),
                        rs.getString("username"),
                        rs.getLong("total"),
                        rs.getTimestamp("createdAt")
                ));
            }
            return list;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "LastCredits fetch error", ex);
            return Collections.emptyList();
        }
    }
}
