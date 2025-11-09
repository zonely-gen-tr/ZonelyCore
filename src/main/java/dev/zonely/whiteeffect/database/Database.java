package dev.zonely.whiteeffect.database;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.Manager;
import dev.zonely.whiteeffect.booster.NetworkBooster;
import dev.zonely.whiteeffect.database.cache.RoleCache;
import dev.zonely.whiteeffect.database.data.DataContainer;
import dev.zonely.whiteeffect.database.exception.ProfileLoadException;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.sql.Connection; 

public abstract class Database {
   public static Logger LOGGER;
   private static Database instance;
   public static final WConfig CONFIG = Core.getInstance().getConfig("config");
   public static String prefixCore;

   public static void setupDatabase(String type, String mysqlHost, String mysqlPort, String mysqlDbname, String mysqlUsername, String mysqlPassword, boolean hikari, boolean mariadb, String mongoURL) {
      LOGGER = Core.getInstance().getLogger();
      boolean failOpen = Core.getInstance().getConfig().getBoolean("database.fail-open", false);
      try {
         if (type.equalsIgnoreCase("mongodb")) {
            instance = new MongoDBDatabase(mongoURL);
         } else if (hikari) {
            instance = new HikariDatabase(mysqlHost, mysqlPort, mysqlDbname, mysqlUsername, mysqlPassword, mariadb);
         } else {
            instance = new MySQLDatabase(mysqlHost, mysqlPort, mysqlDbname, mysqlUsername, mysqlPassword, mariadb);
         }
      } catch (Throwable t) {
         if (failOpen) {
            LOGGER.warning("Database connection failed. Fail-open enabled: using NullDatabase (data stored in RAM and not persistent).");
            instance = new NullDatabase();
         } else {
            LOGGER.log(Level.SEVERE, "Database initialisation failed. Disabling plugin to prevent inconsistent state.", t);
            Bukkit.getPluginManager().disablePlugin(Core.getInstance());
            throw new IllegalStateException("Database initialisation aborted", t);
         }
      }

      (new Timer()).scheduleAtFixedRate(RoleCache.clearCache(), TimeUnit.SECONDS.toMillis(60L), TimeUnit.SECONDS.toMillis(60L));
   }

   public static Database getInstance() {
      return instance;
   }

   public void setupBoosters() {
   }

   public void convertDatabase(Object player) {
      if (!Manager.BUNGEE) {
         ((Player)player).sendMessage(prefixCore + "§cThis feature is not supported for your database type.");
      }
   }

   public abstract void setBooster(String var1, String var2, double var3, long var5);

   public abstract NetworkBooster getBooster(String var1);

   public abstract String getRankAndName(String var1);

   public abstract boolean getPreference(String var1, String var2, boolean var3);

   public abstract List<String[]> getLeaderBoard(String var1, String... var2);

   public abstract void close();

   public abstract Map<String, Map<String, DataContainer>> load(String var1) throws ProfileLoadException;

   public abstract void save(String var1, Map<String, Map<String, DataContainer>> var2);

   public abstract void saveSync(String var1, Map<String, Map<String, DataContainer>> var2);

   public abstract String exists(String var1);

   public abstract Connection getConnection() throws java.sql.SQLException;

   static {
      prefixCore = CONFIG.getString("language.prefix.lobby");
   }
}
