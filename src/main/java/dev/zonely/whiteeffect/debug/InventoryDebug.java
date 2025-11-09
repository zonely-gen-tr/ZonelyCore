package dev.zonely.whiteeffect.debug;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import dev.zonely.whiteeffect.Core;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class InventoryDebug implements Listener {

    private static final Set<UUID> WATCH = ConcurrentHashMap.newKeySet();

    public static void install() {
        Plugin plugin = Core.getInstance();
        Bukkit.getPluginManager().registerEvents(new InventoryDebug(), plugin);

        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            pm.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                    PacketType.Play.Client.HELD_ITEM_SLOT,
                    PacketType.Play.Client.WINDOW_CLICK,
                    PacketType.Play.Client.SET_CREATIVE_SLOT
            ) {
                @Override public void onPacketReceiving(PacketEvent e) {
                    Player p = e.getPlayer();
                    if (p == null) return;

                    PacketType t = e.getPacketType();
                    PacketContainer pc = e.getPacket();

                    if (t == PacketType.Play.Client.SET_CREATIVE_SLOT) {
                        if (p.getGameMode() == GameMode.CREATIVE) {
                            Integer raw = resolveCreativeSlot(pc);
                            ItemStack pktItem = readItemSafe(pc); 
                            int invIndex = mapCreativeRawSlotToPlayerIndex(raw);
                            if (invIndex >= 0) {
                                try {
                                    p.getInventory().setItem(invIndex, pktItem);
                                } catch (Throwable ignored) {}
                            }
                            if (WATCH.contains(p.getUniqueId())) {
                                clog("[PKTDBG] MIRROR SET_CREATIVE_SLOT p=%s raw=%s -> invIdx=%s item=%s",
                                        p.getName(), raw, invIndex, stack(pktItem));
                                pmsg(p, "PKT MIRROR SET_CREATIVE_SLOT raw=%s → idx=%s", raw, invIndex);
                            }
                        } else {
                            if (WATCH.contains(p.getUniqueId())) {
                                Integer slot = resolveCreativeSlot(pc);
                                clog("[PKTDBG] SET_CREATIVE_SLOT p=%s slot=%s (non-creative)", p.getName(), slot);
                            }
                        }
                        return; 
                    }

                    if (!WATCH.contains(p.getUniqueId())) return;

                    if (t == PacketType.Play.Client.HELD_ITEM_SLOT) {
                        Integer slot = readIntSafe(pc, 0);
                        clog("[PKTDBG] HELD_ITEM_SLOT p=%s slot=%s open=%s", p.getName(), slot, openTitle(p));
                        pmsg(p, "PKT HELD_ITEM_SLOT slot=%s", slot);
                    } else if (t == PacketType.Play.Client.WINDOW_CLICK) {
                        Integer slot = readIntSafe(pc, 1);
                        Integer button = readIntSafe(pc, 2);
                        Integer mode = readIntSafe(pc, 4);
                        clog("[PKTDBG] WINDOW_CLICK p=%s slot=%s button=%s mode=%s open=%s",
                                p.getName(), slot, button, mode, openTitle(p));
                        pmsg(p, "PKT WINDOW_CLICK slot=%s btn=%s mode=%s", slot, button, mode);
                    }
                }
            });
            plugin.getLogger().fine("[INVDBG] ProtocolLib hook active.");
        } catch (Throwable t) {
            plugin.getLogger().fine("[INVDBG] ProtocolLib not found/disabled. Packet debug skipped.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreprocess(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().trim();
        if (!msg.equalsIgnoreCase("/invdbg")) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (WATCH.contains(id)) {
            WATCH.remove(id);
            pmsg(p, "Debug §coff§7.");
            clog("[INVDBG] OFF for %s", p.getName());
        } else {
            WATCH.add(id);
            pmsg(p, "Debug §aon§7. Envanter hareketlerini bana ve konsola yazıyorum.");
            clog("[INVDBG] ON for %s", p.getName());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void clickLowest(InventoryClickEvent e) { if (isOn(e.getWhoClicked())) printClick("LOWEST", e); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void clickMonitor(InventoryClickEvent e) { if (isOn(e.getWhoClicked())) printClick("MON", e); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void dragLowest(InventoryDragEvent e) { if (isOn(e.getWhoClicked())) printDrag("LOWEST", e); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void dragMonitor(InventoryDragEvent e) { if (isOn(e.getWhoClicked())) printDrag("MON", e); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void dropLowest(PlayerDropItemEvent e) { if (isOn(e.getPlayer())) printDrop("LOWEST", e); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void dropMonitor(PlayerDropItemEvent e) { if (isOn(e.getPlayer())) printDrop("MON", e); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void heldLowest(PlayerItemHeldEvent e) { if (isOn(e.getPlayer())) printHeld("LOWEST", e); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void heldMonitor(PlayerItemHeldEvent e) { if (isOn(e.getPlayer())) printHeld("MON", e); }


    private void printClick(String stage, InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        Inventory top = e.getView().getTopInventory();
        Inventory bottom = e.getView().getBottomInventory();
        String clickedType = e.getClickedInventory()==null ? "-" : e.getClickedInventory().getType().name();
        ItemStack cur = e.getCurrentItem();
        ItemStack cursor = e.getCursor();

        String line = String.format(
                "[INVDBG:%s] CLICK p=%s cancel=%s act=%s click=%s raw=%d slot=%d hb=%d clickedInv=%s top=%s bottom=%s open=%s cur=%s cursor=%s shift=%s dbl=%s",
                stage, p.getName(), e.isCancelled(), e.getAction(), e.getClick(),
                e.getRawSlot(), e.getSlot(), e.getHotbarButton(),
                clickedType,
                (top==null?"-":top.getType()), (bottom==null?"-":bottom.getType()),
                openTitle(p),
                stack(cur), stack(cursor),
                e.isShiftClick(), e.getClick()== ClickType.DOUBLE_CLICK
        );
        clog(line); pmsg(p, line);
    }

    private void printDrag(String stage, InventoryDragEvent e) {
        Player p = (Player) e.getWhoClicked();
        ItemStack oldCur = getOldCursorSafe(e);
        String line = String.format("[INVDBG:%s] DRAG p=%s cancel=%s slots=%s oldCursor=%s open=%s",
                stage, p.getName(), e.isCancelled(), e.getRawSlots(), stack(oldCur), openTitle(p));
        clog(line); pmsg(p, line);
    }

    private void printDrop(String stage, PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        String line = String.format("[INVDBG:%s] DROP p=%s cancel=%s stack=%s at=%s,%s,%s",
                stage, p.getName(), e.isCancelled(),
                stack(e.getItemDrop()==null?null:e.getItemDrop().getItemStack()),
                p.getWorld().getName(), p.getLocation().getBlockX(), p.getLocation().getBlockZ());
        clog(line); pmsg(p, line);
    }

    private void printHeld(String stage, PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        String line = String.format("[INVDBG:%s] HELD p=%s cancel=%s from=%d to=%d open=%s",
                stage, p.getName(), e.isCancelled(),
                e.getPreviousSlot(), e.getNewSlot(), openTitle(p));
        clog(line); pmsg(p, line);
    }

    private static String stack(ItemStack s) {
        if (s == null || isAirType(s.getType())) return "-";
        String name = (s.hasItemMeta() && s.getItemMeta().hasDisplayName())
                ? Objects.toString(s.getItemMeta().getDisplayName(), "")
                : "";
        String meta = name.isEmpty() ? "" : ("|" + name.replace('§','&'));
        return s.getType().name() + "x" + s.getAmount() + meta;
    }

    private static boolean isAirType(Material m) {
        if (m == null) return true;
        try {
            Method isAir = Material.class.getMethod("isAir");
            Object r = isAir.invoke(m);
            if (r instanceof Boolean) return (Boolean) r;
        } catch (Throwable ignored) { }
        String n = m.name();
        return m == Material.AIR || n.endsWith("_AIR");
    }

    private static ItemStack getOldCursorSafe(InventoryDragEvent e) {
        try { return (ItemStack) InventoryDragEvent.class.getMethod("getOldCursor").invoke(e); }
        catch (Throwable ignored) {
            try { return (ItemStack) InventoryDragEvent.class.getMethod("getCursor").invoke(e); }
            catch (Throwable ignored2) { return null; }
        }
    }

    private static String openTitle(Player p) {
        try {
            String t = p.getOpenInventory().getTitle();
            return t==null?"-":t.replace('§','&');
        } catch (Throwable ignored) { return "-"; }
    }

    private static Integer readIntSafe(PacketContainer pc, int idx) {
        try { return pc.getIntegers().readSafely(idx); }
        catch (Throwable ignored) {
            try { return pc.getIntegers().read(idx); }
            catch (Throwable ignored2) { return -1; }
        }
    }

    private static ItemStack readItemSafe(PacketContainer pc) {
        try { return pc.getItemModifier().readSafely(0); }
        catch (Throwable ignored) {
            try { return pc.getItemModifier().read(0); }
            catch (Throwable ignored2) { return null; }
        }
    }

    private static Integer resolveCreativeSlot(PacketContainer pc) {
        try {
            Short shortSlot = pc.getShorts().readSafely(0);
            if (shortSlot != null) return shortSlot.intValue();
        } catch (Throwable ignored) {}
        try {
            Integer indexed = pc.getIntegers().readSafely(1);
            if (indexed != null) return indexed;
        } catch (Throwable ignored) {}
        try {
            Object raw = pc.getSpecificModifier(java.util.OptionalInt.class).readSafely(0);
            if (raw instanceof Integer) return (Integer) raw;
            if (raw instanceof java.util.OptionalInt) {
                java.util.OptionalInt opt = (java.util.OptionalInt) raw;
                return opt.isPresent() ? opt.getAsInt() : -1;
            }
        } catch (Throwable ignored) {}
        Integer legacy = readIntSafe(pc, 0);
        return legacy != null ? legacy : -1;
    }

    private static int mapCreativeRawSlotToPlayerIndex(Integer raw) {
        if (raw == null || raw < 0) return -1;
        if (raw >= 36 && raw <= 44) return raw - 36;
        if (raw >= 9 && raw <= 35) return raw;
        return -1;
    }

    private static boolean isOn(Object who) {
        if (!(who instanceof Player)) return false;
        return WATCH.contains(((Player) who).getUniqueId());
    }

    private static void clog(String fmt, Object... args) {
        String line = String.format(fmt, args);
        try { Core.getInstance().getLogger().warning(line); } catch (Throwable ignored) {}
        try { System.out.println(line); } catch (Throwable ignored) {}
    }

    private static void pmsg(Player p, String fmt, Object... args) {
        if (p == null) return;
        try { p.sendMessage("§c[INVDBG] §7" + String.format(fmt, args).replace('&','§')); }
        catch (Throwable ignored) {}
    }

}

