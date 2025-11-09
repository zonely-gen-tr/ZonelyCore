package dev.zonely.whiteeffect.api.socket.impl;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.api.socket.SocketAPI;
import dev.zonely.whiteeffect.socket.SecureSocketListener;

import java.util.Optional;

public class SocketApiImpl implements SocketAPI {

    private final Core plugin;

    public SocketApiImpl(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean start() {
        return plugin.startSecureSocketListener();
    }

    @Override
    public void stop() {
        plugin.stopSecureSocketListener();
    }

    @Override
    public boolean restart() {
        return plugin.restartSecureSocketListener();
    }

    @Override
    public boolean isActive() {
        return plugin.isSecureSocketActive();
    }

    @Override
    public int getPort() {
        return plugin.getSecureSocketPort();
    }

    @Override
    public Optional<SecureSocketListener> getListener() {
        return Optional.ofNullable(plugin.getSecureSocketListener());
    }
}
