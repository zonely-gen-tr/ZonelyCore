package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.menus.reports.MenuReportsList;
import dev.zonely.whiteeffect.menus.reports.MenuReportReason;
import dev.zonely.whiteeffect.report.ReportService;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.replay.NPCReplayManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ReportCommand implements CommandExecutor, TabCompleter {

    private final Core plugin;
    private final ReportService service;

    public ReportCommand(Core plugin, ReportService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("report")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(LanguageManager.get("commands.reports.only-player",
                        "&cOnly players can use this command."));
                return true;
            }
            Player reporter = (Player) sender;
            Profile reporterProfile = Profile.getProfile(reporter.getName());
            if (args.length < 1) {
                reporter.sendMessage(LanguageManager.get(reporterProfile, "commands.reports.gui-usage",
                        "{prefix}&eUsage: /report <player>",
                        "prefix", punishPrefix(reporterProfile)));
                return true;
            }
            String target = args[0];
            if (reporter.getName().equalsIgnoreCase(target)) {
                reporter.sendMessage(LanguageManager.get(reporterProfile,
                        "commands.reports.self",
                        "{prefix}&cYou cannot report yourself.",
                        "prefix", punishPrefix(reporterProfile)));
                return true;
            }
            Player targetPlayer = Bukkit.getPlayerExact(target);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                reporter.sendMessage(LanguageManager.get(reporterProfile,
                        "commands.reports.offline",
                        "{prefix}&cThat player must be online to be reported.",
                        "prefix", punishPrefix(reporterProfile)));
                return true;
            }

            new MenuReportReason(plugin, reporter, targetPlayer.getName(), service);
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(LanguageManager.get("commands.reports.only-player",
                    "&cOnly players can use this command."));
            return true;
        }
        Player viewer = (Player) sender;
        Profile viewerProfile = Profile.getProfile(viewer.getName());

        if (args.length >= 1 && args[0].equalsIgnoreCase("history")) {
            if (args.length < 2) {
                viewer.sendMessage(LanguageManager.get(viewerProfile, "commands.reports.history.usage",
                        "{prefix}&eUsage: /reports history <player>",
                        "prefix", punishPrefix(viewerProfile)));
                return true;
            }
            if (!viewer.hasPermission("zonely.reports.view")) {
                viewer.sendMessage(LanguageManager.get(viewerProfile, "commands.reports.no-permission",
                        "{prefix}&cYou do not have permission.",
                        "prefix", punishPrefix(viewerProfile)));
                return true;
            }
            new dev.zonely.whiteeffect.menus.reports.MenuReportHistory(viewer, service, args[1]);
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("approved")) {
            if (!viewer.hasPermission("zonely.reports.view")) {
                viewer.sendMessage(LanguageManager.get(viewerProfile, "commands.reports.no-permission",
                        "{prefix}&cYou do not have permission.",
                        "prefix", punishPrefix(viewerProfile)));
                return true;
            }
            new dev.zonely.whiteeffect.menus.reports.MenuReportsApproved(plugin, viewer, service);
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("seek")) {
            if (!viewer.hasPermission("zonely.reports.view")) {
                viewer.sendMessage(LanguageManager.get(viewerProfile, "commands.reports.no-permission",
                        "{prefix}&cYou do not have permission.",
                        "prefix", punishPrefix(viewerProfile)));
                return true;
            }
            if (args.length < 2) {
                viewer.sendMessage(LanguageManager.get(viewerProfile, "commands.reports.seek.usage",
                        "{prefix}&eUsage: /reports seek <seconds>",
                        "prefix", punishPrefix(viewerProfile)));
                return true;
            }
            if (!NPCReplayManager.isReplaying(viewer)) {
                viewer.sendMessage(LanguageManager.get(viewerProfile, "commands.reports.seek.not-playing",
                        "{prefix}&cYou are not watching a replay.",
                        "prefix", punishPrefix(viewerProfile)));
                return true;
            }
            double seconds;
            try {
                seconds = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                viewer.sendMessage(LanguageManager.get(viewerProfile, "commands.reports.seek.invalid-number",
                        "{prefix}&cPlease enter a valid number of seconds.",
                        "prefix", punishPrefix(viewerProfile)));
                return true;
            }
            int ticks = (int) Math.round(seconds * 20.0);
            if (NPCReplayManager.seek(viewer, ticks)) {
                viewer.sendMessage(LanguageManager.get(viewerProfile, "commands.reports.seek.success",
                        "{prefix}&aMoved replay by {seconds} seconds.",
                        "prefix", punishPrefix(viewerProfile),
                        "seconds", String.format("%.2f", seconds)));
            } else {
                viewer.sendMessage(LanguageManager.get(viewerProfile, "commands.reports.seek.failed",
                        "{prefix}&cUnable to move replay.",
                        "prefix", punishPrefix(viewerProfile)));
            }
            return true;
        }

        if (!viewer.hasPermission("zonely.reports.view")) {
            viewer.sendMessage(LanguageManager.get(viewerProfile, "commands.reports.no-permission",
                    "{prefix}&cYou do not have permission.",
                    "prefix", punishPrefix(viewerProfile)));
            return true;
        }
        new MenuReportsList(plugin, viewer, service);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("report")) {
            List<String> completions = new ArrayList<>();
            if (args.length == 1) {
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList()));
            }
            return completions;
        } else if (name.equals("reports")) {
            if (args.length == 1) {
                return java.util.Arrays.asList("history", "approved", "seek");
            } else if (args.length == 2 && "history".equalsIgnoreCase(args[0])) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        }
        return java.util.Collections.emptyList();
    }

    private String punishPrefix() {
        String prefix = LanguageManager.get("prefix.punish", "&4Punish &8->> ");
        if (prefix == null) {
            return "&4Punish &8->> ";
        }
        String trimmed = prefix.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("prefix")) {
            return "&4Punish &8->> ";
        }
        return prefix;
    }

    private String punishPrefix(Profile profile) {
        if (profile == null) {
            return punishPrefix();
        }
        String prefix = LanguageManager.get(profile, "prefix.punish", "&4Punish &8->> ");
        if (prefix == null) {
            return "&4Punish &8->> ";
        }
        String trimmed = prefix.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("prefix")) {
            return "&4Punish &8->> ";
        }
        return prefix;
    }
}

