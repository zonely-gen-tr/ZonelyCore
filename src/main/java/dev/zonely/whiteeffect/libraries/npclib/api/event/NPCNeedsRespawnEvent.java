package dev.zonely.whiteeffect.libraries.npclib.api.event;

import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import org.bukkit.event.HandlerList;

public class NPCNeedsRespawnEvent extends NPCEvent {
   private static final HandlerList HANDLER_LIST = new HandlerList();
   private final NPC npc;

   public NPCNeedsRespawnEvent(NPC npc) {
      this.npc = npc;
   }

   public static HandlerList getHandlerList() {
      return HANDLER_LIST;
   }

   public NPC getNPC() {
      return this.npc;
   }

   public HandlerList getHandlers() {
      return HANDLER_LIST;
   }
}
