package dev.zonely.whiteeffect.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.Test;

import static org.junit.Assert.*;

public class DatabaseBackoffTest {
   @Test
   public void retryGapsIncreaseWithoutBusyLooping() {
      DatabaseBackoff gate = new DatabaseBackoff();
      assertEquals(0L, gate.remainingMillis(1000L));
      assertEquals(500L, gate.failed(1000L));
      assertEquals(500L, gate.remainingMillis(1000L));
      assertEquals(1L, gate.remainingMillis(1499L));
      assertEquals(0L, gate.remainingMillis(1500L));
      assertEquals(1000L, gate.failed(1500L));
      assertEquals(1000L, gate.remainingMillis(1500L));
      assertEquals(2000L, gate.failed(2500L));
      assertEquals(4000L, gate.failed(4500L));
      assertEquals(8000L, gate.failed(8500L));
      assertEquals(10000L, gate.failed(16500L));
      assertEquals(10000L, gate.failed(26500L));
   }

   @Test
   public void successfulConnectionClearsCooldown() {
      DatabaseBackoff gate = new DatabaseBackoff();
      gate.failed(5000L);
      gate.failed(5000L);
      gate.succeeded();
      assertEquals(0L, gate.remainingMillis(5000L));
      assertEquals(500L, gate.failed(5000L));
   }

   @Test(timeout = 6000L)
   public void hungLocalDatabaseConnectionTimesOut() throws Exception {
      // Isolated local socket: never touches the configured production database.
      try (ServerSocket pretendDatabase = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
         HikariConfig config = new HikariConfig();
         config.setPoolName("zonely-local-test");
         config.setDriverClassName("org.mariadb.jdbc.Driver");
         config.setJdbcUrl("jdbc:mariadb://127.0.0.1:" + pretendDatabase.getLocalPort()
                 + "/zonely_test?connectTimeout=500&socketTimeout=500");
         config.setUsername("test");
         config.setPassword("test");
         config.setMaximumPoolSize(1);
         config.setMinimumIdle(0);
         config.setConnectionTimeout(1500L);
         config.setValidationTimeout(1000L);
         config.setInitializationFailTimeout(-1L);

         try (HikariDataSource dataSource = new HikariDataSource(config)) {
            long start = System.nanoTime();
            try (Connection ignored = dataSource.getConnection()) {
               fail("Dummy socket must not answer MySQL handshakes");
            } catch (SQLException expected) {
               long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
               assertTrue("Timed-out connection blocked too long: " + elapsedMs + "ms", elapsedMs < 3500L);
            }
         }
      }
   }
}
