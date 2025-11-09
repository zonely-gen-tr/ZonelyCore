package dev.zonely.whiteeffect.libraries.npclib.npc;

import dev.zonely.whiteeffect.libraries.npclib.api.EntityController;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import dev.zonely.whiteeffect.nms.NMS;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public abstract class AbstractEntityController implements EntityController {
   private Entity bukkitEntity;

   protected abstract Entity createEntity(Location var1, NPC var2);

   public void spawn(Location location, NPC npc) {
      this.bukkitEntity = this.createEntity(location, npc);
   }

   public void remove() {
      if (this.bukkitEntity != null) {
         try {
            NMS.removeFromWorld(this.bukkitEntity);
         } catch (Throwable ignored) {
         }
         this.bukkitEntity = null;
      }

   }

   public Entity getBukkitEntity() {
      return this.bukkitEntity;
   }
}
