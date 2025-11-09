package dev.zonely.whiteeffect.utils;

import com.google.common.base.Strings;

public final class ProgressBar {
   public static String getProgressBar(int current, int max, int totalBars, String string, String string2, String string3) {
      float percent = (float)current / (float)max;
      int progressBars = (int)((float)totalBars * percent);
      return Strings.repeat("" + string2 + string, progressBars) + Strings.repeat("" + string3 + string, totalBars - progressBars);
   }
}
