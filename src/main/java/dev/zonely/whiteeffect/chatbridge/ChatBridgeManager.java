package dev.zonely.whiteeffect.chatbridge;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.Manager;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.database.HikariDatabase;
import dev.zonely.whiteeffect.database.MySQLDatabase;
import dev.zonely.whiteeffect.database.NullDatabase;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.utils.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.bukkit.event.server.ServerCommandEvent;

import javax.sql.rowset.CachedRowSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ChatBridgeManager implements Listener {

    private static final String CHANNEL_GLOBAL = "global";
    private static final String EVENT_CHAT = "CHAT";
    private static final String EVENT_COMMAND = "COMMAND";
    private static final String EVENT_JOIN = "JOIN";
    private static final String EVENT_QUIT = "QUIT";
    private static final DateTimeFormatter DAY_KEY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> SENSITIVE_COMMANDS = new HashSet<>(Arrays.asList(
            "login", "l", "register", "reg", "email", "mail", "twofactor", "2fa", "passwordreset"
    ));

    private final Core plugin;
    private final Database database;
    private final Logger logger;
    private final String serverId;
    private final boolean enabled;
    private final boolean sqlBackend;
    private final int pollIntervalTicks;
    private final int queueBatchSize;
    private final long retentionMillis;
    private final AtomicBoolean polling = new AtomicBoolean(false);

    private final boolean activityEnabled;
    private final int activityFlushIntervalTicks;
    private final long activityRetentionMillis;
    private final boolean trackChatActivity;
    private final boolean trackCommandActivity;
    private final ZoneId activityZoneId;
    private final ConcurrentHashMap<UUID, Long> sessionLastTick = new ConcurrentHashMap<>();

    private int pollTaskId = -1;
    private int cleanupTaskId = -1;
    private int activityTaskId = -1;

    public ChatBridgeManager(Core plugin) {
        this.plugin = plugin;
        this.database = Database.getInstance();
        this.logger = plugin.getLogger();

        this.sqlBackend = (database instanceof MySQLDatabase) || (database instanceof HikariDatabase);
        boolean configEnabled = plugin.getConfig().getBoolean("chat-bridge.enabled", true);
        this.serverId = sanitizeServerId(plugin.getConfig().getString("chat-bridge.server-id", Bukkit.getServer().getName()));

        int pollSeconds = Math.max(1, plugin.getConfig().getInt("chat-bridge.poll-interval-seconds", 2));
        this.pollIntervalTicks = Math.max(20, pollSeconds * 20);
        this.queueBatchSize = Math.max(1, plugin.getConfig().getInt("chat-bridge.command-batch-size", 25));
        int retentionDays = Math.max(0, plugin.getConfig().getInt("chat-bridge.retention-days", 7));
        this.retentionMillis = retentionDays <= 0 ? -1L : retentionDays * 24L * 60L * 60L * 1000L;

        if (!configEnabled) {
            this.logger.info("[ChatBridge] Disabled via configuration.");
            this.enabled = false;
        } else if (!this.sqlBackend || database instanceof NullDatabase) {
            this.logger.warning("[ChatBridge] No SQL backend available. Chat bridge features are disabled.");
            this.enabled = false;
        } else {
            this.enabled = true;
        }

        this.activityEnabled = this.enabled && plugin.getConfig().getBoolean("chat-bridge.activity.enabled", true);
        int flushSeconds = Math.max(10, plugin.getConfig().getInt("chat-bridge.activity.flush-interval-seconds", 60));
        this.activityFlushIntervalTicks = Math.max(20, flushSeconds * 20);
        int activityRetentionDays = Math.max(1, plugin.getConfig().getInt("chat-bridge.activity.retention-days", 30));
        this.activityRetentionMillis = activityRetentionDays * 24L * 60L * 60L * 1000L;
        this.trackChatActivity = plugin.getConfig().getBoolean("chat-bridge.activity.track-chat", true);
        this.trackCommandActivity = plugin.getConfig().getBoolean("chat-bridge.activity.track-commands", true);
        this.activityZoneId = ZoneId.systemDefault();
    }

    public void start() {
        if (!enabled) {
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
        resetOnlineState();
        syncOnlineSnapshot();
        schedulePoller();
        scheduleCleanup();
        scheduleActivityFlush();

        logger.info(String.format(Locale.ROOT, "[ChatBridge] Online chat bridge active for server '%s'.", serverId));
        if (activityEnabled) {
            logger.info(String.format(Locale.ROOT, "[ChatBridge] Player activity tracking enabled (flush=%ds, retention=%dd).",
                    Math.max(1, activityFlushIntervalTicks / 20),
                    Math.max(1L, activityRetentionMillis / (24L * 60L * 60L * 1000L))));
        }
    }

    public void shutdown() {
        if (!enabled) {
            return;
        }

        HandlerList.unregisterAll(this);
        if (pollTaskId != -1) {
            Bukkit.getScheduler().cancelTask(pollTaskId);
            pollTaskId = -1;
        }
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId);
            cleanupTaskId = -1;
        }
        if (activityTaskId != -1) {
            Bukkit.getScheduler().cancelTask(activityTaskId);
            activityTaskId = -1;
        }
        polling.set(false);

        if (activityEnabled) {
            flushOnlineActivity(true);
            sessionLastTick.clear();
        }

        updateSync("UPDATE `ZonelyCoreOnlinePlayers` SET `online` = 0, `last_seen` = ?, `server` = ? WHERE `server` = ?",
                System.currentTimeMillis(), serverId, serverId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!enabled) {
            return;
        }

        Player player = event.getPlayer();
        String rawMessage = sanitizeMessage(event.getMessage());
        String coloredMessage = StringUtils.formatColors(rawMessage);
        String displayComponent = buildChatDisplayName(player.getDisplayName());

        insertChatLog(
                CHANNEL_GLOBAL,
                player.getName(),
                player.getUniqueId().toString(),
                null,
                null,
                displayComponent,
                rawMessage,
                coloredMessage
        );

        if (activityEnabled && trackChatActivity) {
            recordChatActivity(player, rawMessage, System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) {
            return;
        }
        upsertOnlinePlayer(event.getPlayer(), true);
        if (activityEnabled) {
            markPlayerJoin(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!enabled) {
            return;
        }
        if (activityEnabled) {
            markPlayerQuit(event.getPlayer());
        }
        markOffline(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled) {
            return;
        }

        String message = sanitizeMessage(event.getMessage());
        if (message.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();
        String displayComponent = buildChatDisplayName(player.getDisplayName());

        insertChatLog(
                CHANNEL_GLOBAL,
                player.getName(),
                player.getUniqueId().toString(),
                null,
                null,
                displayComponent,
                message,
                message
        );

        if (activityEnabled && trackCommandActivity) {
            recordCommandActivity(player, message, System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (!enabled) {
            return;
        }

        String command = sanitizeMessage(event.getCommand());
        if (command.isEmpty()) {
            return;
        }

        String source = "CONSOLE";
        String displayComponent = "\u00A77<\u00A7cConsole\u00A77>";

        String lower = command.toLowerCase(Locale.ROOT);
        if (lower.startsWith("say ") || lower.equals("say") || lower.startsWith("minecraft:say ")) {
            String message = command.contains(" ") ? command.substring(command.indexOf(' ') + 1) : "";
            message = sanitizeMessage(message);
            if (!message.isEmpty()) {
                insertChatLog(
                        CHANNEL_GLOBAL,
                        source,
                        null,
                        null,
                        null,
                        displayComponent,
                        message,
                        StringUtils.formatColors(message)
                );
            }
            return;
        }

        if (!command.startsWith("/")) {
            command = "/" + command;
        }

        insertChatLog(
                CHANNEL_GLOBAL,
                source,
                null,
                null,
                null,
                displayComponent,
                command,
                command
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBroadcastMessage(BroadcastMessageEvent event) {
        if (!enabled) {
            return;
        }
        String message = sanitizeMessage(event.getMessage());
        if (message.isEmpty()) {
            return;
        }

        insertChatLog(
                CHANNEL_GLOBAL,
                "BROADCAST",
                null,
                null,
                null,
                "\u00A77[\u00A7aServer\u00A77]",
                message,
                StringUtils.formatColors(message)
        );
    }

    private void schedulePoller() {
        pollTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pollQueue, pollIntervalTicks, pollIntervalTicks).getTaskId();
    }

    private void scheduleCleanup() {
        if (retentionMillis <= 0 && (!activityEnabled || activityRetentionMillis <= 0)) {
            return;
        }
        long intervalTicks = 20L * 60L * 30L;
        cleanupTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupHistory, intervalTicks, intervalTicks).getTaskId();
    }

    private void scheduleActivityFlush() {
        if (!activityEnabled) {
            return;
        }
        activityTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> flushOnlineActivity(false),
                activityFlushIntervalTicks, activityFlushIntervalTicks).getTaskId();
    }

    private void pollQueue() {
        if (!enabled || !polling.compareAndSet(false, true)) {
            return;
        }

        try {
            List<PendingCommand> commands = loadPendingCommands();
            if (commands.isEmpty()) {
                return;
            }

            long now = System.currentTimeMillis();
            for (PendingCommand command : commands) {
                updateSync("UPDATE `ZonelyCoreChatQueue` SET `status` = 'PROCESSING', `processed_at` = ? WHERE `id` = ? AND `status` = 'PENDING'",
                        now, command.id);
                Bukkit.getScheduler().runTask(plugin, () -> executeCommand(command));
            }
        } finally {
            polling.set(false);
        }
    }

    private void executeCommand(PendingCommand command) {
        boolean success = false;
        String result = "UNKNOWN";

        try {
            if ("COMMAND".equalsIgnoreCase(command.type)) {
                success = dispatchServerCommand(command);
                result = success ? "Executed" : "Command returned false";
            } else if ("CHAT".equalsIgnoreCase(command.type)) {
                success = dispatchChatMessage(command);
                result = success ? "Broadcast" : "Blocked";
            } else {
                result = "Unknown command type";
                logger.warning("[ChatBridge] Skipping unknown queue type: " + command.type);
            }
        } catch (Throwable throwable) {
            success = false;
            result = "Exception: " + throwable.getMessage();
            logger.log(Level.WARNING, "[ChatBridge] Failed to execute queued entry id=" + command.id, throwable);
        }

        final boolean finalSuccess = success;
        final String finalResult = truncateResult(result);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> finalizeCommand(command.id, finalSuccess, finalResult));
    }

    private boolean dispatchServerCommand(PendingCommand command) {
        if (command.requiresOp && !hasOpPermission(command.sender)) {
            finalizeCommand(command.id, false, "Sender is not OP");
            return false;
        }

        String payload = sanitizeMessage(command.payload);
        if (payload.isEmpty()) {
            return false;
        }

        String consoleCommand = payload.startsWith("/") ? payload.substring(1) : payload;
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
    }

    private boolean dispatchChatMessage(PendingCommand command) {
        String senderLabel = command.sender != null && !command.sender.trim().isEmpty()
                ? command.sender.trim()
                : "Web";
        String payload = sanitizeMessage(command.payload);
        if (payload.isEmpty()) {
            return false;
        }

        String coloredPayload = StringUtils.formatColors(payload);
        String displayName = StringUtils.formatColors("&3[Web]&7 " + senderLabel);
        String broadcast = displayName + StringUtils.formatColors("&7: &f") + coloredPayload;

        Bukkit.broadcastMessage(broadcast);
        insertChatLog(
                CHANNEL_GLOBAL,
                "WEB:" + senderLabel,
                null,
                "WEB",
                StringUtils.formatColors("&3[Web]&7"),
                displayName,
                payload,
                coloredPayload
        );
        return true;
    }

    private boolean hasOpPermission(String sender) {
        if (sender == null || sender.trim().isEmpty()) {
            return false;
        }

        Player online = Bukkit.getPlayerExact(sender);
        if (online != null) {
            return online.isOp();
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(sender);
        return offline != null && offline.isOp();
    }

    private void finalizeCommand(long id, boolean success, String result) {
        String status = success ? "DONE" : "FAILED";
        updateSync("UPDATE `ZonelyCoreChatQueue` SET `status` = ?, `result_message` = ?, `processed_at` = ? WHERE `id` = ?",
                status,
                result,
                System.currentTimeMillis(),
                id);
    }

    private List<PendingCommand> loadPendingCommands() {
        List<PendingCommand> commands = new ArrayList<>();
        CachedRowSet rowSet = query("SELECT `id`, `type`, `payload`, `sender`, `requires_op` FROM `ZonelyCoreChatQueue` WHERE `server` = ? AND `status` = 'PENDING' ORDER BY `id` ASC LIMIT ?",
                serverId,
                queueBatchSize);
        if (rowSet == null) {
            return commands;
        }

        try {
            while (rowSet.next()) {
                PendingCommand command = new PendingCommand(
                        rowSet.getLong("id"),
                        rowSet.getString("type"),
                        rowSet.getString("payload"),
                        rowSet.getString("sender"),
                        readBoolean(rowSet, "requires_op")
                );
                commands.add(command);
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[ChatBridge] Failed to fetch pending commands.", ex);
        } finally {
            try {
                rowSet.close();
            } catch (Exception ignore) {
            }
        }
        return commands;
    }

    private boolean readBoolean(CachedRowSet rowSet, String column) throws SQLException {
        Object value = rowSet.getObject(column);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return false;
        }
        return text.equalsIgnoreCase("true")
                || text.equalsIgnoreCase("yes")
                || text.equalsIgnoreCase("y")
                || text.equals("1");
    }

    private void insertChatLog(String channel,
                               String source,
                               String uuid,
                               String roleKey,
                               String rolePrefix,
                               String displayName,
                               String plainMessage,
                               String coloredMessage) {

        executeAsync(
                "INSERT INTO `ZonelyCoreChatLog` (`server`, `channel`, `source`, `uuid`, `role_key`, `role_prefix`, `display_name`, `message_plain`, `message_colored`, `meta_json`, `created_at`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                serverId,
                channel,
                source,
                uuid,
                roleKey,
                rolePrefix,
                displayName,
                plainMessage,
                coloredMessage,
                null,
                System.currentTimeMillis()
        );
    }

    private void resetOnlineState() {
        updateSync("UPDATE `ZonelyCoreOnlinePlayers` SET `online` = 0, `last_seen` = ?, `server` = ? WHERE `server` = ?",
                System.currentTimeMillis(), serverId, serverId);
    }

    private void syncOnlineSnapshot() {
        long now = System.currentTimeMillis();
        Bukkit.getOnlinePlayers().forEach(player -> {
            upsertOnlinePlayer(player, true);
            if (activityEnabled) {
                sessionLastTick.put(player.getUniqueId(), now);
                recordDailyActivity(player, now, 0L, 0, 0, 0L, player.getLocation());
                updateActivitySummary(player, now, player.getLocation(), 0L, 0L, null, null, 0L, 0L);
            }
        });
    }

    private void upsertOnlinePlayer(Player player, boolean online) {
        Role role = Role.getPlayerRole(player);
        String current = Manager.getCurrent(player.getName());
        String prefix = role != null ? role.getPrefix() : "&7";
        String displayName = StringUtils.formatColors(prefix + current);
        String roleKey = role != null ? StringUtils.stripColors(role.getName()) : null;
        String rolePrefix = role != null ? role.getPrefix() : prefix;

        executeAsync(
                "INSERT INTO `ZonelyCoreOnlinePlayers` (`uuid`, `name`, `display_name`, `server`, `role_key`, `role_prefix`, `is_op`, `online`, `last_seen`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `display_name` = VALUES(`display_name`), `server` = VALUES(`server`), `role_key` = VALUES(`role_key`), `role_prefix` = VALUES(`role_prefix`), `is_op` = VALUES(`is_op`), `online` = VALUES(`online`), `last_seen` = VALUES(`last_seen`)",
                player.getUniqueId().toString(),
                player.getName(),
                displayName,
                serverId,
                roleKey,
                rolePrefix,
                player.isOp() ? 1 : 0,
                online ? 1 : 0,
                System.currentTimeMillis()
        );
    }

    private void markOffline(Player player) {
        updateSync(
                "UPDATE `ZonelyCoreOnlinePlayers` SET `online` = 0, `last_seen` = ?, `server` = ? WHERE `uuid` = ?",
                System.currentTimeMillis(),
                serverId,
                player.getUniqueId().toString()
        );
    }

    private void markPlayerJoin(Player player) {
        long now = System.currentTimeMillis();
        sessionLastTick.put(player.getUniqueId(), now);
        Location location = player.getLocation();
        recordDailyActivity(player, now, 0L, 0, 0, 0L, location);
        updateActivitySummary(player, now, location, now, 0L, null, null, 0L, 0L);
        insertRecentActivityEvent(player, EVENT_JOIN, null, now, location);
    }

    private void markPlayerQuit(Player player) {
        long now = System.currentTimeMillis();
        Location location = player.getLocation();
        flushPlayerOnlineDuration(player, now, now, location);
        sessionLastTick.remove(player.getUniqueId());

        recordDailyActivity(player, now, 0L, 0, 0, now, location);
        updateActivitySummary(player, now, location, 0L, now, null, null, 0L, 0L);
        insertRecentActivityEvent(player, EVENT_QUIT, null, now, location);
    }

    private void recordChatActivity(Player player, String message, long now) {
        String clean = truncateMessage(message, 512);
        if (clean.isEmpty()) {
            return;
        }

        recordDailyActivity(player, now, 0L, 1, 0, 0L, null);
        updateActivitySummary(player, now, null, 0L, 0L, clean, null, 1L, 0L);
        insertRecentActivityEvent(player, EVENT_CHAT, clean, now, null);
    }

    private void recordCommandActivity(Player player, String commandLine, long now) {
        String clean = sanitizeTrackedCommand(commandLine);
        if (clean.isEmpty()) {
            return;
        }
        Location location = player.getLocation();
        recordDailyActivity(player, now, 0L, 0, 1, 0L, location);
        updateActivitySummary(player, now, location, 0L, 0L, null, clean, 0L, 1L);
        insertRecentActivityEvent(player, EVENT_COMMAND, clean, now, location);
    }

    private void flushOnlineActivity(boolean markQuit) {
        if (!activityEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            long quitAt = markQuit ? now : 0L;
            flushPlayerOnlineDuration(player, now, quitAt, player.getLocation());
        }
    }

    private void flushPlayerOnlineDuration(Player player, long now, long quitAt, Location location) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Long previous = sessionLastTick.get(uuid);
        if (previous == null) {
            sessionLastTick.put(uuid, now);
            updateActivitySummary(player, now, location, 0L, quitAt, null, null, 0L, 0L);
            return;
        }
        if (now <= previous) {
            if (quitAt > 0L) {
                updateActivitySummary(player, now, location, 0L, quitAt, null, null, 0L, 0L);
            }
            return;
        }

        sessionLastTick.put(uuid, now);
        recordOnlineDuration(player, previous, now, quitAt, location);
    }

    private void recordOnlineDuration(Player player, long fromMillis, long toMillis, long quitAt, Location location) {
        if (toMillis <= fromMillis) {
            return;
        }

        long cursor = fromMillis;
        while (cursor < toMillis) {
            long nextDayStart = startOfNextDay(cursor);
            long sliceEnd = Math.min(nextDayStart, toMillis);
            long deltaMillis = sliceEnd - cursor;
            if (deltaMillis > 0L) {
                long rowQuitAt = (quitAt > 0L && sliceEnd == toMillis) ? quitAt : 0L;
                recordDailyActivity(player, sliceEnd, deltaMillis, 0, 0, rowQuitAt, location);
            }
            cursor = sliceEnd;
        }

        updateActivitySummary(player, toMillis, location, 0L, quitAt, null, null, 0L, 0L);
    }

    private void recordDailyActivity(Player player, long activityAt, long onlineMillisDelta,
                                     int messageDelta, int commandDelta, long quitAt, Location location) {
        if (!activityEnabled || player == null) {
            return;
        }
        LocationSnapshot snapshot = LocationSnapshot.from(location);
        String dayKey = dayKey(activityAt);

        executeAsync(
                "INSERT INTO `ZonelyCorePlayerActivityDaily` (`uuid`, `name`, `date_key`, `online_millis`, `message_count`, `command_count`, `last_active_at`, `last_server`, `last_world`, `last_x`, `last_y`, `last_z`, `last_yaw`, `last_pitch`, `last_quit_at`) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "`name` = VALUES(`name`), " +
                        "`online_millis` = `online_millis` + VALUES(`online_millis`), " +
                        "`message_count` = `message_count` + VALUES(`message_count`), " +
                        "`command_count` = `command_count` + VALUES(`command_count`), " +
                        "`last_active_at` = GREATEST(`last_active_at`, VALUES(`last_active_at`)), " +
                        "`last_server` = VALUES(`last_server`), " +
                        "`last_world` = COALESCE(VALUES(`last_world`), `last_world`), " +
                        "`last_x` = IFNULL(VALUES(`last_x`), `last_x`), " +
                        "`last_y` = IFNULL(VALUES(`last_y`), `last_y`), " +
                        "`last_z` = IFNULL(VALUES(`last_z`), `last_z`), " +
                        "`last_yaw` = IFNULL(VALUES(`last_yaw`), `last_yaw`), " +
                        "`last_pitch` = IFNULL(VALUES(`last_pitch`), `last_pitch`), " +
                        "`last_quit_at` = IF(VALUES(`last_quit_at`) > 0, VALUES(`last_quit_at`), `last_quit_at`)",
                player.getUniqueId().toString(),
                truncateValue(player.getName(), 64),
                dayKey,
                Math.max(0L, onlineMillisDelta),
                Math.max(0, messageDelta),
                Math.max(0, commandDelta),
                activityAt,
                serverId,
                snapshot.world,
                snapshot.x,
                snapshot.y,
                snapshot.z,
                snapshot.yaw,
                snapshot.pitch,
                Math.max(0L, quitAt)
        );
    }

    private void updateActivitySummary(Player player, long now, Location location,
                                       long joinAt, long quitAt,
                                       String lastChatMessage, String lastCommand,
                                       long totalMessageDelta, long totalCommandDelta) {
        if (!activityEnabled || player == null) {
            return;
        }
        LocationSnapshot snapshot = LocationSnapshot.from(location);
        String normalizedChat = lastChatMessage == null ? null : truncateMessage(lastChatMessage, 512);
        String normalizedCommand = lastCommand == null ? null : truncateMessage(lastCommand, 256);

        executeAsync(
                "INSERT INTO `ZonelyCorePlayerActivitySummary` (`uuid`, `name`, `last_server`, `last_world`, `last_x`, `last_y`, `last_z`, `last_yaw`, `last_pitch`, `last_seen_at`, `last_join_at`, `last_quit_at`, `last_chat_message`, `last_command`, `total_messages`, `total_commands`, `updated_at`) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "`name` = VALUES(`name`), " +
                        "`last_server` = VALUES(`last_server`), " +
                        "`last_world` = COALESCE(VALUES(`last_world`), `last_world`), " +
                        "`last_x` = IFNULL(VALUES(`last_x`), `last_x`), " +
                        "`last_y` = IFNULL(VALUES(`last_y`), `last_y`), " +
                        "`last_z` = IFNULL(VALUES(`last_z`), `last_z`), " +
                        "`last_yaw` = IFNULL(VALUES(`last_yaw`), `last_yaw`), " +
                        "`last_pitch` = IFNULL(VALUES(`last_pitch`), `last_pitch`), " +
                        "`last_seen_at` = GREATEST(`last_seen_at`, VALUES(`last_seen_at`)), " +
                        "`last_join_at` = IF(VALUES(`last_join_at`) > 0, VALUES(`last_join_at`), `last_join_at`), " +
                        "`last_quit_at` = IF(VALUES(`last_quit_at`) > 0, VALUES(`last_quit_at`), `last_quit_at`), " +
                        "`last_chat_message` = COALESCE(VALUES(`last_chat_message`), `last_chat_message`), " +
                        "`last_command` = COALESCE(VALUES(`last_command`), `last_command`), " +
                        "`total_messages` = `total_messages` + VALUES(`total_messages`), " +
                        "`total_commands` = `total_commands` + VALUES(`total_commands`), " +
                        "`updated_at` = VALUES(`updated_at`)",
                player.getUniqueId().toString(),
                truncateValue(player.getName(), 64),
                serverId,
                snapshot.world,
                snapshot.x,
                snapshot.y,
                snapshot.z,
                snapshot.yaw,
                snapshot.pitch,
                now,
                Math.max(0L, joinAt),
                Math.max(0L, quitAt),
                normalizedChat,
                normalizedCommand,
                Math.max(0L, totalMessageDelta),
                Math.max(0L, totalCommandDelta),
                now
        );
    }

    private void insertRecentActivityEvent(Player player, String type, String content, long createdAt, Location location) {
        if (!activityEnabled || player == null || type == null || type.isEmpty()) {
            return;
        }
        LocationSnapshot snapshot = LocationSnapshot.from(location);
        executeAsync(
                "INSERT INTO `ZonelyCorePlayerActivityRecent` (`uuid`, `name`, `server`, `event_type`, `content`, `world`, `x`, `y`, `z`, `yaw`, `pitch`, `created_at`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                player.getUniqueId().toString(),
                truncateValue(player.getName(), 64),
                serverId,
                truncateValue(type.toUpperCase(Locale.ROOT), 16),
                content == null ? null : truncateMessage(content, 512),
                snapshot.world,
                snapshot.x,
                snapshot.y,
                snapshot.z,
                snapshot.yaw,
                snapshot.pitch,
                createdAt
        );
    }

    private String dayKey(long millis) {
        return DAY_KEY_FORMAT.format(Instant.ofEpochMilli(millis).atZone(activityZoneId).toLocalDate());
    }

    private long startOfNextDay(long millis) {
        ZonedDateTime dateTime = Instant.ofEpochMilli(millis).atZone(activityZoneId);
        ZonedDateTime next = dateTime.toLocalDate().plusDays(1).atStartOfDay(activityZoneId);
        return next.toInstant().toEpochMilli();
    }

    private void cleanupHistory() {
        long now = System.currentTimeMillis();
        if (retentionMillis > 0) {
            long chatThreshold = now - retentionMillis;
            executeAsync("DELETE FROM `ZonelyCoreChatLog` WHERE `created_at` < ?", chatThreshold);
            executeAsync("DELETE FROM `ZonelyCoreChatQueue` WHERE `processed_at` > 0 AND `processed_at` < ?", chatThreshold);
        }

        if (activityEnabled && activityRetentionMillis > 0) {
            long activityThreshold = now - activityRetentionMillis;
            executeAsync("DELETE FROM `ZonelyCorePlayerActivityRecent` WHERE `created_at` < ?", activityThreshold);
            executeAsync("DELETE FROM `ZonelyCorePlayerActivityDaily` WHERE `last_active_at` > 0 AND `last_active_at` < ?", activityThreshold);
        }
    }

    private CachedRowSet query(String sql, Object... params) {
        try {
            if (database instanceof MySQLDatabase) {
                return ((MySQLDatabase) database).query(sql, params);
            }
            if (database instanceof HikariDatabase) {
                return ((HikariDatabase) database).query(sql, params);
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[ChatBridge] Query failed: " + sql, ex);
        }
        return null;
    }

    private void executeAsync(String sql, Object... params) {
        if (database instanceof MySQLDatabase) {
            ((MySQLDatabase) database).execute(sql, params);
        } else if (database instanceof HikariDatabase) {
            ((HikariDatabase) database).execute(sql, params);
        }
    }

    private void updateSync(String sql, Object... params) {
        if (database instanceof MySQLDatabase) {
            ((MySQLDatabase) database).update(sql, params);
        } else if (database instanceof HikariDatabase) {
            ((HikariDatabase) database).update(sql, params);
        }
    }

    private static String sanitizeServerId(String input) {
        String value = input != null ? input.trim() : "";
        if (value.isEmpty()) {
            String fallback = Bukkit.getServer().getName();
            if (fallback != null && !fallback.trim().isEmpty()) {
                value = fallback.trim();
            }
        }
        if (value.isEmpty()) {
            value = "default";
        }
        if (value.length() > 64) {
            value = value.substring(0, 64);
        }
        return value;
    }

    private static String sanitizeMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String truncateResult(String input) {
        if (input == null) {
            return "";
        }
        return input.length() > 512 ? input.substring(0, 512) : input;
    }

    private static String truncateMessage(String input, int maxLength) {
        String sanitized = sanitizeMessage(input);
        if (sanitized.length() > maxLength) {
            return sanitized.substring(0, maxLength);
        }
        return sanitized;
    }

    private static String truncateValue(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        String value = input.trim();
        if (value.length() > maxLength) {
            return value.substring(0, maxLength);
        }
        return value;
    }

    private static String sanitizeTrackedCommand(String commandLine) {
        String command = sanitizeMessage(commandLine);
        if (command.isEmpty()) {
            return "";
        }
        if (!command.startsWith("/")) {
            command = "/" + command;
        }

        String body = command.substring(1).trim();
        if (body.isEmpty()) {
            return truncateMessage(command, 256);
        }

        String[] parts = body.split("\\s+");
        if (parts.length == 0) {
            return truncateMessage(command, 256);
        }
        String label = parts[0].toLowerCase(Locale.ROOT);
        if (SENSITIVE_COMMANDS.contains(label)) {
            return "/" + parts[0] + " ********";
        }
        return truncateMessage(command, 256);
    }

    private String buildChatDisplayName(String display) {
        String shown = display != null && !display.isEmpty() ? display : "Player";
        return StringUtils.formatColors("\u00A77<" + shown + "\u00A77>");
    }

    private static final class LocationSnapshot {
        private final String world;
        private final Double x;
        private final Double y;
        private final Double z;
        private final Float yaw;
        private final Float pitch;

        private LocationSnapshot(String world, Double x, Double y, Double z, Float yaw, Float pitch) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        private static LocationSnapshot from(Location location) {
            if (location == null || location.getWorld() == null) {
                return new LocationSnapshot(null, null, null, null, null, null);
            }
            return new LocationSnapshot(
                    truncate(location.getWorld().getName(), 64),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw(),
                    location.getPitch()
            );
        }

        private static String truncate(String value, int maxLength) {
            if (value == null) {
                return null;
            }
            if (value.length() <= maxLength) {
                return value;
            }
            return value.substring(0, maxLength);
        }
    }

    private static final class PendingCommand {
        private final long id;
        private final String type;
        private final String payload;
        private final String sender;
        private final boolean requiresOp;

        private PendingCommand(long id, String type, String payload, String sender, boolean requiresOp) {
            this.id = id;
            this.type = type != null ? type : "COMMAND";
            this.payload = payload != null ? payload : "";
            this.sender = sender;
            this.requiresOp = requiresOp;
        }
    }

}
