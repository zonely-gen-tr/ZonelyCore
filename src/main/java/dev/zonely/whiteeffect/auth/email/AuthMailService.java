package dev.zonely.whiteeffect.auth.email;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.auth.AuthAccount;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class AuthMailService {

    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000L;

    private final Core plugin;
    private final Database database;
    private final boolean enabled;

    private volatile MailSettings cachedSettings;
    private volatile long lastLoadedAt;
    private final Object reloadLock = new Object();

    public AuthMailService(Core plugin, Database database, WConfig config) {
        this.plugin = plugin;
        this.database = database;
        ConfigurationSection emailSection = config.getSection("auth.email");
        this.enabled = emailSection == null || emailSection.getBoolean("send-mail", true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isOperational() {
        MailSettings settings = getSettings();
        return enabled && settings != null && settings.valid;
    }

    public CompletableFuture<Boolean> sendVerificationEmail(AuthAccount account, String verificationUrl) {
        return sendMail(account, verificationUrl, MailTemplate.VERIFICATION);
    }

    public CompletableFuture<Boolean> sendPasswordResetEmail(AuthAccount account, String resetUrl) {
        return sendMail(account, resetUrl, MailTemplate.PASSWORD_RESET);
    }

    private CompletableFuture<Boolean> sendMail(AuthAccount account, String url, MailTemplate template) {
        if (!enabled) {
            return CompletableFuture.completedFuture(false);
        }
        if (account == null || url == null || url.trim().isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        String email = account.getEmail();
        if (email == null || email.trim().isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        MailSettings settings = getSettings();
        if (settings == null || !settings.valid) {
            return CompletableFuture.completedFuture(false);
        }

        String to = email.trim();
        String displayName = account.getDisplayName() != null ? account.getDisplayName() : account.getUsername();
        String subject = template.subject(settings.displayName);
        String body = template.body(settings, displayName, url);

        return runAsync(() -> sendEmail(settings, to, subject, body));
    }

    private CompletableFuture<Boolean> runAsync(Supplier<Boolean> supplier) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.WARNING, "[Auth] Failed to send email.", throwable);
                future.complete(false);
            }
        });
        return future;
    }

    private boolean sendEmail(MailSettings settings, String to, String subject, String body) {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", settings.host);
        properties.put("mail.smtp.port", Integer.toString(settings.port));
        properties.put("mail.smtp.auth", Boolean.toString(!settings.login.isEmpty()));
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.mime.charset", StandardCharsets.UTF_8.name());

        switch (settings.secureMode) {
            case SSL:
                properties.put("mail.smtp.ssl.enable", "true");
                properties.put("mail.smtp.ssl.trust", settings.host);
                break;
            case STARTTLS:
                properties.put("mail.smtp.starttls.enable", "true");
                properties.put("mail.smtp.ssl.trust", settings.host);
                break;
            case NONE:
            default:
                break;
        }

        Session session = settings.login.isEmpty()
                ? Session.getInstance(properties)
                : Session.getInstance(properties, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(settings.login, settings.password);
                    }
                });

        try {
            MimeMessage message = new MimeMessage(session);
            String fromAddress = settings.fromAddress.isEmpty() ? settings.login : settings.fromAddress;
            if (fromAddress == null || fromAddress.trim().isEmpty()) {
                fromAddress = "no-reply@" + settings.host;
            }
            try {
                message.setFrom(new InternetAddress(fromAddress, settings.displayName, StandardCharsets.UTF_8.name()));
            } catch (UnsupportedEncodingException ex) {
                message.setFrom(new InternetAddress(fromAddress));
            }
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
            message.setSubject(subject, StandardCharsets.UTF_8.name());
            message.setSentDate(new Date());
            message.setContent(body, "text/html; charset=UTF-8");
            Transport.send(message);
            return true;
        } catch (MessagingException ex) {
            plugin.getLogger().log(Level.WARNING, "[Auth] Unable to deliver mail to " + to + ".", ex);
            return false;
        }
    }

    private MailSettings getSettings() {
        if (!enabled || database == null) {
            return null;
        }
        MailSettings settings = cachedSettings;
        long now = System.currentTimeMillis();
        if (settings == null || now - lastLoadedAt > CACHE_TTL_MILLIS) {
            synchronized (reloadLock) {
                settings = cachedSettings;
                if (settings == null || now - lastLoadedAt > CACHE_TTL_MILLIS) {
                    settings = loadSettings();
                    cachedSettings = settings;
                    lastLoadedAt = now;
                }
            }
        }
        return settings;
    }

    private MailSettings loadSettings() {
        if (database == null) {
            return MailSettings.invalid();
        }

        String sql = "SELECT `server`, `port`, `secure`, `name`, `username`, `passwd`, `team`, `verifytemplate`, `passwdtemplate` "
                + "FROM `settings` ORDER BY `id` DESC LIMIT 1";
        try (Connection connection = database.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            if (!rs.next()) {
                return MailSettings.invalid();
            }

            String host = trim(rs.getString("server"));
            int port = 0;
            try {
                port = rs.getInt("port");
                if (rs.wasNull()) {
                    port = 0;
                }
            } catch (SQLException ignored) {
            }

            SecureMode mode = SecureMode.NONE;
            try {
                int secureInt = rs.getInt("secure");
                if (!rs.wasNull()) {
                    mode = SecureMode.fromDatabaseValue(secureInt);
                }
            } catch (SQLException ignored) {
                String secureRaw = trim(rs.getString("secure"));
                mode = SecureMode.fromString(secureRaw);
            }

            String login = firstNonEmpty(trim(rs.getString("username")), trim(rs.getString("name")));
            String fromAddress = trim(rs.getString("name"));
            String password = trim(rs.getString("passwd"));
            String team = trim(rs.getString("team"));
            String verifyTemplate = rs.getString("verifytemplate");
            String passwordTemplate = rs.getString("passwdtemplate");

            return MailSettings.of(host, port, mode, login, fromAddress, password, team, verifyTemplate,
                    passwordTemplate);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "[Auth] Unable to load SMTP settings from database.", ex);
            return MailSettings.invalid();
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private enum SecureMode {
        NONE,
        SSL,
        STARTTLS;

        static SecureMode fromDatabaseValue(int value) {
            if (value == 2) {
                return STARTTLS;
            }
            if (value == 1) {
                return SSL;
            }
            return NONE;
        }

        static SecureMode fromString(String raw) {
            if (raw == null) {
                return NONE;
            }
            String value = raw.trim().toLowerCase(Locale.ROOT);
            switch (value) {
                case "tls":
                case "starttls":
                    return STARTTLS;
                case "ssl":
                case "smtps":
                    return SSL;
                default:
                    return NONE;
            }
        }
    }

    private enum MailTemplate {
        VERIFICATION {
            @Override
            String subject(String team) {
                String sender = team == null || team.isEmpty() ? "Zonely Network" : team;
                return sender + " - Email Verification";
            }

            @Override
            String body(MailSettings settings, String displayName, String url) {
                String template = settings.verifyTemplate;
                if (template == null || template.trim().isEmpty()) {
                    return defaultVerification(displayName, url);
                }
                return applyPlaceholders(template, displayName, url);
            }

            private String defaultVerification(String displayName, String url) {
                return "<p>Hello " + escape(displayName) + ",</p>"
                        + "<p>Please verify your account by clicking the link below:</p>"
                        + "<p><a href=\"" + url + "\">Verify Email</a></p>"
                        + "<p>If you did not request this email, you can safely ignore it.</p>";
            }
        },
        PASSWORD_RESET {
            @Override
            String subject(String team) {
                String sender = team == null || team.isEmpty() ? "Zonely Network" : team;
                return sender + " - Password Reset";
            }

            @Override
            String body(MailSettings settings, String displayName, String url) {
                String template = settings.passwordTemplate;
                if (template == null || template.trim().isEmpty()) {
                    return defaultReset(displayName, url);
                }
                return applyPlaceholders(template, displayName, url);
            }

            private String defaultReset(String displayName, String url) {
                return "<p>Hello " + escape(displayName) + ",</p>"
                        + "<p>You requested a password reset. Use the link below to choose a new password:</p>"
                        + "<p><a href=\"" + url + "\">Reset Password</a></p>"
                        + "<p>If you did not request this change, please contact staff immediately.</p>";
            }
        };

        abstract String subject(String team);

        abstract String body(MailSettings settings, String displayName, String url);

        String applyPlaceholders(String template, String displayName, String url) {
            return template
                    .replace("%username%", displayName)
                    .replace("%url%", url);
        }

        String escape(String value) {
            if (value == null || value.isEmpty()) {
                return "player";
            }
            return value.replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    private static final class MailSettings {
        private final String host;
        private final int port;
        private final SecureMode secureMode;
        private final String login;
        private final String fromAddress;
        private final String password;
        private final String displayName;
        private final String verifyTemplate;
        private final String passwordTemplate;
        private final boolean valid;

        private MailSettings(String host,
                int port,
                SecureMode secureMode,
                String login,
                String fromAddress,
                String password,
                String displayName,
                String verifyTemplate,
                String passwordTemplate,
                boolean valid) {
            this.host = host;
            this.port = port;
            this.secureMode = secureMode;
            this.login = login == null ? "" : login;
            this.fromAddress = fromAddress == null ? "" : fromAddress;
            this.password = password == null ? "" : password;
            this.displayName = displayName == null || displayName.isEmpty() ? "Zonely Network" : displayName;
            this.verifyTemplate = verifyTemplate;
            this.passwordTemplate = passwordTemplate;
            this.valid = valid;
        }

        static MailSettings of(String host,
                int port,
                SecureMode secureMode,
                String login,
                String fromAddress,
                String password,
                String displayName,
                String verifyTemplate,
                String passwordTemplate) {
            boolean valid = host != null && !host.isEmpty() && port > 0 && login != null && !login.isEmpty();
            return new MailSettings(host, port, secureMode, login, fromAddress, password, displayName, verifyTemplate,
                    passwordTemplate, valid);
        }

        static MailSettings invalid() {
            return new MailSettings(null, 0, SecureMode.NONE, "", "", "", "Zonely Network", null, null, false);
        }
    }
}
