package dev.zonely.whiteeffect.api.lastcredit.impl;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.api.lastcredit.LastCreditAPI;
import dev.zonely.whiteeffect.store.LastCreditLeaderboardService;
import dev.zonely.whiteeffect.store.LastCreditPlaceholderService;
import dev.zonely.whiteeffect.store.LastCreditsMenuManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LastCreditApiImpl implements LastCreditAPI {

    private final Core plugin;

    public LastCreditApiImpl(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<LastCreditLeaderboardService.LeaderboardEntry> getLeaderboard(LastCreditsMenuManager.Category category) {
        LastCreditLeaderboardService service = plugin.getLastCreditLeaderboardService();
        if (service == null || category == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(service.getTopEntries(category));
    }

    @Override
    public long getLastLeaderboardRefresh(LastCreditsMenuManager.Category category) {
        LastCreditLeaderboardService service = plugin.getLastCreditLeaderboardService();
        return service == null || category == null ? 0L : service.getLastFetch(category);
    }

    @Override
    public List<LastCreditPlaceholderService.LastCreditRecord> getRecentTopups() {
        LastCreditPlaceholderService service = plugin.getLastCreditPlaceholderService();
        if (service == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(service.getSnapshot());
    }

    @Override
    public LastCreditPlaceholderService.LastCreditRecord getRecentTopup(int position) {
        LastCreditPlaceholderService service = plugin.getLastCreditPlaceholderService();
        if (service == null) {
            return null;
        }
        return service.getRecord(position);
    }

    @Override
    public long getRecentTopupsLastUpdated() {
        LastCreditPlaceholderService service = plugin.getLastCreditPlaceholderService();
        return service == null ? 0L : service.getLastUpdated();
    }
}
