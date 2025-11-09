package dev.zonely.whiteeffect.socket;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.database.Database;
import org.bukkit.Bukkit;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

public class SecureSocketListener extends Thread {

    private final Core plugin;
    private final ServerSocket serverSocket;
    private final String configToken;
    private final int listenPort;     
    private Connection connection;
    private volatile boolean running = false;

    public SecureSocketListener(Core plugin, ServerSocket serverSocket, String configToken, int listenPort) {
        this.plugin = plugin;
        this.serverSocket = serverSocket;
        this.configToken = configToken;
        this.listenPort = listenPort;
        this.connection = null;
    }

    @Override
    public void run() {
        plugin.getLogger().info("SecureSocketListener started listening (port: "
                                + this.listenPort + ")");

        running = true;

        while (!isInterrupted()) {
            Socket client = null;
            try {
                if (connection == null || connection.isClosed()) {
                    try {
                        connection = Database.getInstance().getConnection();
                        plugin.getLogger().info("[SecureSocket] Linked to the database.");
                    } catch (SQLException ex) {
                        plugin.getLogger().log(Level.SEVERE, "[SecureSocket] Could not obtain DB connection: {0}", ex.getMessage());
                        try { Thread.sleep(2000); } catch (InterruptedException ie) { break; }
                        continue;
                    }
                }

                client = serverSocket.accept();
                String clientIP = client.getInetAddress().getHostAddress();

                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                String jsonStr = in.readLine();

                if (jsonStr == null || jsonStr.isEmpty()) {
                    plugin.getLogger().warning("[SecureSocket] Received empty JSON, rejected.");
                    client.close();
                    continue;
                }

                JSONObject json;
                try {
                    json = new JSONObject(jsonStr);
                } catch (Exception e) {
                    plugin.getLogger().warning("[SecureSocket] Invalid JSON: " + jsonStr);
                    client.close();
                    continue;
                }

                String token       = json.optString("token", "");
                long timestamp     = json.optLong("timestamp", 0L);
                String command     = json.optString("command", "");
                String receivedHmac = json.optString("hmac", "");

                if (!token.equals(configToken)) {
                    plugin.getLogger().warning("[SecureSocket] Config token mismatch: " + token);
                    client.close();
                    continue;
                }

                long now = System.currentTimeMillis() / 1000L;
                if (Math.abs(now - timestamp) > 10) {
                    plugin.getLogger().warning(String.format(
                        "[SecureSocket] Timestamp difference too large (received=%d, local=%d).",
                        timestamp, now));
                    client.close();
                    continue;
                }

                String dataForHmac = token + "|" + timestamp + "|" + command;
                String calcHmac = calculateHMAC(dataForHmac, token);
                if (!calcHmac.equalsIgnoreCase(receivedHmac)) {
                    plugin.getLogger().warning("[SecureSocket] HMAC validation failed! Received="
                                               + receivedHmac + " Computed=" + calcHmac);
                    client.close();
                    continue;
                }

                try (PreparedStatement stmt = connection.prepareStatement(
                         "SELECT id FROM plugin_servers WHERE password = ? AND ip = ? AND port = ? LIMIT 1"
                     )) {
                    stmt.setString(1, token);
                    stmt.setString(2, clientIP);
                    stmt.setInt(3, this.listenPort); 
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            plugin.getLogger().warning("[SecureSocket] Database validation failed. IP="
                                                      + clientIP + ", Port=" + this.listenPort);
                            client.close();
                            continue;
                        }
                    }
                } catch (SQLException sqlEx) {
                    plugin.getLogger().log(Level.WARNING, "[SecureSocket] Database query error: {0}", sqlEx.getMessage());
                    client.close();
                    continue;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getLogger().info("[SecureSocket] Executing command: " + command);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                });

                client.close();

            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "[SecureSocket] Listener error: {0}", ex.getMessage());
                try {
                    if (client != null) client.close();
                } catch (Exception ignored) {}
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { break; }
            }
        }

        try {
            serverSocket.close();
        } catch (Exception ignore) {}

        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignore) {}

        running = false;

        plugin.getLogger().info("SecureSocketListener stopped.");
    }

    private String calculateHMAC(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey =
                new javax.crypto.spec.SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes("UTF-8"));

            StringBuilder hexString = new StringBuilder();
            for (byte b : rawHmac) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[SecureSocket] HMAC calculation error: {0}", e.getMessage());
            return "";
        }
    }

    public int getListenPort() {
        return listenPort;
    }

    public boolean isRunning() {
        return running && !isInterrupted();
    }

    public void shutdown() {
        interrupt();
        try {
            serverSocket.close();
        } catch (Exception ignored) {}
    }
}
