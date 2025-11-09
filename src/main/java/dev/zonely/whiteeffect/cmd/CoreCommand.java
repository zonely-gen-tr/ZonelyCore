package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.menus.MenuProfile;
import dev.zonely.whiteeffect.npc.LobbyNPCManager;
import dev.zonely.whiteeffect.hologram.LastCreditHologramManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.utils.CenteredMessage;
import dev.zonely.whiteeffect.utils.CommandMessageUtils;
import dev.zonely.whiteeffect.utils.ZonelyUpdater;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CoreCommand extends Commands {

   private static final String DEFAULT_PREFIX = "&3Lobby &8>> ";
   private static final List<String> DEFAULT_INFO = Arrays.asList(
         "&3ZonelyCore &8>> &bZonelyCore &3v{version}",
         "&eDeveloped by &6WhiteEffect &e- &4Orcun &cDonmez.",
         "&dZONELY.GEN.TR &ehosts distribution."
   );
   private static final List<String> DEFAULT_HELP_HEADER = Arrays.asList("&6&m----------------------------", "&2");
   private static final List<String> DEFAULT_HELP_FOOTER = Arrays.asList("&2", "&6&m----------------------------");
   private static final List<String> DEFAULT_HELP_ENTRIES = Arrays.asList(
         "&e/zc update &2- &fCheck for plugin updates.",
         "&e/zc convert &2- &fConvert stored data to another database type.",
         "&e/zc menu <player> &2- &fOpen the profile menu.",
         "&e/zc npc spawn <type> &2- &fCreate or move a lobby NPC.",
         "&e/zc hologram lastcredits &2- &fPlace the last-credit leaderboard hologram.",
         "&e/zc reload &2- &fReload configuration and language files.",
         "&e/webprofile &2- &fOpen the web profile menu.",
         "&e/credit &2- &fInspect credits.",
         "&e/lastcredits &2- &fList the most recent credit purchasers.",
         "&e/message &2- &fSend a private message.",
         "&e/store &2- &fView store products.",
         "&e/creditvoucher &2- &fShare credits between players.",
         "&e/punish &2- &fManage player punishments.",
         "&e/auctions &2- &fOpen the auction house.",
         "&e/auction [amount] &2- &fSubmit an item for auction.",
         "&e/disguise &2- &fUse the disguise system.",
         "&e/sales &2- &fReview store sale statistics."
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
         sendHelp(player, profile);
         return;
      }

      String action = args[0].toLowerCase(Locale.ROOT);
      switch (action) {
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
         sendHelp(player, profile);
         break;
      }
      }
   }

   private void handleUpdate(Player player, Profile profile) {
      if (ZonelyUpdater.UPDATER != null) {
         if (!ZonelyUpdater.UPDATER.canDownload) {
            LanguageManager.send(player,
                  "commands.core.update.already-downloaded",
                  "{prefix}&eThe update is already downloaded and will apply on restart. Use /stop to apply now.",
                  "prefix", getPrefix(profile));
            return;
         }
         ZonelyUpdater.UPDATER.canDownload = false;
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

      if (args.length >= 2 && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("sil") || args[1].equalsIgnoreCase("kaldir") || args[1].equalsIgnoreCase("kaldır"))) {
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

      if ("remove".equalsIgnoreCase(args[1]) || "sil".equalsIgnoreCase(args[1]) || "kaldir".equalsIgnoreCase(args[1]) || "kaldır".equalsIgnoreCase(args[1])) {
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

      if ("remove".equalsIgnoreCase(args[1]) || "sil".equalsIgnoreCase(args[1]) || "kaldir".equalsIgnoreCase(args[1]) || "kaldır".equalsIgnoreCase(args[1])) {
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

   private void sendHelp(Player player, Profile profile) {
      java.util.List<String> combined = null;
      org.bukkit.configuration.ConfigurationSection helpSection =
            LanguageManager.getSection(profile, "commands.core.help");

      if (helpSection != null && helpSection.isList("lines")) {
         java.util.List<String> rawLines = helpSection.getStringList("lines");
         if (rawLines != null && !rawLines.isEmpty()) {
            combined = new java.util.ArrayList<>(
                  LanguageManager.getList(profile, "commands.core.help.lines", rawLines));
         }
      }

      if (combined == null || combined.isEmpty()) {
         combined = new java.util.ArrayList<>();
         combined.addAll(LanguageManager.getList(profile, "commands.core.help.header", DEFAULT_HELP_HEADER));
         combined.addAll(LanguageManager.getList(profile, "commands.core.help.entries", DEFAULT_HELP_ENTRIES));
         combined.addAll(LanguageManager.getList(profile, "commands.core.help.footer", DEFAULT_HELP_FOOTER));
      }

      CommandMessageUtils.withSignature(combined)
            .forEach(line -> player.sendMessage(CenteredMessage.generate(line)));
   }

   private String getPrefix(Profile profile) {
      return LanguageManager.get(profile, "prefix.lobby", DEFAULT_PREFIX);
   }

   private String getPrefix() {
      return LanguageManager.get("prefix.lobby", DEFAULT_PREFIX);
   }
}
