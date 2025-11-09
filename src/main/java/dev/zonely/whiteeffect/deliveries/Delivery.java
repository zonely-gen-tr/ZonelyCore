package dev.zonely.whiteeffect.deliveries;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import dev.zonely.whiteeffect.utils.StringUtils;
import dev.zonely.whiteeffect.utils.TimeUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Delivery {
   private static final List<Delivery> DELIVERIES = new ArrayList();
   private final long id;
   private final int days;
   private final int slot;
   private final String permission;
   private final List<DeliveryReward> rewards;
   private final String icon;
   private final String message;

   public Delivery(int days, int slot, String permission, List<DeliveryReward> rewards, String icon, String message) {
      this.id = (long)DELIVERIES.size();
      this.days = days;
      this.slot = slot;
      this.permission = permission;
      this.rewards = rewards;
      this.icon = icon;
      this.message = StringUtils.formatColors(message);
   }

   public static void setupDeliveries() {
      WConfig config = Core.getInstance().getConfig("config");
      Iterator var1 = config.getSection("deliveries").getKeys(false).iterator();

      while(var1.hasNext()) {
         String key = (String)var1.next();
         int slot = config.getInt("deliveries." + key + ".slot");
         int days = config.getInt("deliveries." + key + ".days");
         String permission = config.getString("deliveries." + key + ".permission");
         String icon = config.getString("deliveries." + key + ".icon");
         String message = config.getString("deliveries." + key + ".message");
         List<DeliveryReward> rewards = new ArrayList();
         Iterator var9 = config.getStringList("deliveries." + key + ".rewards").iterator();

         while(var9.hasNext()) {
            String reward = (String)var9.next();
            rewards.add(new DeliveryReward(reward));
         }

         DELIVERIES.add(new Delivery(days, slot, permission, rewards, icon, message));
      }

   }

   public static Collection<Delivery> listDeliveries() {
      return DELIVERIES;
   }

   public long getId() {
      return this.id;
   }

   public long getDays() {
      return TimeUnit.DAYS.toMillis((long)this.days);
   }

   public int getSlot() {
      return this.slot;
   }

   public boolean hasPermission(Player player) {
      return this.permission.isEmpty() || player.hasPermission(this.permission);
   }

   public List<DeliveryReward> listRewards() {
      return this.rewards;
   }

   public ItemStack getIcon(Profile profile) {
      Player player = profile.getPlayer();
      boolean permission = !this.hasPermission(player);
      boolean alreadyClaimed = profile.getDeliveriesContainer().alreadyClaimed(this.id);
      String desc;
      if (permission) {
         desc = LanguageManager.get(profile,
               "commands.deliveries.icon.permission-denied",
               "\n&4&l! &cOnly players with the required permission can claim this reward.");
      } else if (alreadyClaimed) {
         desc = LanguageManager.get(profile,
               "commands.deliveries.icon.cooldown",
               "\n&7 &8- &fTime left: &e{time}\n \n&4&l! &cWait until the cooldown expires to claim again.",
               "time", TimeUtils.getTimeUntil(profile.getDeliveriesContainer().getClaimTime(this.id)));
      } else {
         desc = LanguageManager.get(profile,
               "commands.deliveries.icon.ready",
               "\n&6&l! &eClick to collect this reward.");
      }

      ItemStack item = BukkitUtils.deserializeItemStack(this.icon.replace("{color}", !permission && !alreadyClaimed ? "&e" : "&4") + desc);
      if (!permission && alreadyClaimed) {
         String typeName = item.getType().name();
         if (typeName.equals("STAINED_CLAY") || typeName.endsWith("_TERRACOTTA")) {
            ItemStack claimedVariant = MaterialResolver.createItemStack("STAINED_CLAY:6");
            item.setType(claimedVariant.getType());
            MaterialResolver.applyLegacyData(item, claimedVariant.getDurability());
         } else if (typeName.equals("POTION")) {
            item.setType(Material.GLASS_BOTTLE);
            MaterialResolver.applyLegacyData(item, (short)0);
         }
      }

      return item;
   }
   public String getMessage() {
      return this.message;
   }
}


