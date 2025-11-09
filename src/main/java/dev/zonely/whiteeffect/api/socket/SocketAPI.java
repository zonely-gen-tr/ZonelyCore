package dev.zonely.whiteeffect.api.socket;

import dev.zonely.whiteeffect.socket.SecureSocketListener;

import java.util.Optional;

public interface SocketAPI {

    boolean start();

    void stop();

    boolean restart();

    boolean isActive();

    int getPort();

    Optional<SecureSocketListener> getListener();
}
