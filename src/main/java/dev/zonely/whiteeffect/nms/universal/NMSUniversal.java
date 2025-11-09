package dev.zonely.whiteeffect.nms.universal;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.libraries.MinecraftVersion;
import dev.zonely.whiteeffect.libraries.holograms.api.Hologram;
import dev.zonely.whiteeffect.libraries.holograms.api.HologramLine;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPCAnimation;
import dev.zonely.whiteeffect.libraries.npclib.npc.skin.SkinnableEntity;
import dev.zonely.whiteeffect.nms.interfaces.INMS;
import dev.zonely.whiteeffect.nms.interfaces.entity.IArmorStand;
import dev.zonely.whiteeffect.nms.interfaces.entity.IItem;
import dev.zonely.whiteeffect.nms.interfaces.entity.ISlime;
import dev.zonely.whiteeffect.nms.util.SafeInventoryUpdater;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.persistence.PersistentDataType;

public class NMSUniversal implements INMS {
    private static final MinecraftVersion VERSION = MinecraftVersion.getCurrentVersion();
    private static final Map<Class<?>, Field> CONNECTION_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> SEND_PACKET_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final String HOLOGRAM_TAG = "zonely_hologram";
    private static NamespacedKey HOLOGRAM_KEY;
    private static Class<?> cachedEntityPlayerClass;
    private static Class<?> cachedPlayerInfoClass;
    private static Class<?> cachedPlayerInfoRemoveClass;
    private static Class<?> cachedPlayerInfoActionClass;
    private static final Class<?> PARTICLE_CLASS;
    private static final Method WORLD_SPAWN_PARTICLE;
    private static final Class<?> ENTITY_EFFECT_CLASS;
    private static final Method PLAYER_PLAY_ENTITY_EFFECT;
    private static final Method ENTITY_SET_REMOVED;
    private static final Object REMOVAL_REASON_DISCARDED;
    private static final Method ENTITY_DISCARD_METHOD;
    private static final Field ENTITY_DEAD_FIELD;

    static {
        Class<?> particleClass = null;
        Method spawnMethod = null;
        try {
            particleClass = Class.forName("org.bukkit.Particle");
            spawnMethod = World.class.getMethod("spawnParticle", particleClass, Location.class, int.class, double.class, double.class, double.class, double.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        }
        Class<?> entityEffect = null;
        Method playEffect = null;
        try {
            entityEffect = Class.forName("org.bukkit.EntityEffect");
            playEffect = Player.class.getMethod("playEffect", entityEffect);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        }
        PARTICLE_CLASS = particleClass;
        WORLD_SPAWN_PARTICLE = spawnMethod;
        ENTITY_EFFECT_CLASS = entityEffect;
        PLAYER_PLAY_ENTITY_EFFECT = playEffect;

        Method setRemoved = null;
        Object discardReason = null;
        Method discardMethod = null;
        Field deadField = null;
        try {
            Class<?> entityClass = findClass("net.minecraft.world.entity.Entity", "Entity");
            if (entityClass != null) {
                try {
                    discardMethod = entityClass.getDeclaredMethod("discard");
                } catch (Throwable ignored) {
                    try {
                        discardMethod = entityClass.getDeclaredMethod("die");
                    } catch (Throwable ignored2) {
                    }
                }
                if (discardMethod != null) {
                    discardMethod.setAccessible(true);
                }
                try {
                    Class<?> removalReasonClass = findClass("net.minecraft.world.entity.Entity$RemovalReason", "Entity$RemovalReason");
                    if (removalReasonClass != null) {
                        Method candidate = entityClass.getMethod("setRemoved", removalReasonClass);
                        candidate.setAccessible(true);
                        setRemoved = candidate;
                        Object[] constants = removalReasonClass.getEnumConstants();
                        if (constants != null && constants.length > 0) {
                            discardReason = constants[constants.length - 1];
                            for (Object constant : constants) {
                                String name = String.valueOf(constant);
                                String upper = name.toUpperCase(Locale.ROOT);
                                if (upper.contains("DISCARD") || upper.contains("KILLED") || upper.contains("REMOVE")) {
                                    discardReason = constant;
                                    break;
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
                try {
                    deadField = entityClass.getDeclaredField("dead");
                    deadField.setAccessible(true);
                } catch (Throwable ignored) {
                    try {
                        deadField = entityClass.getDeclaredField("isRemoved");
                        deadField.setAccessible(true);
                    } catch (Throwable ignored2) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        ENTITY_SET_REMOVED = setRemoved;
        REMOVAL_REASON_DISCARDED = discardReason;
        ENTITY_DISCARD_METHOD = discardMethod;
        ENTITY_DEAD_FIELD = deadField;
    }

    private static Logger logger() {
        Core core = Core.getInstance();
        return core != null ? core.getLogger() : Bukkit.getLogger();
    }

    private static Class<?> findClass(String modernName, String legacySimpleName) {
        String key = modernName + "#" + legacySimpleName;
        Class<?> cached = CLASS_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        Class<?> clazz = null;
        if (modernName != null) {
            try {
                clazz = Class.forName(modernName);
            } catch (ClassNotFoundException ignored) {
            }
        }

        if (clazz == null && legacySimpleName != null) {
            String legacyName = "net.minecraft.server." + VERSION.getVersion() + "." + legacySimpleName;
            try {
                clazz = Class.forName(legacyName);
            } catch (ClassNotFoundException ignored) {
            }
        }

        if (clazz == null && legacySimpleName != null) {
            try {
                clazz = Class.forName("net.minecraft.server." + legacySimpleName);
            } catch (ClassNotFoundException ignored) {
            }
        }

        if (clazz != null) {
            CLASS_CACHE.put(key, clazz);
        }
        return clazz;
    }

    private static Class<?> entityPlayerClass() {
        if (cachedEntityPlayerClass == null) {
            cachedEntityPlayerClass = findClass("net.minecraft.server.level.EntityPlayer", "EntityPlayer");
        }
        return cachedEntityPlayerClass;
    }

    private static Class<?> playerInfoPacketClass() {
        if (cachedPlayerInfoClass == null) {
            cachedPlayerInfoClass = findClass("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket", null);
            if (cachedPlayerInfoClass == null) {
                cachedPlayerInfoClass = findClass("net.minecraft.network.protocol.game.PacketPlayOutPlayerInfo", "PacketPlayOutPlayerInfo");
            }
        }
        return cachedPlayerInfoClass;
    }

    private static Class<?> playerInfoRemovePacketClass() {
        if (cachedPlayerInfoRemoveClass == null) {
            cachedPlayerInfoRemoveClass = findClass("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket", null);
        }
        return cachedPlayerInfoRemoveClass;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Enum<?>> playerInfoActionClass() {
        if (cachedPlayerInfoActionClass == null) {
            Class<?> packetClass = playerInfoPacketClass();
            if (packetClass != null) {
                for (Class<?> inner : packetClass.getDeclaredClasses()) {
                    if (inner.isEnum() && ("Action".equals(inner.getSimpleName()) || inner.getSimpleName().equalsIgnoreCase("EnumPlayerInfoAction") || inner.getName().endsWith("EnumPlayerInfoAction"))) {
                        cachedPlayerInfoActionClass = inner;
                        break;
                    }
                }
            }
            if (cachedPlayerInfoActionClass == null && packetClass != null) {
                for (Class<?> inner : packetClass.getDeclaredClasses()) {
                    if (inner.isEnum()) {
                        cachedPlayerInfoActionClass = inner;
                        break;
                    }
                }
            }
        }
        return (Class<? extends Enum<?>>) cachedPlayerInfoActionClass;
    }

    private static Object getHandle(Object bukkitEntity) {
        if (bukkitEntity == null) {
            return null;
        }
        try {
            Method method = bukkitEntity.getClass().getMethod("getHandle");
            method.setAccessible(true);
            return method.invoke(bukkitEntity);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static Object getPlayerConnection(Object handle) {
        if (handle == null) {
            return null;
        }

        Class<?> handleClass = handle.getClass();
        Field connectionField = CONNECTION_FIELD_CACHE.get(handleClass);
        if (connectionField == null) {
            Class<?> current = handleClass;
            while (current != null && connectionField == null) {
                for (Field field : current.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        String name = field.getType().getSimpleName();
                        if (name.endsWith("PlayerConnection") || name.endsWith("ServerGamePacketListenerImpl")) {
                            field.setAccessible(true);
                            connectionField = field;
                            break;
                        }
                    }
                }
                current = current.getSuperclass();
            }
            if (connectionField != null) {
                CONNECTION_FIELD_CACHE.put(handleClass, connectionField);
            }
        }

        if (connectionField != null) {
            try {
                return connectionField.get(handle);
            } catch (IllegalAccessException ignored) {
            }
        }
        return null;
    }

    private static Method resolveSendPacketMethod(Object connection) {
        if (connection == null) {
            return null;
        }
        Class<?> connectionClass = connection.getClass();
        Method method = SEND_PACKET_CACHE.get(connectionClass);
        if (method == null) {
            for (Method m : connectionClass.getMethods()) {
                if ("sendPacket".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    method = m;
                    break;
                }
            }
            if (method != null) {
                SEND_PACKET_CACHE.put(connectionClass, method);
            }
        }
        return method;
    }

    private static void sendPacketRaw(Player receiver, Object packet) {
        if (receiver == null || packet == null) {
            return;
        }
        try {
            Object handle = getHandle(receiver);
            Object connection = getPlayerConnection(handle);
            Method method = resolveSendPacketMethod(connection);
            if (method != null) {
                method.invoke(connection, packet);
            }
        } catch (Throwable throwable) {
            logger().log(Level.FINE, "Failed to send packet " + packet.getClass().getSimpleName() + " to " + receiver.getName(), throwable);
        }
    }

    private static Object createPlayerInfoPacket(String actionName, Object... entityPlayers) {
        Class<?> packetClass = playerInfoPacketClass();
        Class<? extends Enum<?>> actionClass = playerInfoActionClass();
        Class<?> entityClass = entityPlayerClass();
        if (packetClass == null || actionClass == null || entityClass == null || entityPlayers == null || entityPlayers.length == 0) {
            return null;
        }

        try {
            if (packetClass.getSimpleName().contains("ClientboundPlayerInfoUpdatePacket")) {
                @SuppressWarnings({"rawtypes","unchecked"})
                Enum actionEnum = Enum.valueOf((Class) actionClass, actionName);
                @SuppressWarnings({"rawtypes","unchecked"})
                EnumSet enumSet = EnumSet.of(actionEnum);
                Collection<Object> entries = buildModernPlayerEntries(packetClass, entityPlayers);
                Collection<?> playersCollection = Arrays.asList(entityPlayers);

                for (Constructor<?> constructor : packetClass.getConstructors()) {
                    Class<?>[] params = constructor.getParameterTypes();
                    if (params.length == 2 && EnumSet.class.isAssignableFrom(params[0])) {
                        constructor.setAccessible(true);
                        Object argument = null;
                        if (Collection.class.isAssignableFrom(params[1])) {
                            argument = entries != null ? entries : playersCollection;
                        } else if (params[1].isArray()) {
                            Object array = Array.newInstance(params[1].getComponentType(), entityPlayers.length);
                            for (int i = 0; i < entityPlayers.length; i++) {
                                Array.set(array, i, entityPlayers[i]);
                            }
                            argument = array;
                        }
                        if (argument != null) {
                            return constructor.newInstance(enumSet, argument);
                        }
                    }
                }

                for (Method method : packetClass.getDeclaredMethods()) {
                    Class<?>[] params = method.getParameterTypes();
                    if (Modifier.isStatic(method.getModifiers())
                            && packetClass.isAssignableFrom(method.getReturnType())
                            && params.length == 2
                            && EnumSet.class.isAssignableFrom(params[0])) {
                        method.setAccessible(true);
                        Object argument = null;
                        if (Collection.class.isAssignableFrom(params[1])) {
                            argument = entries != null ? entries : playersCollection;
                        } else if (params[1].isArray()) {
                            Object array = Array.newInstance(params[1].getComponentType(), entityPlayers.length);
                            for (int i = 0; i < entityPlayers.length; i++) {
                                Array.set(array, i, entityPlayers[i]);
                            }
                            argument = array;
                        }
                        if (argument != null) {
                            return method.invoke(null, enumSet, argument);
                        }
                    }
                }
            } else {
                @SuppressWarnings({"rawtypes","unchecked"})
                Enum actionEnum = Enum.valueOf((Class) actionClass, actionName);
                Object array = Array.newInstance(entityClass, entityPlayers.length);
                for (int i = 0; i < entityPlayers.length; i++) {
                    Array.set(array, i, entityPlayers[i]);
                }
                Constructor<?> constructor = packetClass.getConstructor(actionClass, array.getClass());
                constructor.setAccessible(true);
                return constructor.newInstance(actionEnum, array);
            }
        } catch (Throwable throwable) {
            logger().log(Level.FINE, "Failed to build player info packet for action " + actionName, throwable);
        }
        return null;
    }

    private static Collection<Object> buildModernPlayerEntries(Class<?> packetClass, Object[] entityPlayers) {
        try {
            Class<?> entryClass = null;
            Constructor<?> entryConstructor = null;
            for (Class<?> inner : packetClass.getDeclaredClasses()) {
                if (!inner.isEnum()) {
                    for (Constructor<?> ctor : inner.getDeclaredConstructors()) {
                        Class<?>[] params = ctor.getParameterTypes();
                        if (params.length == 1 && params[0].isAssignableFrom(entityPlayerClass())) {
                            entryClass = inner;
                            entryConstructor = ctor;
                            entryConstructor.setAccessible(true);
                            break;
                        }
                    }
                }
                if (entryClass != null) {
                    break;
                }
            }

            if (entryClass == null || entryConstructor == null) {
                return null;
            }

            List<Object> entries = new ArrayList<>(entityPlayers.length);
            for (Object handle : entityPlayers) {
                entries.add(entryConstructor.newInstance(handle));
            }
            return entries;
        } catch (Throwable throwable) {
            logger().log(Level.FINE, "Failed to create ClientboundPlayerInfoUpdatePacket entries", throwable);
            return null;
        }
    }

    private static Object createPlayerInfoUpdatePacket(String actionName, Object... entityPlayers) {
        Object packet = createPlayerInfoPacket(actionName, entityPlayers);
        if (packet == null) {
            return null;
        }
        try {
            Class<?> packetClass = packet.getClass();
            if (!packetClass.getSimpleName().contains("ClientboundPlayerInfoUpdatePacket")) {
                return packet;
            }
            Class<? extends Enum<?>> actionClass = playerInfoActionClass();
            if (actionClass == null) {
                return packet;
            }
            @SuppressWarnings({"rawtypes","unchecked"})
            Enum actionEnum = Enum.valueOf((Class) actionClass, actionName);
            EnumSet<?> enumSet = EnumSet.of(actionEnum);
            for (Field field : packetClass.getDeclaredFields()) {
                if (EnumSet.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(packet);
                    if (value == null) {
                        field.set(packet, enumSet);
                    }
                    return packet;
                }
            }
        } catch (Throwable throwable) {
            logger().log(Level.FINE, "Failed to enforce player info actions", throwable);
        }
        return packet;
    }

    private static Object createPlayerInfoRemovePacket(Collection<Object> handles) {
        Class<?> packetClass = playerInfoRemovePacketClass();
        if (packetClass == null || handles == null || handles.isEmpty()) {
            return null;
        }
        List<java.util.UUID> ids = new ArrayList<>(handles.size());
        for (Object handle : handles) {
            java.util.UUID uuid = extractUUID(handle);
            if (uuid != null) {
                ids.add(uuid);
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        try {
            for (Constructor<?> ctor : packetClass.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 1) {
                    ctor.setAccessible(true);
                    if (Collection.class.isAssignableFrom(params[0])) {
                        return ctor.newInstance(ids);
                    } else if (params[0].isArray() && params[0].getComponentType().equals(java.util.UUID.class)) {
                        return ctor.newInstance((Object) ids.toArray(new java.util.UUID[0]));
                    }
                }
            }
        } catch (Throwable throwable) {
            logger().log(Level.FINE, "Failed to build PlayerInfo remove packet", throwable);
        }
        return null;
    }

    private static java.util.UUID extractUUID(Object handle) {
        if (handle == null) {
            return null;
        }
        try {
            Method method = handle.getClass().getMethod("getUUID");
            method.setAccessible(true);
            return (java.util.UUID) method.invoke(handle);
        } catch (NoSuchMethodException e) {
            try {
                Method method = handle.getClass().getMethod("getUniqueID");
                method.setAccessible(true);
                return (java.util.UUID) method.invoke(handle);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            }
        } catch (IllegalAccessException | InvocationTargetException ignored) {
        }
        return null;
    }

    private static void invokeSwing(Player player) {
        if (player == null) {
            return;
        }
        try {
            Method swingMain = Player.class.getMethod("swingMainHand");
            swingMain.invoke(player);
        } catch (NoSuchMethodException ignored) {
            try {
                Method swingHand = Player.class.getMethod("swingHand");
                swingHand.invoke(player);
            } catch (Throwable inner) {
                playEntityEffect(player, "ARM_SWING");
            }
        } catch (Throwable throwable) {
            playEntityEffect(player, "ARM_SWING");
        }
    }

    private static void spawnParticle(Player player, String particleName) {
        if (player == null || particleName == null || PARTICLE_CLASS == null || WORLD_SPAWN_PARTICLE == null) {
            return;
        }
        try {
            @SuppressWarnings({"rawtypes","unchecked"})
            Enum particle = Enum.valueOf((Class) PARTICLE_CLASS, particleName);
            double height = player.getEyeHeight();
            try {
                Method getHeight = Player.class.getMethod("getHeight");
                height = ((Number) getHeight.invoke(player)).doubleValue() * 0.6D;
            } catch (NoSuchMethodException ignored) {
                height = player.getEyeHeight();
            }
            Location location = player.getLocation().add(0.0D, height, 0.0D);
            WORLD_SPAWN_PARTICLE.invoke(player.getWorld(), particle, location, 8, 0.3D, 0.2D, 0.3D, 0.1D);
        } catch (Throwable ignored) {
        }
    }

    private static void playEntityEffect(Player player, String effectName) {
        if (player == null || effectName == null || ENTITY_EFFECT_CLASS == null || PLAYER_PLAY_ENTITY_EFFECT == null) {
            return;
        }
        try {
            @SuppressWarnings({"rawtypes","unchecked"})
            Enum effect = Enum.valueOf((Class) ENTITY_EFFECT_CLASS, effectName);
            PLAYER_PLAY_ENTITY_EFFECT.invoke(player, effect);
        } catch (Throwable ignored) {
        }
    }

    private static org.bukkit.Sound resolveSound(String modern, String legacyFallback) {
        try {
            return org.bukkit.Sound.valueOf(modern);
        } catch (IllegalArgumentException ignored) {
            if (legacyFallback != null) {
                try {
                    return org.bukkit.Sound.valueOf(legacyFallback);
                } catch (IllegalArgumentException ignoredAgain) {
                }
            }
            return org.bukkit.Sound.values()[0];
        }
    }


    @Override
    public IArmorStand createArmorStand(Location location, String name, HologramLine line) {
        return UniversalArmorStand.spawn(location, name, line);
    }

    @Override
    public IItem createItem(Location location, ItemStack item, HologramLine line) {
        return UniversalItem.spawn(location, item, line);
    }

    @Override
    public ISlime createSlime(Location location, HologramLine line) {
        return UniversalSlime.spawn(location, line);
    }

    @Override
    public Hologram getHologram(Entity entity) {
        Object meta = entity.hasMetadata(HOLOGRAM_TAG)
                ? entity.getMetadata(HOLOGRAM_TAG).stream().findFirst().map(v -> v.value()).orElse(null)
                : null;
        return meta instanceof Hologram ? (Hologram) meta : null;
    }

    @Override
    public Hologram getPreHologram(int entityId) {
        return null;
    }

    @Override
      public boolean isHologramEntity(Entity entity) {
          if (entity == null) {
              return false;
          }
          if (getHologram(entity) != null) {
              return true;
          }
          try {
              if (hasScoreboardTag(entity, HOLOGRAM_TAG)) {
                  return true;
              }
          } catch (Throwable ignored) {
          }
          try {
              NamespacedKey key = hologramKey();
              if (key != null && hasPersistentByteTag(entity, key)) {
                  return true;
              }
          } catch (Throwable ignored) {
          }
          if (entity instanceof Slime) {
              try {
                  if (entity.hasMetadata(HOLOGRAM_TAG)) {
                      return true;
                  }
              } catch (Throwable ignored) {
              }
              try {
                  if (hasScoreboardTag(entity, HOLOGRAM_TAG)) {
                      return true;
                  }
              } catch (Throwable ignored) {
              }
              try {
                  NamespacedKey key = hologramKey();
                  if (key != null && hasPersistentByteTag(entity, key)) {
                      return true;
                  }
              } catch (Throwable ignored) {
              }
          }
          if (entity instanceof ArmorStand) {
              ArmorStand stand = (ArmorStand) entity;
              try {
                  if (hasScoreboardTag(stand, HOLOGRAM_TAG)) {
                      return true;
                  }
              } catch (Throwable ignored) {
              }
              try {
                  NamespacedKey key = hologramKey();
                  if (key != null && hasPersistentByteTag(stand, key)) {
                      return true;
                  }
              } catch (Throwable ignored) {
              }
              try {
                String name = stand.getCustomName();
                boolean invisible = !stand.isVisible();
                boolean small = stand.isSmall();
                boolean marker = stand.isMarker();
                if (invisible && small && marker && name != null && !name.isEmpty()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
          return false;
      }

    @Override
    public void playChestAction(Location location, boolean open) {
        World world = location.getWorld();
        if (world == null) return;
        try {
            org.bukkit.Sound s;
            s = resolveSound(open ? "BLOCK_CHEST_OPEN" : "BLOCK_CHEST_CLOSE", open ? "CHEST_OPEN" : "CHEST_CLOSE");
            world.playSound(location, s, 1.0f, 1.0f);
        } catch (Throwable t) {
        }
    }

    @Override
    public void playAnimation(Entity entity, NPCAnimation animation) {
        if (!(entity instanceof Player) || animation == null) {
            return;
        }
        Player target = (Player) entity;
        switch (animation) {
            case SWING_ARM:
                invokeSwing(target);
                break;
            case DAMAGE:
                playEntityEffect(target, "HURT");
                break;
            case EAT_FOOD:
                playEntityEffect(target, "EAT_FOOD");
                break;
            case CRITICAL_HIT:
                spawnParticle(target, "CRIT");
                break;
            case MAGIC_CRITICAL_HIT:
                spawnParticle(target, "CRIT_MAGIC");
                break;
            case CROUCH:
                target.setSneaking(true);
                break;
            case UNCROUCH:
                target.setSneaking(false);
                break;
            default:
                break;
        }
    }

    @Override
    public void setValueAndSignature(Player player, String value, String signature) {
        if (player == null || value == null || signature == null) {
            return;
        }
        try {
            Object profile = player.getClass().getMethod("getProfile").invoke(player);
            if (profile == null) {
                return;
            }
            Object properties = profile.getClass().getMethod("getProperties").invoke(profile);
            if (properties == null) {
                return;
            }
            try {
                Method clear = properties.getClass().getMethod("clear");
                clear.invoke(properties);
            } catch (NoSuchMethodException ignored) {
                try {
                    Method removeAll = properties.getClass().getMethod("removeAll", Object.class);
                    removeAll.invoke(properties, "textures");
                } catch (NoSuchMethodException ignoreSecond) {
                }
            }

            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Constructor<?> propertyCtor = propertyClass.getConstructor(String.class, String.class, String.class);
            Object property = propertyCtor.newInstance("textures", value, signature);

            Method putMethod = null;
            for (Method method : properties.getClass().getMethods()) {
                if ("put".equals(method.getName()) && method.getParameterCount() == 2) {
                    putMethod = method;
                    putMethod.setAccessible(true);
                    break;
                }
            }
            if (putMethod != null) {
                putMethod.invoke(properties, "textures", property);
            }
        } catch (Throwable throwable) {
            logger().log(Level.FINE, "Failed to update skin data for " + player.getName(), throwable);
        }
    }

    @Override
    public void sendTabListAdd(Player player, Player listPlayer) {
        Object handle = getHandle(listPlayer);
        if (handle == null) {
            return;
        }
        Object packet = createPlayerInfoUpdatePacket("ADD_PLAYER", handle);
        if (packet == null) {
            packet = createPlayerInfoPacket("ADD_PLAYER", handle);
        }
        if (packet == null) {
            return;
        }
        sendPacketRaw(player, packet);
    }

    @Override
    public void sendTabListRemove(Player player, java.util.Collection<SkinnableEntity> skinnableEntities) {
        if (skinnableEntities == null || skinnableEntities.isEmpty()) {
            return;
        }
        List<Object> handles = new ArrayList<>(skinnableEntities.size());
        for (SkinnableEntity skinnable : skinnableEntities) {
            Player bukkitPlayer = skinnable != null ? skinnable.getEntity() : null;
            Object handle = bukkitPlayer != null ? getHandle(bukkitPlayer) : null;
            if (handle != null) {
                handles.add(handle);
            }
        }
        if (handles.isEmpty()) {
            return;
        }
        Object packet = createPlayerInfoRemovePacket(handles);
        if (packet == null) {
            packet = createPlayerInfoPacket("REMOVE_PLAYER", handles.toArray());
        }
        if (packet != null) {
            sendPacketRaw(player, packet);
        }
    }

    @Override
    public void sendTabListRemove(Player player, Player listPlayer) {
        Object handle = getHandle(listPlayer);
        if (handle == null) {
            return;
        }
        Object packet = createPlayerInfoRemovePacket(Collections.singletonList(handle));
        if (packet == null) {
            packet = createPlayerInfoPacket("REMOVE_PLAYER", handle);
        }
        if (packet != null) {
            sendPacketRaw(player, packet);
        }
    }

    @Override
    public void removeFromPlayerList(Player player) {
        if (player == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(player)) {
                continue;
            }
            sendTabListRemove(viewer, player);
        }
    }

    @Override
    public void removeFromServerPlayerList(Player player) {
        if (player == null) {
            return;
        }
        removeFromPlayerList(player);
        sendTabListRemove(player, player);
    }

    @Override
    public boolean addToWorld(World world, Entity entity, SpawnReason reason) {
        return entity != null && entity.isValid();
    }

    @Override
    public void removeFromWorld(Entity entity) {
        if (entity == null) {
            return;
        }
        if (tryRemoveImmediately(entity)) {
            return;
        }
        if (scheduleDeferredRemoval(entity)) {
            return;
        }
        markRemoved(entity);
    }

    private static boolean tryRemoveImmediately(Entity entity) {
        try {
            entity.remove();
        } catch (IllegalStateException ex) {
            return false;
        } catch (Throwable t) {
            logger().log(Level.FINEST, "Failed to remove entity immediately: " + entity, t);
            return !entity.isValid();
        }
        return !entity.isValid();
    }

    private static boolean scheduleDeferredRemoval(Entity entity) {
        Core core = Core.getInstance();
        if (core == null || !core.isEnabled()) {
            return false;
        }
        try {
            Bukkit.getScheduler().runTask(core, () -> {
                if (!tryRemoveImmediately(entity)) {
                    markRemoved(entity);
                }
            });
            return true;
        } catch (IllegalPluginAccessException ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean hasScoreboardTag(Entity entity, String tag) throws ReflectiveOperationException {
        Method getter = entity.getClass().getMethod("getScoreboardTags");
        Object result = getter.invoke(entity);
        if (result instanceof Set<?>) {
            for (Object value : (Set<?>) result) {
                if (tag != null && tag.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

     private static boolean hasPersistentByteTag(Entity entity, NamespacedKey key) throws ReflectiveOperationException {
          Method getter = entity.getClass().getMethod("getPersistentDataContainer");
          Object container = getter.invoke(entity);
          if (container == null) {
              return false;
          }
          Method hasMethod = container.getClass().getMethod("has", NamespacedKey.class, PersistentDataType.class);
          Object response = hasMethod.invoke(container, key, PersistentDataType.BYTE);
        return response instanceof Boolean && (Boolean) response;
    }

    private static NamespacedKey hologramKey() {
        if (HOLOGRAM_KEY != null) {
            return HOLOGRAM_KEY;
        }
        try {
            Core instance = Core.getInstance();
            if (instance == null) {
                return null;
            }
            HOLOGRAM_KEY = new NamespacedKey(instance, HOLOGRAM_TAG);
        } catch (Throwable ignored) {
            return null;
        }
        return HOLOGRAM_KEY;
    }

    private static void markRemoved(Entity entity) {
        Object handle = getHandle(entity);
        if (handle == null) {
            return;
        }
        if (ENTITY_SET_REMOVED != null && REMOVAL_REASON_DISCARDED != null) {
            try {
                ENTITY_SET_REMOVED.invoke(handle, REMOVAL_REASON_DISCARDED);
                return;
            } catch (Throwable ignored) {
            }
        }
        if (ENTITY_DISCARD_METHOD != null) {
            try {
                ENTITY_DISCARD_METHOD.invoke(handle);
                return;
            } catch (Throwable ignored) {
            }
        }
        if (ENTITY_DEAD_FIELD != null) {
            try {
                ENTITY_DEAD_FIELD.set(handle, true);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void replaceTrackerEntry(Player player) { }

    @Override
    public void sendPacket(Player player, Object packet) {
        sendPacketRaw(player, packet);
    }

    @Override
    public void look(Entity entity, float yaw, float pitch) {
        if (entity != null) {
            try {
                entity.getClass().getMethod("setRotation", float.class, float.class).invoke(entity, yaw, pitch);
            } catch (Throwable ignored) {
                Location l = entity.getLocation();
                entity.teleport(new Location(l.getWorld(), l.getX(), l.getY(), l.getZ(), yaw, pitch));
            }
        }
    }

    @Override
    public void setHeadYaw(Entity entity, float yaw) {
        if (entity instanceof LivingEntity) {
            try {
                entity.getClass().getMethod("setRotation", float.class, float.class).invoke(entity, yaw, ((LivingEntity) entity).getLocation().getPitch());
            } catch (Throwable ignored) {
                Location l = entity.getLocation();
                entity.teleport(new Location(l.getWorld(), l.getX(), l.getY(), l.getZ(), yaw, l.getPitch()));
            }
        }
    }

    @Override
    public void setStepHeight(LivingEntity entity, float height) {  }

    @Override
    public float getStepHeight(LivingEntity entity) { return 0.6f; }

    @Override
    public SkinnableEntity getSkinnable(Entity entity) { return null; }

    @Override
    public void flyingMoveLogic(LivingEntity entity, float f, float f1) { }

    @Override
    public void sendActionBar(Player player, String message) {
        if (player == null || message == null) {
            return;
        }
        try {
            Player.class.getMethod("sendActionBar", String.class).invoke(player, message);
            return;
        } catch (Throwable ignored) { }

        try {
            net.md_5.bungee.api.chat.BaseComponent[] comps = net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message);
            Object spigot = player.spigot();
            Class<?> chatMessageType = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Object actionBar = chatMessageType.getMethod("valueOf", String.class).invoke(null, "ACTION_BAR");
            java.lang.reflect.Method method = null;
            for (java.lang.reflect.Method m : spigot.getClass().getMethods()) {
                if (!m.getName().equals("sendMessage")) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 2 && params[0].equals(chatMessageType)
                        && params[1].isArray()
                        && net.md_5.bungee.api.chat.BaseComponent.class.isAssignableFrom(params[1].getComponentType())) {
                    method = m;
                    break;
                }
            }
            if (method != null) {
                method.invoke(spigot, actionBar, (Object) comps);
                return;
            }
        } catch (Throwable ignored) { }

        try {
            net.md_5.bungee.api.chat.BaseComponent[] comps = net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message);
            Object spigot = player.spigot();
            for (java.lang.reflect.Method m : spigot.getClass().getMethods()) {
                if (!m.getName().equals("sendMessage")) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 1 && params[0].isArray()
                        && net.md_5.bungee.api.chat.BaseComponent.class.isAssignableFrom(params[0].getComponentType())) {
                    m.invoke(spigot, (Object) comps);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, 10, 60, 10);
    }

    @Override
    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Player.class.getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class)
                    .invoke(player, title == null ? "" : title, subtitle == null ? "" : subtitle, fadeIn, stay, fadeOut);
        } catch (Throwable t) {
            try {
                Player.class.getMethod("sendTitle", String.class, String.class)
                        .invoke(player, title == null ? "" : title, subtitle == null ? "" : subtitle);
            } catch (Throwable t2) {
                player.sendMessage((title == null ? "" : title) + (subtitle == null ? "" : (" \n" + subtitle)));
            }
        }
    }

    @Override
    public void sendTabHeaderFooter(Player player, String header, String footer) {
        try {
            Player.class.getMethod("setPlayerListHeaderFooter", String.class, String.class)
                    .invoke(player, header == null ? "" : header, footer == null ? "" : footer);
        } catch (Throwable t) {
        }
    }

    @Override
    public void clearPathfinderGoal(Object entity) { }

    @Override
    public void refreshPlayer(Player player) {
        if (player == null) {
            return;
        }
        Core core = Core.getInstance();
        if (core != null && core.isEnabled()) {
            Bukkit.getScheduler().runTask(core, () -> SafeInventoryUpdater.update(player));
        } else {
            SafeInventoryUpdater.update(player);
        }
    }
}
