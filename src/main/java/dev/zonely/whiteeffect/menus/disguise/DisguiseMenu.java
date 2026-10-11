package dev.zonely.whiteeffect.menus.disguise;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.fake.FakeManager;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class DisguiseMenu extends PlayerMenu {

   private static final int[] ROLE_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
   private static final String ACTION_CLOSE = "close";
   private static final String ACTION_BACK = "back";
   private static final String ACTION_CLEAR = "clear";
   private static final String ACTION_ROLE_PREFIX = "role:";
   private static final String ACTION_SKIN_PREFIX = "skin:";

   private final String selectedRole;
   private final Map<Integer, String> actions = new HashMap<>();

   public static void openRoleMenu(Player player) {
      new DisguiseMenu(player, null);
   }

   public static void openSkinMenu(Player player, String role) {
      new DisguiseMenu(player, role);
   }

   private DisguiseMenu(Player player, String role) {
      super(player, buildTitle(player, role), 5);
      this.selectedRole = normalizeRole(role);
      this.fillBackground();
      if (this.selectedRole == null) {
         this.populateRoleMenu();
      } else {
         this.populateSkinMenu();
      }

      this.register(Core.getInstance());
      this.open();
   }

   private static String buildTitle(Player player, String role) {
      Profile profile = Profile.getProfile(player.getName());
      String normalized = normalizeRole(role);
      if (normalized == null) {
         return LanguageManager.get(profile, "commands.fake.menu.role.title", "&8Disguise &0&l> &8Role");
      }

      Role roleData = Role.getRoleByName(normalized);
      return LanguageManager.get(profile,
            "commands.fake.menu.skin.title",
            "&8Disguise &0&l> &8Skin &7({role})",
            "role", roleData != null ? roleData.getName() : normalized);
   }

   private static String normalizeRole(String role) {
      if (role == null || role.trim().isEmpty()) {
         return null;
      }

      List<String> roles = FakeManager.getConfiguredRoles();
      for (String configured : roles) {
         if (configured != null && configured.equalsIgnoreCase(role.trim())) {
            return configured;
         }
      }

      return null;
   }

   private void fillBackground() {
      ItemStack filler = BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1 : name>&r");
      for (int slot = 0; slot < this.getInventory().getSize(); ++slot) {
         this.setItem(slot, filler.clone());
      }
   }

   private void populateRoleMenu() {
      Profile profile = Profile.getProfile(this.player.getName());
      this.actions.clear();

      String info = LanguageManager.get(profile,
            "commands.fake.menu.role.info",
            "BOOK : 1 : name>&6&lChoose Role : desc>&7Select the role prefix for your disguise.");
      this.setItem(4, BukkitUtils.deserializeItemStack(info));

      int index = 0;
      for (String roleName : FakeManager.getConfiguredRoles()) {
         Role role = Role.getRoleByName(roleName);
         if (role == null) {
            continue;
         }
         if (index >= ROLE_SLOTS.length) {
            break;
         }

         int slot = ROLE_SLOTS[index++];
         String item = LanguageManager.get(profile,
               "commands.fake.menu.role.entry",
               "NAME_TAG : 1 : name>&e{role_display} : desc>&7Prefix: {role_prefix}\n&7Role ID: &f{role_id}\n\n&aClick to continue.",
               "role_display", role.getName(),
               "role_prefix", role.getPrefix(),
               "role_id", roleName);
         this.setItem(slot, BukkitUtils.deserializeItemStack(item));
         this.actions.put(slot, ACTION_ROLE_PREFIX + roleName);
      }

      if (index == 0) {
         String none = LanguageManager.get(profile,
               "commands.fake.menu.role.empty",
               "BARRIER : 1 : name>&cNo Roles Available : desc>&7No disguise roles are configured.");
         this.setItem(22, BukkitUtils.deserializeItemStack(none));
      }

      if (FakeManager.isFake(this.player.getName())) {
         String clear = LanguageManager.get(profile,
               "commands.fake.menu.clear.item",
               "REDSTONE : 1 : name>&cRemove Disguise : desc>&7Return to your normal identity.\n\n&eClick to remove.");
         this.setItem(38, BukkitUtils.deserializeItemStack(clear));
         this.actions.put(38, ACTION_CLEAR);
      }

      String close = LanguageManager.get(profile,
            "commands.fake.menu.close.item",
            "BARRIER : 1 : name>&cClose");
      this.setItem(40, BukkitUtils.deserializeItemStack(close));
      this.actions.put(40, ACTION_CLOSE);
   }

   private void populateSkinMenu() {
      Profile profile = Profile.getProfile(this.player.getName());
      this.actions.clear();

      Role role = Role.getRoleByName(this.selectedRole);
      String roleItem = LanguageManager.get(profile,
            "commands.fake.menu.skin.role-info",
            "BOOK : 1 : name>&6&lSelected Role : desc>&7Role: {role}\n&7Prefix: {prefix}",
            "role", role != null ? role.getName() : this.selectedRole,
            "prefix", role != null ? role.getPrefix() : "");
      this.setItem(4, BukkitUtils.deserializeItemStack(roleItem));

      String steve = LanguageManager.get(profile,
            "commands.fake.menu.skin.option.steve",
            "SKULL_ITEM:3 : 1 : owner>Steve : name>&bSteve Skin : desc>&7Classic model.\n\n&aClick to apply.");
      this.setItem(20, BukkitUtils.deserializeItemStack(steve));
      this.actions.put(20, ACTION_SKIN_PREFIX + "steve");

      String self = LanguageManager.get(profile,
            "commands.fake.menu.skin.option.self",
            "SKULL_ITEM:3 : 1 : owner>{player} : name>&aYour Skin : desc>&7Use your current skin texture.\n\n&aClick to apply.",
            "player", this.player.getName());
      this.setItem(22, BukkitUtils.deserializeItemStack(self));
      this.actions.put(22, ACTION_SKIN_PREFIX + "self");

      String alex = LanguageManager.get(profile,
            "commands.fake.menu.skin.option.alex",
            "SKULL_ITEM:3 : 1 : owner>Alex : name>&dAlex Skin : desc>&7Slim model.\n\n&aClick to apply.");
      this.setItem(24, BukkitUtils.deserializeItemStack(alex));
      this.actions.put(24, ACTION_SKIN_PREFIX + "alex");

      if (FakeManager.isFake(this.player.getName())) {
         String clear = LanguageManager.get(profile,
               "commands.fake.menu.clear.item",
               "REDSTONE : 1 : name>&cRemove Disguise : desc>&7Return to your normal identity.\n\n&eClick to remove.");
         this.setItem(37, BukkitUtils.deserializeItemStack(clear));
         this.actions.put(37, ACTION_CLEAR);
      }

      String back = LanguageManager.get(profile,
            "commands.fake.menu.back.item",
            "ARROW : 1 : name>&eBack");
      this.setItem(39, BukkitUtils.deserializeItemStack(back));
      this.actions.put(39, ACTION_BACK);

      String close = LanguageManager.get(profile,
            "commands.fake.menu.close.item",
            "BARRIER : 1 : name>&cClose");
      this.setItem(41, BukkitUtils.deserializeItemStack(close));
      this.actions.put(41, ACTION_CLOSE);
   }

   private void handleSkinSelection(String skinType) {
      Profile profile = Profile.getProfile(this.player.getName());
      if (profile != null && profile.playingGame()) {
         LanguageManager.send(this.player,
               "commands.fake.in-game",
               "{prefix}&cYou cannot use this command while in a game.",
               "prefix", getCorePrefix(profile));
         this.player.closeInventory();
         return;
      }

      if (!FakeManager.applyRandomFake(this.player, this.selectedRole, skinType)) {
         EnumSound.VILLAGER_NO.play(this.player, 1.0F, 1.0F);
         LanguageManager.send(this.player,
               "commands.fake.names-unavailable",
               "{prefix}&cNo disguise names are currently available.",
               "prefix", getDisguisePrefix(profile));
         return;
      }

      EnumSound.ORB_PICKUP.play(this.player, 1.0F, 2.0F);
      LanguageManager.send(this.player,
            "commands.fake.menu.applied",
            "{prefix}&aDisguise applied successfully.",
            "prefix", getDisguisePrefix(profile));
      this.player.closeInventory();
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (!event.getWhoClicked().equals(this.player)) {
         return;
      }
      if (!event.getView().getTopInventory().equals(this.getInventory())) {
         return;
      }
      event.setCancelled(true);
      event.setResult(Event.Result.DENY);

      int rawSlot = event.getRawSlot();
      if (rawSlot < 0) {
         return;
      }
      int topSize = event.getView().getTopInventory().getSize();
      if (rawSlot >= topSize) {
         return;
      }
      if (event.getClickedInventory() == null || !event.getClickedInventory().equals(this.getInventory())) {
         return;
      }

      ItemStack item = event.getCurrentItem();
      if (item == null || item.getType() == Material.AIR) {
         return;
      }

      String action = this.actions.get(event.getSlot());
      if (action == null) {
         EnumSound.ITEM_PICKUP.play(this.player, 0.5F, 2.0F);
         return;
      }

      if (ACTION_CLOSE.equals(action)) {
         this.player.closeInventory();
         return;
      }

      if (ACTION_BACK.equals(action)) {
         EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
         openRoleMenu(this.player);
         return;
      }

      if (ACTION_CLEAR.equals(action)) {
         EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
         FakeManager.removeFake(this.player);
         this.player.closeInventory();
         return;
      }

      if (action.startsWith(ACTION_ROLE_PREFIX)) {
         String roleName = action.substring(ACTION_ROLE_PREFIX.length());
         EnumSound.CLICK.play(this.player, 0.5F, 2.0F);
         openSkinMenu(this.player, roleName);
         return;
      }

      if (action.startsWith(ACTION_SKIN_PREFIX)) {
         String skinType = action.substring(ACTION_SKIN_PREFIX.length()).toLowerCase(Locale.ROOT);
         this.handleSkinSelection(skinType);
      }
   }

   @EventHandler
   public void onInventoryDrag(InventoryDragEvent event) {
      if (!event.getWhoClicked().equals(this.player)) {
         return;
      }
      if (!event.getView().getTopInventory().equals(this.getInventory())) {
         return;
      }
      event.setCancelled(true);
      event.setResult(Event.Result.DENY);
      event.setCursor(null);
      event.getWhoClicked().setItemOnCursor(null);
   }

   @EventHandler
   public void onInventoryClose(InventoryCloseEvent event) {
      if (event.getPlayer().equals(this.player) && event.getInventory().equals(this.getInventory())) {
         this.cancel();
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      if (event.getPlayer().equals(this.player)) {
         this.cancel();
      }
   }

   public void cancel() {
      HandlerList.unregisterAll(this);
   }

   private static String getCorePrefix(Profile profile) {
      return LanguageManager.get(profile, "prefix.lobby", "&3Lobby &8>> ");
   }

   private static String getDisguisePrefix(Profile profile) {
      return LanguageManager.get(profile, "prefix.disguise", "&3Disguise &8>> ");
   }
}
