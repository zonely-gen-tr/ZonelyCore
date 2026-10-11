package dev.zonely.whiteeffect.friends;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.hook.FriendsHook;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import dev.zonely.whiteeffect.utils.StringUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FriendManager implements Listener, FriendsHook.FriendAccess {

    private static final String DEFAULT_PREFIX = "&dFriends &8>> ";
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final Core plugin;
    private final FriendRepository repository;
    private final JSONParser parser = new JSONParser();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    private final Map<UUID, Set<UUID>> friendCache = new ConcurrentHashMap<UUID, Set<UUID>>();
    private final Map<UUID, MenuContext> openMenus = new ConcurrentHashMap<UUID, MenuContext>();
    private final Map<UUID, PendingRemoval> pendingRemovals = new ConcurrentHashMap<UUID, PendingRemoval>();
    private final String serverId;
    private final long removeConfirmWindowMs;
    private final int mailboxBatchSize;
    private final long mailboxPollTicks;
    private final int maxFriends;
    private final int maxOutgoingPending;
    private final int maxMessageLength;

    private int mailboxTaskId = -1;
    private final AtomicBoolean mailboxPolling = new AtomicBoolean();

    public FriendManager(Core plugin) {
        this.plugin = plugin;
        this.repository = new FriendRepository(plugin);
        String configured = plugin.getConfig().getString("chat-bridge.server-id", Bukkit.getServer().getName());
        if (configured == null || configured.trim().isEmpty()) {
            configured = Bukkit.getServer().getName();
        }
        this.serverId = configured == null || configured.trim().isEmpty() ? "default" : configured.trim();
        this.removeConfirmWindowMs = Math.max(5_000L,
                plugin.getConfig().getLong("friends.remove-confirm-seconds", 15L) * 1_000L);
        this.mailboxBatchSize = Math.max(16, plugin.getConfig().getInt("friends.mailbox.batch-size", 64));
        this.mailboxPollTicks = Math.max(20L, plugin.getConfig().getLong("friends.mailbox.poll-ticks", 40L));
        this.maxFriends = Math.max(0, plugin.getConfig().getInt("friends.limits.max-friends", 150));
        this.maxOutgoingPending = Math.max(0, plugin.getConfig().getInt("friends.limits.max-outgoing-pending", 25));
        this.maxMessageLength = Math.max(64, plugin.getConfig().getInt("friends.message.max-length", 256));
    }

    public void start() {
        repository.ensureTables();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        FriendsHook.bind(this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshFriendCache(player.getUniqueId());
            notifyPendingRequests(player);
        }

        mailboxTaskId = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                pollMailbox();
            }
        }, mailboxPollTicks, mailboxPollTicks).getTaskId();
    }

    public void shutdown() {
        if (mailboxTaskId != -1) {
            Bukkit.getScheduler().cancelTask(mailboxTaskId);
            mailboxTaskId = -1;
        }
        HandlerList.unregisterAll(this);
        FriendsHook.bind(FriendsHook.EMPTY);
        repository.shutdown();
        friendCache.clear();
        openMenus.clear();
        pendingRemovals.clear();
    }

    public void openFriendsMenu(final Player player, final int requestedPage) {
        if (player == null) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        repository.listFriends(uuid).thenCombine(repository.countIncomingRequests(uuid), (friends, pendingCount) -> {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    buildFriendsMenu(player, friends, pendingCount, requestedPage);
                }
            });
            return null;
        });
    }

    public void openRequestsMenu(final Player player, final int requestedPage) {
        if (player == null) {
            return;
        }
        repository.listIncomingRequests(player.getUniqueId()).thenAccept(requests -> Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                buildRequestsMenu(player, requests, requestedPage);
            }
        }));
    }

    public void sendFriendRequest(final Player sender, final String targetInput) {
        if (sender == null) {
            return;
        }
        final String input = sanitizeInput(targetInput);
        if (input.isEmpty()) {
            send(sender, "friends.request.usage", "{prefix}&cKullan\u0131m: /friends add <oyuncu>", "prefix", prefix(sender));
            return;
        }

        final FriendIdentity senderIdentity = new FriendIdentity(sender.getUniqueId(), sender.getName());
        repository.countFriends(senderIdentity.getUuid())
                .thenCombine(repository.countOutgoingPendingRequests(senderIdentity.getUuid()),
                        (friendCount, pendingCount) -> new int[]{friendCount, pendingCount})
                .thenAccept(counts -> {
                    if (maxFriends > 0 && counts[0] >= maxFriends) {
                        runSync(() -> send(sender,
                                "friends.request.limit.max-friends",
                                "{prefix}&cArkada\u015Fl\u0131k s\u0131n\u0131r\u0131na ula\u015Ft\u0131n (&f{limit}&c).",
                                "prefix", prefix(sender),
                                "limit", Integer.toString(maxFriends)));
                        return;
                    }
                    if (maxOutgoingPending > 0 && counts[1] >= maxOutgoingPending) {
                        runSync(() -> send(sender,
                                "friends.request.limit.max-pending",
                                "{prefix}&cBekleyen istek s\u0131n\u0131r\u0131na ula\u015Ft\u0131n (&f{limit}&c).",
                                "prefix", prefix(sender),
                                "limit", Integer.toString(maxOutgoingPending)));
                        return;
                    }

                    resolveTargetIdentity(input).thenAccept(target -> {
                        if (target == null) {
                            runSync(() -> send(sender,
                                    "friends.request.not-found",
                                    "{prefix}&cOyuncu bulunamad\u0131.",
                                    "prefix", prefix(sender)));
                            return;
                        }
                        if (target.getUuid().equals(sender.getUniqueId())) {
                            runSync(() -> send(sender,
                                    "friends.request.self",
                                    "{prefix}&cKendine istek g\u00F6nderemezsin.",
                                    "prefix", prefix(sender)));
                            return;
                        }

                        repository.createRequest(senderIdentity, target)
                                .thenAccept(result -> handleRequestCreateResult(sender, senderIdentity, target, result));
                    });
                });
    }

    public void respondRequest(final Player player, final String requesterInput, final boolean accept) {
        if (player == null) {
            return;
        }
        final String input = sanitizeInput(requesterInput);
        if (input.isEmpty()) {
            send(player,
                    "friends.request.respond.usage",
                    "{prefix}&cKullan\u0131m: /friends {action} <oyuncu>",
                    "prefix", prefix(player),
                    "action", accept ? "accept" : "reject");
            return;
        }

        repository.findIncomingRequest(player.getUniqueId(), input).thenAccept(request -> {
            if (request == null) {
                runSync(() -> send(player,
                        "friends.request.respond.not-found",
                        "{prefix}&cBekleyen bir istek bulunamad\u0131.",
                        "prefix", prefix(player)));
                return;
            }

            if (accept) {
                repository.acceptRequest(request).thenAccept(success -> {
                    if (!success) {
                        runSync(() -> send(player,
                                "friends.request.accept.failed",
                                "{prefix}&c\u0130stek kabul edilemedi.",
                                "prefix", prefix(player)));
                        return;
                    }
                    addFriendCache(request.getRequesterUuid(), request.getTargetUuid());
                    runSync(() -> {
                        send(player,
                                "friends.request.accept.success",
                                "{prefix}&a{player} art\u0131k arkada\u015F\u0131n.",
                                "prefix", prefix(player),
                                "player", request.getRequesterName());
                        Player requesterOnline = Bukkit.getPlayer(request.getRequesterUuid());
                        if (requesterOnline != null && requesterOnline.isOnline()) {
                            send(requesterOnline,
                                    "friends.request.accept.received",
                                    "{prefix}&a{player} arkada\u015Fl\u0131k iste\u011Fini kabul etti.",
                                    "prefix", prefix(requesterOnline),
                                    "player", player.getName());
                        }
                    });

                    FriendIdentity accepter = new FriendIdentity(player.getUniqueId(), player.getName());
                    FriendIdentity requester = new FriendIdentity(request.getRequesterUuid(), request.getRequesterName());
                    sendMailboxIfNeeded(requester.getUuid(), requester.getName(), FriendMailboxKind.FRIEND_ACCEPTED,
                            payload(accepter, null));
                });
            } else {
                repository.rejectRequest(request).thenAccept(success -> {
                    if (!success) {
                        runSync(() -> send(player,
                                "friends.request.reject.failed",
                                "{prefix}&c\u0130stek reddedilemedi.",
                                "prefix", prefix(player)));
                        return;
                    }
                    runSync(() -> send(player,
                            "friends.request.reject.success",
                            "{prefix}&e{player} iste\u011Fi reddedildi.",
                            "prefix", prefix(player),
                            "player", request.getRequesterName()));

                    FriendIdentity rejector = new FriendIdentity(player.getUniqueId(), player.getName());
                    sendMailboxIfNeeded(request.getRequesterUuid(), request.getRequesterName(), FriendMailboxKind.FRIEND_REJECTED,
                            payload(rejector, null));
                });
            }
        });
    }

    public void removeFriend(Player player, String targetInput, boolean confirm, boolean cancel) {
        if (player == null) {
            return;
        }

        final UUID playerId = player.getUniqueId();
        if (cancel) {
            pendingRemovals.remove(playerId);
            send(player,
                    "friends.remove.cancelled",
                    "{prefix}&eSilme i\u015Flemi iptal edildi.",
                    "prefix", prefix(player));
            return;
        }

        final String input = sanitizeInput(targetInput);
        if (input.isEmpty()) {
            send(player,
                    "friends.remove.usage",
                    "{prefix}&cKullan\u0131m: /friends remove <oyuncu>",
                    "prefix", prefix(player));
            return;
        }

        repository.resolveFriendOf(playerId, input).thenAccept(friend -> {
            if (friend == null) {
                runSync(() -> send(player,
                        "friends.remove.not-friend",
                        "{prefix}&cBu oyuncu arkada\u015F listende de\u011Fil.",
                        "prefix", prefix(player)));
                return;
            }

            if (!confirm) {
                PendingRemoval pending = new PendingRemoval(friend, System.currentTimeMillis() + removeConfirmWindowMs);
                pendingRemovals.put(playerId, pending);
                runSync(() -> sendRemovalConfirmation(player, friend));
                return;
            }

            PendingRemoval pending = pendingRemovals.get(playerId);
            if (pending == null || System.currentTimeMillis() > pending.expiresAt
                    || !pending.friend.getUuid().equals(friend.getUuid())) {
                runSync(() -> send(player,
                        "friends.remove.confirm-expired",
                        "{prefix}&cOnay s\u00FCresi doldu. Tekrar dene.",
                        "prefix", prefix(player)));
                return;
            }

            pendingRemovals.remove(playerId);
            repository.removeFriend(playerId, friend.getUuid()).thenAccept(success -> {
                if (!success) {
                    runSync(() -> send(player,
                            "friends.remove.failed",
                            "{prefix}&cArkada\u015F silinemedi.",
                            "prefix", prefix(player)));
                    return;
                }

                removeFriendCache(playerId, friend.getUuid());
                runSync(() -> {
                    send(player,
                            "friends.remove.success",
                            "{prefix}&e{player} arkada\u015F listenden \u00E7\u0131kar\u0131ld\u0131.",
                            "prefix", prefix(player),
                            "player", friend.getName());

                    Player friendOnline = Bukkit.getPlayer(friend.getUuid());
                    if (friendOnline != null && friendOnline.isOnline()) {
                        send(friendOnline,
                                "friends.remove.notify",
                                "{prefix}&e{player} seni arkada\u015F listesinden \u00E7\u0131kard\u0131.",
                                "prefix", prefix(friendOnline),
                                "player", player.getName());
                    }
                });
            });
        });
    }

    public void sendDirectMessage(Player sender, String targetInput, String content) {
        if (sender == null) {
            return;
        }
        String input = sanitizeInput(targetInput);
        String message = trimMessage(content);

        if (input.isEmpty() || message.isEmpty()) {
            send(sender,
                    "friends.message.usage",
                    "{prefix}&cKullan\u0131m: /friends msg <oyuncu> <mesaj>",
                    "prefix", prefix(sender));
            return;
        }

        repository.resolveFriendOf(sender.getUniqueId(), input).thenAccept(friend -> {
            if (friend == null) {
                runSync(() -> send(sender,
                        "friends.message.not-friend",
                        "{prefix}&cBu oyuncu arkada\u015F listende de\u011Fil.",
                        "prefix", prefix(sender)));
                return;
            }

            FriendIdentity senderIdentity = new FriendIdentity(sender.getUniqueId(), sender.getName());
            repository.saveMessage(senderIdentity, friend, "DIRECT", message, serverId);

            runSync(() -> send(sender,
                    "friends.message.sent",
                    "{prefix}&f[{target}] &7{message}",
                    "prefix", prefix(sender),
                    "target", friend.getName(),
                    "message", message));

            Player target = Bukkit.getPlayer(friend.getUuid());
            if (target != null && target.isOnline()) {
                runSync(() -> sendIncomingMessage(target, senderIdentity.getName(), message, false));
            } else {
                sendMailboxIfNeeded(friend.getUuid(), friend.getName(), FriendMailboxKind.FRIEND_MESSAGE,
                        payload(senderIdentity, message));
            }
        });
    }

    public void sendAnnouncement(Player sender, String content) {
        if (sender == null) {
            return;
        }
        String message = trimMessage(content);
        if (message.isEmpty()) {
            send(sender,
                    "friends.announce.usage",
                    "{prefix}&cKullan\u0131m: /friends duyuru <mesaj>",
                    "prefix", prefix(sender));
            return;
        }

        repository.listFriends(sender.getUniqueId()).thenAccept(friends -> {
            if (friends.isEmpty()) {
                runSync(() -> send(sender,
                        "friends.announce.empty",
                        "{prefix}&cDuyuru g\u00F6nderecek arkada\u015F\u0131n yok.",
                        "prefix", prefix(sender)));
                return;
            }

            FriendIdentity senderIdentity = new FriendIdentity(sender.getUniqueId(), sender.getName());
            int delivered = 0;
            for (FriendEntry entry : friends) {
                FriendIdentity targetIdentity = entry.getIdentity();
                repository.saveMessage(senderIdentity, targetIdentity, "ANNOUNCE", message, serverId);
                Player target = Bukkit.getPlayer(targetIdentity.getUuid());
                if (target != null && target.isOnline()) {
                    final Player onlineTarget = target;
                    runSync(() -> sendIncomingMessage(onlineTarget, senderIdentity.getName(), message, true));
                    delivered++;
                } else {
                    sendMailboxIfNeeded(targetIdentity.getUuid(), targetIdentity.getName(), FriendMailboxKind.FRIEND_ANNOUNCE,
                            payload(senderIdentity, message));
                }
            }

            final int deliveredNow = delivered;
            runSync(() -> send(sender,
                    "friends.announce.sent",
                    "{prefix}&aDuyuru {count} arkada\u015Fa iletildi (\u00E7evrimi\u00E7i teslim: {online}).",
                    "prefix", prefix(sender),
                    "count", Integer.toString(friends.size()),
                    "online", Integer.toString(deliveredNow)));
        });
    }

    public FriendRepository getRepository() {
        return repository;
    }

    @Override
    public boolean isFriend(String playerName, String friendName) {
        UUID playerUuid = findOnlineUuid(playerName);
        UUID friendUuid = findOnlineUuid(friendName);
        if (playerUuid == null || friendUuid == null) {
            return false;
        }
        Set<UUID> friends = friendCache.get(playerUuid);
        return friends != null && friends.contains(friendUuid);
    }

    @Override
    public boolean isBlacklisted(String playerName, String blacklistedName) {
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        refreshFriendCache(player.getUniqueId());
        notifyPendingRequests(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        friendCache.remove(uuid);
        openMenus.remove(uuid);
        pendingRemovals.remove(uuid);
    }

    @EventHandler
    public void onCommandAlias(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null) {
            return;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.equals("/arkadas") || lower.startsWith("/arkadas ")
                || lower.equals("/arkada\u015F") || lower.startsWith("/arkada\u015F ")) {
            String command = raw.startsWith("/") ? raw.substring(1) : raw;
            String[] split = command.split(" ", 2);
            String tail = split.length > 1 ? " " + split[1] : "";
            event.setMessage("/friends" + tail);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        MenuContext context = openMenus.get(player.getUniqueId());
        if (context == null) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (context.inventory == null || !context.inventory.equals(top)) {
            return;
        }

        event.setCancelled(true);
        event.setResult(Event.Result.DENY);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }

        if (context.type == MenuType.FRIENDS) {
            handleFriendsMenuClick(player, context, rawSlot, event.getClick());
        } else if (context.type == MenuType.REQUESTS) {
            handleRequestsMenuClick(player, context, rawSlot, event.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        MenuContext context = openMenus.get(player.getUniqueId());
        if (context == null) {
            return;
        }
        if (context.inventory == null || !context.inventory.equals(event.getView().getTopInventory())) {
            return;
        }
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        MenuContext context = openMenus.get(player.getUniqueId());
        if (context != null && context.inventory != null && context.inventory.equals(event.getInventory())) {
            openMenus.remove(player.getUniqueId());
        }
    }

    private void pollMailbox() {
        List<UUID> online = new ArrayList<UUID>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
        }
        if (online.isEmpty()) {
            return;
        }

        // Avoid piling up more DB requests if an earlier mailbox poll is still active.
        if (!mailboxPolling.compareAndSet(false, true)) {
            return;
        }
        repository.consumeMailbox(online, mailboxBatchSize).whenComplete((entries, error) -> {
            mailboxPolling.set(false);
            if (error != null || !plugin.isEnabled() || entries == null || entries.isEmpty()) {
                return;
            }
            runSync(() -> deliverMailbox(entries));
        });
    }

    private void deliverMailbox(List<FriendMailboxEntry> entries) {
        for (FriendMailboxEntry entry : entries) {
            Player target = Bukkit.getPlayer(entry.getTargetUuid());
            if (target == null || !target.isOnline()) {
                continue;
            }

            JSONObject object = parsePayload(entry.getPayload());
            String senderName = readString(object, "sender_name", "Bilinmeyen");
            String message = readString(object, "message", "");

            if (FriendMailboxKind.FRIEND_REQUEST.name().equalsIgnoreCase(entry.getKind())) {
                UUID senderUuid = parseUuid(readString(object, "sender_uuid", ""));
                if (senderUuid != null) {
                    sendRequestAction(target, new FriendIdentity(senderUuid, senderName));
                }
            } else if (FriendMailboxKind.FRIEND_ACCEPTED.name().equalsIgnoreCase(entry.getKind())) {
                send(target,
                        "friends.mail.accepted",
                        "{prefix}&a{player} arkada\u015Fl\u0131k iste\u011Fini kabul etti.",
                        "prefix", prefix(target),
                        "player", senderName);
            } else if (FriendMailboxKind.FRIEND_REJECTED.name().equalsIgnoreCase(entry.getKind())) {
                send(target,
                        "friends.mail.rejected",
                        "{prefix}&c{player} arkada\u015Fl\u0131k iste\u011Fini reddetti.",
                        "prefix", prefix(target),
                        "player", senderName);
            } else if (FriendMailboxKind.FRIEND_ANNOUNCE.name().equalsIgnoreCase(entry.getKind())) {
                sendIncomingMessage(target, senderName, message, true);
            } else if (FriendMailboxKind.FRIEND_MESSAGE.name().equalsIgnoreCase(entry.getKind())) {
                sendIncomingMessage(target, senderName, message, false);
            }
        }
    }

    private void notifyPendingRequests(Player player) {
        repository.countIncomingRequests(player.getUniqueId()).thenAccept(count -> {
            if (count <= 0) {
                return;
            }
            runSync(() -> {
                send(player,
                        "friends.pending.reminder",
                        "{prefix}&e{count} bekleyen arkada\u015Fl\u0131k iste\u011Fin var. &7/friends requests",
                        "prefix", prefix(player),
                        "count", Integer.toString(count));
            });
        });
    }

    private void refreshFriendCache(UUID uuid) {
        if (uuid == null) {
            return;
        }
        repository.listFriends(uuid).thenAccept(entries -> {
            Set<UUID> set = new HashSet<UUID>();
            for (FriendEntry entry : entries) {
                set.add(entry.getIdentity().getUuid());
            }
            friendCache.put(uuid, set);
        });
    }

    private void addFriendCache(UUID first, UUID second) {
        friendCache.computeIfAbsent(first, key -> Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>())).add(second);
        friendCache.computeIfAbsent(second, key -> Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>())).add(first);
    }

    private void removeFriendCache(UUID first, UUID second) {
        Set<UUID> firstSet = friendCache.get(first);
        if (firstSet != null) {
            firstSet.remove(second);
        }
        Set<UUID> secondSet = friendCache.get(second);
        if (secondSet != null) {
            secondSet.remove(first);
        }
    }

    private CompletableFuture<FriendIdentity> resolveTargetIdentity(String input) {
        Player online = findOnlinePlayer(input);
        if (online != null) {
            return CompletableFuture.completedFuture(new FriendIdentity(online.getUniqueId(), online.getName()));
        }
        return repository.resolveIdentity(input);
    }

    private Player findOnlinePlayer(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(input);
        if (exact != null) {
            return exact;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(input)) {
                return online;
            }
        }
        return null;
    }

    private UUID findOnlineUuid(String name) {
        Player player = findOnlinePlayer(name);
        return player != null ? player.getUniqueId() : null;
    }

    private void handleRequestCreateResult(Player sender,
                                           FriendIdentity senderIdentity,
                                           FriendIdentity target,
                                           FriendRequestCreateResult result) {
        if (result == null) {
            runSync(() -> send(sender,
                    "friends.request.failed",
                    "{prefix}&c\u0130stek g\u00F6nderilemedi.",
                    "prefix", prefix(sender)));
            return;
        }

        switch (result.getStatus()) {
            case ALREADY_FRIENDS:
                runSync(() -> send(sender,
                        "friends.request.already-friend",
                        "{prefix}&eBu oyuncu zaten arkada\u015F\u0131n.",
                        "prefix", prefix(sender)));
                return;
            case ALREADY_PENDING:
                runSync(() -> send(sender,
                        "friends.request.already-pending",
                        "{prefix}&eBu oyuncuya zaten bekleyen bir istek var.",
                        "prefix", prefix(sender)));
                return;
            case AUTO_ACCEPTED:
                addFriendCache(senderIdentity.getUuid(), target.getUuid());
                runSync(() -> {
                    send(sender,
                            "friends.request.auto-accepted",
                            "{prefix}&aKar\u015F\u0131l\u0131kl\u0131 istek bulundu, {player} ile otomatik arkada\u015F oldun.",
                            "prefix", prefix(sender),
                            "player", target.getName());

                    Player targetOnline = Bukkit.getPlayer(target.getUuid());
                    if (targetOnline != null && targetOnline.isOnline()) {
                        send(targetOnline,
                                "friends.request.auto-accepted.target",
                                "{prefix}&a{player} ile otomatik arkada\u015F oldun.",
                                "prefix", prefix(targetOnline),
                                "player", sender.getName());
                    }
                });
                sendMailboxIfNeeded(target.getUuid(), target.getName(), FriendMailboxKind.FRIEND_ACCEPTED,
                        payload(senderIdentity, null));
                return;
            case CREATED:
            default:
                runSync(() -> send(sender,
                        "friends.request.sent",
                        "{prefix}&a{player} oyuncusuna istek g\u00F6nderildi.",
                        "prefix", prefix(sender),
                        "player", target.getName()));

                Player targetOnline = Bukkit.getPlayer(target.getUuid());
                if (targetOnline != null && targetOnline.isOnline()) {
                    runSync(() -> sendRequestAction(targetOnline, senderIdentity));
                } else {
                    sendMailboxIfNeeded(target.getUuid(), target.getName(), FriendMailboxKind.FRIEND_REQUEST,
                            payload(senderIdentity, null));
                }
        }
    }

    private void sendMailboxIfNeeded(UUID targetUuid, String targetName, FriendMailboxKind kind, String payload) {
        Player targetOnline = Bukkit.getPlayer(targetUuid);
        if (targetOnline != null && targetOnline.isOnline()) {
            return;
        }
        repository.queueMailbox(targetUuid, targetName, kind, payload);
    }

    private void sendRequestAction(Player target, FriendIdentity sender) {
        String prefix = prefix(target);
        TextComponent base = new TextComponent(StringUtils.formatColors(
                LanguageManager.get(Profile.getProfile(target.getName()),
                        "friends.request.chat",
                        "{prefix}&d{player} &farkada\u015Fl\u0131k iste\u011Fi g\u00F6nderdi. ",
                        "prefix", prefix,
                        "player", sender.getName())));

        TextComponent accept = new TextComponent(StringUtils.formatColors("&a[Kabul]"));
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/friends accept " + sender.getName()));
        accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(StringUtils.formatColors("&7\u0130ste\u011Fi kabul et")).create()));

        TextComponent reject = new TextComponent(StringUtils.formatColors(" &c[Reddet]"));
        reject.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/friends reject " + sender.getName()));
        reject.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(StringUtils.formatColors("&7\u0130ste\u011Fi reddet")).create()));

        base.addExtra(accept);
        base.addExtra(reject);
        target.spigot().sendMessage(base);
    }

    private void sendRemovalConfirmation(Player player, FriendIdentity friend) {
        send(player,
                "friends.remove.confirm",
                "{prefix}&e{player} oyuncusunu silmek i\u00E7in onayla.",
                "prefix", prefix(player),
                "player", friend.getName());

        TextComponent confirm = new TextComponent(StringUtils.formatColors("&a[ONAYLA]"));
        confirm.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/friends remove " + friend.getName() + " confirm"));
        confirm.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(StringUtils.formatColors("&7Arkada\u015F\u0131 listeden sil")).create()));

        TextComponent cancel = new TextComponent(StringUtils.formatColors(" &c[IPTAL]"));
        cancel.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/friends remove " + friend.getName() + " cancel"));
        cancel.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(StringUtils.formatColors("&7\u0130\u015Flemi iptal et")).create()));

        TextComponent line = new TextComponent(" ");
        line.addExtra(confirm);
        line.addExtra(cancel);
        player.spigot().sendMessage(line);
    }

    private void sendIncomingMessage(Player target, String senderName, String message, boolean announce) {
        send(target,
                announce ? "friends.message.announce.receive" : "friends.message.receive",
                announce
                        ? "{prefix}&6[Duyuru] &e{sender}&7: &f{message}"
                        : "{prefix}&d{sender}&7: &f{message}",
                "prefix", prefix(target),
                "sender", senderName,
                "message", message);
    }

    private String payload(FriendIdentity sender, String message) {
        JSONObject object = new JSONObject();
        if (sender != null) {
            object.put("sender_uuid", sender.getUuid().toString());
            object.put("sender_name", sender.getName());
        }
        if (message != null) {
            object.put("message", trimMessage(message));
        }
        return object.toJSONString();
    }

    private JSONObject parsePayload(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new JSONObject();
        }
        try {
            Object parsed = parser.parse(raw);
            if (parsed instanceof JSONObject) {
                return (JSONObject) parsed;
            }
        } catch (Exception ignored) {
        }
        return new JSONObject();
    }

    private String readString(JSONObject object, String key, String def) {
        if (object == null) {
            return def;
        }
        Object value = object.get(key);
        if (value == null) {
            return def;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? def : text;
    }

    private void buildFriendsMenu(Player player, List<FriendEntry> friends, int pendingCount, int requestedPage) {
        int pageSize = CONTENT_SLOTS.length;
        int maxPage = Math.max(1, (friends.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage - 1));
        int from = page * pageSize;
        int to = Math.min(from + pageSize, friends.size());

        Inventory inventory = Bukkit.createInventory(null, 54,
                text(player, "friends.menu.friends.title",
                        "&8Friends Menu &7({page}/{max_page})",
                        "page", Integer.toString(page + 1),
                        "max_page", Integer.toString(maxPage)));
        fillBackground(inventory);

        Map<Integer, FriendEntry> friendSlots = new HashMap<Integer, FriendEntry>();
        int slotIndex = 0;
        for (int i = from; i < to; i++) {
            FriendEntry entry = friends.get(i);
            int slot = CONTENT_SLOTS[slotIndex++];
            friendSlots.put(slot, entry);
            inventory.setItem(slot, buildFriendHead(player, entry));
        }

        if (page > 0) {
            inventory.setItem(45, simpleItem(Material.ARROW,
                    text(player, "friends.menu.common.prev-page.name", "&ePrevious Page"),
                    textList(player, "friends.menu.common.prev-page.lore",
                            Arrays.asList("&7Page {page}"),
                            "page", Integer.toString(page))));
        }
        if (page + 1 < maxPage) {
            inventory.setItem(53, simpleItem(Material.ARROW,
                    text(player, "friends.menu.common.next-page.name", "&eNext Page"),
                    textList(player, "friends.menu.common.next-page.lore",
                            Arrays.asList("&7Page {page}"),
                            "page", Integer.toString(page + 2))));
        }

        inventory.setItem(46, simpleItem(resolveMaterial("BARRIER", Material.REDSTONE_BLOCK),
                text(player, "friends.menu.common.close.name", "&cClose Menu"),
                textList(player, "friends.menu.common.close.lore",
                        Arrays.asList("&7Closes this inventory."))));
        inventory.setItem(47, simpleItem(resolveMaterial("ANVIL", Material.PAPER),
                text(player, "friends.menu.friends.add.name", "&aAdd Friend"),
                textList(player, "friends.menu.friends.add.lore",
                        Arrays.asList("&7/friends add <player>", "&8Send a friend request."))));
        inventory.setItem(49, simpleItem(resolveMaterial("NETHER_STAR", Material.EMERALD),
                text(player, "friends.menu.friends.stats.name", "&bSocial Status"),
                textList(player, "friends.menu.friends.stats.lore",
                        Arrays.asList("&7Total friends: &f{total}", "&7Pending requests: &e{pending}", "&7On this page: &f{shown}"),
                        "total", Integer.toString(friends.size()),
                        "pending", Integer.toString(pendingCount),
                        "shown", Integer.toString(to - from))));
        inventory.setItem(51, simpleItem(resolveMaterial("PAPER", Material.PAPER),
                text(player, "friends.menu.friends.requests.name", "&ePending Requests &7(&f{count}&7)",
                        "count", Integer.toString(pendingCount)),
                textList(player, "friends.menu.friends.requests.lore",
                        Arrays.asList("&7Click to open incoming requests."))));
        inventory.setItem(52, simpleItem(resolveMaterial("BOOK", Material.BOOK),
                text(player, "friends.menu.friends.help.name", "&dQuick Help"),
                textList(player, "friends.menu.friends.help.lore",
                        Arrays.asList("&7Left click: message tip", "&7Right click: remove friend", "&7/friends msg <player> <message>"))));

        MenuContext context = MenuContext.friends(inventory, page, maxPage, friendSlots);
        openMenus.put(player.getUniqueId(), context);
        player.openInventory(inventory);
    }

    private void buildRequestsMenu(Player player, List<FriendRequest> requests, int requestedPage) {
        int pageSize = CONTENT_SLOTS.length;
        int maxPage = Math.max(1, (requests.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage - 1));
        int from = page * pageSize;
        int to = Math.min(from + pageSize, requests.size());

        Inventory inventory = Bukkit.createInventory(null, 54,
                text(player, "friends.menu.requests.title",
                        "&8Friend Requests &7({page}/{max_page})",
                        "page", Integer.toString(page + 1),
                        "max_page", Integer.toString(maxPage)));
        fillBackground(inventory);

        Map<Integer, FriendRequest> requestSlots = new HashMap<Integer, FriendRequest>();
        int slotIndex = 0;
        for (int i = from; i < to; i++) {
            FriendRequest request = requests.get(i);
            int slot = CONTENT_SLOTS[slotIndex++];
            requestSlots.put(slot, request);
            inventory.setItem(slot, buildRequestHead(player, request));
        }

        if (page > 0) {
            inventory.setItem(45, simpleItem(Material.ARROW,
                    text(player, "friends.menu.common.prev-page.name", "&ePrevious Page"),
                    textList(player, "friends.menu.common.prev-page.lore",
                            Arrays.asList("&7Page {page}"),
                            "page", Integer.toString(page))));
        }
        if (page + 1 < maxPage) {
            inventory.setItem(53, simpleItem(Material.ARROW,
                    text(player, "friends.menu.common.next-page.name", "&eNext Page"),
                    textList(player, "friends.menu.common.next-page.lore",
                            Arrays.asList("&7Page {page}"),
                            "page", Integer.toString(page + 2))));
        }

        inventory.setItem(46, simpleItem(resolveMaterial("BARRIER", Material.REDSTONE_BLOCK),
                text(player, "friends.menu.common.close.name", "&cClose Menu"),
                textList(player, "friends.menu.common.close.lore",
                        Arrays.asList("&7Closes this inventory."))));
        inventory.setItem(49, simpleItem(resolveMaterial("ARROW", Material.ARROW),
                text(player, "friends.menu.requests.back.name", "&aFriends Menu"),
                textList(player, "friends.menu.requests.back.lore",
                        Arrays.asList("&7Go back."))));
        inventory.setItem(50, simpleItem(resolveMaterial("BOOK", Material.BOOK),
                text(player, "friends.menu.requests.info.name", "&bHow it Works"),
                textList(player, "friends.menu.requests.info.lore",
                        Arrays.asList("&7Left click: accept", "&7Right click: reject", "&8Menu refreshes after action."))));

        MenuContext context = MenuContext.requests(inventory, page, maxPage, requestSlots);
        openMenus.put(player.getUniqueId(), context);
        player.openInventory(inventory);
    }

    private void handleFriendsMenuClick(Player player, MenuContext context, int slot, ClickType click) {
        FriendEntry entry = context.friendSlots.get(slot);
        if (entry != null) {
            if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                removeFriend(player, entry.getIdentity().getName(), false, false);
            } else {
                player.closeInventory();
                send(player,
                        "friends.menu.message-tip",
                        "{prefix}&7Mesaj g\u00F6ndermek i\u00E7in: &e/friends msg {player} <mesaj>",
                        "prefix", prefix(player),
                        "player", entry.getIdentity().getName());
            }
            return;
        }

        if (slot == 45 && context.page > 0) {
            openFriendsMenu(player, context.page - 1);
            return;
        }
        if (slot == 53 && context.page + 1 < context.maxPage) {
            openFriendsMenu(player, context.page + 1);
            return;
        }
        if (slot == 46) {
            player.closeInventory();
            return;
        }
        if (slot == 47) {
            player.closeInventory();
            send(player,
                    "friends.menu.add-tip",
                    "{prefix}&7Kullan\u0131m: &e/friends add <oyuncu>",
                    "prefix", prefix(player));
            return;
        }
        if (slot == 51) {
            openRequestsMenu(player, 0);
            return;
        }
        if (slot == 52) {
            send(player,
                    "friends.menu.help-tip",
                    "{prefix}&7Tip: &e/friends msg <player> <message> &7or &e/friends remove <player>",
                    "prefix", prefix(player));
        }
    }

    private void handleRequestsMenuClick(Player player, MenuContext context, int slot, ClickType click) {
        FriendRequest request = context.requestSlots.get(slot);
        if (request != null) {
            if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                respondRequest(player, request.getRequesterName(), false);
            } else {
                respondRequest(player, request.getRequesterName(), true);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> openRequestsMenu(player, context.page), 4L);
            return;
        }

        if (slot == 45 && context.page > 0) {
            openRequestsMenu(player, context.page - 1);
            return;
        }
        if (slot == 53 && context.page + 1 < context.maxPage) {
            openRequestsMenu(player, context.page + 1);
            return;
        }
        if (slot == 46) {
            player.closeInventory();
            return;
        }
        if (slot == 49) {
            openFriendsMenu(player, 0);
        }
    }

    private ItemStack buildFriendHead(Player viewer, FriendEntry entry) {
        ItemStack item = resolveHead(entry.getIdentity().getName());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String status = entry.isOnline()
                    ? text(viewer, "friends.menu.friend.status.online", "&a● &fOnline")
                    : text(viewer, "friends.menu.friend.status.offline", "&c● &fOffline");
            String icon = entry.isOnline()
                    ? text(viewer, "friends.menu.friend.icon.online", "&a✔ ")
                    : text(viewer, "friends.menu.friend.icon.offline", "&8✖ ");
            meta.setDisplayName(StringUtils.formatColors(icon + "&b" + entry.getIdentity().getName()));
            String server = entry.getServer() == null || entry.getServer().isEmpty() ? "-" : entry.getServer();
            List<String> lore = new ArrayList<String>();
            lore.add(text(viewer, "friends.menu.friend.lore.status", "&7Status: {status}", "status", status));
            lore.add(text(viewer, "friends.menu.friend.lore.server", "&7Server: &f{server}", "server", server));
            if (!entry.isOnline() && entry.getLastSeen() > 0L) {
                lore.add(text(viewer, "friends.menu.friend.lore.last-seen",
                        "&7Last Seen: &f{date}",
                        "date", dateFormat.format(new Date(entry.getLastSeen()))));
            }
            lore.add("");
            lore.add(text(viewer, "friends.menu.friend.lore.left-click", "&eLeft Click: &7Show message command"));
            lore.add(text(viewer, "friends.menu.friend.lore.right-click", "&cRight Click: &7Remove friend"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildRequestHead(Player viewer, FriendRequest request) {
        ItemStack item = resolveHead(request.getRequesterName());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(text(viewer, "friends.menu.request.display-name", "&e{player} &7(Request)",
                    "player", request.getRequesterName()));
            List<String> lore = new ArrayList<String>();
            lore.add(text(viewer, "friends.menu.request.lore.date",
                    "&7Request Date: &f{date}",
                    "date", dateFormat.format(new Date(request.getCreatedAt()))));
            lore.add("");
            lore.add(text(viewer, "friends.menu.request.lore.left-click", "&aLeft Click: &7Accept"));
            lore.add(text(viewer, "friends.menu.request.lore.right-click", "&cRight Click: &7Reject"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack resolveHead(String ownerName) {
        ItemStack item = MaterialResolver.createItemStack("SKULL_ITEM:3");
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta) {
            try {
                ((SkullMeta) meta).setOwner(ownerName);
                item.setItemMeta(meta);
            } catch (Throwable ignored) {
            }
        }
        return item;
    }

    private ItemStack simpleItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(StringUtils.formatColors(name));
            if (lore != null) {
                List<String> formatted = new ArrayList<String>();
                for (String line : lore) {
                    formatted.add(StringUtils.formatColors(line));
                }
                meta.setLore(formatted);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material resolveMaterial(String token, Material fallback) {
        Material material = MaterialResolver.resolveMaterial(token);
        return material == null ? fallback : material;
    }

    private void fillBackground(Inventory inventory) {
        ItemStack borderItem = simpleItem(resolveMaterial("STAINED_GLASS_PANE:15", Material.PAPER), "&r", Collections.<String>emptyList());
        ItemStack topBar = simpleItem(resolveMaterial("STAINED_GLASS_PANE:11", Material.PAPER), "&r", Collections.<String>emptyList());
        for (int i = 0; i < inventory.getSize(); i++) {
            boolean isBorder = i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8;
            if (isBorder) {
                inventory.setItem(i, borderItem);
            }
        }
        for (int i = 1; i <= 7; i++) {
            inventory.setItem(i, topBar);
        }
        for (int i = 46; i <= 52; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, simpleItem(resolveMaterial("STAINED_GLASS_PANE:7", Material.PAPER), "&r", Collections.<String>emptyList()));
            }
        }
    }

    private String prefix(Player player) {
        Profile profile = Profile.getProfile(player.getName());
        return LanguageManager.get(profile, "prefix.lobby", DEFAULT_PREFIX);
    }

    private String text(Player player, String key, String def, Object... placeholders) {
        Profile profile = player == null ? null : Profile.getProfile(player.getName());
        return LanguageManager.get(profile, key, def, placeholders);
    }

    private List<String> textList(Player player, String key, List<String> def, Object... placeholders) {
        Profile profile = player == null ? null : Profile.getProfile(player.getName());
        return LanguageManager.getList(profile, key, def, placeholders);
    }

    private void send(Player player, String key, String def, Object... placeholders) {
        LanguageManager.send(player, key, def, placeholders);
    }

    private void runSync(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private static String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        String value = input.trim();
        if (value.length() > 64) {
            value = value.substring(0, 64);
        }
        return value;
    }

    private String trimMessage(String input) {
        if (input == null) {
            return "";
        }
        String value = input.replace('\n', ' ').replace('\r', ' ').trim();
        if (value.length() > maxMessageLength) {
            value = value.substring(0, maxMessageLength);
        }
        return value;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private enum MenuType {
        FRIENDS,
        REQUESTS
    }

    private static final class PendingRemoval {
        private final FriendIdentity friend;
        private final long expiresAt;

        private PendingRemoval(FriendIdentity friend, long expiresAt) {
            this.friend = friend;
            this.expiresAt = expiresAt;
        }
    }

    private static final class MenuContext {
        private final MenuType type;
        private final Inventory inventory;
        private final int page;
        private final int maxPage;
        private final Map<Integer, FriendEntry> friendSlots;
        private final Map<Integer, FriendRequest> requestSlots;

        private MenuContext(MenuType type,
                            Inventory inventory,
                            int page,
                            int maxPage,
                            Map<Integer, FriendEntry> friendSlots,
                            Map<Integer, FriendRequest> requestSlots) {
            this.type = type;
            this.inventory = inventory;
            this.page = page;
            this.maxPage = maxPage;
            this.friendSlots = friendSlots;
            this.requestSlots = requestSlots;
        }

        private static MenuContext friends(Inventory inventory,
                                           int page,
                                           int maxPage,
                                           Map<Integer, FriendEntry> friendSlots) {
            return new MenuContext(MenuType.FRIENDS, inventory, page, maxPage, friendSlots,
                    Collections.<Integer, FriendRequest>emptyMap());
        }

        private static MenuContext requests(Inventory inventory,
                                            int page,
                                            int maxPage,
                                            Map<Integer, FriendRequest> requestSlots) {
            return new MenuContext(MenuType.REQUESTS, inventory, page, maxPage,
                    Collections.<Integer, FriendEntry>emptyMap(), requestSlots);
        }
    }
}
