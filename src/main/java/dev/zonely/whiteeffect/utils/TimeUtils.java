package dev.zonely.whiteeffect.utils;

import dev.zonely.whiteeffect.lang.LanguageManager;
import java.util.Calendar;

public class TimeUtils {

   public static boolean isNewYear() {
      Calendar cl = Calendar.getInstance();
      return cl.get(2) == 11 && cl.get(5) == 31 || cl.get(2) == 0 && cl.get(5) == 1;
   }

   public static boolean isChristmas() {
      Calendar cl = Calendar.getInstance();
      return cl.get(2) == 11 && (cl.get(5) == 24 || cl.get(5) == 25);
   }

   public static int getLastDayOfMonth(int month) {
      Calendar cl = Calendar.getInstance();
      cl.set(2, month - 1);
      return cl.getActualMaximum(5);
   }

   public static int getLastDayOfMonth() {
      return Calendar.getInstance().getActualMaximum(5);
   }

   public static long getExpireIn(int days) {
      Calendar cooldown = Calendar.getInstance();
      cooldown.set(10, 24);

      for (int day = 0; day < days - 1; ++day) {
         cooldown.add(10, 24);
      }

      cooldown.set(12, 0);
      cooldown.set(13, 0);
      return cooldown.getTimeInMillis();
   }

   public static String getTimeUntil(long epoch) {
      return getTimeUntil(epoch, true);
   }

   public static String getTimeUntil(long epoch, boolean seconds) {
      epoch -= System.currentTimeMillis();
      return getTime(epoch, seconds);
   }

   public static String getTime(long time) {
      return getTime(time, true);
   }

   public static String getTime(long time, boolean seconds) {
      long ms = time / 1000L;
      if (ms <= 0L) {
         return "";
      }

      StringBuilder result = new StringBuilder();
      long days = ms / 86400L;
      if (days > 0L) {
         appendUnit(result, days, "day", "day", "days");
         ms -= days * 86400L;
      }

      long hours = ms / 3600L;
      if (hours > 0L) {
         appendUnit(result, hours, "hour", "hour", "hours");
         ms -= hours * 3600L;
      }

      long minutes = ms / 60L;
      if (minutes > 0L) {
         appendUnit(result, minutes, "minute", "minute", "minutes");
         ms -= minutes * 60L;
      }

      if (seconds && ms > 0L) {
         appendUnit(result, ms, "second", "second", "seconds");
      }

      return result.toString();
   }

   private static void appendUnit(StringBuilder builder, long value, String unitKey,
                                  String singularDefault, String pluralDefault) {
      if (builder.length() > 0) {
         builder.append(' ');
      }
      builder.append(formatWithUnit(value, unitKey, singularDefault, pluralDefault));
   }

   public static String formatWithUnit(long value, String unitKey,
                                       String singularDefault, String pluralDefault) {
      String key = "time.unit." + unitKey + (value == 1 ? ".singular" : ".plural");
      String fallback = value == 1 ? singularDefault : pluralDefault;
      String label = LanguageManager.get(key, fallback);
      return value + " " + label;
   }

   public static String getPhrase(String phraseKey, String def) {
      return LanguageManager.get("time.phrase." + phraseKey, def);
   }
}
