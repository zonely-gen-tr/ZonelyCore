package dev.zonely.whiteeffect.replay;

import dev.zonely.whiteeffect.Core;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class ReplayRecorder implements Runnable {

    private static final int TICKS_PER_SECOND = 20;
    private static final int SAMPLE_EVERY_TICKS = 2;
    private static final int BUFFER_SECONDS = 120;
    private static final int BUFFER_SIZE = (TICKS_PER_SECOND / SAMPLE_EVERY_TICKS) * BUFFER_SECONDS;

    private static ReplayRecorder instance;

    public static void start(Core plugin) {
        if (instance == null) {
            instance = new ReplayRecorder(plugin);
        }
    }

    public static ReplayRecorder get() {
        return instance;
    }

    private final Core plugin;
    private final Map<String, Deque<ReplayFrame>> buffers = new ConcurrentHashMap<>();
    private int tickCounter = 0;
    private int taskId = -1;

    private ReplayRecorder(Core plugin) {
        this.plugin = plugin;
        this.taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this, 0L, SAMPLE_EVERY_TICKS);
    }

    @Override
    public void run() {
        tickCounter += SAMPLE_EVERY_TICKS;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            ReplayFrame f = new ReplayFrame(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch(), tickCounter);
            Deque<ReplayFrame> q = buffers.computeIfAbsent(p.getName().toLowerCase(), k -> new ArrayDeque<>(BUFFER_SIZE + 5));
            synchronized (q) {
                q.addLast(f);
                while (q.size() > BUFFER_SIZE) {
                    q.removeFirst();
                }
            }
        }
    }


    public List<ReplayFrame> exportRecent(String playerName, int seconds) {
        Deque<ReplayFrame> q = buffers.get(playerName.toLowerCase());
        List<ReplayFrame> result = new ArrayList<>();
        if (q == null || q.isEmpty()) return result;
        int need;
        int skip;
        int baseTick = -1;
        int idx = 0;
        synchronized (q) {
            need = Math.min(q.size(), (TICKS_PER_SECOND / SAMPLE_EVERY_TICKS) * seconds);
            skip = q.size() - need;
            for (ReplayFrame f : q) {
                if (idx++ < skip) continue;
                if (baseTick < 0) baseTick = f.tick;
                result.add(new ReplayFrame(f.world, f.x, f.y, f.z, f.yaw, f.pitch, f.tick - baseTick));
            }
        }
        return result;
    }

    public void shutdown() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        buffers.clear();
    }
}

