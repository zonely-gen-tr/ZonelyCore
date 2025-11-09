package dev.zonely.whiteeffect.hook.protocollib;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import dev.zonely.whiteeffect.libraries.npclib.NPCLibrary;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class NPCAdapter extends PacketAdapter {

   public NPCAdapter() {
      super(params().plugin(NPCLibrary.getPlugin()).types(PacketType.Play.Server.PLAYER_INFO));
   }

   @Override
   public void onPacketSending(PacketEvent evt) {
      PacketContainer packet = evt.getPacket();

      Player player = evt.getPlayer();
      if (packet.getType() == PacketType.Play.Server.PLAYER_INFO) {
         List<PlayerInfoData> original;
         try {
            original = packet.getPlayerInfoDataLists().read(0);
         } catch (Throwable t) {
            return; 
         }

         if (original == null || original.isEmpty()) return;

         List<PlayerInfoData> toSend = new ArrayList<>(original.size());
         boolean modified = false;
         for (PlayerInfoData data : original) {
            if (data == null || data.getProfile() == null) {
               toSend.add(data);
               continue;
            }

            NPC npc = NPCLibrary.findNPC(data.getProfile().getUUID());
            if (npc != null && npc.data().get(NPC.COPY_PLAYER_SKIN, false)) {
               try {
                  data.getProfile().getProperties().clear();
                  WrappedGameProfile profile = WrappedGameProfile.fromPlayer(player);
                  profile.getProperties().get("textures").stream().findFirst()
                        .ifPresent(prop -> data.getProfile().getProperties().put("textures", prop));
                  modified = true;
               } catch (Throwable ignored) { }
            }

            toSend.add(data);
         }

         if (modified) {
            try {
               packet.getPlayerInfoDataLists().write(0, toSend);
            } catch (Throwable ignored) { }
         }
      }
   }

   @Override
   public void onPacketReceiving(PacketEvent evt) {
   }
}
