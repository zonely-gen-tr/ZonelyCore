package dev.zonely.whiteeffect.esports;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.clubs.ClubManager;
import dev.zonely.whiteeffect.clubs.model.ClubData;
import dev.zonely.whiteeffect.esports.db.EsportsRepository;
import dev.zonely.whiteeffect.esports.game.EsportsGameService;
import dev.zonely.whiteeffect.esports.model.EsportsClubEntry;
import dev.zonely.whiteeffect.esports.model.EsportsMatchData;
import dev.zonely.whiteeffect.esports.model.EsportsMatchDraft;
import dev.zonely.whiteeffect.esports.model.EsportsPlaceholderSnapshot;
import dev.zonely.whiteeffect.esports.model.EsportsSpawnPoint;
import dev.zonely.whiteeffect.esports.model.EsportsTournamentData;
import dev.zonely.whiteeffect.esports.model.EsportsTournamentDraft;
import dev.zonely.whiteeffect.esports.model.EsportsTournamentSummary;
import dev.zonely.whiteeffect.esports.placeholder.EsportsPlaceholderExpansion;
import dev.zonely.whiteeffect.utils.CenteredMessage;
import dev.zonely.whiteeffect.utils.JsonUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.text.Normalizer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class EsportsManager {

    private static final int LOBBY_WINDOW_MINUTES = 5;
    private final Core plugin;
    private final EsportsConfig config;
    private final EsportsRepository repository;
    private final AtomicReference<EsportsTournamentData> activeTournament = new AtomicReference<>();
    private final AtomicReference<EsportsPlaceholderSnapshot> placeholderSnapshot = new AtomicReference<>();

    private EsportsPlaceholderExpansion expansion;
    private EsportsGameService gameService;
    private BukkitTask refreshTask;
    private long lastLog;
    private final Set<Integer> lobbyReminderSent = ConcurrentHashMap.newKeySet();
    private final Set<Integer> lobbyWindowSent = ConcurrentHashMap.newKeySet();
    private final Set<Integer> startBroadcastSent = ConcurrentHashMap.newKeySet();

    public EsportsManager(Core plugin, EsportsConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.repository = new EsportsRepository(plugin.getLogger());
    }

    public void start() {
        if (!config.isEnabled()) {
            plugin.getLogger().info("[Esports] Module disabled.");
            return;
        }

        this.expansion = new EsportsPlaceholderExpansion(plugin, this);
        if (!this.expansion.register()) {
            plugin.getLogger().warning("[Esports] Placeholder expansion could not register; continuing without PAPI.");
            this.expansion = null;
        } else if (gameService != null) {
            this.expansion.setGameService(gameService);
        }

        runRefresh(true);
        long interval = config.getRefreshTicks();
        this.refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> runRefresh(false), interval, interval);
    }

    public void attachGameService(EsportsGameService service) {
        this.gameService = service;
        if (this.expansion != null) {
            this.expansion.setGameService(service);
        }
    }

    public EsportsGameService getGameService() {
        return gameService;
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (expansion != null) {
            try {
                Method unregister = expansion.getClass().getMethod("unregister");
                unregister.invoke(expansion);
            } catch (Exception ignored) {
            }
            expansion = null;
        }
        activeTournament.set(null);
        placeholderSnapshot.set(null);
    }

    public long getTournamentCountdownSeconds() {
        EsportsTournamentData data = activeTournament.get();
        if (data == null || data.getStartsAt() == null || data.getStartsAt().trim().isEmpty()) {
            return 0L;
        }
        LocalDateTime start = parseDateTime(data.getStartsAt());
        if (start == null) {
            return 0L;
        }
        long diff = Duration.between(LocalDateTime.now(), start).getSeconds();
        return Math.max(0L, diff);
    }

    public EsportsTournamentData getActiveTournament() {
        return activeTournament.get();
    }

    public EsportsPlaceholderSnapshot getCurrentSnapshot() {
        return placeholderSnapshot.get();
    }

    public EsportsConfig getSettings() {
        return config;
    }

    public EsportsRepository getRepository() {
        return repository;
    }

    public boolean updateMatchResult(int matchId, Integer winnerClubId, String state, JsonObject metadata) {
        return repository.updateMatchResult(matchId, winnerClubId, state, metadata);
    }

    private void runRefresh(boolean initial) {
        reloadFromDatabase(initial);
    }

    public boolean reloadNow() {
        return reloadFromDatabase(false);
    }

    private boolean reloadFromDatabase(boolean initial) {
        try {
            EsportsTournamentData data = loadTournamentFromDatabase();
            if (data == null) {
                if (initial) {
                    plugin.getLogger().warning("[Esports] No tournament record matched the current configuration.");
                }
                activeTournament.set(null);
                placeholderSnapshot.set(null);
                return false;
            }

            activeTournament.set(data);
            placeholderSnapshot.set(buildPlaceholderSnapshot(data));
            maybeSendLobbyReminder(data);
            maybeAutoStart(data);
            if (initial) {
                plugin.getLogger().info("[Esports] Loaded tournament #" + data.getId() + " (" + data.getName() + ")");
            }
            return true;
        } catch (Exception ex) {
            throttleLog("[Esports] Failed to refresh tournament snapshot.", ex);
            return false;
        }
    }

    private EsportsTournamentData loadTournamentFromDatabase() {
        if (config.getTournamentId() != null) {
            Optional<EsportsTournamentData> data = repository.findById(config.getTournamentId());
            if (data.isPresent()) {
                return data.get();
            }
        }

        if (config.getTournamentSlug() != null) {
            Optional<EsportsTournamentData> data = repository.findBySlug(config.getTournamentSlug());
            if (data.isPresent()) {
                return data.get();
            }
        }

        String preferredState = config.getTournamentState();
        if (preferredState != null && !preferredState.trim().isEmpty()) {
            Optional<EsportsTournamentData> preferred = repository.findByState(preferredState);
            if (preferred.isPresent()) {
                return preferred.get();
            }
        }

        List<String> fallbacks = Arrays.asList("live", "upcoming", "scheduled");
        for (String candidate : fallbacks) {
            if (preferredState != null && preferredState.equalsIgnoreCase(candidate)) {
                continue;
            }
            Optional<EsportsTournamentData> fallback = repository.findByState(candidate);
            if (fallback.isPresent()) {
                return fallback.get();
            }
        }

        return null;
    }

    private EsportsPlaceholderSnapshot buildPlaceholderSnapshot(EsportsTournamentData data) {
        Map<String, String> values = new HashMap<>();
        values.put("zonely_tournament_name", nullSafe(data.getName()));
        values.put("zonely_tournament_state", nullSafe(data.getState()));
        values.put("zonely_tournament_event", nullSafe(data.getEventType()));
        values.put("zonely_tournament_start", nullSafe(data.getStartsAt()));
        values.put("zonely_tournament_end", nullSafe(data.getEndsAt()));
        values.put("zonely_tournament_club_count", String.valueOf(data.getClubs().size()));
        values.put("zonely_tournament_countdown_seconds", String.valueOf(getTournamentCountdownSeconds()));
        values.put("zonely_tournament_register_open", String.valueOf(isRegistrationOpen(data)));
        int repeatDays = resolveRepeatDays(data.getSettings());
        values.put("zonely_tournament_repeatable", String.valueOf(repeatDays > 0));
        values.put("zonely_tournament_repeat_days", String.valueOf(Math.max(0, repeatDays)));
        values.put("zonely_tournament_repeat_mode", repeatModeLabel(data.getSettings(), repeatDays));

        EsportsClubEntry club = resolvePreferredClub(data);
        if (club != null) {
            values.put("zonely_club_name", nullSafe(club.getName()));
            values.put("zonely_club_tag", nullSafe(club.getTag()));
            values.put("zonely_club_seed", club.getSeed() == null ? "" : club.getSeed().toString());
            values.put("zonely_club_status", nullSafe(club.getStatus()));
        }

        findNextReadyMatch(data).ifPresent(match -> {
            values.put("zonely_next_match_round", String.valueOf(match.getRound()));
            values.put("zonely_next_match_slot", String.valueOf(match.getBracketSlot()));
            values.put("zonely_next_match_a", resolveClubName(match.getClubAId()));
            values.put("zonely_next_match_b", resolveClubName(match.getClubBId()));
        });
        values.put("zonely_active_match_id", data.getMatches().stream()
                .filter(m -> "running".equalsIgnoreCase(m.getState()))
                .map(m -> String.valueOf(m.getId()))
                .findFirst().orElse(""));
        return new EsportsPlaceholderSnapshot(values, null, null, System.currentTimeMillis());
    }

    private boolean isRegistrationOpen(EsportsTournamentData data) {
        if (data == null) {
            return false;
        }
        if ("live".equalsIgnoreCase(data.getState())) {
            return true;
        }
        if (!"upcoming".equalsIgnoreCase(data.getState())) {
            return false;
        }
        LocalDateTime startAt = parseDateTime(data.getStartsAt());
        if (startAt == null) {
            return false;
        }
        long seconds = Duration.between(LocalDateTime.now(), startAt).getSeconds();
        return seconds <= LOBBY_WINDOW_MINUTES * 60L;
    }

    private String repeatModeLabel(JsonObject settings, int repeatDays) {
        if (settings != null && settings.has("repeatMode")) {
            try {
                String mode = settings.get("repeatMode").getAsString();
                if (mode != null && !mode.trim().isEmpty()) {
                    return mode.toLowerCase(Locale.ROOT);
                }
            } catch (Exception ignored) {
            }
        }
        if (repeatDays <= 0) {
            return "off";
        }
        if (repeatDays >= 14) {
            return "biweekly";
        }
        if (repeatDays >= 7) {
            return "weekly";
        }
        return "daily";
    }

    private String resolveClubName(Integer clubId) {
        if (clubId == null) {
            return "";
        }
        return getClubById(clubId).map(EsportsClubEntry::getName).orElse("");
    }

    private EsportsClubEntry resolvePreferredClub(EsportsTournamentData data) {
        if (config.getClubId() != null) {
            for (EsportsClubEntry entry : data.getClubs()) {
                if (entry.getClubId() == config.getClubId()) {
                    return entry;
                }
            }
        }

        if (config.getClubTag() != null) {
            for (EsportsClubEntry entry : data.getClubs()) {
                if (entry.getTag() != null
                        && entry.getTag().equalsIgnoreCase(config.getClubTag())) {
                    return entry;
                }
            }
        }

        return data.getClubs().isEmpty() ? null : data.getClubs().get(0);
    }

    public Optional<EsportsClubEntry> findClubByPlayer(Player player) {
        EsportsTournamentData data = activeTournament.get();
        if (data == null) {
            return Optional.empty();
        }

        String displayName = player.getScoreboard().getEntryTeam(player.getName()) != null
                ? player.getScoreboard().getEntryTeam(player.getName()).getName()
                : player.getDisplayName();

        for (EsportsClubEntry entry : data.getClubs()) {
            if (entry.getTag() != null && displayName != null
                    && displayName.toLowerCase(Locale.ROOT).contains(entry.getTag().toLowerCase(Locale.ROOT))) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public Optional<EsportsClubEntry> getClubByTag(String tag) {
        EsportsTournamentData data = activeTournament.get();
        if (data == null || tag == null) {
            return Optional.empty();
        }
        for (EsportsClubEntry entry : data.getClubs()) {
            if (entry.getTag() != null && entry.getTag().equalsIgnoreCase(tag)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public Optional<EsportsMatchData> findMatchForClub(String tag) {
        EsportsTournamentData data = activeTournament.get();
        if (data == null || tag == null) {
            return Optional.empty();
        }
        String normalized = tag.trim().toLowerCase(Locale.ROOT);
        for (EsportsMatchData match : data.getMatches()) {
            if (match.getClubAId() != null) {
                Optional<EsportsClubEntry> clubA = getClubById(match.getClubAId());
                if (clubA.isPresent() && clubA.get().getTag() != null
                        && clubA.get().getTag().equalsIgnoreCase(normalized)) {
                    return Optional.of(match);
                }
            }
            if (match.getClubBId() != null) {
                Optional<EsportsClubEntry> clubB = getClubById(match.getClubBId());
                if (clubB.isPresent() && clubB.get().getTag() != null
                        && clubB.get().getTag().equalsIgnoreCase(normalized)) {
                    return Optional.of(match);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<EsportsMatchData> findMatchById(int matchId) {
        EsportsTournamentData data = activeTournament.get();
        if (data == null || data.getMatches().isEmpty()) {
            return Optional.empty();
        }
        return data.getMatches().stream()
                .filter(match -> match.getId() == matchId)
                .findFirst();
    }

    public boolean isNextMatchClub(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return false;
        }
        Optional<EsportsMatchData> next = findNextReadyMatch();
        if (!next.isPresent()) {
            return false;
        }
        EsportsMatchData match = next.get();
        return matchHasTag(match, tag);
    }

    private boolean matchHasTag(EsportsMatchData match, String tag) {
        if (match == null || tag == null) {
            return false;
        }
        String normalized = tag.trim().toLowerCase(Locale.ROOT);
        if (match.getClubAId() != null) {
            Optional<EsportsClubEntry> clubA = getClubById(match.getClubAId());
            if (clubA.isPresent() && clubA.get().getTag() != null
                    && clubA.get().getTag().equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        if (match.getClubBId() != null) {
            Optional<EsportsClubEntry> clubB = getClubById(match.getClubBId());
            if (clubB.isPresent() && clubB.get().getTag() != null
                    && clubB.get().getTag().equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    public Optional<EsportsClubEntry> getClubById(int clubId) {
        EsportsTournamentData data = activeTournament.get();
        if (data == null) {
            return Optional.empty();
        }
        for (EsportsClubEntry entry : data.getClubs()) {
            if (entry.getClubId() == clubId) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public boolean updateClubSpawn(String tag, Location location) {
        EsportsTournamentData data = activeTournament.get();
        if (data == null || location == null) {
            return false;
        }
        Optional<EsportsClubEntry> club = getClubByTag(tag);
        if (!club.isPresent()) {
            return false;
        }
        boolean updated = repository.updateClubSpawn(data.getId(), club.get().getClubId(), EsportsSpawnPoint.toJson(location));
        if (updated) {
            reloadNow();
        }
        return updated;
    }

    public boolean updateClubKit(String tag, JsonObject kitJson) {
        EsportsTournamentData data = activeTournament.get();
        if (data == null) {
            return false;
        }
        Optional<EsportsClubEntry> clubOpt = getClubByTag(tag);
        if (!clubOpt.isPresent()) {
            return false;
        }
        boolean updated = repository.updateClubKit(data.getId(), clubOpt.get().getClubId(), kitJson);
        if (updated) {
            reloadNow();
        }
        repository.saveClubLoadout(data.getId(), clubOpt.get().getClubId(), kitJson);
        return updated;
    }

    public boolean recordMatchResult(int matchId, String winnerTag, String replayUrl, String downloadUrl) {
        EsportsTournamentData data = activeTournament.get();
        if (data == null) {
            return false;
        }
        Integer winnerId = null;
        if (winnerTag != null && !winnerTag.equalsIgnoreCase("none")) {
            Optional<EsportsClubEntry> clubOpt = getClubByTag(winnerTag);
            if (!clubOpt.isPresent()) {
                return false;
            }
            winnerId = clubOpt.get().getClubId();
        }
        JsonObject meta = new JsonObject();
        if (replayUrl != null && !replayUrl.isEmpty()) {
            meta.addProperty("replayUrl", replayUrl);
        }
        boolean updated = repository.updateMatchResult(matchId, winnerId, "completed", meta);
        if (updated && replayUrl != null && !replayUrl.isEmpty()) {
            repository.insertReplay(matchId, replayUrl, downloadUrl, 0L, meta);
        }
        if (updated) {
            reloadNow();
            EsportsTournamentData refreshed = activeTournament.get();
            if (winnerId != null && refreshed != null) {
                advanceBracketAfterMatch(refreshed, matchId, winnerId);
                reloadNow();
                resolveBracketAutoAdvances(activeTournament.get());
            }
            reloadNow();
        }
        return updated;
    }

    public boolean updateMatchReplayPath(int matchId, String replayUrl) {
        return repository.updateMatchReplayUrl(matchId, replayUrl);
    }

    public boolean recordReplaySnapshot(int matchId, String storageUrl, String downloadUrl, long durationMs, JsonObject metadata) {
        return repository.insertReplay(matchId, storageUrl, downloadUrl, durationMs, metadata);
    }

    public boolean recordMatchEvent(int matchId, String eventType, JsonObject payload) {
        if (matchId <= 0) {
            return false;
        }
        return repository.insertMatchEvent(matchId, eventType, payload);
    }

    public List<EsportsTournamentSummary> listTournaments(String state, int limit) {
        return repository.listTournaments(state == null || state.equalsIgnoreCase("all") ? null : state, limit);
    }

    public Optional<EsportsTournamentData> findTournamentById(int id) {
        return repository.findById(id);
    }

    public int createTournament(EsportsTournamentDraft draft) {
        return repository.insertTournament(draft);
    }

    public boolean createRecurringTournamentIfNeeded(EsportsTournamentData source) {
        if (source == null || source.getSettings() == null) {
            return false;
        }
        int repeatDays = resolveRepeatDays(source.getSettings());
        if (repeatDays <= 0) {
            return false;
        }
        LocalDateTime currentStart = parseDateTime(source.getStartsAt());
        if (currentStart == null) {
            currentStart = LocalDateTime.now();
        }
        LocalDateTime nextStart = currentStart.plusDays(repeatDays);
        LocalDateTime minAllowed = LocalDateTime.now().plusMinutes(5);
        while (nextStart.isBefore(minAllowed)) {
            nextStart = nextStart.plusDays(repeatDays);
        }
        String baseName = source.getName() == null || source.getName().trim().isEmpty()
                ? "E-Sports Tournament"
                : source.getName();
        String slug = generateSlug(ChatColor.stripColor(baseName) + "-" + nextStart.toLocalDate());
        JsonObject settingsCopy = JsonUtils.deepCopy(source.getSettings());
        if (settingsCopy == null) {
            settingsCopy = new JsonObject();
        }
        JsonObject rewardsCopy = JsonUtils.deepCopy(source.getRewards());
        if (rewardsCopy == null) {
            rewardsCopy = new JsonObject();
        }
        settingsCopy.addProperty("repeatable", true);
        settingsCopy.addProperty("repeatDays", repeatDays);
        if (!settingsCopy.has("repeatMode")) {
            settingsCopy.addProperty("repeatMode", repeatDays >= 14 ? "biweekly" : (repeatDays >= 7 ? "weekly" : "daily"));
        }
        EsportsTournamentDraft draft = new EsportsTournamentDraft(
                baseName,
                slug,
                source.getEventType(),
                "upcoming",
                "Auto-created recurring tournament from #" + source.getId(),
                nextStart,
                null,
                settingsCopy,
                rewardsCopy,
                new JsonObject()
        );
        int createdId = createTournament(draft);
        if (createdId <= 0) {
            return false;
        }
        reloadNow();
        int finalRepeatDays = repeatDays;
        LocalDateTime finalNextStart = nextStart;
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(
                EsportsLang.get("manager.broadcast.recurring-created",
                        "&3[E-Sports] &6{name}&b recurring event created (every {days} day(s), next at {next}).",
                        "name", baseName,
                        "days", finalRepeatDays,
                        "next", finalNextStart.toString().replace('T', ' '))));
        return true;
    }

    private int resolveRepeatDays(JsonObject settings) {
        if (settings == null) {
            return 0;
        }
        Boolean repeatableFlag = firstBoolean(settings, "repeatable", "recurring", "continuous", "isRepeatable", "isRecurring");
        int repeatDays = firstInt(settings, "repeatDays", "repeat_days", "repeatIntervalDays", "intervalDays", "interval_days", "everyDays");
        if (repeatDays <= 0) {
            repeatDays = repeatModeToDays(firstString(settings, "repeatMode", "repeat_mode", "repeat", "interval", "mode"));
        }
        if (settings.has("recurring") && settings.get("recurring").isJsonObject()) {
            JsonObject recurring = settings.getAsJsonObject("recurring");
            if (repeatableFlag == null) {
                repeatableFlag = firstBoolean(recurring, "enabled", "repeatable", "active");
            }
            if (repeatDays <= 0) {
                repeatDays = firstInt(recurring, "repeatDays", "days", "intervalDays", "interval_days", "everyDays");
                if (repeatDays <= 0) {
                    repeatDays = repeatModeToDays(firstString(recurring, "repeatMode", "mode", "interval"));
                }
            }
        }
        if (repeatableFlag != null && !repeatableFlag) {
            return 0;
        }
        if (repeatableFlag == null && repeatDays <= 0) {
            return 0;
        }
        return Math.max(1, repeatDays <= 0 ? 7 : repeatDays);
    }

    private int repeatModeToDays(String mode) {
        if (mode == null) {
            return 0;
        }
        String lower = mode.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty() || "off".equals(lower) || "none".equals(lower) || "disabled".equals(lower)) {
            return 0;
        }
        if ("daily".equals(lower)) {
            return 1;
        }
        if ("weekly".equals(lower)) {
            return 7;
        }
        if ("biweekly".equals(lower)) {
            return 14;
        }
        try {
            int parsed = Integer.parseInt(lower);
            return Math.max(0, parsed);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private Boolean firstBoolean(JsonObject obj, String... keys) {
        if (obj == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || !obj.has(key)) {
                continue;
            }
            try {
                return obj.get(key).getAsBoolean();
            } catch (Exception ignored) {
            }
            try {
                String raw = obj.get(key).getAsString();
                if (raw == null) {
                    continue;
                }
                String lower = raw.trim().toLowerCase(Locale.ROOT);
                if ("1".equals(lower) || "true".equals(lower) || "yes".equals(lower) || "on".equals(lower)) {
                    return Boolean.TRUE;
                }
                if ("0".equals(lower) || "false".equals(lower) || "no".equals(lower) || "off".equals(lower)) {
                    return Boolean.FALSE;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private int firstInt(JsonObject obj, String... keys) {
        if (obj == null || keys == null) {
            return 0;
        }
        for (String key : keys) {
            if (key == null || !obj.has(key)) {
                continue;
            }
            try {
                return obj.get(key).getAsInt();
            } catch (Exception ignored) {
            }
            try {
                String raw = obj.get(key).getAsString();
                if (raw == null) {
                    continue;
                }
                return Integer.parseInt(raw.trim());
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private String firstString(JsonObject obj, String... keys) {
        if (obj == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || !obj.has(key)) {
                continue;
            }
            try {
                String value = obj.get(key).getAsString();
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public boolean deleteTournament(int id) {
        boolean deleted = repository.deleteTournament(id);
        if (deleted) {
            lobbyReminderSent.remove(id);
            lobbyWindowSent.remove(id);
            startBroadcastSent.remove(id);
            reloadNow();
        }
        return deleted;
    }

    public boolean enrollClub(int clubId) {
        EsportsTournamentData data = activeTournament.get();
        if (data == null) {
            return false;
        }
        boolean enrolled = repository.enrollClub(data.getId(), clubId, "registered");
        if (enrolled) {
            reloadNow();
        }
        return enrolled;
    }

    public boolean startTournamentNow(boolean announce) {
        EsportsTournamentData current = activeTournament.get();
        if (current == null) {
            return false;
        }
        if ("live".equalsIgnoreCase(current.getState())) {
            boolean generated = ensureBracketGenerated(current);
            if (generated) {
                reloadNow();
            }
            EsportsTournamentData liveData = activeTournament.get();
            return generated || (liveData != null && liveData.getMatches() != null && !liveData.getMatches().isEmpty());
        }
        boolean updated = repository.updateTournamentState(current.getId(), "live");
        if (!updated) {
            return false;
        }
        reloadNow();
        EsportsTournamentData refreshed = activeTournament.get();
        if (ensureBracketGenerated(refreshed)) {
            reloadNow();
            refreshed = activeTournament.get();
        }
        if (announce && refreshed != null) {
            EsportsTournamentData startedData = refreshed;
            Bukkit.getScheduler().runTask(plugin, () -> announceTournamentStart(startedData));
        }
        return true;
    }

    public boolean ensureBracketGeneratedIfMissing() {
        EsportsTournamentData current = activeTournament.get();
        if (current == null) {
            return false;
        }
        if (current.getMatches() != null && !current.getMatches().isEmpty()) {
            return false;
        }
        return ensureBracketGenerated(current);
    }

    private boolean ensureBracketGenerated(EsportsTournamentData data) {
        if (data == null) {
            return false;
        }
        if (!"live".equalsIgnoreCase(data.getState())) {
            return false;
        }
        if (data.getMatches() != null && !data.getMatches().isEmpty()) {
            return false;
        }
        List<EsportsMatchDraft> drafts = buildBracketDrafts(data);
        if (drafts.isEmpty()) {
            plugin.getLogger().warning("[Esports] Could not generate bracket. At least two clubs are required.");
            return false;
        }
        if (repository.replaceMatches(data.getId(), drafts)) {
            plugin.getLogger().info("[Esports] Created a " + drafts.size() + "-match bracket for tournament #" + data.getId() + ".");
            reloadNow();
            EsportsTournamentData refreshed = activeTournament.get();
            resolveBracketAutoAdvances(refreshed);
            return true;
        }
        return false;
    }

    private List<EsportsMatchDraft> buildBracketDrafts(EsportsTournamentData data) {
        if (data == null) {
            return Collections.emptyList();
        }
        List<EsportsClubEntry> eligible = new ArrayList<>();
        for (EsportsClubEntry entry : data.getClubs()) {
            if (entry == null) {
                continue;
            }
            String status = entry.getStatus();
            if (status != null && "eliminated".equalsIgnoreCase(status)) {
                continue;
            }
            eligible.add(entry);
        }
        if (eligible.size() < 2) {
            return Collections.emptyList();
        }
        eligible.sort(Comparator
                .comparing((EsportsClubEntry entry) -> entry.getSeed() == null ? Integer.MAX_VALUE : entry.getSeed())
                .thenComparing(entry -> entry.getName() == null ? "" : entry.getName(), String.CASE_INSENSITIVE_ORDER));
        int bracketSize = 1;
        while (bracketSize < eligible.size()) {
            bracketSize <<= 1;
        }
        List<Integer> slots = new ArrayList<>();
        for (EsportsClubEntry entry : eligible) {
            slots.add(entry.getClubId());
        }
        while (slots.size() < bracketSize) {
            slots.add(null);
        }
        List<EsportsMatchDraft> drafts = new ArrayList<>();
        List<Integer> current = new ArrayList<>(slots);
        int round = 1;
        while (current.size() > 1) {
            List<Integer> next = new ArrayList<>();
            int slotIndex = 0;
            for (int i = 0; i < current.size(); i += 2) {
                Integer clubA = current.get(i);
                Integer clubB = (i + 1 < current.size()) ? current.get(i + 1) : null;
                JsonObject metadata = new JsonObject();
                metadata.addProperty("round", round);
                metadata.addProperty("slot", slotIndex);
                if (round > 1) {
                    metadata.addProperty("sourceRound", round - 1);
                    JsonArray sources = new JsonArray();
                    sources.add(new JsonPrimitive(slotIndex * 2));
                    sources.add(new JsonPrimitive((slotIndex * 2) + 1));
                    metadata.add("sourceSlots", sources);
                }
                drafts.add(new EsportsMatchDraft(round, slotIndex, clubA, clubB, "pending", metadata));
                next.add(null);
                slotIndex++;
            }
            current = next;
            round++;
        }
        return drafts;
    }

    private void resolveBracketAutoAdvances(EsportsTournamentData data) {
        EsportsTournamentData working = data;
        for (int guard = 0; guard < 24; guard++) {
            if (working == null || working.getMatches().isEmpty()) {
                return;
            }

            List<Integer> autoMatchIds = new ArrayList<>();
            for (EsportsMatchData match : working.getMatches()) {
                if (!"pending".equalsIgnoreCase(match.getState())) {
                    continue;
                }
                boolean hasA = match.getClubAId() != null;
                boolean hasB = match.getClubBId() != null;
                if (hasA ^ hasB) {
                    Integer winner = hasA ? match.getClubAId() : match.getClubBId();
                    if (winner != null && repository.updateMatchResult(match.getId(), winner, "completed", new JsonObject())) {
                        autoMatchIds.add(match.getId());
                    }
                }
            }
            if (autoMatchIds.isEmpty()) {
                return;
            }

            reloadNow();
            EsportsTournamentData refreshed = activeTournament.get();
            if (refreshed == null) {
                return;
            }
            for (Integer matchId : autoMatchIds) {
                EsportsMatchData completed = refreshed.getMatches().stream()
                        .filter(m -> m.getId() == matchId)
                        .findFirst()
                        .orElse(null);
                if (completed == null || completed.getWinnerClubId() == null) {
                    continue;
                }
                advanceBracketAfterMatch(refreshed, matchId, completed.getWinnerClubId());
            }
            reloadNow();
            working = activeTournament.get();
        }
    }

    private void advanceBracketAfterMatch(EsportsTournamentData data, int matchId, int winnerClubId) {
        if (data == null || data.getMatches().isEmpty()) {
            return;
        }
        EsportsMatchData match = data.getMatches().stream()
                .filter(m -> m.getId() == matchId)
                .findFirst()
                .orElse(null);
        if (match == null) {
            return;
        }
        Integer loserId = null;
        if (match.getClubAId() != null && match.getClubAId() != winnerClubId) {
            loserId = match.getClubAId();
        } else if (match.getClubBId() != null && match.getClubBId() != winnerClubId) {
            loserId = match.getClubBId();
        }
        int maxRound = data.getMatches().stream()
                .mapToInt(EsportsMatchData::getRound)
                .max()
                .orElse(match.getRound());
        boolean isFinal = match.getRound() == maxRound;
        if (isFinal) {
            repository.updateClubPlacement(data.getId(), winnerClubId, 1);
            if (loserId != null) {
                repository.updateClubPlacement(data.getId(), loserId, 2);
            }
            broadcastTournamentTopClubs(data);
            return;
        }
        EsportsMatchData nextMatch = data.getMatches().stream()
                .filter(m -> m.getSourceRound() == match.getRound()
                        && m.getSourceSlots().contains(match.getBracketSlot()))
                .findFirst()
                .orElse(null);
        if (nextMatch == null) {
            return;
        }
        int index = nextMatch.getSourceSlots().indexOf(match.getBracketSlot());
        boolean firstSlot = index <= 0;
        repository.updateMatchClubSlot(nextMatch.getId(), firstSlot, winnerClubId);
        boolean nextIsFinal = nextMatch.getRound() == maxRound;
        if (nextIsFinal && loserId != null) {
            repository.updateClubPlacement(data.getId(), loserId, 3);
        }
    }

    public Optional<EsportsMatchData> findNextReadyMatch() {
        return findNextReadyMatch(activeTournament.get());
    }

    private void broadcastTournamentTopClubs(EsportsTournamentData data) {
        if (data == null || data.getClubs() == null || data.getClubs().isEmpty()) {
            return;
        }
        List<ClubRank> ranks = new ArrayList<>();
        Map<Integer, Integer> winCounts = new HashMap<>();
        if (data.getMatches() != null) {
            for (EsportsMatchData match : data.getMatches()) {
                if (match != null && match.getWinnerClubId() != null) {
                    winCounts.merge(match.getWinnerClubId(), 1, Integer::sum);
                }
            }
        }
        for (EsportsClubEntry club : data.getClubs()) {
            if (club == null) continue;
            int wins = winCounts.getOrDefault(club.getClubId(), 0);
            ranks.add(new ClubRank(
                    club.getName(),
                    club.getTag(),
                    club.getPlacement(),
                    wins
            ));
        }
        ranks.sort(Comparator
                .comparing((ClubRank r) -> r.placement == null ? Integer.MAX_VALUE : r.placement)
                .thenComparing((ClubRank r) -> -r.wins)
                .thenComparing(r -> r.name == null ? "" : r.name));
        List<String> lines = new ArrayList<>();
        lines.add(EsportsLang.get("manager.top-clubs.header-line", "&6&m----------------------------"));
        lines.add(EsportsLang.get("manager.top-clubs.blank", "&2"));
        lines.add(EsportsLang.get("manager.top-clubs.title", "&e&lTournament Finished"));
        lines.add(EsportsLang.get("manager.top-clubs.tournament-name", "&b{name}",
                "name", data.getName() == null
                        ? EsportsLang.get("manager.top-clubs.tournament-fallback", "Tournament")
                        : data.getName()));
        lines.add(EsportsLang.get("manager.top-clubs.blank", "&2"));
        lines.add(EsportsLang.get("manager.top-clubs.subtitle", "&6Top Clubs"));
        int max = Math.min(5, ranks.size());
        for (int i = 0; i < max; i++) {
            ClubRank rank = ranks.get(i);
            int place = rank.placement != null ? rank.placement : (i + 1);
            String tagText = rank.tag == null || rank.tag.isEmpty() ? "" : "&7 [" + rank.tag + "]";
            String line = EsportsLang.get("manager.top-clubs.rank-line",
                    "&e#{place} &6{name}{tag} &7- &bWins: {wins}",
                    "place", place,
                    "rank", place,
                    "position", place,
                    "index", place,
                    "name", rank.name,
                    "club", rank.name,
                    "tag", tagText,
                    "wins", rank.wins,
                    "score", rank.wins,
                    "points", rank.wins);
            lines.add(line);
        }
        lines.add(EsportsLang.get("manager.top-clubs.blank", "&2"));
        lines.add(EsportsLang.get("manager.top-clubs.footer-line", "&6&m----------------------------"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;
            for (String raw : lines) {
                player.sendMessage(CenteredMessage.generate(raw));
            }
        }
    }

    private static final class ClubRank {
        private final String name;
        private final String tag;
        private final Integer placement;
        private final int wins;

        private ClubRank(String name, String tag, Integer placement, int wins) {
            this.name = name;
            this.tag = tag;
            this.placement = placement;
            this.wins = wins;
        }
    }

    private Optional<EsportsMatchData> findNextReadyMatch(EsportsTournamentData data) {
        if (data == null || data.getMatches().isEmpty()) {
            return Optional.empty();
        }
        return data.getMatches().stream()
                .filter(match -> "pending".equalsIgnoreCase(match.getState()))
                .filter(match -> match.getClubAId() != null && match.getClubBId() != null)
                .sorted(Comparator.comparingInt(EsportsMatchData::getRound)
                        .thenComparingInt(EsportsMatchData::getBracketSlot))
                .findFirst();
    }

    public String generateSlug(String name) {
        String base;
        if (name == null || name.trim().isEmpty()) {
            base = "es-" + System.currentTimeMillis();
        } else {
            base = Normalizer.normalize(name, Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                    .replaceAll("[^a-zA-Z0-9]+", "-")
                    .replaceAll("-{2,}", "-")
                    .replaceAll("^-|-$", "")
                    .toLowerCase(Locale.ROOT);
            if (base.isEmpty()) {
                base = "es-" + Long.toHexString(System.currentTimeMillis());
            }
        }
        String slug = base;
        int attempt = 1;
        while (repository.findBySlug(slug).isPresent()) {
            slug = base + "-" + attempt++;
        }
        return slug;
    }

    private void throttleLog(String message, Exception ex) {
        long now = System.currentTimeMillis();
        if (now - lastLog > 15000L) {
            plugin.getLogger().log(Level.WARNING, message, ex);
            lastLog = now;
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            String normalized = raw.contains("T") ? raw : raw.replace(" ", "T");
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private void announceTournamentStart(EsportsTournamentData data) {
        if (data == null) {
            return;
        }
        Bukkit.broadcastMessage(EsportsLang.get("manager.broadcast.started",
                "&3[E-Sports] &6{name}&b tournament has started!",
                "name", data.getName()));
        notifyClubLeaders(data);
        if (gameService != null) {
            gameService.announceNextClub();
        }
    }

    private void notifyClubLeaders(EsportsTournamentData data) {
        if (data == null) {
            return;
        }
        ClubManager clubManager = plugin.getClubManager();
        if (clubManager == null) {
            return;
        }
        for (ClubData club : clubManager.listAllClubs()) {
            Player owner = Bukkit.getPlayer(club.getOwnerUuid());
            if (owner != null && owner.isOnline()) {
                sendLobbyInvite(owner, data.getName(), InviteTone.LIVE);
            }
        }
    }

    private void sendLobbyInvite(Player owner, String tournamentName, InviteTone tone) {
        if (owner == null) {
            return;
        }
        TextComponent prefix = new TextComponent(EsportsLang.get("manager.invite.prefix", "&3[E-Sports] "));
        String detail;
        switch (tone) {
            case REMINDER:
                detail = EsportsLang.get("manager.invite.detail.reminder", "&b tournament will start soon! ");
                break;
            case LOBBY_OPEN:
                detail = EsportsLang.get("manager.invite.detail.lobby-open", "&b tournament waiting lobby is now open! ");
                break;
            default:
                detail = EsportsLang.get("manager.invite.detail.live", "&b tournament has started! ");
                break;
        }
        TextComponent info = new TextComponent(EsportsLang.get("manager.invite.info", "&6{name}{detail}",
                "name", tournamentName,
                "detail", detail));
        TextComponent button = new TextComponent(EsportsLang.get("manager.invite.button", "&a&l[Go to Waiting Lobby]"));
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/esports register"));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                TextComponent.fromLegacyText(tone == InviteTone.REMINDER
                        ? EsportsLang.get("manager.invite.hover.reminder", "&eClick to move to your club's waiting lobby.")
                        : EsportsLang.get("manager.invite.hover.default", "&eClick to move your club to the waiting lobby."))));
        prefix.addExtra(info);
        prefix.addExtra(button);
        owner.spigot().sendMessage(prefix);
    }

    private void maybeSendLobbyReminder(EsportsTournamentData data) {
        if (data == null) {
            return;
        }
        if (!"upcoming".equalsIgnoreCase(data.getState())) {
            lobbyReminderSent.remove(data.getId());
            lobbyWindowSent.remove(data.getId());
            startBroadcastSent.remove(data.getId());
            return;
        }
        LocalDateTime startAt = parseDateTime(data.getStartsAt());
        if (startAt == null) {
            return;
        }
        long minutes = Duration.between(LocalDateTime.now(), startAt).toMinutes();
        if (minutes <= LOBBY_WINDOW_MINUTES && minutes > 0
                && lobbyReminderSent.add(data.getId())) {
            Bukkit.getScheduler().runTask(plugin, () -> notifyLobbyWindow(data, InviteTone.REMINDER));
        }
        if (minutes <= 0 && lobbyWindowSent.add(data.getId())) {
            Bukkit.getScheduler().runTask(plugin, () -> notifyLobbyWindow(data, InviteTone.LOBBY_OPEN));
        }
        if (minutes <= 0 && startBroadcastSent.add(data.getId())) {
            Bukkit.getScheduler().runTask(plugin, () -> broadcastStartInvite(data));
        }
    }

    private void maybeAutoStart(EsportsTournamentData data) {
        if (data == null || config == null) {
            return;
        }
        if (!config.isAutoStartEnabled()) {
            return;
        }
        if (!"upcoming".equalsIgnoreCase(data.getState())) {
            return;
        }
        LocalDateTime startAt = parseDateTime(data.getStartsAt());
        if (startAt == null) {
            return;
        }
        long secondsPast = Duration.between(startAt, LocalDateTime.now()).getSeconds();
        if (secondsPast < config.getAutoStartDelayMinutes() * 60L) {
            return;
        }
        boolean staffOnline = Bukkit.getOnlinePlayers().stream()
                .anyMatch(p -> p != null && p.isOnline() && p.hasPermission("esports.manage"));
        if (staffOnline) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (startTournamentNow(true)) {
                plugin.getLogger().info("[Esports] Auto-started tournament #" + data.getId() + " after delay.");
            }
        });
    }

    private void notifyLobbyWindow(EsportsTournamentData data, InviteTone tone) {
        ClubManager clubManager = plugin.getClubManager();
        if (clubManager == null) {
            return;
        }
        for (ClubData club : clubManager.listAllClubs()) {
            Player owner = Bukkit.getPlayer(club.getOwnerUuid());
            if (owner != null && owner.isOnline()) {
                sendLobbyInvite(owner, data.getName(), tone);
            }
        }
    }

    private enum InviteTone {
        REMINDER,
        LOBBY_OPEN,
        LIVE
    }

    private void broadcastStartInvite(EsportsTournamentData data) {
        if (data == null) {
            return;
        }
        Bukkit.broadcastMessage(EsportsLang.get("manager.broadcast.started",
                "&3[E-Sports] &6{name}&b tournament has started!",
                "name", data.getName()));
        Bukkit.broadcastMessage(EsportsLang.get("manager.broadcast.leader-required",
                "&7You must be a club leader to join."));
        TextComponent join = new TextComponent(EsportsLang.get("manager.broadcast.join-button", "&a&l[Join with my Club]"));
        join.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/esports register"));
        join.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                TextComponent.fromLegacyText(EsportsLang.get("manager.broadcast.join-hover",
                        "&eMoves your club to the waiting lobby and gathers all online members."))));
        TextComponent spacer = new TextComponent(" ");
        TextComponent watch = new TextComponent(EsportsLang.get("manager.broadcast.watch-button", "&b&l[Watch]"));
        watch.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/esports watch"));
        watch.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                TextComponent.fromLegacyText(EsportsLang.get("manager.broadcast.watch-hover",
                        "&eGo to the spectator lobby."))));
        join.addExtra(spacer);
        join.addExtra(watch);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(join);
        }
    }
}
