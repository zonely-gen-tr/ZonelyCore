package dev.zonely.whiteeffect.clubs.menu;

import dev.zonely.whiteeffect.clubs.model.ClubData;
import dev.zonely.whiteeffect.clubs.ClubLang;
import dev.zonely.whiteeffect.clubs.model.ClubMember;
import dev.zonely.whiteeffect.libraries.menu.PlayerMenu;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class ClubMembersMenu extends PlayerMenu {

    private static final int INFO_SLOT = 4;
    private static final int CLOSE_SLOT = 49;
    private static final int[] MEMBER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    public ClubMembersMenu(Player player, ClubData club) {
        super(player, ClubLang.get("menus.members.title", "&8Members - {name}", "name", club.getName()), 6);
        getInventory().clear();
        fillBackground();
        setItem(INFO_SLOT, BukkitUtils.deserializeItemStack(
                "BOOK : 1 : name>" + ClubLang.get("menus.members.info.name", "&b&lMember List")
                        + " : desc>" + ClubLang.get("menus.members.info.club", "&7Club: &f{name}", "name", club.getName())
                        + "\\n" + ClubLang.get("menus.members.info.tag", "&7Tag: &f{tag}", "tag", club.getTag())
                        + "\\n" + ClubLang.get("menus.members.info.total", "&7Total: &f{count}", "count", club.getMembers().size())
        ));
        setItem(CLOSE_SLOT, BukkitUtils.deserializeItemStack(
                "BARRIER : 1 : name>" + ClubLang.get("menus.members.close.name", "&c&lClose")
                        + " : desc>" + ClubLang.get("menus.members.close.lore", "&7Close this menu.")
        ));
        int slot = 0;
        for (ClubMember member : club.getMembers()) {
            if (slot >= MEMBER_SLOTS.length) {
                break;
            }
            String role = member.getRole() == null ? "-" : member.getRole().name();
            String joinedAt = member.getJoinedAt() == null ? "-" : String.valueOf(member.getJoinedAt());
            String lore = ClubLang.get("menus.members.member.role", "&7Role: &f{role}", "role", role)
                    + "\\n" + ClubLang.get("menus.members.member.joined", "&7Joined: &f{date}", "date", joinedAt);
            setItem(MEMBER_SLOTS[slot++], BukkitUtils.deserializeItemStack(
                    "SKULL_ITEM:3 : 1 : name>&a" + member.getUsername() + " : desc>" + lore));
        }
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
        if (!event.getWhoClicked().equals(this.player)) {
            return;
        }
        if (!event.getView().getTopInventory().equals(getInventory())) {
            return;
        }
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);
        int rawSlot = event.getRawSlot();
        if (rawSlot == CLOSE_SLOT) {
            player.closeInventory();
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

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().equals(this.player) && event.getInventory().equals(getInventory())) {
            HandlerList.unregisterAll(this);
        }
    }
}
