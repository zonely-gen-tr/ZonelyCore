package dev.zonely.whiteeffect.libraries.menu;

import dev.zonely.whiteeffect.plugin.WPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public abstract class UpdatablePlayerMenu extends UpdatableMenu implements Listener {
   protected Player player;
   private BukkitTask task;

   public UpdatablePlayerMenu(Player player, String name) {
      this(player, name, 3);
   }

   public UpdatablePlayerMenu(Player player, String name, int rows) {
      super(name, rows);
      this.player = player;
   }

   public void open() {
      this.player.openInventory(this.getInventory());
   }

   public void register(WPlugin plugin, long updateEveryTicks) {
      Bukkit.getPluginManager().registerEvents(this, plugin);
      this.task = (new BukkitRunnable() {
         public void run() {
            UpdatablePlayerMenu.this.update();
         }
      }).runTaskTimer(plugin, 0L, updateEveryTicks);
   }

   public void cancel() {
      this.task.cancel();
      this.task = null;
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
   public void onUpdatableMenuClick(InventoryClickEvent event) {
      if (!event.getWhoClicked().equals(this.player)) {
         return;
      }
      if (!event.getView().getTopInventory().equals(this.getInventory())) {
         return;
      }

      int rawSlot = event.getRawSlot();
      if (rawSlot < 0) {
         return;
      }
      event.setCancelled(true);
      event.setResult(Event.Result.DENY);
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
   public void onUpdatableMenuDrag(InventoryDragEvent event) {
      if (!event.getWhoClicked().equals(this.player)) {
         return;
      }
      if (!event.getView().getTopInventory().equals(this.getInventory())) {
         return;
      }
      event.setCancelled(true);
      event.setResult(Event.Result.DENY);
   }

   public abstract void update();
}
