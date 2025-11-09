package dev.zonely.whiteeffect.libraries.npclib.trait;

import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;

public class CurrentLocation extends NPCTrait {
   private Location location = new Location((World)null, 0.0D, 0.0D, 0.0D);

   public CurrentLocation(NPC npc) {
      super(npc);
   }

   public Location getLocation() {
      return this.location;
   }

   public void setLocation(Location location) {
      this.location = location;
   }
}
