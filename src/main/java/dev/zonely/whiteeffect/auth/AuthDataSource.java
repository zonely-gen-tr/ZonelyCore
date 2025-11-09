package dev.zonely.whiteeffect.auth;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

public final class AuthDataSource {

    private final String backend;
    private final boolean caching;
    private final String host;
    private final String port;
    private final boolean useSsl;
    private final boolean checkServerCertificate;
    private final String username;
    private final String password;
    private final String database;
    private final int poolSize;
    private final long maxLifetime;
    private final Columns columns;
    private final Tables tables;

    private AuthDataSource(String backend,
            boolean caching,
            String host,
            String port,
            boolean useSsl,
            boolean checkServerCertificate,
            String username,
            String password,
            String database,
            int poolSize,
            long maxLifetime,
            Columns columns,
            Tables tables) {
        this.backend = backend;
        this.caching = caching;
        this.host = host;
        this.port = port;
        this.useSsl = useSsl;
        this.checkServerCertificate = checkServerCertificate;
        this.username = username;
        this.password = password;
        this.database = database;
        this.poolSize = poolSize;
        this.maxLifetime = maxLifetime;
        this.columns = columns;
        this.tables = tables;
    }

    public static AuthDataSource fromConfig(WConfig config) {
        ConfigurationSection root = config.getSection("auth.datasource");
        if (root == null) {
            Core.getInstance().getLogger()
                    .fine("[Auth] auth.datasource not found in config; using default column mappings.");
        }

        String backend = getString(root, "backend", "MYSQL");
        boolean caching = getBoolean(root, "caching", false);
        String host = getString(root, "mySQLHost", "localhost");
        String port = getString(root, "mySQLPort", "3306");
        boolean useSsl = getBoolean(root, "mySQLUseSSL", false);
        boolean checkCert = getBoolean(root, "mySQLCheckServerCertificate", true);
        String username = getString(root, "mySQLUsername", "root");
        String password = getString(root, "mySQLPassword", "");
        String database = getString(root, "mySQLDatabase", "");
        int poolSize = getInt(root, "poolSize", 10);
        long maxLifetime = getLong(root, "maxLifetime", 1_140_000L);

        Columns columns = new Columns(
                getString(root, "mySQLTablename", "userslist"),
                getString(root, "mySQLColumnId", "id"),
                getString(root, "mySQLColumnName", "subname"),
                getString(root, "mySQLRealName", "nick"),
                getString(root, "mySQLColumnPassword", "password"),
                getString(root, "mySQLColumnSalt", ""),
                getString(root, "mySQLColumnEmail", "email"),
                getString(root, "mySQLColumnLogged", ""),
                getString(root, "mySQLColumnHasSession", ""),
                getString(root, "mySQLtotpKey", "totp"),
                getString(root, "mySQLColumnIp", ""),
                getString(root, "mySQLColumnLastLogin", "lastlogin"),
                getString(root, "mySQLColumnRegisterDate", "created_at"),
                getString(root, "mySQLColumnRegisterIp", "created_ip"),
                getString(root, "mySQLlastlocX", ""),
                getString(root, "mySQLlastlocY", ""),
                getString(root, "mySQLlastlocZ", ""),
                getString(root, "mySQLlastlocWorld", ""),
                getString(root, "mySQLlastlocYaw", ""),
                getString(root, "mySQLlastlocPitch", ""),
                getString(root, "mySQLColumnTwoFactorStatus", "tfa_status"));

        ConfigurationSection tablesSection = config.getSection("auth.tables");
        Tables tables = new Tables(
                getString(tablesSection, "twofactor-keys", "userskey"),
                getString(tablesSection, "twofactor-recovery", "tfarecovers"),
                getString(tablesSection, "password-recovery", "recovers"),
                getString(tablesSection, "email-verification", "user_verifications"),
                getString(tablesSection, "sessions", "userssesions"));

        return new AuthDataSource(backend, caching, host, port, useSsl, checkCert, username, password, database,
                poolSize, maxLifetime, columns, tables);
    }

    private static String getString(ConfigurationSection section, String path, String def) {
        return section != null ? section.getString(path, def) : def;
    }

    private static boolean getBoolean(ConfigurationSection section, String path, boolean def) {
        return section != null ? section.getBoolean(path, def) : def;
    }

    private static int getInt(ConfigurationSection section, String path, int def) {
        return section != null ? section.getInt(path, def) : def;
    }

    private static long getLong(ConfigurationSection section, String path, long def) {
        return section != null ? section.getLong(path, def) : def;
    }

    public String getBackend() {
        return backend;
    }

    public boolean isCaching() {
        return caching;
    }

    public String getHost() {
        return host;
    }

    public String getPort() {
        return port;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public boolean isCheckServerCertificate() {
        return checkServerCertificate;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDatabase() {
        return database;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public long getMaxLifetime() {
        return maxLifetime;
    }

    public static final class Columns {
        private final String table;
        private final String id;
        private final String username;
        private final String displayName;
        private final String password;
        private final String salt;
        private final String email;
        private final String logged;
        private final String hasSession;
        private final String totpKey;
        private final String ip;
        private final String lastLogin;
        private final String registerDate;
        private final String registerIp;
        private final String lastLocX;
        private final String lastLocY;
        private final String lastLocZ;
        private final String lastLocWorld;
        private final String lastLocYaw;
        private final String lastLocPitch;
        private final String twoFactorStatus;

        private Columns(String table,
                String id,
                String username,
                String displayName,
                String password,
                String salt,
                String email,
                String logged,
                String hasSession,
                String totpKey,
                String ip,
                String lastLogin,
                String registerDate,
                String registerIp,
                String lastLocX,
                String lastLocY,
                String lastLocZ,
                String lastLocWorld,
                String lastLocYaw,
                String lastLocPitch,
                String twoFactorStatus) {
            this.table = Objects.requireNonNull(table, "table");
            this.id = Objects.requireNonNull(id, "id");
            this.username = Objects.requireNonNull(username, "username");
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            this.password = Objects.requireNonNull(password, "password");
            this.salt = salt == null ? "" : salt;
            this.email = email == null ? "" : email;
            this.logged = Objects.requireNonNull(logged, "logged");
            this.hasSession = Objects.requireNonNull(hasSession, "hasSession");
            this.totpKey = totpKey == null ? "" : totpKey;
            this.ip = Objects.requireNonNull(ip, "ip");
            this.lastLogin = Objects.requireNonNull(lastLogin, "lastLogin");
            this.registerDate = Objects.requireNonNull(registerDate, "registerDate");
            this.registerIp = Objects.requireNonNull(registerIp, "registerIp");
            this.lastLocX = Objects.requireNonNull(lastLocX, "lastLocX");
            this.lastLocY = Objects.requireNonNull(lastLocY, "lastLocY");
            this.lastLocZ = Objects.requireNonNull(lastLocZ, "lastLocZ");
            this.lastLocWorld = Objects.requireNonNull(lastLocWorld, "lastLocWorld");
            this.lastLocYaw = Objects.requireNonNull(lastLocYaw, "lastLocYaw");
            this.lastLocPitch = Objects.requireNonNull(lastLocPitch, "lastLocPitch");
            this.twoFactorStatus = Objects.requireNonNull(twoFactorStatus, "twoFactorStatus");
        }

        public String table() {
            return table;
        }

        public String id() {
            return id;
        }

        public String username() {
            return username;
        }

        public String displayName() {
            return displayName;
        }

        public String password() {
            return password;
        }

        public String salt() {
            return salt;
        }

        public String email() {
            return email;
        }

        public String logged() {
            return logged;
        }

        public String hasSession() {
            return hasSession;
        }

        public String totpKey() {
            return totpKey;
        }

        public String ip() {
            return ip;
        }

        public String lastLogin() {
            return lastLogin;
        }

        public String registerDate() {
            return registerDate;
        }

        public String registerIp() {
            return registerIp;
        }

        public String lastLocX() {
            return lastLocX;
        }

        public String lastLocY() {
            return lastLocY;
        }

        public String lastLocZ() {
            return lastLocZ;
        }

        public String lastLocWorld() {
            return lastLocWorld;
        }

        public String lastLocYaw() {
            return lastLocYaw;
        }

        public String lastLocPitch() {
            return lastLocPitch;
        }

        public String twoFactorStatus() {
            return twoFactorStatus;
        }
    }

    public Columns getColumns() {
        return columns;
    }

    public Tables getTables() {
        return tables;
    }

    public static final class Tables {
        private final String twoFactorKeys;
        private final String twoFactorRecovery;
        private final String passwordRecovery;
        private final String emailVerification;
        private final String sessions;

        private Tables(String twoFactorKeys,
                String twoFactorRecovery,
                String passwordRecovery,
                String emailVerification,
                String sessions) {
            this.twoFactorKeys = Objects.requireNonNull(twoFactorKeys, "twoFactorKeys");
            this.twoFactorRecovery = Objects.requireNonNull(twoFactorRecovery, "twoFactorRecovery");
            this.passwordRecovery = Objects.requireNonNull(passwordRecovery, "passwordRecovery");
            this.emailVerification = Objects.requireNonNull(emailVerification, "emailVerification");
            this.sessions = Objects.requireNonNull(sessions, "sessions");
        }

        public String twoFactorKeys() {
            return twoFactorKeys;
        }

        public String twoFactorRecovery() {
            return twoFactorRecovery;
        }

        public String passwordRecovery() {
            return passwordRecovery;
        }

        public String emailVerification() {
            return emailVerification;
        }

        public String sessions() {
            return sessions;
        }
    }
}
