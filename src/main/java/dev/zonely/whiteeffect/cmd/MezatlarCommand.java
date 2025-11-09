package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.store.menu.AuctionMenuManager;
import dev.zonely.whiteeffect.store.model.AuctionItem;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MezatlarCommand implements CommandExecutor {

    private static final String PREFIX_KEY = "prefix.lobby";
    private static final String PREFIX_DEFAULT = "&3Lobby &8» ";

    private final AuctionMenuManager manager;

    public MezatlarCommand(Core plugin) {
        this.manager = plugin.getAuctionMenuManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            LanguageManager.send(sender,
                    "commands.auctions.only-player",
                    "&cOnly players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        Profile profile = Profile.getProfile(player.getName());
        String prefix = LanguageManager.get(profile, PREFIX_KEY, PREFIX_DEFAULT);

        if (args.length == 2 && args[0].equalsIgnoreCase("buy")) {
            try {
                int id = Integer.parseInt(args[1]);
                AuctionItem item = manager.getDao().getById(id);
                if (item != null && item.isApproved() && item.getBuyerName() == null) {
                    manager.openPurchaseMenu(player, item);
                } else {
                    LanguageManager.send(player,
                            "commands.auctions.buy.unavailable",
                            "{prefix}&cThis auction is no longer available.",
                            "prefix", prefix);
                }
            } catch (NumberFormatException ex) {
                LanguageManager.send(player,
                        "commands.auctions.buy.invalid-id",
                        "{prefix}&cInvalid auction ID.",
                        "prefix", prefix);
            } catch (Exception ex) {
                LanguageManager.send(player,
                        "commands.auctions.buy.error",
                        "{prefix}&cAn error occurred while opening that auction.",
                        "prefix", prefix);
            }
            return true;
        }

        manager.openAuctionMenu(player);
        return true;
    }
}


