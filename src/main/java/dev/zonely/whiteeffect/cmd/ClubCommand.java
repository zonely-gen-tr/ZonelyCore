package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.clubs.ClubLang;
import dev.zonely.whiteeffect.clubs.ClubManager;
import dev.zonely.whiteeffect.clubs.menu.ClubCreationMenu;
import dev.zonely.whiteeffect.clubs.menu.ClubManagementMenu;
import dev.zonely.whiteeffect.clubs.model.ClubData;
import dev.zonely.whiteeffect.utils.CenteredMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ClubCommand extends Commands {

    private final Core plugin;

    public ClubCommand(Core plugin) {
        super("club", "kulup");
        this.plugin = plugin;
        this.setDescription("Club creation and management command.");
        this.setUsage("/club");
    }

    private String msg(String key, String def, Object... placeholders) {
        return ClubLang.get("command." + key, def, placeholders);
    }

    private void send(CommandSender sender, String key, String def, Object... placeholders) {
        sender.sendMessage(msg(key, def, placeholders));
    }

    @Override
    public void perform(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            send(sender, "common.only-player", "&cThis command can only be used in-game.");
            return;
        }
        Player player = (Player) sender;
        ClubManager manager = plugin.getClubManager();
        if (manager == null) {
            send(player, "common.system-unavailable", "&cClub system is currently unavailable.");
            return;
        }
        if (args.length == 0) {
            openDefault(player, manager);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create":
                if (manager.isMemberOfAnyClub(player)) {
                    send(player, "create.already-member", "&cYou cannot create a club while already in one.");
                    return;
                }
                ClubManager.ClubCreationSession session = manager.beginCreation(player);
                ClubCreationMenu menu = new ClubCreationMenu(plugin, player, session);
                menu.register(plugin);
                menu.open();
                break;

            case "menu":
                openDefault(player, manager);
                break;

            case "invite":
                if (args.length < 2) {
                    send(player, "invite.usage", "&c/club invite <player>");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    send(player, "invite.target-offline", "&cPlayer not found or offline.");
                    return;
                }
                manager.inviteMember(player, target);
                break;

            case "kick":
                if (args.length < 2) {
                    send(player, "kick.usage", "&c/club kick <player>");
                    return;
                }
                manager.kickMember(player, args[1]);
                break;

            case "leave":
                manager.leaveClub(player);
                break;

            case "help":
            case "yardim":
                sendCenteredHelp(player);
                break;

            default:
                sendCenteredHelp(player);
                break;
        }
    }

    private void openDefault(Player player, ClubManager manager) {
        Optional<ClubData> clubOpt = manager.getClubByOwner(player);
        if (!clubOpt.isPresent()) {
            send(player, "open.no-owned-club", "&cYou have not created a club yet. Use /club create.");
            return;
        }
        ClubManagementMenu menu = new ClubManagementMenu(plugin, player, clubOpt.get());
        menu.register(plugin);
        menu.open();
    }

    private void sendCenteredHelp(Player player) {
        if (player == null) {
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add(msg("help.header", "&6&m----------------------------"));
        lines.add(msg("help.blank", "&2"));
        lines.add(msg("help.title", "&b&lClub Commands"));
        lines.add(msg("help.blank", "&2"));
        lines.add(msg("help.create", "&e/club create &2- &fCreate a new club."));
        lines.add(msg("help.menu", "&e/club menu &2- &fOpen the club panel."));
        lines.add(msg("help.invite", "&e/club invite <player> &2- &fInvite a player to your club."));
        lines.add(msg("help.kick", "&e/club kick <player> &2- &fRemove a member from your club."));
        lines.add(msg("help.leave", "&e/club leave &2- &fLeave your current club."));
        lines.add(msg("help.help", "&e/club help &2- &fShow this help panel."));
        lines.add(msg("help.blank", "&2"));
        lines.add(msg("help.header", "&6&m----------------------------"));

        for (String raw : lines) {
            if (raw == null || raw.isEmpty()) {
                player.sendMessage("");
                continue;
            }
            player.sendMessage(CenteredMessage.generate(raw));
        }
    }
}
