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
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.BroadcastMessageEvent;

import javax.sql.rowset.CachedRowSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ChatBridgeManager implements Listener {

    private static final String CHANNEL_GLOBAL = "global";

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

    private int pollTaskId = -1;
    private int cleanupTaskId = -1;

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

        logger.info(String.format(Locale.ROOT, "[ChatBridge] Online chat bridge active for server '%s'.", serverId));
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
        polling.set(false);

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
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) {
            return;
        }
        upsertOnlinePlayer(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!enabled) {
            return;
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
        String displayComponent = "§7<§cConsole§7>";

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
                "§7[§aServer§7]",
                message,
                StringUtils.formatColors(message)
        );
    }

    private void schedulePoller() {
        pollTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pollQueue, pollIntervalTicks, pollIntervalTicks).getTaskId();
    }

    private void scheduleCleanup() {
        if (retentionMillis <= 0) {
            return;
        }
        long intervalTicks = 20L * 60L * 30L;
        cleanupTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupHistory, intervalTicks, intervalTicks).getTaskId();
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
        Bukkit.getOnlinePlayers().forEach(player -> upsertOnlinePlayer(player, true));
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

    private void cleanupHistory() {
        if (retentionMillis <= 0) {
            return;
        }
        long threshold = System.currentTimeMillis() - retentionMillis;
        executeAsync("DELETE FROM `ZonelyCoreChatLog` WHERE `created_at` < ?", threshold);
        executeAsync("DELETE FROM `ZonelyCoreChatQueue` WHERE `processed_at` > 0 AND `processed_at` < ?", threshold);
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

    private String buildChatDisplayName(String display) {
        String shown = display != null && !display.isEmpty() ? display : "Player";
        return StringUtils.formatColors("§7<" + shown + "§7>");
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
