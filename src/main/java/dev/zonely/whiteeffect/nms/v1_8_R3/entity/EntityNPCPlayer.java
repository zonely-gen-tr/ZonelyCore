package dev.zonely.whiteeffect.nms.v1_8_R3.entity;

import com.mojang.authlib.GameProfile;
import dev.zonely.whiteeffect.libraries.npclib.NPCLibrary;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPCAnimation;
import dev.zonely.whiteeffect.libraries.npclib.npc.ai.NPCHolder;
import dev.zonely.whiteeffect.libraries.npclib.npc.skin.Skin;
import dev.zonely.whiteeffect.libraries.npclib.npc.skin.SkinPacketTracker;
import dev.zonely.whiteeffect.libraries.npclib.npc.skin.SkinnableEntity;
import dev.zonely.whiteeffect.nms.NMS;
import dev.zonely.whiteeffect.nms.v1_8_R3.network.EmptyNetHandler;
import dev.zonely.whiteeffect.nms.v1_8_R3.utils.PlayerNavigation;
import dev.zonely.whiteeffect.nms.v1_8_R3.utils.controllers.PlayerControllerJump;
import dev.zonely.whiteeffect.nms.v1_8_R3.utils.controllers.PlayerControllerLook;
import dev.zonely.whiteeffect.nms.v1_8_R3.utils.controllers.PlayerControllerMove;
import dev.zonely.whiteeffect.utils.Utils;
import java.util.Iterator;
import net.minecraft.server.v1_8_R3.AttributeInstance;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.DamageSource;
import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.GenericAttributes;
import net.minecraft.server.v1_8_R3.MathHelper;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutAnimation;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityEquipment;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityHeadRotation;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityTeleport;
import net.minecraft.server.v1_8_R3.PlayerInteractManager;
import net.minecraft.server.v1_8_R3.WorldServer;
import net.minecraft.server.v1_8_R3.WorldSettings.EnumGamemode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class EntityNPCPlayer extends EntityPlayer implements NPCHolder, SkinnableEntity {
   private final NPC npc;
   private final SkinPacketTracker skinTracker;
   private PlayerControllerJump controllerJump;
   private PlayerControllerLook controllerLook;
   private PlayerControllerMove controllerMove;
   private int jumpTicks = 0;
   private PlayerNavigation navigation;
   private Skin skin;
   private int ticks = 0;

   public EntityNPCPlayer(MinecraftServer server, WorldServer world, GameProfile profile, PlayerInteractManager manager, NPC npc) {
      super(server, world, profile, manager);
      this.npc = npc;
      if (npc != null) {
         manager.setGameMode(EnumGamemode.SURVIVAL);
         this.skinTracker = new SkinPacketTracker(this);
         this.initialise();
      } else {
         this.skinTracker = null;
      }

      this.setInvisible(false);
   }

   protected void a(double d0, boolean flag, Block block, BlockPosition blockposition) {
      if (this.npc == null || !(Boolean)this.npc.data().get("flyable", false)) {
         super.a(d0, flag, block, blockposition);
      }

   }

   public void collide(Entity entity) {
      super.collide(entity);
   }

   public boolean damageEntity(DamageSource damagesource, float f) {
      return super.damageEntity(damagesource, f);
   }

   public void die(DamageSource damagesource) {
      if (!this.dead) {
         super.die(damagesource);
         Bukkit.getScheduler().runTaskLater(NPCLibrary.getPlugin(), () -> {
            this.world.removeEntity(this);
         }, 35L);
      }
   }

   public void e(float f, float f1) {
      if (this.npc == null || !(Boolean)this.npc.data().get("flyable", false)) {
         super.e(f, f1);
      }

   }

   public void g(double d0, double d1, double d2) {
      if (this.npc == null || !this.npc.isProtected()) {
         super.g(d0, d1, d2);
      }

   }

   public void g(float f, float f1) {
      if (this.npc != null && (Boolean)this.npc.data().get("flyable", false)) {
         NMS.flyingMoveLogic(this.getBukkitEntity(), f, f1);
      } else {
         super.g(f, f1);
      }

   }

   public PlayerControllerJump getControllerJump() {
      return this.controllerJump;
   }

   public PlayerControllerMove getControllerMove() {
      return this.controllerMove;
   }

   public PlayerNavigation getNavigation() {
      return this.navigation;
   }

   public CraftPlayer getBukkitEntity() {
      if (this.npc != null && this.bukkitEntity == null) {
         this.bukkitEntity = new EntityNPCPlayer.PlayerNPC(this);
      }

      return super.getBukkitEntity();
   }

   public void initialise() {
      this.invulnerableTicks = 0;
      this.playerConnection = new EmptyNetHandler(this);
      this.playerConnection.networkManager.a(this.playerConnection);
      AttributeInstance range = this.getAttributeInstance(GenericAttributes.FOLLOW_RANGE);
      if (range == null) {
         range = this.getAttributeMap().b(GenericAttributes.FOLLOW_RANGE);
      }

      range.setValue(25.0D);
      this.getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(1.0D);
      this.controllerJump = new PlayerControllerJump(this);
      this.controllerLook = new PlayerControllerLook(this);
      this.controllerMove = new PlayerControllerMove(this);
      this.navigation = new PlayerNavigation(this, this.world);
      NMS.setStepHeight(this.getBukkitEntity(), 1.0F);
      this.setSkinFlags((byte)-1);
   }

   public boolean isNavigating() {
      return !this.getNavigation().m();
   }

   public boolean k_() {
      return this.npc != null && (Boolean)this.npc.data().get("flyable", false) ? false : super.k_();
   }

   public void livingEntityBaseTick() {
      if (!this.world.isClientSide) {
         this.b(0, this.fireTicks > 0);
      }

      this.ay = this.az;
      this.aE = this.aF;
      if (this.hurtTicks > 0) {
         --this.hurtTicks;
      }

      this.bi();
      this.aU = this.aT;
      this.aJ = this.aI;
      this.aL = this.aK;
      this.lastYaw = this.yaw;
      this.lastPitch = this.pitch;
   }

   private void moveOnCurrentHeading() {
      if (this.aY) {
         if (this.onGround && this.jumpTicks == 0) {
            this.bF();
            this.jumpTicks = 10;
         }
      } else {
         this.jumpTicks = 0;
      }

      this.aZ *= 0.98F;
      this.ba *= 0.98F;
      this.bb *= 0.9F;
      this.g(this.aZ, this.ba);
      NMS.setHeadYaw(this.getBukkitEntity(), this.yaw);
      if (this.jumpTicks > 0) {
         --this.jumpTicks;
      }

   }

   public void t_() {
      super.t_();
      if (this.npc != null) {
         this.noclip = this.isSpectator();
         this.livingEntityBaseTick();
         boolean navigating = this.isNavigating();
         this.updatePackets(navigating);
         if (!navigating && (Boolean)this.npc.data().get("gravity", false) && this.getBukkitEntity() != null && Utils.isLoaded(this.getBukkitEntity().getLocation())) {
            this.g(0.0F, 0.0F);
         }

         if (Math.abs(this.motX) < 0.00499999988824129D && Math.abs(this.motY) < 0.00499999988824129D && Math.abs(this.motZ) < 0.00499999988824129D) {
            this.motX = this.motY = this.motZ = 0.0D;
         }

         if (navigating) {
            if (!this.getNavigation().m()) {
               this.getNavigation().k();
            }

            this.moveOnCurrentHeading();
         }

         this.startNavigating();
         this.controllerMove.c();
         this.controllerLook.a();
         this.controllerJump.b();
         if (this.noDamageTicks > 0) {
            --this.noDamageTicks;
         }

         this.npc.update();
      }
   }

   private void startNavigating() {
      Location location = this.getNPC().getWalkingTo();
      if (location == null) {
         org.bukkit.entity.Entity following = this.getNPC().getFollowing();
         if (following != null) {
            location = following.getLocation();
            if (!location.getWorld().equals(this.getBukkitEntity().getWorld())) {
               this.getNPC().setFollowing((org.bukkit.entity.Entity)null);
               return;
            }

            double distance = location.distance(this.getBukkitEntity().getLocation());
            if (distance > this.getAttributeInstance(GenericAttributes.FOLLOW_RANGE).getValue()) {
               this.getNPC().setFollowing((org.bukkit.entity.Entity)null);
               return;
            }

            this.getNavigation().a(location.getX() + 1.0D, location.getY(), location.getZ() + 1.0D, this.isSprinting() ? 1.3D : 1.0D);
         }
      }

      if (location != null) {
         double distance = location.distance(this.getBukkitEntity().getLocation());
         if (distance > this.getAttributeInstance(GenericAttributes.FOLLOW_RANGE).getValue() || distance < 1.0D) {
            this.getNPC().finishNavigation();
            return;
         }

         this.getNPC().setWalkingTo((Location)null);
         this.getNavigation().a(location.getX(), location.getY(), location.getZ(), this.isSprinting() ? 1.3D : 1.0D);
      } else if (this.getNPC().isNavigating() && !this.isNavigating()) {
         this.getNPC().finishNavigation();
      }

   }

   public void playAnimation(NPCAnimation animation) {
      PacketPlayOutAnimation packet = new PacketPlayOutAnimation(this, animation.getId());
      Iterator var3 = this.getEntity().getNearbyEntities(64.0D, 64.0D, 64.0D).iterator();

      while(var3.hasNext()) {
         org.bukkit.entity.Entity player = (org.bukkit.entity.Entity)var3.next();
         if (player instanceof Player && !(player instanceof EntityNPCPlayer.PlayerNPC)) {
            ((CraftPlayer)player).getHandle().playerConnection.sendPacket(packet);
         }
      }

   }

   private void updatePackets(boolean navigating) {
      if (this.ticks++ > 30) {
         this.ticks = 0;
         Packet<?>[] packets = new Packet[navigating ? 5 : 6];
         if (!navigating) {
            packets[5] = new PacketPlayOutEntityHeadRotation(this, (byte)MathHelper.d(this.aK * 256.0F / 360.0F));
         }

         for(int i = 0; i < 5; ++i) {
            packets[i] = new PacketPlayOutEntityEquipment(this.getId(), i, this.getEquipment(i));
         }

         PacketPlayOutEntityTeleport teleport = new PacketPlayOutEntityTeleport(this);
         Iterator var4 = this.getEntity().getNearbyEntities(64.0D, 64.0D, 64.0D).iterator();

         while(true) {
            org.bukkit.entity.Entity player;
            do {
               do {
                  if (!var4.hasNext()) {
                     NMS.removeFromPlayerList(this.getBukkitEntity());
                     return;
                  }

                  player = (org.bukkit.entity.Entity)var4.next();
               } while(!(player instanceof Player));
            } while(player instanceof EntityNPCPlayer.PlayerNPC);

            if (!navigating) {
               ((CraftPlayer)player).getHandle().playerConnection.sendPacket(teleport);
            }

            Packet[] var6 = packets;
            int var7 = packets.length;

            for(int var8 = 0; var8 < var7; ++var8) {
               Packet<?> packet = var6[var8];
               ((CraftPlayer)player).getHandle().playerConnection.sendPacket(packet);
            }
         }
      }
   }

   public Player getEntity() {
      return this.getBukkitEntity();
   }

   public NPC getNPC() {
      return this.npc;
   }

   public SkinPacketTracker getSkinTracker() {
      return this.skinTracker;
   }

   public Skin getSkin() {
      return this.skin;
   }

   public void setSkin(Skin skin) {
      if (skin != null) {
         skin.apply(this);
      }

      this.skin = skin;
   }

   public void setSkinFlags(byte flags) {
      try {
         this.getDataWatcher().watch(10, flags);
      } catch (NullPointerException var3) {
         this.getDataWatcher().a(10, flags);
      }

   }

   static class PlayerNPC extends CraftPlayer implements NPCHolder, SkinnableEntity {
      private final NPC npc;

      public PlayerNPC(EntityNPCPlayer entity) {
         super(entity.world.getServer(), entity);
         this.npc = entity.npc;
      }

      public Player getEntity() {
         return this;
      }

      public SkinPacketTracker getSkinTracker() {
         return ((SkinnableEntity)this.entity).getSkinTracker();
      }

      public NPC getNPC() {
         return this.npc;
      }

      public Skin getSkin() {
         return ((SkinnableEntity)this.entity).getSkin();
      }

      public void setSkin(Skin skin) {
         ((SkinnableEntity)this.entity).setSkin(skin);
      }

      public void setSkinFlags(byte flags) {
         ((SkinnableEntity)this.entity).setSkinFlags(flags);
      }
   }
}

