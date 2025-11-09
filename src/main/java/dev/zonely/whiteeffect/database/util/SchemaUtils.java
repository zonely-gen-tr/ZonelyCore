package dev.zonely.whiteeffect.database.util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class SchemaUtils {

    private SchemaUtils() { }

    public static boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        if (hasTable(meta, connection.getCatalog(), tableName)) {
            return true;
        }
        String lower = tableName.toLowerCase(Locale.ROOT);
        if (!lower.equals(tableName) && hasTable(meta, connection.getCatalog(), lower)) {
            return true;
        }
        String upper = tableName.toUpperCase(Locale.ROOT);
        return !upper.equals(tableName) && hasTable(meta, connection.getCatalog(), upper);
    }

    private static boolean hasTable(DatabaseMetaData meta, String catalog, String table) throws SQLException {
        try (ResultSet rs = meta.getTables(catalog, null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    public static boolean schemaMatches(Connection connection, String tableName, String createSql) throws SQLException {
        Set<String> expected = parseColumnNames(createSql);
        if (expected.isEmpty()) {
            return true;
        }

        Set<String> actual = loadColumns(connection, tableName);
        if (actual.isEmpty()) {
            return false;
        }

        return actual.equals(expected);
    }

    public static Set<String> parseColumnNames(String createSql) {
        Set<String> columns = new LinkedHashSet<>();
        if (createSql == null) {
            return columns;
        }

        String normalized = createSql.replace('\n', ' ').replace('\r', ' ');
        int start = normalized.indexOf('(');
        if (start < 0) {
            return columns;
        }

        int end = normalized.toUpperCase(Locale.ROOT).indexOf("PRIMARY KEY");
        if (end == -1) {
            end = normalized.lastIndexOf(')');
        }
        if (end <= start) {
            return columns;
        }

        String[] definitions = normalized.substring(start + 1, end).split(",");
        for (String definition : definitions) {
            definition = definition.trim();
            if (!definition.startsWith("`")) {
                continue;
            }
            int close = definition.indexOf('`', 1);
            if (close < 0) {
                continue;
            }
            String column = definition.substring(1, close).trim();
            if (!column.isEmpty()) {
                columns.add(column.toLowerCase(Locale.ROOT));
            }
        }

        return columns;
    }

    private static Set<String> loadColumns(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        Set<String> columns = new LinkedHashSet<>();
        loadColumns(meta, connection.getCatalog(), tableName, columns);
        if (!columns.isEmpty()) {
            return columns;
        }

        String lower = tableName.toLowerCase(Locale.ROOT);
        if (!lower.equals(tableName)) {
            loadColumns(meta, connection.getCatalog(), lower, columns);
        }
        if (!columns.isEmpty()) {
            return columns;
        }

        String upper = tableName.toUpperCase(Locale.ROOT);
        if (!upper.equals(tableName)) {
            loadColumns(meta, connection.getCatalog(), upper, columns);
        }

        return columns;
    }

    private static void loadColumns(DatabaseMetaData meta, String catalog, String tableName, Set<String> target) throws SQLException {
        try (ResultSet rs = meta.getColumns(catalog, null, tableName, null)) {
            while (rs.next()) {
                String column = rs.getString("COLUMN_NAME");
                if (column != null) {
                    target.add(column.toLowerCase(Locale.ROOT));
                }
            }
        }
    }
}
