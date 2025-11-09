package dev.zonely.whiteeffect.replay.advanced;

import com.google.gson.JsonObject;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.libraries.npclib.NPCLibrary;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPCAnimation;
import dev.zonely.whiteeffect.nms.universal.entity.PacketNPCManager;
import dev.zonely.whiteeffect.replay.NPCReplayManager;
import dev.zonely.whiteeffect.replay.control.ReplayControlManager;
import dev.zonely.whiteeffect.report.Report;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

public final class AdvancedReplayEngine {
   private static final AdvancedReplayEngine INSTANCE = new AdvancedReplayEngine(Core.getInstance());
   private static final int TICK_STEP = 2;
   private static final boolean SUPPORTS_OFF_HAND;
   private static final boolean SUPPORTS_BUKKIT_ATTRIBUTES;
   private static Method livingGetAttributeMethod;
   private static Object genericMaxHealthAttribute;
   private static Method attributeSetBaseValueMethod;
   private final Core plugin;
   private final AdvancedReplayStorage storage;
   private final ConcurrentMap<UUID, AdvancedReplayEngine.ActiveReplay> active = new ConcurrentHashMap();

   static {
      boolean supports = false;
      try {
         EntityEquipment.class.getMethod("setItemInOffHand", ItemStack.class);
         supports = true;
      } catch (Throwable ignored) {
      }

      SUPPORTS_OFF_HAND = supports;

      boolean attributeSupport = false;
      Method getAttribute = null;
      Object genericMaxHealth = null;
      Method setBaseValue = null;
      try {
         Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
         Class<?> attributeInstanceClass = Class.forName("org.bukkit.attribute.AttributeInstance");
         genericMaxHealth = attributeClass.getField("GENERIC_MAX_HEALTH").get(null);
         getAttribute = LivingEntity.class.getMethod("getAttribute", attributeClass);
         setBaseValue = attributeInstanceClass.getMethod("setBaseValue", double.class);
         attributeSupport = true;
      } catch (Throwable ignored) {
      }

      SUPPORTS_BUKKIT_ATTRIBUTES = attributeSupport;
      livingGetAttributeMethod = getAttribute;
      genericMaxHealthAttribute = genericMaxHealth;
      attributeSetBaseValueMethod = setBaseValue;
   }

   public static AdvancedReplayEngine get() {
      return INSTANCE;
   }

   private AdvancedReplayEngine(Core plugin) {
      this.plugin = plugin;
      this.storage = AdvancedReplayStorage.get();
   }

   public boolean start(Player viewer, Report report) {
      NPCReplayManager.registerPotionBlocker(this.plugin);
      this.stop(viewer);
      Optional<ReplaySessionRecord> sessionOpt = this.storage.findSessionByReport(report.getId());
      if (!sessionOpt.isPresent()) {
         return false;
      } else {
         ReplaySessionRecord session = (ReplaySessionRecord)sessionOpt.get();
         List<ReplayActionRecord> actions = this.storage.fetchActions(session.getId());
         if (actions.isEmpty()) {
            return false;
         } else {
            List<ReplayActorRecord> actorRecords = this.storage.fetchActors(session.getId());
            World world = session.getWorld() == null ? viewer.getWorld() : Bukkit.getWorld(session.getWorld());
            if (world == null) {
               world = viewer.getWorld();
            }

            if (world == null) {
               return false;
            } else {
               AdvancedReplayEngine.ActiveReplay playback = new AdvancedReplayEngine.ActiveReplay(viewer.getUniqueId(), report, session, world, actions);
               for (ReplayActorRecord actor : actorRecords) {
                  playback.actors.put(actor.getId(), new AdvancedReplayEngine.ActorPlaybackState(actor));
                  if (playback.primaryActorId == null && actor.getActorType() == ReplayActorType.PLAYER) {
                     playback.primaryActorId = actor.getId();
                  }
               }

               int taskId = this.plugin.getServer().getScheduler().scheduleSyncRepeatingTask(this.plugin, () -> {
                  this.tick(playback);
               }, 0L, TICK_STEP);
               if (taskId == -1) {
                  return false;
               } else {
                  playback.taskId = taskId;
                  this.active.put(viewer.getUniqueId(), playback);
                  this.updateHud(playback);
                  return true;
               }
            }
         }
      }
   }

   public void stop(Player viewer) {
      AdvancedReplayEngine.ActiveReplay session = this.active.remove(viewer.getUniqueId());
      if (session != null) {
         if (session.taskId != -1) {
            this.plugin.getServer().getScheduler().cancelTask(session.taskId);
         }

         session.actors.values().forEach((state) -> {
            if (state.npc != null) {
               try { NPCReplayManager.untrackReplayEntity(state.npc.getEntity()); } catch (Throwable ignored) {}
               state.npc.destroy();
               state.npc = null;
            }
            if (state.displayEntity != null) {
               try { NPCReplayManager.untrackReplayEntity(state.displayEntity); } catch (Throwable ignored) {}
               try { state.displayEntity.remove(); } catch (Throwable ignored) {}
               state.displayEntity = null;
            }
         });
         this.restoreBlocks(session);
         this.clearProjectiles(session);
         session.blockSnapshots.clear();
         session.activeProjectiles.clear();
         Player onlineViewer = Bukkit.getPlayer(viewer.getUniqueId());
         if (onlineViewer != null && onlineViewer.isOnline()) {
            try {
               if (onlineViewer.getGameMode() == GameMode.SPECTATOR) {
                  onlineViewer.setSpectatorTarget(null);
               }
            } catch (Throwable ignored) {}
         }
         Player target = onlineViewer != null ? onlineViewer : viewer;
         NPCReplayManager.restoreViewerState(target);
      }
   }

   public boolean isRunning(Player viewer) {
      return this.active.containsKey(viewer.getUniqueId());
   }

   public Report getReport(Player viewer) {
      AdvancedReplayEngine.ActiveReplay playback = this.active.get(viewer.getUniqueId());
      return playback == null ? null : playback.report;
   }

   public boolean togglePause(Player viewer) {
      AdvancedReplayEngine.ActiveReplay playback = this.active.get(viewer.getUniqueId());
      if (playback == null) return false;
      playback.paused = !playback.paused;
      this.updateHud(playback);
      return playback.paused;
   }

   public double changeSpeed(Player viewer, double delta) {
      AdvancedReplayEngine.ActiveReplay playback = this.active.get(viewer.getUniqueId());
      if (playback == null) return 1.0D;
      playback.speed = Math.max(0.5D, Math.min(4.0D, playback.speed + delta));
      this.updateHud(playback);
      return playback.speed;
   }

   public void restart(Player viewer) {
      AdvancedReplayEngine.ActiveReplay playback = this.active.get(viewer.getUniqueId());
      if (playback != null) {
         this.rebuildToTick(playback, 0, viewer);
         playback.paused = false;
         this.updateHud(playback);
      }
   }

   public boolean seek(Player viewer, int deltaTicks) {
      AdvancedReplayEngine.ActiveReplay playback = this.active.get(viewer.getUniqueId());
      if (playback == null) return false;
      if (playback.actions.isEmpty()) return false;

      int target = playback.currentTick + deltaTicks;
      target = Math.max(0, Math.min(playback.maxTick, target));
      this.rebuildToTick(playback, target, viewer);
      this.updateHud(playback);
      return true;
   }

   private void tick(AdvancedReplayEngine.ActiveReplay playback) {
      Player viewer = Bukkit.getPlayer(playback.viewerId);
      if (viewer != null && viewer.isOnline()) {
         if (!playback.paused) {
            while (playback.pointer < playback.actions.size() &&
                   playback.actions.get(playback.pointer).getTick() <= playback.currentTick) {
               this.applyAction(playback, playback.actions.get(playback.pointer), viewer);
               ++playback.pointer;
            }

            playback.currentTick += Math.max(1, (int)Math.round(TICK_STEP * playback.speed));
            this.updateHud(playback);
            if (playback.pointer >= playback.actions.size() || playback.currentTick > playback.maxTick + 4) {
               this.stopInternal(playback);
            }
         }
      } else {
         this.stopInternal(playback);
      }
   }

   private void stopInternal(AdvancedReplayEngine.ActiveReplay playback) {
      Player player = Bukkit.getPlayer(playback.viewerId);
      if (player != null) {
         this.stop(player);
         ReplayControlManager.end(player);
      } else {
         this.active.remove(playback.viewerId);
         if (playback.taskId != -1) {
            this.plugin.getServer().getScheduler().cancelTask(playback.taskId);
         }

         playback.actors.values().forEach((state) -> {
            if (state.npc != null) {
               try { NPCReplayManager.untrackReplayEntity(state.npc.getEntity()); } catch (Throwable ignored) {}
               state.npc.destroy();
               state.npc = null;
            }
            if (state.displayEntity != null) {
               try { NPCReplayManager.untrackReplayEntity(state.displayEntity); } catch (Throwable ignored) {}
               try { state.displayEntity.remove(); } catch (Throwable ignored) {}
               state.displayEntity = null;
            }
         });
         this.restoreBlocks(playback);
         this.clearProjectiles(playback);
         playback.blockSnapshots.clear();
         playback.activeProjectiles.clear();
         NPCReplayManager.clearViewerState(playback.viewerId);
      }
   }

   private void rebuildToTick(AdvancedReplayEngine.ActiveReplay playback, int targetTick, Player viewer) {
      this.restoreBlocks(playback);
      this.clearProjectiles(playback);
      playback.blockSnapshots.clear();
      playback.activeProjectiles.clear();
      playback.actors.values().forEach((state) -> {
         if (state.npc != null) {
            try { NPCReplayManager.untrackReplayEntity(state.npc.getEntity()); } catch (Throwable ignored) {}
            state.npc.destroy();
            state.npc = null;
         }
         if (state.displayEntity != null) {
            try { NPCReplayManager.untrackReplayEntity(state.displayEntity); } catch (Throwable ignored) {}
            try { state.displayEntity.remove(); } catch (Throwable ignored) {}
            state.displayEntity = null;
         }
         state.lastLocation = null;
         state.lastKnownHealth = -1.0D;
         state.lastKnownMaxHealth = -1.0D;
         state.lastKnownAbsorption = 0.0D;
      });
      boolean wasPaused = playback.paused;
      playback.pointer = 0;
      playback.currentTick = 0;
      playback.cameraSet = false;
      Player actualViewer = viewer != null ? viewer : Bukkit.getPlayer(playback.viewerId);

      for (int i = 0; i < playback.actions.size(); ++i) {
         ReplayActionRecord action = playback.actions.get(i);
         if (action.getTick() > targetTick) {
            playback.pointer = i;
            playback.currentTick = targetTick;
            playback.paused = wasPaused;
            this.updateHud(playback);
            return;
         }
         this.applyAction(playback, action, actualViewer);
      }

      playback.pointer = playback.actions.size();
      playback.currentTick = targetTick;
      playback.paused = wasPaused;
      this.updateHud(playback);
   }

   private boolean supportsMobDisplay() {
      return NPCReplayManager.isMobDisplaySupportedRuntime();
   }

   private Entity resolveActorEntity(AdvancedReplayEngine.ActorPlaybackState state) {
      if (state == null) return null;
      if (state.mobDisplay && state.displayEntity != null) return state.displayEntity;
      if (state.npc != null) return state.npc.getEntity();
      return null;
   }

   private LivingEntity resolveActorLiving(AdvancedReplayEngine.ActorPlaybackState state) {
      Entity entity = resolveActorEntity(state);
      return entity instanceof LivingEntity ? (LivingEntity) entity : null;
   }

   private void configureSpawnedEntity(AdvancedReplayEngine.ActorPlaybackState state, Entity entity) {
      if (!(entity instanceof LivingEntity)) return;
      LivingEntity living = (LivingEntity) entity;
      this.trySetInvulnerable(living, false);
      this.initializeHealthFromState(living, state);
      try { living.setRemoveWhenFarAway(false); } catch (Throwable ignored) {}
      try { living.setCanPickupItems(false); } catch (Throwable ignored) {}
      clearPotions(living);
      applyFireSafety(living);
      this.applyCachedEquipment(state);
   }

  private void teleportDisplayEntity(Entity entity, Location target) {
      if (entity == null || target == null) return;
      try {
        entity.teleport(target, TeleportCause.PLUGIN);
      } catch (Throwable ignored) {
        try { entity.teleport(target); } catch (Throwable ignored2) {}
      }
      if (entity instanceof LivingEntity) {
        try { entity.setVelocity(new Vector(0, 0, 0)); } catch (Throwable ignored) {}
        clearPotions((LivingEntity) entity);
      }
      applyFireSafety(entity);
   }

  private void clearPotions(LivingEntity living) {
      if (living == null) return;
      try {
        for (PotionEffect effect : living.getActivePotionEffects()) {
           living.removePotionEffect(effect.getType());
        }
        try {
           LivingEntity.class.getMethod("setPotionParticles", boolean.class).invoke(living, false);
        } catch (Throwable ignoredInner) {}
        try { living.setFireTicks(0); } catch (Throwable ignoredInner) {}
        try { living.getClass().getMethod("extinguish").invoke(living); } catch (Throwable ignoredInner) {}
      } catch (Throwable ignored) {}
  }

   private void applyFireSafety(Entity entity) {
      if (entity == null) return;
      try { entity.getClass().getMethod("setImmuneToFire", boolean.class).invoke(entity, true); } catch (Throwable ignored) {}
      if (entity instanceof LivingEntity) {
         LivingEntity living = (LivingEntity) entity;
         try { living.getClass().getMethod("setShouldBurnInDay", boolean.class).invoke(living, false); } catch (Throwable ignored) {}
         try { living.setFireTicks(0); } catch (Throwable ignored) {}
         try { living.getClass().getMethod("extinguish").invoke(living); } catch (Throwable ignored) {}
      } else {
         try { entity.setFireTicks(0); } catch (Throwable ignored) {}
      }
   }

   private void applyAction(AdvancedReplayEngine.ActiveReplay playback, ReplayActionRecord action, Player viewer) {
      AdvancedReplayEngine.ActorPlaybackState state = playback.actors.get(action.getActorId());
      if (state != null) {
         JsonObject payload = action.getPayload();
         switch(action.getActionType()) {
            case MOVE: this.handleMove(playback, state, payload, viewer); break;
            case LOOK: this.handleLook(playback, state, payload); break;
            case VELOCITY: this.handleVelocity(playback, state, payload); break;
            case ANIMATION: this.handleAnimation(state, payload); break;
            case ITEM_HOLD:
            case EQUIP:
            case UNEQUIP: this.handleEquip(state, payload); break;
            case BLOCK_PLACE: this.handleBlockPlace(playback, payload); break;
            case BLOCK_BREAK: this.handleBlockBreak(playback, payload); break;
            case DAMAGE: this.handleDamage(playback, state, payload); break;
            case PROJECTILE_LAUNCH: this.handleProjectileLaunch(playback, state, payload); break;
            case PROJECTILE_HIT: this.handleProjectileHit(playback, payload); break;
            case STATE_CHANGE: this.handleStateChange(playback, state, payload, viewer); break;
         }
      }
   }

   private void handleMove(AdvancedReplayEngine.ActiveReplay playback, AdvancedReplayEngine.ActorPlaybackState state, JsonObject payload, Player viewer) {
      Location location = this.buildLocation(playback.world, payload, state.lastLocation);
      if (location != null) {
         this.spawnActor(playback, state, location, viewer);
      }
   }

   private void handleLook(AdvancedReplayEngine.ActiveReplay playback, AdvancedReplayEngine.ActorPlaybackState state, JsonObject payload) {
      Entity entity = resolveActorEntity(state);
      if (entity == null) return;
      Location base = entity.getLocation();
      float yaw = payload.has("yaw") ? payload.get("yaw").getAsFloat() : base.getYaw();
      float pitch = payload.has("pitch") ? payload.get("pitch").getAsFloat() : base.getPitch();
      base.setYaw(yaw);
      base.setPitch(pitch);
      entity.teleport(base);
      if (!state.mobDisplay && state.npc != null) {
         this.syncPacketNpc(state.npc, base, true);
      }
   }

   private void handleVelocity(AdvancedReplayEngine.ActiveReplay playback, AdvancedReplayEngine.ActorPlaybackState state, JsonObject payload) {
      Entity entity = resolveActorEntity(state);
      if (entity == null) return;
      double x = payload.has("x") ? payload.get("x").getAsDouble() : 0.0D;
      double y = payload.has("y") ? payload.get("y").getAsDouble() : 0.0D;
      double z = payload.has("z") ? payload.get("z").getAsDouble() : 0.0D;
      entity.setVelocity(new Vector(x, y, z));
   }

   private void handleEquip(AdvancedReplayEngine.ActorPlaybackState state, JsonObject payload) {
      if (state == null || payload == null) return;

      String itemData = payload.has("item") ? payload.get("item").getAsString() : null;
      ItemStack item = itemData == null ? null : BukkitUtils.deserializeItemStack(itemData);
      ItemStack cached = cloneItem(item);
      String slotToken = payload.has("slot") ? payload.get("slot").getAsString() : "HAND";
      String slot = slotToken == null ? "HAND" : slotToken.toUpperCase(Locale.ROOT);

      int slotIndex;
      switch(slot) {
         case "HELMET": slotIndex = 0; break;
         case "CHESTPLATE": slotIndex = 1; break;
         case "LEGGINGS": slotIndex = 2; break;
         case "BOOTS": slotIndex = 3; break;
         case "OFFHAND": slotIndex = 4; break;
         case "HAND":
         default: slotIndex = 5;
      }

      switch(slotIndex) {
         case 0:
         case 1:
         case 2:
         case 3: state.cachedArmor[slotIndex] = cached; break;
         case 4:
            state.cachedOffHand = cached;
            if (!SUPPORTS_OFF_HAND) state.cachedMainHand = cached;
            break;
         case 5:
         default: state.cachedMainHand = cached;
      }

      Entity entity = resolveActorEntity(state);
      if (entity instanceof LivingEntity) {
         LivingEntity living = (LivingEntity) entity;
         EntityEquipment equipment = living.getEquipment();
         boolean isArmorStand = entity instanceof ArmorStand;
         if (equipment != null) {
            switch (slotIndex) {
               case 0: equipment.setHelmet(isArmorStand ? null : cloneItem(state.cachedArmor[0])); break;
               case 1: equipment.setChestplate(isArmorStand ? null : cloneItem(state.cachedArmor[1])); break;
               case 2: equipment.setLeggings(isArmorStand ? null : cloneItem(state.cachedArmor[2])); break;
               case 3: equipment.setBoots(isArmorStand ? null : cloneItem(state.cachedArmor[3])); break;
               case 4:
                  if (isArmorStand) {
                     if (SUPPORTS_OFF_HAND) this.setOffHandItem(equipment, null);
                     else equipment.setItemInHand(null);
                  } else if (!SUPPORTS_OFF_HAND || !this.setOffHandItem(equipment, cloneItem(state.cachedOffHand))) {
                     equipment.setItemInHand(cloneItem(state.cachedMainHand));
                  }
                  break;
               case 5:
               default:
                  equipment.setItemInHand(isArmorStand ? null : cloneItem(state.cachedMainHand));
            }
         }
      }
      if (!state.mobDisplay && state.npc != null) {
         this.syncPacketEquipment(state.npc, slotIndex, cached);
      }
   }

   private ItemStack cloneItem(ItemStack item) { return item == null ? null : item.clone(); }

   private boolean setOffHandItem(EntityEquipment equipment, ItemStack item) {
      if (equipment == null || !SUPPORTS_OFF_HAND) return false;
      try {
         EntityEquipment.class.getMethod("setItemInOffHand", ItemStack.class).invoke(equipment, item);
         return true;
      } catch (Throwable ignored) {
         return false;
      }
   }

   private void handleAnimation(AdvancedReplayEngine.ActorPlaybackState state, JsonObject payload) {
      if (state == null || payload == null) return;
      Entity entity = resolveActorEntity(state);
      if (!(entity instanceof LivingEntity)) return;
      String animation = payload.has("animation") ? payload.get("animation").getAsString() : null;
      if (animation != null && !animation.isEmpty()) {
         this.playAnimation(state, animation);
      }
   }

   private void handleBlockPlace(AdvancedReplayEngine.ActiveReplay playback, JsonObject payload) {
      Block block = this.getBlock(playback.world, payload);
      if (block != null) {
         AdvancedReplayEngine.BlockKey key = AdvancedReplayEngine.BlockKey.of(block);
         playback.blockSnapshots.putIfAbsent(key, AdvancedReplayEngine.BlockSnapshot.capture(block));
         String materialName = payload.has("material") ? payload.get("material").getAsString() : null;
         Material material = materialName == null ? null : MaterialResolver.resolveMaterial(materialName);
         if (material == null && materialName != null) {
            try { material = Material.valueOf(materialName); } catch (IllegalArgumentException ignored) {}
         }
         if (material != null) {
            block.setType(material);
            if (payload.has("data")) {
               try { block.setData((byte)payload.get("data").getAsInt()); } catch (Throwable ignored) {}
            }
         }
      }
   }

   private void handleBlockBreak(AdvancedReplayEngine.ActiveReplay playback, JsonObject payload) {
      Block block = this.getBlock(playback.world, payload);
      if (block != null) {
         AdvancedReplayEngine.BlockKey key = AdvancedReplayEngine.BlockKey.of(block);
         playback.blockSnapshots.putIfAbsent(key, AdvancedReplayEngine.BlockSnapshot.capture(block));
         block.setType(Material.AIR);
      }
   }

   private void handleDamage(AdvancedReplayEngine.ActiveReplay playback, AdvancedReplayEngine.ActorPlaybackState source, JsonObject payload) {
      Entity attackerEntity = resolveActorEntity(source);
      if (attackerEntity instanceof LivingEntity) {
         String attackAnimation = (payload != null && payload.has("sourceAnimation")) ? payload.get("sourceAnimation").getAsString() : "SWING_MAIN_HAND";
         this.playAnimation(source, (attackAnimation == null || attackAnimation.isEmpty()) ? "SWING_MAIN_HAND" : attackAnimation);
      }

      if (payload == null) { this.updateHud(playback); return; }

      double damage = 0.0D;
      if (payload.has("damage")) damage = payload.get("damage").getAsDouble();
      else if (payload.has("amount")) damage = payload.get("amount").getAsDouble();

      if (payload.has("targetActor")) {
         int targetId = payload.get("targetActor").getAsInt();
         AdvancedReplayEngine.ActorPlaybackState target = playback.actors.get(targetId);
         LivingEntity living = resolveActorLiving(target);
         if (living != null) {
            double maxHealth = living.getMaxHealth();
            if (payload.has("targetMaxHealth")) maxHealth = payload.get("targetMaxHealth").getAsDouble();
            this.trySetMaxHealth(living, maxHealth);

            double newHealth;
            if (payload.has("targetHealth")) newHealth = payload.get("targetHealth").getAsDouble();
            else {
               double basis = (target.lastKnownHealth >= 0.0D) ? target.lastKnownHealth : safeGetHealth(living);
               newHealth = basis - damage;
            }

            double absorption = target.lastKnownAbsorption;
            if (payload.has("targetAbsorption")) absorption = payload.get("targetAbsorption").getAsDouble();
            else absorption = resolveAbsorption(living);

            double clampMax = Math.max(0.1D, living.getMaxHealth());
            newHealth = Math.max(0.0D, Math.min(clampMax, newHealth));

            try { living.setNoDamageTicks(0); } catch (Throwable ignored) {}
            try {
               if (damage > 0.0D) {
                  if (attackerEntity != null && attackerEntity instanceof LivingEntity) {
                     living.damage(Math.max(0.001D, damage), (LivingEntity) attackerEntity);
                  } else {
                     living.damage(Math.max(0.001D, damage));
                  }
               } else {
                  living.playEffect(EntityEffect.HURT);
               }
            } catch (Throwable ignored) { try { living.playEffect(EntityEffect.HURT); } catch (Throwable ignore2) {} }

            this.trySetHealth(living, newHealth);
            this.playAnimation(target, "DAMAGE");

            target.lastKnownHealth = newHealth;
            if (maxHealth > 0.0D) target.lastKnownMaxHealth = maxHealth;
            target.lastKnownAbsorption = Math.max(0.0D, absorption);
         }
      }

      this.updateHud(playback);
   }

   private double safeGetHealth(LivingEntity living) {
      try { return living.getHealth(); } catch (Throwable ignored) { return 0.0D; }
   }

   private void handleProjectileLaunch(AdvancedReplayEngine.ActiveReplay playback, AdvancedReplayEngine.ActorPlaybackState state, JsonObject payload) {
      if (payload.has("uuid")) {
         String uuid = payload.get("uuid").getAsString();
         Location location = this.buildLocation(playback.world, payload, state.lastLocation);
         if (location != null) {
            String typeName = payload.has("projectileType") ? payload.get("projectileType").getAsString() : "ARROW";
            EntityType type = EntityType.ARROW;
            try { type = EntityType.valueOf(typeName); }
            catch (IllegalArgumentException e) { EntityType alt = EntityType.fromName(typeName); if (alt != null) type = alt; }

            Entity projectile;
            try { projectile = playback.world.spawnEntity(location, type); }
            catch (Throwable ignored) { projectile = playback.world.spawnEntity(location, EntityType.ARROW); }

            if (payload.has("velX")) {
               double vx = payload.get("velX").getAsDouble();
               double vy = payload.get("velY").getAsDouble();
               double vz = payload.get("velZ").getAsDouble();
               projectile.setVelocity(new Vector(vx, vy, vz));
            }

            playback.activeProjectiles.put(uuid, projectile);
         }
      }
   }

   private void handleProjectileHit(AdvancedReplayEngine.ActiveReplay playback, JsonObject payload) {
      if (payload.has("uuid")) {
         String uuid = payload.get("uuid").getAsString();
         Entity projectile = playback.activeProjectiles.remove(uuid);
         if (projectile != null) {
            Location hit = this.buildLocation(playback.world, payload, projectile.getLocation());
            if (hit != null) projectile.teleport(hit);
            projectile.remove();
            try { projectile.getWorld().playEffect(projectile.getLocation(), Effect.SMOKE, 4); } catch (Throwable ignored) {}
         }
      }
   }

   private void handleStateChange(AdvancedReplayEngine.ActiveReplay playback, AdvancedReplayEngine.ActorPlaybackState state, JsonObject payload, Player viewer) {
      if (payload != null) {
         if (payload.has("action")) {
            String action = payload.get("action").getAsString();
            if ("join".equalsIgnoreCase(action)) {
               Location location = this.buildLocation(playback.world, payload, state.lastLocation);
               this.spawnActor(playback, state, location, viewer);
            } else if ("quit".equalsIgnoreCase(action)) {
               this.despawnActor(state);
            }
         }

         if (payload.has("health")) state.lastKnownHealth = payload.get("health").getAsDouble();
         if (payload.has("maxHealth")) state.lastKnownMaxHealth = payload.get("maxHealth").getAsDouble();
         if (payload.has("targetHealth")) state.lastKnownHealth = payload.get("targetHealth").getAsDouble();
         if (payload.has("targetMaxHealth")) state.lastKnownMaxHealth = payload.get("targetMaxHealth").getAsDouble();
         if (payload.has("absorption")) state.lastKnownAbsorption = payload.get("absorption").getAsDouble();
         if (payload.has("targetAbsorption")) state.lastKnownAbsorption = payload.get("targetAbsorption").getAsDouble();

         this.updateHud(playback);
      }
   }

   private void spawnActor(AdvancedReplayEngine.ActiveReplay playback, AdvancedReplayEngine.ActorPlaybackState state, Location location, Player viewer) {
      if (location == null || state == null) return;

      if (state.mobDisplay && state.displayEntity != null) {
         if (!state.displayEntity.isValid()) {
            try { NPCReplayManager.untrackReplayEntity(state.displayEntity); } catch (Throwable ignored) {}
            try { state.displayEntity.remove(); } catch (Throwable ignored) {}
            state.displayEntity = null;
            state.mobDisplay = false;
         } else {
            teleportDisplayEntity(state.displayEntity, location);
            state.lastLocation = location.clone();
            if (!playback.cameraSet && playback.primaryActorId != null && playback.primaryActorId == state.actorRecord.getId() && viewer != null) {
               this.positionViewer(viewer, null, location);
               playback.cameraSet = true;
            }
            return;
         }
      }

      if (state.npc == null) {
         if (supportsMobDisplay()) {
            Entity mob = NPCReplayManager.spawnReplayDisplayEntity(location);
            if (mob != null) {
               state.displayEntity = mob;
               state.mobDisplay = true;
               NPCReplayManager.trackReplayEntity(mob);
               configureSpawnedEntity(state, mob);
               teleportDisplayEntity(mob, location);
               state.lastLocation = location.clone();
               if (!playback.cameraSet && playback.primaryActorId != null && playback.primaryActorId == state.actorRecord.getId() && viewer != null) {
                  this.positionViewer(viewer, null, location);
                  playback.cameraSet = true;
               }
               return;
            }
         }

         NPC npc = NPCLibrary.createNPC(EntityType.PLAYER, state.actorRecord.getName());
         if (playback.viewerId != null) {
            npc.data().set(NPCReplayManager.REPLAY_VIEWER_KEY, playback.viewerId.toString());
         } else {
            npc.data().set(NPCReplayManager.REPLAY_VIEWER_KEY, null);
         }
         npc.spawn(location);
         state.npc = npc;
         state.mobDisplay = false;
         state.displayEntity = null;
         this.exposeNpcEntity(npc);
         NPCReplayManager.trackReplayEntity(npc.getEntity());

         configureSpawnedEntity(state, npc.getEntity());
         if (!playback.cameraSet && playback.primaryActorId != null && playback.primaryActorId == state.actorRecord.getId() && viewer != null) {
            this.positionViewer(viewer, npc, location);
            playback.cameraSet = true;
         }
      } else {
         Entity entity = state.npc.getEntity();
         if (entity != null) {
            entity.teleport(location);
            this.ensureVisible(entity);
            NPCReplayManager.trackReplayEntity(entity);
         }
      }

      boolean updateRotation = state.lastLocation == null ||
              Math.abs(location.getYaw() - (state.lastLocation != null ? state.lastLocation.getYaw() : 0.0F)) > 0.01F ||
              Math.abs(location.getPitch() - (state.lastLocation != null ? state.lastLocation.getPitch() : 0.0F)) > 0.01F;
      if (state.npc != null) {
         this.syncPacketNpc(state.npc, location, updateRotation);
      }
      state.lastLocation = location.clone();
      this.maskNearbyArmorStands(location);
   }

   private void initializeHealthFromState(LivingEntity living, AdvancedReplayEngine.ActorPlaybackState state) {
      try {
         double max = state.lastKnownMaxHealth > 0.0D ? state.lastKnownMaxHealth : living.getMaxHealth();
         this.trySetMaxHealth(living, max);
         double init = state.lastKnownHealth >= 0.0D ? Math.min(max, state.lastKnownHealth) : Math.min(max, living.getHealth());
         this.trySetHealth(living, init);
      } catch (Throwable ignored) {}
   }

   private void applyCachedEquipment(AdvancedReplayEngine.ActorPlaybackState state) {
      if (state == null) return;

      Entity entity = resolveActorEntity(state);
      if (!(entity instanceof LivingEntity)) return;

      LivingEntity living = (LivingEntity) entity;
      EntityEquipment equipment = living.getEquipment();
      if (equipment == null) return;

      boolean isArmorStand = entity instanceof ArmorStand;
      if (isArmorStand) {
         equipment.setHelmet(null);
         equipment.setChestplate(null);
         equipment.setLeggings(null);
         equipment.setBoots(null);
         equipment.setItemInHand(null);
         if (SUPPORTS_OFF_HAND) this.setOffHandItem(equipment, null);
      } else {
         equipment.setHelmet(cloneItem(state.cachedArmor[0]));
         equipment.setChestplate(cloneItem(state.cachedArmor[1]));
         equipment.setLeggings(cloneItem(state.cachedArmor[2]));
         equipment.setBoots(cloneItem(state.cachedArmor[3]));
         equipment.setItemInHand(cloneItem(state.cachedMainHand));
         ItemStack off = cloneItem(state.cachedOffHand);
         if (off != null || state.cachedOffHand == null) this.setOffHandItem(equipment, off);
      }

      if (!state.mobDisplay && state.npc != null) {
         this.syncPacketEquipment(state.npc, 0, state.cachedArmor[0]);
         this.syncPacketEquipment(state.npc, 1, state.cachedArmor[1]);
         this.syncPacketEquipment(state.npc, 2, state.cachedArmor[2]);
         this.syncPacketEquipment(state.npc, 3, state.cachedArmor[3]);
         this.syncPacketEquipment(state.npc, 4, state.cachedOffHand);
         this.syncPacketEquipment(state.npc, 5, state.cachedMainHand);
      }
   }

   private void despawnActor(AdvancedReplayEngine.ActorPlaybackState state) {
     if (state == null) return;
     state.lastLocation = null;
     if (state.npc != null) {
        try { NPCReplayManager.untrackReplayEntity(state.npc.getEntity()); } catch (Throwable ignored) {}
        try { state.npc.destroy(); } catch (Throwable ignored) {}
        state.npc = null;
     }
     if (state.displayEntity != null) {
        try { NPCReplayManager.untrackReplayEntity(state.displayEntity); } catch (Throwable ignored) {}
        try { state.displayEntity.remove(); } catch (Throwable ignored) {}
        state.displayEntity = null;
        state.mobDisplay = false;
     }
   }

   private void playAnimation(AdvancedReplayEngine.ActorPlaybackState state, String animation) {
      if (state == null) return;
      Entity actor = resolveActorEntity(state);
      if (!(actor instanceof LivingEntity)) return;

      if (!state.mobDisplay && state.npc != null) {
         NPCAnimation npcAnimation = resolveNpcAnimation(animation);
         if (npcAnimation != null) {
            try { state.npc.playAnimation(npcAnimation); } catch (Throwable ignored) {}
         }
      }

      playAnimationLegacy((LivingEntity) actor, animation);
   }

   private NPCAnimation resolveNpcAnimation(String animation) {
      if (animation == null) {
         return NPCAnimation.SWING_ARM;
      }
      switch (animation.toUpperCase(Locale.ROOT)) {
         case "SWING_MAIN_HAND":
         case "ARM_SWING":
         case "ARM_SWING_FINISH":
            return NPCAnimation.SWING_ARM;
         case "SWING_OFF_HAND":
         case "OFF_ARM_SWING":
            return NPCAnimation.SWING_ARM;
         case "CRITICAL_HIT":
            return NPCAnimation.CRITICAL_HIT;
         case "MAGIC_CRITICAL_HIT":
            return NPCAnimation.MAGIC_CRITICAL_HIT;
         case "DAMAGE":
            return NPCAnimation.DAMAGE;
         default:
            return null;
      }
   }

   private void playAnimationLegacy(LivingEntity entity, String animation) {
      String key = animation.toUpperCase(Locale.ROOT);
      byte kind = -1;
      switch(key.hashCode()) {
         case -1906106325: if (key.equals("OFF_ARM_SWING")) kind = 3; break;
         case -1597342304: if (key.equals("SWING_OFF_HAND")) kind = 4; break;
         case -879893740:  if (key.equals("SWING_MAIN_HAND")) kind = 1; break;
         case -287127853:  if (key.equals("CRITICAL_HIT")) kind = 5; break;
         case 1486236741:  if (key.equals("MAGIC_CRITICAL_HIT")) kind = 6; break;
         case 1531104823:  if (key.equals("ARM_SWING_FINISH")) kind = 2; break;
         case 2073496347:  if (key.equals("ARM_SWING")) kind = 0; break;
      }

      switch(kind) {
         case 0:
         case 1:
         case 2: if (!this.swingHand(entity, true) && !this.playEntityEffect(entity, "SWING_ARM")) this.playEntityEffect(entity, "ARM_SWING"); break;
         case 3:
         case 4: this.swingHand(entity, false); break;
         case 5: try { entity.getWorld().playEffect(entity.getLocation(), Effect.CRIT, 0); } catch (Throwable ignored) {} break;
         case 6: try { entity.getWorld().playEffect(entity.getLocation(), Effect.MAGIC_CRIT, 0); } catch (Throwable ignored) {} break;
      }
   }

   private boolean swingHand(LivingEntity entity, boolean mainHand) {
      try {
         Method method = entity.getClass().getMethod(mainHand ? "swingMainHand" : "swingOffHand");
         method.invoke(entity);
         return true;
      } catch (Throwable ignored) {}
      return this.playEntityEffect(entity, "ARM_SWING") || this.playEntityEffect(entity, "SWING_ARM");
   }

   private boolean playEntityEffect(Entity entity, String fieldName) {
      try {
         Object value = EntityEffect.class.getField(fieldName).get(null);
         if (value instanceof EntityEffect) {
            entity.playEffect((EntityEffect)value);
            return true;
         }
      } catch (Throwable ignored) {}
      return false;
   }

   private void exposeNpcEntity(NPC npc) {
      if (npc != null) {
         Entity entity = npc.getEntity();
         this.ensureVisible(entity);
         if (entity instanceof Player) {
            ((Player)entity).setSleepingIgnored(true);
         }
      }
   }

   private void ensureVisible(Entity entity) {
      if (entity == null) return;
      try { entity.getClass().getMethod("setInvisible", boolean.class).invoke(entity, false); } catch (Throwable ignored) { try { entity.setCustomNameVisible(false); } catch (Throwable ignored2) {} }
      this.trySetInvulnerable(entity, false);
      try { entity.setCustomNameVisible(false); } catch (Throwable ignored) {}
      if (entity instanceof LivingEntity) {
         LivingEntity living = (LivingEntity)entity;
         living.setCanPickupItems(false);
         living.setRemoveWhenFarAway(false);
         try { living.setCustomNameVisible(false); } catch (Throwable ignored) {}

         for (PotionEffect effect : living.getActivePotionEffects()) {
            try {
               if (effect != null && effect.getType() != null && "INVISIBILITY".equalsIgnoreCase(effect.getType().getName())) {
                  living.removePotionEffect(effect.getType());
                  break;
               }
            } catch (Throwable ignored) {}
         }
      }
   }

   private void maskNearbyArmorStands(Location location) {
      if (location == null) return;
      World world = location.getWorld();
      if (world == null) return;
      try {
         for (Entity entity : world.getNearbyEntities(location, 1.5D, 2.5D, 1.5D)) {
            if (entity.getType() == EntityType.ARMOR_STAND && entity instanceof ArmorStand) {
               ArmorStand stand = (ArmorStand)entity;
               try { stand.setVisible(false); } catch (Throwable ignored) {}
               try { stand.setMarker(true); } catch (Throwable ignored) {}
               try { stand.setGravity(false); } catch (Throwable ignored) {}
               try { stand.setBasePlate(false); } catch (Throwable ignored) {}
               try { stand.setArms(true); } catch (Throwable ignored) {}
            }
         }
      } catch (Throwable ignored) {}
   }

   private void syncPacketNpc(NPC npc, Location target, boolean updateRotation) {
      if (npc == null || target == null) return;
      Entity anchor = npc.getEntity();
      if (anchor == null) return;
      try { PacketNPCManager.handleTeleport(anchor, target.clone(), updateRotation); } catch (Throwable ignored) {}
   }

   private void syncPacketEquipment(NPC npc, int slotIndex, ItemStack item) {
      if (npc == null) return;
      Entity anchor = npc.getEntity();
      if (anchor == null) return;
      try { PacketNPCManager.syncEquipment(anchor, slotIndex, cloneItem(item)); } catch (Throwable ignored) {}
   }

   private Location buildLocation(World world, JsonObject payload, Location fallback) {
      if (payload != null && payload.has("x") && payload.has("y") && payload.has("z")) {
         double x = payload.get("x").getAsDouble();
         double y = payload.get("y").getAsDouble();
         double z = payload.get("z").getAsDouble();
         float yaw = payload.has("yaw") ? payload.get("yaw").getAsFloat() : (fallback != null ? fallback.getYaw() : 0.0F);
         float pitch = payload.has("pitch") ? payload.get("pitch").getAsFloat() : (fallback != null ? fallback.getPitch() : 0.0F);
         return new Location(world, x, y, z, yaw, pitch);
      } else {
         return fallback == null ? null : fallback.clone();
      }
   }

   private Block getBlock(World world, JsonObject payload) {
      if (payload == null) return null;
      int x = payload.has("x") ? payload.get("x").getAsInt() : (payload.has("hitBlockX") ? payload.get("hitBlockX").getAsInt() : Integer.MIN_VALUE);
      int y = payload.has("y") ? payload.get("y").getAsInt() : (payload.has("hitBlockY") ? payload.get("hitBlockY").getAsInt() : Integer.MIN_VALUE);
      int z = payload.has("z") ? payload.get("z").getAsInt() : (payload.has("hitBlockZ") ? payload.get("hitBlockZ").getAsInt() : Integer.MIN_VALUE);
      return x != Integer.MIN_VALUE && y != Integer.MIN_VALUE && z != Integer.MIN_VALUE ? world.getBlockAt(x, y, z) : null;
   }

   private void restoreBlocks(AdvancedReplayEngine.ActiveReplay playback) {
      playback.blockSnapshots.forEach((key, snapshot) -> {
         Block block = key.toBlock(playback.world);
         if (block != null) snapshot.restore(block);
      });
   }

   private void clearProjectiles(AdvancedReplayEngine.ActiveReplay playback) {
      playback.activeProjectiles.values().forEach((entity) -> {
         if (entity != null && !entity.isDead()) entity.remove();
      });
   }

   private void updateHud(AdvancedReplayEngine.ActiveReplay playback) {
      Player viewer = Bukkit.getPlayer(playback.viewerId);
      if (viewer != null && viewer.isOnline()) {
         String primaryName = resolvePrimaryActorName(playback);
         ReplayControlManager.updateHud(
                 viewer,
                 ReplayControlManager.hudContext(playback.currentTick, playback.maxTick, playback.speed, playback.paused, primaryName),
                 this.buildScoreboardLines(playback)
         );

         String bar = buildActionBarText(playback, primaryName);
         sendActionBar(viewer, bar);
      }
   }

   private String resolvePrimaryActorName(AdvancedReplayEngine.ActiveReplay playback) {
      if (playback.primaryActorId != null) {
         AdvancedReplayEngine.ActorPlaybackState state = playback.actors.get(playback.primaryActorId);
         if (state != null && state.actorRecord != null) return state.actorRecord.getName();
      }
      if (playback.report != null && playback.report.getTarget() != null) return playback.report.getTarget();
      return "-";
   }

   private String buildActionBarText(AdvancedReplayEngine.ActiveReplay playback, String primaryName) {
      String stateIcon = playback.paused ? ChatColor.YELLOW + "⏸" : ChatColor.GREEN + "▶";
      String speedTxt = ChatColor.GOLD + String.format("%.1fx", playback.speed);
      String ticks = ChatColor.WHITE + String.valueOf(playback.currentTick) + ChatColor.GRAY + "/" + ChatColor.DARK_GRAY + playback.maxTick;
      String focus = ChatColor.AQUA + primaryName;
      return stateIcon + ChatColor.GRAY + " | " + focus + ChatColor.GRAY + " | " + speedTxt + ChatColor.GRAY + " | " + ticks;
   }

   private void sendActionBar(Player player, String text) {
      try {
         Method m = PacketNPCManager.class.getMethod("sendActionBar", Player.class, String.class);
         m.invoke(null, player, text);
         return;
      } catch (Throwable ignored) {}
      try {
         Object core = Core.getInstance();
         Method getNms = core.getClass().getMethod("getNms");
         Object nms = getNms.invoke(core);
         Method send = nms.getClass().getMethod("sendActionBar", Player.class, String.class);
         send.invoke(nms, player, text);
         return;
      } catch (Throwable ignored) {}

      try {
         Class<?> compCls = Class.forName("net.kyori.adventure.text.Component");
         Object comp = compCls.getMethod("text", String.class).invoke(null, text);
         Player.class.getMethod("sendActionBar", compCls).invoke(player, comp);
         return;
      } catch (Throwable ignored) {}

      try {
         Object spigot = Player.class.getMethod("spigot").invoke(player);
         Class<?> cmt = Class.forName("net.md_5.bungee.api.ChatMessageType");
         Object ACTION_BAR = Enum.valueOf((Class<Enum>) cmt, "ACTION_BAR");
         Class<?> baseComp = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
         Class<?> textComp = Class.forName("net.md_5.bungee.api.chat.TextComponent");
         Object tc = textComp.getConstructor(String.class).newInstance(text);
         Object array = Array.newInstance(baseComp, 1);
         Array.set(array, 0, tc);
         spigot.getClass().getMethod("sendMessage", cmt, baseComp.arrayType()).invoke(spigot, ACTION_BAR, array);
         return;
      } catch (Throwable ignored) {
      }
   }

   private List<String> buildScoreboardLines(AdvancedReplayEngine.ActiveReplay playback) {
      List<String> lines = new ArrayList<>();
      List<AdvancedReplayEngine.ActorPlaybackState> actorStates = new ArrayList<>(playback.actors.values());
      actorStates.sort(Comparator.comparingInt((statex) -> statex.actorRecord.getId()));

      for (AdvancedReplayEngine.ActorPlaybackState state : actorStates) {
         StringBuilder builder = new StringBuilder();
         boolean primary = playback.primaryActorId != null && playback.primaryActorId == state.actorRecord.getId();
         builder.append(primary ? ChatColor.GREEN : ChatColor.YELLOW).append(state.actorRecord.getName());

         Double showHealth = (state.lastKnownHealth >= 0.0D) ? state.lastKnownHealth : null;
         Double showMax = (state.lastKnownMaxHealth > 0.0D) ? state.lastKnownMaxHealth : null;
         Double showAbsorb = state.lastKnownAbsorption;

         if (showHealth == null || showMax == null) {
            LivingEntity living = resolveActorLiving(state);
            if (living != null) {
               try { if (showHealth == null) showHealth = living.getHealth(); } catch (Throwable ignored) {}
               try { if (showMax == null) showMax = living.getMaxHealth(); } catch (Throwable ignored) {}
            }
         }

         if (showHealth != null) {
            double currentHealth = showHealth;
            double maxHealth = (showMax != null && showMax > 0.0D) ? showMax : 20.0D;
            builder.append(ChatColor.RED).append(" ").append(String.format("%.1f/%.0f\u2764", currentHealth, maxHealth));
            if (showAbsorb != null && showAbsorb > 0.01D) {
               builder.append(ChatColor.GOLD).append(" +").append(String.format("%.1f", showAbsorb));
            }
         } else {
            builder.append(ChatColor.DARK_GRAY).append(" (inactive)");
         }

         lines.add(builder.toString());
         if (lines.size() >= 15) break;
      }
      return lines;
   }

   private static double resolveAbsorption(LivingEntity living) {
      if (living == null) return 0.0D;
      try {
         Object result = LivingEntity.class.getMethod("getAbsorptionAmount").invoke(living);
         if (result instanceof Number) return ((Number)result).doubleValue();
      } catch (Throwable ignored) {}
      return 0.0D;
   }

   private void positionViewer(Player viewer, NPC primaryNpc, Location target) {
      Location focus = target.clone().add(0.0D, 1.6D, 0.0D);
      Vector forward = focus.getDirection();
      if (forward.lengthSquared() < 0.01D) forward = viewer.getLocation().getDirection();
      if (forward.lengthSquared() < 0.01D) forward = new Vector(0, 0, 1);

      Vector sideways = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
      Location camera = focus.clone().add(sideways.multiply(4)).add(0.0D, 1.5D, 0.0D);
      camera.setDirection(focus.toVector().subtract(camera.toVector()));
      viewer.teleport(camera, TeleportCause.PLUGIN);

   }

   private void trySetInvulnerable(Entity entity, boolean invulnerable) {
      try {
         Method m = entity.getClass().getMethod("setInvulnerable", boolean.class);
         m.invoke(entity, invulnerable);
      } catch (Throwable ignored) {
         try {
            if (entity instanceof LivingEntity) {
               LivingEntity le = (LivingEntity) entity;
               le.setNoDamageTicks(invulnerable ? Integer.MAX_VALUE / 4 : 0);
            }
         } catch (Throwable ignored2) {}
      }
   }

   private void trySetMaxHealth(LivingEntity living, double max) {
      if (SUPPORTS_BUKKIT_ATTRIBUTES && livingGetAttributeMethod != null && attributeSetBaseValueMethod != null && genericMaxHealthAttribute != null) {
         try {
            Object attributeInstance = livingGetAttributeMethod.invoke(living, genericMaxHealthAttribute);
            if (attributeInstance != null) {
               attributeSetBaseValueMethod.invoke(attributeInstance, Math.max(0.1D, max));
               return;
            }
         } catch (Throwable ignored) {
         }
      }
      try {
         Method legacy = living.getClass().getMethod("setMaxHealth", double.class);
         legacy.invoke(living, Math.max(0.1D, max));
      } catch (Throwable ignored) {}
   }

   private void trySetHealth(LivingEntity living, double health) {
      try { living.setHealth(Math.max(0.0D, health)); } catch (Throwable ignored) {}
   }

   private static final class ActiveReplay {
      final UUID viewerId;
      final Report report;
      final ReplaySessionRecord session;
      final World world;
      final List<ReplayActionRecord> actions;
      final Map<Integer, AdvancedReplayEngine.ActorPlaybackState> actors = new HashMap<>();
      final Map<AdvancedReplayEngine.BlockKey, AdvancedReplayEngine.BlockSnapshot> blockSnapshots = new HashMap<>();
      final Map<String, Entity> activeProjectiles = new HashMap<>();
      final int maxTick;
      int pointer = 0;
      int currentTick = 0;
      double speed = 1.0D;
      boolean paused = false;
      boolean cameraSet = false;
      Integer primaryActorId;
      int taskId = -1;

      ActiveReplay(UUID viewerId, Report report, ReplaySessionRecord session, World world, List<ReplayActionRecord> actions) {
         this.viewerId = viewerId;
         this.report = report;
         this.session = session;
         this.world = world;
         this.actions = new ArrayList<>(actions);
         this.maxTick = actions.get(actions.size() - 1).getTick();
      }
   }

   private static final class ActorPlaybackState {
      final ReplayActorRecord actorRecord;
      NPC npc;
      Entity displayEntity;
      boolean mobDisplay;
      Location lastLocation;
      double lastKnownHealth = -1.0D;
      double lastKnownMaxHealth = -1.0D;
      double lastKnownAbsorption = 0.0D;
      final ItemStack[] cachedArmor = new ItemStack[4];
      ItemStack cachedMainHand;
      ItemStack cachedOffHand;

      ActorPlaybackState(ReplayActorRecord actorRecord) { this.actorRecord = actorRecord; }
   }

   private static final class BlockKey {
      final int x; final int y; final int z;
      private BlockKey(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
      static AdvancedReplayEngine.BlockKey of(Block block) { return new AdvancedReplayEngine.BlockKey(block.getX(), block.getY(), block.getZ()); }
      Block toBlock(World world) { return world.getBlockAt(this.x, this.y, this.z); }
      public boolean equals(Object o) {
         if (this == o) return true;
         if (!(o instanceof AdvancedReplayEngine.BlockKey)) return false;
         AdvancedReplayEngine.BlockKey other = (AdvancedReplayEngine.BlockKey)o;
         return this.x == other.x && this.y == other.y && this.z == other.z;
      }
      public int hashCode() { int r = x; r = 31 * r + y; r = 31 * r + z; return r; }
   }

   private static final class BlockSnapshot {
      final Material type;
      final byte data;
      private BlockSnapshot(Material type, byte data) { this.type = type; this.data = data; }
      static AdvancedReplayEngine.BlockSnapshot capture(Block block) {
         Material material = block.getType();
         byte data = 0;
         try { data = block.getData(); } catch (Throwable ignored) {}
         return new AdvancedReplayEngine.BlockSnapshot(material, data);
      }
      void restore(Block block) {
         if (this.type != null) {
            block.setType(this.type);
            try { block.setData(this.data); } catch (Throwable ignored) {}
         }
      }
   }
}
