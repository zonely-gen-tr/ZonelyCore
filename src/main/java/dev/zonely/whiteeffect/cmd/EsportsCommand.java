package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.clubs.ClubManager;
import dev.zonely.whiteeffect.clubs.model.ClubData;
import dev.zonely.whiteeffect.esports.EsportsLang;
import dev.zonely.whiteeffect.esports.EsportsManager;
import dev.zonely.whiteeffect.esports.game.EsportsGameService;
import dev.zonely.whiteeffect.esports.menu.EsportsBracketMenu;
import dev.zonely.whiteeffect.esports.menu.EsportsCreationService;
import dev.zonely.whiteeffect.esports.menu.EsportsTournamentListMenu;
import dev.zonely.whiteeffect.esports.model.EsportsClubEntry;
import dev.zonely.whiteeffect.esports.model.EsportsMatchData;
import dev.zonely.whiteeffect.esports.model.EsportsTournamentData;
import dev.zonely.whiteeffect.utils.CenteredMessage;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class EsportsCommand extends Commands {

    private final Core plugin;

    public EsportsCommand(Core plugin) {
        super("esports", "esport");
        this.plugin = plugin;
    }

    private String msg(String key, String def, Object... placeholders) {
        return EsportsLang.get("command." + key, def, placeholders);
    }

    private void send(CommandSender sender, String key, String def, Object... placeholders) {
        sender.sendMessage(msg(key, def, placeholders));
    }

    private void handleMatchCommand(CommandSender sender, EsportsGameService service, String[] args) {
        if (args.length < 2) {
            send(sender, "match.usage", "&cUsage: /esports match <id|clear>");
            return;
        }
        if ("clear".equalsIgnoreCase(args[1])) {
            service.setCurrentMatchId(null);
            send(sender, "match.cleared", "&eActive match ID has been reset.");
            return;
        }
        try {
            int matchId = Integer.parseInt(args[1]);
            if (matchId <= 0) {
                send(sender, "match.id-positive", "&cMatch ID must be positive.");
                return;
            }
            service.setCurrentMatchId(matchId);
            send(sender, "match.id-set", "&aActive match ID set to {id}.", "id", matchId);
        } catch (NumberFormatException ex) {
            send(sender, "match.id-numeric", "&cMatch ID must be numeric.");
        }
    }

    private void handleAdminCommand(CommandSender sender, EsportsCreationService creationService, String[] args) {
        if (!sender.hasPermission("esports.manage")) {
            send(sender, "common.no-permission-admin", "&cYou do not have permission to use esports admin commands.");
            return;
        }
        if (creationService == null) {
            send(sender, "admin.service-inactive", "&cCreation service is not active.");
            return;
        }
        if (args.length < 2) {
            send(sender, "admin.usage", "&eUsage: /esports admin <create|cancel>");
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create":
                if (!(sender instanceof Player)) {
                    send(sender, "common.only-player", "&cThis command can only be used in-game.");
                    return;
                }
                creationService.begin((Player) sender);
                break;
            case "cancel":
                if (!(sender instanceof Player)) {
                    send(sender, "common.only-player", "&cThis command can only be used in-game.");
                    return;
                }
                creationService.cancel((Player) sender, true);
                break;
            default:
                send(sender, "admin.unknown-subcommand", "&eUnknown admin subcommand.");
                break;
        }
    }

    private void handleLeaderboardCommand(CommandSender sender, EsportsGameService service, String[] args) {
        if (!(sender instanceof Player) && (args.length < 2 || !"remove".equalsIgnoreCase(args[1]))) {
            send(sender, "common.only-player", "&cThis command can only be used in-game.");
            return;
        }
        if (args.length < 2) {
            send(sender, "leaderboard.usage", "&eUsage: /esports leaderboard <list|add|remove>");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list":
                List<String> ids = service.listLeaderboardIds();
                send(sender, "leaderboard.ids", "&bLeaderboard IDs: &f{ids}", "ids",
                        ids.isEmpty() ? "-" : String.join(", ", ids));
                break;
            case "add":
                if (!(sender instanceof Player)) {
                    send(sender, "common.only-player", "&cThis command can only be used in-game.");
                    return;
                }
                if (args.length < 4) {
                    send(sender, "leaderboard.add.usage", "&cUsage: /esports leaderboard add <id> <rank>");
                    return;
                }
                try {
                    int rank = Integer.parseInt(args[3]);
                    service.spawnLeaderboardHologram((Player) sender, args[2], rank);
                } catch (NumberFormatException ex) {
                    send(sender, "leaderboard.rank-numeric", "&cRank must be numeric.");
                }
                break;
            case "remove":
                if (args.length < 3) {
                    send(sender, "leaderboard.remove.usage", "&cUsage: /esports leaderboard remove <id>");
                    return;
                }
                boolean removed = service.removeLeaderboardHologram(args[2]);
                send(sender,
                        removed ? "leaderboard.remove.success" : "leaderboard.remove.not-found",
                        removed ? "&aLeaderboard hologram removed." : "&cHologram not found.");
                break;
            default:
                send(sender, "leaderboard.usage", "&eUsage: /esports leaderboard <list|add|remove>");
        }
    }

    @Override
    public void perform(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            printUsage(sender);
            return;
        }

        EsportsManager manager = plugin.getEsportsManager();
        EsportsGameService service = plugin.getEsportsGameService();
        EsportsCreationService creationService = plugin.getEsportsCreationService();

        if (manager == null || service == null) {
            send(sender, "common.module-disabled", "&cEsports module is disabled.");
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "tournaments":
                handleTournamentMenu(sender, manager, args);
                return;
            case "register":
            case "basvur":
            case "katil":
                handleClubRegister(sender, manager, service);
                return;
            case "watch":
            case "izle":
                handleSpectatorCommand(sender, service);
                return;
            case "bannerdebug":
                if (!(sender instanceof Player)) {
                    send(sender, "common.only-player", "&cThis command can only be used in-game.");
                    return;
                }
                service.spawnDebugBanner((Player) sender);
                return;
            case "bracket":
            case "agac":
                handleBracketView(sender, manager);
                return;
        }

        if (!sender.hasPermission("esports.manage")) {
            send(sender, "common.no-permission", "&cYou do not have permission to use this command.");
            return;
        }

        switch (sub) {
            case "reload":
                if (manager.reloadNow()) {
                    send(sender, "reload.success", "&aTournament data refreshed.");
                } else {
                    send(sender, "reload.failed", "&cFailed to load tournament data.");
                }
                break;

            case "start":
                if (args.length < 2) {
                    service.startNextQueuedClub(sender);
                } else {
                    service.startClubRound(args[1], sender);
                }
                break;

            case "delete":
                handleTournamentDeleteCommand(sender, manager, args);
                break;

            case "setspawn":
                if (!(sender instanceof Player)) {
                    send(sender, "common.only-player", "&cThis command can only be used in-game.");
                    return;
                }
                if (args.length < 2) {
                    send(sender, "setspawn.usage", "&cUsage: /esports setspawn <club>");
                    return;
                }
                Player spawnSetter = (Player) sender;
                if (service.setClubSpawn(args[1], spawnSetter)) {
                    send(sender, "setspawn.saved", "&aSpawn saved.");
                } else {
                    send(sender, "setspawn.failed", "&cSpawn could not be saved (is the club tag correct?).");
                }
                break;

            case "kit":
                handleKitCommand(sender, service, args);
                break;

            case "match":
                handleMatchCommand(sender, service, args);
                break;

            case "clear":
                service.clearMatch(sender);
                break;

            case "status":
                service.printStatus(sender);
                break;

            case "admin":
                handleAdminCommand(sender, creationService, args);
                break;

            case "leaderboard":
            case "leaderboards":
                handleLeaderboardCommand(sender, service, args);
                break;

            case "hologram":
                if (args.length >= 3 && "remove".equalsIgnoreCase(args[1])) {
                    boolean removed = service.removeHologram(args[2]);
                    send(sender,
                            removed ? "hologram.remove.success" : "hologram.remove.not-found",
                            removed ? "&aHologram removed." : "&cHologram not found.");
                    break;
                }
                if (!(sender instanceof Player)) {
                    send(sender, "common.only-player", "&cThis command can only be used in-game.");
                    return;
                }
                if (args.length < 2) {
                    send(sender, "hologram.usage", "&cUsage: /esports hologram <id>");
                    return;
                }
                service.spawnEsportsHologram((Player) sender, args[1]);
                break;

            case "start-tournament":
            case "begin":
            case "launch":
                handleTournamentStartCommand(sender, manager);
                break;

            default:
                printUsage(sender);
        }
    }

    private void handleClubRegister(CommandSender sender, EsportsManager manager, EsportsGameService service) {
        if (!(sender instanceof Player)) {
            send(sender, "common.only-player", "&cThis command can only be used in-game.");
            return;
        }
        Player player = (Player) sender;
        ClubManager clubManager = plugin.getClubManager();
        if (clubManager == null) {
            send(player, "common.club-system-unavailable", "&cClub system is unavailable.");
            return;
        }
        Optional<ClubData> clubOpt = clubManager.getClubByOwner(player);
        if (!clubOpt.isPresent()) {
            clubOpt = clubManager.getClubByMember(player);
        }
        if (!clubOpt.isPresent()) {
            send(player, "register.no-club", "&cYou must belong to a club to join the tournament.");
            return;
        }
        EsportsTournamentData tournament = manager.getActiveTournament();
        if (tournament == null) {
            send(player, "common.no-active-tournament", "&cNo active tournament found.");
            return;
        }
        ClubData club = clubOpt.get();
        boolean already = tournament.getClubs().stream().anyMatch(entry -> entry.getClubId() == club.getId());
        String state = tournament.getState() == null ? "upcoming" : tournament.getState().toLowerCase(Locale.ROOT);
        boolean upcoming = "upcoming".equals(state);
        boolean live = "live".equals(state);
        if (upcoming) {
            if (!already) {
                if (manager.enrollClub(club.getId())) {
                    send(player, "register.success", "&aYour club has been registered for the tournament.");
                } else {
                    send(player, "register.failed", "&cRegistration failed. Please try again later.");
                    return;
                }
            }
            if (!service.isLobbyWindowOpenNow()) {
                int minutes = Math.max(1, service.getLobbyWindowMinutes());
                send(player, "register.waiting-window",
                        "&eWaiting lobby opens about {minutes} minutes before the tournament starts. Use the same command to enter once invited.",
                        "minutes", minutes);
                return;
            }
            if (service.enqueueClub(club.getTag(), sender)) {
                send(player, "register.move-waiting", "&aMoving to the club's waiting lobby...");
            }
            return;
        }
        if (!live) {
            send(player, "register.closed", "&cRegistration for this tournament is closed.");
            return;
        }
        if (!already) {
            if (!manager.enrollClub(club.getId())) {
                send(player, "register.live-add-failed",
                        "&cYour club could not be added to the tournament. Please try again later.");
                return;
            }
        }
        if (service.enqueueClub(club.getTag(), sender)) {
            send(player, "register.move-live", "&aYour club is being moved to the tournament lobby...");
        }
    }

    private void handleClubEnter(CommandSender sender, EsportsManager manager, EsportsGameService service) {
        if (!(sender instanceof Player)) {
            send(sender, "common.only-player", "&cThis command can only be used in-game.");
            return;
        }
        Player player = (Player) sender;
        ClubManager clubManager = plugin.getClubManager();
        if (clubManager == null) {
            send(player, "common.club-system-unavailable", "&cClub system is unavailable.");
            return;
        }
        Optional<ClubData> clubOpt = clubManager.getClubByOwner(player);
        if (!clubOpt.isPresent()) {
            send(player, "enter.owner-only", "&cOnly club leaders can move their team into the arena.");
            return;
        }
        EsportsTournamentData tournament = manager.getActiveTournament();
        if (tournament == null) {
            send(player, "common.no-active-tournament", "&cNo active tournament found.");
            return;
        }
        if (!"live".equalsIgnoreCase(tournament.getState())) {
            send(player, "enter.not-started", "&cTournament has not started yet.");
            return;
        }
        ClubData club = clubOpt.get();
        boolean registered = tournament.getClubs().stream().anyMatch(entry -> entry.getClubId() == club.getId());
        if (!registered) {
            send(player, "enter.not-registered",
                    "&cYour club is not registered for this tournament. Use /esports register first.");
            return;
        }
        boolean accepted = service.requestQueueEntry(club.getTag(), sender);
        if (accepted) {
            send(player, "enter.moving", "&aMoving your club to the tournament arena...");
        }
    }

    private void handleTournamentStartCommand(CommandSender sender, EsportsManager manager) {
        if (manager == null) {
            send(sender, "start.system-not-ready", "&cTournament system is not ready.");
            return;
        }
        EsportsTournamentData tournament = manager.getActiveTournament();
        if (tournament == null) {
            send(sender, "common.no-active-tournament", "&cNo active tournament found.");
            return;
        }
        if (tournament.getClubs().size() < 2) {
            send(sender, "start.need-two-clubs", "&cAt least 2 clubs must be registered to start the tournament.");
            return;
        }
        if (manager.startTournamentNow(true)) {
            send(sender, "start.updated-live", "&aTournament state updated to 'live'.");
        } else {
            send(sender, "start.failed", "&cTournament is already active or could not be started.");
        }
    }

    private void handleSpectatorCommand(CommandSender sender, EsportsGameService service) {
        if (!(sender instanceof Player)) {
            send(sender, "common.only-player", "&cThis command can only be used in-game.");
            return;
        }
        Player player = (Player) sender;
        if (!service.teleportToSpectator(player, true)) {
            send(sender, "spectator.failed", "&cCould not move you to spectator lobby. Try again later.");
        }
    }

    private void handleTournamentDeleteCommand(CommandSender sender, EsportsManager manager, String[] args) {
        if (args.length < 2) {
            send(sender, "delete.usage", "&cUsage: /esports delete <tournamentId>");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            send(sender, "delete.id-numeric", "&cTournament ID must be numeric.");
            return;
        }
        if (id <= 0) {
            send(sender, "delete.id-positive", "&cTournament ID must be positive.");
            return;
        }
        if (manager.deleteTournament(id)) {
            send(sender, "delete.success", "&aTournament #{id} deleted.", "id", id);
        } else {
            send(sender, "delete.failed", "&cTournament #{id} could not be deleted or was not found.", "id", id);
        }
    }

    private void handleBracketView(CommandSender sender, EsportsManager manager) {
        EsportsTournamentData tournament = manager != null ? manager.getActiveTournament() : null;
        if (tournament == null) {
            send(sender, "bracket.no-active", "&cThere is no active tournament.");
            return;
        }
        List<EsportsMatchData> matches = tournament.getMatches();
        if (matches == null || matches.isEmpty()) {
            manager.ensureBracketGeneratedIfMissing();
            manager.reloadNow();
            tournament = manager.getActiveTournament();
            matches = tournament != null ? tournament.getMatches() : null;
            if (matches == null || matches.isEmpty()) {
                send(sender, "bracket.not-ready", "&eBracket is not ready yet. Waiting for enough club participation.");
                return;
            }
        }
        Map<Integer, String> clubNames = new HashMap<>();
        Map<Integer, String> clubTags = new HashMap<>();
        for (EsportsClubEntry entry : tournament.getClubs()) {
            clubNames.put(entry.getClubId(), entry.getName());
            clubTags.put(entry.getClubId(), entry.getTag());
        }
        if (sender instanceof Player) {
            new EsportsBracketMenu(plugin, (Player) sender, manager);
            return;
        }
        matches.sort(Comparator.comparingInt(EsportsMatchData::getRound)
                .thenComparingInt(EsportsMatchData::getBracketSlot));
        sender.sendMessage(msg("bracket.console.header", "&b=== &e{name} &bBracket ===",
                "name", tournament.getName()));
        int currentRound = Integer.MIN_VALUE;
        for (EsportsMatchData match : matches) {
            if (match.getRound() != currentRound) {
                currentRound = match.getRound();
                sender.sendMessage(msg("bracket.console.round", "&6Round #{round}", "round", currentRound));
            }
            String clubALabel = formatClubLabel(match.getClubAId(), clubNames, clubTags);
            String clubBLabel = formatClubLabel(match.getClubBId(), clubNames, clubTags);
            ChatColor stateColor = formatStateColor(match.getState());
            String winner = match.getWinnerClubId() == null ? ""
                    : msg("bracket.console.winner-suffix", " &a-> {winner}",
                    "winner", formatClubLabel(match.getWinnerClubId(), clubNames, clubTags));
            sender.sendMessage(msg("bracket.console.match-line",
                    "&8[{slot}] &e{clubA}&7 vs &e{clubB}&8 | {stateColor}{state}{winner}",
                    "slot", match.getBracketSlot(),
                    "clubA", clubALabel,
                    "clubB", clubBLabel,
                    "stateColor", stateColor.toString(),
                    "state", match.getState().toUpperCase(Locale.ROOT),
                    "winner", winner));
        }
    }

    private String formatClubLabel(Integer clubId,
                                   Map<Integer, String> clubNames,
                                   Map<Integer, String> clubTags) {
        if (clubId == null) {
            return ChatColor.DARK_GRAY + msg("bracket.console.club-empty", "EMPTY");
        }
        String unknown = msg("bracket.console.club-unknown", "Unknown");
        String name = clubNames.getOrDefault(clubId, unknown);
        String tag = clubTags.getOrDefault(clubId, "?");
        return name + ChatColor.DARK_GRAY + " [" + tag + "]";
    }

    private ChatColor formatStateColor(String state) {
        if (state == null) {
            return ChatColor.GRAY;
        }
        switch (state.toLowerCase(Locale.ROOT)) {
            case "running":
            case "active":
                return ChatColor.GOLD;
            case "completed":
            case "done":
                return ChatColor.GREEN;
            default:
                return ChatColor.YELLOW;
        }
    }

    private void handleKitCommand(CommandSender sender, EsportsGameService service, String[] args) {
        if (args.length == 2) {
            if (!(sender instanceof Player)) {
                send(sender, "kit.editor-only-player", "&cKit editor can only be used in-game.");
                return;
            }
            Player player = (Player) sender;
            service.openKitEditor(args[1], player);
            return;
        }

        if (args.length < 3) {
            send(sender, "kit.usage", "&cUsage: /esports kit <club> or /esports kit save <club>");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "save":
                if (!(sender instanceof Player)) {
                    send(sender, "kit.save-only-player", "&cYou must be in-game to save a kit.");
                    return;
                }

                Player kitPlayer = (Player) sender;
                if (service.saveClubKit(args[2], kitPlayer)) {
                    send(sender, "kit.saved", "&aClub kit saved.");
                } else {
                    send(sender, "kit.save-failed", "&cKit could not be saved. Is the club tag correct?");
                }
                break;

            case "edit":
            case "gui":
            case "menu":
                if (!(sender instanceof Player)) {
                    send(sender, "kit.editor-only-player", "&cKit editor can only be used in-game.");
                    return;
                }
                Player editorPlayer = (Player) sender;
                service.openKitEditor(args[2], editorPlayer);
                break;

            default:
                send(sender, "kit.supported-subcommands", "&cSupported subcommands: save, edit.");
        }
    }

    private void handleResultCommand(CommandSender sender, EsportsManager manager, EsportsGameService service, String[] args) {
        send(sender, "result.disabled", "&cThis command is disabled. Replay is available in the menu.");
    }

    private void printUsage(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            boolean staff = player.hasPermission("esports.manage");
            List<String> lines = new ArrayList<>();
            lines.add(msg("help.header", "&6&m----------------------------"));
            lines.add(msg("help.blank", "&2"));
            lines.add(msg("help.title", "&e&lE-Sports"));
            lines.add(msg("help.blank", "&2"));
            lines.add(msg("help.player.register", "&e/esports register &2- &fQueue your club for the tournament."));
            lines.add(msg("help.player.tournaments", "&e/esports tournaments &2- &fView live/upcoming tournaments."));
            lines.add(msg("help.player.bracket", "&e/esports bracket &2- &fOpen the tournament bracket."));
            lines.add(msg("help.player.watch", "&e/esports watch &2- &fGo to the spectator lobby."));
            lines.add(msg("help.player.club", "&e/club &2- &fOpen club menu."));
            if (staff) {
                lines.add(msg("help.blank", "&2"));
                lines.add(msg("help.staff.start", "&e/esports start <club> &2- &fForce-move a club to arena."));
                lines.add(msg("help.staff.setspawn", "&e/esports setspawn <club> &2- &fSave current location as club spawn."));
                lines.add(msg("help.staff.kit", "&e/esports kit save <club> &2- &fSave current inventory as club kit."));
                lines.add(msg("help.staff.match", "&e/esports match <id|clear> &2- &fSet active match id for tracking."));
                lines.add(msg("help.staff.clear", "&e/esports clear &2- &fReset active match state."));
                lines.add(msg("help.staff.delete", "&e/esports delete <id> &2- &fPermanently delete a tournament."));
                lines.add(msg("help.staff.admin", "&e/esports admin create|cancel &2- &fControl the tournament wizard."));
            }
            lines.add(msg("help.blank", "&2"));
            lines.add(msg("help.header", "&6&m----------------------------"));
            sendCentered(player, lines);
            return;
        }

        sender.sendMessage(msg("help.console.title", "&eEsports commands:"));
        sender.sendMessage(msg("help.console.register", "&6/esports register &7- Queue your club."));
        sender.sendMessage(msg("help.console.bracket", "&6/esports bracket &7- View tournament bracket."));
        sender.sendMessage(msg("help.console.watch", "&6/esports watch &7- Go to spectator lobby."));
        sender.sendMessage(msg("help.console.tournaments", "&6/esports tournaments &7- List tournaments."));
        sender.sendMessage(msg("help.console.club", "&6/club &7- Open club menu."));
        if (sender.hasPermission("esports.manage")) {
            sender.sendMessage(msg("help.console.management", "&eManagement commands:"));
            sender.sendMessage(msg("help.console.start", "&6/esports start <club> &7- Force club into arena."));
            sender.sendMessage(msg("help.console.setspawn", "&6/esports setspawn <club> &7- Save club spawn."));
            sender.sendMessage(msg("help.console.match", "&6/esports match <id|clear> &7- Set active match id."));
            sender.sendMessage(msg("help.console.result", "&6/esports result <matchId> <winner|none> [replayUrl] [downloadUrl] &7- Record match result."));
            sender.sendMessage(msg("help.console.clear", "&6/esports clear &7- Reset active match."));
            sender.sendMessage(msg("help.console.delete", "&6/esports delete <id> &7- Delete tournament."));
            sender.sendMessage(msg("help.console.admin", "&6/esports admin create|cancel &7- Manage wizard."));
        }
    }

    private void handleTournamentMenu(CommandSender sender, EsportsManager manager, String[] args) {
        if (!(sender instanceof Player)) {
            send(sender, "common.only-player", "&cThis command can only be used in-game.");
            return;
        }
        Player player = (Player) sender;
        String state = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "live";
        boolean admin = sender.hasPermission("esports.manage");
        new EsportsTournamentListMenu(plugin, player, manager, state, admin);
    }

    private void sendCentered(Player player, List<String> lines) {
        if (lines == null || player == null) {
            return;
        }
        for (String raw : lines) {
            if (raw == null || raw.isEmpty()) {
                player.sendMessage("");
            } else {
                player.sendMessage(CenteredMessage.generate(raw));
            }
        }
    }
}
