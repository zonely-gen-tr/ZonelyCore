package dev.zonely.whiteeffect.libraries.menu;

import dev.zonely.whiteeffect.plugin.WPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public abstract class UpdatablePlayerPagedMenu extends PagedPlayerMenu implements Listener {
   private BukkitTask task;

   public UpdatablePlayerPagedMenu(Player player, String name) {
      this(player, name, 3);
   }

   public UpdatablePlayerPagedMenu(Player player, String name, int rows) {
      super(player, name, rows);
   }

   public void register(WPlugin plugin, long updateEveryTicks) {
      Bukkit.getPluginManager().registerEvents(this, plugin);
      this.task = (new BukkitRunnable() {
         public void run() {
            UpdatablePlayerPagedMenu.this.update();
         }
      }).runTaskTimer(plugin, 0L, updateEveryTicks);
   }

   public void cancel() {
      this.task.cancel();
      this.task = null;
   }

   public abstract void update();
}
