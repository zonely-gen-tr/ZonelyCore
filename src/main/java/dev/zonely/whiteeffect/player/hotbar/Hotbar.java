package dev.zonely.whiteeffect.player.hotbar;

import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import java.util.ArrayList;
import java.util.List;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import dev.zonely.whiteeffect.nms.util.SafeInventoryUpdater;

public class Hotbar {
   private static final List<Hotbar> HOTBARS = new ArrayList();
   private final String id;
   private final List<HotbarButton> buttons;

   public Hotbar(String id) {
      this.id = id;
      this.buttons = new ArrayList();
   }

   public static void addHotbar(Hotbar hotbar) {
      HOTBARS.add(hotbar);
   }

   public static Hotbar getHotbarById(String id) {
      return HOTBARS.stream().filter(hb -> hb.getName().equalsIgnoreCase(id)).findFirst().orElse(null);
   }
   public String getName() {
      return this.id;
   }

   public List<HotbarButton> getButtons() {
      return this.buttons;
   }

   public void apply(Profile profile) {
      Player player = profile.getPlayer();
      if (player == null) {
         return;
      }

      for (int slot = 0; slot <= 8; slot++) {
         player.getInventory().setItem(slot, null);
      }

      this.buttons.stream()
            .filter(button -> button.getSlot() >= 0 && button.getSlot() <= 8)
            .forEach(button -> {
               ItemStack icon = BukkitUtils.deserializeItemStack(PlaceholderAPI.setPlaceholders(player, button.getIcon().replace("%profil%", "")));
               if (button.getIcon().contains("%profil%")) {
                  icon = BukkitUtils.putProfileOnSkull(player, icon);
               }
               player.getInventory().setItem(button.getSlot(), icon);
            });
      SafeInventoryUpdater.update(player);
   }
   public HotbarButton compareButton(Player player, ItemStack item) {
      return this.buttons.stream().filter(button -> button.getSlot() >= 0 && button.getSlot() <= 8 && item.equals(player.getInventory().getItem(button.getSlot()))).findFirst()
              .orElse(null);
   }
}
