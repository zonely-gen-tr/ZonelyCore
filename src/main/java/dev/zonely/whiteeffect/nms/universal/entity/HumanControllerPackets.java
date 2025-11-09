package dev.zonely.whiteeffect.nms.universal.entity;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.comphenix.protocol.wrappers.WrappedRemoteChatSessionData;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.libraries.npclib.NPCLibrary;
import dev.zonely.whiteeffect.libraries.npclib.api.npc.NPC;
import dev.zonely.whiteeffect.libraries.npclib.npc.AbstractEntityController;
import dev.zonely.whiteeffect.replay.NPCReplayManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class HumanControllerPackets extends AbstractEntityController {
    public static final String REPLAY_SKIN_VALUE_KEY = "replay-skin-value";
    public static final String REPLAY_SKIN_SIGNATURE_KEY = "replay-skin-signature";

    private static int nextEntityId() {
        return ThreadLocalRandom.current().nextInt(1_000_000, Integer.MAX_VALUE);
    }

    private int packetEntityId;
    private UUID uuid;
    private String name16;
    private Location spawnLoc;
    private static boolean equipmentPacketsSupported = detectEquipmentSupport();
    private static boolean equipmentWarningLogged = false;
    private static final Constructor<?> PAIR_CONSTRUCTOR = resolvePairConstructor();
    private static final Method SLOT_PAIR_LIST_METHOD = resolveSlotStackMethod();
    private static final Constructor<PlayerInfoData> MODERN_PLAYER_INFO_CTOR = resolveModernPlayerInfoCtor();
    private String skinValue;
    private String skinSignature;
    private boolean copyViewerSkin;
    private UUID permittedViewer;

    private static final AtomicInteger LIST_ORDER = new AtomicInteger();

    @Override
    protected Entity createEntity(Location location, NPC npc) {
        this.spawnLoc = location.clone();
        this.uuid = npc.getUUID();
        this.name16 = npc.getName().length() > 16 ? npc.getName().substring(0, 16) : npc.getName();
        this.packetEntityId = nextEntityId();
        this.copyViewerSkin = npc.data().get(NPC.COPY_PLAYER_SKIN, false);
        Object valueObj = npc.data().get(REPLAY_SKIN_VALUE_KEY);
        this.skinValue = valueObj instanceof String ? (String) valueObj : null;
        Object signatureObj = npc.data().get(REPLAY_SKIN_SIGNATURE_KEY);
        this.skinSignature = signatureObj instanceof String ? (String) signatureObj : null;
        Object viewerObj = npc.data().get(NPCReplayManager.REPLAY_VIEWER_KEY);
        this.permittedViewer = resolveViewer(viewerObj);

        ArmorStand anchor = Objects.requireNonNull(location.getWorld()).spawn(location, ArmorStand.class);
        try { anchor.setVisible(false); } catch (Throwable ignored) {}
        callIfPresent(anchor, "setInvisible", Boolean.TYPE, true);
        try { anchor.getClass().getMethod("setMarker", boolean.class).invoke(anchor, true); } catch (Throwable ignored) {}
        try { anchor.setSmall(true); } catch (Throwable ignored) {}
        callIfPresent(anchor, "setGravity", Boolean.TYPE, false);
        callIfPresent(anchor, "setCollidable", Boolean.TYPE, false);
        try { anchor.setCustomNameVisible(false); } catch (Throwable ignored) {}
        try { anchor.getClass().getMethod("setBasePlate", boolean.class).invoke(anchor, false); } catch (Throwable ignored) {}
        try { anchor.getClass().getMethod("setAI", boolean.class).invoke(anchor, false); } catch (Throwable ignored) {}
        try { anchor.getClass().getMethod("setInvulnerable", boolean.class).invoke(anchor, true); } catch (Throwable ignored) {}
        callIfPresent(anchor, "setSilent", Boolean.TYPE, true);
        callIfPresent(anchor, "setPersistent", Boolean.TYPE, false);
        anchor.setMetadata("NPC", new FixedMetadataValue(Core.getInstance(), true));
        PacketNPCManager.register(this, anchor);

        World w = location.getWorld();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(w)) continue;
            if (!isViewer(p)) continue;
            sendSpawnPackets(p);
            scheduleTabHide(p);
        }

        return anchor;
    }

    private static void callIfPresent(Object target, String methodName, Class<?> argType, Object value) {
        try { target.getClass().getMethod(methodName, argType).invoke(target, value); } catch (Throwable ignored) {}
    }

    @Override
    public void remove() {
        if (this.spawnLoc != null) {
            World w = this.spawnLoc.getWorld();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (w != null && p.getWorld().equals(w) && isViewer(p)) {
                    sendDestroy(p);
                    sendTabRemove(p);
                }
            }
        }
        Entity anchor = this.getBukkitEntity();
        PacketNPCManager.unregister(this, anchor);
        super.remove();
    }


    void sendSpawnPackets(Player receiver) {
        if (!isViewer(receiver)) return;

        PacketContainer infoAdd = createServerPacketSmart(
                new String[]{"PLAYER","INFO","UPDATE"},
                new String[]{"PLAYER","INFO"}
        );
        if (infoAdd == null) return;

        writePlayerInfoActions(infoAdd,
                EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                EnumWrappers.PlayerInfoAction.UPDATE_LISTED,
                EnumWrappers.PlayerInfoAction.UPDATE_LATENCY,
                EnumWrappers.PlayerInfoAction.UPDATE_GAME_MODE,
                EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME
        );

        WrappedGameProfile profile = new WrappedGameProfile(this.uuid, this.name16);

        if (!this.copyViewerSkin && hasCustomSkin()) {
            try { profile.getProperties().clear(); } catch (UnsupportedOperationException ignored) {
                try { profile.getProperties().removeAll("textures"); } catch (Throwable ignored2) {}
            }
            profile.getProperties().put("textures", new WrappedSignedProperty("textures", this.skinValue, this.skinSignature));
        }

        List<PlayerInfoData> list = buildPlayerInfoEntries(profile, true);
        writePlayerInfoData(infoAdd, list);

        PacketContainer spawn = createServerPacketSmart(
                new String[]{"NAMED","ENTITY","SPAWN"},
                new String[]{"SPAWN","PLAYER"},
                new String[]{"SPAWN","ENTITY"}
        );
        if (spawn == null) return;

        spawn.getIntegers().writeSafely(0, this.packetEntityId);
        spawn.getUUIDs().writeSafely(0, this.uuid);
        spawn.getDoubles().writeSafely(0, this.spawnLoc.getX());
        spawn.getDoubles().writeSafely(1, this.spawnLoc.getY());
        spawn.getDoubles().writeSafely(2, this.spawnLoc.getZ());
        byte yaw = toAngle(this.spawnLoc.getYaw());
        byte pitch = toAngle(this.spawnLoc.getPitch());
        spawn.getBytes().writeSafely(0, yaw);
        spawn.getBytes().writeSafely(1, pitch);

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(receiver, infoAdd);
            ProtocolLibrary.getProtocolManager().sendServerPacket(receiver, spawn);
        } catch (Exception e) { 
            e.printStackTrace();
        }
    }

    private void sendTabRemove(Player receiver) {
        if (!isViewer(receiver)) return;

        PacketContainer infoRemove = createServerPacketSmart(new String[]{"PLAYER","INFO","REMOVE"});
        boolean usedRemovePacket = false;
        if (infoRemove != null) {
            try {
                infoRemove.getUUIDLists().writeSafely(0, Collections.singletonList(this.uuid));
                usedRemovePacket = true;
            } catch (Throwable ignored) {
                try {
                    @SuppressWarnings("unchecked")
                    StructureModifier<List> mod = (StructureModifier<List>) infoRemove.getSpecificModifier(List.class);
                    if (mod != null) {
                        mod.writeSafely(0, Collections.singletonList(this.uuid));
                        usedRemovePacket = true;
                    }
                } catch (Throwable ignored2) {
                    usedRemovePacket = false;
                }
            }
        }

        if (!usedRemovePacket) {
            infoRemove = createServerPacketSmart(
                    new String[]{"PLAYER","INFO","UPDATE"},
                    new String[]{"PLAYER","INFO"}
            );
            if (infoRemove == null) return;

            writePlayerInfoActions(infoRemove,
                    EnumWrappers.PlayerInfoAction.REMOVE_PLAYER,
                    EnumWrappers.PlayerInfoAction.UPDATE_LISTED
            );

            WrappedGameProfile profile = new WrappedGameProfile(this.uuid, this.name16);
            List<PlayerInfoData> list = buildPlayerInfoEntries(profile, false);
            writePlayerInfoData(infoRemove, list);
        }

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(receiver, infoRemove);
        } catch (Exception e) { 
            e.printStackTrace();
        }
    }

    void scheduleTabHide(Player receiver) {
        if (receiver == null || !receiver.isOnline()) return;
        if (!isViewer(receiver)) return;
        Bukkit.getScheduler().runTaskLater(NPCLibrary.getPlugin(), () -> {
            if (receiver.isOnline()) sendTabRemove(receiver);
        }, 20L);
        Bukkit.getScheduler().runTaskLater(NPCLibrary.getPlugin(), () -> {
            if (receiver.isOnline()) sendTabRemove(receiver);
        }, 200L);
    }


    private void sendDestroy(Player receiver) {
        if (!isViewer(receiver)) return;

        PacketContainer destroy = createServerPacketSmart(
                new String[]{"ENTITY","DESTROY"},
                new String[]{"REMOVE","ENTITIES"},
                new String[]{"REMOVE","ENTITY"}
        );
        if (destroy == null) return;

        destroy.getIntegerArrays().writeSafely(0, new int[]{this.packetEntityId});

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(receiver, destroy);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    void handleTeleport(Location newLocation, boolean updateRotation) {
        if (newLocation == null) return;

        this.spawnLoc = newLocation.clone();
        World world = this.spawnLoc.getWorld();
        if (world == null && newLocation.getWorld() != null) world = newLocation.getWorld();
        if (world == null) return;

        Entity anchor = this.getBukkitEntity();
        if (anchor != null && anchor.isValid()) {
            try { anchor.teleport(newLocation, TeleportCause.PLUGIN); }
            catch (Throwable ignored) { try { anchor.teleport(newLocation); } catch (Throwable ignored2) {} }
        }

        PacketContainer teleport = createServerPacketSmart(
                new String[]{"ENTITY","TELEPORT"},
                new String[]{"TELEPORT","ENTITY"}
        );
        if (teleport == null) return;

        teleport.getIntegers().writeSafely(0, this.packetEntityId);
        teleport.getDoubles().writeSafely(0, newLocation.getX());
        teleport.getDoubles().writeSafely(1, newLocation.getY());
        teleport.getDoubles().writeSafely(2, newLocation.getZ());
        byte yaw = toAngle(newLocation.getYaw());
        byte pitch = toAngle(newLocation.getPitch());
        teleport.getBytes().writeSafely(0, yaw);
        teleport.getBytes().writeSafely(1, pitch);
        try { teleport.getBooleans().writeSafely(0, true); } catch (Exception ignored) {}

        PacketContainer look = null;
        PacketContainer head = null;
        if (updateRotation) {
            look = createServerPacketSmart(
                    new String[]{"ENTITY","LOOK"},
                    new String[]{"ENTITY","ROTATION"}
            );
            if (look != null) {
                look.getIntegers().writeSafely(0, this.packetEntityId);
                look.getBytes().writeSafely(0, yaw);
                look.getBytes().writeSafely(1, pitch);
                try { look.getBooleans().writeSafely(0, true); } catch (Exception ignored) {}
            }

            head = createServerPacketSmart(
                    new String[]{"ENTITY","HEAD","ROTATION"},
                    new String[]{"HEAD","ROTATION"},
                    new String[]{"ROTATE","HEAD"}
            );
            if (head != null) {
                head.getIntegers().writeSafely(0, this.packetEntityId);
                head.getBytes().writeSafely(0, yaw);
            }
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getWorld().equals(world) || !isViewer(viewer)) continue;
            try {
                ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, teleport);
                if (updateRotation && look != null && head != null) {
                    ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, look);
                    ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, head);
                }
            } catch (Exception e) { 
                e.printStackTrace();
            }
        }
    }


    void handleEquipment(int slotIndex, ItemStack item) {
        if (!equipmentPacketsSupported) { logEquipmentWarningOnce(); return; }
        if (this.spawnLoc == null || this.spawnLoc.getWorld() == null) return;

        EnumWrappers.ItemSlot slot = resolveSlot(slotIndex);
        ItemStack clone = item == null ? new ItemStack(Material.AIR) : item.clone();

        PacketContainer equipment = createServerPacketSmart(
                new String[]{"ENTITY","EQUIPMENT"}
        );
        if (equipment == null) { logEquipmentWarningOnce(); return; }
        equipment.getIntegers().writeSafely(0, this.packetEntityId);

        boolean composed = false;
        try {
            Object pair = makePair(slot, clone);
            if (pair != null && SLOT_PAIR_LIST_METHOD != null) {
                try {
                    @SuppressWarnings("unchecked")
                    StructureModifier<List> modifier = (StructureModifier<List>) SLOT_PAIR_LIST_METHOD.invoke(equipment);
                    if (modifier != null) {
                        modifier.writeSafely(0, Collections.singletonList(pair));
                        composed = true;
                    }
                } catch (Throwable ignored) {}
            }
            if (!composed) {
                equipment.getItemSlots().writeSafely(0, slot);
                equipment.getItemModifier().writeSafely(0, clone);
                composed = true;
            }
        } catch (Throwable t) {
            equipmentPacketsSupported = false;
            logEquipmentWarningOnce();
            Core.getInstance().getLogger().log(Level.FINEST, "Failed to compose NPC equipment packet", t);
            return;
        }
        if (!composed) {
            equipmentPacketsSupported = false;
            logEquipmentWarningOnce();
            return;
        }

        World world = this.spawnLoc.getWorld();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getWorld().equals(world) || !isViewer(viewer)) continue;
            try {
                ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, equipment);
            } catch (Exception e) { 
                Core.getInstance().getLogger().log(Level.FINEST, "Failed to dispatch NPC equipment packet", e);
            }
        }
    }

    private EnumWrappers.ItemSlot resolveSlot(int slotIndex) {
        switch (slotIndex) {
            case 0: return EnumWrappers.ItemSlot.HEAD;
            case 1: return EnumWrappers.ItemSlot.CHEST;
            case 2: return EnumWrappers.ItemSlot.LEGS;
            case 3: return EnumWrappers.ItemSlot.FEET;
            case 4: return EnumWrappers.ItemSlot.OFFHAND;
            case 5:
            default: return EnumWrappers.ItemSlot.MAINHAND;
        }
    }


    private static final Set<String> dumpedOnce = new HashSet<>();
    private static final AtomicBoolean missingPacketWarned = new AtomicBoolean(false);

    private static PacketContainer createServerPacketSmart(String[]... nameTokensCandidates) {
        for (String[] tokens : nameTokensCandidates) {
            String joined = String.join("_", tokens).toUpperCase(Locale.ROOT);
            PacketType type = lookupServerPacketTypeExact(joined);
            PacketContainer c = tryInstantiate(type);
            if (c != null) return c;
        }
        for (String[] tokens : nameTokensCandidates) {
            PacketType type = lookupServerPacketTypeFuzzy(tokens);
            PacketContainer c = tryInstantiate(type);
            if (c != null) return c;
        }
        logAvailableOnce("PacketType.Play.Server");
        warnMissingPackets(nameTokensCandidates);
        return null;
    }

    private static PacketContainer tryInstantiate(PacketType type) {
        if (type == null) return null;
        try {
            PacketContainer c = ProtocolLibrary.getProtocolManager().createPacket(type);
            if (c != null) return c;
        } catch (Throwable ignored) {}
        try {
            PacketContainer c = new PacketContainer(type);
            try { c.getModifier().writeDefaults(); } catch (Throwable ignored) {}
            return c;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static PacketType lookupServerPacketTypeExact(String name) {
        try {
            Field f = PacketType.Play.Server.class.getField(name);
            Object v = f.get(null);
            return (v instanceof PacketType) ? (PacketType) v : null;
        } catch (Throwable ignored) { return null; }
    }

    private static PacketType lookupServerPacketTypeFuzzy(String[] tokens) {
        String[] toks = Arrays.stream(tokens)
                .filter(Objects::nonNull)
                .map(s -> s.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT))
                .toArray(String[]::new);
        try {
            for (Field f : PacketType.Play.Server.class.getFields()) {
                if (!PacketType.class.isAssignableFrom(f.getType())) continue;
                String fname = f.getName();
                String norm = fname.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
                boolean ok = true;
                for (String t : toks) {
                    if (!norm.contains(t)) { ok = false; break; }
                }
                if (ok) {
                    Object v = f.get(null);
                    if (v instanceof PacketType) return (PacketType) v;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void logAvailableOnce(String key) {
        if (!dumpedOnce.add(key)) return;
        try {
            StringBuilder sb = new StringBuilder("Available server packet names: ");
            boolean first = true;
            for (Field f : PacketType.Play.Server.class.getFields()) {
                if (!PacketType.class.isAssignableFrom(f.getType())) continue;
                if (!first) sb.append(", ");
                first = false;
                sb.append(f.getName());
            }
        } catch (Throwable ignored) { }
    }

    private static void warnMissingPackets(String[][] candidates) {
        if (!missingPacketWarned.compareAndSet(false, true)) return;
        Core core = Core.getInstance();
        if (core == null) return;
        StringBuilder needed = new StringBuilder();
        for (String[] tokens : candidates) {
            if (needed.length() > 0) needed.append(" | ");
            needed.append(String.join("_", tokens));
        }
    }


    @SuppressWarnings("unchecked")
    private List<PlayerInfoData> buildPlayerInfoEntries(WrappedGameProfile profile, boolean listed) {
        List<PlayerInfoData> entries = new ArrayList<>(1);

        Constructor<PlayerInfoData> ctor = MODERN_PLAYER_INFO_CTOR;
        if (ctor != null) {
            try {
                int order = LIST_ORDER.getAndIncrement();
                PlayerInfoData modern = ctor.newInstance(
                        this.uuid,
                        0,
                        listed,
                        EnumWrappers.NativeGameMode.SURVIVAL,
                        profile,
                        (WrappedChatComponent) null,
                        true,
                        order,
                        (WrappedRemoteChatSessionData) null
                );
                entries.add(modern);
                return entries;
            } catch (Throwable ignored) {}
        }

        try {
            PlayerInfoData legacy = new PlayerInfoData(
                    this.uuid,
                    0,
                    listed,
                    EnumWrappers.NativeGameMode.SURVIVAL,
                    profile,
                    (WrappedChatComponent) null
            );
            entries.add(legacy);
            return entries;
        } catch (Throwable ignored) { }

        try {
            PlayerInfoData veryLegacy = new PlayerInfoData(
                    profile,
                    0,
                    EnumWrappers.NativeGameMode.SURVIVAL,
                    (WrappedChatComponent) null
            );
            entries.add(veryLegacy);
        } catch (Throwable ignored) {}

        return entries;
    }

    private static void writePlayerInfoActions(PacketContainer packet, EnumWrappers.PlayerInfoAction... actions) {
        if (packet == null || actions == null || actions.length == 0) return;

        EnumSet<EnumWrappers.PlayerInfoAction> set = EnumSet.noneOf(EnumWrappers.PlayerInfoAction.class);
        Collections.addAll(set, actions);

        try {
            Method m = PacketContainer.class.getMethod("getPlayerInfoActions");
            Object mod = m.invoke(packet);
            if (mod instanceof StructureModifier) {
                StructureModifier<EnumSet<EnumWrappers.PlayerInfoAction>> sm =
                        (StructureModifier<EnumSet<EnumWrappers.PlayerInfoAction>>) mod;
                sm.writeSafely(0, set);
                return;
            }
        } catch (Throwable ignored) {}
        try {
            packet.getPlayerInfoAction().writeSafely(0, actions[0]);
            return;
        } catch (Throwable ignored) {}

        try {
            @SuppressWarnings("rawtypes")
            StructureModifier<EnumSet> generic = (StructureModifier<EnumSet>) packet.getSpecificModifier(EnumSet.class);
            if (generic != null && generic.size() > 0) {
                generic.writeSafely(0, set);
                return;
            }
        } catch (Throwable ignored) {}

        Core.getInstance().getLogger().warning("[NPC] Could not write PlayerInfo actions; check ProtocolLib build.");
    }

    private static final EquivalentConverter<PlayerInfoData> PLAYER_INFO_CONVERTER = resolvePlayerInfoConverter();

    private static void writePlayerInfoData(PacketContainer packet, List<PlayerInfoData> data) {
        if (packet == null || data == null) return;

        List<?> nativeEntries = convertPlayerInfoData(data);
        if (nativeEntries != null) {
            if (writePlayerInfoNativeList(packet, nativeEntries)) return;
        }

        if (writePlayerInfoWrappedList(packet, data)) return;

        Object singleNative = (nativeEntries != null && !nativeEntries.isEmpty()) ? nativeEntries.get(0) : null;
        if (writeSinglePlayerInfo(packet, singleNative)) return;

        if (!data.isEmpty()) {
            writeSinglePlayerInfo(packet, data.get(0));
        }
    }

    private static boolean writePlayerInfoWrappedList(PacketContainer packet, List<PlayerInfoData> list) {
        if (list == null) return false;
        try { packet.getPlayerInfoDataLists().writeSafely(0, list); return true; } catch (Throwable ignored) {}
        try { packet.getSpecificModifier(List.class).writeSafely(0, list); return true; } catch (Throwable ignored) {}
        return false;
    }

    private static boolean writePlayerInfoNativeList(PacketContainer packet, List<?> list) {
        if (list == null) return false;
        try { packet.getSpecificModifier(List.class).writeSafely(0, list); return true; } catch (Throwable ignored) {}
        return false;
    }

    private static boolean writeSinglePlayerInfo(PacketContainer packet, Object entry) {
        if (packet == null || entry == null) return false;
        try {
            Method method = PacketContainer.class.getMethod("getPlayerInfoData");
            Object modifier = method.invoke(packet);
            if (modifier instanceof StructureModifier<?>) {
                @SuppressWarnings({"rawtypes","unchecked"})
                StructureModifier<Object> single = (StructureModifier<Object>) (StructureModifier) modifier;
                single.writeSafely(0, entry);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static List<?> convertPlayerInfoData(List<PlayerInfoData> data) {
        EquivalentConverter<PlayerInfoData> converter = PLAYER_INFO_CONVERTER;
        if (converter == null || data == null) return null;
        try {
            List<Object> converted = new ArrayList<>(data.size());
            for (PlayerInfoData entry : data) {
                if (entry == null) continue;
                Object handle = converter.getGeneric(entry);
                if (handle != null) {
                    converted.add(handle);
                }
            }
            return converted;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static EquivalentConverter<PlayerInfoData> resolvePlayerInfoConverter() {
        try { return PlayerInfoData.getConverter(); }
        catch (Throwable ignored) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static Constructor<PlayerInfoData> resolveModernPlayerInfoCtor() {
        try {
            return (Constructor<PlayerInfoData>) PlayerInfoData.class.getConstructor(
                    UUID.class,
                    int.class,
                    boolean.class,
                    EnumWrappers.NativeGameMode.class,
                    WrappedGameProfile.class,
                    WrappedChatComponent.class,
                    boolean.class,
                    int.class,
                    WrappedRemoteChatSessionData.class
            );
        } catch (Throwable ignored) {
            return null;
        }
    }


    private static byte toAngle(float yaw) {
        return (byte) (Math.floorMod((int) (yaw * 256.0F / 360.0F), 256));
    }

    private UUID resolveViewer(Object viewerObj) {
        if (viewerObj instanceof UUID) return (UUID) viewerObj;
        if (viewerObj instanceof String) { try { return UUID.fromString((String) viewerObj); } catch (IllegalArgumentException ignored) {} }
        return null;
    }

    private boolean isViewer(Player player) {
        if (player == null) return false;
        if (this.permittedViewer == null) return true;
        return this.permittedViewer.equals(player.getUniqueId());
    }

    private boolean hasCustomSkin() {
        return this.skinValue != null && !this.skinValue.isEmpty()
                && this.skinSignature != null && !this.skinSignature.isEmpty();
    }

    private void logEquipmentWarningOnce() {
        if (!equipmentWarningLogged) {
            Core.getInstance().getLogger().warning("Skipping NPC equipment packets to prevent client disconnects. Upgrade server/ProtocolLib for live equipment playback.");
            equipmentWarningLogged = true;
        }
    }

    private static boolean detectEquipmentSupport() {
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            String version = pkg.substring(pkg.lastIndexOf('.') + 1);
            if (version.startsWith("v1_8_") || version.startsWith("v1_7_")) {
                Core.getInstance().getLogger().warning("Legacy server " + version + " detected. NPC equipment packets will be skipped.");
                return false;
            }
        } catch (Throwable ignored) {}

        try {
            PacketContainer.class.getMethod("getItemSlots");
            PacketContainer.class.getMethod("getItemModifier");
            return true;
        } catch (Throwable ignored) {
            Core.getInstance().getLogger().warning("ProtocolLib build does not expose item slot modifiers; NPC equipment sync disabled.");
            return false;
        }
    }

    private static Constructor<?> resolvePairConstructor() {
        try {
            Class<?> pairClass = Class.forName("com.comphenix.protocol.wrappers.Pair");
            Constructor<?> ctor = pairClass.getConstructor(Object.class, Object.class);
            ctor.setAccessible(true);
            return ctor;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object makePair(EnumWrappers.ItemSlot slot, ItemStack item) {
        if (PAIR_CONSTRUCTOR == null) return null;
        try { return PAIR_CONSTRUCTOR.newInstance(slot, item); } catch (Throwable ignored) { return null; }
    }

    private static Method resolveSlotStackMethod() {
        try {
            Method method = PacketContainer.class.getMethod("getSlotStackPairLists");
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) { return null; }
    }
}
