package dev.zonely.whiteeffect.libraries.npclib.api.event;

import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class NPCDespawnEvent extends NPCEvent implements Cancellable {
   private static final HandlerList HANDLER_LIST = new HandlerList();
   private final NPC npc;
   private boolean cancelled;

   public NPCDespawnEvent(NPC npc) {
      this.npc = npc;
   }

   public static HandlerList getHandlerList() {
      return HANDLER_LIST;
   }

   public NPC getNPC() {
      return this.npc;
   }

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void setCancelled(boolean cancelled) {
      this.cancelled = cancelled;
   }

   public HandlerList getHandlers() {
      return HANDLER_LIST;
   }
}
