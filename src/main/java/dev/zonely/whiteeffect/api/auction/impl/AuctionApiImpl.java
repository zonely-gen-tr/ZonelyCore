package dev.zonely.whiteeffect.api.auction.impl;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.api.auction.AuctionAPI;
import dev.zonely.whiteeffect.store.dao.AuctionDao;
import dev.zonely.whiteeffect.store.menu.AuctionMenuManager;
import dev.zonely.whiteeffect.store.model.AuctionItem;
import org.bukkit.entity.Player;

public class AuctionApiImpl implements AuctionAPI {

    private final Core plugin;

    public AuctionApiImpl(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public void openMainMenu(Player player) {
        AuctionMenuManager manager = getMenuManager();
        if (manager != null && player != null) {
            manager.openAuctionMenu(player);
        }
    }

    @Override
    public void openSoldMenu(Player player) {
        AuctionMenuManager manager = getMenuManager();
        if (manager != null && player != null) {
            manager.openSoldMenu(player);
        }
    }

    @Override
    public void openPurchasedMenu(Player player) {
        AuctionMenuManager manager = getMenuManager();
        if (manager != null && player != null) {
            manager.openPurchasedMenu(player);
        }
    }

    @Override
    public void openPurchaseMenu(Player player, AuctionItem item) {
        AuctionMenuManager manager = getMenuManager();
        if (manager != null && player != null && item != null) {
            manager.openPurchaseMenu(player, item);
        }
    }

    @Override
    public AuctionMenuManager getMenuManager() {
        return plugin.getAuctionMenuManager();
    }

    @Override
    public AuctionDao getDao() {
        AuctionMenuManager manager = getMenuManager();
        return manager != null ? manager.getDao() : null;
    }
}
