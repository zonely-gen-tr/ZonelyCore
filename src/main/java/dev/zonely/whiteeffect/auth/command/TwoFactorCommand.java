package dev.zonely.whiteeffect.auth.command;

import dev.zonely.whiteeffect.auth.AuthManager;
import dev.zonely.whiteeffect.auth.twofactor.TwoFactorManager;
import dev.zonely.whiteeffect.auth.twofactor.TwoFactorManager.RecoveryResponse;
import dev.zonely.whiteeffect.auth.twofactor.TwoFactorManager.RecoveryResult;
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

public final class TwoFactorCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS =
            Arrays.asList("setup", "confirm", "cancel", "disable", "code", "recovery");

    private final AuthManager authManager;

    public TwoFactorCommand(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can execute this command.");
            return true;
        }

        Player player = (Player) sender;
        TwoFactorManager manager = authManager.getTwoFactorManager();
        if (manager == null || !manager.isEnabled()) {
            LanguageManager.send(player, "auth.two-factor.not-enabled",
                    "{prefix}&cTwo-factor authentication is not enabled.",
                    "prefix", authManager.getPrefix(player));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "setup":
                manager.beginSetup(player);
                return true;
            case "confirm":
                if (args.length < 2) {
                    sendUsage(player);
                    return true;
                }
                manager.confirmSetup(player, args[1]);
                return true;
            case "cancel":
                manager.cancelSetup(player);
                return true;
            case "disable":
                String disableCode = args.length >= 2 ? args[1] : "";
                manager.disableTwoFactor(player, disableCode);
                return true;
            case "code":
                if (args.length < 2) {
                    sendUsage(player);
                    return true;
                }
                manager.submitLoginCode(player, args[1]);
                return true;
            case "recovery":
                RecoveryResponse response = manager.requestRecoveryLink(player);
                handleRecoveryResponse(player, response);
                return true;
            default:
                sendUsage(player);
                return true;
        }
    }

    private void handleRecoveryResponse(Player player, RecoveryResponse response) {
        switch (response.getResult()) {
            case SUCCESS:
                LanguageManager.send(player, "auth.two-factor.recovery-created",
                        "{prefix}&aRecovery link: &b{url}",
                        "prefix", authManager.getPrefix(player),
                        "url", response.getUrl());
                break;
            case DISABLED:
                LanguageManager.send(player, "auth.two-factor.recovery-disabled",
                        "{prefix}&cTwo-factor recovery links are disabled.",
                        "prefix", authManager.getPrefix(player));
                break;
            case COOLDOWN:
                LanguageManager.send(player, "auth.two-factor.recovery-cooldown",
                        "{prefix}&cPlease wait before requesting another recovery link.",
                        "prefix", authManager.getPrefix(player));
                break;
            case UNAVAILABLE:
            default:
                LanguageManager.send(player, "auth.two-factor.recovery-disabled",
                        "{prefix}&cTwo-factor recovery links are disabled.",
                        "prefix", authManager.getPrefix(player));
                break;
        }
    }

    private void sendUsage(Player player) {
        LanguageManager.send(player, "auth.two-factor.usage",
                "{prefix}&7Usage: /2fa <setup|confirm|cancel|disable|code|recovery>",
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
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("confirm".equals(sub) || "disable".equals(sub) || "code".equals(sub)) {
                return Collections.singletonList("<code>");
            }
        }
        return Collections.emptyList();
    }
}
