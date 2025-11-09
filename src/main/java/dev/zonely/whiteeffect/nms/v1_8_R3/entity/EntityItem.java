package dev.zonely.whiteeffect.nms.v1_8_R3.entity;

import dev.zonely.whiteeffect.libraries.holograms.HologramLibrary;
import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import dev.zonely.whiteeffect.nms.interfaces.entity.IItem;
import java.lang.reflect.Field;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.DamageSource;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NBTTagList;
import net.minecraft.server.v1_8_R3.NBTTagString;
import net.minecraft.server.v1_8_R3.World;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class EntityItem extends net.minecraft.server.v1_8_R3.EntityItem implements IItem {
   private final HologramLine line;

   public EntityItem(World world, HologramLine line) {
      super(world);
      super.pickupDelay = 0;
      this.line = line;
   }

   public void t_() {
      this.ticksLived = 0;
   }

   public void inactiveTick() {
      this.ticksLived = 0;
   }

   public void d(EntityHuman entityHuman) {
      if (!(entityHuman.locY < this.locY - 1.5D) && !(entityHuman.locY > this.locY + 1.0D)) {
         if (entityHuman instanceof EntityPlayer && this.line.getPickupHandler() != null) {
            this.line.getPickupHandler().onPickup((Player)entityHuman.getBukkitEntity());
         }

      }
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

   public boolean isInvulnerable(DamageSource source) {
      return true;
   }

   public void die() {
   }

   public boolean isAlive() {
      return false;
   }

   public CraftEntity getBukkitEntity() {
      if (super.bukkitEntity == null) {
         this.bukkitEntity = new EntityItem.CraftItem(this);
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

   public void setItemStack(ItemStack item) {
      net.minecraft.server.v1_8_R3.ItemStack newItem = CraftItemStack.asNMSCopy(item);
      if (newItem == null) {
         newItem = new net.minecraft.server.v1_8_R3.ItemStack(Blocks.BEDROCK);
      }

      if (newItem.getTag() == null) {
         newItem.setTag(new NBTTagCompound());
      }

      NBTTagCompound display = newItem.getTag().getCompound("display");
      if (!newItem.getTag().hasKey("display")) {
         newItem.getTag().set("display", display);
      }

      NBTTagList tagList = new NBTTagList();
      tagList.add(new NBTTagString("§0" + ThreadLocalRandom.current().nextInt()));
      display.set("Lore", tagList);
      this.setItemStack(newItem);
   }

   public void setLocation(double x, double y, double z) {
      super.setPosition(x, y, z);
   }

   public boolean isDead() {
      return this.dead;
   }

   public void killEntity() {
      super.dead = true;
   }

   public Item getEntity() {
      return (Item)this.getBukkitEntity();
   }

   public HologramLine getLine() {
      return this.line;
   }

   public static class CraftItem extends org.bukkit.craftbukkit.v1_8_R3.entity.CraftItem implements IItem {
      public CraftItem(EntityItem entity) {
         super(entity.world.getServer(), entity);
      }

      public void remove() {
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

      public void setPickupDelay(int delay) {
      }

      public void setPassengerOf(Entity entity) {
         ((EntityItem)this.entity).setPassengerOf(entity);
      }

      public void setLocation(double x, double y, double z) {
         ((EntityItem)this.entity).setLocation(x, y, z);
      }

      public void killEntity() {
         ((EntityItem)this.entity).killEntity();
      }

      public void setItemStack(ItemStack stack) {
      }

      public Item getEntity() {
         return this;
      }

      public HologramLine getLine() {
         return ((EntityItem)this.entity).getLine();
      }
   }
}
