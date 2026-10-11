package dev.zonely.whiteeffect.clubs.menu;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.clubs.ClubManager;
import dev.zonely.whiteeffect.clubs.ClubLang;
import dev.zonely.whiteeffect.clubs.model.BannerDesign;
import dev.zonely.whiteeffect.clubs.model.ClubData;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public final class ClubManagementMenu extends PlayerMenu {

    private final ClubData club;
    private final Core plugin;

    private static final int INFO_SLOT = 13;
    private static final int MEMBERS_SLOT = 20;
    private static final int COLOR_SLOT = 22;
    private static final int BANNER_SLOT = 24;
    private static final int HELP_SLOT = 30;
    private static final int CLOSE_SLOT = 40;

    public ClubManagementMenu(Core plugin, Player player, ClubData club) {
        super(player, ClubLang.get("menus.management.title", "&3Club Hub"), 5);
        this.club = club;
        this.plugin = plugin;
        render();
    }

    private void render() {
        getInventory().clear();
        fillBackground();
        BannerDesign design = BannerDesign.fromJson(club.getBannerJson());
        String clubTag = club.getTag() == null ? "-" : club.getTag();
        String clubName = club.getName() == null ? "-" : club.getName();
        String ownerName = club.getOwnerName() == null ? "-" : club.getOwnerName();
        setItem(INFO_SLOT, BukkitUtils.deserializeItemStack(
                "NETHER_STAR : 1 : name>&b&l" + club.getName()
                        + " : desc>" + ClubLang.get("menus.management.info.tag", "&7Tag: &f{value}",
                        "value", clubTag, "tag", clubTag, "name", clubName)
                        + "\\n" + ClubLang.get("menus.management.info.owner", "&7Owner: &f{value}",
                        "value", ownerName, "owner", ownerName)
                        + "\\n" + ClubLang.get("menus.management.info.members", "&7Members: &f{count}", "count", club.getMembers().size())
                        + "\\n" + ClubLang.get("menus.management.info.color", "&7Color: &f{value}", "value", colorText(club.getColorHex()))
                        + "\\n" + ClubLang.get("menus.management.info.layers", "&7Banner Layers: &f{count}", "count", design.getLayers().size())
                        + "\\n"
                        + "\\n" + ClubLang.get("menus.management.info.overview", "&eClub overview",
                        "tag", clubTag, "value", clubTag)));
        setItem(MEMBERS_SLOT, BukkitUtils.deserializeItemStack(
                "BOOK : 1 : name>" + ClubLang.get("menus.management.members.name", "&a&lMembers")
                        + " : desc>" + ClubLang.get("menus.management.members.lore.1", "&7View all club members.")
                        + "\\n" + ClubLang.get("menus.management.members.lore.2", "&eClick to open.")));
        setItem(COLOR_SLOT, BukkitUtils.deserializeItemStack(
                resolveColorItem() + " : 1 : name>" + ClubLang.get("menus.management.color.name", "&d&lClub Color")
                        + " : desc>" + ClubLang.get("menus.management.color.current", "&7Current: &f{value}", "value", colorText(club.getColorHex()))
                        + "\\n" + ClubLang.get("menus.management.color.click", "&eClick to change.")));
        ItemStack preview = design.createPreviewItem();
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ClubLang.get("menus.management.banner.name", "&b&lBanner Editor"));
            meta.setLore(Arrays.asList(
                    ClubLang.get("menus.management.banner.base", "&7Base: &f{value}", "value", design.getBaseColor()),
                    ClubLang.get("menus.management.banner.patterns", "&7Patterns: &f{count}", "count", design.getLayers().size()),
                    ClubLang.get("menus.management.banner.click", "&eClick to edit banner")));
            preview.setItemMeta(meta);
        }
        setItem(BANNER_SLOT, preview);
        setItem(HELP_SLOT, BukkitUtils.deserializeItemStack(
                "BOOK_AND_QUILL : 1 : name>" + ClubLang.get("menus.management.help.name", "&e&lCommand Help")
                        + " : desc>" + ClubLang.get("menus.management.help.lore.1", "&7Open /club help command list.")
                        + "\\n" + ClubLang.get("menus.management.help.lore.2", "&7Invite, kick, leave and more.")
                        + "\\n" + ClubLang.get("menus.management.help.lore.3", "&eClick -> /club help.")
        ));
        setItem(CLOSE_SLOT, BukkitUtils.deserializeItemStack(
                "BARRIER : 1 : name>" + ClubLang.get("menus.management.close.name", "&c&lClose")
                        + " : desc>" + ClubLang.get("menus.management.close.lore", "&7Close this menu.")
        ));
    }

    private void fillBackground() {
        ItemStack border = BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:11 : 1 : name>&r");
        ItemStack center = BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1 : name>&r");
        for (int slot = 0; slot < getInventory().getSize(); slot++) {
            boolean edge = slot < 9 || slot >= getInventory().getSize() - 9 || slot % 9 == 0 || slot % 9 == 8;
            setItem(slot, edge ? border.clone() : center.clone());
        }
    }

    private String colorText(String hex) {
        if (hex == null || hex.trim().isEmpty()) {
            return ClubLang.get("menus.management.common.empty", "-");
        }
        return hex.toUpperCase();
    }

    private String resolveColorItem() {
        Material mat = MaterialResolver.resolveMaterial("WHITE_WOOL");
        if (mat == null) {
            mat = MaterialResolver.resolveMaterial("WOOL");
        }
        if (mat == null) {
            mat = Material.PAPER;
        }
        return mat.name();
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
        if (slot == MEMBERS_SLOT) {
            ClubMembersMenu menu = new ClubMembersMenu(player, club);
            menu.register(plugin);
            menu.open();
            return;
        }
        if (slot == COLOR_SLOT) {
            ClubManager manager = Core.getInstance().getClubManager();
            if (manager != null) {
                ClubColorMenu menu = new ClubColorMenu(plugin, player, club, manager);
                menu.register(plugin);
                menu.open();
            }
            return;
        }
        if (slot == BANNER_SLOT) {
            ClubManager manager = Core.getInstance().getClubManager();
            if (manager != null) {
                ClubBannerMenu menu = new ClubBannerMenu(plugin, player, club, manager);
                menu.register(plugin);
                menu.open();
            }
            return;
        }
        if (slot == HELP_SLOT) {
            player.closeInventory();
            player.performCommand("club help");
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
        }
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
}
