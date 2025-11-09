package dev.zonely.whiteeffect.libraries.npclib.npc.skin;

import com.google.common.base.Preconditions;
import dev.zonely.whiteeffect.libraries.npclib.NPCLibrary;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import dev.zonely.whiteeffect.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class SkinUpdateTracker {
   private static final Location CACHE_LOCATION = new Location((World)null, 0.0D, 0.0D, 0.0D);
   private static final int MOVEMENT_SKIN_UPDATE_DISTANCE = 2500;
   private static final Location NPC_LOCATION = new Location((World)null, 0.0D, 0.0D, 0.0D);
   private final Map<UUID, SkinUpdateTracker.PlayerTracker> playerTrackers = new HashMap(Bukkit.getMaxPlayers() / 2);

   public SkinUpdateTracker() {
      Preconditions.checkNotNull(NPCLibrary.getPlugin());
      Iterator var1 = Bukkit.getOnlinePlayers().iterator();

      while(var1.hasNext()) {
         Player player = (Player)var1.next();
         if (!NPCLibrary.isNPC(player)) {
            this.playerTrackers.put(player.getUniqueId(), new SkinUpdateTracker.PlayerTracker(player));
         }
      }

   }

   private boolean canSee(Player player, SkinnableEntity skinnable) {
      Player entity = skinnable.getEntity();
      if (entity == null) {
         return false;
      } else if (!player.canSee(entity)) {
         return false;
      } else if (!player.getWorld().equals(entity.getWorld())) {
         return false;
      } else {
         Location playerLoc = player.getLocation(CACHE_LOCATION);
         Location skinLoc = entity.getLocation(NPC_LOCATION);
         double viewDistance = 50.0D;
         viewDistance *= viewDistance;
         return !(playerLoc.distanceSquared(skinLoc) > viewDistance);
      }
   }

   private Iterable<NPC> getAllNPCs() {
      return NPCLibrary.listNPCS();
   }

   private List<SkinnableEntity> getNearbyNPCs(Player player, boolean reset) {
      List<SkinnableEntity> results = new ArrayList();
      this.getTracker(player, reset);
      Iterator var4 = this.getAllNPCs().iterator();

      while(var4.hasNext()) {
         NPC npc = (NPC)var4.next();
         SkinnableEntity skinnable = this.getSkinnable(npc);
         if (skinnable != null && this.canSee(player, skinnable)) {
            results.add(skinnable);
         }
      }

      return results;
   }

   @Nullable
   private SkinnableEntity getSkinnable(NPC npc) {
      Entity entity = npc.getEntity();
      if (entity == null) {
         return null;
      } else {
         return entity instanceof SkinnableEntity ? (SkinnableEntity)entity : null;
      }
   }

   public SkinUpdateTracker.PlayerTracker getTracker(Player player, boolean reset) {
      SkinUpdateTracker.PlayerTracker tracker = (SkinUpdateTracker.PlayerTracker)this.playerTrackers.get(player.getUniqueId());
      if (tracker == null) {
         tracker = new SkinUpdateTracker.PlayerTracker(player);
         this.playerTrackers.put(player.getUniqueId(), tracker);
      } else if (reset) {
         tracker.hardReset(player);
      }

      return tracker;
   }

   public void onNPCSpawn(NPC npc) {
      Preconditions.checkNotNull(npc);
      SkinnableEntity skinnable = this.getSkinnable(npc);
      if (skinnable != null) {
         this.resetNearbyPlayers(skinnable);
      }
   }

   public void onPlayerMove(Player player) {
      Preconditions.checkNotNull(player);
      SkinUpdateTracker.PlayerTracker updateTracker = (SkinUpdateTracker.PlayerTracker)this.playerTrackers.get(player.getUniqueId());
      if (updateTracker != null) {
         if (updateTracker.shouldUpdate(player)) {
            this.updatePlayer(player, 10L, false);
         }
      }
   }

   public void removePlayer(UUID playerId) {
      Preconditions.checkNotNull(playerId);
      this.playerTrackers.remove(playerId);
   }

   public void reset() {
      Iterator var1 = Bukkit.getOnlinePlayers().iterator();

      while(var1.hasNext()) {
         Player player = (Player)var1.next();
         if (!NPCLibrary.isNPC(player)) {
            SkinUpdateTracker.PlayerTracker tracker = (SkinUpdateTracker.PlayerTracker)this.playerTrackers.get(player.getUniqueId());
            if (tracker != null) {
               tracker.hardReset(player);
            }
         }
      }

   }

   private void resetNearbyPlayers(SkinnableEntity skinnable) {
      Entity entity = skinnable.getEntity();
      if (entity != null && entity.isValid()) {
         double viewDistance = 50.0D;
         viewDistance *= viewDistance;
         Location location = entity.getLocation(NPC_LOCATION);
         List<Player> players = entity.getWorld().getPlayers();
         Iterator var7 = players.iterator();

         while(var7.hasNext()) {
            Player player = (Player)var7.next();
            if (!NPCLibrary.isNPC(player)) {
               Location ploc = player.getLocation(CACHE_LOCATION);
               if (ploc.getWorld() == location.getWorld() && !(ploc.distanceSquared(location) > viewDistance)) {
                  SkinUpdateTracker.PlayerTracker tracker = (SkinUpdateTracker.PlayerTracker)this.playerTrackers.get(player.getUniqueId());
                  if (tracker != null) {
                     tracker.hardReset(player);
                  }
               }
            }
         }

      }
   }

   public void updatePlayer(final Player player, long delay, final boolean reset) {
      if (!NPCLibrary.isNPC(player)) {
         (new BukkitRunnable() {
            public void run() {
               List<SkinnableEntity> visible = SkinUpdateTracker.this.getNearbyNPCs(player, reset);
               Iterator var2 = visible.iterator();

               while(var2.hasNext()) {
                  SkinnableEntity skinnable = (SkinnableEntity)var2.next();
                  skinnable.getSkinTracker().updateViewer(player);
               }

            }
         }).runTaskLater(NPCLibrary.getPlugin(), delay);
      }
   }

   private class PlayerTracker {
      final Location location = new Location((World)null, 0.0D, 0.0D, 0.0D);
      boolean hasMoved;
      float lowerBound;
      int rotationCount;
      float startYaw;
      float upperBound;

      PlayerTracker(Player player) {
         this.hardReset(player);
      }

      void hardReset(Player player) {
         this.hasMoved = false;
         this.rotationCount = 0;
         this.lowerBound = this.upperBound = this.startYaw = 0.0F;
         this.reset(player);
      }

      void reset(Player player) {
         player.getLocation(this.location);
         if (this.rotationCount < 3) {
            float rotationDegrees = 90.0F;
            float yaw = Utils.clampYaw(this.location.getYaw());
            this.startYaw = yaw;
            this.upperBound = Utils.clampYaw(yaw + rotationDegrees);
            this.lowerBound = Utils.clampYaw(yaw - rotationDegrees);
            if ((double)this.upperBound == -180.0D && this.startYaw > 0.0F) {
               this.upperBound = 0.0F;
            }
         }

      }

      boolean shouldUpdate(Player player) {
         Location currentLoc = player.getLocation(SkinUpdateTracker.CACHE_LOCATION);
         if (!this.hasMoved) {
            this.hasMoved = true;
            return true;
         } else {
            if (this.rotationCount < 3) {
               float yaw = Utils.clampYaw(currentLoc.getYaw());
               boolean hasRotated;
               if (!(this.startYaw - 90.0F < -180.0F) && !(this.startYaw + 90.0F > 180.0F)) {
                  hasRotated = yaw < this.lowerBound || yaw > this.upperBound;
               } else {
                  hasRotated = yaw > this.lowerBound && yaw < this.upperBound;
               }

               if (hasRotated) {
                  ++this.rotationCount;
                  this.reset(player);
                  return true;
               }
            }

            if (!currentLoc.getWorld().equals(this.location.getWorld())) {
               this.reset(player);
               return true;
            } else {
               double distance = currentLoc.distanceSquared(this.location);
               if (distance > 2500.0D) {
                  this.reset(player);
                  return true;
               } else {
                  return false;
               }
            }
         }
      }
   }
}
