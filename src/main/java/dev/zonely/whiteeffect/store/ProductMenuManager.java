package dev.zonely.whiteeffect.store;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import dev.zonely.whiteeffect.utils.StringUtils;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method; 
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@SuppressWarnings({"unused"})
public class ProductMenuManager implements Listener {

    private final Core plugin;

    public static final WConfig CONFIG = Core.getInstance().getConfig("config");
    public static String prefixCore;

    private final Map<Integer, Category> categoryCache = new ConcurrentHashMap<>();
    private final Map<Integer, List<Category>> childrenByParentCache = new ConcurrentHashMap<>();

    private final Map<Integer, List<ProductEntry>> productsByCategoryCache = new ConcurrentHashMap<>();
    private final Map<Integer, List<PacketEntry>> packetsByCategoryCache = new ConcurrentHashMap<>();
    private final Map<UUID, Double> creditCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<PurchasedItem>> purchaseCache = new ConcurrentHashMap<>();
    private final Map<UUID, FilterSettings> filterByPlayer = new ConcurrentHashMap<>();

    private static class FilterSettings {
        boolean popularOnly = false;
        int minPrice = 0;
        int maxPrice = Integer.MAX_VALUE;
    }

    private static final int INVENTORY_SIZE = 54;
    private static final int CONFIRM_SIZE = 27;
    private static final int PAGE_SIZE = 45;

    private volatile boolean dataLoaded = false;

    public ProductMenuManager(Core plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        String fallbackPrefix = CONFIG.getString("language.prefix.lobby", "&7[Store]&r ");
        prefixCore = ChatColor.translateAlternateColorCodes('&',
                LanguageManager.get("prefix.lobby", fallbackPrefix));

        new BukkitRunnable() {
            @Override
            public void run() {
                loadAllStoreDataOnce();
            }
        }.runTaskAsynchronously(plugin);
    }

    public boolean isDataLoaded() {
        return dataLoaded;
    }

    public void reload() {
        CONFIG.reload();
        String fallbackPrefix = CONFIG.getString("language.prefix.lobby", "&7[Store]&r ");
        prefixCore = ChatColor.translateAlternateColorCodes('&',
                LanguageManager.get("prefix.lobby", fallbackPrefix));
    }

    private static String tr(String key, String fallback, String... kv) {
        String out = LanguageManager.get(key, fallback);
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                String k = kv[i] == null ? "" : kv[i];
                String v = kv[i + 1] == null ? "" : kv[i + 1];
                out = out.replace("{" + k + "}", v);
                out = out.replace("%" + k + "%", v);
            }
        }
        return ChatColor.translateAlternateColorCodes('&', out);
    }
  
    private ItemStack applyCustomModelData(ItemStack item, String modelId) {
        if (item == null) return null;
        if (modelId == null || modelId.trim().isEmpty()) return item;
        try {
            int mid = Integer.parseInt(modelId.trim());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                try {
                    Method m = meta.getClass().getMethod("setCustomModelData", Integer.class);
                    m.invoke(meta, Integer.valueOf(mid));
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable ignored) {
                }
                item.setItemMeta(meta);
            }
        } catch (NumberFormatException ignored) {
        }
        return item;
    }

    private ItemStack iconFromConfig(String icon, String modelId, Material fallback) {
        ItemStack item = null;
        if (icon != null && !icon.trim().isEmpty()) {
            try {
                item = MaterialResolver.createItemStack(icon.trim());
            } catch (Throwable ignored) {
                Material m = Material.matchMaterial(icon.trim().toUpperCase());
                if (m != null) item = new ItemStack(m);
            }
        }
        if (item == null) item = new ItemStack(fallback != null ? fallback : Material.BOOK);
        return applyCustomModelData(item, modelId);
    }

    private ItemStack buildCategoryIcon(Category cat) {
        if (cat.guiIcon != null && !cat.guiIcon.trim().isEmpty()) {
            return iconFromConfig(cat.guiIcon, cat.guiModelId, Material.BOOK);
        }
        return new ItemStack(Material.BOOK);
    }

    private ItemStack buildSubProductIcon(SubProductDetails sp) {
        if (sp.zonelyIcon != null && !sp.zonelyIcon.trim().isEmpty()) {
            return iconFromConfig(sp.zonelyIcon, sp.zonelyModelId, Material.BOOK);
        }
        if (sp.imageUrl != null && !sp.imageUrl.trim().isEmpty()) {
            return new ItemStack(Material.PAINTING);
        }
        return new ItemStack(Material.BOOK);
    }

    private ItemStack buildProductIcon(ProductEntry pe) {
        if (pe.subProducts != null && !pe.subProducts.isEmpty()) {
            return buildSubProductIcon(pe.subProducts.get(0));
        }
        return new ItemStack(Material.CHEST);
    }


    private void loadAllStoreDataOnce() {
        Connection conn = null;
        try {
            if (Database.getInstance() == null) return;
            conn = Database.getInstance().getConnection();
            if (conn == null) return;

            Map<Integer, Category> tempCatMap = new HashMap<>();
            Map<Integer, List<Category>> tempChildrenMap = new HashMap<>();

            String sqlCat = "SELECT id, name, content, gui, parentID, row_order " +
                    "FROM productcategories " +
                    "ORDER BY parentID ASC, " +
                    "CASE WHEN row_order IS NULL THEN 1 ELSE 0 END, row_order ASC, id ASC";

            try (PreparedStatement psCat = conn.prepareStatement(sqlCat);
                 ResultSet rsCat = psCat.executeQuery()) {
                while (rsCat.next()) {
                    int id = rsCat.getInt("id");
                    String name = rsCat.getString("name");
                    String content = rsCat.getString("content");
                    String rawGui = rsCat.getString("gui");
                    int parentId = rsCat.getInt("parentID");
                    int rowOrder = rsCat.getObject("row_order") == null ? Integer.MAX_VALUE : rsCat.getInt("row_order");

                    boolean showInGame = false;
                    String guiTitle = "";
                    String guiDescription = "";
                    String guiIcon = "";
                    String guiModelId = "";

                    if (rawGui != null && !rawGui.trim().isEmpty()) {
                        try {
                            JSONObject guiObj = new JSONObject(rawGui);
                            showInGame = guiObj.optInt("showInGame", 0) == 1;
                            guiTitle = guiObj.optString("title", "");
                            guiDescription = guiObj.optString("description", "");
                            guiIcon = guiObj.optString("icon", "");
                            guiModelId = guiObj.optString("modelId", "");
                        } catch (JSONException je) {
                            plugin.getLogger().warning("GUI JSON parse error for category ID " + id + ": " + je.getMessage());
                        }
                    }

                    Category cat = new Category(
                            id,
                            parentId,
                            name,
                            content,
                            rowOrder,
                            showInGame,
                            guiTitle,
                            guiDescription,
                            guiIcon,
                            guiModelId
                    );

                    tempCatMap.put(id, cat);
                    tempChildrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(cat);
                }
            }

            for (List<Category> lst : tempChildrenMap.values()) {
                lst.sort((a, b) -> {
                    int ro = Integer.compare(a.rowOrder, b.rowOrder);
                    if (ro != 0) return ro;
                    return Integer.compare(a.id, b.id);
                });
            }

            categoryCache.clear();
            categoryCache.putAll(tempCatMap);

            childrenByParentCache.clear();
            childrenByParentCache.putAll(tempChildrenMap);

            String sqlProd = ""
                    + "SELECT p.id AS pid, p.plugin_data, p.name AS pname, p.price, p.discountedPrice, p.discountexpiry_at, "
                    + "p.paidFreeStatus, p.stock, p.details, p.slug, p.categoryID "
                    + "FROM products p "
                    + "WHERE p.productType = 'minecraft' "
                    + "ORDER BY p.row_order ASC, p.id DESC";
            Map<Integer, List<ProductEntry>> tempProdMap = new HashMap<>();
            try (PreparedStatement psProd = conn.prepareStatement(sqlProd);
                 ResultSet rsProd = psProd.executeQuery()) {

                while (rsProd.next()) {
                    int productId = rsProd.getInt("pid");
                    String jsonData = rsProd.getString("plugin_data");
                    String baseName = rsProd.getString("pname");
                    double basePrice = rsProd.getDouble("price");
                    double baseDiscount = rsProd.getDouble("discountedPrice");
                    String expiryStr = rsProd.getString("discountexpiry_at");
                    boolean paidFree = rsProd.getInt("paidFreeStatus") == 1;
                    int stockAll = rsProd.getInt("stock");
                    String rawDetails = rsProd.getString("details");
                    String slug = rsProd.getString("slug");
                    int catId = rsProd.getInt("categoryID");

                    List<SubProductDetails> subList = new ArrayList<>();
                    if (jsonData != null && !jsonData.trim().isEmpty()) {
                        try {
                            JSONArray arr = new JSONArray(jsonData);
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);

                                String spName = obj.optString("name", baseName);
                                String spDetails = obj.optString("details", rawDetails);

                                double spPrice;
                                Object rawPrice = obj.has("price") ? obj.get("price") : basePrice;
                                if (rawPrice instanceof Number) {
                                    spPrice = ((Number) rawPrice).doubleValue();
                                } else {
                                    try { spPrice = Double.parseDouble(rawPrice.toString()); }
                                    catch (NumberFormatException e) { spPrice = basePrice; }
                                }

                                double spDiscounted;
                                if (obj.has("discounted_price")) {
                                    Object rawDisc = obj.get("discounted_price");
                                    if (rawDisc instanceof Number) {
                                        spDiscounted = ((Number) rawDisc).doubleValue();
                                    } else {
                                        try { spDiscounted = Double.parseDouble(rawDisc.toString()); }
                                        catch (NumberFormatException e) { spDiscounted = 0.0; }
                                    }
                                } else {
                                    spDiscounted = 0.0;
                                }

                                String discountType = obj.optString("discount_type", "none");
                                String spExpiry = obj.optString("discount_expiry", "1000-01-01 00:00:00");
                                String stockType = obj.optString("stock_type", "unlimited");
                                int spStock = -1;
                                if ("limited".equalsIgnoreCase(stockType) && obj.has("stock")) {
                                    spStock = obj.optInt("stock", -1);
                                }

                                String imageUrl = obj.optString("image", "");
                                int saleDuration = obj.optInt("sale_duration", 0);

                                boolean showInGame = false;
                                String zonelyTitle = "";
                                String zonelyDescription = "";
                                String zonelyIcon = "";
                                String zonelyModelId = "";

                                if (obj.has("zonely")) {
                                    JSONObject zonelyObj = obj.optJSONObject("zonely");
                                    if (zonelyObj != null) {
                                        showInGame = zonelyObj.optInt("showInGame", 0) == 1;
                                        zonelyTitle = zonelyObj.optString("title", "");
                                        zonelyDescription = zonelyObj.optString("description", "");
                                        zonelyIcon = zonelyObj.optString("icon", "");
                                        zonelyModelId = zonelyObj.optString("modelId", "");
                                    }
                                }

                                SubProductDetails sp = new SubProductDetails(
                                        productId,
                                        spName,
                                        spDetails,
                                        spPrice,
                                        spDiscounted,
                                        discountType,
                                        spExpiry,
                                        stockType,
                                        spStock,
                                        imageUrl,
                                        saleDuration,
                                        i,
                                        showInGame,
                                        zonelyTitle,
                                        zonelyDescription,
                                        zonelyIcon,
                                        zonelyModelId);
                                subList.add(sp);
                            }
                        } catch (JSONException je) {
                            plugin.getLogger().warning("JSON parse error for PRODUCT ID " + productId + ": " + je.getMessage());
                        }
                    }
                    ProductEntry entry = new ProductEntry(
                            productId,
                            baseName,
                            rawDetails,
                            slug,
                            paidFree,
                            stockAll,
                            basePrice,
                            baseDiscount,
                            expiryStr,
                            subList);
                    tempProdMap.computeIfAbsent(catId, k -> new ArrayList<>()).add(entry);
                }
            }
            productsByCategoryCache.clear();
            productsByCategoryCache.putAll(tempProdMap);

            String sqlPkt = "SELECT id, name, products, price, expiry_at, productsStatus, category_id FROM productpackets WHERE productsStatus = 1";
            Map<Integer, List<PacketEntry>> tempPktMap = new HashMap<>();
            try (PreparedStatement psPkt = conn.prepareStatement(sqlPkt);
                 ResultSet rsPkt = psPkt.executeQuery()) {

                while (rsPkt.next()) {
                    int pktId = rsPkt.getInt("id");
                    String name = rsPkt.getString("name");
                    String rawProducts = rsPkt.getString("products");
                    double price = rsPkt.getDouble("price");
                    String expiry = rsPkt.getString("expiry_at");
                    int catId = rsPkt.getInt("category_id");

                    List<Integer> productIds = new ArrayList<>();
                    List<String> productNames = new ArrayList<>();

                    if (rawProducts != null && !rawProducts.trim().isEmpty()) {
                        try {
                            JSONArray arr = new JSONArray(rawProducts);
                            List<Integer> idsForQuery = new ArrayList<>();
                            for (int i = 0; i < arr.length(); i++) {
                                int pid = arr.optInt(i, -1);
                                if (pid >= 0) idsForQuery.add(pid);
                            }
                            if (!idsForQuery.isEmpty()) {
                                StringBuilder inClause = new StringBuilder();
                                for (int i = 0; i < idsForQuery.size(); i++) {
                                    inClause.append("?");
                                    if (i < idsForQuery.size() - 1) inClause.append(",");
                                }
                                String nameSQL = "SELECT id, name FROM products WHERE id IN (" + inClause + ")";
                                try (PreparedStatement ps2 = conn.prepareStatement(nameSQL)) {
                                    for (int i = 0; i < idsForQuery.size(); i++) {
                                        ps2.setInt(i + 1, idsForQuery.get(i));
                                    }
                                    try (ResultSet rs2 = ps2.executeQuery()) {
                                        Map<Integer, String> idToName = new HashMap<>();
                                        while (rs2.next()) {
                                            idToName.put(rs2.getInt("id"), rs2.getString("name"));
                                        }
                                        for (Integer pid : idsForQuery) {
                                            productIds.add(pid);
                                            productNames.add(idToName.getOrDefault(pid, "Product#" + pid));
                                        }
                                    }
                                }
                            }
                        } catch (JSONException je) {
                            plugin.getLogger().warning("JSON parse error for PACKET ID " + pktId + ": " + je.getMessage());
                        }
                    }

                    if (!productIds.isEmpty()) {
                        tempPktMap.computeIfAbsent(catId, k -> new ArrayList<>())
                                .add(new PacketEntry(pktId, name, price, expiry, productIds, productNames));
                    }
                }
            }
            packetsByCategoryCache.clear();
            packetsByCategoryCache.putAll(tempPktMap);

            dataLoaded = true;

        } catch (Exception ignored) {
        } finally {
            try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
        }
    }

    private static String stripHtmlStatic(String html) {
        if (html == null) return "";
        html = html.replaceAll("(?i)<br\\s*/?>", "\n");
        String noTags = html.replaceAll("<[^>]*>", "");
        noTags = noTags.replaceAll("\\s+", " ").trim();
        return noTags;
    }

    private String stripHtml(String html) {
        return stripHtmlStatic(html);
    }

    private ItemStack createCreditIcon(Player player) {
        UUID uuid = player.getUniqueId();
        if (creditCache.containsKey(uuid)) {
            double credits = creditCache.get(uuid);
            ItemStack item = MaterialResolver.createItemStack("DOUBLE_PLANT:0");
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(tr(
                    "store.product-menu.credits.title",
                    ChatColor.GOLD + "§eMy Credits: §6{amount}",
                    "amount", StringUtils.formatNumber(credits)
            ));
            item.setItemMeta(meta);
            return item;
        } else {
            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(LanguageManager.get(
                    "store.product-menu.loading",
                    ChatColor.GRAY + "Loading..."
            ));
            item.setItemMeta(meta);
            return item;
        }
    }

    private ItemStack createLastCreditsIcon(Player player) {
        List<String> lore = new ArrayList<>();
        ItemStack paper = new ItemStack(Material.BOOKSHELF);
        ItemMeta meta = paper.getItemMeta();
        meta.setDisplayName(LanguageManager.get(
                "store.product-menu.last-credits.title",
                ChatColor.GOLD + "§5§lRecent Credit Top-Ups"
        ));
        lore.add("");
        lore.add(LanguageManager.get(
                "store.product-menu.last-credits.l1",
                "§fYou can view players who topped up via our site here."
        ));
        lore.add(LanguageManager.get(
                "store.product-menu.last-credits.l2",
                "§fClick to see the list."
        ));
        lore.add("");
        lore.add(LanguageManager.get(
                "store.product-menu.click-to-view",
                ChatColor.GRAY + "§5§l➤ §dClick to view."
        ));
        meta.setLore(lore);
        paper.setItemMeta(meta);
        return paper;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();

        creditCache.remove(uuid);
        new BukkitRunnable() {
            @Override
            public void run() {
                double credits = 0.0;
                Connection conn = null;
                PreparedStatement ps = null;
                ResultSet rs = null;
                int webUserID = -1;

                try {
                    if (Database.getInstance() == null) return;
                    conn = Database.getInstance().getConnection();
                    if (conn == null) return;

                    String creditSQL = "SELECT id, credit FROM userslist WHERE nick = ?";
                    ps = conn.prepareStatement(creditSQL);
                    ps.setString(1, player.getName());
                    rs = ps.executeQuery();
                    if (rs.next()) {
                        webUserID = rs.getInt("id");
                        credits = rs.getDouble("credit");
                    }
                    rs.close();
                    ps.close();
                } catch (SQLException ignored) {
                } finally {
                    try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
                    try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
                    try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
                }

                creditCache.put(uuid, credits);

                if (webUserID > 0) {
                    Connection conn2 = null;
                    PreparedStatement ps2 = null;
                    ResultSet rs2 = null;
                    List<PurchasedItem> list = new ArrayList<>();

                    try {
                        conn2 = Database.getInstance().getConnection();
                        if (conn2 == null) return;

                        String buysSQL = "SELECT lb.productid, pl.minecraft AS orderIndex, lb.created_at " +
                                "FROM lastedbuys lb " +
                                "JOIN productlicenses pl " +
                                "  ON pl.productid = lb.productid AND pl.userid = lb.userid " +
                                "WHERE lb.userid = ? " +
                                "ORDER BY lb.created_at DESC";

                        ps2 = conn2.prepareStatement(buysSQL);
                        ps2.setInt(1, webUserID);
                        rs2 = ps2.executeQuery();

                        while (rs2.next()) {
                            int productId = rs2.getInt("productid");
                            int orderIndex = rs2.getInt("orderIndex");
                            Timestamp ts = rs2.getTimestamp("created_at");
                            LocalDateTime purchaseDate = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();

                            String pluginData = null;
                            {
                                PreparedStatement ps3 = null;
                                ResultSet rs3 = null;
                                try {
                                    String pdataSQL = "SELECT plugin_data FROM products WHERE id = ?";
                                    ps3 = conn2.prepareStatement(pdataSQL);
                                    ps3.setInt(1, productId);
                                    rs3 = ps3.executeQuery();
                                    if (rs3.next()) {
                                        pluginData = rs3.getString("plugin_data");
                                    }
                                } catch (SQLException ignored) {
                                } finally {
                                    try { if (rs3 != null) rs3.close(); } catch (SQLException ignored) {}
                                    try { if (ps3 != null) ps3.close(); } catch (SQLException ignored) {}
                                }
                            }

                            String subName = null;
                            String zonelyTitle = "";
                            String zonelyIcon = "";
                            String subDetails = "";

                            if (pluginData != null && !pluginData.trim().isEmpty()) {
                                try {
                                    JSONArray arr = new JSONArray(pluginData);
                                    for (int i = 0; i < arr.length(); i++) {
                                        JSONObject obj = arr.getJSONObject(i);
                                        if (obj.optInt("order", -1) == orderIndex) {
                                            subName = obj.optString("name", null);
                                            subDetails = obj.optString("details", "");
                                            JSONObject zonelyObj = obj.optJSONObject("zonely");
                                            if (zonelyObj != null) {
                                                zonelyTitle = zonelyObj.optString("title", "");
                                                zonelyIcon = zonelyObj.optString("icon", "");
                                            }
                                            break;
                                        }
                                    }
                                } catch (JSONException je) {
                                    plugin.getLogger().log(Level.WARNING,
                                            "plugin_data JSON parse error (productId=" + productId + "): " + je.getMessage());
                                }
                            }

                            if (subName == null) {
                                PreparedStatement ps4 = null;
                                ResultSet rs4 = null;
                                try {
                                    String nameSQL = "SELECT name, details FROM products WHERE id = ?";
                                    ps4 = conn2.prepareStatement(nameSQL);
                                    ps4.setInt(1, productId);
                                    rs4 = ps4.executeQuery();
                                    if (rs4.next()) {
                                        subName = rs4.getString("name");
                                        subDetails = rs4.getString("details");
                                    }
                                } catch (SQLException ignored) {
                                } finally {
                                    try { if (rs4 != null) rs4.close(); } catch (SQLException ignored) {}
                                    try { if (ps4 != null) ps4.close(); } catch (SQLException ignored) {}
                                }
                            }

                            if (subName == null) subName = "Unknown Product";

                            String plainDetails = stripHtml(subDetails);

                            PurchasedItem pi = new PurchasedItem(
                                    productId,
                                    subName,
                                    plainDetails,
                                    "",
                                    zonelyTitle,
                                    zonelyIcon,
                                    purchaseDate);
                            list.add(pi);
                        }
                        rs2.close();
                        ps2.close();
                    } catch (Exception ignored) {
                    } finally {
                        try { if (rs2 != null) rs2.close(); } catch (SQLException ignored) {}
                        try { if (ps2 != null) ps2.close(); } catch (SQLException ignored) {}
                        try { if (conn2 != null) conn2.close(); } catch (SQLException ignored) {}
                    }

                    purchaseCache.put(uuid, list);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public void openCategoryMenu(Player player, int page) {
        if (!dataLoaded) {
            player.sendMessage(tr(
                    "store.product-menu.data-not-ready",
                    "{prefix}&eStore data is still loading; please try again shortly.",
                    "prefix", prefixCore
            ));
            return;
        }

        List<Category> categories = getChildren(0);
        if (categories == null) categories = Collections.emptyList();

        int totalItems = categories.size();
        int calcTotalPages = (int) Math.ceil(totalItems / (double) PAGE_SIZE);
        if (calcTotalPages < 1) calcTotalPages = 1;

        int safePage = Math.max(0, Math.min(page, calcTotalPages - 1));

        int startIndex = safePage * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, totalItems);
        List<Category> subList = categories.subList(startIndex, endIndex);

        int itemCount = subList.size();
        if (itemCount == 0) {
            player.sendMessage(tr(
                    "store.product-menu.no-categories",
                    "{prefix}§cThere are no categories to display.",
                    "prefix", prefixCore
            ));
            return;
        }

        Map<Integer, Category> slotMap = new HashMap<>();
        Inventory inv = Bukkit.createInventory(
                new CategoryHolder(slotMap, safePage, calcTotalPages, null, false),
                INVENTORY_SIZE,
                LanguageManager.get(
                        "store.product-menu.inv.categories-title",
                        "§8Product Categories"
                )
        );

        fillCategoryGrid(inv, subList, slotMap);

        if (safePage > 0) inv.setItem(45, createNavArrow(false));
        if (safePage < calcTotalPages - 1) inv.setItem(53, createNavArrow(true));

        inv.setItem(49, createCreditIcon(player));
        inv.setItem(48, createLastCreditsIcon(player));
        inv.setItem(50, createPurchasedIcon(player));
        player.openInventory(inv);
    }

    public void openSubCategoryMenu(Player player, Category parent, int page) {
        if (!dataLoaded) {
            player.sendMessage(tr(
                    "store.product-menu.data-not-ready",
                    "{prefix}&eStore data is still loading; please try again shortly.",
                    "prefix", prefixCore
            ));
            return;
        }

        List<Category> children = getChildren(parent.id);
        if (children == null) children = Collections.emptyList();

        int totalItems = children.size();
        int calcTotalPages = (int) Math.ceil(totalItems / (double) PAGE_SIZE);
        if (calcTotalPages < 1) calcTotalPages = 1;

        int safePage = Math.max(0, Math.min(page, calcTotalPages - 1));

        int startIndex = safePage * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, totalItems);
        List<Category> subList = children.subList(startIndex, endIndex);

        if (subList.isEmpty()) {
            openProductMenu(player, parent, 0);
            return;
        }

        Map<Integer, Category> slotMap = new HashMap<>();
        Inventory inv = Bukkit.createInventory(
                new CategoryHolder(slotMap, safePage, calcTotalPages, parent, true),
                INVENTORY_SIZE,
                tr("store.product-menu.inv.subcategories-title",
                        "§8{category} Subcategories",
                        "category", resolveCategoryDisplay(parent))
        );

        fillCategoryGrid(inv, subList, slotMap);

        inv.setItem(47, createBackToCategoriesButtonForCategory(parent)); 
        if (safePage > 0) inv.setItem(45, createNavArrow(false));
        if (safePage < calcTotalPages - 1) inv.setItem(53, createNavArrow(true));

        inv.setItem(49, createCreditIcon(player));
        inv.setItem(48, createLastCreditsIcon(player));
        inv.setItem(50, createPurchasedIcon(player));
        player.openInventory(inv);
    }

    private void fillCategoryGrid(Inventory inv, List<Category> categories, Map<Integer, Category> slotMap) {
        int itemCount = categories.size();
        int rowsNeeded = (int) Math.ceil(itemCount / 9.0);
        int startRow = (6 - rowsNeeded) / 2;
        int idx = 0;

        for (int r = 0; r < rowsNeeded; r++) {
            int itemsInThisRow = Math.min(9, itemCount - r * 9);
            int baseStartCol = (9 - itemsInThisRow) / 2;

            for (int c = 0; c < itemsInThisRow; c++) {
                int col = (itemsInThisRow == 2) ? baseStartCol + (c * 2) : baseStartCol + c;
                int slot = (startRow + r) * 9 + col;
                Category cat = categories.get(idx);

                ItemStack item = buildCategoryIcon(cat);
                ItemMeta meta = item.getItemMeta();

                String title = (cat.guiTitle != null && !cat.guiTitle.trim().isEmpty()) ? cat.guiTitle : cat.name;
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', title));

                List<String> loreLines = new ArrayList<>();
                String descToUse = (cat.guiDescription != null && !cat.guiDescription.trim().isEmpty())
                        ? cat.guiDescription
                        : cat.contentPlain;
                if (descToUse != null && !descToUse.trim().isEmpty()) {
                    loreLines.add(ChatColor.GRAY + descToUse);
                }

                if (hasChildren(cat.id)) {
                    loreLines.add("");
                    loreLines.add(LanguageManager.get(
                            "store.product-menu.category.has-children",
                            ChatColor.AQUA + "Contains subcategories..."
                    ));
                }

                meta.setLore(loreLines);
                item.setItemMeta(meta);
                inv.setItem(slot, item);
                slotMap.put(slot, cat);

                idx++;
            }
        }
    }

    public void openProductMenu(Player player, Category category, int page) {
        if (!dataLoaded) {
            player.sendMessage(tr(
                    "store.product-menu.data-not-ready",
                    "{prefix}&eStore data is still loading; please try again shortly.",
                    "prefix", prefixCore
            ));
            return;
        }
        int categoryId = category.id;

        List<ProductEntry> products = productsByCategoryCache.get(categoryId);
        List<PacketEntry> packets = packetsByCategoryCache.get(categoryId);

        if (products == null) {
            if (hasChildren(categoryId)) {
                openSubCategoryMenu(player, category, 0);
                return;
            }

            player.sendMessage(tr(
                    "store.product-menu.no-products-for-category",
                    "{prefix}&cNo product data for this category.",
                    "prefix", prefixCore
            ));
            return;
        }
        if (packets == null) {
            packets = Collections.emptyList();
        }

        UUID uuid = player.getUniqueId();
        FilterSettings settings = filterByPlayer.computeIfAbsent(uuid, k -> new FilterSettings());

        List<ProductEntry> filteredProducts = new ArrayList<>();
        for (ProductEntry pe : products) {
            if (settings.popularOnly) {
                if (pe.subProducts.size() <= 1) continue;
            }
            double lowestPrice = Double.MAX_VALUE;
            for (SubProductDetails sp : pe.subProducts) {
                double price = sp.hasDiscount() ? sp.discountedPrice : sp.price;
                if (price < lowestPrice) lowestPrice = price;
            }
            if (lowestPrice < settings.minPrice || lowestPrice > settings.maxPrice) continue;
            filteredProducts.add(pe);
        }

        List<Object> combined = new ArrayList<>();
        combined.addAll(filteredProducts);
        combined.addAll(packets);

        int totalItems = combined.size();
        int calcTotalPages = (int) Math.ceil(totalItems / (double) PAGE_SIZE);
        if (calcTotalPages < 1) calcTotalPages = 1;

        int safePage = page;
        int safeTotalPages = calcTotalPages;
        if (safePage < 0) safePage = 0;
        if (safePage >= safeTotalPages) safePage = safeTotalPages - 1;

        int startIndex = safePage * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, totalItems);
        List<Object> pageList = combined.subList(startIndex, endIndex);

        int itemCount = pageList.size();
        if (itemCount == 0) {
            player.sendMessage(tr(
                    "store.product-menu.no-items-after-filter",
                    "{prefix}§cNo products or bundles to show (with current filters).",
                    "prefix", prefixCore
            ));
            return;
        }

        Map<Integer, ProductEntry> slotToProduct = new HashMap<>();
        Map<Integer, PacketEntry> slotToPacket = new HashMap<>();
        String categoryDisplay = resolveCategoryDisplay(category);
        Inventory inv = Bukkit.createInventory(
                new ProductHolder(slotToProduct, slotToPacket, category, safePage, safeTotalPages),
                INVENTORY_SIZE,
                tr(
                        "store.product-menu.inv.products-title",
                        "§8{category} Products",
                        "category", categoryDisplay
                )
        );

        ItemStack filterIcon = new ItemStack(Material.HOPPER);
        ItemMeta fMeta = filterIcon.getItemMeta();
        fMeta.setDisplayName(LanguageManager.get(
                "store.product-menu.filters.title",
                ChatColor.AQUA + "§lFilters"
        ));
        List<String> fLore = new ArrayList<>();
        fLore.add(LanguageManager.get(
                "store.product-menu.filters.hint",
                ChatColor.GRAY + "Click → Edit filter options"
        ));
        if (settings.popularOnly) {
            fLore.add(LanguageManager.get(
                    "store.product-menu.filters.flag-popular",
                    ChatColor.YELLOW + "- Most Popular"
            ));
        }
        if (settings.minPrice > 0 || settings.maxPrice < Integer.MAX_VALUE) {
            fLore.add(tr(
                    "store.product-menu.filters.flag-price",
                    ChatColor.YELLOW + "- Price: {min} - {max}",
                    "min", String.valueOf(settings.minPrice),
                    "max", (settings.maxPrice == Integer.MAX_VALUE ? "∞" : String.valueOf(settings.maxPrice))
            ));
        }
        if (fLore.size() == 1) {
            fLore.clear();
            fLore.add(LanguageManager.get(
                    "store.product-menu.filters.none",
                    ChatColor.GRAY + "No filters selected."
            ));
        }
        fMeta.setLore(fLore);
        filterIcon.setItemMeta(fMeta);
        inv.setItem(51, filterIcon);

        int rowsNeeded = (int) Math.ceil(itemCount / 9.0);
        int startRow = (6 - rowsNeeded) / 2;
        int indexGlobal = 0;
        for (int r = 0; r < rowsNeeded; r++) {
            int itemsInRow = Math.min(9, itemCount - r * 9);
            int startCol = (9 - itemsInRow) / 2;
            for (int c = 0; c < itemsInRow; c++) {
                int slot = (startRow + r) * 9 + (startCol + c);
                Object obj = pageList.get(indexGlobal);

                if (obj instanceof ProductEntry) {
                    ProductEntry pe = (ProductEntry) obj;
                    ItemStack item;
                    ItemMeta meta;

                    if (pe.subProducts.size() > 1) {
                        item = buildProductIcon(pe);
                        meta = item.getItemMeta();
                        StringBuilder allNames = new StringBuilder();
                        for (int k = 0; k < pe.subProducts.size(); k++) {
                            allNames.append(resolveSubProductTitle(pe.subProducts.get(k), pe));
                            if (k < pe.subProducts.size() - 1) allNames.append(", ");
                        }
                        meta.setDisplayName(ChatColor.YELLOW + "§l" + resolveSubProductTitle(pe.subProducts.get(0), pe) + " §7§l(" + allNames + ")");
                        List<String> lore = new ArrayList<>();
                        lore.add("");
                        lore.add(LanguageManager.get(
                                "store.product-menu.product.multiple-options",
                                ChatColor.WHITE + "Multiple options available, click to open the list."
                        ));
                        lore.add("");
                        int totalStock = pe.getTotalStock();
                        if (totalStock >= 0) {
                            lore.add(tr(
                                    "store.product-menu.stock.limited",
                                    ChatColor.RED + "§8 ▪ §fStock: §c{count} pcs",
                                    "count", String.valueOf(totalStock)
                            ));
                        } else {
                            lore.add(LanguageManager.get(
                                    "store.product-menu.stock.unlimited",
                                    ChatColor.GREEN + "§8 ▪ §fStock: §aUnlimited"
                            ));
                        }
                        if (pe.hasAnyDiscount()) {
                            lore.add(tr(
                                    "store.product-menu.price.old",
                                    ChatColor.RED + "§8 ▪ §fOld: §c{price} Credits",
                                    "price", pe.getDisplayOldPrice()
                            ));
                            lore.add(tr(
                                    "store.product-menu.price.new",
                                    ChatColor.GREEN + "§8 ▪ §fNew: §a{price} Credits",
                                    "price", pe.getDisplayNewPrice()
                            ));
                        } else {
                            lore.add(tr(
                                    "store.product-menu.price.single",
                                    ChatColor.GOLD + "§8 ▪ §fPrice: §e{price} Credits",
                                    "price", pe.getDisplayOldPrice()
                            ));
                        }
                        lore.add("");
                        lore.add(LanguageManager.get(
                                "store.product-menu.click-to-view",
                                ChatColor.GRAY + "§6§l➤ §eClick to view."
                        ));
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    } else {
                        SubProductDetails sp = pe.subProducts.get(0);
                        item = buildSubProductIcon(sp);
                        meta = item.getItemMeta();
                        meta.setDisplayName(resolveSubProductTitle(sp, pe));
                        List<String> lore = new ArrayList<>();
                        String descToUse = (sp.zonelyDescription != null && !sp.zonelyDescription.trim().isEmpty())
                                ? sp.zonelyDescription
                                : stripHtml(sp.details);
                        if (!descToUse.trim().isEmpty()) lore.add(ChatColor.GRAY + descToUse);
                        if (sp.stock >= 0) {
                            lore.add(tr(
                                    "store.product-menu.stock.limited",
                                    ChatColor.RED + "§8 ▪ §fStock: §c{count} pcs",
                                    "count", String.valueOf(sp.stock)
                            ));
                        } else {
                            lore.add(LanguageManager.get(
                                    "store.product-menu.stock.unlimited",
                                    ChatColor.GREEN + "§8 ▪ §fStock: §aUnlimited"
                            ));
                        }
                        if (sp.hasDiscount()) {
                            lore.add(tr(
                                    "store.product-menu.price.old",
                                    ChatColor.RED + "§8 ▪ §fOld: §c{price} Credits",
                                    "price", pe.formatPrice(sp.price)
                            ));
                            lore.add(tr(
                                    "store.product-menu.price.new",
                                    ChatColor.GREEN + "§8 ▪ §fNew: §a{price} Credits",
                                    "price", pe.formatPrice(sp.discountedPrice)
                            ));
                        } else {
                            lore.add(tr(
                                    "store.product-menu.price.single",
                                    ChatColor.GOLD + "§8 ▪ §fPrice: §e{price} Credits",
                                    "price", pe.formatPrice(sp.price)
                            ));
                        }
                        lore.add(LanguageManager.get(
                                "store.product-menu.click-to-view",
                                ChatColor.GRAY + "§6§l➤ §eClick to view."
                        ));
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    }

                    inv.setItem(slot, item);
                    slotToProduct.put(slot, pe);

                } else if (obj instanceof PacketEntry) {
                    PacketEntry pkt = (PacketEntry) obj;
                    ItemStack item = new ItemStack(Material.DIAMOND);
                    ItemMeta meta = item.getItemMeta();
                    meta.setDisplayName(ChatColor.LIGHT_PURPLE + "§l" + pkt.name + " §7§l(Bundle)");
                    List<String> lore = new ArrayList<>();
                    lore.add("");
                    lore.add(tr(
                            "store.product-menu.price.single",
                            ChatColor.GRAY + "§8 ▪ §fPrice: §e{price} Credits",
                            "price", String.format("%.0f", pkt.price)
                    ));
                    lore.add(tr(
                            "store.product-menu.bundle.contents",
                            ChatColor.GRAY + "§8 ▪ §fContents: {names}",
                            "names", String.join(", ", pkt.productNames)
                    ));
                    lore.add(tr(
                            "store.product-menu.bundle.expiry",
                            ChatColor.GRAY + "§8 ▪ §fExpiry: {date}",
                            "date", pkt.expiry
                    ));
                    lore.add("");
                    lore.add(LanguageManager.get(
                            "store.product-menu.click-to-buy-bundle",
                            ChatColor.GRAY + "§6§l➤ §eClick to purchase the bundle."
                    ));
                    meta.setLore(lore);
                    item.setItemMeta(meta);

                    inv.setItem(slot, item);
                    slotToPacket.put(slot, pkt);
                }
                indexGlobal++;
            }
        }

        inv.setItem(47, createBackToCategoriesButtonForCategory(category));
        if (safePage > 0) inv.setItem(45, createNavArrow(false));
        if (safePage < safeTotalPages - 1) inv.setItem(53, createNavArrow(true));
        inv.setItem(49, createCreditIcon(player));
        inv.setItem(48, createLastCreditsIcon(player));
        inv.setItem(50, createPurchasedIcon(player));

        player.openInventory(inv);
    }

    private void openFilterMenu(Player player, Category category) {
        UUID uuid = player.getUniqueId();
        FilterSettings settings = filterByPlayer.computeIfAbsent(uuid, k -> new FilterSettings());

        Inventory inv = Bukkit.createInventory(
                new FilterHolder(category),
                CONFIRM_SIZE,
                tr(
                        "store.product-menu.inv.filters-title",
                        "§8{category} Filters",
                        "category", category.name
                )
        );

        ItemStack popIcon = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta pMeta = popIcon.getItemMeta();
        pMeta.setDisplayName(
                settings.popularOnly
                        ? LanguageManager.get("store.product-menu.filters.popular-on", ChatColor.GREEN + "✓ Most Popular Only")
                        : LanguageManager.get("store.product-menu.filters.popular-off", ChatColor.RED + "✗ Most Popular Only")
        );
        pMeta.setLore(Collections.singletonList(
                LanguageManager.get("store.product-menu.filters.popular-desc",
                        ChatColor.GRAY + "Show only products with multiple options.")
        ));
        popIcon.setItemMeta(pMeta);
        inv.setItem(10, popIcon);

        ItemStack price0_100 = new ItemStack(Material.EMERALD);
        ItemMeta prMeta1 = price0_100.getItemMeta();
        prMeta1.setDisplayName(
                (settings.minPrice == 0 && settings.maxPrice == 100)
                        ? LanguageManager.get("store.product-menu.filters.price.0-100.on", ChatColor.GREEN + "✓ Price: 0 - 100")
                        : LanguageManager.get("store.product-menu.filters.price.0-100.off", ChatColor.YELLOW + "Price: 0 - 100")
        );
        price0_100.setItemMeta(prMeta1);
        inv.setItem(12, price0_100);

        ItemStack price100_500 = new ItemStack(Material.EMERALD);
        ItemMeta prMeta2 = price100_500.getItemMeta();
        prMeta2.setDisplayName(
                (settings.minPrice == 100 && settings.maxPrice == 500)
                        ? LanguageManager.get("store.product-menu.filters.price.100-500.on", ChatColor.GREEN + "✓ Price: 100 - 500")
                        : LanguageManager.get("store.product-menu.filters.price.100-500.off", ChatColor.YELLOW + "Price: 100 - 500")
        );
        price100_500.setItemMeta(prMeta2);
        inv.setItem(13, price100_500);

        ItemStack price500up = new ItemStack(Material.EMERALD);
        ItemMeta prMeta3 = price500up.getItemMeta();
        prMeta3.setDisplayName(
                (settings.minPrice == 500 && settings.maxPrice == Integer.MAX_VALUE)
                        ? LanguageManager.get("store.product-menu.filters.price.500+.on", ChatColor.GREEN + "✓ Price: 500+")
                        : LanguageManager.get("store.product-menu.filters.price.500+.off", ChatColor.YELLOW + "Price: 500+")
        );
        price500up.setItemMeta(prMeta3);
        inv.setItem(14, price500up);

        ItemStack resetIcon = new ItemStack(Material.BARRIER);
        ItemMeta rMeta = resetIcon.getItemMeta();
        rMeta.setDisplayName(LanguageManager.get("store.product-menu.filters.reset", ChatColor.RED + "Reset Filters"));
        resetIcon.setItemMeta(rMeta);
        inv.setItem(16, resetIcon);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bMeta = back.getItemMeta();
        bMeta.setDisplayName(LanguageManager.get("store.common.back-arrow", ChatColor.YELLOW + "<--"));
        back.setItemMeta(bMeta);
        inv.setItem(22, back);

        player.openInventory(inv);
    }

    private static class FilterHolder implements InventoryHolder {
        final Category category;
        public FilterHolder(Category category) { this.category = category; }
        @Override public Inventory getInventory() { return null; }
    }

    private void openSubProductMenu(Player player, Category category, ProductEntry entry, int page) {
        List<SubProductDetails> subs = entry.subProducts;
        if (subs == null || subs.isEmpty()) {
            player.sendMessage(tr(
                    "store.product-menu.no-subproducts",
                    "{prefix}§cNo variants found for this product.",
                    "prefix", prefixCore
            ));
            return;
        }

        int totalItems = subs.size();
        int calcTotalPages = (int) Math.ceil(totalItems / (double) PAGE_SIZE);
        if (calcTotalPages < 1) calcTotalPages = 1;

        int safePage = page;
        int safeTotalPages = calcTotalPages;
        if (safePage < 0) safePage = 0;
        if (safePage >= safeTotalPages) safePage = safeTotalPages - 1;

        int startIndex = safePage * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, totalItems);
        List<SubProductDetails> pageList = subs.subList(startIndex, endIndex);

        int itemCount = pageList.size();
        if (itemCount == 0) {
            player.sendMessage(tr(
                    "store.product-menu.no-subproducts-page",
                    "{prefix}§cNo options found!",
                    "prefix", prefixCore
            ));
            return;
        }

        Map<Integer, SubProductDetails> slotToSub = new HashMap<>();
        String productDisplay = resolveSubProductTitle(subs.get(0), entry);
        Inventory inv = Bukkit.createInventory(
                new SubProductHolder(category, entry, slotToSub, safePage, safeTotalPages),
                INVENTORY_SIZE,
                tr(
                        "store.product-menu.inv.subproducts-title",
                        "§8{product} Variants",
                        "product", productDisplay
                )
        );

        int rowsNeeded = (int) Math.ceil(itemCount / 9.0);
        int startRow = (6 - rowsNeeded) / 2;
        int idx = 0;

        for (int r = 0; r < rowsNeeded; r++) {
            int itemsInRow = Math.min(9, itemCount - r * 9);
            int baseStartCol = (9 - itemsInRow) / 2;

            for (int c = 0; c < itemsInRow; c++) {
                int col = (itemsInRow == 2) ? baseStartCol + (c * 2) : baseStartCol + c;
                int slot = (startRow + r) * 9 + col;
                SubProductDetails sp = pageList.get(idx);

                ItemStack item = buildSubProductIcon(sp); 
                ItemMeta meta = item.getItemMeta();

                meta.setDisplayName(resolveSubProductTitle(sp, entry));

                List<String> lore = new ArrayList<>();
                String descToUse = (sp.zonelyDescription != null && !sp.zonelyDescription.trim().isEmpty())
                        ? sp.zonelyDescription
                        : stripHtml(sp.details);
                if (!descToUse.trim().isEmpty()) {
                    lore.add(ChatColor.WHITE + descToUse);
                }
                lore.add("");
                if (sp.stock >= 0) {
                    lore.add(tr(
                            "store.product-menu.stock.limited",
                            ChatColor.RED + "§8 ▪ §fStock: §c{count} pcs",
                            "count", String.valueOf(sp.stock)
                    ));
                } else {
                    lore.add(LanguageManager.get(
                            "store.product-menu.stock.unlimited",
                            ChatColor.GREEN + "§8 ▪ §fStock: §aUnlimited"
                    ));
                }
                if (sp.hasDiscount()) {
                    lore.add(tr(
                            "store.product-menu.price.old",
                            ChatColor.RED + "§8 ▪ §fOld: §c{price} Credits",
                            "price", entry.formatPrice(sp.price)
                    ));
                    lore.add(tr(
                            "store.product-menu.price.new",
                            ChatColor.GREEN + "§8 ▪ §fNew: §a{price} Credits",
                            "price", entry.formatPrice(sp.discountedPrice)
                    ));
                } else {
                    lore.add(tr(
                            "store.product-menu.price.single",
                            ChatColor.GOLD + "§8 ▪ §fPrice: §e{price} Credits",
                            "price", entry.formatPrice(sp.price)
                    ));
                }

                lore.add("");
                lore.add(LanguageManager.get(
                        "store.product-menu.click-to-buy",
                        ChatColor.GRAY + "§6§l➤ §eClick to purchase."
                ));
                meta.setLore(lore);
                item.setItemMeta(meta);

                inv.setItem(slot, item);
                slotToSub.put(slot, sp);

                idx++;
            }
        }

        inv.setItem(47, createBackToProductsButton(entry));
        if (safePage > 0) inv.setItem(45, createNavArrow(false));
        if (safePage < safeTotalPages - 1) inv.setItem(53, createNavArrow(true));
        inv.setItem(49, createCreditIcon(player));
        inv.setItem(48, createLastCreditsIcon(player));
        inv.setItem(50, createPurchasedIcon(player));
        player.openInventory(inv);
    }

    private void openConfirmationMenu(Player player, ProductEntry entry, SubProductDetails sp) {
        String displayName = resolveSubProductTitle(sp, entry);
        Inventory inv = Bukkit.createInventory(
                new ConfirmationHolder(player, new PendingPurchase(entry, sp, null)),
                CONFIRM_SIZE,
                tr(
                        "store.product-menu.inv.confirm-title",
                        "§8Purchasing: {name}",
                        "name", displayName
                )
        );

        ItemStack cancel = MaterialResolver.createItemStack("STAINED_CLAY:14");
        ItemMeta cm = cancel.getItemMeta();
        cm.setDisplayName(LanguageManager.get("store.common.cancel", ChatColor.RED + "Cancel"));
        cancel.setItemMeta(cm);
        inv.setItem(11, cancel);

        ItemStack accept = MaterialResolver.createItemStack("STAINED_CLAY:13");
        ItemMeta am = accept.getItemMeta();
        am.setDisplayName(LanguageManager.get("store.common.confirm", ChatColor.GREEN + "Confirm"));
        accept.setItemMeta(am);
        inv.setItem(15, accept);

        player.openInventory(inv);
    }

    private void openPacketConfirmationMenu(Player player, PacketEntry pkt) {
        Inventory inv = Bukkit.createInventory(
                new ConfirmationHolder(player, new PendingPurchase(null, null, pkt)),
                CONFIRM_SIZE,
                tr(
                        "store.product-menu.inv.confirm-bundle-title",
                        "§8Purchasing: {name} (Bundle)",
                        "name", pkt.name
                )
        );

        ItemStack cancel = MaterialResolver.createItemStack("STAINED_CLAY:14");
        ItemMeta cm = cancel.getItemMeta();
        cm.setDisplayName(LanguageManager.get("store.common.cancel", ChatColor.RED + "Cancel"));
        cancel.setItemMeta(cm);
        inv.setItem(11, cancel);

        ItemStack accept = MaterialResolver.createItemStack("STAINED_CLAY:13");
        ItemMeta am = accept.getItemMeta();
        am.setDisplayName(LanguageManager.get("store.common.confirm", ChatColor.GREEN + "Confirm"));
        accept.setItemMeta(am);
        inv.setItem(15, accept);

        player.openInventory(inv);
    }

    private String resolveCategoryDisplay(Category category) {
        if (category == null) return "Category";
        String raw = (category.guiTitle != null && !category.guiTitle.trim().isEmpty())
                ? category.guiTitle
                : category.name;
        if (raw == null || raw.trim().isEmpty()) raw = "Category";
        return colorize(raw);
    }

    private String resolveSubProductTitle(SubProductDetails sp, ProductEntry parent) {
        if (sp != null) {
            if (sp.zonelyTitle != null && !sp.zonelyTitle.trim().isEmpty()) {
                return colorize(sp.zonelyTitle);
            }
            if (sp.name != null && !sp.name.trim().isEmpty()) {
                return colorize(sp.name);
            }
        }
        if (parent != null && parent.baseName != null && !parent.baseName.trim().isEmpty()) {
            return colorize(parent.baseName);
        }
        return "Product";
    }

    private String colorize(String raw) {
        if (raw == null) return "";
        return raw.indexOf('§') >= 0 ? raw : ChatColor.translateAlternateColorCodes('&', raw);
    }

    private void purchaseProductForPlayer(Player player, ProductEntry entry, SubProductDetails sp) {
        player.closeInventory();
        player.sendMessage(tr(
                "store.product-menu.purchase.start",
                "{prefix}§aYour purchase has started, please wait a few seconds...",
                "prefix", prefixCore
        ));

        new BukkitRunnable() {
            @Override
            public void run() {
                Connection conn = null;
                PreparedStatement ps = null;
                ResultSet rs = null;
                try {
                    if (Database.getInstance() == null) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                                "store.product-menu.errors.db-lost",
                                "{prefix}§cDatabase connection lost; purchase could not be completed.",
                                "prefix", prefixCore
                        )));
                        return;
                    }
                    conn = Database.getInstance().getConnection();
                    if (conn == null) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                                "store.product-menu.errors.db-open",
                                "{prefix}§cCould not establish database connection; purchase failed.",
                                "prefix", prefixCore
                        )));
                        return;
                    }
                    conn.setAutoCommit(false);

                    String findUserSQL = "SELECT id FROM userslist WHERE nick = ?";
                    ps = conn.prepareStatement(findUserSQL);
                    ps.setString(1, player.getName());
                    rs = ps.executeQuery();
                    if (!rs.next()) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                                "store.product-menu.errors.web-user-missing",
                                "{prefix}§cYour web account was not found; purchase could not be completed.",
                                "prefix", prefixCore
                        )));
                        conn.rollback();
                        return;
                    }
                    int webUserID = rs.getInt("id");
                    rs.close();
                    ps.close();

                    String creditSQL = "SELECT credit FROM userslist WHERE id = ?";
                    ps = conn.prepareStatement(creditSQL);
                    ps.setInt(1, webUserID);
                    rs = ps.executeQuery();
                    if (!rs.next()) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                                "store.product-menu.errors.user-missing",
                                "{prefix}§cUser record not found; purchase could not be completed.",
                                "prefix", prefixCore
                        )));
                        conn.rollback();
                        return;
                    }
                    double credit = rs.getDouble("credit");
                    rs.close();
                    ps.close();

                    double price = sp.hasDiscount() ? sp.discountedPrice : sp.price;
                    if (credit < price) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                                "store.product-menu.errors.insufficient-credits",
                                "{prefix}§cInsufficient credits; purchase cancelled.",
                                "prefix", prefixCore
                        )));
                        conn.rollback();
                        return;
                    }

                    String insertLastedBuys = "INSERT INTO lastedbuys (userid, productid, price, created_at) VALUES (?, ?, ?, ?)";
                    ps = conn.prepareStatement(insertLastedBuys);
                    ps.setInt(1, webUserID);
                    ps.setInt(2, entry.productId);
                    ps.setDouble(3, price);
                    ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                    ps.executeUpdate();
                    ps.close();

                    String updateCredit = "UPDATE userslist SET credit = credit - ? WHERE id = ?";
                    ps = conn.prepareStatement(updateCredit);
                    ps.setDouble(1, price);
                    ps.setInt(2, webUserID);
                    ps.executeUpdate();
                    ps.close();

                    String insertProdLic = "INSERT INTO productlicenses (userid, productid, status, minecraft, created_at) VALUES (?, ?, ?, ?, ?)";
                    ps = conn.prepareStatement(insertProdLic, Statement.RETURN_GENERATED_KEYS);
                    ps.setInt(1, webUserID);
                    ps.setInt(2, entry.productId);
                    ps.setString(3, "0");
                    ps.setInt(4, sp.orderIndex);
                    ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                    ps.executeUpdate();

                    rs = ps.getGeneratedKeys();
                    if (!rs.next()) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                                "store.product-menu.errors.license-create",
                                "{prefix}§cProduct could not be created; purchase half-completed.",
                                "prefix", prefixCore
                        )));
                        conn.rollback();
                        return;
                    }
                    int productLicenseId = rs.getInt(1);
                    rs.close();
                    ps.close();

                    String insertLicense = "INSERT INTO licenses (productlicenses_id, userid, productid, domainAdress, licenseIP, suspendStatus, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    ps = conn.prepareStatement(insertLicense, Statement.RETURN_GENERATED_KEYS);
                    ps.setInt(1, productLicenseId);
                    ps.setInt(2, webUserID);
                    ps.setInt(3, entry.productId);
                    ps.setString(4, "");
                    ps.setString(5, "");
                    ps.setInt(6, 0);
                    ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
                    ps.executeUpdate();

                    rs = ps.getGeneratedKeys();
                    if (!rs.next()) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                                "store.product-menu.errors.license-record-create",
                                "{prefix}§cLicense record could not be created; purchase half-completed.",
                                "prefix", prefixCore
                        )));
                        conn.rollback();
                        return;
                    }
                    int licenseId = rs.getInt(1);
                    rs.close();
                    ps.close();

                    if (sp.stock >= 0) {
                        String getDataSQL = "SELECT plugin_data FROM products WHERE id = ? FOR UPDATE";
                        ps = conn.prepareStatement(getDataSQL);
                        ps.setInt(1, entry.productId);
                        rs = ps.executeQuery();
                        if (rs.next()) {
                            String pluginData = rs.getString("plugin_data");
                            rs.close();
                            ps.close();

                            JSONArray arr = new JSONArray(pluginData);
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                int order = obj.optInt("order", -1);
                                if (order == sp.orderIndex) {
                                    int oldStock = obj.optInt("stock", -1);
                                    if (oldStock > 0) obj.put("stock", oldStock - 1);
                                    break;
                                }
                            }

                            String updateDataSQL = "UPDATE products SET plugin_data = ? WHERE id = ?";
                            ps = conn.prepareStatement(updateDataSQL);
                            ps.setString(1, arr.toString());
                            ps.setInt(2, entry.productId);
                            ps.executeUpdate();
                            ps.close();
                        } else {
                            rs.close();
                            ps.close();
                        }
                    }

                    String updateTotalStart = "UPDATE licenses SET totalStart = totalStart + 1 WHERE id = ?";
                    ps = conn.prepareStatement(updateTotalStart);
                    ps.setInt(1, licenseId);
                    ps.executeUpdate();
                    ps.close();

                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime expiryDate = (sp.saleDuration > 0) ? now.plusDays(sp.saleDuration) : now;
                    String insertExpiry = "INSERT INTO expiries (licenseID, expiry_at, startDate) VALUES (?, ?, ?)";
                    ps = conn.prepareStatement(insertExpiry);
                    ps.setInt(1, licenseId);
                    ps.setTimestamp(2, Timestamp.valueOf(expiryDate));
                    ps.setTimestamp(3, Timestamp.valueOf(now));
                    ps.executeUpdate();
                    ps.close();

                    conn.commit();

                    sendCommandsToMinecraft(entry.productId, sp.orderIndex, player);

                    Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(
                            LanguageManager.get("store.product-menu.purchase.success",
                                    ChatColor.GREEN + "Purchase completed successfully!")
                    ));

                    UUID uuid = player.getUniqueId();
                    creditCache.put(uuid, credit - price);
                    purchaseCache.computeIfAbsent(uuid, k -> new ArrayList<>())
                            .add(new PurchasedItem(
                                    entry.productId,
                                    sp.name,
                                    stripHtml(sp.details),
                                    sp.imageUrl,
                                    sp.zonelyTitle,
                                    sp.zonelyIcon,
                                    LocalDateTime.now()));

                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Purchase error:", ex);
                    try { if (conn != null) conn.rollback(); } catch (SQLException ignored) {}
                    Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                            "store.product-menu.purchase.unexpected-error",
                            "{prefix}§cAn unexpected error occurred during purchase.",
                            "prefix", prefixCore
                    )));
                } finally {
                    try {
                        if (rs != null) rs.close();
                        if (ps != null) ps.close();
                        if (conn != null) conn.setAutoCommit(true);
                    } catch (SQLException ignored) {}
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void purchasePacketForPlayer(Player player, PacketEntry pkt) {
        player.closeInventory();
        player.sendMessage(tr(
                "store.product-menu.bundle.start",
                "{prefix}§aYour bundle purchase has started, please wait a few seconds...",
                "prefix", prefixCore
        ));

        new BukkitRunnable() {
            @Override
            public void run() {
                Connection conn = null;
                PreparedStatement ps = null;
                ResultSet rs = null;
                try {
                    if (Database.getInstance() == null) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                                "store.product-menu.errors.db-lost",
                                "{prefix}§cDatabase connection lost; bundle purchase failed.",
                                "prefix", prefixCore
                        )));
                        return;
                    }
                    conn = Database.getInstance().getConnection();
                    if (conn == null) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                                "store.product-menu.errors.db-open",
                                "{prefix}§cCould not establish database connection; bundle purchase failed.",
                                "prefix", prefixCore
                        )));
                        return;
                    }
                    conn.setAutoCommit(false);

                    String findUserSQL = "SELECT id FROM userslist WHERE nick = ?";
                    ps = conn.prepareStatement(findUserSQL);
                    ps.setString(1, player.getName());
                    rs = ps.executeQuery();
                    if (!rs.next()) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                                "store.product-menu.errors.web-user-missing",
                                "{prefix}§cYour web account was not found; bundle purchase could not be completed.",
                                "prefix", prefixCore
                        )));
                        conn.rollback();
                        return;
                    }
                    int webUserID = rs.getInt("id");
                    rs.close();
                    ps.close();

                    String creditSQL = "SELECT credit FROM userslist WHERE id = ?";
                    ps = conn.prepareStatement(creditSQL);
                    ps.setInt(1, webUserID);
                    rs = ps.executeQuery();
                    if (!rs.next()) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                                "store.product-menu.errors.user-missing",
                                "{prefix}§cUser record not found; bundle purchase could not be completed.",
                                "prefix", prefixCore
                        )));
                        conn.rollback();
                        return;
                    }
                    double credit = rs.getDouble("credit");
                    rs.close();
                    ps.close();

                    double totalPrice = pkt.price;
                    if (credit < totalPrice) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                                "store.product-menu.errors.insufficient-credits",
                                "{prefix}§cInsufficient credits; bundle purchase cancelled.",
                                "prefix", prefixCore
                        )));
                        conn.rollback();
                        return;
                    }

                    String updateCredit = "UPDATE userslist SET credit = credit - ? WHERE id = ?";
                    ps = conn.prepareStatement(updateCredit);
                    ps.setDouble(1, totalPrice);
                    ps.setInt(2, webUserID);
                    ps.executeUpdate();
                    ps.close();

                    for (Integer pid : pkt.productIds) {
                        String insertProdLic = "INSERT INTO productlicenses (userid, productid, status, minecraft, created_at) VALUES (?, ?, ?, ?, ?)";
                        ps = conn.prepareStatement(insertProdLic, Statement.RETURN_GENERATED_KEYS);
                        ps.setInt(1, webUserID);
                        ps.setInt(2, pid);
                        ps.setString(3, "0");
                        ps.setInt(4, 0);
                        ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                        ps.executeUpdate();

                        rs = ps.getGeneratedKeys();
                        if (!rs.next()) {
                            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                                    "store.product-menu.errors.bundle-license-fail",
                                    "{prefix}§cA bundle license could not be inserted; operation half-completed.",
                                    "prefix", prefixCore
                            )));
                            conn.rollback();
                            return;
                        }
                        int productLicenseId = rs.getInt(1);
                        rs.close();
                        ps.close();

                        String insertLicense = "INSERT INTO licenses (productlicenses_id, userid, productid, domainAdress, licenseIP, suspendStatus, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
                        ps = conn.prepareStatement(insertLicense, Statement.RETURN_GENERATED_KEYS);
                        ps.setInt(1, productLicenseId);
                        ps.setInt(2, webUserID);
                        ps.setInt(3, pid);
                        ps.setString(4, "");
                        ps.setString(5, "");
                        ps.setInt(6, 0);
                        ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
                        ps.executeUpdate();

                        rs = ps.getGeneratedKeys();
                        if (!rs.next()) {
                            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                                    "store.product-menu.errors.bundle-license-record-fail",
                                    "{prefix}§cA bundle license record could not be created; operation half-completed.",
                                    "prefix", prefixCore
                            )));
                            conn.rollback();
                            return;
                        }
                        int licenseId = rs.getInt(1);
                        rs.close();
                        ps.close();

                        String updateTotalStart = "UPDATE licenses SET totalStart = totalStart + 1 WHERE id = ?";
                        ps = conn.prepareStatement(updateTotalStart);
                        ps.setInt(1, licenseId);
                        ps.executeUpdate();
                        ps.close();

                        LocalDateTime now = LocalDateTime.now();
                        String insertExpiry = "INSERT INTO expiries (licenseID, expiry_at, startDate) VALUES (?, ?, ?)";
                        ps = conn.prepareStatement(insertExpiry);
                        ps.setInt(1, licenseId);
                        ps.setTimestamp(2, Timestamp.valueOf(now));
                        ps.setTimestamp(3, Timestamp.valueOf(now));
                        ps.executeUpdate();
                        ps.close();
                    }

                    conn.commit();

                    for (Integer pid : pkt.productIds) {
                        sendCommandsToMinecraft(pid, 0, player);
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(LanguageManager.get(
                            "store.product-menu.bundle.success",
                            "{prefix}§eBundle purchase completed successfully!"
                    ).replace("{prefix}", prefixCore))); 

                    UUID uuid = player.getUniqueId();
                    creditCache.put(uuid, credit - totalPrice);
                    List<PurchasedItem> list = purchaseCache.computeIfAbsent(uuid, k -> new ArrayList<>());
                    for (Integer pid : pkt.productIds) {
                        list.add(new PurchasedItem(
                                pid,
                                LanguageManager.get("store.product-menu.bundle.item-name", "Bundle Item"),
                                "",
                                "",
                                "",
                                "",
                                LocalDateTime.now()));
                    }

                } catch (Exception ex) {
                    plugin.getLogger().log(Level.SEVERE, "Bundle purchase error:", ex);
                    try { if (conn != null) conn.rollback(); } catch (SQLException ignored) {}
                    Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(tr(
                            "store.product-menu.purchase.unexpected-error",
                            "{prefix}§cAn unexpected error occurred during purchase.",
                            "prefix", prefixCore
                    )));
                } finally {
                    try {
                        if (rs != null) rs.close();
                        if (ps != null) ps.close();
                        if (conn != null) conn.setAutoCommit(true);
                    } catch (SQLException ignored) {}
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void sendCommandsToMinecraft(int productId, int order, Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                String pluginData;

                Connection conn = null;
                try {
                    if (Database.getInstance() == null) return;
                    conn = Database.getInstance().getConnection();
                    if (conn == null) {
                        plugin.getLogger().severe("Command dispatch: could not obtain DB connection (conn is null).");
                        return;
                    }
                    try (PreparedStatement psData = conn.prepareStatement(
                            "SELECT plugin_data FROM products WHERE id = ?")) {
                        psData.setInt(1, productId);
                        try (ResultSet rsData = psData.executeQuery()) {
                            if (!rsData.next()) return;
                            pluginData = rsData.getString("plugin_data");
                        }
                    }
                } catch (SQLException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Command dispatch error (plugin_data query):", ex);
                    return;
                } finally {
                    try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
                }

                JSONObject selectedProduct = null;
                try {
                    if (pluginData != null && !pluginData.trim().isEmpty()) {
                        JSONArray arr = new JSONArray(pluginData);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            if (obj.optInt("order", -1) == order) {
                                selectedProduct = obj;
                                break;
                            }
                        }
                    }
                } catch (JSONException je) {
                    plugin.getLogger().warning("Command dispatch error: plugin_data JSON could not be parsed: " + je.getMessage());
                    return;
                }

                if (selectedProduct == null) return;

                JSONArray serversArray = selectedProduct.optJSONArray("servers");
                if (serversArray == null || serversArray.length() == 0) return;

                int webUserId = getWebUserIdFromPlayer(player);
                String gameName = player.getName();
                Connection conn2 = null;
                PreparedStatement psName = null;
                ResultSet rsName = null;
                try {
                    if (Database.getInstance() == null) return;
                    conn2 = Database.getInstance().getConnection();
                    if (conn2 != null) {
                        psName = conn2.prepareStatement("SELECT nick FROM userslist WHERE id = ?");
                        psName.setInt(1, webUserId);
                        rsName = psName.executeQuery();
                        if (rsName.next()) {
                            String val = rsName.getString("nick");
                            if (val != null && !val.trim().isEmpty()) {
                                gameName = val;
                            }
                        }
                    }
                } catch (SQLException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Command dispatch error (fetching username):", ex);
                } finally {
                    try { if (rsName != null) rsName.close(); } catch (SQLException ignored) {}
                    try { if (psName != null) psName.close(); } catch (SQLException ignored) {}
                    try { if (conn2 != null) conn2.close(); } catch (SQLException ignored) {}
                }

                for (int s = 0; s < serversArray.length(); s++) {
                    JSONObject serverObj = serversArray.optJSONObject(s);
                    if (serverObj == null) continue;

                    JSONArray commands = serverObj.optJSONArray("commands");
                    if (commands == null) continue;

                    for (int c = 0; c < commands.length(); c++) {
                        JSONObject cmdInfo = commands.optJSONObject(c);
                        if (cmdInfo == null) continue;

                        String rawCmd = cmdInfo.optString("cmd", "").trim();
                        if (rawCmd.isEmpty()) continue;

                        if (rawCmd.startsWith("/")) rawCmd = rawCmd.substring(1);
                        String finalCmd = rawCmd.replace("%player_name%", gameName);

                        Bukkit.getScheduler().runTask(plugin,
                                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
                    }
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private int getWebUserIdFromPlayer(Player player) {
        int webUserID = -1;
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            if (Database.getInstance() == null) return -1;
            conn = Database.getInstance().getConnection();
            if (conn == null) return -1;

            ps = conn.prepareStatement("SELECT id FROM userslist WHERE nick = ?");
            ps.setString(1, player.getName());
            rs = ps.executeQuery();
            if (rs.next()) {
                webUserID = rs.getInt("id");
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error while fetching web userID:", ex);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
            try { if (ps != null) ps.close(); } catch (SQLException ignored) {}
            try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
        }
        return webUserID;
    }

    private ItemStack createPurchasedIcon(Player player) {
        UUID uuid = player.getUniqueId();

        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(LanguageManager.get(
                "store.product-menu.purchased.title",
                "§2§lMy Purchases"
        ));

        if (!purchaseCache.containsKey(uuid)) {
            meta.setLore(Collections.singletonList(LanguageManager.get(
                    "store.product-menu.loading",
                    ChatColor.GRAY + "Loading preview..."
            )));
            item.setItemMeta(meta);
            return item;
        }

        List<PurchasedItem> purchased = purchaseCache.get(uuid);
        if (purchased.isEmpty()) {
            meta.setLore(Collections.singletonList(LanguageManager.get(
                    "store.product-menu.purchased.empty",
                    ChatColor.GRAY + "You have not purchased anything yet."
            )));
            item.setItemMeta(meta);
            return item;
        }

        List<String> lore = new ArrayList<>();
        int maxPreview = 4;
        lore.add("");
        for (int i = 0; i < Math.min(maxPreview, purchased.size()); i++) {
            PurchasedItem pi = purchased.get(i);
            lore.add(ChatColor.GRAY + "- " + pi.subName);
        }
        if (purchased.size() > maxPreview) {
            lore.add(ChatColor.GRAY + "...");
        }
        lore.add("");
        lore.add(LanguageManager.get(
                "store.product-menu.click-to-view",
                ChatColor.GRAY + "§2§l➤ §aClick to view."
        ));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void openPurchasedMenu(Player player, int page) {
        UUID uuid = player.getUniqueId();
        if (!purchaseCache.containsKey(uuid)) {
            player.sendMessage(tr(
                    "store.product-menu.purchased.loading",
                    "{prefix}§eYour purchases are still loading; please try again shortly.",
                    "prefix", prefixCore
            ));
            return;
        }
        List<PurchasedItem> purchased = purchaseCache.get(uuid);
        openActualPurchasedMenu(player, purchased, page);
    }

    private void openActualPurchasedMenu(Player player, List<PurchasedItem> purchased, int page) {
        if (purchased.isEmpty()) {
            player.sendMessage(tr(
                    "store.product-menu.purchased.none",
                    "{prefix}§cYou have not purchased any product yet.",
                    "prefix", prefixCore
            ));
            return;
        }

        int totalItems = purchased.size();
        int calcTotalPages = (int) Math.ceil(totalItems / (double) PAGE_SIZE);
        if (calcTotalPages < 1) calcTotalPages = 1;

        int safePage = page;
        int safeTotalPages = calcTotalPages;
        if (safePage < 0) safePage = 0;
        if (safePage >= safeTotalPages) safePage = safeTotalPages - 1;

        int startIndex = safePage * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, totalItems);
        List<PurchasedItem> pageList = purchased.subList(startIndex, endIndex);

        Map<Integer, PurchasedItem> slotToPurchased = new HashMap<>();
        Inventory inv = Bukkit.createInventory(
                new PurchasedHolder(slotToPurchased, safePage, safeTotalPages),
                INVENTORY_SIZE,
                LanguageManager.get("store.product-menu.inv.purchased-title", "§8My Purchases")
        );

        int itemCount = pageList.size();
        int rowsNeeded = (int) Math.ceil(itemCount / 9.0);
        int startRow = (6 - rowsNeeded) / 2;
        int idx = 0;
        for (int r = 0; r < rowsNeeded; r++) {
            int itemsInRow = Math.min(9, itemCount - r * 9);
            int startCol = (9 - itemsInRow) / 2;
            for (int c = 0; c < itemsInRow; c++) {
                int slot = (startRow + r) * 9 + (startCol + c);
                PurchasedItem pi = pageList.get(idx);

                ItemStack item = iconFromConfig(pi.zonelyIcon, null, Material.BOOK); 
                ItemMeta meta = item.getItemMeta();

                if (pi.zonelyTitle != null && !pi.zonelyTitle.trim().isEmpty()) {
                    String coloredTitle = ChatColor.translateAlternateColorCodes('&', pi.zonelyTitle);
                    meta.setDisplayName(coloredTitle);
                } else {
                    meta.setDisplayName(ChatColor.AQUA + pi.subName);
                }

                List<String> lore = new ArrayList<>();
                if (!pi.subDetails.trim().isEmpty()) {
                    lore.add(ChatColor.GRAY + pi.subDetails);
                }
                String dateString = pi.purchaseDate.format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                lore.add(tr(
                        "store.product-menu.purchased.date",
                        ChatColor.GRAY + "Purchased: " + ChatColor.YELLOW + "{date}",
                        "date", dateString
                ));
                lore.add(tr(
                        "store.product-menu.purchased.product-id",
                        ChatColor.GRAY + "Product ID: " + ChatColor.YELLOW + "{id}",
                        "id", String.valueOf(pi.productId)
                ));

                meta.setLore(lore);
                item.setItemMeta(meta);

                inv.setItem(slot, item);
                slotToPurchased.put(slot, pi);
                idx++;
            }
        }

        inv.setItem(47, createBackToCategoriesButton());
        if (safePage > 0) inv.setItem(45, createNavArrow(false));
        if (safePage < safeTotalPages - 1) inv.setItem(53, createNavArrow(true));
        inv.setItem(49, createCreditIcon(player));
        inv.setItem(48, createLastCreditsIcon(player));
        inv.setItem(50, createPurchasedIcon(player));
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

        Inventory inv = e.getInventory();
        InventoryHolder holder = inv.getHolder();
        Player player = (Player) e.getWhoClicked();
        e.setCancelled(true);

        if (holder instanceof CategoryHolder) {
            CategoryHolder ch = (CategoryHolder) holder;
            int rawSlot = e.getRawSlot();

            if (rawSlot == 47 && ch.isSub && ch.parentCategory != null) {
                if (ch.parentCategory.parentId == 0) {
                    openCategoryMenu(player, 0);
                } else {
                    Category up = getCategoryById(ch.parentCategory.parentId);
                    if (up != null) openSubCategoryMenu(player, up, 0);
                    else openCategoryMenu(player, 0);
                }
                return;
            }

            if (rawSlot == 45 && ch.page > 0) {
                if (ch.isSub && ch.parentCategory != null) {
                    openSubCategoryMenu(player, ch.parentCategory, ch.page - 1);
                } else {
                    openCategoryMenu(player, ch.page - 1);
                }
                return;
            }

            if (rawSlot == 48) {
                if (openLastCreditsMenu(player)) {
                    return;
                }
            }
            if (rawSlot == 53 && ch.page < ch.totalPages - 1) {
                if (ch.isSub && ch.parentCategory != null) {
                    openSubCategoryMenu(player, ch.parentCategory, ch.page + 1);
                } else {
                    openCategoryMenu(player, ch.page + 1);
                }
                return;
            }
            if (rawSlot == 50) {
                openPurchasedMenu(player, 0);
                return;
            }
            if (ch.slotToCategory.containsKey(rawSlot)) {
                Category clickedCat = ch.slotToCategory.get(rawSlot);
                if (hasChildren(clickedCat.id)) {
                    openSubCategoryMenu(player, clickedCat, 0);
                } else {
                    openProductMenu(player, clickedCat, 0);
                }
            }
            return;
        }

        if (holder instanceof ProductHolder) {
            ProductHolder ph = (ProductHolder) holder;
            int rawSlot = e.getRawSlot();

            if (rawSlot == 47) {
                if (ph.category != null && ph.category.parentId != 0) {
                    Category parent = getCategoryById(ph.category.parentId);
                    if (parent != null) {
                        openSubCategoryMenu(player, parent, 0);
                    } else {
                        openCategoryMenu(player, 0);
                    }
                } else {
                    openCategoryMenu(player, 0);
                }
                return;
            }
            if (rawSlot == 48) {
                if (openLastCreditsMenu(player)) {
                    return;
                }
            }
            if (rawSlot == 45 && ph.page > 0) {
                openProductMenu(player, ph.category, ph.page - 1);
                return;
            }
            if (rawSlot == 53 && ph.page < ph.totalPages - 1) {
                openProductMenu(player, ph.category, ph.page + 1);
                return;
            }

            if (rawSlot == 51) {
                openFilterMenu(player, ph.category);
                return;
            }

            if (ph.slotToProduct.containsKey(rawSlot)) {
                ProductEntry clicked = ph.slotToProduct.get(rawSlot);
                if (clicked.subProducts.size() > 1) {
                    openSubProductMenu(player, ph.category, clicked, 0);
                } else {
                    SubProductDetails sp = clicked.subProducts.get(0);
                    openConfirmationMenu(player, clicked, sp);
                }
            } else if (ph.slotToPacket.containsKey(rawSlot)) {
                PacketEntry clickedPkt = ph.slotToPacket.get(rawSlot);
                openPacketConfirmationMenu(player, clickedPkt);
            }
            return;
        }

        if (holder instanceof FilterHolder) {
            FilterHolder fh = (FilterHolder) holder;
            UUID uuid = player.getUniqueId();
            FilterSettings settings = filterByPlayer.computeIfAbsent(uuid, k -> new FilterSettings());
            int rawSlot = e.getRawSlot();

            if (rawSlot == 10) {
                settings.popularOnly = !settings.popularOnly;
                filterByPlayer.put(uuid, settings);
                openFilterMenu(player, fh.category);
                return;
            }
            if (rawSlot == 12) {
                settings.minPrice = 0;
                settings.maxPrice = 100;
                filterByPlayer.put(uuid, settings);
                openFilterMenu(player, fh.category);
                return;
            }
            if (rawSlot == 13) {
                settings.minPrice = 100;
                settings.maxPrice = 500;
                filterByPlayer.put(uuid, settings);
                openFilterMenu(player, fh.category);
                return;
            }
            if (rawSlot == 14) {
                settings.minPrice = 500;
                settings.maxPrice = Integer.MAX_VALUE;
                filterByPlayer.put(uuid, settings);
                openFilterMenu(player, fh.category);
                return;
            }
            if (rawSlot == 16) {
                settings.popularOnly = false;
                settings.minPrice = 0;
                settings.maxPrice = Integer.MAX_VALUE;
                filterByPlayer.put(uuid, settings);
                openFilterMenu(player, fh.category);
                return;
            }
            if (rawSlot == 22) {
                openProductMenu(player, fh.category, 0);
                return;
            }
        }

        if (holder instanceof SubProductHolder) {
            SubProductHolder sph = (SubProductHolder) holder;
            int rawSlot = e.getRawSlot();

            if (rawSlot == 47) {
                openProductMenu(player, sph.category, 0);
                return;
            }
            if (rawSlot == 48) {
                if (openLastCreditsMenu(player)) {
                    return;
                }
            }
            if (rawSlot == 45 && sph.page > 0) {
                openSubProductMenu(player, sph.category, sph.parent, sph.page - 1);
                return;
            }
            if (rawSlot == 53 && sph.page < sph.totalPages - 1) {
                openSubProductMenu(player, sph.category, sph.parent, sph.page + 1);
                return;
            }
            if (sph.slotToSub.containsKey(rawSlot)) {
                SubProductDetails sp = sph.slotToSub.get(rawSlot);
                openConfirmationMenu(player, sph.parent, sp);
            }
            return;
        }

        if (holder instanceof ConfirmationHolder) {
            ConfirmationHolder ch = (ConfirmationHolder) holder;
            PendingPurchase pp = ch.pending;
            int rawSlot = e.getRawSlot();

            if (rawSlot == 11) {
                player.closeInventory();
                return;
            }
            if (rawSlot == 15) {
                if (pp.productEntry != null && pp.subProduct != null) {
                    purchaseProductForPlayer(player, pp.productEntry, pp.subProduct);
                } else if (pp.packetEntry != null) {
                    purchasePacketForPlayer(player, pp.packetEntry);
                }
            }
            return;
        }

        if (holder instanceof PurchasedHolder) {
            PurchasedHolder ph = (PurchasedHolder) holder;
            int rawSlot = e.getRawSlot();

            if (rawSlot == 47) {
                openCategoryMenu(player, 0);
                return;
            }
            if (rawSlot == 48) {
                if (openLastCreditsMenu(player)) {
                    return;
                }
            }
            if (rawSlot == 45 && ph.page > 0) {
                openPurchasedMenu(player, ph.page - 1);
                return;
            }
            if (rawSlot == 53 && ph.page < ph.totalPages - 1) {
                openPurchasedMenu(player, ph.page + 1);
                return;
            }
            if (ph.slotToPurchased.containsKey(rawSlot)) {
                player.closeInventory();
            }
        }
    }

    private static class CategoryHolder implements InventoryHolder {
        private final Map<Integer, Category> slotToCategory;
        final int page;
        final int totalPages;
        final Category parentCategory; 
        final boolean isSub;

        public CategoryHolder(Map<Integer, Category> slotToCategory, int page, int totalPages, Category parentCategory, boolean isSub) {
            this.slotToCategory = slotToCategory;
            this.page = page;
            this.totalPages = totalPages;
            this.parentCategory = parentCategory;
            this.isSub = isSub;
        }

        @Override public Inventory getInventory() { return null; }
    }

    private static class ProductHolder implements InventoryHolder {
        private final Map<Integer, ProductEntry> slotToProduct;
        private final Map<Integer, PacketEntry> slotToPacket;
        final Category category;
        final int page;
        final int totalPages;

        public ProductHolder(Map<Integer, ProductEntry> slotToProduct,
                             Map<Integer, PacketEntry> slotToPacket,
                             Category category,
                             int page,
                             int totalPages) {
            this.slotToProduct = slotToProduct;
            this.slotToPacket = slotToPacket;
            this.category = category;
            this.page = page;
            this.totalPages = totalPages;
        }

        @Override public Inventory getInventory() { return null; }
    }

    private static class SubProductHolder implements InventoryHolder {
        private final Category category;
        private final ProductEntry parent;
        private final Map<Integer, SubProductDetails> slotToSub;
        final int page;
        final int totalPages;

        public SubProductHolder(Category category,
                                ProductEntry parent,
                                Map<Integer, SubProductDetails> slotToSub,
                                int page,
                                int totalPages) {
            this.category = category;
            this.parent = parent;
            this.slotToSub = slotToSub;
            this.page = page;
            this.totalPages = totalPages;
        }

        @Override public Inventory getInventory() { return null; }
    }

    private static class ConfirmationHolder implements InventoryHolder {
        private final Player player;
        private final PendingPurchase pending;

        public ConfirmationHolder(Player player, PendingPurchase pending) {
            this.player = player;
            this.pending = pending;
        }

        @Override public Inventory getInventory() { return null; }
    }

    private static class PurchasedHolder implements InventoryHolder {
        private final Map<Integer, PurchasedItem> slotToPurchased;
        final int page;
        final int totalPages;

        public PurchasedHolder(Map<Integer, PurchasedItem> slotToPurchased,
                               int page,
                               int totalPages) {
            this.slotToPurchased = slotToPurchased;
            this.page = page;
            this.totalPages = totalPages;
        }

        @Override public Inventory getInventory() { return null; }
    }

    private Category getCategoryById(int id) {
        return categoryCache.get(id);
    }

    private List<Category> getChildren(int parentId) {
        return childrenByParentCache.getOrDefault(parentId, Collections.emptyList());
    }

    private boolean hasChildren(int categoryId) {
        List<Category> children = childrenByParentCache.get(categoryId);
        return children != null && !children.isEmpty();
    }

    private static class Category {
        int id;
        int parentId;
        String name;
        String content;
        String contentPlain;
        int rowOrder;

        boolean showInGame;
        String guiTitle;
        String guiDescription;
        String guiIcon;
        String guiModelId;

        public Category(int id, int parentId, String name, String content, int rowOrder,
                        boolean showInGame, String guiTitle,
                        String guiDescription, String guiIcon, String guiModelId) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
            this.content = content;
            this.contentPlain = stripHtmlStatic(content);
            this.rowOrder = rowOrder;

            this.showInGame = showInGame;
            this.guiTitle = guiTitle != null ? guiTitle : "";
            this.guiDescription = guiDescription != null ? guiDescription : "";
            this.guiIcon = guiIcon != null ? guiIcon : "";
            this.guiModelId = guiModelId != null ? guiModelId : "";
        }
    }

    private static class ProductEntry {
        int productId;
        String baseName;
        String rawDetails;
        String slug;
        boolean paidFree;
        int stockAll;
        double basePrice;
        double baseDiscounted;
        String baseExpiry;
        List<SubProductDetails> subProducts;

        public ProductEntry(int productId,
                            String baseName,
                            String rawDetails,
                            String slug,
                            boolean paidFree,
                            int stockAll,
                            double basePrice,
                            double baseDiscounted,
                            String baseExpiry,
                            List<SubProductDetails> subProducts) {
            this.productId = productId;
            this.baseName = baseName;
            this.rawDetails = rawDetails;
            this.slug = slug;
            this.paidFree = paidFree;
            this.stockAll = stockAll;
            this.basePrice = basePrice;
            this.baseDiscounted = baseDiscounted;
            this.baseExpiry = baseExpiry;
            this.subProducts = subProducts;
        }

        public int getTotalStock() {
            int total = 0;
            for (SubProductDetails sp : subProducts) {
                if (sp.stock < 0) return -1;
                total += sp.stock;
            }
            return total;
        }

        public boolean hasAnyDiscount() {
            for (SubProductDetails sp : subProducts) {
                if (sp.hasDiscount()) return true;
            }
            return false;
        }

        public String getDisplayNewPrice() {
            double minNew = Double.MAX_VALUE;
            for (SubProductDetails sp : subProducts) {
                double candidate = sp.hasDiscount() ? sp.discountedPrice : sp.price;
                if (candidate < minNew) minNew = candidate;
            }
            if (minNew == Double.MAX_VALUE && !subProducts.isEmpty()) {
                return formatPrice(subProducts.get(0).price);
            }
            return formatPrice(minNew);
        }

        public String getDisplayOldPrice() {
            double maxOld = 0;
            for (SubProductDetails sp : subProducts) {
                if (sp.price > maxOld) maxOld = sp.price;
            }
            return formatPrice(maxOld);
        }

        public String formatPrice(double x) {
            return String.format("%.0f", x);
        }
    }

    private static class SubProductDetails {
        int parentId;
        String name;
        String details;
        double price;
        double discountedPrice;
        String discountType;
        String expiry;
        String stockType;
        int stock;
        String imageUrl;
        int saleDuration;
        int orderIndex;

        boolean showInGame;
        String zonelyTitle;
        String zonelyDescription;
        String zonelyIcon;
        String zonelyModelId;

        public SubProductDetails(int parentId,
                                 String name,
                                 String details,
                                 double price,
                                 double discountedPrice,
                                 String discountType,
                                 String expiry,
                                 String stockType,
                                 int stock,
                                 String imageUrl,
                                 int saleDuration,
                                 int orderIndex,
                                 boolean showInGame,
                                 String zonelyTitle,
                                 String zonelyDescription,
                                 String zonelyIcon,
                                 String zonelyModelId) {

            this.parentId = parentId;
            this.name = name;
            this.details = details;
            this.price = price;
            this.discountedPrice = discountedPrice;
            this.discountType = discountType;
            this.expiry = expiry;
            this.stockType = stockType;
            this.stock = stock;
            this.imageUrl = imageUrl;
            this.saleDuration = saleDuration;
            this.orderIndex = orderIndex;

            this.showInGame = showInGame;
            this.zonelyTitle = zonelyTitle != null ? zonelyTitle : "";
            this.zonelyDescription = zonelyDescription != null ? zonelyDescription : "";
            this.zonelyIcon = zonelyIcon != null ? zonelyIcon : "";
            this.zonelyModelId = zonelyModelId != null ? zonelyModelId : "";
        }

        public boolean hasDiscount() {
            return discountType != null && !discountType.equalsIgnoreCase("none") && discountedPrice > 0;
        }
    }

    private static class PacketEntry {
        int packetId;
        String name;
        double price;
        String expiry;
        List<Integer> productIds;
        List<String> productNames;

        public PacketEntry(int packetId,
                           String name,
                           double price,
                           String expiry,
                           List<Integer> productIds,
                           List<String> productNames) {
            this.packetId = packetId;
            this.name = name;
            this.price = price;
            this.expiry = expiry;
            this.productIds = productIds;
            this.productNames = productNames;
        }
    }

    private static class PurchasedItem {
        int productId;
        String subName;
        String subDetails;
        String subImage;

        String zonelyTitle;
        String zonelyIcon;
        LocalDateTime purchaseDate;

        public PurchasedItem(int productId,
                             String subName,
                             String subDetails,
                             String subImage,
                             String zonelyTitle,
                             String zonelyIcon,
                             LocalDateTime purchaseDate) {
            this.productId = productId;
            this.subName = subName;
            this.subDetails = subDetails;
            this.subImage = subImage;
            this.zonelyTitle = zonelyTitle != null ? zonelyTitle : "";
            this.zonelyIcon = zonelyIcon != null ? zonelyIcon : "";
            this.purchaseDate = purchaseDate;
        }
    }

    private static class PendingPurchase {
        final ProductEntry productEntry;
        final SubProductDetails subProduct;
        final PacketEntry packetEntry;

        PendingPurchase(ProductEntry pe, SubProductDetails sp, PacketEntry pkt) {
            this.productEntry = pe;
            this.subProduct = sp;
            this.packetEntry = pkt;
        }
    }

    private ItemStack createNavArrow(boolean forward) {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        meta.setDisplayName(forward
                ? LanguageManager.get("store.common.forward-arrow", ChatColor.YELLOW + "-->")
                : LanguageManager.get("store.common.back-arrow", ChatColor.YELLOW + "<--"));
        arrow.setItemMeta(meta);
        return arrow;
    }

    private ItemStack createBackToCategoriesButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(LanguageManager.get("store.common.back-to-categories", ChatColor.RED + "Back to Categories"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackToCategoriesButtonForCategory(Category current) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (current != null && current.parentId != 0) {
            Category parent = getCategoryById(current.parentId);
            String disp = parent != null ? resolveCategoryDisplay(parent) : "Categories";
            meta.setDisplayName(tr("store.common.back-to-parent",
                    ChatColor.RED + "Back to {name}",
                    "name", ChatColor.stripColor(disp)));
        } else {
            meta.setDisplayName(LanguageManager.get("store.common.back-to-categories", ChatColor.RED + "Back to Categories"));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackToProductsButton(ProductEntry parent) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(LanguageManager.get("store.common.back-to-products", ChatColor.RED + "Back to Products"));
        item.setItemMeta(meta);
        return item;
    }


    private boolean openLastCreditsMenu(Player player) {
        try {
            if (!plugin.isLastCreditsEnabled() || plugin.getCreditsManager() == null) {
                String prefix = LanguageManager.get("prefix.lobby", "&3Lobby &8>> ");
                LanguageManager.send(player, "menus.last-credits.disabled",
                        "{prefix}&cLast credits module is disabled.",
                        "prefix", prefix);
                return true;
            }
            plugin.getCreditsManager().openMenu(player, LastCreditsMenuManager.Category.RECENT, 0);
            return true;
        } catch (NoSuchMethodError | NoClassDefFoundError err) {
            String prefix = LanguageManager.get("prefix.lobby", "&3Lobby &8>> ");
            LanguageManager.send(player, "menus.last-credits.disabled",
                    "{prefix}&cLast credits module is unavailable.",
                    "prefix", prefix);
            return true;
        }
    }
}
