package dev.zonely.whiteeffect.league;

public final class AlonsoLeagueLevel {
   private final int slot;
   private final int requiredPoints;
   private final String icon;
   private final String name;
   private final String completedName;
   private final String appearance;
   private final boolean glowOnComplete;

   public AlonsoLeagueLevel(int slot, int requiredPoints, String icon, String name, String completedName, String appearance, boolean glowOnComplete) {
      this.slot = slot;
      this.requiredPoints = requiredPoints;
      this.icon = icon;
      this.name = name;
      this.completedName = completedName;
      this.appearance = appearance;
      this.glowOnComplete = glowOnComplete;
   }

   public int getSlot() {
      return this.slot;
   }

   public int getRequiredPoints() {
      return this.requiredPoints;
   }

   public String getIcon() {
      return this.icon;
   }

   public String getName() {
      return this.name;
   }

   public String getCompletedName() {
      return this.completedName;
   }

   public String getAppearance() {
      return this.appearance;
   }

   public boolean isGlowOnComplete() {
      return this.glowOnComplete;
   }
}
