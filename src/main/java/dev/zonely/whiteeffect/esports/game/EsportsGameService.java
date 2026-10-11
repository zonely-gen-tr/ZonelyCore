package dev.zonely.whiteeffect.esports.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.clubs.ClubManager;
import dev.zonely.whiteeffect.clubs.model.ClubData;
import dev.zonely.whiteeffect.esports.EsportsConfig;
import dev.zonely.whiteeffect.esports.EsportsLang;
import dev.zonely.whiteeffect.esports.EsportsManager;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.esports.menu.EsportsBracketMenu;
import dev.zonely.whiteeffect.esports.menu.EsportsKitMenu;
import dev.zonely.whiteeffect.esports.model.EsportsClubEntry;
import dev.zonely.whiteeffect.esports.model.EsportsMatchData;
import dev.zonely.whiteeffect.esports.model.EsportsPotionEffectSpec;
import dev.zonely.whiteeffect.esports.model.EsportsSpawnPoint;
import dev.zonely.whiteeffect.esports.model.EsportsSpawnSlot;
import dev.zonely.whiteeffect.esports.model.EsportsTournamentData;
import dev.zonely.whiteeffect.esports.reward.EsportsRewardService;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.CenteredMessage;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import dev.zonely.whiteeffect.libraries.holograms.HologramLibrary;
import dev.zonely.whiteeffect.libraries.holograms.api.Hologram;
import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bat;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Giant;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SmallFireball;
import org.bukkit.GameMode;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import org.bukkit.metadata.FixedMetadataValue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

public final class EsportsGameService {

    // Tournament chat prefix
    private static final String CHAT_PREFIX = ChatColor.YELLOW + "";
    private static final int LOBBY_WINDOW_MINUTES = 5;
    private static final int AUTO_QUEUE_DELAY_SECONDS = 8;
    private static final int MATCH_END_CLEANUP_DELAY_SECONDS = 10;
    private static final int WINNER_CELEBRATION_SECONDS = 8;
    private static final int WINNER_CELEBRATION_PARTICLE_POINTS = 26;
    private static final int WINNER_CELEBRATION_FIREWORKS_PER_WAVE = 5;
    private static final int WINNER_CELEBRATION_ICE_RADIUS_MIN = 10;
    private static final int WINNER_CELEBRATION_ICE_RADIUS_MAX = 24;
    private static final int MATCH_START_COUNTDOWN_SECONDS = 10;
    private static final double PREP_AREA_RADIUS = 5.0D; // 10x10 prep area per club/player
    private static final int BORDER_EXPAND_SECONDS = 5;
    private static final int MATCH_DURATION_SECONDS = 6 * 60;
    private static final int BORDER_SHRINK_START_SECONDS = 4 * 60;
    private static final double BRACKET_READY_THRESHOLD = 0.70D;
    private static final int CHEST_DROP_FIRST_SECONDS = 45;
    private static final int CHEST_DROP_INTERVAL_SECONDS = 60;
    private static final int CHEST_REFRESH_SECONDS = 70;
    private static final long SPECIAL_SUPPLY_COOLDOWN_MS = 4000L;
    private static final long SPECIAL_ITEM_COOLDOWN_MS = 1200L;
    private static final long TRAMPOLINE_FALL_PROTECT_MS = 9000L;
    private static final int SPECIAL_ITEM_DEFAULT_USES = 3;
    private static final double SPECIAL_SUPPLY_PICKUP_RADIUS_SQ = 2.25D;
    private static final int METEOR_INTERVAL_SECONDS = 14;
    private static final int TRAIN_INTERVAL_SECONDS = 18;
    private static final int METEOR_ANCHOR_POINTS = 16;
    private static final int SKY_METEOR_IMPACT_RADIUS = 1;
    private static final int SKY_METEOR_MAX_BROKEN_BLOCKS = 4;
    private static final double SKY_METEOR_CRATER_CHANCE = 0.35D;
    private static final long TRAIN_HIT_COOLDOWN_MS = 1400L;
    private static final int REPLAY_DEFAULT_MAX_EVENTS = 2400;
    private static final int REPLAY_DEFAULT_FLUSH_BATCH = 200;
    private static final int REPLAY_DEFAULT_DAMAGE_MIN_INTERVAL_MS = 250;
    private static final int REPLAY_DEFAULT_MAX_STRING_LEN = 180;
    private static final int REPLAY_DEFAULT_LOCATION_DECIMALS = 2;
    private static final int REPLAY_DEFAULT_QUEUE_LIMIT = 9000;
    private static final double TRAIN_COLLISION_DAMAGE = 2.0D;
    private static final int TRAIN_COLLISION_FIRE_TICKS = 20;
    private static final boolean MATCH_EVENT_LOG_TO_CONSOLE = false;
    private static final int SPECIAL_SUPPLY_POINT_COUNT = 4;
    private static final String SPECIAL_ITEM_TRAMPOLINE = "trampoline";
    private static final String SPECIAL_ITEM_FIRE_BLASTER = "fire_blaster";
    private static final String SPECIAL_ITEM_INFERNO = "inferno_launcher";
    private static final String SPECIAL_ITEM_MAGIC_WAND = "magic_wand";
    private static final String SPECIAL_ITEM_FORCE_ORB = "force_orb";
    private static final String SPECIAL_ITEM_METEOR_ORB = "meteor_orb";
    private static final Set<String> AUTH_COMMAND_BYPASS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "login", "l", "register", "reg", "email", "mail", "twofactor", "2fa", "passwordreset"
    )));

    private final Core plugin;
    private final EsportsManager manager;
    private final EsportsRewardService rewardService;
    private final EsportsScoreboardService scoreboardService;
    private final PersonalBorderManager borderManager = new PersonalBorderManager();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private String activeClubTag;
    private final Set<String> activeMatchClubs = new LinkedHashSet<>();
    private final Map<String, Set<UUID>> activeClubMembers = new ConcurrentHashMap<>();
    private final Map<String, Integer> clubMemberSizes = new ConcurrentHashMap<>();
    private final Map<String, EsportsClubEntry> activeClubData = new ConcurrentHashMap<>();
    private final Set<UUID> pendingSpectatorRespawn = ConcurrentHashMap.newKeySet();
    private long matchStartedAt;
    private int currentMatchId;
    private BukkitRunnable violationTask;
    private final Set<UUID> matchParticipants = new LinkedHashSet<>();
    private final List<String> participantNames = new ArrayList<>();
    private final Set<String> participantClubs = new LinkedHashSet<>();
    private final Map<UUID, String> participantClubTags = new HashMap<>();
    private final Map<UUID, Integer> killCounts = new HashMap<>();
    private final Map<UUID, Integer> deathCounts = new HashMap<>();
    private final Map<UUID, Integer> blockBreakCounts = new HashMap<>();
    private final Map<UUID, Integer> blockPlaceCounts = new HashMap<>();
    private final Map<String, Integer> clubKillCounts = new HashMap<>();
    private final Map<String, Location> customClubSpawns = new ConcurrentHashMap<>();
    private final Map<String, JsonObject> customClubKits = new ConcurrentHashMap<>();
    private final Map<UUID, Location> participantReturnLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Location> postMatchReturnLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Location> participantSpawns = new ConcurrentHashMap<>();
    private String lastWinnerDisplay;
    private String lastRunnerUpDisplay;
    private String lastWinnerTag;
    private String lastRunnerUpTag;
    private LocalDateTime lastMatchEndedAt;
    private final Map<String, Hologram> leaderboardHolograms = new ConcurrentHashMap<>();
    private final Map<String, Hologram> customHolograms = new ConcurrentHashMap<>();
    private final Map<String, ArmorStand> leaderboardBannerStands = new ConcurrentHashMap<>();
    private final Map<String, Integer> leaderboardRanks = new ConcurrentHashMap<>();
    private final Map<String, Location> leaderboardLocations = new ConcurrentHashMap<>();
    private BukkitRunnable leaderboardRefreshTask;
    private final File leaderboardFile;
    private ItemStack lobbyLeaveItem;
    private final Set<UUID> borderWarned = new HashSet<>();
    private final Map<UUID, Long> borderDamageLog = new HashMap<>();
    private final ArrayDeque<String> matchQueue = new ArrayDeque<>();
    private final Set<String> queuedTags = new HashSet<>();
    private final Set<Integer> bracketQueuedMatches = new HashSet<>();
    private final Map<String, Integer> clubMatchAssignments = new HashMap<>();
    private final Set<UUID> spectatorLobby = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ItemStack[]> savedContents = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new ConcurrentHashMap<>();
    // Arena snapshot to restore blocks between matches
    private final Map<Location, String> arenaSnapshot = new HashMap<>();
    private boolean arenaSnapshotCaptured = false;
    private String arenaSnapshotWorldName = null;
    private final Map<UUID, ItemStack> savedOffhand = new ConcurrentHashMap<>();
    private final Map<Location, Hologram> chestHolograms = new ConcurrentHashMap<>();
    private final Map<Location, Hologram> specialSupplyHolograms = new ConcurrentHashMap<>();
    private final Map<Location, Integer> specialSupplyTiers = new ConcurrentHashMap<>();
    private final Map<Location, ItemStack> specialSupplyRewards = new ConcurrentHashMap<>();
    private final Map<Location, Material> specialSupplyOriginalBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> specialSupplyCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> specialItemCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, String> specialProjectiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> noFallDamageUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> trainHitCooldown = new ConcurrentHashMap<>();
    private final Set<UUID> celebrationFireballs = ConcurrentHashMap.newKeySet();
    private final Set<UUID> winnerCelebrationRiders = ConcurrentHashMap.newKeySet();
    private final List<UUID> winnerRideOrder = new ArrayList<>();
    private final Set<String> managedTournamentWorlds = ConcurrentHashMap.newKeySet();
    // Track opened chests so holograms spawn only once per drop
    private final Set<Location> openedChestHolograms = new HashSet<>();
    // Track running hologram tickers per chest
    private final Map<Location, BukkitTask> chestHologramTasks = new ConcurrentHashMap<>();
    // Match-time participant banners (per player)
    private final Map<UUID, BannerFollower> matchBannerFollowers = new ConcurrentHashMap<>();
    private static final int LOBBY_MATCH_COUNTDOWN_SECONDS = 600;
    private final String spectatorWorldName;
    private final double spectatorX;
    private final double spectatorY;
    private final double spectatorZ;
    private final float spectatorYaw;
    private final float spectatorPitch;
    private final int countdownSeconds;
    private final double arenaBorderRadius;
    private final double stagingBorderRadius;
    private final int borderGrowthSeconds;
    private final String bracketItemName;
    private final ItemStack bracketViewerItem;
    private BukkitRunnable chestDropTask;
    private int nextDropSeconds;
    private int currentDropTier = 1;
    private final Random random = new Random();
    private final Set<BukkitTask> chestRefreshTasks = new HashSet<>();
    private final Map<Location, Integer> activeChests = new HashMap<>();
    private final Map<Location, Long> chestRefreshTimes = new HashMap<>();
    private Location lastDropLocation;
    private Location spectatorLocation;
    private BukkitRunnable countdownTask;
    private BukkitRunnable matchEndTask;
    private int countdownRemaining;
    private BukkitRunnable autoQueueTask;
    private BukkitRunnable borderGrowthTask;
    private BukkitRunnable winnerCelebrationTask;
    private BukkitRunnable worldStateTask;
    private BukkitRunnable meteorShowerTask;
    private BukkitRunnable trainWaveTask;
    private final Set<UUID> activeTrainEntities = ConcurrentHashMap.newKeySet();
    private final List<Location> meteorAnchorPoints = new ArrayList<>();
    private int meteorAnchorCursor = 0;
    private Location activeMatchCenter;
    private EnderDragon winnerCelebrationDragon;
    private boolean matchConcluded = false;
    private boolean tournamentEndedThisMatch = false;
    private boolean bracketReadyAnnounced = false;
    private final Queue<ReplayEventEntry> replayEventQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, Long> replayDamageThrottle = new ConcurrentHashMap<>();
    private BukkitTask replayFlushTask;
    private volatile MatchReplayCapture replayCapture;

    // NEW: Track if a chest has been initialized at least once
    private final Set<Location> initializedChests = new HashSet<>();

    public EsportsGameService(Core plugin, EsportsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.rewardService = new EsportsRewardService(plugin);
        this.scoreboardService = new EsportsScoreboardService(plugin, manager, this);
        EsportsConfig.ArenaSettings arena = manager.getSettings().getArenaSettings();
        this.spectatorWorldName = arena.getSpectatorWorld();
        this.spectatorX = arena.getSpectatorX();
        this.spectatorY = arena.getSpectatorY();
        this.spectatorZ = arena.getSpectatorZ();
        this.spectatorYaw = arena.getSpectatorYaw();
        this.spectatorPitch = arena.getSpectatorPitch();
        this.countdownSeconds = arena.getCountdownSeconds();
        this.arenaBorderRadius = arena.getBorderRadius();
        this.stagingBorderRadius = arena.getStagingBorderRadius();
        this.borderGrowthSeconds = arena.getBorderGrowthSeconds();
        this.bracketItemName = EsportsLang.get("game.hotbar.bracket.name",
                ChatColor.AQUA + "Tournament Bracket");
        this.bracketViewerItem = createBracketViewerItem();
        this.lobbyLeaveItem = createLobbyLeaveItem();
        this.leaderboardFile = new File(plugin.getDataFolder(), "spawn.yml");
        clearBorders();
        // loadLeaderboardsFromFile();
        // startLeaderboardRefreshTask();
        plugin.getServer().getPluginManager().registerEvents(new EsportsMatchEventListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new EsportsSpectatorChatListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(scoreboardService, plugin);
        plugin.getServer().getPluginManager().registerEvents(new BracketItemListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new SpectatorProtectionListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new FriendlyFireListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ChestOpenListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new SpecialSupplyListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ParticipantLifecycleListener(), plugin);
        resolveSpectatorLocation(manager != null ? manager.getActiveTournament() : null);
        startReplayFlushTask();
    }

    public Core getPlugin() {
        return plugin;
    }

    private void clearBorders() {
        borderManager.clearAll();
    }

    public void shutdown() {
        finishMatchReplayCapture("none", "shutdown");
        stopReplayFlushTask();
        stopWinnerCelebration();
        clearParticipantBanners();
        stopChestDrops();
        clearDropBlocks();
        stopDynamicArenaEvents();
        stopTournamentWorldTask();
        cancelMatchTimeout();
        cancelAutoQueueStart();
        if (countdownTask != null) {
            try {
                countdownTask.cancel();
            } catch (Exception ignored) {
            }
            countdownTask = null;
        }
        countdownRemaining = 0;
        postMatchReturnLocations.clear();
        if (scoreboardService != null) {
            scoreboardService.shutdown();
        }
        matchParticipants.clear();
        participantClubTags.clear();
        matchQueue.clear();
        queuedTags.clear();
        bracketQueuedMatches.clear();
        clubMatchAssignments.clear();
        spectatorLobby.clear();
    }

    public Location getSpectatorLocationSnapshot() {
        return resolveSpectatorLocation(manager != null ? manager.getActiveTournament() : null);
    }

    private Location resolveSpectatorLocation(EsportsTournamentData tournament) {
        Location chosen = readSpectatorLocationFromSettings(tournament);
        if (chosen == null) {
            World world = spectatorWorldName == null ? null : Bukkit.getWorld(spectatorWorldName);
            if (world != null) {
                chosen = new Location(world, spectatorX, spectatorY, spectatorZ, spectatorYaw, spectatorPitch);
            }
        }
        if (chosen == null) {
            return null;
        }
        spectatorLocation = chosen.clone();
        return spectatorLocation.clone();
    }

    private Location readSpectatorLocationFromSettings(EsportsTournamentData tournament) {
        if (tournament == null || tournament.getSettings() == null || !tournament.getSettings().has("spectator")) {
            return null;
        }
        try {
            JsonObject spec = tournament.getSettings().getAsJsonObject("spectator");
            EsportsSpawnPoint point = EsportsSpawnPoint.fromJson(spec);
            return point == null ? null : point.toLocation(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean teleportToSpectator(Player player, boolean giveItems) {
        if (player == null) {
            return false;
        }
        Location target = resolveSpectatorLocation(manager != null ? manager.getActiveTournament() : null);
        if (target == null) {
            player.sendMessage(EsportsLang.get("game.spectator.spawn-missing", "&cSpectator spawn is not configured."));
            return false;
        }
        trackTournamentWorld(target.getWorld());
        rememberReturnLocation(player);
        spectatorLobby.add(player.getUniqueId());
        stashInventoryState(player);
        applySpectatorState(player, giveItems);
        player.teleport(target);
        player.sendMessage(EsportsLang.get("game.spectator.enter", "&aYou are now in the spectator lobby."));
        return true;
    }

    public void leaveSpectator(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        if (!spectatorLobby.remove(id)) {
            return;
        }
        if (scoreboardService != null) {
            scoreboardService.disable(player, false);
        }
        try {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setGameMode(GameMode.SURVIVAL);
        } catch (Exception ignored) {
        }
        restoreInventoryState(player);
        Location back = participantReturnLocations.remove(id);
        if (back != null) {
            player.teleport(back);
        }
        player.sendMessage(EsportsLang.get("game.spectator.leave", "&eYou left the spectator lobby."));
    }

    public boolean isSpectator(UUID id) {
        return id != null && spectatorLobby.contains(id);
    }

    public void removeSpectator(UUID id) {
        if (id == null) {
            return;
        }
        spectatorLobby.remove(id);
        savedContents.remove(id);
        savedArmor.remove(id);
        savedOffhand.remove(id);
        participantReturnLocations.remove(id);
    }

    public void handleSpectatorChat(Player player, String message) {
        if (player == null || message == null) {
            return;
        }
        String formatted = ChatColor.GRAY + "[Spectator] "
                + ChatColor.AQUA + player.getName()
                + ChatColor.GRAY + ": " + ChatColor.WHITE + message;
        for (UUID id : spectatorLobby) {
            Player target = Bukkit.getPlayer(id);
            if (target != null && target.isOnline()) {
                target.sendMessage(formatted);
            }
        }
    }

    private ItemStack createBracketViewerItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(bracketItemName);
            meta.setLore(EsportsLang.getList("game.hotbar.bracket.lore", Arrays.asList(
                    ChatColor.GRAY + "Right-click to open the",
                    ChatColor.GRAY + "tournament bracket in the lobby."
            )));
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isBracketItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && bracketItemName.equals(meta.getDisplayName());
    }

    private boolean isLeaveItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && lobbyLeaveItem != null
                && meta.getDisplayName().equals(lobbyLeaveItem.getItemMeta().getDisplayName());
    }

    private void giveBracketItem(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (ItemStack stack : inv.getContents()) {
            if (isBracketItem(stack)) {
                return;
            }
        }
        int slot = inv.firstEmpty();
        if (slot < 0) {
            slot = 0;
        }
        inv.setItem(slot, bracketViewerItem.clone());
    }

    private void giveLobbyLeaveItem(Player player) {
        if (player == null || lobbyLeaveItem == null) {
            return;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isLeaveItem(stack)) {
                return;
            }
        }
        PlayerInventory inv = player.getInventory();
        ItemStack existing = inv.getItem(8);
        if (existing != null && existing.getType() != Material.AIR && !isLeaveItem(existing)) {
            int slot = inv.firstEmpty();
            if (slot >= 0) {
                inv.setItem(slot, lobbyLeaveItem.clone());
                return;
            }
        }
        inv.setItem(8, lobbyLeaveItem.clone());
    }

    private void ensureScoreboard(Collection<? extends Player> players) {
        if (scoreboardService == null || players == null) {
            return;
        }
        for (Player player : players) {
            if (player != null && player.isOnline()) {
                scoreboardService.enable(player, false);
            }
        }
    }

    private void rememberReturnLocation(Player player) {
        if (player == null) {
            return;
        }
        participantReturnLocations.putIfAbsent(player.getUniqueId(), player.getLocation().clone());
    }

    private ItemStack createLobbyLeaveItem() {
        ItemStack bed = new ItemStack(MaterialResolver.resolveMaterial("RED_BED") != null
                ? MaterialResolver.resolveMaterial("RED_BED")
                : Material.STONE);
        ItemMeta meta = bed.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(EsportsLang.get("game.hotbar.leave.name",
                    ChatColor.RED + "Leave Lobby"));
            meta.setLore(EsportsLang.getList("game.hotbar.leave.lore", Collections.singletonList(
                    ChatColor.GRAY + "Right-click to leave spectator lobby."
            )));
            bed.setItemMeta(meta);
        }
        return bed;
    }

    public boolean hasActiveMatchContext() {
        return isMatchRunning() || isCountdownRunning() || !matchParticipants.isEmpty();
    }

    public boolean isMatchRunning() {
        return currentMatchId > 0 && matchStartedAt > 0 && (lastMatchEndedAt == null || lastMatchEndedAt.isBefore(LocalDateTime.now()));
    }

    public boolean isCountdownRunning() {
        return countdownRemaining > 0;
    }

    private boolean isCountdownActive() {
        return isCountdownRunning();
    }

    public int getCountdownRemainingSeconds() {
        return Math.max(0, countdownRemaining);
    }

    public long getMatchElapsedSeconds() {
        if (!isMatchRunning()) {
            return 0L;
        }
        return ChronoUnit.SECONDS.between(Instant.ofEpochMilli(matchStartedAt), Instant.now());
    }

    public long getMatchDurationSeconds() {
        return MATCH_DURATION_SECONDS;
    }

    public long getMatchRemainingSeconds() {
        if (!isMatchRunning()) {
            return MATCH_DURATION_SECONDS;
        }
        return Math.max(0L, MATCH_DURATION_SECONDS - getMatchElapsedSeconds());
    }

    public int getActiveFighterCount() {
        return matchParticipants.size();
    }

    public int getParticipantCount() {
        return matchParticipants.size();
    }

    public String getActiveEventType() {
        EsportsTournamentData data = manager != null ? manager.getActiveTournament() : null;
        return data != null && data.getEventType() != null ? data.getEventType().toLowerCase(Locale.ROOT) : "pvp";
    }

    public boolean isParticipant(UUID id) {
        return id != null && matchParticipants.contains(id);
    }

    public boolean isActiveCompetitor(UUID id) {
        return isParticipant(id);
    }

    public Optional<String> getParticipantClubTag(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        String tag = participantClubTags.get(id);
        return tag == null ? Optional.empty() : Optional.of(tag);
    }

    public int getPlayerKills(UUID id) {
        return id == null ? 0 : killCounts.getOrDefault(id, 0);
    }

    public int getPlayerDeaths(UUID id) {
        return id == null ? 0 : deathCounts.getOrDefault(id, 0);
    }

    public int getPlayerBlockBreaks(UUID id) {
        return id == null ? 0 : blockBreakCounts.getOrDefault(id, 0);
    }

    public int getPlayerBlockScore(UUID id) {
        if (id == null) {
            return 0;
        }
        int breaks = blockBreakCounts.getOrDefault(id, 0);
        int places = blockPlaceCounts.getOrDefault(id, 0);
        return breaks + places;
    }

    public int getClubKills(String tag) {
        if (tag == null) {
            return 0;
        }
        return clubKillCounts.getOrDefault(tag.toUpperCase(Locale.ROOT), 0);
    }

    public String getFormattedNextDrop() {
        if (nextDropSeconds <= 0) {
            return "-";
        }
        int minutes = nextDropSeconds / 60;
        int seconds = nextDropSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    public int getQueuedClubCount() {
        return matchQueue.size();
    }

    public List<String> getQueuedClubsSnapshot() {
        return new ArrayList<>(matchQueue);
    }

    public String getNextQueuedClub() {
        return matchQueue.peekFirst();
    }

    public void setCurrentMatchId(Integer id) {
        this.currentMatchId = id == null ? 0 : id;
    }

    public void incrementKill(UUID id, String clubTag) {
        if (id != null) {
            killCounts.merge(id, 1, Integer::sum);
        }
        if (clubTag != null) {
            clubKillCounts.merge(clubTag.toUpperCase(Locale.ROOT), 1, Integer::sum);
        }
    }

    public void incrementDeath(UUID id) {
        if (id != null) {
            deathCounts.merge(id, 1, Integer::sum);
        }
    }

    public void incrementBlockBreak(UUID id) {
        if (id != null) {
            blockBreakCounts.merge(id, 1, Integer::sum);
        }
    }

    public void incrementBlockPlace(UUID id) {
        if (id != null) {
            blockPlaceCounts.merge(id, 1, Integer::sum);
        }
    }

    public void handleParticipantDeath(Player victim) {
        if (victim == null) {
            return;
        }
        UUID uuid = victim.getUniqueId();
        removeParticipantBanner(uuid);
        noFallDamageUntil.remove(uuid);
        specialItemCooldown.remove(uuid);
        String club = participantClubTags.get(uuid);
        if (club != null) {
            Set<UUID> alive = activeClubMembers.get(club);
            if (alive != null) {
                alive.remove(uuid);
            }
        }
        spectatorLobby.add(uuid);
        checkMatchCompletion(false);
    }

    private void handleTournamentQuit(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        removeParticipantBanner(uuid);
        noFallDamageUntil.remove(uuid);
        specialItemCooldown.remove(uuid);
        specialSupplyCooldown.remove(uuid);
        spectatorLobby.remove(uuid);
        pendingSpectatorRespawn.remove(uuid);
        winnerCelebrationRiders.remove(uuid);

        String clubTag = participantClubTags.get(uuid);
        if (matchParticipants.contains(uuid)) {
            handleParticipantDeath(player);
            spectatorLobby.remove(uuid);
        }
        if ((clubTag == null || clubTag.trim().isEmpty()) && manager != null) {
            clubTag = manager.findClubByPlayer(player).map(EsportsClubEntry::getTag).orElse(null);
        }
        if (clubTag == null || clubTag.trim().isEmpty()) {
            return;
        }
        if (hasOnlineClubMember(clubTag)) {
            return;
        }
        boolean removedFromQueue = removeQueuedClub(clubTag);
        if (removedFromQueue) {
            Bukkit.broadcastMessage(CHAT_PREFIX + ChatColor.RED + "Club " + clubTag.toUpperCase(Locale.ROOT)
                    + " left the tournament (no online members).");
        }
        forceClubOutOfMatch(clubTag);
        markClubEliminated(clubTag);
    }

    private boolean hasOnlineClubMember(String clubTag) {
        if (clubTag == null || clubTag.trim().isEmpty()) {
            return false;
        }
        String normalized = clubTag.toLowerCase(Locale.ROOT);
        ClubManager clubManager = plugin != null ? plugin.getClubManager() : null;
        if (clubManager != null && manager != null) {
            Optional<EsportsClubEntry> entry = manager.getClubByTag(clubTag);
            if (entry.isPresent()) {
                Optional<ClubData> club = clubManager.getClubById(entry.get().getClubId());
                if (club.isPresent()) {
                    for (dev.zonely.whiteeffect.clubs.model.ClubMember member : club.get().getMembers()) {
                        if (member == null || member.getUniqueId() == null) {
                            continue;
                        }
                        Player online = Bukkit.getPlayer(member.getUniqueId());
                        if (online != null && online.isOnline()) {
                            return true;
                        }
                    }
                    return false;
                }
            }
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null || !online.isOnline()) {
                continue;
            }
            String tag = getParticipantClubTag(online.getUniqueId()).orElse(null);
            if (tag == null && manager != null) {
                tag = manager.findClubByPlayer(online).map(EsportsClubEntry::getTag).orElse(null);
            }
            if (tag != null && normalized.equals(tag.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean removeQueuedClub(String clubTag) {
        if (clubTag == null || clubTag.trim().isEmpty()) {
            return false;
        }
        String normalized = clubTag.toLowerCase(Locale.ROOT);
        boolean removed = false;
        if (queuedTags.remove(normalized)) {
            removed = true;
        }
        Iterator<String> iterator = matchQueue.iterator();
        while (iterator.hasNext()) {
            String queued = iterator.next();
            if (queued == null) {
                continue;
            }
            if (normalized.equals(queued.toLowerCase(Locale.ROOT))) {
                iterator.remove();
                removed = true;
            }
        }
        return removed;
    }

    private void forceClubOutOfMatch(String clubTag) {
        if (clubTag == null || clubTag.trim().isEmpty()) {
            return;
        }
        String normalized = clubTag.toLowerCase(Locale.ROOT);
        Set<UUID> alive = activeClubMembers.get(normalized);
        if (alive == null) {
            for (Map.Entry<String, Set<UUID>> entry : activeClubMembers.entrySet()) {
                if (entry.getKey() != null && normalized.equals(entry.getKey().toLowerCase(Locale.ROOT))) {
                    alive = entry.getValue();
                    break;
                }
            }
        }
        if (alive != null) {
            alive.clear();
        }
        if (isMatchRunning()) {
            sendToParticipants(CHAT_PREFIX + ChatColor.RED + "Club "
                    + ChatColor.YELLOW + normalized.toUpperCase(Locale.ROOT)
                    + ChatColor.RED + " was eliminated (no online members).");
            checkMatchCompletion(false);
        }
    }

    private void markClubEliminated(String clubTag) {
        if (clubTag == null || clubTag.trim().isEmpty() || manager == null) {
            return;
        }
        EsportsTournamentData tournament = manager.getActiveTournament();
        if (tournament == null) {
            return;
        }
        Optional<EsportsClubEntry> entry = manager.getClubByTag(clubTag);
        if (!entry.isPresent()) {
            return;
        }
        manager.getRepository().enrollClub(tournament.getId(), entry.get().getClubId(), "eliminated");
        manager.reloadNow();
    }

    private void markLosingClubsEliminated(String winnerTag) {
        if (winnerTag == null || winnerTag.trim().isEmpty() || manager == null || activeMatchClubs.isEmpty()) {
            return;
        }
        EsportsTournamentData tournament = manager.getActiveTournament();
        if (tournament == null) {
            return;
        }
        boolean changed = false;
        for (String tag : new HashSet<>(activeMatchClubs)) {
            if (tag == null || tag.trim().isEmpty() || tag.equalsIgnoreCase(winnerTag)) {
                continue;
            }
            Optional<EsportsClubEntry> clubOpt = manager.getClubByTag(tag);
            if (!clubOpt.isPresent()) {
                continue;
            }
            if (manager.getRepository().enrollClub(tournament.getId(), clubOpt.get().getClubId(), "eliminated")) {
                changed = true;
            }
        }
        if (changed) {
            manager.reloadNow();
        }
    }

    private void checkMatchCompletion(boolean timeout) {
        if (matchConcluded || !isMatchRunning()) {
            return;
        }
        List<ClubMatchStatus> statuses = getActiveClubStatuses();
        long aliveClubs = statuses.stream().filter(s -> s.getAlive() > 0).count();
        if (!timeout && aliveClubs > 1) {
            return;
        }
        String winnerTag = null;
        if (aliveClubs == 1) {
            winnerTag = statuses.stream().filter(s -> s.getAlive() > 0).findFirst().map(ClubMatchStatus::getTag).orElse(null);
        } else {
            winnerTag = resolveScoreWinner();
        }
        concludeMatch(winnerTag, timeout ? "Time up" : "Elimination");
    }

    private String resolveScoreWinner() {
        String mode = getActiveEventType();
        boolean useBlockScore = mode != null && (mode.contains("block") || mode.contains("build"));
        Map<String, Integer> scores = new HashMap<>();
        for (Map.Entry<UUID, String> entry : participantClubTags.entrySet()) {
            String tag = entry.getValue();
            if (tag == null) {
                continue;
            }
            int score = useBlockScore ? getPlayerBlockScore(entry.getKey()) : getPlayerKills(entry.getKey());
            scores.merge(tag.toLowerCase(Locale.ROOT), score, Integer::sum);
        }
        String bestTag = null;
        int best = -1;
        boolean tie = false;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            int val = entry.getValue();
            if (val > best) {
                best = val;
                bestTag = entry.getKey();
                tie = false;
            } else if (val == best) {
                tie = true;
            }
        }
        return tie ? null : bestTag;
    }

    private String resolveMatchEndReason(Player player, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return "";
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT);
        if ("elimination".equals(normalized)) {
            return getEsportsText(player, "game.match.reason.elimination", "Elimination");
        }
        if ("time up".equals(normalized) || "timeout".equals(normalized) || "time-up".equals(normalized)) {
            return getEsportsText(player, "game.match.reason.time-up", "Time up");
        }
        return reason;
    }

    private void concludeMatch(String winnerTag, String reason) {
        if (matchConcluded) {
            return;
        }
        matchConcluded = true;
        cancelMatchTimeout();
        stopDynamicArenaEvents();
        clearParticipantBanners();
        String displayWinner = null;
        if (winnerTag != null) {
            EsportsClubEntry club = activeClubData.get(winnerTag.toLowerCase(Locale.ROOT));
            displayWinner = club != null ? club.getName() : winnerTag.toUpperCase(Locale.ROOT);
        }
        if (winnerTag == null) {
            broadcastCenteredLocalized(
                    "game.match.end.draw",
                    "&c[E-Sports] Match ended in a draw");
        } else {
            broadcastCenteredLocalized(
                    "game.match.end.winner",
                    "&6[E-Sports] {winner}&b won the match!",
                    "winner", displayWinner);
        }
        if (reason != null && !reason.isEmpty()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                String localizedReason = resolveMatchEndReason(player, reason);
                player.sendMessage(getEsportsText(
                        player,
                        "game.match.end.reason",
                        CHAT_PREFIX + "&7Reason: &e{reason}",
                        "reason", localizedReason));
            }
        }
        if (manager != null && currentMatchId > 0) {
            String winnerForRecord = winnerTag == null ? "none" : winnerTag;
            manager.recordMatchResult(currentMatchId, winnerForRecord, null, null);
        }
        finishMatchReplayCapture(winnerTag, reason);
        if (winnerTag != null && !winnerTag.trim().isEmpty()) {
            markLosingClubsEliminated(winnerTag);
        }
        sendMatchSummary();
        tournamentEndedThisMatch = maybeMarkTournamentEnded(winnerTag);
        if (winnerTag != null && !winnerTag.trim().isEmpty() && tournamentEndedThisMatch) {
            startWinnerCelebration(winnerTag);
        } else {
            stopWinnerCelebration();
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> clearMatch(null), MATCH_END_CLEANUP_DELAY_SECONDS * 20L);
    }

    private void concludeByScore(String reason) {
        if (matchConcluded) {
            return;
        }
        concludeMatch(resolveScoreWinner(), reason);
    }

    private boolean maybeMarkTournamentEnded(String winnerTag) {
        if (manager == null) {
            return false;
        }
        EsportsTournamentData tournament = manager.getActiveTournament();
        if (tournament == null) {
            return false;
        }
        String state = tournament.getState();
        if (state != null && ("ended".equalsIgnoreCase(state) || "finished".equalsIgnoreCase(state))) {
            return true;
        }
        boolean bracketCompleted = isBracketCompleted(tournament);
        boolean liveState = state != null
                && ("live".equalsIgnoreCase(state) || "started".equalsIgnoreCase(state));
        if (!bracketCompleted && !liveState) {
            return false;
        }
        String winner = winnerTag == null ? "" : winnerTag.trim();
        boolean shouldEnd = bracketCompleted
                || (!winner.isEmpty() && isTournamentDownToSingleClub(tournament, winner));
        if (!shouldEnd) {
            return false;
        }
        boolean updated = manager.getRepository().markTournamentFinished(tournament.getId());
        if (!updated) {
            updated = manager.getRepository().updateTournamentState(tournament.getId(), "finished");
        }
        if (updated) {
            manager.reloadNow();
            broadcastCenteredLocalized(
                    "game.tournament.ended",
                    "&6[E-Sports] &b{name}&a tournament ended.",
                    "name", tournament.getName());
            manager.createRecurringTournamentIfNeeded(tournament);
        }
        return updated;
    }

    private boolean isBracketCompleted(EsportsTournamentData tournament) {
        if (tournament == null || tournament.getMatches() == null || tournament.getMatches().isEmpty()) {
            return false;
        }
        for (EsportsMatchData match : tournament.getMatches()) {
            if (match == null) {
                continue;
            }
            if (!"completed".equalsIgnoreCase(match.getState())) {
                return false;
            }
        }
        return true;
    }

    private boolean isTournamentDownToSingleClub(EsportsTournamentData tournament, String winnerTag) {
        if (tournament == null || tournament.getClubs() == null || tournament.getClubs().isEmpty()) {
            return false;
        }
        int activeClubCount = 0;
        boolean winnerListed = false;
        for (EsportsClubEntry club : tournament.getClubs()) {
            if (club == null) {
                continue;
            }
            if (club.getTag() != null && club.getTag().equalsIgnoreCase(winnerTag)) {
                winnerListed = true;
            }
            String status = club.getStatus();
            if (status != null) {
                String lower = status.toLowerCase(Locale.ROOT);
                if (lower.contains("eliminated")
                        || lower.contains("knocked")
                        || lower.contains("out")
                        || lower.contains("lost")
                        || lower.contains("dead")) {
                    continue;
                }
            }
            activeClubCount++;
        }
        return activeClubCount <= 1 && (winnerListed || activeClubCount == 1);
    }

    private void startWinnerCelebration(String winnerTag) {
        stopWinnerCelebration();
        List<Player> winners = collectWinnerPlayers(winnerTag);
        Location center = resolveWinnerCelebrationCenter(winners, winnerTag);
        if (center == null || center.getWorld() == null) {
            return;
        }
        trackTournamentWorld(center.getWorld());
        winnerCelebrationDragon = null;
        winnerCelebrationRiders.clear();
        winnerRideOrder.clear();

        String displayWinner = winnerTag == null ? "-" : winnerTag.toUpperCase(Locale.ROOT);
        if (winnerTag != null) {
            EsportsClubEntry club = activeClubData.get(winnerTag.toLowerCase(Locale.ROOT));
            if (club != null && club.getName() != null && !club.getName().trim().isEmpty()) {
                displayWinner = club.getName();
            }
        }
        int radius = (int) Math.round(Math.max(
                WINNER_CELEBRATION_ICE_RADIUS_MIN,
                Math.min(WINNER_CELEBRATION_ICE_RADIUS_MAX,
                        arenaBorderRadius > 0.0D ? arenaBorderRadius * 0.45D : 14.0D)
        ));
        paintWinnerIce(center, radius);
        Set<Player> audience = collectCelebrationAudience();
        if (audience.isEmpty()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online != null && online.isOnline()) {
                    audience.add(online);
                }
            }
        }
        broadcastWinnerTitle(audience, displayWinner);
        playSound(center, 1.0F, 1.1F, "UI_TOAST_CHALLENGE_COMPLETE", "LEVEL_UP");

        final Location baseCenter = center.clone();
        final String finalDisplayWinner = displayWinner;
        winnerCelebrationTask = new BukkitRunnable() {
            private int elapsedTicks = 0;

            @Override
            public void run() {
                if (elapsedTicks >= WINNER_CELEBRATION_SECONDS * 20) {
                    stopWinnerCelebration();
                    cancel();
                    return;
                }
                if (elapsedTicks % 20 == 0) {
                    broadcastWinnerTitle(collectCelebrationAudience(), finalDisplayWinner);
                }
                runWinnerParticleWave(baseCenter, radius);
                if (elapsedTicks % 8 == 0) {
                    for (int i = 0; i < WINNER_CELEBRATION_FIREWORKS_PER_WAVE; i++) {
                        spawnWinnerFirework(baseCenter, radius);
                    }
                }
                elapsedTicks += 2;
            }
        };
        winnerCelebrationTask.runTaskTimer(plugin, 0L, 2L);
    }

    private Set<Player> collectCelebrationAudience() {
        Set<Player> players = new HashSet<>();
        Set<UUID> ids = new HashSet<>(matchParticipants);
        ids.addAll(spectatorLobby);
        for (UUID id : ids) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    private void broadcastWinnerTitle(Collection<Player> players, String winnerName) {
        if (players == null || players.isEmpty()) {
            return;
        }
        String title = ChatColor.translateAlternateColorCodes('&',
                EsportsLang.get("game.celebration.title", "&bWinner: &e{winner}", "winner", winnerName));
        String subtitle = ChatColor.translateAlternateColorCodes('&',
                EsportsLang.get("game.celebration.subtitle", "&fArena Celebration"));
        for (Player player : players) {
            if (player != null && player.isOnline()) {
                sendTitle(player, title, subtitle);
            }
        }
    }

    private void runWinnerParticleWave(Location center, int radius) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        int waveRadius = Math.max(4, radius);
        for (int i = 0; i < WINNER_CELEBRATION_PARTICLE_POINTS; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = random.nextDouble() * waveRadius;
            double x = center.getX() + (Math.cos(angle) * distance);
            double z = center.getZ() + (Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;
            Location point = new Location(world, x + 0.5D, y + 0.2D, z + 0.5D);
            safeEffect(point, Effect.SMOKE, 1);
            if (i % 3 == 0) {
                safeEffect(point, Effect.MOBSPAWNER_FLAMES, 1);
            }
            if (i % 5 == 0) {
                safeEffect(point, Effect.MOBSPAWNER_FLAMES, 1);
            }
        }
    }

    private void spawnWinnerFirework(Location center, int radius) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = random.nextDouble() * Math.max(3.0D, radius);
        double x = center.getX() + (Math.cos(angle) * distance);
        double z = center.getZ() + (Math.sin(angle) * distance);
        int y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 2;
        Location launch = new Location(world, x, y, z);
        if (!isInsideWorldBorder(launch)) {
            return;
        }
        try {
            Firework firework = (Firework) world.spawnEntity(launch, EntityType.FIREWORK);
            FireworkMeta meta = firework.getFireworkMeta();
            FireworkEffect effect = FireworkEffect.builder()
                    .with(random.nextBoolean() ? FireworkEffect.Type.BURST : FireworkEffect.Type.BALL_LARGE)
                    .withColor(randomFireworkColor(), randomFireworkColor())
                    .withFade(randomFireworkColor())
                    .trail(true)
                    .flicker(true)
                    .build();
            meta.clearEffects();
            meta.addEffect(effect);
            meta.setPower(1);
            firework.setFireworkMeta(meta);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (firework != null && firework.isValid()) {
                    try {
                        firework.detonate();
                    } catch (Throwable ignored) {
                    }
                }
            }, 8L + random.nextInt(10));
        } catch (Throwable ignored) {
        }
    }

    private org.bukkit.Color randomFireworkColor() {
        return org.bukkit.Color.fromRGB(
                80 + random.nextInt(176),
                80 + random.nextInt(176),
                80 + random.nextInt(176)
        );
    }

    private void paintWinnerIce(Location center, int radius) {
        if (center == null || center.getWorld() == null || radius <= 0) {
            return;
        }
        // Only paint arena floor; do not change spectator/waiting lobby.
        if (activeMatchCenter == null || activeMatchCenter.getWorld() == null
                || !activeMatchCenter.getWorld().equals(center.getWorld())) {
            return;
        }
        Material ice = MaterialResolver.resolveMaterial("PACKED_ICE");
        if (ice == null) {
            ice = MaterialResolver.resolveMaterial("ICE");
        }
        if (ice == null) {
            return;
        }
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int minY = safeMinHeight(world);
        int r = Math.max(1, radius);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if ((x * x) + (z * z) > (r * r)) {
                    continue;
                }
                int bx = cx + x;
                int bz = cz + z;
                int y = world.getHighestBlockYAt(bx, bz) - 1;
                if (y < minY) {
                    continue;
                }
                Location blockLoc = new Location(world, bx, y, bz);
                if (!isInsideWorldBorder(blockLoc)) {
                    continue;
                }
                Block block = blockLoc.getBlock();
                if (block == null) {
                    continue;
                }
                Material current = block.getType();
                if (current == Material.AIR || current == Material.CHEST
                        || current == Material.BEDROCK || current == Material.BARRIER) {
                    continue;
                }
                if (current == Material.EMERALD_BLOCK || current == Material.DIAMOND_BLOCK) {
                    continue;
                }
                if (!isMagicBreakable(current)) {
                    continue;
                }
                try {
                    block.setType(ice, false);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void stopWinnerCelebration() {
        if (winnerCelebrationTask != null) {
            try {
                winnerCelebrationTask.cancel();
            } catch (Throwable ignored) {
            }
            winnerCelebrationTask = null;
        }
        if (winnerCelebrationDragon != null) {
            try {
                winnerCelebrationDragon.eject();
            } catch (Throwable ignored) {
            }
            try {
                winnerCelebrationDragon.remove();
            } catch (Throwable ignored) {
            }
            winnerCelebrationDragon = null;
        }
        winnerCelebrationRiders.clear();
        winnerRideOrder.clear();
        for (UUID projectileId : new HashSet<>(celebrationFireballs)) {
            for (World world : Bukkit.getWorlds()) {
                if (world == null) {
                    continue;
                }
                for (Entity entity : world.getEntities()) {
                    if (entity == null || !projectileId.equals(entity.getUniqueId())) {
                        continue;
                    }
                    try {
                        entity.remove();
                    } catch (Exception ignored) {
                    }
                    break;
                }
            }
        }
        celebrationFireballs.clear();
    }

    private List<Player> collectWinnerPlayers(String winnerTag) {
        List<Player> winners = new ArrayList<>();
        if (winnerTag == null) {
            return winners;
        }
        String normalized = winnerTag.toLowerCase(Locale.ROOT);
        for (UUID id : matchParticipants) {
            String tag = participantClubTags.get(id);
            if (tag == null || !normalized.equals(tag.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                winners.add(player);
            }
        }
        return winners;
    }

    private Location resolveWinnerCelebrationCenter(List<Player> winners, String winnerTag) {
        List<Location> points = new ArrayList<>();
        if (winners != null) {
            for (Player player : winners) {
                if (player != null && player.isOnline() && player.getWorld() != null) {
                    points.add(player.getLocation().clone());
                }
            }
        }
        Location center = computeCenter(points);
        if (center != null) {
            return center;
        }
        if (winnerTag != null) {
            for (Map.Entry<UUID, String> entry : participantClubTags.entrySet()) {
                if (entry.getValue() == null || !winnerTag.equalsIgnoreCase(entry.getValue())) {
                    continue;
                }
                Location spawn = participantSpawns.get(entry.getKey());
                if (spawn != null && spawn.getWorld() != null) {
                    return spawn.clone();
                }
            }
        }
        if (activeMatchCenter != null && activeMatchCenter.getWorld() != null) {
            return activeMatchCenter.clone();
        }
        Location spectator = getSpectatorLocationSnapshot();
        if (spectator != null && spectator.getWorld() != null) {
            return spectator;
        }
        return null;
    }

    private boolean safeMount(Entity vehicle, Entity passenger) {
        if (vehicle == null || passenger == null) {
            return false;
        }
        try {
            Method addPassenger = vehicle.getClass().getMethod("addPassenger", Entity.class);
            addPassenger.invoke(vehicle, passenger);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Method setPassenger = vehicle.getClass().getMethod("setPassenger", Entity.class);
            setPassenger.invoke(vehicle, passenger);
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void spawnCelebrationFireball(Location origin, Vector forwardDirection) {
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        try {
            SmallFireball fireball = (SmallFireball) origin.getWorld().spawnEntity(origin, EntityType.SMALL_FIREBALL);
            celebrationFireballs.add(fireball.getUniqueId());
            Vector base = forwardDirection == null ? new Vector(0.0D, 0.0D, 1.0D) : forwardDirection.clone();
            if (base.lengthSquared() <= 0.0001D) {
                base = new Vector(0.0D, 0.0D, 1.0D);
            }
            base = base.normalize().multiply(0.70D);
            Vector spread = new Vector(
                    (random.nextDouble() - 0.5D) * 0.65D,
                    -0.20D - (random.nextDouble() * 0.30D),
                    (random.nextDouble() - 0.5D) * 0.65D
            );
            Vector dir = base.add(spread).normalize();
            fireball.setDirection(dir);
            fireball.setYield(0.0F);
            fireball.setIsIncendiary(false);
        } catch (Throwable ignored) {
        }
    }

    public void logMatchEvent(String type, JsonObject payload) {
        String normalizedType = type == null ? "UNKNOWN" : type.trim().toUpperCase(Locale.ROOT);
        JsonObject source = payload == null ? new JsonObject() : payload;

        if (MATCH_EVENT_LOG_TO_CONSOLE && !"environment_damage".equalsIgnoreCase(type)) {
            try {
                plugin.getLogger().info("[Esports] Event " + normalizedType + ": " + source.toString());
            } catch (Exception ignored) {
            }
        }

        MatchReplayCapture capture = replayCapture;
        if (capture == null || capture.closed || capture.matchId <= 0) {
            return;
        }
        if (!isReplayCaptureEnabled()) {
            return;
        }
        if (!"PLAYER_DAMAGE".equals(normalizedType) && !hasActiveMatchContext()) {
            return;
        }
        if ("ENVIRONMENT_DAMAGE".equals(normalizedType) && !replayIncludeEnvironmentDamage()) {
            return;
        }
        if ("PLAYER_DAMAGE".equals(normalizedType)) {
            String throttleKey = buildDamageThrottleKey(source);
            long now = System.currentTimeMillis();
            Long prev = replayDamageThrottle.put(throttleKey, now);
            if (prev != null && (now - prev) < replayDamageMinIntervalMs()) {
                return;
            }
        }
        enqueueReplayEvent(capture, normalizedType, sanitizeReplayPayload(normalizedType, source), true);
    }

    private void startReplayFlushTask() {
        if (replayFlushTask != null) {
            return;
        }
        replayFlushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                flushReplayEvents(replayFlushBatchSize());
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "[EsportsReplay] Flush task failed.", ex);
            }
        }, 20L, 20L);
    }

    private void stopReplayFlushTask() {
        if (replayFlushTask != null) {
            try {
                replayFlushTask.cancel();
            } catch (Exception ignored) {
            }
            replayFlushTask = null;
        }
        flushReplayEvents(Integer.MAX_VALUE);
        replayEventQueue.clear();
        replayDamageThrottle.clear();
        replayCapture = null;
    }

    private void flushReplayEvents(int maxBatch) {
        if (manager == null) {
            replayEventQueue.clear();
            return;
        }
        int limit = Math.max(1, maxBatch);
        int processed = 0;
        ReplayEventEntry entry;
        while (processed < limit && (entry = replayEventQueue.poll()) != null) {
            try {
                JsonObject payload = null;
                if (entry.payloadJson != null && !entry.payloadJson.isEmpty()) {
                    payload = gson.fromJson(entry.payloadJson, JsonObject.class);
                }
                if (payload == null) {
                    payload = new JsonObject();
                }
                manager.recordMatchEvent(entry.matchId, entry.eventType, payload);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.FINE, "[EsportsReplay] Failed to persist match event.", ex);
            }
            processed++;
        }
    }

    private void beginMatchReplayCapture(List<String> clubs) {
        if (!isReplayCaptureEnabled() || manager == null || currentMatchId <= 0) {
            replayCapture = null;
            return;
        }
        EsportsTournamentData tournament = manager.getActiveTournament();
        if (tournament == null) {
            replayCapture = null;
            return;
        }
        MatchReplayCapture capture = new MatchReplayCapture(
                currentMatchId,
                tournament.getId(),
                tournament.getSlug(),
                tournament.getName(),
                System.currentTimeMillis(),
                replayMaxEventsPerMatch(),
                clubs == null ? Collections.emptyList() : clubs
        );
        replayCapture = capture;
        replayDamageThrottle.clear();

        JsonObject startPayload = new JsonObject();
        startPayload.addProperty("message", "match_start");
        startPayload.addProperty("tournament", tournament.getName() == null ? "" : tournament.getName());
        startPayload.addProperty("tournament_id", tournament.getId());
        startPayload.addProperty("match_id", currentMatchId);
        JsonArray clubArray = new JsonArray();
        if (clubs != null) {
            for (String club : clubs) {
                if (club != null && !club.trim().isEmpty()) {
                    clubArray.add(new JsonPrimitive(club.trim()));
                }
            }
        }
        startPayload.add("clubs", clubArray);
        enqueueReplayEvent(capture, "MATCH_START", sanitizeReplayPayload("MATCH_START", startPayload), false);
    }

    private void finishMatchReplayCapture(String winnerTag, String reason) {
        MatchReplayCapture capture = replayCapture;
        replayCapture = null;
        if (capture == null || capture.closed) {
            return;
        }
        capture.closed = true;
        replayDamageThrottle.clear();

        long endedAt = System.currentTimeMillis();
        long durationMs = Math.max(0L, endedAt - capture.startedAtMs);
        String reasonText = reason == null ? "" : reason.trim();
        String winner = winnerTag == null ? "" : winnerTag.trim();

        JsonObject endPayload = new JsonObject();
        endPayload.addProperty("message", "match_end");
        endPayload.addProperty("winner_tag", winner);
        endPayload.addProperty("reason", reasonText);
        endPayload.addProperty("duration_ms", durationMs);
        enqueueReplayEvent(capture, "MATCH_END", sanitizeReplayPayload("MATCH_END", endPayload), false);

        JsonObject metadata = new JsonObject();
        metadata.addProperty("source", "esports.match.replay-lite");
        metadata.addProperty("tournament_id", capture.tournamentId);
        metadata.addProperty("tournament_slug", capture.tournamentSlug == null ? "" : capture.tournamentSlug);
        metadata.addProperty("tournament_name", capture.tournamentName == null ? "" : capture.tournamentName);
        metadata.addProperty("match_id", capture.matchId);
        metadata.addProperty("winner_tag", winner);
        metadata.addProperty("reason", reasonText);
        metadata.addProperty("started_at_ms", capture.startedAtMs);
        metadata.addProperty("ended_at_ms", endedAt);
        metadata.addProperty("duration_ms", durationMs);
        metadata.addProperty("event_count", capture.acceptedEvents);
        metadata.addProperty("dropped_event_count", capture.droppedEvents);

        JsonObject typeCounts = new JsonObject();
        for (Map.Entry<String, Integer> e : capture.typeCounts.entrySet()) {
            typeCounts.addProperty(e.getKey(), Math.max(0, e.getValue()));
        }
        metadata.add("event_type_counts", typeCounts);

        JsonArray clubs = new JsonArray();
        for (String club : capture.clubTags) {
            if (club != null && !club.trim().isEmpty()) {
                clubs.add(new JsonPrimitive(club.trim()));
            }
        }
        metadata.add("clubs", clubs);

        String storageUrl = "es_match_events://tournament/" + capture.tournamentId + "/match/" + capture.matchId;
        String downloadUrl = buildEventsDownloadUrl(capture);
        manager.recordReplaySnapshot(capture.matchId, storageUrl, downloadUrl, durationMs, metadata);

        if (downloadUrl != null && !downloadUrl.trim().isEmpty()) {
            manager.updateMatchReplayPath(capture.matchId, downloadUrl);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> flushReplayEvents(Math.max(500, replayFlushBatchSize() * 6)));
    }

    private void enqueueReplayEvent(MatchReplayCapture capture, String eventType, JsonObject payload, boolean enforceLimit) {
        if (capture == null || capture.closed || capture.matchId <= 0) {
            return;
        }
        if (replayEventQueue.size() >= replayMaxQueueSize()) {
            capture.droppedEvents++;
            return;
        }
        if (enforceLimit && capture.acceptedEvents >= capture.maxEvents) {
            capture.droppedEvents++;
            return;
        }

        JsonObject safe = payload == null ? new JsonObject() : payload;
        safe.addProperty("event_type", eventType == null ? "INFO" : eventType);
        safe.addProperty("captured_at_ms", System.currentTimeMillis());
        safe.addProperty("match_id", capture.matchId);
        safe.addProperty("tournament_id", capture.tournamentId);

        replayEventQueue.offer(new ReplayEventEntry(
                capture.matchId,
                eventType == null ? "INFO" : eventType,
                safe.toString()
        ));
        capture.acceptedEvents++;
        capture.typeCounts.merge(eventType == null ? "INFO" : eventType, 1, Integer::sum);
    }

    private JsonObject sanitizeReplayPayload(String eventType, JsonObject payload) {
        JsonObject out = new JsonObject();
        if (payload == null) {
            return out;
        }
        int strMax = replayMaxStringLength();
        int decimals = replayLocationDecimals();

        for (Map.Entry<String, com.google.gson.JsonElement> entry : payload.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            com.google.gson.JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (value.isJsonPrimitive()) {
                com.google.gson.JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (primitive.isString()) {
                    out.addProperty(key, trimTo(primitive.getAsString(), strMax));
                } else if (primitive.isBoolean()) {
                    out.addProperty(key, primitive.getAsBoolean());
                } else if (primitive.isNumber()) {
                    double num = primitive.getAsDouble();
                    if (isCoordinateKey(key)) {
                        out.addProperty(key, roundTo(num, decimals));
                    } else if (Math.abs(num) > 1_000_000_000D) {
                        out.addProperty(key, Math.round(num));
                    } else {
                        out.addProperty(key, roundTo(num, 3));
                    }
                }
            } else if (value.isJsonObject()) {
                if (isReplayLocationObjectKey(key)) {
                    JsonObject loc = value.getAsJsonObject();
                    JsonObject compact = new JsonObject();
                    if (loc.has("world")) {
                        compact.addProperty("world", trimTo(loc.get("world").getAsString(), 64));
                    }
                    if (loc.has("x")) compact.addProperty("x", roundTo(loc.get("x").getAsDouble(), decimals));
                    if (loc.has("y")) compact.addProperty("y", roundTo(loc.get("y").getAsDouble(), decimals));
                    if (loc.has("z")) compact.addProperty("z", roundTo(loc.get("z").getAsDouble(), decimals));
                    if (loc.has("yaw")) compact.addProperty("yaw", roundTo(loc.get("yaw").getAsDouble(), 2));
                    if (loc.has("pitch")) compact.addProperty("pitch", roundTo(loc.get("pitch").getAsDouble(), 2));
                    out.add(key, compact);
                }
            } else if (value.isJsonArray()) {
                JsonArray arr = value.getAsJsonArray();
                JsonArray compact = new JsonArray();
                int max = Math.min(8, arr.size());
                for (int i = 0; i < max; i++) {
                    com.google.gson.JsonElement it = arr.get(i);
                    if (it == null || it.isJsonNull()) {
                        continue;
                    }
                    if (it.isJsonPrimitive() && it.getAsJsonPrimitive().isString()) {
                        compact.add(new JsonPrimitive(trimTo(it.getAsString(), strMax)));
                    } else if (it.isJsonPrimitive()) {
                        compact.add(it.getAsJsonPrimitive());
                    }
                }
                if (compact.size() > 0) {
                    out.add(key, compact);
                }
            }
        }

        if (eventType != null && !eventType.isEmpty()) {
            out.addProperty("event_type", eventType);
        }
        return out;
    }

    private boolean isReplayLocationObjectKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return "location".equals(normalized) || normalized.endsWith("_location");
    }

    private String buildDamageThrottleKey(JsonObject payload) {
        String attacker = payload != null && payload.has("attacker") ? payload.get("attacker").getAsString() : "";
        String victim = payload != null && payload.has("victim") ? payload.get("victim").getAsString() : "";
        String cause = payload != null && payload.has("cause") ? payload.get("cause").getAsString() : "";
        return attacker + ">" + victim + ":" + cause;
    }

    private String buildEventsDownloadUrl(MatchReplayCapture capture) {
        String slug = capture.tournamentSlug == null ? "" : capture.tournamentSlug.trim();
        String tournamentArg = slug.isEmpty() ? String.valueOf(capture.tournamentId) : slug;
        return "/Files/Api/esports.replay.viewer.php"
                + "?tournament=" + URLEncoder.encode(tournamentArg, StandardCharsets.UTF_8)
                + "&match_id=" + capture.matchId;
    }

    private boolean isReplayCaptureEnabled() {
        return plugin.getConfig("config").getBoolean("esports.replay.enabled", true);
    }

    private boolean replayIncludeEnvironmentDamage() {
        return plugin.getConfig("config").getBoolean("esports.replay.include-environment-damage", false);
    }

    private int replayMaxEventsPerMatch() {
        return Math.max(300, Math.min(20000,
                plugin.getConfig("config").getInt("esports.replay.max-events-per-match", REPLAY_DEFAULT_MAX_EVENTS)));
    }

    private int replayFlushBatchSize() {
        return Math.max(20, Math.min(500,
                plugin.getConfig("config").getInt("esports.replay.flush-batch-size", REPLAY_DEFAULT_FLUSH_BATCH)));
    }

    private int replayDamageMinIntervalMs() {
        return Math.max(0, Math.min(5000,
                plugin.getConfig("config").getInt("esports.replay.damage-event-min-interval-ms", REPLAY_DEFAULT_DAMAGE_MIN_INTERVAL_MS)));
    }

    private int replayMaxStringLength() {
        return Math.max(32, Math.min(1000,
                plugin.getConfig("config").getInt("esports.replay.max-string-length", REPLAY_DEFAULT_MAX_STRING_LEN)));
    }

    private int replayLocationDecimals() {
        return Math.max(0, Math.min(6,
                plugin.getConfig("config").getInt("esports.replay.location-decimals", REPLAY_DEFAULT_LOCATION_DECIMALS)));
    }

    private int replayMaxQueueSize() {
        return Math.max(500, Math.min(50000,
                plugin.getConfig("config").getInt("esports.replay.max-queue-size", REPLAY_DEFAULT_QUEUE_LIMIT)));
    }

    private String trimTo(String text, int max) {
        if (text == null) {
            return "";
        }
        String value = text.trim();
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private boolean isCoordinateKey(String key) {
        String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return "x".equals(lower) || "y".equals(lower) || "z".equals(lower);
    }

    private double roundTo(double value, int decimals) {
        double scale = Math.pow(10D, decimals);
        return Math.round(value * scale) / scale;
    }

    public void announceNextClub() {
        String next = getNextQueuedClub();
        if (next == null) {
            return;
        }
        Bukkit.broadcastMessage(CHAT_PREFIX + ChatColor.GOLD + "Next club: " + ChatColor.AQUA + next);
    }

    private void sendTitle(Player player, String title, String subtitle) {
        if (player == null) {
            return;
        }
        try {
            Method method = player.getClass().getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class);
            method.invoke(player, title, subtitle, 10, 30, 10);
            return;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {
        }
        try {
            Method method = player.getClass().getMethod("sendTitle", String.class, String.class);
            method.invoke(player, title, subtitle);
        } catch (Exception ignored) {
            player.sendMessage(title + ChatColor.GRAY + " " + subtitle);
        }
    }

    private void applySpectatorState(Player player, boolean giveItems) {
        if (player == null) {
            return;
        }
        clearPlayerInventory(player);
        try {
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(true);
            player.setFlying(true);
        } catch (Exception ignored) {
        }
        player.setHealth(Math.max(1.0D, resolveMaxHealth(player)));
        player.setFoodLevel(20);
        player.setSaturation(20F);
        ensureBracketItem(player);
        if (giveItems) {
            giveLobbyLeaveItem(player);
        }
        if (scoreboardService != null) {
            scoreboardService.enable(player, false);
        }
    }

    private void stashInventoryState(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        PlayerInventory inv = player.getInventory();
        savedContents.putIfAbsent(id, cloneArray(inv.getContents()));
        savedArmor.putIfAbsent(id, cloneArray(inv.getArmorContents()));
        ItemStack off = resolveOffhand(inv);
        if (off != null) {
            savedOffhand.putIfAbsent(id, off);
        }
    }

    private void restoreInventoryState(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        boolean hasSnapshot = savedContents.containsKey(id) || savedArmor.containsKey(id) || savedOffhand.containsKey(id);
        if (!hasSnapshot) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        applyOffhand(inv, null);
        ItemStack[] contents = savedContents.remove(id);
        if (contents != null) {
            inv.setContents(contents);
        }
        ItemStack[] armor = savedArmor.remove(id);
        if (armor != null) {
            inv.setArmorContents(armor);
        }
        ItemStack offhand = savedOffhand.remove(id);
        if (offhand != null) {
            applyOffhand(inv, offhand);
        }
        player.updateInventory();
    }

    private ItemStack[] cloneArray(ItemStack[] items) {
        if (items == null) {
            return null;
        }
        ItemStack[] clone = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            clone[i] = items[i] == null ? null : items[i].clone();
        }
        return clone;
    }

    private ItemStack resolveOffhand(PlayerInventory inv) {
        if (inv == null) {
            return null;
        }
        try {
            Method m = inv.getClass().getMethod("getItemInOffHand");
            Object value = m.invoke(inv);
            if (value instanceof ItemStack) {
                return ((ItemStack) value).clone();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void applyOffhand(PlayerInventory inv, ItemStack item) {
        if (inv == null) {
            return;
        }
        try {
            Method m = inv.getClass().getMethod("setItemInOffHand", ItemStack.class);
            m.invoke(inv, item);
        } catch (Exception ignored) {
        }
    }

    public ChatColor getClubChatColor(String tag) {
        if (tag == null) {
            return ChatColor.YELLOW;
        }
        EsportsClubEntry club = activeClubData.get(tag.toLowerCase(Locale.ROOT));
        if (club != null) {
            try {
                Method m = club.getClass().getMethod("getColor");
                Object value = m.invoke(club);
                if (value != null) {
                    String s = String.valueOf(value).trim();
                    if (!s.isEmpty()) {
                        return ChatColor.valueOf(s.toUpperCase(Locale.ROOT));
                    }
                }
            } catch (Exception ignored) {
            }
            String primary = club.getPrimaryColor();
            if (primary != null && !primary.trim().isEmpty()) {
                try {
                    return ChatColor.valueOf(primary.trim().toUpperCase(Locale.ROOT));
                } catch (Exception ignored) {
                }
            }
        }
        return ChatColor.YELLOW;
    }


    private void loadLeaderboardsFromFile() {
        // Stubbed
    }

    private void saveLeaderboardsToFile() {
        // Stubbed
    }

    private void startLeaderboardRefreshTask() {
        // Stubbed
    }

    private void stopLeaderboardRefreshTask() {
        if (leaderboardRefreshTask != null) {
            try {
                leaderboardRefreshTask.cancel();
            } catch (Exception ignored) {
            }
            leaderboardRefreshTask = null;
        }
    }

    public Collection<Player> collectMatchPlayers() {
        List<Player> list = new ArrayList<>();
        for (UUID id : matchParticipants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                list.add(p);
            }
        }
        return list;
    }

    private void sendToParticipants(String message) {
        if (message == null) {
            return;
        }
        for (Player player : collectMatchPlayers()) {
            player.sendMessage(message);
        }
    }

    private String getEsportsText(Player player, String key, String def, Object... placeholders) {
        String fullKey = "esports." + key;
        if (player != null) {
            Profile profile = Profile.getProfile(player.getName());
            if (profile != null) {
                return LanguageManager.get(profile, fullKey, def, placeholders);
            }
        }
        return LanguageManager.get(fullKey, def, placeholders);
    }

    private void sendToParticipantsLocalized(String key, String def, Object... placeholders) {
        for (Player player : collectMatchPlayers()) {
            player.sendMessage(getEsportsText(player, key, def, placeholders));
        }
    }

    private void broadcastLocalized(String key, String def, Object... placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(getEsportsText(player, key, def, placeholders));
        }
    }

    private void broadcastCenteredLocalized(String key, String def, Object... placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(CenteredMessage.generate(getEsportsText(player, key, def, placeholders)));
        }
    }

    public List<ClubMatchStatus> getActiveClubStatuses() {
        List<ClubMatchStatus> list = new ArrayList<>();
        for (Map.Entry<String, Set<UUID>> entry : activeClubMembers.entrySet()) {
            String tag = entry.getKey();
            Set<UUID> aliveSet = entry.getValue();
            int alive = aliveSet != null ? aliveSet.size() : 0;
            int total = clubMemberSizes.getOrDefault(tag, alive);
            EsportsClubEntry club = activeClubData.get(tag);
            String display = club != null ? club.getName() : tag.toUpperCase(Locale.ROOT);
            ChatColor color = getClubChatColor(tag);
            list.add(new ClubMatchStatus(display, tag, alive, total, color));
        }
        return list;
    }

    public static final class ClubMatchStatus {
        private final String displayName;
        private final String tag;
        private final int alive;
        private final int total;
        private final ChatColor color;

        public ClubMatchStatus(String displayName, String tag, int alive, int total, ChatColor color) {
            this.displayName = displayName;
            this.tag = tag;
            this.alive = alive;
            this.total = total;
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getTag() {
            return tag;
        }

        public int getAlive() {
            return alive;
        }

        public int getTotal() {
            return total;
        }

        public ChatColor getColor() {
            return color;
        }
    }

    // Debug: spawn/toggle banner follower for testing (outside match)
    public void spawnDebugBanner(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        BannerFollower existing = matchBannerFollowers.remove(id);
        if (existing != null) {
            existing.destroy();
            player.sendMessage(EsportsLang.get("game.debug.banner-cleared", CHAT_PREFIX + "&eDebug banner cleared."));
            return;
        }
        ItemStack banner = null;
        try {
            Material mat = MaterialResolver.resolveMaterial("WHITE_BANNER");
            if (mat == null) {
                mat = MaterialResolver.resolveMaterial("BANNER");
            }
            if (mat != null) {
                banner = new ItemStack(mat);
            }
        } catch (Exception ignored) {
        }
        if (banner == null) {
            player.sendMessage(EsportsLang.get("game.debug.no-banner-material", "&cNo banner material available for debug."));
            return;
        }
        String label = getParticipantClubTag(player.getUniqueId()).orElse(player.getName());
        BannerFollower follower = new BannerFollower(plugin, player, banner, label);
        matchBannerFollowers.put(id, follower);
        player.sendMessage(EsportsLang.get("game.debug.banner-spawned", CHAT_PREFIX + "&aDebug banner spawned."));
    }

    public int getCurrentMatchId() {
        return currentMatchId;
    }

    public String getDropDirectionLabel(Player player) {
        if (player == null || lastDropLocation == null || !isMatchRunning()) {
            return "-";
        }
        Location target = lastDropLocation;
        if (target.getWorld() == null || player.getWorld() == null || !target.getWorld().equals(player.getWorld())) {
            return "-";
        }
        Location pLoc = player.getLocation();
        double dx = target.getX() - pLoc.getX();
        double dz = target.getZ() - pLoc.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        String dir = resolveDirection(dx, dz);
        return Math.round(dist) + "m " + dir;
    }

    private String resolveDirection(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        angle = (angle + 360.0D) % 360.0D;
        String[] dirs = {"\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"};
        int idx = (int) Math.round(angle / 45.0D) % 8;
        return dirs[idx];
    }

    private void trackTournamentWorld(World world) {
        if (world == null) {
            return;
        }
        managedTournamentWorlds.add(world.getName());
        enforceTournamentWorldState(world);
        ensureTournamentWorldTask();
    }

    private void ensureTournamentWorldTask() {
        if (worldStateTask != null) {
            return;
        }
        worldStateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (managedTournamentWorlds.isEmpty()) {
                    stopTournamentWorldTask();
                    return;
                }
                for (String worldName : new HashSet<>(managedTournamentWorlds)) {
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        managedTournamentWorlds.remove(worldName);
                        continue;
                    }
                    enforceTournamentWorldState(world);
                }
            }
        };
        worldStateTask.runTaskTimer(plugin, 0L, 200L);
    }

    private void stopTournamentWorldTask() {
        if (worldStateTask != null) {
            try {
                worldStateTask.cancel();
            } catch (Exception ignored) {
            }
            worldStateTask = null;
        }
        managedTournamentWorlds.clear();
    }

    private void enforceTournamentWorldState(World world) {
        if (world == null) {
            return;
        }
        try {
            world.setTime(0L);
        } catch (Exception ignored) {
        }
        try {
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(Integer.MAX_VALUE);
            world.setThunderDuration(Integer.MAX_VALUE);
        } catch (Exception ignored) {
        }
        setGameRuleValue(world, "doDaylightCycle", "false");
        setGameRuleValue(world, "doWeatherCycle", "false");
        setGameRuleValue(world, "doMobSpawning", "false");
        setGameRuleValue(world, "doImmediateRespawn", "true");
        purgeTournamentMobs(world);
    }

    private void setGameRuleValue(World world, String key, String value) {
        if (world == null || key == null || value == null) {
            return;
        }
        try {
            Method method = world.getClass().getMethod("setGameRuleValue", String.class, String.class);
            method.invoke(world, key, value);
        } catch (Throwable ignored) {
        }
    }

    private void purgeTournamentMobs(World world) {
        if (world == null) {
            return;
        }
        UUID dragonId = winnerCelebrationDragon != null ? winnerCelebrationDragon.getUniqueId() : null;
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof LivingEntity) || entity instanceof Player || entity instanceof ArmorStand) {
                continue;
            }
            if (dragonId != null && dragonId.equals(entity.getUniqueId())) {
                continue;
            }
            if (entity.getType() == EntityType.BAT || entity.getType() == EntityType.GIANT) {
                continue;
            }
            if (HologramLibrary.isHologramEntity(entity)) {
                continue;
            }
            try {
                entity.remove();
            } catch (Exception ignored) {
            }
        }
    }

    private void captureArenaSnapshot(List<Location> spawnPoints) {
        if (arenaSnapshotCaptured || spawnPoints == null || spawnPoints.isEmpty()) {
            return;
        }
        Location center = computeCenter(spawnPoints);
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        arenaSnapshotWorldName = world.getName();
        double radius = Math.max(20.0D, Math.min(arenaBorderRadius, computeRadius(spawnPoints, center) + 6.0D));
        int r = (int) Math.ceil(radius);
        int minY = safeMinHeight(world);
        int maxY = safeMaxHeight(world) - 1;
        for (Location loc : spawnPoints) {
            if (loc != null) {
                minY = Math.max(minY, (int) Math.floor(loc.getY()) - 10);
                maxY = Math.min(maxY, (int) Math.ceil(loc.getY()) + 20);
            }
        }
        int saved = 0;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    Location key = new Location(world, center.getBlockX() + x, y, center.getBlockZ() + z);
                    String data = world.getBlockAt(key).getType().name();
                    arenaSnapshot.put(key, data);
                    saved++;
                }
            }
        }
        arenaSnapshotCaptured = true;
        plugin.getLogger().info("[Esports] Arena snapshot captured (" + saved + " blocks, radius=" + radius + ").");
    }

    private void restoreArenaSnapshot() {
        if (!arenaSnapshotCaptured || arenaSnapshot.isEmpty() || arenaSnapshotWorldName == null) {
            return;
        }
        World world = Bukkit.getWorld(arenaSnapshotWorldName);
        if (world == null) {
            return;
        }
        int restored = 0;
        for (Map.Entry<Location, String> entry : arenaSnapshot.entrySet()) {
            Location key = entry.getKey();
            String data = entry.getValue();
            if (key == null || data == null) {
                continue;
            }
            Location target = new Location(world, key.getBlockX(), key.getBlockY(), key.getBlockZ());
            try {
                Material mat = Material.matchMaterial(data);
                if (mat != null) {
                    target.getBlock().setType(mat, false);
                }
                restored++;
            } catch (Exception ignored) {
            }
        }
        plugin.getLogger().info("[Esports] Arena restored from snapshot (" + restored + " blocks).");
    }

    private Integer resolveMatchIdFromContexts(List<MatchClubContext> contexts) {
        if (contexts == null) {
            return null;
        }
        for (MatchClubContext ctx : contexts) {
            if (ctx == null || ctx.getClub() == null || ctx.getClub().getTag() == null) {
                continue;
            }
            Integer mid = resolveMatchIdByTag(ctx.getClub().getTag());
            if (mid != null && mid > 0) {
                return mid;
            }
        }
        return null;
    }

    private Integer resolveMatchIdByTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return null;
        }
        String normalized = tag.trim().toLowerCase(Locale.ROOT);
        Integer assigned = clubMatchAssignments.get(normalized);
        if (assigned != null && assigned > 0) {
            return assigned;
        }
        return manager.findMatchForClub(tag)
                .map(EsportsMatchData::getId)
                .orElse(null);
    }

    private Integer resolveMatchIdForQueuedClubs(List<String> clubs) {
        if (clubs == null || clubs.isEmpty()) {
            return null;
        }
        if (manager == null) {
            return null;
        }
        Set<String> selected = new HashSet<>();
        for (String tag : clubs) {
            if (tag != null && !tag.trim().isEmpty()) {
                selected.add(tag.trim().toLowerCase(Locale.ROOT));
            }
        }
        Set<Integer> candidateMatchIds = new LinkedHashSet<>();
        for (String tag : clubs) {
            Integer byTag = resolveMatchIdByTag(tag);
            if (byTag != null && byTag > 0) {
                candidateMatchIds.add(byTag);
            }
        }
        for (Integer candidateId : candidateMatchIds) {
            Optional<EsportsMatchData> matchOpt = manager.findMatchById(candidateId);
            if (!matchOpt.isPresent()) {
                continue;
            }
            if (matchContainsSelectedTags(matchOpt.get(), selected)) {
                return candidateId;
            }
        }
        manager.ensureBracketGeneratedIfMissing();
        manager.reloadNow();
        candidateMatchIds.clear();
        for (String tag : clubs) {
            Integer byTag = resolveMatchIdByTag(tag);
            if (byTag != null && byTag > 0) {
                candidateMatchIds.add(byTag);
            }
        }
        for (Integer candidateId : candidateMatchIds) {
            Optional<EsportsMatchData> matchOpt = manager.findMatchById(candidateId);
            if (!matchOpt.isPresent()) {
                continue;
            }
            if (matchContainsSelectedTags(matchOpt.get(), selected)) {
                return candidateId;
            }
        }
        Optional<EsportsMatchData> nextReady = manager.findNextReadyMatch();
        if (!nextReady.isPresent()) {
            return null;
        }
        EsportsMatchData next = nextReady.get();
        if (next.getClubAId() == null || next.getClubBId() == null) {
            return null;
        }
        String tagA = manager.getClubById(next.getClubAId()).map(EsportsClubEntry::getTag).orElse(null);
        String tagB = manager.getClubById(next.getClubBId()).map(EsportsClubEntry::getTag).orElse(null);
        if (tagA == null || tagB == null) {
            return null;
        }
        if (selected.contains(tagA.toLowerCase(Locale.ROOT)) && selected.contains(tagB.toLowerCase(Locale.ROOT))) {
            return next.getId();
        }
        return null;
    }

    private boolean matchContainsSelectedTags(EsportsMatchData match, Set<String> selectedTags) {
        if (match == null || selectedTags == null || selectedTags.isEmpty() || manager == null) {
            return false;
        }
        String tagA = match.getClubAId() == null
                ? null
                : manager.getClubById(match.getClubAId()).map(EsportsClubEntry::getTag).orElse(null);
        String tagB = match.getClubBId() == null
                ? null
                : manager.getClubById(match.getClubBId()).map(EsportsClubEntry::getTag).orElse(null);
        if (tagA == null || tagB == null) {
            return false;
        }
        String a = tagA.toLowerCase(Locale.ROOT);
        String b = tagB.toLowerCase(Locale.ROOT);
        return selectedTags.contains(a) && selectedTags.contains(b);
    }

    private void requeueClubs(List<String> clubs) {
        if (clubs == null || clubs.isEmpty()) {
            return;
        }
        for (int i = clubs.size() - 1; i >= 0; i--) {
            String tag = clubs.get(i);
            if (tag == null || tag.trim().isEmpty()) {
                continue;
            }
            String normalized = tag.trim().toLowerCase(Locale.ROOT);
            queuedTags.add(normalized);
            matchQueue.addFirst(normalized);
        }
    }

    private boolean ensureTournamentLiveAndBracket(CommandSender sender) {
        if (manager == null) {
            return true;
        }
        EsportsTournamentData tournament = manager.getActiveTournament();
        if (tournament == null) {
            if (sender != null) {
                sender.sendMessage(EsportsLang.get("game.queue.no-tournament", "&cNo active tournament found."));
            }
            return false;
        }
        String state = tournament.getState() == null ? "" : tournament.getState().toLowerCase(Locale.ROOT);
        if ("upcoming".equals(state) || "scheduled".equals(state)) {
            manager.startTournamentNow(false);
            manager.reloadNow();
            tournament = manager.getActiveTournament();
            state = tournament != null && tournament.getState() != null
                    ? tournament.getState().toLowerCase(Locale.ROOT) : "";
        }
        if (!"live".equals(state)) {
            if (sender != null) {
                sender.sendMessage(EsportsLang.get("game.queue.tournament-not-live",
                        "&cTournament is not live yet. Start it first."));
            }
            return false;
        }
        manager.ensureBracketGeneratedIfMissing();
        manager.reloadNow();
        queueNextBracketMatchIfReady();
        return true;
    }

    private void spawnParticipantBanner(Player player, EsportsClubEntry club) {
        if (player == null || club == null) {
            return;
        }
        ClubManager clubManager = plugin.getClubManager();
        if (clubManager == null) {
            return;
        }
        ItemStack banner = null;
        try {
            ClubData clubData = clubManager.getClubById(club.getClubId()).orElse(null);
            if (clubData != null) {
                banner = clubManager.parseBannerDesign(clubData.getBannerJson()).createPreviewItem();
            }
        } catch (Exception ignored) {
        }
        if (banner == null) {
            Material mat = MaterialResolver.resolveMaterial("WHITE_BANNER");
            if (mat == null) {
                mat = MaterialResolver.resolveMaterial("BANNER");
            }
            if (mat == null) {
                return;
            }
            banner = new ItemStack(mat);
        }
        String label = null;
        try {
            String tabName = player.getPlayerListName();
            if (tabName != null && !tabName.trim().isEmpty()) {
                label = ChatColor.translateAlternateColorCodes('&', tabName);
            }
        } catch (Throwable ignored) {
        }
        if (label == null) {
            // fallback to tag + name
            String tag = getParticipantClubTag(player.getUniqueId()).orElse(null);
            if (tag != null && !tag.isEmpty()) {
                label = "[" + tag + "] " + player.getName();
            } else {
                label = player.getName();
            }
        }
        BannerFollower follower = matchBannerFollowers.get(player.getUniqueId());
        if (follower != null) {
            follower.destroy();
        }
        follower = new BannerFollower(plugin, player, banner, label);
        matchBannerFollowers.put(player.getUniqueId(), follower);
    }

    private void removeParticipantBanner(UUID uuid) {
        if (uuid == null) {
            return;
        }
        BannerFollower follower = matchBannerFollowers.remove(uuid);
        if (follower != null) {
            follower.destroy();
        }
    }

    private void clearParticipantBanners() {
        for (UUID id : new HashSet<>(matchBannerFollowers.keySet())) {
            removeParticipantBanner(id);
        }
    }

    private int safeMinHeight(World world) {
        try {
            Method m = world.getClass().getMethod("getMinHeight");
            Object v = m.invoke(world);
            if (v instanceof Number) {
                return ((Number) v).intValue();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private int safeMaxHeight(World world) {
        try {
            Method m = world.getClass().getMethod("getMaxHeight");
            Object v = m.invoke(world);
            if (v instanceof Number) {
                return ((Number) v).intValue();
            }
        } catch (Exception ignored) {
        }
        try {
            return world.getMaxHeight();
        } catch (Throwable ignored) {
        }
        return 256;
    }

    private double resolveMaxHealth(Player player) {
        if (player == null) {
            return 20.0D;
        }
        try {
            Class<?> attrEnum = Class.forName("org.bukkit.attribute.Attribute");
            Object generic = Enum.valueOf((Class<Enum>) attrEnum, "GENERIC_MAX_HEALTH");
            Method m = player.getClass().getMethod("getAttribute", attrEnum);
            Object attr = m.invoke(player, generic);
            if (attr != null) {
                Method getValue = attr.getClass().getMethod("getValue");
                Object val = getValue.invoke(attr);
                if (val instanceof Number) {
                    return ((Number) val).doubleValue();
                }
            }
        } catch (Exception ignored) {
        }
        try {
            Method max = player.getClass().getMethod("getMaxHealth");
            Object val = max.invoke(player);
            if (val instanceof Number) {
                return ((Number) val).doubleValue();
            }
        } catch (Exception ignored) {
        }
        return 20.0D;
    }

    private static final class MatchClubContext {
        private final EsportsClubEntry club;
        private final Collection<Player> members;
        private final Location spawn;
        private final Integer matchId;

        MatchClubContext(EsportsClubEntry club, Collection<Player> members, Location spawn, Integer matchId) {
            this.club = club;
            this.members = members == null ? Collections.emptyList() : members;
            this.spawn = spawn != null ? spawn.clone() : null;
            this.matchId = matchId;
        }

        public EsportsClubEntry getClub() {
            return club;
        }

        public Collection<Player> getMembers() {
            return members;
        }

        public Location getSpawn() {
            return spawn != null ? spawn.clone() : null;
        }

        public Integer getMatchId() {
            return matchId;
        }
    }

    // Minimal listeners to avoid missing-class errors; extend if behavior is needed
    private final class BracketItemListener implements Listener {
        @EventHandler(ignoreCancelled = false, priority = org.bukkit.event.EventPriority.HIGHEST)
        public void onInteract(PlayerInteractEvent event) {
            if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            Player player = event.getPlayer();
            UUID id = player.getUniqueId();
            ItemStack item = event.getItem();
            if (item == null || item.getType() == Material.AIR) {
                item = player.getInventory().getItemInHand();
            }
            if (isLeaveItem(item) && spectatorLobby.contains(id)) {
                event.setCancelled(true);
                leaveSpectator(player);
                return;
            }
            if (!spectatorLobby.contains(id) && !isActiveCompetitor(id)) {
                return;
            }
            if (!isBracketItem(item)) {
                return;
            }
            event.setCancelled(true);
            if (manager != null) {
                manager.ensureBracketGeneratedIfMissing();
                manager.reloadNow();
            }
            new EsportsBracketMenu(plugin, player, manager);
        }

        @EventHandler(ignoreCancelled = true)
        public void onJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            if (spectatorLobby.contains(player.getUniqueId())) {
                ensureBracketItem(player);
            }
        }
    }

    private boolean shouldBypassForDisabledAuth(String rawMessage) {
        if (plugin.getConfig().getBoolean("modules.auth", false)) {
            return false;
        }
        if (rawMessage == null || rawMessage.isEmpty()) {
            return false;
        }
        String command = rawMessage.charAt(0) == '/' ? rawMessage.substring(1) : rawMessage;
        command = command.trim();
        if (command.isEmpty()) {
            return false;
        }
        int split = command.indexOf(' ');
        String label = split >= 0 ? command.substring(0, split) : command;
        return AUTH_COMMAND_BYPASS.contains(label.toLowerCase(Locale.ROOT));
    }

    // Cancel basic interactions/damage in spectator lobby
    private final class SpectatorProtectionListener implements Listener {
        @EventHandler(ignoreCancelled = true)
        public void onDamage(EntityDamageEvent event) {
            if (!(event.getEntity() instanceof Player)) {
                return;
            }
            Player p = (Player) event.getEntity();
            if (winnerCelebrationDragon != null && winnerCelebrationRiders.contains(p.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            if (hasActiveMatchContext() && isActiveCompetitor(p.getUniqueId())) {
                // during countdown, block all damage; after start allow
                if (isCountdownActive()) {
                    event.setCancelled(true);
                    return;
                }
                if (event instanceof EntityDamageByEntityEvent) {
                    Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
                    boolean fromTrackedPlayer = false;
                    if (damager instanceof Player) {
                        fromTrackedPlayer = isActiveCompetitor(((Player) damager).getUniqueId());
                    } else if (damager instanceof org.bukkit.entity.Projectile) {
                        Object shooter = ((org.bukkit.entity.Projectile) damager).getShooter();
                        if (shooter instanceof Player) {
                            fromTrackedPlayer = isActiveCompetitor(((Player) shooter).getUniqueId());
                        }
                    }
                    if (!fromTrackedPlayer) {
                        event.setCancelled(true);
                        return;
                    }
                }
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    UUID id = p.getUniqueId();
                    Long until = noFallDamageUntil.get(id);
                    if (until != null) {
                        long now = System.currentTimeMillis();
                        if (until >= now) {
                            event.setCancelled(true);
                            return;
                        }
                        noFallDamageUntil.remove(id);
                    }
                }
                return;
            }
            if (spectatorLobby.contains(p.getUniqueId())) {
                event.setCancelled(true);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onBlockBreak(BlockBreakEvent event) {
            UUID id = event.getPlayer().getUniqueId();
            if (hasActiveMatchContext() && isActiveCompetitor(id)) {
                if (isCountdownActive()) {
                    event.setCancelled(true);
                }
                return;
            }
            if (spectatorLobby.contains(id)) {
                ItemStack stack = event.getPlayer().getInventory().getItemInHand();
                if (isBracketItem(stack) || isLeaveItem(stack)) {
                    return;
                }
                event.setCancelled(true);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
            UUID id = event.getPlayer().getUniqueId();
            if (hasActiveMatchContext() && isActiveCompetitor(id)) {
                if (isCountdownActive()) {
                    event.setCancelled(true);
                }
                return;
            }
            if (spectatorLobby.contains(id)) {
                event.setCancelled(true);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onInteract(PlayerInteractEvent event) {
            UUID id = event.getPlayer().getUniqueId();
            if (hasActiveMatchContext() && isActiveCompetitor(id)) {
                if (isCountdownActive()) {
                    event.setCancelled(true);
                }
                return;
            }
            if (spectatorLobby.contains(id)) {
                event.setCancelled(true);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onFoodChange(org.bukkit.event.entity.FoodLevelChangeEvent event) {
            if (!(event.getEntity() instanceof Player)) {
                return;
            }
            Player p = (Player) event.getEntity();
            if (spectatorLobby.contains(p.getUniqueId())) {
                event.setCancelled(true);
                p.setFoodLevel(20);
                p.setSaturation(20f);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onRespawn(PlayerRespawnEvent event) {
            Player player = event.getPlayer();
            UUID id = player.getUniqueId();
            Location postMatchReturn = postMatchReturnLocations.remove(id);
            if (postMatchReturn != null && postMatchReturn.getWorld() != null) {
                event.setRespawnLocation(postMatchReturn);
                return;
            }
            if (spectatorLobby.contains(id)) {
                Location spectator = getSpectatorLocationSnapshot();
                if (spectator != null && spectator.getWorld() != null) {
                    event.setRespawnLocation(spectator);
                }
            }
        }

        @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGHEST)
        public void onCommand(PlayerCommandPreprocessEvent event) {
            Player player = event.getPlayer();
            if (player == null || player.isOp()) {
                return;
            }
            UUID id = player.getUniqueId();
            boolean esportsLocked = spectatorLobby.contains(id)
                    || (hasActiveMatchContext() && isActiveCompetitor(id))
                    || winnerCelebrationRiders.contains(id);
            if (!esportsLocked) {
                return;
            }
            if (shouldBypassForDisabledAuth(event.getMessage())) {
                return;
            }
            event.setCancelled(true);
            player.sendMessage(EsportsLang.get(
                    "game.match.command-blocked",
                    "&8[E-Sports] &cYou cannot use commands while participating in E-Sports."
            ));
        }

        @EventHandler(ignoreCancelled = true)
        public void onDrop(PlayerDropItemEvent event) {
            UUID id = event.getPlayer().getUniqueId();
            if (spectatorLobby.contains(id) || (hasActiveMatchContext() && isActiveCompetitor(id) && isCountdownActive())) {
                event.setCancelled(true);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player)) {
                return;
            }
            UUID id = ((Player) event.getWhoClicked()).getUniqueId();
            if (spectatorLobby.contains(id) || (hasActiveMatchContext() && isActiveCompetitor(id) && isCountdownActive())) {
                event.setCancelled(true);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onInventoryDrag(InventoryDragEvent event) {
            if (!(event.getWhoClicked() instanceof Player)) {
                return;
            }
            UUID id = ((Player) event.getWhoClicked()).getUniqueId();
            if (spectatorLobby.contains(id) || (hasActiveMatchContext() && isActiveCompetitor(id) && isCountdownActive())) {
                event.setCancelled(true);
            }
        }
    }

    // Cancel friendly fire between club members during match
    private final class FriendlyFireListener implements Listener {
        @EventHandler(ignoreCancelled = true)
        public void onDamage(EntityDamageByEntityEvent event) {
            if (!(event.getEntity() instanceof Player)) {
                return;
            }
            if (celebrationFireballs.contains(event.getDamager().getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            Player victim = (Player) event.getEntity();
            Player damager = null;
            if (event.getDamager() instanceof Player) {
                damager = (Player) event.getDamager();
            } else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
                Object shooter = ((org.bukkit.entity.Projectile) event.getDamager()).getShooter();
                if (shooter instanceof Player) {
                    damager = (Player) shooter;
                }
            }
            if (damager == null) {
                return;
            }
            UUID victimId = victim.getUniqueId();
            UUID damagerId = damager.getUniqueId();
            if (victimId.equals(damagerId)) {
                event.setCancelled(true);
                return;
            }
            if (spectatorLobby.contains(damagerId) || spectatorLobby.contains(victimId)) {
                event.setCancelled(true);
                return;
            }
            String tagVictim = getParticipantClubTag(victim.getUniqueId()).orElse(null);
            String tagDamager = getParticipantClubTag(damager.getUniqueId()).orElse(null);
            if (tagVictim != null && tagDamager != null && tagVictim.equalsIgnoreCase(tagDamager)) {
                event.setCancelled(true);
            }
        }
    }

    // Handles drop chest interaction
    private final class ChestOpenListener implements Listener {
        @EventHandler(ignoreCancelled = true)
        public void onInteract(PlayerInteractEvent event) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            Block block = event.getClickedBlock();
            if (block == null || block.getType() != Material.CHEST) {
                return;
            }
            Location key = normalizeBlock(block.getLocation());
            if (key == null || !activeChests.containsKey(key)) {
                return;
            }
            Chest chest = (Chest) block.getState();
            int tier = activeChests.getOrDefault(key, 1);

            if (isInventoryEmpty(chest.getBlockInventory())) {
                applyLootToChest(chest, tier, false);
            }
            spawnChestHologram(key, event.getPlayer());
        }
    }

    private final class ParticipantLifecycleListener implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            Player player = event.getPlayer();
            if (player == null) {
                return;
            }
            handleTournamentQuit(player);
        }
    }

    private final class SpecialSupplyListener implements Listener {
        @EventHandler(ignoreCancelled = true)
        public void onInteract(PlayerInteractEvent event) {
            Action action = event.getAction();
            if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            Player player = event.getPlayer();
            UUID id = player.getUniqueId();
            long now = System.currentTimeMillis();

            Block clicked = event.getClickedBlock();
            if (clicked != null) {
                Location key = normalizeBlock(clicked.getLocation());
                if (key != null && !specialSupplyTiers.containsKey(key)) {
                    Location above = normalizeBlock(key.clone().add(0.0D, 1.0D, 0.0D));
                    if (above != null && specialSupplyTiers.containsKey(above)) {
                        key = above;
                    }
                }
                if (key != null && specialSupplyTiers.containsKey(key)) {
                    event.setCancelled(true);
                    claimSpecialSupply(player, key, now, true);
                    return;
                }
            }

            ItemStack hand = event.getItem();
            if (hand == null || hand.getType() == Material.AIR) {
                hand = player.getInventory().getItemInHand();
            }
            if (hand == null || !isSpecialUtilityItem(hand)) {
                return;
            }
            event.setCancelled(true);
            if (!canUseSpecialItem(player)) {
                return;
            }
            Long itemBlocked = specialItemCooldown.get(id);
            if (itemBlocked != null && itemBlocked > now) {
                long waitMs = itemBlocked - now;
                player.sendMessage(EsportsLang.get("game.special.item-cooldown",
                        CHAT_PREFIX + "&cItem cooldown: {seconds}s",
                        "seconds", String.format(Locale.US, "%.1f", Math.max(0.1D, waitMs / 1000.0D))));
                return;
            }

            String stripped = ChatColor.stripColor(resolveDisplayName(hand));
            String specialType = resolveSpecialItemType(stripped);
            boolean used = false;
            if (SPECIAL_ITEM_TRAMPOLINE.equalsIgnoreCase(specialType)) {
                used = useTrampoline(player);
            } else if (SPECIAL_ITEM_FIRE_BLASTER.equalsIgnoreCase(specialType)) {
                used = shootFireWeapon(player, false);
            } else if (SPECIAL_ITEM_INFERNO.equalsIgnoreCase(specialType)) {
                used = shootFireWeapon(player, true);
            } else if (SPECIAL_ITEM_MAGIC_WAND.equalsIgnoreCase(specialType)) {
                used = useMagicWand(player, clicked);
            } else if (SPECIAL_ITEM_FORCE_ORB.equalsIgnoreCase(specialType)) {
                used = throwSpecialOrb(player, SPECIAL_ITEM_FORCE_ORB, 1.35D);
            } else if (SPECIAL_ITEM_METEOR_ORB.equalsIgnoreCase(specialType)) {
                used = throwSpecialOrb(player, SPECIAL_ITEM_METEOR_ORB, 1.1D);
            }
            if (used) {
                consumeSpecialItemUse(player, hand, specialType);
                specialItemCooldown.put(id, now + SPECIAL_ITEM_COOLDOWN_MS);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onProjectileHit(ProjectileHitEvent event) {
            if (!(event.getEntity().getShooter() instanceof Player)) {
                return;
            }
            Player shooter = (Player) event.getEntity().getShooter();
            if (!canUseSpecialItem(shooter)) {
                return;
            }
            String type = specialProjectiles.remove(event.getEntity().getUniqueId());
            if (type == null || type.trim().isEmpty()) {
                return;
            }
            Location impact = resolveProjectileImpact(event.getEntity(), event);
            if (impact == null || impact.getWorld() == null || !isInsideWorldBorder(impact)) {
                return;
            }
            if (SPECIAL_ITEM_FORCE_ORB.equalsIgnoreCase(type) || "Force Orb".equalsIgnoreCase(type)) {
                detonateForceOrb(shooter, impact);
            } else if (SPECIAL_ITEM_METEOR_ORB.equalsIgnoreCase(type) || "Meteor Orb".equalsIgnoreCase(type)) {
                detonateMeteorOrb(shooter, impact);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            Location key = normalizeBlock(event.getBlock().getLocation());
            if (key == null) {
                return;
            }
            if (specialSupplyTiers.containsKey(key)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(EsportsLang.get("game.drops.break-denied",
                        CHAT_PREFIX + "&eYou cannot break this drop. Walk over it to claim."));
                return;
            }
            Location above = normalizeBlock(key.clone().add(0.0D, 1.0D, 0.0D));
            if (above != null && specialSupplyTiers.containsKey(above)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(EsportsLang.get("game.drops.break-denied",
                        CHAT_PREFIX + "&eYou cannot break this drop. Walk over it to claim."));
            }
        }
    }

    private Location resolveProjectileImpact(Entity projectile, ProjectileHitEvent event) {
        if (event != null) {
            try {
                Method m = event.getClass().getMethod("getHitBlock");
                Object hit = m.invoke(event);
                if (hit instanceof Block) {
                    return ((Block) hit).getLocation().clone().add(0.5D, 0.5D, 0.5D);
                }
            } catch (Exception ignored) {
            }
        }
        if (projectile == null || projectile.getWorld() == null) {
            return null;
        }
        return projectile.getLocation().clone();
    }

    private boolean canUseSpecialItem(Player player) {
        if (player == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        return isMatchRunning() && !isCountdownRunning() && isActiveCompetitor(id) && !spectatorLobby.contains(id);
    }

    private boolean claimSpecialSupply(Player player, Location supplyLocation, long now, boolean notifyCooldown) {
        if (player == null || supplyLocation == null) {
            return false;
        }
        if (!canUseSpecialItem(player)) {
            return false;
        }
        Location key = normalizeBlock(supplyLocation);
        if (key == null || !specialSupplyTiers.containsKey(key)) {
            return false;
        }
        UUID id = player.getUniqueId();
        Long blockedUntil = specialSupplyCooldown.get(id);
        if (blockedUntil != null && blockedUntil > now) {
            if (notifyCooldown) {
                long wait = Math.max(1L, (blockedUntil - now + 999L) / 1000L);
                player.sendMessage(EsportsLang.get("game.special.drop-cooldown",
                        CHAT_PREFIX + "&cSpecial drop cooldown: {seconds}s",
                        "seconds", wait));
            }
            return false;
        }
        ItemStack reward = specialSupplyRewards.get(key);
        if (reward == null) {
            reward = createSpecialSupplyRewardForTier(specialSupplyTiers.getOrDefault(key, 1));
        }
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(reward.clone());
        if (!leftovers.isEmpty()) {
            for (ItemStack item : leftovers.values()) {
                if (item == null) {
                    continue;
                }
                try {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                } catch (Exception ignored) {
                }
            }
        }
        specialSupplyCooldown.put(id, now + SPECIAL_SUPPLY_COOLDOWN_MS);
        restoreSpecialSupplyAt(key, true);
        player.sendMessage(EsportsLang.get("game.special.received",
                CHAT_PREFIX + "&aSpecial item received: &b{name}",
                "name", resolveDisplayName(reward)));
        playSound(player.getLocation(), 1.0F, 1.2F, "ENTITY_PLAYER_LEVELUP", "LEVEL_UP");
        return true;
    }

    private void tryAutoCollectSpecialSupplies() {
        if (specialSupplyTiers.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Location key : new HashSet<>(specialSupplyTiers.keySet())) {
            if (key == null || key.getWorld() == null || !specialSupplyTiers.containsKey(key)) {
                continue;
            }
            Location pickup = key.clone().add(0.5D, 1.1D, 0.5D);
            for (Player player : key.getWorld().getPlayers()) {
                if (player == null || !player.isOnline() || player.getWorld() == null || !player.getWorld().equals(key.getWorld())) {
                    continue;
                }
                if (player.getLocation().distanceSquared(pickup) > SPECIAL_SUPPLY_PICKUP_RADIUS_SQ) {
                    continue;
                }
                if (claimSpecialSupply(player, key, now, false)) {
                    break;
                }
            }
        }
    }

    private boolean isSpecialUtilityItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        String stripped = ChatColor.stripColor(resolveDisplayName(item));
        return resolveSpecialItemType(stripped) != null;
    }

    private boolean matchesSpecialName(String strippedName, String referenceName) {
        if (strippedName == null || referenceName == null) {
            return false;
        }
        if (strippedName.equalsIgnoreCase(referenceName)) {
            return true;
        }
        return strippedName.toLowerCase(Locale.ROOT).startsWith(referenceName.toLowerCase(Locale.ROOT) + " [");
    }

    private boolean throwSpecialOrb(Player player, String type, double speed) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        if (direction == null || direction.lengthSquared() <= 0.0001D) {
            direction = new Vector(0.0D, 0.0D, 1.0D);
        }
        direction = direction.normalize();
        Location spawn = eye.clone().add(direction.clone().multiply(0.6D));
        Projectile projectile;
        try {
            projectile = (Projectile) player.getWorld().spawnEntity(spawn, EntityType.SNOWBALL);
        } catch (Exception ex) {
            return false;
        }
        projectile.setShooter(player);
        projectile.setVelocity(direction.multiply(Math.max(0.6D, speed)));
        try {
            projectile.setMetadata("es-special", new FixedMetadataValue(plugin, type));
        } catch (Exception ignored) {
        }
        specialProjectiles.put(projectile.getUniqueId(), type);
        playSound(player.getLocation(), 1.0F, 1.0F, "ENTITY_SNOWBALL_THROW", "ENTITY_ENDER_PEARL_THROW");
        return true;
    }

    private void detonateForceOrb(Player shooter, Location impact) {
        if (impact == null || impact.getWorld() == null) {
            return;
        }
        safeEffect(impact, Effect.SMOKE, 8);
        playSound(impact, 1.0F, 1.2F, "ENTITY_ENDER_DRAGON_FLAP", "BAT_TAKEOFF");
        for (Player target : collectMatchPlayers()) {
            if (target == null || !target.isOnline() || target.getWorld() == null || !target.getWorld().equals(impact.getWorld())) {
                continue;
            }
            if (target.getLocation().distanceSquared(impact) > 16.0D) {
                continue;
            }
            Vector push = target.getLocation().toVector().subtract(impact.toVector());
            if (push.lengthSquared() <= 0.001D) {
                push = new Vector(0.1D, 0.0D, 0.0D);
            }
            push = push.normalize().multiply(1.05D).setY(0.55D);
            target.setVelocity(push);
            noFallDamageUntil.put(target.getUniqueId(), System.currentTimeMillis() + 3000L);
        }
    }

    private void detonateMeteorOrb(Player shooter, Location impact) {
        if (impact == null || impact.getWorld() == null) {
            return;
        }
        createMeteorStrike(impact, shooter);
    }

    private void consumeSpecialItemUse(Player player, ItemStack hand, String specialType) {
        if (player == null || hand == null || specialType == null) {
            return;
        }
        int uses = readSpecialItemUses(hand);
        if (uses <= 1) {
            if (hand.getAmount() > 1) {
                hand.setAmount(hand.getAmount() - 1);
            } else {
                try {
                    player.getInventory().setItemInHand(null);
                } catch (Throwable ignored) {
                    int slot = player.getInventory().getHeldItemSlot();
                    player.getInventory().setItem(slot, null);
                }
            }
            player.updateInventory();
            return;
        }
        writeSpecialItemUses(hand, uses - 1, specialType);
        player.updateInventory();
    }

    private int readSpecialItemUses(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return SPECIAL_ITEM_DEFAULT_USES;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return SPECIAL_ITEM_DEFAULT_USES;
        }
        for (String line : meta.getLore()) {
            String plain = ChatColor.stripColor(line == null ? "" : line);
            if (!isUsesLoreLine(plain)) {
                continue;
            }
            String digits = plain.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                continue;
            }
            try {
                return Math.max(1, Integer.parseInt(digits));
            } catch (NumberFormatException ignored) {
            }
        }
        return SPECIAL_ITEM_DEFAULT_USES;
    }

    private void writeSpecialItemUses(ItemStack item, int uses, String specialType) {
        if (item == null || uses <= 0) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        String displayName = getSpecialItemDisplayName(specialType);
        meta.setDisplayName(displayName + ChatColor.GRAY + " [" + uses + "]");
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        boolean replaced = false;
        for (int i = 0; i < lore.size(); i++) {
            String plain = ChatColor.stripColor(lore.get(i) == null ? "" : lore.get(i));
            if (!isUsesLoreLine(plain)) {
                continue;
            }
            lore.set(i, getSpecialUsesLoreLine(uses));
            replaced = true;
            break;
        }
        if (!replaced) {
            lore.add(0, getSpecialUsesLoreLine(uses));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private boolean isUsesLoreLine(String plainLine) {
        if (plainLine == null) {
            return false;
        }
        String normalized = plainLine.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        String localizedPrefix = getSpecialUsesPrefix().trim().toLowerCase(Locale.ROOT);
        if (!localizedPrefix.isEmpty() && normalized.startsWith(localizedPrefix)) {
            return true;
        }
        return normalized.startsWith("uses:");
    }

    private String getSpecialUsesPrefix() {
        return EsportsLang.get("game.special.uses-prefix", "Uses:");
    }

    private String getSpecialUsesLoreLine(int uses) {
        return ChatColor.YELLOW + EsportsLang.get("game.special.uses-prefix", "Uses:")
                + " " + ChatColor.WHITE + uses;
    }

    private String resolveSpecialItemType(String strippedName) {
        if (strippedName == null || strippedName.trim().isEmpty()) {
            return null;
        }
        String plain = strippedName.trim();
        if (matchesSpecialName(plain, getSpecialItemPlainName(SPECIAL_ITEM_TRAMPOLINE))
                || matchesSpecialName(plain, "Trampoline")) {
            return SPECIAL_ITEM_TRAMPOLINE;
        }
        if (matchesSpecialName(plain, getSpecialItemPlainName(SPECIAL_ITEM_FIRE_BLASTER))
                || matchesSpecialName(plain, "Fire Blaster")) {
            return SPECIAL_ITEM_FIRE_BLASTER;
        }
        if (matchesSpecialName(plain, getSpecialItemPlainName(SPECIAL_ITEM_INFERNO))
                || matchesSpecialName(plain, "Inferno Launcher")) {
            return SPECIAL_ITEM_INFERNO;
        }
        if (matchesSpecialName(plain, getSpecialItemPlainName(SPECIAL_ITEM_MAGIC_WAND))
                || matchesSpecialName(plain, "Magic Wand")) {
            return SPECIAL_ITEM_MAGIC_WAND;
        }
        if (matchesSpecialName(plain, getSpecialItemPlainName(SPECIAL_ITEM_FORCE_ORB))
                || matchesSpecialName(plain, "Force Orb")) {
            return SPECIAL_ITEM_FORCE_ORB;
        }
        if (matchesSpecialName(plain, getSpecialItemPlainName(SPECIAL_ITEM_METEOR_ORB))
                || matchesSpecialName(plain, "Meteor Orb")) {
            return SPECIAL_ITEM_METEOR_ORB;
        }
        return null;
    }

    private String getSpecialItemPlainName(String itemType) {
        if (SPECIAL_ITEM_TRAMPOLINE.equalsIgnoreCase(itemType)) {
            return EsportsLang.get("game.special.items.trampoline", "Trampoline");
        }
        if (SPECIAL_ITEM_FIRE_BLASTER.equalsIgnoreCase(itemType)) {
            return EsportsLang.get("game.special.items.fire-blaster", "Fire Blaster");
        }
        if (SPECIAL_ITEM_INFERNO.equalsIgnoreCase(itemType)) {
            return EsportsLang.get("game.special.items.inferno-launcher", "Inferno Launcher");
        }
        if (SPECIAL_ITEM_MAGIC_WAND.equalsIgnoreCase(itemType)) {
            return EsportsLang.get("game.special.items.magic-wand", "Magic Wand");
        }
        if (SPECIAL_ITEM_FORCE_ORB.equalsIgnoreCase(itemType)) {
            return EsportsLang.get("game.special.items.force-orb", "Force Orb");
        }
        if (SPECIAL_ITEM_METEOR_ORB.equalsIgnoreCase(itemType)) {
            return EsportsLang.get("game.special.items.meteor-orb", "Meteor Orb");
        }
        return EsportsLang.get("game.special.items.unknown", "Special Item");
    }

    private ChatColor getSpecialItemColor(String itemType) {
        if (SPECIAL_ITEM_TRAMPOLINE.equalsIgnoreCase(itemType)) {
            return ChatColor.GREEN;
        }
        if (SPECIAL_ITEM_FIRE_BLASTER.equalsIgnoreCase(itemType)) {
            return ChatColor.RED;
        }
        if (SPECIAL_ITEM_INFERNO.equalsIgnoreCase(itemType)) {
            return ChatColor.GOLD;
        }
        if (SPECIAL_ITEM_MAGIC_WAND.equalsIgnoreCase(itemType)) {
            return ChatColor.LIGHT_PURPLE;
        }
        if (SPECIAL_ITEM_FORCE_ORB.equalsIgnoreCase(itemType)) {
            return ChatColor.AQUA;
        }
        if (SPECIAL_ITEM_METEOR_ORB.equalsIgnoreCase(itemType)) {
            return ChatColor.DARK_RED;
        }
        return ChatColor.WHITE;
    }

    private String getSpecialItemDisplayName(String itemType) {
        return getSpecialItemColor(itemType) + getSpecialItemPlainName(itemType);
    }

    private boolean useTrampoline(Player player) {
        Location baseKey = normalizeBlock(player.getLocation().clone().subtract(0.0D, 1.0D, 0.0D));
        if (baseKey == null || baseKey.getWorld() == null) {
            return false;
        }
        if (activeChests.containsKey(baseKey) || specialSupplyTiers.containsKey(baseKey)) {
            player.sendMessage(EsportsLang.get("game.special.trampoline-denied",
                    CHAT_PREFIX + "&cYou cannot use Trampoline here."));
            return false;
        }
        Block base = baseKey.getBlock();
        if (base == null) {
            return false;
        }
        Material oldType = base.getType();
        if ("BEDROCK".equalsIgnoreCase(oldType.name()) || oldType == Material.CHEST) {
            player.sendMessage(EsportsLang.get("game.special.trampoline-denied",
                    CHAT_PREFIX + "&cYou cannot use Trampoline here."));
            return false;
        }
        base.setType(Material.SLIME_BLOCK, false);
        noFallDamageUntil.put(player.getUniqueId(), System.currentTimeMillis() + TRAMPOLINE_FALL_PROTECT_MS);
        Vector velocity = player.getVelocity() == null ? new Vector(0.0D, 0.0D, 0.0D) : player.getVelocity().clone();
        velocity.setY(Math.max(1.05D, velocity.getY() + 0.9D));
        player.setVelocity(velocity);
        playSound(player.getLocation(), 1.0F, 1.4F, "BLOCK_SLIME_BLOCK_FALL", "SLIME_WALK", "SLIME_ATTACK");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (baseKey.getWorld() == null) {
                    return;
                }
                Block block = baseKey.getBlock();
                if (block != null && block.getType() == Material.SLIME_BLOCK) {
                    block.setType(oldType, false);
                }
            } catch (Exception ignored) {
            }
        }, 60L);
        return true;
    }

    private boolean shootFireWeapon(Player player, boolean inferno) {
        Location eye = player.getEyeLocation();
        if (eye == null || eye.getWorld() == null) {
            return false;
        }
        Vector direction = eye.getDirection();
        if (direction == null || direction.lengthSquared() <= 0.0001D) {
            direction = new Vector(0.0D, 0.0D, 1.0D);
        }
        direction = direction.normalize();
        Location spawnLoc = eye.clone().add(direction.clone().multiply(0.8D));
        try {
            if (inferno) {
                Fireball fireball = (Fireball) eye.getWorld().spawnEntity(spawnLoc, EntityType.FIREBALL);
                fireball.setShooter(player);
                fireball.setDirection(direction);
                fireball.setVelocity(direction.clone().multiply(1.15D));
                fireball.setYield(2.8F);
                fireball.setIsIncendiary(true);
            } else {
                SmallFireball fireball = (SmallFireball) eye.getWorld().spawnEntity(spawnLoc, EntityType.SMALL_FIREBALL);
                fireball.setShooter(player);
                fireball.setDirection(direction);
                fireball.setVelocity(direction.clone().multiply(1.55D));
                fireball.setYield(1.2F);
                fireball.setIsIncendiary(true);
            }
        } catch (Exception ex) {
            return false;
        }
        playSound(player.getLocation(), 1.0F, inferno ? 0.8F : 1.1F,
                "ENTITY_BLAZE_SHOOT", "GHAST_FIREBALL", "BLAZE_BREATH");
        return true;
    }

    private boolean useMagicWand(Player player, Block clicked) {
        Location target = null;
        if (clicked != null) {
            target = normalizeBlock(clicked.getLocation());
        }
        if (target == null) {
            try {
                Block aimed = player.getTargetBlock((Set<Material>) null, 16);
                if (aimed != null && aimed.getType() != Material.AIR) {
                    target = normalizeBlock(aimed.getLocation());
                }
            } catch (Exception ignored) {
            }
        }
        if (target == null || target.getWorld() == null) {
            player.sendMessage(EsportsLang.get("game.special.magicwand-no-target",
                    CHAT_PREFIX + "&cNo target found for Magic Wand."));
            return false;
        }
        if (!isInsideWorldBorder(target)) {
            player.sendMessage(EsportsLang.get("game.special.magicwand-outside",
                    CHAT_PREFIX + "&cTarget is outside the border."));
            return false;
        }
        int broken = shatterBlocksNear(target, 2);
        if (broken <= 0) {
            player.sendMessage(EsportsLang.get("game.special.magicwand-no-blocks",
                    CHAT_PREFIX + "&eNo valid blocks to shatter."));
            return false;
        }
        safeEffect(target.clone().add(0.5D, 0.8D, 0.5D), Effect.SMOKE, 6);
        playSound(target, 1.0F, 0.9F, "ENTITY_GENERIC_EXPLODE", "EXPLODE");
        player.sendMessage(EsportsLang.get("game.special.magicwand-shattered",
                CHAT_PREFIX + "&dMagic Wand shattered &f{count}&d blocks.",
                "count", broken));
        return true;
    }

    private int shatterBlocksNear(Location center, int radius) {
        return shatterBlocksNear(center, radius, Integer.MAX_VALUE);
    }

    private int shatterBlocksNear(Location center, int radius, int maxBroken) {
        if (center == null || center.getWorld() == null || radius <= 0) {
            return 0;
        }
        int breakLimit = maxBroken <= 0 ? Integer.MAX_VALUE : maxBroken;
        int broken = 0;
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if ((x * x) + (y * y) + (z * z) > (radius * radius) + 1) {
                        continue;
                    }
                    Location blockLoc = new Location(world, cx + x, cy + y, cz + z);
                    if (!isInsideWorldBorder(blockLoc)) {
                        continue;
                    }
                    Location key = normalizeBlock(blockLoc);
                    if (key == null) {
                        continue;
                    }
                    if (activeChests.containsKey(key) || specialSupplyTiers.containsKey(key)) {
                        continue;
                    }
                    Location above = normalizeBlock(key.clone().add(0.0D, 1.0D, 0.0D));
                    if (above != null && specialSupplyTiers.containsKey(above)) {
                        continue;
                    }
                    Block block = key.getBlock();
                    if (block == null || !isMagicBreakable(block.getType())) {
                        continue;
                    }
                    block.setType(Material.AIR, false);
                    broken++;
                    if (broken >= breakLimit) {
                        return broken;
                    }
                }
            }
        }
        return broken;
    }

    private boolean isMagicBreakable(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        if ("AIR".equals(name)
                || "BEDROCK".equals(name)
                || "BARRIER".equals(name)
                || "ENDER_PORTAL".equals(name)
                || "ENDER_PORTAL_FRAME".equals(name)
                || "COMMAND".equals(name)
                || "COMMAND_BLOCK".equals(name)
                || "CHEST".equals(name)
                || "EMERALD_BLOCK".equals(name)
                || "DIAMOND_BLOCK".equals(name)) {
            return false;
        }
        return true;
    }

    private boolean isInventoryEmpty(org.bukkit.inventory.Inventory inv) {
        if (inv == null) {
            return true;
        }
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    // SAFE FLAGS: entity flag setter for BannerFollower
    private static void safeFlags(Entity entity,
                                 boolean invulnerable,
                                 boolean gravity,
                                 boolean collidable,
                                 boolean silent,
                                 boolean ai,
                                 boolean persistent) {
        if (entity == null) {
            return;
        }
        Class<?> clazz = entity.getClass();

        // invulnerable
        try {
            Method m = clazz.getMethod("setInvulnerable", boolean.class);
            m.invoke(entity, invulnerable);
        } catch (Throwable ignored) {
        }

        // gravity
        try {
            Method m = clazz.getMethod("setGravity", boolean.class);
            m.invoke(entity, gravity);
        } catch (Throwable ignored) {
        }

        // collidable
        try {
            Method m = clazz.getMethod("setCollidable", boolean.class);
            m.invoke(entity, collidable);
        } catch (Throwable ignored) {
        }

        // silent
        try {
            Method m = clazz.getMethod("setSilent", boolean.class);
            m.invoke(entity, silent);
        } catch (Throwable ignored) {
        }

        // invisible
        try {
            Method m = clazz.getMethod("setInvisible", boolean.class);
            m.invoke(entity, true);
        } catch (Throwable ignored) {
            try {
                Method m = clazz.getMethod("setVisible", boolean.class);
                m.invoke(entity, false);
            } catch (Throwable ignored2) {
            }
        }

        // AI (only for mobs)
        try {
            Method m = clazz.getMethod("setAI", boolean.class);
            m.invoke(entity, ai);
        } catch (Throwable ignored) {
        }

        // persistence: removeWhenFarAway(false) gibi ele al
        if (persistent && entity instanceof LivingEntity) {
            try {
                Method m = entity.getClass().getMethod("setRemoveWhenFarAway", boolean.class);
                m.invoke(entity, false);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void applyInvisibility(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        try {
            ((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, true, false));
        } catch (Throwable ignored) {
        }
    }

    private static void forceNoClip(Entity entity) {
        try {
            Object handle = entity.getClass().getMethod("getHandle").invoke(entity);
            if (handle == null) {
                return;
            }
            try {
                Method m = handle.getClass().getMethod("setNoClip", boolean.class);
                m.invoke(handle, true);
                return;
            } catch (Throwable ignored) {
            }
            try {
                Field f = handle.getClass().getDeclaredField("noclip");
                f.setAccessible(true);
                f.setBoolean(handle, true);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class BannerFollower {
        private final Giant giant;
        private final Bat bat;
        private final BukkitTask task;
        private final ItemStack banner;
        private final String label;
        private final List<String> holoLines;
        private Hologram hologram;
        private Team noCollisionTeam;

        BannerFollower(Core plugin, Player owner, ItemStack banner, String label) {
            World world = owner.getWorld();
            this.banner = banner == null ? null : banner.clone();
        this.label = label == null ? "" : label;
            this.holoLines = buildAsciiLines(this.label);

        Location giantPos = computeGiantLocation(owner.getLocation());
        giant = (Giant) world.spawnEntity(giantPos, EntityType.GIANT);
        safeFlags(giant, true, false, false, true, false, true);
        applyInvisibility(giant);
        forceNoClip(giant);
        tryAssignNoCollision(owner, giant, "zly_nc");
        try {
            giant.setCustomNameVisible(false);
            giant.setCustomName("");
        } catch (Exception ignored) {
        }
        if (giant.getEquipment() != null && this.banner != null) {
            giant.getEquipment().setItemInHand(this.banner);
        }

        Location handSpawn = computeHandLocation(giantPos);
        bat = (Bat) world.spawnEntity(handSpawn, EntityType.BAT);
        safeFlags(bat, true, false, false, true, false, true);
        applyInvisibility(bat);
        tryAssignNoCollision(owner, bat, "zly_nc");
        try {
            bat.setCustomNameVisible(false);
            bat.setCustomName("");
        } catch (Exception ignored) {
        }
        try {
            bat.setLeashHolder(owner);
        } catch (Exception ignored) {
        }

        // disable hologram for now
        hologram = null;

            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!owner.isOnline() || giant.isDead() || bat.isDead()) {
                        destroy();
                        return;
                    }

                    Location balloon = computeGiantLocation(owner.getLocation());
                    giant.teleport(balloon);

                    Location hand = computeHandLocation(balloon);
                    hand.setYaw(balloon.getYaw());
                    bat.teleport(hand);

                    try {
                        bat.setLeashHolder(owner);
                    } catch (Exception ignored) {
                    }

                    try {
                        if (giant.getEquipment() != null && banner != null) {
                            ItemStack current = giant.getEquipment().getItemInHand();
                            if (current == null || current.getType() == Material.AIR) {
                                giant.getEquipment().setItemInHand(banner);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        private static Location computeGiantLocation(Location base) {
            Location loc = base.clone();

            Vector dir = loc.getDirection().setY(0).normalize();
            Vector left = new Vector(-dir.getZ(), 0, dir.getX()).normalize();

            double forward = 0.4D;
            double side = 3.0D;
            double up = 2.5D;

            loc.add(dir.multiply(forward));
            loc.add(left.multiply(side));
            loc.add(0, up, 0);

            loc.setYaw(base.getYaw() - 45.0F);
            return loc;
        }

        private static Location computeHandLocation(Location giantBase) {
            Location loc = giantBase.clone();

            Vector gDir = loc.getDirection().setY(0).normalize();
            Vector gLeft = new Vector(-gDir.getZ(), 0, gDir.getX()).normalize();

            double up = 8.0D;
            double forward = 3.2D;
            double side = 2.0D;

            loc.add(gDir.multiply(forward));
            loc.add(gLeft.multiply(side));
            loc.add(0.0D, up, 0.0D);

            return loc;
        }

        private static Hologram createAsciiHologram(Location loc, List<String> lines, String fallback) {
            Hologram holo = null;
            try {
                holo = HologramLibrary.createHologram(loc, lines);
            } catch (Throwable ignored) {
            }
            if (holo == null && fallback != null) {
                try {
                    String plain = ChatColor.GOLD + "" + ChatColor.BOLD + fallback;
                    holo = HologramLibrary.createHologram(loc, Collections.singletonList(plain));
                } catch (Throwable ignored) {
                }
            }
            return holo;
        }

        private void tryAssignNoCollision(Player owner, Entity entity, String teamName) {
            try {
                org.bukkit.scoreboard.Scoreboard sb = owner.getScoreboard();
                Team team = sb.getTeam(teamName);
                if (team == null) {
                    team = sb.registerNewTeam(teamName);
                    // 1.9+: collision disable, best-effort via reflection for legacy
                    try {
                        Class<?> optClass = Class.forName("org.bukkit.scoreboard.Team$Option");
                        Class<?> statusClass = Class.forName("org.bukkit.scoreboard.Team$OptionStatus");
                        Object collision = Enum.valueOf((Class<Enum>) optClass.asSubclass(Enum.class), "COLLISION_RULE");
                        Object never = Enum.valueOf((Class<Enum>) statusClass.asSubclass(Enum.class), "NEVER");
                        Method m = team.getClass().getMethod("setOption", optClass, statusClass);
                        m.invoke(team, collision, never);
                    } catch (Throwable ignored) {
                    }
                    try {
                        team.setCanSeeFriendlyInvisibles(true);
                    } catch (Throwable ignored) {
                    }
                }
                team.addEntry(entity.getUniqueId().toString());
                this.noCollisionTeam = team;
            } catch (Throwable ignored) {
            }
        }

        void destroy() {
            if (task != null) {
                task.cancel();
            }
            try {
                bat.setLeashHolder(null);
            } catch (Exception ignored) {
            }
            try {
                giant.remove();
            } catch (Exception ignored) {
            }
            try {
                bat.remove();
            } catch (Exception ignored) {
            }
            if (hologram != null) {
                try {
                    HologramLibrary.removeHologram(hologram);
                } catch (Exception ignored) {
                }
                hologram = null;
            }
        }

        private static List<String> buildAsciiLines(String text) {
            // Compact 3-line glyphs; if unknown chars exist, fall back to single line label
            String upper = text == null ? "" : ChatColor.stripColor(text).toUpperCase(Locale.ROOT);
            int rows = 3;
            String[] buffer = new String[rows];
            Arrays.fill(buffer, "");
            boolean hasUnknown = false;
            for (char ch : upper.toCharArray()) {
                String[] glyph = SMALL_GLYPHS.getOrDefault(ch, SMALL_UNKNOWN);
                if (glyph == SMALL_UNKNOWN) {
                    hasUnknown = true;
                    break;
                }
                for (int r = 0; r < rows; r++) {
                    buffer[r] += glyph[r] + " ";
                }
            }
            if (hasUnknown) {
                return Collections.singletonList(ChatColor.GOLD + "" + ChatColor.BOLD + ChatColor.stripColor(text == null ? "" : text));
            }
            List<String> out = new ArrayList<>();
            for (String line : buffer) {
                out.add(ChatColor.GOLD + "" + ChatColor.BOLD + line);
            }
            return out;
        }

        private static final Map<Character, String[]> SMALL_GLYPHS = new HashMap<>();
        private static final String[] SMALL_UNKNOWN = {
                "???",
                "???",
                "???"
        };

        // Legacy large glyph table kept for compatibility; not used by renderer
        private static final Map<Character, String[]> GLYPHS = new HashMap<>();

        static {
            GLYPHS.put('A', new String[]{
                    " ## ",
                    "#  #",
                    "####",
                    "#  #",
                    "#  #"
            });
            GLYPHS.put('B', new String[]{
                    "### ",
                    "#  #",
                    "### ",
                    "#  #",
                    "### "
            });
            GLYPHS.put('C', new String[]{
                    " ## ",
                    "#  #",
                    "#   ",
                    "#  #",
                    " ## "
            });
            GLYPHS.put('D', new String[]{
                    "### ",
                    "#  #",
                    "#  #",
                    "#  #",
                    "### "
            });
            GLYPHS.put('E', new String[]{
                    "####",
                    "#   ",
                    "### ",
                    "#   ",
                    "####"
            });
            GLYPHS.put('F', new String[]{
                    "####",
                    "#   ",
                    "### ",
                    "#   ",
                    "#   "
            });
            GLYPHS.put('G', new String[]{
                    " ## ",
                    "#   ",
                    "# ##",
                    "#  #",
                    " ###"
            });
            GLYPHS.put('H', new String[]{
                    "#  #",
                    "#  #",
                    "####",
                    "#  #",
                    "#  #"
            });
            GLYPHS.put('I', new String[]{
                    "###",
                    " # ",
                    " # ",
                    " # ",
                    "###"
            });
            GLYPHS.put('J', new String[]{
                    "  ##",
                    "   #",
                    "   #",
                    "#  #",
                    " ## "
            });
            GLYPHS.put('K', new String[]{
                    "#  #",
                    "# # ",
                    "##  ",
                    "# # ",
                    "#  #"
            });
            GLYPHS.put('L', new String[]{
                    "#   ",
                    "#   ",
                    "#   ",
                    "#   ",
                    "####"
            });
            GLYPHS.put('M', new String[]{
                    "#   #",
                    "## ##",
                    "# # #",
                    "#   #",
                    "#   #"
            });
            GLYPHS.put('N', new String[]{
                    "#  #",
                    "## #",
                    "# ##",
                    "#  #",
                    "#  #"
            });
            GLYPHS.put('O', new String[]{
                    " ## ",
                    "#  #",
                    "#  #",
                    "#  #",
                    " ## "
            });
            GLYPHS.put('P', new String[]{
                    "### ",
                    "#  #",
                    "### ",
                    "#   ",
                    "#   "
            });
            GLYPHS.put('Q', new String[]{
                    " ## ",
                    "#  #",
                    "#  #",
                    "# ##",
                    " ###"
            });
            GLYPHS.put('R', new String[]{
                    "### ",
                    "#  #",
                    "### ",
                    "# # ",
                    "#  #"
            });
            GLYPHS.put('S', new String[]{
                    " ###",
                    "#   ",
                    " ## ",
                    "   #",
                    "### "
            });
            GLYPHS.put('T', new String[]{
                    "#####",
                    "  #  ",
                    "  #  ",
                    "  #  ",
                    "  #  "
            });
            GLYPHS.put('U', new String[]{
                    "#  #",
                    "#  #",
                    "#  #",
                    "#  #",
                    " ## "
            });
            GLYPHS.put('V', new String[]{
                    "#   #",
                    "#   #",
                    " # # ",
                    " # # ",
                    "  #  "
            });
            GLYPHS.put('W', new String[]{
                    "#   #",
                    "#   #",
                    "# # #",
                    "## ##",
                    "#   #"
            });
            GLYPHS.put('X', new String[]{
                    "#   #",
                    " # # ",
                    "  #  ",
                    " # # ",
                    "#   #"
            });
            GLYPHS.put('Y', new String[]{
                    "#   #",
                    " # # ",
                    "  #  ",
                    "  #  ",
                    "  #  "
            });
            GLYPHS.put('Z', new String[]{
                    "####",
                    "   #",
                    "  # ",
                    " #  ",
                    "####"
            });
            GLYPHS.put('0', new String[]{
                    " ## ",
                    "#  #",
                    "#  #",
                    "#  #",
                    " ## "
            });
            GLYPHS.put('1', new String[]{
                    "  # ",
                    " ## ",
                    "  # ",
                    "  # ",
                    " ###"
            });
            GLYPHS.put('2', new String[]{
                    " ## ",
                    "#  #",
                    "  # ",
                    " #  ",
                    "####"
            });
            GLYPHS.put('3', new String[]{
                    "### ",
                    "   #",
                    " ## ",
                    "   #",
                    "### "
            });
            GLYPHS.put('4', new String[]{
                    "#  #",
                    "#  #",
                    "####",
                    "   #",
                    "   #"
            });
            GLYPHS.put('5', new String[]{
                    "####",
                    "#   ",
                    "### ",
                    "   #",
                    "### "
            });
            GLYPHS.put('6', new String[]{
                    " ## ",
                    "#   ",
                    "### ",
                    "#  #",
                    " ## "
            });
            GLYPHS.put('7', new String[]{
                    "####",
                    "   #",
                    "  # ",
                    " #  ",
                    "#   "
            });
            GLYPHS.put('8', new String[]{
                    " ## ",
                    "#  #",
                    " ## ",
                    "#  #",
                    " ## "
            });
            GLYPHS.put('9', new String[]{
                    " ## ",
                    "#  #",
                    " ###",
                    "   #",
                    " ## "
            });
        }
    }

    // command-accessible hologram removal (bridged from EsportsCommand "hologram remove")
    public boolean removeHologram(String id) {
        if (id == null) {
            return false;
        }
        Hologram holo = customHolograms.remove(id);
        if (holo != null) {
            try {
                HologramLibrary.removeHologram(holo);
            } catch (Exception ignored) {
            }
        }
        return holo != null;
    }

    // simple status/cleanup helpers used by EsportsCommand
    public void clearMatch(CommandSender sender) {
        if (!matchConcluded) {
            finishMatchReplayCapture("none", "manual_clear");
        }
        stopWinnerCelebration();
        clearParticipantBanners();
        stopChestDrops();
        clearDropBlocks();
        stopDynamicArenaEvents();
        restoreArenaSnapshot();
        if (countdownTask != null) {
            try {
                countdownTask.cancel();
            } catch (Exception ignored) {
            }
            countdownTask = null;
        }
        countdownRemaining = 0;
        cancelMatchTimeout();
        cancelAutoQueueStart();
        if (borderGrowthTask != null) {
            try {
                borderGrowthTask.cancel();
            } catch (Exception ignored) {
            }
            borderGrowthTask = null;
        }
        if (violationTask != null) {
            try {
                violationTask.cancel();
            } catch (Exception ignored) {
            }
            violationTask = null;
        }
        borderManager.clearAll();
        borderWarned.clear();
        borderDamageLog.clear();
        trainHitCooldown.clear();
        pendingSpectatorRespawn.clear();
        if (!matchConcluded) {
            sendMatchSummary();
        }
        Location fallbackLobby = tournamentEndedThisMatch ? null : getSpectatorLocationSnapshot();
        boolean endedNow = tournamentEndedThisMatch;
        Set<UUID> affectedPlayers = new HashSet<>(matchParticipants);
        affectedPlayers.addAll(spectatorLobby);
        affectedPlayers.addAll(participantReturnLocations.keySet());
        affectedPlayers.addAll(postMatchReturnLocations.keySet());
        // restore players to their previous state/locations
        for (UUID id : affectedPlayers) {
            Location back = participantReturnLocations.remove(id);
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                try {
                    player.leaveVehicle();
                    player.eject();
                } catch (Exception ignored) {
                }
                restoreInventoryState(player);
                spectatorLobby.remove(id);
                pendingSpectatorRespawn.remove(id);
                try {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                    if (player.getGameMode() == GameMode.ADVENTURE) {
                        player.setGameMode(GameMode.SURVIVAL);
                    }
                } catch (Exception ignored) {
                }
                boolean teleported = false;
                Location destination = null;
                if (back != null && back.getWorld() != null) {
                    destination = back.clone();
                } else if (player.getWorld() != null) {
                    destination = player.getWorld().getSpawnLocation();
                } else if (fallbackLobby != null && fallbackLobby.getWorld() != null) {
                    destination = fallbackLobby.clone();
                }
                if (player.isDead()) {
                    if (scoreboardService != null) {
                        scoreboardService.disable(player, false);
                    }
                    if (destination != null) {
                        postMatchReturnLocations.put(id, destination);
                    }
                    continue;
                }
                if (destination != null) {
                    try {
                        player.teleport(destination);
                        teleported = true;
                    } catch (Exception ignored) {
                    }
                }
                if (!teleported && fallbackLobby != null && fallbackLobby.getWorld() != null) {
                    try {
                        player.teleport(fallbackLobby.clone());
                    } catch (Exception ignored) {
                    }
                }
                if (scoreboardService != null) {
                    scoreboardService.disable(player, false);
                }
            }
        }
        participantReturnLocations.clear();
        spectatorLobby.clear();
        for (String tag : new HashSet<>(activeMatchClubs)) {
            if (tag != null) {
                clubMatchAssignments.remove(tag.toLowerCase(Locale.ROOT));
            }
        }
        activeMatchClubs.clear();
        activeClubMembers.clear();
        activeClubData.clear();
        clubMemberSizes.clear();
        matchParticipants.clear();
        participantSpawns.clear();
        participantClubTags.clear();
        killCounts.clear();
        deathCounts.clear();
        blockBreakCounts.clear();
        blockPlaceCounts.clear();
        clubKillCounts.clear();
        matchConcluded = false;
        tournamentEndedThisMatch = false;
        lastMatchEndedAt = LocalDateTime.now();
        matchStartedAt = 0L;
        currentMatchId = 0;
        if (endedNow) {
            stopTournamentWorldTask();
            bracketQueuedMatches.clear();
            clubMatchAssignments.clear();
        } else {
            queueNextBracketMatchIfReady();
            scheduleAutoQueueStart();
        }
        if (sender != null) {
            sender.sendMessage(EsportsLang.get("game.debug.match-cleared", CHAT_PREFIX + "&eMatch state cleared."));
        }
    }

    public void printStatus(CommandSender sender) {
        if (sender == null) {
            return;
        }
        sender.sendMessage(EsportsLang.get("game.debug.match-id", "&bMatch ID: &e{id}", "id", currentMatchId));
        sender.sendMessage(EsportsLang.get("game.debug.participants", "&bParticipants: &e{count}", "count", matchParticipants.size()));
        sender.sendMessage(EsportsLang.get("game.debug.queued-clubs", "&bQueued clubs: &e{count}", "count", matchQueue.size()));
    }

    private void sendMatchSummary() {
        if (activeMatchClubs.isEmpty() || matchParticipants.isEmpty()) {
            return;
        }
        Collection<Player> players = collectMatchPlayers();
        for (Player p : players) {
            List<String> lines = new ArrayList<>();
            lines.add(getEsportsText(p, "game.match.summary.title", "&6&lMatch Summary"));
            lines.add("");
            for (ClubMatchStatus status : getActiveClubStatuses()) {
                String tag = status.getTag();
                double healthPct = computeAverageHealthPercent(tag);
                String bar = healthBar(healthPct, 12);
                int kills = clubKillCounts.getOrDefault(tag.toLowerCase(Locale.ROOT), 0);
                String killsLabel = getEsportsText(p,
                        "game.match.summary.kills",
                        "&6Kills: {kills}",
                        "kills", kills);
                String line = status.getColor() + status.getDisplayName()
                        + ChatColor.GRAY + " [" + status.getAlive() + "/" + status.getTotal() + "] "
                        + bar + " " + killsLabel;
                lines.add(line);
            }
            lines.add("");
            for (String raw : lines) {
                p.sendMessage(CenteredMessage.generate(raw));
            }
        }
    }

    private double computeAverageHealthPercent(String tag) {
        if (tag == null) {
            return 0.0D;
        }
        String normalized = tag.toLowerCase(Locale.ROOT);
        double total = 0.0D;
        double maxTotal = 0.0D;
        for (UUID id : matchParticipants) {
            if (!normalized.equals(participantClubTags.get(id))) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            double health = Math.max(0.0D, player.getHealth());
            double max = resolveMaxHealth(player);
            total += health;
            maxTotal += Math.max(1.0D, max);
        }
        if (maxTotal <= 0.0D) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, total / maxTotal));
    }

    private String healthBar(double pct, int segments) {
        int filled = (int) Math.round(pct * segments);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments; i++) {
            if (i < filled) {
                sb.append(ChatColor.RED).append("\u2764");
            } else {
                sb.append(ChatColor.DARK_GRAY).append("\u2764");
            }
        }
        return sb.toString();
    }

    // --- Lightweight helpers to satisfy command wiring ---

    public List<String> listLeaderboardIds() {
        return new ArrayList<>(leaderboardHolograms.keySet());
    }

    public void spawnLeaderboardHologram(Player player, String id, int rank) {
        if (player == null || id == null || id.trim().isEmpty()) {
            return;
        }
        leaderboardRanks.put(id, rank);
        leaderboardLocations.put(id, player.getLocation().clone());
        Hologram holo = HologramLibrary.createHologram(player.getLocation().clone().add(0, 2.0, 0),
                Collections.singletonList(ChatColor.GOLD + "Leaderboard " + ChatColor.YELLOW + "#" + rank));
        if (holo != null) {
            leaderboardHolograms.put(id, holo);
        }
    }

    public boolean removeLeaderboardHologram(String id) {
        if (id == null) {
            return false;
        }
        leaderboardRanks.remove(id);
        leaderboardLocations.remove(id);
        Hologram holo = leaderboardHolograms.remove(id);
        if (holo != null) {
            try {
                HologramLibrary.removeHologram(holo);
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    public void startNextQueuedClub(CommandSender sender) {
        startMatchFromQueue(sender, true);
    }

    public void startClubRound(String tag, CommandSender sender) {
        if (tag == null || tag.trim().isEmpty()) {
            if (sender != null) sender.sendMessage(EsportsLang.get("game.queue.invalid-tag", "&cInvalid club tag."));
            return;
        }
        enqueueClub(tag, sender);
        startMatchFromQueue(sender, true);
    }

    public boolean setClubSpawn(String tag, Player setter) {
        if (tag == null || setter == null) {
            return false;
        }
        customClubSpawns.put(tag.toLowerCase(Locale.ROOT), setter.getLocation().clone());
        return true;
    }

    public boolean enqueueClub(String tag, CommandSender sender) {
        if (tag == null || tag.trim().isEmpty()) {
            return false;
        }
        String lower = tag.toLowerCase(Locale.ROOT);
        if (queuedTags.add(lower)) {
            matchQueue.addLast(lower);
            if (sender != null) {
                sender.sendMessage(EsportsLang.get("game.queue.queued", "&aClub {tag} queued.", "tag", tag));
            }
            if (sender instanceof Player) {
                Player player = (Player) sender;
                teleportToSpectator(player, true);
            }
            teleportClubMembers(tag);
            maybeGenerateBracketByParticipation();
            ensureMatchCountdown();
            return true;
        }
        if (sender != null) {
            sender.sendMessage(EsportsLang.get("game.queue.already-queued",
                    "&eClub already queued. Sending you to the waiting lobby."));
        }
        if (sender instanceof Player) {
            Player player = (Player) sender;
            teleportToSpectator(player, true);
        }
        teleportClubMembers(tag);
        maybeGenerateBracketByParticipation();
        ensureMatchCountdown();
        return false;
    }

    public boolean requestQueueEntry(String tag, CommandSender sender) {
        return enqueueClub(tag, sender);
    }

    private void maybeGenerateBracketByParticipation() {
        if (manager == null) {
            return;
        }
        EsportsTournamentData tournament = manager.getActiveTournament();
        if (tournament == null) {
            return;
        }
        String state = tournament.getState() == null ? "" : tournament.getState().toLowerCase(Locale.ROOT);
        if (!"live".equals(state) && !"upcoming".equals(state)) {
            return;
        }
        List<EsportsClubEntry> clubs = tournament.getClubs();
        if (clubs == null || clubs.size() < 2) {
            return;
        }
        int total = 0;
        int queued = 0;
        for (EsportsClubEntry club : clubs) {
            if (club == null || club.getTag() == null || club.getTag().trim().isEmpty()) {
                continue;
            }
            String status = club.getStatus();
            if (status != null && "eliminated".equalsIgnoreCase(status)) {
                continue;
            }
            total++;
            if (queuedTags.contains(club.getTag().toLowerCase(Locale.ROOT))) {
                queued++;
            }
        }
        if (total < 2) {
            return;
        }
        double ratio = queued / (double) total;
        if (ratio < BRACKET_READY_THRESHOLD) {
            return;
        }
        boolean generated = manager.ensureBracketGeneratedIfMissing();
        if (generated) {
            bracketReadyAnnounced = true;
            int percent = (int) Math.round(ratio * 100.0D);
            Bukkit.broadcastMessage(CHAT_PREFIX + ChatColor.AQUA + "Bracket is ready! "
                    + ChatColor.GRAY + "(" + percent + "% club participation)");
            manager.reloadNow();
            return;
        }
        if (!bracketReadyAnnounced && tournament.getMatches() != null && !tournament.getMatches().isEmpty()) {
            bracketReadyAnnounced = true;
        }
    }

    private void queueNextBracketMatchIfReady() {
        if (manager == null) {
            return;
        }
        Optional<EsportsMatchData> nextOpt = manager.findNextReadyMatch();
        if (!nextOpt.isPresent()) {
            return;
        }
        EsportsMatchData next = nextOpt.get();
        if (next.getId() <= 0 || next.getClubAId() == null || next.getClubBId() == null) {
            return;
        }
        if (!bracketQueuedMatches.add(next.getId())) {
            return;
        }
        String tagA = manager.getClubById(next.getClubAId()).map(EsportsClubEntry::getTag).orElse(null);
        String tagB = manager.getClubById(next.getClubBId()).map(EsportsClubEntry::getTag).orElse(null);
        if (tagA == null || tagB == null || tagA.trim().isEmpty() || tagB.trim().isEmpty()) {
            bracketQueuedMatches.remove(next.getId());
            return;
        }
        String normalizedA = tagA.trim().toLowerCase(Locale.ROOT);
        String normalizedB = tagB.trim().toLowerCase(Locale.ROOT);
        clubMatchAssignments.put(normalizedA, next.getId());
        clubMatchAssignments.put(normalizedB, next.getId());
        if (queuedTags.add(normalizedA)) {
            matchQueue.addLast(normalizedA);
        }
        if (queuedTags.add(normalizedB)) {
            matchQueue.addLast(normalizedB);
        }
        Bukkit.broadcastMessage(CHAT_PREFIX + ChatColor.AQUA + "Bracket match queued: "
                + ChatColor.YELLOW + tagA.toUpperCase(Locale.ROOT)
                + ChatColor.GRAY + " vs "
                + ChatColor.YELLOW + tagB.toUpperCase(Locale.ROOT));
    }

    private void teleportClubMembers(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return;
        }
        Core core = plugin;
        ClubManager clubManager = core != null ? core.getClubManager() : null;
        EsportsManager mgr = manager;
        int clubId = -1;
        if (mgr != null) {
            for (EsportsClubEntry entry : mgr.getActiveTournament() != null ? mgr.getActiveTournament().getClubs() : Collections.<EsportsClubEntry>emptyList()) {
                if (entry != null && entry.getTag() != null && entry.getTag().equalsIgnoreCase(tag)) {
                    clubId = entry.getClubId();
                    break;
                }
            }
        }
        Set<UUID> targets = new HashSet<>();
        if (clubManager != null && clubId > 0) {
            clubManager.getClubById(clubId).ifPresent(club -> {
                for (dev.zonely.whiteeffect.clubs.model.ClubMember member : club.getMembers()) {
                    if (member != null && member.getUniqueId() != null) {
                        targets.add(member.getUniqueId());
                    }
                }
            });
        }
        // Fallback: any online player whose display contains tag
        if (targets.isEmpty()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).contains(tag.toLowerCase(Locale.ROOT))) {
                    targets.add(p.getUniqueId());
                }
            }
        }
        for (UUID id : targets) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                teleportToSpectator(p, true);
            }
        }
    }

    public boolean isLobbyWindowOpenNow() {
        return true;
    }

    public int getLobbyWindowMinutes() {
        return LOBBY_WINDOW_MINUTES;
    }

    private int getRequiredClubCount() {
        EsportsTournamentData data = manager != null ? manager.getActiveTournament() : null;
        if (data == null || data.getSettings() == null || !data.getSettings().has("matchClubCount")) {
            return 2;
        }
        try {
            return Math.max(2, data.getSettings().get("matchClubCount").getAsInt());
        } catch (Exception ignored) {
            return 2;
        }
    }

    private List<Location> getSpawnLocations() {
        List<Location> list = new ArrayList<>();
        EsportsTournamentData data = manager != null ? manager.getActiveTournament() : null;
        if (data == null || data.getSpawnSlots() == null || data.getSpawnSlots().isEmpty()) {
            return list;
        }
        data.getSpawnSlots().stream()
                .sorted(Comparator.comparingInt(EsportsSpawnSlot::getSlotIndex))
                .forEach(slot -> {
                    Location loc = slot.toLocation(null);
                    if (loc != null) {
                        list.add(loc);
                    }
                });
        return list;
    }

    private void ensureMatchCountdown() {
        if (isMatchRunning() || isCountdownRunning()) {
            return;
        }
        int required = getRequiredClubCount();
        if (matchQueue.size() < required) {
            return;
        }
        countdownRemaining = LOBBY_MATCH_COUNTDOWN_SECONDS;
        if (countdownTask != null) {
            try {
                countdownTask.cancel();
            } catch (Exception ignored) {
            }
        }
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (countdownRemaining <= 0) {
                    cancel();
                    countdownTask = null;
                    startMatchFromQueue(null, false);
                    return;
                }
                countdownRemaining--;
            }
        };
        countdownTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void cancelAutoQueueStart() {
        if (autoQueueTask != null) {
            try {
                autoQueueTask.cancel();
            } catch (Exception ignored) {
            }
            autoQueueTask = null;
        }
    }

    private void scheduleAutoQueueStart() {
        cancelAutoQueueStart();
        if (isMatchRunning() || isCountdownRunning()) {
            return;
        }
        int required = getRequiredClubCount();
        if (matchQueue.size() < required) {
            return;
        }
        autoQueueTask = new BukkitRunnable() {
            @Override
            public void run() {
                autoQueueTask = null;
                if (isMatchRunning() || isCountdownRunning()) {
                    return;
                }
                if (matchQueue.size() < required) {
                    return;
                }
                startMatchFromQueue(null, true);
            }
        };
        autoQueueTask.runTaskLater(plugin, AUTO_QUEUE_DELAY_SECONDS * 20L);
    }

    private void startMatchFromQueue(CommandSender sender, boolean immediate) {
        cancelAutoQueueStart();
        int required = getRequiredClubCount();
        if (matchQueue.size() < required) {
            if (sender != null) {
                sender.sendMessage(EsportsLang.get("game.queue.not-enough-clubs",
                        "&cNot enough clubs queued ({current}/{required}).",
                        "current", matchQueue.size(),
                        "required", required));
            }
            return;
        }
        if (!ensureTournamentLiveAndBracket(sender)) {
            ensureMatchCountdown();
            return;
        }
        if (!immediate && countdownRemaining > 0) {
            if (sender != null) {
                sender.sendMessage(EsportsLang.get("game.countdown.already-running",
                        "&eCountdown already running ({seconds}s).",
                        "seconds", countdownRemaining));
            }
            return;
        }
        if (!immediate && countdownRemaining == 0) {
            countdownRemaining = LOBBY_MATCH_COUNTDOWN_SECONDS;
        }
        List<Location> spawns = getSpawnLocations();
        if (spawns.size() < required) {
            if (sender != null) {
                sender.sendMessage(EsportsLang.get("game.spawns.not-enough",
                        "&cNot enough spawn points configured ({count}).",
                        "count", spawns.size()));
            }
            return;
        }
        // Cancel any lobby countdown
        if (countdownTask != null) {
            try {
                countdownTask.cancel();
            } catch (Exception ignored) {
            }
            countdownTask = null;
        }
        countdownRemaining = 0;
        launchQueuedMatch(required, spawns);
    }

    private void launchQueuedMatch(int required, List<Location> spawns) {
        stopWinnerCelebration();
        postMatchReturnLocations.clear();
        List<String> clubs = new ArrayList<>();
        for (int i = 0; i < required && !matchQueue.isEmpty(); i++) {
            String tag = matchQueue.pollFirst();
            if (tag != null) {
                clubs.add(tag);
                queuedTags.remove(tag);
            }
        }
        if (clubs.size() < required) {
            return;
        }
        Integer resolvedMatchId = resolveMatchIdForQueuedClubs(clubs);
        if (resolvedMatchId == null || resolvedMatchId <= 0) {
            requeueClubs(clubs);
            Bukkit.broadcastMessage(CHAT_PREFIX + ChatColor.RED
                    + "Bracket match id could not be resolved. Waiting for bracket data...");
            if (manager != null) {
                manager.ensureBracketGeneratedIfMissing();
                manager.reloadNow();
                queueNextBracketMatchIfReady();
            }
            ensureMatchCountdown();
            return;
        }
        clearParticipantBanners();
        activeMatchClubs.clear();
        matchParticipants.clear();
        participantClubTags.clear();
        activeClubMembers.clear();
        participantSpawns.clear();
        activeClubData.clear();
        clubMemberSizes.clear();
        matchConcluded = false;
        specialSupplyCooldown.clear();
        specialItemCooldown.clear();
        noFallDamageUntil.clear();

        int spawnIndex = 0;
        List<Location> matchSpawns = new ArrayList<>();
        for (String tag : clubs) {
            Set<UUID> members = collectClubMembers(tag);
            Set<UUID> matchMembers = new HashSet<>();
            activeMatchClubs.add(tag);
            manager.getClubByTag(tag).ifPresent(entry -> activeClubData.put(tag.toLowerCase(Locale.ROOT), entry));
            Location spawn = spawns.get(spawnIndex % spawns.size());
            spawnIndex++;
            if (spawn != null) {
                trackTournamentWorld(spawn.getWorld());
                matchSpawns.add(spawn.clone());
            }
            for (UUID id : members) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline()) {
                    continue;
                }
                matchMembers.add(id);
                participantClubTags.put(id, tag);
                matchParticipants.add(id);
                spectatorLobby.remove(id);
                rememberReturnLocation(p);
                try {
                    p.setGameMode(GameMode.SURVIVAL);
                    p.setHealth(Math.max(1.0D, resolveMaxHealth(p)));
                    p.setFoodLevel(20);
                    p.setSaturation(20F);
                } catch (Exception ignored) {
                }
                if (spawn != null) {
                    participantSpawns.put(id, spawn.clone());
                    p.teleport(spawn);
                }
                if (scoreboardService != null) {
                    scoreboardService.enable(p, false);
                }
            }
            activeClubMembers.put(tag, matchMembers);
            clubMemberSizes.put(tag, matchMembers.size());
        }
        matchStartedAt = 0L;
        countdownRemaining = 0;
        currentMatchId = resolvedMatchId;

        List<Location> borderReferenceSpawns = matchSpawns.isEmpty() ? spawns : matchSpawns;
        Location center = computeCenter(borderReferenceSpawns);
        if (center != null) {
            trackTournamentWorld(center.getWorld());
        }
        captureArenaSnapshot(borderReferenceSpawns);
        activeMatchCenter = center == null ? null : center.clone();
        Collection<Player> participants = collectMatchPlayers();
        applyMatchLoadouts(center);
        applyPreparationBorders(participants);

        runMatchStartTitle(participants, () -> {
            if (matchConcluded || currentMatchId <= 0 || matchParticipants.isEmpty()) {
                return;
            }
            matchStartedAt = System.currentTimeMillis();
            beginMatchReplayCapture(clubs);
            Collection<Player> activePlayers = collectMatchPlayers();
            startBorders(center, activePlayers, borderReferenceSpawns);
            startChestDrops(center);
            startMatchTimeout();
            startDynamicArenaEvents(center);
            sendToParticipantsLocalized(
                    "game.match.fight",
                    CHAT_PREFIX + "&aFight!");
        });
        broadcastCenteredLocalized(
                "game.match.starting-in",
                "&e&lMatch starting in {seconds}s",
                "seconds", MATCH_START_COUNTDOWN_SECONDS);
        broadcastLocalized(
                "game.match.starting-clubs",
                CHAT_PREFIX + "&6Match starting: &e{clubs}",
                "clubs", String.join(", ", clubs));
    }

    private void applyMatchLoadouts(Location center) {
        EsportsTournamentData data = manager != null ? manager.getActiveTournament() : null;
        JsonObject defaultKit = data != null ? data.getDefaultKit() : null;
        List<EsportsPotionEffectSpec> potions = data != null ? data.getPotionEffects() : Collections.emptyList();
        for (UUID id : matchParticipants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                continue;
            }
            stashInventoryState(p);
            clearPlayerInventory(p);
            String tag = participantClubTags.get(id);
            JsonObject kit = tag != null ? customClubKits.get(tag.toLowerCase(Locale.ROOT)) : null;
            if (kit == null) {
                kit = defaultKit;
            }
            EsportsKitSerializer.apply(kit, p);
            applyTournamentPotions(p, potions);
            spawnParticipantBanner(p, tag != null ? activeClubData.get(tag.toLowerCase(Locale.ROOT)) : null);
        }
    }

    private void applyTournamentPotions(Player player, List<EsportsPotionEffectSpec> potions) {
        if (player == null || potions == null) {
            return;
        }
        for (EsportsPotionEffectSpec spec : potions) {
            try {
                PotionEffectType type = PotionEffectType.getByName(spec.getType());
                if (type == null) {
                    continue;
                }
                int duration = spec.getDurationTicks() <= 0 ? Integer.MAX_VALUE : spec.getDurationTicks();
                player.addPotionEffect(new PotionEffect(type, duration, spec.getAmplifier(), spec.isAmbient(), spec.hasParticles()));
            } catch (Exception ignored) {
            }
        }
    }

    private void applyPreparationBorders(Collection<Player> players) {
        if (players == null || players.isEmpty()) {
            return;
        }
        List<Player> targets = new ArrayList<>();
        for (Player player : players) {
            if (player != null && player.isOnline()) {
                targets.add(player);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        PersonalBorderManager.BorderOptions opts = new PersonalBorderManager.BorderOptions(
                PREP_AREA_RADIUS,
                3,
                0,
                0.5D,
                1.0D,
                1.5D,
                0.5D,
                true,
                20
        );
        for (Player player : targets) {
            Location center = participantSpawns.get(player.getUniqueId());
            if (center == null || center.getWorld() == null) {
                center = player.getLocation();
            }
            if (center == null || center.getWorld() == null) {
                continue;
            }
            borderManager.apply(player, center, opts, PREP_AREA_RADIUS);
        }
    }

    private void startBorders(Location center, Collection<Player> players, List<Location> matchSpawns) {
        if (center == null || center.getWorld() == null || players == null || players.isEmpty()) {
            return;
        }
        List<Player> targets = new ArrayList<>();
        for (Player player : players) {
            if (player != null && player.isOnline()) {
                targets.add(player);
            }
        }
        if (targets.isEmpty()) {
            return;
        }

        // Use a single arena center for everyone and derive radius from spawn spread.
        double spawnCoverageRadius = resolveRequiredSpawnCoverageRadius(center, matchSpawns);
        double startRadius = Math.max(spawnCoverageRadius, resolveMatchTargetRadius(center, matchSpawns));
        double desiredFinalRadius = stagingBorderRadius > 0.0D ? stagingBorderRadius : (startRadius * 0.45D);
        double finalRadius = Math.max(8.0D, Math.min(startRadius - 1.0D, desiredFinalRadius));
        PersonalBorderManager.BorderOptions opts = new PersonalBorderManager.BorderOptions(
                startRadius,
                3,
                0,
                0.5D,
                1.0D,
                1.5D,
                0.5D,
                true,
                20
        );
        for (Player p : targets) {
            borderManager.apply(p, center, opts, spawnCoverageRadius);
        }
        int expandDurationSeconds = Math.max(1, BORDER_EXPAND_SECONDS);
        double expandStep = (startRadius - spawnCoverageRadius) / expandDurationSeconds;
        if (startRadius > spawnCoverageRadius + 0.01D) {
            new BukkitRunnable() {
                private int elapsed = 0;

                @Override
                public void run() {
                    if (!hasActiveMatchContext() || matchConcluded) {
                        cancel();
                        return;
                    }
                    elapsed++;
                    double radius = spawnCoverageRadius + (expandStep * elapsed);
                    if (elapsed >= expandDurationSeconds || radius >= startRadius) {
                        radius = startRadius;
                    }
                    for (Player player : targets) {
                        if (player != null && player.isOnline()) {
                            borderManager.updateRadius(player, radius);
                        }
                    }
                    if (radius >= startRadius || elapsed >= expandDurationSeconds) {
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 0L, 20L);
        } else {
            for (Player p : targets) {
                borderManager.apply(p, center, opts, startRadius);
            }
        }
        if (borderGrowthTask != null) {
            try {
                borderGrowthTask.cancel();
            } catch (Exception ignored) {
            }
        }
        borderGrowthTask = null;
        int shrinkDurationSeconds = Math.max(1, MATCH_DURATION_SECONDS - BORDER_SHRINK_START_SECONDS);
        if (finalRadius >= startRadius || shrinkDurationSeconds <= 0) {
            return;
        }
        double deltaPerSecond = (startRadius - finalRadius) / shrinkDurationSeconds;
        borderGrowthTask = new BukkitRunnable() {
            private int elapsed = 0;
            private boolean announced = false;

            @Override
            public void run() {
                if (!announced) {
                    announced = true;
                    sendToParticipants(CHAT_PREFIX + ChatColor.RED + "Border is now shrinking!");
                }
                elapsed++;
                double nextRadius = startRadius - (deltaPerSecond * elapsed);
                boolean finished = elapsed >= shrinkDurationSeconds || nextRadius <= finalRadius;
                nextRadius = Math.max(nextRadius, finalRadius);
                for (Player player : targets) {
                    borderManager.updateRadius(player, nextRadius);
                }
                if (finished) {
                    cancel();
                    borderGrowthTask = null;
                }
            }
        };
        borderGrowthTask.runTaskTimer(plugin, BORDER_SHRINK_START_SECONDS * 20L, 20L);
    }

    private double resolveRequiredSpawnCoverageRadius(Location center, List<Location> matchSpawns) {
        double spread = computeRadius(matchSpawns, center);
        if (spread <= 0.0D) {
            spread = arenaBorderRadius > 0 ? (arenaBorderRadius / 2.0D) : 4.0D;
        }
        return Math.max(6.0D, spread + 2.0D);
    }

    private double resolveMatchTargetRadius(Location center, List<Location> matchSpawns) {
        double spread = computeRadius(matchSpawns, center);
        int clubCount = Math.max(1, activeMatchClubs.size());
        double spreadBased = spread > 0.0D ? spread + 12.0D : 0.0D;
        double multipliedByClubAreas = spread > 0.0D ? (spread * clubCount) + 6.0D : 0.0D;
        double configured = arenaBorderRadius > 0.0D ? arenaBorderRadius : 0.0D;
        return Math.max(10.0D, Math.max(Math.max(spreadBased, multipliedByClubAreas), configured));
    }

    private void startChestDrops(Location center) {
        stopChestDrops();
        clearDropBlocks();
        if (center == null || center.getWorld() == null) {
            return;
        }
        nextDropSeconds = CHEST_DROP_FIRST_SECONDS;
        currentDropTier = 1;
        chestDropTask = new BukkitRunnable() {
            int remaining = nextDropSeconds;

            @Override
            public void run() {
                if (!hasActiveMatchContext()) {
                    stopChestDrops();
                    return;
                }
                if (remaining <= 0) {
                    spawnChestDrop(center, currentDropTier);
                    if (currentDropTier < 3) {
                        currentDropTier++;
                    }
                    remaining = CHEST_DROP_INTERVAL_SECONDS;
                }
                nextDropSeconds = remaining;
                tryAutoCollectSpecialSupplies();
                remaining--;
            }
        };
        chestDropTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void startDynamicArenaEvents(Location center) {
        stopDynamicArenaEvents();
        if (center == null || center.getWorld() == null) {
            return;
        }
        activeMatchCenter = center.clone();
        rebuildMeteorAnchors(activeMatchCenter);
        meteorShowerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isMatchRunning() || matchConcluded || activeMatchCenter == null || activeMatchCenter.getWorld() == null) {
                    stopMeteorTask();
                    return;
                }
                if (meteorAnchorPoints.isEmpty()) {
                    rebuildMeteorAnchors(activeMatchCenter);
                }
                int count = 20;
                int anchorCount = Math.max(1, meteorAnchorPoints.size());
                for (int i = 0; i < count; i++) {
                    Location target = pickMeteorTarget(i);
                    if (target == null || target.getWorld() == null) {
                        continue;
                    }
                    spawnMeteorFromSky(target.clone().add(0.5D, 0.0D, 0.5D));
                }
                meteorAnchorCursor = (meteorAnchorCursor + count) % anchorCount;
            }
        };
        meteorShowerTask.runTaskTimer(plugin, 20L * METEOR_INTERVAL_SECONDS, 20L * METEOR_INTERVAL_SECONDS);

        trainWaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isMatchRunning() || matchConcluded || activeMatchCenter == null || activeMatchCenter.getWorld() == null) {
                    stopTrainTask();
                    return;
                }
                spawnColorTrain(activeMatchCenter);
            }
        };
        trainWaveTask.runTaskTimer(plugin, 20L * TRAIN_INTERVAL_SECONDS, 20L * TRAIN_INTERVAL_SECONDS);
    }

    private void stopDynamicArenaEvents() {
        activeMatchCenter = null;
        meteorAnchorPoints.clear();
        meteorAnchorCursor = 0;
        trainHitCooldown.clear();
        stopMeteorTask();
        stopTrainTask();
        removeActiveTrainEntities();
        specialProjectiles.clear();
    }

    private void stopMeteorTask() {
        if (meteorShowerTask != null) {
            try {
                meteorShowerTask.cancel();
            } catch (Exception ignored) {
            }
            meteorShowerTask = null;
        }
    }

    private void stopTrainTask() {
        if (trainWaveTask != null) {
            try {
                trainWaveTask.cancel();
            } catch (Exception ignored) {
            }
            trainWaveTask = null;
        }
    }

    private void rebuildMeteorAnchors(Location center) {
        meteorAnchorPoints.clear();
        meteorAnchorCursor = 0;
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        double radius = Math.max(10.0D, (arenaBorderRadius > 0.0D ? arenaBorderRadius * 0.75D : 18.0D));
        int points = Math.max(8, METEOR_ANCHOR_POINTS);
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D * i) / points;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            int y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;
            Location anchor = new Location(world, x, y, z);
            if (isInsideWorldBorder(anchor)) {
                meteorAnchorPoints.add(anchor);
            }
        }
    }

    private Location pickMeteorTarget(int offset) {
        if (meteorAnchorPoints.isEmpty()) {
            return activeMatchCenter == null ? null : findDropLocation(activeMatchCenter);
        }
        int idx = (meteorAnchorCursor + Math.max(0, offset)) % meteorAnchorPoints.size();
        Location anchor = meteorAnchorPoints.get(idx);
        if (anchor == null || anchor.getWorld() == null) {
            return null;
        }
        World world = anchor.getWorld();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = random.nextDouble() * 1.75D;
        double x = anchor.getX() + (Math.cos(angle) * distance);
        double z = anchor.getZ() + (Math.sin(angle) * distance);
        int y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;
        Location target = new Location(world, x, y, z);
        if (!isInsideWorldBorder(target)) {
            return anchor.clone();
        }
        return target;
    }

    private void spawnMeteorFromSky(Location target) {
        if (target == null || target.getWorld() == null) {
            return;
        }
        World world = target.getWorld();
        Location origin = target.clone().add(
                (random.nextDouble() - 0.5D) * 2.2D,
                26.0D + (random.nextDouble() * 8.0D),
                (random.nextDouble() - 0.5D) * 2.2D
        );
        try {
            Fireball fireball = (Fireball) world.spawnEntity(origin, EntityType.FIREBALL);
            Vector travel = target.toVector().subtract(origin.toVector());
            if (travel.lengthSquared() <= 0.0001D) {
                travel = new Vector(0.0D, -1.0D, 0.0D);
            }
            fireball.setDirection(travel.normalize());
            fireball.setYield(0.0F);
            fireball.setIsIncendiary(false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location strike = target.clone();
                if (fireball != null && fireball.isValid()) {
                    strike = fireball.getLocation().clone();
                    try {
                        fireball.remove();
                    } catch (Exception ignored) {
                    }
                }
                createMeteorStrike(strike, null, SKY_METEOR_IMPACT_RADIUS);
            }, 20L);
        } catch (Exception ignored) {
            createMeteorStrike(target, null, SKY_METEOR_IMPACT_RADIUS);
        }
    }

    private void createMeteorStrike(Location strike, Player source) {
        createMeteorStrike(strike, source, 3);
    }

    private void createMeteorStrike(Location strike, Player source, int impactRadius) {
        if (strike == null || strike.getWorld() == null || !isInsideWorldBorder(strike)) {
            return;
        }
        boolean skyMeteor = source == null;
        int radius = Math.max(1, impactRadius);
        if (skyMeteor) {
            radius = Math.min(radius, SKY_METEOR_IMPACT_RADIUS);
        }
        safeEffect(strike.clone().add(0.0D, 8.0D, 0.0D), Effect.SMOKE, 12);
        playSound(strike, 1.0F, 0.85F, "ENTITY_GENERIC_EXPLODE", "EXPLODE");
        if (skyMeteor) {
            shatterBlocksNear(strike, radius, SKY_METEOR_MAX_BROKEN_BLOCKS);
            if (random.nextDouble() <= SKY_METEOR_CRATER_CHANCE) {
                paintMeteorCrater(strike, radius);
            }
        } else {
            shatterBlocksNear(strike, radius);
            paintMeteorCrater(strike, radius);
        }
        double hitRange = Math.max(2.5D, radius * 2.0D);
        double hitRangeSq = hitRange * hitRange;
        double hitDamage = skyMeteor ? 3.5D : (radius >= 3 ? 7.0D : 5.0D);
        for (Player target : collectMatchPlayers()) {
            if (target == null || !target.isOnline() || target.getWorld() == null || !target.getWorld().equals(strike.getWorld())) {
                continue;
            }
            if (target.getLocation().distanceSquared(strike) > hitRangeSq) {
                continue;
            }
            try {
                if (source != null) {
                    target.damage(hitDamage, source);
                } else {
                    target.damage(hitDamage);
                }
            } catch (Exception ignored) {
            }
            try {
                target.setFireTicks(Math.max(target.getFireTicks(), skyMeteor ? 35 : 70));
            } catch (Exception ignored) {
            }
        }
    }

    private void paintMeteorCrater(Location center, int radius) {
        if (center == null || center.getWorld() == null || radius <= 0) {
            return;
        }
        World world = center.getWorld();
        Material fill = MaterialResolver.resolveMaterial("NETHERRACK");
        if (fill == null) {
            fill = MaterialResolver.resolveMaterial("NETHER_BRICK");
        }
        if (fill == null) {
            fill = Material.STONE;
        }
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if ((x * x) + (z * z) > (radius * radius) + 1) {
                    continue;
                }
                int bx = cx + x;
                int bz = cz + z;
                int y = world.getHighestBlockYAt(bx, bz) - 1;
                if (y < safeMinHeight(world)) {
                    continue;
                }
                Location blockLoc = new Location(world, bx, y, bz);
                if (!isInsideWorldBorder(blockLoc)) {
                    continue;
                }
                Block block = blockLoc.getBlock();
                if (block == null) {
                    continue;
                }
                Material type = block.getType();
                if (!isMagicBreakable(type) && type != Material.AIR) {
                    continue;
                }
                try {
                    block.setType(fill, false);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void spawnColorTrain(Location center) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        int trainCount = 5 + random.nextInt(3);
        double baseRadius = arenaBorderRadius > 0.0D ? arenaBorderRadius : 26.0D;
        double spawnRadius = Math.max(14.0D, baseRadius * 0.80D);
        for (int wave = 0; wave < trainCount; wave++) {
            boolean xAxis = random.nextBoolean();
            boolean forward = random.nextBoolean();
            double laneOffset = (random.nextDouble() * 2.0D - 1.0D) * (spawnRadius * 0.60D);
            double startX;
            double startZ;
            Vector direction;
            if (xAxis) {
                startX = center.getX() + (forward ? -spawnRadius : spawnRadius);
                startZ = center.getZ() + laneOffset;
                direction = new Vector(forward ? 1.0D : -1.0D, 0.0D, 0.0D);
            } else {
                startX = center.getX() + laneOffset;
                startZ = center.getZ() + (forward ? -spawnRadius : spawnRadius);
                direction = new Vector(0.0D, 0.0D, forward ? 1.0D : -1.0D);
            }
            int y = world.getHighestBlockYAt((int) Math.floor(startX), (int) Math.floor(startZ)) + 2;
            Location headStart = new Location(world, startX, y, startZ);
            direction = normalizeTrainAxisDirection(direction, 0.72D);

            int cars = 36 + random.nextInt(28);
            double spacing = 1.10D;
            List<ArmorStand> spawned = new ArrayList<>();
            List<Location> path = new ArrayList<>();
            for (int i = 0; i < cars; i++) {
                Location carLoc = headStart.clone().subtract(direction.clone().multiply(i * spacing));
                int groundY = world.getHighestBlockYAt((int) Math.floor(carLoc.getX()), (int) Math.floor(carLoc.getZ())) + 2;
                carLoc.setY(Math.max(carLoc.getY(), groundY));
                if (!isInsideWorldBorder(carLoc)) {
                    continue;
                }
                ArmorStand stand = spawnTrainCar(carLoc, randomTrainColorMaterial());
                if (stand != null) {
                    spawned.add(stand);
                    path.add(carLoc.clone());
                }
            }
            if (spawned.isEmpty() || path.isEmpty()) {
                cleanupTrainCars(spawned);
                continue;
            }
            final Vector[] stepDirection = new Vector[]{direction};
            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (!isMatchRunning() || matchConcluded || spawned.isEmpty() || path.isEmpty()) {
                        cleanupTrainCars(spawned);
                        cancel();
                        return;
                    }
                    boolean anyValid = false;
                    for (ArmorStand stand : spawned) {
                        if (stand != null && stand.isValid()) {
                            anyValid = true;
                            break;
                        }
                    }
                    if (!anyValid) {
                        cleanupTrainCars(spawned);
                        cancel();
                        return;
                    }
                    Location head = path.get(0);
                    Map.Entry<Location, Vector> next = computeNextTrainStep(head, stepDirection[0]);
                    if (next == null || next.getKey() == null || next.getValue() == null) {
                        cleanupTrainCars(spawned);
                        cancel();
                        return;
                    }
                    stepDirection[0] = next.getValue();
                    path.add(0, next.getKey());
                    while (path.size() > spawned.size()) {
                        path.remove(path.size() - 1);
                    }
                    for (int i = 0; i < spawned.size(); i++) {
                        ArmorStand stand = spawned.get(i);
                        if (stand == null || !stand.isValid()) {
                            continue;
                        }
                        Location target = path.get(Math.min(i, path.size() - 1));
                        try {
                            stand.teleport(target);
                        } catch (Exception ignored) {
                        }
                    }
                    applyTrainCollisions(spawned, stepDirection[0]);
                    ticks += 2;
                    if (ticks >= 420) {
                        cleanupTrainCars(spawned);
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }
    }

    private Map.Entry<Location, Vector> computeNextTrainStep(Location currentHead, Vector currentDirection) {
        if (currentHead == null || currentHead.getWorld() == null) {
            return null;
        }
        Vector direction = normalizeTrainAxisDirection(currentDirection, 0.72D);
        Location candidate = currentHead.clone().add(direction);
        Location adjusted = null;
        if (isInsideWorldBorder(candidate) && !isNearWorldBorderEdge(candidate, 1.2D)) {
            adjusted = fitTrainHeight(currentHead, candidate);
            if (adjusted != null && isInsideWorldBorder(adjusted)) {
                return new AbstractMap.SimpleEntry<>(adjusted, direction);
            }
        }

        // Border reached or blocked: reverse direction (no diagonal turning)
        Vector reversed = direction.clone().multiply(-1.0D);
        Location back = currentHead.clone().add(reversed);
        if (!isInsideWorldBorder(back) || isNearWorldBorderEdge(back, 1.2D)) {
            return null;
        }
        Location adjustedBack = fitTrainHeight(currentHead, back);
        if (adjustedBack == null || !isInsideWorldBorder(adjustedBack)) {
            return null;
        }
        return new AbstractMap.SimpleEntry<>(adjustedBack, reversed);
    }

    private Location fitTrainHeight(Location previousHead, Location candidate) {
        if (previousHead == null || candidate == null || candidate.getWorld() == null) {
            return null;
        }
        World world = candidate.getWorld();
        int bx = (int) Math.floor(candidate.getX());
        int bz = (int) Math.floor(candidate.getZ());
        int groundY = world.getHighestBlockYAt(bx, bz) + 2;
        double nextY = previousHead.getY();
        if (groundY > nextY + 0.40D) {
            nextY = Math.min(nextY + 1.0D, groundY);
        } else if (groundY < nextY - 1.20D) {
            nextY = Math.max(groundY, nextY - 1.0D);
        } else {
            nextY = groundY;
        }
        Location stepped = candidate.clone();
        stepped.setY(nextY);
        if (isTrainBlocked(stepped)) {
            Location up = stepped.clone().add(0.0D, 1.0D, 0.0D);
            if (!isTrainBlocked(up) && up.getY() <= previousHead.getY() + 1.25D) {
                stepped = up;
            } else {
                return null;
            }
        }
        return stepped;
    }

    private boolean isTrainBlocked(Location location) {
        if (location == null || location.getWorld() == null) {
            return true;
        }
        Block body = location.getBlock();
        Block head = location.clone().add(0.0D, 1.0D, 0.0D).getBlock();
        return isTrainSolid(body == null ? null : body.getType())
                || isTrainSolid(head == null ? null : head.getType());
    }

    private boolean isTrainSolid(Material type) {
        if (type == null || type == Material.AIR) {
            return false;
        }
        try {
            Method solid = type.getClass().getMethod("isSolid");
            Object value = solid.invoke(type);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    private boolean isNearWorldBorderEdge(Location location, double padding) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        try {
            Object border = location.getWorld().getWorldBorder();
            if (border == null) {
                return false;
            }
            Method getCenter = border.getClass().getMethod("getCenter");
            Method getSize = border.getClass().getMethod("getSize");
            Location center = (Location) getCenter.invoke(border);
            double half = (((Number) getSize.invoke(border)).doubleValue() / 2.0D) - Math.max(0.1D, padding);
            return Math.abs(location.getX() - center.getX()) >= half
                    || Math.abs(location.getZ() - center.getZ()) >= half;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private Vector normalizeTrainDirection(Vector direction, double speed) {
        Vector out = direction == null ? new Vector(0.6D, 0.0D, 0.0D) : direction.clone();
        out.setY(0.0D);
        if (out.lengthSquared() <= 0.0001D) {
            out = new Vector(0.6D, 0.0D, 0.0D);
        }
        return out.normalize().multiply(speed);
    }

    private Vector normalizeTrainAxisDirection(Vector direction, double speed) {
        Vector out = direction == null ? new Vector(1.0D, 0.0D, 0.0D) : direction.clone();
        out.setY(0.0D);
        if (Math.abs(out.getX()) >= Math.abs(out.getZ())) {
            double sign = out.getX() >= 0.0D ? 1.0D : -1.0D;
            return new Vector(sign, 0.0D, 0.0D).multiply(speed);
        }
        double sign = out.getZ() >= 0.0D ? 1.0D : -1.0D;
        return new Vector(0.0D, 0.0D, sign).multiply(speed);
    }

    private Vector rotateTrainDirection(Vector direction, boolean turnRight) {
        if (direction == null || direction.lengthSquared() <= 0.0001D) {
            return new Vector(0.6D, 0.0D, 0.0D);
        }
        double x = direction.getX();
        double z = direction.getZ();
        Vector rotated = turnRight
                ? new Vector(-z, 0.0D, x)
                : new Vector(z, 0.0D, -x);
        if (rotated.lengthSquared() <= 0.0001D) {
            return new Vector(0.6D, 0.0D, 0.0D);
        }
        return rotated.normalize().multiply(Math.max(0.45D, direction.length()));
    }

    private void applyTrainCollisions(List<ArmorStand> cars, Vector direction) {
        if (cars == null || cars.isEmpty()) {
            return;
        }
        Vector push = direction == null ? new Vector(0.7D, 0.2D, 0.0D) : direction.clone().normalize().multiply(1.15D);
        push.setY(0.35D);
        long now = System.currentTimeMillis();
        for (Player player : collectMatchPlayers()) {
            if (player == null || !player.isOnline() || player.isDead()) {
                continue;
            }
            Long blockedUntil = trainHitCooldown.get(player.getUniqueId());
            if (blockedUntil != null && blockedUntil > now) {
                continue;
            }
            Location pLoc = player.getLocation();
            if (pLoc == null || pLoc.getWorld() == null) {
                continue;
            }
            boolean hit = false;
            for (ArmorStand car : cars) {
                if (car == null || !car.isValid() || car.getWorld() == null || !car.getWorld().equals(pLoc.getWorld())) {
                    continue;
                }
                if (car.getLocation().distanceSquared(pLoc) <= 1.65D) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                continue;
            }
            trainHitCooldown.put(player.getUniqueId(), now + TRAIN_HIT_COOLDOWN_MS);
            try {
                player.damage(TRAIN_COLLISION_DAMAGE);
            } catch (Exception ignored) {
            }
            try {
                player.setVelocity(player.getVelocity().add(push));
            } catch (Exception ignored) {
            }
            try {
                player.setFireTicks(Math.max(player.getFireTicks(), TRAIN_COLLISION_FIRE_TICKS));
            } catch (Exception ignored) {
            }
        }
    }

    private void cleanupTrainCars(List<ArmorStand> cars) {
        if (cars == null || cars.isEmpty()) {
            return;
        }
        for (ArmorStand stand : cars) {
            if (stand == null) {
                continue;
            }
            activeTrainEntities.remove(stand.getUniqueId());
            if (!stand.isValid()) {
                continue;
            }
            try {
                stand.remove();
            } catch (Exception ignored) {
            }
        }
    }

    private ArmorStand spawnTrainCar(Location location, Material helmetType) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        ArmorStand stand;
        try {
            stand = location.getWorld().spawn(location, ArmorStand.class);
        } catch (Exception ex) {
            return null;
        }
        try {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setBasePlate(false);
            stand.setCanPickupItems(false);
            stand.setCustomNameVisible(false);
        } catch (Exception ignored) {
        }
        try {
            if (stand.getEquipment() != null) {
                Material fallback = MaterialResolver.resolveMaterial("WHITE_WOOL");
                if (fallback == null) {
                    fallback = MaterialResolver.resolveMaterial("WOOL");
                }
                if (fallback == null) {
                    fallback = Material.STONE;
                }
                stand.getEquipment().setHelmet(new ItemStack(helmetType == null ? fallback : helmetType));
            }
        } catch (Exception ignored) {
        }
        activeTrainEntities.add(stand.getUniqueId());
        return stand;
    }

    private Material randomTrainColorMaterial() {
        String[] names = {
                "RED_WOOL", "ORANGE_WOOL", "YELLOW_WOOL", "LIME_WOOL",
                "GREEN_WOOL", "LIGHT_BLUE_WOOL", "BLUE_WOOL", "PURPLE_WOOL",
                "MAGENTA_WOOL", "PINK_WOOL", "CYAN_WOOL"
        };
        String pick = names[random.nextInt(names.length)];
        Material material = MaterialResolver.resolveMaterial(pick);
        if (material != null) {
            return material;
        }
        Material legacy = MaterialResolver.resolveMaterial("WOOL");
        if (legacy != null) {
            return legacy;
        }
        return Material.STONE;
    }

    private void removeActiveTrainEntities() {
        for (UUID entityId : new HashSet<>(activeTrainEntities)) {
            Entity entity = null;
            for (World world : Bukkit.getWorlds()) {
                Entity found = null;
                try {
                    for (Entity candidate : world.getEntities()) {
                        if (candidate != null && entityId.equals(candidate.getUniqueId())) {
                            found = candidate;
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
                if (found != null) {
                    entity = found;
                    break;
                }
            }
            if (entity != null) {
                try {
                    entity.remove();
                } catch (Exception ignored) {
                }
            }
        }
        activeTrainEntities.clear();
    }

    private void runMatchStartTitle(Collection<Player> players, Runnable onComplete) {
        if (players == null || players.isEmpty()) {
            if (onComplete != null) {
                Bukkit.getScheduler().runTask(plugin, onComplete);
            }
            return;
        }
        // short pre-start countdown to drive scoreboard + title
        countdownRemaining = MATCH_START_COUNTDOWN_SECONDS;
        if (countdownTask != null) {
            try {
                countdownTask.cancel();
            } catch (Exception ignored) {
            }
        }
        countdownTask = new BukkitRunnable() {
            int counter = countdownRemaining;
            @Override
            public void run() {
                if (counter <= 0) {
                    countdownRemaining = 0;
                    cancel();
                    countdownTask = null;
                    if (onComplete != null) {
                        Bukkit.getScheduler().runTask(plugin, onComplete);
                    }
                    return;
                }
                for (Player p : players) {
                    if (p != null && p.isOnline()) {
                        String title = ChatColor.translateAlternateColorCodes('&',
                                getEsportsText(p, "game.countdown.title", "&6&lMATCH STARTING"));
                        String subtitle = ChatColor.translateAlternateColorCodes('&',
                                getEsportsText(p, "game.countdown.subtitle", "&bStarts in &f{seconds}s",
                                        "seconds", counter));
                        sendTitle(p,
                                title,
                                subtitle);
                    }
                }
                counter--;
                countdownRemaining = counter;
            }
        };
        countdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void startMatchTimeout() {
        cancelMatchTimeout();
        if (!isMatchRunning()) {
            return;
        }
        matchEndTask = new BukkitRunnable() {
            int remaining = MATCH_DURATION_SECONDS;

            @Override
            public void run() {
                if (!isMatchRunning() || matchConcluded) {
                    cancelMatchTimeout();
                    return;
                }
                if (remaining <= 0) {
                    concludeByScore("Time up");
                    cancelMatchTimeout();
                    return;
                }
                remaining--;
            }
        };
        matchEndTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void cancelMatchTimeout() {
        if (matchEndTask != null) {
            try {
                matchEndTask.cancel();
            } catch (Exception ignored) {
            }
            matchEndTask = null;
        }
    }

    private void spawnChestDrop(Location center, int tier) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        int maxDrops = Math.min(4, 2 + Math.max(0, tier - 1));
        if (activeChests.size() >= maxDrops) {
            return;
        }
        Location dropLoc = findDropLocation(center);
        if (dropLoc == null) {
            return;
        }
        spawnFallingChest(dropLoc, tier);
        spawnSpecialSupply(center, tier);
    }

    private Location findDropLocation(Location center) {
        if (center == null || center.getWorld() == null) {
            return null;
        }
        World world = center.getWorld();
        double radius = Math.max(4.0D, arenaBorderRadius - 2.0D);
        int attempts = 12;
        while (attempts-- > 0) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 2.0D + random.nextDouble() * (radius - 2.0D);
            double x = center.getX() + Math.cos(angle) * distance;
            double z = center.getZ() + Math.sin(angle) * distance;
            Location candidate = new Location(world, x, world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1.0D, z);
            if (center.distance(candidate) > radius) {
                continue;
            }
            if (!isInsideWorldBorder(candidate)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private void finalizeChestDrop(Location dropLoc, int tier) {
        dropLoc.getBlock().setType(Material.CHEST, false);
        if (dropLoc.getBlock().getState() instanceof Chest) {
            Chest chest = (Chest) dropLoc.getBlock().getState();
            nameChest(chest, EsportsLang.get("game.drops.chest-name", "&6Tier {tier} Chest", "tier", tier));
            chest.getBlockInventory().clear();
            applyLootToChest(chest, tier, false);
            chest.update(true);
            Location key = new Location(dropLoc.getWorld(), dropLoc.getBlockX(), dropLoc.getBlockY(), dropLoc.getBlockZ());
            activeChests.put(key, tier);
            scheduleChestRefresh(key, tier);
            spawnChestHologram(key, null);
        }
        lastDropLocation = dropLoc.clone();
        safeEffect(dropLoc.clone().add(0.5, 1.2, 0.5), Effect.SMOKE, 5);
        playSound(dropLoc, 1.0F, 1.2F,
                "ENTITY_FIREWORK_ROCKET_BLAST",
                "ENTITY_FIREWORK_BLAST",
                "FIREWORK_BLAST",
                "FIREWORK_LAUNCH",
                "ENTITY_FIREWORK_LAUNCH");
        sendToParticipantsLocalized(
                "game.drops.tier-chest-dropped",
                "&b[E-Sports] &eTier {tier} chest dropped! Location: &f{x}, {y}, {z}",
                "tier", tier,
                "x", dropLoc.getBlockX(),
                "y", dropLoc.getBlockY(),
                "z", dropLoc.getBlockZ());
    }

    private void spawnFallingChest(Location dropLoc, int tier) {
        if (dropLoc == null || dropLoc.getWorld() == null) {
            return;
        }
        World world = dropLoc.getWorld();
        Location start = dropLoc.clone().add(0, 12.0D, 0);
        final Location finalDrop = dropLoc.clone();
        ArmorStand stand;
        try {
            stand = (ArmorStand) world.getClass().getMethod("spawn", Location.class, Class.class).invoke(world, start, ArmorStand.class);
        } catch (Throwable ex) {
            try {
                stand = world.spawn(start, ArmorStand.class);
            } catch (Throwable ex2) {
                return;
            }
        }
        final ArmorStand finalStand = stand;
        final int finalTier = tier;
        try {
            Method m = stand.getClass().getMethod("setVisible", boolean.class);
            m.invoke(stand, false);
        } catch (Throwable ignored) {
            try { stand.setVisible(false); } catch (Throwable ignored2) {}
        }
        try { stand.setMarker(true); } catch (Throwable ignored) {}
        try { stand.setGravity(false); } catch (Throwable ignored) {}
        try { stand.setSmall(true); } catch (Throwable ignored) {}
        try {
            if (stand.getEquipment() != null) {
                stand.getEquipment().setHelmet(new ItemStack(Material.CHEST));
            }
        } catch (Throwable ignored) {}
        runFallingChestAnimation(finalStand, finalDrop, finalTier);
    }

    private void runFallingChestAnimation(final ArmorStand stand, final Location finalDrop, final int finalTier) {
        if (stand == null || finalDrop == null) {
            return;
        }
        final int[] ticks = new int[]{0};
        final boolean[] finished = new boolean[]{false};
        final BukkitTask[] taskRef = new BukkitTask[1];

        taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (finished[0]) {
                BukkitTask task = taskRef[0];
                if (task != null) {
                    task.cancel();
                }
                return;
            }
            try {
                if (stand.isDead() || !stand.isValid()) {
                    finishFallingChestAnimation(stand, finalDrop, finalTier, taskRef, finished);
                    return;
                }
                Location current = stand.getLocation();
                current.setYaw(current.getYaw() + 20.0F);
                current.subtract(0.0D, 0.6D, 0.0D);
                if (current.getY() <= finalDrop.getY() + 0.5D || ticks[0] > 30) {
                    finishFallingChestAnimation(stand, finalDrop, finalTier, taskRef, finished);
                    return;
                }
                stand.teleport(current);
                ticks[0]++;
            } catch (Throwable ignored) {
                finishFallingChestAnimation(stand, finalDrop, finalTier, taskRef, finished);
            }
        }, 0L, 2L);
    }

    private void finishFallingChestAnimation(ArmorStand stand, Location finalDrop, int finalTier,
                                             BukkitTask[] taskRef, boolean[] finished) {
        if (finished != null && finished[0]) {
            return;
        }
        if (finished != null) {
            finished[0] = true;
        }
        if (taskRef != null && taskRef.length > 0 && taskRef[0] != null) {
            try {
                taskRef[0].cancel();
            } catch (Throwable ignored) {
            }
        }
        if (stand != null) {
            try {
                stand.remove();
            } catch (Throwable ignored) {
            }
        }
        finalizeChestDrop(finalDrop, finalTier);
    }

    private void spawnSpecialSupply(Location center, int tier) {
        if (center == null || center.getWorld() == null || !hasActiveMatchContext()) {
            return;
        }
        Location anchor = chooseSpecialSupplyAnchor(center);
        if (anchor == null || anchor.getWorld() == null) {
            return;
        }
        Location key = normalizeBlock(anchor);
        if (key == null) {
            return;
        }
        restoreSpecialSupplyAt(key, true);

        Location baseLoc = key.clone().subtract(0.0D, 1.0D, 0.0D);
        Location baseKey = normalizeBlock(baseLoc);
        if (baseKey == null) {
            return;
        }

        Block topBlock = key.getBlock();
        Block baseBlock = baseKey.getBlock();
        if (topBlock == null || baseBlock == null) {
            return;
        }

        specialSupplyOriginalBlocks.putIfAbsent(key, topBlock.getType());
        specialSupplyOriginalBlocks.putIfAbsent(baseKey, baseBlock.getType());
        baseBlock.setType(Material.DIAMOND_BLOCK, false);
        topBlock.setType(Material.EMERALD_BLOCK, false);

        ItemStack reward = createSpecialSupplyRewardForTier(tier);
        specialSupplyTiers.put(key, tier);
        specialSupplyRewards.put(key, reward);

        String itemName = resolveDisplayName(reward);
        int uses = readSpecialItemUses(reward);
        List<String> lines = Arrays.asList(
                EsportsLang.get("game.special.hologram.title", "&a&lSPECIAL DROP"),
                ChatColor.AQUA + itemName,
                EsportsLang.get("game.special.hologram.uses", "&eUses: &f{uses}", "uses", uses),
                EsportsLang.get("game.special.hologram.claim", "&6Walk over to claim")
        );
        Hologram holo = HologramLibrary.createHologram(key.clone().add(0.5D, 1.6D, 0.5D), lines);
        if (holo != null) {
            specialSupplyHolograms.put(key, holo);
        }
        safeEffect(key.clone().add(0.5D, 1.0D, 0.5D), Effect.SMOKE, 8);
        playSound(key, 1.0F, 1.1F, "ENTITY_EXPERIENCE_ORB_PICKUP", "ORB_PICKUP");
        sendToParticipantsLocalized(
                "game.special.spawned",
                "&b[E-Sports] &aSpecial drop spawned: &e{name} &7@ &f{x}, {y}, {z}",
                "name", itemName,
                "x", key.getBlockX(),
                "y", key.getBlockY(),
                "z", key.getBlockZ());
    }

    private Location chooseSpecialSupplyAnchor(Location center) {
        List<Location> candidates = computeSpecialSupplyAnchors(center);
        if (candidates.isEmpty()) {
            return null;
        }
        Collections.shuffle(candidates, random);
        for (Location candidate : candidates) {
            Location key = normalizeBlock(candidate);
            if (key == null) {
                continue;
            }
            if (specialSupplyTiers.containsKey(key)) {
                continue;
            }
            if (lastDropLocation != null && key.getWorld() != null && lastDropLocation.getWorld() != null
                    && key.getWorld().equals(lastDropLocation.getWorld())
                    && key.distance(lastDropLocation) < 10.0D) {
                continue;
            }
            return key;
        }
        // all anchors are occupied; replace a random one to keep drops rotating
        return normalizeBlock(candidates.get(0));
    }

    private List<Location> computeSpecialSupplyAnchors(Location center) {
        List<Location> points = new ArrayList<>();
        if (center == null || center.getWorld() == null) {
            return points;
        }
        World world = center.getWorld();
        double baseRadius = arenaBorderRadius > 0.0D ? arenaBorderRadius : resolveMatchTargetRadius(center, getSpawnLocations());
        double offset = Math.max(10.0D, baseRadius * 0.72D);
        if (offset > baseRadius - 2.0D && baseRadius > 12.0D) {
            offset = baseRadius - 2.0D;
        }
        double[][] corners = new double[][]{
                {offset, offset},
                {offset, -offset},
                {-offset, offset},
                {-offset, -offset}
        };
        int max = Math.min(SPECIAL_SUPPLY_POINT_COUNT, corners.length);
        for (int i = 0; i < max; i++) {
            double x = center.getX() + corners[i][0];
            double z = center.getZ() + corners[i][1];
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            int y = world.getHighestBlockYAt(bx, bz) + 1;
            Location candidate = new Location(world, bx, y, bz);
            if (!isInsideWorldBorder(candidate)) {
                continue;
            }
            points.add(candidate);
        }
        return points;
    }

    private void restoreSpecialSupplyAt(Location key, boolean restoreBlocks) {
        Location normalized = normalizeBlock(key);
        if (normalized == null) {
            return;
        }
        Hologram holo = specialSupplyHolograms.remove(normalized);
        if (holo != null) {
            try {
                HologramLibrary.removeHologram(holo);
            } catch (Exception ignored) {
            }
        }
        specialSupplyTiers.remove(normalized);
        specialSupplyRewards.remove(normalized);
        if (restoreBlocks) {
            restoreSpecialSupplyBlock(normalized);
            Location base = normalizeBlock(normalized.clone().subtract(0.0D, 1.0D, 0.0D));
            if (base != null) {
                restoreSpecialSupplyBlock(base);
            }
        }
    }

    private void restoreSpecialSupplyBlock(Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) {
            return;
        }
        Material original = specialSupplyOriginalBlocks.remove(blockLoc);
        if (original == null) {
            return;
        }
        try {
            blockLoc.getBlock().setType(original, false);
        } catch (Exception ignored) {
        }
    }

    private ItemStack createSpecialSupplyRewardForTier(int tier) {
        List<ItemStack> pool = new ArrayList<>();
        pool.add(createSpecialItem(Material.SLIME_BALL, SPECIAL_ITEM_TRAMPOLINE, 3,
                ChatColor.GRAY + EsportsLang.get("game.special.items.lore.trampoline.1", "Right-click: places slime under you"),
                ChatColor.GRAY + EsportsLang.get("game.special.items.lore.trampoline.2", "No fall damage for a short time")));
        pool.add(createSpecialItem(Material.BLAZE_ROD, SPECIAL_ITEM_FIRE_BLASTER, 3,
                ChatColor.GRAY + EsportsLang.get("game.special.items.lore.fire-blaster.1", "Right-click: rapid fireball shot"),
                ChatColor.GRAY + EsportsLang.get("game.special.items.lore.fire-blaster.2", "Short cooldown")));
        pool.add(createSpecialItem(resolveMaterial("SNOWBALL", "SNOW_BALL"), SPECIAL_ITEM_FORCE_ORB, 3,
                ChatColor.GRAY + EsportsLang.get("game.special.items.lore.force-orb.1", "Right-click: throw knockback orb"),
                ChatColor.GRAY + EsportsLang.get("game.special.items.lore.force-orb.2", "Launches nearby players")));
        if (tier >= 2) {
            pool.add(createSpecialItem(resolveMaterial("FIRE_CHARGE", "FIREBALL"), SPECIAL_ITEM_INFERNO, 2,
                    ChatColor.GRAY + EsportsLang.get("game.special.items.lore.inferno-launcher.1", "Right-click: powerful explosive shot"),
                    ChatColor.GRAY + EsportsLang.get("game.special.items.lore.inferno-launcher.2", "Deals area damage")));
            pool.add(createSpecialItem(Material.STICK, SPECIAL_ITEM_MAGIC_WAND, 3,
                    ChatColor.GRAY + EsportsLang.get("game.special.items.lore.magic-wand.1", "Right-click: shatters blocks at target"),
                    ChatColor.GRAY + EsportsLang.get("game.special.items.lore.magic-wand.2", "Breaks nearby area")));
            pool.add(createSpecialItem(resolveMaterial("SNOWBALL", "SNOW_BALL"), SPECIAL_ITEM_METEOR_ORB, 2,
                    ChatColor.GRAY + EsportsLang.get("game.special.items.lore.meteor-orb.1", "Right-click: throw meteor orb"),
                    ChatColor.GRAY + EsportsLang.get("game.special.items.lore.meteor-orb.2", "Calls a meteor at impact")));
        }
        if (tier >= 3) {
            pool.add(createSpecialItem(resolveMaterial("FIRE_CHARGE", "FIREBALL"), SPECIAL_ITEM_INFERNO, 3,
                    ChatColor.GRAY + EsportsLang.get("game.special.items.lore.inferno-launcher-tier3.1", "Right-click: empowered inferno"),
                    ChatColor.GRAY + EsportsLang.get("game.special.items.lore.inferno-launcher-tier3.2", "Larger explosion")));
            pool.add(createSpecialItem(Material.STICK, SPECIAL_ITEM_MAGIC_WAND, 4,
                    ChatColor.GRAY + EsportsLang.get("game.special.items.lore.magic-wand-tier3.1", "Right-click: shatter a large area"),
                    ChatColor.GRAY + EsportsLang.get("game.special.items.lore.magic-wand-tier3.2", "Great for opening walls")));
            pool.add(createSpecialItem(resolveMaterial("SNOWBALL", "SNOW_BALL"), SPECIAL_ITEM_METEOR_ORB, 3,
                    ChatColor.GRAY + EsportsLang.get("game.special.items.lore.meteor-orb-tier3.1", "Right-click: meteor barrage orb"),
                    ChatColor.GRAY + EsportsLang.get("game.special.items.lore.meteor-orb-tier3.2", "High impact area strike")));
        }
        if (pool.isEmpty()) {
            return createSpecialItem(Material.SLIME_BALL, SPECIAL_ITEM_TRAMPOLINE, 3,
                    ChatColor.GRAY + EsportsLang.get("game.special.items.lore.default", "Right-click"));
        }
        return pool.get(random.nextInt(pool.size())).clone();
    }

    private ItemStack createSpecialItem(Material type, String specialType, int uses, String... loreLines) {
        Material resolved = type == null ? Material.STICK : type;
        ItemStack item = new ItemStack(resolved, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            int safeUses = Math.max(1, uses);
            meta.setDisplayName(getSpecialItemDisplayName(specialType) + ChatColor.GRAY + " [" + safeUses + "]");
            List<String> lore = new ArrayList<>();
            lore.add(getSpecialUsesLoreLine(safeUses));
            if (loreLines != null && loreLines.length > 0) {
                lore.addAll(Arrays.asList(loreLines));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material resolveMaterial(String primary, String fallback) {
        Material mat = MaterialResolver.resolveMaterial(primary);
        if (mat != null) {
            return mat;
        }
        try {
            return Material.valueOf(primary);
        } catch (Exception ignored) {
        }
        if (fallback != null) {
            mat = MaterialResolver.resolveMaterial(fallback);
            if (mat != null) {
                return mat;
            }
            try {
                return Material.valueOf(fallback);
            } catch (Exception ignored) {
            }
        }
        return Material.STICK;
    }

    private String resolveDisplayName(ItemStack item) {
        if (item == null) {
            return "Special Item";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return item.getType().name();
    }

    private void spawnChestHologram(Location chestLoc, Player ignoredViewer) {
        Location key = normalizeBlock(chestLoc);
        if (key == null) {
            return;
        }
        refreshChestHologram(key);
        ensureChestHologramTicker(key);
    }

    private void refreshChestHologram(Location key) {
        if (key == null) {
            return;
        }
        Integer tier = activeChests.get(key);
        if (tier == null) {
            removeChestHologram(key);
            return;
        }
        long now = System.currentTimeMillis();
        long refreshAt = chestRefreshTimes.getOrDefault(key, now);
        long remaining = Math.max(0L, (refreshAt - now) / 1000L);
        String tierText = EsportsLang.get("game.drops.hologram.tier", "&6Tier {tier}", "tier", tier);
        String nextText = tier >= 3
                ? EsportsLang.get("game.drops.hologram.max-tier", "&7Max Tier")
                : EsportsLang.get("game.drops.hologram.upgrade-in", "&bUpgrade in: &e{seconds}s", "seconds", remaining);
        Hologram existing = chestHolograms.get(key);
        if (existing != null) {
            try {
                existing.updateLine(1, tierText);
                existing.updateLine(2, nextText);
                return;
            } catch (Exception ignored) {
            }
            removeChestHologram(key);
        }
        List<String> lines = Arrays.asList(tierText, nextText);
        Hologram hologram = HologramLibrary.createHologram(key.clone().add(0.5, 1.4, 0.5), lines);
        if (hologram != null) {
            chestHolograms.put(key, hologram);
        }
    }

    private void ensureChestHologramTicker(Location chestLoc) {
        Location key = normalizeBlock(chestLoc);
        if (key == null || chestHologramTasks.containsKey(key)) {
            return;
        }
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!hasActiveMatchContext()
                        || key.getWorld() == null
                        || !activeChests.containsKey(key)
                        || key.getBlock().getType() != Material.CHEST) {
                    removeChestHologram(key);
                    chestHologramTasks.remove(key);
                    cancel();
                    return;
                }
                refreshChestHologram(key);
            }
        }.runTaskTimer(plugin, 20L, 20L);
        chestHologramTasks.put(key, task);
    }

    private void removeChestHologram(Location chestLoc) {
        Location key = normalizeBlock(chestLoc);
        if (key == null) {
            return;
        }
        Hologram hologram = chestHolograms.remove(key);
        if (hologram != null) {
            try {
                HologramLibrary.removeHologram(hologram);
            } catch (Exception ignored) {
            }
        }
    }

    private void cancelChestHologramTicker(Location chestLoc) {
        Location key = normalizeBlock(chestLoc);
        if (key == null) {
            return;
        }
        BukkitTask task = chestHologramTasks.remove(key);
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
        }
    }

    private void cancelAllChestHologramTickers() {
        for (BukkitTask task : new ArrayList<>(chestHologramTasks.values())) {
            if (task == null) {
                continue;
            }
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
        }
        chestHologramTasks.clear();
    }

    private List<ItemStack> buildTierLootList(int tier) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Material.COOKED_BEEF, randomAmount(4, 8)));
        items.add(new ItemStack(Material.GOLDEN_APPLE, tier));
        Material snowball = MaterialResolver.resolveMaterial("SNOWBALL");
        if (snowball == null) {
            try {
                snowball = Material.valueOf("SNOWBALL");
            } catch (Exception ignored) {
                snowball = Material.valueOf("SNOW_BALL");
            }
        }
        items.add(new ItemStack(snowball, randomAmount(4, 12)));
        if (tier >= 2) {
            ItemStack ironSword = new ItemStack(Material.IRON_SWORD, 1);
            addUnsafeEnchant(ironSword, "DAMAGE_ALL", 2);
            addUnsafeEnchant(ironSword, "DURABILITY", 2);
            items.add(ironSword);

            ItemStack ironChest = new ItemStack(Material.IRON_CHESTPLATE, 1);
            addUnsafeEnchant(ironChest, "PROTECTION_ENVIRONMENTAL", 2);
            items.add(ironChest);

            ItemStack ironLeggings = new ItemStack(Material.IRON_LEGGINGS, 1);
            addUnsafeEnchant(ironLeggings, "PROTECTION_ENVIRONMENTAL", 1);
            items.add(ironLeggings);

            ItemStack bow = new ItemStack(Material.BOW, 1);
            addUnsafeEnchant(bow, "ARROW_DAMAGE", 2);
            addUnsafeEnchant(bow, "DURABILITY", 2);
            items.add(bow);

            items.add(new ItemStack(Material.ARROW, randomAmount(32, 64)));
            items.add(new ItemStack(Material.GOLDEN_APPLE, randomAmount(2, 4)));
            items.add(new ItemStack(Material.ENDER_PEARL, randomAmount(1, 3)));
        } else {
            items.add(new ItemStack(Material.STONE_SWORD, 1));
            items.add(new ItemStack(Material.CHAINMAIL_HELMET, 1));
        }
        if (tier == 3) {
            ItemStack diamondSword = new ItemStack(Material.DIAMOND_SWORD, 1);
            addUnsafeEnchant(diamondSword, "DAMAGE_ALL", 4);
            addUnsafeEnchant(diamondSword, "DURABILITY", 3);
            items.add(diamondSword);

            ItemStack diamondChest = new ItemStack(Material.DIAMOND_CHESTPLATE, 1);
            addUnsafeEnchant(diamondChest, "PROTECTION_ENVIRONMENTAL", 3);
            addUnsafeEnchant(diamondChest, "DURABILITY", 2);
            items.add(diamondChest);

            ItemStack diamondHelmet = new ItemStack(Material.DIAMOND_HELMET, 1);
            addUnsafeEnchant(diamondHelmet, "PROTECTION_ENVIRONMENTAL", 2);
            items.add(diamondHelmet);

            ItemStack powerBow = new ItemStack(Material.BOW, 1);
            addUnsafeEnchant(powerBow, "ARROW_DAMAGE", 3);
            addUnsafeEnchant(powerBow, "ARROW_KNOCKBACK", 1);
            addUnsafeEnchant(powerBow, "DURABILITY", 3);
            items.add(powerBow);

            items.add(new ItemStack(Material.ARROW, randomAmount(64, 96)));
            items.add(new ItemStack(Material.GOLDEN_APPLE, randomAmount(4, 7)));
            items.add(new ItemStack(Material.ENDER_PEARL, randomAmount(2, 5)));
        }
        Collections.shuffle(items, random);
        return items;
    }

    private void addUnsafeEnchant(ItemStack item, String enchantName, int level) {
        if (item == null || enchantName == null || enchantName.trim().isEmpty()) {
            return;
        }
        Enchantment enchantment = Enchantment.getByName(enchantName);
        if (enchantment == null) {
            for (Enchantment candidate : Enchantment.values()) {
                if (candidate != null && enchantName.equalsIgnoreCase(candidate.getName())) {
                    enchantment = candidate;
                    break;
                }
            }
        }
        if (enchantment == null) {
            return;
        }
        try {
            item.addUnsafeEnchantment(enchantment, Math.max(1, level));
        } catch (Exception ignored) {
        }
    }

    private void applyLootToChest(Chest chest, int tier, boolean merge) {
        if (chest == null) {
            return;
        }
        List<ItemStack> loot = buildTierLootList(tier);
        List<ItemStack> existing = new ArrayList<>();
        if (merge) {
            for (ItemStack item : chest.getBlockInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    existing.add(item);
                }
            }
        }
        existing.addAll(loot);
        Collections.shuffle(existing, random);
        ItemStack[] contents = new ItemStack[27];
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, random);
        for (int i = 0; i < existing.size() && i < slots.size(); i++) {
            contents[slots.get(i)] = existing.get(i);
        }
        chest.getBlockInventory().setContents(contents);
    }

    private int randomAmount(int min, int max) {
        return min + random.nextInt(Math.max(1, max - min + 1));
    }

    private void scheduleChestRefresh(Location loc, int currentTier) {
        if (loc == null) {
            return;
        }
        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                chestRefreshTasks.remove(this);
                if (!hasActiveMatchContext()) {
                    return;
                }
                if (loc.getWorld() == null) {
                    return;
                }
                Location key = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                Integer tier = activeChests.get(key);
                if (tier == null) {
                    return;
                }
                Material type = key.getBlock().getType();
                if (type != Material.CHEST) {
                    return;
                }
                Chest chest = (Chest) key.getBlock().getState();
                int updatedTier = Math.min(3, tier + 1);
                nameChest(chest, EsportsLang.get("game.drops.chest-name", "&6Tier {tier} Chest", "tier", updatedTier));
                applyLootToChest(chest, updatedTier, true);
                chest.update(true);
                activeChests.put(key, updatedTier);
                safeEffect(key.clone().add(0.5, 1.2, 0.5), Effect.SMOKE, 8);
                playSound(key, 1.0F, 1.0F,
                        "BLOCK_CHEST_OPEN",
                        "CHEST_OPEN");
                sendToParticipantsLocalized(
                        "game.drops.chest-refreshed",
                        "&b[E-Sports] &eChest refreshed! Tier {tier} -> {x}, {y}, {z}",
                        "tier", updatedTier,
                        "x", key.getBlockX(),
                        "y", key.getBlockY(),
                        "z", key.getBlockZ());
                if (updatedTier < 3) {
                    scheduleChestRefresh(key, updatedTier);
                }
            }
        };
        long runAt = System.currentTimeMillis() + (CHEST_REFRESH_SECONDS * 1000L);
        chestRefreshTimes.put(loc, runAt);
        BukkitTask task = runnable.runTaskLater(plugin, 20L * CHEST_REFRESH_SECONDS);
        chestRefreshTasks.add(task);
    }

    private void nameChest(Chest chest, String name) {
        if (chest == null) {
            return;
        }
        try {
            Method setName = chest.getClass().getMethod("setCustomName", String.class);
            setName.invoke(chest, name);
            Method setVisible = chest.getClass().getMethod("setCustomNameVisible", boolean.class);
            setVisible.invoke(chest, true);
        } catch (Exception ignored) {
        }
    }

    private void safeEffect(Location loc, Effect effect, int repeats) {
        if (loc == null || loc.getWorld() == null || effect == null) {
            return;
        }
        for (int i = 0; i < Math.max(1, repeats); i++) {
            loc.getWorld().playEffect(loc, effect, 0);
        }
    }

    private void playSound(Location loc, float volume, float pitch, String... ids) {
        if (loc == null || loc.getWorld() == null || ids == null) {
            return;
        }
        Sound sound = null;
        for (String id : ids) {
            try {
                sound = Sound.valueOf(id);
                break;
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (sound == null) {
            return;
        }
        try {
            loc.getWorld().playSound(loc, sound, volume, pitch);
        } catch (NoSuchMethodError err) {
            try {
                Method legacy = loc.getWorld().getClass().getMethod("playSound", Location.class, String.class, float.class, float.class);
                legacy.invoke(loc.getWorld(), sound.name(), volume, pitch);
            } catch (Exception ignored) {
            }
        }
    }

    private Set<UUID> collectClubMembers(String tag) {
        Set<UUID> ids = new HashSet<>();
        if (tag == null) {
            return ids;
        }
        if (manager != null) {
            manager.getClubByTag(tag).ifPresent(entry -> {
                ClubManager clubManager = plugin.getClubManager();
                if (clubManager != null) {
                    clubManager.getClubById(entry.getClubId()).ifPresent(club -> {
                        club.getMembers().forEach(member -> {
                            if (member != null && member.getUniqueId() != null) {
                                ids.add(member.getUniqueId());
                            }
                        });
                    });
                }
            });
        }
        if (ids.isEmpty()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).contains(tag.toLowerCase(Locale.ROOT))) {
                    ids.add(p.getUniqueId());
                }
            }
        }
        return ids;
    }

    public void openKitEditor(String clubTag, Player player) {
        if (player == null) {
            return;
        }
        JsonObject kit = customClubKits.getOrDefault(clubTag == null ? "" : clubTag.toLowerCase(Locale.ROOT), new JsonObject());
        new EsportsKitMenu(plugin, player, null, kit, payload -> {
            saveClubKit(clubTag, player);
            return true;
        }, true, ChatColor.DARK_GRAY + "Club Kit");
    }

    public boolean saveClubKit(String clubTag, Player player) {
        if (player == null) {
            return false;
        }
        JsonObject kitJson = new JsonObject();
        kitJson.addProperty("owner", player.getName());
        customClubKits.put(clubTag == null ? "" : clubTag.toLowerCase(Locale.ROOT), kitJson);
        return true;
    }

    // --- Internal helpers for chest drops/arena math ---

    private Location normalizeBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private Location computeCenter(List<Location> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        double x = 0, y = 0, z = 0;
        World world = null;
        int count = 0;
        for (Location loc : points) {
            if (loc == null || loc.getWorld() == null) continue;
            world = loc.getWorld();
            x += loc.getX();
            y += loc.getY();
            z += loc.getZ();
            count++;
        }
        if (count == 0 || world == null) {
            return null;
        }
        return new Location(world, x / count, y / count, z / count);
    }

    private double computeRadius(List<Location> points, Location center) {
        if (points == null || points.isEmpty() || center == null) {
            return 0.0D;
        }
        double max = 0.0D;
        for (Location loc : points) {
            if (loc == null || loc.getWorld() == null || center.getWorld() == null
                    || !loc.getWorld().equals(center.getWorld())) {
                continue;
            }
            double dist = loc.distance(center);
            if (dist > max) {
                max = dist;
            }
        }
        return max;
    }

    private boolean isInsideWorldBorder(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        try {
            Object border = loc.getWorld().getWorldBorder();
            if (border != null) {
                try {
                    Method isInside = border.getClass().getMethod("isInside", Location.class);
                    Object result = isInside.invoke(border, loc);
                    if (result instanceof Boolean) {
                        return (Boolean) result;
                    }
                } catch (Exception ignored) {
                }
                try {
                    Method getCenter = border.getClass().getMethod("getCenter");
                    Method getSize = border.getClass().getMethod("getSize");
                    Location c = (Location) getCenter.invoke(border);
                    double size = ((Number) getSize.invoke(border)).doubleValue() / 2.0D;
                    double dx = Math.abs(loc.getX() - c.getX());
                    double dz = Math.abs(loc.getZ() - c.getZ());
                    return dx <= size - 1.0D && dz <= size - 1.0D;
                } catch (Exception ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    private void stopChestDrops() {
        if (chestDropTask != null) {
            try {
                chestDropTask.cancel();
            } catch (Exception ignored) {
            }
            chestDropTask = null;
        }
        cancelAllChestHologramTickers();
        chestRefreshTasks.forEach(task -> {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
        });
        chestRefreshTasks.clear();
        nextDropSeconds = 0;
    }

    private void clearDropBlocks() {
        cancelAllChestHologramTickers();
        for (Location loc : new HashSet<>(activeChests.keySet())) {
            try {
                if (loc != null && loc.getBlock() != null && loc.getBlock().getType() == Material.CHEST) {
                    loc.getBlock().setType(Material.AIR);
                }
            } catch (Throwable ignored) {
            }
        }
        activeChests.clear();
        chestRefreshTimes.clear();
        openedChestHolograms.clear();
        initializedChests.clear();
        for (Map.Entry<Location, Hologram> entry : new HashSet<>(chestHolograms.entrySet())) {
            try {
                HologramLibrary.removeHologram(entry.getValue());
            } catch (Exception ignored) {
            }
        }
        chestHolograms.clear();
        for (Location loc : new HashSet<>(specialSupplyTiers.keySet())) {
            restoreSpecialSupplyAt(loc, true);
        }
        for (Map.Entry<Location, Hologram> entry : new HashSet<>(specialSupplyHolograms.entrySet())) {
            try {
                HologramLibrary.removeHologram(entry.getValue());
            } catch (Exception ignored) {
            }
        }
        specialSupplyHolograms.clear();
        specialSupplyTiers.clear();
        specialSupplyRewards.clear();
        for (Location blockLoc : new HashSet<>(specialSupplyOriginalBlocks.keySet())) {
            restoreSpecialSupplyBlock(blockLoc);
        }
        specialSupplyOriginalBlocks.clear();
        specialSupplyCooldown.clear();
        specialItemCooldown.clear();
        noFallDamageUntil.clear();
        lastDropLocation = null;
    }

    public void spawnEsportsHologram(Player player, String id) {
        if (player == null || id == null) {
            return;
        }
        Location loc = player.getLocation().clone().add(0, 2.0, 0);
        Hologram holo = HologramLibrary.createHologram(loc, Collections.singletonList(ChatColor.GOLD + "ESPORTS " + ChatColor.YELLOW + id));
        if (holo != null) {
            customHolograms.put(id, holo);
            if (player != null) {
                player.sendMessage(EsportsLang.get("game.debug.hologram-spawned",
                        "&aHologram spawned: {id}",
                        "id", id));
            }
        }
    }

    private void ensureBracketItem(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        if (!isBracketItem(inv.getItem(0))) {
            inv.setItem(0, bracketViewerItem.clone());
        }
    }

    private void clearPlayerInventory(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        applyOffhand(inv, null);
        player.updateInventory();
    }

    private static final class ReplayEventEntry {
        final int matchId;
        final String eventType;
        final String payloadJson;

        ReplayEventEntry(int matchId, String eventType, String payloadJson) {
            this.matchId = matchId;
            this.eventType = eventType;
            this.payloadJson = payloadJson;
        }
    }

    private static final class MatchReplayCapture {
        final int matchId;
        final int tournamentId;
        final String tournamentSlug;
        final String tournamentName;
        final long startedAtMs;
        final int maxEvents;
        final List<String> clubTags;
        final Map<String, Integer> typeCounts = new ConcurrentHashMap<>();
        volatile int acceptedEvents;
        volatile int droppedEvents;
        volatile boolean closed;

        MatchReplayCapture(int matchId,
                           int tournamentId,
                           String tournamentSlug,
                           String tournamentName,
                           long startedAtMs,
                           int maxEvents,
                           List<String> clubTags) {
            this.matchId = matchId;
            this.tournamentId = tournamentId;
            this.tournamentSlug = tournamentSlug;
            this.tournamentName = tournamentName;
            this.startedAtMs = startedAtMs;
            this.maxEvents = maxEvents;
            this.clubTags = clubTags == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(clubTags));
        }
    }
}
