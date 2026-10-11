package dev.zonely.whiteeffect.esports.placeholder;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.esports.EsportsManager;
import dev.zonely.whiteeffect.esports.game.EsportsGameService;
import dev.zonely.whiteeffect.esports.model.EsportsPlaceholderSnapshot;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public final class EsportsPlaceholderExpansion extends PlaceholderExpansion {

    private final Core plugin;
    private final EsportsManager manager;
    private EsportsGameService gameService;

    public EsportsPlaceholderExpansion(Core plugin, EsportsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void setGameService(EsportsGameService gameService) {
        this.gameService = gameService;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "zonely";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty()
                ? plugin.getDescription().getName()
                : plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (params == null || params.trim().isEmpty()) {
            return "";
        }
        String normalized = params.trim();
        String dynamic = resolveDynamicPlaceholder(player, normalized);
        if (dynamic != null) {
            return dynamic;
        }
        EsportsPlaceholderSnapshot snapshot = manager.getCurrentSnapshot();
        if (snapshot == null) {
            return "";
        }
        String key = normalized.startsWith("zonely_") ? normalized : "zonely_" + normalized;
        return snapshot.valueFor(key);
    }

    private String resolveDynamicPlaceholder(Player player, String raw) {
        if (gameService == null) {
            return null;
        }
        String key = raw.toLowerCase();
        if (key.startsWith("zonely_")) {
            key = key.substring("zonely_".length());
        }
        switch (key) {
            case "match_time":
                return formatCompactDuration(gameService.getMatchElapsedSeconds());
            case "match_id":
                return gameService.getCurrentMatchId() > 0 ? String.valueOf(gameService.getCurrentMatchId()) : "";
            case "match_players":
                return String.valueOf(gameService.getParticipantCount());
            case "tournament_countdown":
            case "countdown":
            case "registration_countdown":
                return formatCompactDuration(manager.getTournamentCountdownSeconds());
            case "tournament_countdown_seconds":
            case "countdown_seconds":
                return String.valueOf(manager.getTournamentCountdownSeconds());
            case "match_kills":
                return player == null ? "0" : String.valueOf(gameService.getPlayerKills(player.getUniqueId()));
            case "match_deaths":
                return player == null ? "0" : String.valueOf(gameService.getPlayerDeaths(player.getUniqueId()));
            case "match_blocks":
                return player == null ? "0" : String.valueOf(gameService.getPlayerBlockBreaks(player.getUniqueId()));
            case "match_clubkills":
                return player == null ? "0" : String.valueOf(gameService.getClubKills(gameService.getParticipantClubTag(player.getUniqueId()).orElse(null)));
            default:
                if (key.startsWith("match_clubkills_")) {
                    String tag = key.substring("match_clubkills_".length()).toUpperCase();
                    return String.valueOf(gameService.getClubKills(tag));
                }
                return null;
        }
    }

    private static String formatCompactDuration(long seconds) {
        long safeSeconds = Math.max(0L, seconds);
        long hours = safeSeconds / 3600L;
        long minutes = (safeSeconds % 3600L) / 60L;
        long secs = safeSeconds % 60L;
        if (hours > 0L) {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%02d:%02d", minutes, secs);
    }
}
