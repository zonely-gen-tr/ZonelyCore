
package dev.zonely.whiteeffect.support;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.auth.AuthAccount;
import dev.zonely.whiteeffect.auth.AuthManager;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.CenteredMessage;
import dev.zonely.whiteeffect.utils.StringUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class SupportCommand implements CommandExecutor, TabCompleter {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    private static final String DEFAULT_PREFIX = "&dSupport &8>> ";
    private static final List<String> DEFAULT_HELP_PLAYER = Arrays.asList(
            "&6&m----------------------------",
            "&2",
            "&e&lSupport System",
            "&2",
            "&e/support create &2- &fOpen a new support ticket.",
            "&e/support reply <message> &2- &fReply to your ticket.",
            "&e/support view &2- &fShow your ticket conversation.",
            "&e/support close &2- &fClose your active ticket.",
            "&2",
            "&6&m----------------------------"
    );
    private static final List<String> DEFAULT_HELP_STAFF = Arrays.asList(
            "&e/support list [all] &2- &fOpen the staff ticket panel.",
            "&e/support view <id> &2- &fInspect a specific ticket.",
            "&e/support respond <id> <message> &2- &fSend a staff reply.",
            "&e/support close <id> &2- &fClose a ticket."
    );
    private static final List<String> DEFAULT_HELP_STAFF_BAN = Collections.singletonList(
            "&e/support ban <id> &2- &fPrevent new tickets from that user."
    );

    private final Core plugin;
    private final SupportManager manager;
    private final AuthManager authManager;

    public SupportCommand(Core plugin, SupportManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.authManager = plugin.getAuthManager();
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            String message = LanguageManager.get(
                    "support.command.console-only",
                    "&cOnly players can use this command.");
            sender.sendMessage(StringUtils.formatColors(message));
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create":
            case "new":
            case "open":
                handleCreate(player);
                break;
            case "reply":
            case "message":
                handleReply(player, args);
                break;
            case "close":
                handleClose(player, args);
                break;
            case "view":
            case "show":
            case "history":
                handleView(player, args);
                break;
            case "list":
            case "queue":
                handleList(player, args);
                break;
            case "respond":
            case "answer":
                handleRespond(player, args);
                break;
            case "ban":
                handleBan(player, args);
                break;
            default:
                sendHelp(player);
                break;
        }
        return true;
    }
    private void handleCreate(Player player) {
        manager.beginCreateFlow(player);
    }

    private void handleReply(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "support.command.reply.usage",
                    "{prefix}&cUsage: /support reply <message>",
                    "prefix", prefix(player));
            return;
        }
        String message = joinArgs(args, 1);
        manager.replyAsPlayer(player, message)
                .thenRun(() -> runSync(() -> send(player,
                        "support.command.reply.sent",
                        "{prefix}&aYour reply has been sent. Support will respond soon.",
                        "prefix", prefix(player))))
                .exceptionally(ex -> {
                    runSync(() -> send(player,
                            "support.command.reply.error",
                            "{prefix}&cCould not send reply: {error}",
                            "prefix", prefix(player),
                            "error", getErrorMessage(ex)));
                    return null;
                });
    }

    private void handleClose(Player player, String[] args) {
        if (args.length >= 2 && player.hasPermission("zonely.support.staff")) {
            int ticketId = parseTicketId(args[1]);
            if (ticketId <= 0) {
                send(player, "support.command.error.invalid-id",
                        "{prefix}&cInvalid ticket id.",
                        "prefix", prefix(player));
                return;
            }
            manager.closeTicketAsStaff(ticketId)
                    .thenRun(() -> runSync(() -> send(player,
                            "support.command.close.staff.success",
                            "{prefix}&aTicket #{id} has been closed.",
                            "prefix", prefix(player),
                            "id", ticketId)))
                    .exceptionally(ex -> {
                        runSync(() -> send(player,
                                "support.command.close.staff.error",
                                "{prefix}&cUnable to close ticket: {error}",
                                "prefix", prefix(player),
                                "error", getErrorMessage(ex)));
                        return null;
                    });
            return;
        }
        manager.closeTicketAsPlayer(player)
                .thenRun(() -> runSync(() -> send(player,
                        "support.command.close.player.success",
                        "{prefix}&aYour ticket has been closed.",
                        "prefix", prefix(player))))
                .exceptionally(ex -> {
                    runSync(() -> send(player,
                            "support.command.close.player.error",
                            "{prefix}&cUnable to close ticket: {error}",
                            "prefix", prefix(player),
                            "error", getErrorMessage(ex)));
                    return null;
                });
    }

    private void handleView(Player player, String[] args) {
        CompletableFuture<Optional<SupportTicket>> ticketFuture;
        if (args.length >= 2 && player.hasPermission("zonely.support.staff")) {
            int ticketId = parseTicketId(args[1]);
            if (ticketId <= 0) {
                send(player, "support.command.error.invalid-id",
                        "{prefix}&cInvalid ticket id.",
                        "prefix", prefix(player));
                return;
            }
            ticketFuture = manager.loadTicket(ticketId);
        } else {
            ticketFuture = manager.findActiveTicket(player);
        }

        AuthAccount account = manager.getAccount(player);
        Integer playerUserId = account != null ? account.getId() : null;

        ticketFuture.thenCompose(optional -> {
            if (optional.isEmpty()) {
                runSync(() -> send(player,
                        "support.command.view.not-found",
                        "{prefix}&cNo support ticket found.",
                        "prefix", prefix(player)));
                return CompletableFuture.completedFuture(null);
            }
            SupportTicket ticket = optional.get();
            boolean ownsTicket = (playerUserId != null && playerUserId.equals(ticket.getUserId()))
                    || (!player.hasPermission("zonely.support.staff") && playerUserId == null && ticket.getUserId() == null);
            return manager.fetchConversation(ticket.getId(), 0)
                    .thenApply(messages -> new SupportView(ticket, messages, ownsTicket));
        }).thenAccept(view -> {
            if (view == null) {
                return;
            }
            if (view.ownsTicket()) {
                manager.getRepository().markTicketRead(view.getTicket().getId());
            }
            runSync(() -> sendConversation(player, view));
        }).exceptionally(ex -> {
            runSync(() -> send(player,
                    "support.command.view.error",
                    "{prefix}&cUnable to load ticket: {error}",
                    "prefix", prefix(player),
                    "error", getErrorMessage(ex)));
            return null;
        });
    }

    private void handleList(Player player, String[] args) {
        if (!player.hasPermission("zonely.support.staff")) {
            send(player, "support.command.no-permission",
                    "{prefix}&cYou do not have permission to do that.",
                    "prefix", prefix(player));
            return;
        }
        boolean includeClosed = args.length >= 2 && args[1].equalsIgnoreCase("all");
        manager.openStaffPanel(player, includeClosed);
    }

    private void handleRespond(Player player, String[] args) {
        if (!player.hasPermission("zonely.support.staff")) {
            send(player, "support.command.no-permission",
                    "{prefix}&cYou do not have permission to do that.",
                    "prefix", prefix(player));
            return;
        }
        if (args.length < 3) {
            send(player, "support.command.respond.usage",
                    "{prefix}&cUsage: /support respond <id> <message>",
                    "prefix", prefix(player));
            return;
        }
        int ticketId = parseTicketId(args[1]);
        if (ticketId <= 0) {
            send(player, "support.command.error.invalid-id",
                    "{prefix}&cInvalid ticket id.",
                    "prefix", prefix(player));
            return;
        }
        String message = joinArgs(args, 2);
        manager.respondAsStaff(player, ticketId, message)
                .thenRun(() -> runSync(() -> send(player,
                        "support.command.respond.success",
                        "{prefix}&aReply sent for ticket #{id}.",
                        "prefix", prefix(player),
                        "id", ticketId)))
                .exceptionally(ex -> {
                    runSync(() -> send(player,
                            "support.command.respond.error",
                            "{prefix}&cCould not send reply: {error}",
                            "prefix", prefix(player),
                            "error", getErrorMessage(ex)));
                    return null;
                });
    }

    private void handleBan(Player player, String[] args) {
        if (!player.hasPermission("zonely.support.ban")) {
            send(player, "support.command.no-permission",
                    "{prefix}&cYou do not have permission to do that.",
                    "prefix", prefix(player));
            return;
        }
        if (args.length < 2) {
            send(player, "support.command.ban.usage",
                    "{prefix}&cUsage: /support ban <id>",
                    "prefix", prefix(player));
            return;
        }
        int ticketId = parseTicketId(args[1]);
        if (ticketId <= 0) {
            send(player, "support.command.error.invalid-id",
                    "{prefix}&cInvalid ticket id.",
                    "prefix", prefix(player));
            return;
        }
        manager.banTicket(player, ticketId)
                .thenRun(() -> runSync(() -> send(player,
                        "support.command.ban.success",
                        "{prefix}&aTicket #{id} is now banned from creating new requests.",
                        "prefix", prefix(player),
                        "id", ticketId)))
                .exceptionally(ex -> {
                    runSync(() -> send(player,
                            "support.command.ban.error",
                            "{prefix}&cUnable to apply ban: {error}",
                            "prefix", prefix(player),
                            "error", getErrorMessage(ex)));
                    return null;
                });
    }
    private void sendConversation(Player player, SupportView view) {
        SupportTicket ticket = view.getTicket();
        String title = ticket.getTitle();
        if (title == null || title.isBlank()) {
            title = translate(player,
                    "support.command.view.title-unknown",
                    "No title");
        }
        send(player, "support.command.view.header",
                "{prefix}&dSupport #{id} &7- &f{title}",
                "prefix", prefix(player),
                "id", ticket.getId(),
                "title", title);
        send(player, "support.command.view.status",
                "{prefix}&7Status: &f{status} &8| &7Created: &f{created}",
                "prefix", prefix(player),
                "status", ticket.getStatus().getLegacyLabel(),
                "created", DATE_FORMAT.format(ticket.getCreatedAt()));
        if (ticket.getInitialMessage() != null && !ticket.getInitialMessage().isEmpty()) {
            send(player, "support.command.view.initial",
                    "{prefix}&6Initial message: &f{message}",
                    "prefix", prefix(player),
                    "message", ticket.getInitialMessage());
        }
        if (view.getMessages().isEmpty()) {
            send(player, "support.command.view.no-messages",
                    "{prefix}&7No replies yet.",
                    "prefix", prefix(player));
        } else {
            for (SupportMessage msg : view.getMessages()) {
                send(player, "support.command.view.message",
                        "{prefix}&3{author}&7: &f{message}",
                        "prefix", prefix(player),
                        "author", formatAuthor(player, msg),
                        "message", msg.getMessage());
            }
        }
        if (!ticket.isClosed()) {
            TextComponent reply = new TextComponent(StringUtils.formatColors(
                    translate(player,
                            "support.command.view.reply.label",
                            "&a[Send Reply]")));
            reply.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/support reply "));
            reply.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(StringUtils.formatColors(
                            translate(player,
                                    "support.command.view.reply.hover",
                                    "&7Write a reply to support."))).create()));
            player.spigot().sendMessage(reply);
        }
    }

    private void sendTicketSummary(Player staff, SupportTicket ticket) {
        send(staff, "support.command.staff.summary",
                "{prefix}&d#{id} &7[{status}&7] &f{title}",
                "prefix", prefix(staff),
                "id", ticket.getId(),
                "status", ticket.getStatus().getLegacyLabel(),
                "title", ticket.getTitle() == null ? "" : ticket.getTitle());
    }

    private String formatAuthor(Player viewer, SupportMessage msg) {
        String name = msg.getAuthorName();
        if (msg.isFromCustomer()) {
            String display = name == null || name.isEmpty()
                    ? translate(viewer, "support.command.view.author.customer-missing", "Customer")
                    : name;
            return translate(viewer, "support.command.view.author.customer", "&e{display}", "display", display);
        }
        String display = name == null || name.isEmpty()
                ? translate(viewer, "support.command.view.author.staff-missing", "Support")
                : name;
        return translate(viewer, "support.command.view.author.staff", "&b{display}", "display", display);
    }

    private void sendHelp(Player player) {
        String prefixValue = prefix(player);
        List<String> lines = new ArrayList<>(resolveList(player, "support.command.help.centered.player", DEFAULT_HELP_PLAYER, "prefix", prefixValue));
        if (player.hasPermission("zonely.support.staff")) {
            String borderLine = removeTrailingBorder(lines);
            String spacerLine = removeTrailingSpacer(lines);
            lines.addAll(resolveList(player, "support.command.help.centered.staff", DEFAULT_HELP_STAFF, "prefix", prefixValue));
            if (player.hasPermission("zonely.support.ban")) {
                lines.addAll(resolveList(player, "support.command.help.centered.staff-ban", DEFAULT_HELP_STAFF_BAN, "prefix", prefixValue));
            }
            if (borderLine != null) {
                lines.add(spacerLine != null ? spacerLine : "&2");
                lines.add(borderLine);
            } else if (spacerLine != null) {
                lines.add(spacerLine);
            }
        }
        sendCenteredLines(player, lines);
    }

    private int parseTicketId(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String prefix(Player player) {
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

    private String joinArgs(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private void runSync(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private void sendCenteredLines(Player player, List<String> lines) {
        for (String line : lines) {
            player.sendMessage(CenteredMessage.generate(line));
        }
    }

    private List<String> resolveList(Player player, String key, List<String> defaults, Object... placeholders) {
        return LanguageManager.getList(getProfile(player), key, defaults, placeholders);
    }

    private String removeTrailingBorder(List<String> lines) {
        if (!lines.isEmpty()) {
            String last = lines.get(lines.size() - 1);
            if (last.contains("----------------")) {
                lines.remove(lines.size() - 1);
                return last;
            }
        }
        return null;
    }

    private String removeTrailingSpacer(List<String> lines) {
        if (!lines.isEmpty()) {
            String last = lines.get(lines.size() - 1);
            String stripped = last.replaceAll("&.", "").trim();
            if (stripped.isEmpty()) {
                lines.remove(lines.size() - 1);
                return last;
            }
        }
        return null;
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

    private Profile getProfile(Player player) {
        return player == null ? null : Profile.getProfile(player.getName());
    }

    private String getErrorMessage(Throwable throwable) {
        Throwable cause = throwable instanceof java.util.concurrent.CompletionException
                && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
        String message = cause.getMessage();
        return message == null || message.isEmpty() ? cause.getClass().getSimpleName() : message;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        Player player = (Player) sender;
        if (args.length == 1) {
            List<String> base = new ArrayList<>();
            base.add("create");
            base.add("reply");
            base.add("view");
            base.add("close");
            if (player.hasPermission("zonely.support.staff")) {
                base.add("list");
                base.add("respond");
                base.add("ban");
            }
            return base;
        }
        if (args.length == 2 && player.hasPermission("zonely.support.staff")) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("view") || sub.equals("respond") || sub.equals("close") || sub.equals("ban")) {
                return Collections.singletonList("<id>");
            }
            if (sub.equals("list")) {
                return Arrays.asList("all");
            }
        }
        return Collections.emptyList();
    }

    private static final class SupportView {
        private final SupportTicket ticket;
        private final List<SupportMessage> messages;
        private final boolean ownsTicket;

        private SupportView(SupportTicket ticket, List<SupportMessage> messages, boolean ownsTicket) {
            this.ticket = ticket;
            this.messages = messages;
            this.ownsTicket = ownsTicket;
        }

        private SupportTicket getTicket() {
            return ticket;
        }

        private List<SupportMessage> getMessages() {
            return messages;
        }

        private boolean ownsTicket() {
            return ownsTicket;
        }
    }
}
