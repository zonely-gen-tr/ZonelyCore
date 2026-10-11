package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.clubs.ClubLang;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.menus.MenuProfile;
import dev.zonely.whiteeffect.npc.LobbyNPCManager;
import dev.zonely.whiteeffect.hologram.LastCreditHologramManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.CenteredMessage;
import dev.zonely.whiteeffect.utils.CommandMessageUtils;
import dev.zonely.whiteeffect.utils.StringUtils;
import dev.zonely.whiteeffect.utils.ZonelyUpdater;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CoreCommand extends Commands {

   private static final String DEFAULT_PREFIX = "&3Lobby &8>> ";
   private static final List<String> DEFAULT_INFO = Arrays.asList(
         "&3ZonelyCore &8>> &bZonelyCore &3v{version}",
         "&eDeveloped by &6WhiteEffect &e- &4Orcun &cDonmez.",
         "&dZONELY.GEN.TR &ehosts distribution."
   );
   private static final int HELP_PAGE_SIZE = 8;
   private static final List<String> DEFAULT_HELP_ENTRIES = Arrays.asList(
         "&e/zc update &8- &7Update check",
         "&e/zc reload &8- &7Reload files",
         "&e/zc convert &8- &7Convert database",
         "&e/zc menu <player> &8- &7Open profile",
         "&e/zc npc spawn <type> [id] &8- &7Spawn lobby NPC",
         "&e/zc hologram lastcredits <id> &8- &7Set hologram",
         "&e/webprofile &8- &7Open profile UI",
         "&e/credit &8- &7Check credits",
         "&e/lastcredits &8- &7Recent buyers",
         "&e/store &8- &7Open store",
         "&e/creditvoucher &8- &7Send credits",
         "&e/punish &8- &7Punishment panel",
         "&e/esports &8- &7E-Sports panel",
         "&e/club &8- &7Club panel",
         "&e/club help &8- &7Club commands",
         "&e/auctions &8- &7Auction house",
         "&e/auction <amount> &8- &7List held item",
         "&e/report <player> &8- &7Report player",
         "&e/reports &8- &7Report panel",
         "&e/friends &8- &7Friends panel",
         "&e/support &8- &7Support panel",
         "&e/email <set|resend|status> &8- &7Email settings",
         "&e/passwordreset &8- &7Reset password",
         "&e/2fa <setup|code|disable> &8- &72FA control"
   );

   public CoreCommand() {
      super("zcore", "zc");
   }

   @Override
   public void perform(CommandSender sender, String label, String[] args) {
      if (!(sender instanceof Player)) {
         LanguageManager.send(sender,
               "commands.core.only-player",
               "{prefix}&cOnly players can use this command.",
               "prefix", getPrefix());
         return;
      }

      Player player = (Player) sender;
      Profile profile = Profile.getProfile(player.getName());
      if (!player.hasPermission("zcore.admin")) {
         List<String> lines = CommandMessageUtils.withSignature(
               LanguageManager.getList(profile,
                     "commands.core.info",
                     DEFAULT_INFO,
                     "version", Core.getInstance().getDescription().getVersion()));
         for (String line : lines) {
            player.sendMessage(CenteredMessage.generate(line));
         }
         return;
      }

      if (args.length == 0) {
         sendHelp(player, profile, 1);
         return;
      }

      Integer numericPage = parsePage(args[0]);
      if (numericPage != null) {
         sendHelp(player, profile, numericPage);
         return;
      }

      String action = args[0].toLowerCase(Locale.ROOT);
      switch (action) {
         case "help":
         case "yardim":
         case "yardım": {
            int page = 1;
            if (args.length >= 2) {
               Integer requested = parsePage(args[1]);
               if (requested == null) {
                  LanguageManager.send(player,
                        "commands.core.help.invalid-page",
                        "{prefix}&cPlease enter a valid help page number.",
                        "prefix", getPrefix(profile));
               } else {
                  page = requested;
               }
            }
            sendHelp(player, profile, page);
            break;
         }
         case "guncelle":
         case "güncelle":
         case "update": {
            handleUpdate(player, profile);
            break;
         }
         case "donustur":
         case "dönüştür":
         case "convert": {
            handleConvert(player, profile);
            break;
         }
         case "menu": {
            handleMenu(player, profile, args);
            break;
         }
         case "npc": {
            handleNpcCommand(player, profile, args);
            break;
         }
         case "hologram": {
         handleHologramCommand(player, profile, args);
         break;
      }
      case "reload": {
         handleReload(player, profile);
         break;
      }
      default: {
         sendHelp(player, profile, 1);
         break;
      }
      }
   }

   private void handleUpdate(Player player, Profile profile) {
      if (ZonelyUpdater.UPDATER != null) {
         ZonelyUpdater.UPDATER.downloadUpdate(player);
      } else {
         LanguageManager.send(player,
               "commands.core.update.up-to-date",
               "{prefix}&cThe plugin is already on the latest version.",
               "prefix", getPrefix(profile));
      }
   }

   private void handleConvert(Player player, Profile profile) {
      LanguageManager.send(player,
            "commands.core.convert.start",
            "{prefix}&fCurrent database: &b{database}",
            "prefix", getPrefix(profile),
            "database", Database.getInstance().getClass().getSimpleName().replace("Database", ""));
      Database.getInstance().convertDatabase(player);
   }

   private void handleMenu(Player player, Profile profile, String[] args) {
      if (args.length < 2) {
         LanguageManager.send(player,
               "commands.core.menu.usage",
               "{prefix}&cUsage: /zcore menu <player>",
               "prefix", getPrefix(profile));
         return;
      }

      String targetName = args[1];
      if (targetName.equalsIgnoreCase(player.getName())) {
         new MenuProfile(profile);
         return;
      }

      Profile target = Profile.getProfile(targetName);
      if (target == null) {
         LanguageManager.send(player,
               "commands.core.menu.not-found",
               "{prefix}&cCould not load profile data for {target}.",
               "prefix", getPrefix(profile),
               "target", targetName);
         return;
      }

      new MenuProfile(target);
   }

   private void handleNpc(Player player, Profile profile, String[] args) {
      LobbyNPCManager manager = LobbyNPCManager.getInstance();
      if (manager == null) {
         LanguageManager.send(player,
               "commands.core.npc.unavailable",
               "{prefix}&cNPC manager is not initialised yet.",
               "prefix", getPrefix(profile));
         return;
      }

      if (args.length < 3 || !args[1].equalsIgnoreCase("spawn")) {
         LanguageManager.send(player,
               "commands.core.npc.usage",
               "{prefix}&cUsage: /zc npc spawn <type>",
               "prefix", getPrefix(profile));
         return;
      }

      LobbyNPCManager.NPCType type = LobbyNPCManager.NPCType.fromId(args[2]);
      if (type == null) {
         LanguageManager.send(player,
               "commands.core.npc.invalid-type",
               "{prefix}&cUnknown NPC type. Available: {types}",
               "prefix", getPrefix(profile),
               "types", LobbyNPCManager.NPCType.listAsString());
         return;
      }

      boolean success = manager.spawnNPC(type, player.getLocation(), true);
      if (success) {
         LanguageManager.send(player,
               "commands.core.npc.spawned",
               "{prefix}&a{type} NPC saved and spawned at your location.",
               "prefix", getPrefix(profile),
               "type", type.getId());
      } else {
         LanguageManager.send(player,
               "commands.core.npc.failed",
               "{prefix}&cFailed to spawn the NPC. Check console for details.",
               "prefix", getPrefix(profile));
      }
   }

   private void handleNpcCommand(Player player, Profile profile, String[] args) {
      LobbyNPCManager manager = LobbyNPCManager.getInstance();
      if (manager == null) {
         LanguageManager.send(player,
               "commands.core.npc.unavailable",
               "{prefix}&cNPC manager is not initialised yet.",
               "prefix", getPrefix(profile));
         return;
      }

      if (args.length >= 2 && args[1].equalsIgnoreCase("spawn")) {
         if (args.length < 3) {
            LanguageManager.send(player,
                  "commands.core.npc.usage",
                  "{prefix}&cUsage: /zc npc spawn <type> [id]",
                  "prefix", getPrefix(profile));
            return;
         }

         LobbyNPCManager.NPCType type = LobbyNPCManager.NPCType.fromId(args[2]);
         if (type == null) {
            LanguageManager.send(player,
                  "commands.core.npc.invalid-type",
                  "{prefix}&cUnknown NPC type. Available: {types}",
                  "prefix", getPrefix(profile),
                  "types", LobbyNPCManager.NPCType.listAsString());
            return;
         }

         String id = args.length >= 4 ? args[3] : "default";
         boolean success = manager.spawnNPC(type, id, player.getLocation(), true);
         if (success) {
            LanguageManager.send(player,
                  "commands.core.npc.spawned",
                  "{prefix}&a{type} NPC saved and spawned at your location.",
                  "prefix", getPrefix(profile),
                  "type", type.getId());
         } else {
            LanguageManager.send(player,
                  "commands.core.npc.failed",
                  "{prefix}&cFailed to spawn the NPC. Check the console for details.",
                  "prefix", getPrefix(profile));
         }
         return;
      }

      if (args.length >= 2 && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("sil") || args[1].equalsIgnoreCase("kaldir") || args[1].equalsIgnoreCase("kaldÄ±r"))) {
         if (args.length < 3) {
            LanguageManager.send(player,
                  "commands.core.npc.usage-remove",
                  "{prefix}&cUsage: /zc npc remove <type> [id]",
                  "prefix", getPrefix(profile));
            return;
         }
         LobbyNPCManager.NPCType type = LobbyNPCManager.NPCType.fromId(args[2]);
         if (type == null) {
            LanguageManager.send(player,
                  "commands.core.npc.invalid-type",
                  "{prefix}&cUnknown NPC type. Available: {types}",
                  "prefix", getPrefix(profile),
                  "types", LobbyNPCManager.NPCType.listAsString());
            return;
         }
         String id = args.length >= 4 ? args[3] : "default";
         boolean removed = manager.removeNPC(type, id);
         LanguageManager.send(player,
               removed ? "commands.core.npc.removed" : "commands.core.npc.none",
               removed ? "{prefix}&aNPC removed and location cleared." : "{prefix}&cNo NPC to remove.",
               "prefix", getPrefix(profile));
         return;
      }

      LanguageManager.send(player,
            "commands.core.npc.usage",
            "{prefix}&cUsage: /zc npc spawn <type> [id]",
            "prefix", getPrefix(profile));
   }

   private void handleHologram(Player player, Profile profile, String[] args) {
      if (args.length < 2) {
         LanguageManager.send(player,
               "commands.core.hologram.usage",
               "{prefix}&cUsage: /zc hologram lastcredits",
               "prefix", getPrefix(profile));
         return;
      }

      if ("remove".equalsIgnoreCase(args[1]) || "sil".equalsIgnoreCase(args[1]) || "kaldir".equalsIgnoreCase(args[1]) || "kaldÄ±r".equalsIgnoreCase(args[1])) {
         LastCreditHologramManager manager = LastCreditHologramManager.getInstance();
         if (manager == null) {
            LanguageManager.send(player,
                  "commands.core.hologram.unavailable",
                  "{prefix}&cHologram manager is not initialised yet.",
                  "prefix", getPrefix(profile));
            return;
         }
         boolean existed = manager.remove(true);
         LanguageManager.send(player,
               existed ? "commands.core.hologram.removed" : "commands.core.hologram.none",
               existed ? "{prefix}&aHologram removed and location cleared." : "{prefix}&cNo hologram to remove.",
               "prefix", getPrefix(profile));
         return;
      }

      if (!"lastcredits".equalsIgnoreCase(args[1])) {
         LanguageManager.send(player,
               "commands.core.hologram.invalid-type",
               "{prefix}&cUnknown hologram target. Available: lastcredits",
               "prefix", getPrefix(profile));
         return;
      }

      LastCreditHologramManager manager =
            LastCreditHologramManager.getInstance();
      if (manager == null) {
         LanguageManager.send(player,
               "commands.core.hologram.unavailable",
               "{prefix}&cHologram manager is not initialised yet.",
               "prefix", getPrefix(profile));
         return;
      }

      boolean success = manager.setLocation(player.getLocation(), true);
      LanguageManager.send(player,
            success ? "commands.core.hologram.set" : "commands.core.hologram.failed",
            success
                  ? "{prefix}&aLast-credit leaderboard hologram saved at your location."
                  : "{prefix}&cUnable to place the hologram here.",
            "prefix", getPrefix(profile));
   }

   private void handleHologramCommand(Player player, Profile profile, String[] args) {
      if (args.length < 2) {
         LanguageManager.send(player,
               "commands.core.hologram.usage",
               "{prefix}&cUsage: /zc hologram lastcredits <id>",
               "prefix", getPrefix(profile));
         return;
      }

      if ("remove".equalsIgnoreCase(args[1]) || "sil".equalsIgnoreCase(args[1]) || "kaldir".equalsIgnoreCase(args[1]) || "kaldÄ±r".equalsIgnoreCase(args[1])) {
         LastCreditHologramManager manager = LastCreditHologramManager.getInstance();
         if (manager == null) {
            LanguageManager.send(player,
                  "commands.core.hologram.unavailable",
                  "{prefix}&cHologram manager is not initialised yet.",
                  "prefix", getPrefix(profile));
            return;
         }
         String id = args.length >= 3 ? args[2] : "default";
         if (id != null) {
            id = id.trim().replaceAll("[^A-Za-z0-9_-]", "").toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
               id = "default";
            }
         }
         boolean existed = manager.remove(id, true);
         LanguageManager.send(player,
               existed ? "commands.core.hologram.removed" : "commands.core.hologram.none",
               existed ? "{prefix}&aHologram removed and location cleared." : "{prefix}&cNo hologram to remove.",
               "prefix", getPrefix(profile));
         return;
      }

      if (!"lastcredits".equalsIgnoreCase(args[1])) {
         LanguageManager.send(player,
               "commands.core.hologram.invalid-type",
               "{prefix}&cUnknown hologram target. Available: lastcredits",
               "prefix", getPrefix(profile));
         return;
      }

      LastCreditHologramManager manager = LastCreditHologramManager.getInstance();
      if (manager == null) {
         LanguageManager.send(player,
               "commands.core.hologram.unavailable",
               "{prefix}&cHologram manager is not initialised yet.",
               "prefix", getPrefix(profile));
         return;
      }

      if (args.length < 3) {
         LanguageManager.send(player,
               "commands.core.hologram.usage",
               "{prefix}&cUsage: /zc hologram lastcredits <id>",
               "prefix", getPrefix(profile));
         return;
      }

      String id = args[2] == null ? "" : args[2].trim();
      id = id.replaceAll("[^A-Za-z0-9_-]", "").toLowerCase(Locale.ROOT);
      if (id.isEmpty()) {
         LanguageManager.send(player,
               "commands.core.hologram.invalid-id",
               "{prefix}&cPlease provide a valid hologram id (letters, numbers, _, -).",
               "prefix", getPrefix(profile));
         return;
      }

      boolean success = manager.setLocation(id, player.getLocation(), true);
      LanguageManager.send(player,
            success ? "commands.core.hologram.set" : "commands.core.hologram.failed",
            success
                  ? "{prefix}&aLast-credit leaderboard hologram saved at your location."
                  : "{prefix}&cUnable to place the hologram here.",
            "prefix", getPrefix(profile));
   }

   private void handleReload(Player player, Profile profile) {
      long start = System.currentTimeMillis();
      try {
         Core plugin = Core.getInstance();
         plugin.reloadPluginResources();
         long took = System.currentTimeMillis() - start;
         LanguageManager.send(player,
               "commands.core.reload.success",
               "{prefix}&aConfiguration and language files reloaded in {ms}ms.",
               "prefix", getPrefix(profile),
               "ms", Long.toString(took));
      } catch (Exception ex) {
         Core.getInstance().getLogger().log(Level.SEVERE, "[ZC] Reload failed", ex);
         LanguageManager.send(player,
               "commands.core.reload.failed",
               "{prefix}&cReload failed. Check console for details.",
               "prefix", getPrefix(profile));
      }
   }

   private void sendHelp(Player player, Profile profile, int requestedPage) {
      List<String> entries = resolveHelpEntries(profile);
      if (entries.isEmpty()) {
         entries = new ArrayList<>(DEFAULT_HELP_ENTRIES);
      }

      int maxPage = Math.max(1, (entries.size() + HELP_PAGE_SIZE - 1) / HELP_PAGE_SIZE);
      int page = Math.max(1, Math.min(requestedPage, maxPage));
      int from = (page - 1) * HELP_PAGE_SIZE;
      int to = Math.min(from + HELP_PAGE_SIZE, entries.size());

      String border = "&6&m-----------------------------";
      String title = "&6&lZonelyCore Commands";

      player.sendMessage(CenteredMessage.generate(border));
      player.sendMessage("");
      player.sendMessage(CenteredMessage.generate(title));

      List<String> pageEntries = entries.subList(from, to);
      for (String line : CommandMessageUtils.withSignature(pageEntries)) {
         player.sendMessage(CenteredMessage.generate(line));
      }

      player.sendMessage("");
      sendHelpNavigation(player, profile, page, maxPage);
      player.sendMessage("");
      player.sendMessage(CenteredMessage.generate(border));
   }

   private List<String> resolveHelpEntries(Profile profile) {
      Map<String, String> entries = new LinkedHashMap<>();

      List<String> configured = LanguageManager.getList(profile,
            "commands.core.help.entries",
            new ArrayList<>());
      if (configured.isEmpty()) {
         configured = LanguageManager.getList(profile, "commands.core.help.lines", DEFAULT_HELP_ENTRIES);
      }
      for (String line : configured) {
         addHelpEntry(entries, line);
      }
      ensureHelpEntry(entries, profile, "commands.core.help.entry.email",
            "&e/email <set|resend|status> &2- &fManage account email.");
      ensureHelpEntry(entries, profile, "commands.core.help.entry.passwordreset",
            "&e/passwordreset &2- &fRequest a password reset link.");
      ensureHelpEntry(entries, profile, "commands.core.help.entry.2fa",
            "&e/2fa <setup|code|disable> &2- &fManage two-factor authentication.");
      ensureHelpEntry(entries, profile, "commands.core.help.entry.disguise",
            "&e/disguise &2- &fUse the disguise system.");
      addHelpEntry(entries, ClubLang.get("command.help.help", "&e/club help &2- &fShow this help panel."));

      return new ArrayList<>(entries.values());
   }

   private void ensureHelpEntry(Map<String, String> entries, Profile profile, String key, String def) {
      String line = LanguageManager.get(profile, key, def);
      if (!isHelpCommandLine(line)) {
         return;
      }
      String helpKey = extractHelpKey(line);
      if (helpKey.isEmpty()) {
         return;
      }
      // Force localized/synced help entry to override stale line-based entries.
      entries.put(helpKey, line);
   }

   private void addHelpEntry(Map<String, String> target, String line) {
      if (!isHelpCommandLine(line)) {
         return;
      }
      String key = extractHelpKey(line);
      if (key.isEmpty()) {
         return;
      }
      target.putIfAbsent(key, line);
   }

   private boolean isHelpCommandLine(String line) {
      if (line == null) {
         return false;
      }
      String stripped = ChatColor.stripColor(StringUtils.formatColors(line));
      if (stripped == null) {
         return false;
      }
      return stripped.trim().startsWith("/");
   }

   private String extractHelpKey(String line) {
      String stripped = ChatColor.stripColor(StringUtils.formatColors(line));
      if (stripped == null) {
         return "";
      }
      String normalized = stripped.trim().toLowerCase(Locale.ROOT);
      int split = normalized.indexOf(" - ");
      if (split > 0) {
         normalized = normalized.substring(0, split).trim();
      }
      return normalized;
   }

   private void sendHelpNavigation(Player player, Profile profile, int page, int maxPage) {
      String prevActive = LanguageManager.get(profile, "commands.core.help.compact.nav.prev", "&e<--");
      String prevDisabled = LanguageManager.get(profile,
            "commands.core.help.compact.nav.prev-disabled",
            "&8" + ChatColor.stripColor(StringUtils.formatColors(prevActive)));
      String nextActive = LanguageManager.get(profile, "commands.core.help.compact.nav.next", "&e-->");
      String nextDisabled = LanguageManager.get(profile,
            "commands.core.help.compact.nav.next-disabled",
            "&8" + ChatColor.stripColor(StringUtils.formatColors(nextActive)));
      String pageText = LanguageManager.get(profile,
            "commands.core.help.compact.nav.page",
            " &8[&e{page}&7/&e{max}&8] ",
            "page", Integer.toString(page),
            "max", Integer.toString(maxPage));

      String leftLabel = page > 1 ? prevActive : prevDisabled;
      String rightLabel = page < maxPage ? nextActive : nextDisabled;
      String padding = extractCenterPadding(leftLabel + pageText + rightLabel);

      TextComponent root = new TextComponent(padding);
      root.addExtra(createNavButton(
            page > 1 ? prevActive : prevDisabled,
            page > 1 ? "/zc help " + (page - 1) : null,
            LanguageManager.get(profile,
                  "commands.core.help.compact.nav.prev-hover",
                  "&7Open previous help page")));

      root.addExtra(new TextComponent(StringUtils.formatColors(pageText)));

      root.addExtra(createNavButton(
            page < maxPage ? nextActive : nextDisabled,
            page < maxPage ? "/zc help " + (page + 1) : null,
            LanguageManager.get(profile,
                  "commands.core.help.compact.nav.next-hover",
                  "&7Open next help page")));
      player.spigot().sendMessage(root);
   }

   private String extractCenterPadding(String text) {
      String centered = CenteredMessage.generate(text);
      int index = 0;
      while (index < centered.length() && centered.charAt(index) == ' ') {
         index++;
      }
      return centered.substring(0, index);
   }

   private TextComponent createNavButton(String label, String command, String hoverText) {
      TextComponent component = new TextComponent(StringUtils.formatColors(label));
      if (command != null && !command.trim().isEmpty()) {
         component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
         component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
               TextComponent.fromLegacyText(StringUtils.formatColors(hoverText))));
      }
      return component;
   }

   private Integer parsePage(String raw) {
      if (raw == null || raw.trim().isEmpty()) {
         return null;
      }
      try {
         int value = Integer.parseInt(raw.trim());
         return value >= 1 ? value : null;
      } catch (NumberFormatException ignored) {
         return null;
      }
   }

   private String getPrefix(Profile profile) {
      return LanguageManager.get(profile, "prefix.lobby", DEFAULT_PREFIX);
   }

   private String getPrefix() {
      return LanguageManager.get("prefix.lobby", DEFAULT_PREFIX);
   }
}

