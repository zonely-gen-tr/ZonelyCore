package dev.zonely.whiteeffect.nms.v1_8_R3.utils;

import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import dev.zonely.whiteeffect.libraries.npclib.npc.skin.SkinnableEntity;
import dev.zonely.whiteeffect.nms.v1_8_R3.entity.EntityNPCPlayer;
import java.lang.reflect.Field;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.EntityTrackerEntry;
import net.minecraft.server.v1_8_R3.PacketPlayOutBed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class PlayerlistTrackerEntry extends EntityTrackerEntry {
   private static Field U;

   public PlayerlistTrackerEntry(Entity entity, int i, int j, boolean flag) {
      super(entity, i, j, flag);
   }

   public PlayerlistTrackerEntry(EntityTrackerEntry entry) {
      this(entry.tracker, entry.b, entry.c, getU(entry));
   }

   static boolean getU(EntityTrackerEntry entry) {
      try {
         return (Boolean)U.get(entry);
      } catch (ReflectiveOperationException var2) {
         var2.printStackTrace();
         return false;
      }
   }

   public void updatePlayer(EntityPlayer entityplayer) {
      if (!(entityplayer instanceof EntityNPCPlayer)) {
         boolean layingSend = false;
         NPC npc;
         if (entityplayer != this.tracker && this.c(entityplayer) && !this.trackedPlayers.contains(entityplayer) && (entityplayer.u().getPlayerChunkMap().a(entityplayer, this.tracker.ae, this.tracker.ag) || this.tracker.attachedToPlayer) && this.tracker instanceof SkinnableEntity) {
            SkinnableEntity entity = (SkinnableEntity)this.tracker;
            npc = entity.getNPC();
            if (npc.data().has("only-for") && !npc.data().get("only-for").equals(entityplayer.getName())) {
               entityplayer.getBukkitEntity().hidePlayer(entity.getEntity());
               return;
            }

            Player player = entity.getEntity();
            if (entityplayer.getBukkitEntity().canSee(player)) {
               entity.getSkinTracker().updateViewer(entityplayer.getBukkitEntity());
               layingSend = true;
            }
         }

         super.updatePlayer(entityplayer);
         if (layingSend) {
            Player player = entityplayer.getBukkitEntity();
            npc = ((SkinnableEntity)this.tracker).getNPC();
            if (entityplayer.getBukkitEntity().canSee(player) && npc.isLaying()) {
               Location bedLocation = this.tracker.getBukkitEntity().getLocation();
               bedLocation.setY(0.0D);
               player.sendBlockChange(bedLocation, Material.BED_BLOCK, (byte)0);
               entityplayer.playerConnection.sendPacket(new PacketPlayOutBed((EntityHuman)this.tracker, new BlockPosition(bedLocation.getBlockX(), bedLocation.getBlockY(), bedLocation.getBlockZ())));
            }
         }

      }
   }

   static {
      try {
         U = EntityTrackerEntry.class.getDeclaredField("u");
         U.setAccessible(true);
      } catch (ReflectiveOperationException var1) {
         var1.printStackTrace();
      }

   }
}
