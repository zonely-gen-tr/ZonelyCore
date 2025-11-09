package dev.zonely.whiteeffect.auth.command;

import dev.zonely.whiteeffect.auth.AuthManager;
import dev.zonely.whiteeffect.lang.LanguageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class LoginCommand implements CommandExecutor, TabCompleter {

    private final AuthManager manager;

    public LoginCommand(AuthManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can execute this command.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 1) {
            LanguageManager.send(player, "auth.login.usage",
                    "{prefix}&7Usage: &b/" + label.toLowerCase(Locale.ROOT) + " <password>",
                    "prefix", manager.getPrefix(player),
                    "command", label.toLowerCase(Locale.ROOT));
            return true;
        }
        manager.attemptLogin(player, args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
