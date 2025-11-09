package dev.zonely.whiteeffect.nms.interfaces.entity;

import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import org.bukkit.entity.ArmorStand;

public interface IArmorStand {
   int getId();

   void setName(String var1);

   void setLocation(double var1, double var3, double var5);

   boolean isDead();

   void killEntity();

   ArmorStand getEntity();

   HologramLine getLine();
}
