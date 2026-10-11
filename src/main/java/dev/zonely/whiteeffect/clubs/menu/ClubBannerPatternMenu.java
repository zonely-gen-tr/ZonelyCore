package dev.zonely.whiteeffect.clubs.menu;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.clubs.ClubLang;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ClubBannerPatternMenu extends PlayerMenu {

    private static final int INFO_SLOT = 4;
    private static final int PREV_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int[] PATTERN_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final Core plugin;
    private final Consumer<PatternType> handler;
    private final Runnable backAction;
    private final List<PatternType> options = new ArrayList<>();
    private boolean consumed;
    private int page;

    public ClubBannerPatternMenu(Core plugin,
                                 Player player,
                                 Consumer<PatternType> handler,
                                 Runnable backAction) {
        super(player, ClubLang.get("menus.banner-pattern.title", "&3Pattern Selector"), 6);
        this.plugin = plugin;
        this.handler = handler;
        this.backAction = backAction;
        populateOptions();
        render();
    }

    private void populateOptions() {
        for (PatternType type : PatternType.values()) {
            if (type == null || type == PatternType.BASE) {
                continue;
            }
            options.add(type);
        }
    }

    private void render() {
        fillBackground();
        int pages = Math.max(1, (int) Math.ceil(options.size() / (double) PATTERN_SLOTS.length));
        if (page < 0) {
            page = 0;
        }
        if (page >= pages) {
            page = pages - 1;
        }

        int start = page * PATTERN_SLOTS.length;
        int end = Math.min(options.size(), start + PATTERN_SLOTS.length);
        int cardIndex = 0;
        for (int idx = start; idx < end; idx++) {
            PatternType type = options.get(idx);
            String friendly = type.name().toLowerCase().replace('_', ' ');
            setItem(PATTERN_SLOTS[cardIndex++], createPatternIcon(type, friendly));
        }
        while (cardIndex < PATTERN_SLOTS.length) {
            setItem(PATTERN_SLOTS[cardIndex++], BukkitUtils.deserializeItemStack(
                    "STAINED_GLASS_PANE:7 : 1 : name>&r"));
        }

        setItem(INFO_SLOT, BukkitUtils.deserializeItemStack(
                "BOOK : 1 : name>" + ClubLang.get("menus.banner-pattern.info.name", "&b&lPattern Selection")
                        + " : desc>" + ClubLang.get("menus.banner-pattern.info.lore.1", "&7Choose a banner pattern.")
                        + "\\n" + ClubLang.get("menus.banner-pattern.info.page", "&7Page: &f{current}&7/&f{pages}",
                        "current", page + 1,
                        "pages", pages)
                        + "\\n" + ClubLang.get("menus.banner-pattern.info.lore.2", "&7Then you will pick a color.")
        ));
        setItem(PREV_SLOT, BukkitUtils.deserializeItemStack(
                "ARROW : 1 : name>" + ClubLang.get("menus.banner-pattern.prev.name", "&ePrevious")
                        + " : desc>" + ClubLang.get("menus.banner-pattern.prev.lore", "&7Go to previous page.")
                        + "\\n" + ClubLang.get("menus.banner-pattern.current", "&7Current: &f{page}", "page", page + 1)
        ));
        setItem(BACK_SLOT, BukkitUtils.deserializeItemStack(
                "ARROW : 1 : name>" + ClubLang.get("menus.banner-pattern.back.name", "&7Back")
                        + " : desc>" + ClubLang.get("menus.banner-pattern.back.lore", "&7Return to banner editor.")
        ));
        setItem(NEXT_SLOT, BukkitUtils.deserializeItemStack(
                "ARROW : 1 : name>" + ClubLang.get("menus.banner-pattern.next.name", "&eNext")
                        + " : desc>" + ClubLang.get("menus.banner-pattern.next.lore", "&7Go to next page.")
                        + "\\n" + ClubLang.get("menus.banner-pattern.current", "&7Current: &f{page}", "page", page + 1)
        ));
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
        if (raw == BACK_SLOT) {
            consumed = true;
            backAction.run();
            return;
        }
        if (raw == PREV_SLOT) {
            if (page > 0) {
                page--;
                render();
            }
            return;
        }
        if (raw == NEXT_SLOT) {
            int pages = Math.max(1, (int) Math.ceil(options.size() / (double) PATTERN_SLOTS.length));
            if (page + 1 < pages) {
                page++;
                render();
            }
            return;
        }
        int localIndex = -1;
        for (int i = 0; i < PATTERN_SLOTS.length; i++) {
            if (PATTERN_SLOTS[i] == raw) {
                localIndex = i;
                break;
            }
        }
        if (localIndex < 0) {
            return;
        }
        int absoluteIndex = (page * PATTERN_SLOTS.length) + localIndex;
        if (absoluteIndex < 0 || absoluteIndex >= options.size()) {
            return;
        }
        consumed = true;
        PatternType type = options.get(absoluteIndex);
        if (handler != null) {
            handler.accept(type);
        }
    }

    private ItemStack createPatternIcon(PatternType type, String friendlyName) {
        Material bannerMat = MaterialResolver.resolveMaterial("WHITE_BANNER");
        if (bannerMat == null) {
            bannerMat = MaterialResolver.resolveMaterial("BANNER");
        }
        if (bannerMat == null) {
            bannerMat = Material.PAPER;
        }
        ItemStack stack = new ItemStack(bannerMat);
        if (stack.getItemMeta() instanceof BannerMeta) {
            BannerMeta meta = (BannerMeta) stack.getItemMeta();
            applyBaseColor(meta, DyeColor.WHITE);
            meta.addPattern(new Pattern(DyeColor.RED, type));
            meta.setDisplayName(ClubLang.get("menus.banner-pattern.option.name", "&b{name}", "name", friendlyName));
            List<String> lore = new ArrayList<>();
            lore.add(ClubLang.get("menus.banner-pattern.option.pattern", "&7Pattern: &f{value}", "value", type.name()));
            lore.add(ClubLang.get("menus.banner-pattern.option.click", "&eClick to select"));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        } else {
            stack = BukkitUtils.deserializeItemStack("PAPER : 1 : name>"
                    + ClubLang.get("menus.banner-pattern.option.name", "&b{name}", "name", friendlyName));
        }
        return stack;
    }

    private static final Method SET_BASE_COLOR_METHOD;
    static {
        Method m = null;
        try {
            m = BannerMeta.class.getMethod("setBaseColor", DyeColor.class);
        } catch (NoSuchMethodException ignored) {
        }
        SET_BASE_COLOR_METHOD = m;
    }

    private void applyBaseColor(BannerMeta meta, DyeColor color) {
        if (SET_BASE_COLOR_METHOD != null) {
            try {
                SET_BASE_COLOR_METHOD.invoke(meta, color);
                return;
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        meta.addPattern(new Pattern(color, PatternType.BASE));
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
            if (!consumed && backAction != null) {
                backAction.run();
            }
        }
    }
}
