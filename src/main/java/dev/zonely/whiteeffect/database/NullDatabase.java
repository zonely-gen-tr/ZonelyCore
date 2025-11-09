package dev.zonely.whiteeffect.database;

import dev.zonely.whiteeffect.booster.NetworkBooster;
import dev.zonely.whiteeffect.database.data.DataContainer;
import dev.zonely.whiteeffect.database.exception.ProfileLoadException;
import java.sql.Connection;
import java.util.*;


public class NullDatabase extends Database {
    private final Map<String, Map<String, Map<String, DataContainer>>> mem = new HashMap<>();

    @Override
    public void setupBoosters() { }

    @Override
    public void convertDatabase(Object player) { }

    @Override
    public void setBooster(String minigame, String booster, double multiplier, long expires) { }

    @Override
    public NetworkBooster getBooster(String minigame) { return null; }

    @Override
    public String getRankAndName(String player) { return player; }

    @Override
    public boolean getPreference(String player, String id, boolean def) { return def; }

    @Override
    public List<String[]> getLeaderBoard(String table, String... columns) { return Collections.emptyList(); }

    @Override
    public void close() { mem.clear(); }

    @Override
    public Map<String, Map<String, DataContainer>> load(String name) throws ProfileLoadException {
        Map<String, Map<String, DataContainer>> tables = mem.computeIfAbsent(name.toLowerCase(), k -> new HashMap<>());
        tables.computeIfAbsent("ZonelyCoreProfile", k -> new HashMap<>());
        return tables;
    }

    @Override
    public void save(String name, Map<String, Map<String, DataContainer>> tableMap) {
        mem.computeIfAbsent(name.toLowerCase(), k -> new HashMap<>()).putAll(tableMap);
    }

    @Override
    public void saveSync(String name, Map<String, Map<String, DataContainer>> tableMap) {
        save(name, tableMap);
    }

    @Override
    public String exists(String name) { return mem.containsKey(name.toLowerCase()) ? name : null; }

    @Override
    public Connection getConnection() throws java.sql.SQLException {
        throw new java.sql.SQLException("NullDatabase: no SQL connection in fail-open mode");
    }
}
