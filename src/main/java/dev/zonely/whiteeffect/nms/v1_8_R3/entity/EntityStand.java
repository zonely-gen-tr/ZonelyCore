package dev.zonely.whiteeffect.nms.v1_8_R3.entity;

import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import dev.zonely.whiteeffect.nms.interfaces.entity.IArmorStand;
import dev.zonely.whiteeffect.nms.v1_8_R3.utils.NullBoundingBox;
import java.lang.reflect.Field;
import java.util.Iterator;
import net.minecraft.server.v1_8_R3.DamageSource;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.MathHelper;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityTeleport;
import net.minecraft.server.v1_8_R3.Vector3f;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftArmorStand;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.EulerAngle;

public class EntityStand extends net.minecraft.server.v1_8_R3.EntityArmorStand implements IArmorStand {
   public EntityStand(Location toSpawn) {
      super(((CraftWorld)toSpawn.getWorld()).getHandle());
      this.setArms(false);
      this.setBasePlate(true);
      this.setInvisible(true);
      this.setGravity(false);
      this.setSmall(true);

      try {
         Field field = net.minecraft.server.v1_8_R3.EntityArmorStand.class.getDeclaredField("bi");
         field.setAccessible(true);
         field.set(this, Integer.MAX_VALUE);
      } catch (Exception var3) {
         var3.printStackTrace();
      }

      this.a(new NullBoundingBox());
   }

   private static double square(double num) {
      return num * num;
   }

   public boolean isInvulnerable(DamageSource source) {
      return true;
   }

   public void setCustomName(String customName) {
   }

   public void setCustomNameVisible(boolean visible) {
   }

   public void t_() {
      this.ticksLived = 0;
      super.t_();
   }

   public void makeSound(String sound, float f1, float f2) {
   }

   public int getId() {
      return super.getId();
   }

   public void setName(String text) {
      if (text != null && text.length() > 300) {
         text = text.substring(0, 300);
      }

      super.setCustomName(text == null ? "" : text);
      super.setCustomNameVisible(text != null && !text.isEmpty());
   }

   public void killEntity() {
      super.die();
   }

   public HologramLine getLine() {
      return null;
   }

   public ArmorStand getEntity() {
      return (ArmorStand)this.getBukkitEntity();
   }

   public CraftEntity getBukkitEntity() {
      if (this.bukkitEntity == null) {
         this.bukkitEntity = new EntityStand.CraftStand(this);
      }

      return super.getBukkitEntity();
   }

   public void setLocation(double x, double y, double z) {
      super.setPosition(x, y, z);
      PacketPlayOutEntityTeleport teleportPacket = new PacketPlayOutEntityTeleport(this.getId(), MathHelper.floor(this.locX * 32.0D), MathHelper.floor(this.locY * 32.0D), MathHelper.floor(this.locZ * 32.0D), (byte)((int)(this.yaw * 256.0F / 360.0F)), (byte)((int)(this.pitch * 256.0F / 360.0F)), this.onGround);
      Iterator var8 = this.world.players.iterator();

      while(var8.hasNext()) {
         EntityHuman obj = (EntityHuman)var8.next();
         if (obj instanceof EntityPlayer) {
            EntityPlayer nmsPlayer = (EntityPlayer)obj;
            double distanceSquared = square(nmsPlayer.locX - this.locX) + square(nmsPlayer.locZ - this.locZ);
            if (distanceSquared < 8192.0D && nmsPlayer.playerConnection != null) {
               nmsPlayer.playerConnection.sendPacket(teleportPacket);
            }
         }
      }

   }

   public boolean isDead() {
      return this.dead;
   }

   static class CraftStand extends CraftArmorStand implements IArmorStand {
      public CraftStand(EntityStand entity) {
         super(entity.world.getServer(), entity);
      }

      public int getId() {
         return this.entity.getId();
      }

      public void setBodyPose(EulerAngle pose) {
         ((EntityStand)this.entity).setHeadPose(new Vector3f((float)pose.getX(), (float)pose.getY(), (float)pose.getZ()));
      }

      public void setHeadPose(EulerAngle pose) {
         ((EntityStand)this.entity).setHeadPose(new Vector3f((float)pose.getX(), (float)pose.getY(), (float)pose.getZ()));
      }

      public void setLeftArmPose(EulerAngle pose) {
         ((EntityStand)this.entity).setLeftArmPose(new Vector3f((float)pose.getX(), (float)pose.getY(), (float)pose.getZ()));
      }

      public void setLeftLegPose(EulerAngle pose) {
         ((EntityStand)this.entity).setLeftLegPose(new Vector3f((float)pose.getX(), (float)pose.getY(), (float)pose.getZ()));
      }

      public void setRightLegPose(EulerAngle pose) {
         ((EntityStand)this.entity).setRightLegPose(new Vector3f((float)pose.getX(), (float)pose.getY(), (float)pose.getZ()));
      }

      public void setName(String text) {
         ((EntityStand)this.entity).setName(text);
      }

      public void killEntity() {
         ((EntityStand)this.entity).killEntity();
      }

      public HologramLine getLine() {
         return ((EntityStand)this.entity).getLine();
      }

      public ArmorStand getEntity() {
         return this;
      }

      public void setLocation(double x, double y, double z) {
         ((EntityStand)this.entity).setLocation(x, y, z);
      }
   }
}
