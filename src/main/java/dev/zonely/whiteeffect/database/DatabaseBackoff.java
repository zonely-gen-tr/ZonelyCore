package dev.zonely.whiteeffect.database;

final class DatabaseBackoff {
   private int failures;
   private long retryAtMillis;

   synchronized long remainingMillis(long nowMillis) {
      return Math.max(0L, retryAtMillis - nowMillis);
   }

   synchronized long failed(long nowMillis) {
      failures = Math.min(failures + 1, 6);
      long delay = Math.min(10000L, 500L << (failures - 1));
      retryAtMillis = Math.max(retryAtMillis, nowMillis + delay);
      return delay;
   }

   synchronized void succeeded() {
      failures = 0;
      retryAtMillis = 0L;
   }
}
