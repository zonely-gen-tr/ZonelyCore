package dev.zonely.whiteeffect.libraries.holograms;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import dev.zonely.whiteeffect.libraries.holograms.api.Hologram;
import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import dev.zonely.whiteeffect.nms.interfaces.entity.IItem;
import dev.zonely.whiteeffect.nms.interfaces.entity.ISlime;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

public class HologramListeners implements Listener {
   private final Map<Player, Long> anticlickSpam = new HashMap();
   private final ListMultimap<HologramListeners.ChunkCoord, Hologram> toRespawn = ArrayListMultimap.create();
   private final Plugin plugin = HologramLibrary.getPlugin();
   private static final Method CHUNK_UNLOAD_SET_CANCELLED = resolveChunkUnloadSetCancelled();

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onItemSpawn(ItemSpawnEvent evt) {
      if (evt.getEntity() instanceof IItem) {
         evt.setCancelled(false);
      }

   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onEntityDamage(EntityDamageEvent event) {
      if (HologramLibrary.isHologramEntity(event.getEntity())) {
         event.setCancelled(true);
         try {
            event.setDamage(0.0);
         } catch (Throwable ignored) {}
         if (event.getEntity() instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) event.getEntity();
            try {
               double max = living.getMaxHealth();
               if (max > 0.0 && living.getHealth() < max) {
                  living.setHealth(max);
               }
            } catch (Throwable ignored) {}
            try {
               living.setFireTicks(0);
            } catch (Throwable ignored) {}
            try { living.setNoDamageTicks(10); } catch (Throwable ignored) {}
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
      if (!(event.getDamager() instanceof Player)) {
         return;
      }
      Player player = (Player) event.getDamager();
      HologramLine line = resolveTouchableLine(event.getEntity());
      if (line == null || line.getTouchHandler() == null) {
         return;
      }
      event.setCancelled(true);
      try {
         event.setDamage(0.0);
      } catch (Throwable ignored) {}
      if (event.getEntity() instanceof LivingEntity) {
         LivingEntity living = (LivingEntity) event.getEntity();
         try {
            double max = living.getMaxHealth();
            if (max > 0.0 && living.getHealth() < max) {
               living.setHealth(max);
            }
         } catch (Throwable ignored) {}
         try { living.setFireTicks(0); } catch (Throwable ignored) {}
         try { living.setNoDamageTicks(10); } catch (Throwable ignored) {}
      }
      if (isClickThrottled(player)) {
         return;
      }
      triggerTouch(player, line);
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onEntityDeath(EntityDeathEvent event) {
      if (HologramLibrary.isHologramEntity(event.getEntity())) {
         event.getDrops().clear();
         event.setDroppedExp(0);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onSlimeSplit(SlimeSplitEvent event) {
      if (HologramLibrary.isHologramEntity(event.getEntity())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPluginDisable(PluginDisableEvent evt) {
      if (this.plugin.equals(evt.getPlugin())) {
         HologramLibrary.unregisterAll();
      }

   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent evt) {
      this.anticlickSpam.remove(evt.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlayerInteractEntity(PlayerInteractEntityEvent evt) {
      if (handleInteract(evt.getPlayer(), evt.getRightClicked())) {
         evt.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
   public void onPlayerInteractAtEntityHigh(org.bukkit.event.player.PlayerInteractAtEntityEvent evt) {
      if (handleInteract(evt.getPlayer(), evt.getRightClicked())) {
         evt.setCancelled(true);
      }
   }

   private boolean handleInteract(Player player, Entity clicked) {
      if (player == null || clicked == null) return false;
      if (player.getGameMode().toString().contains("SPECTATOR")) return false;

      HologramLine line = resolveTouchableLine(clicked);
      if (line == null || line.getTouchHandler() == null) return false;
      if (isClickThrottled(player)) {
         return true;
      }
      triggerTouch(player, line);
      return true;
   }

   private boolean triggerTouch(Player player, HologramLine line) {
      if (line == null || line.getTouchHandler() == null) return false;
      try {
         line.getTouchHandler().onTouch(player);
         return true;
      } catch (Throwable ignored) {
         return false;
      }
   }

   private boolean isClickThrottled(Player player) {
      long now = System.currentTimeMillis();
      Long lastClick = this.anticlickSpam.get(player);
      if (lastClick != null && now - lastClick < 250L) {
         return true;
      }
      this.anticlickSpam.put(player, now);
      return false;
   }

   private HologramLine resolveTouchableLine(Entity entity) {
      if (entity == null) return null;
      if (entity instanceof ISlime) {
         return ((ISlime) entity).getLine();
      }
      Hologram holo = HologramLibrary.getHologram(entity);
      if (holo == null) return null;
      for (HologramLine line : holo.getLines()) {
         if (line.getSlime() != null && line.getSlime().getEntity() != null
                 && entity.equals(line.getSlime().getEntity())) {
            return line;
         }
         if (line.getArmor() != null && line.getArmor().getEntity() != null
                 && entity.equals(line.getArmor().getEntity())) {
            return line;
         }
      }
      return null;
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onWorldLoad(WorldLoadEvent evt) {
      Iterator var2 = this.toRespawn.keys().iterator();

      while(var2.hasNext()) {
         HologramListeners.ChunkCoord coord = (HologramListeners.ChunkCoord)var2.next();
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
      Iterator var2 = HologramLibrary.listHolograms().iterator();

      while(var2.hasNext()) {
         Hologram hologram = (Hologram)var2.next();
         if (hologram != null && hologram.isSpawned() && hologram.getLocation().getWorld().equals(evt.getWorld())) {
            if (evt.isCancelled()) {
               Iterator var4 = this.toRespawn.keys().iterator();

               while(var4.hasNext()) {
                  HologramListeners.ChunkCoord coord = (HologramListeners.ChunkCoord)var4.next();
                  if (coord.world.equals(evt.getWorld().getName())) {
                     this.respawnAllFromCoord(coord);
                  }
               }

               return;
            }

            hologram.despawn();
            this.storeForRespawn(hologram);
         }
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
      HologramListeners.ChunkCoord coord = this.toCoord(evt.getChunk());
      new Location((World)null, 0.0D, 0.0D, 0.0D);
      Iterator var4 = HologramLibrary.listHolograms().iterator();

      while(var4.hasNext()) {
         Hologram hologram = (Hologram)var4.next();
         if (hologram != null && hologram.isSpawned()) {
            Location location = hologram.getLocation().clone();
            if (this.toCoord(location).equals(coord)) {
               hologram.spawn();
               if (hologram.isSpawned() && this.tryCancelChunkUnload(evt)) {
                  this.respawnAllFromCoord(coord);
                  return;
               }

               this.toRespawn.put(coord, hologram);
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
            this.plugin.getLogger().log(Level.WARNING, "Unable to cancel ChunkUnloadEvent for holograms", var3);
            return false;
         }
      }
   }

   private void respawnAllFromCoord(HologramListeners.ChunkCoord coord) {
      Iterator var2 = this.toRespawn.asMap().keySet().iterator();

      while(true) {
         HologramListeners.ChunkCoord c;
         do {
            if (!var2.hasNext()) {
               return;
            }

            c = (HologramListeners.ChunkCoord)var2.next();
         } while(!c.equals(coord));

         Iterator var4 = this.toRespawn.get(c).iterator();

         while(var4.hasNext()) {
            Hologram hologram = (Hologram)var4.next();
            hologram.spawn();
         }

         this.toRespawn.asMap().remove(c);
      }
   }

   private void storeForRespawn(Hologram hologram) {
      this.toRespawn.put(this.toCoord(hologram.getLocation()), hologram);
   }

   private HologramListeners.ChunkCoord toCoord(Chunk chunk) {
      return new HologramListeners.ChunkCoord(chunk);
   }

   private HologramListeners.ChunkCoord toCoord(Location location) {
      return new HologramListeners.ChunkCoord(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
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
         if (!(obj instanceof HologramListeners.ChunkCoord)) {
            return false;
         } else if (this == obj) {
            return true;
         } else {
            HologramListeners.ChunkCoord other = (HologramListeners.ChunkCoord)obj;
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


