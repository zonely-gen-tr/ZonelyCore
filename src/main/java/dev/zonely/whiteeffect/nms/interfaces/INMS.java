package dev.zonely.whiteeffect.nms.interfaces;

import dev.zonely.whiteeffect.libraries.holograms.api.Hologram;
import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPCAnimation;
import dev.zonely.whiteeffect.libraries.npclib.npc.skin.SkinnableEntity;
import dev.zonely.whiteeffect.nms.interfaces.entity.IArmorStand;
import dev.zonely.whiteeffect.nms.interfaces.entity.IItem;
import dev.zonely.whiteeffect.nms.interfaces.entity.ISlime;
import java.util.Collection;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;

public interface INMS {
   IArmorStand createArmorStand(Location var1, String var2, HologramLine var3);

   IItem createItem(Location var1, ItemStack var2, HologramLine var3);

   ISlime createSlime(Location var1, HologramLine var2);

   Hologram getHologram(Entity var1);

   Hologram getPreHologram(int var1);

   boolean isHologramEntity(Entity var1);

   void playChestAction(Location var1, boolean var2);

   void playAnimation(Entity var1, NPCAnimation var2);

   void setValueAndSignature(Player var1, String var2, String var3);

   void sendTabListAdd(Player var1, Player var2);

   void sendTabListRemove(Player var1, Collection<SkinnableEntity> var2);

   void sendTabListRemove(Player var1, Player var2);

   void removeFromPlayerList(Player var1);

   void removeFromServerPlayerList(Player var1);

   boolean addToWorld(World var1, Entity var2, SpawnReason var3);

   void removeFromWorld(Entity var1);

   void replaceTrackerEntry(Player var1);

   void sendPacket(Player var1, Object var2);

   void look(Entity var1, float var2, float var3);

   void setHeadYaw(Entity var1, float var2);

   void setStepHeight(LivingEntity var1, float var2);

   float getStepHeight(LivingEntity var1);

   SkinnableEntity getSkinnable(Entity var1);

   void flyingMoveLogic(LivingEntity var1, float var2, float var3);

   void sendActionBar(Player var1, String var2);

   void sendTitle(Player var1, String var2, String var3);

   void sendTitle(Player var1, String var2, String var3, int var4, int var5, int var6);

   void sendTabHeaderFooter(Player var1, String var2, String var3);

   void clearPathfinderGoal(Object var1);

   void refreshPlayer(Player var1);
}
