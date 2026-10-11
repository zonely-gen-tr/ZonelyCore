package dev.zonely.whiteeffect.utils;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.Serializer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import dev.zonely.whiteeffect.Core;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ProtocolLibUtils {

    private static final AtomicBoolean WATCHER_LOGGED = new AtomicBoolean(false);
    private static Method nativeChatFromString;

    private static final boolean HAS_WRAPPED_DATA_VALUE;
    private static final Constructor<?> DATA_VALUE_CTOR; 
    private static Method DATA_VALUE_MODIFIER_METHOD;   
    private static Method DATA_VALUES_METHOD;         

    private static Method WATCHABLES_MODIFIER_METHOD;    

    static {
        Constructor<?> ctor = null;
        boolean modern = false;
        try {
            Class<?> wdv = Class.forName("com.comphenix.protocol.wrappers.WrappedDataValue");
            ctor = wdv.getConstructor(int.class, Serializer.class, Object.class);
            ctor.setAccessible(true);
            modern = true;
        } catch (Throwable ignored) {
            ctor = null;
            modern = false;
        }
        DATA_VALUE_CTOR = ctor;
        HAS_WRAPPED_DATA_VALUE = modern;

        DATA_VALUE_MODIFIER_METHOD = null;
        DATA_VALUES_METHOD = null;
        WATCHABLES_MODIFIER_METHOD = null;
    }

    private ProtocolLibUtils() {}


    public static WrappedDataWatcher createDataWatcher(Entity entity) {
        WrappedDataWatcher cloned = cloneFromEntity(entity);
        if (cloned != null) return cloned;
        try {
            return new WrappedDataWatcher();
        } catch (Throwable primary) {
            logFailure(primary);
            return null;
        }
    }

    private static WrappedDataWatcher cloneFromEntity(Entity entity) {
        if (entity == null) return null;
        try {
            return WrappedDataWatcher.getEntityWatcher(entity).deepClone();
        } catch (Throwable ignored) {}
        try {
            return new WrappedDataWatcher(entity).deepClone();
        } catch (Throwable ignored) {}
        return null;
    }

    public static void setWatcherByte(WrappedDataWatcher watcher, int index, byte value) {
        if (watcher == null) return;
        try {
            Serializer serializer = WrappedDataWatcher.Registry.get(Byte.class);
            watcher.setObject(new WrappedDataWatcherObject(index, serializer), value);
        } catch (Throwable ignored) {
            try { watcher.setObject(index, value); } catch (Throwable ignored2) {}
        }
    }

    public static void setWatcherBoolean(WrappedDataWatcher watcher, int index, boolean value) {
        if (watcher == null) return;
        try {
            Serializer serializer = WrappedDataWatcher.Registry.get(Boolean.class);
            watcher.setObject(new WrappedDataWatcherObject(index, serializer), value);
        } catch (Throwable ignored) {
            setWatcherByte(watcher, index, (byte) (value ? 1 : 0));
        }
    }

    public static void setCustomName(WrappedDataWatcher watcher, int index, String raw) {
        setCustomNameChecked(watcher, index, raw);
    }

    public static Object nativeChatComponent(String text) throws ReflectiveOperationException {
        if (nativeChatFromString == null) {
            String craftPackage = Bukkit.getServer().getClass().getPackage().getName();
            Class<?> chat = Class.forName(craftPackage + ".util.CraftChatMessage");
            nativeChatFromString = chat.getMethod("fromString", String.class);
        }
        Object components = nativeChatFromString.invoke(null, text);
        if (components == null || java.lang.reflect.Array.getLength(components) == 0)
            throw new IllegalStateException("Server returned no chat component");
        return java.lang.reflect.Array.get(components, 0);
    }

    public static boolean setCustomNameChecked(WrappedDataWatcher watcher, int index, String raw) {
        if (watcher == null) return false;
        String formatted = raw == null ? "" : ChatColor.translateAlternateColorCodes('&', raw);
        boolean empty = formatted.isEmpty();
        com.comphenix.protocol.utility.MinecraftVersion version = com.comphenix.protocol.utility.MinecraftVersion.getCurrentVersion();
        boolean modern = version.getMajor() > 1 || version.getMinor() >= 13;
        try {
            if (modern) {
                Serializer chat = WrappedDataWatcher.Registry.getChatComponentSerializer(true);
                if (chat == null) return false;
                Optional<Object> component = empty ? Optional.empty() : Optional.of(nativeChatComponent(formatted));
                watcher.setObject(new WrappedDataWatcherObject(index, chat), component);
            } else {
                Serializer serializer = WrappedDataWatcher.Registry.get(String.class);
                if (serializer != null) watcher.setObject(new WrappedDataWatcherObject(index, serializer), formatted);
                else watcher.setObject(index, formatted);
            }
            return true;
        } catch (Exception | LinkageError failure) {
            logFailure(failure);
            return false;
        }
    }

    public static void setCustomNameVisible(WrappedDataWatcher watcher, int index, boolean visible) {
        setWatcherBoolean(watcher, index, visible);
    }

    public static PacketContainer nameMetadataPacket(com.comphenix.protocol.ProtocolManager manager, int entityId, String text) {
        PacketContainer packet = manager.createPacket(com.comphenix.protocol.PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, entityId);
        WrappedDataWatcher watcher = new WrappedDataWatcher();
        if (!setCustomNameChecked(watcher, 2, text)) throw new IllegalStateException("Custom name metadata unavailable");
        setCustomNameVisible(watcher, 3, text != null && !text.isEmpty());
        if (!writeMetadataChecked(packet, watcher)) throw new IllegalStateException("Metadata packet field unavailable");
        return packet;
    }

    public static void writeMetadata(PacketContainer packet, WrappedDataWatcher watcher) {
        writeMetadataChecked(packet, watcher);
    }

    public static boolean writeMetadataChecked(PacketContainer packet, WrappedDataWatcher watcher) {
        if (packet == null || watcher == null) return false;

        if (HAS_WRAPPED_DATA_VALUE && hasDataValueModifier(packet)) {
            StructureModifier<List<?>> mod = getDataValueModifier(packet);
            if (mod != null && mod.size() > 0) {
                List<?> values = tryBuildWrappedDataValues(watcher);
                if (values == null) {
                    values = extractDataValues(watcher);
                }
                if (values == null) return false;
                try {
                    mod.writeSafely(0, values);
                    return true;
                } catch (Throwable t) {
                    logFailure(t);
                    return false;
                }
            }
            // Older servers may expose the method but have no modern metadata field.
        }

        try {
            StructureModifier<List<?>> legacyMod = getWatchableCollectionModifier(packet);
            if (legacyMod != null && legacyMod.size() > 0) {
                List<WrappedWatchableObject> objs = watcher.getWatchableObjects();
                legacyMod.writeSafely(0, objs != null ? objs : Collections.emptyList());
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }


    private static boolean hasDataValueModifier(PacketContainer packet) {
        try {
            if (DATA_VALUE_MODIFIER_METHOD == null) {
                DATA_VALUE_MODIFIER_METHOD = PacketContainer.class.getMethod("getDataValueCollectionModifier");
                DATA_VALUE_MODIFIER_METHOD.setAccessible(true);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static StructureModifier<List<?>> getDataValueModifier(PacketContainer packet) {
        try {
            if (DATA_VALUE_MODIFIER_METHOD == null) return null;
            Object result = DATA_VALUE_MODIFIER_METHOD.invoke(packet);
            if (result instanceof StructureModifier) {
                return (StructureModifier<List<?>>) result;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static List<?> extractDataValues(WrappedDataWatcher watcher) {
        try {
            if (DATA_VALUES_METHOD == null) {
                DATA_VALUES_METHOD = WrappedDataWatcher.class.getMethod("getDataValues");
                DATA_VALUES_METHOD.setAccessible(true);
            }
            Object result = DATA_VALUES_METHOD.invoke(watcher);
            return (result instanceof List) ? (List<?>) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<?> tryBuildWrappedDataValues(WrappedDataWatcher watcher) {
        if (watcher == null || DATA_VALUE_CTOR == null) return null;
        try {
            List<WrappedWatchableObject> watchables = watcher.getWatchableObjects();
            if (watchables == null) return Collections.emptyList();

            List<Object> values = new ArrayList<>(watchables.size());
            for (WrappedWatchableObject w : watchables) {
                if (w == null) continue;
                WrappedDataWatcherObject obj = w.getWatcherObject();
                if (obj == null) continue;
                Serializer serializer = obj.getSerializer();
                Object raw = w.getRawValue();
                values.add(DATA_VALUE_CTOR.newInstance(obj.getIndex(), serializer, raw));
            }
            return values;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static StructureModifier<List<?>> getWatchableCollectionModifier(PacketContainer packet) {
        try {
            if (WATCHABLES_MODIFIER_METHOD == null) {
                WATCHABLES_MODIFIER_METHOD = PacketContainer.class.getMethod("getWatchableCollectionModifier");
                WATCHABLES_MODIFIER_METHOD.setAccessible(true);
            }
            Object result = WATCHABLES_MODIFIER_METHOD.invoke(packet);
            if (result instanceof StructureModifier) {
                return (StructureModifier<List<?>>) result;
            }
        } catch (Throwable ignored) {}
        return null;
    }


    private static void logFailure(Throwable cause) {
        if (WATCHER_LOGGED.compareAndSet(false, true)) {
            Logger logger = Core.getInstance() != null ? Core.getInstance().getLogger() : Logger.getLogger("ZonelyCore");
            logger.log(Level.SEVERE, "Metadata write failed. Ensure ProtocolLib is up-to-date for this server version.", cause);
        }
    }
}
