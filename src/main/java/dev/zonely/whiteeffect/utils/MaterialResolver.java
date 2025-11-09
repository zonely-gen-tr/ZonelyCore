package dev.zonely.whiteeffect.utils;

import java.lang.reflect.Method;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class MaterialResolver {
   private static final boolean MODERN_MATERIALS = Material.getMaterial("PLAYER_HEAD") != null;
   private static final Method GET_MATERIAL_BY_ID;
   private static final String[] STAINED_GLASS_PANE_MAP = new String[]{
      "WHITE_STAINED_GLASS_PANE",
      "ORANGE_STAINED_GLASS_PANE",
      "MAGENTA_STAINED_GLASS_PANE",
      "LIGHT_BLUE_STAINED_GLASS_PANE",
      "YELLOW_STAINED_GLASS_PANE",
      "LIME_STAINED_GLASS_PANE",
      "PINK_STAINED_GLASS_PANE",
      "GRAY_STAINED_GLASS_PANE",
      "LIGHT_GRAY_STAINED_GLASS_PANE",
      "CYAN_STAINED_GLASS_PANE",
      "PURPLE_STAINED_GLASS_PANE",
      "BLUE_STAINED_GLASS_PANE",
      "BROWN_STAINED_GLASS_PANE",
      "GREEN_STAINED_GLASS_PANE",
      "RED_STAINED_GLASS_PANE",
      "BLACK_STAINED_GLASS_PANE"
   };
   private static final String[] TERRACOTTA_MAP = new String[]{
      "WHITE_TERRACOTTA",
      "ORANGE_TERRACOTTA",
      "MAGENTA_TERRACOTTA",
      "LIGHT_BLUE_TERRACOTTA",
      "YELLOW_TERRACOTTA",
      "LIME_TERRACOTTA",
      "PINK_TERRACOTTA",
      "GRAY_TERRACOTTA",
      "LIGHT_GRAY_TERRACOTTA",
      "CYAN_TERRACOTTA",
      "PURPLE_TERRACOTTA",
      "BLUE_TERRACOTTA",
      "BROWN_TERRACOTTA",
      "GREEN_TERRACOTTA",
      "RED_TERRACOTTA",
      "BLACK_TERRACOTTA"
   };
   private static final String[] SKULL_MAP = new String[]{
      "SKELETON_SKULL",
      "WITHER_SKELETON_SKULL",
      "ZOMBIE_HEAD",
      "PLAYER_HEAD",
      "CREEPER_HEAD",
      "DRAGON_HEAD"
   };
   private static final String[] DOUBLE_PLANT_MAP = new String[]{
      "SUNFLOWER",
      "LILAC",
      "TALL_GRASS",
      "LARGE_FERN",
      "ROSE_BUSH",
      "PEONY"
   };
   private static final String[] INK_SACK_MAP = new String[]{
      "INK_SAC",
      "RED_DYE",
      "GREEN_DYE",
      "COCOA_BEANS",
      "LAPIS_LAZULI",
      "PURPLE_DYE",
      "CYAN_DYE",
      "LIGHT_GRAY_DYE",
      "GRAY_DYE",
      "PINK_DYE",
      "LIME_DYE",
      "YELLOW_DYE",
      "LIGHT_BLUE_DYE",
      "MAGENTA_DYE",
      "ORANGE_DYE",
      "BONE_MEAL"
   };

   static {
      Method method = null;

      try {
         method = Material.class.getDeclaredMethod("getMaterial", int.class);
      } catch (NoSuchMethodException ignored) {
      }

      GET_MATERIAL_BY_ID = method;
   }

   private MaterialResolver() {
   }

   public static ItemStack createItemStack(String token) {
      ParsedToken parsed = ParsedToken.of(token);
      Material material = resolveMaterial(parsed);
      if (material == null) {
         throw new IllegalArgumentException("Cannot resolve material from token '" + token + "'.");
      } else {
         ItemStack stack = new ItemStack(material, 1);
         if (!MODERN_MATERIALS && parsed.data != null) {
            stack.setDurability(parsed.data);
         }

         return stack;
      }
   }

   public static ItemStack createItemStack(String token, int amount) {
      ItemStack stack = createItemStack(token);
      stack.setAmount(amount);
      return stack;
   }

   public static Material resolveMaterial(String token) {
      ParsedToken parsed = ParsedToken.of(token);
      return resolveMaterial(parsed);
   }

   public static boolean isModernMaterials() {
      return MODERN_MATERIALS;
   }

   public static void applyLegacyData(ItemStack stack, short data) {
      if (!MODERN_MATERIALS) {
         stack.setDurability(data);
      }
   }

   private static Material resolveMaterial(ParsedToken parsed) {
      Material direct = Material.matchMaterial(parsed.name);
      if (direct != null) {
         return direct;
      }

      return MODERN_MATERIALS ? resolveModern(parsed) : resolveLegacy(parsed);
   }

   private static Material resolveLegacy(ParsedToken parsed) {
      if (parsed.numeric && GET_MATERIAL_BY_ID != null) {
         try {
            return (Material)GET_MATERIAL_BY_ID.invoke((Object)null, parsed.numericId);
         } catch (Exception ignored) {
         }
      }

      return null;
   }

   private static Material resolveModern(ParsedToken parsed) {
      if (parsed.numeric) {
         Material fromId = resolveModernById(parsed.numericId, parsed.data);
         if (fromId != null) {
            return fromId;
         }
      }

      return resolveModernByName(parsed.name, parsed.data);
   }

   private static Material resolveModernById(int id, Short data) {
      switch (id) {
         case 27:
            return valueOf("POWERED_RAIL");
         case 28:
            return valueOf("DETECTOR_RAIL");
         case 101:
            return valueOf("IRON_BARS");
         case 116:
            return valueOf("ENCHANTING_TABLE");
         case 158:
            return valueOf("DROPPER");
         case 159:
            return resolveColorMapped(TERRACOTTA_MAP, "TERRACOTTA", data);
         case 175:
            return resolveColorMapped(DOUBLE_PLANT_MAP, "SUNFLOWER", data);
         case 323:
            return valueOf("OAK_SIGN");
         case 324:
            return valueOf("OAK_DOOR");
         case 329:
            return valueOf("SADDLE");
         case 336:
            return valueOf("BRICK");
         case 339:
            return valueOf("BOOK");
         case 357:
            return valueOf("COOKIE");
         case 379:
            return valueOf("BREWING_STAND");
         case 380:
            return valueOf("CAULDRON");
         case 384:
            return valueOf("EXPERIENCE_BOTTLE");
         case 404:
            return valueOf("COMPARATOR");
         case 407:
            return valueOf("COMMAND_BLOCK_MINECART");
         default:
            return null;
      }
   }

   private static Material resolveModernByName(String name, Short data) {
      String upper = name.toUpperCase(Locale.ROOT);
      switch (upper) {
         case "WOOD_DOOR":
         case "WOODEN_DOOR":
            return valueOf("OAK_DOOR");
         case "SIGN":
            return valueOf("OAK_SIGN");
         case "SKULL_ITEM":
         case "PLAYER_HEAD":
            return resolveColorMapped(SKULL_MAP, "PLAYER_HEAD", data);
         case "STAINED_GLASS_PANE":
            return resolveColorMapped(STAINED_GLASS_PANE_MAP, "WHITE_STAINED_GLASS_PANE", data);
         case "STAINED_CLAY":
         case "HARD_CLAY":
            return resolveColorMapped(TERRACOTTA_MAP, "TERRACOTTA", data);
         case "DOUBLE_PLANT":
            return resolveColorMapped(DOUBLE_PLANT_MAP, "SUNFLOWER", data);
         case "EXP_BOTTLE":
            return valueOf("EXPERIENCE_BOTTLE");
         case "INK_SACK":
            return resolveColorMapped(INK_SACK_MAP, "INK_SAC", data);
         case "BOOK_AND_QUILL":
            return valueOf("WRITABLE_BOOK");
         case "REDSTONE_COMPARATOR":
            return valueOf("COMPARATOR");
         default:
            return null;
      }
   }

   private static Material resolveColorMapped(String[] map, String fallback, Short data) {
      if (data != null && data >= 0 && data < map.length) {
         Material material = valueOf(map[data]);
         if (material != null) {
            return material;
         }
      }

      return valueOf(fallback);
   }

   private static Material valueOf(String name) {
      if (name == null) {
         return null;
      }

      try {
         return Material.valueOf(name);
      } catch (IllegalArgumentException var2) {
         return null;
      }
   }

   private static final class ParsedToken {
      private final String name;
      private final Short data;
      private final boolean numeric;
      private final int numericId;

      private ParsedToken(String name, Short data, boolean numeric, int numericId) {
         this.name = name;
         this.data = data;
         this.numeric = numeric;
         this.numericId = numericId;
      }

      private static ParsedToken of(String token) {
         String trimmed = token.trim();
         String[] parts = trimmed.split(":");
         String rawName = parts[0].trim();
         Short data = parts.length > 1 && isInteger(parts[1]) ? Short.parseShort(parts[1]) : null;
         boolean numeric = isInteger(rawName);
         int numericId = numeric ? Integer.parseInt(rawName) : -1;
         return new ParsedToken(rawName.toUpperCase(Locale.ROOT), data, numeric, numericId);
      }

      private static boolean isInteger(String value) {
         if (value == null || value.isEmpty()) {
            return false;
         } else {
            for(int i = 0; i < value.length(); ++i) {
               if (!Character.isDigit(value.charAt(i))) {
                  return false;
               }
            }

            return true;
         }
      }
   }
}
