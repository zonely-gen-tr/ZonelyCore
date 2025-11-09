package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import dev.zonely.whiteeffect.utils.CenteredMessage;
import dev.zonely.whiteeffect.utils.CommandMessageUtils;
import dev.zonely.whiteeffect.utils.PunishmentManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class PunishCommand implements CommandExecutor, TabCompleter {

    private static final WConfig CONFIG = Core.getInstance().getConfig("config");
    private static final String DEFAULT_PREFIX = "";
    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "ban", "ipban", "hwidban",
            "mute", "ipmute", "hwidmute",
            "warning", "ipwarning", "hwidwarning",
            "unban", "unipban", "unhwidban",
            "unmute", "unipmute", "unhwidmute",
            "unwarning", "unipwarning", "unhwidwarning",
            "gecmis", "history", "yardim", "help");

    private static final List<String> DEFAULT_HELP_LINES = Arrays.asList(
            "&6&m----------------------------",
            "&2",
            "&e&lPunishment System",
            "&2",
            "&e/punish ban <player> [duration] [reason] &2- &fBan a player.",
            "&e/punish ipban <player|ip> [duration] [reason] &2- &fBan a player's IP.",
            "&e/punish hwidban <hwid> [duration] [reason] &2- &fBan a hardware ID.",
            "&e/punish mute <player> [duration] [reason] &2- &fMute a player.",
            "&e/punish warning <player> [reason] &2- &fSend a warning.",
            "&e/punish unban <player> &2- &fRemove a ban.",
            "&e/punish unmute <player> &2- &fRemove a mute.",
            "&e/punish unwarning <player> &2- &fRemove a warning.",
            "&e/punish history <player> &2- &fView the punishment history.",
            "&2",
            "&6&m----------------------------");

    private static final List<String> DEFAULT_RESULT_BAN = Arrays.asList(
            "&6&m----------------------------",
            "&2",
            "&e&lPunishment System",
            "&2",
            "&c{target} &fhas been &4banned&f from the network.",
            "&2",
            "&fDuration:&6 {duration}",
            "&fReason:&b {reason}",
            "&2",
            "&6&m----------------------------");

    private static final List<String> DEFAULT_RESULT_MUTE = Arrays.asList(
            "&6&m----------------------------",
            "&2",
            "&e&lPunishment System",
            "&2",
            "&c{target} &fhas been &6muted&f on the network.",
            "&2",
            "&fDuration:&6 {duration}",
            "&fReason:&b {reason}",
            "&2",
            "&6&m----------------------------");

    private static final List<String> DEFAULT_RESULT_WARNING = Arrays.asList(
            "&6&m----------------------------",
            "&2",
            "&e&lPunishment System",
            "&2",
            "&c{target} &fhas received a warning.",
            "&2",
            "&fReason:&b {reason}",
            "&2",
            "&6&m----------------------------");

    private static final List<String> DEFAULT_HISTORY_HEADER = Arrays.asList(
            "&2",
            "&6&m----- Punishment History ({page}/{pages}) -----");

    private static final List<String> DEFAULT_HISTORY_FOOTER = Arrays.asList(
            "&6&m---------------------------");

    private static final String DEFAULT_HISTORY_ENTRY =
            "&7{index}. &e{date} &7| &a{type} &7| &f{duration} &7| &f{reason} &7| &fExecutor: &e{executor}";
    private static final String DEFAULT_HISTORY_NEXT =
            "{prefix}&7Use &e/punish history {target} page <2-{pages}>&7 to view additional pages.";
    private static final String DEFAULT_HISTORY_EMPTY =
            "{prefix}&eNo punishment history was found for {target}.";

    private final PunishmentManager punishmentManager;

    public PunishCommand(PunishmentManager punishmentManager) {
        this.punishmentManager = punishmentManager;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!isEnabled()) {
            sendMessage(sender, "commands.punish.disabled", "{prefix}&cPunishment commands are disabled.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("yardim") || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "ban":
                if (!hasPermission(sender, "zonely.punish.ban") || !ensureActionAllowed(sender, PunishAction.BAN)) {
                    return true;
                }
                handleBan(sender, args);
                break;
            case "ipban":
                if (!hasPermission(sender, "zonely.punish.ban") || !ensureActionAllowed(sender, PunishAction.BAN)) {
                    return true;
                }
                handleIPBan(sender, args);
                break;
            case "hwidban":
                if (!hasPermission(sender, "zonely.punish.ban") || !ensureActionAllowed(sender, PunishAction.BAN)) {
                    return true;
                }
                handleHWIDBan(sender, args);
                break;
            case "unban":
                if (!hasPermission(sender, "zonely.punish.ban") || !ensureActionAllowed(sender, PunishAction.BAN)) {
                    return true;
                }
                handleUnban(sender, args, PunishmentManager.BanType.NORMAL, "commands.punish.success.unban.player",
                        "{prefix}&aRemoved the player ban for {target}.",
                        "commands.punish.error.unban.player",
                        "{prefix}&cCould not remove the player ban for {target}.");
                break;
            case "unipban":
                if (!hasPermission(sender, "zonely.punish.ban") || !ensureActionAllowed(sender, PunishAction.BAN)) {
                    return true;
                }
                handleUnban(sender, args, PunishmentManager.BanType.IP, "commands.punish.success.unban.ip",
                        "{prefix}&aRemoved the IP ban for {target}.",
                        "commands.punish.error.unban.ip",
                        "{prefix}&cCould not remove the IP ban for {target}.");
                break;
            case "unhwidban":
                if (!hasPermission(sender, "zonely.punish.ban") || !ensureActionAllowed(sender, PunishAction.BAN)) {
                    return true;
                }
                handleUnban(sender, args, PunishmentManager.BanType.HWID, "commands.punish.success.unban.hwid",
                        "{prefix}&aRemoved the HWID ban for {target}.",
                        "commands.punish.error.unban.hwid",
                        "{prefix}&cCould not remove the HWID ban for {target}.");
                break;
            case "mute":
                if (!hasPermission(sender, "zonely.punish.mute") || !ensureActionAllowed(sender, PunishAction.MUTE)) {
                    return true;
                }
                handleMute(sender, args);
                break;
            case "ipmute":
                if (!hasPermission(sender, "zonely.punish.mute") || !ensureActionAllowed(sender, PunishAction.MUTE)) {
                    return true;
                }
                handleIPMute(sender, args);
                break;
            case "hwidmute":
                if (!hasPermission(sender, "zonely.punish.mute") || !ensureActionAllowed(sender, PunishAction.MUTE)) {
                    return true;
                }
                handleHWIDMute(sender, args);
                break;
            case "unmute":
                if (!hasPermission(sender, "zonely.punish.mute") || !ensureActionAllowed(sender, PunishAction.MUTE)) {
                    return true;
                }
                handleUnmute(sender, args, PunishmentManager.MuteType.NORMAL, "commands.punish.success.unmute.player",
                        "{prefix}&aRemoved the player mute for {target}.",
                        "commands.punish.error.unmute.player",
                        "{prefix}&cCould not remove the player mute for {target}.");
                break;
            case "unipmute":
                if (!hasPermission(sender, "zonely.punish.mute") || !ensureActionAllowed(sender, PunishAction.MUTE)) {
                    return true;
                }
                handleUnmute(sender, args, PunishmentManager.MuteType.IP, "commands.punish.success.unmute.ip",
                        "{prefix}&aRemoved the IP mute for {target}.",
                        "commands.punish.error.unmute.ip",
                        "{prefix}&cCould not remove the IP mute for {target}.");
                break;
            case "unhwidmute":
                if (!hasPermission(sender, "zonely.punish.mute") || !ensureActionAllowed(sender, PunishAction.MUTE)) {
                    return true;
                }
                handleUnmute(sender, args, PunishmentManager.MuteType.HWID, "commands.punish.success.unmute.hwid",
                        "{prefix}&aRemoved the HWID mute for {target}.",
                        "commands.punish.error.unmute.hwid",
                        "{prefix}&cCould not remove the HWID mute for {target}.");
                break;
            case "warning":
                if (!hasPermission(sender, "zonely.punish.warning") || !ensureActionAllowed(sender, PunishAction.WARNING)) {
                    return true;
                }
                handleWarning(sender, args);
                break;
            case "ipwarning":
                if (!hasPermission(sender, "zonely.punish.warning") || !ensureActionAllowed(sender, PunishAction.WARNING)) {
                    return true;
                }
                handleIPWarning(sender, args);
                break;
            case "hwidwarning":
                if (!hasPermission(sender, "zonely.punish.warning") || !ensureActionAllowed(sender, PunishAction.WARNING)) {
                    return true;
                }
                handleHWIDWarning(sender, args);
                break;
            case "unwarning":
                if (!hasPermission(sender, "zonely.punish.warning") || !ensureActionAllowed(sender, PunishAction.WARNING)) {
                    return true;
                }
                handleUnwarning(sender, args, PunishmentManager.WarningType.NORMAL, "commands.punish.success.unwarning.player",
                        "{prefix}&aRemoved the warning for {target}.",
                        "commands.punish.error.unwarning.player",
                        "{prefix}&cCould not remove the warning for {target}.");
                break;
            case "unipwarning":
                if (!hasPermission(sender, "zonely.punish.warning") || !ensureActionAllowed(sender, PunishAction.WARNING)) {
                    return true;
                }
                handleUnwarning(sender, args, PunishmentManager.WarningType.IP, "commands.punish.success.unwarning.ip",
                        "{prefix}&aRemoved the IP warning for {target}.",
                        "commands.punish.error.unwarning.ip",
                        "{prefix}&cCould not remove the IP warning for {target}.");
                break;
            case "unhwidwarning":
                if (!hasPermission(sender, "zonely.punish.warning") || !ensureActionAllowed(sender, PunishAction.WARNING)) {
                    return true;
                }
                handleUnwarning(sender, args, PunishmentManager.WarningType.HWID, "commands.punish.success.unwarning.hwid",
                        "{prefix}&aRemoved the HWID warning for {target}.",
                        "commands.punish.error.unwarning.hwid",
                        "{prefix}&cCould not remove the HWID warning for {target}.");
                break;
            case "gecmis":
            case "history":
                handleHistory(sender, args);
                break;
            default:
                sendMessage(sender, "commands.punish.error.invalid-subcommand",
                        "{prefix}&cUnknown sub-command. Use /punish help.");
                break;
        }

        return true;
    }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            for (String cmd : SUB_COMMANDS) {
                if (cmd.startsWith(partial)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (Arrays.asList("ban", "mute", "warning", "gecmis", "history", "unban", "unmute", "unwarning").contains(sub)) {
                completions.addAll(
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                                .collect(Collectors.toList()));
            }
        }
        return completions;
    }
    private void handleBan(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "commands.punish.usage.ban",
                    "{prefix}&cUsage: /punish ban <player> [duration] [reason]");
            return;
        }

        Map<String, String> opts = parseOptionalParams(args, 2);
        String duration = opts.get("duration");
        if (duration != null && !isValidDuration(duration)) {
            sendMessage(sender, "commands.punish.error.invalid-duration",
                    "{prefix}&cDuration must end with s, m, h, or d.");
            return;
        }

        String reason = resolveReason(opts);
        String targetName = args[1];

        boolean success = punishmentManager.banPlayer(
                targetName, duration, reason, sender.getName(), PunishmentManager.BanType.NORMAL);

        if (success) {
            sendResult(sender, "commands.punish.result.ban", DEFAULT_RESULT_BAN,
                    "target", targetName,
                    "duration", formatDuration(sender, duration),
                    "reason", reason);
        } else {
            sendMessage(sender, "commands.punish.error.ban",
                    "{prefix}&cFailed to ban {target}.", "target", targetName);
        }
    }

    private void handleIPBan(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "commands.punish.usage.ipban",
                    "{prefix}&cUsage: /punish ipban <player|ip> [duration] [reason]");
            return;
        }

        Map<String, String> opts = parseOptionalParams(args, 2);
        String duration = opts.get("duration");
        if (duration != null && !isValidDuration(duration)) {
            sendMessage(sender, "commands.punish.error.invalid-duration",
                    "{prefix}&cDuration must end with s, m, h, or d.");
            return;
        }

        String reason = resolveReason(opts);
        String rawTarget = args[1];
        boolean inputLooksLikeIp = rawTarget.contains(".") || rawTarget.contains(":");
        String resolvedIp = punishmentManager.resolveIpTarget(rawTarget);
        if (resolvedIp == null) {
            sendMessage(sender, "commands.punish.error.ipban-resolve-failed",
                    "{prefix}&cCould not determine an IP address for {target}. Make sure the player has joined before.",
                    "prefix", LanguageManager.get("prefix.punish", "&4Punish &8->> "),
                    "target", rawTarget);
            return;
        }

        if (!inputLooksLikeIp) {
            punishmentManager.recordLastIp(rawTarget, resolvedIp);
        }

        boolean success = punishmentManager.banPlayer(
                resolvedIp, duration, reason, sender.getName(), PunishmentManager.BanType.IP);

        if (success) {
            String targetDisplay = inputLooksLikeIp ? resolvedIp : rawTarget + " (" + resolvedIp + ")";
            sendResult(sender, "commands.punish.result.ipban", DEFAULT_RESULT_BAN,
                    "target", targetDisplay,
                    "duration", formatDuration(sender, duration),
                    "reason", reason);
        } else {
            sendMessage(sender, "commands.punish.error.ipban",
                    "{prefix}&cFailed to ban IP {target}.", "target", resolvedIp);
        }
    }

    private void handleHWIDBan(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "commands.punish.usage.hwidban",
                    "{prefix}&cUsage: /punish hwidban <hwid> [duration] [reason]");
            return;
        }

        Map<String, String> opts = parseOptionalParams(args, 2);
        String duration = opts.get("duration");
        if (duration != null && !isValidDuration(duration)) {
            sendMessage(sender, "commands.punish.error.invalid-duration",
                    "{prefix}&cDuration must end with s, m, h, or d.");
            return;
        }

        String reason = resolveReason(opts);
        String targetHWID = args[1];

        boolean success = punishmentManager.banPlayer(
                targetHWID, duration, reason, sender.getName(), PunishmentManager.BanType.HWID);

        if (success) {
            sendResult(sender, "commands.punish.result.hwidban", DEFAULT_RESULT_BAN,
                    "target", targetHWID,
                    "duration", formatDuration(sender, duration),
                    "reason", reason);
        } else {
            sendMessage(sender, "commands.punish.error.hwidban",
                    "{prefix}&cFailed to ban HWID {target}.", "target", targetHWID);
        }
    }
    private void handleMute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "commands.punish.usage.mute",
                    "{prefix}&cUsage: /punish mute <player> [duration] [reason]");
            return;
        }

        Map<String, String> opts = parseOptionalParams(args, 2);
        String duration = opts.get("duration");
        if (duration != null && !isValidDuration(duration)) {
            sendMessage(sender, "commands.punish.error.invalid-duration",
                    "{prefix}&cDuration must end with s, m, h, or d.");
            return;
        }

        String reason = resolveReason(opts);
        String targetName = args[1];

        boolean success = punishmentManager.mutePlayer(
                targetName, duration, reason, sender.getName(), PunishmentManager.MuteType.NORMAL);

        if (success) {
            sendResult(sender, "commands.punish.result.mute", DEFAULT_RESULT_MUTE,
                    "target", targetName,
                    "duration", formatDuration(sender, duration),
                    "reason", reason);
        } else {
            sendMessage(sender, "commands.punish.error.mute",
                    "{prefix}&cFailed to mute {target}.", "target", targetName);
        }
    }

    private void handleIPMute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "commands.punish.usage.ipmute",
                    "{prefix}&cUsage: /punish ipmute <player|ip> [duration] [reason]");
            return;
        }

        Map<String, String> opts = parseOptionalParams(args, 2);
        String duration = opts.get("duration");
        if (duration != null && !isValidDuration(duration)) {
            sendMessage(sender, "commands.punish.error.invalid-duration",
                    "{prefix}&cDuration must end with s, m, h, or d.");
            return;
        }

        String reason = resolveReason(opts);
        String rawTarget = args[1];
        boolean inputLooksLikeIp = rawTarget.contains(".") || rawTarget.contains(":");
        String resolvedIp = punishmentManager.resolveIpTarget(rawTarget);
        if (resolvedIp == null) {
            sendMessage(sender, "commands.punish.error.ipban-resolve-failed",
                    "{prefix}&cCould not determine an IP address for {target}. Make sure the player has joined before.",
                    "prefix", LanguageManager.get("prefix.punish", "&4Punish &8->> "),
                    "target", rawTarget);
            return;
        }

        if (!inputLooksLikeIp) {
            punishmentManager.recordLastIp(rawTarget, resolvedIp);
        }

        boolean success = punishmentManager.mutePlayer(
                resolvedIp, duration, reason, sender.getName(), PunishmentManager.MuteType.IP);

        if (success) {
            sendResult(sender, "commands.punish.result.ipmute", DEFAULT_RESULT_MUTE,
                    "target", inputLooksLikeIp ? resolvedIp : rawTarget + " (" + resolvedIp + ")",
                    "duration", formatDuration(sender, duration),
                    "reason", reason);
        } else {
            sendMessage(sender, "commands.punish.error.ipmute",
                    "{prefix}&cFailed to mute IP {target}.", "target", resolvedIp);
        }
    }

    private void handleHWIDMute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "commands.punish.usage.hwidmute",
                    "{prefix}&cUsage: /punish hwidmute <hwid> [duration] [reason]");
            return;
        }

        Map<String, String> opts = parseOptionalParams(args, 2);
        String duration = opts.get("duration");
        if (duration != null && !isValidDuration(duration)) {
            sendMessage(sender, "commands.punish.error.invalid-duration",
                    "{prefix}&cDuration must end with s, m, h, or d.");
            return;
        }

        String reason = resolveReason(opts);
        String targetHWID = args[1];

        boolean success = punishmentManager.mutePlayer(
                targetHWID, duration, reason, sender.getName(), PunishmentManager.MuteType.HWID);

        if (success) {
            sendResult(sender, "commands.punish.result.hwidmute", DEFAULT_RESULT_MUTE,
                    "target", targetHWID,
                    "duration", formatDuration(sender, duration),
                    "reason", reason);
        } else {
            sendMessage(sender, "commands.punish.error.hwidmute",
                    "{prefix}&cFailed to mute HWID {target}.", "target", targetHWID);
        }
    }
    private void handleUnban(CommandSender sender, String[] args, PunishmentManager.BanType type,
                              String successKey, String successDef,
                              String errorKey, String errorDef) {
        if (args.length < 2) {
            String usageKey = type == PunishmentManager.BanType.NORMAL ? "commands.punish.usage.unban"
                    : type == PunishmentManager.BanType.IP ? "commands.punish.usage.unipban"
                    : "commands.punish.usage.unhwidban";
            String usageDef = type == PunishmentManager.BanType.NORMAL
                    ? "{prefix}&cUsage: /punish unban <player>"
                    : type == PunishmentManager.BanType.IP
                    ? "{prefix}&cUsage: /punish unipban <ip>"
                    : "{prefix}&cUsage: /punish unhwidban <hwid>";
            sendMessage(sender, usageKey, usageDef);
            return;
        }

        String target = args[1];
        boolean success = punishmentManager.unbanPlayer(target, type);

        if (success) {
            sendMessage(sender, successKey, successDef, "target", target);
        } else {
            sendMessage(sender, errorKey, errorDef, "target", target);
        }
    }

    private void handleUnmute(CommandSender sender, String[] args, PunishmentManager.MuteType type,
                               String successKey, String successDef,
                               String errorKey, String errorDef) {
        if (args.length < 2) {
            String usageKey = type == PunishmentManager.MuteType.NORMAL ? "commands.punish.usage.unmute"
                    : type == PunishmentManager.MuteType.IP ? "commands.punish.usage.unipmute"
                    : "commands.punish.usage.unhwidmute";
            String usageDef = type == PunishmentManager.MuteType.NORMAL
                    ? "{prefix}&cUsage: /punish unmute <player>"
                    : type == PunishmentManager.MuteType.IP
                    ? "{prefix}&cUsage: /punish unipmute <ip>"
                    : "{prefix}&cUsage: /punish unhwidmute <hwid>";
            sendMessage(sender, usageKey, usageDef);
            return;
        }

        String target = args[1];
        boolean success = punishmentManager.unmutePlayer(target, type);

        if (success) {
            sendMessage(sender, successKey, successDef, "target", target);
        } else {
            sendMessage(sender, errorKey, errorDef, "target", target);
        }
    }

    private void handleWarning(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "commands.punish.usage.warning",
                    "{prefix}&cUsage: /punish warning <player> [reason]");
            return;
        }

        Map<String, String> opts = parseOptionalParams(args, 2);
        String reason = resolveReason(opts);
        String targetName = args[1];

        boolean success = punishmentManager.warnPlayer(
                targetName, reason, sender.getName(), PunishmentManager.WarningType.NORMAL);

        if (success) {
            sendResult(sender, "commands.punish.result.warning", DEFAULT_RESULT_WARNING,
                    "target", targetName,
                    "reason", reason);
        } else {
            sendMessage(sender, "commands.punish.error.warning",
                    "{prefix}&cFailed to warn {target}.", "target", targetName);
        }
    }

    private void handleIPWarning(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "commands.punish.usage.ipwarning",
                    "{prefix}&cUsage: /punish ipwarning <ip> [reason]");
            return;
        }

        Map<String, String> opts = parseOptionalParams(args, 2);
        String reason = resolveReason(opts);
        String targetIP = args[1];

        boolean success = punishmentManager.warnPlayer(
                targetIP, reason, sender.getName(), PunishmentManager.WarningType.IP);

        if (success) {
            sendResult(sender, "commands.punish.result.ipwarning", DEFAULT_RESULT_WARNING,
                    "target", targetIP,
                    "reason", reason);
        } else {
            sendMessage(sender, "commands.punish.error.ipwarning",
                    "{prefix}&cFailed to warn IP {target}.", "target", targetIP);
        }
    }

    private void handleHWIDWarning(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "commands.punish.usage.hwidwarning",
                    "{prefix}&cUsage: /punish hwidwarning <hwid> [reason]");
            return;
        }

        Map<String, String> opts = parseOptionalParams(args, 2);
        String reason = resolveReason(opts);
        String targetHWID = args[1];

        boolean success = punishmentManager.warnPlayer(
                targetHWID, reason, sender.getName(), PunishmentManager.WarningType.HWID);

        if (success) {
            sendResult(sender, "commands.punish.result.hwidwarning", DEFAULT_RESULT_WARNING,
                    "target", targetHWID,
                    "reason", reason);
        } else {
            sendMessage(sender, "commands.punish.error.hwidwarning",
                    "{prefix}&cFailed to warn HWID {target}.", "target", targetHWID);
        }
    }

    private void handleUnwarning(CommandSender sender, String[] args, PunishmentManager.WarningType type,
                                  String successKey, String successDef,
                                  String errorKey, String errorDef) {
        if (args.length < 2) {
            String usageKey = type == PunishmentManager.WarningType.NORMAL ? "commands.punish.usage.unwarning"
                    : type == PunishmentManager.WarningType.IP ? "commands.punish.usage.unipwarning"
                    : "commands.punish.usage.unhwidwarning";
            String usageDef = type == PunishmentManager.WarningType.NORMAL
                    ? "{prefix}&cUsage: /punish unwarning <player>"
                    : type == PunishmentManager.WarningType.IP
                    ? "{prefix}&cUsage: /punish unipwarning <ip>"
                    : "{prefix}&cUsage: /punish unhwidwarning <hwid>";
            sendMessage(sender, usageKey, usageDef);
            return;
        }

        String target = args[1];
        boolean success = punishmentManager.unwarnPlayer(target, type);

        if (success) {
            sendMessage(sender, successKey, successDef, "target", target);
        } else {
            sendMessage(sender, errorKey, errorDef, "target", target);
        }
    }

    private void handleHistory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, "commands.punish.usage.history",
                    "{prefix}&cUsage: /punish history <player>");
            return;
        }

        String targetName = args[1];
        boolean ownHistory = sender instanceof Player && ((Player) sender).getName().equalsIgnoreCase(targetName);

        int page = 1;
        if (args.length >= 4 && args[2].equalsIgnoreCase("sayfa")) {
            try {
                page = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }

        List<PunishmentManager.PunishmentEntry> historyList = punishmentManager.getHistory(targetName, ownHistory);
        if (historyList.isEmpty()) {
            sendMessage(sender, "commands.punish.history.empty", DEFAULT_HISTORY_EMPTY, "target", targetName);
            return;
        }

        int pageSize = historyPageSize();
        int pages = Math.max(1, (int) Math.ceil(historyList.size() / (double) pageSize));
        if (page < 1) {
            page = 1;
        } else if (page > pages) {
            page = pages;
        }

        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, historyList.size());

        sendCenteredList(sender, "commands.punish.history.header", DEFAULT_HISTORY_HEADER,
                "page", page, "pages", pages);

        for (int i = startIndex; i < endIndex; i++) {
            PunishmentManager.PunishmentEntry entry = historyList.get(i);
            String durationText = entry.getDuration() != null
                    ? punishmentManager.formatDurationHuman(entry.getDuration())
                    : getLocalized(sender, "commands.punish.duration.permanent", "Permanent");

            String line = getLocalized(sender, "commands.punish.history.entry", DEFAULT_HISTORY_ENTRY,
                    "index", (i + 1),
                    "date", entry.getDate(),
                    "type", localizeType(sender, entry.getType()),
                    "duration", durationText,
                    "reason", entry.getReason(),
                    "executor", entry.getExecutor());
            sender.sendMessage(applyColor(line));
        }

        sendCenteredList(sender, "commands.punish.history.footer", DEFAULT_HISTORY_FOOTER);

        if (pages > 1) {
            sendMessage(sender, "commands.punish.history.next-page", DEFAULT_HISTORY_NEXT,
                    "target", targetName, "pages", pages);
        }
    }
    private Map<String, String> parseOptionalParams(String[] args, int idx) {
        String duration = null;
        String reason = null;
        if (idx < args.length && args[idx].matches("\\d+[smhd]")) {
            duration = args[idx++];
        }
        if (idx < args.length) {
            StringBuilder sb = new StringBuilder();
            for (int i = idx; i < args.length; i++) {
                sb.append(args[i]).append(" ");
            }
            reason = sb.toString().trim();
        }
        Map<String, String> map = new HashMap<>();
        map.put("duration", duration);
        map.put("reason", reason);
        return map;
    }

    private String resolveReason(Map<String, String> opts) {
        String reason = opts.get("reason");
        return (reason == null || reason.isEmpty()) ? getDefaultReason() : reason;
    }

    private String formatDuration(CommandSender sender, String rawDuration) {
        if (rawDuration == null) {
            return getLocalized(sender, "commands.punish.duration.permanent", "Permanent");
        }
        return punishmentManager.formatDurationHuman(rawDuration);
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            sendMessage(sender, "commands.punish.error.no-permission",
                    "{prefix}&cYou do not have permission to perform this action.");
            return false;
        }
        return true;
    }

    private boolean ensureActionAllowed(CommandSender sender, PunishAction action) {
        if (!isActionAllowed(action)) {
            sendMessage(sender, "commands.punish.error.not-allowed",
                    "{prefix}&cThis punishment action is disabled in the configuration.");
            return false;
        }
        return true;
    }

    private boolean isEnabled() {
        return CONFIG.getBoolean("punishments.enabled", true);
    }

    private boolean isActionAllowed(PunishAction action) {
        switch (action) {
            case BAN:
                return CONFIG.getBoolean("punishments.allow.ban", true);
            case MUTE:
                return CONFIG.getBoolean("punishments.allow.mute", true);
            case WARNING:
                return CONFIG.getBoolean("punishments.allow.warning", true);
            default:
                return true;
        }
    }

    private String getDefaultReason() {
        return CONFIG.getString("punishments.default-reason", "Not specified");
    }

    private int historyPageSize() {
        return Math.max(1, CONFIG.getInt("punishments.history.page-size", 10));
    }

    private void showHelp(CommandSender sender) {
        sendCenteredList(sender, "commands.punish.help", DEFAULT_HELP_LINES);
    }

    private void sendResult(CommandSender sender, String key, List<String> defaults, Object... placeholders) {
        sendCenteredList(sender, key, defaults, placeholders);
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            Player senderPlayer = sender instanceof Player ? (Player) sender : null;
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (senderPlayer != null && viewer.getUniqueId().equals(senderPlayer.getUniqueId())) {
                    continue;
                }
                sendCenteredList(viewer, key, defaults, placeholders);
            }
        }
    }

    private void sendCenteredList(CommandSender sender, String key, List<String> defaults, Object... placeholders) {
        List<String> lines = CommandMessageUtils.withSignature(getLines(sender, key, defaults, placeholders));
        for (String line : lines) {
            sender.sendMessage(CenteredMessage.generate(line));
        }
    }

    private void sendMessage(CommandSender sender, String key, String def, Object... placeholders) {
        LanguageManager.send(sender, key, def, withPrefix(sender, placeholders));
    }

    private List<String> getLines(CommandSender sender, String key, List<String> defaults, Object... placeholders) {
        Profile profile = resolveProfile(sender);
        Object[] params = withPrefix(sender, placeholders);
        if (profile != null) {
            return LanguageManager.getList(profile, key, defaults, params);
        }
        return LanguageManager.getList(key, defaults, params);
    }

    private Object[] withPrefix(CommandSender sender, Object... placeholders) {
        Object[] combined = new Object[placeholders.length + 2];
        combined[0] = "prefix";
        combined[1] = getPrefix(sender);
        System.arraycopy(placeholders, 0, combined, 2, placeholders.length);
        return combined;
    }

    private String getPrefix(CommandSender sender) {
        Profile profile = resolveProfile(sender);
        if (profile != null) {
            return LanguageManager.get(profile, "prefix.punish", DEFAULT_PREFIX);
        }
        return LanguageManager.get("prefix.punish", DEFAULT_PREFIX);
    }

    private Profile resolveProfile(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            return Profile.getProfile(player.getName());
        }
        return null;
    }

    private String getLocalized(CommandSender sender, String key, String def, Object... placeholders) {
        Profile profile = resolveProfile(sender);
        if (profile != null) {
            return LanguageManager.get(profile, key, def, placeholders);
        }
        return LanguageManager.get(key, def, placeholders);
    }

    private String applyColor(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private String localizeType(CommandSender sender, String rawType) {
        String normalized = rawType.toLowerCase(Locale.ROOT);
        String key = "commands.punish.type." + normalized.replace('_', '-');
        String def;
        switch (normalized) {
            case "ban":
                def = "Player Ban";
                break;
            case "ip_ban":
                def = "IP Ban";
                break;
            case "hwid_ban":
                def = "HWID Ban";
                break;
            case "mute":
                def = "Player Mute";
                break;
            case "ip_mute":
                def = "IP Mute";
                break;
            case "hwid_mute":
                def = "HWID Mute";
                break;
            case "warning":
                def = "Player Warning";
                break;
            case "ip_warning":
                def = "IP Warning";
                break;
            case "hwid_warning":
                def = "HWID Warning";
                break;
            default:
                def = rawType;
                break;
        }
        return getLocalized(sender, key, def);
    }

    private boolean isValidDuration(String input) {
        return input.matches("\\d+[smhd]");
    }

    private enum PunishAction {
        BAN,
        MUTE,
        WARNING
    }
}
