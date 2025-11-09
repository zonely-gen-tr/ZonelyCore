package dev.zonely.whiteeffect.nms.v1_8_R3.entity;

import dev.zonely.whiteeffect.libraries.holograms.HologramLibrary;
import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import dev.zonely.whiteeffect.nms.interfaces.entity.ISlime;
import dev.zonely.whiteeffect.nms.v1_8_R3.utils.NullBoundingBox;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.logging.Level;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.DamageSource;
import net.minecraft.server.v1_8_R3.EntityDamageSource;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.World;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Slime;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

public class EntitySlime extends net.minecraft.server.v1_8_R3.EntitySlime implements ISlime {
   private final HologramLine line;

   public EntitySlime(World world, HologramLine line) {
      super(world);
      super.persistent = true;
      this.a(new float[]{0.0F, 0.0F});
      this.setSize(2);
      this.setInvisible(true);
      this.line = line;
      super.a(new NullBoundingBox());
   }

   public void a(AxisAlignedBB boundingBox) {
   }

   public void b(NBTTagCompound nbttagcompound) {
   }

   public boolean c(NBTTagCompound nbttagcompound) {
      return false;
   }

   public boolean d(NBTTagCompound nbttagcompound) {
      return false;
   }

   public void e(NBTTagCompound nbttagcompound) {
   }

   public void setCustomName(String customName) {
   }

   public void setCustomNameVisible(boolean visible) {
   }

   public void t_() {
      if (this.ticksLived % 20 == 0 && this.vehicle == null) {
         this.die();
      }

      if (this.dead) {
         super.t_();
      }

   }

   public void makeSound(String sound, float f1, float f2) {
   }

   public boolean damageEntity(DamageSource damageSource, float amount) {
      if (damageSource instanceof EntityDamageSource) {
         EntityDamageSource entityDamageSource = (EntityDamageSource)damageSource;
         if (entityDamageSource.getEntity() instanceof EntityPlayer) {
            Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(((EntityPlayer)entityDamageSource.getEntity()).getBukkitEntity(), this.getBukkitEntity()));
         }
      }

      return false;
   }

   public boolean isInvulnerable(DamageSource source) {
      return true;
   }

   public CraftEntity getBukkitEntity() {
      if (this.bukkitEntity == null) {
         this.bukkitEntity = new EntitySlime.CraftSlime(this.world.getServer(), this);
      }

      return this.bukkitEntity;
   }

   public void setPassengerOf(Entity entity) {
      if (entity != null) {
         net.minecraft.server.v1_8_R3.Entity nms = ((CraftEntity)entity).getHandle();

         try {
            Field pitchDelta = net.minecraft.server.v1_8_R3.Entity.class.getDeclaredField("ar");
            pitchDelta.setAccessible(true);
            pitchDelta.set(this, 0.0D);
            Field yawDelta = net.minecraft.server.v1_8_R3.Entity.class.getDeclaredField("as");
            yawDelta.setAccessible(true);
            yawDelta.set(this, 0.0D);
         } catch (ReflectiveOperationException var5) {
            HologramLibrary.LOGGER.log(Level.SEVERE, "Couldnt set rider pitch and yaw: ", var5);
         }

         if (this.vehicle != null) {
            this.vehicle.passenger = null;
         }

         this.vehicle = nms;
         nms.passenger = this;
      }
   }

   public void setLocation(double x, double y, double z) {
      super.setPosition(x, y, z);
   }

   public boolean isDead() {
      return this.dead;
   }

   public void killEntity() {
      this.die();
   }

   public Slime getEntity() {
      return (Slime)this.getBukkitEntity();
   }

   public HologramLine getLine() {
      return this.line;
   }

   public static class CraftSlime extends org.bukkit.craftbukkit.v1_8_R3.entity.CraftSlime implements ISlime {
      public CraftSlime(CraftServer server, EntitySlime entity) {
         super(server, entity);
      }

      public void remove() {
      }

      public boolean addPotionEffect(PotionEffect effect) {
         return false;
      }

      public boolean addPotionEffect(PotionEffect effect, boolean param) {
         return false;
      }

      public boolean addPotionEffects(Collection<PotionEffect> effects) {
         return false;
      }

      public void setRemoveWhenFarAway(boolean remove) {
      }

      public void setVelocity(Vector vel) {
      }

      public boolean teleport(Location loc) {
         return false;
      }

      public boolean teleport(Entity entity) {
         return false;
      }

      public boolean teleport(Location loc, TeleportCause cause) {
         return false;
      }

      public boolean teleport(Entity entity, TeleportCause cause) {
         return false;
      }

      public void setFireTicks(int ticks) {
      }

      public boolean setPassenger(Entity entity) {
         return false;
      }

      public boolean eject() {
         return false;
      }

      public boolean leaveVehicle() {
         return false;
      }

      public void playEffect(EntityEffect effect) {
      }

      public void setCustomName(String name) {
      }

      public void setCustomNameVisible(boolean flag) {
      }

      public void setSize(int size) {
      }

      public void setPassengerOf(Entity entity) {
         ((EntitySlime)this.entity).setPassengerOf(entity);
      }

      public void setLocation(double x, double y, double z) {
         ((EntitySlime)this.entity).setLocation(x, y, z);
      }

      public void killEntity() {
         ((EntitySlime)this.entity).killEntity();
      }

      public Slime getEntity() {
         return this;
      }

      public HologramLine getLine() {
         return ((EntitySlime)this.entity).getLine();
      }
   }
}
