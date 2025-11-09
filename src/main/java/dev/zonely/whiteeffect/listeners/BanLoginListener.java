package dev.zonely.whiteeffect.listeners;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.utils.PunishmentManager;
import java.util.Date;
import java.util.Objects;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;

public class BanLoginListener implements Listener {

    private final PunishmentManager punishmentManager;

    public BanLoginListener(PunishmentManager punishmentManager) {
        this.punishmentManager = Objects.requireNonNull(punishmentManager, "punishmentManager");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String playerName = event.getName();

        BanEntry nameEntry = Core.getInstance()
                .getServer()
                .getBanList(BanList.Type.NAME)
                .getBanEntry(playerName);
        if (nameEntry != null) {
            event.disallow(Result.KICK_BANNED,
                    buildBanMessage("listeners.ban-login.name",
                            nameEntry.getReason(),
                            nameEntry.getExpiration()));
            return;
        }

        String ipAddress = event.getAddress() != null
                ? event.getAddress().getHostAddress()
                : null;
        if (ipAddress != null) {
            BanEntry ipEntry = Core.getInstance()
                    .getServer()
                    .getBanList(BanList.Type.IP)
                    .getBanEntry(ipAddress);
            if (ipEntry != null) {
                event.disallow(Result.KICK_BANNED,
                        buildBanMessage("listeners.ban-login.ip",
                                ipEntry.getReason(),
                                ipEntry.getExpiration()));
                return;
            }
        }

        PunishmentManager.PunishmentEntry activeBan = punishmentManager.getActiveBanEntry(playerName);
        if (activeBan != null) {
            String reason = resolveReason(activeBan.getReason());
            String durationDisplay;
            String rawDuration = activeBan.getDuration();
            if (rawDuration == null || rawDuration.trim().isEmpty()) {
                durationDisplay = LanguageManager.get("listeners.ban-login.duration-fallback", "Permanent");
            } else {
                durationDisplay = punishmentManager.formatDurationHuman(rawDuration);
            }

            String banMessage = punishmentManager.buildBanDisplayMessage(null, reason, durationDisplay);
            event.disallow(Result.KICK_BANNED, banMessage);
        }
    }

    private String buildBanMessage(String keyPrefix, String reason, Date expiration) {
        if (reason != null) {
            String trimmed = reason.trim();
            if (trimmed.contains("\u00a7") || trimmed.contains("\n")) {
                return trimmed;
            }
        }
        String resolvedReason = resolveReason(reason);
        String expiresText;
        if (expiration != null) {
            expiresText = LanguageManager.get(keyPrefix + ".expire",
                    "\n&7Ban expires: {expiry}",
                    "expiry", expiration.toString());
        } else {
            expiresText = LanguageManager.get(keyPrefix + ".permanent",
                    "\n&cThis ban does not expire.");
        }
        return LanguageManager.get(keyPrefix + ".disallow",
                "&cYou are banned from the server!\n&7Reason: {reason}{expires}",
                "reason", resolvedReason,
                "expires", expiresText);
    }

    private String resolveReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return LanguageManager.get("listeners.ban-login.reason-fallback", "No reason provided");
        }
        return reason;
    }
}
