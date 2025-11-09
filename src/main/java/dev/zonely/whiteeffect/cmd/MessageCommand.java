package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MessageCommand implements CommandExecutor {

    private static final String DEFAULT_PREFIX = "&3Lobby &8>> ";

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            LanguageManager.send(sender,
                    "commands.message.only-player",
                    "{prefix}&cOnly players can use this command.",
                    "prefix", getPrefix());
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            LanguageManager.send(player,
                    "commands.message.usage",
                    "{prefix}&cUsage: /{label} <player> <message>",
                    "prefix", getPrefix(player),
                    "label", label);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            LanguageManager.send(player,
                    "commands.message.offline",
                    "{prefix}&cThat player is not online.",
                    "prefix", getPrefix(player));
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        LanguageManager.send(player,
                "commands.message.sent",
                "&3Message &8» &f[to {target}] &7{message}",
                "target", target.getName(),
                "message", message);

        LanguageManager.send(target,
                "commands.message.received",
                "&3Message &8» &f[{sender}] &7{message}",
                "sender", player.getName(),
                "message", message);

        return true;
    }

    private String getPrefix(Player player) {
        Profile profile = Profile.getProfile(player.getName());
        return LanguageManager.get(profile, "prefix.lobby", DEFAULT_PREFIX);
    }

    private String getPrefix() {
        return LanguageManager.get("prefix.lobby", DEFAULT_PREFIX);
    }
}
