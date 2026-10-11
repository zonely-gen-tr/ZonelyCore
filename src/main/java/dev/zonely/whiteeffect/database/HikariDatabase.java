package dev.zonely.whiteeffect.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.Manager;
import dev.zonely.whiteeffect.booster.NetworkBooster;
import dev.zonely.whiteeffect.database.cache.RoleCache;
import dev.zonely.whiteeffect.database.data.DataContainer;
import dev.zonely.whiteeffect.database.data.DataTable;
import dev.zonely.whiteeffect.database.exception.ProfileLoadException;
import dev.zonely.whiteeffect.database.util.ChatBridgeSchema;
import dev.zonely.whiteeffect.database.util.SchemaUtils;
import dev.zonely.whiteeffect.player.role.Role;
import dev.zonely.whiteeffect.utils.StringUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class HikariDatabase extends Database {
   private final String host;
   private final String port;
   private final String dbname;
   private final String username;
   private final String password;
   private final boolean mariadb;
   private final ExecutorService executor;
   private final DatabaseBackoff reconnectBackoff = new DatabaseBackoff();
   private final AtomicLong nextErrorLogAt = new AtomicLong();
   private HikariDataSource dataSource;

   public HikariDatabase(String host, String port, String dbname, String username, String password, boolean mariadb) {
      this.host = host;
      this.port = port;
      this.dbname = dbname;
      this.username = username;
      this.password = password;
      this.mariadb = mariadb;
      this.openConnection();
      // Bounded workers and queue: outages must not accumulate unlimited write jobs in RAM.
      this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
              new ArrayBlockingQueue<>(1024), runnable -> {
                 Thread thread = new Thread(runnable, "zonely-db-writer");
                 thread.setDaemon(true);
                 return thread;
              }, new ThreadPoolExecutor.AbortPolicy());
      ensureTableStructure("ZonelyCoreNetworkBooster",
              "CREATE TABLE IF NOT EXISTS `ZonelyCoreNetworkBooster` (`id` VARCHAR(32), `booster` TEXT, `multiplier` DOUBLE, `expires` LONG, PRIMARY KEY(`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;");
      ChatBridgeSchema.tables().forEach(this::ensureTableStructure);
      DataTable.listTables().forEach((table) -> {
         ensureTableStructure(table.getInfo().name(), table.getInfo().create());

         try {
            Connection connection = this.getConnection();

            try {
               PreparedStatement ps = connection.prepareStatement("ALTER TABLE `" + table.getInfo().name() + "` ADD INDEX `namex` (`name` DESC)");

               try {
                  ps.executeUpdate();
               } catch (Throwable var8) {
                  if (ps != null) {
                     try {
                        ps.close();
                     } catch (Throwable var7) {
                        var8.addSuppressed(var7);
                     }
                  }

                  throw var8;
               }

               if (ps != null) {
                  ps.close();
               }
            } catch (Throwable var9) {
               if (connection != null) {
                  try {
                     connection.close();
                  } catch (Throwable var6) {
                     var9.addSuppressed(var6);
                  }
               }

               throw var9;
            }

            if (connection != null) {
               connection.close();
            }
         } catch (SQLException var10) {
         }

         table.init(this);
      });
   }

   public void setupBoosters() {
      if (!Manager.BUNGEE) {
         Iterator var1 = Core.minigames.iterator();

         while(var1.hasNext()) {
            String mg = (String)var1.next();
            if (this.query("SELECT * FROM `ZonelyCoreNetworkBooster` WHERE `id` = ?", mg) == null) {
               this.execute("INSERT INTO `ZonelyCoreNetworkBooster` VALUES (?, ?, ?, ?)", mg, "WhiteEffect", 1.0D, 0L);
            }
         }
      }
   }

   public void setBooster(String minigame, String booster, double multiplier, long expires) {
      this.execute("UPDATE `ZonelyCoreNetworkBooster` SET `booster` = ?, `multiplier` = ?, `expires` = ? WHERE `id` = ?", booster, multiplier, expires, minigame);
   }

   public NetworkBooster getBooster(String minigame) {
      try {
         CachedRowSet rs = this.query("SELECT * FROM `ZonelyCoreNetworkBooster` WHERE `id` = ?", minigame);

         NetworkBooster var8;
         label52: {
            try {
               if (rs != null && rs.next()) {
                  String booster = rs.getString("booster");
                  double multiplier = rs.getDouble("multiplier");
                  long expires = rs.getLong("expires");
                  if (expires > System.currentTimeMillis()) {
                     rs.close();
                     var8 = new NetworkBooster(booster, multiplier, expires);
                     break label52;
                  }
               }
            } catch (Throwable var10) {
               if (rs != null) {
                  try {
                     rs.close();
                  } catch (Throwable var9) {
                     var10.addSuppressed(var9);
                  }
               }

               throw var10;
            }

            if (rs != null) {
               rs.close();
            }

            return null;
         }

         if (rs != null) {
            rs.close();
         }

         return var8;
      } catch (SQLException var11) {
         return null;
      }
   }

   public String getRankAndName(String player) {
      try {
         CachedRowSet rs = this.query("SELECT `name`, `role` FROM `ZonelyCoreProfile` WHERE LOWER(`name`) = ?", player.toLowerCase());

         String var4;
         label50: {
            try {
               if (rs != null && rs.next()) {
                  String result = rs.getString("role") + " : " + rs.getString("name");
                  RoleCache.setCache(player, rs.getString("role"), rs.getString("name"));
                  var4 = result;
                  break label50;
               }
            } catch (Throwable var6) {
               if (rs != null) {
                  try {
                     rs.close();
                  } catch (Throwable var5) {
                     var6.addSuppressed(var5);
                  }
               }

               throw var6;
            }

            if (rs != null) {
               rs.close();
            }

            return null;
         }

         if (rs != null) {
            rs.close();
         }

         return var4;
      } catch (SQLException var7) {
         return null;
      }
   }

   public boolean getPreference(String player, String id, boolean def) {
      boolean preference = true;

      try {
         CachedRowSet rs = this.query("SELECT `preferences` FROM `ZonelyCoreProfile` WHERE LOWER(`name`) = ?", player.toLowerCase());

         try {
            if (rs != null && rs.next()) {
               preference = ((JSONObject)(new JSONParser()).parse(rs.getString("preferences"))).get(id).equals(0L);
            }
         } catch (Throwable var9) {
            if (rs != null) {
               try {
                  rs.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }
            }

            throw var9;
         }

         if (rs != null) {
            rs.close();
         }
      } catch (Exception var10) {
         var10.printStackTrace();
      }

      return preference;
   }

   public List<String[]> getLeaderBoard(String table, String... columns) {
      List<String[]> result = new ArrayList();
      StringBuilder add = new StringBuilder();
      StringBuilder select = new StringBuilder();
      String[] var6 = columns;
      int var7 = columns.length;

      for(int var8 = 0; var8 < var7; ++var8) {
         String column = var6[var8];
         add.append("`").append(column).append("` + ");
         select.append("`").append(column).append("`, ");
      }

      try {
         CachedRowSet rs = this.query("SELECT " + select + "`name` FROM `" + table + "` ORDER BY " + add + " 0 DESC LIMIT 10");

         try {
            if (rs != null) {
               rs.beforeFirst();

               while(rs.next()) {
                  long count = 0L;
                  String[] var18 = columns;
                  int var10 = columns.length;

                  for(int var11 = 0; var11 < var10; ++var11) {
                     String column = var18[var11];
                     count += rs.getLong(column);
                  }

                  result.add(new String[]{Role.getColored(rs.getString("name"), true), StringUtils.formatNumber(count)});
               }
            }
         } catch (Throwable var14) {
            if (rs != null) {
               try {
                  rs.close();
               } catch (Throwable var13) {
                  var14.addSuppressed(var13);
               }
            }

            throw var14;
         }

         if (rs != null) {
            rs.close();
         }
      } catch (SQLException var15) {
      }

      return result;
   }

   public void close() {
      this.executor.shutdown();
      try {
         if (!this.executor.awaitTermination(5, TimeUnit.SECONDS)) {
            LOGGER.warning("Database writes did not finish before shutdown timeout.");
            this.executor.shutdownNow();
         }
      } catch (InterruptedException ex) {
         Thread.currentThread().interrupt();
         this.executor.shutdownNow();
      }
      this.closeConnection();
   }

   public Map<String, Map<String, DataContainer>> load(String name) throws ProfileLoadException {
      Map<String, Map<String, DataContainer>> tableMap = new HashMap();
      Iterator var3 = DataTable.listTables().iterator();

      while(var3.hasNext()) {
         DataTable table = (DataTable)var3.next();
         Map<String, DataContainer> containerMap = new LinkedHashMap();
         tableMap.put(table.getInfo().name(), containerMap);

         try {
            label65: {
               CachedRowSet rs = this.queryOrThrow(table.getInfo().select(), name.toLowerCase());

               label66: {
                  try {
                     if (rs != null && rs.next()) {
                        int column = 2;

                        while(true) {
                           if (column > rs.getMetaData().getColumnCount()) {
                              break label66;
                           }

                           containerMap.put(rs.getMetaData().getColumnName(column), new DataContainer(rs.getObject(column)));
                           ++column;
                        }
                     }
                  } catch (Throwable var10) {
                     if (rs != null) {
                        try {
                           rs.close();
                        } catch (Throwable var9) {
                           var10.addSuppressed(var9);
                        }
                     }

                     throw var10;
                  }

                  if (rs != null) {
                     rs.close();
                  }
                  break label65;
               }

               if (rs != null) {
                  rs.close();
               }
               continue;
            }
         } catch (SQLException var11) {
            throw new ProfileLoadException(var11.getMessage());
         }

         containerMap = table.getDefaultValues();
         tableMap.put(table.getInfo().name(), containerMap);
         List<Object> list = new ArrayList();
         list.add(name);
         list.addAll((Collection)containerMap.values().stream().map(DataContainer::get).collect(Collectors.toList()));
         // A new profile must exist before subsequent UPDATE statements run.
         if (!updateOnce(table.getInfo().insert(), list.toArray())) {
            throw new ProfileLoadException("Could not create profile row for " + table.getInfo().name());
         }
         list.clear();
      }

      return tableMap;
   }

   public void save(String name, Map<String, Map<String, DataContainer>> tableMap) {
      this.save0(name, tableMap, true);
   }

   public void saveSync(String name, Map<String, Map<String, DataContainer>> tableMap) {
      this.save0(name, tableMap, false);
   }


   private void save0(String name, Map<String, Map<String, DataContainer>> tableMap, boolean async) {
      for (DataTable table : DataTable.listTables()) {
         Map<String, DataContainer> rows = tableMap.get(table.getInfo().name());
         if (rows == null || rows.values().stream().noneMatch(DataContainer::isUpdated)) {
            continue;
         }

         List<Object> values = new ArrayList<>();
         Map<DataContainer, Object> snapshot = new LinkedHashMap<>();
         StringBuilder query = new StringBuilder("UPDATE `" + table.getInfo().name() + "` SET ");
         for (Map.Entry<String, DataContainer> collumn : rows.entrySet()) {
            if (collumn.getValue().isUpdated()) {
               Object value = collumn.getValue().get();
               values.add(value);
               snapshot.put(collumn.getValue(), value);
               query.append("`").append(collumn.getKey()).append("` = ?, ");
            }
         }
         query.deleteCharAt(query.length() - 1);
         query.deleteCharAt(query.length() - 1);
         query.append(" WHERE LOWER(`name`) = ?");
         values.add(name.toLowerCase());
         Runnable markSaved = () -> snapshot.forEach((container, value) -> {
            if (Objects.equals(container.get(), value)) {
               container.setUpdated(false);
            }
         });
         if (async) {
            this.queueUpdate(query.toString(), values.toArray(), markSaved);
         } else if (this.updateOnce(query.toString(), values.toArray())) {
            markSaved.run();
         }
      }
   }

   public String exists(String name) {
      try (CachedRowSet rs = this.query("SELECT `name` FROM `ZonelyCoreProfile` WHERE LOWER(`name`) = ?", name.toLowerCase())) {
         return rs != null && rs.next() ? rs.getString("name") : null;
      } catch (SQLException ex) {
         return null;
      }
   }

   public void openConnection() {
      LOGGER.info("§6MySQL §fserver connection initializing...");
      HikariConfig config = new HikariConfig();
      config.setPoolName("wConnectionPool");
      config.setMaximumPoolSize(5);
      config.setMinimumIdle(0);
      config.setConnectionTimeout(1500L);
      config.setValidationTimeout(1000L);
      config.setInitializationFailTimeout(2000L);
      // MariaDB JDBC is a declared runtime dependency and also supports MySQL servers.
      // The project does not package MySQL Connector/J.
      config.setDriverClassName("org.mariadb.jdbc.Driver");
      config.setJdbcUrl("jdbc:mariadb://" + this.host + ":" + this.port + "/" + this.dbname
              + "?connectTimeout=1500&socketTimeout=5000&useUnicode=true&characterEncoding=UTF-8");
      config.setUsername(this.username);
      config.setPassword(this.password);
      this.dataSource = new HikariDataSource(config);
      LOGGER.info("§6MySQL §fserver connection established.");
   }

   public void closeConnection() {
      if (this.dataSource != null && !this.dataSource.isClosed()) {
         this.dataSource.close();
      }

   }

   public Connection getConnection() throws SQLException {
      long remaining = reconnectBackoff.remainingMillis(System.currentTimeMillis());
      if (remaining > 0L) {
         throw new SQLTransientConnectionException("MySQL reconnect cooldown (" + remaining + " ms)", "08001");
      }
      try {
         Connection connection = this.dataSource.getConnection();
         reconnectBackoff.succeeded();
         return connection;
      } catch (SQLException ex) {
         reconnectBackoff.failed(System.currentTimeMillis());
         throw ex;
      }
   }

   public boolean isConnected() {
      return this.dataSource != null && !this.dataSource.isClosed();
   }

   public void update(String sql, Object... vars) {
      // Synchronous calls must never sleep on the Bukkit tick thread.
      updateOnce(sql, vars);
   }

   private boolean updateOnce(String sql, Object... vars) {
      try (Connection connection = this.getConnection();
           PreparedStatement ps = connection.prepareStatement(sql)) {

         for(int i = 0; i < vars.length; ++i) {
            ps.setObject(i + 1, vars[i]);
         }

         ps.executeUpdate();
         return true;
      } catch (SQLException ex) {
         if (isConnectionError(ex) && reconnectBackoff.remainingMillis(System.currentTimeMillis()) == 0L) {
            reconnectBackoff.failed(System.currentTimeMillis());
         }
         logFailure("Database update failed", ex);
         return false;
      }
   }

   public void execute(String sql, Object... vars) {
      queueUpdate(sql, vars, null);
   }

   private void queueUpdate(String sql, Object[] vars, Runnable success) {
      try {
         this.executor.execute(() -> {
            // Sleep/retry happens ONLY in the DB worker threads.
            // Keep one bounded worker waiting for recovery, preserving queued write order.
            // Shutdown interrupts this wait; non-connection SQL errors still stop immediately.
            while (!Thread.currentThread().isInterrupted()) {
               long cooldown = reconnectBackoff.remainingMillis(System.currentTimeMillis());
               if (cooldown > 0L) {
                  try {
                     Thread.sleep(cooldown);
                  } catch (InterruptedException ex) {
                     Thread.currentThread().interrupt();
                     return;
                  }
               }
               if (updateOnce(sql, vars)) {
                  if (success != null) {
                     success.run();
                  }
                  return;
               }
               // A syntax/constraint error doesn't trigger the reconnect gate.
               if (reconnectBackoff.remainingMillis(System.currentTimeMillis()) == 0L) {
                  return;
               }
            }
         });
      } catch (RejectedExecutionException ex) {
         logFailure("Database write queue full or stopping; write was not queued", ex);
      }
   }

   private boolean isConnectionError(SQLException ex) {
      String state = ex.getSQLState();
      return ex instanceof SQLTransientConnectionException || ex instanceof SQLRecoverableException
              || (state != null && state.startsWith("08"));
   }

   private void logFailure(String description, Exception error) {
      long now = System.currentTimeMillis();
      long previous = nextErrorLogAt.get();
      if (now >= previous && nextErrorLogAt.compareAndSet(previous, now + 15000L)) {
         LOGGER.log(Level.WARNING, description + "; further warnings suppressed for 15 seconds", error);
      }
   }

   public int updateWithInsertId(String sql, Object... vars) {
      int id = -1;
      try (Connection connection = this.getConnection();
           PreparedStatement ps = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

         for(int i = 0; i < vars.length; ++i) {
            ps.setObject(i + 1, vars[i]);
         }

         ps.execute();
         try (ResultSet rs = ps.getGeneratedKeys()) {
            if (rs.next()) {
               id = rs.getInt(1);
            }
         }
      } catch (SQLException ex) {
         LOGGER.log(Level.WARNING, "Database generated-key update failed", ex);
      }

      return id;
   }

   public CachedRowSet query(String query, Object... vars) {
      try {
         return queryOrThrow(query, vars);
      } catch (SQLException ex) {
         if (isConnectionError(ex) && reconnectBackoff.remainingMillis(System.currentTimeMillis()) == 0L) {
            reconnectBackoff.failed(System.currentTimeMillis());
         }
         logFailure("Failed to execute database query", ex);
         return null;
      }
   }

   private CachedRowSet queryOrThrow(String query, Object... vars) throws SQLException {
      try (Connection connection = this.getConnection();
           PreparedStatement ps = connection.prepareStatement(query)) {

         for(int i = 0; i < vars.length; ++i) {
            ps.setObject(i + 1, vars[i]);
         }

         CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
         try (ResultSet rs = ps.executeQuery()) {
            rowSet.populate(rs);
         }
         if (!rowSet.next()) {
            rowSet.close();
            return null;
         }
         rowSet.beforeFirst();
         return rowSet;
      }
   }

   private void ensureTableStructure(String tableName, String createSql) {
      boolean exists = false;
      try (Connection connection = this.getConnection()) {
         exists = SchemaUtils.tableExists(connection, tableName);
         if (exists && !SchemaUtils.schemaMatches(connection, tableName, createSql)) {
            LOGGER.warning("[Database] Schema mismatch in " + tableName + "; preserving existing data. Review migrations manually.");
         }
      } catch (SQLException ex) {
         LOGGER.log(Level.WARNING, "[Database] Failed to inspect " + tableName + "; refusing destructive migration.", ex);
      }
      if (!exists) {
         this.update(createSql);
      }
   }
}
