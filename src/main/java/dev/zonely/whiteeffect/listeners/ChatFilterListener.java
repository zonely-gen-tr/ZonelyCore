package dev.zonely.whiteeffect.listeners;

import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.player.Profile;
import dev.zonely.whiteeffect.plugin.config.WConfig;
import dev.zonely.whiteeffect.utils.PunishmentManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatFilterListener implements Listener {

    private static final String DEFAULT_PREFIX = "&3Lobby &8->> ";
    private static final List<String> DEFAULT_PROFANITY_WORDS =
            Collections.unmodifiableList(Arrays.asList("o\u00e7", "sq", "am"));
    private static final List<String> DEFAULT_PROFANITY_DURATIONS =
            Collections.unmodifiableList(Arrays.asList("5m", "15m", "1h"));
    private static final List<String> DEFAULT_ADVERTISING_KEYWORDS =
            Collections.unmodifiableList(Arrays.asList("http://", "https://", "www."));

    private final PunishmentManager punishmentManager;
    private final Map<String, Integer> profanityLevel = new HashMap<>();

    private final boolean profanityEnabled;
    private final List<String> profanityWords;
    private final List<String> profanityDurations;

    private final boolean advertisingEnabled;
    private final String advertisingDuration;
    private final List<String> advertisingKeywords;

    public ChatFilterListener(PunishmentManager punishmentManager, WConfig config) {
        this.punishmentManager = punishmentManager;

        this.profanityEnabled = config.getBoolean("chat-filter.profanity.enabled", true);
        this.profanityWords = toLowercaseList(
                fallback(config.getStringList("chat-filter.profanity.blocked-words"), DEFAULT_PROFANITY_WORDS));
        this.profanityDurations = new ArrayList<>(
                fallback(config.getStringList("chat-filter.profanity.mute-durations"), DEFAULT_PROFANITY_DURATIONS));

        this.advertisingEnabled = config.getBoolean("chat-filter.advertising.enabled", true);
        String configuredAdvertisingDuration = config.getString("chat-filter.advertising.mute-duration", "3d");
        this.advertisingDuration = (configuredAdvertisingDuration == null || configuredAdvertisingDuration.isEmpty())
                ? "3d"
                : configuredAdvertisingDuration;
        this.advertisingKeywords = toLowercaseList(
                fallback(config.getStringList("chat-filter.advertising.keywords"), DEFAULT_ADVERTISING_KEYWORDS));
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Profile profile = Profile.getProfile(player.getName());
        String prefix = LanguageManager.get(profile, "prefix.lobby", DEFAULT_PREFIX);
        String msg = event.getMessage().toLowerCase(Locale.ROOT);

        if (punishmentManager.isPlayerMuted(player.getName())) {
            String remaining = punishmentManager.getRemainingMuteTime(player.getName());
            player.sendMessage(LanguageManager.get(profile,
                    "listeners.chat-filter.muted",
                    "{prefix}&cYou cannot chat while muted. Remaining time: &e{remaining}",
                    "prefix", prefix,
                    "remaining", remaining));
            event.setCancelled(true);
            return;
        }

        if (profanityEnabled && containsProfanity(msg)) {
            String mapKey = player.getName().toLowerCase(Locale.ROOT);
            int previousLevel = profanityLevel.getOrDefault(mapKey, 0);
            int level = Math.min(previousLevel + 1, Math.max(1, profanityDurations.size()));
            profanityLevel.put(mapKey, level);
            String muteDuration = profanityDurations.isEmpty()
                    ? DEFAULT_PROFANITY_DURATIONS.get(Math.min(level - 1, DEFAULT_PROFANITY_DURATIONS.size() - 1))
                    : profanityDurations.get(Math.min(level - 1, profanityDurations.size() - 1));

            String reason = LanguageManager.get(
                    "listeners.chat-filter.profanity-reason",
                    "Profanity (Level {level})",
                    "level", level);

            punishmentManager.mutePlayer(
                    player.getName(),
                    muteDuration,
                    reason,
                    "ChatFilter",
                    PunishmentManager.MuteType.NORMAL
            );

            player.sendMessage(LanguageManager.get(profile,
                    "listeners.chat-filter.profanity-detected",
                    "{prefix}&cProfanity detected! &7(Level &e{level}&7) &cDuration: &e{duration}",
                    "prefix", prefix,
                    "level", level,
                    "duration", punishmentManager.getRemainingMuteTime(player.getName())));

            event.setCancelled(true);
            return;
        }

        if (advertisingEnabled && containsAdvertising(msg)) {
            String reason = LanguageManager.get(
                    "listeners.chat-filter.advertising-reason",
                    "Advertising");

            punishmentManager.mutePlayer(
                    player.getName(),
                    advertisingDuration,
                    reason,
                    "ChatFilter",
                    PunishmentManager.MuteType.NORMAL
            );
            player.sendMessage(LanguageManager.get(profile,
                    "listeners.chat-filter.advertising-detected",
                    "{prefix}&cAdvertising is not allowed. You have been muted for &e{duration}.",
                    "prefix", prefix,
                    "duration", punishmentManager.getRemainingMuteTime(player.getName())));
            event.setCancelled(true);
        }
    }

    private boolean containsProfanity(String msg) {
        for (String word : profanityWords) {
            if (!word.isEmpty() && msg.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAdvertising(String msg) {
        for (String keyword : advertisingKeywords) {
            if (!keyword.isEmpty() && msg.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> toLowercaseList(List<String> source) {
        List<String> result = new ArrayList<>(source.size());
        for (String entry : source) {
            if (entry != null) {
                result.add(entry.toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static List<String> fallback(List<String> candidate, List<String> defaults) {
        if (candidate == null || candidate.isEmpty()) {
            return defaults;
        }
        return candidate;
    }
}
