package dev.zonely.whiteeffect.replay;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.libraries.MinecraftVersion;
import dev.zonely.whiteeffect.libraries.npclib.NPCLibrary;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import dev.zonely.whiteeffect.libraries.profile.InvalidMojangException;
import dev.zonely.whiteeffect.libraries.profile.Mojang;
import dev.zonely.whiteeffect.nms.universal.entity.HumanControllerPackets;
import dev.zonely.whiteeffect.nms.universal.entity.PacketNPCManager;
import dev.zonely.whiteeffect.report.Report;
import dev.zonely.whiteeffect.report.ReportService;
import dev.zonely.whiteeffect.replay.advanced.AdvancedReplayEngine;
import dev.zonely.whiteeffect.replay.control.ReplayControlManager;
import dev.zonely.whiteeffect.replay.control.ReplayStatusHud;
import dev.zonely.whiteeffect.lang.LanguageManager;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPotionEffectEvent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class NPCReplayManager {

    public static final String REPLAY_VIEWER_KEY = "replay-viewer";
    private static final boolean MOB_DISPLAY_SUPPORTED = detectMobDisplaySupport();

    private static final Set<UUID> REPLAY_ENTITIES = ConcurrentHashMap.newKeySet();
    private static volatile boolean POTION_BLOCKER_REGISTERED = false;

    public static boolean isMobDisplaySupportedRuntime() {
        return MOB_DISPLAY_SUPPORTED;
    }

    public static class PlaybackSession {
        public final NPC npc;
        public final Entity displayEntity;
        public final boolean mobDisplay;
        public final Report report;
        public final List<ReplayFrame> frames;
        public int index;
        public double speed = 1.0; 
        public boolean paused = false;
        public int taskId = -1;
        public double segmentProgress = 0.0D;
        public int segmentLength = 1;
        public int maxTick = 0;
        public Location lastLocation;

        public PlaybackSession(NPC npc, Entity displayEntity, boolean mobDisplay, Report report, List<ReplayFrame> frames) {
            this.npc = npc;
            this.displayEntity = displayEntity;
            this.mobDisplay = mobDisplay;
            this.report = report;
            this.frames = frames;
            this.index = 0;
        }
    }

    private static final Map<UUID, PlaybackSession> ACTIVE = new HashMap<>();
    private static final Map<UUID, ViewerState> ORIGINAL_VIEWER_STATES = new ConcurrentHashMap<>();

    public static boolean startReplay(Core plugin, Player viewer, Report report) {
        registerPotionBlocker(plugin);

        stopReplay(viewer);
        Location origin = viewer != null ? cloneLocation(viewer.getLocation()) : null;
        AdvancedReplayEngine advancedEngine = AdvancedReplayEngine.get();
        rememberViewerState(viewer, origin);
        if (advancedEngine.start(viewer, report)) {
            ReplayControlManager.begin(viewer);
            return true;
        }
        ReportService svc = plugin.getReportService();
        if (svc == null) {
            plugin.getLogger().warning("[Reports] Replay requested but report service is unavailable.");
            clearViewerState(viewer != null ? viewer.getUniqueId() : null);
            return false;
        }
        List<ReplayFrame> frames = svc.loadReplayFrames(report.getId());
        if (frames.isEmpty()) {
            clearViewerState(viewer != null ? viewer.getUniqueId() : null);
            return false;
        }

        Location first = frames.get(0).toLocation();
        if (first.getWorld() == null) {
            clearViewerState(viewer != null ? viewer.getUniqueId() : null);
            return false;
        }
        boolean useMobDisplay = MOB_DISPLAY_SUPPORTED;
        Entity displayEntity = null;
        NPC npc = null;
        if (useMobDisplay) {
            displayEntity = spawnReplayDisplayEntity(first);
            if (displayEntity == null) {
                useMobDisplay = false;
            } else {
                trackReplayEntity(displayEntity);
            }
        }
        if (!useMobDisplay) {
            UUID npcUuid = report.getTargetUuid();
            npc = npcUuid != null
                    ? NPCLibrary.createNPC(EntityType.PLAYER, npcUuid, report.getTarget())
                    : NPCLibrary.createNPC(EntityType.PLAYER, report.getTarget());
            configureReplayNpcSkin(plugin, npc, report);
            if (viewer != null) {
                npc.data().set(REPLAY_VIEWER_KEY, viewer.getUniqueId().toString());
            } else {
                npc.data().set(REPLAY_VIEWER_KEY, null);
            }
            npc.spawn(first);
            prepareAnchorEntity(npc.getEntity());
            trackReplayEntity(npc.getEntity());
        }
        if (useMobDisplay && displayEntity != null) {
            orientEntity(displayEntity, first);
            zeroVelocity(displayEntity);
            trySetInvisible(displayEntity, false);
            clearInvisibilityPotions(displayEntity);
        } else if (npc != null && npc.getEntity() != null) {
            clearInvisibilityPotions(npc.getEntity());
        }

        Location camera = first.clone();
        Vector forward = camera.getDirection().normalize();
        if (forward.lengthSquared() == 0) {
            forward = new Vector(0, 0, 1);
        }
        Vector sideways = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Location viewPoint = camera.clone()
                .add(sideways.multiply(3))
                .add(0, 1.5, 0);
        Vector look = camera.clone().add(0, 1.5, 0).toVector().subtract(viewPoint.toVector());
        if (look.lengthSquared() > 0) {
            viewPoint.setDirection(look);
        }
        viewer.teleport(viewPoint, PlayerTeleportEvent.TeleportCause.PLUGIN);
        try {
            viewer.setGameMode(GameMode.SPECTATOR);
        } catch (Throwable ignored) {}

        PlaybackSession session = new PlaybackSession(npc, displayEntity, useMobDisplay, report, frames);
        ACTIVE.put(viewer.getUniqueId(), session);
        initializePlaybackSession(session, first);

        session.taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (session.paused) {
                return;
            }
            if (!advancePlayback(session)) {
                stopReplay(viewer);
            } else {
                sendLegacyHud(viewer, session);
            }
        }, 0L, 1L);
        ReplayControlManager.begin(viewer);
        sendLegacyHud(viewer, session);
        LanguageManager.send(viewer,
                "commands.reports.replay.limited",
                "{prefix}&eAdvanced replay data is unavailable for this report. Display may be limited.",
                "prefix", LanguageManager.get("prefix.punish", "&4Punish &8->> "));
        return true;
    }

    private static void configureReplayNpcSkin(Core plugin, NPC npc, Report report) {
        if (npc == null) {
            return;
        }
        npc.data().remove(HumanControllerPackets.REPLAY_SKIN_VALUE_KEY);
        npc.data().remove(HumanControllerPackets.REPLAY_SKIN_SIGNATURE_KEY);
        npc.data().set(NPC.COPY_PLAYER_SKIN, false);
        SkinData skin = resolveSkinData(plugin, report);
        if (skin != null) {
            npc.data().set(HumanControllerPackets.REPLAY_SKIN_VALUE_KEY, skin.value());
            npc.data().set(HumanControllerPackets.REPLAY_SKIN_SIGNATURE_KEY, skin.signature());
        } else {
            npc.data().set(NPC.COPY_PLAYER_SKIN, true);
        }
    }

    private static SkinData resolveSkinData(Core plugin, Report report) {
        if (report == null) {
            return null;
        }
        String property = null;
        try {
            UUID targetUuid = report.getTargetUuid();
            if (targetUuid != null) {
                property = Mojang.getSkinProperty(targetUuid.toString().replace("-", ""));
            }
            if ((property == null || property.isEmpty()) && report.getTarget() != null && !report.getTarget().isEmpty()) {
                String uuid = Mojang.getUUID(report.getTarget());
                if (uuid != null && !uuid.isEmpty()) {
                    property = Mojang.getSkinProperty(uuid);
                }
            }
        } catch (InvalidMojangException ex) {
            if (plugin != null) {
                plugin.getLogger().log(Level.FINEST, "Unable to resolve replay skin for " + report.getTarget(), ex);
            }
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().log(Level.FINEST, "Unexpected replay skin error for " + report.getTarget(), ex);
            }
        }
        return parseSkinProperty(property);
    }

    private static SkinData parseSkinProperty(String property) {
        if (property == null || property.isEmpty()) {
            return null;
        }
        String[] spaced = property.split(" : ");
        if (spaced.length >= 3) {
            String value = spaced[1].trim();
            String signature = spaced[2].trim();
            if (!value.isEmpty() && !signature.isEmpty()) {
                return new SkinData(value, signature);
            }
        }
        String[] colonSplit = property.split(":");
        if (colonSplit.length == 2) {
            String value = colonSplit[0].trim();
            String signature = colonSplit[1].trim();
            if (!value.isEmpty() && !signature.isEmpty()) {
                return new SkinData(value, signature);
            }
        } else if (colonSplit.length >= 3) {
            String value = colonSplit[1].trim();
            String signature = colonSplit[2].trim();
            if (!value.isEmpty() && !signature.isEmpty()) {
                return new SkinData(value, signature);
            }
        }
        return null;
    }

    private static void initializePlaybackSession(PlaybackSession session, Location initialLocation) {
        session.index = 0;
        session.segmentProgress = 0.0D;
        session.segmentLength = computeSegmentLength(session);
        session.maxTick = session.frames.isEmpty() ? 0 : session.frames.get(session.frames.size() - 1).tick;
        if (initialLocation != null) {
            session.lastLocation = initialLocation.clone();
        } else if (!session.frames.isEmpty()) {
            session.lastLocation = session.frames.get(0).toLocation();
        } else {
            session.lastLocation = null;
        }
        if (session.mobDisplay && session.displayEntity != null && session.lastLocation != null) {
            orientEntity(session.displayEntity, session.lastLocation);
            zeroVelocity(session.displayEntity);
        }
    }

    private static boolean advancePlayback(PlaybackSession session) {
        if (session.frames.isEmpty()) {
            return false;
        }
        if (session.index >= session.frames.size() - 1) {
            Location last = session.frames.get(session.frames.size() - 1).toLocation();
            moveNpc(session, last);
            return false;
        }

        session.segmentProgress += session.speed;
        while (session.segmentProgress >= session.segmentLength && session.index < session.frames.size() - 1) {
            session.segmentProgress -= session.segmentLength;
            session.index++;
            if (session.index >= session.frames.size() - 1) {
                Location last = session.frames.get(session.frames.size() - 1).toLocation();
                moveNpc(session, last);
                return false;
            }
            session.segmentLength = computeSegmentLength(session);
        }

        ReplayFrame current = session.frames.get(session.index);
        ReplayFrame next = session.frames.get(session.index + 1);
        double ratio = session.segmentLength <= 0 ? 0.0D : Math.max(0.0D, Math.min(1.0D, session.segmentProgress / session.segmentLength));
        Location step = interpolate(current, next, ratio);
        moveNpc(session, step);
        return true;
    }

    private static void moveNpc(PlaybackSession session, Location target) {
        if (session == null || target == null || target.getWorld() == null) {
            return;
        }
        session.lastLocation = target.clone();
        if (session.mobDisplay) {
            Entity display = session.displayEntity;
            if (display == null || !display.isValid()) {
                return;
            }
            orientEntity(display, target);
            zeroVelocity(display);
            clearInvisibilityPotions(display);
            return;
        }
        NPC npc = session.npc;
        if (npc == null) {
            return;
        }
        org.bukkit.entity.Entity anchor = npc.getEntity();
        if (anchor == null) {
            return;
        }
        clearInvisibilityPotions(anchor);
        PacketNPCManager.handleTeleport(anchor, target, true);
        try {
            anchor.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } catch (Throwable ignored) {
            try {
                anchor.teleport(target);
            } catch (Throwable ignored2) {
            }
        }
        if (session.displayEntity != null) {
            orientEntity(session.displayEntity, target);
            zeroVelocity(session.displayEntity);
            clearInvisibilityPotions(session.displayEntity);
        }
    }

    private static int computeSegmentLength(PlaybackSession session) {
        if (session.frames.size() <= session.index + 1) {
            return 1;
        }
        int delta = session.frames.get(session.index + 1).tick - session.frames.get(session.index).tick;
        return Math.max(1, delta);
    }

    private static Location interpolate(ReplayFrame from, ReplayFrame to, double ratio) {
        Location a = from == null ? null : from.toLocation();
        Location b = to == null ? null : to.toLocation();
        if (a == null || a.getWorld() == null) {
            return b;
        }
        if (b == null || b.getWorld() == null) {
            return a;
        }
        double x = a.getX() + (b.getX() - a.getX()) * ratio;
        double y = a.getY() + (b.getY() - a.getY()) * ratio;
        double z = a.getZ() + (b.getZ() - a.getZ()) * ratio;
        float yaw = interpolateAngle(a.getYaw(), b.getYaw(), ratio);
        float pitch = (float) (a.getPitch() + (b.getPitch() - a.getPitch()) * ratio);
        return new Location(a.getWorld(), x, y, z, yaw, pitch);
    }

    private static float interpolateAngle(float start, float end, double ratio) {
        float wrapped = wrapDegrees(end - start);
        return start + (float) (wrapped * ratio);
    }

    private static float wrapDegrees(float degrees) {
        float result = degrees;
        while (result <= -180.0F) {
            result += 360.0F;
        }
        while (result > 180.0F) {
            result -= 360.0F;
        }
        return result;
    }

    private static void positionSessionAtTick(PlaybackSession session, int targetTick) {
        if (session.frames.isEmpty()) {
            return;
        }
        int lastIndex = session.frames.size() - 1;
        session.maxTick = session.frames.get(lastIndex).tick;
        if (targetTick >= session.frames.get(lastIndex).tick) {
            session.index = lastIndex;
            session.segmentProgress = 0.0D;
            session.segmentLength = 1;
            Location loc = session.frames.get(lastIndex).toLocation();
            moveNpc(session, loc);
            return;
        }
        int i = 0;
        while (i < lastIndex && session.frames.get(i + 1).tick <= targetTick) {
            i++;
        }
        session.index = i;
        ReplayFrame current = session.frames.get(i);
        ReplayFrame next = session.frames.get(i + 1);
        int span = Math.max(1, next.tick - current.tick);
        session.segmentLength = span;
        session.segmentProgress = Math.max(0, targetTick - current.tick);
        double ratio = span <= 0 ? 0.0D : Math.max(0.0D, Math.min(1.0D, session.segmentProgress / span));
        Location loc = interpolate(current, next, ratio);
        moveNpc(session, loc);
    }

    private static int getCurrentTick(PlaybackSession session) {
        if (session.frames.isEmpty()) {
            return 0;
        }
        if (session.index >= session.frames.size() - 1) {
            return session.frames.get(session.frames.size() - 1).tick;
        }
        ReplayFrame current = session.frames.get(session.index);
        int base = current.tick;
        double clamped = Math.max(0.0D, Math.min(session.segmentLength, session.segmentProgress));
        int additional = (int) Math.floor(clamped);
        return base + additional;
    }

    public static void stopReplay(Player viewer) {
        AdvancedReplayEngine.get().stop(viewer);
        PlaybackSession s = ACTIVE.remove(viewer.getUniqueId());
        if (s != null) {
            if (s.taskId != -1) {
                Core.getInstance().getServer().getScheduler().cancelTask(s.taskId);
            }
            s.taskId = -1;
            if (s.npc != null) {
                try {
                    untrackReplayEntity(s.npc.getEntity());
                } catch (Throwable ignored) {}
                try {
                    s.npc.destroy();
                } catch (Throwable ignored) {
                }
            }
            if (s.displayEntity != null) {
                untrackReplayEntity(s.displayEntity);
                removeEntitySafely(s.displayEntity);
            }
        }
        if (viewer != null) {
            try { viewer.setSpectatorTarget(null); } catch (Throwable ignored) {}
            ReplayControlManager.end(viewer);
            restoreViewerState(viewer);
            ReplayStatusHud.send(viewer, 0, 0, 1.0, false);
        }
    }

    public static boolean isReplaying(Player viewer) {
        if (AdvancedReplayEngine.get().isRunning(viewer)) {
            return true;
        }
        return ACTIVE.containsKey(viewer.getUniqueId());
    }

    public static boolean togglePause(Player viewer) {
        AdvancedReplayEngine advanced = AdvancedReplayEngine.get();
        if (advanced.isRunning(viewer)) {
            return advanced.togglePause(viewer);
        }
        PlaybackSession s = ACTIVE.get(viewer.getUniqueId());
        if (s == null) return false;
        s.paused = !s.paused;
        sendLegacyHud(viewer, s);
        return s.paused;
    }

    public static void restart(Player viewer) {
        AdvancedReplayEngine advanced = AdvancedReplayEngine.get();
        if (advanced.isRunning(viewer)) {
            advanced.restart(viewer);
            return;
        }
        PlaybackSession s = ACTIVE.get(viewer.getUniqueId());
        if (s != null) {
            Location firstLoc = s.frames.isEmpty() ? s.lastLocation : s.frames.get(0).toLocation();
            initializePlaybackSession(s, firstLoc);
            if (firstLoc != null) {
                moveNpc(s, firstLoc);
            }
            sendLegacyHud(viewer, s);
        }
    }

    public static double changeSpeed(Player viewer, double delta) {
        AdvancedReplayEngine advanced = AdvancedReplayEngine.get();
        if (advanced.isRunning(viewer)) {
            return advanced.changeSpeed(viewer, delta);
        }
        PlaybackSession s = ACTIVE.get(viewer.getUniqueId());
        if (s == null) return 1.0;
        s.speed = Math.max(0.5, Math.min(4.0, s.speed + delta));
        sendLegacyHud(viewer, s);
        return s.speed;
    }

    public static boolean seek(Player viewer, int deltaTicks) {
        AdvancedReplayEngine advanced = AdvancedReplayEngine.get();
        if (advanced.isRunning(viewer)) {
            return advanced.seek(viewer, deltaTicks);
        }
        PlaybackSession session = ACTIVE.get(viewer.getUniqueId());
        if (session == null || session.frames.isEmpty()) {
            return false;
        }
        int maxTick = session.maxTick > 0 || session.frames.isEmpty() ? session.maxTick : session.frames.get(session.frames.size() - 1).tick;
        session.maxTick = maxTick;
        int current = getCurrentTick(session);
        int target = Math.max(0, Math.min(maxTick, current + deltaTicks));
        positionSessionAtTick(session, target);
        sendLegacyHud(viewer, session);
        return true;
    }

    public static Report getActiveReport(Player viewer) {
        AdvancedReplayEngine advanced = AdvancedReplayEngine.get();
        Report advancedReport = advanced.getReport(viewer);
        if (advancedReport != null) {
            return advancedReport;
        }
        PlaybackSession s = ACTIVE.get(viewer.getUniqueId());
        return s == null ? null : s.report;
    }

    private static void sendLegacyHud(Player viewer, PlaybackSession session) {
        if (viewer == null || session == null) return;
        int currentTick = getCurrentTick(session);
        int maxTick = session.maxTick;
        if (maxTick == 0 && !session.frames.isEmpty()) {
            maxTick = session.frames.get(session.frames.size() - 1).tick;
            session.maxTick = maxTick;
        }
        ReplayStatusHud.send(viewer, currentTick, maxTick, session.speed, session.paused);
        String target = session.report != null ? session.report.getTarget() : "-";
        ReplayControlManager.updateHud(viewer,
                ReplayControlManager.hudContext(currentTick, maxTick, session.speed, session.paused, target));
    }

    private static Location cloneLocation(Location location) {
        return location == null ? null : location.clone();
    }

    private static void rememberViewerState(Player viewer, Location fallbackLocation) {
        if (viewer == null) return;
        Location base = fallbackLocation != null ? fallbackLocation.clone() : cloneLocation(viewer.getLocation());
        if (base == null || base.getWorld() == null) return;
        ViewerState state = new ViewerState(base, viewer.getGameMode(), viewer.getAllowFlight(), viewer.isFlying());
        ORIGINAL_VIEWER_STATES.put(viewer.getUniqueId(), state);
    }

    public static void restoreViewerState(Player viewer) {
        if (viewer == null) return;
        ViewerState state = ORIGINAL_VIEWER_STATES.remove(viewer.getUniqueId());
        if (state == null) return;
        if (!viewer.isOnline()) return;
        try {
            if (state.location != null && state.location.getWorld() != null) {
                viewer.teleport(state.location, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        } catch (Throwable ignored) {
            try {
                if (state.location != null && state.location.getWorld() != null) {
                    viewer.teleport(state.location);
                }
            } catch (Throwable ignored2) {
            }
        }
        try {
            if (state.gameMode != null) {
                viewer.setGameMode(state.gameMode);
            }
        } catch (Throwable ignored) {}
        try {
            viewer.setAllowFlight(state.allowFlight);
            if (state.allowFlight) {
                viewer.setFlying(state.flying);
            }
        } catch (Throwable ignored) {}
    }

    public static void clearViewerState(UUID viewerId) {
        if (viewerId == null) return;
        ORIGINAL_VIEWER_STATES.remove(viewerId);
    }

    public static Entity spawnReplayDisplayEntity(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        try {
            Entity entity = loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
            try { entity.setCustomNameVisible(false); } catch (Throwable ignored) {}
            try { entity.setCustomName(null); } catch (Throwable ignored) {}
            callBooleanSetter(entity, "setSilent", true);
            callBooleanSetter(entity, "setInvulnerable", true);
            callBooleanSetter(entity, "setGravity", false);
            callBooleanSetter(entity, "setCollidable", false);
            if (entity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) entity;
                callBooleanSetter(living, "setAI", false);
                callBooleanSetter(living, "setRemoveWhenFarAway", false);
                callBooleanSetter(living, "setCanPickupItems", false);
                callBooleanSetter(living, "setBaby", false);
                callBooleanSetter(living, "setShouldBurnInDay", false);
                callBooleanSetter(living, "setImmuneToFire", true);
                try { living.setFireTicks(0); } catch (Throwable ignored) {}
                try { living.getClass().getMethod("extinguish").invoke(living); } catch (Throwable ignored) {}
            }
            callBooleanSetter(entity, "setImmuneToFire", true);
            trySetInvisible(entity, false);
            clearInvisibilityPotions(entity);
            try { entity.setFireTicks(0); } catch (Throwable ignored) {}
            try { entity.getClass().getMethod("extinguish").invoke(entity); } catch (Throwable ignored) {}
            return entity;
        } catch (Throwable t) {
            Core.getInstance().getLogger().log(Level.WARNING, "[Replay] Failed to spawn zombie display entity: " + t.getMessage());
            return null;
        }
    }

    private static void orientEntity(Entity entity, Location target) {
        if (entity == null || target == null || target.getWorld() == null) {
            return;
        }
    try {
        entity.teleport(target);
    } catch (Throwable ignored) {
        try {
            entity.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
            } catch (Throwable ignored2) {
            }
    }
    setEntityRotation(entity, target.getYaw(), target.getPitch());
    clearInvisibilityPotions(entity);
    try { entity.setFireTicks(0); } catch (Throwable ignored) {}
    try { entity.getClass().getMethod("extinguish").invoke(entity); } catch (Throwable ignored) {}
}

    private static void zeroVelocity(Entity entity) {
        if (entity == null) return;
        try {
            entity.setVelocity(new Vector(0, 0, 0));
        } catch (Throwable ignored) {}
    }

    private static void prepareAnchorEntity(Entity anchor) {
        if (anchor == null) return;
        if (anchor instanceof Player) {
            Player playerAnchor = (Player) anchor;
            try { playerAnchor.setPlayerListName(""); } catch (Throwable ignored) {}
            try { playerAnchor.setCustomNameVisible(false); } catch (Throwable ignored) {}
            try { playerAnchor.setCustomName(null); } catch (Throwable ignored) {}
        }
    callBooleanSetter(anchor, "setSilent", true);
    callBooleanSetter(anchor, "setInvulnerable", true);
    callBooleanSetter(anchor, "setGravity", false);
    callBooleanSetter(anchor, "setCollidable", false);
    callBooleanSetter(anchor, "setImmuneToFire", true);
    try { anchor.setFireTicks(0); } catch (Throwable ignored) {}
    if (anchor instanceof LivingEntity) {
        LivingEntity living = (LivingEntity) anchor;
        callBooleanSetter(living, "setAI", false);
        clearInvisibilityPotions(living);
        try { living.getClass().getMethod("extinguish").invoke(living); } catch (Throwable ignored) {}
        callBooleanSetter(living, "setShouldBurnInDay", false);
    }
}

    private static void removeEntitySafely(Entity entity) {
        if (entity == null) return;
        try {
            entity.remove();
        } catch (Throwable ignored) {}
    }

    private static void setEntityRotation(Entity entity, float yaw, float pitch) {
        if (entity == null) return;
        try {
            Method m = entity.getClass().getMethod("setRotation", float.class, float.class);
            m.invoke(entity, yaw, pitch);
            return;
        } catch (Throwable ignored) {}
        try {
            Method m = entity.getClass().getMethod("setYawPitch", float.class, float.class);
            m.invoke(entity, yaw, pitch);
        } catch (Throwable ignored) {}
        try {
            Method m = entity.getClass().getMethod("setHeadYaw", float.class);
            m.invoke(entity, yaw);
        } catch (Throwable ignored) {}
        try {
            Method m = entity.getClass().getMethod("setBodyYaw", float.class);
            m.invoke(entity, yaw);
        } catch (Throwable ignored) {}
    }

    private static void callBooleanSetter(Object target, String method, boolean value) {
        if (target == null) return;
        try {
            Method m = target.getClass().getMethod(method, boolean.class);
            m.setAccessible(true);
            m.invoke(target, value);
        } catch (Throwable ignored) {}
    }

    private static void trySetInvisible(Object entity, boolean invisible) {
        if (entity == null) return;
        try {
            Method setter = entity.getClass().getMethod("setInvisible", boolean.class);
            setter.setAccessible(true);
            setter.invoke(entity, invisible);
        } catch (Throwable ignored) {}
    }

    private static void clearInvisibilityPotions(Object entity) {
        if (!(entity instanceof LivingEntity)) return;
        LivingEntity living = (LivingEntity) entity;
    try {
        for (PotionEffect effect : living.getActivePotionEffects()) {
            living.removePotionEffect(effect.getType());
        }
        try { living.setFireTicks(0); } catch (Throwable ignoredInner) {}
        try { living.getClass().getMethod("extinguish").invoke(living); } catch (Throwable ignoredInner) {}
    } catch (Throwable ignored) {}
}

    private static boolean detectMobDisplaySupport() {
        try {
            MinecraftVersion current = MinecraftVersion.getCurrentVersion();
            return current.newerThan(new MinecraftVersion(1, 17, 0));
        } catch (Throwable ignored) {
            try {
                String raw = org.bukkit.Bukkit.getBukkitVersion().split("-")[0];
                String[] parts = raw.split("\\.");
                int major = Integer.parseInt(parts[0]);
                int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                return major > 1 || minor >= 17;
            } catch (Throwable ignored2) {
                return true;
            }
        }
    }

    public static void registerPotionBlocker(Core plugin) {
        if (POTION_BLOCKER_REGISTERED) return;
        plugin.getServer().getPluginManager().registerEvents(new ReplayPotionBlocker(), plugin);
        POTION_BLOCKER_REGISTERED = true;
    }

    public static void trackReplayEntity(Entity e) {
        if (e != null) REPLAY_ENTITIES.add(e.getUniqueId());
    }
    public static void untrackReplayEntity(Entity e) {
        if (e != null) REPLAY_ENTITIES.remove(e.getUniqueId());
    }

    public static final class ReplayPotionBlocker implements Listener {
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
        public void onEffect(EntityPotionEffectEvent e) {
            if (!REPLAY_ENTITIES.contains(e.getEntity().getUniqueId())) return;
            EntityPotionEffectEvent.Action a = e.getAction();
            if (a == EntityPotionEffectEvent.Action.ADDED || a == EntityPotionEffectEvent.Action.CHANGED) {
                e.setCancelled(true);
            }
        }
    }

    private static final class ViewerState {
        final Location location;
        final GameMode gameMode;
        final boolean allowFlight;
        final boolean flying;

        ViewerState(Location location, GameMode gameMode, boolean allowFlight, boolean flying) {
            this.location = location;
            this.gameMode = gameMode;
            this.allowFlight = allowFlight;
            this.flying = flying;
        }
    }

    private static final class SkinData {
        private final String value;
        private final String signature;

        SkinData(String value, String signature) {
            this.value = value;
            this.signature = signature;
        }

        String value() {
            return value;
        }

        String signature() {
            return signature;
        }
    }
}
