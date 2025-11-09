package dev.zonely.whiteeffect.league;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.ProgressBar;
import dev.zonely.whiteeffect.utils.StringUtils;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AlonsoLeagueMenuRenderer {
   private AlonsoLeagueMenuRenderer() {
   }

   public static void apply(PlayerMenu menu, Profile profile, int points) {
      AlonsoLeagueSettings settings = Core.getInstance().getAlonsoLeagueSettings();
      if (settings == null || settings.getLevels().isEmpty()) {
         return;
      }

      Player player = profile.getPlayer();
      for (AlonsoLeagueLevel level : settings.getLevels()) {
         ItemStack icon = buildIcon(player, settings, level, points);
         if (icon != null) {
            menu.setItem(level.getSlot(), icon);
         }
      }
   }

   private static ItemStack buildIcon(Player player, AlonsoLeagueSettings settings, AlonsoLeagueLevel level, int points) {
      int required = Math.max(0, level.getRequiredPoints());
      boolean completed = required <= 0 || points >= required;
      int effectiveCurrent = points;
      if (completed && required > 0) {
         effectiveCurrent = required;
      } else if (required > 0) {
         effectiveCurrent = Math.max(0, Math.min(points, required));
      } else {
         effectiveCurrent = 1;
      }

      String progressBar = "";
      String percentText = "100.0";
      if (required > 0) {
         int percent = (effectiveCurrent * 100) / required;
         double rounded = Math.round(percent * 10.0D) / 10.0D;
         percentText = String.valueOf(rounded);
         progressBar = ProgressBar.getProgressBar(
               effectiveCurrent,
               required,
               settings.getProgressBarLength(),
               settings.getProgressBarSymbol(),
               settings.getProgressColor(),
               settings.getRemainingColor());
      } else {
         progressBar = ProgressBar.getProgressBar(
               settings.getProgressBarLength(),
               settings.getProgressBarLength(),
               settings.getProgressBarLength(),
               settings.getProgressBarSymbol(),
               settings.getProgressColor(),
               settings.getRemainingColor());
      }

      Map<String, String> replacements = new HashMap<>();
      replacements.put("{player}", player.getName());
      replacements.put("{appearance}", level.getAppearance());
      replacements.put("{current}", String.valueOf(points));
      replacements.put("{required}", String.valueOf(required));
      replacements.put("{missing}", String.valueOf(required > 0 ? Math.max(0, required - points) : 0));
      replacements.put("{percent}", percentText);
      replacements.put("{bar}", progressBar);

      String displayName = replace(levelDisplayName(level, completed), replacements);

      List<String> loreLines = new ArrayList<>();
      addLines(loreLines, settings.getBaseDescription(), replacements);
      if (required > 0) {
         if (completed) {
            addLines(loreLines, settings.getCompletedDescription(), replacements);
         } else {
            addLines(loreLines, settings.getProgressDescription(), replacements);
         }
      } else {
         addLines(loreLines, settings.getCompletedDescription(), replacements);
      }

      String description = String.join("\n", loreLines);
      StringBuilder raw = new StringBuilder();
      raw.append(level.getIcon()).append(" : name>").append(displayName).append(" : desc>").append(description);
      if (completed && level.isGlowOnComplete()) {
         raw.append(" : enchant>LURE:1");
      }

      String resolved = PlaceholderAPI.setPlaceholders(player, replace(raw.toString(), replacements));
      return BukkitUtils.deserializeItemStack(resolved);
   }

   private static void addLines(List<String> target, List<String> source, Map<String, String> replacements) {
      for (String line : source) {
         target.add(replace(line, replacements));
      }
   }

   private static String levelDisplayName(AlonsoLeagueLevel level, boolean completed) {
      String name = completed ? level.getCompletedName() : level.getName();
      return name == null ? "" : name;
   }

   private static String replace(String input, Map<String, String> replacements) {
      String result = input == null ? "" : input;
      for (Map.Entry<String, String> entry : replacements.entrySet()) {
         result = result.replace(entry.getKey(), entry.getValue());
      }
      return StringUtils.formatColors(result);
   }
}
