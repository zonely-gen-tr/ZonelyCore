package dev.zonely.whiteeffect.esports.game;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class EsportsSpectatorChatListener implements Listener {

    private final EsportsGameService gameService;

    public EsportsSpectatorChatListener(EsportsGameService gameService) {
        this.gameService = gameService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (gameService == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!gameService.isSpectator(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        gameService.handleSpectatorChat(player, event.getMessage());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (gameService != null) {
            gameService.leaveSpectator(event.getPlayer());
        }
    }
}
