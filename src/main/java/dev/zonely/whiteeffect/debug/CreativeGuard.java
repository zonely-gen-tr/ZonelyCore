package dev.zonely.whiteeffect.debug;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import dev.zonely.whiteeffect.Core;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public final class CreativeGuard implements Listener {

    private static final String BYPASS = "zonely.lobby.creative";
    private final Plugin plugin;

    private CreativeGuard(Plugin plugin) { this.plugin = plugin; }

    public static void install() {
        Plugin plugin = Core.getInstance();
    }

    private void hookPackets() {
        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGHEST,
                    PacketType.Play.Client.SET_CREATIVE_SLOT) {
                @Override public void onPacketReceiving(PacketEvent e) {
                    Player p = e.getPlayer();
                    if (p == null) return;
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private boolean canUseCreative(Player p) {
        if (p.hasPermission(BYPASS)) return true;
        try {
            Class<?> engClz = Class.forName("dev.zonely.whiteeffect.replay.advanced.AdvancedReplayEngine");
            Object eng = engClz.getMethod("get").invoke(null);
            Object running = engClz.getMethod("isRunning", Player.class).invoke(eng, p);
            return (running instanceof Boolean) && ((Boolean) running);
        } catch (Throwable ignored) { }
        return false;
    }
}
