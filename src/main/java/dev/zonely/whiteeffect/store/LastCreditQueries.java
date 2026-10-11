package dev.zonely.whiteeffect.store;

import dev.zonely.whiteeffect.store.LastCreditsMenuManager.Category;

public final class LastCreditQueries {
    private LastCreditQueries() { }
    public static String leaderboard(Category category) {
        String base = "FROM lastcredits lc JOIN userslist ul ON lc.userid = ul.id ";
        if (category == Category.RECENT) return "SELECT lc.userid, ul.nick AS username, lc.gain AS total, lc.created_at AS createdAt "
                + base + "ORDER BY lc.created_at DESC LIMIT ? OFFSET ?";
        String filter = "";
        switch (category) {
            case YEARLY: filter="WHERE YEAR(lc.created_at)=YEAR(CURDATE()) "; break;
            case MONTHLY: filter="WHERE YEAR(lc.created_at)=YEAR(CURDATE()) AND MONTH(lc.created_at)=MONTH(CURDATE()) "; break;
            case DAILY: filter="WHERE DATE(lc.created_at)=CURDATE() "; break;
            default: break;
        }
        return "SELECT lc.userid, ul.nick AS username, SUM(lc.gain) AS total, MAX(lc.created_at) AS createdAt "
                + base + filter + "GROUP BY lc.userid, ul.nick ORDER BY SUM(lc.gain) DESC, lc.userid ASC LIMIT ? OFFSET ?";
    }
}
