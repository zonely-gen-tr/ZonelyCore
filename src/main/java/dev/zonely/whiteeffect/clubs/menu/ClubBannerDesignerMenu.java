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
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class ClubBannerDesignerMenu extends PlayerMenu {

    private static final int PREVIEW_SLOT = 4;
    private static final int BASE_SLOT = 10;
    private static final int[] PATTERN_SLOTS = {19, 20, 21, 22, 23, 24};
    private static final int ADD_SLOT = 28;
    private static final int CLEAR_SLOT = 29;
    private static final int SAVE_SLOT = 33;
    private static final int BACK_SLOT = 34;
    private static final int INFO_SLOT = 40;
    private static final int CLOSE_SLOT = 41;

    private final Core plugin;
    private final BannerDesignTarget target;
    private BannerDesign working;

    private ClubBannerDesignerMenu(Core plugin, Player player, BannerDesignTarget target) {
        super(player, ClubLang.get("menus.banner-designer.title", "&5Banner Editor"), 5);
        this.plugin = plugin;
        this.target = target;
        this.working = target.currentDesign().copy();
        populate();
    }

    public static void openForSession(Core plugin, Player player, ClubManager.ClubCreationSession session) {
        ClubBannerDesignerMenu menu = new ClubBannerDesignerMenu(plugin, player, new SessionTarget(plugin, session));
        menu.register(plugin);
        menu.open();
    }

    public static void openForClub(Core plugin, Player player, ClubManager manager, ClubData club, BannerDesign design) {
        ClubBannerDesignerMenu menu = new ClubBannerDesignerMenu(plugin, player, new ClubTarget(plugin, manager, club, design));
        menu.register(plugin);
        menu.open();
    }

    private void populate() {
        fillBackground();
        setItem(PREVIEW_SLOT, working.createPreviewItem());
        setItem(BASE_SLOT, BukkitUtils.deserializeItemStack(
                resolveMaterialToken(working.getBaseColor()) + " : 1 : name>"
                        + ClubLang.get("menus.banner-designer.base.name", "&eBase Color")
                        + " : desc>" + ClubLang.get("menus.banner-designer.base.current", "&7Current: &f{value}", "value", working.getBaseColor())
                        + "\\n" + ClubLang.get("menus.banner-designer.base.click", "&7Click to change.")));

        for (int i = 0; i < PATTERN_SLOTS.length; i++) {
            int slot = PATTERN_SLOTS[i];
            if (i < working.getLayers().size()) {
                BannerDesign.Layer layer = working.getLayers().get(i);
                setItem(slot, createLayerIcon(layer, i));
            } else {
                setItem(slot, BukkitUtils.deserializeItemStack(
                        "GRAY_STAINED_GLASS_PANE : 1 : name>" + ClubLang.get("menus.banner-designer.empty-slot", "&8Empty Slot")));
            }
        }

        if (working.canAddLayer()) {
            setItem(ADD_SLOT, BukkitUtils.deserializeItemStack(
                    "EMERALD : 1 : name>" + ClubLang.get("menus.banner-designer.add.name", "&aAdd Pattern")
                            + " : desc>" + ClubLang.get("menus.banner-designer.add.lore", "&7Add a new pattern layer.")));
        } else {
            setItem(ADD_SLOT, BukkitUtils.deserializeItemStack(
                    "BARRIER : 1 : name>" + ClubLang.get("menus.banner-designer.limit.name", "&cLimit Reached")
                            + " : desc>" + ClubLang.get("menus.banner-designer.limit.lore", "&7Max {count} patterns.",
                            "count", BannerDesign.MAX_LAYERS)));
        }

        setItem(CLEAR_SLOT, BukkitUtils.deserializeItemStack(
                "REDSTONE_BLOCK : 1 : name>" + ClubLang.get("menus.banner-designer.clear.name", "&cClear")
                        + " : desc>" + ClubLang.get("menus.banner-designer.clear.lore", "&7Remove all pattern layers.")));
        setItem(SAVE_SLOT, BukkitUtils.deserializeItemStack(
                "LIME_CONCRETE : 1 : name>" + ClubLang.get("menus.banner-designer.save.name", "&aSave")
                        + " : desc>" + ClubLang.get("menus.banner-designer.save.lore", "&7Apply changes.")));
        setItem(BACK_SLOT, BukkitUtils.deserializeItemStack(
                "ARROW : 1 : name>" + ClubLang.get("menus.banner-designer.back.name", "&7Back")
                        + " : desc>" + ClubLang.get("menus.banner-designer.back.lore", "&7Exit without saving.")));
        setItem(INFO_SLOT, BukkitUtils.deserializeItemStack(
                "BOOK : 1 : name>" + ClubLang.get("menus.banner-designer.info.name", "&b&lBanner Editor")
                        + " : desc>" + ClubLang.get("menus.banner-designer.info.lore.1", "&7Left click a layer to remove.")
                        + "\\n" + ClubLang.get("menus.banner-designer.info.lore.2", "&7Add new pattern with color picker.")
                        + "\\n" + ClubLang.get("menus.banner-designer.info.lore.3", "&7Save to apply club banner.")));
        setItem(CLOSE_SLOT, BukkitUtils.deserializeItemStack(
                "BARRIER : 1 : name>" + ClubLang.get("menus.banner-designer.close.name", "&c&lClose")
                        + " : desc>" + ClubLang.get("menus.banner-designer.close.lore", "&7Close this menu.")));
    }

    private void fillBackground() {
        for (int slot = 0; slot < getInventory().getSize(); slot++) {
            boolean edge = slot < 9 || slot >= getInventory().getSize() - 9 || slot % 9 == 0 || slot % 9 == 8;
            setItem(slot, BukkitUtils.deserializeItemStack(
                    (edge ? "STAINED_GLASS_PANE:11" : "STAINED_GLASS_PANE:7") + " : 1 : name>&r"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(this.player)) return;
        if (!event.getView().getTopInventory().equals(getInventory())) return;
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (raw == BASE_SLOT) {
            openBaseColorMenu();
            return;
        }
        for (int i = 0; i < PATTERN_SLOTS.length; i++) {
            if (raw == PATTERN_SLOTS[i]) {
                if (i < working.getLayers().size()) {
                    working.removeLayer(i);
                    player.sendMessage(ClubLang.get("menus.banner-designer.messages.pattern-removed",
                            "&ePattern #{index} removed.",
                            "index", i + 1));
                    populate();
                }
                return;
            }
        }
        if (raw == ADD_SLOT) {
            if (working.canAddLayer()) {
                openPatternMenu();
            } else {
                player.sendMessage(ClubLang.get("menus.banner-designer.messages.limit-reached", "&cPattern limit reached."));
            }
            return;
        }
        if (raw == CLEAR_SLOT) {
            working.clearLayers();
            player.sendMessage(ClubLang.get("menus.banner-designer.messages.cleared", "&eAll patterns cleared."));
            populate();
            return;
        }
        if (raw == SAVE_SLOT) {
            target.apply(player, working.copy());
            target.reopenParent(player);
            return;
        }
        if (raw == BACK_SLOT) {
            target.reopenParent(player);
            return;
        }
        if (raw == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    private void openBaseColorMenu() {
        player.closeInventory();
        ClubColorMenu menu = new ClubColorMenu(plugin, player, (hex, colorName, p) -> {
            working.setBaseColor(colorName);
            populate();
            reopenSelf();
        });
        menu.register(plugin);
        menu.open();
    }

    private void openPatternMenu() {
        player.closeInventory();
        ClubBannerPatternMenu menu = new ClubBannerPatternMenu(plugin, player, type -> {
            ClubColorMenu colorMenu = new ClubColorMenu(plugin, player, (hex, colorName, p) -> {
                try {
                    working.addLayer(new BannerDesign.Layer(type.name(), colorName));
                    populate();
                    reopenSelf();
                } catch (Exception ex) {
                    player.sendMessage(ClubLang.get("menus.banner-designer.messages.add-failed", "&cFailed to add pattern."));
                }
            });
            colorMenu.register(plugin);
            colorMenu.open();
        }, this::reopenSelf);
        menu.register(plugin);
        menu.open();
    }

    private String resolveMaterialToken(String color) {
        return findBannerMaterial(color).name();
    }

    private Material findBannerMaterial(String color) {
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

    private PatternType resolvePattern(String name) {
        if (name == null) {
            return null;
        }
        try {
            return PatternType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ItemStack createLayerIcon(BannerDesign.Layer layer, int index) {
        ItemStack icon = new ItemStack(findBannerMaterial(working.getBaseColor()));
        if (icon.getItemMeta() instanceof BannerMeta) {
            BannerMeta meta = (BannerMeta) icon.getItemMeta();
            BannerDesign.applyBaseColor(meta, BannerDesign.resolveColor(working.getBaseColor()));
            PatternType type = resolvePattern(layer.getPattern());
            DyeColor dye = BannerDesign.resolveColor(layer.getColor());
            if (type != null) {
                meta.addPattern(new Pattern(dye, type));
            }
            meta.setDisplayName(ClubLang.get("menus.banner-designer.layer.name", "&bPattern #{index}", "index", index + 1));
            meta.setLore(Arrays.asList(
                    ClubLang.get("menus.banner-designer.layer.type", "&7Type: {value}", "value", (type == null ? layer.getPattern() : type.name())),
                    ClubLang.get("menus.banner-designer.layer.color", "&7Color: {value}", "value", layer.getColor()),
                    ClubLang.get("menus.banner-designer.layer.remove", "&cLeft-click: Remove")
            ));
            icon.setItemMeta(meta);
            return icon;
        }
        return BukkitUtils.deserializeItemStack(
                "PAPER : 1 : name>" + ClubLang.get("menus.banner-designer.layer.name", "&bPattern #{index}", "index", index + 1)
                        + " : desc>" + ClubLang.get("menus.banner-designer.layer.type-fallback", "&7Type: &f{value}", "value", layer.getPattern())
                        + "\\n" + ClubLang.get("menus.banner-designer.layer.color-fallback", "&7Color: &f{value}", "value", layer.getColor())
                        + "\\n" + ClubLang.get("menus.banner-designer.layer.click-remove", "&cClick to remove."));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(this.player) && event.getInventory().equals(getInventory())) {
            HandlerList.unregisterAll(this);
        }
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

    private void reopenSelf() {
        this.register(plugin);
        this.open();
    }

    public interface BannerDesignTarget {
        BannerDesign currentDesign();
        void apply(Player player, BannerDesign design);
        void reopenParent(Player player);
    }

    private static final class SessionTarget implements BannerDesignTarget {
        private final Core plugin;
        private final ClubManager.ClubCreationSession session;

        private SessionTarget(Core plugin, ClubManager.ClubCreationSession session) {
            this.plugin = plugin;
            this.session = session;
        }

        @Override
        public BannerDesign currentDesign() {
            return session.getBannerDesign();
        }

        @Override
        public void apply(Player player, BannerDesign design) {
            session.applyBannerDesign(design);
            player.sendMessage(ClubLang.get("menus.banner-designer.messages.saved", "&aBanner design saved."));
        }

        @Override
        public void reopenParent(Player player) {
            PlayerMenu menu = session.openMenu(plugin, player);
            menu.register(plugin);
            menu.open();
        }
    }

    private static final class ClubTarget implements BannerDesignTarget {
        private final Core plugin;
        private final ClubManager manager;
        private final ClubData club;
        private BannerDesign design;

        private ClubTarget(Core plugin, ClubManager manager, ClubData club, BannerDesign design) {
            this.plugin = plugin;
            this.manager = manager;
            this.club = club;
            this.design = design == null ? new BannerDesign() : design.copy();
        }

        @Override
        public BannerDesign currentDesign() {
            return design;
        }

        @Override
        public void apply(Player player, BannerDesign design) {
            manager.updateBanner(player, club, design);
            this.design = design.copy();
        }

        @Override
        public void reopenParent(Player player) {
            manager.getClubByOwner(player).ifPresent(updated -> {
                ClubManagementMenu menu = new ClubManagementMenu(plugin, player, updated);
                menu.register(plugin);
                menu.open();
            });
        }
    }
}
