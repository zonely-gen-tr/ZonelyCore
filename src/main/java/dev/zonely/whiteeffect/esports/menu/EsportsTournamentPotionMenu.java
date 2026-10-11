package dev.zonely.whiteeffect.esports.menu;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.esports.EsportsLang;
import dev.zonely.whiteeffect.esports.menu.EsportsCreationService.CreationSession;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class EsportsTournamentPotionMenu extends PlayerMenu {

    private static final List<PotionTemplate> TEMPLATES = Arrays.asList(
            new PotionTemplate("SPEED", "Speed", "POTION:8194", Material.POTION),
            new PotionTemplate("STRENGTH", "Strength", "POTION:8201", Material.POTION),
            new PotionTemplate("REGENERATION", "Regeneration", "GOLDEN_APPLE", Material.GOLDEN_APPLE),
            new PotionTemplate("RESISTANCE", "Resistance", "IRON_CHESTPLATE", Material.IRON_CHESTPLATE),
            new PotionTemplate("FIRE_RESISTANCE", "Fire Resistance", "MAGMA_CREAM", Material.MAGMA_CREAM),
            new PotionTemplate("JUMP_BOOST", "Jump Boost", "RABBIT_FOOT", Material.RABBIT_FOOT)
    );
    private static final int BACK_SLOT = 49;

    private final Core plugin;
    private final EsportsCreationService service;
    private final CreationSession session;

    public EsportsTournamentPotionMenu(Core plugin,
                                       org.bukkit.entity.Player player,
                                       EsportsCreationService service,
                                       CreationSession session) {
        super(player, EsportsLang.get("menus.potion.title", "&1Potion Effects"), 6);
        this.plugin = plugin;
        this.service = service;
        this.session = session;
        render();
        this.register(plugin);
        this.open();
    }

    private void render() {
        for (int i = 0; i < TEMPLATES.size(); i++) {
            PotionTemplate template = TEMPLATES.get(i);
            CreationSession.PotionSetting setting = session.getPotionSetting(template.getKey());
            String level = setting == null
                    ? EsportsLang.get("menus.potion.status.off", "&cOff")
                    : EsportsLang.get("menus.potion.status.level", "&aLevel {level}", "level", setting.getLevel());
            String duration = setting == null
                    ? EsportsLang.get("menus.potion.duration.none", "&7--")
                    : (setting.getDurationSeconds() == 0
                    ? EsportsLang.get("menus.potion.duration.infinite", "&fInfinite")
                    : EsportsLang.get("menus.potion.duration.seconds", "&f{seconds}s",
                    "seconds", setting.getDurationSeconds()));
            String desc = EsportsLang.get("menus.potion.item.left", "&7Left click: increase level")
                    + "\\n" + EsportsLang.get("menus.potion.item.right", "&7Right click: change duration")
                    + "\\n" + EsportsLang.get("menus.potion.item.shift", "&7Shift click: remove effect")
                    + "\\n" + EsportsLang.get("menus.potion.item.status", "&7Status: {value}", "value", level)
                    + "\\n" + EsportsLang.get("menus.potion.item.duration", "&7Duration: {value}", "value", duration);
            int slot = 10 + i + (i / 3) * 4;
            setItem(slot, BukkitUtils.deserializeItemStack(
                    template.materialName() + " : 1 : name>&f"
                            + EsportsLang.get("menus.potion.effects." + template.getKey().toLowerCase(Locale.ROOT),
                            template.getDisplayName())
                            + " : desc>" + desc));
        }
        setItem(BACK_SLOT, BukkitUtils.deserializeItemStack(
                mat("ARROW", "ARROW") + " : 1 : name>"
                        + EsportsLang.get("menus.potion.back.name", "&7Back")
                        + " : desc>" + EsportsLang.get("menus.potion.back.lore", "&7Return to main setup.")));

        fillGaps();
    }

    private String mat(String preferred, String legacy) {
        Material material = MaterialResolver.resolveMaterial(preferred);
        if (material == null && legacy != null) {
            material = MaterialResolver.resolveMaterial(legacy);
        }
        if (material == null) {
            material = Material.BOOK;
        }
        return material.name();
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
        PotionTemplate template = resolveTemplate(slot);
        if (template == null) {
            return;
        }
        if (event.isShiftClick()) {
            session.removePotion(template.getKey());
            EnumSound.CLICK.play(player, 1.0F, 0.5F);
            render();
            return;
        }
        if (event.isRightClick()) {
            session.bumpPotionDuration(template.getKey());
            EnumSound.CLICK.play(player, 1.0F, 1.4F);
            render();
            return;
        }
        session.bumpPotionLevel(template.getKey());
        EnumSound.CLICK.play(player, 1.0F, 1.0F);
        render();
    }

    private PotionTemplate resolveTemplate(int slot) {
        for (int i = 0; i < TEMPLATES.size(); i++) {
            PotionTemplate template = TEMPLATES.get(i);
            int mapped = 10 + i + (i / 3) * 4;
            if (mapped == slot) {
                return template;
            }
        }
        return null;
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

    private static final class PotionTemplate {
        private final String key;
        private final String displayName;
        private final String modernToken;
        private final Material legacyFallback;

        private PotionTemplate(String key, String displayName, String modernToken, Material legacyFallback) {
            this.key = key;
            this.displayName = displayName;
            this.modernToken = modernToken;
            this.legacyFallback = legacyFallback;
        }

        String getKey() {
            return key;
        }

        String getDisplayName() {
            return displayName;
        }

        String materialName() {
            Material modern = MaterialResolver.resolveMaterial(modernToken);
            if (modern == null && legacyFallback != null) {
                modern = legacyFallback;
            }
            return modern == null ? Material.POTION.name() : modern.name();
        }
    }
}
