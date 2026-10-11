package dev.zonely.whiteeffect.utils;

import dev.zonely.whiteeffect.lang.LanguageManager;
import dev.zonely.whiteeffect.plugin.WPlugin;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ZonelyUpdater {
    public static volatile ZonelyUpdater UPDATER;
    public volatile boolean canDownload;
    private final WPlugin plugin;
    private final GitHubReleaseClient client = new GitHubReleaseClient();
    private final AtomicBoolean checking = new AtomicBoolean();
    private final AtomicBoolean downloading = new AtomicBoolean();
    private volatile GitHubReleaseClient.Release latest;
    private volatile boolean downloaded;

    public ZonelyUpdater(WPlugin plugin) {
        this.plugin = plugin;
        UPDATER = this;
    }

    public ZonelyUpdater(WPlugin plugin, int ignoredResourceId) {
        this(plugin);
    }

    public static Object getVersion(int ignoredResourceId) {
        ZonelyUpdater updater = UPDATER;
        return updater == null || updater.latest == null ? null : updater.latest.version;
    }

    public String getLatestVersion() {
        return latest == null ? "?" : latest.version;
    }

    public void run() {
        check(null);
    }

    public void downloadUpdate(Player player) {
        if (downloaded) {
            LanguageManager.send(player, "commands.core.update.already-downloaded",
                    "{prefix}&eThe update is downloaded. Restart the server to apply it.",
                    "prefix", LanguageManager.get("prefix.lobby", "&3ZonelyCore &8-> "));
        } else if (downloading.get()) {
            LanguageManager.send(player, "updater.player.downloading", "&3ZonelyCore &8-> &eThe update is downloading. Please wait.");
        } else if (latest != null && canDownload) {
            download(player, latest);
        } else {
            check(player);
        }
    }

    private void check(Player player) {
        if (!checking.compareAndSet(false, true)) {
            if (player != null) LanguageManager.send(player, "updater.player.checking", "&3ZonelyCore &8-> &eChecking GitHub Releases...");
            return;
        }
        if (player == null) plugin.getLogger().info("Checking GitHub Releases for ZonelyCore updates.");
        else LanguageManager.send(player, "updater.player.checking", "&3ZonelyCore &8-> &eChecking GitHub Releases...");
        String current = plugin.getDescription().getVersion();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                GitHubReleaseClient.Release release = client.fetchLatest();
                boolean newer = GitHubReleaseClient.isNewer(release.version, current);
                onMain(() -> {
                    latest = release;
                    canDownload = newer && !downloaded && !downloading.get();
                    if (player != null && player.isOnline()) {
                        if (canDownload) download(player, release);
                        else LanguageManager.send(player, "commands.core.update.up-to-date", "{prefix}&aYou are running the latest version.",
                                "prefix", LanguageManager.get("prefix.lobby", "&3ZonelyCore &8-> "));
                    } else {
                        plugin.getLogger().info(newer ? "ZonelyCore " + release.version + " is available on GitHub. Use /zc update."
                                : "ZonelyCore is up to date (GitHub Releases).");
                    }
                });
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Could not check GitHub Releases: " + ex.getMessage());
                onMain(() -> {
                    if (player != null && player.isOnline()) LanguageManager.send(player, "updater.player.check-error",
                            "&3ZonelyCore &8-> &cCould not check GitHub Releases. Please try again later.");
                });
            } finally {
                checking.set(false);
            }
        });
    }

    private void download(Player player, GitHubReleaseClient.Release release) {
        if (!downloading.compareAndSet(false, true)) return;
        canDownload = false;
        File target = new File(plugin.getServer().getUpdateFolderFile(), plugin.getPluginFile().getName());
        LanguageManager.send(player, "updater.player.applying", "&3ZonelyCore &8-> &aDownloading the update from GitHub...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = false;
            try {
                client.download(release, target);
                downloaded = true;
                success = true;
                plugin.getLogger().info("ZonelyCore " + release.version + " prepared at " + target + ". Restart to apply.");
                onMain(() -> {
                    if (player.isOnline()) LanguageManager.send(player, "updater.player.restart", "&aPlease restart the server to finish the update.");
                });
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "GitHub release download failed: " + ex.getMessage());
                onMain(() -> {
                    if (player.isOnline()) LanguageManager.send(player, "updater.player.download-error",
                            "&cUnable to download the update: {error}", "error", ex.getMessage());
                });
            } finally {
                canDownload = !success;
                downloading.set(false);
            }
        });
    }

    private void onMain(Runnable action) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, action);
    }

    public static String getCheck(String url) {
        try {
            return new GitHubReleaseClient().readText(url, 1024 * 1024);
        } catch (IOException ex) {
            return "";
        }
    }

    public static String getExternalIP() throws IOException {
        return new GitHubReleaseClient().readText("https://checkip.amazonaws.com", 1024).trim();
    }
}
