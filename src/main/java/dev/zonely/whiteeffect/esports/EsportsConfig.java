package dev.zonely.whiteeffect.esports;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

public final class EsportsConfig {

    private final boolean enabled;
    private final int refreshSeconds;
    private final Integer tournamentId;
    private final String tournamentSlug;
    private final String tournamentState;
    private final Integer clubId;
    private final String clubTag;
    private final ArenaSettings arenaSettings;
    private final boolean autoStartEnabled;
    private final int autoStartDelayMinutes;

    private EsportsConfig(
            boolean enabled,
            int refreshSeconds,
            Integer tournamentId,
            String tournamentSlug,
            String tournamentState,
            Integer clubId,
            String clubTag,
            ArenaSettings arenaSettings,
            boolean autoStartEnabled,
            int autoStartDelayMinutes
    ) {
        this.enabled = enabled;
        this.refreshSeconds = refreshSeconds;
        this.tournamentId = tournamentId;
        this.tournamentSlug = tournamentSlug;
        this.tournamentState = tournamentState;
        this.clubId = clubId;
        this.clubTag = clubTag;
        this.arenaSettings = arenaSettings;
        this.autoStartEnabled = autoStartEnabled;
        this.autoStartDelayMinutes = autoStartDelayMinutes;
    }

    public static EsportsConfig from(ConfigurationSection section) {
        if (section == null) {
            section = new MemoryConfiguration();
        }

        boolean enabled = section.getBoolean("enabled", true);
        int refresh = Math.max(5, section.getInt("refresh-seconds", 20));

        ConfigurationSection tournamentSection = section.getConfigurationSection("tournament");
        Integer tournamentId = null;
        String tournamentSlug = null;
        String state = null;
        if (tournamentSection != null) {
            int id = tournamentSection.getInt("id", 0);
            if (id > 0) {
                tournamentId = id;
            }
            tournamentSlug = trimToNull(tournamentSection.getString("slug", ""));
            state = trimToNull(tournamentSection.getString("state", "live"));
        } else {
            state = "live";
        }

        ConfigurationSection clubSection = section.getConfigurationSection("club");
        Integer clubId = null;
        String clubTag = null;
        if (clubSection != null) {
            int id = clubSection.getInt("id", 0);
            if (id > 0) {
                clubId = id;
            }
            clubTag = trimToNull(clubSection.getString("tag", ""));
        }

        ArenaSettings arenaSettings = ArenaSettings.from(section.getConfigurationSection("arena"));
        ConfigurationSection autoStart = section.getConfigurationSection("auto-start");
        if (autoStart == null) {
            autoStart = section.getConfigurationSection("auto_start");
        }
        boolean autoStartEnabled = autoStart != null && autoStart.getBoolean("enabled", false);
        int autoStartDelayMinutes = autoStart != null ? Math.max(1, autoStart.getInt("delay-minutes", 10)) : 10;

        return new EsportsConfig(enabled, refresh, tournamentId, tournamentSlug, state, clubId, clubTag, arenaSettings, autoStartEnabled, autoStartDelayMinutes);
    }

    public static EsportsConfig disabled() {
        return new EsportsConfig(false, 30, null, null, "live", null, null, ArenaSettings.from(null), false, 10);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getRefreshSeconds() {
        return refreshSeconds;
    }

    public long getRefreshTicks() {
        return Math.max(5, refreshSeconds) * 20L;
    }

    public Integer getTournamentId() {
        return tournamentId;
    }

    public String getTournamentSlug() {
        return tournamentSlug;
    }

    public String getTournamentState() {
        return tournamentState;
    }

    public Integer getClubId() {
        return clubId;
    }

    public String getClubTag() {
        return clubTag;
    }

    public ArenaSettings getArenaSettings() {
        return arenaSettings;
    }

    public boolean isAutoStartEnabled() {
        return autoStartEnabled;
    }

    public int getAutoStartDelayMinutes() {
        return autoStartDelayMinutes;
    }

    public static final class ArenaSettings {
        private final String spectatorWorld;
        private final double spectatorX;
        private final double spectatorY;
        private final double spectatorZ;
        private final float spectatorYaw;
        private final float spectatorPitch;
        private final int countdownSeconds;
        private final double borderRadius;
        private final double stagingBorderRadius;
        private final int borderGrowthSeconds;
        private final double autoSpawnRadius;

        private ArenaSettings(String spectatorWorld,
                              double spectatorX,
                              double spectatorY,
                              double spectatorZ,
                              float spectatorYaw,
                              float spectatorPitch,
                              int countdownSeconds,
                              double borderRadius,
                              double stagingBorderRadius,
                              int borderGrowthSeconds,
                              double autoSpawnRadius) {
            this.spectatorWorld = spectatorWorld;
            this.spectatorX = spectatorX;
            this.spectatorY = spectatorY;
            this.spectatorZ = spectatorZ;
            this.spectatorYaw = spectatorYaw;
            this.spectatorPitch = spectatorPitch;
            this.countdownSeconds = countdownSeconds;
            this.borderRadius = borderRadius;
            this.stagingBorderRadius = stagingBorderRadius;
            this.borderGrowthSeconds = borderGrowthSeconds;
            this.autoSpawnRadius = autoSpawnRadius;
        }

        public static ArenaSettings from(ConfigurationSection section) {
            if (section == null) {
                section = new MemoryConfiguration();
            }
            ConfigurationSection spectator = section.getConfigurationSection("spectator");
            String world = spectator != null ? trimToNull(spectator.getString("world", "world")) : "world";
            double x = spectator != null ? spectator.getDouble("x", 0.5D) : 0.5D;
            double y = spectator != null ? spectator.getDouble("y", 80.0D) : 80.0D;
            double z = spectator != null ? spectator.getDouble("z", 0.5D) : 0.5D;
            float yaw = spectator != null ? (float) spectator.getDouble("yaw", 0.0D) : 0.0F;
            float pitch = spectator != null ? (float) spectator.getDouble("pitch", 0.0D) : 0.0F;
            int countdown = Math.max(3, section.getInt("countdown-seconds", 5));
            double radius = Math.max(5.0D, section.getDouble("border-radius", 35.0D));
            double stagingRadius = Math.max(2.0D, section.getDouble("staging-border-radius", 8.0D));
            int growthSeconds = Math.max(0, section.getInt("border-growth-seconds", 6));
            double autoSpawnRadius = Math.max(2.0D, section.getDouble("spawn-autofill-radius", 6.0D));
            return new ArenaSettings(world, x, y, z, yaw, pitch, countdown, radius, stagingRadius, growthSeconds, autoSpawnRadius);
        }

        public String getSpectatorWorld() {
            return spectatorWorld;
        }

        public double getSpectatorX() {
            return spectatorX;
        }

        public double getSpectatorY() {
            return spectatorY;
        }

        public double getSpectatorZ() {
            return spectatorZ;
        }

        public float getSpectatorYaw() {
            return spectatorYaw;
        }

        public float getSpectatorPitch() {
            return spectatorPitch;
        }

        public int getCountdownSeconds() {
            return countdownSeconds;
        }

        public double getBorderRadius() {
            return borderRadius;
        }

        public double getStagingBorderRadius() {
            return stagingBorderRadius;
        }

        public int getBorderGrowthSeconds() {
            return borderGrowthSeconds;
        }

        public double getAutoSpawnRadius() {
            return autoSpawnRadius;
        }
    }
}
