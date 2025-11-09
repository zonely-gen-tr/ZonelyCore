package dev.zonely.whiteeffect.auth;

import java.util.UUID;

public final class AuthSession {

    private final UUID uniqueId;
    private final String username;
    private final String address;
    private final long createdAt;
    private volatile boolean authenticated;
    private volatile boolean registrationRequired;
    private volatile int failedAttempts;
    private volatile AuthAccount account;
    private volatile long lastInteraction;
    private volatile boolean twoFactorRequired;
    private volatile boolean twoFactorVerified;
    private volatile String twoFactorSecret;
    private volatile long twoFactorRequestedAt;
    private volatile boolean emailRequired;
    private volatile boolean emailVerified;
    private volatile long lastEmailReminderAt;

    AuthSession(UUID uniqueId, String username, String address) {
        this.uniqueId = uniqueId;
        this.username = username;
        this.address = address;
        this.createdAt = System.currentTimeMillis();
        this.lastInteraction = this.createdAt;
        this.twoFactorRequestedAt = 0L;
        this.lastEmailReminderAt = 0L;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public String getUsername() {
        return username;
    }

    public String getAddress() {
        return address;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        touch();
    }

    public boolean isRegistrationRequired() {
        return registrationRequired;
    }

    public void setRegistrationRequired(boolean registrationRequired) {
        this.registrationRequired = registrationRequired;
        touch();
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void incrementFailedAttempts() {
        this.failedAttempts++;
        touch();
    }

    public void resetFailedAttempts() {
        this.failedAttempts = 0;
        touch();
    }

    public AuthAccount getAccount() {
        return account;
    }

    public void setAccount(AuthAccount account) {
        this.account = account;
        touch();
    }

    public boolean isTwoFactorRequired() {
        return twoFactorRequired;
    }

    public void setTwoFactorRequired(boolean twoFactorRequired) {
        this.twoFactorRequired = twoFactorRequired;
        touch();
    }

    public boolean isTwoFactorVerified() {
        return twoFactorVerified;
    }

    public void setTwoFactorVerified(boolean twoFactorVerified) {
        this.twoFactorVerified = twoFactorVerified;
        touch();
    }

    public String getTwoFactorSecret() {
        return twoFactorSecret;
    }

    public void setTwoFactorSecret(String twoFactorSecret) {
        this.twoFactorSecret = twoFactorSecret;
        touch();
    }

    public long getTwoFactorRequestedAt() {
        return twoFactorRequestedAt;
    }

    public void markTwoFactorRequested() {
        this.twoFactorRequestedAt = System.currentTimeMillis();
        touch();
    }

    public boolean isEmailRequired() {
        return emailRequired;
    }

    public void setEmailRequired(boolean emailRequired) {
        this.emailRequired = emailRequired;
        touch();
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
        touch();
    }

    public long getLastEmailReminderAt() {
        return lastEmailReminderAt;
    }

    public void markEmailReminderSent() {
        this.lastEmailReminderAt = System.currentTimeMillis();
        touch();
    }

    public long getLastInteraction() {
        return lastInteraction;
    }

    void touch() {
        this.lastInteraction = System.currentTimeMillis();
    }
}
