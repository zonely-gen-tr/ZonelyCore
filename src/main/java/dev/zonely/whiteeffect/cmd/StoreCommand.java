package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.store.ProductMenuManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StoreCommand implements CommandExecutor {

    private static final String DEFAULT_PREFIX = "&3Lobby &8>> ";
    private final Core plugin;

    public StoreCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            LanguageManager.send(sender,
                    "commands.store.only-player",
                    "{prefix}&cOnly players can use this command.",
                    "prefix", LanguageManager.get("prefix.lobby", DEFAULT_PREFIX));
            return true;
        }

        Player player = (Player) sender;
        ProductMenuManager manager = plugin.getProductMenuManager();
        if (!manager.isDataLoaded()) {
            LanguageManager.send(player,
                    "store.product-menu.data-not-ready",
                    "{prefix}&cStore data has not loaded yet; please wait a few seconds.",
                    "prefix", LanguageManager.get("prefix.lobby", DEFAULT_PREFIX));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (manager.isDataLoaded()) {
                    manager.openCategoryMenu(player, 0);
                }
            }, 60L);
            return true;
        }
        manager.openCategoryMenu(player, 0);
        return true;
    }
}
