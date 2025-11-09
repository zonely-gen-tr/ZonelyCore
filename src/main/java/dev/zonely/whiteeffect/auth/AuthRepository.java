package dev.zonely.whiteeffect.auth;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;
import org.bukkit.Location;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;

public final class AuthRepository {

    private final Database database;
    private final AuthDataSource.Columns columns;

    public AuthRepository(Database database, AuthDataSource.Columns columns) {
        this.database = database;
        this.columns = columns;
    }

    public Optional<AuthAccount> findByUsername(String username) {
        String sql = "SELECT * FROM `" + columns.table() + "` WHERE LOWER(`" + columns.username() + "`) = ? LIMIT 1";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapAccount(rs));
                }
            }
        } catch (SQLException ex) {
            Core.getInstance().getLogger().log(Level.WARNING, "[Auth] Failed to query account for " + username, ex);
        }
        return Optional.empty();
    }

    public AuthAccount insertAccount(String username, String displayName, String passwordHash, String ip, long timestamp, Location location) throws SQLException {
        List<String> columnNames = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        addColumn(columnNames, params, columns.username(), username);
        if (hasColumn(columns.displayName())) {
            if (columns.displayName().equals(columns.username())) {
                int idx = columnNames.indexOf(columns.username());
                if (idx >= 0) {
                    params.set(idx, displayName);
                }
            } else {
                addColumn(columnNames, params, columns.displayName(), displayName);
            }
        }
        addColumn(columnNames, params, columns.password(), passwordHash);
        addColumn(columnNames, params, columns.salt(), "");
        addColumn(columnNames, params, columns.email(), "");
        addColumn(columnNames, params, columns.logged(), 1);
        addColumn(columnNames, params, columns.hasSession(), 1);
        addColumn(columnNames, params, columns.ip(), ip);
        addColumn(columnNames, params, columns.lastLogin(), new Timestamp(timestamp));
        addColumn(columnNames, params, columns.registerDate(), new Timestamp(timestamp));
        addColumn(columnNames, params, columns.registerIp(), ip);
        addColumn(columnNames, params, columns.twoFactorStatus(), 0);
        addLocationColumns(columnNames, params, location);

        if (columnNames.isEmpty()) {
            throw new SQLException("No columns defined for auth datasource insert.");
        }

        StringBuilder columnSql = new StringBuilder();
        StringBuilder valuesSql = new StringBuilder();
        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0) {
                columnSql.append(", ");
                valuesSql.append(", ");
            }
            columnSql.append('`').append(columnNames.get(i)).append('`');
            valuesSql.append('?');
        }

        String sql = "INSERT INTO `" + columns.table() + "` (" + columnSql + ") VALUES (" + valuesSql + ")";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParameters(ps, params);
            ps.executeUpdate();

            Integer id = null;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getInt(1);
                }
            }
            return new AuthAccount(id, username, displayName, passwordHash, "", true, true, ip, timestamp, timestamp, ip, false);
        }
    }

    public void markLogin(String username, String ip, long timestamp, Location location) {
        List<String> assignments = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        addAssignment(assignments, params, columns.logged(), 1);
        addAssignment(assignments, params, columns.hasSession(), 1);
        addAssignment(assignments, params, columns.ip(), ip);
        addAssignment(assignments, params, columns.lastLogin(), new Timestamp(timestamp));
        addLocationAssignments(assignments, params, location);

        if (assignments.isEmpty()) {
            return;
        }

        StringBuilder setSql = new StringBuilder();
        for (int i = 0; i < assignments.size(); i++) {
            if (i > 0) {
                setSql.append(", ");
            }
            setSql.append(assignments.get(i));
        }

        String sql = "UPDATE `" + columns.table() + "` SET " + setSql + " WHERE LOWER(`" + columns.username() + "`) = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            setParameters(ps, params);
            ps.setString(params.size() + 1, username.toLowerCase(Locale.ROOT));
            ps.executeUpdate();
        } catch (SQLException ex) {
            Core.getInstance().getLogger().log(Level.WARNING, "[Auth] Failed to update login state for " + username, ex);
        }
    }

    public void markLogout(String username, boolean clearSession) {
        List<String> assignments = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        addAssignment(assignments, params, columns.logged(), 0);
        addAssignment(assignments, params, columns.hasSession(), clearSession ? 0 : 1);

        if (assignments.isEmpty()) {
            return;
        }

        StringBuilder setSql = new StringBuilder();
        for (int i = 0; i < assignments.size(); i++) {
            if (i > 0) {
                setSql.append(", ");
            }
            setSql.append(assignments.get(i));
        }

        String sql = "UPDATE `" + columns.table() + "` SET " + setSql + " WHERE LOWER(`" + columns.username() + "`) = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            setParameters(ps, params);
            ps.setString(params.size() + 1, username.toLowerCase(Locale.ROOT));
            ps.executeUpdate();
        } catch (SQLException ex) {
            Core.getInstance().getLogger().log(Level.WARNING, "[Auth] Failed to clear login state for " + username, ex);
        }
    }

    public void updateEmail(int userId, String email) throws SQLException {
        String sql = "UPDATE `" + columns.table() + "` SET `" + columns.email() + "` = ? WHERE `" + columns.id() + "` = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    private AuthAccount mapAccount(ResultSet rs) throws SQLException {
        Integer id = null;
        try {
            id = rs.getInt(columns.id());
        } catch (SQLException ignored) {
        }
        String username = rs.getString(columns.username());
        String display = rs.getString(columns.displayName());
        String password = rs.getString(columns.password());
        String email = safeGet(rs, columns.email());
        boolean logged = safeGetBoolean(rs, columns.logged());
        boolean session = safeGetBoolean(rs, columns.hasSession());
        String ip = safeGet(rs, columns.ip());
        long lastLogin = safeGetLong(rs, columns.lastLogin());
        long registerDate = safeGetLong(rs, columns.registerDate());
        String registerIp = safeGet(rs, columns.registerIp());
        boolean twoFactor = safeGetBoolean(rs, columns.twoFactorStatus());
        return new AuthAccount(id, username, display, password, email, logged, session, ip, lastLogin, registerDate, registerIp, twoFactor);
    }

    private String safeGet(ResultSet rs, String column) {
        if (!hasColumn(column)) {
            return "";
        }
        try {
            return rs.getString(column);
        } catch (SQLException ignored) {
            return "";
        }
    }

    private long safeGetLong(ResultSet rs, String column) {
        if (!hasColumn(column)) {
            return 0L;
        }
        try {
            Timestamp timestamp = rs.getTimestamp(column);
            if (timestamp != null) {
                return timestamp.getTime();
            }
        } catch (SQLException ignored) {
        }
        try {
            return rs.getLong(column);
        } catch (SQLException ignored) {
            return 0L;
        }
    }

    private boolean safeGetBoolean(ResultSet rs, String column) {
        if (!hasColumn(column)) {
            return false;
        }
        try {
            String raw = rs.getString(column);
            if (raw != null) {
                raw = raw.trim();
                if ("1".equals(raw) || "true".equalsIgnoreCase(raw)) {
                    return true;
                }
                if ("0".equals(raw) || "false".equalsIgnoreCase(raw) || raw.isEmpty()) {
                    return false;
                }
            }
            return rs.getInt(column) == 1;
        } catch (SQLException ignored) {
            return false;
        }
    }

    private boolean hasColumn(String column) {
        return column != null && !column.trim().isEmpty();
    }

    private boolean hasLocationColumns() {
        return hasColumn(columns.lastLocX())
                && hasColumn(columns.lastLocY())
                && hasColumn(columns.lastLocZ())
                && hasColumn(columns.lastLocWorld())
                && hasColumn(columns.lastLocYaw())
                && hasColumn(columns.lastLocPitch());
    }

    private void addColumn(List<String> columnNames, List<Object> params, String column, Object value) {
        if (!hasColumn(column)) {
            return;
        }
        if (columnNames.contains(column)) {
            return;
        }
        columnNames.add(column);
        params.add(value);
    }

    private void addLocationColumns(List<String> columnNames, List<Object> params, Location location) {
        if (!hasLocationColumns()) {
            return;
        }
        double x = 0D;
        double y = 0D;
        double z = 0D;
        String world = "";
        float yaw = 0F;
        float pitch = 0F;
        if (location != null) {
            x = location.getX();
            y = location.getY();
            z = location.getZ();
            world = location.getWorld() == null ? "" : location.getWorld().getName();
            yaw = location.getYaw();
            pitch = location.getPitch();
        }
        addColumn(columnNames, params, columns.lastLocX(), x);
        addColumn(columnNames, params, columns.lastLocY(), y);
        addColumn(columnNames, params, columns.lastLocZ(), z);
        addColumn(columnNames, params, columns.lastLocWorld(), world);
        addColumn(columnNames, params, columns.lastLocYaw(), yaw);
        addColumn(columnNames, params, columns.lastLocPitch(), pitch);
    }

    private void addAssignment(List<String> assignments, List<Object> params, String column, Object value) {
        if (!hasColumn(column)) {
            return;
        }
        assignments.add("`" + column + "` = ?");
        params.add(value);
    }

    private void addLocationAssignments(List<String> assignments, List<Object> params, Location location) {
        if (!hasLocationColumns()) {
            return;
        }
        double x = 0D;
        double y = 0D;
        double z = 0D;
        String world = "";
        float yaw = 0F;
        float pitch = 0F;
        if (location != null) {
            x = location.getX();
            y = location.getY();
            z = location.getZ();
            world = location.getWorld() == null ? "" : location.getWorld().getName();
            yaw = location.getYaw();
            pitch = location.getPitch();
        }
        addAssignment(assignments, params, columns.lastLocX(), x);
        addAssignment(assignments, params, columns.lastLocY(), y);
        addAssignment(assignments, params, columns.lastLocZ(), z);
        addAssignment(assignments, params, columns.lastLocWorld(), world);
        addAssignment(assignments, params, columns.lastLocYaw(), yaw);
        addAssignment(assignments, params, columns.lastLocPitch(), pitch);
    }

    private void setParameters(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            setParameter(ps, i + 1, params.get(i));
        }
    }

    private void setParameter(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
            return;
        }
        if (value instanceof Integer) {
            ps.setInt(index, (Integer) value);
            return;
        }
        if (value instanceof Long) {
            ps.setLong(index, (Long) value);
            return;
        }
        if (value instanceof Double) {
            ps.setDouble(index, (Double) value);
            return;
        }
        if (value instanceof Float) {
            ps.setFloat(index, (Float) value);
            return;
        }
        if (value instanceof Boolean) {
            ps.setBoolean(index, (Boolean) value);
            return;
        }
        if (value instanceof Timestamp) {
            ps.setTimestamp(index, (Timestamp) value);
            return;
        }
        ps.setObject(index, value);
    }
}
