package dev.zonely.whiteeffect.utils.menu;

import dev.zonely.whiteeffect.Core;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class ChatPrompt implements Listener {

    private static final Map<UUID, PromptSession> ACTIVE = new HashMap<>();
    private static boolean registered;

    private ChatPrompt() {
    }

    public static void request(Player player, String title, String hint,
                               Consumer<String> callback, Runnable cancel) {
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(new ChatPrompt(), Core.getInstance());
            registered = true;
        }
        UUID key = player.getUniqueId();
        ACTIVE.put(key, new PromptSession(callback, cancel));

        if (title != null && !title.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', title));
        }
        if (hint != null && !hint.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', hint));
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Type your response in chat. Type &ccancel &7to abort."));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        PromptSession session = ACTIVE.remove(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        String msg = event.getMessage();
        if ("cancel".equalsIgnoreCase(msg.trim())) {
            session.cancel();
        } else {
            session.complete(msg.trim());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PromptSession session = ACTIVE.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            session.cancel();
        }
    }

    private static final class PromptSession {
        private final Consumer<String> completion;
        private final Runnable cancel;

        PromptSession(Consumer<String> completion, Runnable cancel) {
            this.completion = completion;
            this.cancel = cancel;
        }

        void complete(String text) {
            Bukkit.getScheduler().runTask(Core.getInstance(), () -> {
                if (completion != null) completion.accept(text);
            });
        }

        void cancel() {
            if (cancel != null) {
                Bukkit.getScheduler().runTask(Core.getInstance(), cancel);
            }
        }
    }
}
