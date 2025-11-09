package dev.zonely.whiteeffect.reflection;

import dev.zonely.whiteeffect.libraries.MinecraftVersion;

import java.util.Arrays;

public class MinecraftReflection {

    public static final MinecraftVersion VERSION = MinecraftVersion.getCurrentVersion();

    public static String NMU_PREFIX = "";
    public static String OBC_PREFIX;
    public static String NMS_PREFIX;

    private static final boolean MODERN_NMS = classExists("net.minecraft.world.item.ItemStack");

    private static Class<?> craftItemStack;
    private static Class<?> block;
    private static Class<?> blocks;
    private static Class<?> entity;
    private static Class<?> entityHuman;
    private static Class<?> enumDirection;
    private static Class<?> enumProtocol;
    private static Class<?> enumGamemode;
    private static Class<?> enumPlayerInfoAction;
    private static Class<?> enumTitleAction;
    private static Class<?> nbtTagCompound;
    private static Class<?> channel;
    private static Class<?> playerInfoData;
    private static Class<?> serverPing;
    private static Class<?> serverData;
    private static Class<?> serverPingPlayerSample;
    private static Class<?> serverConnection;
    private static Class<?> world;
    private static Class<?> worldServer;
    private static Class<?> blockPosition;
    private static Class<?> iBlockData;
    private static Class<?> vector3F;
    private static Class<?> iChatBaseComponent;
    private static Class<?> chatSerializer;
    private static Class<?> itemStack;
    private static Class<?> gameProfile;
    private static Class<?> propertyMap;
    private static Class<?> property;
    private static Class<?> dataWatcher;
    private static Class<?> dataWatcherObject;
    private static Class<?> dataWatcherSerializer;
    private static Class<?> dataWatcherRegistry;
    private static Class<?> watchableObject;


    public static Class<?> getServerPing() {
        if (serverPing == null) {
            serverPing = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.network.protocol.status.ServerStatus",
                    "ServerPing"
            );
        }
        return serverPing;
    }

    public static Class<?> getServerData() {
        if (serverData == null) {
            serverData = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.network.protocol.status.ServerStatus$Version",
                    "ServerPing$ServerData"
            );
        }
        return serverData;
    }

    public static Class<?> getServerPingPlayerSample() {
        if (serverPingPlayerSample == null) {
            serverPingPlayerSample = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.network.protocol.status.ServerStatus$Players",
                    "ServerPing$ServerPingPlayerSample"
            );
        }
        return serverPingPlayerSample;
    }

    public static Class<?> getEnumDirectionClass() {
        if (enumDirection == null) {
            enumDirection = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.core.Direction",
                    "EnumDirection"
            );
        }
        return enumDirection;
    }

    public static Class<?> getEnumProtocolDirectionClass() {
        return getMinecraftClass(Integer.TYPE,
                "net.minecraft.network.protocol.PacketFlow",
                "EnumProtocolDirection"
        );
    }

    public static Class<?> getEnumProtocolClass() {
        if (enumProtocol == null) {
            enumProtocol = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.network.protocol.Protocol",
                    "EnumProtocol"
            );
        }
        return enumProtocol;
    }

    public static Class<?> getEnumGamemodeClass() {
        if (enumGamemode == null) {
            enumGamemode = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.world.level.GameType",
                    "WorldSettings$EnumGamemode",
                    "EnumGamemode"
            );
        }
        return enumGamemode;
    }

    public static Class<?> getEnumPlayerInfoActionClass() {
        if (enumPlayerInfoAction == null) {
            enumPlayerInfoAction = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action",
                    "PacketPlayOutPlayerInfo$EnumPlayerInfoAction"
            );
        }
        return enumPlayerInfoAction;
    }

    public static Class<?> getEnumTitleAction() {
        if (enumTitleAction == null) {
            enumTitleAction = getMinecraftClass(Integer.TYPE,
                    "PacketPlayOutTitle$EnumTitleAction"
            );
        }
        return enumTitleAction;
    }

    public static Class<?> getNBTTagCompoundClass() {
        if (nbtTagCompound == null) {
            try {
                nbtTagCompound = getMinecraftClass(true,
                        "net.minecraft.nbt.CompoundTag",
                        "net.minecraft.nbt.NbtCompound",
                        "NBTTagCompound"
                );
            } catch (IllegalArgumentException ignored) {
                nbtTagCompound = null;
            }
        }
        return nbtTagCompound;
    }

    public static Class<?> getDataWatcherClass() {
        if (dataWatcher == null) {
            dataWatcher = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.network.syncher.SynchedEntityData",
                    "DataWatcher"
            );
        }
        return dataWatcher;
    }

    public static Class<?> getDataWatcherObjectClass() {
        if (dataWatcherObject == null) {
            dataWatcherObject = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.network.syncher.EntityDataAccessor",
                    "DataWatcherObject"
            );
        }
        return dataWatcherObject;
    }

    public static Class<?> getDataWatcherSerializerClass() {
        if (dataWatcherSerializer == null) {
            dataWatcherSerializer = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.network.syncher.EntityDataSerializer",
                    "DataWatcherSerializer"
            );
        }
        return dataWatcherSerializer;
    }

    public static Class<?> getDataWatcherRegistryClass() {
        if (dataWatcherRegistry == null) {
            dataWatcherRegistry = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.network.syncher.EntityDataSerializers",
                    "DataWatcherRegistry"
            );
        }
        return dataWatcherRegistry;
    }

    public static Class<?> getWatchableObjectClass() {
        if (watchableObject == null) {
            watchableObject = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.network.syncher.SynchedEntityData$DataItem",
                    "DataWatcher$Item",
                    "DataWatcher$WatchableObject",
                    "WatchableObject"
            );
        }
        return watchableObject;
    }

    public static Class<?> getBlock() {
        if (block == null) {
            block = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.world.level.block.Block",
                    "Block"
            );
        }
        return block;
    }

    public static Class<?> getBlocks() {
        if (blocks == null) {
            blocks = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.world.level.block.Blocks",
                    "Blocks"
            );
        }
        return blocks;
    }

    public static Class<?> getChannelClass() {
        if (channel == null) {
            channel = getMinecraftUtilClass("io.netty.channel.Channel");
        }
        return channel;
    }

    public static Class<?> getEntityClass() {
        if (entity == null) {
            entity = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.world.entity.Entity",
                    "Entity"
            );
        }
        return entity;
    }

    public static Class<?> getEntityHumanClass() {
        if (entityHuman == null) {
            entityHuman = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.world.entity.player.Player",
                    "EntityHuman"
            );
        }
        return entityHuman;
    }

    public static Class<?> getPlayerInfoDataClass() {
        if (playerInfoData == null) {
            playerInfoData = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry",
                    "PacketPlayOutPlayerInfo$PlayerInfoData"
            );
        }
        return playerInfoData;
    }

    public static Class<?> getServerConnectionClass() {
        if (serverConnection == null) {
            serverConnection = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.server.network.ServerConnection",
                    "ServerConnection"
            );
        }
        return serverConnection;
    }

    public static Class<?> getWorldClass() {
        if (world == null) {
            world = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.world.level.Level",
                    "World"
            );
        }
        return world;
    }

    public static Class<?> getWorldServerClass() {
        if (worldServer == null) {
            worldServer = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.server.level.ServerLevel",
                    "WorldServer"
            );
        }
        return worldServer;
    }

    public static Class<?> getIChatBaseComponent() {
        if (iChatBaseComponent == null) {
            iChatBaseComponent = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.network.chat.Component",
                    "IChatBaseComponent"
            );
        }
        return iChatBaseComponent;
    }

    public static Class<?> getChatSerializer() {
        if (chatSerializer == null) {
            chatSerializer = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.network.chat.Component$Serializer",
                    "IChatBaseComponent$ChatSerializer",
                    "ChatSerializer"
            );
        }
        return chatSerializer;
    }

    public static Class<?> getCraftItemStackClass() {
        if (craftItemStack == null) {
            craftItemStack = getCraftBukkitClass("inventory.CraftItemStack");
        }
        return craftItemStack;
    }

    public static Class<?> getItemStackClass() {
        if (itemStack == null) {
            itemStack = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.world.item.ItemStack",
                    "ItemStack"
            );
        }
        return itemStack;
    }

    public static Class<?> getBlockPositionClass() {
        if (blockPosition == null) {
            blockPosition = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.core.BlockPos",
                    "BlockPosition"
            );
        }
        return blockPosition;
    }

    public static Class<?> getIBlockDataClass() {
        if (iBlockData == null) {
            iBlockData = getMinecraftClass(Integer.TYPE,
                    "net.minecraft.world.level.block.state.BlockState",
                    "IBlockData"
            );
        }
        return iBlockData;
    }

    public static Class<?> getVector3FClass() {
        if (vector3F == null) {
            vector3F = getMinecraftClass(Integer.TYPE,
                    "org.joml.Vector3f",
                    "com.mojang.math.Vector3f",
                    "Vector3f"
            );
        }
        return vector3F;
    }

    public static Class<?> getGameProfileClass() {
        if (gameProfile == null) {
            gameProfile = getMinecraftUtilClass("com.mojang.authlib.GameProfile");
        }
        return gameProfile;
    }

    public static Class<?> getPropertyMapClass() {
        if (propertyMap == null) {
            propertyMap = getMinecraftUtilClass("com.mojang.authlib.properties.PropertyMap");
        }
        return propertyMap;
    }

    public static Class<?> getPropertyClass() {
        if (property == null) {
            property = getMinecraftUtilClass("com.mojang.authlib.properties.Property");
        }
        return property;
    }


    public static boolean is(Class<?> clazz, Object object) {
        return clazz != null && object != null && clazz.isAssignableFrom(object.getClass());
    }

    public static Class<?> getClass(String name) {
        try {
            return MinecraftReflection.class.getClassLoader().loadClass(name);
        } catch (Exception var2) {
            throw new IllegalArgumentException("Cannot find class " + name);
        }
    }

    public static Class<?> getCraftBukkitClass(String... names) {
        return getCraftBukkitClass(false, names);
    }

    public static Class<?> getCraftBukkitClass(Object canNull, String... names) {
        for (String name : names) {
            try {
                return getClass(OBC_PREFIX + name);
            } catch (Exception ignored) { }
        }
        if (canNull instanceof Boolean && !(Boolean) canNull) {
            throw new IllegalArgumentException("Cannot find CraftBukkit Class from names " + Arrays.asList(names) + ".");
        }
        return (canNull != null && canNull.getClass().equals(Class.class)) ? (Class<?>) canNull : null;
    }

    public static Class<?> getMinecraftClass(String... names) {
        return getMinecraftClass(false, names);
    }

    public static Class<?> getMinecraftClass(Object canNull, String... names) {
        for (String name : names) {
            try {
                if (name.contains(".")) {
                    return getClass(name);
                }
                if (!MODERN_NMS) {
                    return getClass(NMS_PREFIX + name);
                }
            } catch (Exception ignored) { }
        }
        if (canNull instanceof Boolean && !(Boolean) canNull) {
            throw new IllegalArgumentException("Cannot find Minecraft/NMS Class from names " + Arrays.asList(names) + ".");
        }
        return (canNull != null && canNull.getClass().equals(Class.class)) ? (Class<?>) canNull : null;
    }

    public static Class<?> getMinecraftUtilClass(String name) {
        try {
            return getClass(NMU_PREFIX + name);
        } catch (Exception var2) {
            throw new IllegalArgumentException("Cannot find Util Class from name " + name + ".");
        }
    }

    private static boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn, false, MinecraftReflection.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }


    static {
        try {
            getClass("net.minecraft.util.com.google.common.collect.ImmutableList");
            NMU_PREFIX = "net.minecraft.util.";
        } catch (Exception ignored) { }

        String craftBase = "org.bukkit.craftbukkit";
        String packageToken = MinecraftVersion.hasLegacyPackageStructure() ? MinecraftVersion.getDetectedPackageToken() : null;
        if (packageToken != null && classExists(craftBase + "." + packageToken + ".CraftServer")) {
            OBC_PREFIX = craftBase + "." + packageToken + ".";
        } else if (classExists(craftBase + ".CraftServer")) {
            OBC_PREFIX = craftBase + ".";
        } else {
            OBC_PREFIX = craftBase + "." + VERSION.getVersion() + ".";
        }

        if (MODERN_NMS) {
            NMS_PREFIX = "";
        } else if (packageToken != null) {
            NMS_PREFIX = OBC_PREFIX.replace("org.bukkit.craftbukkit", "net.minecraft.server");
        } else {
            NMS_PREFIX = "net.minecraft.server.";
        }
    }
}
