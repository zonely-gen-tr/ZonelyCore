package dev.zonely.whiteeffect.libraries.npclib.api.npc;

import dev.zonely.whiteeffect.libraries.npclib.api.metadata.MetadataStore;
import dev.zonely.whiteeffect.libraries.npclib.trait.NPCTrait;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface NPC {
   String PROTECTED_KEY = "protected";
   String TAB_LIST_KEY = "hide-from-tablist";
   String HIDE_BY_TEAMS_KEY = "hide-by-teams";
   String FLYABLE = "flyable";
   String GRAVITY = "gravity";
   String ATTACHED_PLAYER = "only-for";
   String COPY_PLAYER_SKIN = "copy-player";

   boolean spawn(Location var1);

   boolean despawn();

   void destroy();

   void update();

   MetadataStore data();

   void playAnimation(NPCAnimation var1);

   void addTrait(NPCTrait var1);

   void addTrait(Class<? extends NPCTrait> var1);

   void removeTrait(Class<? extends NPCTrait> var1);

   void finishNavigation();

   boolean isSpawned();

   boolean isProtected();

   boolean isLaying();

   void setLaying(boolean var1);

   boolean isNavigating();

   <T extends NPCTrait> T getTrait(Class<T> var1);

   Entity getEntity();

   Entity getFollowing();

   void setFollowing(Entity var1);

   Location getWalkingTo();

   void setWalkingTo(Location var1);

   Location getCurrentLocation();

   UUID getUUID();

   String getName();
}
