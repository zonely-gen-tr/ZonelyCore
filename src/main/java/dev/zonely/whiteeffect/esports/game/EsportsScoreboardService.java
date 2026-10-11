package dev.zonely.whiteeffect.esports.game;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.esports.EsportsLang;
import dev.zonely.whiteeffect.esports.EsportsManager;
import dev.zonely.whiteeffect.esports.game.EsportsGameService;
import dev.zonely.whiteeffect.esports.model.EsportsClubEntry;
import dev.zonely.whiteeffect.esports.model.EsportsMatchData;
import dev.zonely.whiteeffect.esports.model.EsportsTournamentData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EsportsScoreboardService implements Listener {

    private static final String OBJECTIVE_ID = "zesb";
    private static final String TITLE = ChatColor.GOLD + "" + ChatColor.BOLD + "E-Sports";
    private static final int MAX_LINES = 15;

    private final Core plugin;
    private final EsportsManager manager;
    private final EsportsGameService gameService;
    private final Map<UUID, BoardState> boards = new ConcurrentHashMap<>();
    private final Map<UUID, String> originalTabNames = new ConcurrentHashMap<>();
    private BukkitTask updateTask;
    private final List<String> titleFrames = new ArrayList<>();
    private int titleFrameIndex = 0;

    public EsportsScoreboardService(Core plugin, EsportsManager manager, EsportsGameService gameService) {
        this.plugin = plugin;
        this.manager = manager;
        this.gameService = gameService;
        loadTitleFrames();
        startTask();
    }

    public boolean isEnabled(Player player) {
        return player != null && boards.containsKey(player.getUniqueId());
    }

    public void enable(Player player, boolean notify) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (boards.containsKey(uuid)) {
            if (notify) {
                player.sendMessage(msg("messages.already-enabled", "&eEsports scoreboard is already enabled."));
            }
            return;
        }
        if (!initializeBoard(player)) {
            if (notify) {
                player.sendMessage(msg("messages.manager-unavailable",
                        "&cScoreboard manager is not ready (ScoreboardManager is null)."));
            }
            return;
        }
        originalTabNames.putIfAbsent(uuid, player.getPlayerListName());
        updateTabStyle(player);
        if (notify) {
            player.sendMessage(msg("messages.enabled", "&aEsports scoreboard enabled."));
        }
    }

    public void disable(Player player, boolean notify) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        BoardState state = boards.remove(uuid);
        if (state == null) {
            if (notify) {
                player.sendMessage(msg("messages.already-disabled", "&eEsports scoreboard is already disabled."));
            }
            return;
        }
        restoreBoard(player, state);
        restoreTabName(player);
        if (notify) {
            player.sendMessage(msg("messages.disabled", "&cEsports scoreboard disabled."));
        }
    }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        for (Map.Entry<UUID, BoardState> entry : boards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                restoreBoard(player, entry.getValue());
                restoreTabName(player);
            }
        }
        boards.clear();
        for (UUID uuid : originalTabNames.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                restoreTabName(player);
            }
        }
        originalTabNames.clear();
    }

    private void startTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 1L, 1L);
    }

    private void refreshAll() {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, BoardState> entry : boards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                toRemove.add(entry.getKey());
                originalTabNames.remove(entry.getKey());
                continue;
            }
            updateBoard(player, entry.getValue());
        }
        toRemove.forEach(boards::remove);
    }

    private boolean initializeBoard(Player player) {
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        BoardState state = new BoardState();
        Scoreboard board = scoreboardManager.getNewScoreboard();
        Objective objective = registerObjective(board, uuid);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        try {
            state.previous = player.getScoreboard();
        } catch (Throwable ignored) {
        }
        state.board = board;
        state.objective = objective;
        boards.put(uuid, state);
        try {
            player.setScoreboard(board);
        } catch (Throwable ignored) {
        }
        updateBoard(player, state);
        return true;
    }

    private void restoreBoard(Player player, BoardState state) {
        if (state == null) {
            return;
        }
        Scoreboard previous = state.previous;
        ScoreboardManager managerObj = Bukkit.getScoreboardManager();
        if (previous != null) {
            try {
                player.setScoreboard(previous);
            } catch (Throwable ignored) {
            }
        } else if (managerObj != null) {
            try {
                player.setScoreboard(managerObj.getMainScoreboard());
            } catch (Throwable ignored) {
            }
        }
    }

    private void restoreTabName(Player player) {
        if (player == null) {
            return;
        }
        String original = originalTabNames.remove(player.getUniqueId());
        if (original != null) {
            try {
                player.setPlayerListName(original);
            } catch (Throwable ignored) {
            }
        }
    }

    private void updateBoard(Player player, BoardState state) {
        if (state.board == null || state.objective == null) {
            initializeBoard(player);
            state = boards.get(player.getUniqueId());
            if (state == null || state.board == null || state.objective == null) {
                return;
            }
        }
        Scoreboard board = state.board;
        Objective objective = state.objective;
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        List<String> lines = ensureUnique(buildLines(player));
        int score = lines.size();
        for (String line : lines) {
            objective.getScore(line).setScore(score--);
        }
        try {
            objective.setDisplayName(nextTitle());
        } catch (Throwable ignored) {
        }
        updateTabStyle(player);
    }

    private List<String> ensureUnique(List<String> lines) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int salt = 0;
        for (String line : lines) {
            String candidate = line;
            while (seen.contains(candidate)) {
                candidate = line + ChatColor.values()[salt % ChatColor.values().length];
                salt++;
            }
            seen.add(candidate);
            out.add(candidate);
        }
        return out;
    }

    private List<String> buildLines(Player player) {
        UUID playerId = player.getUniqueId();
        if (gameService.isActiveCompetitor(playerId)) {
            if (gameService.isCountdownRunning()) {
                return buildCountdownLines(player);
            }
            return buildParticipantLines(player);
        }
        if (gameService.isCountdownRunning()) {
            return buildCountdownLines(player);
        }
        if (gameService.isSpectator(playerId)
                || gameService.isMatchRunning()
                || gameService.getQueuedClubCount() > 0) {
            return buildSpectatorLines(player);
        }
        if (gameService.hasActiveMatchContext()) {
            return buildSimpleMatchLines(player);
        }
        return buildSimpleLobbyLines();
    }

    private void loadTitleFrames() {
        try {
            List<String> frames = plugin.getConfig().getStringList("esports.scoreboard.title-frames");
            String baseTitle = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("esports.scoreboard.title-base",
                            msg("title.base", "&e&lE-Sports")));
            if (frames != null) {
                for (String frame : frames) {
                    if (frame != null && !frame.trim().isEmpty()) {
                        titleFrames.add(ChatColor.translateAlternateColorCodes('&', frame));
                    }
                }
            }
            if (titleFrames.isEmpty()) {
                titleFrames.addAll(buildWaveFrames(baseTitle));
            }
        } catch (Exception ignored) {
        }
        if (titleFrames.isEmpty()) {
            String fallback = msg("title.fallback", "Server");
            titleFrames.add(ChatColor.YELLOW + "" + ChatColor.BOLD + fallback);
            titleFrames.add(ChatColor.GOLD + "" + ChatColor.BOLD + fallback);
            titleFrames.add(ChatColor.WHITE + "" + ChatColor.BOLD + fallback);
        }
    }

    private String nextTitle() {
        if (titleFrames.isEmpty()) {
            return TITLE;
        }
        String frame = titleFrames.get(titleFrameIndex % titleFrames.size());
        titleFrameIndex++;
        return frame;
    }

    private List<String> buildWaveFrames(String baseTitle) {
        List<String> frames = new ArrayList<>();
        if (baseTitle == null || baseTitle.isEmpty()) {
            return frames;
        }
        String plain = ChatColor.stripColor(baseTitle);
        boolean bold = baseTitle.contains(ChatColor.BOLD.toString());
        ChatColor primary = ChatColor.YELLOW;   // &e
        ChatColor accent = ChatColor.GOLD;     // &6
        ChatColor secondary = ChatColor.WHITE; // &f

        // 1-5: fully primary
        for (int i = 0; i < 5; i++) {
            frames.add(colorizeFull(plain, primary, bold));
        }

        // moving highlight from left to right
        for (int i = 0; i < plain.length(); i++) {
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < plain.length(); idx++) {
                ChatColor color;
                if (idx < i) {
                    color = secondary;
                } else if (idx == i) {
                    color = accent;
                } else {
                    color = primary;
                }
                sb.append(color);
                if (bold) {
                    sb.append(ChatColor.BOLD);
                }
                sb.append(plain.charAt(idx));
            }
            frames.add(sb.toString());
        }

        // ending pulses
        frames.add(colorizeFull(plain, secondary, bold));
        frames.add(colorizeFull(plain, primary, bold));
        frames.add(colorizeFull(plain, secondary, bold));
        frames.add(colorizeFull(plain, primary, bold));
        return frames;
    }

    private String colorizeFull(String text, ChatColor color, boolean bold) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(color);
            if (bold) {
                sb.append(ChatColor.BOLD);
            }
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    private List<String> buildSimpleMatchLines(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GRAY + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));  
        lines.add("");
        EsportsTournamentData t = manager.getActiveTournament();
        lines.add(ChatColor.WHITE + msg("labels.tournament", "Tournament: ")
                + ChatColor.AQUA + (t != null ? t.getName() : "-"));
        String vsLine = resolveMatchVsLine();
        if (!vsLine.isEmpty()) {
            lines.add(ChatColor.WHITE + msg("labels.match", "Match: ") + vsLine);
        }
        lines.add(ChatColor.WHITE + msg("labels.drop", "Drop: ") + ChatColor.AQUA + gameService.getFormattedNextDrop()
                + ChatColor.GRAY + " (" + gameService.getDropDirectionLabel(player) + ")");
        lines.add("");
        lines.add(msg("labels.clubs", "Clubs:"));
        for (EsportsGameService.ClubMatchStatus status : gameService.getActiveClubStatuses()) {
            lines.add(renderClubStatus(status));
        }
        lines.add("");
        boolean showKills = isPvpEvent();
        boolean showBlocks = isBlockEvent();
        if (showKills) {
            lines.add(ChatColor.WHITE + msg("labels.kills", "Kills: ")
                    + ChatColor.GOLD + gameService.getPlayerKills(player.getUniqueId()));
        }
        if (showBlocks) {
            lines.add(ChatColor.WHITE + msg("labels.blocks", "Blocks: ")
                    + ChatColor.GREEN + gameService.getPlayerBlockScore(player.getUniqueId()));
        }
        lines.add(ChatColor.WHITE + msg("labels.time", "Time: ") + ChatColor.YELLOW + formatMatchClock());
        lines.add(ChatColor.WHITE + msg("labels.players", "Players: ")
                + ChatColor.YELLOW + gameService.getActiveFighterCount());
        lines.add(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "");
        lines.add(ChatColor.YELLOW + msg("server", "server.net"));
        return lines;
    }

    private String resolveMatchVsLine() {
        List<EsportsGameService.ClubMatchStatus> statuses = gameService.getActiveClubStatuses();
        if (statuses.isEmpty()) {
            return "";
        }
        EsportsGameService.ClubMatchStatus first = statuses.get(0);
        EsportsGameService.ClubMatchStatus second = statuses.size() > 1 ? statuses.get(1) : null;
        String firstName = first.getColor() + first.getTag();
        String secondName = second != null ? second.getColor() + second.getTag() : ChatColor.GRAY + "-";
        return firstName + ChatColor.GRAY + " vs " + secondName;
    }

    private List<String> buildSimpleLobbyLines() {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "");
        EsportsTournamentData t = manager.getActiveTournament();
        lines.add(ChatColor.WHITE + msg("labels.tournament", "Tournament: ")
                + ChatColor.AQUA + (t != null ? t.getName() : "-"));
        lines.add(ChatColor.WHITE + msg("labels.players", "Players: ")
                + ChatColor.AQUA + Bukkit.getOnlinePlayers().size());
        int clubCount = t != null && t.getClubs() != null ? t.getClubs().size() : 0;
        lines.add(ChatColor.WHITE + msg("labels.clubs", "Clubs: ") + ChatColor.AQUA + clubCount);
        lines.add("");
        lines.add(ChatColor.WHITE + msg("labels.status", "Status: ")
                + ChatColor.GREEN + msg("status.lobby", "Lobby"));
        lines.add("");
        lines.add(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "");
        lines.add(ChatColor.YELLOW + msg("server", "server.net"));
        return lines;
    }

    private List<String> buildCountdownLines(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GRAY + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        lines.add(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "");
        lines.add(msg("countdown.starting", "Match Starting: ")
                + ChatColor.GREEN + gameService.getCountdownRemainingSeconds() + "s");
        lines.add("");
        EsportsTournamentData t = manager.getActiveTournament();
        lines.add(ChatColor.WHITE + msg("labels.tournament", "Tournament: ")
                + ChatColor.AQUA + (t != null ? t.getName() : "-"));
        lines.add(ChatColor.WHITE + msg("labels.match", "Match: ") + ChatColor.AQUA + gameService.getCurrentMatchId());
        lines.add(ChatColor.WHITE + msg("labels.players", "Players: ")
                + ChatColor.AQUA + gameService.getActiveFighterCount());
        int limit = Math.min(3, gameService.getActiveClubStatuses().size());
        for (int i = 0; i < limit; i++) {
            lines.add(renderClubStatus(gameService.getActiveClubStatuses().get(i)));
        }
        lines.add(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "");
        lines.add(ChatColor.YELLOW + msg("server", "server.net"));
        return lines;
    }

    private List<String> buildParticipantLines(Player player) {
        LineBuffer buffer = new LineBuffer();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        String divider = ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "";

        buffer.text(ChatColor.GRAY + today, ChatColor.GRAY);
        buffer.text(divider, ChatColor.DARK_GRAY);

        EsportsTournamentData t = manager.getActiveTournament();
        buffer.text(msg("labels.tournament", "Tournament: ") + ChatColor.AQUA + (t != null ? t.getName() : "-"), ChatColor.WHITE);

        Optional<String> clubTag = resolveClubTag(player);
        String tagValue = clubTag.orElse("-");
        int playersLeft = gameService.getActiveFighterCount();

        buffer.text(msg("labels.players", "Players: ") + ChatColor.AQUA + playersLeft, ChatColor.WHITE);
        buffer.text(msg("labels.drop", "Drop: ") + ChatColor.AQUA + gameService.getFormattedNextDrop()
                + ChatColor.GRAY + " (" + gameService.getDropDirectionLabel(player) + ")", ChatColor.WHITE);

        int kills = gameService.getPlayerKills(player.getUniqueId());
        int deaths = gameService.getPlayerDeaths(player.getUniqueId());
        int blocks = gameService.getPlayerBlockScore(player.getUniqueId());

        buffer.blank();
        // Club statuses (tick/cross + hearts per member)
        for (EsportsGameService.ClubMatchStatus status : gameService.getActiveClubStatuses()) {
            buffer.text(renderClubStatus(status), ChatColor.WHITE);
        }
        if (gameService.isCountdownRunning()) {
            buffer.blank();
            buffer.text(ChatColor.YELLOW + "" + ChatColor.BOLD + msg("countdown.header", "MATCH STARTING"), ChatColor.YELLOW);
            buffer.text(msg("countdown.starts-in", "Starts in: ")
                    + ChatColor.RED + gameService.getCountdownRemainingSeconds() + "s", ChatColor.YELLOW);
        }


        buffer.blank();
        boolean showKills = isPvpEvent();
        boolean showBlocks = isBlockEvent();
        if (showKills) {
            int clubKills = gameService.getClubKills("-".equals(tagValue) ? null : tagValue);
            buffer.text(msg("labels.kills", "Kills: ") + ChatColor.GOLD + clubKills, ChatColor.WHITE);
        } else if (showBlocks) {
            buffer.text(msg("labels.blocks", "Blocks: ") + ChatColor.GREEN + blocks, ChatColor.WHITE);
        } else {
            buffer.text(msg("labels.kills", "Kills: ") + ChatColor.GOLD + kills, ChatColor.WHITE);
        }   
        buffer.text(msg("labels.match", "Match: ") + ChatColor.AQUA + gameService.getCurrentMatchId(), ChatColor.WHITE);
        buffer.text(msg("labels.time", "Time: ") + ChatColor.AQUA + formatMatchClock(), ChatColor.WHITE);
        buffer.text(divider, ChatColor.DARK_GRAY);
        buffer.text(ChatColor.YELLOW + msg("server", "server.net"), ChatColor.YELLOW);
        return buffer.build();
    }

    private String formatMatchClock() {
        return formatCompactDuration(gameService.getMatchRemainingSeconds());
    }

    private String formatCompactDuration(long seconds) {
        long safeSeconds = Math.max(0L, seconds);
        long hours = safeSeconds / 3600L;
        long minutes = (safeSeconds % 3600L) / 60L;
        long secs = safeSeconds % 60L;
        if (hours > 0L) {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%02d:%02d", minutes, secs);
    }

    private boolean isPvpEvent() {
        String event = getActiveEventType();
        return event == null || event.equalsIgnoreCase("pvp");
    }

    private boolean isBlockEvent() {
        String event = getActiveEventType();
        return "block-break".equalsIgnoreCase(event) || "block-place".equalsIgnoreCase(event);
    }

    private String getActiveEventType() {
        EsportsTournamentData t = manager.getActiveTournament();
        return t != null ? t.getEventType() : null;
    }

    private String renderClubStatus(EsportsGameService.ClubMatchStatus status) {
        if (status == null) {
            return "";
        }
        boolean alive = status.getAlive() > 0;
        String icon = alive ? ChatColor.GREEN + "\u2714" : ChatColor.RED + "\u2716";
        StringBuilder hearts = new StringBuilder();
        int total = Math.max(1, status.getTotal());
        int aliveCount = Math.max(0, status.getAlive());
        for (int i = 0; i < total; i++) {
            if (i < aliveCount) {
                hearts.append(ChatColor.RED).append('\u2764');
            } else {
                hearts.append(ChatColor.DARK_GRAY).append('\u2764');
            }
        }
        return icon + " " + status.getColor() + status.getDisplayName()
                + ChatColor.GRAY + " (" + aliveCount + "/" + total + ") "
                + hearts;
    }

    private List<String> buildSpectatorLines(Player player) {
        LineBuffer buffer = new LineBuffer();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yy"));
        String divider = ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "";

        buffer.text(ChatColor.GRAY + today, ChatColor.GRAY);
        buffer.text(divider, ChatColor.DARK_GRAY);

        buffer.text(msg("spectator.mode", "Mode: ") + ChatColor.AQUA + msg("spectator.label", "Spectator"), ChatColor.WHITE);
        writeStateBlock(buffer);
        buffer.blank();
        appendRoundProgress(buffer);
        appendSpectatorNextMatchBlock(buffer);
        appendMiniBracketPreview(buffer);
        appendQueueBlock(buffer);
        buffer.text(divider, ChatColor.DARK_GRAY);
        buffer.text(ChatColor.YELLOW + msg("server", "server.net"), ChatColor.YELLOW);
        return buffer.build();
    }

    private void appendRoundProgress(LineBuffer buffer) {
        int totalRounds = getTotalRoundCount();
        if (totalRounds <= 0) {
            buffer.text(msg("spectator.rounds-empty", "Rounds: -"), ChatColor.GRAY);
            return;
        }
        int remaining = getRemainingRoundCount();
        int played = Math.max(0, totalRounds - remaining);
        buffer.text(msg("spectator.rounds", "Rounds: {played}/{total} {bar}",
                "played", played,
                "total", totalRounds,
                "bar", buildRoundProgressBar(played, totalRounds)), ChatColor.GRAY);
    }

    private String buildRoundProgressBar(int played, int total) {
        if (total <= 0) {
            return "";
        }
        int segments = 5;
        int filled = (int) Math.round(((double) played / Math.max(1, total)) * segments);
        filled = Math.max(0, Math.min(segments, filled));
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < segments; i++) {
            builder.append(i < filled ? "#" : "-");
        }
        builder.append("]");
        return builder.toString();
    }

    private void appendSpectatorNextMatchBlock(LineBuffer buffer) {
        Optional<EsportsMatchData> nextMatch = manager.findNextReadyMatch();
        if (nextMatch.isPresent()) {
            EsportsMatchData match = nextMatch.get();
            buffer.styled(ChatColor.GOLD + msg("spectator.next", "Next: ") + formatCompactMatch(match));
            List<EsportsMatchData> upcoming = findUpcomingMatches(match);
            if (!upcoming.isEmpty()) {
                buffer.styled(ChatColor.DARK_GRAY + "> " + ChatColor.GRAY + formatCompactMatch(upcoming.get(0)));
            }
        } else if (gameService.isMatchRunning()) {
            List<EsportsGameService.ClubMatchStatus> statuses = gameService.getActiveClubStatuses();
            if (!statuses.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < Math.min(2, statuses.size()); i++) {
                    if (i > 0) {
                        builder.append(ChatColor.GRAY).append(" vs ");
                    }
                    EsportsGameService.ClubMatchStatus status = statuses.get(i);
                    ChatColor color = status.getColor() != null ? status.getColor() : ChatColor.YELLOW;
                    builder.append(color).append(status.getTag().toUpperCase(Locale.ROOT));
                }
                buffer.styled(ChatColor.GOLD + msg("spectator.active", "Active: ") + builder);
            }
        } else {
            String nextQueued = gameService.getNextQueuedClub() != null ? gameService.getNextQueuedClub() : "-";
            buffer.text(msg("spectator.next-club", "Next Club: {club}", "club", nextQueued), ChatColor.YELLOW);
        }
    }

    private void appendMiniBracketPreview(LineBuffer buffer) {
        List<String> lines = buildMiniBracketPreview();
        if (lines.isEmpty()) {
            return;
        }
        buffer.blank();
        boolean header = true;
        for (String line : lines) {
            if (header) {
                buffer.styled(ChatColor.GOLD + msg("spectator.mini-prefix", "Mini ") + line);
                header = false;
            } else {
                buffer.styled(line);
            }
        }
    }

    private List<String> buildMiniBracketPreview() {
        EsportsTournamentData data = manager.getActiveTournament();
        if (data == null || data.getMatches().isEmpty()) {
            return Collections.emptyList();
        }
        List<EsportsMatchData> pending = new ArrayList<>();
        for (EsportsMatchData match : data.getMatches()) {
            if (!"completed".equalsIgnoreCase(match.getState())
                    && match.getClubAId() != null && match.getClubBId() != null) {
                pending.add(match);
            }
        }
        if (pending.isEmpty()) {
            return Collections.emptyList();
        }
        pending.sort(Comparator.comparingInt(EsportsMatchData::getRound)
                .thenComparingInt(EsportsMatchData::getBracketSlot));
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(2, pending.size()); i++) {
            EsportsMatchData match = pending.get(i);
            String hook = i == 0 ? "+-" : "\\-";
            String line = ChatColor.DARK_GRAY + hook + " "
                    + formatMiniClubTag(match.getClubAId())
                    + ChatColor.GRAY + " vs "
                    + formatMiniClubTag(match.getClubBId())
                    + ChatColor.DARK_GRAY + " R" + match.getRound();
            lines.add(line);
        }
        return lines;
    }

    private String formatCompactMatch(EsportsMatchData match) {
        if (match == null) {
            return ChatColor.DARK_GRAY + "-";
        }
        return formatMiniClubTag(match.getClubAId())
                + ChatColor.GRAY + " vs "
                + formatMiniClubTag(match.getClubBId())
                + ChatColor.DARK_GRAY + " R" + match.getRound();
    }

    private String formatMiniClubTag(Integer clubId) {
        if (clubId == null) {
            return ChatColor.DARK_GRAY + msg("labels.empty", "EMPTY");
        }
        return manager.getClubById(clubId)
                .map(entry -> ChatColor.YELLOW + entry.getTag().toUpperCase(Locale.ROOT))
                .orElse(ChatColor.YELLOW + msg("labels.unknown-short", "??"));
    }

    private void writeStateBlock(LineBuffer buffer) {
        String stateText;
        if (gameService.isMatchRunning()) {
            stateText = ChatColor.GREEN + msg("status.battle", "Battle");
        } else if (gameService.isCountdownRunning()) {
            stateText = ChatColor.YELLOW + msg("status.preparing", "Preparing");
        } else if (gameService.getQueuedClubCount() > 0) {
            stateText = ChatColor.AQUA + msg("status.in-queue", "In Queue");
        } else {
            stateText = ChatColor.GRAY + msg("status.idle", "Idle");
        }
        buffer.text(msg("labels.status", "Status: ") + stateText, ChatColor.AQUA);
        if (gameService.getCurrentMatchId() > 0) {
            buffer.text(msg("spectator.match-id", "Match #{id}", "id", gameService.getCurrentMatchId()), ChatColor.GOLD);
        }
        if (gameService.isCountdownRunning()) {
            buffer.text(msg("spectator.countdown", "Countdown: {seconds}s",
                    "seconds", gameService.getCountdownRemainingSeconds()), ChatColor.RED);
        }
        List<EsportsGameService.ClubMatchStatus> statuses = gameService.getActiveClubStatuses();
        if (!statuses.isEmpty()) {
            buffer.text(msg("spectator.arena-clubs", "Arena Clubs"), ChatColor.GOLD);
            int limit = Math.min(3, statuses.size());
            for (int i = 0; i < limit; i++) {
                EsportsGameService.ClubMatchStatus status = statuses.get(i);
                ChatColor color = status.getColor() != null ? status.getColor() : ChatColor.YELLOW;
                buffer.text(msg("spectator.club-line", " - {name} {alive}/{total}",
                                "name", status.getDisplayName(),
                                "alive", status.getAlive(),
                                "total", status.getTotal()),
                        color != null ? color : ChatColor.YELLOW);
            }
            if (statuses.size() > limit) {
                buffer.text(" ...", ChatColor.DARK_GRAY);
            }
        }
    }

    private void appendNextMatchBlock(LineBuffer buffer, boolean includeQueue) {
        Optional<EsportsMatchData> nextMatch = manager.findNextReadyMatch();
        if (nextMatch.isPresent()) {
            EsportsMatchData match = nextMatch.get();
            buffer.text(ChatColor.GOLD + msg("spectator.next-match-header", "Next Match"), ChatColor.GOLD);
            buffer.text(formatMatchClubs(match), ChatColor.YELLOW);
            buffer.text(msg("spectator.next-match-meta", "Round #{round}  Slot #{slot}",
                    "round", match.getRound(),
                    "slot", match.getBracketSlot()), ChatColor.DARK_GRAY);
            if (includeQueue) {
                List<EsportsMatchData> upcoming = findUpcomingMatches(match);
                for (EsportsMatchData other : upcoming) {
                    buffer.text("-> " + formatMatchClubs(other), ChatColor.GRAY);
                }
            }
        } else if (includeQueue) {
            String nextQueued = gameService.getNextQueuedClub() != null ? gameService.getNextQueuedClub() : "-";
            buffer.text(msg("spectator.next-club", "Next Club: {club}", "club", nextQueued), ChatColor.YELLOW);
        }
    }

    private List<EsportsMatchData> findUpcomingMatches(EsportsMatchData firstMatch) {
        EsportsTournamentData data = manager.getActiveTournament();
        if (data == null) {
            return Collections.emptyList();
        }
        List<EsportsMatchData> pending = new ArrayList<>();
        for (EsportsMatchData match : data.getMatches()) {
            if ("pending".equalsIgnoreCase(match.getState())
                    && match.getClubAId() != null && match.getClubBId() != null
                    && match.getId() != firstMatch.getId()) {
                pending.add(match);
            }
        }
        pending.sort(Comparator.comparingInt(EsportsMatchData::getRound)
                .thenComparingInt(EsportsMatchData::getBracketSlot));
        return pending.subList(0, Math.min(2, pending.size()));
    }

    private void appendQueueBlock(LineBuffer buffer) {
        int count = gameService.getQueuedClubCount();
        List<String> queued = gameService.getQueuedClubsSnapshot();
        String preview = "-";
        if (!queued.isEmpty()) {
            List<String> slice = queued.subList(0, Math.min(2, queued.size()));
            preview = String.join(", ", slice);
        }
        buffer.text(msg("spectator.queue", "Queue: {count} | {preview}",
                "count", count,
                "preview", preview), ChatColor.YELLOW);
    }

    private void appendAtlasHints(LineBuffer buffer) {
        buffer.text(msg("spectator.hint", "Atlas: Right-click | Live Round"), ChatColor.LIGHT_PURPLE);
    }

    private int getTotalRoundCount() {
        EsportsTournamentData data = manager.getActiveTournament();
        if (data == null || data.getMatches().isEmpty()) {
            return 0;
        }
        Set<Integer> rounds = new HashSet<>();
        for (EsportsMatchData match : data.getMatches()) {
            rounds.add(match.getRound());
        }
        return rounds.size();
    }

    private int getRemainingRoundCount() {
        EsportsTournamentData data = manager.getActiveTournament();
        if (data == null || data.getMatches().isEmpty()) {
            return 0;
        }
        Set<Integer> remaining = new HashSet<>();
        for (EsportsMatchData match : data.getMatches()) {
            if (!"completed".equalsIgnoreCase(match.getState())) {
                remaining.add(match.getRound());
            }
        }
        return remaining.size();
    }

    private String formatMatchClubs(EsportsMatchData match) {
        String clubA = formatMatchClub(match.getClubAId());
        String clubB = formatMatchClub(match.getClubBId());
        return clubA + ChatColor.GRAY + " vs " + clubB;
    }

    private String formatMatchClub(Integer clubId) {
        if (clubId == null) {
            return ChatColor.DARK_GRAY + msg("labels.empty", "EMPTY");
        }
        return manager.getClubById(clubId)
                .map(entry -> ChatColor.YELLOW + entry.getName()
                        + ChatColor.DARK_GRAY + " [" + entry.getTag() + "]")
                .orElse(ChatColor.YELLOW + msg("labels.unknown", "Unknown"));
    }

    private String colored(String text, ChatColor color) {
        return color + text + ChatColor.RESET + ChatColor.COLOR_CHAR + uniqueCode();
    }

    private void updateTabStyle(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        originalTabNames.putIfAbsent(uuid, player.getPlayerListName());
        Optional<String> tagOpt = resolveClubTag(player);
        String newName = null;
        if (tagOpt.isPresent()) {
            String tag = tagOpt.get();
            ChatColor color;
            if (manager.isNextMatchClub(tag) && !gameService.isMatchRunning()) {
                color = ChatColor.GOLD;
            } else {
                color = gameService.getClubChatColor(tag);
                if (color == null) {
                    color = ChatColor.YELLOW;
                }
            }
            newName = color + "[" + tag.toUpperCase(Locale.ROOT) + "] " + ChatColor.RESET + player.getName();
        } else if (gameService.isSpectator(uuid) || gameService.isMatchRunning() || gameService.isCountdownRunning()) {
            newName = ChatColor.DARK_GRAY + "[Spectator] " + ChatColor.RESET + player.getName();
        }
        if (newName == null) {
            String original = originalTabNames.get(uuid);
            newName = original != null ? original : player.getName();
        }
        if (!Objects.equals(player.getPlayerListName(), newName)) {
            try {
                player.setPlayerListName(newName);
            } catch (Throwable ignored) {
            }
        }
    }

    private Optional<String> resolveClubTag(Player player) {
        Optional<String> tag = gameService.getParticipantClubTag(player.getUniqueId());
        if (tag.isPresent()) {
            return tag;
        }
        return manager.findClubByPlayer(player).map(EsportsClubEntry::getTag);
    }

    private char uniqueCode() {
        return UNIQUE_CODES[(int) (System.nanoTime() % UNIQUE_CODES.length)];
    }

    private static final char[] UNIQUE_CODES = "0123456789abcdef".toCharArray();

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        disable(event.getPlayer(), false);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin)) {
            shutdown();
        }
    }

    private final class LineBuffer {
        private final List<String> values = new ArrayList<>();

        void text(String text, ChatColor color) {
            if (text == null) {
                return;
            }
            add(colored(text, color));
        }

        void styled(String text) {
            if (text == null) {
                return;
            }
            add(text + ChatColor.RESET + "" + ChatColor.COLOR_CHAR + uniqueCode());
        }

        void blank() {
            add(ChatColor.RESET + "" + ChatColor.COLOR_CHAR + uniqueCode());
        }

        void add(String entry) {
            if (entry == null || values.size() >= MAX_LINES) {
                return;
            }
            values.add(entry);
        }

        List<String> build() {
            return values;
        }
    }

    private static final class BoardState {
        private Scoreboard previous;
        private Scoreboard board;
        private Objective objective;
    }

    private Objective registerObjective(Scoreboard board, UUID uuid) {
        String id = OBJECTIVE_ID + uuid.hashCode();
        try {
            Method method = board.getClass().getMethod("registerNewObjective", String.class, String.class, String.class);
            return (Objective) method.invoke(board, id, "dummy", TITLE);
        } catch (NoSuchMethodException e) {
            Objective objective = board.registerNewObjective(id, "dummy");
            objective.setDisplayName(TITLE);
            return objective;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to register scoreboard objective", e);
        }
    }

    private String msg(String key, String def, Object... placeholders) {
        return EsportsLang.get("scoreboard." + key, def, placeholders);
    }
}
