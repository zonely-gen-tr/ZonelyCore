/*
 *  PacketWrapper - Contains wrappers for each packet in Minecraft.
 *  Copyright (C) 2012 Kristian S. Stangeland
 *
 *  GNU LGPL v2 or later
 */

package com.comphenix.packetwrapper;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Objects;

public abstract class AbstractPacket {
  // The packet we will be modifying
  protected final PacketContainer handle;

  protected AbstractPacket(PacketContainer handle, PacketType type) {
    // Make sure we're given a valid packet
    if (handle == null) {
      throw new IllegalArgumentException("Packet handle cannot be NULL.");
    }
    if (!Objects.equals(handle.getType(), type)) {
      throw new IllegalArgumentException(handle.getHandle() + " is not a packet of type " + type);
    }
    this.handle = handle;
  }

  public PacketContainer getHandle() {
    return handle;
  }

  public void sendPacket(Player receiver) {
    try {
      ProtocolLibrary.getProtocolManager().sendServerPacket(receiver, getHandle());
    } catch (Exception e) {
      throw new RuntimeException("Cannot send packet.", e);
    }
  }

  public void receivePacket(Player sender) {
    try {
      Object pm = ProtocolLibrary.getProtocolManager();
      Class<?> cls = pm.getClass();
      Method m;
      try {
        // preferred (correct spelling)
        m = cls.getMethod("receiveClientPacket", Player.class, PacketContainer.class);
      } catch (NoSuchMethodException ignored) {
        // fallback (legacy misspelling)
        m = cls.getMethod("recieveClientPacket", Player.class, PacketContainer.class);
      }
      m.invoke(pm, sender, getHandle());
    } catch (Exception e) {
      throw new RuntimeException("Cannot receive packet.", e);
    }
  }

  public void recievePacket(Player sender) {
    receivePacket(sender);
  }
}
