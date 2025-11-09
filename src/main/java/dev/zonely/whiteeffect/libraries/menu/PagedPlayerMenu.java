package dev.zonely.whiteeffect.libraries.menu;

import dev.zonely.whiteeffect.plugin.WPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

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
}
