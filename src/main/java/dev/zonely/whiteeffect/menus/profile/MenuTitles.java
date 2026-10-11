package dev.zonely.whiteeffect.menus.profile;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.lang.LanguageManager.MenuItemDefinition;
import dev.zonely.whiteeffect.libraries.menu.PagedPlayerMenu;
import dev.zonely.whiteeffect.menus.MenuProfile;
import dev.zonely.whiteeffect.mysterybox.api.MysteryBoxAPI;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.titles.Title;
import dev.zonely.whiteeffect.titles.TitleManager;
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

   private static final SimpleDateFormat SDF = new SimpleDateFormat("d MMMM yyyy HH:mm", Locale.ENGLISH);

   private final Map<ItemStack, Title> titles = new HashMap<>();

   public MenuTitles(Profile profile) {
      super(profile.getPlayer(), LanguageManager.get(profile,
            "menus.profile.titles.title",
            "&8Profile &0&l> &8Titles"), 6);

      this.previousPage = 45;
      this.nextPage = 53;
      this.onlySlots(new Integer[]{19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 35, 37, 38, 39, 40, 41, 42, 43});

      String rank = Role.getRoleByName(profile.getDataContainer("ZonelyCoreProfile", "role").getAsString()).getName();
      String credits = StringUtils.formatNumber(CashManager.getCash(profile));
      String fragments = profile.getFormatedStats("ZonelyMysteryBox", "mystery_frags");
      String boxes = StringUtils.formatNumber(MysteryBoxAPI.getMysteryBoxes(profile));
      String firstLogin = SDF.format(profile.getDataContainer("ZonelyCoreProfile", "created").getAsLong());
      String lastLogin = SDF.format(profile.getDataContainer("ZonelyCoreProfile", "lastlogin").getAsLong());

      MenuItemDefinition info = LanguageManager.getMenuItem(profile,
            "menus.profile.items.player-info", 4,
            "SKULL_ITEM:3 : 1 : name>&e&l{player} : desc>\n"
                  + "&8- &fRank: &b{role}\n"
                  + "&8- &fCredits: &6{credits}\n"
                  + "&8- &fMystery Fragments: &d{fragments}\n"
                  + "&8- &fMystery Boxes: &b{boxes}\n \n"
                  + "&8- &fFirst Login: &f{first_login}\n"
                  + "&8- &fLast Login: &f{last_login}",
            "player", profile.getName(),
            "role", rank,
            "credits", credits,
            "fragments", fragments,
            "boxes", boxes,
            "first_login", firstLogin,
            "last_login", lastLogin);
      this.setItem(info.slot(), BukkitUtils.putProfileOnSkull(this.player,
            BukkitUtils.deserializeItemStack(info.item())));

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

      List<ItemStack> items = new ArrayList<>();
      List<ItemStack> locked = new ArrayList<>();
      Iterator<Title> iterator = Title.listTitles().iterator();

      while (iterator.hasNext()) {
         Title title = iterator.next();
         ItemStack item = title.getIcon(profile);
         this.titles.put(item, title);
         if (title.has(profile)) {
            items.add(item);
         } else {
            locked.add(item);
         }
      }

      items.addAll(locked);
      this.setItems(items);
      this.register(Core.getInstance());
      this.open();
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent evt) {
      if (!evt.getInventory().equals(this.getCurrentInventory())) {
         return;
      }

      evt.setCancelled(true);
      if (!evt.getWhoClicked().equals(this.player)) {
         return;
      }

      Profile profile = Profile.getProfile(this.player.getName());
      if (profile == null) {
         this.player.closeInventory();
         return;
      }

      if (evt.getClickedInventory() == null || !evt.getClickedInventory().equals(this.getCurrentInventory())) {
         return;
      }

      ItemStack item = evt.getCurrentItem();
      if (item == null || item.getType() == Material.AIR) {
         return;
      }

      if (evt.getSlot() == this.previousPage) {
         EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
         this.openPrevious();
         return;
      }

      if (evt.getSlot() == this.nextPage) {
         EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
         this.openNext();
         return;
      }

      if (evt.getSlot() == 49) {
         EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
         new MenuProfile(profile);
         return;
      }

      Title title = this.titles.get(item);
      if (title == null) {
         return;
      }

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

   public void cancel() {
      this.titles.clear();
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
