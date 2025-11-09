package dev.zonely.whiteeffect.menus.profile;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.league.AlonsoLeagueLevel;
import dev.zonely.whiteeffect.league.AlonsoLeagueMenuRenderer;
import dev.zonely.whiteeffect.league.AlonsoLeagueSettings;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.menus.MenuProfile;
import dev.zonely.whiteeffect.mysterybox.api.MysteryBoxAPI;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.StringUtils;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import java.text.SimpleDateFormat;
import java.util.Locale;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class MenuLevels extends PlayerMenu {
   private static final SimpleDateFormat SDF = new SimpleDateFormat("d MMMM yyyy HH:mm", Locale.forLanguageTag("tr-TR"));
   private final int pointsLevel;

   public MenuLevels(Profile profile) {
      super(profile.getPlayer(), "§8Profil §0§l> §8Seviye", 6);
      this.pointsLevel = parsePoints(PlaceholderAPI.setPlaceholders(this.player, "%alonsoleagues_points%"));
      this.fillBorders(profile);
      AlonsoLeagueMenuRenderer.apply(this, profile, this.pointsLevel);
      this.setItem(13, BukkitUtils.deserializeItemStack("101 : 1"));
      this.setItem(22, BukkitUtils.deserializeItemStack("101 : 1"));
      this.setItem(31, BukkitUtils.deserializeItemStack("101 : 1"));
      this.setItem(40, BukkitUtils.deserializeItemStack("101 : 1"));
      this.setItem(49, BukkitUtils.deserializeItemStack("ARROW : 1 : name>&e<--"));
      this.register(Core.getInstance());
      this.open();
   }

   private void fillBorders(Profile profile) {
      this.setItem(0, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      this.setItem(1, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      this.setItem(2, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      this.setItem(3, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      this.setItem(5, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      this.setItem(6, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      this.setItem(7, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      this.setItem(8, BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1"));
      ItemStack profileItem = BukkitUtils.deserializeItemStack(
            "SKULL_ITEM:3 : 1 : name>&e&l" + profile.getName() + " : desc>\n"
                  + "&8 ▪ &fYetki: &b" + Role.getRoleByName(profile.getDataContainer("ZonelyCoreProfile", "role").getAsString()).getName() + "\n"
                  + "&8 ▪ &fKredi: &6" + StringUtils.formatNumber(CashManager.getCash(profile)) + " [KREDi]\n"
                  + "&7 &8▪ &fParçacık: &d" + profile.getFormatedStats("ZonelyMysteryBox", "mystery_frags") + " [PARÇACIK]\n"
                  + "&7 &8▪ &fGizemli Sandık: &b" + StringUtils.formatNumber(MysteryBoxAPI.getMysteryBoxes(profile)) + " [GiZEMLi SANDIK]\n"
                  + " \n"
                  + "&8 ▪ &fİlk Oturum Açma: &f" + SDF.format(profile.getDataContainer("ZonelyCoreProfile", "created").getAsLong()) + "\n"
                  + "&8 ▪ &fSon Oturum Açma: &f" + SDF.format(profile.getDataContainer("ZonelyCoreProfile", "lastlogin").getAsLong()));
      this.setItem(4, BukkitUtils.putProfileOnSkull(this.player, profileItem));
   }

   private int parsePoints(String raw) {
      try {
         return Integer.parseInt(raw);
      } catch (NumberFormatException ignored) {
         return 0;
      }
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent evt) {
      if (!evt.getInventory().equals(this.getInventory())) {
         return;
      }
      evt.setCancelled(true);
      if (!evt.getWhoClicked().equals(this.player)) {
         return;
      }
      if (evt.getClickedInventory() == null || !evt.getClickedInventory().equals(this.getInventory())) {
         return;
      }
      ItemStack item = evt.getCurrentItem();
      if (item == null || item.getType() == Material.AIR) {
         return;
      }
      int slot = evt.getSlot();
      if (slot == 49) {
         EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
         Profile profile = Profile.getProfile(this.player.getName());
         if (profile != null) {
            new MenuProfile(profile);
         }
         return;
      }
      if (isLevelSlot(slot)) {
         EnumSound.ITEM_PICKUP.play(this.player, 0.5F, 2.0F);
      }
   }

   private boolean isLevelSlot(int slot) {
      AlonsoLeagueSettings settings = Core.getInstance().getAlonsoLeagueSettings();
      if (settings == null) {
         return false;
      }
      for (AlonsoLeagueLevel level : settings.getLevels()) {
         if (level.getSlot() == slot) {
            return true;
         }
      }
      return false;
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
