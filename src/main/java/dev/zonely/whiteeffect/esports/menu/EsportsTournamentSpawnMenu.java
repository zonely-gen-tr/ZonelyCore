package dev.zonely.whiteeffect.esports.menu;

import com.google.gson.JsonObject;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.esports.EsportsLang;
import dev.zonely.whiteeffect.esports.menu.EsportsCreationService.CreationSession;
import dev.zonely.whiteeffect.esports.model.EsportsSpawnPoint;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

public final class EsportsTournamentSpawnMenu extends PlayerMenu {

    private static final int[] SLOT_LAYOUT = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int INFO_SLOT = 40;
    private static final int AUTO_SLOT = 45;
    private static final int BACK_SLOT = 49;

    private final Core plugin;
    private final EsportsCreationService service;
    private final CreationSession session;

    public EsportsTournamentSpawnMenu(Core plugin,
                                      Player player,
                                      EsportsCreationService service,
                                      CreationSession session) {
        super(player, EsportsLang.get("menus.spawn.title", "&2Spawn Points"), 6);
        this.plugin = plugin;
        this.service = service;
        this.session = session;
        render();
        this.register(plugin);
        this.open();
    }

    private void render() {
        int required = session.getRequiredSpawnSlots();
        for (int index = 0; index < SLOT_LAYOUT.length; index++) {
            int slot = SLOT_LAYOUT[index];
            if (index >= required) {
                setItem(slot, BukkitUtils.deserializeItemStack("BARRIER : 1 : name>"
                        + EsportsLang.get("menus.spawn.inactive.name", "&cInactive Slot")));
                continue;
            }
            JsonObject spawn = session.getSpawnPoint(index);
            EsportsSpawnPoint point = spawn == null ? null : EsportsSpawnPoint.fromJson(spawn);
            String status = spawn == null
                    ? EsportsLang.get("menus.spawn.status.not-set", "&cNot set")
                    : "&a" + format(point);
            String desc = EsportsLang.get("menus.spawn.slot.left", "&7Left click: save your current location.")
                    + "\\n" + EsportsLang.get("menus.spawn.slot.right", "&7Right click: clear this slot.");
            setItem(slot, BukkitUtils.deserializeItemStack(
                    mat("ARMOR_STAND", "IRON_BLOCK") + " : 1 : name>"
                            + EsportsLang.get("menus.spawn.slot.name", "&fSlot #{slot}", "slot", index + 1)
                            + " : desc>" + EsportsLang.get("menus.spawn.slot.status", "&7Status: {status}", "status", status)
                            + "\\n" + desc));
        }
        renderAutoSlot();
        setItem(INFO_SLOT, BukkitUtils.deserializeItemStack(
                "BOOK : 1 : name>" + EsportsLang.get("menus.spawn.info.name", "&eHow it works")
                        + " : desc>" + EsportsLang.get("menus.spawn.info.lore.1", "&7Save spawn slots for clubs.")
                        + "\\n" + EsportsLang.get("menus.spawn.info.lore.2", "&7Left click a slot to store your location.")
                        + "\\n" + EsportsLang.get("menus.spawn.info.lore.3", "&7Right click a slot to clear it.")
                        + "\\n" + EsportsLang.get("menus.spawn.info.lore.4", "&7Auto-place fills or regenerates slots.")));
        setItem(BACK_SLOT, BukkitUtils.deserializeItemStack(
                mat("ARROW", "ARROW") + " : 1 : name>" + EsportsLang.get("menus.spawn.back.name", "&7Back")
                        + " : desc>" + EsportsLang.get("menus.spawn.back.lore", "&7Return to main setup.")));

        fillGaps();
    }

    private void renderAutoSlot() {
        double radius = service.getAutoSpawnRadius();
        int defined = session.getDefinedSpawnSlotCount();
        int required = session.getRequiredSpawnSlots();
        String radiusLabel = String.format(Locale.US, "%.1f", radius);
        String item = mat("COMPASS", "COMPASS")
                + " : 1 : name>" + EsportsLang.get("menus.spawn.auto.name", "&aAuto-Place")
                + " : desc>" + EsportsLang.get("menus.spawn.auto.left", "&7Left click: fill missing slots.")
                + "\\n" + EsportsLang.get("menus.spawn.auto.right", "&7Right click: regenerate all slots.")
                + "\\n" + EsportsLang.get("menus.spawn.auto.radius", "&8Radius: &f{radius} blocks", "radius", radiusLabel)
                + "\\n" + EsportsLang.get("menus.spawn.auto.slots", "&8Slots: &f{defined}/{required}",
                "defined", defined,
                "required", required);
        setItem(AUTO_SLOT, BukkitUtils.deserializeItemStack(item));
    }

    private void handleAutoPlacement(boolean regenerate) {
        if (!session.hasSpectatorSpawn()) {
            player.sendMessage(EsportsLang.get("spawn.spectator-required", "&cSet the spectator lobby location first."));
            return;
        }
        double radius = service.getAutoSpawnRadius();
        int before = session.getDefinedSpawnSlotCount();
        boolean success = regenerate
                ? session.regenerateSpawnSlots(radius)
                : session.autoPopulateSpawnSlots(radius);
        if (!success) {
            player.sendMessage(EsportsLang.get("spawn.auto-failed", "&cAuto placement failed. Please try again."));
            return;
        }
        int after = session.getDefinedSpawnSlotCount();
        if (regenerate) {
            player.sendMessage(EsportsLang.get("spawn.recalculated", "&aAll spawn slots recalculated."));
        } else if (after == before) {
            player.sendMessage(EsportsLang.get("spawn.no-missing", "&eNo missing slots to fill."));
        } else {
            player.sendMessage(EsportsLang.get("spawn.auto-filled", "&aMissing slots filled automatically."));
        }
        EnumSound.CLICK.play(player, 1.0F, regenerate ? 1.0F : 1.3F);
        render();
    }

    private String mat(String preferred, String legacy) {
        Material material = MaterialResolver.resolveMaterial(preferred);
        if (material == null && legacy != null) {
            material = MaterialResolver.resolveMaterial(legacy);
        }
        if (material == null) {
            material = Material.STONE;
        }
        return material.name();
    }

    private String format(EsportsSpawnPoint point) {
        if (point == null) {
            return EsportsLang.get("menus.spawn.status.unknown", "Unknown");
        }
        return point.getWorldName() + " "
                + point.getX() + "," + point.getY() + "," + point.getZ();
    }

    private void fillGaps() {
        String filler = "STAINED_GLASS_PANE:7 : 1 : name>&r";
        for (int i = 0; i < getInventory().getSize(); i++) {
            if (getInventory().getItem(i) == null) {
                setItem(i, BukkitUtils.deserializeItemStack(filler));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(this.player)) return;
        if (!event.getView().getTopInventory().equals(getInventory())) return;
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);
        event.setCursor(null);
        event.getWhoClicked().setItemOnCursor(null);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (slot == BACK_SLOT) {
            service.openWizard(player, session);
            return;
        }
        if (slot == AUTO_SLOT) {
            handleAutoPlacement(event.isRightClick());
            return;
        }
        int index = resolveIndex(slot);
        if (index < 0) {
            return;
        }
        if (index >= session.getRequiredSpawnSlots()) {
            player.sendMessage(EsportsLang.get("spawn.no-extra-needed", "&cNo additional slots needed for this round."));
            return;
        }
        if (event.isRightClick()) {
            session.clearSpawnPoint(index);
            player.sendMessage(EsportsLang.get("spawn.slot-cleared", "&eSlot #{slot} cleared.", "slot", index + 1));
            render();
            return;
        }
        Location location = player.getLocation();
        session.setSpawnPoint(index, EsportsSpawnPoint.toJson(location));
        player.sendMessage(EsportsLang.get("spawn.slot-saved", "&aLocation saved for slot #{slot}.", "slot", index + 1));
        EnumSound.LEVEL_UP.play(player, 1.0F, 1.2F);
        render();
    }

    private int resolveIndex(int slot) {
        for (int i = 0; i < SLOT_LAYOUT.length; i++) {
            if (SLOT_LAYOUT[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(this.player) && event.getInventory().equals(getInventory())) {
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!event.getWhoClicked().equals(this.player)) return;
        if (!event.getView().getTopInventory().equals(getInventory())) return;
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < getInventory().getSize()) {
                event.setCancelled(true);
                event.setResult(org.bukkit.event.Event.Result.DENY);
                break;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(this.player)) {
            HandlerList.unregisterAll(this);
        }
    }
}
