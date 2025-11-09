package dev.zonely.whiteeffect.menus.profile;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.menu.PagedPlayerMenu;
import dev.zonely.whiteeffect.menus.MenuProfile;
import dev.zonely.whiteeffect.mysterybox.api.MysteryBoxAPI;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.titles.Title;
import dev.zonely.whiteeffect.titles.TitleManager;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.StringUtils;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class MenuTitles extends PagedPlayerMenu {
   private Map<ItemStack, Title> titles = new HashMap();
   private static final SimpleDateFormat SDF = new SimpleDateFormat("d MMMM yyyy HH:mm", Locale.forLanguageTag("tr-TR"));

   public MenuTitles(Profile profile) {
      super(profile.getPlayer(), LanguageManager.get(profile,
            "menus.profile.titles.title",
            "&8Profile &0&l> &8Titles"), 6);
      this.previousPage = 45;
      this.nextPage = 53;
      this.onlySlots(new Integer[]{19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 35, 37, 38, 39, 40, 41, 42, 43});
      this.removeSlotsWith(BukkitUtils.putProfileOnSkull(this.player, BukkitUtils.deserializeItemStack("SKULL_ITEM:3 : 1 : name>&e&l" + profile.getName() + " : desc>\n&8 ▪ &fYetki: &b" + Role.getRoleByName(profile.getDataContainer("ZonelyCoreProfile", "role").getAsString()).getName() + "\n&8 ▪ &fKredi: &6" + StringUtils.formatNumber(CashManager.getCash(profile)) + " [KREDi]\n&7 &8▪ &fParçacık: &d" + profile.getFormatedStats("ZonelyMysteryBox", "mystery_frags") + " [PARÇACIK]\n&7 &8▪ &fGizemli Sandık: &b" + StringUtils.formatNumber(MysteryBoxAPI.getMysteryBoxes(profile)) + " [GiZEMLi SANDIK]\n \n&8 ▪ &fIlk Oturum Açma: &f" + SDF.format(profile.getDataContainer("ZonelyCoreProfile", "created").getAsLong()) + "\n&8 ▪ &fSon Oturum Açma: &f" + SDF.format(profile.getDataContainer("ZonelyCoreProfile", "lastlogin").getAsLong()))), new int[]{4});
      this.removeSlotsWith(BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"), new int[]{0});
      this.removeSlotsWith(BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"), new int[]{1});
      this.removeSlotsWith(BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"), new int[]{2});
      this.removeSlotsWith(BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"), new int[]{3});
      this.removeSlotsWith(BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"), new int[]{8});
      this.removeSlotsWith(BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"), new int[]{7});
      this.removeSlotsWith(BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"), new int[]{6});
      this.removeSlotsWith(BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"), new int[]{7});
      this.removeSlotsWith(BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"), new int[]{5});
      this.removeSlotsWith(BukkitUtils.deserializeItemStack("ARROW : 1 : name>&e<--"), new int[]{49});
      List<ItemStack> items = new ArrayList();
      List<ItemStack> sub = new ArrayList();
      Iterator var4 = Title.listTitles().iterator();

      while(var4.hasNext()) {
         Title title = (Title)var4.next();
         ItemStack item = title.getIcon(profile);
         this.titles.put(item, title);
         if (title.has(profile)) {
            items.add(item);
         } else {
            sub.add(item);
         }
      }

      items.addAll(sub);
      this.setItems(items);
      sub.clear();
      items.clear();
      this.register(Core.getInstance());
      this.open();
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent evt) {
      if (evt.getInventory().equals(this.getCurrentInventory())) {
         evt.setCancelled(true);
         if (evt.getWhoClicked().equals(this.player)) {
            Profile profile = Profile.getProfile(this.player.getName());
            if (profile == null) {
               this.player.closeInventory();
               return;
            }

            if (evt.getClickedInventory() != null && evt.getClickedInventory().equals(this.getCurrentInventory())) {
               ItemStack item = evt.getCurrentItem();
               if (item != null && item.getType() != Material.AIR) {
                  if (evt.getSlot() == this.previousPage) {
                     EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                     this.openPrevious();
                  } else if (evt.getSlot() == this.nextPage) {
                     EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                     this.openNext();
                  } else if (evt.getSlot() == 49) {
                     EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
                     new MenuProfile(profile);
                  } else {
                     Title title = (Title)this.titles.get(item);
                     if (title != null) {
                        if (!title.has(profile)) {
                           EnumSound.ENDERMAN_TELEPORT.play(this.player, 0.5F, 1.0F);
                           return;
                        }

                        EnumSound.ITEM_PICKUP.play(this.player, 0.5F, 2.0F);
                        Title selected = profile.getSelectedContainer().getTitle();
                        if (title.equals(selected)) {
                           profile.getSelectedContainer().setTitle("0");
                           TitleManager.deselect(profile);
                        } else {
                           profile.getSelectedContainer().setTitle(title.getId());
                           TitleManager.select(profile, title);
                        }

                        new MenuTitles(profile);
                     }
                  }
               }
            }
         }
      }

   }

   public void cancel() {
      this.titles.clear();
      this.titles = null;
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
      if (evt.getPlayer().equals(this.player) && evt.getInventory().equals(this.getCurrentInventory())) {
         this.cancel();
      }

   }
}


