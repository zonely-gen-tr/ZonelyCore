package dev.zonely.whiteeffect.database;

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
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MySQLDatabase extends Database {

   private final String host;
   private final String port;
   private final String dbname;
   private final String username;
   private final String password;
   private final boolean mariadb;

   private Connection connection;
   private final ExecutorService executor;

   public MySQLDatabase(String host, String port, String dbname, String username, String password, boolean mariadb) {
      this(host, port, dbname, username, password, mariadb, false);
   }

   public MySQLDatabase(String host, String port, String dbname, String username, String password, boolean mariadb, boolean skipTables) {
      this.host = host;
      this.port = port;
      this.dbname = dbname;
      this.username = username;
      this.password = password;
      this.mariadb = mariadb;

      this.openConnection();
      this.executor = Executors.newCachedThreadPool();

      if (!skipTables) {
         ensureTableStructure("ZonelyCoreNetworkBooster",
                 "CREATE TABLE IF NOT EXISTS `ZonelyCoreNetworkBooster` (`id` VARCHAR(32), `booster` TEXT, `multiplier` DOUBLE, `expires` LONG, PRIMARY KEY(`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;");
         ChatBridgeSchema.tables().forEach(this::ensureTableStructure);

         DataTable.listTables().forEach(table -> {
            ensureTableStructure(table.getInfo().name(), table.getInfo().create());
            try (
               PreparedStatement ps = prepareStatement(
                  "ALTER TABLE `" + table.getInfo().name() + "` ADD INDEX `namex` (`name` DESC)"
               )
            ) {
               ps.executeUpdate();
            } catch (SQLException ignore) {
            }
            table.init(this);
         });

      }
   }

   @Override
   public void setupBoosters() {
      if (!Manager.BUNGEE) {
         for (String mg : Core.minigames) {
            execute("INSERT INTO `ZonelyCoreNetworkBooster` (`id`, `booster`, `multiplier`, `expires`) " +
                    "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE `booster` = VALUES(`booster`), " +
                    "`multiplier` = VALUES(`multiplier`), `expires` = VALUES(`expires`)", mg, "WhiteEffect", 1.0, 0L);
         }
      }
   }

   @Override
   public void setBooster(String minigame, String booster, double multiplier, long expires) {
      execute("UPDATE `ZonelyCoreNetworkBooster` SET `booster` = ?, `multiplier` = ?, `expires` = ? WHERE `id` = ?", booster, multiplier, expires, minigame);
   }

   @Override
   public NetworkBooster getBooster(String minigame) {
      try (CachedRowSet rs = query("SELECT * FROM `ZonelyCoreNetworkBooster` WHERE `id` = ?", minigame)) {
         if (rs != null) {
            String booster = rs.getString("booster");
            double multiplier = rs.getDouble("multiplier");
            long expires = rs.getLong("expires");
            if (expires > System.currentTimeMillis()) {
               rs.close();
               return new NetworkBooster(booster, multiplier, expires);
            }
         }
      } catch (SQLException ignored) {
      }
      return null;
   }

   @Override
   public String getRankAndName(String player) {
      try (CachedRowSet rs = query("SELECT `name`, `role` FROM `ZonelyCoreProfile` WHERE LOWER(`name`) = ?", player.toLowerCase())) {
         if (rs != null) {
            String result = rs.getString("role") + " : " + rs.getString("name");
            RoleCache.setCache(player, rs.getString("role"), rs.getString("name"));
            return result;
         }
      } catch (SQLException ignored) {
      }
      return null;
   }

   @Override
   public boolean getPreference(String player, String id, boolean def) {
      boolean preference = true;
      try (CachedRowSet rs = query("SELECT `preferences` FROM `ZonelyCoreProfile` WHERE LOWER(`name`) = ?", player.toLowerCase())) {
         if (rs != null) {
            preference = ((JSONObject) new JSONParser().parse(rs.getString("preferences"))).get(id).equals(0L);
         }
      } catch (Exception ex) {
         ex.printStackTrace();
      }
      return preference;
   }

   @Override
   public List<String[]> getLeaderBoard(String table, String... columns) {
      List<String[]> result = new ArrayList<>();
      StringBuilder add = new StringBuilder(), select = new StringBuilder();
      for (String column : columns) {
         add.append("`").append(column).append("` + ");
         select.append("`").append(column).append("`, ");
      }

      try (CachedRowSet rs = query("SELECT " + select + "`name` FROM `" + table + "` ORDER BY " + add + " 0 DESC LIMIT 10")) {
         if (rs != null) {
            rs.beforeFirst();
            while (rs.next()) {
               long count = 0;
               for (String column : columns) {
                  count += rs.getLong(column);
               }
               result.add(new String[]{Role.getColored(rs.getString("name"), true), StringUtils.formatNumber(count)});
            }
         }
      } catch (SQLException ignore) {
      }
      return result;
   }

   @Override
   public void close() {
      this.executor.shutdownNow().forEach(Runnable::run);
      this.closeConnection();
   }

   @Override
   public Map<String, Map<String, DataContainer>> load(String name) throws ProfileLoadException {
      Map<String, Map<String, DataContainer>> tableMap = new HashMap<>();
      for (DataTable table : DataTable.listTables()) {
         Map<String, DataContainer> containerMap = new LinkedHashMap<>();
         tableMap.put(table.getInfo().name(), containerMap);

         try (CachedRowSet rs = this.query(table.getInfo().select(), name.toLowerCase())) {
            if (rs != null && rs.next()) {
               for (int column = 2; column <= rs.getMetaData().getColumnCount(); column++) {
                  containerMap.put(rs.getMetaData().getColumnName(column), new DataContainer(rs.getObject(column)));
               }
               continue;
            }
         } catch (SQLException ex) {
            throw new ProfileLoadException(ex.getMessage());
         }

         containerMap = table.getDefaultValues();
         tableMap.put(table.getInfo().name(), containerMap);
         List<Object> list = new ArrayList<>();
         list.add(name);
         list.addAll(containerMap.values().stream().map(DataContainer::get).collect(Collectors.toList()));
         this.execute(table.getInfo().insert(), list.toArray());
         list.clear();
      }
      return tableMap;
   }

   @Override
   public void save(String name, Map<String, Map<String, DataContainer>> tableMap) {
      this.save0(name, tableMap, true);
   }

   @Override
   public void saveSync(String name, Map<String, Map<String, DataContainer>> tableMap) {
      this.save0(name, tableMap, false);
   }

   private void save0(String name, Map<String, Map<String, DataContainer>> tableMap, boolean async) {
      for (DataTable table : DataTable.listTables()) {
         Map<String, DataContainer> rows = tableMap.get(table.getInfo().name());
         if (rows.values().stream().noneMatch(DataContainer::isUpdated)) {
            continue;
         }

         List<Object> values = rows.values().stream().filter(DataContainer::isUpdated).map(DataContainer::get).collect(Collectors.toList());
         StringBuilder query = new StringBuilder("UPDATE `" + table.getInfo().name() + "` SET ");
         for (Map.Entry<String, DataContainer> collumn : rows.entrySet()) {
            if (collumn.getValue().isUpdated()) {
               collumn.getValue().setUpdated(false);
               query.append("`").append(collumn.getKey()).append("` = ?, ");
            }
         }
         query.deleteCharAt(query.length() - 1);
         query.deleteCharAt(query.length() - 1);
         query.append(" WHERE LOWER(`name`) = ?");
         values.add(name.toLowerCase());
         if (async) {
            this.execute(query.toString(), values.toArray());
         } else {
            this.update(query.toString(), values.toArray());
         }
         values.clear();
      }
   }

   @Override
   public String exists(String name) {
      try {
         return this.query("SELECT `name` FROM `ZonelyCoreProfile` WHERE LOWER(`name`) = ?", name.toLowerCase()).getString("name");
      } catch (Exception ex) {
         return null;
      }
   }

   public void openConnection() {
      try {
         boolean reconnected = this.connection != null;

         final String urlMySQL = "jdbc:mysql://" + host + ":" + port + "/" + dbname +
                 "?verifyServerCertificate=false&useSSL=false&useUnicode=yes&characterEncoding=UTF-8";
         final String urlMaria = "jdbc:mariadb://" + host + ":" + port + "/" + dbname +
                 "?useUnicode=yes&characterEncoding=UTF-8";

         Exception last = null;

         if (this.mariadb) {
            try {
               Class.forName("org.mariadb.jdbc.Driver");
               this.connection = DriverManager.getConnection(urlMaria, username, password);
            } catch (Exception e) {
               last = e;
            }
         } else {
            try {
               Class.forName("com.mysql.cj.jdbc.Driver");
               this.connection = DriverManager.getConnection(urlMySQL, username, password);
            } catch (Exception e1) {
               last = e1;
               try {
                  Class.forName("com.mysql.jdbc.Driver");
                  this.connection = DriverManager.getConnection(urlMySQL, username, password);
               } catch (Exception e2) {
                  last = e2;
                  try {
                     Class.forName("org.mariadb.jdbc.Driver");
                     this.connection = DriverManager.getConnection(urlMaria, username, password);
                  } catch (Exception e3) {
                     last = e3;
                  }
               }
            }
         }

         if (this.connection == null) {
            throw last != null ? last : new SQLException("Unknown driver/connection error");
         }

         if (!reconnected) {
            LOGGER.info("MySQL/MariaDB connection established.");
         }
      } catch (Exception ex) {
         throw new RuntimeException("Unable to connect to the database", ex);
      }
   }

   public void closeConnection() {
      if (isConnected()) {
         try {
            connection.close();
         } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Database connection close failed: ", e);
         }
      }
   }

   @Override
   public Connection getConnection() throws SQLException {
      if (!isConnected()) {
         this.openConnection();
      }
      return wrapConnection(this.connection);
   }

   private Connection wrapConnection(Connection delegate) {
      if (delegate == null) return null;
      ClassLoader cl = delegate.getClass().getClassLoader();
      return (Connection) java.lang.reflect.Proxy.newProxyInstance(
              cl,
              new Class[]{Connection.class},
              (proxy, method, args) -> {
                 String name = method.getName();
                 if ("close".equals(name)) {
                    return null;
                 }
                 try {
                    return method.invoke(delegate, args);
                 } catch (java.lang.reflect.InvocationTargetException ite) {
                    throw ite.getCause();
                 }
              }
      );
   }

   public boolean isConnected() {
      try {
         return !(connection == null || connection.isClosed() || !connection.isValid(5));
      } catch (SQLException ex) {
         LOGGER.log(Level.SEVERE, "Database connectivity check failed: ", ex);
         return false;
      }
   }

   public void update(String sql, Object... vars) {
      PreparedStatement ps = null;
      try {
         ps = prepareStatement(sql, vars);
         if (ps == null) {
            LOGGER.log(Level.WARNING, "Skipping database update; failed to prepare statement for SQL: {0}", sql);
            return;
         }
         ps.executeUpdate();
      } catch (SQLException ex) {
         LOGGER.log(Level.WARNING, "Database update failed for SQL: " + sql, ex);
      } finally {
         if (ps != null) {
            try {
               ps.close();
            } catch (SQLException ignored) {
            }
         }
      }
   }

   public void execute(String sql, Object... vars) {
      executor.execute(() -> {
         update(sql, vars);
      });
   }

   public int updateWithInsertId(String sql, Object... vars) {
      int id = -1;
      ResultSet rs = null;
      try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
         for (int i = 0; i < vars.length; i++) {
            ps.setObject(i + 1, vars[i]);
         }
         ps.execute();
         rs = ps.getGeneratedKeys();
         if (rs.next()) {
            id = rs.getInt(1);
         }
      } catch (SQLException ex) {
         LOGGER.log(Level.WARNING, "Database update (with generated keys) failed for SQL: " + sql, ex);
      } finally {
         try {
            if (rs != null && !rs.isClosed())
               rs.close();
         } catch (SQLException e) {
            e.printStackTrace();
         }
      }
      return id;
   }

   public PreparedStatement prepareStatement(String query, Object... vars) {
      try {
         PreparedStatement ps = getConnection().prepareStatement(query);
         for (int i = 0; i < vars.length; i++) {
            ps.setObject(i + 1, vars[i]);
         }
         return ps;
      } catch (SQLException ex) {
         LOGGER.log(Level.WARNING, "Failed to prepare statement for SQL: " + query, ex);
      }
      return null;
   }

   public CachedRowSet query(String query, Object... vars) {
      CachedRowSet rowSet = null;
      try {
         Future<CachedRowSet> future = executor.submit(() -> {
            try (PreparedStatement ps = prepareStatement(query, vars);
                 ResultSet rs = ps.executeQuery()) {
               CachedRowSet rs2 = RowSetProvider.newFactory().createCachedRowSet();
               rs2.populate(rs);
               if (!rs2.next()) {
                  rs2.close();
                  return null;
               }
               rs2.beforeFirst();
               return rs2;
            } catch (SQLException ex) {
               LOGGER.log(Level.WARNING, "Failed to execute query: ", ex);
               return null;
            }
         });

         rowSet = future.get();
      } catch (Exception ex) {
         LOGGER.log(Level.WARNING, "Unable to retrieve the scheduled task: ", ex);
      }
      return rowSet;
   }

   private void ensureTableStructure(String tableName, String createSql) {
      boolean rebuild = false;
      boolean exists = false;
      try {
         Connection connection = getConnection();
         exists = SchemaUtils.tableExists(connection, tableName);
         if (exists && !SchemaUtils.schemaMatches(connection, tableName, createSql)) {
            rebuild = true;
         }
      } catch (SQLException ex) {
         LOGGER.log(Level.WARNING, "[Database] Unable to validate table " + tableName + ". Forcing rebuild.", ex);
         rebuild = true;
      }

      if (rebuild) {
         LOGGER.warning("[Database] Detected schema mismatch for table " + tableName + ". Rebuilding table to match plugin definition.");
         update("DROP TABLE IF EXISTS `" + tableName + "`");
         exists = false;
      }

      if (!exists || rebuild) {
         update(createSql);
      }
   }
}

