package dev.zonely.whiteeffect.player.fake;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.Manager;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.profile.Mojang;
import dev.zonely.whiteeffect.menus.disguise.DisguiseMenu;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import dev.zonely.whiteeffect.utils.StringUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class FakeManager {
   public static final String STEVE = "eyJ0aW1lc3RhbXAiOjE1ODcxNTAzMTc3MjAsInByb2ZpbGVJZCI6IjRkNzA0ODZmNTA5MjRkMzM4NmJiZmM5YzEyYmFiNGFlIiwicHJvZmlsZU5hbWUiOiJzaXJGYWJpb3pzY2hlIiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8xYTRhZjcxODQ1NWQ0YWFiNTI4ZTdhNjFmODZmYTI1ZTZhMzY5ZDE3NjhkY2IxM2Y3ZGYzMTlhNzEzZWI4MTBiIn19fQ==:syZ2Mt1vQeEjh/t8RGbv810mcfTrhQvnwEV7iLCd+5udVeroTa5NjoUehgswacTML3k/KxHZHaq4o6LmACHwsj/ivstW4PWc2RmVn+CcOoDKI3ytEm70LvGz0wAaTVKkrXHSw/RbEX/b7g7oQ8F67rzpiZ1+Z3TKaxbgZ9vgBQZQdwRJjVML2keI0669a9a1lWq3V/VIKFZc1rMJGzETMB2QL7JVTpQFOH/zXJGA+hJS5bRol+JG3LZTX93+DililM1e8KEjKDS496DYhMAr6AfTUfirLAN1Jv+WW70DzIpeKKXWR5ZeI+9qf48+IvjG8DhRBVFwwKP34DADbLhuebrolF/UyBIB9sABmozYdfit9uIywWW9+KYgpl2EtFXHG7CltIcNkbBbOdZy0Qzq62Tx6z/EK2acKn4oscFMqrobtioh5cA/BCRb9V4wh0fy5qx6DYHyRBdzLcQUfb6DkDx1uyNJ7R5mO44b79pSo8gdd9VvMryn/+KaJu2UvyCrMVUtOOzoIh4nCMc9wXOFW3jZ7ZTo4J6c28ouL98rVQSAImEd/P017uGvWIT+hgkdXnacVG895Y6ilXqJToyvf1JUQb4dgry0WTv6UTAjNgrm5a8mZx9OryLuI2obas97LCon1rydcNXnBtjUk0TUzdrvIa5zNstYZPchUb+FSnU=";
   public static final String ALEX = "eyJ0aW1lc3RhbXAiOjE1ODcxMzkyMDU4MzUsInByb2ZpbGVJZCI6Ijc1MTQ0NDgxOTFlNjQ1NDY4Yzk3MzlhNmUzOTU3YmViIiwicHJvZmlsZU5hbWUiOiJUaGFua3NNb2phbmciLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzNiNjBhMWY2ZDU2MmY1MmFhZWJiZjE0MzRmMWRlMTQ3OTMzYTNhZmZlMGU3NjRmYTQ5ZWEwNTc1MzY2MjNjZDMiLCJtZXRhZGF0YSI6eyJtb2RlbCI6InNsaW0ifX19fQ==:W60UUuAYlWfLFt5Ay3Lvd/CGUbKuuU8+HTtN/cZLhc0BC22XNgbY1btTite7ZtBUGiZyFOhYqQi+LxVWrdjKEAdHCSYWpCRMFhB1m0zEfu78yg4XMcFmd1v7y9ZfS45b3pLAJ463YyjDaT64kkeUkP6BUmgsTA2iIWvM33k6Tj3OAM39kypFSuH+UEpkx603XtxratD+pBjUCUvWyj2DMxwnwclP/uACyh0ZVrI7rC5xJn4jSura+5J2/j6Z/I7lMBBGLESt7+pGn/3/kArDE/1RShOvm5eYKqrTMRfK4n3yd1U1DRsMzxkU2AdlCrv1swT4o+Cq8zMI97CF/xyqk8z2L98HKlzLjtvXIE6ogljyHc9YsfU9XhHwZ7SKXRNkmHswOgYIQCSa1RdLHtlVjN9UdUyUoQIIO2AWPzdKseKJJhXwqKJ7lzfAtStErRzDjmjr7ld/5tFd3TTQZ8yiq3D6aRLRUnOMTr7kFOycPOPhOeZQlTjJ6SH3PWFsdtMMQsGzb2vSukkXvJXFVUM0TcwRZlqT5MFHyKBBPprIt0wVN6MmSKc8m5kdk7ZBU2ICDs/9Cd/fyzAIRDu3Kzm7egbAVK9zc1kXwGzowUkGGy1XvZxyRS5jF1zu6KzVgaXOGcrOLH4z/OHzxvbyW22/UwahWGN7MD4j37iJ7gjZDrk=";
   private static final WConfig CONFIG = Core.getInstance().getConfig("config");
   private static final Pattern REAL_PATTERN = Pattern.compile("(?i)zcorefakereal:\\w*");
   private static final Pattern NOT_CHANGE_PATTERN = Pattern.compile("(?i)zcorenotchange:\\w*");
   public static Map<String, String> fakeNames = new HashMap();
   public static Map<String, Role> fakeRoles = new HashMap();
   public static Map<String, String> fakeSkins = new HashMap();
   private static List<String> randoms;
   private static Boolean bungeeSide;


   public static void setupFake() {
      if (CONFIG.get("disguise.groups") instanceof String) {
         CONFIG.set("disguise.groups", Arrays.asList(CONFIG.getString("disguise.groups")));
      }
   }

   public static void sendRole(Player player) {
      DisguiseMenu.openRoleMenu(player);
   }

   public static void sendSkin(Player player, String role) {
      DisguiseMenu.openSkinMenu(player, role);
   }

   public static List<String> getConfiguredRoles() {
      return new ArrayList(CONFIG.getStringList("disguise.groups"));
   }

   public static String chooseRandomUsableName() {
      List<String> enabled = new ArrayList();
      Iterator var1 = getRandomNicks().iterator();

      while(var1.hasNext()) {
         String name = (String)var1.next();
         if (isUsable(name)) {
            enabled.add(name);
         }
      }

      if (enabled.isEmpty()) {
         return null;
      } else {
         return (String)enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
      }
   }

   public static String resolveSkinData(Player player, String skinType) {
      if (skinType == null) {
         return ALEX;
      } else if (skinType.equalsIgnoreCase("steve")) {
         return STEVE;
      } else if (!skinType.equalsIgnoreCase("self") && !skinType.equalsIgnoreCase("kendin")) {
         return ALEX;
      } else {
         String value = Manager.getSkin(player.getName(), "value");
         String signature = Manager.getSkin(player.getName(), "signature");
         if (value != null && !value.trim().isEmpty() && signature != null && !signature.trim().isEmpty()) {
            return value + ":" + signature;
         } else {
            return ALEX;
         }
      }
   }

   public static boolean applyRandomFake(Player player, String role, String skinType) {
      if (player != null && role != null && isFakeRole(role) && Role.getRoleByName(role) != null) {
         String fakeName = chooseRandomUsableName();
         if (fakeName == null) {
            return false;
         } else {
            applyFake(player, fakeName, role, resolveSkinData(player, skinType));
            return true;
         }
      } else {
         return false;
      }
   }

   public static void applyFake(Player player, String fakeName, String role, String skin) {
      if (!isBungeeSide()) {
         Profile profile = player == null ? null : Profile.getProfile(player.getName());
         String kickMessage = LanguageManager.get(profile,
               "commands.fake.kick.apply",
               CONFIG.getString("disguise.kick-apply", "&6&l- Disguise -\\n \\n&eRejoin the server to apply your disguise."));
         player.kickPlayer(StringUtils.formatColors(kickMessage).replace("\\n", "\n"));
      }

      fakeNames.put(player.getName(), fakeName);
      fakeRoles.put(player.getName(), Role.getRoleByName(role));
      fakeSkins.put(player.getName(), skin);
   }

   public static void removeFake(Player player) {
      if (!isBungeeSide()) {
         Profile profile = player == null ? null : Profile.getProfile(player.getName());
         String kickMessage = LanguageManager.get(profile,
               "commands.fake.kick.remove",
               CONFIG.getString("disguise.kick-remove", "&6&l- Disguise -\\n \\n&cUnable to find a replacement name. Please rejoin."));
         player.kickPlayer(StringUtils.formatColors(kickMessage).replace("\\n", "\n"));
      }

      fakeNames.remove(player.getName());
      fakeRoles.remove(player.getName());
      fakeSkins.remove(player.getName());
   }

   public static String getCurrent(String playerName) {
      return isFake(playerName) ? getFake(playerName) : playerName;
   }

   public static String getFake(String playerName) {
      return (String)fakeNames.get(playerName);
   }

   public static Role getRole(String playerName) {
      return (Role)fakeRoles.getOrDefault(playerName, Role.getLastRole());
   }

   public static String getSkin(String playerName) {
      return (String)fakeSkins.getOrDefault(playerName, "eyJ0aW1lc3RhbXAiOjE1ODcxNTAzMTc3MjAsInByb2ZpbGVJZCI6IjRkNzA0ODZmNTA5MjRkMzM4NmJiZmM5YzEyYmFiNGFlIiwicHJvZmlsZU5hbWUiOiJzaXJGYWJpb3pzY2hlIiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8xYTRhZjcxODQ1NWQ0YWFiNTI4ZTdhNjFmODZmYTI1ZTZhMzY5ZDE3NjhkY2IxM2Y3ZGYzMTlhNzEzZWI4MTBiIn19fQ==:syZ2Mt1vQeEjh/t8RGbv810mcfTrhQvnwEV7iLCd+5udVeroTa5NjoUehgswacTML3k/KxHZHaq4o6LmACHwsj/ivstW4PWc2RmVn+CcOoDKI3ytEm70LvGz0wAaTVKkrXHSw/RbEX/b7g7oQ8F67rzpiZ1+Z3TKaxbgZ9vgBQZQdwRJjVML2keI0669a9a1lWq3V/VIKFZc1rMJGzETMB2QL7JVTpQFOH/zXJGA+hJS5bRol+JG3LZTX93+DililM1e8KEjKDS496DYhMAr6AfTUfirLAN1Jv+WW70DzIpeKKXWR5ZeI+9qf48+IvjG8DhRBVFwwKP34DADbLhuebrolF/UyBIB9sABmozYdfit9uIywWW9+KYgpl2EtFXHG7CltIcNkbBbOdZy0Qzq62Tx6z/EK2acKn4oscFMqrobtioh5cA/BCRb9V4wh0fy5qx6DYHyRBdzLcQUfb6DkDx1uyNJ7R5mO44b79pSo8gdd9VvMryn/+KaJu2UvyCrMVUtOOzoIh4nCMc9wXOFW3jZ7ZTo4J6c28ouL98rVQSAImEd/P017uGvWIT+hgkdXnacVG895Y6ilXqJToyvf1JUQb4dgry0WTv6UTAjNgrm5a8mZx9OryLuI2obas97LCon1rydcNXnBtjUk0TUzdrvIa5zNstYZPchUb+FSnU=");
   }

   public static boolean isFake(String playerName) {
      return fakeNames.containsKey(playerName);
   }

   public static boolean isUsable(String name) {
      return !fakeNames.containsKey(name) && !fakeNames.containsValue(name) && Bukkit.getPlayer(name) == null;
   }

   public static List<String> listNicked() {
      return new ArrayList(fakeNames.keySet());
   }

   public static List<String> getRandomNicks() {
      if (randoms == null) {
         randoms = CONFIG.getStringList("disguise.randoms");
      }

      return randoms;
   }

   public static boolean isFakeRole(String roleName) {
      return CONFIG.getStringList("disguise.groups").stream().anyMatch((role) -> {
         return role.equalsIgnoreCase(roleName);
      });
   }

   public static boolean isBungeeSide() {
      if (bungeeSide == null) {
         bungeeSide = CONFIG.getBoolean("bungeecord");
      }

      return bungeeSide;
   }

   public static String replaceNickedChanges(String original) {
      String replaced = original;
      Iterator var2 = listNicked().iterator();

      while(var2.hasNext()) {
         String name = (String)var2.next();

         for(Matcher matcher = Pattern.compile("(?i)" + name).matcher(replaced); matcher.find(); replaced = replaced.replaceFirst(Pattern.quote(matcher.group()), Matcher.quoteReplacement("zcorenotchange:" + name))) {
         }
      }

      return replaced;
   }

   public static String replaceNickedPlayers(String original, boolean toFake) {
      String replaced = original;
      List<String> backup = new ArrayList();
      Iterator var4 = listNicked().iterator();

      String name;
      while(var4.hasNext()) {
         name = (String)var4.next();

         Matcher matcher;
         String found;
         for(matcher = NOT_CHANGE_PATTERN.matcher(replaced); matcher.find(); replaced = replaced.replaceFirst(Pattern.quote(found), Matcher.quoteReplacement("zcorenotchange:" + (backup.size() - 1)))) {
            found = matcher.group();
            backup.add(found.replace("zcorenotchange:", ""));
         }

         for(matcher = Pattern.compile("(?i)" + (toFake ? name : getFake(name))).matcher(replaced); matcher.find(); replaced = replaced.replaceFirst(Pattern.quote(matcher.group()), Matcher.quoteReplacement(toFake ? getFake(name) : name))) {
         }
      }

      Matcher matcher = REAL_PATTERN.matcher(replaced);
      while (matcher.find()) {
         String found = matcher.group();
         replaced = replaced.replaceFirst(Pattern.quote(found), Matcher.quoteReplacement(
                 fakeNames.entrySet().stream().filter(entry -> entry.getValue().equals(found.replace("zcorefakereal:", ""))).map(Map.Entry::getKey).findFirst().orElse("")));
      }

      for(matcher = NOT_CHANGE_PATTERN.matcher(replaced); matcher.find(); replaced = replaced.replaceFirst(Pattern.quote(matcher.group()), Matcher.quoteReplacement((String)backup.get(Integer.parseInt(name.replace("zcorenotchange:", "")))))) {
         name = matcher.group();
      }

      backup.clear();
      return replaced;
   }

   public static WrappedGameProfile cloneProfile(WrappedGameProfile profile) {
      WrappedGameProfile gameProfile = profile.withName(getFake(profile.getName()));
      gameProfile.getProperties().clear();

      try {
         String id = Mojang.getUUID(gameProfile.getName());
         if (id != null) {
            String textures = Mojang.getSkinProperty(id);
            if (textures != null) {
               gameProfile.getProperties().put("textures", new WrappedSignedProperty(textures.split(" : ")[0], textures.split(" : ")[1], textures.split(" : ")[2]));
            }
         }
      } catch (Exception var4) {
      }

      return gameProfile;
   }
}

