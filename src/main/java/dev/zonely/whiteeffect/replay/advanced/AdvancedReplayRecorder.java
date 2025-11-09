package dev.zonely.whiteeffect.replay.advanced;

import com.google.gson.JsonObject;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.report.Report;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;


public final class AdvancedReplayRecorder {

    private static final int SAMPLE_EVERY_TICKS = 2; 
    private static final double MIN_MOVE_DISTANCE_SQ = 0.0004;
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

        final JsonObject joinPayload = buildPositionPayload(location);
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
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "quit");
        storage.recordActionAsync(ReplayActionRecord.of(ctx.sessionId, state.actorId, ctx.tick, ReplayActionType.STATE_CHANGE, payload));
    }

    public void recordHeldItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        SessionBinding binding = findSession(player.getUniqueId());
        if (binding == null) return;
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item == null) {
            item = new ItemStack(Material.AIR);
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("slot", "HAND");
        payload.addProperty("newSlot", event.getNewSlot());
        payload.addProperty("item", BukkitUtils.serializeItemStack(item));
        storage.recordActionAsync(ReplayActionRecord.of(binding.sessionId, binding.actorId, binding.context.tick, ReplayActionType.ITEM_HOLD, payload));
    }

    public void recordAnimation(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        SessionBinding binding = findSession(player.getUniqueId());
        if (binding == null) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("animation", event.getAnimationType().name());
        storage.recordActionAsync(ReplayActionRecord.of(binding.sessionId, binding.actorId, binding.context.tick, ReplayActionType.ANIMATION, payload));
    }

    public void recordBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        SessionBinding binding = findSession(player.getUniqueId());
        if (binding == null) return;
        Block block = event.getBlock();
        JsonObject payload = buildBlockPayload(block.getLocation());
        payload.addProperty("material", block.getType().name());
        payload.addProperty("data", block.getData() & 0xFF);
        payload.addProperty("held", BukkitUtils.serializeItemStack(event.getItemInHand()));
        storage.recordActionAsync(ReplayActionRecord.of(binding.sessionId, binding.actorId, binding.context.tick, ReplayActionType.BLOCK_PLACE, payload));
    }

    public void recordBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        SessionBinding binding = findSession(player.getUniqueId());
        if (binding == null) return;
        Block block = event.getBlock();
        JsonObject payload = buildBlockPayload(block.getLocation());
        payload.addProperty("material", block.getType().name());
        payload.addProperty("data", block.getData() & 0xFF);
        storage.recordActionAsync(ReplayActionRecord.of(binding.sessionId, binding.actorId, binding.context.tick, ReplayActionType.BLOCK_BREAK, payload));
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
        storage.recordActionAsync(ReplayActionRecord.of(binding.sessionId, actorId, ctx.tick, ReplayActionType.DAMAGE, payload));
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
        JsonObject payload = buildPositionPayload(projectile.getLocation());
        payload.addProperty("uuid", projectile.getUniqueId().toString());
        payload.addProperty("projectileType", projectile.getType().name());
        Vector velocity = projectile.getVelocity();
        payload.addProperty("velX", velocity.getX());
        payload.addProperty("velY", velocity.getY());
        payload.addProperty("velZ", velocity.getZ());
        storage.recordActionAsync(ReplayActionRecord.of(binding.sessionId, binding.actorId, binding.context.tick, ReplayActionType.PROJECTILE_LAUNCH, payload));
        projectiles.put(projectile.getUniqueId(), new ProjectileContext(binding.context, binding.actorId, projectile.getType().name()));
    }

    public void recordProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileContext ctx = projectiles.remove(projectile.getUniqueId());
        if (ctx == null) {
            return;
        }
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
        storage.recordActionAsync(ReplayActionRecord.of(ctx.context.sessionId, ctx.actorId, ctx.context.tick, ReplayActionType.PROJECTILE_HIT, payload));
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

    private void sampleMovement(SessionContext ctx) {
        ctx.tick += SAMPLE_EVERY_TICKS;
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
            boolean moved = state.lastLocation == null || loc.distanceSquared(state.lastLocation) > MIN_MOVE_DISTANCE_SQ;
            boolean rotated = state.lastLocation == null
                    || Math.abs(loc.getYaw() - state.lastYaw) > MIN_ROTATION_DELTA
                    || Math.abs(loc.getPitch() - state.lastPitch) > MIN_ROTATION_DELTA;
            if (moved) {
                JsonObject payload = buildPositionPayload(loc);
                storage.recordActionAsync(ReplayActionRecord.of(ctx.sessionId, state.actorId, ctx.tick, ReplayActionType.MOVE, payload));
                state.lastLocation = loc.clone();
                state.lastYaw = loc.getYaw();
                state.lastPitch = loc.getPitch();
            } else if (rotated) {
                JsonObject payload = new JsonObject();
                payload.addProperty("yaw", loc.getYaw());
                payload.addProperty("pitch", loc.getPitch());
                storage.recordActionAsync(ReplayActionRecord.of(ctx.sessionId, state.actorId, ctx.tick, ReplayActionType.LOOK, payload));
                state.lastYaw = loc.getYaw();
                state.lastPitch = loc.getPitch();
            }
            Vector velocity = player.getVelocity();
            if (velocity.lengthSquared() > 0.0001) {
                JsonObject payload = new JsonObject();
                payload.addProperty("x", velocity.getX());
                payload.addProperty("y", velocity.getY());
                payload.addProperty("z", velocity.getZ());
                storage.recordActionAsync(ReplayActionRecord.of(ctx.sessionId, state.actorId, ctx.tick, ReplayActionType.VELOCITY, payload));
            }
            double health = getHealth(player);
            if (Math.abs(health - state.lastHealth) > 0.05) {
                JsonObject payload = new JsonObject();
                payload.addProperty("health", health);
                payload.addProperty("maxHealth", player.getMaxHealth());
                payload.addProperty("absorption", getAbsorption(player));
                storage.recordActionAsync(ReplayActionRecord.of(ctx.sessionId, state.actorId, ctx.tick, ReplayActionType.STATE_CHANGE, payload));
                state.lastHealth = health;
            }
        }
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
}
