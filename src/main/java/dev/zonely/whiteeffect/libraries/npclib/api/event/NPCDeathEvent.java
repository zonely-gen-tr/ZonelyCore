package dev.zonely.whiteeffect.libraries.npclib.api.event;

import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class NPCDeathEvent extends NPCEvent implements Cancellable {
   private static final HandlerList HANDLER_LIST = new HandlerList();
   private final NPC npc;
   private final Player killer;
   private boolean cancelled;

   public NPCDeathEvent(NPC npc, Player killer) {
      this.npc = npc;
      this.killer = killer;
   }

   public static HandlerList getHandlerList() {
      return HANDLER_LIST;
   }

   public NPC getNPC() {
      return this.npc;
   }

   public Player getKiller() {
      return this.killer;
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
