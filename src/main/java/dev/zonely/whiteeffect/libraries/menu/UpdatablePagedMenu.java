package dev.zonely.whiteeffect.libraries.menu;

import dev.zonely.whiteeffect.plugin.WPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public abstract class UpdatablePagedMenu extends PagedMenu implements Listener {
   private BukkitTask task;

   public UpdatablePagedMenu(String name) {
      this(name, 3);
   }

   public UpdatablePagedMenu(String name, int rows) {
      super(name, rows);
   }

   public void register(WPlugin plugin, long updateEveryTicks) {
      Bukkit.getPluginManager().registerEvents(this, plugin);
      this.task = (new BukkitRunnable() {
         public void run() {
            UpdatablePagedMenu.this.update();
         }
      }).runTaskTimer(plugin, 0L, updateEveryTicks);
   }

   public void cancel() {
      this.task.cancel();
      this.task = null;
   }

   public abstract void update();
}
