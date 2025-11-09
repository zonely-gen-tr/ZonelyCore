package dev.zonely.whiteeffect.menus;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.data.container.DeliveriesContainer;
import dev.zonely.whiteeffect.deliveries.Delivery;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.menu.UpdatablePlayerMenu;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class MenuDeliveries extends UpdatablePlayerMenu {
   private Profile profile;
   private Map<ItemStack, Delivery> deliveries;

   public MenuDeliveries(Profile profile) {
      super(profile.getPlayer(), LanguageManager.get(profile,
            "menus.deliveries.title",
            "&8Reward Courier"), 4);
      this.profile = profile;
      this.deliveries = new HashMap();
      this.update();
      this.register(Core.getInstance(), 20L);
      this.open();
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent evt) {
      if (evt.getInventory().equals(this.getInventory())) {
         evt.setCancelled(true);
         if (evt.getWhoClicked().equals(this.player) && evt.getClickedInventory() != null && evt.getClickedInventory().equals(this.getInventory())) {
            ItemStack item = evt.getCurrentItem();
            if (item != null && item.getType() != Material.AIR) {
               Delivery delivery = (Delivery)this.deliveries.get(item);
               if (delivery != null) {
                  DeliveriesContainer container = this.profile.getDeliveriesContainer();
                  if (!container.alreadyClaimed(delivery.getId()) && delivery.hasPermission(this.player)) {
                     EnumSound.LEVEL_UP.play(this.player, 1.0F, 1.0F);
                     container.claimDelivery(delivery.getId(), delivery.getDays());
                     delivery.listRewards().forEach((reward) -> {
                        reward.dispatch(this.profile);
                     });
                     this.player.sendMessage(delivery.getMessage());
                     this.player.closeInventory();
                  } else {
                     EnumSound.ENDERMAN_TELEPORT.play(this.player, 0.5F, 1.0F);
                  }
               }
            }
         }
      }

   }

   public void update() {
      this.deliveries.clear();
      Iterator var1 = Delivery.listDeliveries().iterator();

      while(var1.hasNext()) {
         Delivery delivery = (Delivery)var1.next();
         ItemStack item = delivery.getIcon(this.profile);
         this.setItem(delivery.getSlot(), item);
         this.deliveries.put(item, delivery);
      }

      dev.zonely.whiteeffect.nms.util.SafeInventoryUpdater.update(this.player);
   }

   public void cancel() {
      super.cancel();
      HandlerList.unregisterAll(this);
      this.profile = null;
      this.deliveries.clear();
      this.deliveries = null;
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
