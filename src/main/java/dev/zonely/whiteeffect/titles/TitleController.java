package dev.zonely.whiteeffect.titles;

import com.comphenix.packetwrapper.WrapperPlayServerAttachEntity;
import com.comphenix.packetwrapper.WrapperPlayServerEntityDestroy;
import com.comphenix.packetwrapper.WrapperPlayServerSpawnEntityLiving;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.reflection.Accessors;
import dev.zonely.whiteeffect.reflection.MinecraftReflection;
import dev.zonely.whiteeffect.reflection.acessors.FieldAccessor;
import dev.zonely.whiteeffect.utils.ProtocolLibUtils;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public class TitleController {
    private static final FieldAccessor<Integer> ENTITY_ID;

    private Player owner;
    private WrappedDataWatcher watcher;
    private boolean disabled = true;
    private final int entityId;
    private String currentName;

    public TitleController(Player owner, String title) {
        this.owner = owner;
        this.currentName = title == null ? "" : title;

        this.watcher = ProtocolLibUtils.createDataWatcher(null);
        if (this.watcher == null) {
            throw new IllegalStateException("Unable to create data watcher for TitleController");
        }
        this.watcher.clear();

        ProtocolLibUtils.setWatcherByte(this.watcher, 0, (byte) 0x20);
        ProtocolLibUtils.setWatcherBoolean(this.watcher, 5, true);    
        ProtocolLibUtils.setWatcherByte(this.watcher, 10, (byte) 1);  
        applyNameToWatcher(this.currentName);

        this.entityId = ENTITY_ID.get(null);
        ENTITY_ID.set(null, this.entityId + 1);
    }

    public void setName(String name) {
        if (this.watcher == null) return;

        String newName = name == null ? "" : name;
        if (Objects.equals(this.currentName, newName)) return;

        boolean wasDisabled = isDisabledName();
        this.currentName = newName;
        boolean willDisable = isDisabledName();
        applyNameToWatcher(this.currentName);

        if (this.disabled) return;

        if (wasDisabled && !willDisable) {
            forEachVisiblePlayer(this::showToPlayer);
        } else if (!wasDisabled && willDisable) {
            forEachVisiblePlayer(this::hideToPlayer);
        } else if (!willDisable) {
            broadcastMetadata();
        }
    }

    public void destroy() {
        this.disable();
        this.owner = null;
        this.watcher = null;
    }

    public void enable() {
        if (this.disabled) {
            this.disabled = false;
            if (!isDisabledName()) {
                forEachVisiblePlayer(this::showToPlayer);
            }
        }
    }

    public void disable() {
        if (!this.disabled) {
            forEachVisiblePlayer(this::hideToPlayer);
            this.disabled = true;
        }
    }

    void showToPlayer(Player player) {
        if (player == null || player.equals(this.owner) || this.owner == null) return;
        if (this.disabled || isDisabledName()) return;

        WrapperPlayServerSpawnEntityLiving spawn = new WrapperPlayServerSpawnEntityLiving();
        spawn.setType(EntityType.ARMOR_STAND);
        spawn.setEntityID(this.entityId);
        spawn.setMetadata(this.watcher);
        spawn.setX(this.owner.getLocation().getX());
        spawn.setY(this.owner.getLocation().getY());
        spawn.setZ(this.owner.getLocation().getZ());

        WrapperPlayServerAttachEntity attach = new WrapperPlayServerAttachEntity();
        attach.setEntityId(this.entityId);
        attach.setVehicleId(this.owner.getEntityId());

        spawn.sendPacket(player);
        attach.sendPacket(player);
    }

    void hideToPlayer(Player player) {
        if (player == null || player.equals(this.owner)) return;
        WrapperPlayServerEntityDestroy destroy = new WrapperPlayServerEntityDestroy();
        destroy.setEntities(new int[]{ this.entityId });
        destroy.sendPacket(player);
    }

    public Player getOwner() {
        return this.owner;
    }

    private void applyNameToWatcher(String name) {
        if (this.watcher == null) return;

        if (isDisabledName(name)) {
            ProtocolLibUtils.setCustomName(this.watcher, 2, "");
            ProtocolLibUtils.setCustomNameVisible(this.watcher, 3, false);
        } else {
            ProtocolLibUtils.setCustomName(this.watcher, 2, name);
            ProtocolLibUtils.setCustomNameVisible(this.watcher, 3, hasVisibleText(name));
        }
    }

    private void broadcastMetadata() {
        if (this.owner == null || this.watcher == null) return;

        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        PacketContainer packet = manager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().writeSafely(0, this.entityId);
        ProtocolLibUtils.writeMetadata(packet, this.watcher);

        forEachVisiblePlayer(player -> {
            if (player.equals(this.owner)) return;
            try {
                manager.sendServerPacket(player, packet);
            } catch (Exception e) { 
                Core instance = Core.getInstance();
                if (instance != null) {
                    instance.getLogger().log(Level.FINEST, "Failed to push title metadata", e);
                }
            }
        });
    }

    private void forEachVisiblePlayer(Consumer<Player> consumer) {
        if (this.owner == null) return;
        Profile.listProfiles().forEach(profile -> {
            Player player = profile.getPlayer();
            if (player != null && player.isOnline() && player.canSee(this.owner)) {
                consumer.accept(player);
            }
        });
    }

    private boolean hasVisibleText(String name) {
        if (name == null) return false;
        String coloured = ChatColor.translateAlternateColorCodes('&', name);
        return !ChatColor.stripColor(coloured).isEmpty();
    }

    private boolean isDisabledName() {
        return isDisabledName(this.currentName);
    }

    private boolean isDisabledName(String value) {
        return value != null && value.equalsIgnoreCase("disabled");
    }

    static {
        ENTITY_ID = Accessors.getField(MinecraftReflection.getEntityClass(), "entityCount", Integer.TYPE);
    }
}
