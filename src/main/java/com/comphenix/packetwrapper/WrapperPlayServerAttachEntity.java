/*
 * PacketWrapper - Contains wrappers for each packet in Minecraft. Copyright (C) 2012 Kristian S.
 * Stangeland
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 */

package com.comphenix.packetwrapper;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;

public class WrapperPlayServerAttachEntity extends AbstractPacket {
  public static final PacketType TYPE = PacketType.Play.Server.ATTACH_ENTITY;

  public WrapperPlayServerAttachEntity() {
    super(new PacketContainer(TYPE), TYPE);
    handle.getModifier().writeDefaults();
  }

  public WrapperPlayServerAttachEntity(PacketContainer packet) {
    super(packet, TYPE);
  }

  public boolean getLeached() {
    return handle.getIntegers().read(0) != 0;
  }

  public void setLeached(boolean value) {
    handle.getIntegers().write(0, value ? 1 : 0);
  }

  public int getEntityId() {
    return handle.getIntegers().read(1);
  }

  public void setEntityId(int value) {
    handle.getIntegers().write(1, value);
  }

  public Entity getEntity(World world) {
    return handle.getEntityModifier(world).read(1);
  }

  public Entity getEntity(PacketEvent event) {
    return getEntity(event.getPlayer().getWorld());
  }

  public int getVehicleId() {
    return handle.getIntegers().read(2);
  }

  public void setVehicleId(int value) {
    handle.getIntegers().write(2, value);
  }

  public Entity getVehicle(World world) {
    return handle.getEntityModifier(world).read(2);
  }

  public Entity getVehicle(PacketEvent event) {
    return getVehicle(event.getPlayer().getWorld());
  }
}

