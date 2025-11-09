package dev.zonely.whiteeffect.utils;

import com.mojang.authlib.GameProfile;
import dev.zonely.whiteeffect.reflection.MinecraftReflection;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.entity.Player;


public final class GameProfileResolver {

   private static final Class<?> CRAFT_PLAYER = safe(() -> MinecraftReflection.getCraftBukkitClass("entity.CraftPlayer"));
   private static final Method CP_GET_HANDLE = findMethod(CRAFT_PLAYER, "getHandle");
   private static final Method CP_GET_PROFILE = findMethod(CRAFT_PLAYER, "getProfile");
   private static final Field CP_PROFILE_FIELD = findField(CRAFT_PLAYER, "profile");

   private static final Class<?> SERVER_PLAYER = tryClass(
         "net.minecraft.server.level.ServerPlayer",
         "net.minecraft.server.network.ServerPlayer",
         "net.minecraft.server.v1_16_R3.EntityPlayer",
         "net.minecraft.server.v1_12_R1.EntityPlayer",
         "net.minecraft.server.EntityPlayer"
   );

   private static final Method SP_GET_GAMEPROFILE = findMethod(SERVER_PLAYER, "getGameProfile");
   private static final Method SP_GET_PROFILE = findMethod(SERVER_PLAYER, "getProfile");
   private static final Field SP_GAMEPROFILE_FIELD = findField(SERVER_PLAYER, "gameProfile");

   private GameProfileResolver() {
   }


   public static GameProfile get(Player player) {
      if (player == null) {
         return null;
      }

      if (CP_GET_PROFILE != null && CRAFT_PLAYER != null && CRAFT_PLAYER.isInstance(player)) {
         GameProfile profile = invoke(player, CP_GET_PROFILE, GameProfile.class);
         if (profile != null) {
            return profile;
         }
      }

      if (CP_PROFILE_FIELD != null && CRAFT_PLAYER != null && CRAFT_PLAYER.isInstance(player)) {
         GameProfile profile = getFieldValue(player, CP_PROFILE_FIELD, GameProfile.class);
         if (profile != null) {
            return profile;
         }
      }

      Object handle = null;
      if (CP_GET_HANDLE != null && CRAFT_PLAYER != null && CRAFT_PLAYER.isInstance(player)) {
         handle = invoke(player, CP_GET_HANDLE, Object.class);
      }

      if (handle != null && SERVER_PLAYER != null && SERVER_PLAYER.isInstance(handle)) {
         if (SP_GET_GAMEPROFILE != null) {
            GameProfile profile = invoke(handle, SP_GET_GAMEPROFILE, GameProfile.class);
            if (profile != null) {
               return profile;
            }
         }

         if (SP_GET_PROFILE != null) {
            GameProfile profile = invoke(handle, SP_GET_PROFILE, GameProfile.class);
            if (profile != null) {
               return profile;
            }
         }

         if (SP_GAMEPROFILE_FIELD != null) {
            GameProfile profile = getFieldValue(handle, SP_GAMEPROFILE_FIELD, GameProfile.class);
            if (profile != null) {
               return profile;
            }
         }
      }

      try {
         return new GameProfile(player.getUniqueId(), player.getName());
      } catch (Throwable ignored) {
         return null;
      }
   }

   private static <T> T invoke(Object target, Method method, Class<T> type) {
      if (target == null || method == null) {
         return null;
      }
      try {
         Object result = method.invoke(target);
         if (result != null && (type == null || type.isInstance(result))) {
            return type.cast(result);
         }
      } catch (Throwable ignored) {
      }
      return null;
   }

   private static <T> T getFieldValue(Object target, Field field, Class<T> type) {
      if (target == null || field == null) {
         return null;
      }
      try {
         Object result = field.get(target);
         if (result != null && (type == null || type.isInstance(result))) {
            return type.cast(result);
         }
      } catch (Throwable ignored) {
      }
      return null;
   }

   private static Method findMethod(Class<?> owner, String name, Class<?>... params) {
      if (owner == null || name == null) {
         return null;
      }
      try {
         Method method = owner.getMethod(name, params);
         method.setAccessible(true);
         return method;
      } catch (Throwable ignored) {
         try {
            Method method = owner.getDeclaredMethod(name, params);
            method.setAccessible(true);
            return method;
         } catch (Throwable ignored2) {
            return null;
         }
      }
   }

   private static Field findField(Class<?> owner, String name) {
      if (owner == null || name == null) {
         return null;
      }
      try {
         Field field = owner.getField(name);
         field.setAccessible(true);
         return field;
      } catch (Throwable ignored) {
         try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
         } catch (Throwable ignored2) {
            return null;
         }
      }
   }

   private static Class<?> tryClass(String... names) {
      if (names == null) {
         return null;
      }
      for (String name : names) {
         Class<?> candidate = safe(() -> MinecraftReflection.getClass(name));
         if (candidate != null) {
            return candidate;
         }
      }
      return null;
   }

   private interface ThrowingSupplier<T> {
      T get() throws Exception;
   }

   private static <T> T safe(ThrowingSupplier<T> supplier) {
      try {
         return supplier.get();
      } catch (Throwable ignored) {
         return null;
      }
   }
}

