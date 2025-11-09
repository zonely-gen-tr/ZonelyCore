package dev.zonely.whiteeffect.hook;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.Manager;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.deliveries.Delivery;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.mysterybox.api.MysteryBoxAPI;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.enums.PlayerVisibility;
import dev.zonely.whiteeffect.player.fake.FakeManager;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.store.LastCreditPlaceholderService;
import dev.zonely.whiteeffect.utils.StringUtils;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class WCoreExpansion extends PlaceholderExpansion {
   private static final SimpleDateFormat MURDER_FORMAT = new SimpleDateFormat("mm:ss");
   private static final SimpleDateFormat LAST_CREDIT_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

   public boolean canRegister() {
      return true;
   }

   public String getAuthor() {
      return "WhiteEffect";
   }

   public String getIdentifier() {
      return "ZonelyCore";
   }

   public String getVersion() {
      return Core.getInstance().getDescription().getVersion();
   }

   public String onPlaceholderRequest(Player player, String params) {
      Profile profile = null;
      if (player != null && (profile = Profile.getProfile(player.getName())) != null) {
         String table;
         if (params.equals("perm")) {
            return Role.getPlayerRole(player).getName();
         } else if (params.equals("cash")) {
            return StringUtils.formatNumber(CashManager.getCash(player));
         } else if (params.equals("mysteryboxes")) {
            return StringUtils.formatNumber(MysteryBoxAPI.getMysteryBoxes(profile));
         } else if (params.equals("playername")) {
            return Manager.isFake(profile.getName()) ? FakeManager.getFake(profile.getName()) : profile.getName();
         } else if (params.equals("powder")) {
            return profile.getFormatedStats("ZonelyMysteryBox", "mystery_frags");
         } else if (params.equals("status_online")) {
            return profile.getPreferencesContainer().getPlayerVisibility().getName();
         } else if (params.equals("status_online_name")) {
            return profile.getPreferencesContainer().getPlayerVisibility() == PlayerVisibility.GENEL ? "§aAÃ‡IK" : "§cKAPALI";
         } else if (params.equals("status_delivery")) {
            Profile finalProfile = profile;
            long deliveries = Delivery.listDeliveries().stream()
                  .filter(delivery -> !finalProfile.getDeliveriesContainer().alreadyClaimed(delivery.getId()))
                  .count();
            if (deliveries == 0L) {
               return LanguageManager.get(profile,
                     "placeholders.status.delivery.none",
                     "");
            }
            return LanguageManager.get(profile,
                  "placeholders.status.delivery.pending",
                  "&d{amount} &erewards waiting.",
                  "amount", StringUtils.formatNumber(deliveries));
         } else if (params.equals("status_online_inksack")) {
            return profile.getPreferencesContainer().getPlayerVisibility().getInkSack();
         } else if (params.startsWith("lastcredit_")) {
            LastCreditPlaceholderService service = Core.getInstance().getLastCreditPlaceholderService();
            if (service == null) {
               return "";
            }

            String[] parts = params.split("_");
            if (parts.length < 2) {
               return "";
            }

            int position;
            try {
               position = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
               return "";
            }

            LastCreditPlaceholderService.LastCreditRecord record = service.getRecord(position);
            if (record == null) {
               return "";
            }

            if (parts.length == 2) {
               return record.getUsername();
            }

            String detail = parts[2].toLowerCase(Locale.ROOT);
            switch (detail) {
               case "name":
                  return record.getUsername();
               case "amount":
                  return StringUtils.formatNumber(record.getAmount());
               case "time":
               case "date":
                  return record.getCreatedAt() != null ? LAST_CREDIT_FORMAT.format(record.getCreatedAt()) : "";
               case "display":
                  return record.getUsername() + " - " + StringUtils.formatNumber(record.getAmount());
               default:
                  return "";
            }
         } else {
            return null;
         }
      } else {
         return "";
      }
   }
}
