package dev.zonely.whiteeffect.esports.db;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.esports.model.EsportsClubEntry;
import dev.zonely.whiteeffect.esports.model.EsportsMatchData;
import dev.zonely.whiteeffect.esports.model.EsportsMatchDraft;
import dev.zonely.whiteeffect.esports.model.EsportsPotionEffectSpec;
import dev.zonely.whiteeffect.esports.model.EsportsSpawnPoint;
import dev.zonely.whiteeffect.esports.model.EsportsSpawnSlot;
import dev.zonely.whiteeffect.esports.model.EsportsTournamentData;
import dev.zonely.whiteeffect.esports.model.EsportsTournamentDraft;
import dev.zonely.whiteeffect.esports.model.EsportsTournamentSummary;
import dev.zonely.whiteeffect.utils.JsonUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EsportsRepository {

    private static final String SELECT_TOURNAMENT_BY_STATE = String.join("\n",
            "SELECT *",
            "FROM es_tournaments",
            "WHERE state = ?",
            "ORDER BY COALESCE(starts_at, created_at) DESC",
            "LIMIT 1"
    );

    private static final String SELECT_TOURNAMENT_BY_ID = String.join("\n",
            "SELECT *",
            "FROM es_tournaments",
            "WHERE id = ?",
            "LIMIT 1"
    );

    private static final String SELECT_TOURNAMENT_BY_SLUG = String.join("\n",
            "SELECT *",
            "FROM es_tournaments",
            "WHERE slug = ?",
            "LIMIT 1"
    );

    private static final String SELECT_TOURNAMENT_CLUBS = String.join("\n",
            "SELECT",
            "    tc.*,",
            "    c.name,",
            "    c.tag,",
            "    c.primary_color,",
            "    c.secondary_color,",
            "    c.accent_color,",
            "    c.icon_url",
            "FROM es_tournament_clubs tc",
            "INNER JOIN es_clubs c ON c.id = tc.club_id",
            "WHERE tc.tournament_id = ?",
            "ORDER BY",
            "    CASE WHEN tc.seed IS NULL THEN 1 ELSE 0 END,",
            "    tc.seed ASC,",
            "    c.name ASC"
    );

    private static final String SELECT_TOURNAMENT_MATCHES = String.join("\n",
            "SELECT",
            "    m.id,",
            "    m.tournament_id,",
            "    m.round,",
            "    m.club_a_id,",
            "    m.club_b_id,",
            "    m.state,",
            "    m.scheduled_at,",
            "    m.started_at,",
            "    m.ended_at,",
            "    m.metadata_json,",
            "    m.replay_url,",
            "    m.winner_club_id",
            "FROM es_matches m",
            "WHERE m.tournament_id = ?",
            "ORDER BY m.round ASC, m.id ASC"
    );

    private static final String UPDATE_CLUB_SPAWN = String.join("\n",
            "UPDATE es_tournament_clubs",
            "SET spawn_json = ?",
            "WHERE tournament_id = ? AND club_id = ?",
            "LIMIT 1"
    );
    private static final String UPDATE_CLUB_KIT = String.join("\n",
            "UPDATE es_tournament_clubs",
            "SET kit_json = ?",
            "WHERE tournament_id = ? AND club_id = ?",
            "LIMIT 1"
    );
    private static final String UPDATE_CLUB_PLACEMENT = String.join("\n",
            "UPDATE es_tournament_clubs",
            "SET placement = ?",
            "WHERE tournament_id = ? AND club_id = ?",
            "LIMIT 1"
    );
    private static final String SELECT_CLUB_ID_BY_TAG = String.join("\n",
            "SELECT tc.club_id",
            "FROM es_tournament_clubs tc",
            "INNER JOIN es_clubs c ON c.id = tc.club_id",
            "WHERE tc.tournament_id = ? AND LOWER(c.tag) = LOWER(?)",
            "LIMIT 1"
    );
    private static final String UPDATE_MATCH_RESULT = String.join("\n",
            "UPDATE es_matches",
            "SET winner_club_id = ?, state = ?, ended_at = NOW(), metadata_json = ?",
            "WHERE id = ?",
            "LIMIT 1"
    );
    private static final String INSERT_REPLAY = String.join("\n",
            "INSERT INTO es_replays (match_id, storage_url, download_url, duration_ms, metadata_json)",
            "VALUES (?, ?, ?, ?, ?)"
    );
    private static final String UPDATE_MATCH_REPLAY_URL = String.join("\n",
            "UPDATE es_matches",
            "SET replay_url = ?",
            "WHERE id = ?",
            "LIMIT 1"
    );
    private static final String UPDATE_MATCH_CLUB_A = String.join("\n",
            "UPDATE es_matches",
            "SET club_a_id = ?",
            "WHERE id = ?",
            "LIMIT 1"
    );
    private static final String UPDATE_MATCH_CLUB_B = String.join("\n",
            "UPDATE es_matches",
            "SET club_b_id = ?",
            "WHERE id = ?",
            "LIMIT 1"
    );
    private static final String INSERT_MATCH_EVENT = String.join("\n",
            "INSERT INTO es_match_events (match_id, event_type, payload_json)",
            "VALUES (?, ?, ?)"
    );

    private static final String DELETE_MATCH_EVENTS = "DELETE FROM es_match_events WHERE match_id IN (SELECT id FROM es_matches WHERE tournament_id = ?)";
    private static final String DELETE_REPLAYS = "DELETE FROM es_replays WHERE match_id IN (SELECT id FROM es_matches WHERE tournament_id = ?)";
    private static final String DELETE_MATCHES = "DELETE FROM es_matches WHERE tournament_id = ?";
    private static final String DELETE_TOURNAMENT_CLUBS = "DELETE FROM es_tournament_clubs WHERE tournament_id = ?";
    private static final String DELETE_TOURNAMENT = "DELETE FROM es_tournaments WHERE id = ? LIMIT 1";
    private static final String LIST_TOURNAMENT_SUMMARIES = String.join("\n",
            "SELECT id, name, slug, state, event_type, starts_at, ends_at",
            "FROM es_tournaments",
            "%s",
            "ORDER BY COALESCE(starts_at, created_at) DESC",
            "LIMIT ?"
    );
    private static final String INSERT_TOURNAMENT = String.join("\n",
            "INSERT INTO es_tournaments",
            "    (name, slug, description, event_type, state, starts_at, ends_at, settings_json, rewards_json, featured_stream_json)",
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    );
    private static final String MARK_TOURNAMENT_FINISHED = String.join("\n",
            "UPDATE es_tournaments",
            "SET state = 'finished', ends_at = COALESCE(ends_at, CURRENT_TIMESTAMP), updated_at = CURRENT_TIMESTAMP",
            "WHERE id = ?",
            "LIMIT 1"
    );
    private static final String INSERT_MATCH = String.join("\n",
            "INSERT INTO es_matches",
            "    (tournament_id, round, club_a_id, club_b_id, state, scheduled_at, started_at, ended_at, metadata_json, replay_url, winner_club_id)",
            "VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, ?, NULL, NULL)"
    );
    private static final String UPDATE_MATCH_START = String.join("\n",
            "UPDATE es_matches",
            "SET state = 'started', started_at = COALESCE(started_at, CURRENT_TIMESTAMP), updated_at = CURRENT_TIMESTAMP",
            "WHERE id = ?",
            "LIMIT 1"
    );
    private static final String UPDATE_MATCH_FINISH = String.join("\n",
            "UPDATE es_matches",
            "SET state = 'finished', ended_at = COALESCE(ended_at, CURRENT_TIMESTAMP), winner_club_id = ?, updated_at = CURRENT_TIMESTAMP",
            "WHERE id = ?",
            "LIMIT 1"
    );
    private static final String UPSERT_TOURNAMENT_CLUB = String.join("\n",
            "INSERT INTO es_tournament_clubs (tournament_id, club_id, status)",
            "VALUES (?, ?, ?)",
            "ON DUPLICATE KEY UPDATE status = VALUES(status)"
    );
    private static final String UPDATE_TOURNAMENT_STATE = String.join("\n",
            "UPDATE es_tournaments",
            "SET state = ?, updated_at = CURRENT_TIMESTAMP",
            "WHERE id = ?",
            "LIMIT 1"
    );
    private static final String SELECT_LOADOUT = String.join("\n",
            "SELECT loadout_json",
            "FROM es_kit_loadouts",
            "WHERE scope = 'club' AND owner_id = ?",
            "  AND (tournament_id = ? OR tournament_id IS NULL)",
            "ORDER BY CASE WHEN tournament_id = ? THEN 0 ELSE 1 END, updated_at DESC",
            "LIMIT 1"
    );
    private static final String UPSERT_LOADOUT = String.join("\n",
            "INSERT INTO es_kit_loadouts (tournament_id, scope, owner_id, loadout_json)",
            "VALUES (?, 'club', ?, ?)",
            "ON DUPLICATE KEY UPDATE loadout_json = VALUES(loadout_json), updated_at = CURRENT_TIMESTAMP"
    );

    private final Logger logger;

    public EsportsRepository(Logger logger) {
        this.logger = logger;
        ensureSchema();
    }

    public Optional<EsportsTournamentData> findById(int id) {
        return queryTournament(SELECT_TOURNAMENT_BY_ID, stmt -> stmt.setInt(1, id));
    }

    public Optional<EsportsTournamentData> findBySlug(String slug) {
        return queryTournament(SELECT_TOURNAMENT_BY_SLUG, stmt -> stmt.setString(1, slug));
    }

    public Optional<EsportsTournamentData> findByState(String state) {
        return queryTournament(SELECT_TOURNAMENT_BY_STATE, stmt -> stmt.setString(1, state));
    }

    public boolean markTournamentFinished(int tournamentId) {
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement ps = connection.prepareStatement(MARK_TOURNAMENT_FINISHED)) {
            ps.setInt(1, tournamentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Unable to mark tournament finished", ex);
            return false;
        }
    }

    private Optional<EsportsTournamentData> queryTournament(String sql, StatementConfigurer configurer) {
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            configurer.configure(statement);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                EsportsTournamentData data = mapTournamentRow(connection, rs);
                return Optional.ofNullable(data);
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Tournament query failed.", ex);
            return Optional.empty();
        }
    }

    private void ensureSchema() {
        try (Connection connection = Database.getInstance().getConnection();
             Statement statement = connection.createStatement()) {
            statement.addBatch("CREATE TABLE IF NOT EXISTS es_tournaments (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(150) NOT NULL," +
                    "slug VARCHAR(150) NOT NULL," +
                    "description TEXT," +
                    "event_type VARCHAR(64)," +
                    "state VARCHAR(32)," +
                    "starts_at DATETIME NULL," +
                    "ends_at DATETIME NULL," +
                    "settings_json LONGTEXT," +
                    "rewards_json LONGTEXT," +
                    "featured_stream_json LONGTEXT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "UNIQUE KEY uk_es_tournaments_slug(slug)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            statement.addBatch("CREATE TABLE IF NOT EXISTS es_tournament_clubs (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "tournament_id INT NOT NULL," +
                    "club_id INT NOT NULL," +
                    "seed INT NULL," +
                    "status VARCHAR(32)," +
                    "placement INT NULL," +
                    "spawn_json LONGTEXT," +
                    "kit_json LONGTEXT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE KEY uk_es_tournament_club (tournament_id, club_id)," +
                    "INDEX idx_es_tournament_clubs_tournament (tournament_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            statement.addBatch("CREATE TABLE IF NOT EXISTS es_matches (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "tournament_id INT NOT NULL," +
                    "round INT NULL," +
                    "club_a_id INT NULL," +
                    "club_b_id INT NULL," +
                    "state VARCHAR(32) NOT NULL DEFAULT 'pending'," +
                    "scheduled_at DATETIME NULL," +
                    "started_at DATETIME NULL," +
                    "ended_at DATETIME NULL," +
                    "metadata_json LONGTEXT," +
                    "replay_url VARCHAR(512)," +
                    "winner_club_id INT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "INDEX idx_es_matches_tournament (tournament_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            statement.addBatch("CREATE TABLE IF NOT EXISTS es_replays (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "match_id INT NOT NULL," +
                    "storage_url VARCHAR(512) NOT NULL," +
                    "download_url VARCHAR(512)," +
                    "duration_ms BIGINT DEFAULT 0," +
                    "metadata_json LONGTEXT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "INDEX idx_es_replays_match (match_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            statement.addBatch("CREATE TABLE IF NOT EXISTS es_match_events (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "match_id INT NOT NULL," +
                    "event_type VARCHAR(64) NOT NULL," +
                    "payload_json LONGTEXT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "INDEX idx_es_match_events_match (match_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            statement.addBatch("CREATE TABLE IF NOT EXISTS es_kit_loadouts (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "tournament_id INT NULL," +
                    "scope VARCHAR(32) NOT NULL," +
                    "owner_id INT NOT NULL," +
                    "loadout_json LONGTEXT," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "UNIQUE KEY uk_es_loadout_scope_owner (scope, owner_id, tournament_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            statement.addBatch("CREATE TABLE IF NOT EXISTS es_clubs (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(120) NOT NULL," +
                    "tag VARCHAR(32) NOT NULL," +
                    "owner_uuid VARCHAR(36) NOT NULL," +
                    "owner_name VARCHAR(16) NOT NULL," +
                    "primary_color VARCHAR(16)," +
                    "secondary_color VARCHAR(16)," +
                    "accent_color VARCHAR(16)," +
                    "icon_url VARCHAR(255)," +
                    "banner_json LONGTEXT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "UNIQUE KEY uk_es_clubs_tag (tag)," +
                    "UNIQUE KEY uk_es_clubs_owner (owner_uuid)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            statement.addBatch("CREATE TABLE IF NOT EXISTS es_club_members (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "club_id INT NOT NULL," +
                    "member_uuid VARCHAR(36) NOT NULL," +
                    "member_name VARCHAR(16) NOT NULL," +
                    "role VARCHAR(16) NOT NULL," +
                    "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE KEY uk_club_member_unique (club_id, member_uuid)," +
                    "INDEX idx_club_members_club (club_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            statement.executeBatch();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[EsportsRepository] Failed to ensure database schema", ex);
        }
    }

    private EsportsTournamentData mapTournamentRow(Connection connection, ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        String slug = rs.getString("slug");
        String state = rs.getString("state");
        String eventType = rs.getString("event_type");
        String startsAt = rs.getString("starts_at");
        String endsAt = rs.getString("ends_at");
        JsonObject settings = parseObject(rs.getString("settings_json"));
        JsonObject rewards = parseObject(rs.getString("rewards_json"));
        List<EsportsPotionEffectSpec> potionEffects = extractPotionEffects(settings);
        List<EsportsSpawnSlot> spawnSlots = extractSpawnSlots(settings);
        JsonObject defaultKit = extractDefaultKit(settings);
        List<EsportsClubEntry> clubs = loadClubs(connection, id);
        List<EsportsMatchData> matches = loadMatches(connection, id);
        return new EsportsTournamentData(id, name, slug, state, eventType, startsAt, endsAt,
                settings, rewards, potionEffects, clubs, matches, spawnSlots, defaultKit);
    }

    private List<EsportsPotionEffectSpec> extractPotionEffects(JsonObject settings) {
        if (settings == null || !settings.has("potionEffects")) {
            return Collections.emptyList();
        }
        JsonElement element = settings.get("potionEffects");
        if (!element.isJsonArray()) {
            return Collections.emptyList();
        }
        List<EsportsPotionEffectSpec> effects = new ArrayList<>();
        JsonArray array = element.getAsJsonArray();
        for (JsonElement entry : array) {
            if (!entry.isJsonObject()) {
                continue;
            }
            EsportsPotionEffectSpec spec = EsportsPotionEffectSpec.fromJson(entry.getAsJsonObject());
            if (spec != null) {
                effects.add(spec);
            }
        }
        return effects;
    }

    private List<EsportsSpawnSlot> extractSpawnSlots(JsonObject settings) {
        if (settings == null || !settings.has("spawnSlots")) {
            return Collections.emptyList();
        }
        JsonElement element = settings.get("spawnSlots");
        if (!element.isJsonArray()) {
            return Collections.emptyList();
        }
        List<EsportsSpawnSlot> slots = new ArrayList<>();
        JsonArray array = element.getAsJsonArray();
        for (JsonElement entry : array) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject obj = entry.getAsJsonObject();
            int index = obj.has("slot") ? obj.get("slot").getAsInt() : slots.size();
            String label = obj.has("label") ? obj.get("label").getAsString() : null;
            JsonObject location = obj.has("location") && obj.get("location").isJsonObject()
                    ? obj.getAsJsonObject("location") : null;
            EsportsSpawnPoint point = EsportsSpawnPoint.fromJson(location);
            slots.add(new EsportsSpawnSlot(index, label, point));
        }
        slots.sort(Comparator.comparingInt(EsportsSpawnSlot::getSlotIndex));
        return Collections.unmodifiableList(slots);
    }

    private JsonObject extractDefaultKit(JsonObject settings) {
        if (settings == null || !settings.has("defaultKit")) {
            return null;
        }
        JsonElement element = settings.get("defaultKit");
        if (!element.isJsonObject()) {
            return null;
        }
        return JsonUtils.deepCopy(element.getAsJsonObject());
    }

    private List<EsportsClubEntry> loadClubs(Connection connection, int tournamentId) {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_TOURNAMENT_CLUBS)) {
            statement.setInt(1, tournamentId);
            try (ResultSet rs = statement.executeQuery()) {
                List<EsportsClubEntry> clubs = new ArrayList<>();
                while (rs.next()) {
                    JsonObject spawnJson = parseObject(rs.getString("spawn_json"));
                    JsonObject kitJson = resolveKitForClub(connection, tournamentId, rs.getInt("club_id"),
                            parseObject(rs.getString("kit_json")));
                    EsportsClubEntry entry = new EsportsClubEntry(
                            rs.getInt("club_id"),
                            rs.getString("name"),
                            rs.getString("tag"),
                            rs.getObject("seed") != null ? rs.getInt("seed") : null,
                            rs.getString("status"),
                            rs.getObject("placement") != null ? rs.getInt("placement") : null,
                            rs.getString("primary_color"),
                            rs.getString("secondary_color"),
                            rs.getString("accent_color"),
                            rs.getString("icon_url"),
                            EsportsSpawnPoint.fromJson(spawnJson),
                            kitJson
                    );
                    clubs.add(entry);
                }
                return clubs;
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to load clubs for tournament " + tournamentId, ex);
            return Collections.emptyList();
        }
    }

    private List<EsportsMatchData> loadMatches(Connection connection, int tournamentId) {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_TOURNAMENT_MATCHES)) {
            statement.setInt(1, tournamentId);
            try (ResultSet rs = statement.executeQuery()) {
                List<EsportsMatchData> matches = new ArrayList<>();
                while (rs.next()) {
                    JsonObject metadata = parseObject(rs.getString("metadata_json"));
                    EsportsMatchData data = new EsportsMatchData(
                            rs.getInt("id"),
                            rs.getInt("tournament_id"),
                            rs.getInt("round"),
                            rs.getObject("club_a_id") != null ? rs.getInt("club_a_id") : null,
                            rs.getObject("club_b_id") != null ? rs.getInt("club_b_id") : null,
                            rs.getString("state"),
                            rs.getString("scheduled_at"),
                            rs.getString("started_at"),
                            rs.getString("ended_at"),
                            metadata,
                            rs.getString("replay_url"),
                            rs.getObject("winner_club_id") != null ? rs.getInt("winner_club_id") : null
                    );
                    matches.add(data);
                }
                return matches;
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to load matches for tournament " + tournamentId, ex);
            return Collections.emptyList();
        }
    }

    private JsonObject parseObject(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(value);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[EsportsRepository] JSON parse error", ex);
            return null;
        }
    }

    public boolean updateClubSpawn(int tournamentId, int clubId, JsonObject spawnJson) {
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_CLUB_SPAWN)) {
            if (spawnJson == null) {
                statement.setNull(1, java.sql.Types.LONGVARCHAR);
            } else {
                statement.setString(1, spawnJson.toString());
            }
            statement.setInt(2, tournamentId);
            statement.setInt(3, clubId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to update spawn for club " + clubId, ex);
            return false;
        }
    }

    public boolean updateClubKit(int tournamentId, int clubId, JsonObject kitJson) {
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_CLUB_KIT)) {
            if (kitJson == null) {
                statement.setNull(1, java.sql.Types.LONGVARCHAR);
            } else {
                statement.setString(1, kitJson.toString());
            }
            statement.setInt(2, tournamentId);
            statement.setInt(3, clubId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to update kit for club " + clubId, ex);
            return false;
        }
    }

    public Integer findClubIdByTag(int tournamentId, String tag) {
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_CLUB_ID_BY_TAG)) {
            statement.setInt(1, tournamentId);
            statement.setString(2, tag);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("club_id");
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to find club for tag " + tag, ex);
        }
        return null;
    }

    public boolean updateMatchResult(int matchId, Integer winnerClubId, String state, JsonObject metadata) {
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_MATCH_RESULT)) {
            if (winnerClubId != null) {
                statement.setInt(1, winnerClubId);
            } else {
                statement.setNull(1, java.sql.Types.INTEGER);
            }
            statement.setString(2, state == null ? "completed" : state);
            if (metadata == null) {
                statement.setNull(3, java.sql.Types.LONGVARCHAR);
            } else {
                statement.setString(3, metadata.toString());
            }
            statement.setInt(4, matchId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to update match #" + matchId, ex);
            return false;
        }
    }

    private JsonObject resolveKitForClub(Connection connection, int tournamentId, int clubId, JsonObject fallback) {
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_LOADOUT)) {
            stmt.setInt(1, clubId);
            stmt.setInt(2, tournamentId);
            stmt.setInt(3, tournamentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return parseObject(rs.getString("loadout_json"));
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to fetch kit loadout for club " + clubId, ex);
        }
        return fallback;
    }

    public boolean saveClubLoadout(int tournamentId, int clubId, JsonObject payload) {
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_LOADOUT)) {
            if (tournamentId > 0) {
                statement.setInt(1, tournamentId);
            } else {
                statement.setNull(1, java.sql.Types.INTEGER);
            }
            statement.setInt(2, clubId);
            statement.setString(3, payload == null ? "{}" : payload.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to save kit loadout for club " + clubId, ex);
            return false;
        }
    }

    public boolean insertReplay(int matchId, String storageUrl, String downloadUrl, long durationMs, JsonObject metadata) {
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_REPLAY)) {
            statement.setInt(1, matchId);
            statement.setString(2, storageUrl);
            statement.setString(3, downloadUrl);
            statement.setLong(4, Math.max(0L, durationMs));
            if (metadata == null) {
                statement.setNull(5, java.sql.Types.LONGVARCHAR);
            } else {
                statement.setString(5, metadata.toString());
            }
            statement.executeUpdate();
            return true;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to insert replay for match #" + matchId, ex);
            return false;
        }
    }

    public boolean updateMatchReplayUrl(int matchId, String replayUrl) {
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_MATCH_REPLAY_URL)) {
            if (replayUrl == null || replayUrl.isEmpty()) {
                statement.setNull(1, java.sql.Types.VARCHAR);
            } else {
                statement.setString(1, replayUrl);
            }
            statement.setInt(2, matchId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to update replay url for match #" + matchId, ex);
            return false;
        }
    }

    public boolean updateMatchClubSlot(int matchId, boolean firstSlot, Integer clubId) {
        String sql = firstSlot ? UPDATE_MATCH_CLUB_A : UPDATE_MATCH_CLUB_B;
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (clubId == null) {
                statement.setNull(1, Types.INTEGER);
            } else {
                statement.setInt(1, clubId);
            }
            statement.setInt(2, matchId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to update match #" + matchId + " slot", ex);
            return false;
        }
    }

    public boolean updateClubPlacement(int tournamentId, int clubId, int placement) {
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_CLUB_PLACEMENT)) {
            statement.setInt(1, placement);
            statement.setInt(2, tournamentId);
            statement.setInt(3, clubId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to update placement for club " + clubId, ex);
            return false;
        }
    }

    public boolean insertMatchEvent(int matchId, String eventType, JsonObject payload) {
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_MATCH_EVENT)) {
            statement.setInt(1, matchId);
            statement.setString(2, eventType);
            if (payload == null) {
                statement.setNull(3, java.sql.Types.LONGVARCHAR);
            } else {
                statement.setString(3, payload.toString());
            }
            statement.executeUpdate();
            return true;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to insert match event for match #" + matchId, ex);
            return false;
        }
    }

    public boolean deleteTournament(int tournamentId) {
        try (Connection connection = Database.getInstance().getConnection()) {
            boolean auto = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement deleteEvents = connection.prepareStatement(DELETE_MATCH_EVENTS);
                 PreparedStatement deleteReplays = connection.prepareStatement(DELETE_REPLAYS);
                 PreparedStatement deleteMatches = connection.prepareStatement(DELETE_MATCHES);
                 PreparedStatement deleteClubs = connection.prepareStatement(DELETE_TOURNAMENT_CLUBS);
                 PreparedStatement deleteTournament = connection.prepareStatement(DELETE_TOURNAMENT)) {
                deleteEvents.setInt(1, tournamentId);
                deleteEvents.executeUpdate();

                deleteReplays.setInt(1, tournamentId);
                deleteReplays.executeUpdate();

                deleteMatches.setInt(1, tournamentId);
                deleteMatches.executeUpdate();

                deleteClubs.setInt(1, tournamentId);
                deleteClubs.executeUpdate();

                deleteTournament.setInt(1, tournamentId);
                int affected = deleteTournament.executeUpdate();
                connection.commit();
                connection.setAutoCommit(auto);
                return affected > 0;
            } catch (SQLException ex) {
                connection.rollback();
                connection.setAutoCommit(auto);
                logger.log(Level.SEVERE, "[EsportsRepository] Failed to delete tournament #" + tournamentId, ex);
                return false;
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[EsportsRepository] Failed to delete tournament #" + tournamentId, ex);
            return false;
        }
    }

    public List<EsportsTournamentSummary> listTournaments(String state, int limit) {
        String filter = state != null && !state.isEmpty() ? "WHERE state = ?" : "";
        String sql = String.format(LIST_TOURNAMENT_SUMMARIES, filter);
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (state != null && !state.isEmpty()) {
                statement.setString(index++, state);
            }
            statement.setInt(index, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                List<EsportsTournamentSummary> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new EsportsTournamentSummary(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("slug"),
                            rs.getString("state"),
                            rs.getString("event_type"),
                            rs.getString("starts_at"),
                            rs.getString("ends_at")
                    ));
                }
                return list;
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to list tournaments", ex);
            return Collections.emptyList();
        }
    }

    public int insertTournament(EsportsTournamentDraft draft) {
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_TOURNAMENT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, draft.getName());
            statement.setString(2, draft.getSlug());
            if (draft.getDescription() == null || draft.getDescription().isEmpty()) {
                statement.setNull(3, java.sql.Types.LONGVARCHAR);
            } else {
                statement.setString(3, draft.getDescription());
            }
            statement.setString(4, draft.getEventType());
            statement.setString(5, draft.getState());
            if (draft.getStartsAt() != null) {
                statement.setTimestamp(6, java.sql.Timestamp.valueOf(draft.getStartsAt()));
            } else {
                statement.setNull(6, java.sql.Types.TIMESTAMP);
            }
            if (draft.getEndsAt() != null) {
                statement.setTimestamp(7, java.sql.Timestamp.valueOf(draft.getEndsAt()));
            } else {
                statement.setNull(7, java.sql.Types.TIMESTAMP);
            }
            statement.setString(8, draft.getSettings() == null ? "{}" : draft.getSettings().toString());
            statement.setString(9, draft.getRewards() == null ? "{}" : draft.getRewards().toString());
            statement.setString(10, draft.getFeaturedStream() == null ? "{}" : draft.getFeaturedStream().toString());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to insert tournament draft", ex);
        }
        return -1;
    }

    public boolean replaceMatches(int tournamentId, List<EsportsMatchDraft> drafts) {
        try (Connection connection = Database.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteEvents = connection.prepareStatement(DELETE_MATCH_EVENTS);
                 PreparedStatement deleteReplays = connection.prepareStatement(DELETE_REPLAYS);
                 PreparedStatement deleteMatches = connection.prepareStatement(DELETE_MATCHES);
                 PreparedStatement insert = connection.prepareStatement(INSERT_MATCH)) {

                deleteEvents.setInt(1, tournamentId);
                deleteEvents.executeUpdate();
                deleteReplays.setInt(1, tournamentId);
                deleteReplays.executeUpdate();
                deleteMatches.setInt(1, tournamentId);
                deleteMatches.executeUpdate();

                if (drafts != null && !drafts.isEmpty()) {
                    for (EsportsMatchDraft draft : drafts) {
                        insert.setInt(1, tournamentId);
                        insert.setInt(2, draft.getRound());
                        if (draft.getClubAId() == null) {
                            insert.setNull(3, Types.INTEGER);
                        } else {
                            insert.setInt(3, draft.getClubAId());
                        }
                        if (draft.getClubBId() == null) {
                            insert.setNull(4, Types.INTEGER);
                        } else {
                            insert.setInt(4, draft.getClubBId());
                        }
                        insert.setString(5, draft.getState());
                        JsonObject metadata = draft.getMetadata();
                        if (metadata == null) {
                            insert.setNull(6, Types.LONGVARCHAR);
                        } else {
                            insert.setString(6, metadata.toString());
                        }
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }

                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                logger.log(Level.WARNING, "[EsportsRepository] Failed to replace matches for tournament " + tournamentId, ex);
                return false;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Could not obtain connection for replaceMatches", ex);
            return false;
        }
    }

    public boolean enrollClub(int tournamentId, int clubId, String status) {
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_TOURNAMENT_CLUB)) {
            statement.setInt(1, tournamentId);
            statement.setInt(2, clubId);
            statement.setString(3, status == null ? "registered" : status);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to enroll club " + clubId + " to tournament " + tournamentId, ex);
            return false;
        }
    }

    public boolean updateTournamentState(int tournamentId, String state) {
        try (Connection connection = Database.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_TOURNAMENT_STATE)) {
            statement.setString(1, state);
            statement.setInt(2, tournamentId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[EsportsRepository] Failed to update tournament state for #" + tournamentId, ex);
            return false;
        }
    }

    @FunctionalInterface
    private interface StatementConfigurer {
        void configure(PreparedStatement statement) throws SQLException;
    }
}
