package dev.zonely.whiteeffect.libraries.npclib;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.UnmodifiableIterator;
import dev.zonely.whiteeffect.libraries.npclib.api.event.NPCDeathEvent;
import dev.zonely.whiteeffect.libraries.npclib.api.event.NPCLeftClickEvent;
import dev.zonely.whiteeffect.libraries.npclib.api.event.NPCNeedsRespawnEvent;
import dev.zonely.whiteeffect.libraries.npclib.api.event.NPCRightClickEvent;
import dev.zonely.whiteeffect.libraries.npclib.api.event.NPCSpawnEvent;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import dev.zonely.whiteeffect.libraries.npclib.npc.skin.SkinUpdateTracker;
import dev.zonely.whiteeffect.nms.NMS;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

public class NPCListeners implements Listener {
   private final Map<Player, Long> antiSpam = new HashMap();
   private final ListMultimap<NPCListeners.ChunkCoord, NPC> toRespawn = ArrayListMultimap.create();
   private final Plugin plugin = NPCLibrary.getPlugin();
   private final SkinUpdateTracker updateTracker = new SkinUpdateTracker();
   private static final Method CHUNK_UNLOAD_SET_CANCELLED = resolveChunkUnloadSetCancelled();

   NPCListeners() {
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPluginDisable(PluginDisableEvent evt) {
      if (this.plugin.equals(evt.getPlugin())) {
         this.updateTracker.reset();
         this.antiSpam.clear();
         NPCLibrary.unregisterAll();
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onEntityDeath(EntityDeathEvent evt) {
      if (NPCLibrary.isNPC(evt.getEntity())) {
         NPC npc = NPCLibrary.getNPC(evt.getEntity());
         NPCDeathEvent event = new NPCDeathEvent(npc, evt.getEntity().getKiller());
         Bukkit.getPluginManager().callEvent(event);
         if (!event.isCancelled()) {
            npc.destroy();
         }
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onEntityDamage(EntityDamageEvent evt) {
      if (NPCLibrary.isNPC(evt.getEntity())) {
         NPC npc = NPCLibrary.getNPC(evt.getEntity());
         if (!npc.isProtected()) {
            evt.setCancelled(false);
         }
      }

   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onNPCSpawn(NPCSpawnEvent evt) {
      this.updateTracker.onNPCSpawn(evt.getNPC());
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onPlayerChangedWorld(PlayerChangedWorldEvent evt) {
      if (NPCLibrary.isNPC(evt.getPlayer())) {
         NMS.removeFromServerPlayerList(evt.getPlayer());
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPlayerChangeWorld(PlayerChangedWorldEvent evt) {
      this.updateTracker.updatePlayer(evt.getPlayer(), 20L, true);
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onPlayerMove(PlayerMoveEvent evt) {
      this.updateTracker.onPlayerMove(evt.getPlayer());
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onPlayerJoin(PlayerJoinEvent evt) {
      this.updateTracker.updatePlayer(evt.getPlayer(), 120L, true);
   }

   @EventHandler(
      ignoreCancelled = true,
      priority = EventPriority.MONITOR
   )
   public void onPlayerQuit(PlayerQuitEvent evt) {
      this.updateTracker.removePlayer(evt.getPlayer().getUniqueId());
      this.antiSpam.remove(evt.getPlayer());
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPlayerRespawn(PlayerRespawnEvent evt) {
      this.updateTracker.updatePlayer(evt.getPlayer(), 15L, true);
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPlayerTeleport(PlayerTeleportEvent evt) {
      this.updateTracker.updatePlayer(evt.getPlayer(), 15L, true);
   }

   @EventHandler
   public void onEntityDamage(EntityDamageByEntityEvent evt) {
      if (NPCLibrary.isNPC(evt.getEntity())) {
         NPC npc = NPCLibrary.getNPC(evt.getEntity());
         if (evt.getDamager() instanceof Player) {
            Player player = (Player)evt.getDamager();
            long last = this.antiSpam.get(player) == null ? 0L : (Long)this.antiSpam.get(player) - System.currentTimeMillis();
            if (last > 0L) {
               return;
            }

            this.antiSpam.put(player, System.currentTimeMillis() + 100L);
            Bukkit.getPluginManager().callEvent(new NPCLeftClickEvent(npc, player));
         }
      }

   }

   @EventHandler
   public void onPlayerInteractEntity(PlayerInteractEntityEvent evt) {
      if (NPCLibrary.isNPC(evt.getRightClicked())) {
         NPC npc = NPCLibrary.getNPC(evt.getRightClicked());
         long last = this.antiSpam.get(evt.getPlayer()) == null ? 0L : (Long)this.antiSpam.get(evt.getPlayer()) - System.currentTimeMillis();
         if (last > 0L) {
            return;
         }

         this.antiSpam.put(evt.getPlayer(), System.currentTimeMillis() + 100L);
         Bukkit.getPluginManager().callEvent(new NPCRightClickEvent(npc, evt.getPlayer()));
      }

   }

   @EventHandler
   public void onNPCNeedsRespawn(NPCNeedsRespawnEvent evt) {
      this.toRespawn.put(this.toCoord(evt.getNPC().getCurrentLocation()), evt.getNPC());
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onWorldLoad(WorldLoadEvent evt) {
      Iterator var2 = this.toRespawn.keys().iterator();

      while(var2.hasNext()) {
         NPCListeners.ChunkCoord coord = (NPCListeners.ChunkCoord)var2.next();
         if (coord.world.equals(evt.getWorld().getName()) && evt.getWorld().isChunkLoaded(coord.x, coord.z)) {
            this.respawnAllFromCoord(coord);
         }
      }

   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onWorldUnload(WorldUnloadEvent evt) {
      Iterator var2 = NPCLibrary.listNPCS().iterator();

      while(true) {
         NPC npc;
         do {
            do {
               do {
                  if (!var2.hasNext()) {
                     return;
                  }

                  npc = (NPC)var2.next();
               } while(npc == null);
            } while(!npc.isSpawned());
         } while(!npc.getCurrentLocation().getWorld().equals(evt.getWorld()));

         boolean despawned = npc.despawn();
         if (evt.isCancelled() || !despawned) {
            Iterator var5 = this.toRespawn.keys().iterator();

            while(var5.hasNext()) {
               NPCListeners.ChunkCoord coord = (NPCListeners.ChunkCoord)var5.next();
               if (coord.world.equals(evt.getWorld().getName())) {
                  this.respawnAllFromCoord(coord);
               }
            }

            return;
         }

         this.storeForRespawn(npc);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onChunkLoad(ChunkLoadEvent evt) {
      this.respawnAllFromCoord(this.toCoord(evt.getChunk()));
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onChunkUnload(ChunkUnloadEvent evt) {
      NPCListeners.ChunkCoord coord = this.toCoord(evt.getChunk());
      Location location = new Location((World)null, 0.0D, 0.0D, 0.0D);
      Iterator var4 = NPCLibrary.listNPCS().iterator();

      while(var4.hasNext()) {
         NPC npc = (NPC)var4.next();
         if (npc != null && npc.isSpawned()) {
            location = npc.getEntity().getLocation(location);
            if (this.toCoord(location).equals(coord)) {
               if (!npc.despawn()) {
                  if (this.tryCancelChunkUnload(evt)) {
                     this.respawnAllFromCoord(coord);
                     return;
                  }
               } else {
                  this.toRespawn.put(coord, npc);
               }
            }
         }
      }

   }

   private static Method resolveChunkUnloadSetCancelled() {
      try {
         return ChunkUnloadEvent.class.getMethod("setCancelled", Boolean.TYPE);
      } catch (NoSuchMethodException var0) {
         return null;
      }
   }

   private boolean tryCancelChunkUnload(ChunkUnloadEvent event) {
      if (CHUNK_UNLOAD_SET_CANCELLED == null) {
         return false;
      } else {
         try {
            CHUNK_UNLOAD_SET_CANCELLED.invoke(event, true);
            return true;
         } catch (IllegalAccessException | InvocationTargetException var3) {
            this.plugin.getLogger().log(Level.WARNING, "Unable to cancel ChunkUnloadEvent for NPCs", var3);
            return false;
         }
      }
   }

   private void respawnAllFromCoord(NPCListeners.ChunkCoord coord) {
      UnmodifiableIterator var2 = ImmutableSet.copyOf(this.toRespawn.asMap().keySet()).iterator();

      while(true) {
         NPCListeners.ChunkCoord c;
         do {
            if (!var2.hasNext()) {
               return;
            }

            c = (NPCListeners.ChunkCoord)var2.next();
         } while(!c.equals(coord));

         Iterator var4 = this.toRespawn.get(c).iterator();

         while(var4.hasNext()) {
            NPC npc = (NPC)var4.next();
            if (npc.getUUID() != null && !npc.isSpawned()) {
               npc.spawn(npc.getCurrentLocation());
            }
         }

         this.toRespawn.asMap().remove(c);
      }
   }

   private void storeForRespawn(NPC npc) {
      this.toRespawn.put(this.toCoord(npc.getCurrentLocation()), npc);
   }

   private NPCListeners.ChunkCoord toCoord(Chunk chunk) {
      return new NPCListeners.ChunkCoord(chunk);
   }

   private NPCListeners.ChunkCoord toCoord(Location location) {
      return new NPCListeners.ChunkCoord(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
   }

   private static class ChunkCoord {
      private final String world;
      private final int x;
      private final int z;

      private ChunkCoord(Chunk chunk) {
         this(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
      }

      private ChunkCoord(String world, int x, int z) {
         this.world = world;
         this.x = x;
         this.z = z;
      }

      public boolean equals(Object obj) {
         if (!(obj instanceof NPCListeners.ChunkCoord)) {
            return false;
         } else if (this == obj) {
            return true;
         } else {
            NPCListeners.ChunkCoord other = (NPCListeners.ChunkCoord)obj;
            if (this.world == null) {
               if (other.world != null) {
                  return false;
               }
            } else if (!this.world.equals(other.world)) {
               return false;
            }

            return this.x == other.x && this.z == other.z;
         }
      }

      ChunkCoord(Chunk x0, Object x1) {
         this(x0);
      }

      ChunkCoord(String x0, int x1, int x2, Object x3) {
         this(x0, x1, x2);
      }
   }
}
