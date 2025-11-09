package dev.zonely.whiteeffect.replay.control;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.replay.NPCReplayManager;
import dev.zonely.whiteeffect.replay.advanced.AdvancedReplayEngine;
import dev.zonely.whiteeffect.nms.NMS;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ReplayControlManager implements Listener {

    private static final Map<UUID, ControlState> ACTIVE = new ConcurrentHashMap<>();
    private static final int SEEK_FIVE_SECONDS = 20 * 5;
    private static final double SPEED_STEP = 0.25;
    private static final EnumMap<ControlAction, ControlLayout> CONTROL_LAYOUT = new EnumMap<>(ControlAction.class);
    private static int SELECTED_HOTBAR_SLOT;
    private static boolean SCOREBOARD_ENABLED;
    private static String SCOREBOARD_TITLE;

    private static final List<String> DEFAULT_SCOREBOARD_LINES = Arrays.asList(
            "&eCurrent: &f{current_time}",
            "&eTotal: &f{max_time}",
            "&bSpeed: &f{speed}x",
            "&aState: &f{state}"
    );
    private static final String DEFAULT_STATE_PLAYING = "&aPlaying";
    private static final String DEFAULT_STATE_PAUSED = "&cPaused";
    private static final String DEFAULT_HUD_TEMPLATE = "&e{current_time} &7/ &e{max_time} &b{speed}x";

    private static List<String> SCOREBOARD_LINES = new ArrayList<>(DEFAULT_SCOREBOARD_LINES);
    private static String STATE_PLAYING = DEFAULT_STATE_PLAYING;
    private static String STATE_PAUSED = DEFAULT_STATE_PAUSED;
    private static String HUD_TEMPLATE = DEFAULT_HUD_TEMPLATE;

    private static long ACTIONBAR_INTERVAL_MS; 
    private static final Map<UUID, Long> LAST_AB_AT = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_AB_SECOND = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_AB_SPEED = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> LAST_AB_PAUSED = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_AB_PRIMARY = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_AB_TEXT = new ConcurrentHashMap<>();

    static {
        reloadLocalization();
    }

    private static void reloadLocalization() {
        CONTROL_LAYOUT.clear();
        for (ControlAction action : ControlAction.values()) {
            int rawSlot = LanguageManager.getInt("menus.reports.replay.controls.items." + action.configKey + ".slot", action.defaultSlot);
            int slot = sanitizeActionSlot(rawSlot);
            String definition = LanguageManager.get("menus.reports.replay.controls.items." + action.configKey + ".item", action.defaultDefinition);
            ItemStack item = deserializeItem(action.configKey + ".item", definition);
            if (item == null && action.defaultDefinition != null) {
                item = deserializeItem(action.configKey + ".default", action.defaultDefinition);
            }
            if (item == null && action.legacyDefinition != null) {
                item = deserializeItem(action.configKey + ".legacy", action.legacyDefinition);
            }
            CONTROL_LAYOUT.put(action, new ControlLayout(slot, item));
        }
        SELECTED_HOTBAR_SLOT = sanitizeSelectedSlot(LanguageManager.getInt("menus.reports.replay.controls.hotbar.selected-slot", 0));
        SCOREBOARD_ENABLED = LanguageManager.getBoolean("menus.reports.replay.controls.scoreboard.enabled", true);
        SCOREBOARD_TITLE = LanguageManager.get("menus.reports.replay.controls.scoreboard.title", "&6Replay Actors");
        List<String> configuredLines = LanguageManager.getList("menus.reports.replay.controls.scoreboard.lines", DEFAULT_SCOREBOARD_LINES);
        SCOREBOARD_LINES = configuredLines == null || configuredLines.isEmpty()
                ? new ArrayList<>(DEFAULT_SCOREBOARD_LINES)
                : new ArrayList<>(configuredLines);
        STATE_PLAYING = LanguageManager.get("menus.reports.replay.controls.messages.state.playing", DEFAULT_STATE_PLAYING);
        STATE_PAUSED = LanguageManager.get("menus.reports.replay.controls.messages.state.paused", DEFAULT_STATE_PAUSED);
        HUD_TEMPLATE = LanguageManager.get("menus.reports.replay.controls.hud", DEFAULT_HUD_TEMPLATE);

    }

    private static int sanitizeActionSlot(int slot) { return (slot < 0 || slot > 8) ? -1 : slot; }
    private static int sanitizeSelectedSlot(int slot) { return (slot < 0 || slot > 8) ? 0 : slot; }

    private static ItemStack deserializeItem(String key, String definition) {
        if (definition == null || definition.trim().isEmpty()) return null;
        try {
            return BukkitUtils.deserializeItemStack(definition);
        } catch (IllegalArgumentException ex) {
            Core instance = Core.getInstance();
            if (instance != null) instance.getLogger().warning("[ReplayControl] Failed to parse item for " + key + ": " + definition);
            return null;
        }
    }

    private static ReplayControlManager instance;
    private final Core plugin;

    private ReplayControlManager(Core plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static void init(Core plugin) {
        if (instance == null) instance = new ReplayControlManager(plugin);
    }

    public static void shutdownAll() {
        if (instance == null) return;
        for (UUID uuid : ACTIVE.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) end(player);
        }
        ACTIVE.clear();
        instance = null;
    }

    public static void begin(Player player) {
        if (player == null || !player.isOnline()) return;
        if (ACTIVE.containsKey(player.getUniqueId())) return;
        ControlState state = new ControlState(player);
        ACTIVE.put(player.getUniqueId(), state);
        instance.applyControls(player);
        applyScoreboardLines(player, Collections.singletonList(ChatColor.GRAY + "Loading..."));
    }

    public static void end(Player player) {
        if (player == null) return;
        ControlState state = ACTIVE.remove(player.getUniqueId());
        if (state != null) state.restore(player, instance);

        UUID id = player.getUniqueId();
        LAST_AB_AT.remove(id);
        LAST_AB_SECOND.remove(id);
        LAST_AB_SPEED.remove(id);
        LAST_AB_PAUSED.remove(id);
        LAST_AB_PRIMARY.remove(id);
        LAST_AB_TEXT.remove(id);
    }

    private void applyControls(Player player) {
        ControlState state = ACTIVE.get(player.getUniqueId());
        if (state == null) return;

        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);

        for (ControlAction action : ControlAction.values()) {
            ControlLayout layout = CONTROL_LAYOUT.get(action);
            if (layout == null || layout.item == null) continue;
            int slot = layout.slot;
            if (slot < 0 || slot > 8) continue;
            inv.setItem(slot, layout.item.clone());
        }

        try {
            state.previousGameMode = player.getGameMode();
            if (state.previousGameMode != GameMode.CREATIVE) player.setGameMode(GameMode.CREATIVE);
        } catch (Throwable ignored) {}

        state.previousAllowFlight = player.getAllowFlight();
        state.previousFlying = player.isFlying();
        player.setAllowFlight(true);
        player.setFlying(true);
        applyInvisibility(player, state);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            ControlState current = ACTIVE.get(player.getUniqueId());
            if (current == null) return;
            try { player.setGameMode(GameMode.CREATIVE); } catch (Throwable ignored) {}
            player.setAllowFlight(true);
            player.setFlying(true);
        }, 1L);

        inv.setHeldItemSlot(Math.max(0, Math.min(8, SELECTED_HOTBAR_SLOT)));
        dev.zonely.whiteeffect.nms.util.SafeInventoryUpdater.update(player);

        if (SCOREBOARD_ENABLED) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                try { state.originalScoreboard = player.getScoreboard(); } catch (Throwable ignored) {}
                Scoreboard replayBoard = manager.getNewScoreboard();
                Objective obj = replayBoard.registerNewObjective("replay", "dummy");
                obj.setDisplaySlot(DisplaySlot.SIDEBAR);
                obj.setDisplayName(SCOREBOARD_TITLE);
                state.replayScoreboard = replayBoard;
                state.replayObjective = obj;
                try { player.setScoreboard(replayBoard); } catch (Throwable ignored) {}
            }
        }

        hideFromOthers(player, state);
    }

    private void applyInvisibility(Player player, ControlState state) {
        if (player == null || state == null) return;
        if (state.previousInvisibility != null || state.appliedInvisibility) return;
        try {
            PotionEffect effect = new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false);
            player.addPotionEffect(effect);
            state.appliedInvisibility = true;
        } catch (Throwable ignored) {}
    }

    private void hideFromOthers(Player player, ControlState state) {
        if (player == null) return;
        state.hiddenFrom.clear();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (!canSee(other, player)) continue;
            if (hidePlayer(other, player)) state.hiddenFrom.add(other.getUniqueId());
        }
    }

    private void showToOthers(Player player, ControlState state) {
        if (player == null || state.hiddenFrom.isEmpty()) return;
        Set<UUID> snapshot = new HashSet<>(state.hiddenFrom);
        for (UUID uuid : snapshot) {
            Player other = Bukkit.getPlayer(uuid);
            if (other == null) continue;
            showPlayer(other, player);
        }
        state.hiddenFrom.clear();
    }

    private boolean canSee(Player observer, Player target) {
        try { return observer.canSee(target); } catch (Throwable ignored) { return true; }
    }

    private boolean hidePlayer(Player observer, Player target) {
        try {
            Player.class.getMethod("hidePlayer", org.bukkit.plugin.Plugin.class, Player.class).invoke(observer, plugin, target);
            return true;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable ignored) { return false; }
        try {
            Player.class.getMethod("hidePlayer", Player.class).invoke(observer, target);
            return true;
        } catch (Throwable ignored) { return false; }
    }

    private void showPlayer(Player observer, Player target) {
        try {
            Player.class.getMethod("showPlayer", org.bukkit.plugin.Plugin.class, Player.class).invoke(observer, plugin, target);
            return;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable ignored) { return; }
        try { Player.class.getMethod("showPlayer", Player.class).invoke(observer, target); } catch (Throwable ignored) {}
    }


    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!ACTIVE.containsKey(player.getUniqueId())) return;
        ItemStack item = event.getItem();
        ControlAction action = ControlAction.fromItem(item);
        if (action == null) return;
        event.setCancelled(true);
        handleAction(player, action);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!ACTIVE.containsKey(player.getUniqueId())) return;

        Inventory clicked = event.getClickedInventory();

        if (clicked != null && clicked.getType() == InventoryType.PLAYER) {
            boolean movingControl = false;

            ItemStack current = event.getCurrentItem();
            ItemStack cursor  = event.getCursor();

            if (ControlAction.fromItem(current) != null || ControlAction.fromItem(cursor) != null) {
                movingControl = true;
            }

            if (event.getClick() == ClickType.NUMBER_KEY) {
                int hb = event.getHotbarButton(); 
                if (hb >= 0 && hb <= 8) {
                    ItemStack hotbar = player.getInventory().getItem(hb);
                    if (ControlAction.fromItem(hotbar) != null) movingControl = true;
                }
            }

            if (movingControl) {
                event.setCancelled(true);
                dev.zonely.whiteeffect.nms.util.SafeInventoryUpdater.update(player);
            }
            return; 
        }

    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!ACTIVE.containsKey(event.getPlayer().getUniqueId())) return;
        if (ControlAction.fromItem(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        for (Map.Entry<UUID, ControlState> entry : ACTIVE.entrySet()) {
            ControlState state = entry.getValue();
            if (state == null) continue;
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer == null || !viewer.isOnline()) continue;
            if (hidePlayer(joining, viewer)) state.hiddenFrom.add(joining.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (ACTIVE.containsKey(player.getUniqueId())) {
            NPCReplayManager.stopReplay(player);
        }
    }


    public static void updateHud(Player player, HudContext context) {
        updateHud(player, context, null);
    }

    public static void updateHud(Player player, HudContext context, List<String> additionalLines) {
        if (player == null || !player.isOnline() || context == null) return;

        if (instance != null && HUD_TEMPLATE != null && !HUD_TEMPLATE.isEmpty()) {
            String action = applyPlaceholders(HUD_TEMPLATE, context);
            if (action != null && !action.isEmpty()) {
                instance.sendActionBarThrottled(player, action, context);
            }
        }

        if (SCOREBOARD_ENABLED) {
            List<String> lines = applyPlaceholders(SCOREBOARD_LINES, context);
            if (additionalLines != null && !additionalLines.isEmpty()) lines.addAll(additionalLines);
            applyScoreboardLines(player, lines);
        }
    }

    private static void applyScoreboardLines(Player player, List<String> lines) {
        if (player == null || !player.isOnline()) return;
        if (!SCOREBOARD_ENABLED) return;

        ControlState state = ACTIVE.get(player.getUniqueId());
        if (state == null || state.replayScoreboard == null || state.replayObjective == null) return;

        Scoreboard board = state.replayScoreboard;
        Objective obj = state.replayObjective;

        for (String entry : new ArrayList<>(board.getEntries())) board.resetScores(entry);

        List<String> applied = (lines == null || lines.isEmpty())
                ? Collections.singletonList(ChatColor.GRAY + "No data")
                : lines;

        int score = applied.size();
        ChatColor[] colors = ChatColor.values();
        for (int i = 0; i < applied.size(); i++) {
            String line = ChatColor.WHITE + applied.get(i);
            if (line.length() > 32) line = line.substring(0, 32);

            String entryBase = line;
            if (entryBase.length() > 14) entryBase = entryBase.substring(0, 14);

            ChatColor suffix = colors[i % colors.length];
            String entry = entryBase + suffix;
            obj.getScore(entry).setScore(score--);
        }
    }


    private void handleAction(Player player, ControlAction action) {
        if (player == null || action == null) return;
        AdvancedReplayEngine engine = AdvancedReplayEngine.get();
        boolean running = engine.isRunning(player);

        switch (action) {
            case TOGGLE_PAUSE: {
                if (!running) return;
                boolean paused = engine.togglePause(player);
                String key = paused ? "menus.reports.replay.messages.paused" : "menus.reports.replay.messages.resumed";
                String def = paused ? "&eReplay paused." : "&aReplay resumed.";
                sendLocalizedActionBar(player, key, def);
                break;
            }
            case SEEK_BACK: {
                if (!running) {
                    sendLocalizedActionBar(player, "menus.reports.replay.messages.seek-back.failure", "&cUnable to rewind replay.");
                    return;
                }
                boolean success = engine.seek(player, -SEEK_FIVE_SECONDS);
                String key = success ? "menus.reports.replay.messages.seek-back.success" : "menus.reports.replay.messages.seek-back.failure";
                String def = success ? "&bRewound 5 seconds." : "&cUnable to rewind replay.";
                sendLocalizedActionBar(player, key, def);
                break;
            }
            case SEEK_FORWARD: {
                if (!running) {
                    sendLocalizedActionBar(player, "menus.reports.replay.messages.seek-forward.failure", "&cUnable to fast-forward replay.");
                    return;
                }
                boolean success = engine.seek(player, SEEK_FIVE_SECONDS);
                String key = success ? "menus.reports.replay.messages.seek-forward.success" : "menus.reports.replay.messages.seek-forward.failure";
                String def = success ? "&bAdvanced 5 seconds." : "&cUnable to fast-forward replay.";
                sendLocalizedActionBar(player, key, def);
                break;
            }
            case SPEED_DOWN: {
                if (!running) return;
                double speed = engine.changeSpeed(player, -SPEED_STEP);
                sendLocalizedActionBar(player, "menus.reports.replay.messages.speed", "&eReplay speed: {speed}x",
                        "speed", String.format(Locale.US, "%.2f", speed));
                break;
            }
            case SPEED_UP: {
                if (!running) return;
                double speed = engine.changeSpeed(player, SPEED_STEP);
                sendLocalizedActionBar(player, "menus.reports.replay.messages.speed", "&eReplay speed: {speed}x",
                        "speed", String.format(Locale.US, "%.2f", speed));
                break;
            }
            case RESTART: {
                if (!running) return;
                engine.restart(player);
                sendLocalizedActionBar(player, "menus.reports.replay.messages.restart", "&aReplay restarted.");
                break;
            }
            case EXIT: {
                NPCReplayManager.stopReplay(player);
                sendLocalizedActionBar(player, "menus.reports.replay.messages.exit", "&cStopped replay playback.");
                break;
            }
            default: break;
        }
    }

    private void sendLocalizedActionBar(Player player, String key, String def, Object... placeholders) {
        if (player == null) return;
        String message = def;
        if (key != null && !key.isEmpty()) {
            Profile profile = Profile.getProfile(player.getName());
            message = LanguageManager.get(profile, key, def, placeholders);
        }
        if (message == null || message.trim().isEmpty()) return;
        sendActionBar(player, message);
    }

    private void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        if (message == null || message.trim().isEmpty()) return;
        String colored = ChatColor.translateAlternateColorCodes('&', message);
        try { NMS.sendActionBar(player, colored); return; } catch (Throwable ignored) {}
        trySendSpigotActionBar(player, colored);
    }

  
    private void sendActionBarThrottled(Player player, String message, HudContext ctx) {
        if (player == null || !player.isOnline()) return;
        if (message == null || message.trim().isEmpty()) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        int second = Math.max(0, ctx.currentTick / 20);
        String speedStr = ctx.formattedSpeed;
        boolean paused = ctx.paused;
        String primary = ctx.primaryActor == null ? "-" : ctx.primaryActor;

        Integer lastSec = LAST_AB_SECOND.get(id);
        String lastSpeed = LAST_AB_SPEED.get(id);
        Boolean lastPaused = LAST_AB_PAUSED.get(id);
        String lastPrimary = LAST_AB_PRIMARY.get(id);
        String lastText = LAST_AB_TEXT.get(id);
        Long lastAt = LAST_AB_AT.get(id);

        boolean stateChanged =
                (lastSec == null || !lastSec.equals(second)) ||
                (lastSpeed == null || !lastSpeed.equals(speedStr)) ||
                (lastPaused == null || !lastPaused.equals(paused)) ||
                (lastPrimary == null || !lastPrimary.equals(primary));

        boolean timeOk = (lastAt == null) || ((now - lastAt) >= ACTIONBAR_INTERVAL_MS);
        boolean textChanged = (lastText == null) || !lastText.equals(message);

        if (stateChanged || (timeOk && textChanged)) {
            sendActionBar(player, message);
            LAST_AB_SECOND.put(id, second);
            LAST_AB_SPEED.put(id, speedStr);
            LAST_AB_PAUSED.put(id, paused);
            LAST_AB_PRIMARY.put(id, primary);
            LAST_AB_TEXT.put(id, message);
            LAST_AB_AT.put(id, now);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean trySendSpigotActionBar(Player player, String message) {
        try {
            Class<?> chatMessageType = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Method sendMethod = Player.Spigot.class.getMethod("sendMessage", chatMessageType, BaseComponent[].class);
            Object actionBar = Enum.valueOf((Class<Enum>) chatMessageType, "ACTION_BAR");
            sendMethod.invoke(player.spigot(), actionBar, (Object) TextComponent.fromLegacyText(message));
            return true;
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable ignored) { }
        try {
            Method legacyMethod = Player.Spigot.class.getMethod("sendMessage", BaseComponent.class);
            legacyMethod.invoke(player.spigot(), new TextComponent(message));
            return true;
        } catch (Throwable ignored) { }
        return false;
    }


    private static String applyPlaceholders(String template, HudContext context) {
        if (template == null) return null;
        if (context == null) return ChatColor.translateAlternateColorCodes('&', template);
        String result = template
                .replace("{current_time}", context.formattedCurrentTime)
                .replace("{max_time}", context.formattedMaxTime)
                .replace("{speed}", context.formattedSpeed)
                .replace("{state}", context.state)
                .replace("{primary_actor}", context.primaryActor)
                .replace("{primary}", context.primaryActor);
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    private static List<String> applyPlaceholders(List<String> templates, HudContext context) {
        if (templates == null || templates.isEmpty()) return new ArrayList<>();
        List<String> resolved = new ArrayList<>(templates.size());
        for (String template : templates) {
            String applied = applyPlaceholders(template, context);
            resolved.add(applied == null ? "" : applied);
        }
        return resolved;
    }

    public static HudContext hudContext(int currentTick, int maxTick, double speed, boolean paused, String primaryActor) {
        return new HudContext(currentTick, maxTick, speed, paused, primaryActor);
    }

    private static String formatTime(int ticks) {
        if (ticks < 0) ticks = 0;
        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }


    public static final class HudContext {
        private final int currentTick;
        private final int maxTick;
        private final double speed;
        private final boolean paused;
        private final String primaryActor;
        private final String formattedCurrentTime;
        private final String formattedMaxTime;
        private final String formattedSpeed;
        private final String state;

        private HudContext(int currentTick, int maxTick, double speed, boolean paused, String primaryActor) {
            this.currentTick = Math.max(0, currentTick);
            this.maxTick = Math.max(maxTick, this.currentTick);
            this.speed = Math.max(0.0D, speed);
            this.paused = paused;
            this.primaryActor = primaryActor == null || primaryActor.isEmpty() ? "-" : primaryActor;
            this.formattedCurrentTime = formatTime(this.currentTick);
            this.formattedMaxTime = formatTime(Math.max(this.maxTick, this.currentTick));
            this.formattedSpeed = String.format(Locale.US, "%.2f", this.speed);
            String rawState = paused ? STATE_PAUSED : STATE_PLAYING;
            this.state = ChatColor.translateAlternateColorCodes('&', rawState == null ? "" : rawState);
        }
    }

    private enum ControlAction {
        TOGGLE_PAUSE("toggle-pause", 0, "SLIME_BALL : 1 : name>&aPause/Play : desc>&7Toggle playback."),
        SEEK_BACK("seek-back", 1, "ARROW : 1 : name>&e<< 5s Rewind : desc>&7Rewind 5 seconds."),
        SEEK_FORWARD("seek-forward", 2,
                "SPECTRAL_ARROW : 1 : name>&e>> 5s Forward : desc>&7Fast-forward 5 seconds.",
                "ARROW : 1 : name>&e>> 5s Forward : desc>&7Fast-forward 5 seconds."),
        SPEED_DOWN("speed-down", 3, "FEATHER : 1 : name>&b- Speed : desc>&7Decrease playback speed."),
        SPEED_UP("speed-up", 4, "SUGAR : 1 : name>&b+ Speed : desc>&7Increase playback speed."),
        RESTART("restart", 7, "CLOCK : 1 : name>&dRestart : desc>&7Restart the replay from the beginning."),
        EXIT("exit", 8, "BARRIER : 1 : name>&cExit : desc>&7Stop replay playback.");

        private final String configKey;
        private final int defaultSlot;
        private final String defaultDefinition;
        private final String legacyDefinition;

        ControlAction(String configKey, int defaultSlot, String defaultDefinition) {
            this(configKey, defaultSlot, defaultDefinition, null);
        }

        ControlAction(String configKey, int defaultSlot, String defaultDefinition, String legacyDefinition) {
            this.configKey = configKey;
            this.defaultSlot = defaultSlot;
            this.defaultDefinition = defaultDefinition;
            this.legacyDefinition = legacyDefinition;
        }

        static ControlAction fromItem(ItemStack stack) {
            if (stack == null) return null;
            for (Map.Entry<ControlAction, ControlLayout> entry : CONTROL_LAYOUT.entrySet()) {
                ControlLayout layout = entry.getValue();
                if (layout != null && layout.matches(stack)) return entry.getKey();
            }
            return null;
        }
    }

    private static final class ControlLayout {
        private final int slot;
        private final ItemStack item;

        private ControlLayout(int slot, ItemStack item) {
            this.slot = slot;
            this.item = item;
        }

        private boolean matches(ItemStack other) {
            if (this.item == null || other == null) return false;
            try { return this.item.isSimilar(other); } catch (Throwable ignored) { return false; }
        }
    }

    private static final class ControlState {
        private final ItemStack[] originalContents;
        private final ItemStack[] originalArmor;
        private final ItemStack originalOffHand;
        private final int originalSlot;
        private final Set<UUID> hiddenFrom = new HashSet<>();
        private GameMode previousGameMode;
        private boolean previousAllowFlight;
        private boolean previousFlying;
        private PotionEffect previousInvisibility;
        private boolean appliedInvisibility;
        private Scoreboard originalScoreboard;
        private Scoreboard replayScoreboard;
        private Objective replayObjective;

        private ControlState(Player player) {
            PlayerInventory inventory = player.getInventory();
            this.originalContents = cloneItems(inventory.getContents());
            this.originalArmor = cloneItems(inventory.getArmorContents());
            this.originalOffHand = getOffHandItem(inventory);
            this.originalSlot = inventory.getHeldItemSlot();
            this.previousGameMode = player.getGameMode();
            this.previousAllowFlight = player.getAllowFlight();
            this.previousFlying = player.isFlying();
            this.previousInvisibility = captureInvisibility(player);
        }

        private void restore(Player player, ReplayControlManager manager) {
            if (player == null) return;

            PlayerInventory inventory = player.getInventory();
            ItemStack[] contents = cloneItems(this.originalContents);
            if (contents != null) inventory.setContents(contents); else inventory.clear();

            ItemStack[] armor = cloneItems(this.originalArmor);
            if (armor != null) inventory.setArmorContents(armor); else inventory.setArmorContents(new ItemStack[4]);

            setOffHandItem(inventory, originalOffHand == null ? null : originalOffHand.clone());
            inventory.setHeldItemSlot(Math.max(0, Math.min(8, this.originalSlot)));
            dev.zonely.whiteeffect.nms.util.SafeInventoryUpdater.update(player);

            try { if (this.previousGameMode != null) player.setGameMode(this.previousGameMode); } catch (Throwable ignored) {}
            try {
                player.setAllowFlight(this.previousAllowFlight);
                if (this.previousAllowFlight) player.setFlying(this.previousFlying);
            } catch (Throwable ignored) {}

            if (this.appliedInvisibility) {
                try { player.removePotionEffect(PotionEffectType.INVISIBILITY); } catch (Throwable ignored) {}
            }
            if (this.previousInvisibility != null) {
                try { player.addPotionEffect(this.previousInvisibility); } catch (Throwable ignored) {}
            }

            if (this.originalScoreboard != null) {
                try { player.setScoreboard(this.originalScoreboard); } catch (Throwable ignored) {}
            } else if (this.replayScoreboard != null) {
                try {
                    ScoreboardManager managerObj = Bukkit.getScoreboardManager();
                    if (managerObj != null) player.setScoreboard(managerObj.getMainScoreboard());
                } catch (Throwable ignored) {}
            }

            if (manager != null) manager.showToOthers(player, this);
        }

        private static ItemStack[] cloneItems(ItemStack[] items) {
            if (items == null) return null;
            ItemStack[] clone = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) {
                ItemStack stack = items[i];
                clone[i] = stack == null ? null : stack.clone();
            }
            return clone;
        }

        private static ItemStack getOffHandItem(PlayerInventory inventory) {
            try {
                Method getter = PlayerInventory.class.getMethod("getItemInOffHand");
                ItemStack offHand = (ItemStack) getter.invoke(inventory);
                return offHand == null ? null : offHand.clone();
            } catch (Throwable ignored) { return null; }
        }

        private static void setOffHandItem(PlayerInventory inventory, ItemStack item) {
            try {
                Method setter = PlayerInventory.class.getMethod("setItemInOffHand", ItemStack.class);
                setter.invoke(inventory, item);
            } catch (Throwable ignored) {}
        }

        private static PotionEffect captureInvisibility(Player player) {
            if (player == null) return null;
            try {
                Method getter = Player.class.getMethod("getPotionEffect", PotionEffectType.class);
                Object effect = getter.invoke(player, PotionEffectType.INVISIBILITY);
                if (effect instanceof PotionEffect) return cloneEffect((PotionEffect) effect);
            } catch (Throwable ignored) {}
            try {
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    if (effect != null && PotionEffectType.INVISIBILITY.equals(effect.getType()))
                        return cloneEffect(effect);
                }
            } catch (Throwable ignored) {}
            return null;
        }

        private static PotionEffect cloneEffect(PotionEffect effect) {
            if (effect == null) return null;
            try {
                Method clone = PotionEffect.class.getMethod("clone");
                Object cloned = clone.invoke(effect);
                if (cloned instanceof PotionEffect) return (PotionEffect) cloned;
            } catch (Throwable ignored) {}

            PotionEffectType type = effect.getType();
            int duration = effect.getDuration();
            int amplifier = effect.getAmplifier();
            boolean ambient = callBooleanMethod(effect, "isAmbient", false);
            boolean particles = callBooleanMethod(effect, "hasParticles", true);
            boolean icon = callBooleanMethod(effect, "hasIcon", true);
            try {
                Constructor<PotionEffect> ctor = PotionEffect.class.getConstructor(PotionEffectType.class, int.class, int.class, boolean.class, boolean.class, boolean.class);
                return ctor.newInstance(type, duration, amplifier, ambient, particles, icon);
            } catch (Throwable ignored) {}
            try {
                Constructor<PotionEffect> ctor = PotionEffect.class.getConstructor(PotionEffectType.class, int.class, int.class, boolean.class, boolean.class);
                return ctor.newInstance(type, duration, amplifier, ambient, particles);
            } catch (Throwable ignored) {}
            try {
                Constructor<PotionEffect> ctor = PotionEffect.class.getConstructor(PotionEffectType.class, int.class, int.class, boolean.class);
                return ctor.newInstance(type, duration, amplifier, ambient);
            } catch (Throwable ignored) {}
            try {
                Constructor<PotionEffect> ctor = PotionEffect.class.getConstructor(PotionEffectType.class, int.class, int.class);
                return ctor.newInstance(type, duration, amplifier);
            } catch (Throwable ignored) {}
            return new PotionEffect(type, duration, amplifier);
        }

        private static boolean callBooleanMethod(PotionEffect effect, String method, boolean fallback) {
            try {
                Method m = PotionEffect.class.getMethod(method);
                Object result = m.invoke(effect);
                if (result instanceof Boolean) return (Boolean) result;
            } catch (Throwable ignored) {}
            return fallback;
        }
    }
}
