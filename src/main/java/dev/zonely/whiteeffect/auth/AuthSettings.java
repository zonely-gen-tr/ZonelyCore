package dev.zonely.whiteeffect.auth;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class AuthSettings {

    private static final Pattern DEFAULT_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    private final String algorithm;
    private final Pattern usernamePattern;

    private AuthSettings(String algorithm, Pattern usernamePattern) {
        this.algorithm = algorithm;
        this.usernamePattern = usernamePattern;
    }

    public static AuthSettings load(Database database) {
        String algorithm = "SHA256";
        Pattern pattern = DEFAULT_PATTERN;

        if (database == null) {
            Core.getInstance().getLogger().warning("[Auth] Database not initialised; using default auth settings.");
            return new AuthSettings(algorithm, pattern);
        }

        try (Connection connection = database.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM settings LIMIT 1");
                ResultSet rs = statement.executeQuery()) {

            if (rs.next()) {
                String crypt = readColumn(rs, "crypt");
                if (crypt == null || crypt.trim().isEmpty()) {
                    crypt = readColumn(rs, "cyrpto");
                }
                if (crypt != null && !crypt.trim().isEmpty()) {
                    algorithm = crypt.trim().toUpperCase(Locale.ROOT);
                }
                String regex = readColumn(rs, "regex");
                if (regex != null && !regex.trim().isEmpty()) {
                    try {
                        pattern = Pattern.compile(regex);
                    } catch (PatternSyntaxException ex) {
                        Core.getInstance().getLogger().log(Level.WARNING,
                                "[Auth] Invalid regex in settings table, using fallback.", ex);
                    }
                }
            }
        } catch (SQLException ex) {
            Core.getInstance().getLogger().log(Level.WARNING,
                    "[Auth] Unable to query settings table for crypt configuration.", ex);
        }

        return new AuthSettings(algorithm, pattern);
    }

    private static String readColumn(ResultSet rs, String column) throws SQLException {
        if (!hasColumn(rs, column)) {
            return null;
        }
        return rs.getString(column);
    }

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (SQLException ignored) {
            return false;
        }
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public Pattern getUsernamePattern() {
        return usernamePattern;
    }
}
