package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.store.dao.AuctionDao;
import dev.zonely.whiteeffect.store.model.AuctionItem;
import dev.zonely.whiteeffect.utils.ItemSerializer;
import java.sql.SQLException;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class MezatOyunVerCommand implements CommandExecutor {

    private static final String DEFAULT_PREFIX = "&3Lobby &8->> ";

    private final Core plugin;

    public MezatOyunVerCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String prefix = getPrefix(sender);

        if (!(sender instanceof Player) && !(sender instanceof ConsoleCommandSender)) {
            send(sender,
                    prefix,
                    "commands.auctions.give.invalid-sender",
                    "{prefix}&cOnly players or the console can run this command.");
            return true;
        }

        if (args.length != 2) {
            send(sender,
                    prefix,
                    "commands.auctions.give.usage",
                    "{prefix}&cUsage: /{label} <player> <auctionId>",
                    "label", label);
            return true;
        }

        String playerName = args[0];
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            send(sender,
                    prefix,
                    "commands.auctions.give.offline",
                    "{prefix}&cPlayer is offline or not found: {target}.",
                    "target", playerName);
            return true;
        }

        final int auctionId;
        try {
            auctionId = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            send(sender,
                    prefix,
                    "commands.auctions.give.invalid-id",
                    "{prefix}&cAuction ID must be a whole number: {input}.",
                    "input", args[1]);
            return true;
        }

        send(sender,
                prefix,
                "commands.auctions.give.fetching",
                "{prefix}&eFetching auction data...");

        new BukkitRunnable() {
            @Override
            public void run() {
                AuctionDao dao = plugin.getAuctionMenuManager().getDao();
                List<AuctionItem> bought;
                try {
                    int buyerId = dao.fetchUserId(target.getName());
                    bought = dao.listByBuyer(buyerId);
                } catch (SQLException ex) {
                    MezatOyunVerCommand.this.send(sender,
                            prefix,
                            "commands.auctions.give.database-error",
                            "{prefix}&cDatabase error: {error}",
                            "error", ex.getMessage());
                    plugin.getLogger().severe("Auction fetch error: " + ex.getMessage());
                    return;
                }

                AuctionItem found = bought.stream()
                        .filter(item -> item.getId() == auctionId)
                        .findFirst()
                        .orElse(null);
                if (found == null) {
                    MezatOyunVerCommand.this.send(sender,
                            prefix,
                            "commands.auctions.give.not-owned",
                            "{prefix}&cNo purchase found for auction ID {id}.",
                            "id", auctionId);
                    return;
                }

                final ItemStack deliveredStack;
                try {
                    deliveredStack = ItemSerializer.deserialize(found.getItemData());
                } catch (Exception ex) {
                    MezatOyunVerCommand.this.send(sender,
                            prefix,
                            "commands.auctions.give.deserialize-error",
                            "{prefix}&cUnable to read item data: {error}",
                            "error", ex.getMessage());
                    plugin.getLogger().severe("Item deserialize error: " + ex.getMessage());
                    return;
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        target.getInventory().addItem(deliveredStack);
                        MezatOyunVerCommand.this.send(sender,
                                prefix,
                                "commands.auctions.give.delivered",
                                "{prefix}&a{target} received auction item &b#{id}&a.",
                                "target", target.getName(),
                                "id", auctionId);
                    }
                }.runTask(plugin);

                try {
                    dao.delete(auctionId);
                } catch (SQLException ex) {
                    plugin.getLogger().severe("Auction delete error: " + ex.getMessage());
                }
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }

    private void send(CommandSender sender, String prefix, String key, String def, Object... placeholders) {
        LanguageManager.send(sender, key, def, mergePlaceholders(prefix, placeholders));
    }

    private Object[] mergePlaceholders(String prefix, Object... placeholders) {
        Object[] merged = new Object[placeholders.length + 2];
        merged[0] = "prefix";
        merged[1] = prefix;
        System.arraycopy(placeholders, 0, merged, 2, placeholders.length);
        return merged;
    }

    private String getPrefix(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            Profile profile = Profile.getProfile(player.getName());
            return LanguageManager.get(profile, "prefix.lobby", DEFAULT_PREFIX);
        }
        return LanguageManager.get("prefix.lobby", DEFAULT_PREFIX);
    }
}
