package dev.zonely.whiteeffect.database.conversor;

import com.mongodb.client.model.Filters;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.database.MongoDBDatabase;
import dev.zonely.whiteeffect.database.MySQLDatabase;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.nms.NMS;
import dev.zonely.whiteeffect.player.Profile;
import org.bson.Document;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import javax.sql.rowset.CachedRowSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MongoDBConversor implements Listener {

   private static final NumberFormat NUMBER_FORMAT = new DecimalFormat("###.#");
   private static final String DEFAULT_LOBBY_PREFIX = "&3Lobby &8->> ";
   private static final String DEFAULT_CONNECTION_SUMMARY = "{prefix}&fHost: &7{host}&f Port: &7{port}\n &fDatabase: &7{database}\n &fUser: &7{user}\n &fPassword: &7\"{password}\"";
   private static final String DEFAULT_INCORRECT_WARNING = "{prefix}&cIf the provided data is incorrect, the server will shut down!";
   private static final String DEFAULT_TABLE_COUNT_FAILURE = "{prefix}&cUnable to verify entry count for table &b{table}&c.";
   private static final String DEFAULT_TABLE_ORDER_HEADER = "&6Table order:";
   private static final String DEFAULT_TABLE_ORDER_ENTRY = " &7- &f{table}";
   private static final String DEFAULT_TABLE_COMPLETE = "{prefix}&6Processing for table &b{table} &6is complete.";
   private static final String DEFAULT_CONVERSION_COMPLETE = "{prefix}&eDatabase conversion completed (MySQL -> MongoDB)";
   private static final String DEFAULT_CONVERTER_CANCELLED = "{prefix}&cConverter cancelled!";
   private static final String DEFAULT_PROMPT_HOST = "{prefix}&eEnter the MySQL host address.";
   private static final String DEFAULT_PROMPT_DATABASE = "{prefix}&eEnter the MySQL database name.";
   private static final String DEFAULT_PROMPT_USERNAME = "{prefix}&eEnter the MySQL username.";
   private static final String DEFAULT_PROMPT_PASSWORD = "{prefix}&eEnter the MySQL password.";
   private static final String DEFAULT_CONVERSION_RUNNING = "{prefix}&cConversion is still running.";
   private static final String DEFAULT_ACTIONBAR_PROGRESS = "&aConverting &f{table}: &7{current}/{max} &8({percent}%)";
   public static String[] CONVERT;
   public static BukkitTask task;

   private static String resolvePrefix(Profile profile) {
      return LanguageManager.get(profile, "prefix.lobby", DEFAULT_LOBBY_PREFIX);
   }

   private static Object[] withPrefix(String prefix, Object... placeholders) {
      Object[] extended = Arrays.copyOf(placeholders, placeholders.length + 2);
      extended[placeholders.length] = "prefix";
      extended[placeholders.length + 1] = prefix;
      return extended;
   }

   private static void send(Player player, Profile profile, String prefix, String key, String def, Object... placeholders) {
      player.sendMessage(LanguageManager.get(profile, key, def, withPrefix(prefix, placeholders)));
   }

   private static void startConvert(Player player) {
      Profile profile = Profile.getProfile(player.getName());
      String prefix = resolvePrefix(profile);
      String host = CONVERT[0], port = CONVERT[1], database = CONVERT[2], user = CONVERT[3], password = CONVERT[4];
      send(player, profile, prefix, "database.mongodb.converter.connection-summary", DEFAULT_CONNECTION_SUMMARY,
            "host", host, "port", port, "database", database, "user", user, "password", password);
      send(player, profile, prefix, "database.mongodb.converter.incorrect-warning", DEFAULT_INCORRECT_WARNING);

      MongoDBDatabase mongoDB = (MongoDBDatabase) Database.getInstance();
      MySQLDatabase mysql = new MySQLDatabase(host, port, database, user, password, false, true);

      Map<String, Long> tables = new LinkedHashMap<>();
      for (String table : new String[]{"ZonelyCoreProfile", "ZonelyCoreTheBridge", "ZonelyCoreSkyWars", "ZonelyCoreBedWars", "ZonelyCosmetics", "ZonelyCoreMurder", "ZonelyMysteryBox", "ZonelyCoreNetworkBooster", "ZonelyMysteryBoxContent"}) {
         if (mysql.query("SELECT `table_name` FROM INFORMATION_SCHEMA.STATISTICS WHERE table_name = ?", table) != null) {
            try {
               tables.put(table, Long.parseLong(mysql.query("SELECT COUNT(*) FROM " + table).getObject(1).toString()));
            } catch (Exception ex) {
               send(player, profile, prefix, "database.mongodb.converter.table-count-failure", DEFAULT_TABLE_COUNT_FAILURE,
                     "table", table);
            }
         }
      }

      final List<String> tableQueue = new ArrayList<>(tables.keySet());
      player.sendMessage(LanguageManager.get(profile, "database.mongodb.converter.table-order.header", DEFAULT_TABLE_ORDER_HEADER));
      for (String table : tableQueue) {
         player.sendMessage(LanguageManager.get(profile, "database.mongodb.converter.table-order.entry", DEFAULT_TABLE_ORDER_ENTRY,
               "table", table));
      }
      player.sendMessage("");
      task = new BukkitRunnable() {
         private boolean running;
         private String currentTable = tableQueue.get(0);
         private long currentRow = 0, maxRows = tables.get(currentTable);
         private CachedRowSet rs;
         private final ExecutorService executor = Executors.newSingleThreadExecutor();

         @Override
         public void run() {
            if (rs == null) {
               rs = mysql.query("SELECT * FROM `" + this.currentTable + "` LIMIT " + currentRow + ", " + Math.min(currentRow + 1000, maxRows));
               if (rs == null) {
                  send(player, profile, prefix, "database.mongodb.converter.table-complete", DEFAULT_TABLE_COMPLETE,
                        "table", this.currentTable);
                  tableQueue.remove(0);
                  if (tableQueue.isEmpty()) {
                     mysql.close();
                     send(player, profile, prefix, "database.mongodb.converter.conversion-complete", DEFAULT_CONVERSION_COMPLETE);
                     cancel();
                     return;
                  }

                  this.currentTable = tableQueue.get(0);
                  this.currentRow = 0;
                  this.maxRows = tables.get(this.currentTable);
                  return;
               }
            }

            if (!running) {
               this.running = true;
               executor.execute(() -> {
                  String collection =
                          this.currentTable.equalsIgnoreCase("zcorenetworkbooster") || this.currentTable.equalsIgnoreCase("wmysteryboxcontent") ? this.currentTable : "Profile";
                  if (currentRow == 0) {
                     if (collection.equalsIgnoreCase("Profile")) {
                        if (this.currentTable.equalsIgnoreCase("ZonelyCoreProfile")) {
                           mongoDB.getDatabase().getCollection(collection).drop();
                        }
                     } else {
                        mongoDB.getDatabase().getCollection(collection).drop();
                     }
                  }
                  List<Document> documents = new ArrayList<>(rs.size());
                  try {
                     rs.beforeFirst();
                     while (rs.next()) {
                        documents.add(convertResultSetToDocument(this.currentTable, rs));
                        this.currentRow++;
                     }
                  } catch (SQLException ex) {
                     ex.printStackTrace();
                  } finally {
                     try {
                        rs.close();
                     } catch (SQLException ignore) {
                     }
                  }
                  if (collection.equalsIgnoreCase("Profile") && !this.currentTable.equalsIgnoreCase("ZonelyCoreProfile")) {
                     documents.forEach(document -> {
                        String _id = document.getString("_id");
                        document.remove("_id");
                        mongoDB.getCollection().updateOne(Filters.eq("_id", _id), new Document("$set", new Document(this.currentTable, document)));
                     });
                  } else {
                     mongoDB.getDatabase().getCollection(collection).insertMany(documents);
                  }
                  this.running = false;
                  this.rs = null;
               });
            }

            if (player.isOnline()) {
               NMS.sendActionBar(player,
                     LanguageManager.get(profile, "database.mongodb.converter.actionbar-progress", DEFAULT_ACTIONBAR_PROGRESS,
                           "table", this.currentTable,
                           "current", this.currentRow,
                           "max", this.maxRows,
                           "percent", NUMBER_FORMAT.format(((this.currentRow * 100.0) / this.maxRows))));
            }
         }
      }.runTaskTimerAsynchronously(Core.getInstance(), 0, 5L);
   }
   public static Document convertResultSetToDocument(String table, CachedRowSet rs) throws SQLException {
      Document document = new Document();
      if (table.contains("SkyWars") || table.contains("TheBridge")) {
         document.put("totalkills", rs.getLong("1v1kills") + rs.getLong("2v2kills"));
         document.put("totalwins", rs.getLong("1v1wins") + rs.getLong("2v2wins"));
         if (table.contains("TheBridge")) {
            document.put("totalpoints", rs.getLong("1v1points") + rs.getLong("2v2points"));
         }
      }

      for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) {
         String name = rs.getMetaData().getColumnName(column);
         if (name.equals("id") || name.equals("name")) {
            if (document.containsKey("_id")) {
               document.put(name, rs.getObject(name));
               continue;
            }

            document.put("_id", rs.getObject(name));
            continue;
         }

         try {
            document.put(name, rs.getLong(name));
            continue;
         } catch (SQLException ignore) {
         }

         document.put(name, rs.getObject(name));
      }

      return document;
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onAsyncPlayerChat(AsyncPlayerChatEvent evt) {
      Player player = evt.getPlayer();
      if (CONVERT != null) {
         evt.setCancelled(true);
         String message = evt.getMessage();
         Profile profile = Profile.getProfile(player.getName());
         String prefix = resolvePrefix(profile);
         if (message.equalsIgnoreCase("cancel")
               || message.equalsIgnoreCase("canceling")
               || message.equalsIgnoreCase("iptal")) {
            CONVERT = null;
            if (task != null) {
               task.cancel();
               task = null;
            }
            send(player, profile, prefix, "database.mongodb.converter.cancelled", DEFAULT_CONVERTER_CANCELLED);
            return;
         }

         if (CONVERT[0] == null) {
            CONVERT[0] = message;
            send(player, profile, prefix, "database.mongodb.converter.prompt.host", DEFAULT_PROMPT_HOST);
         } else if (CONVERT[1] == null) {
            CONVERT[1] = message;
            send(player, profile, prefix, "database.mongodb.converter.prompt.database", DEFAULT_PROMPT_DATABASE);
         } else if (CONVERT[2] == null) {
            CONVERT[2] = message;
            send(player, profile, prefix, "database.mongodb.converter.prompt.username", DEFAULT_PROMPT_USERNAME);
         } else if (CONVERT[3] == null) {
            CONVERT[3] = message;
            send(player, profile, prefix, "database.mongodb.converter.prompt.password", DEFAULT_PROMPT_PASSWORD);
         } else if (CONVERT[4] == null) {
            if (message.equals("!")) {
               message = "";
            }
            CONVERT[4] = message;
            startConvert(player);
         } else {
            send(player, profile, prefix, "database.mongodb.converter.running", DEFAULT_CONVERSION_RUNNING);
         }
      }
   }
}