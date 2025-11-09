package dev.zonely.whiteeffect.libraries.profile;

import com.google.common.base.Charsets;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.zonely.whiteeffect.libraries.profile.api.MineToolsAPI;
import dev.zonely.whiteeffect.libraries.profile.api.MojangAPI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public abstract class Mojang {
   private static final List<Mojang> MOJANGAPIS = new ArrayList();
   private static final Cache<String, String> CACHED_UUID;
   private static final Cache<String, String> CACHED_PROPERTY;

   public static String getUUID(String name) throws InvalidMojangException {
      String id = (String)CACHED_UUID.getIfPresent(name);
      if (id != null) {
         return id;
      } else {
         Iterator var2 = MOJANGAPIS.iterator();

         Mojang api;
         do {
            if (!var2.hasNext()) {
               throw new InvalidMojangException(name + " kullanicisinin UUDIsi bulunamadi.");
            }

            api = (Mojang)var2.next();
            id = api.fetchId(name);
         } while(!api.getResponse());

         if (id != null) {
            CACHED_UUID.put(name, id);
         }

         return id;
      }
   }

   public static String getSkinProperty(String id) throws InvalidMojangException {
      String property = (String)CACHED_PROPERTY.getIfPresent(id);
      if (property != null) {
         return property;
      } else {
         Iterator var2 = MOJANGAPIS.iterator();

         Mojang api;
         do {
            if (!var2.hasNext()) {
               throw new InvalidMojangException(id + " kullanicisi bulunamadi.");
            }

            api = (Mojang)var2.next();
            property = api.fetchSkinProperty(id);
         } while(!api.getResponse());

         if (property != null) {
            CACHED_PROPERTY.put(id, property);
         }

         return property;
      }
   }

   public static UUID getOfflineUUID(String name) {
      return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(Charsets.UTF_8));
   }

   public static String parseUUID(String withoutDashes) {
      return withoutDashes.substring(0, 8) + '-' + withoutDashes.substring(8, 12) + '-' + withoutDashes.substring(12, 16) + '-' + withoutDashes.substring(16, 20) + '-' + withoutDashes.substring(20, 32);
   }

   public abstract String fetchId(String var1);

   public abstract String fetchSkinProperty(String var1);

   public abstract boolean getResponse();

   static {
      MOJANGAPIS.add(new MojangAPI());
      MOJANGAPIS.add(new MineToolsAPI());
      CACHED_UUID = CacheBuilder.newBuilder().expireAfterWrite(1L, TimeUnit.HOURS).build();
      CACHED_PROPERTY = CacheBuilder.newBuilder().expireAfterWrite(30L, TimeUnit.MINUTES).build();
   }
}
