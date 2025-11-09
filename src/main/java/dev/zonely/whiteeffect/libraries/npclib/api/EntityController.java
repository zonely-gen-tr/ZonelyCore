package dev.zonely.whiteeffect.libraries.npclib.api;

import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface EntityController {
   void spawn(Location var1, NPC var2);

   void remove();

   Entity getBukkitEntity();
}
