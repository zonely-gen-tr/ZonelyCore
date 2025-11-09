package dev.zonely.whiteeffect.libraries.npclib.npc.skin;

import com.google.common.base.Preconditions;
import dev.zonely.whiteeffect.libraries.npclib.NPCLibrary;
import dev.zonely.whiteeffect.nms.NMS;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class SkinPacketTracker {
   private static final Location CACHE_LOCATION = new Location((World)null, 0.0D, 0.0D, 0.0D);
   private static final int PACKET_DELAY_REMOVE = 1;
   private static final TabListRemover TAB_LIST_REMOVER = new TabListRemover();
   private static SkinPacketTracker.PlayerListener LISTENER;
   private final SkinnableEntity entity;
   private final Map<UUID, SkinPacketTracker.PlayerEntry> inProgress = new HashMap(Bukkit.getMaxPlayers() / 2);
   private boolean isRemoved;

   public SkinPacketTracker(SkinnableEntity entity) {
      Preconditions.checkNotNull(entity);
      this.entity = entity;
      if (LISTENER == null) {
         LISTENER = new SkinPacketTracker.PlayerListener();
         Bukkit.getPluginManager().registerEvents(LISTENER, NPCLibrary.getPlugin());
      }

   }

   public boolean shouldRemoveFromTabList() {
      return (Boolean)this.entity.getNPC().data().get("hide-from-tablist", true);
   }

   void notifyRemovePacketCancelled(UUID playerId) {
      this.inProgress.remove(playerId);
   }

   void notifyRemovePacketSent(UUID playerId) {
      SkinPacketTracker.PlayerEntry entry = (SkinPacketTracker.PlayerEntry)this.inProgress.get(playerId);
      if (entry != null) {
         if (entry.removeCount != 0) {
            --entry.removeCount;
            if (entry.removeCount == 0) {
               this.inProgress.remove(playerId);
            } else {
               this.scheduleRemovePacket(entry);
            }

         }
      }
   }

   public void onRemoveNPC() {
      this.isRemoved = true;
      Iterator var1 = Bukkit.getOnlinePlayers().iterator();

      while(var1.hasNext()) {
         Player player = (Player)var1.next();
         if (!NPCLibrary.isNPC(player)) {
            NMS.sendTabListRemove(player, this.entity.getEntity());
            TAB_LIST_REMOVER.sendPacket(player, this.entity);
         }
      }

   }

   public void onSpawnNPC() {
      this.isRemoved = false;
      (new BukkitRunnable() {
         public void run() {
            if (SkinPacketTracker.this.entity.getNPC().isSpawned()) {
               SkinPacketTracker.this.updateNearbyViewers(50.0D);
            }
         }
      }).runTaskLater(NPCLibrary.getPlugin(), 20L);
   }

   private void scheduleRemovePacket(final SkinPacketTracker.PlayerEntry entry) {
      if (!this.isRemoved && NPCLibrary.getPlugin() != null && NPCLibrary.getPlugin().isEnabled()) {
         entry.removeTask = Bukkit.getScheduler().runTaskLater(NPCLibrary.getPlugin(), new Runnable() {
            public void run() {
               if (SkinPacketTracker.this.shouldRemoveFromTabList()) {
                  SkinPacketTracker.TAB_LIST_REMOVER.sendPacket(entry.player, SkinPacketTracker.this.entity);
               }

            }
         }, 1L);
      }
   }

   private void scheduleRemovePacket(SkinPacketTracker.PlayerEntry entry, int count) {
      if (this.shouldRemoveFromTabList()) {
         entry.removeCount = count;
         this.scheduleRemovePacket(entry);
      }
   }

   public void updateNearbyViewers(double radius) {
      radius *= radius;
      World world = this.entity.getEntity().getWorld();
      Player from = this.entity.getEntity();
      Location location = from.getLocation();
      Iterator var6 = world.getPlayers().iterator();

      while(var6.hasNext()) {
         Player player = (Player)var6.next();
         if (player != null && !NPCLibrary.isNPC(player)) {
            player.getLocation(CACHE_LOCATION);
            if (player.canSee(from) && location.getWorld().equals(CACHE_LOCATION.getWorld()) && !(location.distanceSquared(CACHE_LOCATION) > radius)) {
               this.updateViewer(player);
            }
         }
      }

   }

   public void updateViewer(Player player) {
      Preconditions.checkNotNull(player);
      if (!this.isRemoved && !NPCLibrary.isNPC(player)) {
         SkinPacketTracker.PlayerEntry entry = (SkinPacketTracker.PlayerEntry)this.inProgress.get(player.getUniqueId());
         if (entry != null) {
            entry.cancel();
         } else {
            entry = new SkinPacketTracker.PlayerEntry(player);
         }

         TAB_LIST_REMOVER.cancelPackets(player, this.entity);
         this.inProgress.put(player.getUniqueId(), entry);
         if (this.entity.getSkin() != null) {
            this.entity.getSkin().apply(this.entity);
         }

         NMS.sendTabListAdd(player, this.entity.getEntity());
         this.scheduleRemovePacket(entry, 2);
      }
   }

   private class PlayerEntry {
      Player player;
      int removeCount;
      BukkitTask removeTask;

      PlayerEntry(Player player) {
         this.player = player;
      }

      void cancel() {
         if (this.removeTask != null) {
            this.removeTask.cancel();
         }

         this.removeCount = 0;
      }
   }

   private static class PlayerListener implements Listener {
      private PlayerListener() {
      }

      @EventHandler
      private void onPlayerQuit(PlayerQuitEvent event) {
         SkinPacketTracker.TAB_LIST_REMOVER.cancelPackets(event.getPlayer());
      }

      PlayerListener(Object x0) {
         this();
      }
   }
}
