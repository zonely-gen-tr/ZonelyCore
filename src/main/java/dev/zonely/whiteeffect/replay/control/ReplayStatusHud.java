package dev.zonely.whiteeffect.replay.control;

import dev.zonely.whiteeffect.nms.NMS;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public final class ReplayStatusHud {

    private ReplayStatusHud() {
    }

    public static void send(Player viewer, int currentTick, int maxTick, double speed, boolean paused) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        if (maxTick <= 0) {
            maxTick = Math.max(currentTick, 1);
        }
        String current = formatTime(currentTick);
        String total = formatTime(maxTick);
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.GOLD).append(current)
                .append(ChatColor.GRAY).append(" / ")
                .append(ChatColor.GOLD).append(total)
                .append(ChatColor.GRAY).append(" | ")
                .append(ChatColor.AQUA).append(String.format("%.2fx", speed));
        if (paused) {
            sb.append(ChatColor.RED).append(" \u23F8");
        }
        sendActionBarSafe(viewer, sb.toString());
    }

    private static String formatTime(int ticks) {
        if (ticks < 0) ticks = 0;
        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static void sendActionBarSafe(Player viewer, String message) {
        try {
            NMS.sendActionBar(viewer, message);
            return;
        } catch (Throwable ignored) {
        }
        trySendSpigotActionBar(viewer, message);
    }

    private static boolean trySendSpigotActionBar(Player viewer, String message) {
        try {
            Class<?> chatMessageType = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Method sendMethod = Player.Spigot.class.getMethod("sendMessage", chatMessageType, BaseComponent[].class);
            Object actionBar = Enum.valueOf((Class<Enum>) chatMessageType, "ACTION_BAR");
            sendMethod.invoke(viewer.spigot(), actionBar, (Object) TextComponent.fromLegacyText(message));
            return true;
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable ignored) {
        }
        return false;
    }
}
