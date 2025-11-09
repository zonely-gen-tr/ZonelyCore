package dev.zonely.whiteeffect.nms.v1_8_R3.network;

import dev.zonely.whiteeffect.nms.v1_8_R3.entity.EntityNPCPlayer;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PlayerConnection;

public class EmptyNetHandler extends PlayerConnection {
   public EmptyNetHandler(EntityNPCPlayer entityplayer) {
      super(entityplayer.server, new EmptyNetworkManager(), entityplayer);
   }

   public void sendPacket(Packet packet) {
   }
}
