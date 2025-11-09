package dev.zonely.whiteeffect.nms.v1_8_R3.entity;

import com.mojang.authlib.GameProfile;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.libraries.npclib.NPCLibrary;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import dev.zonely.whiteeffect.libraries.npclib.npc.AbstractEntityController;
import dev.zonely.whiteeffect.libraries.npclib.npc.skin.SkinnableEntity;
import dev.zonely.whiteeffect.nms.NMS;
import java.util.UUID;
import net.minecraft.server.v1_8_R3.PlayerInteractManager;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

public class HumanController extends AbstractEntityController {
   protected Entity createEntity(Location location, NPC npc) {
      WorldServer nmsWorld = ((CraftWorld)location.getWorld()).getHandle();
      UUID uuid = npc.getUUID();
      GameProfile profile = new GameProfile(uuid, npc.getName().substring(0, Math.min(npc.getName().length(), 16)));
      EntityNPCPlayer handle = new EntityNPCPlayer(nmsWorld.getMinecraftServer(), nmsWorld, profile, new PlayerInteractManager(nmsWorld), npc);
      handle.setPositionRotation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
      Bukkit.getScheduler().scheduleSyncDelayedTask(NPCLibrary.getPlugin(), () -> {
         if (this.getBukkitEntity() != null && this.getBukkitEntity().isValid()) {
            NMS.removeFromPlayerList(handle.getBukkitEntity());
         }

      }, 20L);
      handle.getBukkitEntity().setMetadata("NPC", new FixedMetadataValue(Core.getInstance(), true));
      handle.getBukkitEntity().setSleepingIgnored(true);
      return handle.getBukkitEntity();
   }

   public Player getBukkitEntity() {
      return (Player)super.getBukkitEntity();
   }

   public void remove() {
      Player entity = this.getBukkitEntity();
      if (entity != null) {
         NMS.removeFromWorld(entity);
         SkinnableEntity skinnable = NMS.getSkinnable(entity);
         skinnable.getSkinTracker().onRemoveNPC();
      }

      super.remove();
   }
}
