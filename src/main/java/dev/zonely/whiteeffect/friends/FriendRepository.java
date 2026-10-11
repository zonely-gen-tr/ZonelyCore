package dev.zonely.whiteeffect.friends;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class FriendRepository {

    private static final int MAX_NAME_LENGTH = 32;

    private final Core plugin;
    private final ExecutorService executor;
    private final AtomicLong nextSqlWarningAt = new AtomicLong();

    public FriendRepository(Core plugin) {
        this.plugin = plugin;
        ThreadFactory factory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "FriendRepository");
                thread.setDaemon(true);
                return thread;
            }
        };
        this.executor = Executors.newSingleThreadExecutor(factory);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public CompletableFuture<Void> ensureTables() {
        return supplyAsync(new Supplier<Void>() {
            @Override
            public Void get() {
                String[] ddl = new String[]{
                        "CREATE TABLE IF NOT EXISTS `ZonelyCoreFriendRequests` ("
                                + "`id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,"
                                + "`requester_uuid` VARCHAR(36) NOT NULL,"
                                + "`requester_name` VARCHAR(32) NOT NULL,"
                                + "`target_uuid` VARCHAR(36) NOT NULL,"
                                + "`target_name` VARCHAR(32) NOT NULL,"
                                + "`status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',"
                                + "`created_at` BIGINT NOT NULL,"
                                + "`responded_at` BIGINT NOT NULL DEFAULT 0,"
                                + "PRIMARY KEY (`id`),"
                                + "INDEX `friend_req_target_idx` (`target_uuid`, `status`, `id`),"
                                + "INDEX `friend_req_requester_idx` (`requester_uuid`, `status`, `id`),"
                                + "UNIQUE KEY `friend_req_unique_pending` (`requester_uuid`, `target_uuid`, `status`)"
                                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;",
                        "CREATE TABLE IF NOT EXISTS `ZonelyCoreFriendRelations` ("
                                + "`user_low_uuid` VARCHAR(36) NOT NULL,"
                                + "`user_low_name` VARCHAR(32) NOT NULL,"
                                + "`user_high_uuid` VARCHAR(36) NOT NULL,"
                                + "`user_high_name` VARCHAR(32) NOT NULL,"
                                + "`created_at` BIGINT NOT NULL,"
                                + "PRIMARY KEY (`user_low_uuid`, `user_high_uuid`),"
                                + "INDEX `friend_rel_low_idx` (`user_low_uuid`),"
                                + "INDEX `friend_rel_high_idx` (`user_high_uuid`)"
                                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;",
                        "CREATE TABLE IF NOT EXISTS `ZonelyCoreFriendMessages` ("
                                + "`id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,"
                                + "`sender_uuid` VARCHAR(36) NOT NULL,"
                                + "`sender_name` VARCHAR(32) NOT NULL,"
                                + "`target_uuid` VARCHAR(36) NOT NULL,"
                                + "`target_name` VARCHAR(32) NOT NULL,"
                                + "`kind` VARCHAR(16) NOT NULL DEFAULT 'DIRECT',"
                                + "`message` VARCHAR(512) NOT NULL,"
                                + "`server_from` VARCHAR(64) NULL,"
                                + "`created_at` BIGINT NOT NULL,"
                                + "PRIMARY KEY (`id`),"
                                + "INDEX `friend_msg_target_idx` (`target_uuid`, `created_at`),"
                                + "INDEX `friend_msg_sender_idx` (`sender_uuid`, `created_at`)"
                                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;",
                        "CREATE TABLE IF NOT EXISTS `ZonelyCoreFriendMailbox` ("
                                + "`id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,"
                                + "`target_uuid` VARCHAR(36) NOT NULL,"
                                + "`target_name` VARCHAR(32) NULL,"
                                + "`kind` VARCHAR(24) NOT NULL,"
                                + "`payload` TEXT NOT NULL,"
                                + "`created_at` BIGINT NOT NULL,"
                                + "`delivered_at` BIGINT NOT NULL DEFAULT 0,"
                                + "PRIMARY KEY (`id`),"
                                + "INDEX `friend_mailbox_target_idx` (`target_uuid`, `delivered_at`, `id`)"
                                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
                };

                try (Connection connection = getConnection()) {
                    for (String query : ddl) {
                        try (PreparedStatement statement = connection.prepareStatement(query)) {
                            statement.executeUpdate();
                        }
                    }
                } catch (SQLException ex) {
                    logSql("ensureTables", ex);
                }
                return null;
            }
        });
    }

    public CompletableFuture<FriendIdentity> resolveIdentity(final String rawInput) {
        return supplyAsync(new Supplier<FriendIdentity>() {
            @Override
            public FriendIdentity get() {
                String input = normalizeInput(rawInput);
                if (input.isEmpty()) {
                    return null;
                }

                UUID parsedUuid = parseUuid(input);
                try (Connection connection = getConnection()) {
                    if (parsedUuid != null) {
                        FriendIdentity identity = resolveByUuid(connection, parsedUuid);
                        if (identity != null) {
                            return identity;
                        }
                    }
                    return resolveByName(connection, input);
                } catch (SQLException ex) {
                    logSql("resolveIdentity", ex);
                    return null;
                }
            }
        });
    }

    public CompletableFuture<FriendIdentity> resolveFriendOf(final UUID ownerUuid, final String rawInput) {
        return supplyAsync(new Supplier<FriendIdentity>() {
            @Override
            public FriendIdentity get() {
                if (ownerUuid == null) {
                    return null;
                }
                String input = normalizeInput(rawInput);
                if (input.isEmpty()) {
                    return null;
                }

                try (Connection connection = getConnection()) {
                    UUID parsedUuid = parseUuid(input);
                    if (parsedUuid != null) {
                        FriendIdentity candidate = resolveByUuid(connection, parsedUuid);
                        if (candidate != null && areFriendsRaw(connection, ownerUuid, candidate.getUuid())) {
                            return candidate;
                        }
                    }

                    String lookup = input.toLowerCase(Locale.ROOT);
                    String sql = "SELECT "
                            + "CASE WHEN `user_low_uuid` = ? THEN `user_high_uuid` ELSE `user_low_uuid` END AS `friend_uuid`, "
                            + "CASE WHEN `user_low_uuid` = ? THEN `user_high_name` ELSE `user_low_name` END AS `friend_name` "
                            + "FROM `ZonelyCoreFriendRelations` "
                            + "WHERE (`user_low_uuid` = ? OR `user_high_uuid` = ?) "
                            + "AND LOWER(CASE WHEN `user_low_uuid` = ? THEN `user_high_name` ELSE `user_low_name` END) = ? "
                            + "LIMIT 1";
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        String owner = ownerUuid.toString();
                        statement.setString(1, owner);
                        statement.setString(2, owner);
                        statement.setString(3, owner);
                        statement.setString(4, owner);
                        statement.setString(5, owner);
                        statement.setString(6, lookup);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                UUID friendUuid = parseUuid(resultSet.getString("friend_uuid"));
                                String friendName = normalizeName(resultSet.getString("friend_name"));
                                if (friendUuid != null && !friendName.isEmpty()) {
                                    return new FriendIdentity(friendUuid, friendName);
                                }
                            }
                        }
                    }
                } catch (SQLException ex) {
                    logSql("resolveFriendOf", ex);
                }
                return null;
            }
        });
    }

    public CompletableFuture<Boolean> areFriends(final UUID first, final UUID second) {
        return supplyAsync(new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                if (first == null || second == null) {
                    return false;
                }
                try (Connection connection = getConnection()) {
                    return areFriendsRaw(connection, first, second);
                } catch (SQLException ex) {
                    logSql("areFriends", ex);
                    return false;
                }
            }
        });
    }

    public CompletableFuture<FriendRequestCreateResult> createRequest(final FriendIdentity requester,
                                                                      final FriendIdentity target) {
        return supplyAsync(new Supplier<FriendRequestCreateResult>() {
            @Override
            public FriendRequestCreateResult get() {
                if (requester == null || target == null) {
                    return new FriendRequestCreateResult(FriendRequestCreateResult.Status.ALREADY_PENDING, -1L);
                }
                if (requester.getUuid().equals(target.getUuid())) {
                    return new FriendRequestCreateResult(FriendRequestCreateResult.Status.ALREADY_PENDING, -1L);
                }

                long now = System.currentTimeMillis();
                try (Connection connection = getConnection()) {
                    if (areFriendsRaw(connection, requester.getUuid(), target.getUuid())) {
                        return new FriendRequestCreateResult(FriendRequestCreateResult.Status.ALREADY_FRIENDS, -1L);
                    }

                    if (hasPendingRequest(connection, requester.getUuid(), target.getUuid())) {
                        return new FriendRequestCreateResult(FriendRequestCreateResult.Status.ALREADY_PENDING, -1L);
                    }

                    FriendRequest reverse = findPendingRequestRaw(connection, target.getUuid(), requester.getUuid());
                    if (reverse != null) {
                        boolean accepted = acceptRequestRaw(connection, reverse, now);
                        if (accepted) {
                            return new FriendRequestCreateResult(FriendRequestCreateResult.Status.AUTO_ACCEPTED, reverse.getId());
                        }
                    }

                    String insert = "INSERT IGNORE INTO `ZonelyCoreFriendRequests` "
                            + "(`requester_uuid`, `requester_name`, `target_uuid`, `target_name`, `status`, `created_at`, `responded_at`) "
                            + "VALUES (?, ?, ?, ?, 'PENDING', ?, 0)";
                    try (PreparedStatement statement = connection.prepareStatement(insert, PreparedStatement.RETURN_GENERATED_KEYS)) {
                        statement.setString(1, requester.getUuid().toString());
                        statement.setString(2, normalizeName(requester.getName()));
                        statement.setString(3, target.getUuid().toString());
                        statement.setString(4, normalizeName(target.getName()));
                        statement.setLong(5, now);
                        statement.executeUpdate();
                        try (ResultSet keys = statement.getGeneratedKeys()) {
                            if (keys.next()) {
                                return new FriendRequestCreateResult(FriendRequestCreateResult.Status.CREATED, keys.getLong(1));
                            }
                        }
                    }
                    return new FriendRequestCreateResult(FriendRequestCreateResult.Status.ALREADY_PENDING, -1L);
                } catch (SQLException ex) {
                    logSql("createRequest", ex);
                    return new FriendRequestCreateResult(FriendRequestCreateResult.Status.ALREADY_PENDING, -1L);
                }
            }
        });
    }

    public CompletableFuture<List<FriendEntry>> listFriends(final UUID ownerUuid) {
        return supplyAsync(new Supplier<List<FriendEntry>>() {
            @Override
            public List<FriendEntry> get() {
                if (ownerUuid == null) {
                    return Collections.emptyList();
                }
                List<FriendEntry> entries = new ArrayList<FriendEntry>();
                try (Connection connection = getConnection()) {
                    String owner = ownerUuid.toString();
                    String query = "SELECT "
                            + "CASE WHEN r.`user_low_uuid` = ? THEN r.`user_high_uuid` ELSE r.`user_low_uuid` END AS `friend_uuid`, "
                            + "CASE WHEN r.`user_low_uuid` = ? THEN r.`user_high_name` ELSE r.`user_low_name` END AS `friend_name`, "
                            + "COALESCE(o.`online`, 0) AS `online`, "
                            + "o.`server` AS `server`, "
                            + "COALESCE(o.`last_seen`, 0) AS `last_seen` "
                            + "FROM `ZonelyCoreFriendRelations` r "
                            + "LEFT JOIN `ZonelyCoreOnlinePlayers` o "
                            + "ON o.`uuid` = CASE WHEN r.`user_low_uuid` = ? THEN r.`user_high_uuid` ELSE r.`user_low_uuid` END "
                            + "WHERE r.`user_low_uuid` = ? OR r.`user_high_uuid` = ? "
                            + "ORDER BY COALESCE(o.`online`, 0) DESC, `friend_name` ASC";

                    try (PreparedStatement statement = connection.prepareStatement(query)) {
                        statement.setString(1, owner);
                        statement.setString(2, owner);
                        statement.setString(3, owner);
                        statement.setString(4, owner);
                        statement.setString(5, owner);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            while (resultSet.next()) {
                                UUID friendUuid = parseUuid(resultSet.getString("friend_uuid"));
                                String friendName = normalizeName(resultSet.getString("friend_name"));
                                if (friendUuid == null || friendName.isEmpty()) {
                                    continue;
                                }
                                boolean online = resultSet.getInt("online") == 1;
                                String server = resultSet.getString("server");
                                long lastSeen = resultSet.getLong("last_seen");
                                entries.add(new FriendEntry(
                                        new FriendIdentity(friendUuid, friendName),
                                        online,
                                        server,
                                        lastSeen
                                ));
                            }
                        }
                    }
                } catch (SQLException ex) {
                    logSql("listFriends", ex);
                }
                return entries;
            }
        });
    }

    public CompletableFuture<Integer> countFriends(final UUID ownerUuid) {
        return supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                if (ownerUuid == null) {
                    return 0;
                }
                String query = "SELECT COUNT(*) AS `count` "
                        + "FROM `ZonelyCoreFriendRelations` "
                        + "WHERE `user_low_uuid` = ? OR `user_high_uuid` = ?";
                try (Connection connection = getConnection();
                     PreparedStatement statement = connection.prepareStatement(query)) {
                    String owner = ownerUuid.toString();
                    statement.setString(1, owner);
                    statement.setString(2, owner);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            return resultSet.getInt("count");
                        }
                    }
                } catch (SQLException ex) {
                    logSql("countFriends", ex);
                }
                return 0;
            }
        });
    }

    public CompletableFuture<Integer> countOutgoingPendingRequests(final UUID requesterUuid) {
        return supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                if (requesterUuid == null) {
                    return 0;
                }
                String query = "SELECT COUNT(*) AS `count` "
                        + "FROM `ZonelyCoreFriendRequests` "
                        + "WHERE `requester_uuid` = ? AND `status` = 'PENDING'";
                try (Connection connection = getConnection();
                     PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, requesterUuid.toString());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            return resultSet.getInt("count");
                        }
                    }
                } catch (SQLException ex) {
                    logSql("countOutgoingPendingRequests", ex);
                }
                return 0;
            }
        });
    }

    public CompletableFuture<Integer> countIncomingRequests(final UUID targetUuid) {
        return supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                if (targetUuid == null) {
                    return 0;
                }
                String query = "SELECT COUNT(*) AS `count` "
                        + "FROM `ZonelyCoreFriendRequests` "
                        + "WHERE `target_uuid` = ? AND `status` = 'PENDING'";
                try (Connection connection = getConnection();
                     PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, targetUuid.toString());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            return resultSet.getInt("count");
                        }
                    }
                } catch (SQLException ex) {
                    logSql("countIncomingRequests", ex);
                }
                return 0;
            }
        });
    }

    public CompletableFuture<List<FriendRequest>> listIncomingRequests(final UUID targetUuid) {
        return supplyAsync(new Supplier<List<FriendRequest>>() {
            @Override
            public List<FriendRequest> get() {
                if (targetUuid == null) {
                    return Collections.emptyList();
                }
                List<FriendRequest> requests = new ArrayList<FriendRequest>();
                String query = "SELECT `id`, `requester_uuid`, `requester_name`, `target_uuid`, `target_name`, `created_at` "
                        + "FROM `ZonelyCoreFriendRequests` "
                        + "WHERE `target_uuid` = ? AND `status` = 'PENDING' "
                        + "ORDER BY `id` DESC LIMIT 100";

                try (Connection connection = getConnection();
                     PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, targetUuid.toString());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            FriendRequest request = mapRequest(resultSet);
                            if (request != null) {
                                requests.add(request);
                            }
                        }
                    }
                } catch (SQLException ex) {
                    logSql("listIncomingRequests", ex);
                }
                return requests;
            }
        });
    }

    public CompletableFuture<FriendRequest> findIncomingRequest(final UUID targetUuid, final String requesterInput) {
        return supplyAsync(new Supplier<FriendRequest>() {
            @Override
            public FriendRequest get() {
                if (targetUuid == null) {
                    return null;
                }
                String input = normalizeInput(requesterInput);
                if (input.isEmpty()) {
                    return null;
                }

                UUID requesterUuid = parseUuid(input);
                String queryByUuid = "SELECT `id`, `requester_uuid`, `requester_name`, `target_uuid`, `target_name`, `created_at` "
                        + "FROM `ZonelyCoreFriendRequests` "
                        + "WHERE `target_uuid` = ? AND `requester_uuid` = ? AND `status` = 'PENDING' "
                        + "ORDER BY `id` DESC LIMIT 1";
                String queryByName = "SELECT `id`, `requester_uuid`, `requester_name`, `target_uuid`, `target_name`, `created_at` "
                        + "FROM `ZonelyCoreFriendRequests` "
                        + "WHERE `target_uuid` = ? AND LOWER(`requester_name`) = ? AND `status` = 'PENDING' "
                        + "ORDER BY `id` DESC LIMIT 1";

                try (Connection connection = getConnection()) {
                    if (requesterUuid != null) {
                        try (PreparedStatement statement = connection.prepareStatement(queryByUuid)) {
                            statement.setString(1, targetUuid.toString());
                            statement.setString(2, requesterUuid.toString());
                            try (ResultSet resultSet = statement.executeQuery()) {
                                if (resultSet.next()) {
                                    return mapRequest(resultSet);
                                }
                            }
                        }
                    }

                    try (PreparedStatement statement = connection.prepareStatement(queryByName)) {
                        statement.setString(1, targetUuid.toString());
                        statement.setString(2, input.toLowerCase(Locale.ROOT));
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                return mapRequest(resultSet);
                            }
                        }
                    }
                } catch (SQLException ex) {
                    logSql("findIncomingRequest", ex);
                }
                return null;
            }
        });
    }

    public CompletableFuture<Boolean> acceptRequest(final FriendRequest request) {
        return supplyAsync(new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                if (request == null) {
                    return false;
                }
                try (Connection connection = getConnection()) {
                    return acceptRequestRaw(connection, request, System.currentTimeMillis());
                } catch (SQLException ex) {
                    logSql("acceptRequest", ex);
                    return false;
                }
            }
        });
    }

    public CompletableFuture<Boolean> rejectRequest(final FriendRequest request) {
        return supplyAsync(new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                if (request == null) {
                    return false;
                }
                String query = "UPDATE `ZonelyCoreFriendRequests` SET `status` = 'REJECTED', `responded_at` = ? "
                        + "WHERE `id` = ? AND `status` = 'PENDING'";
                try (Connection connection = getConnection();
                     PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setLong(1, System.currentTimeMillis());
                    statement.setLong(2, request.getId());
                    return statement.executeUpdate() > 0;
                } catch (SQLException ex) {
                    logSql("rejectRequest", ex);
                    return false;
                }
            }
        });
    }

    public CompletableFuture<Boolean> removeFriend(final UUID first, final UUID second) {
        return supplyAsync(new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                if (first == null || second == null || first.equals(second)) {
                    return false;
                }
                Pair pair = normalizePair(first, second);
                String query = "DELETE FROM `ZonelyCoreFriendRelations` "
                        + "WHERE `user_low_uuid` = ? AND `user_high_uuid` = ?";
                try (Connection connection = getConnection();
                     PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, pair.lowUuid.toString());
                    statement.setString(2, pair.highUuid.toString());
                    return statement.executeUpdate() > 0;
                } catch (SQLException ex) {
                    logSql("removeFriend", ex);
                    return false;
                }
            }
        });
    }

    public CompletableFuture<Void> saveMessage(final FriendIdentity sender,
                                               final FriendIdentity target,
                                               final String kind,
                                               final String message,
                                               final String server) {
        return supplyAsync(new Supplier<Void>() {
            @Override
            public Void get() {
                if (sender == null || target == null) {
                    return null;
                }
                String query = "INSERT INTO `ZonelyCoreFriendMessages` "
                        + "(`sender_uuid`, `sender_name`, `target_uuid`, `target_name`, `kind`, `message`, `server_from`, `created_at`) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (Connection connection = getConnection();
                     PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, sender.getUuid().toString());
                    statement.setString(2, normalizeName(sender.getName()));
                    statement.setString(3, target.getUuid().toString());
                    statement.setString(4, normalizeName(target.getName()));
                    statement.setString(5, normalizeInput(kind).toUpperCase(Locale.ROOT));
                    statement.setString(6, trimMessage(message));
                    statement.setString(7, server);
                    statement.setLong(8, System.currentTimeMillis());
                    statement.executeUpdate();
                } catch (SQLException ex) {
                    logSql("saveMessage", ex);
                }
                return null;
            }
        });
    }

    public CompletableFuture<Void> queueMailbox(final UUID targetUuid,
                                                final String targetName,
                                                final FriendMailboxKind kind,
                                                final String payload) {
        return supplyAsync(new Supplier<Void>() {
            @Override
            public Void get() {
                if (targetUuid == null || kind == null) {
                    return null;
                }
                String query = "INSERT INTO `ZonelyCoreFriendMailbox` "
                        + "(`target_uuid`, `target_name`, `kind`, `payload`, `created_at`, `delivered_at`) "
                        + "VALUES (?, ?, ?, ?, ?, 0)";
                try (Connection connection = getConnection();
                     PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, targetUuid.toString());
                    statement.setString(2, normalizeName(targetName));
                    statement.setString(3, kind.name());
                    statement.setString(4, payload == null ? "" : payload);
                    statement.setLong(5, System.currentTimeMillis());
                    statement.executeUpdate();
                } catch (SQLException ex) {
                    logSql("queueMailbox", ex);
                }
                return null;
            }
        });
    }

    public CompletableFuture<List<FriendMailboxEntry>> consumeMailbox(final Collection<UUID> targetUuids, final int limit) {
        return supplyAsync(new Supplier<List<FriendMailboxEntry>>() {
            @Override
            public List<FriendMailboxEntry> get() {
                if (targetUuids == null || targetUuids.isEmpty()) {
                    return Collections.emptyList();
                }

                List<UUID> compactTargets = new ArrayList<UUID>();
                for (UUID uuid : targetUuids) {
                    if (uuid != null) {
                        compactTargets.add(uuid);
                    }
                }
                if (compactTargets.isEmpty()) {
                    return Collections.emptyList();
                }

                int rowLimit = Math.max(1, limit);
                List<FriendMailboxEntry> entries = new ArrayList<FriendMailboxEntry>();
                List<Long> deliveredIds = new ArrayList<Long>();

                StringBuilder placeholders = new StringBuilder();
                for (int i = 0; i < compactTargets.size(); i++) {
                    if (i > 0) {
                        placeholders.append(',');
                    }
                    placeholders.append('?');
                }

                String query = "SELECT `id`, `target_uuid`, `kind`, `payload` "
                        + "FROM `ZonelyCoreFriendMailbox` "
                        + "WHERE `delivered_at` = 0 AND `target_uuid` IN (" + placeholders + ") "
                        + "ORDER BY `id` ASC LIMIT ?";

                try (Connection connection = getConnection();
                     PreparedStatement statement = connection.prepareStatement(query)) {
                    int index = 1;
                    for (UUID uuid : compactTargets) {
                        statement.setString(index++, uuid.toString());
                    }
                    statement.setInt(index, rowLimit);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            long id = resultSet.getLong("id");
                            UUID target = parseUuid(resultSet.getString("target_uuid"));
                            String kind = resultSet.getString("kind");
                            String payload = resultSet.getString("payload");
                            if (target == null || kind == null || kind.isEmpty()) {
                                deliveredIds.add(id);
                                continue;
                            }
                            entries.add(new FriendMailboxEntry(id, target, kind, payload));
                            deliveredIds.add(id);
                        }
                    }

                    if (!deliveredIds.isEmpty()) {
                        markDelivered(connection, deliveredIds);
                    }
                } catch (SQLException ex) {
                    logSql("consumeMailbox", ex);
                }
                return entries;
            }
        });
    }

    private boolean acceptRequestRaw(Connection connection, FriendRequest request, long now) throws SQLException {
        String update = "UPDATE `ZonelyCoreFriendRequests` SET `status` = 'ACCEPTED', `responded_at` = ? "
                + "WHERE `id` = ? AND `status` = 'PENDING'";
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setLong(1, now);
            statement.setLong(2, request.getId());
            if (statement.executeUpdate() <= 0) {
                return false;
            }
        }

        Pair pair = normalizePair(request.getRequesterUuid(), request.getTargetUuid());
        String lowName = pair.lowUuid.equals(request.getRequesterUuid())
                ? request.getRequesterName()
                : request.getTargetName();
        String highName = pair.highUuid.equals(request.getRequesterUuid())
                ? request.getRequesterName()
                : request.getTargetName();

        String insert = "INSERT IGNORE INTO `ZonelyCoreFriendRelations` "
                + "(`user_low_uuid`, `user_low_name`, `user_high_uuid`, `user_high_name`, `created_at`) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, pair.lowUuid.toString());
            statement.setString(2, normalizeName(lowName));
            statement.setString(3, pair.highUuid.toString());
            statement.setString(4, normalizeName(highName));
            statement.setLong(5, now);
            statement.executeUpdate();
        }
        return true;
    }

    private boolean areFriendsRaw(Connection connection, UUID first, UUID second) throws SQLException {
        Pair pair = normalizePair(first, second);
        String query = "SELECT 1 FROM `ZonelyCoreFriendRelations` "
                + "WHERE `user_low_uuid` = ? AND `user_high_uuid` = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, pair.lowUuid.toString());
            statement.setString(2, pair.highUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean hasPendingRequest(Connection connection, UUID requester, UUID target) throws SQLException {
        String query = "SELECT 1 FROM `ZonelyCoreFriendRequests` "
                + "WHERE `requester_uuid` = ? AND `target_uuid` = ? AND `status` = 'PENDING' LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, requester.toString());
            statement.setString(2, target.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private FriendRequest findPendingRequestRaw(Connection connection, UUID requester, UUID target) throws SQLException {
        String query = "SELECT `id`, `requester_uuid`, `requester_name`, `target_uuid`, `target_name`, `created_at` "
                + "FROM `ZonelyCoreFriendRequests` "
                + "WHERE `requester_uuid` = ? AND `target_uuid` = ? AND `status` = 'PENDING' "
                + "ORDER BY `id` DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, requester.toString());
            statement.setString(2, target.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapRequest(resultSet);
                }
            }
        }
        return null;
    }

    private FriendIdentity resolveByUuid(Connection connection, UUID uuid) throws SQLException {
        String uuidText = uuid.toString();

        String online = "SELECT `name` FROM `ZonelyCoreOnlinePlayers` "
                + "WHERE `uuid` = ? ORDER BY `online` DESC, `last_seen` DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(online)) {
            statement.setString(1, uuidText);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String name = normalizeName(resultSet.getString("name"));
                    if (!name.isEmpty()) {
                        return new FriendIdentity(uuid, name);
                    }
                }
            }
        }

        String known = "SELECT `user_low_name` AS `name` FROM `ZonelyCoreFriendRelations` WHERE `user_low_uuid` = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(known)) {
            statement.setString(1, uuidText);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String name = normalizeName(resultSet.getString("name"));
                    if (!name.isEmpty()) {
                        return new FriendIdentity(uuid, name);
                    }
                }
            }
        }

        String known2 = "SELECT `user_high_name` AS `name` FROM `ZonelyCoreFriendRelations` WHERE `user_high_uuid` = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(known2)) {
            statement.setString(1, uuidText);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String name = normalizeName(resultSet.getString("name"));
                    if (!name.isEmpty()) {
                        return new FriendIdentity(uuid, name);
                    }
                }
            }
        }

        String requests = "SELECT `requester_name` AS `name` FROM `ZonelyCoreFriendRequests` "
                + "WHERE `requester_uuid` = ? ORDER BY `id` DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(requests)) {
            statement.setString(1, uuidText);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String name = normalizeName(resultSet.getString("name"));
                    if (!name.isEmpty()) {
                        return new FriendIdentity(uuid, name);
                    }
                }
            }
        }

        String requests2 = "SELECT `target_name` AS `name` FROM `ZonelyCoreFriendRequests` "
                + "WHERE `target_uuid` = ? ORDER BY `id` DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(requests2)) {
            statement.setString(1, uuidText);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String name = normalizeName(resultSet.getString("name"));
                    if (!name.isEmpty()) {
                        return new FriendIdentity(uuid, name);
                    }
                }
            }
        }

        return null;
    }

    private FriendIdentity resolveByName(Connection connection, String input) throws SQLException {
        String lookup = input.toLowerCase(Locale.ROOT);

        String online = "SELECT `uuid`, `name` FROM `ZonelyCoreOnlinePlayers` "
                + "WHERE LOWER(`name`) = ? ORDER BY `online` DESC, `last_seen` DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(online)) {
            statement.setString(1, lookup);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    UUID uuid = parseUuid(resultSet.getString("uuid"));
                    String name = normalizeName(resultSet.getString("name"));
                    if (uuid != null && !name.isEmpty()) {
                        return new FriendIdentity(uuid, name);
                    }
                }
            }
        }

        String[] queries = new String[] {
                "SELECT `user_low_uuid` AS `uuid`, `user_low_name` AS `name` FROM `ZonelyCoreFriendRelations` WHERE LOWER(`user_low_name`) = ? LIMIT 1",
                "SELECT `user_high_uuid` AS `uuid`, `user_high_name` AS `name` FROM `ZonelyCoreFriendRelations` WHERE LOWER(`user_high_name`) = ? LIMIT 1",
                "SELECT `requester_uuid` AS `uuid`, `requester_name` AS `name` FROM `ZonelyCoreFriendRequests` WHERE LOWER(`requester_name`) = ? ORDER BY `id` DESC LIMIT 1",
                "SELECT `target_uuid` AS `uuid`, `target_name` AS `name` FROM `ZonelyCoreFriendRequests` WHERE LOWER(`target_name`) = ? ORDER BY `id` DESC LIMIT 1"
        };

        for (String query : queries) {
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, lookup);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        UUID uuid = parseUuid(resultSet.getString("uuid"));
                        String name = normalizeName(resultSet.getString("name"));
                        if (uuid != null && !name.isEmpty()) {
                            return new FriendIdentity(uuid, name);
                        }
                    }
                }
            }
        }

        return null;
    }

    private void markDelivered(Connection connection, List<Long> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                placeholders.append(',');
            }
            placeholders.append('?');
        }

        String query = "UPDATE `ZonelyCoreFriendMailbox` SET `delivered_at` = ? WHERE `id` IN (" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, System.currentTimeMillis());
            for (int i = 0; i < ids.size(); i++) {
                statement.setLong(i + 2, ids.get(i));
            }
            statement.executeUpdate();
        }
    }

    private FriendRequest mapRequest(ResultSet resultSet) throws SQLException {
        UUID requesterUuid = parseUuid(resultSet.getString("requester_uuid"));
        UUID targetUuid = parseUuid(resultSet.getString("target_uuid"));
        if (requesterUuid == null || targetUuid == null) {
            return null;
        }
        String requesterName = normalizeName(resultSet.getString("requester_name"));
        String targetName = normalizeName(resultSet.getString("target_name"));
        long id = resultSet.getLong("id");
        long createdAt = resultSet.getLong("created_at");
        return new FriendRequest(id, requesterUuid, requesterName, targetUuid, targetName, createdAt);
    }

    private Connection getConnection() throws SQLException {
        return Database.getInstance().getConnection();
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    private void logSql(String action, SQLException ex) {
        long now = System.currentTimeMillis();
        long previous = nextSqlWarningAt.get();
        if (now >= previous && nextSqlWarningAt.compareAndSet(previous, now + 15000L)) {
            plugin.getLogger().log(Level.WARNING,
                    "[Friends] SQL error during " + action + "; further warnings suppressed for 15 seconds", ex);
        }
    }

    private static Pair normalizePair(UUID first, UUID second) {
        String left = first.toString();
        String right = second.toString();
        if (left.compareTo(right) <= 0) {
            return new Pair(first, second);
        }
        return new Pair(second, first);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(cleaned);
        } catch (IllegalArgumentException ignored) {
            if (cleaned.length() == 32) {
                try {
                    String hyphen = cleaned.substring(0, 8) + "-"
                            + cleaned.substring(8, 12) + "-"
                            + cleaned.substring(12, 16) + "-"
                            + cleaned.substring(16, 20) + "-"
                            + cleaned.substring(20);
                    return UUID.fromString(hyphen);
                } catch (Exception ignored2) {
                    return null;
                }
            }
            return null;
        }
    }

    private static String normalizeInput(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() > 64) {
            text = text.substring(0, 64);
        }
        return text;
    }

    private static String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() > MAX_NAME_LENGTH) {
            text = text.substring(0, MAX_NAME_LENGTH);
        }
        return text;
    }

    private static String trimMessage(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (text.length() > 512) {
            text = text.substring(0, 512);
        }
        return text;
    }

    private static final class Pair {
        private final UUID lowUuid;
        private final UUID highUuid;

        private Pair(UUID lowUuid, UUID highUuid) {
            this.lowUuid = lowUuid;
            this.highUuid = highUuid;
        }
    }
}
