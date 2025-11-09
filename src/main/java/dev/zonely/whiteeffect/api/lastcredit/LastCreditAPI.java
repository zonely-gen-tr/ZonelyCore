package dev.zonely.whiteeffect.api.lastcredit;

import dev.zonely.whiteeffect.store.LastCreditLeaderboardService;
import dev.zonely.whiteeffect.store.LastCreditPlaceholderService;
import dev.zonely.whiteeffect.store.LastCreditsMenuManager;

import java.util.List;

public interface LastCreditAPI {

    List<LastCreditLeaderboardService.LeaderboardEntry> getLeaderboard(LastCreditsMenuManager.Category category);

    long getLastLeaderboardRefresh(LastCreditsMenuManager.Category category);

    List<LastCreditPlaceholderService.LastCreditRecord> getRecentTopups();

    LastCreditPlaceholderService.LastCreditRecord getRecentTopup(int position);

    long getRecentTopupsLastUpdated();
}
