package dev.zonely.whiteeffect.player.hotbar;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.listeners.Listeners;
import dev.zonely.whiteeffect.menus.MenuProfile;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import dev.zonely.whiteeffect.utils.StringUtils;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.entity.Player;

public abstract class HotbarActionType {
   private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("###.#");
   private static final Map<String, HotbarActionType> actionTypes = new HashMap();
   public static final WConfig CONFIG = Core.getInstance().getConfig("config");
   public static String prefixCore;

   public static void addActionType(String name, HotbarActionType actionType) {
      actionTypes.put(name.toLowerCase(), actionType);
   }

   public static HotbarActionType fromName(String name) {
      return (HotbarActionType)actionTypes.get(name.toLowerCase());
   }

   public abstract void execute(Profile var1, String var2);

   static {
      prefixCore = CONFIG.getString("language.prefix.lobby");
      actionTypes.put("komut", new HotbarActionType() {
         public void execute(Profile profile, String action) {
            profile.getPlayer().performCommand(action);
         }
      });
      actionTypes.put("mesaj", new HotbarActionType() {
         public void execute(Profile profile, String action) {
            profile.getPlayer().sendMessage(StringUtils.formatColors(action).replace("\\n", "\n"));
         }
      });
      actionTypes.put("core", new HotbarActionType() {
         public void execute(Profile profile, String action) {
            if (action.equalsIgnoreCase("profil")) {
               new MenuProfile(profile);
            } else if (action.equalsIgnoreCase("online")) {
               Player player = profile.getPlayer();
               long start = Listeners.DELAY_PLAYERS.containsKey(player.getName()) ? (Long)Listeners.DELAY_PLAYERS.get(player.getName()) : 0L;
               if (start > System.currentTimeMillis()) {
                  double time = (double)(start - System.currentTimeMillis()) / 1000.0D;
                  if (time > 0.1D) {
                     String timeString = HotbarActionType.DECIMAL_FORMAT.format(time).replace(",", ".");
                     if (timeString.endsWith("0")) {
                        timeString = timeString.substring(0, timeString.lastIndexOf("."));
                     }

                     player.sendMessage(prefixCore + "§cLütfen §b" + timeString + "§b saniye §csonra oyuncuları görme özelliğini kullanın.");
                     return;
                  }
               }

               Listeners.DELAY_PLAYERS.put(player.getName(), System.currentTimeMillis() + 3000L);
               profile.getPreferencesContainer().changePlayerVisibility();
               switch(profile.getPreferencesContainer().getPlayerVisibility()) {
               case GENEL:
                  player.sendMessage("§3Lobi §8» §eOyuncu görünürlüğü başarıyla etkinleştirildi.");
                  break;
               case HICBIRI:
                  player.sendMessage("§3Lobi §8» §cOyuncu görünürlüğü başarıyla devre dışı bırakıldı.");
               }

               profile.refreshPlayers();
            }

         }
      });
   }
}
