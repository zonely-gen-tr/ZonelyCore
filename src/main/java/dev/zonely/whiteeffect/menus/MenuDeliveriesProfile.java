package dev.zonely.whiteeffect.menus;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.database.data.DataContainer;
import dev.zonely.whiteeffect.database.data.container.DeliveriesContainer;
import dev.zonely.whiteeffect.deliveries.Delivery;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.menu.UpdatablePlayerMenu;
import dev.zonely.whiteeffect.mysterybox.api.MysteryBoxAPI;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.StringUtils;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class MenuDeliveriesProfile extends UpdatablePlayerMenu {
   private static final SimpleDateFormat SDF = new SimpleDateFormat("d MMMM yyyy HH:mm", Locale.forLanguageTag("tr-TR"));
   private Profile profile;
   private Map<ItemStack, Delivery> deliveries = new HashMap<>();

   public MenuDeliveriesProfile(Profile profile) {
      super(profile.getPlayer(), LanguageManager.get(profile,
            "menus.profile.deliveries-title",
            "&8Profile &0&l> &8Reward Courier"), 6);
      this.profile = profile;

      ItemStack profileInfo;
      try {
         Role role = Role.getPlayerRole(profile.getPlayer());
         if (role == null) {
            role = Role.getLastRole();
         }
         long created = safeLong(profile, "ZonelyCoreProfile", "created");
         long lastLogin = safeLong(profile, "ZonelyCoreProfile", "lastlogin");
         profileInfo = BukkitUtils.putProfileOnSkull(this.player, BukkitUtils.deserializeItemStack(
               "SKULL_ITEM:3 : 1 : name>&e&l" + profile.getName()
                     + " : desc>\n&8 > &fRole: &b" + (role != null ? role.getName() : "Player")
                     + "\n&8 > &fCredits: &6" + StringUtils.formatNumber(CashManager.getCash(profile)) + " [CREDITS]"
                     + "\n&7 &8> &fMystery Fragments: &d" + profile.getFormatedStats("ZonelyMysteryBox", "mystery_frags") + " [FRAGMENTS]"
                     + "\n&7 &8> &fMystery Boxes: &b" + StringUtils.formatNumber(MysteryBoxAPI.getMysteryBoxes(profile)) + " [BOXES]"
                     + "\n \n&8 > &fFirst Login: &f" + (created > 0 ? SDF.format(created) : "-")
                     + "\n&8 > &fLast Login: &f" + (lastLogin > 0 ? SDF.format(lastLogin) : "-")));
      } catch (Throwable t) {
         Core.getInstance().getLogger().log(Level.WARNING,
               "[Deliveries] Failed to build profile summary for " + profile.getName(), t);
         profileInfo = BukkitUtils.putProfileOnSkull(this.player,
               BukkitUtils.deserializeItemStack("PLAYER_HEAD : 1 : name>&e" + profile.getName()));
      }
      this.setItem(4, profileInfo);
      this.setItem(0, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      this.setItem(1, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      this.setItem(2, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      this.setItem(3, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      this.setItem(5, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      this.setItem(6, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      this.setItem(7, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      this.setItem(8, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      this.setItem(49, BukkitUtils.deserializeItemStack("ARROW : 1 : name>&e<--"));
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
               Delivery delivery = this.deliveries.get(item);
               if (delivery != null) {
                  DeliveriesContainer container = this.profile.getDeliveriesContainer();
                  if (!container.alreadyClaimed(delivery.getId()) && delivery.hasPermission(this.player)) {
                     EnumSound.LEVEL_UP.play(this.player, 1.0F, 1.0F);
                     container.claimDelivery(delivery.getId(), delivery.getDays());
                     delivery.listRewards().forEach(reward -> reward.dispatch(this.profile));
                     this.player.sendMessage(delivery.getMessage());
                  } else {
                     EnumSound.ENDERMAN_TELEPORT.play(this.player, 0.5F, 1.0F);
                  }
               }

               if (evt.getSlot() == 49) {
                  EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                  new MenuProfile(this.profile);
               }
            }
         }
      }
   }

   public void update() {
      this.deliveries.clear();
      Iterator<Delivery> iterator = Delivery.listDeliveries().iterator();

      while (iterator.hasNext()) {
         Delivery delivery = iterator.next();
         ItemStack item = delivery.getIcon(this.profile);
         this.setItem(delivery.getSlot() + 9, item);
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

   private static long safeLong(Profile profile, String container, String key) {
      try {
         DataContainer containerData = profile.getDataContainer(container, key);
         if (containerData == null) {
            return -1L;
         }
         Object raw = containerData.get();
         if (raw == null) {
            return -1L;
         }
         if (raw instanceof Number) {
            return ((Number) raw).longValue();
         }
         return Long.parseLong(raw.toString());
      } catch (Throwable ignored) {
         return -1L;
      }
   }
}
