package dev.zonely.whiteeffect.booster;

import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.player.Profile;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Booster {
   private static final Map<String, NetworkBooster> CACHE = new HashMap();
   private double multiplier;
   private long hours;

   public Booster(double multiplier, long hours) {
      this.multiplier = multiplier;
      this.hours = hours;
   }

   public static Booster parseBooster(String toParse) {
      String[] splitted = toParse.split(":");
      return splitted.length < 2 ? null : new Booster(Double.parseDouble(splitted[0]), Long.parseLong(splitted[1]));
   }

   public static void setupBoosters() {
      Database.getInstance().setupBoosters();
   }

   public static boolean setNetworkBooster(String id, Profile profile, Booster booster) {
      NetworkBooster nb = getNetworkBooster(id);
      if (nb != null) {
         return false;
      } else {
         profile.getBoostersContainer().removeBooster(Booster.BoosterType.NETWORK, booster);
         nb = new NetworkBooster(profile.getName(), booster.getMultiplier(), System.currentTimeMillis() + TimeUnit.HOURS.toMillis(booster.getHours()));
         Database.getInstance().setBooster(id, nb.getBooster(), nb.getMultiplier(), nb.getExpires());
         CACHE.put(id, nb);
         return true;
      }
   }

   public static NetworkBooster getNetworkBooster(String id) {
      NetworkBooster nb = (NetworkBooster)CACHE.get(id);
      if (nb != null) {
         if (nb.getExpires() > System.currentTimeMillis()) {
            return nb;
         }

         nb.gc();
         CACHE.remove(id);
      }

      nb = Database.getInstance().getBooster(id);
      if (nb != null) {
         CACHE.put(id, nb);
      }

      return nb;
   }

   public void gc() {
      this.multiplier = 0.0D;
      this.hours = 0L;
   }

   public double getMultiplier() {
      return this.multiplier;
   }

   public long getHours() {
      return this.hours;
   }

   public String toString() {
      return this.multiplier + ":" + this.hours;
   }

   public static enum BoosterType {
      PRIVATE,
      NETWORK;
   }
}
