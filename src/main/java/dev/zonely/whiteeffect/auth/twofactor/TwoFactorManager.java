package dev.zonely.whiteeffect.auth.twofactor;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.auth.AuthAccount;
import dev.zonely.whiteeffect.auth.AuthManager;
import dev.zonely.whiteeffect.auth.AuthSession;
import dev.zonely.whiteeffect.auth.recovery.AuthRecoveryService;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TwoFactorManager {

    private final Core plugin;
    private final AuthManager authManager;
    private final TwoFactorRepository repository;
    private final AuthRecoveryService recoveryService;
    private final boolean enabled;
    private final boolean enforceOnLogin;
    private final boolean requireCodeToDisable;
    private final int digits;
    private final int periodSeconds;
    private final int allowedDrift;
    private final long setupTimeoutMillis;
    private final long cooldownMillis;
    private final String issuer;
    private final boolean recoveryEnabled;

    private final Map<UUID, PendingSetup> pendingSetups = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recoveryCooldowns = new ConcurrentHashMap<>();

    public TwoFactorManager(Core plugin,
            AuthManager authManager,
            AuthRecoveryService recoveryService,
            WConfig config) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.repository = new TwoFactorRepository(DatabaseHolder.INSTANCE, authManager.getDataSource());
        this.recoveryService = recoveryService;

        ConfigurationSection section = config.getSection("auth.two-factor");
        this.enabled = section == null || section.getBoolean("enabled", true);
        this.issuer = section == null ? "Zonely Network" : section.getString("issuer", "Zonely Network");
        this.digits = Math.max(6, section == null ? 6 : section.getInt("digits", 6));
        this.periodSeconds = Math.max(15, section == null ? 30 : section.getInt("period-seconds", 30));
        this.allowedDrift = Math.max(0, section == null ? 1 : section.getInt("allowed-drift-windows", 1));
        this.enforceOnLogin = section == null || section.getBoolean("enforce-on-login", true);
        long setupTimeoutSeconds = section == null ? 300L : section.getLong("setup-timeout-seconds", 300L);
        this.setupTimeoutMillis = Math.max(30L, setupTimeoutSeconds) * 1000L;
        long cooldownSeconds = section == null ? 5L : section.getLong("cooldown-seconds", 5L);
        this.cooldownMillis = Math.max(1L, cooldownSeconds) * 1000L;
        this.requireCodeToDisable = section == null || section.getBoolean("require-code-to-disable", true);

        ConfigurationSection recoverySection = section != null ? section.getConfigurationSection("recovery") : null;
        this.recoveryEnabled = recoveryService != null
                && recoveryService.isTwoFactorRecoveryEnabled()
                && (recoverySection == null || recoverySection.getBoolean("enabled", true));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRecoveryEnabled() {
        return recoveryEnabled;
    }

    public boolean handleLoginChallenge(Player player, AuthSession session, AuthAccount account) {
        if (!enabled || session == null || account == null || account.getId() == null) {
            return false;
        }
        if (!enforceOnLogin || !account.isTwoFactorEnabled()) {
            return false;
        }
        if (session.isTwoFactorVerified()) {
            return false;
        }

        Optional<String> secretOpt = Optional.ofNullable(session.getTwoFactorSecret());
        if (!secretOpt.isPresent()) {
            secretOpt = repository.findSecret(account.getId());
        }
        if (!secretOpt.isPresent()) {
            repository.setStatus(account.getId(), false);
            session.setAccount(account.withTwoFactorEnabled(false));
            return false;
        }

        session.setTwoFactorSecret(secretOpt.get());
        session.setTwoFactorRequired(true);
        session.setTwoFactorVerified(false);
        session.markTwoFactorRequested();

        authManager.getBossBarManager().showTwoFactor(player);
        LanguageManager.send(player, "auth.two-factor.login-prompt",
                "{prefix}&eEnter your authenticator code with &b/2fa code <123456>&e.",
                "prefix", authManager.getPrefix(player));
        return true;
    }

    public boolean submitLoginCode(Player player, String rawCode) {
        AuthSession session = authManager.getSession(player.getUniqueId()).orElse(null);
        if (session == null || !session.isTwoFactorRequired()) {
            LanguageManager.send(player, "auth.two-factor.not-enabled",
                    "{prefix}&cTwo-factor authentication is not enabled.",
                    "prefix", authManager.getPrefix(player));
            return false;
        }
        String secret = session.getTwoFactorSecret();
        if (secret == null || secret.isEmpty()) {
            Optional<AuthAccount> accountOpt = Optional.ofNullable(session.getAccount());
            if (!accountOpt.isPresent() || accountOpt.get().getId() == null) {
                return false;
            }
            secret = repository.findSecret(accountOpt.get().getId()).orElse("");
            session.setTwoFactorSecret(secret);
        }
        if (secret.isEmpty()) {
            LanguageManager.send(player, "auth.two-factor.login-failed",
                    "{prefix}&cInvalid authenticator code.",
                    "prefix", authManager.getPrefix(player));
            return false;
        }

        if (TotpUtils.verifyCode(secret, rawCode, digits, allowedDrift, periodSeconds)) {
            LanguageManager.send(player, "auth.two-factor.login-success",
                    "{prefix}&aTwo-factor verification successful.",
                    "prefix", authManager.getPrefix(player));
            authManager.onTwoFactorPassed(player);
            return true;
        }

        LanguageManager.send(player, "auth.two-factor.login-failed",
                "{prefix}&cInvalid authenticator code.",
                "prefix", authManager.getPrefix(player));
        return false;
    }

    public void beginSetup(Player player) {
        if (!enabled) {
            LanguageManager.send(player, "auth.two-factor.not-enabled",
                    "{prefix}&cTwo-factor authentication is not enabled.",
                    "prefix", authManager.getPrefix(player));
            return;
        }
        AuthSession session = authManager.getSession(player.getUniqueId()).orElse(null);
        if (session == null || !authManager.isAuthenticated(player)) {
            LanguageManager.send(player, "auth.login.fail",
                    "{prefix}&cIncorrect password.",
                    "prefix", authManager.getPrefix(player));
            return;
        }
        AuthAccount account = session.getAccount();
        if (account == null || account.getId() == null) {
            LanguageManager.send(player, "auth.two-factor.not-enabled",
                    "{prefix}&cTwo-factor authentication is not enabled.",
                    "prefix", authManager.getPrefix(player));
            return;
        }
        if (account.isTwoFactorEnabled()) {
            LanguageManager.send(player, "auth.two-factor.already-enabled",
                    "{prefix}&eTwo-factor authentication is already enabled.",
                    "prefix", authManager.getPrefix(player));
            return;
        }

        String secret = TotpUtils.generateSecret(20);
        pendingSetups.put(player.getUniqueId(), new PendingSetup(secret, System.currentTimeMillis()));
        String otpauth = TotpUtils.generateAuthenticatorUri(issuer, account.getUsername(), secret, digits,
                periodSeconds);
        String qrUrl = "https://chart.googleapis.com/chart?chs=220x220&cht=qr&chl="
                + URLEncoder.encode(otpauth, StandardCharsets.UTF_8);

        LanguageManager.send(player, "auth.two-factor.setup-start",
                "{prefix}&aTOTP setup started. Scan the QR code or enter the secret below.",
                "prefix", authManager.getPrefix(player));
        LanguageManager.send(player, "auth.two-factor.setup-secret",
                "{prefix}&7Secret: &b{secret}",
                "prefix", authManager.getPrefix(player),
                "secret", secret);
        LanguageManager.send(player, "auth.two-factor.setup-link",
                "{prefix}&7Scan: &b{url}",
                "prefix", authManager.getPrefix(player),
                "url", qrUrl);
        LanguageManager.send(player, "auth.two-factor.setup-confirm",
                "{prefix}&7Enter &b/2fa confirm <code>&7 in your authenticator app.",
                "prefix", authManager.getPrefix(player));
    }

    public void confirmSetup(Player player, String code) {
        PendingSetup pending = pendingSetups.get(player.getUniqueId());
        if (pending == null) {
            LanguageManager.send(player, "auth.two-factor.setup-not-started",
                    "{prefix}&cYou do not have a pending setup. Use &b/2fa setup&c first.",
                    "prefix", authManager.getPrefix(player));
            return;
        }
        if (System.currentTimeMillis() - pending.createdAt > setupTimeoutMillis) {
            pendingSetups.remove(player.getUniqueId());
            LanguageManager.send(player, "auth.two-factor.setup-timeout",
                    "{prefix}&cSetup timed out. Please start again.",
                    "prefix", authManager.getPrefix(player));
            return;
        }

        AuthSession session = authManager.getSession(player.getUniqueId()).orElse(null);
        if (session == null || session.getAccount() == null || session.getAccount().getId() == null) {
            LanguageManager.send(player, "auth.two-factor.setup-not-started",
                    "{prefix}&cYou do not have a pending setup. Use &b/2fa setup&c first.",
                    "prefix", authManager.getPrefix(player));
            return;
        }

        if (!TotpUtils.verifyCode(pending.secret, code, digits, allowedDrift, periodSeconds)) {
            LanguageManager.send(player, "auth.two-factor.setup-invalid",
                    "{prefix}&cInvalid authenticator code. Please try again.",
                    "prefix", authManager.getPrefix(player));
            return;
        }

        int userId = session.getAccount().getId();
        repository.upsertSecret(userId, pending.secret);
        repository.setStatus(userId, true);
        session.setAccount(session.getAccount().withTwoFactorEnabled(true));
        session.setTwoFactorRequired(false);
        session.setTwoFactorVerified(true);
        session.setTwoFactorSecret(null);
        pendingSetups.remove(player.getUniqueId());

        LanguageManager.send(player, "auth.two-factor.setup-success",
                "{prefix}&aTwo-factor authentication enabled successfully.",
                "prefix", authManager.getPrefix(player));
    }

    public void cancelSetup(Player player) {
        if (pendingSetups.remove(player.getUniqueId()) != null) {
            LanguageManager.send(player, "auth.two-factor.setup-cancelled",
                    "{prefix}&ePending two-factor setup cancelled.",
                    "prefix", authManager.getPrefix(player));
        } else {
            LanguageManager.send(player, "auth.two-factor.setup-not-started",
                    "{prefix}&cYou do not have a pending setup. Use &b/2fa setup&c first.",
                    "prefix", authManager.getPrefix(player));
        }
    }

    public void disableTwoFactor(Player player, String code) {
        AuthSession session = authManager.getSession(player.getUniqueId()).orElse(null);
        if (session == null || session.getAccount() == null || session.getAccount().getId() == null) {
            LanguageManager.send(player, "auth.two-factor.not-enabled",
                    "{prefix}&cTwo-factor authentication is not enabled.",
                    "prefix", authManager.getPrefix(player));
            return;
        }
        AuthAccount account = session.getAccount();
        if (!account.isTwoFactorEnabled()) {
            LanguageManager.send(player, "auth.two-factor.not-enabled",
                    "{prefix}&cTwo-factor authentication is not enabled.",
                    "prefix", authManager.getPrefix(player));
            return;
        }
        String secret = repository.findSecret(account.getId()).orElse(session.getTwoFactorSecret());
        if (requireCodeToDisable) {
            if (secret == null || secret.isEmpty()
                    || !TotpUtils.verifyCode(secret, code, digits, allowedDrift, periodSeconds)) {
                LanguageManager.send(player, "auth.two-factor.disable-requires-code",
                        "{prefix}&cYou must supply a valid authenticator code to disable two-factor authentication.",
                        "prefix", authManager.getPrefix(player));
                return;
            }
        }

        repository.deleteSecret(account.getId());
        repository.setStatus(account.getId(), false);
        session.setAccount(account.withTwoFactorEnabled(false));
        session.setTwoFactorSecret(null);
        session.setTwoFactorVerified(false);
        LanguageManager.send(player, "auth.two-factor.disable-success",
                "{prefix}&aTwo-factor authentication disabled.",
                "prefix", authManager.getPrefix(player));
        authManager.getBossBarManager().hide(player);
    }

    public RecoveryResponse requestRecoveryLink(Player player) {
        if (!recoveryEnabled || recoveryService == null) {
            return new RecoveryResponse(RecoveryResult.DISABLED, null);
        }
        long now = System.currentTimeMillis();
        Long last = recoveryCooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldownMillis) {
            return new RecoveryResponse(RecoveryResult.COOLDOWN, null);
        }
        AuthSession session = authManager.getSession(player.getUniqueId()).orElse(null);
        if (session == null || session.getAccount() == null || session.getAccount().getId() == null) {
            return new RecoveryResponse(RecoveryResult.UNAVAILABLE, null);
        }
        Optional<String> url = recoveryService.createTwoFactorRecovery(session.getAccount(), session.getAddress());
        if (!url.isPresent()) {
            return new RecoveryResponse(RecoveryResult.UNAVAILABLE, null);
        }
        recoveryCooldowns.put(player.getUniqueId(), now);
        return new RecoveryResponse(RecoveryResult.SUCCESS, url.get());
    }

    public void clear(Player player) {
        pendingSetups.remove(player.getUniqueId());
        recoveryCooldowns.remove(player.getUniqueId());
    }

    private static final class PendingSetup {
        private final String secret;
        private final long createdAt;

        private PendingSetup(String secret, long createdAt) {
            this.secret = secret;
            this.createdAt = createdAt;
        }
    }

    private static final class DatabaseHolder {
        private static final Database INSTANCE = Database.getInstance();
    }

    public enum RecoveryResult {
        SUCCESS,
        DISABLED,
        COOLDOWN,
        UNAVAILABLE
    }

    public static final class RecoveryResponse {
        private final RecoveryResult result;
        private final String url;

        private RecoveryResponse(RecoveryResult result, String url) {
            this.result = result;
            this.url = url;
        }

        public RecoveryResult getResult() {
            return result;
        }

        public String getUrl() {
            return url;
        }
    }
}
