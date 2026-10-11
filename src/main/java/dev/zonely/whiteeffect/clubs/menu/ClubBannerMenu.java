package dev.zonely.whiteeffect.clubs.menu;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.clubs.ClubManager;
import dev.zonely.whiteeffect.clubs.ClubLang;
import dev.zonely.whiteeffect.clubs.model.BannerDesign;
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

public final class ClubBannerMenu extends PlayerMenu {

    private static final DyeColor[] COLORS = DyeColor.values();
    private static final int[] COLOR_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29};
    private static final int DESIGN_SLOT = 31;
    private static final int BACK_SLOT = 30;
    private static final int CLOSE_SLOT = 32;

    private final Core plugin;
    private final ClubManager.ClubCreationSession session;
    private final ClubData club;
    private final ClubManager manager;
    private final BannerDesign design;

    public ClubBannerMenu(Core plugin, Player player, ClubManager.ClubCreationSession session) {
        super(player, ClubLang.get("menus.banner.title", "&5Banner Settings"), 4);
        this.plugin = plugin;
        this.session = session;
        this.club = null;
        this.manager = Core.getInstance().getClubManager();
        this.design = session.getBannerDesign();
        populate();
    }

    public ClubBannerMenu(Core plugin, Player player, ClubData club, ClubManager manager) {
        super(player, ClubLang.get("menus.banner.title", "&5Banner Settings"), 4);
        this.plugin = plugin;
        this.session = null;
        this.club = club;
        this.manager = manager;
        this.design = manager.parseBannerDesign(club.getBannerJson());
        populate();
    }

    private void populate() {
        fillBackground();
        int slot = 0;
        for (DyeColor color : COLORS) {
            if (slot >= COLOR_SLOTS.length) {
                break;
            }
            Material material = resolveBannerMaterial(color.name());
            String token = material.name() + " : 1 : name>&d" + color.name()
                    + " : desc>" + ClubLang.get("menus.banner.color.lore", "&7Change base color.");
            setItem(COLOR_SLOTS[slot++], BukkitUtils.deserializeItemStack(token));
        }
        setItem(DESIGN_SLOT, BukkitUtils.deserializeItemStack(
                "NETHER_STAR : 1 : name>" + ClubLang.get("menus.banner.pattern.name", "&b&lPattern Editor")
                        + " : desc>" + ClubLang.get("menus.banner.pattern.base", "&7Base: &f{value}", "value", design.getBaseColor())
                        + "\\n" + ClubLang.get("menus.banner.pattern.count", "&7Pattern count: &f{count}", "count", design.getLayers().size())
                        + "\\n" + ClubLang.get("menus.banner.pattern.click", "&7Click to edit.")));
        setItem(BACK_SLOT, BukkitUtils.deserializeItemStack(
                "ARROW : 1 : name>" + ClubLang.get("menus.banner.back.name", "&7&lBack")
                        + " : desc>" + ClubLang.get("menus.banner.back.lore", "&7Return to previous menu.")));
        setItem(CLOSE_SLOT, BukkitUtils.deserializeItemStack(
                "BARRIER : 1 : name>" + ClubLang.get("menus.banner.close.name", "&c&lClose")
                        + " : desc>" + ClubLang.get("menus.banner.close.lore", "&7Close this menu.")));
    }

    private void fillBackground() {
        for (int slot = 0; slot < getInventory().getSize(); slot++) {
            boolean edge = slot < 9 || slot >= getInventory().getSize() - 9 || slot % 9 == 0 || slot % 9 == 8;
            setItem(slot, BukkitUtils.deserializeItemStack(
                    (edge ? "STAINED_GLASS_PANE:11" : "STAINED_GLASS_PANE:7") + " : 1 : name>&r"));
        }
    }

    private void openParent() {
        player.closeInventory();
        if (session != null) {
            dev.zonely.whiteeffect.libraries.menu.PlayerMenu menu = session.openMenu(plugin, player);
            menu.register(plugin);
            menu.open();
            return;
        }
        if (club != null && manager != null) {
            manager.getClubByOwner(player).ifPresent(updated -> {
                ClubManagementMenu menu = new ClubManagementMenu(plugin, player, updated);
                menu.register(plugin);
                menu.open();
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(this.player)) return;
        if (!event.getView().getTopInventory().equals(getInventory())) return;
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (slot == DESIGN_SLOT) {
            openDesigner();
            return;
        }
        if (slot == BACK_SLOT) {
            openParent();
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
        if (colorIndex < 0 || colorIndex >= COLORS.length) {
            return;
        }
        DyeColor selected = COLORS[colorIndex];
        if (session != null) {
            session.setBannerBaseColor(selected.name());
            player.sendMessage(ClubLang.get("menus.banner.messages.base-set",
                    "&aBanner base color set to {color}.",
                    "color", selected.name()));
            openDesigner();
            return;
        }
        if (club != null && manager != null) {
            design.setBaseColor(selected.name());
            manager.updateBanner(player, club, design);
            openDesigner();
        }
    }

    private void openDesigner() {
        player.closeInventory();
        if (session != null) {
            ClubBannerDesignerMenu.openForSession(plugin, player, session);
        } else if (club != null && manager != null) {
            ClubBannerDesignerMenu.openForClub(plugin, player, manager, club, design);
        }
    }

    private Material resolveBannerMaterial(String color) {
        Material material = MaterialResolver.resolveMaterial(color + "_BANNER");
        if (material == null) {
            material = MaterialResolver.resolveMaterial("WHITE_BANNER");
        }
        if (material == null) {
            material = MaterialResolver.resolveMaterial("BANNER");
        }
        if (material == null) {
            material = MaterialResolver.resolveMaterial("STANDING_BANNER");
        }
        if (material == null) {
            material = MaterialResolver.resolveMaterial("WALL_BANNER");
        }
        if (material == null) {
            material = Material.PAPER;
        }
        return material;
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
