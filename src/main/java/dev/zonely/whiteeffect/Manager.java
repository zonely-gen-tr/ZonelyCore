package dev.zonely.whiteeffect;

import dev.zonely.whiteeffect.libraries.profile.Mojang;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.reflection.Accessors;
import dev.zonely.whiteeffect.reflection.acessors.MethodAccessor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

public class Manager {
   public static boolean BUNGEE;
   private static Object PROXY_SERVER;
   private static MethodAccessor GET_NAME;
   private static MethodAccessor GET_PLAYER;
   private static MethodAccessor GET_SPIGOT;
   private static MethodAccessor HAS_PERMISSION;
   private static MethodAccessor SEND_MESSAGE;
   private static MethodAccessor SEND_MESSAGE_COMPONENTS;
   private static MethodAccessor IS_FAKE;
   private static MethodAccessor GET_CURRENT;
   private static MethodAccessor GET_FAKE;
   private static MethodAccessor GET_FAKE_ROLE;

   public static String getSkin(String player, String type) {
      try {
         String id = Mojang.getUUID(player);
         if (id != null) {
            String textures = Mojang.getSkinProperty(id);
            if (textures != null) {
               return type.equalsIgnoreCase("value") ? textures.split(" : ")[1] : textures.split(" : ")[2];
            }
         }
      } catch (Exception var4) {
      }

      return BUNGEE ? "eyJ0aW1lc3RhbXAiOjE1ODcxNTAzMTc3MjAsInByb2ZpbGVJZCI6IjRkNzA0ODZmNTA5MjRkMzM4NmJiZmM5YzEyYmFiNGFlIiwicHJvZmlsZU5hbWUiOiJzaXJGYWJpb3pzY2hlIiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8xYTRhZjcxODQ1NWQ0YWFiNTI4ZTdhNjFmODZmYTI1ZTZhMzY5ZDE3NjhkY2IxM2Y3ZGYzMTlhNzEzZWI4MTBiIn19fQ==:syZ2Mt1vQeEjh/t8RGbv810mcfTrhQvnwEV7iLCd+5udVeroTa5NjoUehgswacTML3k/KxHZHaq4o6LmACHwsj/ivstW4PWc2RmVn+CcOoDKI3ytEm70LvGz0wAaTVKkrXHSw/RbEX/b7g7oQ8F67rzpiZ1+Z3TKaxbgZ9vgBQZQdwRJjVML2keI0669a9a1lWq3V/VIKFZc1rMJGzETMB2QL7JVTpQFOH/zXJGA+hJS5bRol+JG3LZTX93+DililM1e8KEjKDS496DYhMAr6AfTUfirLAN1Jv+WW70DzIpeKKXWR5ZeI+9qf48+IvjG8DhRBVFwwKP34DADbLhuebrolF/UyBIB9sABmozYdfit9uIywWW9+KYgpl2EtFXHG7CltIcNkbBbOdZy0Qzq62Tx6z/EK2acKn4oscFMqrobtioh5cA/BCRb9V4wh0fy5qx6DYHyRBdzLcQUfb6DkDx1uyNJ7R5mO44b79pSo8gdd9VvMryn/+KaJu2UvyCrMVUtOOzoIh4nCMc9wXOFW3jZ7ZTo4J6c28ouL98rVQSAImEd/P017uGvWIT+hgkdXnacVG895Y6ilXqJToyvf1JUQb4dgry0WTv6UTAjNgrm5a8mZx9OryLuI2obas97LCon1rydcNXnBtjUk0TUzdrvIa5zNstYZPchUb+FSnU=" : "eyJ0aW1lc3RhbXAiOjE1ODcxMzkyMDU4MzUsInByb2ZpbGVJZCI6Ijc1MTQ0NDgxOTFlNjQ1NDY4Yzk3MzlhNmUzOTU3YmViIiwicHJvZmlsZU5hbWUiOiJUaGFua3NNb2phbmciLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzNiNjBhMWY2ZDU2MmY1MmFhZWJiZjE0MzRmMWRlMTQ3OTMzYTNhZmZlMGU3NjRmYTQ5ZWEwNTc1MzY2MjNjZDMiLCJtZXRhZGF0YSI6eyJtb2RlbCI6InNsaW0ifX19fQ==:W60UUuAYlWfLFt5Ay3Lvd/CGUbKuuU8+HTtN/cZLhc0BC22XNgbY1btTite7ZtBUGiZyFOhYqQi+LxVWrdjKEAdHCSYWpCRMFhB1m0zEfu78yg4XMcFmd1v7y9ZfS45b3pLAJ463YyjDaT64kkeUkP6BUmgsTA2iIWvM33k6Tj3OAM39kypFSuH+UEpkx603XtxratD+pBjUCUvWyj2DMxwnwclP/uACyh0ZVrI7rC5xJn4jSura+5J2/j6Z/I7lMBBGLESt7+pGn/3/kArDE/1RShOvm5eYKqrTMRfK4n3yd1U1DRsMzxkU2AdlCrv1swT4o+Cq8zMI97CF/xyqk8z2L98HKlzLjtvXIE6ogljyHc9YsfU9XhHwZ7SKXRNkmHswOgYIQCSa1RdLHtlVjN9UdUyUoQIIO2AWPzdKseKJJhXwqKJ7lzfAtStErRzDjmjr7ld/5tFd3TTQZ8yiq3D6aRLRUnOMTr7kFOycPOPhOeZQlTjJ6SH3PWFsdtMMQsGzb2vSukkXvJXFVUM0TcwRZlqT5MFHyKBBPprIt0wVN6MmSKc8m5kdk7ZBU2ICDs/9Cd/fyzAIRDu3Kzm7egbAVK9zc1kXwGzowUkGGy1XvZxyRS5jF1zu6KzVgaXOGcrOLH4z/OHzxvbyW22/UwahWGN7MD4j37iJ7gjZDrk=";
   }

   public static void sendMessage(Object player, String message) {
      if (BUNGEE) {
         sendMessage(player, TextComponent.fromLegacyText(message));
      } else {
         SEND_MESSAGE.invoke(player, message);
      }
   }

   public static void sendMessage(Object player, BaseComponent... components) {
      SEND_MESSAGE_COMPONENTS.invoke(BUNGEE ? player : GET_SPIGOT.invoke(player), components);
   }

   public static String getName(Object player) {
      return (String)GET_NAME.invoke(player);
   }

   public static Object getPlayer(String name) {
      return GET_PLAYER.invoke(BUNGEE ? PROXY_SERVER : null, name);
   }

   public static String getCurrent(String playerName) {
      return GET_CURRENT != null ? (String)GET_CURRENT.invoke((Object)null, playerName) : playerName;
   }

   public static String getFake(String playerName) {
      return GET_FAKE != null ? (String)GET_FAKE.invoke((Object)null, playerName) : playerName;
   }

   public static Role getFakeRole(String playerName) {
      return GET_FAKE_ROLE != null ? (Role)GET_FAKE_ROLE.invoke((Object)null, playerName) : Role.getLastRole();
   }

   public static boolean hasPermission(Object player, String permission) {
      return (Boolean)HAS_PERMISSION.invoke(player, permission);
   }

   public static boolean isFake(String playerName) {
      return IS_FAKE != null && (Boolean)IS_FAKE.invoke((Object)null, playerName);
   }

   static {
      Class player;
      Class spigot;
      try {
         Class<?> proxyServer = Class.forName("net.md_5.bungee.api.ProxyServer");
         player = Class.forName("net.md_5.bungee.api.connection.ProxiedPlayer");
         spigot = Class.forName("dev.zonely.whiteeffect.bungee.Bungee");
         PROXY_SERVER = Accessors.getMethod(proxyServer, "getInstance").invoke((Object)null);
         GET_NAME = Accessors.getMethod(player, "getName");
         GET_PLAYER = Accessors.getMethod(proxyServer, "getPlayer", String.class);
         HAS_PERMISSION = Accessors.getMethod(player, "hasPermission", String.class);
         SEND_MESSAGE_COMPONENTS = Accessors.getMethod(player, "sendMessage", BaseComponent[].class);
         IS_FAKE = Accessors.getMethod(spigot, "isFake", String.class);
         GET_CURRENT = Accessors.getMethod(spigot, "getCurrent", String.class);
         GET_FAKE = Accessors.getMethod(spigot, "getFake", String.class);
         GET_FAKE_ROLE = Accessors.getMethod(spigot, "getRole", String.class);
         BUNGEE = true;
      } catch (ClassNotFoundException var5) {
         try {
            player = Class.forName("org.bukkit.entity.Player");
            spigot = Class.forName("org.bukkit.entity.Player$Spigot");
            Class<?> fakeManager = null;
            if (isClassPresent("com.comphenix.protocol.wrappers.WrappedGameProfile")) {
               try {
                  fakeManager = Class.forName("dev.zonely.whiteeffect.player.fake.FakeManager");
               } catch (Throwable ignored) {
               }
            }
            GET_NAME = Accessors.getMethod(player, "getName");
            GET_PLAYER = Accessors.getMethod(Class.forName("dev.zonely.whiteeffect.player.Profile"), "findCached", String.class);
            HAS_PERMISSION = Accessors.getMethod(player, "hasPermission", String.class);
            SEND_MESSAGE = Accessors.getMethod(player, "sendMessage", String.class);
            GET_SPIGOT = Accessors.getMethod(player, "spigot");
            SEND_MESSAGE_COMPONENTS = Accessors.getMethod(spigot, "sendMessage", BaseComponent[].class);
            if (fakeManager != null) {
               IS_FAKE = Accessors.getMethod(fakeManager, "isFake", String.class);
               GET_CURRENT = Accessors.getMethod(fakeManager, "getCurrent", String.class);
               GET_FAKE = Accessors.getMethod(fakeManager, "getFake", String.class);
               GET_FAKE_ROLE = Accessors.getMethod(fakeManager, "getRole", String.class);
            }
         } catch (ClassNotFoundException var4) {
            var4.printStackTrace();
         }
      }

   }

   private static boolean isClassPresent(String className) {
      try {
         Class.forName(className, false, Manager.class.getClassLoader());
         return true;
      } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
         return false;
      }
   }
}
