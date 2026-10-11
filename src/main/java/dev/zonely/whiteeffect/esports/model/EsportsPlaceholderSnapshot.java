package dev.zonely.whiteeffect.esports.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class EsportsPlaceholderSnapshot {

    private final Map<String, String> placeholders;
    private final EsportsTournamentSummary tournament;
    private final EsportsClubSummary club;
    private final long fetchedAt;

    public EsportsPlaceholderSnapshot(Map<String, String> placeholders,
                                      EsportsTournamentSummary tournament,
                                      EsportsClubSummary club,
                                      long fetchedAt) {
        this.placeholders = Collections.unmodifiableMap(new HashMap<>(placeholders));
        this.tournament = tournament;
        this.club = club;
        this.fetchedAt = fetchedAt;
    }

    public String valueFor(String key) {
        if (key == null) {
            return "";
        }
        String value = placeholders.get(key);
        return value == null ? "" : value;
    }

    public Map<String, String> getPlaceholders() {
        return placeholders;
    }

    public EsportsTournamentSummary getTournament() {
        return tournament;
    }

    public EsportsClubSummary getClub() {
        return club;
    }

    public long getFetchedAt() {
        return fetchedAt;
    }
}
