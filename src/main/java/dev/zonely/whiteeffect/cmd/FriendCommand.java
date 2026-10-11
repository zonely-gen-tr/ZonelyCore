package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.friends.FriendManager;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.CenteredMessage;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class FriendCommand extends Commands {

    private static final String DEFAULT_PREFIX = "&dFriends &8>> ";

    private final Core plugin;

    public FriendCommand(Core plugin) {
        super("friends", "friend", "arkadas", "arkada\u015F");
        this.plugin = plugin;
        this.setDescription("Friends command");
        this.setUsage("/friends");
    }

    @Override
    public void perform(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            LanguageManager.send(sender,
                    "friends.command.only-player",
                    "{prefix}&cBu komutu sadece oyuncular kullanabilir.",
                    "prefix", getPrefix());
            return;
        }

        Player player = (Player) sender;
        FriendManager manager = plugin.getFriendManager();
        if (manager == null) {
            LanguageManager.send(player,
                    "friends.command.disabled",
                    "{prefix}&cArkada\u015Fl\u0131k mod\u00FCl\u00FC \u015Fu anda kapal\u0131.",
                    "prefix", getPrefix(player));
            return;
        }

        if (args.length == 0) {
            manager.openFriendsMenu(player, 0);
            return;
        }

        Integer page = parsePage(args[0]);
        if (page != null) {
            manager.openFriendsMenu(player, page - 1);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
            case "yardim":
            case "yard\u0131m":
                sendHelp(player);
                return;

            case "list":
            case "gui":
            case "menu":
                manager.openFriendsMenu(player, 0);
                return;

            case "requests":
            case "istekler":
            case "pending":
                manager.openRequestsMenu(player, 0);
                return;

            case "add":
            case "ekle":
                if (args.length < 2) {
                    LanguageManager.send(player,
                            "friends.command.add.usage",
                            "{prefix}&cKullan\u0131m: /friends add <oyuncu>",
                            "prefix", getPrefix(player));
                    return;
                }
                manager.sendFriendRequest(player, args[1]);
                return;

            case "accept":
            case "kabul":
                if (args.length < 2) {
                    LanguageManager.send(player,
                            "friends.command.accept.usage",
                            "{prefix}&cKullan\u0131m: /friends accept <oyuncu>",
                            "prefix", getPrefix(player));
                    return;
                }
                manager.respondRequest(player, args[1], true);
                return;

            case "reject":
            case "reddet":
            case "deny":
                if (args.length < 2) {
                    LanguageManager.send(player,
                            "friends.command.reject.usage",
                            "{prefix}&cKullan\u0131m: /friends reject <oyuncu>",
                            "prefix", getPrefix(player));
                    return;
                }
                manager.respondRequest(player, args[1], false);
                return;

            case "remove":
            case "sil":
            case "kaldir":
            case "kald\u0131r":
                if (args.length < 2) {
                    LanguageManager.send(player,
                            "friends.command.remove.usage",
                            "{prefix}&cKullan\u0131m: /friends remove <oyuncu>",
                            "prefix", getPrefix(player));
                    return;
                }
                boolean confirm = args.length >= 3 && args[2].equalsIgnoreCase("confirm");
                boolean cancel = args.length >= 3 && args[2].equalsIgnoreCase("cancel");
                manager.removeFriend(player, args[1], confirm, cancel);
                return;

            case "msg":
            case "mesaj":
            case "message":
                if (args.length < 3) {
                    LanguageManager.send(player,
                            "friends.command.msg.usage",
                            "{prefix}&cKullan\u0131m: /friends msg <oyuncu> <mesaj>",
                            "prefix", getPrefix(player));
                    return;
                }
                manager.sendDirectMessage(player, args[1], joinArgs(args, 2));
                return;

            case "duyuru":
            case "announce":
            case "announcement":
            case "broadcast":
                if (args.length < 2) {
                    LanguageManager.send(player,
                            "friends.command.announce.usage",
                            "{prefix}&cUsage: /friends announce <message>",
                            "prefix", getPrefix(player));
                    return;
                }
                manager.sendAnnouncement(player, joinArgs(args, 1));
                return;

            default:
                sendHelp(player);
        }
    }

    private String joinArgs(String[] args, int from) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private Integer parsePage(String raw) {
        try {
            int value = Integer.parseInt(raw);
            return value <= 0 ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void sendHelp(Player player) {
        Profile profile = Profile.getProfile(player.getName());
        List<String> lines = LanguageManager.getList(profile,
                "friends.command.help.lines",
                java.util.Arrays.asList(
                        "&6&m----------------------------",
                        "&2",
                        "&d&lArkada\u015F Komutlar\u0131",
                        "&2",
                        "&e/friends &2- &fArkada\u015F men\u00FCs\u00FC",
                        "&e/friends add <oyuncu> &2- &fArkada\u015Fl\u0131k iste\u011Fi g\u00F6nder",
                        "&e/friends requests &2- &fBekleyen istekler",
                        "&e/friends accept <oyuncu> &2- &f\u0130ste\u011Fi kabul et",
                        "&e/friends reject <oyuncu> &2- &f\u0130ste\u011Fi reddet",
                        "&e/friends remove <oyuncu> &2- &fArkada\u015F\u0131 kald\u0131r (onayl\u0131)",
                        "&e/friends msg <oyuncu> <mesaj> &2- &fArkada\u015Fa mesaj g\u00F6nder",
                        "&e/friends announce <mesaj> &2- &fT\u00FCm arkada\u015Flara duyuru",
                        "&2",
                        "&6&m----------------------------"
                ));

        for (String line : lines) {
            player.sendMessage(CenteredMessage.generate(line));
        }
    }

    private String getPrefix(Player player) {
        Profile profile = Profile.getProfile(player.getName());
        return LanguageManager.get(profile, "prefix.lobby", DEFAULT_PREFIX);
    }

    private String getPrefix() {
        return LanguageManager.get("prefix.lobby", DEFAULT_PREFIX);
    }
}
