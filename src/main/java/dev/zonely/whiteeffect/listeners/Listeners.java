package dev.zonely.whiteeffect.listeners;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.Manager;
import dev.zonely.whiteeffect.database.exception.ProfileLoadException;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.launcher.LauncherSessionService;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.enums.PrivateMessages;
import dev.zonely.whiteeffect.player.fake.FakeManager;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.plugin.logger.WLogger;
import dev.zonely.whiteeffect.reflection.Accessors;
import dev.zonely.whiteeffect.reflection.acessors.FieldAccessor;
import dev.zonely.whiteeffect.replay.advanced.AdvancedReplayListener;
import dev.zonely.whiteeffect.titles.TitleManager;
import dev.zonely.whiteeffect.utils.StringUtils;
import dev.zonely.whiteeffect.utils.ZonelyUpdater;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent.Action;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.ItemStack;
import org.spigotmc.WatchdogThread;

public class Listeners implements Listener {

    public static final WLogger LOGGER = ((WLogger) Core.getInstance().getLogger()).getModule("Listeners");
    public static final Map<String, Long> DELAY_PLAYERS = new HashMap<>();
    private static final Map<String, Long> PROTECTION_LOBBY = new HashMap<>();
    private static final String DEFAULT_PREFIX = "&3Lobby &8->> ";

    @SuppressWarnings({"rawtypes"})
    private static final FieldAccessor<Map> COMMAND_MAP;
    private static final SimpleCommandMap SIMPLE_COMMAND_MAP;
    private static FieldAccessor<WatchdogThread> RESTART_WATCHDOG;
    private static FieldAccessor<Boolean> RESTART_WATCHDOG_STOPPING;

    public static void setupListeners() {
        Bukkit.getPluginManager().registerEvents(new Listeners(), Core.getInstance());
        Bukkit.getPluginManager().registerEvents(new AdvancedReplayListener(), Core.getInstance());
        new ChatFilterListener(Core.getInstance().getPunishmentManager(), Core.getInstance().getConfig("config"));
    dev.zonely.whiteeffect.debug.InventoryDebug.install();
dev.zonely.whiteeffect.debug.CreativeGuard.install();
dev.zonely.whiteeffect.debug.InventoryUnlocker.install();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent evt) {
        if (evt.getAddress() != null) {
            String ip = evt.getAddress().getHostAddress();
            if (ip != null && !ip.isEmpty()) {
                Core.getInstance().getPunishmentManager().recordLastIp(evt.getName(), ip);
            }
        }
        if (evt.getLoginResult() == Result.ALLOWED) {
            try { Profile.createOrLoadProfile(evt.getName()); }
            catch (ProfileLoadException ex) {
                LOGGER.log(Level.SEVERE, "Failed to load profile for player " + evt.getName() + ": ", ex);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLoginLauncherGate(PlayerLoginEvent evt) {
        LauncherSessionService service = Core.getInstance().getLauncherSessionService();
        if (service == null || !service.isEnabled() || evt.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }

        String ip = evt.getAddress() != null ? evt.getAddress().getHostAddress() : "";
        if (service.isPlayerAuthorized(evt.getPlayer().getName(), ip)) {
            return;
        }

        Profile profile = Profile.getProfile(evt.getPlayer().getName());
        String prefix = LanguageManager.get(profile, "prefix.lobby", DEFAULT_PREFIX);
        String message = LanguageManager.get(
                profile,
                "listeners.launcher-auth.denied",
                "{prefix}&cYou must use the Zonely Launchpad to join this server.",
                "prefix", prefix);
        evt.disallow(PlayerLoginEvent.Result.KICK_OTHER, message);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLoginMonitor(PlayerLoginEvent evt) {
        Profile profile = Profile.getProfile(evt.getPlayer().getName());
        if (profile == null) {
            evt.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                    LanguageManager.get("listeners.general.profile-load-failed",
                            "&cThe server could not load your profile. Please wait a moment and try again."));
        } else profile.setPlayer(evt.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent evt) {
        Player player = evt.getPlayer();
        try { dev.zonely.whiteeffect.nms.universal.entity.PacketNPCManager.resendFor(player); } catch (Throwable ignored) {}

        if (player.hasPermission("zcore.admin")
                && ZonelyUpdater.UPDATER != null
                && ZonelyUpdater.UPDATER.canDownload) {
            Profile profile = Profile.getProfile(player.getName());
            String prefix = LanguageManager.get(profile, "prefix.lobby", DEFAULT_PREFIX);

            TextComponent component = new TextComponent("");
            String message = LanguageManager.get(profile,
                    "listeners.general.update.message",
                    "{prefix}&eA new update is available for &6ZonelyCore &b{version}&e. ",
                    "prefix", prefix,
                    "version", ZonelyUpdater.getVersion(9));
            addComponents(component, TextComponent.fromLegacyText(message));

            ClickEvent clickEvent = new ClickEvent(Action.RUN_COMMAND, "/zc guncelle");
            HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    TextComponent.fromLegacyText(LanguageManager.get(profile,
                            "listeners.general.update.button-hover",
                            "&7Click to download and apply the update.")));

            String buttonLabel = LanguageManager.get(profile,
                    "listeners.general.update.button-label",
                    "&a[ACCEPT]");
            addComponentsWithEvents(component, TextComponent.fromLegacyText(buttonLabel), clickEvent, hoverEvent);

            String trailing = LanguageManager.get(profile,
                    "listeners.general.update.trailing",
                    "&7.\n ");
            addComponents(component, TextComponent.fromLegacyText(trailing));

            player.spigot().sendMessage(component);
            EnumSound.LEVEL_UP.play(player, 1.0F, 1.0F);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent evt) {
        try { dev.zonely.whiteeffect.nms.universal.entity.PacketNPCManager.resendFor(evt.getPlayer()); } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent evt) {
        Profile profile = Profile.unloadProfile(evt.getPlayer().getName());
        if (profile != null) {
            TitleManager.leaveServer(profile);
            boolean canAsyncSave = true;
            try {
                if (RESTART_WATCHDOG != null && RESTART_WATCHDOG_STOPPING != null) {
                    Object watchdog = RESTART_WATCHDOG.get((Object) null);
                    Boolean stopping = RESTART_WATCHDOG_STOPPING.get(watchdog);
                    canAsyncSave = Boolean.FALSE.equals(stopping);
                }
            } catch (Throwable ignored) {}
            if (canAsyncSave) profile.save();
            else {
                profile.saveSync();
                Core.getInstance().getLogger().info("Saved " + profile.getName());
            }
            profile.destroy();
        }

        FakeManager.fakeNames.remove(evt.getPlayer().getName());
        FakeManager.fakeRoles.remove(evt.getPlayer().getName());
        FakeManager.fakeSkins.remove(evt.getPlayer().getName());
        DELAY_PLAYERS.remove(evt.getPlayer().getName());
        PROTECTION_LOBBY.remove(evt.getPlayer().getName().toLowerCase(Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent evt) {
        if (evt.isCancelled()) return;

        Player player = evt.getPlayer();
        String current = Manager.getCurrent(player.getName());
        Role role = Role.getPlayerRole(player);
        String prefix = role != null ? role.getPrefix() : "";
        String coloredPlayer = StringUtils.formatColors(prefix + current);

        String format = evt.getFormat();
        if (format.contains("%1$s")) {
            format = format.replace("%1$s", coloredPlayer);
        } else {
            format = format.replaceFirst("%s", Matcher.quoteReplacement(coloredPlayer));
        }
        evt.setFormat(format);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent evt) {
        if (evt.isCancelled()) return;
        Player player = evt.getPlayer();
        Profile senderProfile = Profile.getProfile(player.getName());
        if (senderProfile == null) return;

        String rawCommand = evt.getMessage().startsWith("/") ? evt.getMessage().substring(1) : evt.getMessage();
        String[] args = rawCommand.split(" ");
        if (args.length == 0) return;

        String command = args[0].toLowerCase(Locale.ROOT);
        @SuppressWarnings("unchecked")
        Map<String, Command> known = (Map<String, Command>) COMMAND_MAP.get(SIMPLE_COMMAND_MAP);

        if (known.containsKey("tell")
                && command.equals("tell")
                && args.length > 1
                && !args[1].equalsIgnoreCase(player.getName())) {
            Profile targetProfile = Profile.getProfile(args[1]);
            if (targetProfile != null
                    && targetProfile.getPreferencesContainer().getPrivateMessages() != PrivateMessages.GENEL) {
                evt.setCancelled(true);
                String prefix = LanguageManager.get(senderProfile, "prefix.lobby", DEFAULT_PREFIX);
                player.sendMessage(LanguageManager.get(senderProfile,
                        "listeners.commands.private-messages-disabled",
                        "{prefix}&cThat player has private messages disabled.",
                        "prefix", prefix));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerCommand(ServerCommandEvent evt) {
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent evt) {
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInventoryClick(InventoryClickEvent evt) {
        if (!(evt.getWhoClicked() instanceof Player)) return;
        Player player = (Player) evt.getWhoClicked();
        Profile profile = Profile.getProfile(player.getName());
        if (profile == null || profile.playingGame()) return;

    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInventoryDrag(InventoryDragEvent evt) {
        if (!(evt.getWhoClicked() instanceof Player)) return;
        Player player = (Player) evt.getWhoClicked();
        Profile profile = Profile.getProfile(player.getName());
        if (profile == null || profile.playingGame()) return;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerDropItem(PlayerDropItemEvent evt) {
        Player player = evt.getPlayer();
        Profile profile = Profile.getProfile(player.getName());
        if (profile == null || profile.playingGame()) return;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterClickSync(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        Bukkit.getScheduler().runTask(Core.getInstance(), p::updateInventory);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterDragSync(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        Bukkit.getScheduler().runTask(Core.getInstance(), p::updateInventory);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterHeldSync(PlayerItemHeldEvent e) {
        Bukkit.getScheduler().runTask(Core.getInstance(), e.getPlayer()::updateInventory);
    }

    private static void addComponents(TextComponent parent, BaseComponent[] components) {
        for (BaseComponent component : components) parent.addExtra(component);
    }
    private static void addComponentsWithEvents(TextComponent parent, BaseComponent[] components,
                                                ClickEvent clickEvent, HoverEvent hoverEvent) {
        for (BaseComponent component : components) {
            component.setClickEvent(clickEvent);
            component.setHoverEvent(hoverEvent);
            parent.addExtra(component);
        }
    }

    static {
        COMMAND_MAP = Accessors.getField(SimpleCommandMap.class, "knownCommands", Map.class);
        SIMPLE_COMMAND_MAP = (SimpleCommandMap) Accessors.getMethod(Bukkit.getServer().getClass(), "getCommandMap").invoke(Bukkit.getServer());
        try {
            RESTART_WATCHDOG = Accessors.getField(WatchdogThread.class, "instance", WatchdogThread.class);
            RESTART_WATCHDOG_STOPPING = Accessors.getField(WatchdogThread.class, "stopping", Boolean.TYPE);
        } catch (Throwable ignored) {
            RESTART_WATCHDOG = null;
            RESTART_WATCHDOG_STOPPING = null;
        }
    }
}
