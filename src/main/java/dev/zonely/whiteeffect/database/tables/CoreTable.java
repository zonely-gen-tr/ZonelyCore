package dev.zonely.whiteeffect.database.tables;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.database.HikariDatabase;
import dev.zonely.whiteeffect.database.MySQLDatabase;
import dev.zonely.whiteeffect.database.data.DataContainer;
import dev.zonely.whiteeffect.database.data.DataTable;
import dev.zonely.whiteeffect.database.data.interfaces.DataTableInfo;

import javax.sql.rowset.CachedRowSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

@DataTableInfo(
   name = "ZonelyCoreProfile",
   create = "CREATE TABLE IF NOT EXISTS `ZonelyCoreProfile` (`name` VARCHAR(32), `cash` LONG, `role` TEXT, `deliveries` TEXT, `preferences` TEXT, `titles` TEXT, `boosters` TEXT, `achievements` TEXT, `selected` TEXT, `created` LONG, `clan` TEXT, `lang` TEXT, `lastlogin` LONG, PRIMARY KEY(`name`)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;",
   select = "SELECT * FROM `ZonelyCoreProfile` WHERE LOWER(`name`) = ?",
   insert = "INSERT IGNORE INTO `ZonelyCoreProfile` (`name`, `cash`, `role`, `deliveries`, `preferences`, `titles`, `boosters`, `achievements`, `selected`, `created`, `clan`, `lang`, `lastlogin`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
   update = "UPDATE `ZonelyCoreProfile` SET `cash` = ?, `role` = ?, `deliveries` = ?, `preferences` = ?, `titles` = ?, `boosters` = ?, `achievements` = ?, `selected` = ?, `created` = ?, `clan` = ?, `lang` = ?, `lastlogin` = ? WHERE LOWER(`name`) = ?"
)
public class CoreTable extends DataTable {
   public void init(Database database) {
      ensureColumn(database, "cash", "`cash` BIGINT DEFAULT 0");
      ensureColumn(database, "role", "`role` TEXT");
      ensureColumn(database, "deliveries", "`deliveries` TEXT");
      ensureColumn(database, "preferences", "`preferences` TEXT");
      ensureColumn(database, "titles", "`titles` TEXT");
      ensureColumn(database, "boosters", "`boosters` TEXT");
      ensureColumn(database, "achievements", "`achievements` TEXT");
      ensureColumn(database, "selected", "`selected` TEXT");
      ensureColumn(database, "created", "`created` BIGINT");
      ensureColumn(database, "clan", "`clan` TEXT");
      ensureColumn(database, "lang", "`lang` TEXT");
      ensureColumn(database, "lastlogin", "`lastlogin` BIGINT");
   }

   public Map<String, DataContainer> getDefaultValues() {
      Map<String, DataContainer> defaultValues = new LinkedHashMap();
      defaultValues.put("cash", new DataContainer(0L));
      defaultValues.put("role", new DataContainer("Membro"));
      defaultValues.put("deliveries", new DataContainer("{}"));
      defaultValues.put("preferences", new DataContainer("{\"pv\": 0, \"pm\": 0, \"bg\": 0, \"pl\": 0}"));
      defaultValues.put("titles", new DataContainer("[]"));
      defaultValues.put("boosters", new DataContainer("{}"));
      defaultValues.put("achievements", new DataContainer("[]"));
      defaultValues.put("selected", new DataContainer("{\"title\": \"0\", \"icon\": \"0\"}"));
      defaultValues.put("created", new DataContainer(System.currentTimeMillis()));
      defaultValues.put("clan", new DataContainer(""));
      defaultValues.put("lang", new DataContainer("en_US"));
      defaultValues.put("lastlogin", new DataContainer(System.currentTimeMillis()));
      return defaultValues;
   }

   private void ensureColumn(Database database, String column, String definition) {
      String checkSql = "SHOW COLUMNS FROM `ZonelyCoreProfile` LIKE '" + column + "'";
      try {
         boolean exists = false;
         if (database instanceof MySQLDatabase) {
            CachedRowSet rowSet = ((MySQLDatabase) database).query(checkSql);
            exists = rowSet != null && rowSet.next();
            if (rowSet != null) {
               rowSet.close();
            }
            if (!exists) {
               ((MySQLDatabase) database).execute("ALTER TABLE `ZonelyCoreProfile` ADD COLUMN " + definition);
            }
         } else if (database instanceof HikariDatabase) {
            CachedRowSet rowSet = ((HikariDatabase) database).query(checkSql);
            exists = rowSet != null && rowSet.next();
            if (rowSet != null) {
               rowSet.close();
            }
            if (!exists) {
               ((HikariDatabase) database).execute("ALTER TABLE `ZonelyCoreProfile` ADD COLUMN " + definition);
            }
         }
      } catch (SQLException ex) {
         Core.getInstance().getLogger().log(Level.WARNING, "[Database] Failed to ensure column {0} on ZonelyCoreProfile.", new Object[]{column});
         Core.getInstance().getLogger().log(Level.FINEST, "[Database] Column ensure error", ex);
      }
   }
}
