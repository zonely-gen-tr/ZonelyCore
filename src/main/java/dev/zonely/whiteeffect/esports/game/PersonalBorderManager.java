package dev.zonely.whiteeffect.esports.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PersonalBorderManager {

    private final Map<UUID, BorderState> borders = new ConcurrentHashMap<>();
    private static final Method CREATE_BORDER_METHOD;
    private static final Method SET_PLAYER_BORDER_METHOD;

    static {
        Method create = null;
        Method set = null;
        try {
            create = Bukkit.class.getMethod("createWorldBorder");
        } catch (NoSuchMethodException ignored) {
        }
        try {
            set = Player.class.getMethod("setWorldBorder", WorldBorder.class);
        } catch (NoSuchMethodException ignored) {
        }
        CREATE_BORDER_METHOD = create;
        SET_PLAYER_BORDER_METHOD = set;
    }

    void apply(Collection<Player> players, Location center, BorderOptions options, double initialRadius) {
        if (players == null || players.isEmpty()) {
            return;
        }
        for (Player player : players) {
            apply(player, center, options, initialRadius);
        }
    }

    void apply(Player player, Location center, BorderOptions options, double initialRadius) {
        if (player == null || center == null || center.getWorld() == null || options == null) {
            return;
        }
        double radius = Math.max(1.0D, initialRadius);
        WorldBorder border = createPersonalBorder(center, options, radius);
        if (border == null) {
            return;
        }
        assignBorder(player, border);
        borders.put(player.getUniqueId(), new BorderState(player.getUniqueId(), border, center, options, radius));
    }

    void updateRadius(Player player, double newRadius) {
        if (player == null) {
            return;
        }
        BorderState state = borders.get(player.getUniqueId());
        if (state == null || state.getBorder() == null) {
            return;
        }
        double radius = Math.max(1.0D, newRadius);
        state.getBorder().setSize(radius * 2.0D);
        state.setCurrentRadius(radius);
    }

    void clear(Player player) {
        if (player == null) {
            return;
        }
        borders.remove(player.getUniqueId());
        resetBorder(player);
    }

    void clearAll() {
        for (UUID uuid : new ArrayList<>(borders.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                resetBorder(player);
            }
        }
        borders.clear();
    }

    Collection<BorderState> snapshot() {
        return Collections.unmodifiableCollection(new ArrayList<>(borders.values()));
    }

    BorderState getState(UUID uuid) {
        return borders.get(uuid);
    }

    static final class BorderState {
        private final UUID playerId;
        private final WorldBorder border;
        private final Location center;
        private final String worldName;
        private final BorderOptions options;
        private double currentRadius;

        BorderState(UUID playerId, WorldBorder border, Location center, BorderOptions options, double currentRadius) {
            this.playerId = playerId;
            this.border = border;
            this.center = center.clone();
            this.worldName = center.getWorld() != null ? center.getWorld().getName() : null;
            this.options = options;
            this.currentRadius = currentRadius;
        }

        UUID getPlayerId() {
            return playerId;
        }

        Location getCenter() {
            return center.clone();
        }

        String getWorldName() {
            return worldName;
        }

        BorderOptions getOptions() {
            return options;
        }

        WorldBorder getBorder() {
            return border;
        }

        double getCurrentRadius() {
            return currentRadius;
        }

        void setCurrentRadius(double radius) {
            this.currentRadius = radius;
        }

        double distance(Location location) {
            if (location == null || location.getWorld() == null) {
                return Double.MAX_VALUE;
            }
            if (worldName == null || !worldName.equals(location.getWorld().getName())) {
                return Double.MAX_VALUE;
            }
            double dx = location.getX() - center.getX();
            double dz = location.getZ() - center.getZ();
            return Math.sqrt((dx * dx) + (dz * dz));
        }
    }

    static final class BorderOptions {
        private final double targetRadius;
        private final int warningDistance;
        private final int warningTime;
        private final double damageBuffer;
        private final double damageAmount;
        private final double warningMargin;
        private final double damageMargin;
        private final boolean teleportBack;
        private final int violationIntervalTicks;

        BorderOptions(double radius,
                      int warningDistance,
                      int warningTime,
                      double damageBuffer,
                      double damageAmount,
                      double warningMargin,
                      double damageMargin,
                      boolean teleportBack,
                      int violationIntervalTicks) {
            this.targetRadius = radius;
            this.warningDistance = warningDistance;
            this.warningTime = warningTime;
            this.damageBuffer = damageBuffer;
            this.damageAmount = damageAmount;
            this.warningMargin = warningMargin;
            this.damageMargin = damageMargin;
            this.teleportBack = teleportBack;
            this.violationIntervalTicks = violationIntervalTicks;
        }

        double getTargetRadius() {
            return targetRadius;
        }

        int getWarningDistance() {
            return warningDistance;
        }

        int getWarningTime() {
            return warningTime;
        }

        double getDamageBuffer() {
            return damageBuffer;
        }

        double getDamageAmount() {
            return damageAmount;
        }

        double getWarningMargin() {
            return warningMargin;
        }

        double getDamageMargin() {
            return damageMargin;
        }

        boolean isTeleportBack() {
            return teleportBack;
        }

        int getViolationIntervalTicks() {
            return violationIntervalTicks;
        }
    }

    private WorldBorder createPersonalBorder(Location center, BorderOptions options, double radius) {
        if (CREATE_BORDER_METHOD == null) {
            return null;
        }
        try {
            WorldBorder border = (WorldBorder) CREATE_BORDER_METHOD.invoke(null);
            if (border == null) {
                return null;
            }
            border.setCenter(center);
            border.setSize(radius * 2.0D);
            border.setWarningDistance(Math.max(0, options.getWarningDistance()));
            border.setWarningTime(Math.max(0, options.getWarningTime()));
            border.setDamageBuffer(Math.max(0, options.getDamageBuffer()));
            border.setDamageAmount(Math.max(0, options.getDamageAmount()));
            return border;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void assignBorder(Player player, WorldBorder border) {
        if (player == null || border == null || SET_PLAYER_BORDER_METHOD == null) {
            return;
        }
        try {
            SET_PLAYER_BORDER_METHOD.invoke(player, border);
        } catch (Exception ignored) {
        }
    }

    private void resetBorder(Player player) {
        if (player == null) {
            return;
        }
        WorldBorder worldBorder = player.getWorld() != null ? player.getWorld().getWorldBorder() : null;
        assignBorder(player, worldBorder);
    }
}
