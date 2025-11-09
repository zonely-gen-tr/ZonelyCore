package dev.zonely.whiteeffect.auth.command;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.auth.AuthAccount;
import dev.zonely.whiteeffect.auth.AuthManager;
import dev.zonely.whiteeffect.auth.AuthSession;
import dev.zonely.whiteeffect.auth.email.AuthMailService;
import dev.zonely.whiteeffect.auth.recovery.AuthRecoveryService;
import dev.zonely.whiteeffect.lang.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class PasswordResetCommand implements CommandExecutor, TabCompleter {

    private final AuthManager authManager;

    public PasswordResetCommand(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can execute this command.");
            return true;
        }

        Player player = (Player) sender;
        AuthRecoveryService service = authManager.getRecoveryService();
        if (service == null || !service.isPasswordResetEnabled()) {
            LanguageManager.send(player, "auth.password-reset.disabled",
                    "{prefix}&cPassword reset feature is disabled.",
                    "prefix", authManager.getPrefix(player));
            return true;
        }

        if (args.length > 0) {
            LanguageManager.send(player, "auth.password-reset.usage",
                    "{prefix}&7Usage: /passwordreset",
                    "prefix", authManager.getPrefix(player));
            return true;
        }

        AuthSession session = authManager.getSession(player.getUniqueId()).orElse(null);
        AuthAccount account = session != null ? session.getAccount() : null;
        if (account == null) {
            account = authManager.findAccount(player.getName()).orElse(null);
        }

        if (account == null) {
            LanguageManager.send(player, "auth.password-reset.no-account",
                    "{prefix}&cYour web account could not be located.",
                    "prefix", authManager.getPrefix(player));
            return true;
        }

        String email = account.getEmail();
        if (email == null || email.trim().isEmpty()) {
            LanguageManager.send(player, "auth.password-reset.missing-email",
                    "{prefix}&cNo email address is associated with this account.",
                    "prefix", authManager.getPrefix(player));
            return true;
        }

        String address = session != null ? session.getAddress() : getPlayerAddress(player);
        Optional<String> link = service.createPasswordReset(account, address);
        if (!link.isPresent()) {
            LanguageManager.send(player, "auth.password-reset.disabled",
                    "{prefix}&cPassword reset feature is disabled.",
                    "prefix", authManager.getPrefix(player));
            return true;
        }

        String url = link.get();
        String targetEmail = email.trim();
        AuthMailService mailService = authManager.getMailService();
        if (mailService != null && mailService.isOperational()) {
            LanguageManager.send(player, "auth.password-reset.mail-sending",
                    "{prefix}&7Sending a password reset email to &f{email}&7...",
                    "prefix", authManager.getPrefix(player),
                    "email", targetEmail);
            mailService.sendPasswordResetEmail(account, url).whenComplete((success, throwable) ->
                    Bukkit.getScheduler().runTask(Core.getInstance(), () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (throwable != null || !Boolean.TRUE.equals(success)) {
                            LanguageManager.send(player, "auth.password-reset.mail-failed",
                                    "{prefix}&cUnable to email a reset link. Use this URL: &b{url}",
                                    "prefix", authManager.getPrefix(player),
                                    "url", url);
                        } else {
                            LanguageManager.send(player, "auth.password-reset.mail-sent",
                                    "{prefix}&aPassword reset email sent to &f{email}&a.",
                                    "prefix", authManager.getPrefix(player),
                                    "email", targetEmail);
                        }
                    }));
            return true;
        }

        LanguageManager.send(player, "auth.password-reset.created",
                "{prefix}&aPassword reset link: &b{url}",
                "prefix", authManager.getPrefix(player),
                "url", url);
        return true;
    }

    private String getPlayerAddress(Player player) {
        if (player.getAddress() == null) {
            return "0.0.0.0";
        }
        if (player.getAddress() instanceof InetSocketAddress) {
            InetSocketAddress socket = (InetSocketAddress) player.getAddress();
            if (socket.getAddress() != null) {
                return socket.getAddress().getHostAddress();
            }
        }
        return player.getAddress().getAddress().getHostAddress();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
