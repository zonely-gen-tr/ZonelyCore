package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.cash.CashException;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.utils.CenteredMessage;
import dev.zonely.whiteeffect.utils.CommandMessageUtils;
import dev.zonely.whiteeffect.utils.StringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CashCommand extends Commands {

    private static final String DEFAULT_PREFIX = "&3Lobby &8>> ";
    private static final List<String> DEFAULT_USAGE = Arrays.asList(
            "&6&m----------------------------",
            "&2",
            "&e&lCredit System",
            "&2",
            "&e/credit [player] &2- &fShows the player's balance.",
            "&e/credit set [player] [amount] &2- &fSets the player's balance.",
            "&e/credit add [player] [amount] &2- &fAdds credits to the player.",
            "&e/credit remove [player] [amount] &2- &fRemoves credits from the player.",
            "&2",
            "&6&m----------------------------"
    );

    public CashCommand() {
        super("credit");
    }

    @Override
    public void perform(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return;
        }

        String actionInput = args[0].toLowerCase(Locale.ROOT);
        ActionType actionType = resolveAction(actionInput);

        if (args.length == 1 && actionType == null) {
            String target = args[0];
            long balance = CashManager.getCash(target);
            LanguageManager.send(sender,
                    "commands.credit.balance",
                    "{prefix}&b{player} &ehas &6{amount} &ecredits.",
                    "prefix", getPrefix(sender),
                    "player", Role.getColored(target),
                    "name", target,
                    "amount", StringUtils.formatNumber(balance));
            return;
        }

        if (!sender.hasPermission("zcore.cmd.credit")) {
            LanguageManager.send(sender,
                    "commands.credit.no-permission",
                    "{prefix}&cYou do not have permission to use this command.",
                    "prefix", getPrefix(sender));
            return;
        }

        if (actionType == null || args.length < 3) {
            sendUsage(sender);
            return;
        }

        String target = args[1];
        String qtyString = args[2];
        long amount;
        try {
            if (qtyString.startsWith("-")) {
                throw new NumberFormatException();
            }
            amount = Long.parseLong(qtyString);
        } catch (NumberFormatException ex) {
            LanguageManager.send(sender,
                    "commands.credit.invalid-amount",
                    "{prefix}&cAmount must be a positive integer.",
                    "prefix", getPrefix(sender));
            return;
        }

        try {
            switch (actionType) {
                case SET:
                    CashManager.setCash(target, amount);
                    LanguageManager.send(sender,
                            "commands.credit.set",
                            "{prefix}&aSet {player}'s balance to &6{amount}&e.",
                            "prefix", getPrefix(sender),
                            "player", Role.getColored(target),
                            "name", target,
                            "amount", StringUtils.formatNumber(amount));
                    break;
                case ADD:
                    CashManager.addCash(target, amount);
                    LanguageManager.send(sender,
                            "commands.credit.add",
                            "{prefix}&aAdded &6{amount}&e credits to {player}.",
                            "prefix", getPrefix(sender),
                            "player", Role.getColored(target),
                            "name", target,
                            "amount", StringUtils.formatNumber(amount));
                    break;
                case REMOVE:
                    CashManager.removeCash(target, amount);
                    LanguageManager.send(sender,
                            "commands.credit.remove",
                            "{prefix}&aRemoved &6{amount}&e credits from {player}.",
                            "prefix", getPrefix(sender),
                            "player", Role.getColored(target),
                            "name", target,
                            "amount", StringUtils.formatNumber(amount));
                    break;
                default:
                    sendUsage(sender);
            }
        } catch (CashException ex) {
            LanguageManager.send(sender,
                    "commands.credit.error",
                    "{prefix}&cError: {error}",
                    "prefix", getPrefix(sender),
                    "error", ex.getMessage());
        }
    }

    private void sendUsage(CommandSender sender) {
        String version = Core.getInstance().getDescription().getVersion();
        if (sender instanceof Player) {
            Player player = (Player) sender;
            Profile profile = Profile.getProfile(player.getName());
            List<String> lines = CommandMessageUtils.withSignature(
                    LanguageManager.getList(profile,
                            "commands.credit.usage",
                            DEFAULT_USAGE,
                            "version", version));
            for (String line : lines) {
                sender.sendMessage(CenteredMessage.generate(line));
            }
        } else {
            List<String> lines = CommandMessageUtils.withSignature(
                    LanguageManager.getList(
                            "commands.credit.usage",
                            DEFAULT_USAGE,
                            "version", version));
            for (String line : lines) {
                sender.sendMessage(CenteredMessage.generate(line));
            }
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

    private ActionType resolveAction(String input) {
        switch (input) {
            case "belirle":
            case "set":
                return ActionType.SET;
            case "ekle":
            case "add":
                return ActionType.ADD;
            case "kaldir":
            case "kaldirmak":
            case "remove":
                return ActionType.REMOVE;
            default:
                return null;
        }
    }

    private enum ActionType {
        SET,
        ADD,
        REMOVE
    }
}

