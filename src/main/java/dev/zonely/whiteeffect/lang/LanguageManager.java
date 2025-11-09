package dev.zonely.whiteeffect.lang;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import dev.zonely.whiteeffect.utils.StringUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class LanguageManager {

    private static final String ROOT = "languages.";
    private static final String FALLBACK_LOCALE = "en_US";
    private static final Set<String> MISSING_WARNINGS = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final Map<String, String> LOCALE_CACHE = new ConcurrentHashMap<>();

    private static WConfig config;
    private static String defaultLocale = FALLBACK_LOCALE;

    private LanguageManager() {
    }

    public static void init(Core plugin) {
        config = plugin.getConfig("languages");
        mergeDefaultFile(plugin);
        if (!config.contains("default")) {
            config.set("default", FALLBACK_LOCALE);
        }
        if (!config.contains("languages")) {
            config.createSection("languages");
        }
        String configuredDefault = normalize(config.getString("default", FALLBACK_LOCALE));
        if (configuredDefault == null || configuredDefault.isEmpty()) {
            configuredDefault = FALLBACK_LOCALE;
            config.set("default", configuredDefault);
        }
        defaultLocale = configuredDefault;
        ensureLocaleSection(defaultLocale);
    }

    public static void reload(Core plugin) {
        config.reload();
        mergeDefaultFile(plugin);
        String configuredDefault = normalize(config.getString("default", FALLBACK_LOCALE));
        if (configuredDefault == null || configuredDefault.isEmpty()) {
            configuredDefault = FALLBACK_LOCALE;
            config.set("default", configuredDefault);
        }
        defaultLocale = configuredDefault;
        ensureLocaleSection(defaultLocale);
    }

    private static void mergeDefaultFile(Core plugin) {
        try (InputStream stream = plugin.getResource("languages.yml")) {
            if (stream == null) {
                return;
            }

            YamlConfiguration defaults =
                YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            YamlConfiguration target = config.getRawConfig();

            if (mergeSection(defaults, target)) {
                config.save();
                plugin.getLogger().info("languages.yml has been updated with new default entries.");
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Unable to compare languages.yml with built-in defaults.", ex);
        }
    }

    private static boolean mergeSection(ConfigurationSection defaults, ConfigurationSection target) {
        boolean updated = false;

        for (String key : defaults.getKeys(false)) {
            Object defValue = defaults.get(key);

            if (!target.contains(key)) {
                target.set(key, defValue);
                updated = true;
                continue;
            }

            if (defValue instanceof ConfigurationSection) {
                ConfigurationSection defSection = (ConfigurationSection) defValue;
                ConfigurationSection targetSection = target.getConfigurationSection(key);
                if (targetSection == null) {
                    targetSection = target.createSection(key);
                    updated = true;
                }
                if (mergeSection(defSection, targetSection)) {
                    updated = true;
                }
            }
        }

        return updated;
    }

    public static String getDefaultLocale() {
        return defaultLocale;
    }

    public static List<String> getAvailableLocales() {
        if (!config.contains("languages")) {
            return Collections.emptyList();
        }
        ConfigurationSection section = config.getSection("languages");
        if (section == null) {
            return Collections.emptyList();
        }
        Set<String> keys = section.getKeys(false);
        return new ArrayList<>(keys);
    }

    public static String resolveProfileLocale(Profile profile) {
        if (profile == null) {
            return defaultLocale;
        }
        String cached = LOCALE_CACHE.get(profile.getName().toLowerCase(Locale.ROOT));
        if (cached != null) {
            return cached;
        }
        String raw = profile.getLanguage();
        String locale = resolveLocale(raw);
        LOCALE_CACHE.put(profile.getName().toLowerCase(Locale.ROOT), locale);
        return locale;
    }

    public static void invalidateProfileLocale(Profile profile) {
        if (profile != null) {
            LOCALE_CACHE.remove(profile.getName().toLowerCase(Locale.ROOT));
        }
    }

    public static LanguageMeta getMeta(String locale) {
        String resolved = resolveLocale(locale);
        String displayName = StringUtils.formatColors(metaString(resolved, "display-name", "&f&l" + resolved));
        List<String> description = format(metaList(resolved, "description", Collections.singletonList("&7Configure your language.")));
        String icon = metaString(resolved, "icon", "SKULL_ITEM:3 : 1");
        String skin = metaString(resolved, "skin", "");
        return new LanguageMeta(resolved, displayName, description, icon, skin);
    }

    public static void send(CommandSender sender, String key, String def, Object... placeholders) {
        if (sender == null) {
            return;
        }
        if (sender instanceof Player) {
            Player player = (Player) sender;
            Profile profile = Profile.getProfile(player.getName());
            sender.sendMessage(get(profile, key, def, placeholders));
        } else {
            sender.sendMessage(get(defaultLocale, key, def, placeholders));
        }
    }

    public static String get(Profile profile, String key, String def, Object... placeholders) {
        return get(resolveProfileLocale(profile), key, def, placeholders);
    }

    public static String get(String key, String def, Object... placeholders) {
        return get(defaultLocale, key, def, placeholders);
    }

    public static List<String> getList(String key, List<String> def, Object... placeholders) {
        return getList(defaultLocale, key, def, placeholders);
    }

    public static List<String> getList(Profile profile, String key, List<String> def, Object... placeholders) {
        return getList(resolveProfileLocale(profile), key, def, placeholders);
    }

    public static MenuItemDefinition getMenuItem(Profile profile, String key, int defaultSlot,
                                                 String def, Object... placeholders) {
        return getMenuItem(resolveProfileLocale(profile), key, defaultSlot, def, placeholders);
    }

    public static MenuItemDefinition getMenuItem(String locale, String key, int defaultSlot,
                                                 String def, Object... placeholders) {
        String actualLocale = resolveLocale(locale);
        String path = localeKey(actualLocale, key);
        ensureMenuItemDefaults(path, defaultSlot, def);

        int slot = defaultSlot;
        String itemData = def;
        List<String> commands = Collections.emptyList();
        boolean close = false;
        String permission = "";

        ConfigurationSection section = config.getSection(path);
        if (section != null) {
            slot = parseSlot(section.get("slot"), defaultSlot);
            itemData = section.getString("item", def);
            close = section.getBoolean("close", false);
            permission = section.getString("permission", "");
            List<String> cmds = section.getStringList("commands");
            if (cmds != null && !cmds.isEmpty()) {
                commands = new ArrayList<>();
                for (String cmd : cmds) {
                    if (cmd != null) {
                        commands.add(cmd);
                    }
                }
            }
        } else {
            Object raw = config.get(path);
            if (raw instanceof String) {
                itemData = (String) raw;
            } else if (raw instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) raw;
                slot = parseSlot(map.get("slot"), defaultSlot);
                Object item = map.get("item");
                if (item != null) {
                    itemData = item.toString();
                }
                Object cmds = map.get("commands");
                if (cmds instanceof Iterable<?>) {
                    commands = new ArrayList<>();
                    for (Object c : (Iterable<?>) cmds) {
                        if (c != null) {
                            commands.add(c.toString());
                        }
                    }
                }
                Object closeObj = map.get("close");
                if (closeObj instanceof Boolean) {
                    close = (Boolean) closeObj;
                }
                Object permObj = map.get("permission");
                if (permObj != null) {
                    permission = permObj.toString();
                }
            }
        }
        if ((commands == null || commands.isEmpty()) && section != null && section.contains("command")) {
            String single = section.getString("command");
            if (single != null && !single.trim().isEmpty()) {
                commands = Collections.singletonList(single);
            }
        }

        itemData = applyPlaceholders(itemData, placeholders);
        itemData = StringUtils.formatColors(itemData);
        return new MenuItemDefinition(slot, itemData, commands, close, permission);
    }

    public static String get(String locale, String key, String def, Object... placeholders) {
        String raw = getRaw(locale, key, def);
        if (raw != null) {
            String trimmed = raw.trim();
            if ("prefix".equalsIgnoreCase(trimmed) && def != null) {
                raw = def;
            }
        }
        String formatted = StringUtils.formatColors(applyPlaceholders(raw, placeholders));
        return formatted;
    }

    public static List<String> getList(String locale, String key, List<String> def, Object... placeholders) {
        List<String> rawList = fetchList(locale, key, def);
        List<String> resolved = new ArrayList<>(rawList.size());
        for (String line : rawList) {
            resolved.add(StringUtils.formatColors(applyPlaceholders(line, placeholders)));
        }
        return resolved;
    }

    public static String getRaw(String key, String def) {
        return getRaw(defaultLocale, key, def);
    }

    public static String getRaw(String locale, String key, String def) {
        if (key == null || key.isEmpty()) {
            return def;
        }
        String actualLocale = resolveLocale(locale);
        String path = localeKey(actualLocale, key);
        if (!config.contains(path) && key != null && !key.isEmpty() && def != null && path != null && !path.isEmpty()) {
            try {
                config.set(path, def);
            } catch (IllegalArgumentException ex) {
                Core.getInstance().getLogger().log(Level.FINEST,
                        "Unable to auto-populate language key '{0}' at path '{1}': {2}",
                        new Object[]{key, path, ex.getMessage()});
            }
        }
        String value = config.getString(path);
        if (value == null) {
            ConfigurationSection section = config.getSection(path);
            if (section != null) {
                value = section.getString("item");
            } else {
                Object raw = config.get(path);
                if (raw instanceof Map<?, ?>) {
                    Map<?, ?> map = (Map<?, ?>) raw;
                    Object item = map.get("item");
                    if (item != null) {
                        value = item.toString();
                    }
                }
            }
        }
        if (value == null) {
            if (def != null) {
                try {
                    config.set(path, def);
                } catch (IllegalArgumentException ex) {
                    Core.getInstance().getLogger().log(Level.FINEST,
                            "Unable to auto-populate missing language key '{0}' at '{1}': {2}",
                            new Object[]{key, path, ex.getMessage()});
                }
                return def;
            }
            warnMissing(path);
            return "";
        }
        return value;
    }

    public static int getInt(String key, int def) {
        return getInt(defaultLocale, key, def);
    }

    public static int getInt(String locale, String key, int def) {
        if (key == null || key.isEmpty()) {
            return def;
        }
        String actualLocale = resolveLocale(locale);
        String path = localeKey(actualLocale, key);
        if (!config.contains(path)) {
            config.set(path, def);
        }
        return config.getInt(path, def);
    }

    public static int getInt(Profile profile, String key, int def) {
        return getInt(resolveProfileLocale(profile), key, def);
    }

    public static boolean getBoolean(String key, boolean def) {
        return getBoolean(defaultLocale, key, def);
    }

    public static boolean getBoolean(String locale, String key, boolean def) {
        if (key == null || key.isEmpty()) {
            return def;
        }
        String actualLocale = resolveLocale(locale);
        String path = localeKey(actualLocale, key);
        if (!config.contains(path)) {
            config.set(path, def);
        }
        return config.getBoolean(path, def);
    }

    public static boolean getBoolean(Profile profile, String key, boolean def) {
        return getBoolean(resolveProfileLocale(profile), key, def);
    }

    public static List<Integer> getIntegerList(String key, List<Integer> def) {
        return getIntegerList(defaultLocale, key, def);
    }

    public static List<Integer> getIntegerList(String locale, String key, List<Integer> def) {
        if (key == null || key.isEmpty()) {
            return def == null ? Collections.emptyList() : new ArrayList<>(def);
        }
        String actualLocale = resolveLocale(locale);
        String path = localeKey(actualLocale, key);
        if (!config.contains(path) && def != null) {
            config.set(path, def);
        }
        List<Integer> values = config.getIntegerList(path);
        if (values == null || values.isEmpty()) {
            return def == null ? Collections.emptyList() : new ArrayList<>(def);
        }
        return new ArrayList<>(values);
    }

    public static List<Integer> getIntegerList(Profile profile, String key, List<Integer> def) {
        return getIntegerList(resolveProfileLocale(profile), key, def);
    }

    public static ConfigurationSection getSection(String key) {
        return getSection(defaultLocale, key);
    }

    public static ConfigurationSection getSection(Profile profile, String key) {
        return getSection(resolveProfileLocale(profile), key);
    }

    public static ConfigurationSection getSection(String locale, String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        String actualLocale = resolveLocale(locale);
        String path = localeKey(actualLocale, key);
        return config.getSection(path);
    }

    public static List<String> fetchList(String locale, String key, List<String> def) {
        String actualLocale = resolveLocale(locale);
        String path = localeKey(actualLocale, key);
        if (!config.contains(path) && def != null && key != null && !key.isEmpty() && path != null && !path.isEmpty()) {
            config.set(path, def);
        }
        List<String> list = config.getStringList(path);
        if (list == null || list.isEmpty()) {
            if (def != null) {
                return new ArrayList<>(def);
            }
            warnMissing(path);
            return Collections.emptyList();
        }
        return list;
    }

    private static String applyPlaceholders(String input, Object... placeholders) {
        if (input == null || placeholders == null || placeholders.length == 0) {
            return Objects.requireNonNullElse(input, "");
        }
        String result = input;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            Object placeholder = placeholders[i];
            Object value = placeholders[i + 1];
            if (placeholder == null) {
                continue;
            }
            String token = "{" + placeholder + "}";
            result = result.replace(token, Objects.toString(value, ""));
        }
        return result;
    }

    private static String resolveLocale(String raw) {
        if (config == null) {
            return fallbackLocale();
        }
        if (raw == null || raw.isEmpty()) {
            return fallbackLocale();
        }
        String normalized = normalize(raw);
        if (normalized == null || normalized.isEmpty()) {
            return fallbackLocale();
        }
        if (!config.contains(localePath(normalized))) {
            return fallbackLocale();
        }
        return normalized;
    }

    private static String normalize(String locale) {
        return locale == null ? null : locale.trim().replace('-', '_');
    }

    private static String localeKey(String locale, String key) {
        return ROOT + effectiveLocale(locale) + "." + key;
    }

    private static String localePath(String locale) {
        return ROOT + effectiveLocale(locale);
    }

    private static void ensureLocaleSection(String locale) {
        if (config == null) {
            return;
        }
        String effective = effectiveLocale(locale);
        String path = ROOT + effective;
        if (!config.contains(path)) {
            config.createSection(path);
        }
    }

    private static String effectiveLocale(String locale) {
        if (locale == null || locale.isEmpty()) {
            return fallbackLocale();
        }
        return locale;
    }

    private static String fallbackLocale() {
        if (defaultLocale == null || defaultLocale.isEmpty()) {
            defaultLocale = FALLBACK_LOCALE;
        }
        return defaultLocale;
    }

    private static void ensureMenuItemDefaults(String path, int defaultSlot, String def) {
        if (!config.contains(path)) {
            Map<String, Object> defaults = new LinkedHashMap<>();
            defaults.put("slot", defaultSlot);
            defaults.put("item", def);
            config.set(path, defaults);
            return;
        }

        ConfigurationSection section = config.getSection(path);
        if (section != null) {
            if (!section.contains("slot")) {
                config.set(path + ".slot", defaultSlot);
            }
            if (!section.contains("item") && def != null) {
                config.set(path + ".item", def);
            }
            return;
        }

        Object raw = config.get(path);
        if (raw instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) raw;
            if (!map.containsKey("slot")) {
                config.set(path + ".slot", defaultSlot);
            }
            if (!map.containsKey("item") && def != null) {
                config.set(path + ".item", def);
            }
            return;
        }

        if (raw instanceof String) {
            String string = (String) raw;
            config.set(path + ".slot", defaultSlot);
            config.set(path + ".item", string);
            return;
        }

        config.set(path + ".slot", defaultSlot);
        if (def != null) {
            config.set(path + ".item", def);
        }
    }

    private static int parseSlot(Object raw, int def) {
        if (raw instanceof Number) {
            Number number = (Number) raw;
            return number.intValue();
        }
        if (raw instanceof String) {
            String string = (String) raw;
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static void warnMissing(String path) {
        if (MISSING_WARNINGS.add(path)) {
            Bukkit.getLogger().log(Level.WARNING, "[ZonelyCore] Missing language key: {0}", path);
        }
    }

    private static String metaString(String locale, String key, String def) {
        String path = localeKey(locale, "meta." + key);
        if (!config.contains(path) && def != null) {
            config.set(path, def);
        }
        return config.getString(path, def);
    }

    private static List<String> metaList(String locale, String key, List<String> def) {
        String path = localeKey(locale, "meta." + key);
        if (!config.contains(path) && def != null) {
            config.set(path, def);
        }
        List<String> values = config.getStringList(path);
        if (values == null || values.isEmpty()) {
            return def == null ? Collections.emptyList() : new ArrayList<>(def);
        }
        return values;
    }

    private static List<String> format(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> formatted = new ArrayList<>(lines.size());
        for (String line : lines) {
            formatted.add(StringUtils.formatColors(line));
        }
        return formatted;
    }

    public static final class LanguageMeta {
        private final String locale;
        private final String displayName;
        private final List<String> description;
        private final String icon;
        private final String skin;

        private LanguageMeta(String locale, String displayName, List<String> description, String icon, String skin) {
            this.locale = locale;
            this.displayName = displayName;
            this.description = Collections.unmodifiableList(description);
            this.icon = icon;
            this.skin = skin;
        }

        public String locale() {
            return locale;
        }

        public String displayName() {
            return displayName;
        }

        public List<String> description() {
            return description;
        }

        public String icon() {
            return icon;
        }

        public String skin() {
            return skin;
        }
    }

    public static final class MenuItemDefinition {
        private final int slot;
        private final String item;

        private final List<String> commands;
        private final boolean close;
        private final String permission;

        public MenuItemDefinition(int slot, String item) {
            this(slot, item, Collections.emptyList(), false, "");
        }

        public MenuItemDefinition(int slot, String item, List<String> commands) {
            this(slot, item, commands, false, "");
        }

        public MenuItemDefinition(int slot, String item, List<String> commands, boolean close, String permission) {
            this.slot = slot;
            this.item = item;
            this.commands = commands == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(commands));
            this.close = close;
            this.permission = permission == null ? "" : permission;
        }

        public int getSlot() {
            return slot;
        }

        public String getItem() {
            return item;
        }

        public int slot() {
            return slot;
        }

        public String item() {
            return item;
        }

        public List<String> getCommands() {
            return commands;
        }

        public List<String> commands() {
            return commands;
        }

        public boolean shouldClose() {
            return close;
        }

        public boolean close() {
            return close;
        }

        public String getPermission() {
            return permission;
        }

        public String permission() {
            return permission;
        }
    }

}
