package dev.zonely.whiteeffect.hologram;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.holograms.HologramLibrary;
import dev.zonely.whiteeffect.libraries.holograms.api.Hologram;
import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import dev.zonely.whiteeffect.store.LastCreditLeaderboardService;
import dev.zonely.whiteeffect.store.LastCreditLeaderboardService.LeaderboardEntry;
import dev.zonely.whiteeffect.store.LastCreditsMenuManager.Category;
import dev.zonely.whiteeffect.utils.ProtocolLibUtils;
import dev.zonely.whiteeffect.utils.StringUtils;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class LastCreditHologramManager implements Listener {

   private static final List<String> DEFAULT_LINES = Arrays.asList(
         "&6&lLast Credit Loaders",
         "{category_buttons}",
         "&7Category: {category_display}",
         "{entry_1}",
         "{entry_2}",
         "{entry_3}",
         "&8(Click to cycle views)");
   private static final String DEFAULT_ENTRY = "&e#{rank} &f{player} &7- &6{amount}";
   private static final String DEFAULT_EMPTY_ENTRY = "&7#{rank} - &8No data.";
   private static final String DEFAULT_BUTTON_ACTIVE = "&a&l> &f{raw_label} &a&l<";
   private static final String DEFAULT_BUTTON_INACTIVE = "&7{raw_label}";
   private static final String DEFAULT_BUTTON_SEPARATOR = "&8 | ";

   private static LastCreditHologramManager instance;

   private final Core plugin;
   private final ProtocolManager protocolManager;
   private final LastCreditLeaderboardService leaderboardService;
   private final Map<Category, String> categoryLabels = new EnumMap<>(Category.class);
   private final Map<UUID, Category> viewerCategory = new ConcurrentHashMap<>();
   private final Map<UUID, List<String>> lastSentLines = new ConcurrentHashMap<>();
   private final BukkitTask updateTask;

   private final List<String> templates;
   private final String entryFormat;
   private final String emptyEntry;
   private final int entrySlots;
   private final String activeButtonTemplate;
   private final String inactiveButtonTemplate;
   private final String buttonSeparator;

   private final Map<UUID, Location> pendingPlacement = new ConcurrentHashMap<>();

   private Hologram hologram;
   private List<HologramLine> hologramLines;
   private Location location;

   private final Map<String, HoloInstance> instances = new ConcurrentHashMap<>();

   private LastCreditHologramManager(Core plugin,
         LastCreditLeaderboardService leaderboardService) {
      instance = this;

      this.plugin = plugin;
      this.protocolManager = ProtocolLibrary.getProtocolManager();
      this.leaderboardService = leaderboardService;

      List<String> configured = LanguageManager.getList("holograms.last-credits.lines", DEFAULT_LINES);
      this.templates = (configured == null || configured.isEmpty()) ? new ArrayList<>(DEFAULT_LINES) : configured;

      this.entryFormat = LanguageManager.get("holograms.last-credits.entry-format", DEFAULT_ENTRY);
      this.emptyEntry = LanguageManager.get("holograms.last-credits.empty-entry", DEFAULT_EMPTY_ENTRY);
      this.entrySlots = detectEntrySlots(this.templates);
      this.activeButtonTemplate = LanguageManager.get("holograms.last-credits.buttons.active", DEFAULT_BUTTON_ACTIVE);
      this.inactiveButtonTemplate = LanguageManager.get("holograms.last-credits.buttons.inactive",
            DEFAULT_BUTTON_INACTIVE);
      this.buttonSeparator = LanguageManager.get("holograms.last-credits.buttons.separator", DEFAULT_BUTTON_SEPARATOR);

      for (Category category : Category.values()) {
         String key = category.name().toLowerCase(Locale.ROOT);
         String displayDefault = StringUtils.formatColors("&e" +
               StringUtils.capitalise(key.replace('_', ' ')));
         categoryLabels.put(category, LanguageManager.get(
               "holograms.last-credits.categories." + key,
               displayDefault));
      }

      Bukkit.getPluginManager().registerEvents(this, plugin);

      readLegacyLocation();
      readInstanceLocations();

      updateTask = new BukkitRunnable() {
         @Override
         public void run() {
            boolean any = false;
            if (!instances.isEmpty()) {
               any = true;
               for (Player player : Bukkit.getOnlinePlayers()) {
                  for (HoloInstance inst : instances.values()) {
                     if (inst.hologram != null && inst.hologram.isSpawned()) {
                        inst.pushLines(player);
                     }
                  }
               }
            }

            if (!any) {
               if (hologram == null || hologramLines == null || !hologram.isSpawned()) {
                  return;
               }
               for (Player player : Bukkit.getOnlinePlayers()) {
                  pushLines(player);
               }
            }
         }
      }.runTaskTimer(plugin, 40L, 40L);
   }

   public static void init(Core plugin, LastCreditLeaderboardService leaderboardService) {
      if (instance == null) {
         instance = new LastCreditHologramManager(plugin, leaderboardService);
      }
   }

   public static LastCreditHologramManager getInstance() {
      return instance;
   }

   public static void shutdown() {
      if (instance != null) {
         instance.destroy();
         instance = null;
      }
   }

   public static void reload(Core plugin, LastCreditLeaderboardService leaderboardService) {
      shutdown();
      if (plugin != null && leaderboardService != null) {
         init(plugin, leaderboardService);
      }
   }

   private void destroy() {
      if (updateTask != null) {
         updateTask.cancel();
      }
      if (hologram != null) {
         try {
            hologram.despawn();
            HologramLibrary.removeHologram(hologram);
         } catch (Throwable ignored) {
         }
      }
      hologram = null;
      hologramLines = null;
      viewerCategory.clear();
      lastSentLines.clear();
      for (HoloInstance inst : instances.values()) {
         try {
            if (inst.hologram != null) {
               inst.hologram.despawn();
               HologramLibrary.removeHologram(inst.hologram);
            }
         } catch (Throwable ignored) {
         }
      }
      instances.clear();
   }

   public boolean setLocation(Location location, boolean save) {
      if (location == null || location.getWorld() == null) {
         return false;
      }
      spawnAt(location);
      if (save) {
         persistLocation(location);
      }
      return true;
   }

   public boolean setLocation(String id, Location location, boolean save) {
      if (location == null || location.getWorld() == null) {
         return false;
      }
      spawnAt(id, location);
      if (save) {
         persistLocation(id, location);
      }
      return true;
   }

   public void beginIdPrompt(Player player, Location at) {
      if (player == null || at == null || at.getWorld() == null) {
         return;
      }
      pendingPlacement.put(player.getUniqueId(), at.clone());
      LanguageManager.send(player,
            "commands.core.hologram.prompt-id",
            "{prefix}&eType an id for this hologram (e.g. lobby1). Type 'cancel' to abort.",
            "prefix", LanguageManager.get("prefix.lobby", "&3Lobby &8->> "));
   }

   public Location getLocation() {
      return location;
   }

   private void readLegacyLocation() {
      ConfigurationSection section = plugin.getConfig("spawn").getSection("holograms.last-credits");
      if (section == null) {
         return;
      }
      String worldName = section.getString("world", "");
      if (worldName == null || worldName.trim().isEmpty()) {
         return;
      }
      World world = Bukkit.getWorld(worldName);
      if (world == null) {
         plugin.getLogger().warning("[LastCreditHologram] World '" + worldName + "' is not loaded.");
         return;
      }
      this.location = new Location(
            world,
            section.getDouble("x"),
            section.getDouble("y"),
            section.getDouble("z"),
            (float) section.getDouble("yaw"),
            (float) section.getDouble("pitch"));
      spawnAt(this.location);
   }

   private void readInstanceLocations() {
      ConfigurationSection list = plugin.getConfig("spawn").getSection("holograms.last-credits.instances");
      if (list == null) {
         return;
      }
      for (String id : list.getKeys(false)) {
         ConfigurationSection section = list.getConfigurationSection(id);
         if (section == null)
            continue;
         String worldName = section.getString("world", "");
         if (worldName == null || worldName.trim().isEmpty())
            continue;
         World world = Bukkit.getWorld(worldName);
         if (world == null) {
            plugin.getLogger().warning(
                  "[LastCreditHologram] World '" + worldName + "' is not loaded for hologram id '" + id + "'.");
            continue;
         }
         Location loc = new Location(world,
               section.getDouble("x"),
               section.getDouble("y"),
               section.getDouble("z"),
               (float) section.getDouble("yaw"),
               (float) section.getDouble("pitch"));
         spawnAt(id, loc);
      }
   }

   private void persistLocation(Location loc) {
      if (loc.getWorld() == null) {
         return;
      }
      ConfigurationSection section = plugin.getConfig("spawn").getSection("holograms.last-credits");
      if (section == null) {
         section = plugin.getConfig("spawn").getRawConfig().createSection("holograms.last-credits");
      }
      section.set("world", loc.getWorld().getName());
      section.set("x", loc.getX());
      section.set("y", loc.getY());
      section.set("z", loc.getZ());
      section.set("yaw", loc.getYaw());
      section.set("pitch", loc.getPitch());
      plugin.getConfig("spawn").save();
   }

   private void persistLocation(String id, Location loc) {
      if (loc.getWorld() == null)
         return;
      if (id == null || id.equalsIgnoreCase("default")) {
         persistLocation(loc);
         return;
      }
      ConfigurationSection base = plugin.getConfig("spawn").getSection("holograms.last-credits.instances");
      if (base == null) {
         base = plugin.getConfig("spawn").getRawConfig().createSection("holograms.last-credits.instances");
      }
      ConfigurationSection section = base.getConfigurationSection(id);
      if (section == null) {
         section = base.createSection(id);
      }
      section.set("world", loc.getWorld().getName());
      section.set("x", loc.getX());
      section.set("y", loc.getY());
      section.set("z", loc.getZ());
      section.set("yaw", loc.getYaw());
      section.set("pitch", loc.getPitch());
      plugin.getConfig("spawn").save();
   }

   private void spawnAt(Location loc) {
      if (hologram != null) {
         try {
            hologram.despawn();
            HologramLibrary.removeHologram(hologram);
         } catch (Throwable ignored) {
         }
      }
      this.location = loc.clone();
      List<String> initialLines;
      try {
         List<LeaderboardEntry> initial = leaderboardService.getTopEntries(Category.RECENT);
         Map<String, String> replacements = buildReplacements(Category.RECENT, initial, this.location);
         initialLines = new ArrayList<>(templates.size());
         for (String t : templates) {
            initialLines.add(StringUtils.formatColors(apply(t, replacements)));
         }
      } catch (Throwable ex) {
         initialLines = new ArrayList<>(templates.size());
         for (String t : templates) {
            initialLines.add(t.contains("{") ? "" : StringUtils.formatColors(t));
         }
      }
      hologram = HologramLibrary.createHologram(loc.clone(), initialLines.toArray(new String[0])).spawn();
      hologramLines = captureLines(hologram);

      hologramLines.forEach(line -> line.setTouchable(this::onTouch));
      Bukkit.getOnlinePlayers().forEach(this::pushLines);
      lastSentLines.clear();
      viewerCategory.clear();
   }

   private void spawnAt(String id, Location loc) {
      HoloInstance existing = instances.remove(id);
      if (existing != null && existing.hologram != null) {
         try {
            existing.hologram.despawn();
            HologramLibrary.removeHologram(existing.hologram);
         } catch (Throwable ignored) {
         }
      }
      HoloInstance inst = new HoloInstance(id);
      inst.spawnAt(loc);
      instances.put(id, inst);
   }

   public boolean remove(boolean clearSaved) {
      boolean existed = this.hologram != null;
      if (this.hologram != null) {
         try {
            this.hologram.despawn();
            HologramLibrary.removeHologram(this.hologram);
         } catch (Throwable ignored) {
         }
      }
      this.hologram = null;
      this.hologramLines = null;
      this.location = null;
      this.viewerCategory.clear();
      this.lastSentLines.clear();

      if (clearSaved) {
         try {
            plugin.getConfig("spawn").getRawConfig().set("holograms.last-credits", null);
            plugin.getConfig("spawn").save();
         } catch (Throwable ignored) {
         }
      }
      return existed;
   }

   public boolean remove(String id, boolean clearSaved) {
      if (id == null || id.equalsIgnoreCase("default")) {
         return remove(clearSaved);
      }
      HoloInstance inst = instances.remove(id);
      if (inst == null) {
         return false;
      }
      try {
         if (inst.hologram != null) {
            inst.hologram.despawn();
            HologramLibrary.removeHologram(inst.hologram);
         }
      } catch (Throwable ignored) {
      }
      if (clearSaved) {
         try {
            plugin.getConfig("spawn").getRawConfig().set("holograms.last-credits.instances." + id, null);
            plugin.getConfig("spawn").save();
         } catch (Throwable ignored) {
         }
      }
      return true;
   }

   private List<HologramLine> captureLines(Hologram hologram) {
      List<HologramLine> ordered = new ArrayList<>();
      for (int i = 1;; i++) {
         HologramLine line = hologram.getLine(i);
         if (line == null) {
            break;
         }
         ordered.add(line);
      }
      return ordered;
   }

   private void onTouch(Player player) {
      UUID playerId = player.getUniqueId();
      Category current = viewerCategory.computeIfAbsent(playerId, uuid -> Category.RECENT);
      Category[] values = Category.values();
      Category next = values[(current.ordinal() + 1) % values.length];
      viewerCategory.put(playerId, next);
      lastSentLines.remove(playerId);
      LanguageManager.send(player,
            "holograms.last-credits.category-switched",
            "&aNow viewing {category}",
            "category", getCategoryLabel(next));
      try {
         Location effectLocation = player.getLocation().add(0.0, 0.8, 0.0);
         for (int i = 0; i < 6; i++) {
            player.getWorld().playEffect(effectLocation, Effect.HAPPY_VILLAGER, 0);
         }
      } catch (Throwable ignored) {
      }
      pushLines(player);

      for (UUID uuid : new ArrayList<>(lastSentLines.keySet())) {
         if (uuid.equals(playerId)) {
            continue;
         }
         lastSentLines.remove(uuid);
         Player viewer = Bukkit.getPlayer(uuid);
         if (viewer != null && viewer.isOnline()) {
            pushLines(viewer);
         }
      }
   }

   private void pushLines(Player player) {
      if (hologramLines == null || hologramLines.isEmpty()) {
         return;
      }

      List<String> rendered = renderLines(player);
      List<String> cache = lastSentLines.computeIfAbsent(player.getUniqueId(), id -> initialiseCache(rendered.size()));

      for (int i = 0; i < Math.min(rendered.size(), hologramLines.size()); i++) {
         String text = rendered.get(i);
         if (!text.equals(cache.get(i))) {
            sendMetadata(protocolManager, player, hologramLines.get(i), text);
            cache.set(i, text);
         }
      }
   }

   private List<String> renderLines(Player player) {
      Category category = viewerCategory.computeIfAbsent(player.getUniqueId(), uuid -> Category.RECENT);
      List<LeaderboardEntry> entries = leaderboardService.getTopEntries(category);
      Map<String, String> replacements = buildReplacements(category, entries, this.location);

      List<String> resolved = new ArrayList<>(templates.size());
      for (String template : templates) {
         String line = apply(template, replacements);
         try {
            if (PlaceholderAPI.containsPlaceholders(line)) {
               line = PlaceholderAPI.setPlaceholders(player, line);
            }
         } catch (Throwable ignored) {
         }
         resolved.add(StringUtils.formatColors(line));
      }
      return resolved;
   }

   private int detectEntrySlots(List<String> templates) {
      int max = 3;
      if (templates == null || templates.isEmpty()) {
         return max;
      }
      Pattern pattern = Pattern.compile("\\{entry_(\\d+)\\}");
      for (String template : templates) {
         Matcher matcher = pattern.matcher(template);
         while (matcher.find()) {
            try {
               int value = Integer.parseInt(matcher.group(1));
               max = Math.max(max, value);
            } catch (NumberFormatException ignored) {
            }
         }
      }
      return max;
   }

   public Map<String, String> buildReplacements(Category category, List<LeaderboardEntry> entries,
         Location baseLocation) {
      Map<String, String> replacements = new HashMap<>();
      replacements.put("{category}", category.name());
      replacements.put("{category_display}", getCategoryLabel(category));
      replacements.put("{category_buttons}", renderCategoryButtons(category));
      replacements.put("{location_world}",
            baseLocation != null && baseLocation.getWorld() != null ? baseLocation.getWorld().getName() : "");
      for (Category value : Category.values()) {
         String key = value.name().toLowerCase(Locale.ROOT);
         replacements.put("{category_button_" + key + "}", formatCategoryButton(value, category));
      }

      if (entries == null)
         entries = new ArrayList<>();
      for (int i = 1; i <= entrySlots; i++) {
         LeaderboardEntry entry = i <= entries.size() ? entries.get(i - 1) : null;
         String entryKey = "{entry_" + i + "}";
         if (entry != null) {
            String formatted = entryFormat
                  .replace("{rank}", Integer.toString(entry.getPosition()))
                  .replace("{player}", entry.getUsername())
                  .replace("{amount}", entry.getFormattedAmount())
                  .replace("{amount_raw}", Long.toString(entry.getAmount()));
            replacements.put(entryKey, formatted);
            replacements.put("{entry_" + i + "_name}", entry.getUsername());
            replacements.put("{entry_" + i + "_amount}", entry.getFormattedAmount());
            replacements.put("{entry_" + i + "_amount_raw}", Long.toString(entry.getAmount()));
         } else {
            replacements.put(entryKey, emptyEntry.replace("{rank}", Integer.toString(i)));
            replacements.put("{entry_" + i + "_name}", "-");
            replacements.put("{entry_" + i + "_amount}", "0");
            replacements.put("{entry_" + i + "_amount_raw}", "0");
         }
      }
      return replacements;
   }

   public String apply(String template, Map<String, String> replacements) {
      String line = template;
      for (Map.Entry<String, String> entry : replacements.entrySet()) {
         line = line.replace(entry.getKey(), entry.getValue());
      }
      return line;
   }

   private String renderCategoryButtons(Category active) {
      StringBuilder builder = new StringBuilder();
      Category[] values = Category.values();
      for (int i = 0; i < values.length; i++) {
         if (i > 0) {
            builder.append(buttonSeparator);
         }
         builder.append(formatCategoryButton(values[i], active));
      }
      return builder.toString();
   }

   private String formatCategoryButton(Category target, Category active) {
      String label = getCategoryLabel(target);
      String rawLabel = StringUtils.stripColors(label);
      if (rawLabel == null || rawLabel.trim().isEmpty()) {
         rawLabel = label;
      }
      String template = target == active ? activeButtonTemplate : inactiveButtonTemplate;
      return template
            .replace("{label}", label)
            .replace("{raw_label}", rawLabel == null ? "" : rawLabel);
   }

   private List<String> initialiseCache(int size) {
      List<String> cache = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
         cache.add("");
      }
      return cache;
   }

   public void sendMetadata(ProtocolManager manager, Player player, HologramLine line, String text) {
      if (line == null || line.getArmor() == null || line.getArmor().isDead()) {
         return;
      }

      PacketContainer packet = manager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
      packet.getIntegers().write(0, line.getArmor().getId());

      WrappedDataWatcher watcher = ProtocolLibUtils.createDataWatcher(line.getArmor().getEntity());
      if (watcher == null) {
         return;
      }
      BaseComponent[] legacyComponents = TextComponent.fromLegacyText(text);
      if (legacyComponents.length == 0) {
         legacyComponents = new BaseComponent[] { new TextComponent("") };
      }
      String json = ComponentSerializer.toString(legacyComponents);
      Object baseComponent = WrappedChatComponent.fromJson(json).getHandle();

      try {
         WrappedDataWatcher.Serializer chat = WrappedDataWatcher.Registry.getChatComponentSerializer(true);
         watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(2, chat), Optional.of(baseComponent));
      } catch (Throwable throwable) {
         WrappedDataWatcher.Serializer legacy = WrappedDataWatcher.Registry.get(String.class);
         watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(2, legacy), text);
      }

      try {
         WrappedDataWatcher.Serializer boolSerializer = WrappedDataWatcher.Registry.get(Boolean.class);
         watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(3, boolSerializer), !text.isEmpty());
      } catch (Throwable throwable) {
         WrappedDataWatcher.Serializer byteSerializer = WrappedDataWatcher.Registry.get(Byte.class);
         watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(3, byteSerializer),
               (byte) (text.isEmpty() ? 0 : 1));
      }

      ProtocolLibUtils.writeMetadata(packet, watcher);

      try {
         manager.sendServerPacket(player, packet);
      } catch (Exception ex) {
         plugin.getLogger().log(Level.WARNING, "[LastCreditHologram] Failed to send update: " + ex.getMessage());
      }
   }

   public String getCategoryLabel(Category category) {
      return categoryLabels.getOrDefault(category, category.name());
   }

   public Category getViewerCategory(Player player) {
      if (player == null) {
         return Category.RECENT;
      }
      UUID uuid = player.getUniqueId();
      Category value = viewerCategory.get(uuid);
      if (value != null) {
         return value;
      }
      for (HoloInstance instance : instances.values()) {
         Category instValue = instance.viewerCategory.get(uuid);
         if (instValue != null) {
            return instValue;
         }
      }
      return Category.RECENT;
   }

   public void setViewerCategory(Player player, Category category) {
      if (player == null || category == null) {
         return;
      }
      UUID uuid = player.getUniqueId();
      viewerCategory.put(uuid, category);
      lastSentLines.remove(uuid);
      if (hologram != null && hologramLines != null && hologram.isSpawned()) {
         pushLines(player);
      }
      for (HoloInstance instance : instances.values()) {
         instance.setViewerCategory(player, category);
      }
   }

   public LastCreditLeaderboardService getLeaderboardService() {
      return leaderboardService;
   }

   public List<String> getTemplates() {
      return templates;
   }

   public ProtocolManager getProtocolManager() {
      return protocolManager;
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      viewerCategory.remove(uuid);
      lastSentLines.remove(uuid);
      pendingPlacement.remove(uuid);
      for (HoloInstance instance : instances.values()) {
         instance.viewerCategory.remove(uuid);
         instance.lastSentLines.remove(uuid);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      Location where = pendingPlacement.get(uuid);
      if (where == null) {
         return;
      }
      event.setCancelled(true);
      String msg = event.getMessage() == null ? "" : event.getMessage().trim();
      if (msg.equalsIgnoreCase("cancel") || msg.equalsIgnoreCase("iptal")) {
         pendingPlacement.remove(uuid);
         LanguageManager.send(event.getPlayer(),
               "commands.core.hologram.cancelled",
               "{prefix}&cHologram placement cancelled.",
               "prefix", LanguageManager.get("prefix.lobby", "&3Lobby &8->> "));
         return;
      }
      String id = msg.isEmpty() ? "default" : msg.replaceAll("[^A-Za-z0-9_-]", "").toLowerCase(java.util.Locale.ROOT);
      boolean ok = setLocation(id, where, true);
      pendingPlacement.remove(uuid);
      LanguageManager.send(event.getPlayer(),
            ok ? "commands.core.hologram.set" : "commands.core.hologram.failed",
            ok ? "{prefix}&aLast-credit leaderboard hologram saved at your location."
                  : "{prefix}&cUnable to place the hologram here.",
            "prefix", LanguageManager.get("prefix.lobby", "&3Lobby &8->> "));
   }
}

class HoloInstance {
   final String id;
   Hologram hologram;
   List<HologramLine> hologramLines;
   Location location;
   final Map<UUID, Category> viewerCategory = new ConcurrentHashMap<>();
   final Map<UUID, List<String>> lastSentLines = new ConcurrentHashMap<>();

   HoloInstance(String id) {
      this.id = id;
   }

   void spawnAt(Location loc) {
      LastCreditHologramManager mgr = LastCreditHologramManager.getInstance();
      if (mgr == null) {
         return;
      }
      this.location = loc.clone();
      List<String> templates = mgr.getTemplates();
      if (templates == null)
         templates = new ArrayList<>();
      List<String> initialLines;
      try {
         List<LeaderboardEntry> initial = mgr.getLeaderboardService().getTopEntries(Category.RECENT);
         Map<String, String> replacements = mgr.buildReplacements(Category.RECENT, initial, this.location);
         initialLines = new ArrayList<>(templates.size());
         for (String t : templates) {
            initialLines.add(StringUtils.formatColors(mgr.apply(t, replacements)));
         }
      } catch (Throwable ex) {
         initialLines = new ArrayList<>(templates.size());
         for (String t : templates) {
            initialLines.add(t.contains("{") ? "" : StringUtils.formatColors(t));
         }
      }
      this.hologram = HologramLibrary.createHologram(loc.clone(), initialLines.toArray(new String[0])).spawn();
      this.hologramLines = captureLines(this.hologram);
      this.hologramLines.forEach(line -> line.setTouchable(this::onTouch));
      Bukkit.getOnlinePlayers().forEach(this::pushLines);
      this.lastSentLines.clear();
      this.viewerCategory.clear();
   }

   private List<HologramLine> captureLines(Hologram hologram) {
      List<HologramLine> ordered = new ArrayList<>();
      for (int i = 1;; i++) {
         HologramLine line = hologram.getLine(i);
         if (line == null)
            break;
         ordered.add(line);
      }
      return ordered;
   }

   private void onTouch(Player player) {
      LastCreditHologramManager mgr = LastCreditHologramManager.getInstance();
      if (mgr == null)
         return;
      Category current = mgr.getViewerCategory(player);
      Category[] values = Category.values();
      Category next = values[(current.ordinal() + 1) % values.length];
      mgr.setViewerCategory(player, next);
      LanguageManager.send(player,
            "holograms.last-credits.category-switched",
            "&aNow viewing {category}",
            "category", mgr.getCategoryLabel(next));
      try {
         Location effectLocation = player.getLocation().add(0.0, 0.8, 0.0);
         for (int i = 0; i < 6; i++) {
            player.getWorld().playEffect(effectLocation, Effect.HAPPY_VILLAGER, 0);
         }
      } catch (Throwable ignored) {
      }
   }

   public void pushLines(Player player) {
      LastCreditHologramManager mgr = LastCreditHologramManager.getInstance();
      if (mgr == null)
         return;
      if (hologramLines == null || hologramLines.isEmpty())
         return;
      List<String> rendered = renderLines(player);
      List<String> cache = lastSentLines.computeIfAbsent(player.getUniqueId(), id -> initialiseCache(rendered.size()));
      for (int i = 0; i < Math.min(rendered.size(), hologramLines.size()); i++) {
         String text = rendered.get(i);
         if (!text.equals(cache.get(i))) {
            mgr.sendMetadata(mgr.getProtocolManager(), player, hologramLines.get(i), text);
            cache.set(i, text);
         }
      }
   }

   private List<String> renderLines(Player player) {
      LastCreditHologramManager mgr = LastCreditHologramManager.getInstance();
      if (mgr == null)
         return new ArrayList<>();
      List<String> templates = mgr.getTemplates();
      if (templates == null)
         templates = new ArrayList<>();
      Category category = mgr.getViewerCategory(player);
      viewerCategory.put(player.getUniqueId(), category);
      List<LeaderboardEntry> entries = mgr.getLeaderboardService().getTopEntries(category);
      Map<String, String> replacements = mgr.buildReplacements(category, entries, this.location);
      List<String> resolved = new ArrayList<>(templates.size());
      for (String template : templates) {
         String line = mgr.apply(template, replacements);
         try {
            if (PlaceholderAPI.containsPlaceholders(line)) {
               line = PlaceholderAPI.setPlaceholders(player, line);
            }
         } catch (Throwable ignored) {
         }
         resolved.add(StringUtils.formatColors(line));
      }
      return resolved;
   }

   void setViewerCategory(Player player, Category category) {
      if (player == null || category == null) {
         return;
      }
      UUID uuid = player.getUniqueId();
      viewerCategory.put(uuid, category);
      lastSentLines.remove(uuid);
      if (hologram != null && hologram.isSpawned()) {
         pushLines(player);
      }
   }

   private List<String> initialiseCache(int size) {
      List<String> cache = new ArrayList<>(size);
      for (int i = 0; i < size; i++)
         cache.add("");
      return cache;
   }
}
