package dev.zonely.whiteeffect.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import dev.zonely.whiteeffect.nms.NMS;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.fake.FakeManager;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PluginMessageListener implements org.bukkit.plugin.messaging.PluginMessageListener {

   @Override
   public void onPluginMessageReceived(String channel, Player receiver, byte[] data) {
   if (channel.equals("zonelycore:main")) {
         ByteArrayDataInput in = ByteStreams.newDataInput(data);

         String subChannel = in.readUTF();
         switch (subChannel) {
            case "FAKE": {
               Player player = Bukkit.getPlayerExact(in.readUTF());
               if (player != null) {
                  String fakeName = in.readUTF();
                  String roleName = in.readUTF();
                  String skin = in.readUTF();
                  FakeManager.applyFake(player, fakeName, roleName, skin);
                  NMS.refreshPlayer(player);
               }
               break;
            }
            case "FAKE_BOOK": {
               Player player = Bukkit.getPlayerExact(in.readUTF());
               if (player != null) {
                  try {
                     String sound = in.readUTF();
                     EnumSound.valueOf(sound).play(player, 1.0F, sound.contains("VILL") ? 1.0F : 2.0F);
                  } catch (Exception ignore) {
                  }
                  FakeManager.sendRole(player);
               }
               break;
            }
            case "SEND_PARTY": {
               in.readUTF(); 
               in.readUTF(); 
               break;
            }
            case "FAKE_BOOK2": {
               Player player = Bukkit.getPlayerExact(in.readUTF());
               if (player != null) {
                  String roleName = in.readUTF();
                  String sound = in.readUTF();
                  EnumSound.valueOf(sound).play(player, 1.0F, sound.contains("VILL") ? 1.0F : 2.0F);
                  FakeManager.sendSkin(player, roleName);
               }
               break;
            }
         }
      }
   }
}
