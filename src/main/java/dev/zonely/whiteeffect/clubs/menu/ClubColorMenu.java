package dev.zonely.whiteeffect.clubs.menu;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.clubs.ClubManager;
import dev.zonely.whiteeffect.clubs.ClubLang;
import dev.zonely.whiteeffect.clubs.model.ClubData;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ClubColorMenu extends PlayerMenu {

    private static final int[] COLOR_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29
    };
    private static final int INFO_SLOT = 4;
    private static final int CLOSE_SLOT = 31;

    private static final Map<DyeColor, String> COLOR_MAP = new LinkedHashMap<>();

    static {
        COLOR_MAP.put(DyeColor.WHITE, "#FFFFFF");
        COLOR_MAP.put(DyeColor.ORANGE, "#FF8C00");
        COLOR_MAP.put(DyeColor.MAGENTA, "#FF00FF");
        COLOR_MAP.put(DyeColor.LIGHT_BLUE, "#55FFFF");
        COLOR_MAP.put(DyeColor.YELLOW, "#FFFF55");
        COLOR_MAP.put(DyeColor.LIME, "#00FF00");
        COLOR_MAP.put(DyeColor.PINK, "#FFB6C1");
        COLOR_MAP.put(DyeColor.GRAY, "#555555");
        DyeColor lightGray = resolveColorVariant("LIGHT_GRAY", "SILVER");
        if (lightGray != null) {
            COLOR_MAP.put(lightGray, "#AAAAAA");
        }
        COLOR_MAP.put(DyeColor.CYAN, "#00AAAA");
        COLOR_MAP.put(DyeColor.PURPLE, "#AA00AA");
        COLOR_MAP.put(DyeColor.BLUE, "#0000FF");
        COLOR_MAP.put(DyeColor.BROWN, "#8B4513");
        COLOR_MAP.put(DyeColor.GREEN, "#008000");
        COLOR_MAP.put(DyeColor.RED, "#FF0000");
        COLOR_MAP.put(DyeColor.BLACK, "#000000");
    }

    private final Core plugin;
    private final ColorSelectionHandler handler;

    public interface ColorSelectionHandler {
        void handle(String hex, String colorName, Player player);
    }

    public ClubColorMenu(Core plugin, Player player, ClubManager.ClubCreationSession session) {
        this(plugin, player, (hex, name, p) -> {
            session.setColor(hex, name);
            p.sendMessage(ClubLang.get("menus.color.messages.set", "&aClub color set to {color}.", "color", name));
            PlayerMenu menu = session.openMenu(plugin, p);
            menu.register(plugin);
            menu.open();
        });
    }

    public ClubColorMenu(Core plugin, Player player, ClubData club, ClubManager manager) {
        this(plugin, player, (hex, name, p) -> {
            if (manager.updateColor(p, club, hex, name)) {
                manager.getClubByOwner(p).ifPresent(updated -> {
                    ClubManagementMenu menu = new ClubManagementMenu(plugin, p, updated);
                    menu.register(plugin);
                    menu.open();
                });
            }
        });
    }

    public ClubColorMenu(Core plugin, Player player, ColorSelectionHandler handler) {
        super(player, ClubLang.get("menus.color.title", "&2Color Picker"), 4);
        this.plugin = plugin;
        this.handler = handler;
        populate();
    }

    private static DyeColor resolveColorVariant(String primary, String fallback) {
        DyeColor color = tryColor(primary);
        if (color == null) {
            color = tryColor(fallback);
        }
        return color;
    }

    private static DyeColor tryColor(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        try {
            return DyeColor.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void populate() {
        fillBackground();
        setItem(INFO_SLOT, BukkitUtils.deserializeItemStack(
                "NETHER_STAR : 1 : name>" + ClubLang.get("menus.color.info.name", "&b&lSelect Club Color")
                        + " : desc>" + ClubLang.get("menus.color.info.lore.1", "&7Choose one of the preset colors.")
                        + "\\n" + ClubLang.get("menus.color.info.lore.2", "&7The color will be used in club")
                        + "\\n" + ClubLang.get("menus.color.info.lore.3", "&7identity and banner styling.")
        ));
        setItem(CLOSE_SLOT, BukkitUtils.deserializeItemStack(
                "BARRIER : 1 : name>" + ClubLang.get("menus.color.close.name", "&c&lClose")
                        + " : desc>" + ClubLang.get("menus.color.close.lore", "&7Close this menu.")
        ));

        int slot = 0;
        for (Map.Entry<DyeColor, String> entry : COLOR_MAP.entrySet()) {
            if (slot >= COLOR_SLOTS.length) {
                break;
            }
            DyeColor dye = entry.getKey();
            String token = resolveWoolToken(dye) + " : 1 : name>&e"
                    + ClubLang.get("menus.color.option.name", "{name}", "name", dye.name())
                    + " : desc>" + ClubLang.get("menus.color.option.hex", "&7Hex: {hex}", "hex", entry.getValue());
            setItem(COLOR_SLOTS[slot++], BukkitUtils.deserializeItemStack(token));
        }
    }

    private void fillBackground() {
        for (int slot = 0; slot < getInventory().getSize(); slot++) {
            boolean edge = slot < 9 || slot >= getInventory().getSize() - 9 || slot % 9 == 0 || slot % 9 == 8;
            setItem(slot, BukkitUtils.deserializeItemStack(
                    (edge ? "STAINED_GLASS_PANE:11" : "STAINED_GLASS_PANE:7") + " : 1 : name>&r"));
        }
    }

    private String resolveWoolToken(DyeColor color) {
        Material resolved = MaterialResolver.resolveMaterial(color.name() + "_WOOL");
        if (resolved != null) {
            return resolved.name();
        }
        return "WOOL:" + color.getWoolData();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(player)) return;
        if (!event.getView().getTopInventory().equals(getInventory())) return;
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        int colorIndex = -1;
        for (int i = 0; i < COLOR_SLOTS.length; i++) {
            if (COLOR_SLOTS[i] == slot) {
                colorIndex = i;
                break;
            }
        }
        if (colorIndex < 0 || colorIndex >= COLOR_MAP.size()) {
            return;
        }
        DyeColor color = (DyeColor) COLOR_MAP.keySet().toArray()[colorIndex];
        String hex = COLOR_MAP.get(color);
        player.closeInventory();
        handler.handle(hex, color.name(), player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!event.getWhoClicked().equals(this.player)) {
            return;
        }
        if (!event.getView().getTopInventory().equals(getInventory())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                event.setCancelled(true);
                event.setResult(org.bukkit.event.Event.Result.DENY);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(this.player) && event.getInventory().equals(getInventory())) {
            HandlerList.unregisterAll(this);
        }
    }
}
