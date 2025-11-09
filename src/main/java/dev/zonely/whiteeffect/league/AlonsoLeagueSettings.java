package dev.zonely.whiteeffect.league;

import java.util.Collections;
import java.util.List;

public final class AlonsoLeagueSettings {
   private final List<String> baseDescription;
   private final List<String> progressDescription;
   private final List<String> completedDescription;
   private final int progressBarLength;
   private final String progressBarSymbol;
   private final String progressColor;
   private final String remainingColor;
   private final List<AlonsoLeagueLevel> levels;

   public AlonsoLeagueSettings(List<String> baseDescription,
                               List<String> progressDescription,
                               List<String> completedDescription,
                               int progressBarLength,
                               String progressBarSymbol,
                               String progressColor,
                               String remainingColor,
                               List<AlonsoLeagueLevel> levels) {
      this.baseDescription = Collections.unmodifiableList(baseDescription);
      this.progressDescription = Collections.unmodifiableList(progressDescription);
      this.completedDescription = Collections.unmodifiableList(completedDescription);
      this.progressBarLength = progressBarLength;
      this.progressBarSymbol = progressBarSymbol;
      this.progressColor = progressColor;
      this.remainingColor = remainingColor;
      this.levels = Collections.unmodifiableList(levels);
   }

   public List<String> getBaseDescription() {
      return this.baseDescription;
   }

   public List<String> getProgressDescription() {
      return this.progressDescription;
   }

   public List<String> getCompletedDescription() {
      return this.completedDescription;
   }

   public int getProgressBarLength() {
      return this.progressBarLength;
   }

   public String getProgressBarSymbol() {
      return this.progressBarSymbol;
   }

   public String getProgressColor() {
      return this.progressColor;
   }

   public String getRemainingColor() {
      return this.remainingColor;
   }

   public List<AlonsoLeagueLevel> getLevels() {
      return this.levels;
   }
}
