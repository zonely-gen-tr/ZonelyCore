package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.Manager;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.fake.FakeManager;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.utils.CenteredMessage;
import dev.zonely.whiteeffect.utils.enums.EnumSound;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
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
      if (!skin.equalsIgnoreCase("alex") && !skin.equalsIgnoreCase("steve") && !skin.equalsIgnoreCase("kendin")) {
         EnumSound.VILLAGER_NO.play(player, 1.0F, 1.0F);
         FakeManager.sendSkin(player, roleName);
         return;
      }

      List<String> enabled = FakeManager.getRandomNicks().stream().filter(FakeManager::isUsable).collect(Collectors.toList());
      String fakeName = enabled.isEmpty() ? null : enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
      if (fakeName == null) {
         LanguageManager.send(player,
               "commands.fake.names-unavailable",
               "{prefix}&cNo disguise names are currently available.",
               "prefix", getDisguisePrefix(profile));
         return;
      }

      FakeManager.applyFake(player, fakeName, roleName, skin.equalsIgnoreCase("steve") ? "eyJ0aW1lc3RhbXAiOjE1ODcxNTAzMTc3MjAsInByb2ZpbGVJZCI6IjRkNzA0ODZmNTA5MjRkMzM4NmJiZmM5YzEyYmFiNGFlIiwicHJvZmlsZU5hbWUiOiJzaXJGYWJpb3pzY2hlIiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8xYTRhZjcxODQ1NWQ0YWFiNTI4ZTdhNjFmODZmYTI1ZTZhMzY5ZDE3NjhkY2IxM2Y3ZGYzMTlhNzEzZWI4MTBiIn19fQ==:syZ2Mt1vQeEjh/t8RGbv810mcfTrhQvnwEV7iLCd+5udVeroTa5NjoUehgswacTML3k/KxHZHaq4o6LmACHwsj/ivstW4PWc2RmVn+CcOoDKI3ytEm70LvGz0wAaTVKkrXHSw/RbEX/b7g7oQ8F67rzpiZ1+Z3TKaxbgZ9vgBQZQdwRJjVML2keI0669a9a1lWq3V/VIKFZc1rMJGzETMB2QL7JVTpQFOH/zXJGA+hJS5bRol+JG3LZTX93+DililM1e8KEjKDS496DYhMAr6AfTUfirLAN1Jv+WW70DzIpeKKXWR5ZeI+9qf48+IvjG8DhRBVFwwKP34DADbLhuebrolF/UyBIB9sABmozYdfit9uIywWW9+KYgpl2EtFXHG7CltIcNkbBbOdZy0Qzq62Tx6z/EK2acKn4oscFMqrobtioh5cA/BCRb9V4wh0fy5qx6DYHyRBdzLcQUfb6DkDx1uyNJ7R5mO44b79pSo8gdd9VvMryn/+KaJu2UvyCrMVUtOOzoIh4nCMc9wXOFW3jZ7ZTo4J6c28ouL98rVQSAImEd/P017uGvWIT+hgkdXnacVG895Y6ilXqJToyvf1JUQb4dgry0WTv6UTAjNgrm5a8mZx9OryLuI2obas97LCon1rydcNXnBtjUk0TUzdrvIa5zNstYZPchUb+FSnU=" : (skin.equalsIgnoreCase("kendin") ? Manager.getSkin(player.getName(), "value") + ":" + Manager.getSkin(player.getName(), "signature") : "eyJ0aW1lc3RhbXAiOjE1ODcxMzkyMDU4MzUsInByb2ZpbGVJZCI6Ijc1MTQ0NDgxOTFlNjQ1NDY4Yzk3MzlhNmUzOTU3YmViIiwicHJvZmlsZU5hbWUiOiJUaGFua3NNb2phbmciLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzNiNjBhMWY2ZDU2MmY1MmFhZWJiZjE0MzRmMWRlMTQ3OTMzYTNhZmZlMGU3NjRmYTQ5ZWEwNTc1MzY2MjNjZDMiLCJtZXRhZGF0YSI6eyJtb2RlbCI6InNsaW0ifX19fQ==:W60UUuAYlWfLFt5Ay3Lvd/CGUbKuuU8+HTtN/cZLhc0BC22XNgbY1btTite7ZtBUGiZyFOhYqQi+LxVWrdjKEAdHCSYWpCRMFhB1m0zEfu78yg4XMcFmd1v7y9ZfS45b3pLAJ463YyjDaT64kkeUkP6BUmgsTA2iIWvM33k6Tj3OAM39kypFSuH+UEpkx603XtxratD+pBjUCUvWyj2DMxwnwclP/uACyh0ZVrI7rC5xJn4jSura+5J2/j6Z/I7lMBBGLESt7+pGn/3/kArDE/1RShOvm5eYKqrTMRfK4n3yd1U1DRsMzxkU2AdlCrv1swT4o+Cq8zMI97CF/xyqk8z2L98HKlzLjtvXIE6ogljyHc9YsfU9XhHwZ7SKXRNkmHswOgYIQCSa1RdLHtlVjN9UdUyUoQIIO2AWPzdKseKJJhXwqKJ7lzfAtStErRzDjmjr7ld/5tFd3TTQZ8yiq3D6aRLRUnOMTr7kFOycPOPhOeZQlTjJ6SH3PWFsdtMMQsGzb2vSukkXvJXFVUM0TcwRZlqT5MFHyKBBPprIt0wVN6MmSKc8m5kdk7ZBU2ICDs/9Cd/fyzAIRDu3Kzm7egbAVK9zc1kXwGzowUkGGy1XvZxyRS5jF1zu6KzVgaXOGcrOLH4z/OHzxvbyW22/UwahWGN7MD4j37iJ7gjZDrk="));
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
