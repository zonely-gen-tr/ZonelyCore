package dev.zonely.whiteeffect.esports.menu;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.esports.EsportsLang;
import dev.zonely.whiteeffect.esports.EsportsManager;
import dev.zonely.whiteeffect.esports.model.EsportsClubEntry;
import dev.zonely.whiteeffect.esports.model.EsportsTournamentData;
import dev.zonely.whiteeffect.esports.model.EsportsTournamentSummary;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class EsportsTournamentDetailMenu extends PlayerMenu {

    private final Core plugin;
    private final EsportsManager manager;
    private final EsportsTournamentSummary summary;
    private final boolean admin;
    private final EsportsTournamentData detailed;

    public EsportsTournamentDetailMenu(Core plugin,
                                       Player player,
                                       EsportsManager manager,
                                       EsportsTournamentSummary summary,
                                       boolean admin) {
        super(player, EsportsLang.get("menus.detail.title", "&8Tournament #{id}", "id", summary.getId()), 6);
        this.plugin = plugin;
        this.manager = manager;
        this.summary = summary;
        this.admin = admin;
        this.detailed = manager.findTournamentById(summary.getId()).orElse(null);
        fillBackground();
        populate();
        this.register(plugin);
        this.open();
    }

    private void fillBackground() {
        ItemStack filler = BukkitUtils.deserializeItemStack("STAINED_GLASS_PANE:7 : 1 : name>&r");
        for (int slot = 0; slot < getInventory().getSize(); slot++) {
            setItem(slot, filler.clone());
        }
    }

    private void populate() {
        ItemStack info = BukkitUtils.deserializeItemStack("BOOK : 1 : name>&a" + summary.getName()
                + " : desc>" + EsportsLang.get("menus.detail.info.status", "&7Status: &f{state}", "state", summary.getState())
                + "\\n" + EsportsLang.get("menus.detail.info.event", "&7Event: &f{event}", "event", summary.getEventType())
                + "\\n" + EsportsLang.get("menus.detail.info.start", "&7Start: &f{start}", "start", nullSafe(summary.getStartsAt()))
                + "\\n" + EsportsLang.get("menus.detail.info.end", "&7End: &f{end}", "end", nullSafe(summary.getEndsAt())));
        setItem(10, info);

        if (detailed != null) {
            setItem(12, buildClubsItem(detailed.getClubs()));
        }

        if (admin) {
            ItemStack reload = BukkitUtils.deserializeItemStack("REDSTONE_BLOCK : 1 : name>"
                    + EsportsLang.get("menus.detail.change-active.name", "&cChange Active Tournament")
                    + " : desc>" + EsportsLang.get("menus.detail.change-active.lore", "&7Update config to make this tournament active."));
            setItem(40, reload);
        }
    }

    private ItemStack buildClubsItem(List<EsportsClubEntry> clubs) {
        StringBuilder builder = new StringBuilder();
        int count = Math.min(clubs.size(), 10);
        for (int i = 0; i < count; i++) {
            EsportsClubEntry club = clubs.get(i);
            builder.append("&7- &f")
                    .append(club.getName())
                    .append(" (&e")
                    .append(club.getTag())
                    .append("&f)");
            if (i + 1 < count) {
                builder.append("\\n");
            }
        }
        if (clubs.size() > count) {
            builder.append("\\n").append(EsportsLang.get("menus.detail.clubs.more", "&7... and {count} more clubs",
                    "count", clubs.size() - count));
        }
        return BukkitUtils.deserializeItemStack("MAP : 1 : name>"
                + EsportsLang.get("menus.detail.clubs.name", "&bClub List")
                + " : desc>" + (builder.length() == 0
                ? EsportsLang.get("menus.detail.clubs.empty", "&7No entries")
                : builder));
    }

    private String nullSafe(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(this.player)) return;
        if (!event.getView().getTopInventory().equals(getInventory())) return;
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);
        event.setCursor(null);
        event.getWhoClicked().setItemOnCursor(null);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            Bukkit.getScheduler().runTask(plugin, this.player::updateInventory);
            return;
        }

        if (event.getRawSlot() == 40 && admin) {
            this.player.closeInventory();
            this.player.sendMessage(EsportsLang.get("menus.detail.change-active.message",
                    "&eChange active tournament via config or Bukkit.yml."));
            return;
        }

        EnumSound.CLICK.play(this.player, 1.0F, 1.0F);
        Bukkit.getScheduler().runTask(plugin, this.player::updateInventory);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(this.player) && event.getInventory().equals(getInventory())) {
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!event.getWhoClicked().equals(this.player)) return;
        if (!event.getView().getTopInventory().equals(getInventory())) return;
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
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(this.player)) {
            HandlerList.unregisterAll(this);
        }
    }
}
