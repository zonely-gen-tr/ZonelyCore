package dev.zonely.whiteeffect.inventory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.database.HikariDatabase;
import dev.zonely.whiteeffect.database.MySQLDatabase;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import javax.sql.rowset.CachedRowSet;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class InventorySnapshotManager implements Listener {

    private static final int MAX_SERVER_ID_LENGTH = 64;

    private final Core plugin;
    private final Database database;
    private final Logger logger;
    private final String serverId;
    private final boolean enabled;
    private final boolean removeOnJoin;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public InventorySnapshotManager(Core plugin) {
        this.plugin = plugin;
        this.database = Database.getInstance();
        this.logger = plugin.getLogger();

        boolean configEnabled = plugin.getConfig().getBoolean("inventory-snapshot.enabled", true);
        this.removeOnJoin = plugin.getConfig().getBoolean("inventory-snapshot.remove-on-join", true);

        String configuredServerId = plugin.getConfig().getString("inventory-snapshot.server-id", "");
        if (configuredServerId == null || configuredServerId.trim().isEmpty()) {
            configuredServerId = plugin.getConfig().getString("chat-bridge.server-id", "");
        }
        String derivedId = sanitizeServerId(configuredServerId);
        if (derivedId.isEmpty()) {
            derivedId = sanitizeServerId(Bukkit.getServer().getName());
        }
        if (derivedId.isEmpty()) {
            derivedId = "default";
        }
        this.serverId = derivedId;

        if (!configEnabled) {
            this.enabled = false;
            this.logger.info("[InventorySnapshot] Disabled via configuration.");
        } else if (!(database instanceof MySQLDatabase) && !(database instanceof HikariDatabase)) {
            this.enabled = false;
            this.logger.warning("[InventorySnapshot] No SQL backend available. Inventory snapshots are disabled.");
        } else {
            this.enabled = true;
        }
    }

    public void start() {
        if (!enabled) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (removeOnJoin) {
            Bukkit.getOnlinePlayers().forEach(player -> deleteSnapshot(player.getUniqueId()));
        }
        logger.info(String.format(Locale.ROOT, "[InventorySnapshot] Inventory snapshots active for server '%s'.", serverId));
    }

    public void shutdown() {
        if (!enabled) {
            return;
        }
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!enabled) {
            return;
        }
        Player player = event.getPlayer();
        SnapshotPayload payload = buildSnapshot(player);
        if (payload == null) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> persistSnapshot(payload));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled || !removeOnJoin) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        deleteSnapshot(uuid);
    }

    private void deleteSnapshot(UUID uuid) {
        if (uuid == null) {
            return;
        }
        executeUpdate("DELETE FROM `ZonelyCoreInventorySnapshots` WHERE `server` = ? AND `uuid` = ?", serverId, uuid.toString());
    }

    private SnapshotPayload buildSnapshot(Player player) {
        try {
            long capturedAt = System.currentTimeMillis();
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("server", serverId);
            root.put("player", player.getName());
            root.put("uuid", player.getUniqueId().toString());
            root.put("items", serializeInventory(player.getInventory()));
            root.put("stats", serializeStats(player));
            root.put("effects", serializeEffects(player.getActivePotionEffects()));
            root.put("capturedAt", capturedAt);
            return new SnapshotPayload(player.getUniqueId(), player.getName(), gson.toJson(root), capturedAt);
        } catch (Throwable throwable) {
            logger.log(Level.WARNING, "[InventorySnapshot] Failed to capture snapshot for " + player.getName(), throwable);
            return null;
        }
    }

    private void persistSnapshot(SnapshotPayload payload) {
        Integer profileId = lookupProfileId(payload.getName());
        executeUpdate(
                "INSERT INTO `ZonelyCoreInventorySnapshots` (`server`, `uuid`, `name`, `profile_id`, `data_json`, `captured_at`) " +
                        "VALUES (?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `profile_id` = VALUES(`profile_id`), " +
                        "`data_json` = VALUES(`data_json`), `captured_at` = VALUES(`captured_at`)",
                serverId,
                payload.getUuid().toString(),
                payload.getName(),
                profileId,
                payload.getJson(),
                payload.getCapturedAt()
        );
    }

    private Integer lookupProfileId(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return null;
        }
        try (CachedRowSet rowSet =
                     database instanceof MySQLDatabase
                             ? ((MySQLDatabase) database).query("SELECT `id` FROM `userslist` WHERE LOWER(`nick`) = LOWER(?) LIMIT 1", playerName)
                             : database instanceof HikariDatabase
                             ? ((HikariDatabase) database).query("SELECT `id` FROM `userslist` WHERE LOWER(`nick`) = LOWER(?) LIMIT 1", playerName)
                             : null) {
            if (rowSet != null && rowSet.next()) {
                return rowSet.getInt("id");
            }
            logger.fine(() -> "[InventorySnapshot] No userslist entry for " + playerName);
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "[InventorySnapshot] Failed to resolve userslist id for " + playerName, exception);
        }
        return null;
    }

    private List<Map<String, Object>> serializeInventory(PlayerInventory inventory) {
        List<Map<String, Object>> items = new ArrayList<>();

        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (isEmpty(item)) {
                continue;
            }
            items.add(serializeItem(item, "MAIN", slot, null));
        }

        ItemStack[] armor = inventory.getArmorContents();
        String[] armorNames = new String[]{"BOOTS", "LEGGINGS", "CHESTPLATE", "HELMET"};
        for (int index = 0; index < armor.length; index++) {
            ItemStack item = armor[index];
            if (isEmpty(item)) {
                continue;
            }
            int slot = 100 + index;
            items.add(serializeItem(item, "ARMOR", slot, armorNames[index]));
        }

        ItemStack offHand = getOffHandItem(inventory);
        if (!isEmpty(offHand)) {
            items.add(serializeItem(offHand, "OFFHAND", 150, "OFFHAND"));
        }

        return items;
    }

    private Map<String, Object> serializeItem(ItemStack item, String section, int slot, String slotName) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("section", section);
        map.put("slot", slot);
        if (slotName != null) {
            map.put("slotName", slotName);
        }
        map.put("material", item.getType().name());
        map.put("amount", item.getAmount());
        map.put("maxStackSize", item.getMaxStackSize());

        int maxDurability = item.getType().getMaxDurability();
        if (maxDurability > 0) {
            map.put("maxDurability", maxDurability);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                try {
                    map.put("displayName", meta.getDisplayName());
                } catch (NoSuchMethodError ignored) {
                }
            }
            if (meta.hasLore()) {
                map.put("lore", meta.getLore().stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
            }
            if (meta.hasEnchants()) {
                List<Map<String, Object>> enchants = meta.getEnchants().entrySet().stream()
                        .map(entry -> {
                            Enchantment enchantment = entry.getKey();
                            int level = entry.getValue();
                            Map<String, Object> enchantData = new LinkedHashMap<>();
                            String key = resolveEnchantmentKey(enchantment);
                            enchantData.put("id", key);
                            enchantData.put("name", toTitleCase(key));
                            enchantData.put("level", level);
                            enchantData.put("formatted", ChatColor.BLUE + toTitleCase(key) + " " + toRomanNumeral(level));
                            return enchantData;
                        })
                        .collect(Collectors.toList());
                if (!enchants.isEmpty()) {
                    map.put("enchants", enchants);
                }
            }
            if (!meta.getItemFlags().isEmpty()) {
                map.put("flags", meta.getItemFlags().stream()
                        .map(ItemFlag::name)
                        .collect(Collectors.toList()));
            }
            Boolean unbreakable = reflectBoolean(meta, "isUnbreakable");
            if (unbreakable == null) {
                Object spigot = reflectCall(meta, "spigot");
                if (spigot != null) {
                    unbreakable = reflectBoolean(spigot, "isUnbreakable");
                }
            }
            if (Boolean.TRUE.equals(unbreakable)) {
                map.put("unbreakable", true);
            }

            if (meta instanceof Damageable) {
                Damageable damageable = (Damageable) meta;
                map.put("damage", damageable.getDamage());
                if (maxDurability > 0) {
                    map.put("durabilityLeft", Math.max(maxDurability - damageable.getDamage(), 0));
                }
            }

            Boolean hasCustomModel = reflectBoolean(meta, "hasCustomModelData");
            Integer customModelData = reflectInteger(meta, "getCustomModelData");
            if ((hasCustomModel == null || hasCustomModel) && customModelData != null) {
                map.put("customModelData", customModelData);
            }

            if (meta instanceof PotionMeta) {
                map.put("potion", serializePotionMeta((PotionMeta) meta));
            }
        }

        return map;
    }

    private Map<String, Object> serializePotionMeta(PotionMeta potionMeta) {
        Map<String, Object> map = new LinkedHashMap<>();
        Boolean hasColor = reflectBoolean(potionMeta, "hasColor");
        if (Boolean.TRUE.equals(hasColor)) {
            Object color = reflectCall(potionMeta, "getColor");
            if (color != null) {
                Integer rgb = reflectInteger(color, "asRGB");
                if (rgb != null) {
                    map.put("color", String.format("#%06X", rgb));
                }
            }
        }

        Object basePotionData = reflectCall(potionMeta, "getBasePotionData");
        if (basePotionData != null) {
            Object type = reflectCall(basePotionData, "getType");
            if (type != null) {
                map.put("baseType", type.toString());
            }
            Boolean upgraded = reflectBoolean(basePotionData, "isUpgraded");
            if (upgraded != null) {
                map.put("upgraded", upgraded);
            }
            Boolean extended = reflectBoolean(basePotionData, "isExtended");
            if (extended != null) {
                map.put("extended", extended);
            }
        }

        if (potionMeta.hasCustomEffects()) {
            List<Map<String, Object>> effects = potionMeta.getCustomEffects().stream()
                    .map(effect -> {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("type", effect.getType().getName());
                        data.put("durationTicks", effect.getDuration());
                        data.put("durationSeconds", effect.getDuration() / 20);
                        data.put("amplifier", effect.getAmplifier());
                        data.put("ambient", effect.isAmbient());
                        Boolean particles = reflectBoolean(effect, "hasParticles");
                        if (particles != null) {
                            data.put("particles", particles);
                        }
                        Boolean icon = reflectBoolean(effect, "hasIcon");
                        if (icon != null) {
                            data.put("icon", icon);
                        }
                        data.put("formatted", formatEffectLine(effect.getType(), effect.getAmplifier(), effect.getDuration()));
                        return data;
                    })
                    .collect(Collectors.toList());
            map.put("customEffects", effects);
        }
        return map;
    }

    private Map<String, Object> serializeStats(Player player) {
        Map<String, Object> stats = new LinkedHashMap<>();
        double health = player.getHealth();
        stats.put("health", round(health));

        Double maxHealth = reflectDouble(player, "getMaxHealth");
        stats.put("maxHealth", round(maxHealth != null ? maxHealth : 20.0D));

        Double absorption = reflectDouble(player, "getAbsorptionAmount");
        stats.put("absorption", round(absorption != null ? absorption : 0.0D));

        Integer food = reflectInteger(player, "getFoodLevel");
        stats.put("food", food != null ? food : 20);

        Double saturation = reflectDouble(player, "getSaturation");
        stats.put("saturation", saturation != null ? round(saturation) : 0.0D);

        Double exhaustion = reflectDouble(player, "getExhaustion");
        stats.put("exhaustion", exhaustion != null ? round(exhaustion) : 0.0D);

        Integer totalExp = reflectInteger(player, "getTotalExperience");
        stats.put("totalExperience", totalExp != null ? totalExp : 0);

        Integer level = reflectInteger(player, "getLevel");
        stats.put("experienceLevel", level != null ? level : 0);

        Double expProgress = reflectDouble(player, "getExp");
        stats.put("experienceProgress", expProgress != null ? round(expProgress) : 0.0D);

        stats.put("gamemode", player.getGameMode() != null ? player.getGameMode().name() : GameMode.SURVIVAL.name());

        Boolean flying = reflectBoolean(player, "isFlying");
        stats.put("flying", flying != null ? flying : Boolean.FALSE);

        Object bed = reflectCall(player, "getBedSpawnLocation");
        stats.put("bedSpawn", bed != null);

        Map<String, Object> location = new LinkedHashMap<>();
        location.put("world", player.getWorld().getName());
        location.put("x", round(player.getLocation().getX()));
        location.put("y", round(player.getLocation().getY()));
        location.put("z", round(player.getLocation().getZ()));
        location.put("yaw", round(player.getLocation().getYaw()));
        location.put("pitch", round(player.getLocation().getPitch()));
        stats.put("location", location);

        return stats;
    }

    private List<Map<String, Object>> serializeEffects(Collection<PotionEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return new ArrayList<>();
        }
        return effects.stream()
                .map(effect -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    PotionEffectType type = effect.getType();
                    String key = type.getName();
                    data.put("type", key);
                    data.put("name", toTitleCase(key));
                    data.put("durationTicks", effect.getDuration());
                    data.put("durationSeconds", effect.getDuration() / 20);
                    data.put("amplifier", effect.getAmplifier());
                    data.put("ambient", effect.isAmbient());
                    Boolean particles = reflectBoolean(effect, "hasParticles");
                    if (particles != null) {
                        data.put("particles", particles);
                    }
                    Boolean icon = reflectBoolean(effect, "hasIcon");
                    if (icon != null) {
                        data.put("icon", icon);
                    }
                    data.put("formatted", formatEffectLine(type, effect.getAmplifier(), effect.getDuration()));
                    return data;
                })
                .collect(Collectors.toList());
    }

    private String formatEffectLine(PotionEffectType type, int amplifier, int durationTicks) {
        String base = toTitleCase(type.getName());
        String level = toRomanNumeral(amplifier + 1);
        int seconds = Math.max(durationTicks / 20, 0);
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        String time = minutes + "m " + remainingSeconds + "s";
        return ChatColor.DARK_AQUA + base + " " + level + ChatColor.GRAY + " (" + time + ")";
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private ItemStack getOffHandItem(PlayerInventory inventory) {
        Object result = reflectCall(inventory, "getItemInOffHand");
        if (result instanceof ItemStack) {
            return (ItemStack) result;
        }
        return null;
    }

    private double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private String toTitleCase(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        String[] parts = key.split("[_:-]");
        return java.util.Arrays.stream(parts)
                .filter(part -> !part.isEmpty())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private String toRomanNumeral(int number) {
        if (number <= 0) {
            return "I";
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        int remaining = number;
        for (int i = 0; i < values.length; i++) {
            while (remaining >= values[i]) {
                remaining -= values[i];
                result.append(numerals[i]);
            }
        }
        return result.toString();
    }

    private String sanitizeServerId(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String filtered = trimmed.replaceAll("[^a-zA-Z0-9_.-]", "");
        if (filtered.length() > MAX_SERVER_ID_LENGTH) {
            filtered = filtered.substring(0, MAX_SERVER_ID_LENGTH);
        }
        return filtered;
    }

    private String resolveEnchantmentKey(Enchantment enchantment) {
        if (enchantment == null) {
            return "UNKNOWN";
        }
        Object namespacedKey = reflectCall(enchantment, "getKey");
        if (namespacedKey != null) {
            Object value = reflectCall(namespacedKey, "getKey");
            if (value instanceof String) {
                return (String) value;
            }
            return namespacedKey.toString();
        }
        String legacy = enchantment.getName();
        if (legacy != null && !legacy.isEmpty()) {
            return legacy;
        }
        return "UNKNOWN";
    }

    private void executeUpdate(String sql, Object... params) {
        if (database instanceof MySQLDatabase) {
            ((MySQLDatabase) database).execute(sql, params);
        } else if (database instanceof HikariDatabase) {
            ((HikariDatabase) database).execute(sql, params);
        }
    }

    private Object reflectCall(Object target, String methodName, Class<?>... parameterTypes) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Boolean reflectBoolean(Object target, String methodName) {
        Object value = reflectCall(target, methodName);
        return value instanceof Boolean ? (Boolean) value : null;
    }

    private Integer reflectInteger(Object target, String methodName) {
        Object value = reflectCall(target, methodName);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private Double reflectDouble(Object target, String methodName) {
        Object value = reflectCall(target, methodName);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private static final class SnapshotPayload {
        private final UUID uuid;
        private final String name;
        private final String json;
        private final long capturedAt;

        private SnapshotPayload(UUID uuid, String name, String json, long capturedAt) {
            this.uuid = uuid;
            this.name = name;
            this.json = json;
            this.capturedAt = capturedAt;
        }

        private UUID getUuid() {
            return uuid;
        }

        private String getName() {
            return name;
        }

        private String getJson() {
            return json;
        }

        private long getCapturedAt() {
            return capturedAt;
        }
    }
}
