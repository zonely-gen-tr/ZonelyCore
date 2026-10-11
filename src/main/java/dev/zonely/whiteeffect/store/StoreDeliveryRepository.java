package dev.zonely.whiteeffect.store;

import java.sql.*;
import java.util.*;

public final class StoreDeliveryRepository {
    private final StorePurchaseService.Connections connections;
    public StoreDeliveryRepository(StorePurchaseService.Connections connections) { this.connections = connections; }

    public int resolveServer(String token) throws SQLException {
        if (token == null || token.trim().isEmpty()) return 0;
        try (Connection c = connections.open(); PreparedStatement s = c.prepareStatement(
                "SELECT id FROM plugin_servers WHERE password=?")) {
            s.setString(1, token);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return 0;
                int id = r.getInt(1);
                return r.next() ? 0 : id;
            }
        }
    }

    public List<Delivery> claim(int server, Set<String> online, boolean requireOnline, int limit) throws SQLException {
        List<Delivery> rows = new ArrayList<>();
        if (requireOnline && online.isEmpty()) return rows;
        String filter = "";
        if (requireOnline) filter = " AND LOWER(username) IN (" + String.join(",", Collections.nCopies(online.size(), "?")) + ")";
        try (Connection c = connections.open(); PreparedStatement s = c.prepareStatement(
                "SELECT id,username,command_text FROM ZonelyCoreStoreDeliveries WHERE server_id=? AND status='PENDING'" + filter + " ORDER BY id LIMIT ?")) {
            s.setInt(1, server);
            int parameter=2;
            if(requireOnline) for(String name:online) s.setString(parameter++, name);
            s.setInt(parameter, Math.max(1, Math.min(limit, 100)));
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    String name = r.getString(2);
                    if (!requireOnline || online.contains(name.toLowerCase(Locale.ROOT)))
                        rows.add(new Delivery(r.getLong(1), name, r.getString(3)));
                }
            }
            List<Delivery> claimed = new ArrayList<>();
            try (PreparedStatement update = c.prepareStatement(
                    "UPDATE ZonelyCoreStoreDeliveries SET status='CLAIMED',processed_at=? WHERE id=? AND server_id=? AND status='PENDING'")) {
                for (Delivery row : rows) {
                    update.setLong(1, System.currentTimeMillis()); update.setLong(2, row.id); update.setInt(3, server);
                    if (update.executeUpdate() == 1) claimed.add(row);
                }
            }
            return claimed;
        }
    }

    public void complete(long id, String status, String result) throws SQLException {
        if (!Arrays.asList("DONE", "FAILED", "PENDING").contains(status)) throw new IllegalArgumentException("Invalid delivery status");
        try (Connection c = connections.open(); PreparedStatement s = c.prepareStatement(
                "UPDATE ZonelyCoreStoreDeliveries SET status=?,result_message=?,processed_at=? WHERE id=? AND status='CLAIMED'")) {
            s.setString(1, status); s.setString(2, result == null ? "" : result.substring(0, Math.min(255, result.length())));
            s.setLong(3, System.currentTimeMillis()); s.setLong(4, id); s.executeUpdate();
        }
    }

    public static final class Delivery {
        public final long id;
        public final String username, command;
        Delivery(long id, String username, String command) { this.id=id; this.username=username; this.command=command; }
    }
}
