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

public final class AuthListener implements Listener {

    private final AuthManager manager;

    public AuthListener(AuthManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
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
        if (!manager.requiresAuthentication(event.getPlayer())) {
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
