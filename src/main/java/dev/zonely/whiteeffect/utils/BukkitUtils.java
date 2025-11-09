package dev.zonely.whiteeffect.utils;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.libraries.menu.text.MenuText;
import dev.zonely.whiteeffect.libraries.menu.text.MenuTextApplier;
import dev.zonely.whiteeffect.reflection.Accessors;
import dev.zonely.whiteeffect.reflection.MinecraftReflection;
import dev.zonely.whiteeffect.reflection.acessors.ConstructorAccessor;
import dev.zonely.whiteeffect.reflection.acessors.FieldAccessor;
import dev.zonely.whiteeffect.reflection.acessors.MethodAccessor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class BukkitUtils {
   public static final List<FieldAccessor<Color>> COLORS;
   public static final FieldAccessor<GameProfile> SKULL_META_PROFILE;
   private static final Method SKULL_PROFILE_SETTER;
   private static final Map<Class<?>, MethodAccessor> getHandleCache = new HashMap();
   private static final Class<?> NBTagList = resolveNbtClass("net.minecraft.nbt.ListTag", "net.minecraft.nbt.NbtList", "NBTTagList");
   private static final Class<?> NBTagString = resolveNbtClass("net.minecraft.nbt.StringTag", "net.minecraft.nbt.NbtString", "NBTTagString");
   private static final ConstructorAccessor<?> constructorTagList;
   private static final ConstructorAccessor<?> constructorTagString;
   private static final MethodAccessor getTag;
   private static final MethodAccessor setTagMethod;
   private static final MethodAccessor setCompound;
   private static final MethodAccessor addList;
   private static final MethodAccessor asNMSCopy;
   private static final MethodAccessor asCraftMirror;
   private static final Method PLAYER_INV_GET_MAIN_HAND;
   private static final Method PLAYER_INV_SET_MAIN_HAND;
   private static final Method PLAYER_GET_ITEM_IN_HAND;
   private static final Method PLAYER_SET_ITEM_IN_HAND;
   private static final Method PLAYER_INV_GET_ITEM_IN_HAND;
   private static final Method PLAYER_INV_SET_ITEM_IN_HAND;

   public static Object getHandle(Object target) {
      try {
         Class<?> clazz = target.getClass();
         MethodAccessor accessor = (MethodAccessor)getHandleCache.get(clazz);
         if (accessor == null) {
            accessor = Accessors.getMethod(clazz, "getHandle");
            getHandleCache.put(clazz, accessor);
         }

         return accessor.invoke(target);
      } catch (Exception var3) {
         throw new IllegalArgumentException("Cannot find method getHandle() for " + target + ".");
      }
   }

   public static void openBook(Player player, ItemStack book) {
      Object entityPlayer = getHandle(player);
      ItemStack old = getItemInHand(player);

      try {
         setItemInHand(player, book);
         Method openBook = null;
         for (Method method : entityPlayer.getClass().getMethods()) {
            if ("openBook".equals(method.getName())) {
               int paramCount = method.getParameterCount();
               if (paramCount == 1 || paramCount == 2) {
                  openBook = method;
                  openBook.setAccessible(true);
                  break;
               }
            }
         }
         Object nmsBook = asNMSCopy(book);
         if (openBook != null) {
            Class<?>[] params = openBook.getParameterTypes();
            if (params.length == 1) {
               openBook.invoke(entityPlayer, nmsBook);
            } else if (params.length == 2 || params.length == 3) {
               Object enumHand = null;
               Class<?> handClass = params[1];
               if (handClass.isEnum()) {
                  try {
                     enumHand = java.lang.Enum.valueOf((Class) handClass, "MAIN_HAND");
                  } catch (IllegalArgumentException ignored) {
                     Object[] constants = handClass.getEnumConstants();
                     if (constants != null && constants.length > 0) {
                        enumHand = constants[0];
                     }
                  }
               }
               if (enumHand != null) {
                  if (params.length == 2) {
                     openBook.invoke(entityPlayer, nmsBook, enumHand);
                  } else {
                     Object third = params[2].equals(Boolean.TYPE) || params[2].equals(Boolean.class)
                           ? Boolean.FALSE
                           : null;
                     openBook.invoke(entityPlayer, nmsBook, enumHand, third);
                  }
               } else {
                  openBook.invoke(entityPlayer, nmsBook);
               }
            } else {
               openBook.invoke(entityPlayer, nmsBook);
            }
         } else {
            Accessors.getMethod(entityPlayer.getClass(), "openBook").invoke(entityPlayer, nmsBook);
         }
      } catch (Exception var5) {
         var5.printStackTrace();
      }

      setItemInHand(player, old);
      dev.zonely.whiteeffect.nms.util.SafeInventoryUpdater.update(player);
   }

   public static ItemStack getItemInHand(Player player) {
      PlayerInventory inventory = player.getInventory();

      try {
         if (PLAYER_INV_GET_MAIN_HAND != null) {
            return (ItemStack)PLAYER_INV_GET_MAIN_HAND.invoke(inventory);
         }
      } catch (Exception ignored) {
      }

      try {
         if (PLAYER_GET_ITEM_IN_HAND != null) {
            return (ItemStack)PLAYER_GET_ITEM_IN_HAND.invoke(player);
         }
      } catch (Exception ignored) {
      }

      try {
         if (PLAYER_INV_GET_ITEM_IN_HAND != null) {
            return (ItemStack)PLAYER_INV_GET_ITEM_IN_HAND.invoke(inventory);
         }
      } catch (Exception ignored) {
      }

      return inventory.getItem(inventory.getHeldItemSlot());
   }

   public static void setItemInHand(Player player, ItemStack item) {
      PlayerInventory inventory = player.getInventory();

      try {
         if (PLAYER_INV_SET_MAIN_HAND != null) {
            PLAYER_INV_SET_MAIN_HAND.invoke(inventory, item);
            return;
         }
      } catch (Exception ignored) {
      }

      try {
         if (PLAYER_SET_ITEM_IN_HAND != null) {
            PLAYER_SET_ITEM_IN_HAND.invoke(player, item);
            return;
         }
      } catch (Exception ignored) {
      }

      try {
         if (PLAYER_INV_SET_ITEM_IN_HAND != null) {
            PLAYER_INV_SET_ITEM_IN_HAND.invoke(inventory, item);
            return;
         }
      } catch (Exception ignored) {
      }

      inventory.setItem(inventory.getHeldItemSlot(), item);
   }

   public static ItemStack deserializeItemStack(String item) {
      if (item != null && !item.isEmpty()) {
         item = StringUtils.formatColors(item).replace("\\n", "\n");
         String[] split = item.split(" : ");
         ItemStack stack = MaterialResolver.createItemStack(split[0]);

         ItemMeta meta = stack.getItemMeta();
         BookMeta book = meta instanceof BookMeta ? (BookMeta)meta : null;
         SkullMeta skull = meta instanceof SkullMeta ? (SkullMeta)meta : null;
         PotionMeta potion = meta instanceof PotionMeta ? (PotionMeta)meta : null;
         FireworkEffectMeta effect = meta instanceof FireworkEffectMeta ? (FireworkEffectMeta)meta : null;
         LeatherArmorMeta armor = meta instanceof LeatherArmorMeta ? (LeatherArmorMeta)meta : null;
         EnchantmentStorageMeta enchantment = meta instanceof EnchantmentStorageMeta ? (EnchantmentStorageMeta)meta : null;
         if (split.length > 1) {
            stack.setAmount(Math.min(Integer.parseInt(split[1]), 64));
         }

         List<MenuText> lore = new ArrayList();

         for(int i = 2; i < split.length; ++i) {
            String opt = split[i];
            if (opt.startsWith("name>")) {
               String rawName = opt.substring(opt.indexOf('>') + 1);
               MenuText nameText = MenuText.parse(rawName);
               MenuTextApplier.applyDisplayName(meta, nameText);
            } else {
               String[] flags;
               int var15;
               int var16;
               String pe;
               if (opt.startsWith("desc>")) {
                  flags = opt.substring(opt.indexOf('>') + 1).split("\n");
                  var15 = flags.length;

                  for(var16 = 0; var16 < var15; ++var16) {
                     pe = flags[var16];
                     lore.add(MenuText.parse(pe));
                  }
               } else if (opt.startsWith("enchant>")) {
                  flags = opt.split(">")[1].split("\n");
                  var15 = flags.length;

                  for(var16 = 0; var16 < var15; ++var16) {
                     pe = flags[var16];
                     if (enchantment != null) {
                        enchantment.addStoredEnchant(Enchantment.getByName(pe.split(":")[0]), Integer.parseInt(pe.split(":")[1]), true);
                     } else {
                        meta.addEnchant(Enchantment.getByName(pe.split(":")[0]), Integer.parseInt(pe.split(":")[1]), true);
                     }
                  }
               } else if (!opt.startsWith("color>") || effect == null && armor == null) {
                  if (opt.startsWith("owner>") && skull != null) {
                     skull.setOwner(opt.split(">")[1]);
                  } else if (opt.startsWith("skin>") && skull != null) {
                     String texture = opt.split(">")[1];
                     String profileName = ("Skin" + Integer.toHexString(texture.hashCode())).substring(0, Math.min(16, ("Skin" + Integer.toHexString(texture.hashCode())).length()));
                     GameProfile gp = new GameProfile(UUID.randomUUID(), profileName);
                     gp.getProperties().put("textures", new Property("textures", texture));
                     applySkullProfile(skull, gp);
                  } else if (opt.startsWith("page>") && book != null) {
                     book.setPages(opt.split(">")[1].split("\\{sayfalar}"));
                  } else if (opt.startsWith("author>") && book != null) {
                     book.setAuthor(opt.split(">")[1]);
                  } else if (opt.startsWith("title>") && book != null) {
                     book.setTitle(opt.split(">")[1]);
                  } else if (opt.startsWith("effect>") && potion != null) {
                     flags = opt.split(">")[1].split("\n");
                     var15 = flags.length;

                     for(var16 = 0; var16 < var15; ++var16) {
                        pe = flags[var16];
                        potion.addCustomEffect(new PotionEffect(PotionEffectType.getByName(pe.split(":")[0]), Integer.parseInt(pe.split(":")[2]), Integer.parseInt(pe.split(":")[1])), false);
                     }
                  } else if (opt.startsWith("hide>")) {
                     flags = opt.split(">")[1].split("\n");
                     String[] var21 = flags;
                     var16 = flags.length;

                     for(int var22 = 0; var22 < var16; ++var22) {
                        String flag = var21[var22];
                        if (flag.equalsIgnoreCase("genel")) {
                           meta.addItemFlags(ItemFlag.values());
                           break;
                        }

                        meta.addItemFlags(new ItemFlag[]{ItemFlag.valueOf(flag.toUpperCase())});
                     }
                  }
               } else {
                  flags = opt.split(">")[1].split("\n");
                  var15 = flags.length;

                  for(var16 = 0; var16 < var15; ++var16) {
                     pe = flags[var16];
                     if (pe.split(":").length > 2) {
                        if (armor != null) {
                           armor.setColor(Color.fromRGB(Integer.parseInt(pe.split(":")[0]), Integer.parseInt(pe.split(":")[1]), Integer.parseInt(pe.split(":")[2])));
                        } else if (effect != null) {
                           effect.setEffect(FireworkEffect.builder().withColor(Color.fromRGB(Integer.parseInt(pe.split(":")[0]), Integer.parseInt(pe.split(":")[1]), Integer.parseInt(pe.split(":")[2]))).build());
                        }
                     } else {
                        Iterator var18 = COLORS.iterator();

                        while(var18.hasNext()) {
                           FieldAccessor<Color> field = (FieldAccessor)var18.next();
                           if (field.getHandle().getName().equals(pe.toUpperCase())) {
                              if (armor != null) {
                                 armor.setColor((Color)field.get((Object)null));
                              } else if (effect != null) {
                                 effect.setEffect(FireworkEffect.builder().withColor((Color)field.get((Object)null)).build());
                              }
                              break;
                           }
                        }
                     }
                  }
               }
            }
         }

         if (!lore.isEmpty()) {
            MenuTextApplier.applyLore(meta, lore);
         }

         stack.setItemMeta(meta);
         return stack;
      } else {
         return new ItemStack(Material.AIR);
      }
   }

   public static String serializeItemStack(ItemStack item) {
      if (item == null) {
         return "AIR : 1";
      }

      Material type = item.getType();
      if (type == null) {
         type = Material.AIR;
      }

      StringBuilder sb = new StringBuilder(type.name() + (item.getDurability() != 0 ? ":" + item.getDurability() : "") + " : " + item.getAmount());
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return sb.toString();
      }
      BookMeta book = meta instanceof BookMeta ? (BookMeta)meta : null;
      SkullMeta skull = meta instanceof SkullMeta ? (SkullMeta)meta : null;
      PotionMeta potion = meta instanceof PotionMeta ? (PotionMeta)meta : null;
      FireworkEffectMeta effect = meta instanceof FireworkEffectMeta ? (FireworkEffectMeta)meta : null;
      LeatherArmorMeta armor = meta instanceof LeatherArmorMeta ? (LeatherArmorMeta)meta : null;
      EnchantmentStorageMeta enchantment = meta instanceof EnchantmentStorageMeta ? (EnchantmentStorageMeta)meta : null;
      if (meta.hasDisplayName()) {
         sb.append(" : name>").append(StringUtils.deformatColors(meta.getDisplayName()));
      }

      int size;
      if (meta.hasLore()) {
         sb.append(" : desc>");

         for(size = 0; size < meta.getLore().size(); ++size) {
            String line = (String)meta.getLore().get(size);
            sb.append(line).append(size + 1 == meta.getLore().size() ? "" : "\n");
         }
      }

      Iterator var14;
      StringBuilder var10000;
      if (meta.hasEnchants() || enchantment != null && enchantment.hasStoredEnchants()) {
         sb.append(" : enchant>");
         size = 0;
         var14 = (enchantment != null ? enchantment.getStoredEnchants() : meta.getEnchants()).entrySet().iterator();

         while(var14.hasNext()) {
            Entry<Enchantment, Integer> entry = (Entry)var14.next();
            int level = (Integer)entry.getValue();
            String name = ((Enchantment)entry.getKey()).getName();
            var10000 = sb.append(name).append(":").append(level);
            ++size;
            var10000.append(size == (enchantment != null ? enchantment.getStoredEnchants() : meta.getEnchants()).size() ? "" : "\n");
         }
      }

      if (skull != null && !skull.getOwner().isEmpty()) {
         sb.append(" : owner>").append(skull.getOwner());
      }

      if (book != null && book.hasPages()) {
         sb.append(" : page>").append(StringUtils.join((Collection)book.getPages(), "{sayfalar}"));
      }

      if (book != null && book.hasTitle()) {
         sb.append(" : title>").append(book.getTitle());
      }

      if (book != null && book.hasAuthor()) {
         sb.append(" : author>").append(book.getAuthor());
      }

      if (effect != null && effect.hasEffect() && !effect.getEffect().getColors().isEmpty() || armor != null && armor.getColor() != null) {
         Color color = effect != null ? (Color)effect.getEffect().getColors().get(0) : armor.getColor();
         sb.append(" : color>").append(color.getRed()).append(":").append(color.getGreen()).append(":").append(color.getBlue());
      }

      if (potion != null && potion.hasCustomEffects()) {
         sb.append(" : effect>");
         size = 0;
         var14 = potion.getCustomEffects().iterator();

         while(var14.hasNext()) {
            PotionEffect pe = (PotionEffect)var14.next();
            var10000 = sb.append(pe.getType().getName()).append(":").append(pe.getAmplifier()).append(":").append(pe.getDuration());
            ++size;
            var10000.append(size == potion.getCustomEffects().size() ? "" : "\n");
         }
      }

      Iterator var17 = meta.getItemFlags().iterator();

      while(var17.hasNext()) {
         ItemFlag flag = (ItemFlag)var17.next();
         sb.append(" : hide>").append(flag.name());
      }

      return StringUtils.deformatColors(sb.toString()).replace("\n", "\\n");
   }

   public static ItemStack putProfileOnSkull(Player player, ItemStack head) {
      if (head != null && head.getItemMeta() instanceof SkullMeta) {
         ItemMeta meta = head.getItemMeta();
         GameProfile profile = GameProfileResolver.get(player);
         applySkullProfile((SkullMeta)meta, profile);
         head.setItemMeta(meta);
      }
      return head;
   }

   private static void applySkullProfile(SkullMeta skull, GameProfile profile) {
      if (skull == null || profile == null) {
         return;
      }
      boolean applied = false;
      if (SKULL_PROFILE_SETTER != null) {
         try {
            SKULL_PROFILE_SETTER.invoke(skull, profile);
            applied = true;
         } catch (Throwable ignored) {
         }
      }
      if (!applied && SKULL_META_PROFILE != null) {
         try {
            SKULL_META_PROFILE.set(skull, profile);
            applied = true;
         } catch (Throwable ignored) {
         }
      }
   }

   public static ItemStack putProfileOnSkull(Object profile, ItemStack head) {
      if (head != null && head.getItemMeta() instanceof SkullMeta && profile instanceof GameProfile) {
         ItemMeta meta = head.getItemMeta();
         applySkullProfile((SkullMeta)meta, (GameProfile)profile);
         head.setItemMeta(meta);
      }
      return head;
   }

   public static void putGlowEnchantment(ItemStack item) {
      ItemMeta meta = item.getItemMeta();
      meta.addEnchant(Enchantment.LURE, 1, true);
      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
      item.setItemMeta(meta);
   }

   public static Object asNMSCopy(ItemStack item) {
      return asNMSCopy.invoke((Object)null, item);
   }

   public static ItemStack asCraftMirror(Object nmsItem) {
      return (ItemStack)asCraftMirror.invoke((Object)null, nmsItem);
   }

   public static ItemStack setNBTList(ItemStack item, String key, List<String> strings) {
      if (strings == null) {
         strings = Collections.emptyList();
      }
      if (constructorTagList == null || constructorTagString == null || getTag == null || setCompound == null || addList == null || setTagMethod == null) {
         return fallbackSetNbtList(item, key, strings);
      }
      try {
         Object nmsStack = asNMSCopy(item);
         Object compound = getTag.invoke(nmsStack);
         if (compound == null) {
            Class<?> compoundClass = MinecraftReflection.getNBTTagCompoundClass();
            if (compoundClass != null) {
               Object newCompound = compoundClass.getDeclaredConstructor().newInstance();
               setTagMethod.invoke(nmsStack, newCompound);
               compound = newCompound;
            }
         }
         if (compound == null) {
            return fallbackSetNbtList(item, key, strings);
         }
         Object compoundList = constructorTagList.newInstance();
         for (String string : strings) {
            addList.invoke(compoundList, constructorTagString.newInstance(string));
         }
         setCompound.invoke(compound, key, compoundList);
         return asCraftMirror(nmsStack);
      } catch (Throwable ex) {
         Core instance = Core.getInstance();
         if (instance != null) {
            instance.getLogger().warning("[BukkitUtils] Falling back while setting NBT list '" + key + "': " + ex.getMessage());
         }
         return fallbackSetNbtList(item, key, strings);
      }
   }

   public static String serializeLocation(Location unserialized) {
      return unserialized.getWorld().getName() + "; " + unserialized.getX() + "; " + unserialized.getY() + "; " + unserialized.getZ() + "; " + unserialized.getYaw() + "; " + unserialized.getPitch();
   }

   public static Location deserializeLocation(String serialized) {
      String[] divPoints = serialized.split("; ");
      Location deserialized = new Location(Bukkit.getWorld(divPoints[0]), Double.parseDouble(divPoints[1]), Double.parseDouble(divPoints[2]), Double.parseDouble(divPoints[3]));
      deserialized.setYaw(Float.parseFloat(divPoints[4]));
      deserialized.setPitch(Float.parseFloat(divPoints[5]));
      return deserialized;
   }

   static {
      COLORS = new ArrayList<>();
      constructorTagList = createConstructorAccessor(NBTagList);
      constructorTagString = createConstructorAccessor(NBTagString);

      MethodAccessor tmpGetTag = null;
      MethodAccessor tmpSetTag = null;
      MethodAccessor tmpSetCompound = resolveCompoundSetter();
      MethodAccessor tmpAddList = findAddListMethod(NBTagList);
      MethodAccessor tmpAsNMSCopy = null;
      MethodAccessor tmpAsCraftMirror = null;

      try {
         Class<?> craftItemStack = MinecraftReflection.getCraftItemStackClass();
         if (craftItemStack != null) {
            tmpAsNMSCopy = findMethod(craftItemStack, "asNMSCopy", ItemStack.class);
            Class<?> nmsItemStack = MinecraftReflection.getItemStackClass();
            if (nmsItemStack != null) {
               tmpAsCraftMirror = findMethod(craftItemStack, "asCraftMirror", nmsItemStack);
            }
         }
      } catch (Throwable ignored) {
      }

      try {
         Class<?> nmsItemStack = MinecraftReflection.getItemStackClass();
         if (nmsItemStack != null) {
            tmpGetTag = findMethod(nmsItemStack, "getTag");
            Class<?> compoundClass = MinecraftReflection.getNBTTagCompoundClass();
            if (compoundClass != null) {
               tmpSetTag = findMethod(nmsItemStack, "setTag", compoundClass);
            }
         }
      } catch (Throwable ignored) {
      }

      asNMSCopy = tmpAsNMSCopy;
      asCraftMirror = tmpAsCraftMirror;
      getTag = tmpGetTag;
      setTagMethod = tmpSetTag;
      setCompound = tmpSetCompound;
      addList = tmpAddList;

      Method invGetMainHand = null;
      Method invSetMainHand = null;
      Method playerGetHand = null;
      Method playerSetHand = null;
      Method invGetLegacy = null;
      Method invSetLegacy = null;

      for (Field field : Color.class.getDeclaredFields()) {
         if (field.getType().equals(Color.class)) {
            COLORS.add(new FieldAccessor<>(field));
         }
      }

      Method skullProfileSetter = null;
      for (String methodName : new String[]{"setPlayerProfile", "setOwnerProfile"}) {
         try {
            Method method = SkullMeta.class.getMethod(methodName, GameProfile.class);
            method.setAccessible(true);
            skullProfileSetter = method;
            break;
         } catch (Throwable ignored) {
         }
      }

      FieldAccessor<GameProfile> skullMetaProfileAccessor = null;
      try {
         Class<?> craftMetaSkull = MinecraftReflection.getCraftBukkitClass("inventory.CraftMetaSkull");
         if (craftMetaSkull != null) {
            skullMetaProfileAccessor = Accessors.getField(craftMetaSkull, "profile", GameProfile.class);
         }
      } catch (Throwable ignored) {
      }

      SKULL_PROFILE_SETTER = skullProfileSetter;
      SKULL_META_PROFILE = skullMetaProfileAccessor;

      try {
         invGetMainHand = PlayerInventory.class.getMethod("getItemInMainHand");
      } catch (NoSuchMethodException ignored) {
      }

      try {
         invSetMainHand = PlayerInventory.class.getMethod("setItemInMainHand", ItemStack.class);
      } catch (NoSuchMethodException ignored) {
      }

      try {
         playerGetHand = Player.class.getMethod("getItemInHand");
      } catch (NoSuchMethodException ignored) {
      }

      try {
         playerSetHand = Player.class.getMethod("setItemInHand", ItemStack.class);
      } catch (NoSuchMethodException ignored) {
      }

      try {
         invGetLegacy = PlayerInventory.class.getMethod("getItemInHand");
      } catch (NoSuchMethodException ignored) {
      }

      try {
         invSetLegacy = PlayerInventory.class.getMethod("setItemInHand", ItemStack.class);
      } catch (NoSuchMethodException ignored) {
      }

      PLAYER_INV_GET_MAIN_HAND = invGetMainHand;
      PLAYER_INV_SET_MAIN_HAND = invSetMainHand;
      PLAYER_GET_ITEM_IN_HAND = playerGetHand;
      PLAYER_SET_ITEM_IN_HAND = playerSetHand;
      PLAYER_INV_GET_ITEM_IN_HAND = invGetLegacy;
      PLAYER_INV_SET_ITEM_IN_HAND = invSetLegacy;
   }

   private static ItemStack fallbackSetNbtList(ItemStack item, String key, List<String> strings) {
      if ("pages".equalsIgnoreCase(key) && item != null) {
         ItemMeta meta = item.getItemMeta();
         if (meta instanceof BookMeta) {
            BookMeta book = (BookMeta) meta;
            List<BaseComponent[]> components = new ArrayList<>();
            List<String> legacyPages = new ArrayList<>();
            for (String entry : strings) {
               BaseComponent[] parsed;
               try {
                  parsed = ComponentSerializer.parse(entry);
               } catch (Throwable ignored) {
                  parsed = new BaseComponent[]{ new TextComponent(ChatColor.translateAlternateColorCodes('&', entry)) };
               }
               components.add(parsed);
               legacyPages.add(TextComponent.toLegacyText(parsed));
            }
            boolean applied = false;
            try {
               Object spigot = BookMeta.class.getMethod("spigot").invoke(book);
               if (spigot != null) {
                  Method setPages = spigot.getClass().getMethod("setPages", BaseComponent[][].class);
                  setPages.invoke(spigot, (Object) components.toArray(new BaseComponent[0][]));
                  applied = true;
               }
            } catch (Throwable ignored) {
            }
            if (!applied) {
               book.setPages(legacyPages);
            }
            item.setItemMeta(book);
         }
      }
      return item;
   }

   private static ConstructorAccessor<?> createConstructorAccessor(Class<?> type) {
      if (type == null) {
         return null;
      }
      try {
         Constructor<?>[] declared = type.getDeclaredConstructors();
         Constructor<?> ctor = declared.length > 0 ? declared[0] : type.getConstructors()[0];
         ctor.setAccessible(true);
         return new ConstructorAccessor<>(ctor);
      } catch (Throwable ignored) {
         return null;
      }
   }

   private static MethodAccessor findMethod(Class<?> owner, String name, Class<?>... params) {
      if (owner == null || name == null) {
         return null;
      }
      try {
         Method method = params == null ? owner.getDeclaredMethod(name) : owner.getDeclaredMethod(name, params);
         method.setAccessible(true);
         return new MethodAccessor(method);
      } catch (NoSuchMethodException ignored) {
         try {
            Method method = params == null ? owner.getMethod(name) : owner.getMethod(name, params);
            method.setAccessible(true);
            return new MethodAccessor(method);
         } catch (Throwable ignored2) {
            return null;
         }
      } catch (Throwable ignored) {
         return null;
      }
   }

   private static MethodAccessor findAddListMethod(Class<?> listClass) {
      if (listClass == null) {
         return null;
      }
      for (Method method : listClass.getMethods()) {
         if (method.getName().equals("add") && method.getParameterCount() == 1) {
            method.setAccessible(true);
            return new MethodAccessor(method);
         }
      }
      return null;
   }

   private static MethodAccessor resolveCompoundSetter() {
      Class<?> compoundClass = MinecraftReflection.getNBTTagCompoundClass();
      if (compoundClass == null) {
         return null;
      }
      String[] names = new String[]{"set", "put"};
      for (String name : names) {
         for (Method method : compoundClass.getMethods()) {
            if (method.getName().equals(name)) {
               Class<?>[] params = method.getParameterTypes();
               if (params.length == 2 && params[0] == String.class) {
                  method.setAccessible(true);
                  return new MethodAccessor(method);
               }
            }
         }
      }
      return null;
   }

   private static Class<?> resolveNbtClass(String... candidates) {
      if (candidates == null) {
         return null;
      }
      for (String candidate : candidates) {
         if (candidate == null || candidate.isEmpty()) {
            continue;
         }
         try {
            return Class.forName(candidate);
         } catch (Throwable ignored) {
         }
         try {
            Class<?> clazz = MinecraftReflection.getMinecraftClass(candidate);
            if (clazz != null) {
               return clazz;
            }
         } catch (Throwable ignored) {
         }
      }
      return null;
   }
}


