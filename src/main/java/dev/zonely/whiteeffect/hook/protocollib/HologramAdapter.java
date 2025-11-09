package dev.zonely.whiteeffect.hook.protocollib;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import dev.zonely.whiteeffect.libraries.holograms.HologramLibrary;
import dev.zonely.whiteeffect.libraries.holograms.api.Hologram;
import dev.zonely.whiteeffect.libraries.npclib.NPCLibrary;
import dev.zonely.whiteeffect.nms.NMS;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class HologramAdapter extends PacketAdapter {
   public HologramAdapter() {
      super(params().plugin(NPCLibrary.getPlugin()).types(new PacketType[]{Server.SPAWN_ENTITY, Server.ENTITY_METADATA}));
   }

   public void onPacketSending(PacketEvent evt) {
      PacketContainer packet = evt.getPacket();
      Player player = evt.getPlayer();
      Entity entity = HologramLibrary.getHologramEntity((Integer)packet.getIntegers().read(0));
      Hologram hologram;
      if (entity == null || !HologramLibrary.isHologramEntity(entity) || (hologram = HologramLibrary.getHologram(entity)) == null) {
         hologram = NMS.getPreHologram((Integer)packet.getIntegers().read(0));
      }

      if (hologram != null && !hologram.canSee(player)) {
         evt.setCancelled(true);
      }
   }

   public void onPacketReceiving(PacketEvent evt) {
   }
}
