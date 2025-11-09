package dev.zonely.whiteeffect.auth.email;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.auth.AuthAccount;
import dev.zonely.whiteeffect.auth.AuthManager;
import dev.zonely.whiteeffect.auth.AuthRepository;
import dev.zonely.whiteeffect.auth.AuthSession;
import dev.zonely.whiteeffect.auth.bossbar.AuthBossBarManager;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.logging.Level;

public final class EmailVerificationManager {

    private static final Pattern DEFAULT_EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final Core plugin;
    private final AuthManager authManager;
    private final AuthRepository authRepository;
    private final EmailVerificationRepository repository;
    private final AuthMailService mailService;
    private final boolean enabled;
    private final boolean requireAddress;
    private final boolean requireVerification;
    private final boolean autoSendToken;
    private final boolean showLinkInChat;
    private final long resendCooldownMillis;
    private final long reminderIntervalMillis;
    private final Pattern emailPattern;
    private final Set<String> allowedDomains;
    private final String verificationBaseUrl;
    private final String verificationPath;

    private final Map<Integer, Long> resendCooldowns = new ConcurrentHashMap<>();

    public EmailVerificationManager(Core plugin,
                                    AuthManager authManager,
                                    AuthRepository authRepository,
                                    EmailVerificationRepository repository,
                                    WConfig config,
                                    AuthMailService mailService) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.authRepository = authRepository;
        this.repository = repository;
        this.mailService = mailService;

        ConfigurationSection emailSection = config.getSection("auth.email");
        this.enabled = emailSection == null || emailSection.getBoolean("enabled", true);
        this.requireAddress = emailSection == null || emailSection.getBoolean("require-address", true);
        this.requireVerification = emailSection == null || emailSection.getBoolean("require-verification", true);
        this.autoSendToken = emailSection == null || emailSection.getBoolean("auto-send-token", true);
        this.showLinkInChat = emailSection == null || emailSection.getBoolean("show-link-in-chat", true);
        long cooldownSeconds = emailSection == null ? 300L : emailSection.getLong("resend-cooldown-seconds", 300L);
        this.resendCooldownMillis = Math.max(5L, cooldownSeconds) * 1000L;
        long reminderSeconds = emailSection == null ? 60L : emailSection.getLong("reminder-interval-seconds", 60L);
        this.reminderIntervalMillis = Math.max(5L, reminderSeconds) * 1000L;

        List<String> domains = emailSection == null ? Collections.emptyList() : emailSection.getStringList("allowed-domains");
        this.allowedDomains = Collections.unmodifiableSet(domains == null
                ? Collections.emptySet()
                : domains.stream()
                .filter(d -> d != null && !d.trim().isEmpty())
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet()));

        this.emailPattern = DEFAULT_EMAIL_PATTERN;

        ConfigurationSection siteSection = config.getSection("auth.site");
        String baseUrl = siteSection == null ? "" : siteSection.getString("base-url", "");
        this.verificationBaseUrl = normalizeBaseUrl(baseUrl);
        this.verificationPath = normalizePath(siteSection == null ? "/mail/verify" : siteSection.getString("email-verify-path", "/mail/verify"));
    }

    public EmailCheckResult evaluate(AuthAccount account) {
        if (!enabled || account == null || account.getId() == null) {
            return EmailCheckResult.ready();
        }

        String email = account.getEmail();
        boolean missingEmail = email == null || email.trim().isEmpty();
        if (missingEmail) {
            if (!requireAddress) {
                return EmailCheckResult.ready();
            }
            return EmailCheckResult.missingEmail();
        }

        if (!requireVerification) {
            return EmailCheckResult.ready();
        }

        Optional<EmailVerificationRepository.EmailVerificationRecord> recordOpt = repository.findByUserId(account.getId());
        if (recordOpt.isPresent()) {
            EmailVerificationRepository.EmailVerificationRecord record = recordOpt.get();
            if (record.verified()) {
                return EmailCheckResult.ready();
            }
            String token = record.token();
            if (token == null || token.isEmpty()) {
                token = generateToken();
                repository.upsertToken(account.getId(), token);
                updateCooldown(account.getId());
            }
            return EmailCheckResult.notVerified(buildVerificationUrl(token));
        }

        if (autoSendToken) {
            String token = generateToken();
            repository.upsertToken(account.getId(), token);
            updateCooldown(account.getId());
            return EmailCheckResult.notVerified(buildVerificationUrl(token));
        }

        return EmailCheckResult.notVerified(null);
    }

    public boolean applyResult(Player player,
                               AuthSession session,
                               AuthAccount account,
                               EmailCheckResult result,
                               boolean sessionRestored) {
        if (!enabled || result == null) {
            return false;
        }
        AuthBossBarManager bossBars = authManager.getBossBarManager();

        switch (result.status()) {
            case READY:
                session.setEmailRequired(false);
                session.setEmailVerified(true);
                if (bossBars != null) {
                    bossBars.hide(player);
                }
                authManager.finalizeAuthentication(player, sessionRestored);
                return false;
            case MISSING_EMAIL:
                session.setEmailRequired(true);
                session.setEmailVerified(false);
                maybeSendReminder(player, session, "auth.email.prompt-set",
                        "{prefix}&cYou must set an email address. Use &b/email set <address>&c.");
                if (bossBars != null) {
                    bossBars.showEmailMissing(player);
                }
                return true;
            case NOT_VERIFIED:
                session.setEmailRequired(true);
                session.setEmailVerified(false);
                maybeSendReminder(player, session, "auth.email.verification-required",
                        "{prefix}&eVerify your email via the link sent or use &b/email resend&7.");
                if (bossBars != null) {
                    bossBars.showEmailPending(player);
                }
                boolean mailOperational = mailService != null && mailService.isOperational();
                boolean allowLink = showLinkInChat || !mailOperational;
                if (allowLink && result.verificationUrl() != null && !result.verificationUrl().isEmpty()) {
                    sendVerificationLink(player, result.verificationUrl(), false);
                }
                return true;
            default:
                return true;
        }
    }

    public void handleSetEmail(Player player, String email) {
        if (!enabled) {
            LanguageManager.send(player, "auth.email.disabled",
                    "{prefix}&cEmail verification is disabled.",
                    "prefix", authManager.getPrefix(player));
            return;
        }

        AuthSession session = authManager.getSession(player.getUniqueId()).orElse(null);
        if (session == null || session.getAccount() == null || session.getAccount().getId() == null) {
            LanguageManager.send(player, "auth.login.fail",
                    "{prefix}&cIncorrect password.",
                    "prefix", authManager.getPrefix(player));
            return;
        }

        String normalized = email == null ? "" : email.trim();
        if (!isValidEmail(normalized)) {
            LanguageManager.send(player, "auth.email.invalid",
                    "{prefix}&cInvalid email address.",
                    "prefix", authManager.getPrefix(player));
            return;
        }

        AuthAccount account = session.getAccount();
        if (normalized.equalsIgnoreCase(account.getEmail())) {
            LanguageManager.send(player, "auth.email.already-set",
                    "{prefix}&eThis email is already on your account.",
                    "prefix", authManager.getPrefix(player));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            CompletableFuture<Boolean> mailFuture = null;
            String verificationUrl = null;
            AuthAccount updatedAccount = account.withEmail(normalized);

            try {
                authRepository.updateEmail(account.getId(), normalized);

                if (requireVerification) {
                    EmailVerificationRepository.EmailVerificationRecord record =
                            repository.upsertToken(account.getId(), generateToken());
                    updateCooldown(account.getId());
                    verificationUrl = buildVerificationUrl(record.token());
                }

                EmailCheckResult result = evaluate(updatedAccount);
                final String targetEmail = updatedAccount.getEmail();
                if (requireVerification && verificationUrl != null && mailService != null && mailService.isEnabled()) {
                    mailFuture = mailService.sendVerificationEmail(updatedAccount, verificationUrl);
                }

                final String finalVerificationUrl = verificationUrl;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    session.setAccount(updatedAccount);
                    LanguageManager.send(player, "auth.email.set-success",
                            "{prefix}&aEmail updated. Please check your inbox.",
                            "prefix", authManager.getPrefix(player));
                    boolean gating = applyResult(player, session, updatedAccount, result, false);
                    if (!gating && !session.isAuthenticated()) {
                        authManager.finalizeAuthentication(player, false);
                    }
                });

                if (mailFuture != null) {
                    mailFuture.whenComplete((success, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (throwable != null || !Boolean.TRUE.equals(success)) {
                            LanguageManager.send(player, "auth.email.send-failure",
                                    "{prefix}&cFailed to send verification email. Use &b/email resend&c or contact staff.",
                                    "prefix", authManager.getPrefix(player));
                            sendVerificationLink(player, finalVerificationUrl, true);
                        } else {
                            LanguageManager.send(player, "auth.email.send-success",
                                    "{prefix}&aVerification email sent to &f{email}&a.",
                                    "prefix", authManager.getPrefix(player),
                                    "email", targetEmail);
                        }
                    }));
                }
            } catch (SQLException ex) {
                Core.getInstance().getLogger().log(Level.SEVERE, "[Auth] Unable to update email for " + account.getUsername(), ex);
                Bukkit.getScheduler().runTask(plugin, () -> LanguageManager.send(player, "auth.email.error",
                        "{prefix}&cEmail update failed. Please contact staff.",
                        "prefix", authManager.getPrefix(player)));
            }
        });
    }

    public void handleResend(Player player) {
        if (!enabled) {
            LanguageManager.send(player, "auth.email.disabled",
                    "{prefix}&cEmail verification is disabled.",
                    "prefix", authManager.getPrefix(player));
            return;
        }
        AuthSession session = authManager.getSession(player.getUniqueId()).orElse(null);
        if (session == null || session.getAccount() == null || session.getAccount().getId() == null) {
            LanguageManager.send(player, "auth.login.fail",
                    "{prefix}&cIncorrect password.",
                    "prefix", authManager.getPrefix(player));
            return;
        }
        AuthAccount account = session.getAccount();
        if (account.getEmail() == null || account.getEmail().trim().isEmpty()) {
            LanguageManager.send(player, "auth.email.prompt-set",
                    "{prefix}&cYou must set an email address. Use &b/email set <address>&c.",
                    "prefix", authManager.getPrefix(player));
            return;
        }

        long lastRequest = resendCooldowns.getOrDefault(account.getId(), 0L);
        long now = System.currentTimeMillis();
        if (now - lastRequest < resendCooldownMillis) {
            long remaining = (resendCooldownMillis - (now - lastRequest)) / 1000L;
            if (remaining < 1L) {
                remaining = 1L;
            }
            LanguageManager.send(player, "auth.email.resend-cooldown",
                    "{prefix}&cPlease wait {seconds}s before requesting another verification email.",
                    "prefix", authManager.getPrefix(player),
                    "seconds", remaining);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            EmailVerificationRepository.EmailVerificationRecord record =
                    repository.upsertToken(account.getId(), generateToken());
            updateCooldown(account.getId());
            String url = buildVerificationUrl(record.token());

            CompletableFuture<Boolean> mailFuture = null;
            if (url != null && mailService != null && mailService.isEnabled()) {
                mailFuture = mailService.sendVerificationEmail(account, url);
            }

            final String finalUrl = url;
            CompletableFuture<Boolean> finalMailFuture = mailFuture;
            Bukkit.getScheduler().runTask(plugin, () -> {
                LanguageManager.send(player, "auth.email.resend-success",
                        "{prefix}&aVerification email generated.",
                        "prefix", authManager.getPrefix(player));
                if (finalMailFuture == null) {
                    sendVerificationLink(player, finalUrl, false);
                }
                authManager.getSession(player.getUniqueId()).ifPresent(AuthSession::markEmailReminderSent);
                AuthBossBarManager bossBars = authManager.getBossBarManager();
                if (bossBars != null) {
                    bossBars.showEmailPending(player);
                }
            });

            if (mailFuture != null) {
                mailFuture.whenComplete((success, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (throwable != null || !Boolean.TRUE.equals(success)) {
                        LanguageManager.send(player, "auth.email.send-failure",
                                "{prefix}&cFailed to send verification email. Use &b/email resend&c or contact staff.",
                                "prefix", authManager.getPrefix(player));
                        sendVerificationLink(player, finalUrl, true);
                    } else {
                        LanguageManager.send(player, "auth.email.send-success",
                                "{prefix}&aVerification email sent to &f{email}&a.",
                                "prefix", authManager.getPrefix(player),
                                "email", account.getEmail());
                        if (showLinkInChat) {
                            sendVerificationLink(player, finalUrl, false);
                        }
                    }
                }));
            }
        });
    }

    public void handleStatus(Player player) {
        AuthSession session = authManager.getSession(player.getUniqueId()).orElse(null);
        if (session == null || session.getAccount() == null || session.getAccount().getId() == null) {
            LanguageManager.send(player, "auth.login.fail",
                    "{prefix}&cIncorrect password.",
                    "prefix", authManager.getPrefix(player));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            EmailCheckResult result = evaluate(session.getAccount());
            Bukkit.getScheduler().runTask(plugin, () -> {
                AuthBossBarManager bossBars = authManager.getBossBarManager();
                if (result.status() == EmailStatus.READY) {
                    LanguageManager.send(player, "auth.email.status-verified",
                            "{prefix}&aEmail verified. Thank you!",
                            "prefix", authManager.getPrefix(player));
                    applyResult(player, session, session.getAccount(), result, false);
                    if (bossBars != null) {
                        bossBars.hide(player);
                    }
                } else if (result.status() == EmailStatus.MISSING_EMAIL) {
                    LanguageManager.send(player, "auth.email.prompt-set",
                            "{prefix}&cYou must set an email address. Use &b/email set <address>&c.",
                            "prefix", authManager.getPrefix(player));
                    if (bossBars != null) {
                        bossBars.showEmailMissing(player);
                    }
                } else {
                    LanguageManager.send(player, "auth.email.status-pending",
                            "{prefix}&eEmail pending verification.",
                            "prefix", authManager.getPrefix(player));
                    sendVerificationLink(player, result.verificationUrl(), false);
                    if (bossBars != null) {
                        bossBars.showEmailPending(player);
                    }
                }
            });
        });
    }

    public void clear(Player player) {
        if (player == null) {
            return;
        }
        authManager.getSession(player.getUniqueId()).ifPresent(session -> {
            AuthAccount account = session.getAccount();
            if (account != null && account.getId() != null) {
                resendCooldowns.remove(account.getId());
            }
        });
    }

    public boolean isEnabled() {
        return enabled;
    }

    private boolean isValidEmail(String email) {
        if (!emailPattern.matcher(email).matches()) {
            return false;
        }
        if (allowedDomains.isEmpty()) {
            return true;
        }
        int atIndex = email.lastIndexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            return false;
        }
        String domain = email.substring(atIndex + 1).toLowerCase(Locale.ROOT);
        return allowedDomains.contains(domain);
    }

    private void maybeSendReminder(Player player, AuthSession session, String key, String fallback) {
        long now = System.currentTimeMillis();
        if (now - session.getLastEmailReminderAt() < reminderIntervalMillis) {
            return;
        }
        LanguageManager.send(player, key, fallback, "prefix", authManager.getPrefix(player));
        session.markEmailReminderSent();
    }

    private void updateCooldown(int userId) {
        resendCooldowns.put(userId, System.currentTimeMillis());
    }

    private void sendVerificationLink(Player player, String url, boolean force) {
        if (player == null || url == null || url.isEmpty()) {
            return;
        }
        boolean mailOperational = mailService != null && mailService.isOperational();
        if (!force && !showLinkInChat && mailOperational) {
            return;
        }
        LanguageManager.send(player, "auth.email.verification-link",
                "{prefix}&7Verification link: &b{url}",
                "prefix", authManager.getPrefix(player),
                "url", url);
        authManager.getSession(player.getUniqueId()).ifPresent(AuthSession::markEmailReminderSent);
    }

    private String buildVerificationUrl(String token) {
        if (token == null || token.isEmpty() || verificationBaseUrl.isEmpty()) {
            return null;
        }
        return verificationBaseUrl + verificationPath + "/" + token;
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "";
        }
        String value = path.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String generateToken() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    public enum EmailStatus {
        READY,
        MISSING_EMAIL,
        NOT_VERIFIED
    }

    public static final class EmailCheckResult {
        private final EmailStatus status;
        private final String verificationUrl;

        private EmailCheckResult(EmailStatus status, String verificationUrl) {
            this.status = status;
            this.verificationUrl = verificationUrl;
        }

        public static EmailCheckResult ready() {
            return new EmailCheckResult(EmailStatus.READY, null);
        }

        public static EmailCheckResult missingEmail() {
            return new EmailCheckResult(EmailStatus.MISSING_EMAIL, null);
        }

        public static EmailCheckResult notVerified(String url) {
            return new EmailCheckResult(EmailStatus.NOT_VERIFIED, url);
        }

        public EmailStatus status() {
            return status;
        }

        public String verificationUrl() {
            return verificationUrl;
        }
    }
}
