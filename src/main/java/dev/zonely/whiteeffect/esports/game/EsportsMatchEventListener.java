package dev.zonely.whiteeffect.esports.game;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

public final class EsportsMatchEventListener implements Listener {

    private final EsportsGameService gameService;

    public EsportsMatchEventListener(EsportsGameService gameService) {
        this.gameService = gameService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!gameService.hasActiveMatchContext()) {
            return;
        }
        Player victim = event.getEntity();
        if (!isTracked(victim.getUniqueId())) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("victim", victim.getName());
        payload.addProperty("victim_uuid", victim.getUniqueId().toString());
        payload.addProperty("victimClub", clubTag(victim.getUniqueId()));
        payload.add("location", serializeLocation(victim.getLocation()));
        payload.add("victim_location", serializeLocation(victim.getLocation()));
        payload.addProperty("victim_health", 0.0D);
        payload.addProperty("target_health", 0.0D);
        payload.addProperty("victim_max_health", round(victim.getMaxHealth(), 2));
        payload.addProperty("target_max_health", round(victim.getMaxHealth(), 2));
        addEquipmentPayload(payload, "victim_", victim);
        gameService.incrementDeath(victim.getUniqueId());
        if (event.getEntity().getKiller() != null) {
            Player killer = event.getEntity().getKiller();
            payload.addProperty("killer", killer.getName());
            payload.addProperty("killer_uuid", killer.getUniqueId().toString());
            payload.addProperty("killerClub", clubTag(killer.getUniqueId()));
            ItemStack weapon = killer.getInventory().getItemInHand();
            if (weapon != null) {
                payload.addProperty("weapon", weapon.getType().name());
            }
            payload.add("attacker_location", serializeLocation(killer.getLocation()));
            addEquipmentPayload(payload, "", killer);
            if (isTracked(killer.getUniqueId())) {
                gameService.incrementKill(killer.getUniqueId(), clubTag(killer.getUniqueId()));
            }
        }
        payload.addProperty("cause", event.getEntity().getLastDamageCause() != null
                ? event.getEntity().getLastDamageCause().getCause().name()
                : "UNKNOWN");
        gameService.logMatchEvent("player_death", payload);

        gameService.handleParticipantDeath(victim);
        Location spect = gameService.getSpectatorLocationSnapshot();
        if (spect != null) {
            Bukkit.getScheduler().runTask(gameService.getPlugin(), () -> victim.teleport(spect));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!gameService.hasActiveMatchContext()) {
            return;
        }
        Player player = event.getPlayer();
        if (!isTracked(player.getUniqueId())) {
            return;
        }
        Block block = event.getBlock();
        JsonObject payload = new JsonObject();
        payload.addProperty("player", player.getName());
        payload.addProperty("player_uuid", player.getUniqueId().toString());
        payload.addProperty("club", clubTag(player.getUniqueId()));
        payload.addProperty("block", block.getType().name());
        payload.add("location", serializeLocation(block.getLocation()));
        payload.add("player_location", serializeLocation(player.getLocation()));
        ItemStack held = player.getInventory().getItemInHand();
        if (held != null) {
            payload.addProperty("held_item", held.getType().name());
        }
        addEquipmentPayload(payload, "", player);
        gameService.incrementBlockBreak(player.getUniqueId());
        gameService.logMatchEvent("block_break", payload);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!gameService.hasActiveMatchContext()) {
            return;
        }
        Player player = event.getPlayer();
        if (!isTracked(player.getUniqueId())) {
            return;
        }
        Block block = event.getBlockPlaced();
        JsonObject payload = new JsonObject();
        payload.addProperty("player", player.getName());
        payload.addProperty("player_uuid", player.getUniqueId().toString());
        payload.addProperty("club", clubTag(player.getUniqueId()));
        payload.addProperty("block", block.getType().name());
        payload.add("location", serializeLocation(block.getLocation()));
        payload.add("player_location", serializeLocation(player.getLocation()));
        ItemStack held = player.getInventory().getItemInHand();
        if (held != null) {
            payload.addProperty("held_item", held.getType().name());
        }
        addEquipmentPayload(payload, "", player);
        gameService.incrementBlockPlace(player.getUniqueId());
        gameService.logMatchEvent("block_place", payload);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!gameService.hasActiveMatchContext()) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        if (!isTracked(victim.getUniqueId())) {
            return;
        }
        JsonObject payload = createDamagePayload(victim, event.getFinalDamage(), event.getCause());
        Player attacker = resolveDamager(event.getDamager());
        if (attacker != null) {
            payload.addProperty("attacker", attacker.getName());
            payload.addProperty("attacker_uuid", attacker.getUniqueId().toString());
            payload.addProperty("attackerClub", clubTag(attacker.getUniqueId()));
            payload.add("attacker_location", serializeLocation(attacker.getLocation()));
            ItemStack weapon = attacker.getInventory().getItemInHand();
            if (weapon != null) {
                payload.addProperty("weapon", weapon.getType().name());
            }
            payload.addProperty("critical", attacker.getFallDistance() > 0.0F && !attacker.isOnGround());
            addEquipmentPayload(payload, "", attacker);
            addEquipmentPayload(payload, "victim_", victim);
        } else {
            payload.addProperty("source", event.getDamager().getType().name());
        }
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            payload.addProperty("projectile", projectile.getType().name());
        }
        gameService.logMatchEvent("player_damage", payload);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnvironmentDamage(EntityDamageEvent event) {
        if (!gameService.hasActiveMatchContext()) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        if (!isTracked(victim.getUniqueId())) {
            return;
        }
        JsonObject payload = createDamagePayload(victim, event.getFinalDamage(), event.getCause());
        gameService.logMatchEvent("environment_damage", payload);
    }

    private boolean isTracked(UUID uniqueId) {
        return uniqueId != null && gameService.isParticipant(uniqueId);
    }

    private String clubTag(UUID uniqueId) {
        return gameService.getParticipantClubTag(uniqueId).orElse("");
    }

    private Player resolveDamager(Entity source) {
        if (source instanceof Player) {
            return (Player) source;
        }
        if (source instanceof Projectile) {
            Projectile projectile = (Projectile) source;
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }

    private JsonObject createDamagePayload(Player victim, double damage, EntityDamageEvent.DamageCause cause) {
        JsonObject payload = new JsonObject();
        payload.addProperty("victim", victim.getName());
        payload.addProperty("victim_uuid", victim.getUniqueId().toString());
        payload.addProperty("victimClub", clubTag(victim.getUniqueId()));
        payload.addProperty("damage", round(damage, 2));
        double remaining = Math.max(0.0D, victim.getHealth() - damage);
        payload.addProperty("healthAfter", round(remaining, 2));
        payload.addProperty("victim_health", round(remaining, 2));
        payload.addProperty("target_health", round(remaining, 2));
        payload.addProperty("victim_max_health", round(victim.getMaxHealth(), 2));
        payload.addProperty("target_max_health", round(victim.getMaxHealth(), 2));
        payload.addProperty("cause", cause != null ? cause.name() : "UNKNOWN");
        payload.add("location", serializeLocation(victim.getLocation()));
        payload.add("victim_location", serializeLocation(victim.getLocation()));
        return payload;
    }

    private double round(double value, int precision) {
        double factor = Math.pow(10, precision);
        return Math.round(value * factor) / factor;
    }

    private JsonObject serializeLocation(Location location) {
        JsonObject node = new JsonObject();
        if (location == null) {
            return node;
        }
        node.addProperty("world", location.getWorld() != null ? location.getWorld().getName() : "unknown");
        node.addProperty("x", location.getX());
        node.addProperty("y", location.getY());
        node.addProperty("z", location.getZ());
        node.addProperty("yaw", round(location.getYaw(), 2));
        node.addProperty("pitch", round(location.getPitch(), 2));
        return node;
    }

    private void addEquipmentPayload(JsonObject payload, String prefix, Player player) {
        if (payload == null || player == null) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInHand();
        if (hand != null) {
            payload.addProperty(prefix + "held_item", hand.getType().name());
            if (!payload.has(prefix + "weapon")) {
                payload.addProperty(prefix + "weapon", hand.getType().name());
            }
        }
        if (player.getInventory().getHelmet() != null) {
            payload.addProperty(prefix + "helmet", player.getInventory().getHelmet().getType().name());
        }
        if (player.getInventory().getChestplate() != null) {
            payload.addProperty(prefix + "chestplate", player.getInventory().getChestplate().getType().name());
        }
        if (player.getInventory().getLeggings() != null) {
            payload.addProperty(prefix + "leggings", player.getInventory().getLeggings().getType().name());
        }
        if (player.getInventory().getBoots() != null) {
            payload.addProperty(prefix + "boots", player.getInventory().getBoots().getType().name());
        }
    }
}
