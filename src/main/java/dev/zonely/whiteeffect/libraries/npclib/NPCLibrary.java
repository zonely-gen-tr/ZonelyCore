package dev.zonely.whiteeffect.libraries.npclib;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import dev.zonely.whiteeffect.libraries.npclib.api.EntityController;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import dev.zonely.whiteeffect.libraries.npclib.npc.AbstractNPC;
import dev.zonely.whiteeffect.libraries.npclib.npc.EntityControllers;
import dev.zonely.whiteeffect.libraries.npclib.npc.ai.NPCHolder;
import dev.zonely.whiteeffect.plugin.WPlugin;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class NPCLibrary {
   private static final List<NPC> NPCS = new ArrayList();
   private static Plugin plugin;
   private static Listener LISTENER;

   public static void setupNPCs(WPlugin pl) {
      if (pl != null && plugin == null) {
         plugin = pl;
         LISTENER = new NPCListeners();
         Bukkit.getServer().getPluginManager().registerEvents(LISTENER, pl);
      }
   }

   public static NPC createNPC(EntityType type, String name) {
      return createNPC(type, UUID.randomUUID(), name);
   }

   public static NPC createNPC(EntityType type, UUID uuid, String name) {
      Preconditions.checkNotNull(type, "Tip yanlis.");
      Preconditions.checkNotNull(name, "İsim yanlis.");
      EntityController controller = EntityControllers.getController(type);
      NPC npc = new AbstractNPC(uuid, name, controller);
      NPCS.add(npc);
      return npc;
   }

   public static void unregister(NPC npc) {
      NPCS.remove(npc);
   }

   public static void unregisterAll() {
      Iterator var0 = listNPCS().iterator();

      while(var0.hasNext()) {
         NPC npc = (NPC)var0.next();
         npc.destroy();
      }

      HandlerList.unregisterAll(LISTENER);
      NPCS.clear();
      plugin = null;
   }

   public static boolean isNPC(Entity entity) {
      return getNPC(entity) != null;
   }

   public static NPC getNPC(Entity entity) {
      return entity instanceof NPCHolder ? ((NPCHolder)entity).getNPC() : null;
   }

   public static NPC findNPC(UUID uuid) {
      return (NPC)listNPCS().stream().filter((npc) -> {
         return npc.getUUID().equals(uuid);
      }).findFirst().orElse((NPC) null);
   }

   public static Plugin getPlugin() {
      return plugin;
   }

   public static Collection<NPC> listNPCS() {
      return ImmutableList.copyOf(NPCS);
   }
}
