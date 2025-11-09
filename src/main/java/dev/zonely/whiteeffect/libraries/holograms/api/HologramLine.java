package dev.zonely.whiteeffect.libraries.holograms.api;

import dev.zonely.whiteeffect.nms.NMS;
import dev.zonely.whiteeffect.nms.interfaces.entity.IArmorStand;
import dev.zonely.whiteeffect.nms.interfaces.entity.IItem;
import dev.zonely.whiteeffect.nms.interfaces.entity.ISlime;
import dev.zonely.whiteeffect.utils.StringUtils;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public class HologramLine {
   private Location location;
   private IArmorStand armor;
   private ISlime slime;
   private IItem item;
   private TouchHandler touch;
   private PickupHandler pickup;
   private String line;
   private final Hologram hologram;

   public HologramLine(Hologram hologram, Location location, String line) {
      this.line = StringUtils.formatColors(line);
      this.location = location;
      this.armor = null;
      this.hologram = hologram;
   }

   public void spawn() {
      if (this.armor == null) {
         this.armor = NMS.createArmorStand(this.location, this.line, this);
         this.reattachPassengers();
         if (this.touch != null) {
            this.setTouchable(this.touch);
         }
         if (this.item != null) {
            this.item.setPassengerOf(this.armor.getEntity());
         }
      }
   }

   public void despawn() {
      if (this.armor != null) {
         this.armor.killEntity();
         this.armor = null;
      }
      if (this.slime != null) {
         this.slime.killEntity();
         this.slime = null;
      }
      if (this.item != null) {
         this.item.killEntity();
         this.item = null;
      }
   }

   public void setTouchable(TouchHandler touch) {
      this.touch = touch;
      if (touch == null) {
         if (this.slime != null) {
            this.slime.killEntity();
            this.slime = null;
         }
         return;
      }
      if (this.armor == null) {
         return;
      }
      if (this.slime == null) {
         this.slime = NMS.createSlime(this.location, this);
      }
      if (this.slime != null) {
         this.slime.setPassengerOf(this.armor.getEntity());
      }
   }

   public void setItem(ItemStack item, PickupHandler pickup) {
      if (pickup == null) {
         if (this.item != null) {
            this.item.killEntity();
         }
         this.item = null;
         this.pickup = null;
      } else {
         this.pickup = pickup;
         if (this.armor != null) {
            this.item = (this.item == null) ? NMS.createItem(this.location, item, this) : this.item;
            if (this.item != null) {
               this.item.setPassengerOf(this.armor.getEntity());
            }
         } else {
            this.item = NMS.createItem(this.location, item, this);
         }
      }
   }

   public Location getLocation() {
      return this.location;
   }

   public void setLocation(Location location) {
      this.location = location;
      if (this.armor != null) {
         this.armor.setLocation(location.getX(), location.getY(), location.getZ());
         this.reattachPassengers();
      }
   }

   private void reattachPassengers() {
      if (this.armor == null) return;
      if (this.slime != null) {
         this.slime.setPassengerOf(this.armor.getEntity());
      }
      if (this.item != null) {
         this.item.setPassengerOf(this.armor.getEntity());
      }
   }

   public IArmorStand getArmor() {
      return this.armor;
   }

   public ISlime getSlime() {
      return this.slime;
   }

   public TouchHandler getTouchHandler() {
      return this.touch;
   }

   public PickupHandler getPickupHandler() {
      return this.pickup;
   }

   public String getLine() {
      return this.line;
   }

   public void setLine(String line) {
      String colored = StringUtils.formatColors(line);
      if (colored == null) colored = "";
      if (colored.equals(this.line)) {
         return;
      }
      this.line = colored;
      if (this.armor == null) {
         if (this.hologram.isSpawned()) {
            this.spawn();
         }
      } else {
         this.armor.setName(this.line);
      }
   }

   public Hologram getHologram() {
      return this.hologram;
   }
}