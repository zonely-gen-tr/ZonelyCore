package dev.zonely.whiteeffect.libraries.npclib.npc;

import dev.zonely.whiteeffect.libraries.npclib.api.EntityController;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.entity.EntityType;

public class EntityControllers {
   private static final Map<EntityType, Class<? extends EntityController>> controllers = new HashMap();

   public static void registerEntityController(EntityType type, Class<? extends EntityController> controller) {
      controllers.put(type, controller);
   }

   public static EntityController getController(EntityType type) {
      Class<? extends EntityController> clazz = (Class)controllers.get(type);
      if (clazz == null) {
         throw new IllegalArgumentException(type.name() + " NPC tipi yanlis girildi.");
      } else {
         try {
            return (EntityController)clazz.newInstance();
         } catch (ReflectiveOperationException var3) {
            var3.printStackTrace();
            return null;
         }
      }
   }
}
