package dev.zonely.whiteeffect.libraries.menu;

import dev.zonely.whiteeffect.plugin.WPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class PlayerMenu extends Menu implements Listener {
   protected Player player;

   public PlayerMenu(Player player, String title) {
      this(player, title, 3);
   }

   public PlayerMenu(Player player, String title, int rows) {
      super(title, rows);
      this.player = player;
   }

   public void register(WPlugin plugin) {
      Bukkit.getPluginManager().registerEvents(this, plugin);
   }

   public void open() {
      this.player.openInventory(this.getInventory());
   }

   public Player getPlayer() {
      return this.player;
   }

   @EventHandler
   public void onMenuClick(InventoryClickEvent event) {
      if (!event.getWhoClicked().equals(this.player)) {
         return;
      }
      if (!event.getView().getTopInventory().equals(this.getInventory())) {
         return;
      }
      event.setCancelled(true);
      event.setResult(Event.Result.DENY);
   }

   @EventHandler
   public void onMenuDrag(InventoryDragEvent event) {
      if (!event.getWhoClicked().equals(this.player)) {
         return;
      }
      if (!event.getView().getTopInventory().equals(this.getInventory())) {
         return;
      }
      event.setCancelled(true);
      event.setResult(Event.Result.DENY);
      event.setCursor(null);
      event.getWhoClicked().setItemOnCursor(null);
   }
}
