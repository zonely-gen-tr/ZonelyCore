package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.fake.FakeManager;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.utils.CenteredMessage;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FakeCommand extends Commands {

   private static final String DEFAULT_CORE_PREFIX = "&3Lobby &8>> ";
   private static final String DEFAULT_DISGUISE_PREFIX = "&3Disguise &8>> ";

   public FakeCommand() {
      super("disguise", "d");
   }

   @Override
   public void perform(CommandSender sender, String label, String[] args) {
      if (!(sender instanceof Player)) {
         LanguageManager.send(sender,
               "commands.fake.only-player",
               "{prefix}&cOnly players can use this command.",
               "prefix", getCorePrefix());
         return;
      }

      Player player = (Player)sender;
      Profile profile = Profile.getProfile(player.getName());
      String lowerLabel = label.toLowerCase(Locale.ROOT);
      String firstArg = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
      boolean isListCommand = lowerLabel.equals("dl") || firstArg.equals("liste") || firstArg.equals("list");
      boolean isClearCommand = lowerLabel.equals("dtemizle") || firstArg.equals("temizle") || firstArg.equals("clear");

      if (isListCommand) {
         if (!player.hasPermission("zcore.cmd.disguiselist")) {
            LanguageManager.send(player,
                  "commands.fake.no-permission",
                  "{prefix}&cYou do not have permission to use this command.",
                  "prefix", getCorePrefix(profile));
            return;
         }

         sendDisguiseList(player);
         return;
      }

      if (isClearCommand) {
         if (!player.hasPermission("zcore.cmd.disguise")) {
            LanguageManager.send(player,
                  "commands.fake.no-permission",
                  "{prefix}&cYou do not have permission to use this command.",
                  "prefix", getCorePrefix(profile));
            return;
         }

         if (profile != null && profile.playingGame()) {
            LanguageManager.send(player,
                  "commands.fake.in-game",
                  "{prefix}&cYou cannot use this command while in a game.",
                  "prefix", getCorePrefix(profile));
            return;
         }

         if (!FakeManager.isFake(player.getName())) {
            LanguageManager.send(player,
                  "commands.fake.not-disguised",
                  "{prefix}&cYou are not currently disguised.",
                  "prefix", getCorePrefix(profile));
            return;
         }

         FakeManager.removeFake(player);
         return;
      }

      if (!player.hasPermission("zcore.cmd.disguise")) {
         LanguageManager.send(player,
               "commands.fake.no-permission",
               "{prefix}&cYou do not have permission to use this command.",
               "prefix", getCorePrefix(profile));
         return;
      }

      if (profile != null && profile.playingGame()) {
         LanguageManager.send(player,
               "commands.fake.in-game",
               "{prefix}&cYou cannot use this command while in a game.",
               "prefix", getCorePrefix(profile));
         return;
      }

      if (FakeManager.getRandomNicks().stream().noneMatch(FakeManager::isUsable)) {
         LanguageManager.send(player,
               "commands.fake.names-unavailable",
               "{prefix}&cNo disguise names are currently available.",
               "prefix", getDisguisePrefix(profile));
         return;
      }

      if (args.length == 0) {
         FakeManager.sendRole(player);
         return;
      }

      String roleName = args[0];
      if (!FakeManager.isFakeRole(roleName) || Role.getRoleByName(roleName) == null) {
         EnumSound.VILLAGER_NO.play(player, 1.0F, 1.0F);
         FakeManager.sendRole(player);
         return;
      }

      if (args.length == 1) {
         EnumSound.ORB_PICKUP.play(player, 1.0F, 2.0F);
         FakeManager.sendSkin(player, roleName);
         return;
      }

      String skin = args[1];
      if (!skin.equalsIgnoreCase("alex")
            && !skin.equalsIgnoreCase("steve")
            && !skin.equalsIgnoreCase("kendin")
            && !skin.equalsIgnoreCase("self")) {
         EnumSound.VILLAGER_NO.play(player, 1.0F, 1.0F);
         FakeManager.sendSkin(player, roleName);
         return;
      }

      if (!FakeManager.applyRandomFake(player, roleName, skin)) {
         LanguageManager.send(player,
               "commands.fake.names-unavailable",
               "{prefix}&cNo disguise names are currently available.",
               "prefix", getDisguisePrefix(profile));
         return;
      }
   }

   private void sendDisguiseList(Player player) {
      Profile profile = Profile.getProfile(player.getName());
      List<String> header = LanguageManager.getList(profile,
            "commands.fake.list.header",
            Arrays.asList("&6&m----------------------------", "&2", "&6&lActive Disguise Names", "&2"));
      List<String> footer = LanguageManager.getList(profile,
            "commands.fake.list.footer",
            Arrays.asList("&2", "&6&m----------------------------"));

      header.forEach(line -> player.sendMessage(CenteredMessage.generate(line)));

      List<String> nicked = FakeManager.listNicked();
      if (nicked.isEmpty()) {
         String empty = LanguageManager.get(profile,
               "commands.fake.list.empty",
               "&cNo players are currently using disguise names.");
         player.sendMessage(CenteredMessage.generate(empty));
      } else {
         for (String nick : nicked) {
            String entry = LanguageManager.get(profile,
                  "commands.fake.list.entry",
                  "&c{name} &2-> &azcorefakereal:{name}",
                  "name", nick);
            player.sendMessage(CenteredMessage.generate(entry));
         }
      }

      footer.forEach(line -> player.sendMessage(CenteredMessage.generate(line)));
      nicked.clear();
   }

   private String getCorePrefix(Profile profile) {
      return LanguageManager.get(profile, "prefix.lobby", DEFAULT_CORE_PREFIX);
   }

   private String getCorePrefix() {
      return LanguageManager.get("prefix.lobby", DEFAULT_CORE_PREFIX);
   }

   private String getDisguisePrefix(Profile profile) {
      return LanguageManager.get(profile, "prefix.disguise", DEFAULT_DISGUISE_PREFIX);
   }
}
