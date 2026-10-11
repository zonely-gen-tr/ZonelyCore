package dev.zonely.whiteeffect.esports.menu;

import com.google.gson.JsonObject;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.esports.EsportsLang;
import dev.zonely.whiteeffect.esports.game.EsportsKitSerializer;
import dev.zonely.whiteeffect.esports.model.EsportsClubEntry;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class EsportsKitMenu extends PlayerMenu {

    private static final int[] STORAGE_SLOT_ORDER = {
            45, 46, 47, 48, 49, 50, 51, 52, 53, // hotbar
            36, 37, 38, 39, 40, 41, 42, 43, 44,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            18, 19, 20, 21, 22, 23, 24, 25, 26
    };
    private static final int[] ARMOR_SLOTS = {10, 11, 12, 13}; // helmet -> boots
    private static final int OFFHAND_SLOT = 15;

    private static final Method PLAYER_GET_OFFHAND_METHOD;

    static {
        Method method = null;
        try {
            method = PlayerInventory.class.getMethod("getItemInOffHand");
        } catch (NoSuchMethodException ignored) {
        }
        PLAYER_GET_OFFHAND_METHOD = method;
    }

    private static final int INFO_SLOT = 0;
    private static final int CLUB_SLOT = 4;
    private static final int CLEAR_SLOT = 5;
    private static final int COPY_SLOT = 6;
    private static final int DEFAULT_SAVE_SLOT = 7;
    private static final int DEFAULT_CANCEL_SLOT = 8;

    private final Set<Integer> editableSlots = new HashSet<>();
    private final Function<JsonObject, Boolean> saveHandler;
    private final EsportsClubEntry club;
    private final int inventorySize;
    private final boolean hideCancelButton;
    private final int saveSlot;
    private final Integer cancelSlot;
    private boolean finished;
    private boolean closed;

    public EsportsKitMenu(Core plugin,
                          Player player,
                          EsportsClubEntry club,
                          JsonObject initialKit,
                          Function<JsonObject, Boolean> saveHandler) {
        this(plugin, player, club, initialKit, saveHandler, false, null);
    }

    public EsportsKitMenu(Core plugin,
                          Player player,
                          EsportsClubEntry club,
                          JsonObject initialKit,
                          Function<JsonObject, Boolean> saveHandler,
                          boolean hideCancelButton,
                          String customTitle) {
        super(player,
                customTitle == null
                        ? ChatColor.DARK_GRAY + "Kit Editor | " + (club == null ? "Tournament" : club.getTag())
                        : customTitle,
                6);
        this.club = club;
        this.saveHandler = saveHandler;
        this.inventorySize = player.getInventory().getSize();
        this.hideCancelButton = hideCancelButton;
        this.saveSlot = hideCancelButton ? DEFAULT_CANCEL_SLOT : DEFAULT_SAVE_SLOT;
        this.cancelSlot = hideCancelButton ? null : DEFAULT_CANCEL_SLOT;
        initializeEditableSlots();
        renderStaticLayout();
        populate(initialKit);
        this.register(plugin);
        this.open();
    }

    private void initializeEditableSlots() {
        Arrays.stream(STORAGE_SLOT_ORDER).forEach(editableSlots::add);
        Arrays.stream(ARMOR_SLOTS).forEach(editableSlots::add);
        editableSlots.add(OFFHAND_SLOT);
    }

    private void renderStaticLayout() {
        Material legacyPane = MaterialResolver.resolveMaterial("STAINED_GLASS_PANE");
        if (legacyPane == null) {
            legacyPane = Material.GLASS;
        }
        ItemStack filler = createButton("GRAY_STAINED_GLASS_PANE", legacyPane, (short) 7,
                ChatColor.DARK_GRAY, " ", null);
        Inventory inventory = getInventory();
        lockSlots(filler, 1, 2, 3, 9, 14, 16, 17);

        inventory.setItem(INFO_SLOT, createButton(
                Material.BOOK,
                ChatColor.AQUA,
                "Kit Information",
                Arrays.asList(
                        ChatColor.GRAY + "Top row: armor + off-hand slots.",
                        ChatColor.GRAY + "Place your layout in editable slots.",
                        ChatColor.GRAY + "Bottom: main inventory + hotbar."
                )
        ));
        String clubName = club == null ? "Tournament" : club.getName();
        String clubTag = club == null ? "DRAFT" : club.getTag();
        String seedText = club == null ? "-" : (club.getSeed() == null ? "-" : club.getSeed().toString());
        inventory.setItem(CLUB_SLOT, createButton(
                Material.NAME_TAG,
                ChatColor.GOLD,
                "Club: " + clubName,
                Arrays.asList(
                        ChatColor.YELLOW + "Tag: " + clubTag,
                        ChatColor.YELLOW + "Seed: " + seedText
                )
        ));
        Material clearLegacy = MaterialResolver.resolveMaterial("INK_SACK");
        if (clearLegacy == null) {
            Material grayModern = MaterialResolver.resolveMaterial("GRAY_DYE");
            if (grayModern != null) {
                clearLegacy = grayModern;
            } else {
                Material gunpowder = MaterialResolver.resolveMaterial("GUNPOWDER");
                if (gunpowder == null) {
                    gunpowder = MaterialResolver.resolveMaterial("SULPHUR");
                }
                if (gunpowder == null) {
                    gunpowder = Material.PAPER;
                }
                clearLegacy = gunpowder;
            }
        }
        inventory.setItem(CLEAR_SLOT, createButton(
                "GRAY_DYE",
                clearLegacy,
                (short) 8,
                ChatColor.GRAY,
                "Clear",
                Arrays.asList(ChatColor.DARK_GRAY + "Clear all editable slots.")
        ));
        inventory.setItem(COPY_SLOT, createButton(
                "WRITABLE_BOOK",
                Material.BOOK,
                (short) -1,
                ChatColor.YELLOW,
                "Copy Inventory",
                Arrays.asList(
                        ChatColor.GRAY + "Copy your inventory",
                        ChatColor.GRAY + "into the editor."
                )
        ));
        inventory.setItem(saveSlot, createButton(
                "LIME_CONCRETE",
                Material.EMERALD_BLOCK,
                (short) -1,
                ChatColor.GREEN,
                "Save",
                Arrays.asList(
                        ChatColor.GRAY + "Save the club kit to database.",
                        ChatColor.DARK_GREEN + "This menu will close."
                )
        ));
        if (!hideCancelButton && cancelSlot != null) {
            inventory.setItem(cancelSlot, createButton(
                    "BARRIER",
                    Material.REDSTONE_BLOCK,
                    (short) -1,
                    ChatColor.RED,
                    "Cancel",
                    Arrays.asList(
                            ChatColor.GRAY + "Close without saving changes.",
                            ChatColor.DARK_RED + "This cannot be undone."
                    )
            ));
        }

    }

    private void populate(JsonObject kit) {
        clearEditableSlots();
        if (kit == null) {
            return;
        }
        ItemStack[] contents = EsportsKitSerializer.readContents(kit, inventorySize);
        for (int i = 0; i < STORAGE_SLOT_ORDER.length && i < contents.length; i++) {
            getInventory().setItem(STORAGE_SLOT_ORDER[i], cloneItem(contents[i]));
        }

        ItemStack[] armor = EsportsKitSerializer.readArmor(kit);
        setArmorSlots(armor);
        ItemStack offhand = EsportsKitSerializer.readOffhand(kit);
        getInventory().setItem(OFFHAND_SLOT, cloneItem(offhand));
    }

    private void setArmorSlots(ItemStack[] armor) {
        ItemStack boots = armor.length > 0 ? armor[0] : null;
        ItemStack leggings = armor.length > 1 ? armor[1] : null;
        ItemStack chest = armor.length > 2 ? armor[2] : null;
        ItemStack helmet = armor.length > 3 ? armor[3] : null;

        getInventory().setItem(ARMOR_SLOTS[0], cloneItem(helmet));
        getInventory().setItem(ARMOR_SLOTS[1], cloneItem(chest));
        getInventory().setItem(ARMOR_SLOTS[2], cloneItem(leggings));
        getInventory().setItem(ARMOR_SLOTS[3], cloneItem(boots));
    }

    private void clearEditableSlots() {
        Inventory inventory = getInventory();
        for (Integer slot : editableSlots) {
            inventory.setItem(slot, null);
        }
    }

    private void copyFromPlayerInventory() {
        PlayerInventory source = player.getInventory();
        ItemStack[] sourceContents = source.getContents();
        for (int i = 0; i < STORAGE_SLOT_ORDER.length && i < sourceContents.length; i++) {
            getInventory().setItem(STORAGE_SLOT_ORDER[i], cloneItem(sourceContents[i]));
        }
        setArmorSlots(source.getArmorContents());
        ItemStack offhandItem = readOffhand(source);
        getInventory().setItem(OFFHAND_SLOT, cloneItem(offhandItem));
        player.sendMessage(EsportsLang.get("kit.copy-current", "&aCurrent inventory copied into kit editor."));
    }
    private JsonObject collectPayload() {
        ItemStack[] contents = new ItemStack[inventorySize];
        for (int i = 0; i < STORAGE_SLOT_ORDER.length && i < contents.length; i++) {
            contents[i] = cloneItem(getInventory().getItem(STORAGE_SLOT_ORDER[i]));
        }

        ItemStack[] armor = new ItemStack[4];
        armor[3] = cloneItem(getInventory().getItem(ARMOR_SLOTS[0]));
        armor[2] = cloneItem(getInventory().getItem(ARMOR_SLOTS[1]));
        armor[1] = cloneItem(getInventory().getItem(ARMOR_SLOTS[2]));
        armor[0] = cloneItem(getInventory().getItem(ARMOR_SLOTS[3]));

        ItemStack offhand = cloneItem(getInventory().getItem(OFFHAND_SLOT));
        return EsportsKitSerializer.serialize(contents, armor, offhand);
    }

    private void saveAndClose() {
        JsonObject payload = collectPayload();
        boolean ok = saveHandler.apply(payload);
        if (ok) {
            finished = true;
            player.sendMessage(EsportsLang.get("kit.saved", "&aClub kit saved."));
            player.closeInventory();
        } else {
            player.sendMessage(EsportsLang.get("kit.save-error", "&cAn error occurred while saving. Please try again."));
        }
    }

    private void cancelAndClose() {
        finished = true;
        player.sendMessage(EsportsLang.get("kit.cancelled", "&eKit editing cancelled."));
        player.closeInventory();
    }

    private void lockSlots(ItemStack template, int... slots) {
        for (int slot : slots) {
            getInventory().setItem(slot, template.clone());
        }
    }

    private ItemStack createButton(Material material, ChatColor color, String title, List<String> lore) {
        return createButton(material, (short) -1, color, title, lore);
    }

    private ItemStack createButton(String modernName, Material legacy, short legacyData,
                                   ChatColor color, String title, List<String> lore) {
        Material material = resolveMaterial(modernName, legacy);
        short data = material == legacy ? legacyData : (short) -1;
        return createButton(material, data, color, title, lore);
    }

    private ItemStack createButton(Material material, short legacyData, ChatColor color, String title, List<String> lore) {
        ItemStack item = new ItemStack(material);
        if (legacyData >= 0) {
            item.setDurability(legacyData);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color + title);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(new ArrayList<>(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material resolveMaterial(String modernName, Material legacy) {
        if (modernName != null) {
            try {
                Material matched = Material.matchMaterial(modernName);
                if (matched != null) {
                    return matched;
                }
            } catch (Throwable ignored) {
            }
        }
        return legacy;
    }

    private ItemStack cloneItem(ItemStack item) {
        if (item == null) {
            return null;
        }
        Material type = item.getType();
        if (type == null || type == Material.AIR) {
            return null;
        }
        return item.clone();
    }

    private ItemStack readOffhand(PlayerInventory inventory) {
        if (PLAYER_GET_OFFHAND_METHOD == null || inventory == null) {
            return null;
        }
        try {
            return (ItemStack) PLAYER_GET_OFFHAND_METHOD.invoke(inventory);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(player)) {
            return;
        }
        if (!event.getView().getTopInventory().equals(getInventory())) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            return;
        }
        if (rawSlot >= event.getView().getTopInventory().getSize()) {
            // Prevent player-inventory click modes that can pull protected top-row buttons.
            if (event.isShiftClick()
                    || event.getHotbarButton() >= 0
                    || "DOUBLE_CLICK".equals(event.getClick().name())) {
                event.setCancelled(true);
                event.setResult(org.bukkit.event.Event.Result.DENY);
            }
            return;
        }
        if (rawSlot == saveSlot) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
            event.setCursor(null);
            event.getWhoClicked().setItemOnCursor(null);
            saveAndClose();
            return;
        }
        if (cancelSlot != null && rawSlot == cancelSlot) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
            event.setCursor(null);
            event.getWhoClicked().setItemOnCursor(null);
            cancelAndClose();
            return;
        }
        if (rawSlot == COPY_SLOT) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
            event.setCursor(null);
            event.getWhoClicked().setItemOnCursor(null);
            copyFromPlayerInventory();
            return;
        }
        if (rawSlot == CLEAR_SLOT) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
            event.setCursor(null);
            event.getWhoClicked().setItemOnCursor(null);
            clearEditableSlots();
            return;
        }
        if (!editableSlots.contains(rawSlot)) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
            event.setCursor(null);
            event.getWhoClicked().setItemOnCursor(null);
            return;
        }
        if (event.isShiftClick()
                || event.getHotbarButton() >= 0
                || "DOUBLE_CLICK".equals(event.getClick().name())) {
            // Keep editable slots usable, but block click modes that cause ghost/pickup issues.
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (!event.getWhoClicked().equals(player)) {
            return;
        }
        if (!event.getView().getTopInventory().equals(getInventory())) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < getInventory().getSize() && !editableSlots.contains(slot)) {
                event.setCancelled(true);
                event.setResult(org.bukkit.event.Event.Result.DENY);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getPlayer().equals(player)) {
            return;
        }
        if (!event.getInventory().equals(getInventory())) {
            return;
        }
        if (!finished) {
            player.sendMessage(EsportsLang.get("kit.not-saved", "&cChanges were not saved."));
        }
        cleanup();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!event.getPlayer().equals(player)) {
            return;
        }
        if (!finished) {
            cleanup();
        }
    }

    private void cleanup() {
        if (closed) {
            return;
        }
        closed = true;
        HandlerList.unregisterAll(this);
    }
}


















