package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.menus.MenuProfile;
import dev.zonely.whiteeffect.player.Profile;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ProfileCommand extends Commands {

    private static final String DEFAULT_PREFIX = "&3Lobby &8->> ";

    public ProfileCommand() {
        super("webprofile");
    }

    @Override
    public void perform(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            LanguageManager.send(sender,
                    "commands.profile.only-player",
                    "{prefix}&cOnly players can use this command.",
                    "prefix", LanguageManager.get("prefix.lobby", DEFAULT_PREFIX));
            return;
        }

        Player player = (Player) sender;
        Profile profile = Profile.getProfile(player.getName());
        if (profile == null) {
            LanguageManager.send(player,
                    "commands.profile.not-found",
                    "{prefix}&cProfile data could not be found.",
                    "prefix", LanguageManager.get(profile, "prefix.lobby", DEFAULT_PREFIX));
            return;
        }

        new MenuProfile(profile);
    }
}
