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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.Bukkit;

public class LastCreditLeaderboardService {

   private static final long DEFAULT_REFRESH_INTERVAL_MILLIS = 10_000L;
   private static final int DEFAULT_LIMIT = 5;

   private final Core plugin;
   private final long refreshIntervalMillis;
   private volatile int limit;
   private final Map<Category, CacheBucket> cache = new ConcurrentHashMap<>();
   private volatile boolean closed;

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
      if (!closed && plugin.isEnabled()
            && now - bucket.lastAttempt >= refreshIntervalMillis
            && bucket.fetching.compareAndSet(false, true)) {
         bucket.lastAttempt = now;
         try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> refreshBucket(category, bucket));
         } catch (IllegalStateException ex) {
            bucket.fetching.set(false);
         }
      }

      return bucket.entries;
   }

   public long getLastFetch(Category category) {
      CacheBucket bucket = cache.get(category);
      return bucket != null ? bucket.lastFetch : 0L;
   }

   public void ensureLimit(int requestedLimit) {
      if (requestedLimit > limit) {
         limit = requestedLimit;
         for (CacheBucket bucket : cache.values()) {
            bucket.lastAttempt = 0L;
         }
      }
   }

   public void shutdown() {
      closed = true;
      cache.clear();
   }

   private void refreshBucket(Category category, CacheBucket bucket) {
      try (Connection conn = Database.getInstance().getConnection();
           PreparedStatement ps = conn.prepareStatement(buildQuery(category))) {
         ps.setInt(1, limit);
         ps.setInt(2, 0);
         ps.setQueryTimeout(5);
         List<LeaderboardEntry> result = new ArrayList<>();
         try (ResultSet rs = ps.executeQuery()) {
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
         }
         if (!closed) {
            bucket.entries = Collections.unmodifiableList(result);
            bucket.lastFetch = System.currentTimeMillis();
         }
      } catch (SQLException ex) {
         plugin.getLogger().log(Level.WARNING,
               "[LastCreditLeaderboardService] Refresh failed for " + category
                     + " (retry in " + refreshIntervalMillis / 1000 + "s): " + ex.getMessage());
      } finally {
         bucket.fetching.set(false);
      }
   }

   private String buildQuery(Category category) {
      return LastCreditQueries.leaderboard(category);
   }

   private static final class CacheBucket {
      private volatile List<LeaderboardEntry> entries = Collections.emptyList();
      private volatile long lastFetch = 0L;
      private volatile long lastAttempt = 0L;
      private final AtomicBoolean fetching = new AtomicBoolean();
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
