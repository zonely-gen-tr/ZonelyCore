package dev.zonely.whiteeffect.utils.enums;

import dev.zonely.whiteeffect.utils.StringUtils;
import java.util.concurrent.ThreadLocalRandom;

public enum EnumRarity {
   EFSANEVI("§bEFSANEVI", 10),
   DESTANSI("§dDESTANSI", 25),
   ENDER("§6ENDER", 50),
   SIRADAN("§7SIRADAN", 100);

   private static final EnumRarity[] VALUES = values();
   private final String name;
   private final int percentage;

   private EnumRarity(String name, int percentage) {
      this.name = name;
      this.percentage = percentage;
   }

   public static EnumRarity getRandomRarity() {
      int random = ThreadLocalRandom.current().nextInt(100);
      EnumRarity[] var1 = VALUES;
      int var2 = var1.length;

      for(int var3 = 0; var3 < var2; ++var3) {
         EnumRarity rarity = var1[var3];
         if (random <= rarity.percentage) {
            return rarity;
         }
      }

      return SIRADAN;
   }

   public static EnumRarity fromName(String name) {
      EnumRarity[] var1 = VALUES;
      int var2 = var1.length;

      for(int var3 = 0; var3 < var2; ++var3) {
         EnumRarity rarity = var1[var3];
         if (rarity.name().equalsIgnoreCase(name)) {
            return rarity;
         }
      }

      return SIRADAN;
   }

   public String getName() {
      return this.name;
   }

   public String getColor() {
      return StringUtils.getFirstColor(this.getName());
   }

   public String getTagged() {
      return this.getColor() + "[" + StringUtils.stripColors(this.getName()) + "]";
   }
}
