package dev.zonely.whiteeffect.replay.advanced;

import com.google.gson.JsonObject;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.report.Report;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;


public final class AdvancedReplayRecorder {

    private static final int SAMPLE_EVERY_TICKS = 2; 
    private static final double MIN_MOVE_DISTANCE_SQ = 0.0004;
    private static final double MIN_VELOCITY_LENGTH_SQ = 0.0001;
    private static final float MIN_ROTATION_DELTA = 1.5F;

    private static AdvancedReplayRecorder instance;

    public static void initialize(Core plugin) {
        if (instance == null) {
            instance = new AdvancedReplayRecorder(plugin);
        }
    }

    public static AdvancedReplayRecorder get() {
        return instance;
    }

    private final Core plugin;
    private final AdvancedReplayStorage storage;
    private final Map<Integer, SessionContext> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> actorIndex = new ConcurrentHashMap<>();
    private final Map<UUID, ProjectileContext> projectiles = new ConcurrentHashMap<>();
    private BukkitTask samplerTask;
    private BukkitTask housekeepingTask;

    private AdvancedReplayRecorder(Core plugin) {
        this.plugin = plugin;
        this.storage = AdvancedReplayStorage.get();
        startSampler();
        startHousekeeping();
    }

    private void startSampler() {
        if (samplerTask != null) return;
        samplerTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> sessions.values().forEach(this::sampleMovement),
                SAMPLE_EVERY_TICKS,
                SAMPLE_EVERY_TICKS
        );
    }

    private void startHousekeeping() {
        if (housekeepingTask != null) return;
        housekeepingTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                () -> sessions.values().removeIf(SessionContext::expired),
                20L * 60,
                20L * 60
        );
    }

    public void shutdown() {
        if (samplerTask != null) {
            samplerTask.cancel();
            samplerTask = null;
        }
        if (housekeepingTask != null) {
            housekeepingTask.cancel();
            housekeepingTask = null;
        }
        sessions.clear();
        actorIndex.clear();
        projectiles.clear();
        storage.shutdown();
    }

    public CompletableFuture<Integer> startSessionAsync(Report report, int captureSeconds) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player target = resolveOnlinePlayer(report.getTargetUuid(), report.getTarget());
            String world = target != null ? target.getWorld().getName() : null;
            storage.createSessionAsync(report.getId(), world).thenAccept(sessionId -> {
                if (sessionId <= 0) {
                    future.complete(-1);
                    return;
                }
                SessionContext ctx = new SessionContext(report.getId(), sessionId, System.currentTimeMillis());
                sessions.put(report.getId(), ctx);

                double captureRadius = plugin.getConfig("config")
                        .getDouble("reports.replay.capture-radius", 25.0);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (target != null) {
                        trackPlayer(report.getId(), target, ReplayActorType.PLAYER);
                        trackNearbyPlayers(report.getId(), target, captureRadius);
                    }

                    Player reporter = resolveOnlinePlayer(report.getReporterUuid(), report.getReporter());
                    if (reporter != null) {
                        boolean trackReporter = target == null;
                        if (target != null && reporter.getUniqueId().equals(target.getUniqueId())) {
                            trackReporter = false;
                        }
                        if (!trackReporter && target != null) {
                            if (reporter.getWorld().equals(target.getWorld())
                                    && reporter.getLocation().distanceSquared(target.getLocation()) <= captureRadius * captureRadius) {
                                trackReporter = true;
                            }
                        }
                        if (trackReporter) {
                            trackPlayer(report.getId(), reporter, ReplayActorType.PLAYER);
                            if (target == null) {
                                trackNearbyPlayers(report.getId(), reporter, captureRadius);
                            }
                        }
                    }

                    Location chunkCaptureCenter = null;
                    if (target != null && target.isOnline()) {
                        chunkCaptureCenter = target.getLocation();
                    } else if (reporter != null && reporter.isOnline()) {
                        chunkCaptureCenter = reporter.getLocation();
                    }
                    captureInitialMapChunks(ctx, chunkCaptureCenter);

                    int seconds = Math.max(5, captureSeconds);
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> finishSession(report.getId()),
                            seconds * 20L);

                    future.complete(sessionId);
                });
            }).exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING, "[AdvancedReplay] Unable to start session for report " + report.getId(), ex);
                future.completeExceptionally(ex);
                return null;
            });
        }, 1L);
        return future;
    }

    public int startSession(Report report, int captureSeconds) {
        try {
            return startSessionAsync(report, captureSeconds).get();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "[AdvancedReplay] startSession sync fallback failed for report " + report.getId(), ex);
            return -1;
        }
    }

    public void finishSession(int reportId) {
        SessionContext ctx = sessions.remove(reportId);
        if (ctx == null) return;
        actorIndex.entrySet().removeIf(entry -> entry.getValue() == reportId);
        projectiles.entrySet().removeIf(entry -> entry.getValue().context == ctx);
        int duration = (int) (System.currentTimeMillis() - ctx.startedAt);
        storage.closeSessionAsync(ctx.sessionId, duration);
    }

    public boolean isRecording(int reportId) {
        return sessions.containsKey(reportId);
    }

    public void trackPlayer(int reportId, Player player, ReplayActorType type) {
        SessionContext ctx = sessions.get(reportId);
        if (ctx == null || player == null) return;
        UUID uuid = player.getUniqueId();
        ActorState state = ctx.actors.get(uuid);
        if (state != null && state.actorId > 0) {
            return;
        }

        if (state == null) {
            state = new ActorState(uuid, player.getName());
            ctx.actors.put(uuid, state);
            actorIndex.put(uuid, reportId);
        }

        final SessionContext sessionContext = ctx;
        final UUID playerUuid = uuid;
        final ActorState actorState = state;

        final Location location = player.getLocation().clone();
        state.lastLocation = location;
        state.lastYaw = location.getYaw();
        state.lastPitch = location.getPitch();
        state.lastHealth = getHealth(player);
        state.lastSneaking = player.isSneaking();
        state.lastSprinting = player.isSprinting();
        state.lastOnGround = resolveOnGround(player);

        final JsonObject joinPayload = buildPositionPayload(location);
        appendMovementState(joinPayload, player);
        appendHealth(joinPayload, player);
        joinPayload.addProperty("action", "join");

        PlayerInventory inventory = player.getInventory();
        final Map<String, String> equipment = captureEquipmentData(inventory);
        final int heldSlot = inventory.getHeldItemSlot();
        ItemStack heldItem = inventory.getItem(heldSlot);
        if (heldItem == null) {
            heldItem = new ItemStack(Material.AIR);
        }
        final String heldItemJson = BukkitUtils.serializeItemStack(heldItem);

        storage.ensureActorAsync(sessionContext.sessionId, playerUuid, player.getName(), type).thenAccept(actorId -> {
            if (actorId <= 0) {
                sessionContext.actors.remove(playerUuid);
                actorIndex.remove(playerUuid);
                return;
            }
            actorState.actorId = actorId;
            storage.recordActionAsync(ReplayActionRecord.of(sessionContext.sessionId, actorId, sessionContext.tick, ReplayActionType.STATE_CHANGE, joinPayload));
            for (Map.Entry<String, String> entry : equipment.entrySet()) {
                JsonObject equipPayload = new JsonObject();
                equipPayload.addProperty("slot", entry.getKey());
                equipPayload.addProperty("item", entry.getValue());
                storage.recordActionAsync(ReplayActionRecord.of(sessionContext.sessionId, actorId, sessionContext.tick, ReplayActionType.EQUIP, equipPayload));
            }
            JsonObject holdPayload = new JsonObject();
            holdPayload.addProperty("slot", "HAND");
            holdPayload.addProperty("newSlot", heldSlot);
            holdPayload.addProperty("item", heldItemJson);
            storage.recordActionAsync(ReplayActionRecord.of(sessionContext.sessionId, actorId, sessionContext.tick, ReplayActionType.ITEM_HOLD, holdPayload));
            actorState.lastEquipment.clear();
            actorState.lastEquipment.putAll(equipment);
        });
    }

    public void trackNearbyPlayers(int reportId, Player center, double radius) {
        double radiusSq = radius * radius;
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            if (candidate.equals(center)) continue;
            if (!candidate.getWorld().equals(center.getWorld())) continue;
            if (candidate.getLocation().distanceSquared(center.getLocation()) <= radiusSq) {
                trackPlayer(reportId, candidate, ReplayActorType.PLAYER);
            }
        }
    }

    public void handlePlayerQuit(UUID uuid) {
        Integer reportId = actorIndex.remove(uuid);
        if (reportId == null) return;
        SessionContext ctx = sessions.get(reportId);
        if (ctx == null) return;
        ActorState state = ctx.actors.remove(uuid);
        if (state == null) return;
        if (state.actorId <= 0) return;
        int tick = syncTick(ctx);
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "quit");
        storage.recordActionAsync(ReplayActionRecord.of(ctx.sessionId, state.actorId, tick, ReplayActionType.STATE_CHANGE, payload));
    }

    public void recordHeldItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        SessionBinding binding = findSession(player.getUniqueId());
        if (binding == null) return;
        int tick = syncTick(binding.context);
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item == null) {
            item = new ItemStack(Material.AIR);
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("slot", "HAND");
        payload.addProperty("newSlot", event.getNewSlot());
        payload.addProperty("item", BukkitUtils.serializeItemStack(item));
        storage.recordActionAsync(ReplayActionRecord.of(binding.sessionId, binding.actorId, tick, ReplayActionType.ITEM_HOLD, payload));
    }

    public void recordAnimation(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        SessionBinding binding = findSession(player.getUniqueId());
        if (binding == null) return;
        int tick = syncTick(binding.context);
        JsonObject payload = new JsonObject();
        payload.addProperty("animation", event.getAnimationType().name());
        storage.recordActionAsync(ReplayActionRecord.of(binding.sessionId, binding.actorId, tick, ReplayActionType.ANIMATION, payload));
    }

    public void recordBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        SessionBinding binding = findSession(player.getUniqueId());
        if (binding == null) return;
        int tick = syncTick(binding.context);
        Block block = event.getBlock();
        captureBlockEventMapChunks(binding.context, block.getLocation());
        JsonObject payload = buildBlockPayload(block.getLocation());
        payload.addProperty("material", block.getType().name());
        payload.addProperty("data", block.getData() & 0xFF);
        payload.addProperty("held", BukkitUtils.serializeItemStack(event.getItemInHand()));
        storage.recordActionAsync(ReplayActionRecord.of(binding.sessionId, binding.actorId, tick, ReplayActionType.BLOCK_PLACE, payload));
    }

    public void recordBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        SessionBinding binding = findSession(player.getUniqueId());
        if (binding == null) return;
        int tick = syncTick(binding.context);
        Block block = event.getBlock();
        captureBlockEventMapChunks(binding.context, block.getLocation());
        JsonObject payload = buildBlockPayload(block.getLocation());
        payload.addProperty("material", block.getType().name());
        payload.addProperty("data", block.getData() & 0xFF);
        storage.recordActionAsync(ReplayActionRecord.of(binding.sessionId, binding.actorId, tick, ReplayActionType.BLOCK_BREAK, payload));
    }

    public void recordDamage(EntityDamageByEntityEvent event) {
        Entity rawDamager = event.getDamager();
        Player attacker = null;
        if (rawDamager instanceof Player) {
            attacker = (Player) rawDamager;
        } else if (rawDamager instanceof Projectile) {
            Object shooter = ((Projectile) rawDamager).getShooter();
            if (shooter instanceof Player) {
                attacker = (Player) shooter;
            }
        }

        SessionBinding binding = attacker != null ? findSession(attacker.getUniqueId()) : null;
        Player victimPlayer = event.getEntity() instanceof Player ? (Player) event.getEntity() : null;

        if (binding == null && attacker != null && victimPlayer != null) {
            SessionBinding victimBinding = findSession(victimPlayer.getUniqueId());
            if (victimBinding != null) {
                trackPlayer(victimBinding.context.reportId, attacker, ReplayActorType.PLAYER);
                binding = findSession(attacker.getUniqueId());
                if (binding == null) {
                    ActorState state = victimBinding.context.actors.get(attacker.getUniqueId());
                    if (state != null) {
                        binding = new SessionBinding(victimBinding.context, state.actorId);
                    }
                }
            }
        }

        if (binding == null) {
            return;
        }

        SessionContext ctx = binding.context;
        int actorId = binding.actorId;
        int tick = syncTick(ctx);

        Entity victim = event.getEntity();
        int targetActor = -1;
        if (victimPlayer != null) {
            targetActor = ensureTracked(ctx, victimPlayer);
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("cause", event.getCause().name());
        payload.addProperty("amount", event.getFinalDamage());
        payload.addProperty("targetActor", targetActor);
        if (attacker != null && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            payload.addProperty("sourceAnimation", "SWING_MAIN_HAND");
        }
        if (victimPlayer != null) {
            payload.addProperty("targetHealth", getHealth(victimPlayer));
            payload.addProperty("targetMaxHealth", victimPlayer.getMaxHealth());
            payload.addProperty("targetAbsorption", getAbsorption(victimPlayer));
        }
        storage.recordActionAsync(ReplayActionRecord.of(binding.sessionId, actorId, tick, ReplayActionType.DAMAGE, payload));
    }

    public void recordProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player)) {
            return;
        }
        Player shooter = (Player) projectile.getShooter();
        SessionBinding binding = findSession(shooter.getUniqueId());
        if (binding == null) {
            return;
        }
        int tick = syncTick(binding.context);
        JsonObject payload = buildPositionPayload(projectile.getLocation());
        payload.addProperty("uuid", projectile.getUniqueId().toString());
        payload.addProperty("projectileType", projectile.getType().name());
        Vector velocity = projectile.getVelocity();
        payload.addProperty("velX", velocity.getX());
        payload.addProperty("velY", velocity.getY());
        payload.addProperty("velZ", velocity.getZ());
        storage.recordActionAsync(ReplayActionRecord.of(binding.sessionId, binding.actorId, tick, ReplayActionType.PROJECTILE_LAUNCH, payload));
        projectiles.put(projectile.getUniqueId(), new ProjectileContext(binding.context, binding.actorId, projectile.getType().name()));
    }

    public void recordProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileContext ctx = projectiles.remove(projectile.getUniqueId());
        if (ctx == null) {
            return;
        }
        int tick = syncTick(ctx.context);
        JsonObject payload = buildPositionPayload(projectile.getLocation());
        payload.addProperty("uuid", projectile.getUniqueId().toString());
        payload.addProperty("projectileType", ctx.type);
        Entity hitEntity = extractHitEntity(event);
        if (hitEntity != null) {
            payload.addProperty("hitEntity", hitEntity.getUniqueId().toString());
        }
        Block hitBlock = extractHitBlock(event);
        if (hitBlock != null) {
            payload.addProperty("hitBlockX", hitBlock.getX());
            payload.addProperty("hitBlockY", hitBlock.getY());
            payload.addProperty("hitBlockZ", hitBlock.getZ());
        }
        storage.recordActionAsync(ReplayActionRecord.of(ctx.context.sessionId, ctx.actorId, tick, ReplayActionType.PROJECTILE_HIT, payload));
    }

    private int ensureTracked(SessionContext ctx, Player player) {
        trackPlayer(ctx.reportId, player, ReplayActorType.PLAYER);
        ActorState state = ctx.actors.get(player.getUniqueId());
        return state != null ? state.actorId : -1;
    }

    private Player resolveOnlinePlayer(UUID uuid, String name) {
        Player player = null;
        if (uuid != null) {
            player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                return player;
            }
        }
        if (name == null || name.isEmpty()) {
            return null;
        }
        player = Bukkit.getPlayerExact(name);
        if (player == null) {
            player = Bukkit.getPlayer(name);
        }
        return player != null && player.isOnline() ? player : null;
    }

    public void recordMove(PlayerMoveEvent event) {
        if (event == null) {
            return;
        }
        Player player = event.getPlayer();
        SessionBinding binding = findSession(player.getUniqueId());
        if (binding == null) {
            return;
        }
        SessionContext ctx = binding.context;
        ActorState state = ctx.actors.get(player.getUniqueId());
        Location to = event.getTo();
        if (state == null || state.actorId <= 0 || to == null || to.getWorld() == null) {
            return;
        }
        int tick = syncTick(ctx);
        recordMovementSnapshot(ctx, state, player, to, tick);
        syncEquipmentSnapshot(ctx, state, player, tick);
    }

    public void recordVelocity(PlayerVelocityEvent event) {
        if (event == null) {
            return;
        }
        Player player = event.getPlayer();
        SessionBinding binding = findSession(player.getUniqueId());
        if (binding == null) {
            return;
        }
        SessionContext ctx = binding.context;
        ActorState state = ctx.actors.get(player.getUniqueId());
        if (state == null || state.actorId <= 0) {
            return;
        }
        Vector velocity = event.getVelocity();
        if (velocity == null || velocity.lengthSquared() <= MIN_VELOCITY_LENGTH_SQ) {
            return;
        }
        int tick = syncTick(ctx);
        recordVelocitySnapshot(ctx, state, velocity, tick);
    }

    private void sampleMovement(SessionContext ctx) {
        int tick = syncTick(ctx);
        Collection<ActorState> actors = ctx.actors.values();
        for (ActorState state : actors) {
            Player player = Bukkit.getPlayer(state.uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (state.actorId <= 0) {
                continue;
            }
            Location loc = player.getLocation();
            if (loc.getWorld() == null) continue;
            recordMovementSnapshot(ctx, state, player, loc, tick);
            syncEquipmentSnapshot(ctx, state, player, tick);
            double health = getHealth(player);
            if (Math.abs(health - state.lastHealth) > 0.05) {
                JsonObject payload = new JsonObject();
                payload.addProperty("health", health);
                payload.addProperty("maxHealth", player.getMaxHealth());
                payload.addProperty("absorption", getAbsorption(player));
                storage.recordActionAsync(ReplayActionRecord.of(ctx.sessionId, state.actorId, tick, ReplayActionType.STATE_CHANGE, payload));
                state.lastHealth = health;
            }
        }
    }

    private void recordMovementSnapshot(SessionContext ctx, ActorState state, Player player, Location loc, int tick) {
        if (ctx == null || state == null || player == null || loc == null || loc.getWorld() == null || state.actorId <= 0) {
            return;
        }
        captureFollowMapChunks(ctx, state, loc);
        boolean moved = state.lastLocation == null || loc.distanceSquared(state.lastLocation) > MIN_MOVE_DISTANCE_SQ;
        boolean rotated = state.lastLocation == null
                || angleDelta(loc.getYaw(), state.lastYaw) > MIN_ROTATION_DELTA
                || Math.abs(loc.getPitch() - state.lastPitch) > MIN_ROTATION_DELTA;
        boolean sneaking = player.isSneaking();
        boolean sprinting = player.isSprinting();
        Boolean onGround = resolveOnGround(player);
        boolean stateChanged = sneaking != state.lastSneaking
                || sprinting != state.lastSprinting
                || (onGround != null && !onGround.equals(state.lastOnGround));
        if (moved) {
            JsonObject payload = buildPositionPayload(loc);
            appendMovementState(payload, player);
            storage.recordActionAsync(ReplayActionRecord.of(ctx.sessionId, state.actorId, tick, ReplayActionType.MOVE, payload));
            state.lastLocation = loc.clone();
            state.lastYaw = loc.getYaw();
            state.lastPitch = loc.getPitch();
        } else if (rotated || stateChanged) {
            JsonObject payload = new JsonObject();
            payload.addProperty("yaw", loc.getYaw());
            payload.addProperty("pitch", loc.getPitch());
            appendMovementState(payload, player);
            storage.recordActionAsync(ReplayActionRecord.of(ctx.sessionId, state.actorId, tick, ReplayActionType.LOOK, payload));
            state.lastYaw = loc.getYaw();
            state.lastPitch = loc.getPitch();
        }
        state.lastSneaking = sneaking;
        state.lastSprinting = sprinting;
        state.lastOnGround = onGround;
    }

    private void recordVelocitySnapshot(SessionContext ctx, ActorState state, Vector velocity, int tick) {
        if (ctx == null || state == null || velocity == null || state.actorId <= 0) {
            return;
        }
        if (velocity.lengthSquared() <= MIN_VELOCITY_LENGTH_SQ) {
            return;
        }
        double vx = velocity.getX();
        double vy = velocity.getY();
        double vz = velocity.getZ();
        boolean sameVector = nearlyEqual(vx, state.lastVelocityX)
                && nearlyEqual(vy, state.lastVelocityY)
                && nearlyEqual(vz, state.lastVelocityZ);
        if (state.lastVelocityTick == tick && sameVector) {
            return;
        }
        if (sameVector && state.lastVelocityTick >= 0 && (tick - state.lastVelocityTick) < SAMPLE_EVERY_TICKS) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("x", vx);
        payload.addProperty("y", vy);
        payload.addProperty("z", vz);
        storage.recordActionAsync(ReplayActionRecord.of(ctx.sessionId, state.actorId, tick, ReplayActionType.VELOCITY, payload));
        state.lastVelocityX = vx;
        state.lastVelocityY = vy;
        state.lastVelocityZ = vz;
        state.lastVelocityTick = tick;
    }

    private void syncEquipmentSnapshot(SessionContext ctx, ActorState state, Player player, int tick) {
        if (ctx == null || state == null || player == null || state.actorId <= 0) {
            return;
        }
        Map<String, String> equipment = captureEquipmentData(player.getInventory());
        for (Map.Entry<String, String> entry : equipment.entrySet()) {
            String slot = entry.getKey();
            String current = entry.getValue();
            String previous = state.lastEquipment.get(slot);
            if (previous != null && previous.equals(current)) {
                continue;
            }
            JsonObject payload = new JsonObject();
            payload.addProperty("slot", slot);
            payload.addProperty("item", current);
            storage.recordActionAsync(ReplayActionRecord.of(ctx.sessionId, state.actorId, tick, ReplayActionType.EQUIP, payload));
            state.lastEquipment.put(slot, current);
        }
    }

    private int syncTick(SessionContext ctx) {
        if (ctx == null) {
            return 0;
        }
        int wallTick = (int) Math.max(0L, (System.currentTimeMillis() - ctx.startedAt) / 50L);
        if (wallTick > ctx.tick) {
            ctx.tick = wallTick;
        }
        return ctx.tick;
    }

    private static float angleDelta(float current, float previous) {
        float delta = Math.abs(current - previous) % 360.0F;
        return delta > 180.0F ? 360.0F - delta : delta;
    }

    private static boolean nearlyEqual(double left, double right) {
        return Math.abs(left - right) <= 0.0001D;
    }

    private SessionBinding findSession(UUID uuid) {
        if (uuid == null) return null;
        Integer reportId = actorIndex.get(uuid);
        if (reportId == null) return null;
        SessionContext ctx = sessions.get(reportId);
        if (ctx == null) {
            actorIndex.remove(uuid);
            return null;
        }
        ActorState state = ctx.actors.get(uuid);
        if (state == null) {
            actorIndex.remove(uuid);
            return null;
        }
        if (state.actorId <= 0) {
            return null;
        }
        return new SessionBinding(ctx, state.actorId);
    }

    private void captureInitialMapChunks(SessionContext ctx, Location center) {
        if (ctx == null || center == null) {
            return;
        }
        int radiusChunks = Math.max(0, Math.min(8,
                plugin.getConfig("config").getInt("reports.replay.chunk-capture.radius-chunks", 4)));
        captureMapChunksAround(ctx, center, radiusChunks);
    }

    private void captureFollowMapChunks(SessionContext ctx, ActorState state, Location center) {
        if (ctx == null || state == null || center == null) {
            return;
        }
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        boolean enabled = plugin.getConfig("config")
                .getBoolean("reports.replay.chunk-capture.enabled", true);
        boolean followEnabled = plugin.getConfig("config")
                .getBoolean("reports.replay.chunk-capture.follow-player", true);
        if (!enabled || !followEnabled) {
            return;
        }

        int chunkX = center.getBlockX() >> 4;
        int chunkZ = center.getBlockZ() >> 4;
        String worldName = world.getName() == null ? "world" : world.getName();
        int refreshIntervalTicks = Math.max(20, Math.min(20 * 60,
                plugin.getConfig("config").getInt("reports.replay.chunk-capture.follow-refresh-ticks", 100)));
        boolean sameChunk = worldName.equals(state.lastChunkCaptureWorld)
                && state.lastChunkCaptureX == chunkX
                && state.lastChunkCaptureZ == chunkZ;
        boolean refreshDue = state.lastChunkRefreshTick < 0
                || (ctx.tick - state.lastChunkRefreshTick) >= refreshIntervalTicks;
        if (sameChunk && !refreshDue) {
            return;
        }

        state.lastChunkCaptureWorld = worldName;
        state.lastChunkCaptureX = chunkX;
        state.lastChunkCaptureZ = chunkZ;
        state.lastChunkRefreshTick = ctx.tick;

        int radiusChunks = Math.max(0, Math.min(4,
                plugin.getConfig("config").getInt("reports.replay.chunk-capture.follow-radius-chunks", 2)));
        captureMapChunksAround(ctx, center, radiusChunks);
    }

    private void captureBlockEventMapChunks(SessionContext ctx, Location center) {
        if (ctx == null || center == null) {
            return;
        }
        boolean enabled = plugin.getConfig("config")
                .getBoolean("reports.replay.chunk-capture.enabled", true);
        boolean blockEventEnabled = plugin.getConfig("config")
                .getBoolean("reports.replay.chunk-capture.capture-on-block-events", true);
        if (!enabled || !blockEventEnabled) {
            return;
        }
        int radiusChunks = Math.max(0, Math.min(2,
                plugin.getConfig("config").getInt("reports.replay.chunk-capture.block-event-radius-chunks", 1)));
        captureMapChunksAround(ctx, center, radiusChunks);
    }

    private void captureMapChunksAround(SessionContext ctx, Location center, int radiusChunks) {
        if (ctx == null || center == null) {
            return;
        }
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        boolean enabled = plugin.getConfig("config")
                .getBoolean("reports.replay.chunk-capture.enabled", true);
        if (!enabled) {
            return;
        }

        int maxChunks = Math.max(1, Math.min(512,
                plugin.getConfig("config").getInt("reports.replay.chunk-capture.max-chunks", 256)));
        boolean loadedOnly = plugin.getConfig("config")
                .getBoolean("reports.replay.chunk-capture.loaded-only", true);
        boolean gzipEnabled = plugin.getConfig("config")
                .getBoolean("reports.replay.chunk-capture.gzip", true);

        int chunkX = center.getBlockX() >> 4;
        int chunkZ = center.getBlockZ() >> 4;
        int maxHeight = Math.max(1, Math.min(512, world.getMaxHeight()));

        int captured = 0;
        Set<String> visited = new HashSet<String>();
        String worldName = world.getName() == null ? "world" : world.getName();
        outer:
        for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                if (ctx.capturedChunkKeys.size() >= maxChunks) {
                    break outer;
                }

                int cx = chunkX + dx;
                int cz = chunkZ + dz;
                String key = buildChunkCaptureKey(worldName, cx, cz);
                if (!visited.add(key) || ctx.capturedChunkKeys.contains(key)) {
                    continue;
                }

                if (loadedOnly && !world.isChunkLoaded(cx, cz)) {
                    continue;
                }

                Chunk chunk;
                try {
                    chunk = world.getChunkAt(cx, cz);
                } catch (Throwable ignored) {
                    continue;
                }

                JsonObject payload = buildChunkSurfacePayload(world.getName(), chunk, maxHeight, center.getY(), gzipEnabled);
                if (payload == null) {
                    continue;
                }
                payload.addProperty("captureCenterChunkX", chunkX);
                payload.addProperty("captureCenterChunkZ", chunkZ);
                payload.addProperty("captureCenterY", center.getY());
                payload.addProperty("captureRadiusChunks", radiusChunks);
                payload.addProperty("capturedWorld", worldName);

                if (!ctx.capturedChunkKeys.add(key)) {
                    continue;
                }

                storage.recordActionAsync(ReplayActionRecord.of(
                        ctx.sessionId,
                        0,
                        ctx.tick,
                        ReplayActionType.MAP_CHUNK,
                        payload
                ));
                captured++;
            }
        }

        if (captured > 0) {
            plugin.getLogger().log(Level.FINE,
                    "[AdvancedReplay] Captured " + captured + " chunk surfaces for report " + ctx.reportId + ".");
        }
    }

    private JsonObject buildChunkSurfacePayload(String worldName, Chunk chunk, int maxHeight, double centerY, boolean gzipEnabled) {
        if (chunk == null) {
            return null;
        }

        ChunkSnapshot snapshot;
        try {
            snapshot = chunk.getChunkSnapshot(true, false, false);
        } catch (Throwable ignored) {
            return null;
        }

        ChunkSurfaceData encoded = encodeChunkSurface(snapshot, maxHeight, centerY);
        byte[] payloadBytes = gzipEnabled ? gzip(encoded.bytes) : encoded.bytes;
        if (payloadBytes == null || payloadBytes.length == 0) {
            return null;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("world", worldName == null ? "world" : worldName);
        payload.addProperty("chunkX", chunk.getX());
        payload.addProperty("chunkZ", chunk.getZ());
        payload.addProperty("format", "surface-palette-v1");
        payload.addProperty("compression", gzipEnabled ? "gzip" : "none");
        payload.addProperty("columns", 256);
        payload.addProperty("solidColumns", encoded.solidColumns);
        payload.addProperty("rawBytes", encoded.bytes.length);
        payload.addProperty("bytes", payloadBytes.length);
        payload.addProperty("surfaceCenterY", centerY);
        payload.addProperty("surface", Base64.getEncoder().encodeToString(payloadBytes));
        return payload;
    }

    private String buildChunkCaptureKey(String worldName, int chunkX, int chunkZ) {
        return (worldName == null ? "world" : worldName) + ':' + chunkX + ':' + chunkZ;
    }

    private ChunkSurfaceData encodeChunkSurface(ChunkSnapshot snapshot, int maxHeight, double centerY) {
        SnapshotMaterialResolver materialResolver = buildSnapshotMaterialResolver(snapshot);
        ByteArrayOutputStream columns = new ByteArrayOutputStream(16 * 16 * 2);
        Map<String, Integer> palette = new LinkedHashMap<String, Integer>();
        final int maxPaletteEntries = 255;
        int topY = Math.max(1, maxHeight) - 1;
        int centerBlockY = Math.max(0, Math.min(topY, (int) Math.floor(centerY)));
        int preferredTopY = Math.max(0, Math.min(topY, centerBlockY + 3));
        int preferredBottomY = Math.max(0, centerBlockY - 56);
        int solidColumns = 0;

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int foundY = 0;
                int paletteIndex = 0;
                SurfaceColumn column = findSurfaceColumn(materialResolver, x, z, preferredTopY, preferredBottomY, topY);
                if (column != null) {
                    String materialName = column.material.name();
                    Integer existing = palette.get(materialName);
                    if (existing == null) {
                        if (palette.size() >= maxPaletteEntries) {
                            existing = palette.get("OTHER");
                            if (existing == null) {
                                existing = maxPaletteEntries;
                                palette.put("OTHER", existing);
                            }
                        } else {
                            existing = palette.size() + 1;
                            palette.put(materialName, existing);
                        }
                    }

                    foundY = column.y;
                    paletteIndex = existing;
                    solidColumns++;
                }

                columns.write(foundY & 0xFF);
                columns.write(paletteIndex & 0xFF);
            }
        }

        ByteArrayOutputStream encoded = new ByteArrayOutputStream(4096);
        encoded.write(1); // format version
        encoded.write(palette.size() & 0xFF);

        for (Map.Entry<String, Integer> entry : palette.entrySet()) {
            String name = entry.getKey();
            byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
            int len = Math.min(255, nameBytes.length);
            encoded.write(len);
            encoded.write(nameBytes, 0, len);
        }

        byte[] columnBytes = columns.toByteArray();
        encoded.write(columnBytes, 0, columnBytes.length);
        return new ChunkSurfaceData(encoded.toByteArray(), solidColumns);
    }

    private SurfaceColumn findSurfaceColumn(SnapshotMaterialResolver materialResolver, int x, int z, int preferredTopY, int preferredBottomY, int absoluteTopY) {
        for (int y = preferredTopY; y >= preferredBottomY; y--) {
            Material type = materialResolver.get(x, y, z);
            if (isRenderableSurfaceMaterial(type)) {
                return new SurfaceColumn(y, type);
            }
        }

        for (int y = preferredBottomY - 1; y >= 0; y--) {
            Material type = materialResolver.get(x, y, z);
            if (isRenderableSurfaceMaterial(type)) {
                return new SurfaceColumn(y, type);
            }
        }

        for (int y = absoluteTopY; y > preferredTopY; y--) {
            Material type = materialResolver.get(x, y, z);
            if (isRenderableSurfaceMaterial(type)) {
                return new SurfaceColumn(y, type);
            }
        }

        return null;
    }

    private boolean isRenderableSurfaceMaterial(Material type) {
        if (type == null || type == Material.AIR) {
            return false;
        }

        String name = type.name();
        if (name == null || name.isEmpty()) {
            return false;
        }

        if ("CAVE_AIR".equals(name) || "VOID_AIR".equals(name)) {
            return false;
        }

        if (name.endsWith("_BUTTON")
                || name.endsWith("_TORCH")
                || name.endsWith("_WALL_TORCH")
                || name.endsWith("_PRESSURE_PLATE")
                || name.endsWith("_BANNER")
                || name.endsWith("_SIGN")
                || name.endsWith("_WALL_SIGN")
                || name.endsWith("_CARPET")
                || name.endsWith("_RAIL")
                || name.endsWith("_SAPLING")
                || name.endsWith("_FLOWER")
                || name.endsWith("_CORAL")
                || name.endsWith("_FAN")
                || name.endsWith("_CANDLE")) {
            return false;
        }

        if (name.contains("POTTED_")
                || name.contains("TALL_GRASS")
                || name.contains("SEAGRASS")
                || name.contains("FERN")
                || name.contains("VINE")
                || name.contains("LILY_PAD")
                || name.contains("DOUBLE_PLANT")
                || name.contains("MUSHROOM")
                || name.contains("AMETHYST_CLUSTER")
                || name.contains("SMALL_AMETHYST_BUD")
                || name.contains("MEDIUM_AMETHYST_BUD")
                || name.contains("LARGE_AMETHYST_BUD")) {
            return false;
        }

        return true;
    }

    private SnapshotMaterialResolver buildSnapshotMaterialResolver(final ChunkSnapshot snapshot) {
        if (snapshot == null) {
            return SnapshotMaterialResolver.EMPTY;
        }

        final Class<?> snapshotClass = snapshot.getClass();
        try {
            final Method getBlockTypeMethod = snapshotClass.getMethod("getBlockType", int.class, int.class, int.class);
            return new SnapshotMaterialResolver() {
                @Override
                public Material get(int x, int y, int z) {
                    try {
                        Object value = getBlockTypeMethod.invoke(snapshot, x, y, z);
                        return value instanceof Material ? (Material) value : null;
                    } catch (Throwable ignored) {
                        return null;
                    }
                }
            };
        } catch (Throwable ignored) {
        }

        try {
            final Method getBlockDataMethod = snapshotClass.getMethod("getBlockData", int.class, int.class, int.class);
            return new SnapshotMaterialResolver() {
                private Method getMaterialMethod;

                @Override
                public Material get(int x, int y, int z) {
                    try {
                        Object blockData = getBlockDataMethod.invoke(snapshot, x, y, z);
                        if (blockData == null) {
                            return null;
                        }
                        if (getMaterialMethod == null) {
                            getMaterialMethod = blockData.getClass().getMethod("getMaterial");
                        }
                        Object material = getMaterialMethod.invoke(blockData);
                        return material instanceof Material ? (Material) material : null;
                    } catch (Throwable ignored) {
                        return null;
                    }
                }
            };
        } catch (Throwable ignored) {
        }

        try {
            final Method getBlockTypeIdMethod = snapshotClass.getMethod("getBlockTypeId", int.class, int.class, int.class);
            final Method materialByIdMethod = resolveMaterialByIdMethod();
            return new SnapshotMaterialResolver() {
                @Override
                public Material get(int x, int y, int z) {
                    try {
                        Object rawId = getBlockTypeIdMethod.invoke(snapshot, x, y, z);
                        int id = rawId instanceof Number ? ((Number) rawId).intValue() : 0;
                        if (id <= 0) {
                            return Material.AIR;
                        }
                        if (materialByIdMethod != null) {
                            Object value = materialByIdMethod.invoke(null, id);
                            if (value instanceof Material) {
                                return (Material) value;
                            }
                        }
                        return Material.STONE;
                    } catch (Throwable ignored) {
                        return null;
                    }
                }
            };
        } catch (Throwable ignored) {
        }

        return SnapshotMaterialResolver.EMPTY;
    }

    private static Method resolveMaterialByIdMethod() {
        try {
            return Material.class.getMethod("getMaterial", int.class);
        } catch (Throwable ignored) {
        }
        try {
            return Material.class.getMethod("getById", int.class);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private byte[] gzip(byte[] input) {
        if (input == null || input.length == 0) {
            return input;
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(input.length);
             GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(input);
            gzip.finish();
            return output.toByteArray();
        } catch (IOException ignored) {
            return input;
        }
    }

    private static JsonObject buildPositionPayload(Location loc) {
        JsonObject payload = new JsonObject();
        payload.addProperty("world", loc.getWorld().getName());
        payload.addProperty("x", loc.getX());
        payload.addProperty("y", loc.getY());
        payload.addProperty("z", loc.getZ());
        payload.addProperty("yaw", loc.getYaw());
        payload.addProperty("pitch", loc.getPitch());
        return payload;
    }

    private static void appendMovementState(JsonObject payload, Player player) {
        if (payload == null || player == null) {
            return;
        }
        payload.addProperty("sneaking", player.isSneaking());
        payload.addProperty("sprinting", player.isSprinting());
        Boolean onGround = resolveOnGround(player);
        if (onGround != null) {
            payload.addProperty("onGround", onGround);
        }
    }

    private static Boolean resolveOnGround(Player player) {
        if (player == null) {
            return null;
        }
        try {
            Method method = player.getClass().getMethod("isOnGround");
            Object value = method.invoke(player);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static void appendHealth(JsonObject payload, Player player) {
        payload.addProperty("health", getHealth(player));
        payload.addProperty("maxHealth", player.getMaxHealth());
        payload.addProperty("absorption", getAbsorption(player));
    }

    private static double getHealth(Player player) {
        try {
            return Math.max(0.0, Math.min(player.getMaxHealth(), player.getHealth()));
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static double getAbsorption(Player player) {
        if (player == null) {
            return 0.0;
        }
        try {
            Object result = player.getClass().getMethod("getAbsorptionAmount").invoke(player);
            if (result instanceof Number) {
                return ((Number) result).doubleValue();
            }
        } catch (Throwable ignored) {
        }
        return 0.0;
    }

    private static JsonObject buildBlockPayload(Location loc) {
        JsonObject payload = new JsonObject();
        payload.addProperty("world", loc.getWorld().getName());
        payload.addProperty("x", loc.getBlockX());
        payload.addProperty("y", loc.getBlockY());
        payload.addProperty("z", loc.getBlockZ());
        return payload;
    }

    private Map<String, String> captureEquipmentData(PlayerInventory inventory) {
        Map<String, String> equipment = new LinkedHashMap<>();
        equipment.put("HAND", BukkitUtils.serializeItemStack(inventory.getItemInHand()));
        ItemStack offHand = getOffHandItem(inventory);
        equipment.put("OFFHAND", BukkitUtils.serializeItemStack(offHand));
        equipment.put("HELMET", BukkitUtils.serializeItemStack(inventory.getHelmet()));
        equipment.put("CHESTPLATE", BukkitUtils.serializeItemStack(inventory.getChestplate()));
        equipment.put("LEGGINGS", BukkitUtils.serializeItemStack(inventory.getLeggings()));
        equipment.put("BOOTS", BukkitUtils.serializeItemStack(inventory.getBoots()));
        return equipment;
    }

    private ItemStack getOffHandItem(PlayerInventory inventory) {
        try {
            Method method = PlayerInventory.class.getMethod("getItemInOffHand");
            Object result = method.invoke(inventory);
            return result instanceof ItemStack ? (ItemStack) result : null;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Entity extractHitEntity(ProjectileHitEvent event) {
        try {
            Object result = ProjectileHitEvent.class.getMethod("getHitEntity").invoke(event);
            if (result instanceof Entity) {
                return (Entity) result;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Block extractHitBlock(ProjectileHitEvent event) {
        try {
            Object result = ProjectileHitEvent.class.getMethod("getHitBlock").invoke(event);
            if (result instanceof Block) {
                return (Block) result;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static final class SessionContext {
        final int reportId;
        final int sessionId;
        final long startedAt;
        final Map<UUID, ActorState> actors = new ConcurrentHashMap<>();
        final Set<String> capturedChunkKeys = ConcurrentHashMap.newKeySet();
        int tick = 0;

        SessionContext(int reportId, int sessionId, long startedAt) {
            this.reportId = reportId;
            this.sessionId = sessionId;
            this.startedAt = startedAt;
        }

        boolean expired() {
            return System.currentTimeMillis() - startedAt > 1000L * 60 * 60 * 2;
        }
    }

    private static final class ActorState {
        volatile int actorId;
        final UUID uuid;
        final String name;
        Location lastLocation;
        float lastYaw;
        float lastPitch;
        double lastHealth = -1.0;
        boolean lastSneaking;
        boolean lastSprinting;
        Boolean lastOnGround;
        double lastVelocityX;
        double lastVelocityY;
        double lastVelocityZ;
        int lastVelocityTick = -1;
        final Map<String, String> lastEquipment = new ConcurrentHashMap<>();
        String lastChunkCaptureWorld = "";
        int lastChunkCaptureX = Integer.MIN_VALUE;
        int lastChunkCaptureZ = Integer.MIN_VALUE;
        int lastChunkRefreshTick = -1;

        ActorState(UUID uuid, String name) {
            this.actorId = -1;
            this.uuid = uuid;
            this.name = name;
        }
    }

    private static final class SessionBinding {
        final SessionContext context;
        final int actorId;
        final int sessionId;

        SessionBinding(SessionContext context, int actorId) {
            this.context = context;
            this.actorId = actorId;
            this.sessionId = context.sessionId;
        }
    }

    private static final class ProjectileContext {
        final SessionContext context;
        final int actorId;
        final String type;

        ProjectileContext(SessionContext context, int actorId, String type) {
            this.context = context;
            this.actorId = actorId;
            this.type = type;
        }
    }

    private interface SnapshotMaterialResolver {
        SnapshotMaterialResolver EMPTY = new SnapshotMaterialResolver() {
            @Override
            public Material get(int x, int y, int z) {
                return null;
            }
        };

        Material get(int x, int y, int z);
    }

    private static final class ChunkSurfaceData {
        final byte[] bytes;
        final int solidColumns;

        private ChunkSurfaceData(byte[] bytes, int solidColumns) {
            this.bytes = bytes;
            this.solidColumns = solidColumns;
        }
    }

    private static final class SurfaceColumn {
        final int y;
        final Material material;

        private SurfaceColumn(int y, Material material) {
            this.y = y;
            this.material = material;
        }
    }
}
