package dev.zonely.whiteeffect.auth.bossbar;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.libraries.MinecraftVersion;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.StringUtils;
import dev.zonely.whiteeffect.nms.NMS;
import net.minecraft.server.v1_8_R3.EntityWither;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityTeleport;
import net.minecraft.server.v1_8_R3.PacketPlayOutSpawnEntityLiving;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthBossBarManager {

    private final Core plugin;
    private final Map<UUID, BossBarHandle> active = new ConcurrentHashMap<>();
    private final boolean legacyMode;
    private final ModernSupport modernSupport;

    public AuthBossBarManager(Core plugin) {
        this.plugin = plugin;
        this.legacyMode = MinecraftVersion.getCurrentVersion().getMinor() <= 8;
        this.modernSupport = legacyMode ? null : ModernSupport.tryCreate();
    }

    public void showLogin(Player player) {
        show(player, getMessage(player, "auth.bossbar.login", "&cPlease login with /login <password>"));
    }

    public void showRegister(Player player) {
        show(player, getMessage(player, "auth.bossbar.register", "&cRegister with /register <password> <confirm>"));
    }

    public void showTwoFactor(Player player) {
        show(player, getMessage(player, "auth.bossbar.two-factor", "&eEnter your 2FA code with /2fa code <123456>"));
    }

    public void showEmailMissing(Player player) {
        show(player, getMessage(player, "auth.bossbar.email-missing", "&cSet an email with /email set <address>"));
    }

    public void showEmailPending(Player player) {
        show(player, getMessage(player, "auth.bossbar.email-pending", "&eVerify your email or use /email resend"));
    }

    public void show(Player player, String message) {
        if (player == null) {
            return;
        }
        if (!player.isOnline()) {
            hide(player);
            return;
        }
        String formatted = formatMessage(message);
        if (formatted == null || formatted.isEmpty()) {
            hide(player);
            return;
        }
        BossBarHandle handle = active.computeIfAbsent(player.getUniqueId(), uuid -> createHandle(player));
        handle.show(formatted);
    }

    public void hide(Player player) {
        if (player == null) {
            return;
        }
        BossBarHandle handle = active.remove(player.getUniqueId());
        if (handle != null) {
            handle.hide();
        }
    }

    public void clearAll() {
        for (BossBarHandle handle : active.values()) {
            handle.hide();
        }
        active.clear();
    }

    private BossBarHandle createHandle(Player player) {
        if (!legacyMode) {
            if (modernSupport != null && modernSupport.isAvailable()) {
                return new ModernBossBarHandle(modernSupport, player);
            }
            return new NoopBossBarHandle();
        }
        return new LegacyBossBarHandle(plugin, player);
    }

    private String getMessage(Player player, String key, String def) {
        Profile profile = Profile.getProfile(player.getName());
        if (profile != null) {
            return LanguageManager.get(profile, key, def);
        }
        return LanguageManager.get(key, def);
    }

    private String formatMessage(String input) {
        if (input == null) {
            return null;
        }
        return StringUtils.formatColors(input.trim());
    }

    private interface BossBarHandle {
        void show(String message);
        void hide();
    }

    private static final class NoopBossBarHandle implements BossBarHandle {
        @Override
        public void show(String message) {
        }

        @Override
        public void hide() {
        }
    }

    private static final class ModernSupport {
        private final Method createBossBar;
        private final Method addPlayer;
        private final Method removePlayer;
        private final Method setTitle;
        private final Method setProgress;
        private final Method setVisible;
        private final Object color;
        private final Object style;
        private final boolean requiresFlags;
        private final Object emptyFlagsArray;

        private ModernSupport(Method createBossBar,
                              Method addPlayer,
                              Method removePlayer,
                              Method setTitle,
                              Method setProgress,
                              Method setVisible,
                              Object color,
                              Object style,
                              boolean requiresFlags,
                              Object emptyFlagsArray) {
            this.createBossBar = createBossBar;
            this.addPlayer = addPlayer;
            this.removePlayer = removePlayer;
            this.setTitle = setTitle;
            this.setProgress = setProgress;
            this.setVisible = setVisible;
            this.color = color;
            this.style = style;
            this.requiresFlags = requiresFlags;
            this.emptyFlagsArray = emptyFlagsArray;
        }

        static ModernSupport tryCreate() {
            try {
                Class<?> bossBarClass = Class.forName("org.bukkit.boss.BossBar");
                Class<?> barColor = Class.forName("org.bukkit.boss.BarColor");
                Class<?> barStyle = Class.forName("org.bukkit.boss.BarStyle");
                Method create;
                boolean requiresFlags = false;
                Object emptyFlagsArray = null;
                try {
                    create = Bukkit.class.getMethod("createBossBar", String.class, barColor, barStyle);
                } catch (NoSuchMethodException ignored) {
                    Class<?> barFlag = Class.forName("org.bukkit.boss.BarFlag");
                    Class<?> flagArrayClass = java.lang.reflect.Array.newInstance(barFlag, 0).getClass();
                    create = Bukkit.class.getMethod("createBossBar", String.class, barColor, barStyle, flagArrayClass);
                    requiresFlags = true;
                    emptyFlagsArray = java.lang.reflect.Array.newInstance(barFlag, 0);
                }
                Method add = bossBarClass.getMethod("addPlayer", Player.class);
                Method remove = bossBarClass.getMethod("removePlayer", Player.class);
                Method setTitle = bossBarClass.getMethod("setTitle", String.class);
                Method setProgress = bossBarClass.getMethod("setProgress", double.class);
                Method setVisible = bossBarClass.getMethod("setVisible", boolean.class);
                @SuppressWarnings("unchecked")
                Object color = Enum.valueOf((Class<Enum>) barColor, "PURPLE");
                @SuppressWarnings("unchecked")
                Object style = Enum.valueOf((Class<Enum>) barStyle, "SOLID");
                return new ModernSupport(create, add, remove, setTitle, setProgress, setVisible, color, style, requiresFlags, emptyFlagsArray);
            } catch (Throwable ignored) {
                return null;
            }
        }

        boolean isAvailable() {
            return createBossBar != null && addPlayer != null && removePlayer != null && setTitle != null;
        }
    }

    private static final class ModernBossBarHandle implements BossBarHandle {
        private final ModernSupport support;
        private final Player player;
        private Object bossBar;

        ModernBossBarHandle(ModernSupport support, Player player) {
            this.support = support;
            this.player = player;
        }

        @Override
        public void show(String message) {
            try {
                if (bossBar == null) {
                    if (support.requiresFlags) {
                        bossBar = support.createBossBar.invoke(null, message, support.color, support.style, support.emptyFlagsArray);
                    } else {
                        bossBar = support.createBossBar.invoke(null, message, support.color, support.style);
                    }
                    support.addPlayer.invoke(bossBar, player);
                    support.setVisible.invoke(bossBar, true);
                } else {
                    support.setTitle.invoke(bossBar, message);
                }
                support.setProgress.invoke(bossBar, 1.0D);
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void hide() {
            if (bossBar == null) {
                return;
            }
            try {
                support.setVisible.invoke(bossBar, false);
                support.removePlayer.invoke(bossBar, player);
            } catch (Throwable ignored) {
            }
            bossBar = null;
        }
    }

    private static final class LegacyBossBarHandle implements BossBarHandle, Runnable {
        private static final float MAX_HEALTH = 300.0F;
        private static final double DISTANCE = 32.0D;

        private final Core plugin;
        private final Player player;
        private EntityWither wither;
        private BukkitTask task;
        private String currentMessage;

        LegacyBossBarHandle(Core plugin, Player player) {
            this.plugin = plugin;
            this.player = player;
        }

        @Override
        public void show(String message) {
            currentMessage = trimMessage(message);
            if (wither == null) {
                spawnBossBar();
            } else {
                updateMetadata();
            }
            ensureTask();
        }

        @Override
        public void hide() {
            cancelTask();
            if (wither != null) {
                PacketPlayOutEntityDestroy destroy = new PacketPlayOutEntityDestroy(wither.getId());
                NMS.sendPacket(player, destroy);
                wither = null;
            }
        }

        @Override
        public void run() {
            if (!player.isOnline() || wither == null) {
                hide();
                return;
            }
            updateLocation();
        }

        private void spawnBossBar() {
            try {
                Location location = calculateLocation();
                World world = player.getWorld();
                WorldServer handle = ((CraftWorld) world).getHandle();
                wither = new EntityWither(handle);
                wither.setLocation(location.getX(), location.getY(), location.getZ(), 0.0F, 0.0F);
                wither.setInvisible(true);
                wither.setCustomName(currentMessage);
                wither.setCustomNameVisible(true);
                wither.setHealth(MAX_HEALTH);

                PacketPlayOutSpawnEntityLiving spawnPacket = new PacketPlayOutSpawnEntityLiving(wither);
                NMS.sendPacket(player, spawnPacket);
                updateMetadata();
            } catch (Throwable ignored) {
                wither = null;
            }
        }

        private void updateMetadata() {
            if (wither == null) {
                return;
            }
            wither.setCustomName(currentMessage);
            wither.setCustomNameVisible(true);
            PacketPlayOutEntityMetadata metadata = new PacketPlayOutEntityMetadata(
                    wither.getId(),
                    wither.getDataWatcher(),
                    true
            );
            NMS.sendPacket(player, metadata);
        }

        private void updateLocation() {
            if (wither == null) {
                return;
            }
            Location location = calculateLocation();
            wither.setLocation(location.getX(), location.getY(), location.getZ(), 0.0F, 0.0F);
            PacketPlayOutEntityTeleport teleport = new PacketPlayOutEntityTeleport(wither);
            NMS.sendPacket(player, teleport);
        }

        private Location calculateLocation() {
            Location eye = player.getEyeLocation();
            return eye.add(eye.getDirection().normalize().multiply(DISTANCE));
        }

        private void ensureTask() {
            BukkitTask current = this.task;
            if (current != null) {
                int taskId = current.getTaskId();
                if (Bukkit.getScheduler().isCurrentlyRunning(taskId) || Bukkit.getScheduler().isQueued(taskId)) {
                    return;
                }
            }
            this.task = Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, 20L);
        }

        private void cancelTask() {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }

        private String trimMessage(String message) {
            if (message == null) {
                return " ";
            }
            String cleaned = message.length() > 64 ? message.substring(0, 64) : message;
            if (cleaned.trim().isEmpty()) {
                return " ";
            }
            return cleaned;
        }
    }
}
