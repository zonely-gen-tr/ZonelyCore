package dev.zonely.whiteeffect.auth;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class AuthFallbackConnector {

    private static final String DEFAULT_CHANNEL = "bungeecord:main";

    private final Core plugin;
    private final boolean enabled;
    private final boolean respectBungeecordSwitch;
    private final boolean bungeecordFlag;
    private final String targetServer;
    private final long delayTicks;
    private final boolean triggerOnLogin;
    private final boolean triggerOnSessionRestore;
    private final String pluginChannel;

    public AuthFallbackConnector(Core plugin, WConfig config) {
        this.plugin = plugin;

        ConfigurationSection section = config.getSection("auth.fallback-server");
        if (section == null) {
            config.createSection("auth.fallback-server");
            section = config.getSection("auth.fallback-server");
        }

        this.enabled = section.getBoolean("enabled", false);
        this.respectBungeecordSwitch = section.getBoolean("require-bungeecord-flag", true);
        this.bungeecordFlag = config.getBoolean("bungeecord", false);
        this.targetServer = trim(section.getString("server", ""));
        this.delayTicks = Math.max(0L, section.getLong("connect-delay-ticks", 20L));

        ConfigurationSection trigger = section.getConfigurationSection("trigger");
        if (trigger == null) {
            section.createSection("trigger");
            trigger = section.getConfigurationSection("trigger");
        }
        this.triggerOnLogin = trigger.getBoolean("on-login", true);
        this.triggerOnSessionRestore = trigger.getBoolean("on-session-restore", true);

        String configuredChannel = trim(section.getString("channel", DEFAULT_CHANNEL));
        this.pluginChannel = configuredChannel.isEmpty() ? DEFAULT_CHANNEL : configuredChannel;
    }

    public void handlePostLogin(Player player, boolean sessionRestored) {
        if (!shouldAttemptTransfer(sessionRestored)) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            try {
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF(targetServer);
                player.sendPluginMessage(plugin, pluginChannel, out.toByteArray());
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "[Auth] Unable to forward " + player.getName() + " to fallback server " + targetServer, ex);
            }
        }, delayTicks);
    }

    private boolean shouldAttemptTransfer(boolean sessionRestored) {
        if (!enabled) {
            return false;
        }
        if (targetServer.isEmpty()) {
            return false;
        }
        if (respectBungeecordSwitch && !bungeecordFlag) {
            return false;
        }
        if (sessionRestored) {
            return triggerOnSessionRestore;
        }
        return triggerOnLogin;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
