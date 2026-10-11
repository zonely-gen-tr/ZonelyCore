package dev.zonely.whiteeffect.clubs;

import dev.zonely.whiteeffect.lang.LanguageManager;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class ClubLang {

    private static final String ROOT = "clubs.";

    private ClubLang() {
    }

    public static String get(String key, String def, Object... placeholders) {
        return LanguageManager.get(ROOT + key, def, placeholders);
    }

    public static List<String> getList(String key, List<String> def, Object... placeholders) {
        return LanguageManager.getList(ROOT + key, def, placeholders);
    }

    public static void send(CommandSender sender, String key, String def, Object... placeholders) {
        LanguageManager.send(sender, ROOT + key, def, placeholders);
    }
}
