package dev.zonely.whiteeffect.libraries.npclib.npc.skin;

import dev.zonely.whiteeffect.libraries.profile.InvalidMojangException;
import dev.zonely.whiteeffect.libraries.profile.Mojang;
import dev.zonely.whiteeffect.nms.NMS;

public class Skin {
   private String value;
   private String signature;

   private Skin(String value, String signature) {
      this.value = value;
      this.signature = signature;
   }

   public static Skin fromName(String name) {
      return (new Skin((String)null, (String)null)).fetch(name);
   }

   public static Skin fromData(String value, String signature) {
      return new Skin(value, signature);
   }

   private Skin fetch(String name) {
      try {
         String id = Mojang.getUUID(name);
         if (id != null) {
            String property = Mojang.getSkinProperty(id);
            if (property != null) {
               this.value = property.split(" : ")[1];
               this.signature = property.split(" : ")[2];
            }
         }
      } catch (InvalidMojangException var4) {
         System.out.println("Cannot fetch skin from name " + name + ": " + var4.getMessage());
      }

      return this;
   }

   public void apply(SkinnableEntity entity) {
      NMS.setValueAndSignature(entity.getEntity(), this.value, this.signature);
   }
}
