package dev.zonely.whiteeffect.libraries.holograms;

import com.google.common.collect.ImmutableList;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.libraries.holograms.api.Hologram;
import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import dev.zonely.whiteeffect.nms.NMS;
import dev.zonely.whiteeffect.plugin.WPlugin;
import dev.zonely.whiteeffect.plugin.logger.WLogger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public class HologramLibrary {
   public static final WLogger LOGGER = ((WLogger)Core.getInstance().getLogger()).getModule("HOLOGRAMS");
   private static final List<Hologram> holograms = new ArrayList();
   private static Plugin plugin;

   public static Hologram createHologram(Location location, List<String> lines) {
      return createHologram(location, (String[])lines.toArray(new String[0]));
   }

   public static Hologram createHologram(Location location, String... lines) {
      return createHologram(location, true, lines);
   }

   public static Hologram createHologram(Location location, boolean spawn, String... lines) {
      Hologram hologram = new Hologram(location, lines);
      if (spawn) {
         hologram.spawn();
      }

      holograms.add(hologram);
      return hologram;
   }

   public static void removeHologram(Hologram hologram) {
      holograms.remove(hologram);
      hologram.despawn();
   }

   public static int purgeOrphanedHolograms() {
      int removed = 0;
      boolean loggedError = false;
      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : world.getEntities()) {
            try {
               if (NMS.isHologramEntity(entity)) {
                  entity.remove();
                  ++removed;
               }
            } catch (Throwable ex) {
               if (!loggedError) {
                  LOGGER.log(Level.WARNING, "Failed to inspect entity while purging orphaned holograms. Continuing cleanup.", ex);
                  loggedError = true;
               }
            }
         }
      }
      if (removed > 0) {
         LOGGER.info("Purged " + removed + " orphaned hologram entities from previous session.");
      }
      return removed;
   }

   public static void unregisterAll() {
      holograms.forEach(Hologram::despawn);
      holograms.clear();
      plugin = null;
   }

   public static Entity getHologramEntity(int entityId) {
      Iterator var1 = listHolograms().iterator();

      while(true) {
         Hologram hologram;
         do {
            if (!var1.hasNext()) {
               return null;
            }

            hologram = (Hologram)var1.next();
         } while(!hologram.isSpawned());

         Iterator var3 = hologram.getLines().iterator();

         while(var3.hasNext()) {
            HologramLine line = (HologramLine)var3.next();
            if (line.getArmor() != null && line.getArmor().getId() == entityId) {
               return line.getArmor().getEntity();
            }
         }
      }
   }

   public static Hologram getHologram(Entity entity) {
      return NMS.getHologram(entity);
   }

   public static boolean isHologramEntity(Entity entity) {
      return NMS.isHologramEntity(entity);
   }

   public static Collection<Hologram> listHolograms() {
      return ImmutableList.copyOf(holograms);
   }

   public static void setupHolograms(WPlugin pl) {
      if (plugin == null) {
         plugin = pl;
         Bukkit.getPluginManager().registerEvents(new HologramListeners(), plugin);
      }
   }

   public static Plugin getPlugin() {
      return plugin;
   }
}
