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
import org.bukkit.inventory.Inventory;

public class PagedPlayerMenu extends PagedMenu implements Listener {
   protected Player player;

   public PagedPlayerMenu(Player player, String name) {
      this(player, name, 3);
   }

   public PagedPlayerMenu(Player player, String name, int rows) {
      super(name, rows);
      this.player = player;
   }

   public void open() {
      this.player.openInventory(((Menu)this.menus.get(0)).getInventory());
   }

   public void register(WPlugin plugin) {
      Bukkit.getPluginManager().registerEvents(this, plugin);
   }

   public void openPrevious() {
      if (this.currentPage != 1) {
         --this.currentPage;
         this.player.openInventory(((Menu)this.menus.get(this.currentPage - 1)).getInventory());
      }
   }

   public void openNext() {
      if (this.currentPage + 1 <= this.menus.size()) {
         ++this.currentPage;
         this.player.openInventory(((Menu)this.menus.get(this.currentPage - 1)).getInventory());
      }
   }

   @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
   public void onPagedMenuClick(InventoryClickEvent event) {
      if (!event.getWhoClicked().equals(this.player)) {
         return;
      }
      Inventory top = event.getView().getTopInventory();
      if (!isTopInventory(top)) {
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
   public void onPagedMenuDrag(InventoryDragEvent event) {
      if (!event.getWhoClicked().equals(this.player)) {
         return;
      }
      Inventory top = event.getView().getTopInventory();
      if (!isTopInventory(top)) {
         return;
      }
      event.setCancelled(true);
      event.setResult(Event.Result.DENY);
   }

   private boolean isTopInventory(Inventory top) {
      if (top == null || this.menus == null || this.menus.isEmpty()) {
         return false;
      }
      int pageIndex = Math.max(0, Math.min(this.currentPage - 1, this.menus.size() - 1));
      Menu current = this.menus.get(pageIndex);
      return current != null && top.equals(current.getInventory());
   }
}
