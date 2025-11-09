package dev.zonely.whiteeffect.support;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;
import java.util.logging.Level;

final class SupportRepository {

    private final Core plugin;
    private final ExecutorService executor;

    SupportRepository(Core plugin) {
        this.plugin = plugin;
        ThreadFactory factory = r -> {
            Thread thread = new Thread(r, "SupportRepository");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(2, factory);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public CompletableFuture<Optional<SupportTicket>> findActiveTicketByUserId(int userId) {
        String sql = "SELECT * FROM supports WHERE userid = ? AND statusID IN (1,2,3) ORDER BY id DESC LIMIT 1";
        return supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapTicket(rs));
                    }
                }
            } catch (SQLException ex) {
                logSqlError("findActiveTicketByUserId", ex);
            }
            return Optional.empty();
        });
    }

    public CompletableFuture<List<SupportTicket>> listTicketsByStatuses(List<SupportStatus> statuses, int limit) {
        if (statuses == null || statuses.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM supports WHERE statusID IN (");
        for (int i = 0; i < statuses.size(); i++) {
            if (i > 0) sql.append(',');
            sql.append('?');
        }
        sql.append(") ORDER BY updated_at DESC");
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }
        return supplyAsync(() -> {
            List<SupportTicket> tickets = new ArrayList<>();
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < statuses.size(); i++) {
                    ps.setInt(i + 1, statuses.get(i).getId());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tickets.add(mapTicket(rs));
                    }
                }
            } catch (SQLException ex) {
                logSqlError("listTicketsByStatuses", ex);
            }
            return tickets;
        });
    }

    public CompletableFuture<Integer> resolveDefaultCategoryId() {
        String sql = "SELECT id FROM supportcategories "
                + "ORDER BY CASE WHEN row_order IS NULL THEN 1 ELSE 0 END, row_order ASC, id ASC LIMIT 1";
        return supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            } catch (SQLException ex) {
                logSqlError("resolveDefaultCategoryId", ex);
            }
            return 0;
        });
    }

    public CompletableFuture<List<SupportCategory>> listCategories() {
        String sql = "SELECT id, name FROM supportcategories "
                + "ORDER BY CASE WHEN row_order IS NULL THEN 1 ELSE 0 END, row_order ASC, id ASC";
        return supplyAsync(() -> {
            List<SupportCategory> categories = new ArrayList<>();
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    categories.add(new SupportCategory(rs.getInt("id"), rs.getString("name")));
                }
            } catch (SQLException ex) {
                logSqlError("listCategories", ex);
            }
            return categories;
        });
    }

    public CompletableFuture<SupportTicket> createTicket(int userId,
                                                         String ipAddress,
                                                         String title,
                                                         String message,
                                                         int categoryId) {
        return supplyAsync(() -> {
            String sql = "INSERT INTO supports (title, ipAdress, userid, message, categoryID, statusID, readed, updated_at, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            Timestamp now = Timestamp.from(Instant.now());
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, title);
                ps.setString(2, ipAddress);
                ps.setInt(3, userId);
                ps.setString(4, message);
                ps.setInt(5, categoryId);
                ps.setInt(6, SupportStatus.OPEN.getId());
                ps.setString(7, "1");
                ps.setTimestamp(8, now);
                ps.setTimestamp(9, now);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        int id = keys.getInt(1);
                        return new SupportTicket(
                                id,
                                userId,
                                null,
                                ipAddress,
                                title,
                                message,
                                categoryId,
                                SupportStatus.OPEN,
                                true,
                                now,
                                now
                        );
                    }
                }
            } catch (SQLException ex) {
                logSqlError("createTicket", ex);
            }
            throw new IllegalStateException("Unable to create support ticket");
        });
    }

    public CompletableFuture<List<SupportMessage>> fetchMessagesAfter(int ticketId, int lastMessageId) {
        String sql = "SELECT SM.*, U.subname, U.nick FROM supportmsgs SM "
                + "LEFT JOIN userslist U ON SM.userid = U.id "
                + "WHERE SM.support_id = ? AND SM.id > ? ORDER BY SM.id ASC";
        return supplyAsync(() -> {
            List<SupportMessage> messages = new ArrayList<>();
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, ticketId);
                ps.setInt(2, lastMessageId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        messages.add(mapMessage(rs));
                    }
                }
            } catch (SQLException ex) {
                logSqlError("fetchMessagesAfter", ex);
            }
            return messages;
        });
    }

    public CompletableFuture<Integer> getLastMessageId(int ticketId) {
        String sql = "SELECT id FROM supportmsgs WHERE support_id = ? ORDER BY id DESC LIMIT 1";
        return supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, ticketId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            } catch (SQLException ex) {
                logSqlError("getLastMessageId", ex);
            }
            return 0;
        });
    }

    public CompletableFuture<SupportMessage> addCustomerMessage(int ticketId, int userId, String message) {
        return supplyAsync(() -> {
            try {
                return insertMessage(ticketId, userId, message, 2, SupportStatus.OPEN);
            } catch (SQLException ex) {
                logSqlError("addCustomerMessage", ex);
                throw new RuntimeException("Unable to add message.", ex);
            }
        });
    }

    public CompletableFuture<SupportMessage> addStaffMessage(int ticketId, int userId, String message) {
        return supplyAsync(() -> {
            try {
                return insertMessage(ticketId, userId, message, 1, SupportStatus.ANSWERED);
            } catch (SQLException ex) {
                logSqlError("addStaffMessage", ex);
                throw new RuntimeException("Unable to add message.", ex);
            }
        });
    }

    public CompletableFuture<Void> markTicketRead(int ticketId) {
        String sql = "UPDATE supports SET readed = '1' WHERE id = ?";
        return runAsync(sql, ticketId);
    }

    public CompletableFuture<Void> closeTicket(int ticketId) {
        String sql = "UPDATE supports SET statusID = ?, readed = '0', updated_at = ? WHERE id = ?";
        Timestamp now = Timestamp.from(Instant.now());
        return runAsync(sql, SupportStatus.CLOSED.getId(), now, ticketId);
    }

    public CompletableFuture<Void> banTicket(int ticketId) {
        Timestamp now = Timestamp.from(Instant.now());
        return supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement("UPDATE supports SET statusID = ?, readed = '0', updated_at = ? WHERE id = ?")) {
                    ps.setInt(1, SupportStatus.CLOSED.getId());
                    ps.setTimestamp(2, now);
                    ps.setInt(3, ticketId);
                    ps.executeUpdate();
                }

                try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM supportbanneds WHERE support_id = ? LIMIT 1")) {
                    check.setInt(1, ticketId);
                    boolean exists;
                    try (ResultSet rs = check.executeQuery()) {
                        exists = rs.next();
                    }
                    if (!exists) {
                        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO supportbanneds (support_id, created_at) VALUES (?, ?)")) {
                            insert.setInt(1, ticketId);
                            insert.setTimestamp(2, now);
                            insert.executeUpdate();
                        }
                    }
                }
                connection.commit();
            } catch (SQLException ex) {
                logSqlError("banTicket", ex);
            }
            return null;
        });
    }

    public CompletableFuture<Boolean> isTicketBanned(int ticketId) {
        String sql = "SELECT 1 FROM supportbanneds WHERE support_id = ? LIMIT 1";
        return supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, ticketId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException ex) {
                logSqlError("isTicketBanned", ex);
            }
            return false;
        });
    }

    public CompletableFuture<Optional<SupportTicket>> loadTicket(int ticketId) {
        String sql = "SELECT * FROM supports WHERE id = ?";
        return supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, ticketId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapTicket(rs));
                    }
                }
            } catch (SQLException ex) {
                logSqlError("loadTicket", ex);
            }
            return Optional.empty();
        });
    }

    public CompletableFuture<List<SupportTicket>> listTicketsByUser(int userId, int limit) {
        String sql = "SELECT * FROM supports WHERE userid = ? ORDER BY id DESC";
        if (limit > 0) {
            sql += " LIMIT " + limit;
        }
        final String finalSql = sql;
        return supplyAsync(() -> {
            List<SupportTicket> tickets = new ArrayList<>();
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(finalSql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tickets.add(mapTicket(rs));
                    }
                }
            } catch (SQLException ex) {
                logSqlError("listTicketsByUser", ex);
            }
            return tickets;
        });
    }

    private SupportMessage insertMessage(int ticketId, int userId, String message, int location, SupportStatus newStatus) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            int messageId;
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO supportmsgs (userid, support_id, message, location, created_at) VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userId);
                ps.setInt(2, ticketId);
                ps.setString(3, message);
                ps.setInt(4, location);
                ps.setTimestamp(5, now);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Failed to generate message id");
                    }
                    messageId = keys.getInt(1);
                }
            }

            try (PreparedStatement ps = connection.prepareStatement("UPDATE supports SET statusID = ?, readed = '0', updated_at = ? WHERE id = ?")) {
                ps.setInt(1, newStatus.getId());
                ps.setTimestamp(2, now);
                ps.setInt(3, ticketId);
                ps.executeUpdate();
            }
            connection.commit();
            return new SupportMessage(messageId, ticketId, userId, null, message, location, now);
        }
    }

    private SupportTicket mapTicket(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        Integer userId = rs.getInt("userid");
        if (rs.wasNull()) {
            userId = null;
        }
        String title = rs.getString("title");
        String message = rs.getString("message");
        String ip = rs.getString("ipAdress");
        int categoryId = rs.getInt("categoryID");
        SupportStatus status = SupportStatus.fromId(rs.getInt("statusID"));
        boolean read = rs.getInt("readed") == 1;
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new SupportTicket(id, userId, null, ip, title, message, categoryId, status, read, updatedAt, createdAt);
    }

    private SupportMessage mapMessage(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int ticketId = rs.getInt("support_id");
        Integer userId = rs.getInt("userid");
        if (rs.wasNull()) {
            userId = null;
        }
        String author = rs.getString("nick");
        if (author == null || author.trim().isEmpty()) {
            author = rs.getString("subname");
        }
        if ((author == null || author.trim().isEmpty()) && userId != null) {
            author = "User#" + userId;
        }
        if (author == null || author.trim().isEmpty()) {
            author = "Support";
        }
        String message = rs.getString("message");
        int location = rs.getInt("location");
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new SupportMessage(id, ticketId, userId, author, message, location, createdAt);
    }

    private CompletableFuture<Void> runAsync(String sql, Object... params) {
        return supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement ps = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                ps.executeUpdate();
            } catch (SQLException ex) {
                logSqlError("runAsync", ex);
            }
            return null;
        });
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    private Connection getConnection() throws SQLException {
        Database database = Database.getInstance();
        return database.getConnection();
    }

    private void logSqlError(String method, SQLException ex) {
        plugin.getLogger().log(Level.WARNING, "[Support] SQL error during " + method + ": " + ex.getMessage(), ex);
    }
}
