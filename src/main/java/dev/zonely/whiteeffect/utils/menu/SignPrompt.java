package dev.zonely.whiteeffect.utils.menu;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class SignPrompt implements Listener {

    private static final Map<UUID, PromptState> ACTIVE = new ConcurrentHashMap<>();
    private static final Material SIGN_MATERIAL;
    private static final Method OPEN_SIGN_METHOD;
    private static final Method SIGN_SET_EDITABLE_METHOD;
    private static final Method SIGN_SET_EDITOR_METHOD;
    private static final Method SIGN_SET_ALLOWED_EDITOR_METHOD;
    private static final Method SIGN_SET_WAXED_METHOD;
    private static boolean registered;

    static {
        Material sign = MaterialResolver.resolveMaterial("OAK_SIGN");
        if (sign == null) {
            sign = MaterialResolver.resolveMaterial("SIGN_POST");
        }
        SIGN_MATERIAL = sign;
        Method method = null;
        try {
            method = Player.class.getMethod("openSign", Sign.class);
        } catch (NoSuchMethodException ignored) {
        }
        OPEN_SIGN_METHOD = method;
        SIGN_SET_EDITABLE_METHOD = resolveMethod(Sign.class, "setEditable", boolean.class);
        SIGN_SET_EDITOR_METHOD = resolveMethod(Sign.class, "setEditor", UUID.class);
        SIGN_SET_ALLOWED_EDITOR_METHOD = resolveMethod(Sign.class, "setAllowedPlayerEditor", UUID.class);
        SIGN_SET_WAXED_METHOD = resolveMethod(Sign.class, "setWaxed", boolean.class);
    }

    private SignPrompt() {
    }

    public static boolean prompt(Player player,
                                 String[] presetLines,
                                 Consumer<String[]> completion,
                                 Runnable cancel) {
        if (player == null || SIGN_MATERIAL == null || OPEN_SIGN_METHOD == null) {
            return false;
        }
        ensureRegistered();

        Block target = findTargetBlock(player);
        if (target == null) {
            return false;
        }
        BlockState original = target.getState();
        target.setType(SIGN_MATERIAL, false);
        BlockState updated = target.getState();
        if (!(updated instanceof Sign)) {
            original.update(true, false);
            return false;
        }
        Sign sign = (Sign) updated;
        String[] lines = presetLines == null ? new String[0] : presetLines;
        for (int i = 0; i < 4; i++) {
            String value = i < lines.length ? lines[i] : "";
            sign.setLine(i, ChatColor.translateAlternateColorCodes('&', value == null ? "" : value));
        }
        sign.update(true, false);
        prepareSignForEditing(sign, player);

        PromptState state = new PromptState(player.getUniqueId(), target.getLocation(), original, completion, cancel);
        ACTIVE.put(player.getUniqueId(), state);
        try {
            OPEN_SIGN_METHOD.invoke(player, sign);
        } catch (Exception ex) {
            ACTIVE.remove(player.getUniqueId());
            state.restore();
            return false;
        }
        state.scheduleTimeout();
        return true;
    }

    private static void ensureRegistered() {
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(new SignPrompt(), Core.getInstance());
            registered = true;
        }
    }

    private static Block findTargetBlock(Player player) {
        Location base = player.getLocation();
        Block[] candidates = new Block[]{
                base.getBlock(),
                base.clone().add(0, 1, 0).getBlock(),
                base.clone().add(0, 2, 0).getBlock()
        };
        for (Block candidate : candidates) {
            if (isAir(candidate.getType())) {
                return candidate;
            }
        }
        return candidates[0];
    }

    private static void prepareSignForEditing(Sign sign, Player player) {
        if (sign == null) {
            return;
        }
        invokeVoid(sign, SIGN_SET_WAXED_METHOD, false);
        invokeVoid(sign, SIGN_SET_EDITABLE_METHOD, true);
        if (player != null) {
            UUID editor = player.getUniqueId();
            // Try both legacy and modern editor setters
            boolean applied = invokeBooleanResult(sign, SIGN_SET_ALLOWED_EDITOR_METHOD, editor);
            if (!applied) {
                invokeVoid(sign, SIGN_SET_EDITOR_METHOD, editor);
            }
        }
        sign.update(true, false);
    }

    private static Method resolveMethod(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static void invokeVoid(Object target, Method method, Object... args) {
        if (target == null || method == null) {
            return;
        }
        try {
            method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
        }
    }

    private static boolean invokeBooleanResult(Object target, Method method, Object... args) {
        if (target == null || method == null) {
            return false;
        }
        try {
            Object result = method.invoke(target, args);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            return true;
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return false;
        }
    }

    private static boolean isAir(Material material) {
        if (material == null) {
            return true;
        }
        String name = material.name();
        return name.endsWith("AIR");
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        PromptState state = ACTIVE.remove(event.getPlayer().getUniqueId());
        if (state == null) {
            return;
        }
        if (!state.matches(event.getBlock().getLocation())) {
            ACTIVE.put(event.getPlayer().getUniqueId(), state);
            return;
        }
        event.setCancelled(true);
        state.finish(event.getLines());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PromptState state = ACTIVE.remove(event.getPlayer().getUniqueId());
        if (state != null) {
            state.cancel();
        }
    }

    private static final class PromptState {
        private final UUID playerId;
        private final Location location;
        private final BlockState original;
        private final Consumer<String[]> completion;
        private final Runnable cancel;
        private BukkitRunnable timeout;
        private boolean completed;

        PromptState(UUID playerId,
                    Location location,
                    BlockState original,
                    Consumer<String[]> completion,
                    Runnable cancel) {
            this.playerId = playerId;
            this.location = location.clone();
            this.original = original;
            this.completion = completion;
            this.cancel = cancel;
        }

        boolean matches(Location other) {
            return other != null
                    && other.getWorld().equals(location.getWorld())
                    && other.getBlockX() == location.getBlockX()
                    && other.getBlockY() == location.getBlockY()
                    && other.getBlockZ() == location.getBlockZ();
        }

        void scheduleTimeout() {
            timeout = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!completed) {
                        ACTIVE.remove(playerId);
                        cancel();
                    }
                }
            };
            timeout.runTaskLater(Core.getInstance(), 20L * 30);
        }

        void finish(String[] lines) {
            if (completed) {
                return;
            }
            completed = true;
            restore();
            if (timeout != null) {
                timeout.cancel();
            }
            String[] copy = lines == null ? new String[0] : Arrays.copyOf(lines, lines.length);
            if (completion != null) {
                completion.accept(copy);
            }
        }

        void cancel() {
            if (completed) {
                return;
            }
            completed = true;
            restore();
            if (timeout != null) {
                timeout.cancel();
            }
            if (cancel != null) {
                cancel.run();
            }
        }

        void restore() {
            Bukkit.getScheduler().runTask(Core.getInstance(), () -> original.update(true, false));
        }
    }
}
