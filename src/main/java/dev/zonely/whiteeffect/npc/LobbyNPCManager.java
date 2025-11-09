package dev.zonely.whiteeffect.npc;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.google.common.collect.ImmutableList;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.MinecraftVersion;
import dev.zonely.whiteeffect.libraries.holograms.HologramLibrary;
import dev.zonely.whiteeffect.libraries.holograms.api.Hologram;
import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import dev.zonely.whiteeffect.libraries.npclib.NPCLibrary;
import dev.zonely.whiteeffect.libraries.npclib.api.event.NPCLeftClickEvent;
import dev.zonely.whiteeffect.libraries.npclib.api.event.NPCRightClickEvent;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPCAnimation;
import dev.zonely.whiteeffect.libraries.npclib.npc.skin.Skin;
import dev.zonely.whiteeffect.libraries.npclib.npc.skin.SkinnableEntity;
import dev.zonely.whiteeffect.nms.NMS;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import dev.zonely.whiteeffect.store.LastCreditsMenuManager;
import dev.zonely.whiteeffect.support.SupportManager;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.ProtocolLibUtils;
import dev.zonely.whiteeffect.utils.StringUtils;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.menus.MenuProfile;
import dev.zonely.whiteeffect.menus.MenuDeliveriesProfile;
import dev.zonely.whiteeffect.menus.reports.MenuReportsList;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Villager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;


public final class LobbyNPCManager implements Listener {

  private static final String NAMELESS_TEAM = "wNPCS";

  private static LobbyNPCManager instance;

  private final Core plugin;
  private final ProtocolManager protocolManager;
  private final WConfig spawnConfig;

  private final Map<String, ManagedNPC> activeNPCs = new HashMap<>();

  private static final Map<UUID, ManagedNPC> ENTITY_UUID_INDEX = new ConcurrentHashMap<>();
  private static final Map<Integer, ManagedNPC> ENTITY_ID_INDEX   = new ConcurrentHashMap<>();
  private static final boolean MOB_DISPLAY_ENABLED = isMobDisplaySupported();

  private BukkitTask placeholderTask;
  private BukkitTask tablistTask;

  private LobbyNPCManager(Core plugin) {
    this.plugin = plugin;
    this.protocolManager = ProtocolLibrary.getProtocolManager();
    this.spawnConfig = plugin.getConfig("spawn");

    Bukkit.getPluginManager().registerEvents(this, plugin);
    registerUseEntitySniffer();
    startPlaceholderPusher();
    startTablistCleaner();
    loadFromConfig();
  }

  public static void init(Core plugin) {
    if (instance == null) instance = new LobbyNPCManager(plugin);
  }

  public static LobbyNPCManager get() { return instance; }

  public static LobbyNPCManager getInstance() { return instance; }

  public static void shutdown() {
    if (instance == null) return;
    instance.destroy();
    instance = null;
  }

  public static void reload(Core plugin) {
    shutdown();
    init(plugin);
  }

  private void destroy() {
    try { protocolManager.removePacketListeners(plugin); } catch (Throwable ignored) {}
    if (placeholderTask != null) placeholderTask.cancel();
    if (tablistTask != null) tablistTask.cancel();
    for (ManagedNPC m : activeNPCs.values()) m.destroy();
    activeNPCs.clear();
    ENTITY_ID_INDEX.clear();
    ENTITY_UUID_INDEX.clear();
  }

  private void registerUseEntitySniffer() {
    try {
      protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.USE_ENTITY) {
        @Override public void onPacketReceiving(PacketEvent event) {
          try {
            Player player = event.getPlayer();
            if (player == null || !player.isOnline()) return;
            PacketContainer packet = event.getPacket();
            Integer entityId = readEntityId(packet);
            if (entityId == null) return;
            ManagedNPC managed = ENTITY_ID_INDEX.get(entityId);
            if (managed == null) return;
            if (!isInteractAction(packet)) return;
            Bukkit.getScheduler().runTask(plugin, () -> managed.execute(player));
          } catch (Throwable ignored) {}
        }
      });
    } catch (Throwable ignored) {}
  }

  private void startPlaceholderPusher() {
    placeholderTask = new BukkitRunnable() {
      @Override public void run() {
        if (activeNPCs.isEmpty()) return;
        for (ManagedNPC m : activeNPCs.values()) m.syncHitbox();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
          for (ManagedNPC m : activeNPCs.values()) m.pushPlaceholders(viewer, protocolManager);
        }
      }
    }.runTaskTimer(plugin, 20L, 40L);
  }

  private void startTablistCleaner() {
    tablistTask = new BukkitRunnable() {
      @Override public void run() {
        for (ManagedNPC m : activeNPCs.values()) {
          try {
            if (m.npc != null && m.npc.getEntity() instanceof Player) {
              ((Player) m.npc.getEntity()).setPlayerListName("");
            }
          } catch (Throwable ignored) {}
        }
      }
    }.runTaskTimer(plugin, 40L, 100L);
  }

  private void loadFromConfig() {
    for (NPCType t : NPCType.values()) {
      Location def = readLocation(t, "default");
      if (def != null) spawnNPC(t, "default", def, false);

      ConfigurationSection inst = spawnConfig.getSection("npcs." + t.getId() + ".instances");
      if (inst != null) for (String id : inst.getKeys(false)) {
        Location loc = readLocation(t, id);
        if (loc != null) spawnNPC(t, id, loc, false);
      }
    }
  }

  private Location readLocation(NPCType t, String id) {
    String base = (id == null || id.equalsIgnoreCase("default"))
        ? "npcs." + t.getId()
        : "npcs." + t.getId() + ".instances." + id;

    ConfigurationSection s = spawnConfig.getSection(base);
    if (s == null) return null;

    String world = s.getString("world", "");
    if (world == null || world.isEmpty()) return null;
    World w = Bukkit.getWorld(world);
    if (w == null) {
      plugin.getLogger().warning("[LobbyNPC] World '" + world + "' is not loaded for " + t.getId() + " (" + id + ")");
      return null;
    }
    Location loc = new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
        (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    return sanitizeLocation(loc);
  }

  private void saveLocation(NPCType t, String id, Location loc) {
    String base = (id == null || id.equalsIgnoreCase("default"))
        ? "npcs." + t.getId()
        : "npcs." + t.getId() + ".instances." + id;

    Location clean = sanitizeLocation(loc);
    YamlConfiguration raw = spawnConfig.getRawConfig();
    raw.set(base + ".world", clean.getWorld().getName());
    raw.set(base + ".x", clean.getX());
    raw.set(base + ".y", clean.getY());
    raw.set(base + ".z", clean.getZ());
    raw.set(base + ".yaw", clean.getYaw());
    raw.set(base + ".pitch", clean.getPitch());
    spawnConfig.save();
  }

  private void removeLocation(NPCType t, String id) {
    String base = (id == null || id.equalsIgnoreCase("default"))
        ? "npcs." + t.getId()
        : "npcs." + t.getId() + ".instances." + id;
    spawnConfig.getRawConfig().set(base, null);
    spawnConfig.save();
  }

  public boolean spawnNPC(NPCType type, Location loc, boolean persist) {
    return spawnNPC(type, "default", loc, persist);
  }

  public boolean spawnNPC(NPCType type, String id, Location loc, boolean persist) {
    if (loc == null || loc.getWorld() == null) return false;

    Location spawnLoc = sanitizeLocation(loc);

    String key = key(type, id);
    ManagedNPC prev = activeNPCs.remove(key);
    if (prev != null) prev.destroy();

    NPCSettings settings = type.readSettings();

    boolean useMobDisplay = MOB_DISPLAY_ENABLED
        && settings.displayType() != null
        && settings.displayType() != EntityType.PLAYER;

    NPC npc = null;
    Entity displayEntity = null;

    if (useMobDisplay) {
      displayEntity = spawnDisplayEntity(settings.displayType(), spawnLoc);
      if (displayEntity == null) {
        useMobDisplay = false;
      }
    }

    if (!useMobDisplay) {
      npc = NPCLibrary.createNPC(EntityType.PLAYER, settings.internalName());
      if (!npc.spawn(spawnLoc)) {
        plugin.getLogger().warning("[LobbyNPC] Failed to spawn " + type.getId() + " at " + spawnLoc);
        return false;
      }
      displayEntity = npc.getEntity();
    }

    orientNpc(npc, displayEntity, spawnLoc);
    final NPC orientNpc = npc;
    final Entity orientDisplay = displayEntity;
    Bukkit.getScheduler().runTask(plugin, () -> orientNpc(orientNpc, orientDisplay, spawnLoc));

    if (!useMobDisplay && displayEntity instanceof Player) {
      Player p = (Player) displayEntity;
      try { p.setCustomNameVisible(false); } catch (Throwable ignored) {}
      try { p.setCustomName(null); } catch (Throwable ignored) {}
      try { p.setPlayerListName(""); } catch (Throwable ignored) {}
      ItemStack hand = settings.handItem();
      if (hand != null) try { p.getInventory().setItemInHand(hand); } catch (Throwable ignored) {}
      try { Player.class.getMethod("setGravity", boolean.class).invoke(p, false); } catch (Throwable ignored) {}
    }

    if (npc != null) {
      applySkin(npc, settings);
    }

    Hologram hologram = HologramLibrary
        .createHologram(spawnLoc.clone().add(0, settings.hologramOffset(), 0), settings.templates().toArray(new String[0]))
        .spawn();
    List<HologramLine> lines = captureLines(hologram);

    ManagedNPC m = new ManagedNPC(plugin, type, npc, displayEntity, hologram, lines, settings.templates(), settings, useMobDisplay);
    m.configureMobAppearance();
    activeNPCs.put(key, m);

    registerEntityIndices(m);
    Bukkit.getScheduler().runTaskLater(plugin, () -> registerEntityIndices(m), 1L);

    Bukkit.getOnlinePlayers().forEach(m::applyNametagHider);

    if (persist) saveLocation(type, id, spawnLoc);
    return true;
  }

  public boolean removeNPC(NPCType type) { return removeNPC(type, "default"); }

  public boolean removeNPC(NPCType type, String id) {
    String key = key(type, id);
    ManagedNPC m = activeNPCs.remove(key);
    if (m == null) return false;
    m.destroy();
    removeLocation(type, id);
    return true;
  }

  private static boolean isMobDisplaySupported() {
    try {
      MinecraftVersion current = MinecraftVersion.getCurrentVersion();
      return current.newerThan(new MinecraftVersion(1, 17, 0));
    } catch (Throwable ignored) {
      try {
        String raw = Bukkit.getBukkitVersion().split("-")[0];
        String[] parts = raw.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return major > 1 || minor >= 17;
      } catch (Throwable ignored2) {
        return true;
      }
    }
  }

  private static String key(NPCType t, String id) {
    String suffix = (id == null || id.isEmpty()) ? "default" : id.toLowerCase(Locale.ROOT);
    return t.getId() + ":" + suffix;
  }

  private static Location sanitizeLocation(Location loc) {
    Location clone = loc.clone();
    float pitch = clone.getPitch();
    if (pitch > 90f) pitch = 90f;
    if (pitch < -90f) pitch = -90f;
    clone.setPitch(pitch);
    float yaw = clone.getYaw();
    while (yaw < -180f) yaw += 360f;
    while (yaw >= 180f) yaw -= 360f;
    clone.setYaw(yaw);
    return clone;
  }

  @EventHandler public void onRight(NPCRightClickEvent e) {
    for (ManagedNPC m : activeNPCs.values()) if (m.matches(e.getNPC())) { m.execute(e.getPlayer()); return; }
  }
  @EventHandler public void onLeft(NPCLeftClickEvent e) {
    for (ManagedNPC m : activeNPCs.values()) if (m.matches(e.getNPC())) { m.execute(e.getPlayer()); return; }
  }

  @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
  public void onInteract(PlayerInteractEntityEvent e) {
    if (handleEntityInteraction(e.getPlayer(), e.getRightClicked())) e.setCancelled(true);
  }
  @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
  public void onInteractAt(PlayerInteractAtEntityEvent e) {
    if (handleEntityInteraction(e.getPlayer(), e.getRightClicked())) e.setCancelled(true);
  }
  @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
  public void onDamage(EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Player && handleEntityInteraction((Player) e.getDamager(), e.getEntity()))
      e.setCancelled(true);
  }

  @EventHandler public void onQuit(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    for (ManagedNPC m : activeNPCs.values()) m.resetFor(id);
  }
  @EventHandler public void onJoin(PlayerJoinEvent e) {
    Player p = e.getPlayer();
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      for (ManagedNPC m : activeNPCs.values()) {
        m.applyNametagHider(p);
        try {
          if (m.npc.getEntity() instanceof Player) {
            ((Player) m.npc.getEntity()).setPlayerListName("");
          }
        } catch (Throwable ignored) {}
        m.refreshSkinFor(p);
      }
    }, 5L);
  }

  public enum NPCType {
    WEB_PROFILE("web-profile", "&bWeb Profile", "VILLAGER", "PLAINS:LIBRARIAN",
      ImmutableList.of("&fWeb Profile", "&7Click to open"),
      player -> { Profile pr = Profile.getProfile(player.getName()); if (pr != null) new MenuProfile(pr); }),
    AUCTIONS("auctions", "&dAuctions", "VILLAGER", "DESERT:WEAPONSMITH",
      ImmutableList.of("&dAuctions", "&7Click to open"),
      player -> {
        Core core = Core.getInstance();
        if (!core.isAuctionsEnabled() || core.getAuctionMenuManager() == null) {
          String prefix = LanguageManager.get("prefix.lobby", "&3Lobby &8>> ");
          LanguageManager.send(player, "commands.auctions.disabled",
              "{prefix}&cAuctions are disabled.",
              "prefix", prefix);
          return;
        }
        core.getAuctionMenuManager().openAuctionMenu(player);
      }),
    LAST_CREDITS("last-credits", "&6Latest Top-Ups", "VILLAGER", "SAVANNA:FARMER",
      ImmutableList.of("&6Last Credit Loaders", "&7Click to open"),
      player -> {
        Core core = Core.getInstance();
        if (!core.isLastCreditsEnabled() || core.getCreditsManager() == null) {
          String prefix = LanguageManager.get("prefix.lobby", "&3Lobby &8>> ");
          LanguageManager.send(player, "menus.last-credits.disabled",
              "{prefix}&cLast credits module is disabled.",
              "prefix", prefix);
          return;
        }
        core.getCreditsManager().openMenu(player, LastCreditsMenuManager.Category.RECENT, 0);
      }),
    REPORTS("reports", "&cReports", "VILLAGER", "TAIGA:CLERIC",
      ImmutableList.of("&cReport System", "&7Click to open"),
      player -> {
        Core core = Core.getInstance();
        if (!core.isReportsEnabled() || core.getReportService() == null) {
          LanguageManager.send(player, "commands.reports.disabled",
              "{prefix}&cReports module is disabled.",
              "prefix", LanguageManager.get("prefix.punish", ""));
          return;
        }
        if (player.hasPermission("zonely.reports.view")) {
          new MenuReportsList(core, player, core.getReportService());
        } else {
          LanguageManager.send(player, "commands.reports.no-permission",
              "{prefix}&cYou do not have permission.",
              "prefix", LanguageManager.get("prefix.punish", ""));
        }
      }),
    SUPPORTS("supports", "&dSupport Desk", "VILLAGER", "SWAMP:LIBRARIAN",
      ImmutableList.of("&dSupport Desk", "&7Click to open"),
      player -> {
        Core core = Core.getInstance();
        SupportManager manager = core.getSupportManager();
        if (manager == null) {
          String prefix = LanguageManager.get("support.prefix", "&dSupport &8>> ");
          LanguageManager.send(player, "support.disabled",
              "{prefix}&cSupport module is disabled.",
              "prefix", prefix);
          return;
        }
        if (player.hasPermission("zonely.support.staff")) {
          manager.openStaffPanel(player, false);
        } else {
          player.performCommand("support");
        }
      }),
    DELIVERIES("deliveries", "&aReward Courier", "VILLAGER", "SNOW:FLETCHER",
      ImmutableList.of("&aReward Courier", "&7Click to open"),
      player -> { Profile pr = Profile.getProfile(player.getName()); if (pr != null) new MenuDeliveriesProfile(pr); }),
    WEB_STORE("web-store", "&aWeb Store", "VILLAGER", "JUNGLE:BUTCHER",
      ImmutableList.of("&aWeb Store", "&7Click to open"),
      player -> Core.getInstance().getProductMenuManager().openCategoryMenu(player, 0));

    private final String id; private final String defName; private final EntityType mobDisplay; private final String defaultVillagerVariant; private final List<String> defHolo; private final NPCAction action;
    NPCType(String id, String defName, String mobDisplayKey, String villagerVariant, List<String> defHolo, NPCAction action) {
      this.id=id;
      this.defName=defName;
      this.mobDisplay=resolveMobDisplay(mobDisplayKey);
      this.defaultVillagerVariant=villagerVariant;
      this.defHolo=defHolo;
      this.action=action;
    }

    public String getId() { return id; }
    public EntityType getMobDisplay() { return mobDisplay; }
    public String getDefaultVillagerVariant() { return defaultVillagerVariant; }

    public static NPCType fromId(String input) {
      if (input == null || input.isEmpty()) return null;
      for (NPCType type : values()) {
        if (type.id.equalsIgnoreCase(input)) return type;
      }
      return null;
    }

    public static String listAsString() {
      StringBuilder builder = new StringBuilder();
      for (NPCType type : values()) {
        if (builder.length() > 0) builder.append(", ");
        builder.append(type.id);
      }
      return builder.toString();
    }

    private static EntityType resolveMobDisplay(String key) {
      if (key == null || key.trim().isEmpty()) return null;
      String upper = key.trim().toUpperCase(Locale.ROOT);
      try {
        return EntityType.valueOf(upper);
      } catch (Throwable ignored) {
        return null;
      }
    }

    private NPCSettings readSettings() {
      String base = "npc." + id;
      String display = LanguageManager.get(base + ".display-name", defName);
      String item = LanguageManager.get(base + ".item", "");
      List<String> holo = LanguageManager.getList(base + ".holograms", defHolo);
      String villagerVariant = LanguageManager.get(base + ".villager-variant", defaultVillagerVariant);

      String v = LanguageManager.getRaw(LanguageManager.getDefaultLocale(), base + ".skin.value", "");
      String s = LanguageManager.getRaw(LanguageManager.getDefaultLocale(), base + ".skin.signature", "");
      String vTL = LanguageManager.getRaw(LanguageManager.getDefaultLocale(), base + ".skin.tl_value", "");
      if ((v == null || v.isEmpty()) && vTL != null && !vTL.isEmpty()) v = vTL;
      String skinName = LanguageManager.getRaw(LanguageManager.getDefaultLocale(), base + ".skin.username", "");

      double off = 2.25D;
      try {
        String raw = LanguageManager.getRaw(LanguageManager.getDefaultLocale(), base + ".hologram-offset", "2.25");
        if (raw != null && !raw.trim().isEmpty()) off = Double.parseDouble(raw);
      } catch (Throwable ignored) {}
      ItemStack hand = null;
      if (item != null && !item.trim().isEmpty()) {
        try { hand = BukkitUtils.deserializeItemStack(item); } catch (Throwable ex) {
          Core.getInstance().getLogger().log(Level.WARNING, "[LobbyNPC] item parse failed for " + id + ": " + ex.getMessage());
        }
      }
      return new NPCSettings(this, display, hand, holo, v, s, skinName, off, villagerVariant);
    }
    public void handle(Player p) { action.handle(p); }
  }

  private static final class ManagedNPC {
    private final NPCType type;
    private final NPC npc;
    private final Entity displayEntity;
    private final Hologram holo;
    private final List<HologramLine> lines;
    private final List<String> templates;
    private final NPCSettings settings;
    private final boolean mobDisplay;
    private final Map<UUID, List<String>> lastSent = new HashMap<>();
    private final List<UUID> registeredUuids = new ArrayList<>();
    private final List<Integer> registeredIds = new ArrayList<>();
    private final Core plugin;
    private Slime hitbox;

    ManagedNPC(Core plugin, NPCType type, NPC npc, Entity displayEntity, Hologram holo,
               List<HologramLine> lines, List<String> templates, NPCSettings settings, boolean mobDisplay) {
      this.plugin = plugin;
      this.type = type;
      this.npc = npc;
      this.displayEntity = displayEntity != null ? displayEntity : (npc != null ? npc.getEntity() : null);
      this.holo = holo;
      this.lines = lines;
      this.templates = templates;
      this.settings = settings;
      this.mobDisplay = mobDisplay;
    }

    void configureMobAppearance() {
      if (!mobDisplay) return;
      Entity entity = getDisplayEntity();
      if (entity == null) return;
      configureMobDisplayEntity(entity, settings.villagerVariant());
    }

    private Entity primaryEntity() {
      if (displayEntity != null && !displayEntity.isDead()) {
        return displayEntity;
      }
      return npc != null ? npc.getEntity() : null;
    }

    void ensureHitbox() {
      if (mobDisplay) return;
      Entity anchor = primaryEntity();
      if (anchor == null || anchor.getWorld() == null) {
        removeHitbox();
        return;
      }
      if (hitbox == null || hitbox.isDead()) {
        spawnHitbox(anchor);
      }
    }

    void syncHitbox() {
      if (mobDisplay) return;
      ensureHitbox();
      Slime current = getHitbox();
      Entity anchor = primaryEntity();
      if (current == null || anchor == null || anchor.getWorld() == null) return;
      try {
        Location target = anchor.getLocation();
        Location present = current.getLocation();
        if (!present.getWorld().equals(target.getWorld()) || present.distanceSquared(target) > 0.01D) {
          Location withYaw = target.clone();
          current.teleport(withYaw);
          setEntityRotation(current, withYaw.getYaw(), withYaw.getPitch());
        } else {
          setEntityRotation(current, target.getYaw(), target.getPitch());
        }
        current.setVelocity(new Vector(0, 0, 0));
      } catch (Throwable ignored) {}
    }

    Slime getHitbox() { return hitbox != null && !hitbox.isDead() ? hitbox : null; }

    private void spawnHitbox(Entity anchor) {
      try {
        Location loc = anchor.getLocation().clone().add(0.0D, 0.1D, 0.0D);
        Slime slime;
        try {
          slime = anchor.getWorld().spawn(loc, Slime.class);
        } catch (Throwable fallback) {
          slime = (Slime) anchor.getWorld().spawnEntity(loc, EntityType.SLIME);
        }
        if (slime == null) {
          hitbox = null;
          return;
        }
        hitbox = slime;
        LobbyNPCManager.callBooleanSetter(slime, "setSilent", true);
        LobbyNPCManager.callBooleanSetter(slime, "setAI", false);
        LobbyNPCManager.callBooleanSetter(slime, "setGravity", false);
        LobbyNPCManager.callBooleanSetter(slime, "setInvulnerable", true);
        LobbyNPCManager.callBooleanSetter(slime, "setCollidable", false);
        LobbyNPCManager.callBooleanSetter(slime, "setRemoveWhenFarAway", false);
        try { slime.setSize(4); } catch (Throwable ignored) {}
        try { slime.setVelocity(new Vector(0, 0, 0)); } catch (Throwable ignored) {}
        try { slime.setCustomNameVisible(false); } catch (Throwable ignored) {}

        boolean invisibleApplied = trySetInvisible(slime, true);
        if (invisibleApplied) {
          try { slime.removePotionEffect(PotionEffectType.INVISIBILITY); } catch (Throwable ignored) {}
        } else {
          PotionEffect invis = createInvisibilityEffect();
          if (invis != null) {
            try { slime.addPotionEffect(invis, true); } catch (Throwable ignored) {}
          }
        }
        try {
          setEntityRotation(slime, loc.getYaw(), loc.getPitch());
        } catch (Throwable ignored) {}
      } catch (Throwable ignored) {
        hitbox = null;
      }
    }

    private void setEntityRotation(Entity entity, float yaw, float pitch) {
      if (entity == null) return;
      try {
        Method m = entity.getClass().getMethod("setRotation", float.class, float.class);
        m.invoke(entity, yaw, pitch);
        return;
      } catch (Throwable ignored) {}
      try {
        Method m = entity.getClass().getMethod("setYawPitch", float.class, float.class);
        m.invoke(entity, yaw, pitch);
      } catch (Throwable ignored) {}
      try {
        Method m = entity.getClass().getMethod("setHeadYaw", float.class);
        m.invoke(entity, yaw);
      } catch (Throwable ignored) {}
      try {
        Method m = entity.getClass().getMethod("setBodyYaw", float.class);
        m.invoke(entity, yaw);
      } catch (Throwable ignored) {}
    }

    private boolean trySetInvisible(Entity entity, boolean invisible) {
      if (entity == null) return false;
      try {
        Method setter = entity.getClass().getMethod("setInvisible", boolean.class);
        setter.setAccessible(true);
        setter.invoke(entity, invisible);
        try {
          Method getter = entity.getClass().getMethod("isInvisible");
          getter.setAccessible(true);
          Object result = getter.invoke(entity);
          if (result instanceof Boolean) {
            return ((Boolean) result) == invisible;
          }
        } catch (Throwable ignored) {}
        return true;
      } catch (Throwable ignored) {
        return false;
      }
    }

    private PotionEffect createInvisibilityEffect() {
      PotionEffectType type = PotionEffectType.INVISIBILITY;
      int duration = Integer.MAX_VALUE;
      int amplifier = 0;
      PotionEffect effect = tryCreatePotionEffect(type, duration, amplifier, true, false, false);
      if (effect != null) return effect;
      effect = tryCreatePotionEffect(type, duration, amplifier, true, false);
      if (effect != null) return effect;
      effect = tryCreatePotionEffect(type, duration, amplifier, true);
      if (effect != null) return effect;
      try {
        return new PotionEffect(type, duration, amplifier);
      } catch (Throwable ignored) {}
      try {
        return type.createEffect(duration, amplifier);
      } catch (Throwable ignored) {}
      return null;
    }

    private PotionEffect tryCreatePotionEffect(PotionEffectType type, int duration, int amplifier, boolean... flags) {
      try {
        Class<?>[] paramTypes = new Class<?>[3 + flags.length];
        Object[] args = new Object[3 + flags.length];
        paramTypes[0] = PotionEffectType.class;
        paramTypes[1] = int.class;
        paramTypes[2] = int.class;
        args[0] = type;
        args[1] = duration;
        args[2] = amplifier;
        for (int i = 0; i < flags.length; i++) {
          paramTypes[3 + i] = boolean.class;
          args[3 + i] = flags[i];
        }
        Constructor<PotionEffect> ctor = PotionEffect.class.getConstructor(paramTypes);
        return ctor.newInstance(args);
      } catch (Throwable ignored) {
        return null;
      }
    }

    private void removeHitbox() {
      if (hitbox == null) return;
      try {
        UUID uuid = hitbox.getUniqueId();
        ENTITY_UUID_INDEX.remove(uuid);
        registeredUuids.remove(uuid);
      } catch (Throwable ignored) {}
      try {
        Integer id = hitbox.getEntityId();
        ENTITY_ID_INDEX.remove(id);
        registeredIds.remove(id);
      } catch (Throwable ignored) {}
      try { NMS.removeFromWorld(hitbox); } catch (Throwable ignored) { try { hitbox.remove(); } catch (Throwable ignored2) {} }
      hitbox = null;
    }

    void pushPlaceholders(Player viewer, ProtocolManager pm) {
      if (lines == null || lines.isEmpty()) return;
      List<String> cache = lastSent.computeIfAbsent(viewer.getUniqueId(), u -> {
        List<String> c = new ArrayList<>(lines.size());
        for (int i=0;i<lines.size();i++) c.add("");
        return c;
      });

      for (int i=0;i<lines.size() && i<templates.size(); i++) {
        HologramLine hl = lines.get(i);
        if (hl==null || hl.getArmor()==null || hl.getArmor().isDead()) continue;

        String text = templates.get(i);
        try { if (PlaceholderAPI.containsPlaceholders(text)) text = PlaceholderAPI.setPlaceholders(viewer, text); } catch (Throwable ignored) {}
        text = StringUtils.formatColors(text);

        if (!text.equals(cache.get(i))) {
          sendMeta(pm, viewer, hl, text);
          cache.set(i, text);
        }
      }
    }

    void execute(Player p) {
      if (p==null) return;
      try { type.handle(p); } catch (Throwable t) {
        Core.getInstance().getLogger().log(Level.SEVERE, "[LobbyNPC] action error for "+type.getId(), t);
      }
      if (!mobDisplay && settings.playAnimation() && npc != null) {
        try { npc.playAnimation(NPCAnimation.SWING_ARM); } catch (Throwable ignored) {}
      }
    }

    boolean matches(NPC other) {
      if (npc == null || other == null) return false;
      if (npc == other) return true;
      try {
        UUID a = npc.getUUID(), b = other.getUUID();
        if (a!=null && b!=null && a.equals(b)) return true;
      } catch (Throwable ignored) {}
      try { return npc.getEntity().getEntityId() == other.getEntity().getEntityId(); } catch (Throwable ignored) { return false; }
    }

    void destroy() {
      unregisterEntities();
      removeHitbox();
      if (mobDisplay) {
        try { if (displayEntity != null && displayEntity.isValid()) displayEntity.remove(); } catch (Throwable ignored) {}
      } else {
        try { if (npc != null) { npc.destroy(); } } catch (Throwable ignored) {}
      }
      try { if (holo != null) { holo.despawn(); HologramLibrary.removeHologram(holo); } } catch (Throwable ignored) {}
      removeNametagForAll(npc);
    }

    void applyNametagHider(Player viewer) {
      if (npc == null) return;
      hideName(viewer, npc);
    }

    void resetFor(UUID playerId) {
      if (playerId == null) return;
      lastSent.remove(playerId);
    }

    void refreshSkinFor(Player viewer) {
      if (npc == null || viewer == null || settings == null) return;
      Skin skin = buildSkinFromSettings(settings);
      if (skin == null) return;

      SkinnableEntity sk = NMS.getSkinnable(npc.getEntity());
      if (sk == null) return;
      try {
        sk.setSkin(skin);
        sk.getSkinTracker().updateViewer(viewer);
      } catch (Throwable t) {
        plugin.getLogger().log(Level.WARNING,
              "[LobbyNPC] Failed to refresh skin for viewer " + viewer.getName() + ": " + t.getMessage());
      }
    }

    void rememberEntity(Entity entity) {
      if (entity == null) return;
      try { registeredUuids.add(entity.getUniqueId()); } catch (Throwable ignored) {}
      try { registeredIds.add(entity.getEntityId()); } catch (Throwable ignored) {}
    }

    void unregisterEntities() {
      for (UUID id : registeredUuids) {
        if (id != null) ENTITY_UUID_INDEX.remove(id);
      }
      for (Integer id : registeredIds) {
        if (id != null) ENTITY_ID_INDEX.remove(id);
      }
      registeredUuids.clear();
      registeredIds.clear();
    }

    private void sendMeta(ProtocolManager pm, Player player, HologramLine line, String text) {
      PacketContainer packet = pm.createPacket(PacketType.Play.Server.ENTITY_METADATA);
      packet.getIntegers().write(0, line.getArmor().getId());

      WrappedDataWatcher watcher = ProtocolLibUtils.createDataWatcher(line.getArmor().getEntity());
      if (watcher == null) return;

      BaseComponent[] components = TextComponent.fromLegacyText(text);
      if (components.length==0) components = new BaseComponent[]{ new TextComponent("") };
      String json = ComponentSerializer.toString(components);
      Object base = WrappedChatComponent.fromJson(json).getHandle();

      try {
        WrappedDataWatcher.Serializer chat = WrappedDataWatcher.Registry.getChatComponentSerializer(true);
        watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(2, chat), Optional.of(base));
      } catch (Throwable t) {
        WrappedDataWatcher.Serializer legacy = WrappedDataWatcher.Registry.get(String.class);
        watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(2, legacy), text);
      }
      try {
        WrappedDataWatcher.Serializer bool = WrappedDataWatcher.Registry.get(Boolean.class);
        watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(3, bool), !text.isEmpty());
      } catch (Throwable t) {
        WrappedDataWatcher.Serializer byt = WrappedDataWatcher.Registry.get(Byte.class);
        watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(3, byt), (byte)(text.isEmpty()?0:1));
      }

      ProtocolLibUtils.writeMetadata(packet, watcher);
      try { pm.sendServerPacket(player, packet); } catch (Exception ex) {
        Core.getInstance().getLogger().log(Level.WARNING, "[LobbyNPC] hologram metadata push failed: "+ex.getMessage());
      }
    }

    static Skin buildSkinFromSettings(NPCSettings s) {
      if (!s.skinValue().isEmpty() && !s.skinSignature().isEmpty()) {
        try { return Skin.fromData(s.skinValue(), s.skinSignature()); }
        catch (Throwable ignored) { }
      }
      if (!s.skinValue().isEmpty()) {
        Skin sk = trySkinValueOnly(s.skinValue());
        if (sk != null) return sk;
      }
      if (!s.skinUsername().isEmpty()) {
        Skin byName = trySkinFromUsername(s.skinUsername());
        if (byName != null) return byName;
      }
      return null;
    }

    private static Skin trySkinValueOnly(String value) {
      try {
        Method m = Skin.class.getMethod("fromData", String.class);
        return (Skin) m.invoke(null, value);
      } catch (Throwable ignored) {}
      try {
        Method m = Skin.class.getMethod("fromBase64", String.class);
        return (Skin) m.invoke(null, value);
      } catch (Throwable ignored) {}
      try {
        Method m = Skin.class.getMethod("fromData", String.class, String.class);
        return (Skin) m.invoke(null, value, null);
      } catch (Throwable ignored) {}
      try {
        Class<?> propClz = Class.forName("com.mojang.authlib.properties.Property");
        Constructor<?> ctor = propClz.getConstructor(String.class, String.class, String.class);
        Object prop = ctor.newInstance("textures", value, null);
        Method m = Skin.class.getMethod("fromProperty", propClz);
        return (Skin) m.invoke(null, prop);
      } catch (Throwable ignored) {}
      return null;
    }

    private static Skin trySkinFromUsername(String name) {
      try {
        Method m = Skin.class.getMethod("fromUsername", String.class);
        return (Skin) m.invoke(null, name);
      } catch (Throwable ignored) {}
      try {
        Method m = Skin.class.getMethod("fromPlayer", String.class);
        return (Skin) m.invoke(null, name);
      } catch (Throwable ignored) {}
      try {
        Method m = Skin.class.getMethod("fromName", String.class);
        return (Skin) m.invoke(null, name);
      } catch (Throwable ignored) {}
      return null;
    }

    NPC getNpc() {
      return npc;
    }

    Entity getDisplayEntity() {
      return displayEntity;
    }

    Entity getPrimaryAnchor() {
      return primaryEntity();
    }

    boolean isMobDisplay() {
      return mobDisplay;
    }
  }

  private static final class NPCSettings {
    private final NPCType type;
    private final String display;
    private final ItemStack hand;
    private final List<String> templates;
    private final String skinValue;
    private final String skinSig;
    private final String skinUsername;
    private final double holoOffset;
    private final String villagerVariant;
    private final EntityType mobDisplay;
    NPCSettings(NPCType type, String display, ItemStack hand, List<String> templates, String skinValue, String skinSig, String skinUsername, double off, String villagerVariant) {
      this(type, display, hand, templates, skinValue, skinSig, skinUsername, off, villagerVariant, type.getMobDisplay());
    }
    NPCSettings(NPCType type, String display, ItemStack hand, List<String> templates, String skinValue, String skinSig, String skinUsername, double off, String villagerVariant, EntityType mobDisplay) {
      this.type=type; this.display=display; this.hand=hand; this.templates=templates;
      this.skinValue = sanitiseSkinComponent(skinValue);
      this.skinSig = sanitiseSkinComponent(skinSig);
      this.skinUsername = skinUsername == null ? "" : skinUsername.trim();
      this.holoOffset=off;
      this.villagerVariant = villagerVariant == null ? "" : villagerVariant.trim();
      this.mobDisplay = mobDisplay;
    }
    String internalName() {
      String plain = StringUtils.stripColors(display);
      if (plain==null || plain.trim().isEmpty()) plain = type.getId().toUpperCase(Locale.ROOT);
      plain = plain.replaceAll("[^A-Za-z0-9_]", "");
      if (plain.length()>16) plain = plain.substring(0,16);
      return plain;
    }
    ItemStack handItem() { return hand==null?null:hand.clone(); }
    List<String> templates() { return templates; }
    double hologramOffset() { return holoOffset; }
    String skinValue(){return skinValue;} String skinSignature(){return skinSig;} String skinUsername(){return skinUsername;}
    boolean playAnimation(){return true;}
    EntityType displayType(){ return mobDisplay; }
    String villagerVariant(){ return villagerVariant; }
  }

  @FunctionalInterface private interface NPCAction { void handle(Player player); }

  private static List<HologramLine> captureLines(Hologram h) {
    List<HologramLine> out = new ArrayList<>();
    for (int i=1;;i++) { HologramLine l = h.getLine(i); if (l==null) break; out.add(l); }
    return out;
  }

  private static String sanitiseSkinComponent(String raw) {
    if (raw == null) return "";
    return raw.replaceAll("\\s+", "");
  }

  private void orientNpc(NPC npc, Entity displayEntity, Location target) {
    if (target == null) return;
    Location desired = target.clone();

    Entity npcEntity = null;
    if (npc != null) {
      try { npcEntity = npc.getEntity(); } catch (Throwable ignored) {}
    }

    if (displayEntity != null && displayEntity != npcEntity) {
      try {
        displayEntity.teleport(desired);
      } catch (Throwable ignored) {}
      setEntityRotation(displayEntity, desired.getYaw(), desired.getPitch());
      try { displayEntity.setVelocity(new Vector(0, 0, 0)); } catch (Throwable ignored) {}
    }

    if (npc != null) {
      orientNpc(npc, desired);
    }
  }

  private void orientNpc(NPC npc, Location target) {
    if (npc == null || target == null) return;
    Entity entity = npc.getEntity();
    if (entity == null) return;
    Location desired = target.clone();
    boolean handled = false;
    try {
      dev.zonely.whiteeffect.nms.universal.entity.PacketNPCManager.handleTeleport(entity, desired, true);
      handled = true;
    } catch (Throwable ignored) { }
    if (!handled) {
      try {
        entity.teleport(desired);
        handled = true;
      } catch (Throwable ignored) { }
    }
    if (!handled) return;
    setEntityRotation(entity, desired.getYaw(), desired.getPitch());
    try { entity.setVelocity(new Vector(0, 0, 0)); } catch (Throwable ignored) {}
  }

  private void setEntityRotation(Entity entity, float yaw, float pitch) {
    if (entity == null) return;
    try {
      Method m = entity.getClass().getMethod("setRotation", float.class, float.class);
      m.invoke(entity, yaw, pitch);
      return;
    } catch (Throwable ignored) {}
    try {
      Method m = entity.getClass().getMethod("setYawPitch", float.class, float.class);
      m.invoke(entity, yaw, pitch);
    } catch (Throwable ignored) {}
    try {
      Method m = entity.getClass().getMethod("setHeadYaw", float.class);
      m.invoke(entity, yaw);
    } catch (Throwable ignored) {}
    try {
      Method m = entity.getClass().getMethod("setBodyYaw", float.class);
      m.invoke(entity, yaw);
    } catch (Throwable ignored) {}
  }

  private static void callBooleanSetter(Object target, String method, boolean value) {
    if (target == null) return;
    try {
      Method m = target.getClass().getMethod(method, boolean.class);
      m.setAccessible(true);
      m.invoke(target, value);
    } catch (Throwable ignored) {}
  }

  private static void configureMobDisplayEntity(Entity entity, String variant) {
    if (entity == null) return;
    if (!(entity instanceof Villager)) return;
    Villager villager = (Villager) entity;
    try { villager.setCustomNameVisible(false); } catch (Throwable ignored) {}
    try { villager.setCustomName(null); } catch (Throwable ignored) {}
    try { villager.setAdult(); } catch (Throwable ignored) {}
    callBooleanSetter(villager, "setAgeLock", true);
    callBooleanSetter(villager, "setAware", false);
    callBooleanSetter(villager, "setBreed", false);
    callBooleanSetter(villager, "setInvulnerable", true);
    callBooleanSetter(villager, "setSilent", true);
    callBooleanSetter(villager, "setCollidable", false);
    applyVillagerVariant(villager, variant);
  }

  private static void applyVillagerVariant(Villager villager, String variant) {
    if (villager == null || variant == null) return;
    String trimmed = variant.trim();
    if (trimmed.isEmpty()) return;
    String typeKey = null;
    String professionKey = trimmed;
    int sep = trimmed.indexOf(':');
    if (sep >= 0) {
      typeKey = trimmed.substring(0, sep).trim();
      professionKey = trimmed.substring(sep + 1).trim();
    }
    applyVillagerType(villager, typeKey);
    applyVillagerProfession(villager, professionKey);
    try {
      Method setLevel = findMethod(villager.getClass(), "setVillagerLevel", int.class);
      if (setLevel != null) setLevel.invoke(villager, 5);
    } catch (Throwable ignored) {}
  }

  private static void applyVillagerType(Villager villager, String typeKey) {
    if (villager == null || typeKey == null || typeKey.isEmpty()) return;
    try {
      Class<?> typeClass = Class.forName("org.bukkit.entity.Villager$Type");
      Object enumValue = resolveEnum(typeClass, typeKey);
      if (enumValue == null) return;
      if (!applyVillagerDataMutation(villager, "withType", typeClass, enumValue)) {
        Method setter = findMethod(villager.getClass(), "setVillagerType", typeClass);
        if (setter != null) setter.invoke(villager, enumValue);
      }
    } catch (Throwable ignored) {}
  }

  private static void applyVillagerProfession(Villager villager, String professionKey) {
    if (villager == null || professionKey == null || professionKey.isEmpty()) return;
    try {
      Class<?> profClass = Class.forName("org.bukkit.entity.Villager$Profession");
      Object enumValue = resolveEnum(profClass, professionKey);
      if (enumValue == null) return;
      if (!applyVillagerDataMutation(villager, "withProfession", profClass, enumValue)) {
        Method setter = findMethod(villager.getClass(), "setProfession", profClass);
        if (setter != null) setter.invoke(villager, enumValue);
      }
    } catch (Throwable ignored) {}
  }

  private static boolean applyVillagerDataMutation(Villager villager, String method, Class<?> paramType, Object paramValue) {
    try {
      Class<?> dataClass = Class.forName("org.bukkit.entity.Villager$VillagerData");
      Method get = findMethod(villager.getClass(), "getVillagerData");
      Method set = findMethod(villager.getClass(), "setVillagerData", dataClass);
      if (get == null || set == null) return false;
      Object data = get.invoke(villager);
      if (data == null) return false;
      Method mutator = findMethod(dataClass, method, paramType);
      if (mutator == null) return false;
      Object mutated = mutator.invoke(data, paramValue);
      if (mutated == null) return false;
      Method levelMutator = findMethod(dataClass, "withLevel", int.class);
      if (levelMutator != null) mutated = levelMutator.invoke(mutated, 5);
      set.invoke(villager, mutated);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static Object resolveEnum(Class<?> enumClass, String key) {
    if (enumClass == null || key == null) return null;
    try {
      return Enum.valueOf((Class<? extends Enum>) enumClass, key.toUpperCase(Locale.ROOT));
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static Method findMethod(Class<?> target, String name, Class<?>... parameters) {
    if (target == null || name == null) return null;
    try {
      Method m = target.getMethod(name, parameters);
      m.setAccessible(true);
      return m;
    } catch (Throwable ignored) {
      try {
        Method m = target.getDeclaredMethod(name, parameters);
        m.setAccessible(true);
        return m;
      } catch (Throwable ignored2) {
        return null;
      }
    }
  }

  private Entity spawnDisplayEntity(EntityType type, Location loc) {
    if (type == null || type == EntityType.PLAYER || loc == null || loc.getWorld() == null) {
      return null;
    }
    try {
      Entity entity = loc.getWorld().spawnEntity(loc, type);
      try { entity.setCustomNameVisible(false); } catch (Throwable ignored) {}
      try { entity.setCustomName(null); } catch (Throwable ignored) {}
      LobbyNPCManager.callBooleanSetter(entity, "setSilent", true);
      LobbyNPCManager.callBooleanSetter(entity, "setInvulnerable", true);
      LobbyNPCManager.callBooleanSetter(entity, "setGravity", false);
      LobbyNPCManager.callBooleanSetter(entity, "setCollidable", false);
      if (entity instanceof LivingEntity) {
        LivingEntity living = (LivingEntity) entity;
        LobbyNPCManager.callBooleanSetter(living, "setAI", false);
        LobbyNPCManager.callBooleanSetter(living, "setRemoveWhenFarAway", false);
        LobbyNPCManager.callBooleanSetter(living, "setCanPickupItems", false);
      }
      return entity;
    } catch (Throwable t) {
      plugin.getLogger().log(Level.WARNING,
          "[LobbyNPC] Failed to spawn display entity for " + type.name() + ": " + t.getMessage());
      return null;
    }
  }

  private void applySkin(NPC npc, NPCSettings s) {
    Skin skin = ManagedNPC.buildSkinFromSettings(s);
    if (skin == null) return;

    SkinnableEntity sk = NMS.getSkinnable(npc.getEntity());
    if (sk == null) return;
    try {
      sk.setSkin(skin);
      sk.getSkinTracker().updateNearbyViewers(64.0D);
    } catch (Throwable t) {
      plugin.getLogger().log(Level.WARNING, "[LobbyNPC] skin apply failed for "+s.type.getId()+": "+t.getMessage(), t);
    }
  }

  private Integer readEntityId(PacketContainer packet) {
    try { return packet.getIntegers().readSafely(0); } catch (Throwable ignored) {}
    return null;
  }

  private boolean isInteractAction(PacketContainer packet) {
    try {
      EnumWrappers.EntityUseAction action = packet.getEntityUseActions().read(0);
      if (action == null) return true;
      switch (action) {
        case INTERACT:
        case INTERACT_AT:
        case ATTACK:
          return true;
        default:
          return false;
      }
    } catch (Throwable ignored) {}
    return true;
  }

  private void registerEntityIndices(ManagedNPC m) {
    m.unregisterEntities();
    m.syncHitbox();
    Entity anchor = null;
    try {
      anchor = m.getPrimaryAnchor();
      if (anchor != null) {
        ENTITY_UUID_INDEX.put(anchor.getUniqueId(), m);
        ENTITY_ID_INDEX.put(anchor.getEntityId(), m);
        m.rememberEntity(anchor);
      }
    } catch (Throwable ignored) {}
    try {
      NPC npc = m.getNpc();
      if (npc != null) {
        Entity entity = npc.getEntity();
        if (entity != null && entity != anchor) {
          ENTITY_UUID_INDEX.put(entity.getUniqueId(), m);
          ENTITY_ID_INDEX.put(entity.getEntityId(), m);
          m.rememberEntity(entity);
        }
      }
    } catch (Throwable ignored) {}
    if (m.lines != null) for (HologramLine line : m.lines) {
      try {
        if (line!=null && line.getArmor()!=null && line.getArmor().getEntity()!=null) {
          Entity a = line.getArmor().getEntity();
          ENTITY_UUID_INDEX.put(a.getUniqueId(), m);
          ENTITY_ID_INDEX.put(a.getEntityId(), m);
          m.rememberEntity(a);
        }
      } catch (Throwable ignored) {}
    }
    Slime hitbox = m.getHitbox();
    if (hitbox != null && !hitbox.isDead()) {
      ENTITY_UUID_INDEX.put(hitbox.getUniqueId(), m);
      ENTITY_ID_INDEX.put(hitbox.getEntityId(), m);
      m.rememberEntity(hitbox);
    }
  }

  private static void hideName(Player viewer, NPC npc) {
    if (viewer==null || npc==null || npc.getName()==null) return;
    Scoreboard sb = viewer.getScoreboard();
    if (sb == null) return;
    Team t = sb.getTeam(NAMELESS_TEAM);
    if (t == null) {
      try { t = sb.registerNewTeam(NAMELESS_TEAM); } catch (IllegalArgumentException ignored) { t = sb.getTeam(NAMELESS_TEAM); }
    }
    if (t == null) return;
    try {
      Class<?> opt = Class.forName("org.bukkit.scoreboard.Team$Option");
      Class<?> stat = Class.forName("org.bukkit.scoreboard.Team$OptionStatus");
      Object NAME_TAG = java.lang.Enum.valueOf((Class<Enum>) opt, "NAME_TAG_VISIBILITY");
      Object NEVER = java.lang.Enum.valueOf((Class<Enum>) stat, "NEVER");
      t.getClass().getMethod("setOption", opt, stat).invoke(t, NAME_TAG, NEVER);
    } catch (Throwable ignored) {}
    try { t.setNameTagVisibility(NameTagVisibility.NEVER); } catch (Throwable ignored) {}
    try { Team.class.getMethod("addEntry", String.class).invoke(t, npc.getName()); }
    catch (Throwable ignored) { try { t.addPlayer(Bukkit.getOfflinePlayer(npc.getName())); } catch (Throwable ignored2) {} }
  }

  private static void removeNametagForAll(NPC npc) {
    if (npc==null || npc.getName()==null) return;
    for (Player viewer : Bukkit.getOnlinePlayers()) {
      Scoreboard sb = viewer.getScoreboard(); if (sb==null) continue;
      Team t = sb.getTeam(NAMELESS_TEAM); if (t==null) continue;
      try { Team.class.getMethod("removeEntry", String.class).invoke(t, npc.getName()); }
      catch (Throwable ignored) { try { t.removePlayer(Bukkit.getOfflinePlayer(npc.getName())); } catch (Throwable ignored2) {} }
    }
  }

  private boolean handleEntityInteraction(Player player, Entity clicked) {
    if (player==null || clicked==null) return false;
    ManagedNPC m = ENTITY_UUID_INDEX.get(clicked.getUniqueId());
    if (m == null) try { m = ENTITY_ID_INDEX.get(clicked.getEntityId()); } catch (Throwable ignored) {}
    if (m == null) {
      for (ManagedNPC candidate : activeNPCs.values()) {
        if (candidate == null || candidate.npc == null) continue;
        Entity npcEntity = candidate.npc.getEntity();
        if (npcEntity == null) continue;
        try {
          if (!npcEntity.getWorld().equals(clicked.getWorld())) continue;
          double distanceSq = npcEntity.getLocation().distanceSquared(clicked.getLocation());
          if (distanceSq <= 6.25D) {
            m = candidate;
            break;
          }
        } catch (Throwable ignored) {}
      }
    }
    if (m == null) return false;
    m.execute(player);
    return true;
  }
}
