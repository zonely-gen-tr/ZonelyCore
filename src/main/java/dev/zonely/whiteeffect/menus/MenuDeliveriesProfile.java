package dev.zonely.whiteeffect.menus;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.database.data.DataContainer;
import dev.zonely.whiteeffect.database.data.container.DeliveriesContainer;
import dev.zonely.whiteeffect.deliveries.Delivery;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.lang.LanguageManager.MenuItemDefinition;
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
   private static final SimpleDateFormat SDF = new SimpleDateFormat("d MMMM yyyy HH:mm", Locale.ENGLISH);
   private Profile profile;
   private Map<ItemStack, Delivery> deliveries = new HashMap<>();

   public MenuDeliveriesProfile(Profile profile) {
      super(profile.getPlayer(), LanguageManager.get(profile,
            "menus.profile.deliveries-menu-title",
            localize(profile, "&8Profile &0&l> &8Reward Courier", "&8Profil &0&l> &8Odul Kuryesi")), 6);
      this.profile = profile;
      populateHeaderItems(profile);
      this.update();
      this.register(Core.getInstance(), 20L);
      this.open();
   }

   private void populateHeaderItems(Profile profile) {
      ItemStack profileInfo;
      try {
         Role role = Role.getPlayerRole(profile.getPlayer());
         if (role == null) {
            role = Role.getLastRole();
         }
         long created = safeLong(profile, "ZonelyCoreProfile", "created");
         long lastLogin = safeLong(profile, "ZonelyCoreProfile", "lastlogin");
         String createdText = created > 0L ? SDF.format(created) : "-";
         String lastLoginText = lastLogin > 0L ? SDF.format(lastLogin) : "-";

         MenuItemDefinition playerInfo = LanguageManager.getMenuItem(profile,
               "menus.profile.items.player-info",
               4,
               "SKULL_ITEM:3 : 1 : name>&e&l{player} : desc>\n"
                     + "&8- &fRole: &b{role}\n"
                     + "&8- &fCredits: &6{credits}\n"
                     + "&8- &fMystery Fragments: &d{fragments}\n"
                     + "&8- &fMystery Boxes: &b{boxes}\n \n"
                     + "&8- &fFirst Login: &f{first_login}\n"
                     + "&8- &fLast Login: &f{last_login}",
               "player", profile.getName(),
               "role", role != null ? role.getName() : "Player",
               "credits", StringUtils.formatNumber(CashManager.getCash(profile)),
               "fragments", profile.getFormatedStats("ZonelyMysteryBox", "mystery_frags"),
               "boxes", StringUtils.formatNumber(MysteryBoxAPI.getMysteryBoxes(profile)),
               "first_login", createdText,
               "last_login", lastLoginText);

         profileInfo = BukkitUtils.putProfileOnSkull(this.player,
               BukkitUtils.deserializeItemStack(playerInfo.item()));
      } catch (Throwable t) {
         Core.getInstance().getLogger().log(Level.WARNING,
               "[Deliveries] Failed to build profile summary for " + profile.getName(), t);
         profileInfo = BukkitUtils.putProfileOnSkull(this.player,
               BukkitUtils.deserializeItemStack("PLAYER_HEAD : 1 : name>&e" + profile.getName()));
      }
      this.setItem(4, profileInfo);

      ItemStack filler = BukkitUtils.deserializeItemStack(LanguageManager.get(profile,
            "menus.common.filler", "STAINED_GLASS_PANE:7 : 1 : name>&r"));
      for (int slot = 0; slot <= 8; slot++) {
         if (slot == 4) {
            continue;
         }
         this.setItem(slot, filler.clone());
      }

      this.setItem(49, BukkitUtils.deserializeItemStack(LanguageManager.get(profile,
            "menus.common.back", "ARROW : 1 : name>&e<-")));
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

   private static String localize(Profile profile, String en, String tr) {
      String locale = LanguageManager.resolveProfileLocale(profile);
      if (locale != null && locale.toLowerCase(Locale.ROOT).startsWith("tr")) {
         return tr;
      }
      return en;
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
