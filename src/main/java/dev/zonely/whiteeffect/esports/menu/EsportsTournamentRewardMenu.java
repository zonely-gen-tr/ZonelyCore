package dev.zonely.whiteeffect.esports.menu;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.esports.EsportsLang;
import dev.zonely.whiteeffect.esports.menu.EsportsCreationService.CreationSession;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

final class EsportsTournamentRewardMenu extends PlayerMenu {

    private static final int INFO_SLOT = 13;
    private static final int BACK_SLOT = 49;
    private static final int[] PLACEMENT_SLOTS = {19, 22, 25};
    private static final Material[] PLACEMENT_MATERIALS = {
            Material.GOLD_BLOCK,
            Material.IRON_BLOCK,
            Material.BRICK
    };

    private final Core plugin;
    private final EsportsCreationService service;
    private final CreationSession session;

    EsportsTournamentRewardMenu(Core plugin,
                                Player player,
                                EsportsCreationService service,
                                CreationSession session) {
        super(player, EsportsLang.get("menus.reward.title", "&6Reward Setup"), 6);
        this.plugin = plugin;
        this.service = service;
        this.session = session;
        renderPlacements();
        this.register(plugin);
        this.open();
    }

    private void renderPlacements() {
        setItem(INFO_SLOT, BukkitUtils.deserializeItemStack(
                "BOOK : 1 : name>" + EsportsLang.get("menus.reward.info.name", "&eReward Structure")
                        + " : desc>" + EsportsLang.get("menus.reward.info.lore.1", "&7Define console commands per placement.")
                        + "\\n" + EsportsLang.get("menus.reward.info.lore.2", "&7Use {player} for the player name.")
                        + "\\n" + EsportsLang.get("menus.reward.info.lore.3", "&7Right click to clear commands.")
        ));
        for (int index = 0; index < PLACEMENT_SLOTS.length; index++) {
            int placement = index + 1;
            List<String> commands = session.getPlacementCommands(placement);
            String status = commands.isEmpty()
                    ? EsportsLang.get("menus.reward.placement.no-commands", "&cNo commands")
                    : EsportsLang.get("menus.reward.placement.command-count", "&a{count} command(s)", "count", commands.size());
            StringBuilder desc = new StringBuilder(EsportsLang.get("menus.reward.placement.status", "&7Status: {status}", "status", status))
                    .append("\\n").append(EsportsLang.get("menus.reward.placement.left", "&7Left click: enter commands"))
                    .append("\\n").append(EsportsLang.get("menus.reward.placement.right", "&7Right click: clear"));
            if (!commands.isEmpty()) {
                int preview = Math.min(3, commands.size());
                desc.append("\\n").append(EsportsLang.get("menus.reward.placement.separator", "&8-----------------"));
                for (int i = 0; i < preview; i++) {
                    desc.append("\\n&8- ").append(commands.get(i));
                }
                if (commands.size() > preview) {
                    desc.append("\\n").append(EsportsLang.get("menus.reward.placement.more",
                            "&7... ({count} total)",
                            "count", commands.size()));
                }
            }
            setItem(PLACEMENT_SLOTS[index], BukkitUtils.deserializeItemStack(
                    materialName(PLACEMENT_MATERIALS[index]) + " : 1 : name>&e" + EsportsCreationService.placementLabel(placement)
                            + " : desc>" + desc
            ));
        }
        setItem(BACK_SLOT, BukkitUtils.deserializeItemStack(
                materialName(Material.ARROW) + " : 1 : name>" + EsportsLang.get("menus.reward.back.name", "&7Back")
                        + " : desc>" + EsportsLang.get("menus.reward.back.lore", "&7Return to main setup.")
        ));

        fillGaps();
    }

    private String materialName(Material material) {
        return material == null ? Material.STONE.name() : material.name();
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
            HandlerList.unregisterAll(this);
            service.openWizard(player, session);
            EnumSound.CLICK.play(player, 1.0F, 1.2F);
            return;
        }
        for (int index = 0; index < PLACEMENT_SLOTS.length; index++) {
            if (slot != PLACEMENT_SLOTS[index]) {
                continue;
            }
            int placement = index + 1;
            if (event.isRightClick()) {
                session.clearPlacementCommands(placement);
                player.sendMessage(EsportsLang.get("reward.cleared",
                        "&e{placement} commands cleared.",
                        "placement", EsportsCreationService.placementLabel(placement)));
                EnumSound.CLICK.play(player, 1.0F, 1.0F);
                renderPlacements();
                return;
            }
            EnumSound.CLICK.play(player, 1.0F, 1.2F);
            player.closeInventory();
            HandlerList.unregisterAll(this);
            service.requestRewardCommands(player, session, placement);
            return;
        }
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
