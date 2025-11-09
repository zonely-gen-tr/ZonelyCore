package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.store.LastCreditsMenuManager;
import dev.zonely.whiteeffect.hologram.LastCreditHologramManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LastCreditsCommand implements CommandExecutor {

    private static final String DEFAULT_PREFIX = "&3Lobby &8>> ";
    private final Core plugin;

    public LastCreditsCommand(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            LanguageManager.send(sender,
                    "commands.last-credits.only-player",
                    "{prefix}&cOnly players can use this command.",
                    "prefix", LanguageManager.get("prefix.lobby", DEFAULT_PREFIX));
            return true;
        }

        Player player = (Player) sender;
        if (!plugin.isLastCreditsEnabled() || plugin.getCreditsManager() == null) {
            String prefix = LanguageManager.get("prefix.lobby", DEFAULT_PREFIX);
            LanguageManager.send(player,
                    "commands.last-credits.disabled",
                    "{prefix}&cLast credits module is disabled.",
                    "prefix", prefix);
            return true;
        }
        LastCreditHologramManager hologramManager = LastCreditHologramManager.getInstance();
        LastCreditsMenuManager.Category initialCategory = LastCreditsMenuManager.Category.RECENT;
        if (hologramManager != null) {
            initialCategory = hologramManager.getViewerCategory(player);
        }
        plugin.getCreditsManager().openMenu(player, initialCategory, 0);
        return true;
    }
}
