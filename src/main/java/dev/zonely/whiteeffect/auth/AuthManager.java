package dev.zonely.whiteeffect.auth;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.auth.bossbar.AuthBossBarManager;
import dev.zonely.whiteeffect.auth.email.AuthMailService;
import dev.zonely.whiteeffect.auth.email.EmailVerificationManager;
import dev.zonely.whiteeffect.auth.email.EmailVerificationRepository;
import dev.zonely.whiteeffect.auth.recovery.AuthRecoveryService;
import dev.zonely.whiteeffect.auth.twofactor.TwoFactorManager;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import dev.zonely.whiteeffect.utils.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class AuthManager {

    private static final String DEFAULT_PREFIX = "&3Lobby &8>> ";

    private final Core plugin;
    private final AuthDataSource dataSource;
    private final AuthRepository repository;
    private final AuthSettings settings;
    private final Map<UUID, AuthSession> sessions = new ConcurrentHashMap<>();
    private final Set<String> allowedCommands;
    private final int maxAttempts;
    private final long timeoutMillis;
    private final long autoLoginMillis;
    private final boolean autoLoginRequireSameIp;
    private final int minPasswordLength;
    private final int maxPasswordLength;
    private final boolean disallowNameSubstring;
    private BukkitTask timeoutTask;
    private TwoFactorManager twoFactorManager;
    private AuthRecoveryService recoveryService;
    private final AuthBossBarManager bossBarManager;
    private EmailVerificationManager emailManager;
    private final AuthMailService mailService;
    private final AuthFallbackConnector fallbackConnector;

    public AuthManager(Core plugin) {
        this.plugin = plugin;

        WConfig config = plugin.getConfig("config");
        this.dataSource = AuthDataSource.fromConfig(config);
        this.repository = new AuthRepository(Database.getInstance(), dataSource.getColumns());
        this.settings = AuthSettings.load(Database.getInstance());

        ConfigurationSection security = config.getSection("auth.security");
        if (security == null) {
            config.createSection("auth.security");
            security = config.getSection("auth.security");
        }
        this.maxAttempts = Math.max(1, security.getInt("max-login-attempts", 5));
        this.timeoutMillis = Math.max(10, security.getInt("login-timeout-seconds", 120)) * 1000L;
        long autoLoginSeconds = Math.max(0L, security.getLong("auto-login-seconds", 0));
        this.autoLoginMillis = autoLoginSeconds * 1000L;
        this.autoLoginRequireSameIp = security.getBoolean("auto-login-require-same-ip", true);
        List<String> allowedFromConfig = security.getStringList("allowed-commands");
        List<String> baseAllowed = (allowedFromConfig == null || allowedFromConfig.isEmpty())
                ? Arrays.asList("login", "l", "register", "reg", "2fa", "twofactor", "email", "mail", "passwordreset")
                : allowedFromConfig;
        this.allowedCommands = baseAllowed.stream()
                .map(cmd -> cmd.startsWith("/") ? cmd.substring(1) : cmd)
                .map(cmd -> cmd.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(() -> new HashSet<>(baseAllowed.size())));
        this.allowedCommands.addAll(Arrays.asList("login", "l", "register", "reg", "2fa", "twofactor", "email", "mail", "passwordreset"));

        ConfigurationSection policy = config.getSection("auth.password-policy");
        if (policy == null) {
            config.createSection("auth.password-policy");
            policy = config.getSection("auth.password-policy");
        }
        this.minPasswordLength = Math.max(4, policy.getInt("min-length", 6));
        this.maxPasswordLength = Math.max(this.minPasswordLength, policy.getInt("max-length", 32));
        this.disallowNameSubstring = policy.getBoolean("disallow-name-substring", true);

        this.recoveryService = new AuthRecoveryService(Database.getInstance(), dataSource, config);
        this.twoFactorManager = new TwoFactorManager(plugin, this, this.recoveryService, config);
        this.bossBarManager = new AuthBossBarManager(plugin);
        this.mailService = new AuthMailService(plugin, Database.getInstance(), config);
        EmailVerificationRepository emailRepository = new EmailVerificationRepository(Database.getInstance(), dataSource);
        this.emailManager = new EmailVerificationManager(plugin, this, this.repository, emailRepository, config, mailService);
        this.fallbackConnector = new AuthFallbackConnector(plugin, config);

        startTimeoutWatcher();
    }

    private void startTimeoutWatcher() {
        this.timeoutTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkTimeouts, 20L, 20L);
    }

    public void shutdown() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        if (twoFactorManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                twoFactorManager.clear(player);
            }
        }
        if (emailManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                emailManager.clear(player);
            }
        }
        bossBarManager.clearAll();
        sessions.clear();
    }

    public AuthSettings getSettings() {
        return settings;
    }

    public AuthDataSource getDataSource() {
        return dataSource;
    }

    public Optional<AuthSession> getSession(UUID uniqueId) {
        return Optional.ofNullable(sessions.get(uniqueId));
    }

    public TwoFactorManager getTwoFactorManager() {
        return twoFactorManager;
    }

    public AuthRecoveryService getRecoveryService() {
        return recoveryService;
    }

    public AuthBossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public AuthMailService getMailService() {
        return mailService;
    }

    public EmailVerificationManager getEmailManager() {
        return emailManager;
    }

    public Optional<AuthAccount> findAccount(String username) {
        if (username == null || username.isEmpty()) {
            return Optional.empty();
        }
        return repository.findByUsername(username);
    }

    public boolean isAuthenticated(Player player) {
        AuthSession session = sessions.get(player.getUniqueId());
        return session != null && session.isAuthenticated();
    }

    public boolean requiresAuthentication(Player player) {
        AuthSession session = sessions.get(player.getUniqueId());
        return session != null && !session.isAuthenticated();
    }

    public boolean isCommandAllowed(String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        int space = normalized.indexOf(' ');
        if (space >= 0) {
            normalized = normalized.substring(0, space);
        }
        return allowedCommands.contains(normalized.toLowerCase(Locale.ROOT));
    }

    public void handleJoin(Player player) {
        String address = getPlayerAddress(player);
        AuthSession session = new AuthSession(player.getUniqueId(), player.getName(), address);
        sessions.put(player.getUniqueId(), session);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<AuthAccount> optional = repository.findByUsername(player.getName());
            if (!sessions.containsKey(player.getUniqueId())) {
                return;
            }
            if (!optional.isPresent()) {
                session.setRegistrationRequired(true);
                runSync(() -> {
                    Player current = Bukkit.getPlayer(player.getUniqueId());
                    if (current != null && current.isOnline()) {
                        LanguageManager.send(current, "auth.join.unregistered",
                                "{prefix}&7Please register with &b/register <password> <confirm>&7.",
                                "prefix", getPrefix(current));
                        bossBarManager.showRegister(current);
                    }
                });
                return;
            }

            AuthAccount account = optional.get();
            session.setAccount(account);
            session.setRegistrationRequired(false);

            EmailVerificationManager.EmailCheckResult emailResult = emailManager != null
                    ? emailManager.evaluate(account)
                    : EmailVerificationManager.EmailCheckResult.ready();

            if (shouldRestoreSession(account, address)) {
                runSync(() -> {
                    Player current = Bukkit.getPlayer(player.getUniqueId());
                    if (current != null && current.isOnline()) {
                        if (twoFactorManager != null && twoFactorManager.handleLoginChallenge(current, session, account)) {
                            return;
                        }
                        if (emailManager != null) {
                            boolean gating = emailManager.applyResult(current, session, account, emailResult, true);
                            if (gating) {
                                return;
                            }
                            if (session.isAuthenticated()) {
                                LanguageManager.send(current, "auth.session.restored",
                                        "{prefix}&aSession restored automatically.",
                                        "prefix", getPrefix(current));
                                return;
                            }
                        }
                        finalizeAuthentication(current, true);
                        LanguageManager.send(current, "auth.session.restored",
                                "{prefix}&aSession restored automatically.",
                                "prefix", getPrefix(current));
                    }
                });
                return;
            }

            runSync(() -> {
                Player current = Bukkit.getPlayer(player.getUniqueId());
                if (current != null && current.isOnline() && !session.isAuthenticated()) {
                    LanguageManager.send(current, "auth.join.registered",
                            "{prefix}&7Please login with &b/login <password>&7.",
                            "prefix", getPrefix(current));
                    bossBarManager.showLogin(current);
                }
            });
        });
    }

    public void handleQuit(Player player) {
        if (emailManager != null) {
            emailManager.clear(player);
        }
        bossBarManager.hide(player);
        sessions.remove(player.getUniqueId());
        if (twoFactorManager != null) {
            twoFactorManager.clear(player);
        }
        boolean clearSession = autoLoginMillis <= 0;
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> repository.markLogout(player.getName(), clearSession));
    }

    public void attemptLogin(Player player, String password) {
        AuthSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            LanguageManager.send(player, "auth.login.fail",
                    "{prefix}&cIncorrect password.",
                    "prefix", getPrefix(player));
            bossBarManager.showLogin(player);
            return;
        }
        if (session.isAuthenticated()) {
            LanguageManager.send(player, "auth.login.already",
                    "{prefix}&eYou are already logged in.",
                    "prefix", getPrefix(player));
            return;
        }
        if (session.isRegistrationRequired()) {
            LanguageManager.send(player, "auth.register.already",
                    "{prefix}&cThis account is already registered.",
                    "prefix", getPrefix(player));
            bossBarManager.showRegister(player);
            return;
        }
        if (password == null || password.isEmpty()) {
            LanguageManager.send(player, "auth.login.fail",
                    "{prefix}&cIncorrect password.",
                    "prefix", getPrefix(player));
            bossBarManager.showLogin(player);
            return;
        }

        session.touch();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthAccount account = session.getAccount();
            if (account == null) {
                account = repository.findByUsername(player.getName()).orElse(null);
                if (account == null) {
                    session.setRegistrationRequired(true);
                    runSync(() -> LanguageManager.send(player, "auth.join.unregistered",
                            "{prefix}&7Please register with &b/register <password> <confirm>&7.",
                            "prefix", getPrefix(player)));
                    return;
                }
                session.setAccount(account);
            }

            boolean matches = PasswordHasher.matches(settings.getAlgorithm(), password, account.getPasswordHash());
            if (matches) {
                EmailVerificationManager.EmailCheckResult emailResult = emailManager != null
                        ? emailManager.evaluate(account)
                        : EmailVerificationManager.EmailCheckResult.ready();
                AuthAccount finalAccount = account;
                AuthSession finalSession = session;
                runSync(() -> {
                    Player current = Bukkit.getPlayer(player.getUniqueId());
                    if (current == null || !current.isOnline()) {
                        return;
                    }
                    if (twoFactorManager != null && twoFactorManager.handleLoginChallenge(current, finalSession, finalAccount)) {
                        return;
                    }
                    if (emailManager != null) {
                        boolean gating = emailManager.applyResult(current, finalSession, finalAccount, emailResult, false);
                        if (gating) {
                            return;
                        }
                        if (finalSession.isAuthenticated()) {
                            return;
                        }
                    }
                    finalizeAuthentication(current, false);
                });
                return;
            }

            session.incrementFailedAttempts();
            int failed = session.getFailedAttempts();
            int remaining = Math.max(0, maxAttempts - failed);

            if (failed >= maxAttempts) {
                final AuthAccount snapshot = account;
                runSync(() -> {
                    Player current = Bukkit.getPlayer(player.getUniqueId());
                    if (current != null) {
                        LanguageManager.send(current, "auth.login.locked",
                                "{prefix}&cToo many incorrect attempts. Please reconnect and try again later.",
                                "prefix", getPrefix(current));
                        String kickMessage = LanguageManager.get("auth.protection.timeout",
                                "{prefix}&cLogin timed out.",
                                "prefix", getPrefix(current));
                        current.kickPlayer(StringUtils.formatColors(kickMessage));
                    }
                });
                sessions.remove(player.getUniqueId());
                repository.markLogout(snapshot.getUsername(), true);
            } else {
                runSync(() -> LanguageManager.send(player, "auth.login.attempts-remaining",
                        "{prefix}&cIncorrect password. Attempts remaining: &f{remaining}",
                        "prefix", getPrefix(player), "remaining", remaining));
                bossBarManager.showLogin(player);
            }
        });
    }

    public void attemptRegister(Player player, String password, String confirmation) {
        AuthSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            LanguageManager.send(player, "auth.register.already",
                    "{prefix}&cThis account is already registered.",
                    "prefix", getPrefix(player));
            return;
        }
        if (!session.isRegistrationRequired()) {
            LanguageManager.send(player, "auth.register.already",
                    "{prefix}&cThis account is already registered.",
                    "prefix", getPrefix(player));
            return;
        }
        if (password == null || confirmation == null || !password.equals(confirmation)) {
            LanguageManager.send(player, "auth.register.mismatch",
                    "{prefix}&cPasswords do not match.",
                    "prefix", getPrefix(player));
            return;
        }
        if (!isPasswordValid(player.getName(), password)) {
            LanguageManager.send(player, "auth.register.invalid-password",
                    "{prefix}&cPassword must be between {min} and {max} characters.",
                    "prefix", getPrefix(player), "min", minPasswordLength, "max", maxPasswordLength);
            return;
        }
        if (!settings.getUsernamePattern().matcher(player.getName()).matches()) {
            LanguageManager.send(player, "auth.register.invalid-username",
                    "{prefix}&cThis username does not meet the requirements.",
                    "prefix", getPrefix(player));
            return;
        }

        session.touch();

        Location joinLocation = player.getLocation().clone();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (repository.findByUsername(player.getName()).isPresent()) {
                session.setRegistrationRequired(false);
                runSync(() -> LanguageManager.send(player, "auth.register.already",
                        "{prefix}&cThis account is already registered.",
                        "prefix", getPrefix(player)));
                bossBarManager.showLogin(player);
                return;
            }

            String hashed = PasswordHasher.hash(settings.getAlgorithm(), password);
            try {
                AuthAccount account = repository.insertAccount(
                        player.getName(),
                        player.getName(),
                        hashed,
                        session.getAddress(),
                        System.currentTimeMillis(),
                        joinLocation);
                session.setAccount(account);
                session.setRegistrationRequired(false);
                EmailVerificationManager.EmailCheckResult emailResult = emailManager != null
                        ? emailManager.evaluate(account)
                        : EmailVerificationManager.EmailCheckResult.ready();
                runSync(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    LanguageManager.send(player, "auth.register.success",
                            "{prefix}&aAccount created successfully.",
                            "prefix", getPrefix(player));
                    if (emailManager != null) {
                        boolean gating = emailManager.applyResult(player, session, account, emailResult, false);
                        if (gating) {
                            return;
                        }
                        if (session.isAuthenticated()) {
                            return;
                        }
                    }
                    finalizeAuthentication(player, false);
                });
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "[Auth] Unable to create account for " + player.getName(), ex);
                runSync(() -> LanguageManager.send(player, "auth.register.already",
                        "{prefix}&cThis account is already registered.",
                        "prefix", getPrefix(player)));
            }
        });
    }

    private void completeLogin(Player player, AuthSession session, AuthAccount account, boolean sessionRestored) {
        session.setAuthenticated(true);
        session.resetFailedAttempts();
        session.touch();
        session.setTwoFactorRequired(false);
        session.setTwoFactorSecret(null);
        session.setTwoFactorVerified(true);
        session.setEmailRequired(false);
        session.setEmailVerified(true);

        if (!sessionRestored) {
            LanguageManager.send(player, "auth.login.success",
                    "{prefix}&aLogged in successfully.",
                    "prefix", getPrefix(player));
        }

        String ip = session.getAddress();
        Location snapshot = player.getLocation().clone();
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> repository.markLogin(account.getUsername(), ip, System.currentTimeMillis(), snapshot));
    }

    public void finalizeAuthentication(Player player, boolean sessionRestored) {
        AuthSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        AuthAccount account = session.getAccount();
        if (account == null) {
            return;
        }
        completeLogin(player, session, account, sessionRestored);
        if (twoFactorManager != null) {
            twoFactorManager.clear(player);
        }
        bossBarManager.hide(player);
        if (fallbackConnector != null) {
            fallbackConnector.handlePostLogin(player, sessionRestored);
        }
    }

    public void onTwoFactorPassed(Player player) {
        AuthSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        AuthAccount account = session.getAccount();
        if (account == null) {
            return;
        }
        session.setTwoFactorVerified(true);
        session.setTwoFactorRequired(false);
        session.setTwoFactorSecret(null);
        if (emailManager == null) {
            finalizeAuthentication(player, false);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            EmailVerificationManager.EmailCheckResult result = emailManager.evaluate(account);
            runSync(() -> {
                if (!player.isOnline()) {
                    return;
                }
                boolean gating = emailManager.applyResult(player, session, account, result, false);
                if (gating) {
                    return;
                }
                if (session.isAuthenticated()) {
                    return;
                }
                finalizeAuthentication(player, false);
            });
        });
    }

    private boolean shouldRestoreSession(AuthAccount account, String address) {
        if (autoLoginMillis <= 0) {
            return false;
        }
        if (!account.hasSession()) {
            return false;
        }
        if (autoLoginRequireSameIp) {
            if (account.getLastIp() == null || account.getLastIp().isEmpty()) {
                return false;
            }
            if (!account.getLastIp().equalsIgnoreCase(address)) {
                return false;
            }
        }
        long lastLogin = account.getLastLogin();
        if (lastLogin <= 0) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - lastLogin;
        return elapsed >= 0 && elapsed <= autoLoginMillis;
    }

    private boolean isPasswordValid(String username, String password) {
        int length = password.length();
        if (length < minPasswordLength || length > maxPasswordLength) {
            return false;
        }
        if (disallowNameSubstring && password.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return true;
    }

    private void checkTimeouts() {
        if (timeoutMillis <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        List<AuthSession> toKick = new ArrayList<>();
        for (AuthSession session : sessions.values()) {
            if (session.isAuthenticated()) {
                continue;
            }
            if ((now - session.getCreatedAt()) >= timeoutMillis) {
                toKick.add(session);
            }
        }

        if (toKick.isEmpty()) {
            return;
        }

        for (AuthSession session : toKick) {
            sessions.remove(session.getUniqueId());
            Player player = Bukkit.getPlayer(session.getUniqueId());
            if (player != null && player.isOnline()) {
                String message = LanguageManager.get("auth.protection.timeout",
                        "{prefix}&cLogin timed out.",
                        "prefix", getPrefix(player));
                player.kickPlayer(StringUtils.formatColors(message));
            }
        }
    }

    private void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    private String getPlayerAddress(Player player) {
        if (player.getAddress() == null) {
            return "0.0.0.0";
        }
        if (player.getAddress() instanceof InetSocketAddress) {
            InetSocketAddress socket = (InetSocketAddress) player.getAddress();
            if (socket.getAddress() != null) {
                return socket.getAddress().getHostAddress();
            }
        }
        return player.getAddress().getAddress().getHostAddress();
    }

    public String getPrefix(Player player) {
        Profile profile = Profile.getProfile(player.getName());
        String prefix = profile != null
                ? LanguageManager.get(profile, "prefix.lobby", DEFAULT_PREFIX)
                : LanguageManager.get("prefix.lobby", DEFAULT_PREFIX);
        if (prefix == null || prefix.trim().isEmpty() || prefix.contains("MemorySection")) {
            prefix = DEFAULT_PREFIX;
        }
        return StringUtils.formatColors(prefix);
    }
}
