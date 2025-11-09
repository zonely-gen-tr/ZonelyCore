package dev.zonely.whiteeffect.hook.protocollib;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.PacketType.Play.Client;
import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.player.fake.FakeManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;

public class FakeAdapter extends PacketAdapter {
   public FakeAdapter() {
      super(params().plugin(Core.getInstance()).types(new PacketType[]{Client.CHAT, Server.PLAYER_INFO, Server.CHAT, Server.SCOREBOARD_OBJECTIVE, Server.SCOREBOARD_SCORE, Server.SCOREBOARD_TEAM}));
   }

   public void onPacketReceiving(PacketEvent evt) {
      PacketContainer packet = evt.getPacket();
      if (packet.getType() == Client.CHAT) {
         String command = (String)packet.getStrings().read(0);
         if (command.startsWith("/")) {
            packet.getStrings().write(0, FakeManager.replaceNickedPlayers((String)packet.getStrings().read(0), false));
         } else {
            packet.getStrings().write(0, FakeManager.replaceNickedChanges((String)packet.getStrings().read(0)));
         }
      }

   }

   public void onPacketSending(PacketEvent evt) {
      PacketContainer packet = evt.getPacket();
      if (packet.getType() == Server.PLAYER_INFO) {
         List<PlayerInfoData> list;
         try {
            list = (List<PlayerInfoData>) packet.getPlayerInfoDataLists().read(0);
         } catch (Throwable t) {
            return; 
         }
         if (list == null || list.isEmpty()) return;

         List<PlayerInfoData> out = new ArrayList<>(list.size());
         for (PlayerInfoData infoData : list) {
            if (infoData == null || infoData.getProfile() == null) {
               out.add(infoData);
               continue;
            }
            WrappedGameProfile profile = infoData.getProfile();
            try {
               if (FakeManager.isFake(profile.getName())) {
                  infoData = new PlayerInfoData(
                        FakeManager.cloneProfile(profile),
                        infoData.getLatency(),
                        infoData.getGameMode(),
                        infoData.getDisplayName());
               }
            } catch (Throwable ignored) { }
            out.add(infoData);
         }
         try { packet.getPlayerInfoDataLists().write(0, out); } catch (Throwable ignored) { }
         return;
      }

      if (packet.getType() == Server.CHAT) {
         WrappedChatComponent component = (WrappedChatComponent)packet.getChatComponents().read(0);
         if (component != null) {
            packet.getChatComponents().write(0, WrappedChatComponent.fromJson(FakeManager.replaceNickedPlayers(component.getJson(), true)));
         }

         BaseComponent[] components = (BaseComponent[])packet.getModifier().read(1);
         if (components != null) {
            List<BaseComponent> newComps = new ArrayList<>();
            BaseComponent[] var22 = components;
            int var23 = components.length;

            for(int var8 = 0; var8 < var23; ++var8) {
               BaseComponent comp = var22[var8];
               TextComponent newComp = new TextComponent("");
               BaseComponent[] var11 = ComponentSerializer.parse(FakeManager.replaceNickedPlayers(ComponentSerializer.toString(comp), true));
               int var12 = var11.length;

               for(int var13 = 0; var13 < var12; ++var13) {
                  BaseComponent newTextComp = var11[var13];
                  newComp.addExtra(newTextComp);
               }

               newComps.add(newComp);
            }

            packet.getModifier().write(1, newComps.toArray(new BaseComponent[0]));
         }
         return;
      }

      if (packet.getType() == Server.SCOREBOARD_OBJECTIVE) {
         boolean replaced = false;
         try {
            if (packet.getStrings().size() > 1) {
               String title = packet.getStrings().read(1);
               if (title != null) {
                  packet.getStrings().write(1, FakeManager.replaceNickedPlayers(title, true));
                  replaced = true;
               }
            }
         } catch (Throwable ignored) {
         }
         if (!replaced) {
            try {
               WrappedChatComponent component = packet.getChatComponents().read(0);
               if (component != null) {
                  packet.getChatComponents().write(0, WrappedChatComponent.fromJson(FakeManager.replaceNickedPlayers(component.getJson(), true)));
               }
            } catch (Throwable ignored) {
            }
         }
         return;
      }

      if (packet.getType() == Server.SCOREBOARD_SCORE) {
         try {
            if (packet.getStrings().size() > 0) {
               String entry = packet.getStrings().read(0);
               if (entry != null) {
                  packet.getStrings().write(0, FakeManager.replaceNickedPlayers(entry, true));
               }
            }
         } catch (Throwable ignored) {
         }
         return;
      }

      if (packet.getType() == Server.SCOREBOARD_TEAM) {
         List<String> teamMembers = new ArrayList<>();
         Collection<?> raw;
         try {
            raw = (Collection<?>) packet.getModifier().withType(Collection.class).read(0);
         } catch (Throwable t) {
            return; 
         }
         if (raw == null) return;
         for (Object o : raw) {
            String member = (String) o;
            if (member != null && FakeManager.isFake(member)) {
               member = FakeManager.getFake(member);
            }
            teamMembers.add(member);
         }

         try { packet.getModifier().withType(Collection.class).write(0, teamMembers); } catch (Throwable ignored) { }
      }
   }
}
