package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.store.SaleManager;
import dev.zonely.whiteeffect.utils.CenteredMessage;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SaleCommand extends Commands {

    private static final String DEFAULT_PREFIX = "&3Lobby &8>> ";
    private static final List<String> DEFAULT_HEADER = Arrays.asList("&6&m----- Last Sale -----");
    private static final List<String> DEFAULT_FOOTER = Arrays.asList("&6&m-----------------------");

    public SaleCommand() {
        super("sales", "satis");
    }

    @Override
    public void perform(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            showLastSale(sender);
            return;
        }

        if (!sender.hasPermission("zcore.cmd.satis")) {
            LanguageManager.send(sender,
                    "commands.sale.no-permission",
                    "{prefix}&cYou do not have permission to use this command.",
                    "prefix", getPrefix(sender));
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "ekle":
            case "add":
                if (args.length < 3) {
                    LanguageManager.send(sender,
                            "commands.sale.usage-add",
                            "{prefix}&cUsage: /{label} add <player> <product>",
                            "prefix", getPrefix(sender),
                            "label", label);
                    return;
                }
                SaleManager.setSale(args[1], args[2]);
                LanguageManager.send(sender,
                        "commands.sale.updated",
                        "{prefix}&aLast sale updated: &b{player} &7-> &b{product}",
                        "prefix", getPrefix(sender),
                        "player", args[1],
                        "product", args[2]);
                break;

            case "temizle":
            case "clear":
                SaleManager.clearSale();
                LanguageManager.send(sender,
                        "commands.sale.cleared",
                        "{prefix}&aLast sale cleared.",
                        "prefix", getPrefix(sender));
                break;

            default:
                LanguageManager.send(sender,
                        "commands.sale.unknown",
                        "{prefix}&cUnknown sub-command.",
                        "prefix", getPrefix(sender));
        }
    }

    private void showLastSale(CommandSender sender) {
        for (String line : LanguageManager.getList(
                "commands.sale.display.header",
                DEFAULT_HEADER)) {
            sender.sendMessage(CenteredMessage.generate(line));
        }

        String buyer = SaleManager.getLastBuyer();
        String product = SaleManager.getLastProduct();
        if (buyer == null) {
            LanguageManager.send(sender,
                    "commands.sale.display.empty",
                    "&cNo sales have been recorded yet.");
        } else {
            LanguageManager.send(sender,
                    "commands.sale.display.buyer",
                    "&ePlayer: &b{buyer}",
                    "buyer", buyer);
            LanguageManager.send(sender,
                    "commands.sale.display.product",
                    "&eProduct: &b{product}",
                    "product", product);
        }

        for (String line : LanguageManager.getList(
                "commands.sale.display.footer",
                DEFAULT_FOOTER)) {
            sender.sendMessage(CenteredMessage.generate(line));
        }
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

