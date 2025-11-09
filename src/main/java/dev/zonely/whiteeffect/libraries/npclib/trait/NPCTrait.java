package dev.zonely.whiteeffect.libraries.npclib.trait;

import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import dev.zonely.whiteeffect.libraries.npclib.api.trait.Trait;

public abstract class NPCTrait implements Trait {
   private final NPC npc;

   public NPCTrait(NPC npc) {
      this.npc = npc;
   }

   public NPC getNPC() {
      return this.npc;
   }

   public void onAttach() {
   }

   public void onSpawn() {
   }

   public void onDespawn() {
   }

   public void onRemove() {
   }
}
