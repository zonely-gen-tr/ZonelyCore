package dev.zonely.whiteeffect.player;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.cash.CashManager;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

public class CreditLoadListener implements Listener {
    private final Core plugin;

    public CreditLoadListener(Core plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        String name = e.getPlayer().getName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long credit = CashManager.loadCashFromDB(name);

            Bukkit.getScheduler().runTask(plugin, () -> {
                CashManager.cacheCredit(name, credit);
            });
        });
    }
}
