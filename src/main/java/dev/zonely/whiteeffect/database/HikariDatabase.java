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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
   private HikariDataSource dataSource;

   public HikariDatabase(String host, String port, String dbname, String username, String password, boolean mariadb) {
      this.host = host;
      this.port = port;
      this.dbname = dbname;
      this.username = username;
      this.password = password;
      this.mariadb = mariadb;
      this.openConnection();
      this.executor = Executors.newCachedThreadPool();
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
               if (rs != null) {
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
               if (rs != null) {
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
            if (rs != null) {
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
      this.executor.shutdownNow().forEach(Runnable::run);
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
               CachedRowSet rs = this.query(table.getInfo().select(), name.toLowerCase());

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
         this.execute(table.getInfo().insert(), list.toArray());
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

   public String exists(String name) {
      try {
         return this.query("SELECT `name` FROM `ZonelyCoreProfile` WHERE LOWER(`name`) = ?", name.toLowerCase()).getString("name");
      } catch (Exception var3) {
         return null;
      }
   }

   public void openConnection() {
      LOGGER.info("§6MySQL §fserver connection initializing...");
      HikariConfig config = new HikariConfig();
      config.setPoolName("wConnectionPool");
      config.setMaximumPoolSize(32);
      config.setConnectionTimeout(30000L);
      config.setDriverClassName(this.mariadb ? "org.mariadb.jdbc.Driver" : "com.mysql.jdbc.Driver");
      config.setJdbcUrl((this.mariadb ? "jdbc:mariadb://" : "jdbc:mysql://") + this.host + ":" + this.port + "/" + this.dbname);
      config.setUsername(this.username);
      config.setPassword(this.password);
      config.addDataSourceProperty("autoReconnect", "true");
      this.dataSource = new HikariDataSource(config);
      LOGGER.info("§6MySQL §fserver connection established.");
   }

   public void closeConnection() {
      if (this.isConnected()) {
         this.dataSource.close();
      }

   }

   public Connection getConnection() throws SQLException {
      return this.dataSource.getConnection();
   }

   public boolean isConnected() {
      return !this.dataSource.isClosed();
   }

   public void update(String sql, Object... vars) {
      Connection connection = null;
      PreparedStatement ps = null;

      try {
         connection = this.getConnection();
         ps = connection.prepareStatement(sql);

         for(int i = 0; i < vars.length; ++i) {
            ps.setObject(i + 1, vars[i]);
         }

         ps.executeUpdate();
      } catch (SQLException var18) {
         LOGGER.log(Level.WARNING, "Unable to obtain SQL connection: ", var18);
      } finally {
         try {
            if (connection != null && !connection.isClosed()) {
               connection.close();
            }
         } catch (SQLException var17) {
            var17.printStackTrace();
         }

         try {
            if (ps != null && !ps.isClosed()) {
               ps.close();
            }
         } catch (SQLException var16) {
            var16.printStackTrace();
         }

      }

   }

   public void execute(String sql, Object... vars) {
      this.executor.execute(() -> {
         this.update(sql, vars);
      });
   }

   public int updateWithInsertId(String sql, Object... vars) {
      int id = -1;
      Connection connection = null;
      PreparedStatement ps = null;
      ResultSet rs = null;

      try {
         connection = this.getConnection();
         ps = connection.prepareStatement(sql, 1);

         for(int i = 0; i < vars.length; ++i) {
            ps.setObject(i + 1, vars[i]);
         }

         ps.execute();
         rs = ps.getGeneratedKeys();
         if (rs.next()) {
            id = rs.getInt(1);
         }
      } catch (SQLException var24) {
         LOGGER.log(Level.WARNING, "Unable to obtain SQL connection: ", var24);
      } finally {
         try {
            if (connection != null && !connection.isClosed()) {
               connection.close();
            }
         } catch (SQLException var23) {
            var23.printStackTrace();
         }

         try {
            if (ps != null && !ps.isClosed()) {
               ps.close();
            }
         } catch (SQLException var22) {
            var22.printStackTrace();
         }

         try {
            if (rs != null && !rs.isClosed()) {
               rs.close();
            }
         } catch (SQLException var21) {
            var21.printStackTrace();
         }

      }

      return id;
   }

   public CachedRowSet query(String query, Object... vars) {
      Connection connection = null;
      PreparedStatement ps = null;
      ResultSet rs = null;
      CachedRowSet rowSet = null;

      try {
         connection = this.getConnection();
         ps = connection.prepareStatement(query);

         for(int i = 0; i < vars.length; ++i) {
            ps.setObject(i + 1, vars[i]);
         }

         rs = ps.executeQuery();
         rowSet = RowSetProvider.newFactory().createCachedRowSet();
         rowSet.populate(rs);
         if (!rowSet.next()) {
            rowSet.close();
            rowSet = null;
         } else {
            rowSet.beforeFirst();
         }
      } catch (SQLException var28) {
         LOGGER.log(Level.WARNING, "Failed to execute query: ", var28);
      } finally {
         try {
            if (connection != null && !connection.isClosed()) {
               connection.close();
            }
         } catch (SQLException var27) {
            var27.printStackTrace();
         }

         try {
            if (ps != null && !ps.isClosed()) {
               ps.close();
            }
         } catch (SQLException var26) {
            var26.printStackTrace();
         }

         try {
            if (rs != null && !rs.isClosed()) {
               rs.close();
            }
         } catch (SQLException var25) {
            var25.printStackTrace();
         }

      }

      return null;
   }

   private void ensureTableStructure(String tableName, String createSql) {
      boolean rebuild = false;
      boolean exists = false;
      try (Connection connection = this.getConnection()) {
         exists = SchemaUtils.tableExists(connection, tableName);
         if (exists && !SchemaUtils.schemaMatches(connection, tableName, createSql)) {
            rebuild = true;
         }
      } catch (SQLException ex) {
         LOGGER.log(Level.WARNING, "[Database] Failed to inspect table " + tableName + ". Forcing rebuild.", ex);
         rebuild = true;
      }

      if (rebuild) {
         LOGGER.warning("[Database] Detected schema mismatch for table " + tableName + ". Rebuilding table to match plugin definition.");
         this.update("DROP TABLE IF EXISTS `" + tableName + "`");
         exists = false;
      }

      if (!exists || rebuild) {
         this.update(createSql);
      }
   }
}
