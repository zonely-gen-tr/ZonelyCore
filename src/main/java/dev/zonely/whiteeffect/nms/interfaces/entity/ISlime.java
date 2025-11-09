package dev.zonely.whiteeffect.nms.interfaces.entity;

import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Slime;

public interface ISlime {
   void setPassengerOf(Entity var1);

   void setLocation(double var1, double var3, double var5);

   boolean isDead();

   void killEntity();

   Slime getEntity();

   HologramLine getLine();
}
