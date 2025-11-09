package dev.zonely.whiteeffect.auth;

import java.util.Objects;

public final class AuthAccount {

    private final Integer id;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final String email;
    private final boolean loggedIn;
    private final boolean hasSession;
    private final String lastIp;
    private final long lastLogin;
    private final long registerDate;
    private final String registerIp;
    private final boolean twoFactorEnabled;

    public AuthAccount(Integer id,
            String username,
            String displayName,
            String passwordHash,
            String email,
            boolean loggedIn,
            boolean hasSession,
            String lastIp,
            long lastLogin,
            long registerDate,
            String registerIp,
            boolean twoFactorEnabled) {
        this.id = id;
        this.username = Objects.requireNonNull(username, "username");
        this.displayName = displayName == null ? username : displayName;
        this.passwordHash = passwordHash;
        this.email = email;
        this.loggedIn = loggedIn;
        this.hasSession = hasSession;
        this.lastIp = lastIp;
        this.lastLogin = lastLogin;
        this.registerDate = registerDate;
        this.registerIp = registerIp;
        this.twoFactorEnabled = twoFactorEnabled;
    }

    public Integer getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public boolean hasSession() {
        return hasSession;
    }

    public String getLastIp() {
        return lastIp;
    }

    public long getLastLogin() {
        return lastLogin;
    }

    public long getRegisterDate() {
        return registerDate;
    }

    public String getRegisterIp() {
        return registerIp;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public AuthAccount withTwoFactorEnabled(boolean enabled) {
        return new AuthAccount(
                this.id,
                this.username,
                this.displayName,
                this.passwordHash,
                this.email,
                this.loggedIn,
                this.hasSession,
                this.lastIp,
                this.lastLogin,
                this.registerDate,
                this.registerIp,
                enabled);
    }

    public AuthAccount withEmail(String newEmail) {
        return new AuthAccount(
                this.id,
                this.username,
                this.displayName,
                this.passwordHash,
                newEmail,
                this.loggedIn,
                this.hasSession,
                this.lastIp,
                this.lastLogin,
                this.registerDate,
                this.registerIp,
                this.twoFactorEnabled);
    }
}
