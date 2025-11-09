package dev.zonely.whiteeffect.libraries.npclib.npc.skin;

import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import org.bukkit.entity.Player;

public interface SkinnableEntity {
   NPC getNPC();

   Player getEntity();

   SkinPacketTracker getSkinTracker();

   Skin getSkin();

   void setSkin(Skin var1);

   void setSkinFlags(byte var1);
}
