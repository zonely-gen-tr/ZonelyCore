package dev.zonely.whiteeffect.auth.command;

import dev.zonely.whiteeffect.auth.AuthManager;
import dev.zonely.whiteeffect.auth.email.EmailVerificationManager;
import dev.zonely.whiteeffect.lang.LanguageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class EmailCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = Arrays.asList("set", "resend", "status");

    private final AuthManager authManager;

    public EmailCommand(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can execute this command.");
            return true;
        }

        Player player = (Player) sender;
        EmailVerificationManager manager = authManager.getEmailManager();
        if (manager == null || !manager.isEnabled()) {
            LanguageManager.send(player, "auth.email.disabled",
                    "{prefix}&cEmail verification is disabled.",
                    "prefix", authManager.getPrefix(player));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set":
                if (args.length < 2) {
                    sendUsage(player);
                    return true;
                }
                manager.handleSetEmail(player, args[1]);
                return true;
            case "resend":
                manager.handleResend(player);
                return true;
            case "status":
                manager.handleStatus(player);
                return true;
            default:
                sendUsage(player);
                return true;
        }
    }

    private void sendUsage(Player player) {
        LanguageManager.send(player, "auth.email.usage",
                "{prefix}&7Usage: /email <set|resend|status>",
                "prefix", authManager.getPrefix(player));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String entered = args[0].toLowerCase(Locale.ROOT);
            return SUB_COMMANDS.stream()
                    .filter(sub -> sub.startsWith(entered))
                    .collect(java.util.stream.Collectors.toList());
        }
        if (args.length == 2 && "set".equalsIgnoreCase(args[0])) {
            return Collections.singletonList("example@mail.com");
        }
        return Collections.emptyList();
    }
}
