package dev.zonely.whiteeffect.store;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class LastCreditsHolder implements InventoryHolder {
    private final LastCreditsMenuManager.Category category;
    private final int page;

    public LastCreditsHolder(LastCreditsMenuManager.Category category, int page) {
        this.category = category;
        this.page     = page;
    }

    @Override
    public Inventory getInventory() {
        return null; 
    }

    public LastCreditsMenuManager.Category getCategory() { return category; }
    public int getPage()                     { return page;     }
}