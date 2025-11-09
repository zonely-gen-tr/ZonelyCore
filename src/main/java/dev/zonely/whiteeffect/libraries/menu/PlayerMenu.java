package dev.zonely.whiteeffect.libraries.menu;

import dev.zonely.whiteeffect.plugin.WPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

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
}
