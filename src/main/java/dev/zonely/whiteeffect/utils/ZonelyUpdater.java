package dev.zonely.whiteeffect.utils;

import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.nms.NMS;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.plugin.WPlugin;
import dev.zonely.whiteeffect.plugin.logger.WLogger;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ZonelyUpdater {
   public static ZonelyUpdater UPDATER;
   public boolean canDownload;
   private final WPlugin plugin;
   private final WLogger logger;
   private final int resourceId;

   public ZonelyUpdater(WPlugin plugin, int resourceId) {
      this.plugin = plugin;
      this.logger = ((WLogger)this.plugin.getLogger()).getModule("UPDATER");
      this.resourceId = resourceId;
   }

   public static Object getVersion(int resourceId) {
      try {
         String urlStr = "https://zonely.gen.tr/Files/Api/zonelycore.php";
         return getCheck(urlStr);
      } catch (Exception var2) {
         return null;
      }
   }

   public void run() {
      Bukkit.getConsoleSender().sendMessage(LanguageManager.get("updater.console.checking",
              "[Update] Checking for plugin updates."));
      String latest = (String)getVersion(this.resourceId);
      String current = this.plugin.getDescription().getVersion();
      if (latest == null || latest.trim().isEmpty()) {
         Bukkit.getConsoleSender().sendMessage(LanguageManager.get("updater.console.offline",
                 "[Update] Cannot check for updates while offline. Connect to the network and try again."));
         return;
      }

      int siteVersion;
      int pluginVersion;
      try {
         siteVersion = Integer.parseInt(latest.replace(".", ""));
         pluginVersion = Integer.parseInt(current.replace(".", ""));
      } catch (NumberFormatException ex) {
         Bukkit.getConsoleSender().sendMessage(LanguageManager.get("updater.console.offline",
                 "[Update] Cannot check for updates while offline. Connect to the network and try again."));
         this.logger.log(java.util.logging.Level.WARNING,
                 "Failed to parse version information from updater response: " + latest, ex);
         return;
      }

      if (pluginVersion >= siteVersion) {
        Bukkit.getConsoleSender().sendMessage(LanguageManager.get("updater.console.latest",
                "[Update] You are running the latest version. Thank you for staying up to date."));
        return;
      }

      Bukkit.getConsoleSender().sendMessage(LanguageManager.get("updater.console.available",
              "[Update] A newer version is available. Use /zc update to download it."));
      UPDATER = this;
      this.canDownload = true;
   }

   public void downloadUpdate(Player player) {
      Profile profile = Profile.getProfile(player.getName());
      LanguageManager.send(player, "updater.player.applying", "&3ZonelyCore &8-> &aApplying update...");

      try {
         File file = new File("plugins/ZonelyCore/update", "ZonelyCore.jar");
         if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
         }

         HttpsURLConnection connection = (HttpsURLConnection)(new URL("https://zonely.gen.tr/plugins/ZonelyCore.jar")).openConnection();
         connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
         int max = connection.getContentLength();
         BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
         FileOutputStream fos = new FileOutputStream(file);
         BufferedOutputStream bout = new BufferedOutputStream(fos, 1024);
         int progress = 0;
         if (max > 0) {
            int kb = Math.max(1, max / 1024);
            LanguageManager.send(player, "updater.player.size", "&3ZonelyCore &8-> &7Detected file size: &b{size}kb",
                    "size", kb);
         } else {
            LanguageManager.send(player, "updater.player.size-unknown", "&3ZonelyCore &8-> &7Detected file size: &cUnknown");
         }

         int oneChar;
         while ((oneChar = in.read()) != -1) {
            bout.write(oneChar);
            ++progress;
            if (max > 0 && progress % 1024 == 0) {
               int percentage = Math.max(0, Math.min(100, progress * 100 / max));
               int segments = Math.max(1, LanguageManager.getInt(profile, "updater.player.progress-bar.segments", 25));
               int filledSegments = Math.min(segments, Math.max(0, percentage * segments / 100));
               String filled = LanguageManager.get(profile, "updater.player.progress-bar.filled", "&a#");
               String empty = LanguageManager.get(profile, "updater.player.progress-bar.empty", "&7#");
               String bar = StringUtils.repeat(filled, filledSegments) + StringUtils.repeat(empty, segments - filledSegments);
               String action = LanguageManager.get(profile, "updater.player.progress-bar.template",
                       "{file} &7[{bar}]", "file", file.getName(), "bar", bar);
               NMS.sendActionBar(player, action);
            }
         }

         NMS.sendActionBar(player, LanguageManager.get(profile, "updater.player.restart",
                 "&aPlease restart the server to finish the update."));
         in.close();
         bout.close();
      } catch (Exception ex) {
         NMS.sendActionBar(player, LanguageManager.get(profile, "updater.player.download-error",
                 "&cUnable to download the update: {error}", "error", ex.getMessage()));
      }

   }

   public static String getCheck(String urlStr) {
      StringBuilder content = new StringBuilder();

      try {
         URL url = new URL(urlStr);
         HttpURLConnection connection = (HttpURLConnection)url.openConnection();
         connection.setRequestMethod("GET");
         int responseCode = connection.getResponseCode();
         if (responseCode != 200) {
            System.out.println("GET request failed. Response code: " + responseCode);
         } else {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            String inputLine;
            while((inputLine = in.readLine()) != null) {
               content.append(inputLine);
            }

            in.close();
         }

         connection.disconnect();
      } catch (Exception var7) {
         var7.printStackTrace();
      }

      return content.toString();
   }

   public static String getExternalIP() throws IOException {
      URL url = new URL("http://checkip.amazonaws.com");
      HttpURLConnection connection = (HttpURLConnection)url.openConnection();

      String var3;
      try {
         BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

         try {
            var3 = in.readLine();
         } catch (Throwable var10) {
            try {
               in.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }

            throw var10;
         }

         in.close();
      } finally {
         connection.disconnect();
      }

      return var3;
   }
}
