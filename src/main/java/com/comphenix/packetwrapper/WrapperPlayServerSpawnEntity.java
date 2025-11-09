package com.comphenix.packetwrapper;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.PacketConstructor;
import org.bukkit.World;
import org.bukkit.entity.Entity;

public class WrapperPlayServerSpawnEntity extends AbstractPacket {
  public static final PacketType TYPE = PacketType.Play.Server.SPAWN_ENTITY;

  private static PacketConstructor entityConstructor;

  /**
   * Eski PacketWrapper ile gelen IntEnum bağımlılığını kaldırdık.
   * Sadece sabitler gerektiği için düz bir holder sınıfı yeterli.
   */
  public static final class ObjectTypes {
    public static final int BOAT = 1;
    public static final int ITEM_STACK = 2;
    public static final int MINECART = 10;
    public static final int MINECART_STORAGE = 11;
    public static final int MINECART_POWERED = 12;
    public static final int ACTIVATED_TNT = 50;
    public static final int ENDER_CRYSTAL = 51;
    public static final int ARROW_PROJECTILE = 60;
    public static final int SNOWBALL_PROJECTILE = 61;
    public static final int EGG_PROJECTILE = 62;
    public static final int FIRE_BALL_GHAST = 63;
    public static final int FIRE_BALL_BLAZE = 64;
    public static final int THROWN_ENDERPEARL = 65;
    public static final int WITHER_SKULL = 66;
    public static final int FALLING_BLOCK = 70;
    public static final int ITEM_FRAME = 71;
    public static final int EYE_OF_ENDER = 72;
    public static final int THROWN_POTION = 73;
    public static final int FALLING_DRAGON_EGG = 74;
    public static final int THROWN_EXP_BOTTLE = 75;
    public static final int FIREWORK = 76;
    public static final int FISHING_FLOAT = 90;

    // Eski API’lerle uyum için no-op instance/getter (kullanılmasa da derlensin diye).
    @Deprecated public static final ObjectTypes INSTANCE = new ObjectTypes();
    @Deprecated public static ObjectTypes getInstance() { return INSTANCE; }
    private ObjectTypes() {}
  }

  public WrapperPlayServerSpawnEntity() {
    super(new PacketContainer(TYPE), TYPE);
    handle.getModifier().writeDefaults();
  }

  public WrapperPlayServerSpawnEntity(PacketContainer packet) {
    super(packet, TYPE);
  }

  public WrapperPlayServerSpawnEntity(Entity entity, int type, int objectData) {
    super(fromEntity(entity, type, objectData), TYPE);
  }

  // Eski PacketWrapper imzası – PL 5.x’te yoksa güvenli fallback’e düş.
  private static PacketContainer fromEntity(Entity entity, int type, int objectData) {
    try {
      if (entityConstructor == null) {
        entityConstructor = ProtocolLibrary.getProtocolManager()
            .createPacketConstructor(TYPE, entity, type, objectData);
      }
      return entityConstructor.createPacket(entity, type, objectData);
    } catch (Throwable ignored) {
      // Fallback: boş konteyner – alanları dışarıdan set edeceksin.
      PacketContainer pc = new PacketContainer(TYPE);
      try { pc.getModifier().writeDefaults(); } catch (Throwable ignored2) {}
      return pc;
    }
  }

  /** Entity ID */
  public int getEntityID() { return handle.getIntegers().read(0); }
  public void setEntityID(int value) { handle.getIntegers().write(0, value); }

  /** Spawn’lanacak entity (dünyaya göre çözümlenir) */
  public Entity getEntity(World world) { return handle.getEntityModifier(world).read(0); }
  public Entity getEntity(PacketEvent event) { return getEntity(event.getPlayer().getWorld()); }

  /** Tür (ObjectTypes sabitleri) */
  public int getType() { return handle.getIntegers().read(9); }
  public void setType(int value) { handle.getIntegers().write(9, value); }

  /** Konum (eski wrapper indeksleri – yeni sürümlerde uyumlu olmayabilir) */
  public double getX() { return handle.getIntegers().read(1) / 32.0D; }
  public void setX(double value) { handle.getIntegers().write(1, (int) Math.floor(value * 32.0D)); }

  public double getY() { return handle.getIntegers().read(2) / 32.0D; }
  public void setY(double value) { handle.getIntegers().write(2, (int) Math.floor(value * 32.0D)); }

  public double getZ() { return handle.getIntegers().read(3) / 32.0D; }
  public void setZ(double value) { handle.getIntegers().write(3, (int) Math.floor(value * 32.0D)); }

  /** Opsiyonel hızlar */
  public double getOptionalSpeedX() { return handle.getIntegers().read(4) / 8000.0D; }
  public void setOptionalSpeedX(double value) { handle.getIntegers().write(4, (int) (value * 8000.0D)); }

  public double getOptionalSpeedY() { return handle.getIntegers().read(5) / 8000.0D; }
  public void setOptionalSpeedY(double value) { handle.getIntegers().write(5, (int) (value * 8000.0D)); }

  public double getOptionalSpeedZ() { return handle.getIntegers().read(6) / 8000.0D; }
  public void setOptionalSpeedZ(double value) { handle.getIntegers().write(6, (int) (value * 8000.0D)); }

  /** Yaw/Pitch (byte derece ölçeği) */
  public float getYaw() { return (handle.getIntegers().read(7) * 360.F) / 256.0F; }
  public void setYaw(float value) { handle.getIntegers().write(7, (int) (value * 256.0F / 360.0F)); }

  public float getPitch() { return (handle.getIntegers().read(8) * 360.F) / 256.0F; }
  public void setPitch(float value) { handle.getIntegers().write(8, (int) (value * 256.0F / 360.0F)); }

  /** Object data alanı */
  public int getObjectData() { return handle.getIntegers().read(10); }
  public void setObjectData(int value) { handle.getIntegers().write(10, value); }
}
