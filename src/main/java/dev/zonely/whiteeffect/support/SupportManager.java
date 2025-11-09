
package dev.zonely.whiteeffect.support;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.auth.AuthAccount;
import dev.zonely.whiteeffect.auth.AuthManager;
import dev.zonely.whiteeffect.auth.AuthRepository;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.MaterialResolver;
import dev.zonely.whiteeffect.utils.StringUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class SupportManager implements Listener {

    private static final Duration STAFF_ALERT_COOLDOWN = Duration.ofSeconds(45);
    private static final Duration PLAYER_REMINDER_COOLDOWN = Duration.ofSeconds(60);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final Material EMPTY_PLACEHOLDER = resolveCobweb();
    private static final String DEFAULT_PREFIX = "&dSupport &8>> ";
    private static final int STAFF_MENU_SIZE = 54;
    private static final int STAFF_CONTENT_START = 9;
    private static final int STAFF_CONTENT_CAPACITY = 36;
    private static final int STAFF_PREVIOUS_SLOT_DEFAULT = 45;
    private static final int STAFF_NEXT_SLOT_DEFAULT = 53;
    private static final int STAFF_CREATE_SLOT_DEFAULT = 49;

    private final Core plugin;
    private final AuthManager authManager;
    private final AuthRepository authRepository;
    private final SupportRepository repository;
    private final int configuredDefaultCategoryId;

    private final Map<UUID, SupportPlayerSession> playerSessions = new ConcurrentHashMap<>();
    private final Map<UUID, SupportStaffSession> staffSessions = new ConcurrentHashMap<>();
    private final Map<UUID, MenuContext> openMenus = new ConcurrentHashMap<>();
    private final Map<UUID, ChatFlow> chatFlows = new ConcurrentHashMap<>();

    private volatile Integer cachedCategoryId;
    private volatile List<SupportCategory> cachedCategories = Collections.emptyList();
    private int pollTaskId = -1;

    public SupportManager(Core plugin, int configuredDefaultCategoryId, AuthManager authManager, AuthRepository authRepository) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.authRepository = authRepository;
        this.repository = new SupportRepository(plugin);
        this.configuredDefaultCategoryId = configuredDefaultCategoryId;
    }
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        pollTaskId = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(plugin, this::poll, 20L * 10, 20L * 10)
                .getTaskId();

        for (Player player : Bukkit.getOnlinePlayers()) {
            handlePlayerJoin(player);
        }
    }

    public void shutdown() {
        if (pollTaskId != -1) {
            Bukkit.getScheduler().cancelTask(pollTaskId);
            pollTaskId = -1;
        }
        repository.shutdown();
        playerSessions.clear();
        staffSessions.clear();
        openMenus.clear();
        chatFlows.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        handlePlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uniqueId = event.getPlayer().getUniqueId();
        playerSessions.remove(uniqueId);
        staffSessions.remove(uniqueId);
        openMenus.remove(uniqueId);
        chatFlows.remove(uniqueId);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        MenuContext context = openMenus.get(player.getUniqueId());
        if (context == null) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (context.inventory != null && !context.matches(top)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }

        event.setCancelled(true);
        switch (context.type) {
            case CATEGORY_SELECT:
                handleCategoryMenuClick(player, context, rawSlot);
                break;
            case STAFF_TICKET_LIST:
                handleStaffListClick(player, context, rawSlot);
                break;
            case STAFF_TICKET_DETAIL:
                handleStaffDetailClick(player, context, rawSlot);
                break;
            default:
                break;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        MenuContext context = openMenus.get(player.getUniqueId());
        if (context == null || !context.matches(event.getInventory())) {
            return;
        }
        openMenus.remove(player.getUniqueId());
        if (context.type == MenuType.STAFF_TICKET_DETAIL) {
            SupportStaffSession session = staffSessions.get(player.getUniqueId());
            if (session != null) {
                session.setActiveTicketId(-1);
            }
        }
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ChatFlow flow = chatFlows.get(player.getUniqueId());
        if (flow == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        if (message.equalsIgnoreCase("iptal") || message.equalsIgnoreCase("cancel")) {
            chatFlows.remove(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin,
                    () -> send(player, "support.general.cancelled",
                            "{prefix}&eAction cancelled.",
                            "prefix", getPrefix(player)));
            return;
        }

        switch (flow.type) {
            case PLAYER_TITLE:
                if (message.length() < 3) {
                    Bukkit.getScheduler().runTask(plugin,
                            () -> send(player, "support.create.title-too-short",
                                    "{prefix}&cPlease enter a longer title (minimum 3 characters).",
                                    "prefix", getPrefix(player)));
                    return;
                }
                flow.title = message;
                flow.type = ChatFlowType.PLAYER_MESSAGE;
                Bukkit.getScheduler().runTask(plugin,
                        () -> send(player, "support.create.message-prompt",
                                "{prefix}&7Please write the ticket message. Type 'cancel' to abort.",
                                "prefix", getPrefix(player)));
                break;
            case PLAYER_MESSAGE:
                if (message.length() < 3) {
                    Bukkit.getScheduler().runTask(plugin,
                            () -> send(player, "support.create.message-too-short",
                                    "{prefix}&cPlease enter a longer message (minimum 3 characters).",
                                    "prefix", getPrefix(player)));
                    return;
                }
                chatFlows.remove(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> submitNewTicket(player, flow, message));
                break;
            case STAFF_RESPONSE:
                chatFlows.remove(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin,
                        () -> submitStaffResponse(player, flow.ticketId, message, flow.includeClosed));
                break;
            default:
                chatFlows.remove(player.getUniqueId());
                break;
        }
    }

    public void beginCreateFlow(Player player) {
        cancelChatFlow(player, null);
        send(player, "support.create.menu-open",
                "{prefix}&7Select a category from the menu. Type 'cancel' to abort.",
                "prefix", getPrefix(player));
        openCategoryMenu(player);
    }

    public CompletableFuture<Optional<SupportTicket>> findActiveTicket(Player player) {
        AuthAccount account = getAccount(player);
        if (account == null || account.getId() == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return repository.findActiveTicketByUserId(account.getId());
    }
    public CompletableFuture<SupportTicket> createTicket(Player player,
                                                         int requestedCategoryId,
                                                         String title,
                                                         String message) {
        AuthAccount account = getAccount(player);
        if (account == null || account.getId() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    translate(player, "support.error.account-missing",
                            "Linked web account could not be found.")));
        }

        CompletableFuture<Integer> categoryFuture = requestedCategoryId > 0
                ? CompletableFuture.completedFuture(requestedCategoryId)
                : resolveCategoryId();

        String baseTitle = title == null || title.isBlank()
                ? translate(player, "support.create.default-title",
                "{player} - Minecraft Support",
                "player", player.getName())
                : title.trim();
        String cleanedTitle = ChatColor.stripColor(StringUtils.formatColors(baseTitle));
        String cleanedMessage = message == null ? "" : message.trim();

        return categoryFuture.thenCompose(categoryId ->
                repository.findActiveTicketByUserId(account.getId()).thenCompose(existing -> {
                    if (existing.isPresent() && !existing.get().isClosed()) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException(
                                        translate(player,
                                                "support.error.ticket-already-open",
                                                "You already have an open support ticket (#{id}).",
                                                "id", existing.get().getId())));
                    }
                    String ip = player.getAddress() != null
                            ? player.getAddress().getAddress().getHostAddress()
                            : "0.0.0.0";
                    return repository.createTicket(account.getId(), ip, cleanedTitle, cleanedMessage, categoryId)
                            .thenCompose(ticket -> repository.getLastMessageId(ticket.getId()).thenApply(lastId -> {
                                SupportPlayerSession session = new SupportPlayerSession(ticket, lastId);
                                playerSessions.put(player.getUniqueId(), session);
                                notifyStaffAboutTicket(ticket, cleanedMessage);
                                return ticket;
                            }));
                }));
    }

    public CompletableFuture<Void> replyAsPlayer(Player player, String message) {
        AuthAccount account = getAccount(player);
        if (account == null || account.getId() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    translate(player, "support.error.account-missing",
                            "Linked web account could not be found.")));
        }
        return ensurePlayerSession(player).thenCompose(session ->
                repository.addCustomerMessage(session.getTicket().getId(), account.getId(), message)
                        .thenCompose(inserted ->
                                repository.getLastMessageId(session.getTicket().getId()).thenAccept(lastId -> {
                                    session.setLastKnownMessageId(lastId);
                                    session.setTicket(session.getTicket()
                                            .withStatus(SupportStatus.OPEN, false, inserted.getCreatedAt()));
                                    session.setLastReminderAt(Instant.now());
                                    notifyStaffAboutTicket(session.getTicket(), message);
                                })));
    }

    public CompletableFuture<Void> closeTicketAsPlayer(Player player) {
        return ensurePlayerSession(player).thenCompose(session ->
                repository.closeTicket(session.getTicket().getId())
                        .thenRun(() -> playerSessions.remove(player.getUniqueId())));
    }

    public CompletableFuture<List<SupportTicket>> listTicketsForStaff(boolean includeClosed, int limit) {
        List<SupportStatus> statuses = includeClosed
                ? Arrays.asList(SupportStatus.OPEN, SupportStatus.ANSWERED,
                SupportStatus.AWAITING_CUSTOMER, SupportStatus.CLOSED)
                : Arrays.asList(SupportStatus.OPEN, SupportStatus.AWAITING_CUSTOMER, SupportStatus.ANSWERED);
        return repository.listTicketsByStatuses(statuses, limit);
    }

    public CompletableFuture<List<SupportMessage>> fetchConversation(int ticketId, int fromMessageId) {
        return repository.fetchMessagesAfter(ticketId, fromMessageId);
    }

    public CompletableFuture<Void> respondAsStaff(Player staff, int ticketId, String message) {
        AuthAccount account = getAccount(staff);
        if (account == null || account.getId() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    translate(staff, "support.error.account-missing",
                            "Linked web account could not be found.")));
        }
        return repository.addStaffMessage(ticketId, account.getId(), message)
                .thenCompose(inserted -> repository.getLastMessageId(ticketId).thenAccept(lastId -> {
                    SupportPlayerSession owningPlayer = findSessionByTicket(ticketId);
                    if (owningPlayer != null) {
                        owningPlayer.setLastKnownMessageId(lastId);
                        owningPlayer.setTicket(
                                owningPlayer.getTicket().withStatus(SupportStatus.ANSWERED, false, inserted.getCreatedAt()));
                    }
                    notifyCustomerAboutStaffResponse(ticketId);
                }));
    }

    public CompletableFuture<Void> closeTicketAsStaff(int ticketId) {
        return repository.closeTicket(ticketId);
    }

    public CompletableFuture<Void> banTicket(Player staff, int ticketId) {
        if (!staff.hasPermission("zonely.support.ban")) {
            return CompletableFuture.failedFuture(new IllegalStateException("Bu islemi yapmaya yetkin yok."));
        }
        return repository.banTicket(ticketId);
    }

    public CompletableFuture<Boolean> isTicketBanned(int ticketId) {
        return repository.isTicketBanned(ticketId);
    }

    public CompletableFuture<Optional<SupportTicket>> loadTicket(int ticketId) {
        return repository.loadTicket(ticketId);
    }

    public void openStaffPanel(Player staff, boolean includeClosed) {
        openStaffPanel(staff, includeClosed, 0);
    }

    public void openStaffPanel(Player staff, boolean includeClosed, int page) {
        List<SupportStatus> statuses = includeClosed
                ? Arrays.asList(SupportStatus.OPEN, SupportStatus.ANSWERED,
                SupportStatus.AWAITING_CUSTOMER, SupportStatus.CLOSED)
                : Arrays.asList(SupportStatus.OPEN, SupportStatus.ANSWERED, SupportStatus.AWAITING_CUSTOMER);

        CompletableFuture<List<SupportTicket>> ticketsFuture = repository.listTicketsByStatuses(statuses, 0);
        CompletableFuture<List<SupportCategory>> categoriesFuture = repository.listCategories();

        CompletableFuture.allOf(ticketsFuture, categoriesFuture).thenAccept(ignored -> {
            List<SupportTicket> tickets = ticketsFuture.join();
            List<SupportCategory> categories = categoriesFuture.join();
            if (!categories.isEmpty()) {
                cachedCategories = new ArrayList<>(categories);
            }
            int pageCount = Math.max(1, (int) Math.ceil(tickets.size() / (double) STAFF_CONTENT_CAPACITY));
            int clampedPage = Math.max(0, Math.min(page, pageCount - 1));
            Bukkit.getScheduler().runTask(plugin,
                    () -> buildStaffPanel(staff, tickets, categories, includeClosed, clampedPage, pageCount));
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "[Support] Unable to load staff panel", ex);
            Bukkit.getScheduler().runTask(plugin,
                    () -> send(staff, "support.staff.panel.load-fail",
                            "{prefix}&cSupport panel could not be opened. Please try again.",
                            "prefix", getPrefix(staff)));
            return null;
        });
    }

    public void openStaffTicketDetail(Player staff, int ticketId, boolean includeClosed) {
        openStaffTicketDetail(staff, ticketId, includeClosed, 0, 1);
    }

    public void openStaffTicketDetail(Player staff, int ticketId, boolean includeClosed, int page, int pageCount) {
        CompletableFuture<Optional<SupportTicket>> ticketFuture = loadTicket(ticketId);
        CompletableFuture<List<SupportCategory>> categoriesFuture = repository.listCategories();

        CompletableFuture.allOf(ticketFuture, categoriesFuture).thenAccept(ignored -> {
            Optional<SupportTicket> ticketOptional = ticketFuture.join();
            List<SupportCategory> categories = categoriesFuture.join();
            if (!categories.isEmpty()) {
                cachedCategories = new ArrayList<>(categories);
            }
            Bukkit.getScheduler().runTask(plugin,
                    () -> buildStaffTicketDetail(staff, ticketOptional, includeClosed, page, pageCount));
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "[Support] Unable to load ticket detail", ex);
            Bukkit.getScheduler().runTask(plugin,
                    () -> send(staff, "support.staff.detail.load-fail",
                            "{prefix}&cTicket details could not be loaded. Please try again.",
                            "prefix", getPrefix(staff)));
            return null;
        });
    }
    private void buildStaffPanel(Player staff,
                                 List<SupportTicket> tickets,
                                 List<SupportCategory> categories,
                                 boolean includeClosed,
                                 int page,
                                 int pageCount) {
        if (!staff.isOnline()) {
            return;
        }

        SupportStaffSession session = staffSessions.computeIfAbsent(
                staff.getUniqueId(), id -> new SupportStaffSession());
        session.setActiveTicketId(-1);
        session.setActiveTicketPage(page);
        session.setActivePageCount(pageCount);

        String menuTitleKey = includeClosed
                ? "support.staff.panel.menu-title-all"
                : "support.staff.panel.menu-title";
        String menuTitle = translate(staff, menuTitleKey,
                includeClosed
                        ? ChatColor.DARK_PURPLE + "Support Tickets (All)"
                        : ChatColor.DARK_PURPLE + "Support Tickets");

        Inventory inventory = Bukkit.createInventory(null, STAFF_MENU_SIZE, menuTitle);

        Map<Integer, Integer> mapping = new ConcurrentHashMap<>();
        Map<Integer, String> categoryNames = new ConcurrentHashMap<>();
        for (SupportCategory category : categories) {
            categoryNames.put(category.getId(), category.getName());
        }

        int startIndex = page * STAFF_CONTENT_CAPACITY;
        int endIndex = Math.min(startIndex + STAFF_CONTENT_CAPACITY, tickets.size());
        if (tickets.isEmpty()) {
            inventory.setItem(22, createMenuItem(
                    EMPTY_PLACEHOLDER,
                    translate(staff, "support.staff.panel.empty.name",
                            ChatColor.GOLD + "No tickets to show"),
                    translateList(staff, "support.staff.panel.empty.lore",
                            Collections.singletonList(ChatColor.GRAY + "Waiting for new tickets."),
                            "prefix", getPrefix(staff))));
        } else {
            for (int index = startIndex; index < endIndex; index++) {
                SupportTicket ticket = tickets.get(index);
                int slot = STAFF_CONTENT_START + (index - startIndex);
                inventory.setItem(slot, createStaffTicketItem(staff, ticket, categoryNames));
                mapping.put(slot, ticket.getId());
            }
        }

        applyStaffNavigation(staff, inventory, includeClosed, page, pageCount);

        openMenus.put(staff.getUniqueId(), MenuContext.staffList(mapping, includeClosed, inventory, page, pageCount));
        staff.openInventory(inventory);
    }

    private void applyStaffNavigation(Player staff, Inventory inventory, boolean includeClosed, int page, int pageCount) {
        boolean hasPrev = page > 0;
        boolean hasNext = page + 1 < pageCount;

        int previousSlot = getConfiguredSlot("support.staff.panel.nav.previous.slot", STAFF_PREVIOUS_SLOT_DEFAULT);
        int nextSlot = getConfiguredSlot("support.staff.panel.nav.next.slot", STAFF_NEXT_SLOT_DEFAULT);
        int createSlot = getConfiguredSlot("support.staff.panel.create.slot", STAFF_CREATE_SLOT_DEFAULT);

        Material previousMaterial = getConfiguredMaterial("support.staff.panel.nav.previous.material", Material.BOOK);
        Material nextMaterial = getConfiguredMaterial("support.staff.panel.nav.next.material", Material.BOOK);
        Material createMaterial = getConfiguredMaterial("support.staff.panel.create.material",
                resolveMaterial("WRITABLE_BOOK", Material.BOOK));

        if (previousSlot >= 0) {
            inventory.setItem(previousSlot,
                    createNavigationItem(staff, false, hasPrev, page, pageCount, previousMaterial));
        }
        if (nextSlot >= 0) {
            inventory.setItem(nextSlot,
                    createNavigationItem(staff, true, hasNext, page, pageCount, nextMaterial));
        }
        if (createSlot >= 0) {
            inventory.setItem(createSlot, createCreateTicketItem(staff, createMaterial));
        }
    }

    private ItemStack createNavigationItem(Player staff,
                                           boolean forward,
                                           boolean enabled,
                                           int page,
                                           int pageCount,
                                           Material configuredMaterial) {
        int currentPage = page + 1;
        int targetPage = forward ? Math.min(pageCount, page + 2) : Math.max(1, page);
        String key = forward ? "support.staff.panel.nav.next" : "support.staff.panel.nav.previous";
        String name = translate(staff, key + ".name",
                forward ? ChatColor.GREEN + "Next Page" : ChatColor.YELLOW + "Previous Page",
                "current", currentPage,
                "target", targetPage,
                "pages", pageCount);
        List<String> lore;
        if (enabled) {
            lore = translateList(staff, key + ".lore",
                    Arrays.asList("&7Current: &f{current}/{pages}", "&7Target: &f{target}"),
                    "current", currentPage,
                    "target", targetPage,
                    "pages", pageCount);
        } else {
            lore = translateList(staff, key + ".disabled-lore",
                    Collections.singletonList("&8No more pages."),
                    "current", currentPage,
                    "target", targetPage,
                    "pages", pageCount);
        }
        Material material = configuredMaterial != null ? configuredMaterial : Material.BOOK;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(StringUtils.formatColors(name));
            List<String> formattedLore = new ArrayList<>();
            if (lore != null) {
                for (String line : lore) {
                    formattedLore.add(StringUtils.formatColors(line));
                }
            }
            meta.setLore(formattedLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCreateTicketItem(Player staff, Material material) {
        Material actual = material != null ? material : Material.BOOK;
        ItemStack icon = new ItemStack(actual);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(StringUtils.formatColors(translate(staff,
                    "support.staff.panel.create.name",
                    ChatColor.LIGHT_PURPLE + "Create Support Ticket")));
            List<String> lore = translateList(staff,
                    "support.staff.panel.create.lore",
                    Collections.singletonList("&7Start a new ticket for a player."));
            if (lore != null) {
                List<String> formatted = new ArrayList<>(lore.size());
                for (String line : lore) {
                    formatted.add(StringUtils.formatColors(line));
                }
                meta.setLore(formatted);
            }
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack createCreateTicketItem(Player staff) {
        return createCreateTicketItem(staff, resolveMaterial("WRITABLE_BOOK", Material.BOOK));
    }

    private void buildStaffTicketDetail(Player staff,
                                        Optional<SupportTicket> ticketOptional,
                                        boolean includeClosed,
                                        int page,
                                        int pageCount) {
        if (!staff.isOnline()) {
            return;
        }
        if (ticketOptional.isEmpty()) {
            send(staff, "support.staff.ticket-missing",
                    "{prefix}&cTicket could not be found.",
                    "prefix", getPrefix(staff));
            openStaffPanel(staff, includeClosed, page);
            return;
        }

        SupportTicket ticket = ticketOptional.get();
        String menuTitle = translate(staff, "support.staff.detail.menu-title",
                ChatColor.DARK_PURPLE + "Support #" + ticket.getId(),
                "id", ticket.getId());
        Inventory inventory = Bukkit.createInventory(null, 27, menuTitle);

        String categoryName = findCategoryById(ticket.getCategoryId())
                .map(SupportCategory::getName)
                .orElse("Unknown");
        String titleValue = ticket.getTitle() == null || ticket.getTitle().isEmpty()
                ? "(none)" : ticket.getTitle();

        inventory.setItem(10, createMenuItem(
                Material.PAPER,
                translate(staff, "support.staff.detail.info.name",
                        ChatColor.LIGHT_PURPLE + "Ticket Info"),
                translateList(staff, "support.staff.detail.info.lore",
                        Arrays.asList(
                                "&7Title: &f{title}",
                                "&7Category: &e{category}",
                                "&7Status: &b{status}",
                                "&7Created: &b{created}",
                                "&7Updated: &b{updated}",
                                "&7Player: &b{player}"),
                        "title", titleValue,
                        "category", categoryName,
                        "status", ticket.getStatus().getLegacyLabel(),
                        "created", formatTimestamp(ticket.getCreatedAt()),
                        "updated", formatTimestamp(ticket.getUpdatedAt()),
                        "player", getTicketOwnerName(ticket))));
        inventory.setItem(12, createMenuItem(
                resolveMaterial("BOOK_AND_QUILL", Material.PAPER),
                translate(staff, "support.staff.detail.messages.name",
                        ChatColor.AQUA + "View Messages"),
                translateList(staff, "support.staff.detail.messages.lore",
                        Collections.singletonList(ChatColor.YELLOW + "Open the conversation in chat."),
                        "prefix", getPrefix(staff))));
        inventory.setItem(14, createMenuItem(
                Material.FEATHER,
                translate(staff, "support.staff.detail.reply.name",
                        ChatColor.GREEN + "Send Reply"),
                translateList(staff, "support.staff.detail.reply.lore",
                        Collections.singletonList(ChatColor.YELLOW + "Write a response in chat."),
                        "prefix", getPrefix(staff))));
        inventory.setItem(16, createMenuItem(
                Material.BARRIER,
                translate(staff, "support.staff.detail.close.name",
                        ChatColor.RED + "Close Ticket"),
                translateList(staff, "support.staff.detail.close.lore",
                        Collections.singletonList(ChatColor.GRAY + "Close the ticket."),
                        "prefix", getPrefix(staff))));
        inventory.setItem(18, createMenuItem(
                Material.ARROW,
                translate(staff, "support.staff.detail.back.name",
                        ChatColor.GOLD + "Back"),
                translateList(staff, "support.staff.detail.back.lore",
                        Collections.singletonList(ChatColor.GRAY + "Return to the list."),
                        "prefix", getPrefix(staff))));
        inventory.setItem(22, createMenuItem(
                Material.ANVIL,
                translate(staff, "support.staff.detail.ban.name",
                        ChatColor.DARK_RED + "Ban User"),
                translateList(staff, "support.staff.detail.ban.lore",
                        Collections.singletonList(ChatColor.GRAY + "Prevent further tickets from this user."),
                        "prefix", getPrefix(staff))));

        openMenus.put(staff.getUniqueId(), MenuContext.staffDetail(ticket.getId(), includeClosed, inventory, page, pageCount));
        staff.openInventory(inventory);

        SupportStaffSession session = staffSessions.computeIfAbsent(
                staff.getUniqueId(), id -> new SupportStaffSession());
        session.setActiveTicketId(ticket.getId());
        session.setActiveTicketPage(page);
        session.setActivePageCount(pageCount);
    }

    private void handleCategoryMenuClick(Player player, MenuContext context, int slot) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (slot >= top.getSize()) {
            return;
        }
        if (slot == top.getSize() - 1) {
            openMenus.remove(player.getUniqueId());
            player.closeInventory();
            send(player, "support.general.cancelled",
                    "{prefix}&eAction cancelled.",
                    "prefix", getPrefix(player));
            return;
        }

        Integer categoryId = context.slotMapping.get(slot);
        if (categoryId == null) {
            return;
        }
        Optional<SupportCategory> category = findCategoryById(categoryId);
        if (category.isEmpty()) {
            openMenus.remove(player.getUniqueId());
            player.closeInventory();
            send(player, "support.create.category-missing",
                    "{prefix}&cThat category is no longer available.",
                    "prefix", getPrefix(player));
            return;
        }

        openMenus.remove(player.getUniqueId());
        player.closeInventory();
        startPlayerTitlePrompt(player, category.get());
    }

    private void handleStaffListClick(Player player, MenuContext context, int slot) {
        int previousSlot = getConfiguredSlot("support.staff.panel.nav.previous.slot", STAFF_PREVIOUS_SLOT_DEFAULT);
        int nextSlot = getConfiguredSlot("support.staff.panel.nav.next.slot", STAFF_NEXT_SLOT_DEFAULT);
        int createSlot = getConfiguredSlot("support.staff.panel.create.slot", STAFF_CREATE_SLOT_DEFAULT);

        if (slot == previousSlot) {
            if (context.page > 0) {
                openMenus.remove(player.getUniqueId());
                player.closeInventory();
                openStaffPanel(player, context.includeClosed, context.page - 1);
            }
            return;
        }
        if (slot == nextSlot) {
            if (context.page + 1 < context.pageCount) {
                openMenus.remove(player.getUniqueId());
                player.closeInventory();
                openStaffPanel(player, context.includeClosed, context.page + 1);
            }
            return;
        }
        if (slot == createSlot) {
            openMenus.remove(player.getUniqueId());
            player.closeInventory();
            player.performCommand("support create");
            return;
        }
        Integer ticketId = context.slotMapping.get(slot);
        if (ticketId == null) {
            return;
        }
        openMenus.remove(player.getUniqueId());
        openStaffTicketDetail(player, ticketId, context.includeClosed, context.page, context.pageCount);
    }

    private void handleStaffDetailClick(Player player, MenuContext context, int slot) {
        int ticketId = context.ticketId;
        switch (slot) {
            case 12:
                openMenus.remove(player.getUniqueId());
                player.closeInventory();
                player.performCommand("support view " + ticketId);
                break;
            case 14:
                openMenus.remove(player.getUniqueId());
                player.closeInventory();
                promptStaffResponse(player, ticketId, context.includeClosed);
                break;
            case 16:
                openMenus.remove(player.getUniqueId());
                player.closeInventory();
                submitCloseTicket(player, ticketId, context.includeClosed);
                break;
            case 18:
                openMenus.remove(player.getUniqueId());
                player.closeInventory();
                openStaffPanel(player, context.includeClosed, context.page);
                break;
            case 22:
                openMenus.remove(player.getUniqueId());
                player.closeInventory();
                submitBanTicket(player, ticketId, context.includeClosed);
                break;
            default:
                break;
        }
    }

    private void promptStaffResponse(Player staff, int ticketId, boolean includeClosed) {
        chatFlows.put(staff.getUniqueId(), ChatFlow.staff(ticketId, includeClosed));
        send(staff, "support.staff.response.prompt",
                "{prefix}&aType your response in chat. Use 'cancel' to abort.",
                "prefix", getPrefix(staff));
    }

    private void submitStaffResponse(Player staff, int ticketId, String message, boolean includeClosed) {
        String prefix = getPrefix(staff);
        send(staff, "support.staff.response.sending",
                "{prefix}&eSending response...",
                "prefix", prefix);
        respondAsStaff(staff, ticketId, message).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    send(staff, "support.staff.response.success",
                            "{prefix}&aResponse sent.",
                            "prefix", prefix);
                    SupportStaffSession session = staffSessions.get(staff.getUniqueId());
                    int page = session != null ? session.getActiveTicketPage() : 0;
                    int pageCount = session != null ? session.getActivePageCount() : 1;
                    openStaffTicketDetail(staff, ticketId, includeClosed, page, pageCount);
                })
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "[Support] Unable to send staff response", ex);
            Bukkit.getScheduler().runTask(plugin, () ->
                    send(staff, "support.staff.response.error",
                            "{prefix}&cResponse could not be delivered: {error}",
                            "prefix", prefix,
                            "error", getErrorMessage(ex)));
            return null;
        });
    }

    private void submitCloseTicket(Player staff, int ticketId, boolean includeClosed) {
        String prefix = getPrefix(staff);
        send(staff, "support.staff.close.sending",
                "{prefix}&eClosing ticket...",
                "prefix", prefix);
        closeTicketAsStaff(ticketId).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    send(staff, "support.staff.close.success",
                            "{prefix}&aTicket closed.",
                            "prefix", prefix);
                    SupportStaffSession session = staffSessions.get(staff.getUniqueId());
                    int page = session != null ? session.getActiveTicketPage() : 0;
                    openStaffPanel(staff, includeClosed, page);
                })
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "[Support] Unable to close ticket", ex);
            Bukkit.getScheduler().runTask(plugin, () ->
                    send(staff, "support.staff.close.error",
                            "{prefix}&cTicket could not be closed: {error}",
                            "prefix", prefix,
                            "error", getErrorMessage(ex)));
            return null;
        });
    }

    private void submitBanTicket(Player staff, int ticketId, boolean includeClosed) {
        String prefix = getPrefix(staff);
        send(staff, "support.staff.ban.sending",
                "{prefix}&eApplying ban...",
                "prefix", prefix);
        banTicket(staff, ticketId).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    send(staff, "support.staff.ban.success",
                            "{prefix}&aThe user can no longer open tickets.",
                            "prefix", prefix);
                    SupportStaffSession session = staffSessions.get(staff.getUniqueId());
                    int page = session != null ? session.getActiveTicketPage() : 0;
                    openStaffPanel(staff, includeClosed, page);
                })
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "[Support] Unable to ban ticket", ex);
            Bukkit.getScheduler().runTask(plugin, () ->
                    send(staff, "support.staff.ban.error",
                            "{prefix}&cBan failed: {error}",
                            "prefix", prefix,
                            "error", getErrorMessage(ex)));
            return null;
        });
    }

    private void submitNewTicket(Player player, ChatFlow flow, String message) {
        String prefix = getPrefix(player);
        send(player, "support.create.submitting",
                "{prefix}&eCreating your ticket...",
                "prefix", prefix);
        createTicket(player, flow.categoryId, flow.title, message).thenAccept(ticket ->
                Bukkit.getScheduler().runTask(plugin, () ->
                        send(player, "support.create.created",
                                "{prefix}&aTicket created (#{id}).",
                                "prefix", prefix,
                                "id", ticket.getId()))
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "[Support] Unable to create ticket", ex);
            Bukkit.getScheduler().runTask(plugin, () ->
                    send(player, "support.create.error",
                            "{prefix}&cTicket could not be created: {error}",
                            "prefix", prefix,
                            "error", getErrorMessage(ex)));
            return null;
        });
    }

    private ItemStack createStaffTicketItem(Player staff, SupportTicket ticket, Map<Integer, String> categoryNames) {
        Material material = getConfiguredMaterial("support.staff.panel.ticket.material", Material.PAPER);

        String titleValue = ticket.getTitle() == null ? "" : ticket.getTitle();
        String displayName = translate(staff, "support.staff.panel.ticket.name",
                ChatColor.LIGHT_PURPLE + "#" + ticket.getId() + ChatColor.WHITE + " {title}",
                "id", ticket.getId(),
                "title", titleValue);

        String categoryName = categoryNames.getOrDefault(ticket.getCategoryId(), "Unknown");
        Player ownerOnline = getPlayerByTicket(ticket.getId());
        String ownerStatus = ownerOnline != null
                ? translate(staff, "support.status.online", ChatColor.GREEN + "online")
                : translate(staff, "support.status.offline", ChatColor.RED + "offline");

        boolean staffActive = staffSessions.values().stream()
                .anyMatch(session -> session.getActiveTicketId() == ticket.getId());
        String staffState = staffActive
                ? translate(staff, "support.staff.panel.ticket.staff-active",
                ChatColor.GRAY + "Moderator: " + ChatColor.GREEN + "active")
                : "";

        String unread = ticket.isRead()
                ? ""
                : translate(staff, "support.staff.panel.ticket.unread",
                ChatColor.RED + "Unread staff responses!");

        List<String> lore = translateList(staff, "support.staff.panel.ticket.lore",
                Arrays.asList(
                        "&7Category: &e{category}",
                        "&7Player: &b{player} &7({player_status})",
                        "{staff_state}",
                        "&7Status: &b{status}",
                        "&7Updated: &b{updated}",
                        "{unread}",
                        "&eClick to view details."),
                "category", categoryName,
                "player", getTicketOwnerName(ticket),
                "player_status", ownerStatus,
                "staff_state", staffState,
                "status", ticket.getStatus().getLegacyLabel(),
                "updated", formatTimestamp(ticket.getUpdatedAt()),
                "unread", unread);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMenuItem(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private int getConfiguredSlot(String key, int defaultSlot) {
        String raw = LanguageManager.get(key, null);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultSlot;
        }
        raw = raw.trim();
        if (raw.equalsIgnoreCase("disabled") || raw.equalsIgnoreCase("-1")) {
            return -1;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                return -1;
            }
            if (value >= STAFF_MENU_SIZE) {
                return defaultSlot;
            }
            return value;
        } catch (NumberFormatException ex) {
            return defaultSlot;
        }
    }

    private Material getConfiguredMaterial(String key, Material fallback) {
        String raw = LanguageManager.get(key, null);
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        Material material = MaterialResolver.resolveMaterial(raw.trim());
        return material != null ? material : fallback;
    }
    private static Material resolveCobweb() {
        Material material = Material.matchMaterial("COBWEB");
        if (material == null) {
            material = Material.matchMaterial("WEB");
        }
        return material != null ? material : Material.WEB;
    }

    private void openCategoryMenu(Player player) {
        repository.listCategories().thenAccept(categories -> {
            if (!categories.isEmpty()) {
                cachedCategories = new ArrayList<>(categories);
            }
            List<SupportCategory> effective = !categories.isEmpty() ? categories : cachedCategories;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (effective.isEmpty()) {
                    send(player, "support.create.categories-empty",
                            "{prefix}&cNo support categories are available.",
                            "prefix", getPrefix(player));
                    return;
                }
                int rows = Math.min(6, Math.max(1, (int) Math.ceil(effective.size() / 9.0)));
                int size = rows * 9;
                String title = translate(player, "support.create.menu.title",
                        ChatColor.DARK_PURPLE + "Support Categories");
                Inventory inventory = Bukkit.createInventory(null, size, title);
                Map<Integer, Integer> mapping = new ConcurrentHashMap<>();
                for (int index = 0; index < effective.size() && index < size; index++) {
                    SupportCategory category = effective.get(index);
                    String itemName = translate(player, "support.create.category-item.name",
                            ChatColor.AQUA + "{category}",
                            "category", category.getName());
                    List<String> lore = translateList(player, "support.create.category-item.lore",
                            Arrays.asList(
                                    ChatColor.GRAY + "Create a ticket in this category.",
                                    ChatColor.YELLOW + "Click to continue."),
                            "category", category.getName());
                    inventory.setItem(index, createMenuItem(
                            Material.BOOK,
                            itemName,
                            lore));
                    mapping.put(index, category.getId());
                }
                inventory.setItem(size - 1, createMenuItem(
                        Material.BARRIER,
                        translate(player, "support.create.cancel.name",
                                ChatColor.RED + "Cancel"),
                        translateList(player, "support.create.cancel.lore",
                                Collections.singletonList(ChatColor.GRAY + "Close the menu."),
                                "prefix", getPrefix(player))));
                openMenus.put(player.getUniqueId(), MenuContext.category(mapping, inventory));
                player.openInventory(inventory);
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "[Support] Unable to load categories", ex);
            Bukkit.getScheduler().runTask(plugin,
                    () -> send(player, "support.create.categories-load-fail",
                            "{prefix}&cUnable to load support categories. Please try again later.",
                            "prefix", getPrefix(player)));
            return null;
        });
    }

    private void startPlayerTitlePrompt(Player player, SupportCategory category) {
        chatFlows.put(player.getUniqueId(), ChatFlow.player(category));
        send(player, "support.create.category-selected",
                "{prefix}&aSelected category: &f{category}",
                "prefix", getPrefix(player),
                "category", category.getName());
        send(player, "support.create.title-prompt",
                "{prefix}&7Please write the ticket title. Type 'cancel' to abort.",
                "prefix", getPrefix(player));
    }

    private CompletableFuture<SupportPlayerSession> ensurePlayerSession(Player player) {
        SupportPlayerSession existing = playerSessions.get(player.getUniqueId());
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }
        return findActiveTicket(player).thenApply(optional -> {
            if (optional.isEmpty()) {
                throw new IllegalStateException(
                        translate(player,
                                "support.error.no-active-ticket",
                                "You do not have an active support ticket."));
            }
            SupportTicket ticket = optional.get();
            SupportPlayerSession session = new SupportPlayerSession(ticket, 0);
            playerSessions.put(player.getUniqueId(), session);
            return session;
        });
    }
    private CompletableFuture<Integer> resolveCategoryId() {
        if (configuredDefaultCategoryId > 0) {
            return CompletableFuture.completedFuture(configuredDefaultCategoryId);
        }
        Integer cached = cachedCategoryId;
        if (cached != null && cached > 0) {
            return CompletableFuture.completedFuture(cached);
        }
        return repository.resolveDefaultCategoryId().thenApply(id -> {
            int resolved = id > 0 ? id : 1;
            if (id <= 0) {
                plugin.getLogger().warning("[Support] No supportcategories rows found, defaulting to category id 1.");
            }
            cachedCategoryId = resolved;
            return resolved;
        });
    }

    private void poll() {
        try {
            pollPlayers();
            pollStaff();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "[Support] Poll loop error: " + ex.getMessage(), ex);
        }
    }

    private void pollPlayers() {
        if (playerSessions.isEmpty()) {
            return;
        }
        List<Map.Entry<UUID, SupportPlayerSession>> entries = new ArrayList<>(playerSessions.entrySet());
        for (Map.Entry<UUID, SupportPlayerSession> entry : entries) {
            UUID uuid = entry.getKey();
            SupportPlayerSession session = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            repository.fetchMessagesAfter(session.getTicket().getId(), session.getLastKnownMessageId())
                    .thenAccept(messages -> {
                        if (messages.isEmpty()) {
                            return;
                        }
                        List<SupportMessage> staffMessages = new ArrayList<>();
                        int maxId = session.getLastKnownMessageId();
                        for (SupportMessage message : messages) {
                            if (!message.isFromCustomer()) {
                                staffMessages.add(message);
                            }
                            if (message.getId() > maxId) {
                                maxId = message.getId();
                            }
                        }
                        if (!staffMessages.isEmpty()) {
                            int finalMaxId = maxId;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                String prefix = getPrefix(player);
                                for (SupportMessage msg : staffMessages) {
                                    String line = translate(player, "support.player.staff-message-line",
                                            "{prefix}&dSupport &8#{id} &7({author}): &f{text}",
                                            "prefix", prefix,
                                            "id", msg.getTicketId(),
                                            "author", msg.getAuthorName(),
                                            "text", msg.getMessage());
                                    player.sendMessage(line);
                                }
                                TextComponent reply = new TextComponent(translate(player,
                                        "support.player.reply-button.label",
                                        "{prefix}&aClick to continue [Write Message]",
                                        "prefix", prefix));
                                reply.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/support reply "));
                                reply.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        new ComponentBuilder(translate(player,
                                                "support.player.reply-button.hover",
                                                "&7Send a quick reply to support.")).create()));
                                player.spigot().sendMessage(reply);
                                session.setLastKnownMessageId(finalMaxId);
                                session.setLastReminderAt(Instant.now());
                            });
                        } else if (maxId > session.getLastKnownMessageId()) {
                            session.setLastKnownMessageId(maxId);
                        }
                    });
        }
    }

    private void pollStaff() {
        if (staffSessions.isEmpty()) {
            return;
        }
        repository.listTicketsByStatuses(
                Arrays.asList(SupportStatus.OPEN, SupportStatus.AWAITING_CUSTOMER),
                25
        ).thenAccept(tickets -> {
            if (tickets.isEmpty()) {
                return;
            }
            for (Map.Entry<UUID, SupportStaffSession> entry : staffSessions.entrySet()) {
                Player staff = Bukkit.getPlayer(entry.getKey());
                if (staff == null || !staff.isOnline() || !staff.hasPermission("zonely.support.staff")) {
                    continue;
                }
                SupportStaffSession session = entry.getValue();
                for (SupportTicket ticket : tickets) {
                    long lastAlert = session.getLastAlert(ticket.getId());
                    long now = System.currentTimeMillis();
                    if (now - lastAlert < STAFF_ALERT_COOLDOWN.toMillis()) {
                        continue;
                    }
                    repository.getLastMessageId(ticket.getId()).thenAccept(lastId -> {
                        int knownId = session.getKnownMessageId(ticket.getId());
                        if (lastId <= knownId) {
                            return;
                        }
                        session.setKnownMessageId(ticket.getId(), lastId);
                        session.setLastAlert(ticket.getId(), System.currentTimeMillis());
                        Bukkit.getScheduler().runTask(plugin,
                                () -> sendStaffTicketAlert(staff, ticket));
                    });
                }
            }
        });
    }

    private void handlePlayerJoin(Player player) {
        if (player.hasPermission("zonely.support.staff")) {
            staffSessions.computeIfAbsent(player.getUniqueId(), id -> new SupportStaffSession());
        }
        AuthAccount account = getAccount(player);
        if (account == null || account.getId() == null) {
            return;
        }
        repository.findActiveTicketByUserId(account.getId()).thenAccept(optional -> {
            if (optional.isEmpty()) {
                playerSessions.remove(player.getUniqueId());
                return;
            }
            SupportTicket ticket = optional.get();
            repository.getLastMessageId(ticket.getId()).thenAccept(lastId -> {
                SupportPlayerSession session = new SupportPlayerSession(ticket, lastId);
                playerSessions.put(player.getUniqueId(), session);
                if (!ticket.isRead() && ticket.getStatus() == SupportStatus.ANSWERED) {
                    Bukkit.getScheduler().runTask(plugin, () -> send(player,
                            "support.player.awaiting-response",
                            "{prefix}&aA new reply is available. Use /support view.",
                            "prefix", getPrefix(player)));
                }
            });
        });
    }

    private void sendStaffTicketAlert(Player staff, SupportTicket ticket) {
        String prefix = getPrefix(staff);
        TextComponent base = new TextComponent(translate(staff, "support.staff.alert.message",
                "{prefix}&dSupport &8#{id} &7({status}) ",
                "prefix", prefix,
                "id", ticket.getId(),
                "status", ticket.getStatus().getLegacyLabel()));

        addButton(base,
                translate(staff, "support.staff.alert.buttons.view.label", "&a[View]"),
                "/support view " + ticket.getId(),
                translate(staff, "support.staff.alert.buttons.view.hover", "&7View the ticket."));
        base.addExtra(space());
        addButton(base,
                translate(staff, "support.staff.alert.buttons.respond.label", "&b[Reply]"),
                "/support respond " + ticket.getId() + " ",
                translate(staff, "support.staff.alert.buttons.respond.hover", "&7Write a response."));
        base.addExtra(space());
        addButton(base,
                translate(staff, "support.staff.alert.buttons.close.label", "&c[Close]"),
                "/support close " + ticket.getId(),
                translate(staff, "support.staff.alert.buttons.close.hover", "&7Close the ticket."));
        if (staff.hasPermission("zonely.support.ban")) {
            base.addExtra(space());
            addButton(base,
                    translate(staff, "support.staff.alert.buttons.ban.label", "&4[Ban]"),
                    "/support ban " + ticket.getId(),
                    translate(staff, "support.staff.alert.buttons.ban.hover", "&7Prevent new tickets."));
        }
        staff.spigot().sendMessage(base);
    }

    private void notifyStaffAboutTicket(SupportTicket ticket, String preview) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (!staff.hasPermission("zonely.support.staff")) {
                    continue;
                }
                String message = translate(staff, "support.staff.broadcast",
                        "{prefix}&dSupport &8#{id} &7> &f{preview}",
                        "prefix", getPrefix(staff),
                        "id", ticket.getId(),
                        "preview", preview);
                staff.sendMessage(message);
                staffSessions.computeIfAbsent(staff.getUniqueId(), id -> new SupportStaffSession());
            }
        });
    }

    private void notifyCustomerAboutStaffResponse(int ticketId) {
        SupportPlayerSession session = findSessionByTicket(ticketId);
        if (session == null) {
            return;
        }
        if (Duration.between(session.getLastReminderAt(), Instant.now()).compareTo(PLAYER_REMINDER_COOLDOWN) < 0) {
            return;
        }
        Player player = getPlayerByTicket(ticketId);
        if (player == null) {
            return;
        }
        session.setLastReminderAt(Instant.now());
        Bukkit.getScheduler().runTask(plugin, () -> send(player,
                "support.player.staff-response-notice",
                "{prefix}&aSupport replied. Use /support view to read it.",
                "prefix", getPrefix(player)));
    }

    private Player getPlayerByTicket(int ticketId) {
        for (Map.Entry<UUID, SupportPlayerSession> entry : playerSessions.entrySet()) {
            if (entry.getValue().getTicket().getId() == ticketId) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    return player;
                }
            }
        }
        return null;
    }

    private SupportPlayerSession findSessionByTicket(int ticketId) {
        for (SupportPlayerSession session : playerSessions.values()) {
            if (session.getTicket().getId() == ticketId) {
                return session;
            }
        }
        return null;
    }

    private void addButton(TextComponent base, String label, String command, String hover) {
        TextComponent button = new TextComponent(StringUtils.formatColors(label));
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        button.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(StringUtils.formatColors(hover)).create()));
        base.addExtra(button);
    }

    private TextComponent space() {
        return new TextComponent(" ");
    }

    private Material resolveMaterial(String token, Material fallback) {
        Material material = MaterialResolver.resolveMaterial(token);
        return material != null ? material : fallback;
    }

    private String getPrefix(Player player) {
        Profile profile = getProfile(player);
        String configured = profile != null
                ? LanguageManager.get(profile, "support.prefix", null)
                : LanguageManager.get("support.prefix", null);
        if (configured != null && !configured.isEmpty()) {
            return StringUtils.formatColors(configured);
        }
        if (authManager != null) {
            return StringUtils.formatColors(authManager.getPrefix(player));
        }
        String value = profile != null
                ? LanguageManager.get(profile, "prefix.lobby", DEFAULT_PREFIX)
                : LanguageManager.get("prefix.lobby", DEFAULT_PREFIX);
        return StringUtils.formatColors(value);
    }

    AuthAccount getAccount(Player player) {
        if (authManager != null) {
            return authManager.findAccount(player.getName()).orElse(null);
        }
        if (authRepository != null) {
            return authRepository.findByUsername(player.getName()).orElse(null);
        }
        return null;
    }

    private Profile getProfile(Player player) {
        return player == null ? null : Profile.getProfile(player.getName());
    }

    private String translate(Player player, String key, String def, Object... placeholders) {
        return LanguageManager.get(getProfile(player), key, def, placeholders);
    }

    private List<String> translateList(Player player, String key, List<String> def, Object... placeholders) {
        return LanguageManager.getList(getProfile(player), key, def, placeholders);
    }

    private void send(Player player, String key, String def, Object... placeholders) {
        player.sendMessage(translate(player, key, def, placeholders));
    }

    public SupportRepository getRepository() {
        return repository;
    }

    public Map<UUID, SupportPlayerSession> getPlayerSessions() {
        return Collections.unmodifiableMap(playerSessions);
    }
    private String getTicketOwnerName(SupportTicket ticket) {
        if (ticket.getUserName() != null && !ticket.getUserName().isEmpty()) {
            return ticket.getUserName();
        }
        if (ticket.getUserId() != null) {
            return "ID#" + ticket.getUserId();
        }
        return "Guest";
    }

    private String formatTimestamp(java.sql.Timestamp timestamp) {
        if (timestamp == null) {
            return "-";
        }
        return DATE_FORMAT.format(timestamp.toInstant().atZone(ZoneId.systemDefault()));
    }

    private Optional<SupportCategory> findCategoryById(int categoryId) {
        List<SupportCategory> snapshot = cachedCategories;
        for (SupportCategory category : snapshot) {
            if (category.getId() == categoryId) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    private void cancelChatFlow(Player player, String feedback) {
        ChatFlow removed = chatFlows.remove(player.getUniqueId());
        if (removed != null && feedback != null && !feedback.isEmpty()) {
            player.sendMessage(StringUtils.formatColors(getPrefix(player) + feedback));
        }
    }

    private String getErrorMessage(Throwable throwable) {
        Throwable cause = throwable instanceof java.util.concurrent.CompletionException
                && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
        String message = cause.getMessage();
        return message == null || message.isEmpty() ? cause.getClass().getSimpleName() : message;
    }

    private enum MenuType {
        CATEGORY_SELECT,
        STAFF_TICKET_LIST,
        STAFF_TICKET_DETAIL
    }

    private static final class MenuContext {
        private final MenuType type;
        private final Map<Integer, Integer> slotMapping;
        private final int ticketId;
        private final boolean includeClosed;
        private final Inventory inventory;
        private final int page;
        private final int pageCount;

        private MenuContext(MenuType type,
                            Map<Integer, Integer> slotMapping,
                            int ticketId,
                            boolean includeClosed,
                            Inventory inventory,
                            int page,
                            int pageCount) {
            this.type = type;
            this.slotMapping = slotMapping;
            this.ticketId = ticketId;
            this.includeClosed = includeClosed;
            this.inventory = inventory;
            this.page = page;
            this.pageCount = pageCount;
        }

        static MenuContext category(Map<Integer, Integer> mapping, Inventory inventory) {
            return new MenuContext(MenuType.CATEGORY_SELECT, mapping, -1, false, inventory, 0, 1);
        }

        static MenuContext staffList(Map<Integer, Integer> mapping, boolean includeClosed, Inventory inventory, int page, int pageCount) {
            return new MenuContext(MenuType.STAFF_TICKET_LIST, mapping, -1, includeClosed, inventory, page, pageCount);
        }

        static MenuContext staffDetail(int ticketId, boolean includeClosed, Inventory inventory, int page, int pageCount) {
            return new MenuContext(MenuType.STAFF_TICKET_DETAIL, Collections.emptyMap(), ticketId, includeClosed, inventory, page, pageCount);
        }

        boolean matches(Inventory other) {
            return inventory != null && inventory.equals(other);
        }
    }

    private enum ChatFlowType {
        PLAYER_TITLE,
        PLAYER_MESSAGE,
        STAFF_RESPONSE
    }

    private static final class ChatFlow {
        private ChatFlowType type;
        private final int categoryId;
        private final int ticketId;
        private final boolean includeClosed;
        private String title;

        private ChatFlow(ChatFlowType type, int categoryId, int ticketId, boolean includeClosed) {
            this.type = type;
            this.categoryId = categoryId;
            this.ticketId = ticketId;
            this.includeClosed = includeClosed;
        }

        static ChatFlow player(SupportCategory category) {
            ChatFlow flow = new ChatFlow(ChatFlowType.PLAYER_TITLE, category.getId(), -1, false);
            flow.title = "";
            return flow;
        }

        static ChatFlow staff(int ticketId, boolean includeClosed) {
            return new ChatFlow(ChatFlowType.STAFF_RESPONSE, -1, ticketId, includeClosed);
        }
    }
}
