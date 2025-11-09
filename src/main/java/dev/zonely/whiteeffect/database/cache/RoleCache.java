package dev.zonely.whiteeffect.database.cache;

import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RoleCache {
   private static final Map<String, Object[]> CACHE = new ConcurrentHashMap();

   public static void setCache(String playerName, String role, String realName) {
      Object[] array = new Object[]{System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30L), role, realName};
      CACHE.put(playerName.toLowerCase(), array);
   }

   public static String get(String playerName) {
      Object[] array = (Object[])CACHE.get(playerName.toLowerCase());
      return array == null ? null : array[1] + " : " + array[2];
   }

   public static boolean isPresent(String playerName) {
      return CACHE.containsKey(playerName.toLowerCase());
   }

   public static TimerTask clearCache() {
      return new TimerTask() {
         public void run() {
            RoleCache.CACHE.entrySet().removeIf((entry) -> {
               return (Long)((Object[])entry.getValue())[0] < System.currentTimeMillis();
            });
         }
      };
   }
}
