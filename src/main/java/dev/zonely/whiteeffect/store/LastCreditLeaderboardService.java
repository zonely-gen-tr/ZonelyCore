package dev.zonely.whiteeffect.store;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;
import dev.zonely.whiteeffect.store.LastCreditsMenuManager.Category;
import dev.zonely.whiteeffect.utils.StringUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;

public class LastCreditLeaderboardService {

   private static final long DEFAULT_REFRESH_INTERVAL_MILLIS = 60_000L;
   private static final int DEFAULT_LIMIT = 5;

   private final Core plugin;
   private final long refreshIntervalMillis;
   private final int limit;
   private final Map<Category, CacheBucket> cache = new ConcurrentHashMap<>();

   public LastCreditLeaderboardService(Core plugin) {
      this(plugin, DEFAULT_LIMIT, DEFAULT_REFRESH_INTERVAL_MILLIS);
   }

   public LastCreditLeaderboardService(Core plugin, int limit, long refreshIntervalMillis) {
      this.plugin = plugin;
      this.limit = Math.max(1, limit);
      this.refreshIntervalMillis = Math.max(10_000L, refreshIntervalMillis);
      for (Category category : Category.values()) {
         cache.put(category, new CacheBucket());
      }
   }

   public List<LeaderboardEntry> getTopEntries(Category category) {
      CacheBucket bucket = cache.get(category);
      if (bucket == null) {
         return Collections.emptyList();
      }

      long now = System.currentTimeMillis();
      if (!bucket.fetching && now - bucket.lastFetch > refreshIntervalMillis) {
         bucket.fetching = true;
         Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> refreshBucket(category, bucket));
      }

      return bucket.entries;
   }

   public long getLastFetch(Category category) {
      CacheBucket bucket = cache.get(category);
      return bucket != null ? bucket.lastFetch : 0L;
   }

   public void shutdown() {
      cache.clear();
   }

   private void refreshBucket(Category category, CacheBucket bucket) {
      try (Connection conn = Database.getInstance().getConnection();
           PreparedStatement ps = conn.prepareStatement(buildQuery(category))) {
         ps.setInt(1, limit);
         ps.setInt(2, 0);
         ResultSet rs = ps.executeQuery();
         List<LeaderboardEntry> result = new ArrayList<>();
         int position = 1;
         while (rs.next()) {
            result.add(new LeaderboardEntry(
                  position++,
                  rs.getString("username"),
                  rs.getLong("total"),
                  rs.getTimestamp("createdAt") != null
                        ? rs.getTimestamp("createdAt").getTime()
                        : 0L
            ));
         }
         bucket.entries = Collections.unmodifiableList(result);
         bucket.lastFetch = System.currentTimeMillis();
      } catch (SQLException ex) {
         plugin.getLogger().log(Level.SEVERE,
               "[LastCreditLeaderboardService] Failed to refresh data for " + category + ": " + ex.getMessage(), ex);
      } finally {
         bucket.fetching = false;
      }
   }

   private String buildQuery(Category category) {
      String clause;
      switch (category) {
         case RECENT:
            clause = "ORDER BY lc.created_at DESC";
            break;
         case ALL_TIME:
            clause = "GROUP BY lc.userid ORDER BY SUM(lc.gain) DESC";
            break;
         case YEARLY:
            clause = "WHERE YEAR(lc.created_at)=YEAR(CURDATE()) GROUP BY lc.userid ORDER BY SUM(lc.gain) DESC";
            break;
         case MONTHLY:
            clause = "WHERE YEAR(lc.created_at)=YEAR(CURDATE()) AND MONTH(lc.created_at)=MONTH(CURDATE()) " +
                     "GROUP BY lc.userid ORDER BY SUM(lc.gain) DESC";
            break;
         case DAILY:
            clause = "WHERE DATE(lc.created_at)=CURDATE() GROUP BY lc.userid ORDER BY SUM(lc.gain) DESC";
            break;
         default:
            clause = "";
      }

      if (category == Category.RECENT) {
        return "SELECT lc.userid, ul.nick AS username, lc.gain AS total, lc.created_at AS createdAt " +
               "FROM lastcredits lc JOIN userslist ul ON lc.userid = ul.id " +
               clause + " LIMIT ? OFFSET ?";
      }

      return "SELECT lc.userid, ul.nick AS username, SUM(lc.gain) AS total, MAX(lc.created_at) AS createdAt " +
             "FROM lastcredits lc JOIN userslist ul ON lc.userid = ul.id " +
             clause + " LIMIT ? OFFSET ?";
   }

   private static final class CacheBucket {
      private volatile List<LeaderboardEntry> entries = Collections.emptyList();
      private volatile long lastFetch = 0L;
      private volatile boolean fetching = false;
   }

   public static final class LeaderboardEntry {
      private final int position;
      private final String username;
      private final long amount;
      private final long lastUpdated;

      public LeaderboardEntry(int position, String username, long amount, long lastUpdated) {
         this.position = position;
         this.username = username;
         this.amount = amount;
         this.lastUpdated = lastUpdated;
      }

      public int getPosition() {
         return position;
      }

      public String getUsername() {
         return username;
      }

      public long getAmount() {
         return amount;
      }

      public String getFormattedAmount() {
         return StringUtils.formatNumber(amount);
      }

      public long getLastUpdated() {
         return lastUpdated;
      }
   }
}
