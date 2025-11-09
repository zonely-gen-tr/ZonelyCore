package dev.zonely.whiteeffect.player.fake;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.libraries.profile.Mojang;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import dev.zonely.whiteeffect.utils.BukkitUtils;
import dev.zonely.whiteeffect.utils.StringUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.HoverEvent.Action;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

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
      ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
      BookMeta meta = (BookMeta) book.getItemMeta();
      Profile profile = Profile.getProfile(player.getName());
      meta.setAuthor(LanguageManager.get(profile, "commands.fake.book.author", "WhiteEffect"));
      meta.setTitle(LanguageManager.get(profile, "commands.fake.book.title", "Sayfa Sec"));
      TextComponent page = buildRolePage(profile);
      applyTextPage(meta, page);
      book.setItemMeta(meta);
      BukkitUtils.openBook(player, book);
   }

   public static void sendSkin(Player player, String role) {
      ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
      BookMeta meta = (BookMeta) book.getItemMeta();
      Profile profile = Profile.getProfile(player.getName());
      meta.setAuthor(LanguageManager.get(profile, "commands.fake.book.author", "WhiteEffect"));
      meta.setTitle(LanguageManager.get(profile, "commands.fake.book.title", "Sayfa Sec"));
      TextComponent page = buildSkinPage(profile, role);
      applyTextPage(meta, page);
      book.setItemMeta(meta);
      BukkitUtils.openBook(player, book);
   }

   private static TextComponent buildRolePage(Profile profile) {
      TextComponent root = new TextComponent("");
      List<String> introLines = LanguageManager.getList(profile,
            "commands.fake.book.roles.intro",
            Arrays.asList("&6&l[DISGUISE - GIZLENME]", " ", "&0Oyunculardan gizlenmek amaciyla kimliginin yetkisini sec:"));
      appendLines(root, introLines, true);

      String entryText = LanguageManager.get(profile,
            "commands.fake.book.roles.entry.text",
            "&0> {role_display}");
      String entryHover = LanguageManager.get(profile,
            "commands.fake.book.roles.entry.hover",
            "&fYeni ismin {role_prefix}&f olarak gorunecek.");
      String entryCommand = LanguageManager.get(profile,
            "commands.fake.book.roles.entry.command",
            "/disguise {role_id}");

      for (String roleName : CONFIG.getStringList("disguise.groups")) {
         Role role = Role.getRoleByName(roleName);
         if (role == null) {
            continue;
         }
         String display = role.getName();
         String prefix = role.getPrefix();
         String text = entryText
               .replace("{role_display}", display)
               .replace("{role_prefix}", prefix)
               .replace("{role_id}", roleName);
         String hoverText = entryHover
               .replace("{role_display}", display)
               .replace("{role_prefix}", prefix)
               .replace("{role_id}", roleName);
         String command = entryCommand
               .replace("{role_display}", display)
               .replace("{role_prefix}", prefix)
               .replace("{role_id}", roleName);
         appendInteractiveLine(root, text, hoverText, command, true);
      }

      return root;
   }

   private static TextComponent buildSkinPage(Profile profile, String role) {
      TextComponent root = new TextComponent("");
      List<String> introLines = LanguageManager.getList(profile,
            "commands.fake.book.skins.intro",
            Arrays.asList("&6&l[DISGUISE - GIZLENME]", " ", "&0Simdi sirada cinsiyetini belirlemelisin:"));
      appendLines(root, introLines, true);

      appendSkinOption(root, profile, "commands.fake.book.skins.options.steve", role,
            "&0> &7Steve", "&fSkinin Steve olarak ayarlanacak.", "/disguise {role} steve");
      appendSkinOption(root, profile, "commands.fake.book.skins.options.self", role,
            "&0> &7Kendi Skinin", "&fSkinin kendi skinin olarak ayarlanacak.", "/disguise {role} kendin");
      appendSkinOption(root, profile, "commands.fake.book.skins.options.alex", role,
            "&0> &7Alex", "&fSkinin Alex olarak ayarlanacak.", "/disguise {role} alex");

      return root;
   }

   private static void appendSkinOption(TextComponent root,
                                        Profile profile,
                                        String baseKey,
                                        String role,
                                        String defaultText,
                                        String defaultHover,
                                        String defaultCommand) {
      String text = LanguageManager.get(profile, baseKey + ".text", defaultText)
            .replace("{role}", role);
      String hover = LanguageManager.get(profile, baseKey + ".hover", defaultHover)
            .replace("{role}", role);
      String command = LanguageManager.get(profile, baseKey + ".command", defaultCommand)
            .replace("{role}", role);
      appendInteractiveLine(root, text, hover, command, true);
   }

   private static void appendLines(TextComponent root, List<String> lines, boolean trailingNewline) {
      if (lines == null || lines.isEmpty()) {
         return;
      }
      for (int i = 0; i < lines.size(); i++) {
         String line = lines.get(i);
         appendLegacy(root, line);
         if (i < lines.size() - 1 || trailingNewline) {
            root.addExtra(new TextComponent("\n"));
         }
      }
   }

   private static void appendInteractiveLine(TextComponent root,
                                             String text,
                                             String hoverText,
                                             String command,
                                             boolean trailingNewline) {
      BaseComponent[] components = TextComponent.fromLegacyText(StringUtils.formatColors(text == null ? "" : text));
      HoverEvent hover = null;
      if (hoverText != null && !hoverText.isEmpty()) {
         hover = new HoverEvent(Action.SHOW_TEXT,
               TextComponent.fromLegacyText(StringUtils.formatColors(hoverText)));
      }
      ClickEvent click = null;
      if (command != null && !command.isEmpty()) {
         click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
      }
      for (BaseComponent component : components) {
         if (hover != null) {
            component.setHoverEvent(hover);
         }
         if (click != null) {
            component.setClickEvent(click);
         }
         root.addExtra(component);
      }
      if (trailingNewline) {
         root.addExtra(new TextComponent("\n"));
      }
   }

   private static void appendLegacy(TextComponent root, String text) {
      if (text == null) {
         return;
      }
      BaseComponent[] components = TextComponent.fromLegacyText(StringUtils.formatColors(text));
      for (BaseComponent component : components) {
         root.addExtra(component);
      }
   }

   private static void applyTextPage(BookMeta meta, TextComponent page) {
      if (meta == null || page == null) {
         return;
      }
      BaseComponent[] components = new BaseComponent[]{page};
      try {
         java.lang.reflect.Method spigotMethod = BookMeta.class.getMethod("spigot");
         Object spigot = spigotMethod.invoke(meta);
         if (spigot != null) {
            java.lang.reflect.Method setPages = spigot.getClass().getMethod("setPages", BaseComponent[][].class);
            setPages.invoke(spigot, (Object) new BaseComponent[][]{components});
            return;
         }
      } catch (Throwable ignored) {
      }
      meta.setPages(TextComponent.toLegacyText(page));
   }
   public static void applyFake(Player player, String fakeName, String role, String skin) {
      if (!isBungeeSide()) {
         player.kickPlayer(StringUtils.formatColors(CONFIG.getString("disguise.kick-apply")).replace("\\n", "\n"));
      }

      fakeNames.put(player.getName(), fakeName);
      fakeRoles.put(player.getName(), Role.getRoleByName(role));
      fakeSkins.put(player.getName(), skin);
   }

   public static void removeFake(Player player) {
      if (!isBungeeSide()) {
         player.kickPlayer(StringUtils.formatColors(CONFIG.getString("disguise.kick-remove")).replace("\\n", "\n"));
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

