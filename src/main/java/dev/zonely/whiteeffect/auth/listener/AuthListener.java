package dev.zonely.whiteeffect.auth.listener;

import dev.zonely.whiteeffect.auth.AuthManager;
import dev.zonely.whiteeffect.lang.LanguageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

public final class AuthListener implements Listener {

    private final AuthManager manager;

    public AuthListener(AuthManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSensitiveAuthCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.length() <= 1 || raw.charAt(0) != '/') {
            return;
        }
        String line = raw.substring(1).trim();
        if (line.isEmpty()) {
            return;
        }
        String[] parts = line.split("\\s+");
        if (parts.length == 0) {
            return;
        }
        String label = parts[0].toLowerCase(Locale.ROOT);
        Player player = event.getPlayer();

        if ("login".equals(label) || "l".equals(label)) {
            // Mask message early so command/password never appears in command logs.
            event.setMessage("/" + parts[0] + " ********");
            event.setCancelled(true);
            if (parts.length < 2) {
                LanguageManager.send(player, "auth.login.usage",
                        "{prefix}&7Usage: &b/" + label + " <password>",
                        "prefix", manager.getPrefix(player),
                        "command", label);
                return;
            }
            manager.attemptLogin(player, parts[1]);
            return;
        }

        if ("register".equals(label) || "reg".equals(label)) {
            event.setMessage("/" + parts[0] + " ******** ********");
            event.setCancelled(true);
            if (parts.length < 3) {
                LanguageManager.send(player, "auth.register.usage",
                        "{prefix}&7Usage: &b/" + label + " <password> <confirm>",
                        "prefix", manager.getPrefix(player),
                        "command", label);
                return;
            }
            manager.attemptRegister(player, parts[1], parts[2]);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        manager.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!manager.requiresAuthentication(player)) {
            return;
        }
        if (manager.isCommandAllowed(event.getMessage())) {
            return;
        }
        event.setCancelled(true);
        LanguageManager.send(player, "auth.protection.command-blocked",
                "{prefix}&cYou must login before using other commands.",
                "prefix", manager.getPrefix(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!manager.requiresAuthentication(player)) {
            return;
        }
        if (manager.handleMapVerificationChat(player, event.getMessage())) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        LanguageManager.send(player, "auth.protection.chat-blocked",
                "{prefix}&cYou must login before chatting.",
                "prefix", manager.getPrefix(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!manager.requiresAuthentication(player)) {
            return;
        }
        if (event.getTo() != null &&
                (event.getFrom().getBlockX() != event.getTo().getBlockX()
                        || event.getFrom().getBlockY() != event.getTo().getBlockY()
                        || event.getFrom().getBlockZ() != event.getTo().getBlockZ())) {
            event.setTo(event.getFrom());
            LanguageManager.send(player, "auth.protection.movement-blocked",
                    "{prefix}&cPlease login before moving.",
                    "prefix", manager.getPrefix(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!manager.requiresAuthentication(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!manager.requiresAuthentication(player)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (!manager.requiresAuthentication(player)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (!manager.requiresAuthentication(player)) {
            return;
        }
        event.setCancelled(true);
    }
}
