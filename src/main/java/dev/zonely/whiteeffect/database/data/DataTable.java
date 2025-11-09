package dev.zonely.whiteeffect.database.data;

import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.database.data.interfaces.DataTableInfo;
import dev.zonely.whiteeffect.database.tables.CoreTable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class DataTable {
   private static final List<DataTable> TABLES = new ArrayList();

   public static void registerTable(DataTable table) {
      TABLES.add(table);
   }

   public static Collection<DataTable> listTables() {
      return TABLES;
   }

   public abstract void init(Database var1);

   public abstract Map<String, DataContainer> getDefaultValues();

   public DataTableInfo getInfo() {
      return (DataTableInfo)this.getClass().getAnnotation(DataTableInfo.class);
   }

   static {
      TABLES.add(new CoreTable());
   }
}
