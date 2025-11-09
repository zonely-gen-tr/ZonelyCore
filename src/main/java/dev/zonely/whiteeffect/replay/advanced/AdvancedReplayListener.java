package dev.zonely.whiteeffect.replay.advanced;

import dev.zonely.whiteeffect.Core;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AdvancedReplayListener implements Listener {

    private final AdvancedReplayRecorder recorder = AdvancedReplayRecorder.get();

    public AdvancedReplayListener() {
        Core.getInstance().getLogger().fine("[AdvancedReplay] Listener initialised");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        recorder.recordBlockPlace(event);
    }

    @EventHandler(ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        recorder.recordBlockBreak(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        recorder.recordHeldItem(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        recorder.recordAnimation(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        recorder.recordDamage(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        recorder.recordProjectileLaunch(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        recorder.recordProjectileHit(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        recorder.handlePlayerQuit(player.getUniqueId());
    }
}
