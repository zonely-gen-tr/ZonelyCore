package dev.zonely.whiteeffect.nms.interfaces.entity;

import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

public interface IItem {
   void setPassengerOf(Entity var1);

   void setItemStack(ItemStack var1);

   void setLocation(double var1, double var3, double var5);

   boolean isDead();

   void killEntity();

   Item getEntity();

   HologramLine getLine();
}
