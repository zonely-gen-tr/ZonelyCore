package dev.zonely.whiteeffect.libraries.npclib.npc.skin;

import com.google.common.base.Preconditions;
import dev.zonely.whiteeffect.libraries.npclib.NPCLibrary;
import dev.zonely.whiteeffect.nms.NMS;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

class TabListRemover {
   private final Map<UUID, TabListRemover.PlayerEntry> pending = new HashMap(Bukkit.getMaxPlayers() / 2);

   TabListRemover() {
      Bukkit.getScheduler().runTaskTimer(NPCLibrary.getPlugin(), new TabListRemover.Sender(), 2L, 2L);
   }

   public void cancelPackets(Player player) {
      Preconditions.checkNotNull(player);
      TabListRemover.PlayerEntry entry = (TabListRemover.PlayerEntry)this.pending.remove(player.getUniqueId());
      if (entry != null) {
         Iterator var3 = entry.toRemove.iterator();

         while(var3.hasNext()) {
            SkinnableEntity entity = (SkinnableEntity)var3.next();
            entity.getSkinTracker().notifyRemovePacketCancelled(player.getUniqueId());
         }

      }
   }

   public void cancelPackets(Player player, SkinnableEntity skinnable) {
      Preconditions.checkNotNull(player);
      Preconditions.checkNotNull(skinnable);
      TabListRemover.PlayerEntry entry = (TabListRemover.PlayerEntry)this.pending.get(player.getUniqueId());
      if (entry != null) {
         if (entry.toRemove.remove(skinnable)) {
            skinnable.getSkinTracker().notifyRemovePacketCancelled(player.getUniqueId());
         }

         if (entry.toRemove.isEmpty()) {
            this.pending.remove(player.getUniqueId());
         }

      }
   }

   private TabListRemover.PlayerEntry getEntry(Player player) {
      TabListRemover.PlayerEntry entry = (TabListRemover.PlayerEntry)this.pending.get(player.getUniqueId());
      if (entry == null) {
         entry = new TabListRemover.PlayerEntry(player);
         this.pending.put(player.getUniqueId(), entry);
      }

      return entry;
   }

   public void sendPacket(Player player, SkinnableEntity entity) {
      Preconditions.checkNotNull(player);
      Preconditions.checkNotNull(entity);
      TabListRemover.PlayerEntry entry = this.getEntry(player);
      entry.toRemove.add(entity);
   }

   private class PlayerEntry {
      Player player;
      Set<SkinnableEntity> toRemove = new HashSet(25);

      PlayerEntry(Player player) {
         this.player = player;
      }
   }

   private class Sender implements Runnable {
      private Sender() {
      }

      public void run() {
         int maxPacketEntries = 15;
         Iterator entryIterator = TabListRemover.this.pending.entrySet().iterator();

         while(entryIterator.hasNext()) {
            Entry<UUID, TabListRemover.PlayerEntry> mapEntry = (Entry)entryIterator.next();
            TabListRemover.PlayerEntry entry = (TabListRemover.PlayerEntry)mapEntry.getValue();
            int listSize = Math.min(maxPacketEntries, entry.toRemove.size());
            boolean sendAll = listSize == entry.toRemove.size();
            List<SkinnableEntity> skinnableList = new ArrayList(listSize);
            int i = 0;

            for(Iterator skinIterator = entry.toRemove.iterator(); skinIterator.hasNext() && i < maxPacketEntries; ++i) {
               SkinnableEntity skinnable = (SkinnableEntity)skinIterator.next();
               skinnableList.add(skinnable);
               skinIterator.remove();
            }

            if (entry.player.isOnline()) {
               NMS.sendTabListRemove(entry.player, (Collection)skinnableList);
            }

            Iterator var12 = skinnableList.iterator();

            while(var12.hasNext()) {
               SkinnableEntity entity = (SkinnableEntity)var12.next();
               entity.getSkinTracker().notifyRemovePacketSent(entry.player.getUniqueId());
            }

            if (sendAll) {
               entryIterator.remove();
            }
         }

      }

      Sender(Object x1) {
         this();
      }
   }
}
