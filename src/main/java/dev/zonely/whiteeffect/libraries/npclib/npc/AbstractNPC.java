package dev.zonely.whiteeffect.libraries.npclib.npc;

import com.google.common.base.Preconditions;
import dev.zonely.whiteeffect.libraries.npclib.NPCLibrary;
import dev.zonely.whiteeffect.libraries.npclib.api.EntityController;
import dev.zonely.whiteeffect.libraries.npclib.api.event.NPCDespawnEvent;
import dev.zonely.whiteeffect.libraries.npclib.api.event.NPCNavigationEndEvent;
import dev.zonely.whiteeffect.libraries.npclib.api.event.NPCNeedsRespawnEvent;
import dev.zonely.whiteeffect.libraries.npclib.api.event.NPCSpawnEvent;
import dev.zonely.whiteeffect.libraries.npclib.api.event.NPCStopFollowingEvent;
import dev.zonely.whiteeffect.libraries.npclib.api.metadata.MetadataStore;
import dev.zonely.whiteeffect.libraries.npclib.api.metadata.SimpleMetadataStore;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPCAnimation;
import dev.zonely.whiteeffect.libraries.npclib.npc.skin.SkinnableEntity;
import dev.zonely.whiteeffect.libraries.npclib.trait.CurrentLocation;
import dev.zonely.whiteeffect.libraries.npclib.trait.NPCTrait;
import dev.zonely.whiteeffect.nms.NMS;
import dev.zonely.whiteeffect.utils.Utils;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class AbstractNPC implements NPC {
   private UUID uuid;
   private String name;
   private EntityController controller;
   private final MetadataStore data;
   private Map<Class<? extends NPCTrait>, NPCTrait> traits;
   private int ticksToUpdate;
   private Entity following;
   private boolean navigating;
   private Location walkingTo;
   private boolean laying;

   public AbstractNPC(UUID uuid, String name, EntityController controller) {
      this.uuid = uuid;
      this.name = name;
      this.controller = controller;
      this.data = new SimpleMetadataStore();
      this.traits = new HashMap();
      this.addTrait(CurrentLocation.class);
   }

   public boolean spawn(Location location) {
      Preconditions.checkNotNull(location, "Lokasyon yanlis girildi.");
      Preconditions.checkState(!this.isSpawned(), "NPC ekleniyor.");
      this.controller.spawn(location, this);
      boolean couldSpawn = Utils.isLoaded(location) && NMS.addToWorld(location.getWorld(), this.controller.getBukkitEntity(), SpawnReason.CUSTOM);
      if (couldSpawn) {
         SkinnableEntity entity = NMS.getSkinnable(this.getEntity());
         if (entity != null) {
            entity.getSkinTracker().onSpawnNPC();
         }
      }

      ((CurrentLocation)this.getTrait(CurrentLocation.class)).setLocation(location);
      if (!couldSpawn) {
         Bukkit.getPluginManager().callEvent(new NPCNeedsRespawnEvent(this));
         this.controller.remove();
         return false;
      } else {
         NPCSpawnEvent event = new NPCSpawnEvent(this);
         if (event.isCancelled()) {
            this.controller.remove();
            return false;
         } else {
            NMS.setHeadYaw(this.getEntity(), location.getYaw());
            this.getEntity().setMetadata("NPC", new FixedMetadataValue(NPCLibrary.getPlugin(), this));
            Iterator var4 = this.traits.values().iterator();

            while(var4.hasNext()) {
               NPCTrait trait = (NPCTrait)var4.next();
               trait.onSpawn();
            }

            if (this.getEntity() instanceof LivingEntity) {
               LivingEntity entity = (LivingEntity)this.getEntity();
               entity.setRemoveWhenFarAway(false);
               if (NMS.getStepHeight(entity) < 1.0F) {
                  NMS.setStepHeight(entity, 1.0F);
               }

               if (this.getEntity() instanceof Player) {
                  NMS.replaceTrackerEntry((Player)this.getEntity());
               }
            }

            ((CurrentLocation)this.getTrait(CurrentLocation.class)).setLocation(this.getEntity().getLocation());
            return true;
         }
      }
   }

   public boolean despawn() {
      Preconditions.checkState(this.isSpawned(), "NPC yeniden yapildi.");
      NPCDespawnEvent event = new NPCDespawnEvent(this);
      Bukkit.getServer().getPluginManager().callEvent(event);
      if (event.isCancelled()) {
         return false;
      } else {
         Iterator var2 = this.traits.values().iterator();

         while(var2.hasNext()) {
            NPCTrait trait = (NPCTrait)var2.next();
            trait.onDespawn();
         }

         this.controller.remove();
         Bukkit.getOnlinePlayers().forEach((player) -> {
            Scoreboard sb = player.getScoreboard();
            Team team = sb.getTeam("wNPCS");
            if (team != null) {
               team.removeEntry(this.name);
               if (team.getSize() == 0) {
                  team.unregister();
               }
            }

         });
         return true;
      }
   }

   public void destroy() {
      if (this.isSpawned()) {
         this.despawn();
      }

      Bukkit.getOnlinePlayers().forEach((player) -> {
         Scoreboard sb = player.getScoreboard();
         Team team = sb.getTeam("wNPCS");
         if (team != null) {
            team.removeEntry(this.name);
            if (team.getSize() == 0) {
               team.unregister();
            }
         }

      });
      this.uuid = null;
      this.name = null;
      this.controller = null;
      this.traits.clear();
      this.traits = null;
      NPCLibrary.unregister(this);
   }

   public MetadataStore data() {
      return this.data;
   }

   public void update() {
      if (this.isSpawned() && this.ticksToUpdate++ > 30) {
         this.ticksToUpdate = 0;
         Entity entity = this.controller.getBukkitEntity();
         if (entity instanceof Player) {
            Iterator var2 = Bukkit.getServer().getOnlinePlayers().iterator();

            while(var2.hasNext()) {
               Player players = (Player)var2.next();
               if (!NPCLibrary.isNPC(players)) {
                  Scoreboard sb = players.getScoreboard();
                  Team team = sb.getTeam("wNPCS");
                  if ((Boolean)this.data().get("hide-by-teams", false)) {
                      if (team == null) {
                         team = sb.registerNewTeam("wNPCS");
                         team.setNameTagVisibility(NameTagVisibility.NEVER);
                      }
                      try {
                         team.setAllowFriendlyFire(true);
                      } catch (Throwable ignored) {
                      }

                      if (!team.hasEntry(this.name)) {
                         team.addEntry(this.name);
                      }
                  } else if (team != null && team.getSize() == 0) {
                     team.unregister();
                  }
               }
            }
         }
      }

   }

   public void playAnimation(NPCAnimation animation) {
      Preconditions.checkState(this.isSpawned(), "NPC animasyonlari tamamlandi.");
      NMS.playAnimation(this.getEntity(), animation);
   }

   public void addTrait(NPCTrait trait) {
      this.traits.put(trait.getClass(), trait);
      trait.onAttach();
   }

   public void addTrait(Class<? extends NPCTrait> traitClass) {
      try {
         NPCTrait trait = (NPCTrait)traitClass.getDeclaredConstructors()[0].newInstance(this);
         this.traits.put(traitClass, trait);
         trait.onAttach();
      } catch (ReflectiveOperationException var3) {
         throw new RuntimeException(traitClass.getName() + " trait yanlis.", var3);
      }
   }

   public void removeTrait(Class<? extends NPCTrait> traitClass) {
      NPCTrait trait = (NPCTrait)this.traits.get(traitClass);
      if (trait != null) {
         trait.onRemove();
         this.traits.remove(traitClass);
      }

   }

   public void finishNavigation() {
      Bukkit.getPluginManager().callEvent(new NPCNavigationEndEvent(this));
      this.navigating = false;
   }

   @SuppressWarnings("unchecked")
   @Override
   public <T extends NPCTrait> T getTrait(Class<T> traitClass) {
      return (T) this.traits.get(traitClass);
   }


   public boolean isSpawned() {
      return this.controller != null && this.controller.getBukkitEntity() != null && this.controller.getBukkitEntity().isValid();
   }

   public boolean isProtected() {
      return (Boolean)this.data().get("protected", true);
   }

   public boolean isNavigating() {
      return this.navigating;
   }

   public boolean isLaying() {
      return this.laying;
   }

   public void setLaying(boolean laying) {
      this.laying = true;
   }

   public Entity getEntity() {
      return this.controller.getBukkitEntity();
   }

   public Entity getFollowing() {
      return this.following;
   }

   public void setFollowing(Entity entity) {
      Preconditions.checkState(!this.navigating, "NPC takip edilemedi.");
      if (this.following != null) {
         Bukkit.getPluginManager().callEvent(new NPCStopFollowingEvent(this, this.following));
      }

      this.following = entity;
   }

   public Location getWalkingTo() {
      return this.walkingTo;
   }

   public void setWalkingTo(Location location) {
      Preconditions.checkState(this.following == null, "NPC yurutulemedi.");
      if (location == null) {
         this.walkingTo = null;
      } else {
         Preconditions.checkState(!this.navigating, "NPC yurutuluyor.");
         this.navigating = true;
         this.walkingTo = location;
      }
   }

   public Location getCurrentLocation() {
      return ((CurrentLocation)this.getTrait(CurrentLocation.class)).getLocation().getWorld() != null ? ((CurrentLocation)this.getTrait(CurrentLocation.class)).getLocation() : (this.isSpawned() ? this.getEntity().getLocation() : null);
   }

   public UUID getUUID() {
      return this.uuid;
   }

   public String getName() {
      return this.name;
   }
}
