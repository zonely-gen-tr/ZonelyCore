package dev.zonely.whiteeffect.clubs.menu;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.clubs.ClubManager;
import dev.zonely.whiteeffect.clubs.ClubLang;
import dev.zonely.whiteeffect.clubs.model.BannerDesign;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.utils.BukkitUtils;
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
import java.util.ArrayList;
import java.util.List;

public final class ClubCreationMenu extends PlayerMenu {

    private static final int NAME_SLOT = 20;
    private static final int COLOR_SLOT = 22;
    private static final int BANNER_SLOT = 24;
    private static final int INFO_SLOT = 13;
    private static final int CONFIRM_SLOT = 49;
    private static final int CANCEL_SLOT = 51;
    private static final int STATUS_SLOT = 31;

    private final Core plugin;
    private final ClubManager.ClubCreationSession session;

    public ClubCreationMenu(Core plugin, Player player, ClubManager.ClubCreationSession session) {
        super(player, ClubLang.get("menus.creation.title", "&3Club Creation"), 6);
        this.plugin = plugin;
        this.session = session;
        render();
    }

    private void render() {
        fillBackground();
        updateButtons();
    }

    private void fillBackground() {
        ItemStack border = BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:11 : 1 : name>&r");
        ItemStack center = BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1 : name>&r");
        for (int slot = 0; slot < getInventory().getSize(); slot++) {
            boolean edge = slot < 9 || slot >= getInventory().getSize() - 9 || slot % 9 == 0 || slot % 9 == 8;
            setItem(slot, edge ? border.clone() : center.clone());
        }
    }

    private void updateButtons() {
        String nameText = session.getName() == null
                ? ClubLang.get("menus.creation.common.not-set", "&cNot set")
                : "&a" + session.getName();
        setItem(NAME_SLOT, BukkitUtils.deserializeItemStack(
                "NAME_TAG : 1 : name>" + ClubLang.get("menus.creation.name.name", "&e&lSet Club Name")
                        + " : desc>" + ClubLang.get("menus.creation.name.current", "&8 - &7Current: {value}", "value", nameText)
                        + "\\n"
                        + "\\n" + ClubLang.get("menus.creation.name.click", "&6&l> &eClick to enter via chat.")));

        String colorText = session.getColorName() == null
                ? ClubLang.get("menus.creation.common.not-set", "&cNot set")
                : "&a" + session.getColorName();
        setItem(COLOR_SLOT, BukkitUtils.deserializeItemStack(
                "WOOL:5 : 1 : name>" + ClubLang.get("menus.creation.color.name", "&d&lChoose Color")
                        + " : desc>" + ClubLang.get("menus.creation.color.current", "&8 - &7Current: {value}", "value", colorText)
                        + "\\n"
                        + "\\n" + ClubLang.get("menus.creation.color.click", "&6&l> &eOpen color picker.")));

        BannerDesign design = session.getBannerDesign();
        ItemStack preview = design.createPreviewItem();
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ClubLang.get("menus.creation.banner.name", "&b&lBanner Editor"));
            meta.setLore(Arrays.asList(
                    ClubLang.get("menus.creation.banner.base", "&7Base: &f{value}", "value", design.getBaseColor()),
                    ClubLang.get("menus.creation.banner.layers", "&7Layers: &f{count}", "count", design.getLayers().size()),
                    ClubLang.get("menus.creation.banner.click", "&eClick to edit")));
            preview.setItemMeta(meta);
        }
        setItem(BANNER_SLOT, preview);

        String confirmColor = session.isReady() ? "&a" : "&c";
        String confirmDesc = session.isReady()
                ? ClubLang.get("menus.creation.confirm.ready", "&8 - &7All fields ready.")
                + "\\n\\n" + ClubLang.get("menus.creation.confirm.ready-click", "&6&l> &aCreate club.")
                : ClubLang.get("menus.creation.confirm.missing", "&8 - &7Missing: &f{fields}",
                "fields", String.join(", ", missingFields()))
                + "\\n\\n" + ClubLang.get("menus.creation.confirm.missing-click", "&6&l> &eComplete before creating.");
        setItem(CONFIRM_SLOT, BukkitUtils.deserializeItemStack(
                "EMERALD_BLOCK : 1 : name>" + confirmColor + ClubLang.get("menus.creation.confirm.name", "Create")
                        + " : desc>" + confirmDesc));
        setItem(CANCEL_SLOT, BukkitUtils.deserializeItemStack(
                "REDSTONE_BLOCK : 1 : name>" + ClubLang.get("menus.creation.cancel.name", "&cCancel")
                        + " : desc>" + ClubLang.get("menus.creation.cancel.lore", "&7Cancel creation and close.")));

        setItem(INFO_SLOT, BukkitUtils.deserializeItemStack(
                "NETHER_STAR : 1 : name>" + ClubLang.get("menus.creation.info.name", "&b&lClub Creator")
                        + " : desc>" + ClubLang.get("menus.creation.info.lore.1", "&7Create your club in 3 steps:")
                        + "\\n" + ClubLang.get("menus.creation.info.lore.2", "&71) Name")
                        + "\\n" + ClubLang.get("menus.creation.info.lore.3", "&72) Color")
                        + "\\n" + ClubLang.get("menus.creation.info.lore.4", "&73) Banner")
                        + "\\n"
                        + "\\n" + ClubLang.get("menus.creation.info.lore.5", "&7When everything is ready,")
                        + "\\n" + ClubLang.get("menus.creation.info.lore.6", "&7click &aCreate&7.")));
        setItem(STATUS_SLOT, BukkitUtils.deserializeItemStack(
                "BOOK : 1 : name>" + ClubLang.get("menus.creation.status.name", "&e&lSetup Status")
                        + " : desc>" + ClubLang.get("menus.creation.status.ready", "&7Ready: {value}",
                        "value", session.isReady()
                                ? ClubLang.get("menus.creation.status.yes", "&aYes")
                                : ClubLang.get("menus.creation.status.no", "&cNo"))
                        + "\\n" + ClubLang.get("menus.creation.status.missing", "&7Missing: &f{fields}",
                        "fields", String.join(", ", missingFields()))
                        + "\\n"
                        + "\\n" + ClubLang.get("menus.creation.status.hint", "&eKeep completing fields.")));
    }

    private List<String> missingFields() {
        List<String> fields = new ArrayList<>();
        if (session.getName() == null) {
            fields.add(ClubLang.get("menus.creation.fields.name", "name"));
        }
        if (session.getColorName() == null) {
            fields.add(ClubLang.get("menus.creation.fields.color", "color"));
        }
        return fields.isEmpty() ? Arrays.asList(ClubLang.get("menus.creation.fields.none", "-")) : fields;
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
        if (slot == NAME_SLOT) {
            ClubManager manager = Core.getInstance().getClubManager();
            if (manager != null) {
                manager.requestNameInput(player);
            }
            return;
        }
        if (slot == COLOR_SLOT) {
            ClubColorMenu colorMenu = new ClubColorMenu(plugin, player, session);
            colorMenu.register(plugin);
            colorMenu.open();
            return;
        }
        if (slot == BANNER_SLOT) {
            ClubBannerMenu bannerMenu = new ClubBannerMenu(plugin, player, session);
            bannerMenu.register(plugin);
            bannerMenu.open();
            return;
        }
        if (slot == CONFIRM_SLOT) {
            if (!session.isReady()) {
                player.sendMessage(ClubLang.get("menus.creation.messages.name-color-required", "&cName and color are required."));
                return;
            }
            ClubManager manager = Core.getInstance().getClubManager();
            if (manager != null) {
                manager.createClub(player, session);
            }
            return;
        }
        if (slot == CANCEL_SLOT) {
            ClubManager manager = Core.getInstance().getClubManager();
            if (manager != null) {
                manager.cancelSession(player);
            }
            player.closeInventory();
            player.sendMessage(ClubLang.get("menus.creation.messages.cancelled", "&cClub creation cancelled."));
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
