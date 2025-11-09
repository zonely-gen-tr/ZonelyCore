package dev.zonely.whiteeffect.api.auction;

import dev.zonely.whiteeffect.store.dao.AuctionDao;
import dev.zonely.whiteeffect.store.menu.AuctionMenuManager;
import dev.zonely.whiteeffect.store.model.AuctionItem;
import org.bukkit.entity.Player;

public interface AuctionAPI {

    void openMainMenu(Player player);

    void openSoldMenu(Player player);

    void openPurchasedMenu(Player player);

    void openPurchaseMenu(Player player, AuctionItem item);

    AuctionMenuManager getMenuManager();

    AuctionDao getDao();
}
