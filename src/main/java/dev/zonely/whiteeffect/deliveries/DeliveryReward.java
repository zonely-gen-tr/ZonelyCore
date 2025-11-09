package dev.zonely.whiteeffect.deliveries;

import dev.zonely.whiteeffect.booster.Booster;
import dev.zonely.whiteeffect.player.Profile;
import org.bukkit.Bukkit;
import dev.zonely.whiteeffect.cash.CashManager;

public class DeliveryReward {
   private DeliveryReward.RewardType type;
   private Object[] values;

   public DeliveryReward(String reward) {
      if (reward == null) {
         reward = "";
      }

      String[] splitter = reward.split(">");
      DeliveryReward.RewardType type = DeliveryReward.RewardType.from(splitter[0]);
      if (type != null && reward.replace(splitter[0] + ">", "").split(":").length >= type.getParameters()) {
         this.type = type;

         try {
            this.values = type.parseValues(reward.replace(splitter[0] + ">", ""));
         } catch (Exception var5) {
            var5.printStackTrace();
            this.type = DeliveryReward.RewardType.KOMUT;
            this.values = new Object[]{"tell {name} §b" + reward + "§c ödülü geçersiz."};
         }

      } else {
         this.type = DeliveryReward.RewardType.KOMUT;
         this.values = new Object[]{"tell {name} §b" + reward + "§c ödülü geçersiz."};
      }
   }

   public void dispatch(Profile profile) {
      if (this.type == DeliveryReward.RewardType.KOMUT) {
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), ((String)this.values[0]).replace("{name}", profile.getName()));
      } else if (this.type == DeliveryReward.RewardType.CASH) {
         profile.setStats("ZonelyCoreProfile", CashManager.getCash(profile) + (Long)this.values[0], "cash");
      } else if (this.type.name().contains("_COINS")) {
         profile.addCoins("ZonelyCore" + this.type.name().replace("_COINS", ""), (Double)this.values[0]);
      } else if (this.type.name().contains("_BOOSTER")) {
         for(int i = 0; i < (Integer)this.values[0]; ++i) {
            profile.getBoostersContainer().addBooster(Booster.BoosterType.valueOf(this.type.name().replace("_BOOSTER", "")), (Double)this.values[1], (Long)this.values[2]);
         }
      }

   }

   private static enum RewardType {
      KOMUT(1),
      CASH(1),
      SkyWars_COINS(1),
      BedWars_Coins(1),
      TheBridge_COINS(1),
      Murder_COINS(1),
      PRIVATE_BOOSTER(3),
      NETWORK_BOOSTER(3);

      private final int parameters;

      private RewardType(int parameters) {
         this.parameters = parameters;
      }

      public static DeliveryReward.RewardType from(String name) {
         DeliveryReward.RewardType[] var1 = values();
         int var2 = var1.length;

         for(int var3 = 0; var3 < var2; ++var3) {
            DeliveryReward.RewardType type = var1[var3];
            if (type.name().equalsIgnoreCase(name)) {
               return type;
            }
         }

         return null;
      }

      public int getParameters() {
         return this.parameters;
      }

      public Object[] parseValues(String value) throws Exception {
         if (this == KOMUT) {
            return new Object[]{value};
         } else if (this == CASH) {
            return new Object[]{Long.parseLong(value)};
         } else if (this.name().contains("_COINS")) {
            return new Object[]{Double.parseDouble(value)};
         } else if (this.name().contains("_BOOSTER")) {
            String[] values = value.split(":");
            return new Object[]{Integer.parseInt(values[0]), Double.parseDouble(values[1]), Long.parseLong(values[2])};
         } else {
            throw new Exception();
         }
      }
   }
}
