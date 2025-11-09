package dev.zonely.whiteeffect.libraries.npclib.api.event;

import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;

public class NPCStopFollowingEvent extends NPCEvent {
   private static final HandlerList HANDLER_LIST = new HandlerList();
   private final NPC npc;
   private final Entity target;

   public NPCStopFollowingEvent(NPC npc, Entity target) {
      this.npc = npc;
      this.target = target;
   }

   public static HandlerList getHandlerList() {
      return HANDLER_LIST;
   }

   public NPC getNPC() {
      return this.npc;
   }

   public Entity getTarget() {
      return this.target;
   }

   public HandlerList getHandlers() {
      return HANDLER_LIST;
   }
}
