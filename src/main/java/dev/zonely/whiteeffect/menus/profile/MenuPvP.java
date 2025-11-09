package dev.zonely.whiteeffect.menus.profile;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import java.text.SimpleDateFormat;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class MenuPvP extends PlayerMenu {
   private static final SimpleDateFormat SDF = new SimpleDateFormat("d MMMM yyyy HH:mm", Locale.forLanguageTag("tr-TR"));

   public MenuPvP(Profile profile) {
      super(profile.getPlayer(), "§8PvP", 3);
      this.setItem(11, BukkitUtils.deserializeItemStack("159:13 : 1 : name>&a&lOyuna Katıl! : desc>&7\n&fPvP oyununa giriş yapmak için tıkla.\n \n&7* Oyun Kuralları\n&fKit seçin ve pvp mekanizmasının keyfini sürün.\n&fKarşılıklı takımlar yasaktır.\n&fYerinizde beklediğiniz taktirde puan kaybedersiniz."));
      this.setItem(15, BukkitUtils.deserializeItemStack("159:14 : 1 : name>&c&lReddet"));
      this.register(Core.getInstance());
      this.open();
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent evt) {
      if (evt.getInventory().equals(this.getInventory())) {
         evt.setCancelled(true);
         if (evt.getWhoClicked().equals(this.player)) {
            Profile profile = Profile.getProfile(this.player.getName());
            if (profile == null) {
               this.player.closeInventory();
               return;
            }

            if (evt.getClickedInventory() != null && evt.getClickedInventory().equals(this.getInventory())) {
               ItemStack item = evt.getCurrentItem();
               if (item != null && item.getType() != Material.AIR) {
                  if (evt.getSlot() == 11) {
                     this.player.closeInventory();
                     Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "oyun pvp oyna " + profile.getName());
                  } else if (evt.getSlot() == 15) {
                     this.player.closeInventory();
                  }
               }
            }
         }
      }

   }

   public void cancel() {
      HandlerList.unregisterAll(this);
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent evt) {
      if (evt.getPlayer().equals(this.player)) {
         this.cancel();
      }

   }

   @EventHandler
   public void onInventoryClose(InventoryCloseEvent evt) {
      if (evt.getPlayer().equals(this.player) && evt.getInventory().equals(this.getInventory())) {
         this.cancel();
      }

   }
}
