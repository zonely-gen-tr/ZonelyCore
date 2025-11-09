package dev.zonely.whiteeffect.libraries.holograms.api;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class Hologram {
   private String attached;
   private boolean spawned;
   private final Location location;
   private final Map<Integer, HologramLine> lines = new HashMap();

   public Hologram(Location location, String... lines) {
      this.location = location;
      int current = 0;
      String[] var4 = lines;
      int var5 = lines.length;

      for(int var6 = 0; var6 < var5; ++var6) {
         String line = var4[var6];
         ++current;
         this.lines.put(current, new HologramLine(this, location.clone().add(0.0D, 0.33D * (double)current, 0.0D), line));
      }

   }

   public void setAttached(String player) {
      this.attached = player;
   }

   public Hologram spawn() {
      if (this.spawned) {
         return this;
      } else {
         this.lines.values().forEach(HologramLine::spawn);
         this.spawned = true;
         return this;
      }
   }

   public Hologram despawn() {
      if (!this.spawned) {
         return this;
      } else {
         this.lines.values().forEach(HologramLine::despawn);
         this.spawned = false;
         return this;
      }
   }

   public Hologram withLine(String line) {
      int l;
      for(l = 1; this.lines.containsKey(l); ++l) {
      }

      this.lines.put(l, new HologramLine(this, this.location.clone().add(0.0D, 0.33D * (double)l, 0.0D), line));
      if (this.spawned) {
         ((HologramLine)this.lines.get(l)).spawn();
      }

      return this;
   }

   public Hologram updateLine(int id, String line) {
      if (!this.lines.containsKey(id)) {
         return this;
      } else {
         HologramLine hl = (HologramLine)this.lines.get(id);
         hl.setLine(line);
         return this;
      }
   }

   public boolean canSee(Player player) {
      return this.attached == null || this.attached.equals(player.getName());
   }

   public boolean isSpawned() {
      return this.spawned;
   }

   public Location getLocation() {
      return this.location;
   }

   public HologramLine getLine(int id) {
      return (HologramLine)this.lines.get(id);
   }

   public Collection<HologramLine> getLines() {
      return this.lines.values();
   }
}
