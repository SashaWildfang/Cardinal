package com.bearnation.cardinal.database;

import com.bearnation.cardinal.messaging.Messaging;

import java.sql.*;
import java.util.UUID;

public class DatabaseManager {

    static {
        try {
            Class.forName("com.bearnation.shadow.mysql.cj.jdbc.Driver");
            System.out.println("[Cardinal] MySQL driver loaded.");
        } catch (ClassNotFoundException e) {
            System.err.println("[Cardinal] MySQL driver not found.");
            e.printStackTrace();
        }
    }

    private static String JDBC_URL;
    private static String USERNAME;
    private static String PASSWORD;

    private static Connection connection;

    // Replaces PluginMessagingManager
    private static Messaging messaging;

    private DatabaseManager() {}

    /** Call this once during bootstrap (e.g., in Cardinal.onProxyInit) */
    public static void setMessaging(Messaging m) {
        messaging = m;
    }

    public static void initialize(String host, int port, String database, String username, String password) {
        JDBC_URL = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&autoReconnect=true&maxReconnects=3&connectTimeout=5000&socketTimeout=5000&characterEncoding=utf8",
                host, port, database
        );
        USERNAME = username;
        PASSWORD = password;

        System.out.println("[Cardinal] Initializing database connection...");
        try {
            getConnection();
            System.out.println("[Cardinal] Database connected.");
        } catch (SQLException e) {
            System.err.println("[Cardinal] Failed to connect to database.");
            e.printStackTrace();
        }
    }

    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
            System.out.println("[Cardinal] Connection established.");
        }
        return connection;
    }

    // =========================================================
    // ==============  COIN MANAGEMENT (users)  =================
    // =========================================================

    public static int getCoins(UUID uuid) {
        String sql = "SELECT coins FROM users WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("coins");
            }
        } catch (SQLException e) {
            System.err.println("[DB] Error fetching coins for " + uuid);
            e.printStackTrace();
        }
        return 0;
    }

    public static boolean setCoins(UUID uuid, int coins) {
        String sql = "UPDATE users SET coins = ? WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, coins);
            ps.setString(2, uuid.toString());
            int updated = ps.executeUpdate();

            if (updated == 0) {
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO users (uuid, coins) VALUES (?, ?)")) {
                    ins.setString(1, uuid.toString());
                    ins.setInt(2, coins);
                    ins.executeUpdate();
                }
            }

            notifyStatsUpdate(uuid);
            return true;

        } catch (SQLException e) {
            System.err.println("[DB] Error setting coins for " + uuid);
            e.printStackTrace();
            return false;
        }
    }

    public static boolean addCoins(UUID uuid, int amount) {
        int current = getCoins(uuid);
        return setCoins(uuid, current + amount);
    }

    public static boolean removeCoins(UUID uuid, int amount) {
        int current = getCoins(uuid);
        int newBalance = Math.max(0, current - amount);
        return setCoins(uuid, newBalance);
    }

    public static boolean resetCoins(UUID uuid) {
        return setCoins(uuid, 0);
    }

    // =========================================================
    // ==============  PLAYTIME (users.play_seconds) ===========
    // =========================================================
    // Run this once in your DB:
    // ALTER TABLE users ADD COLUMN play_seconds BIGINT NOT NULL DEFAULT 0;

    /** Add seconds to a user's total playtime (creates row if missing). */
    public static void addPlaySeconds(UUID uuid, long seconds) {
        if (seconds <= 0) return;

        // Prefer an upsert pattern to avoid an extra read
        final String upsert =
                "INSERT INTO users (uuid, play_seconds) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE play_seconds = play_seconds + VALUES(play_seconds)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(upsert)) {

            ps.setString(1, uuid.toString());
            ps.setLong(2, seconds);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[DB] addPlaySeconds failed for " + uuid + " (+ " + seconds + "s)");
            e.printStackTrace();
        }
    }

    /** Get total stored playtime (seconds). */
    public static long getPlaySeconds(UUID uuid) {
        final String sql = "SELECT play_seconds FROM users WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            System.err.println("[DB] getPlaySeconds failed for " + uuid);
            e.printStackTrace();
        }
        return 0L;
    }

    // =========================================================
    // ==============  HUB SCOREBOARD REFRESH  =================
    // =========================================================

    private static void notifyStatsUpdate(UUID uuid) {
        if (messaging != null) {
            messaging.requestStatsRefresh(uuid);
        }
    }

    // =========================================================
    // ==============  BASIC STAT MANAGEMENT  ==================
    // =========================================================

    public static boolean setKills(UUID uuid, int kills) { return updateSingleStat(uuid, "kills", kills); }
    public static boolean setDeaths(UUID uuid, int deaths) { return updateSingleStat(uuid, "deaths", deaths); }
    public static boolean setWins(UUID uuid, int wins) { return updateSingleStat(uuid, "wins", wins); }
    public static boolean setLosses(UUID uuid, int losses) { return updateSingleStat(uuid, "losses", losses); }

    private static boolean updateSingleStat(UUID uuid, String column, int value) {
        String sql = "UPDATE users SET " + column + " = ? WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, value);
            ps.setString(2, uuid.toString());
            int updated = ps.executeUpdate();

            if (updated == 0) {
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO users (uuid, " + column + ") VALUES (?, ?)")) {
                    ins.setString(1, uuid.toString());
                    ins.setInt(2, value);
                    ins.executeUpdate();
                }
            }

            notifyStatsUpdate(uuid);
            return true;

        } catch (SQLException e) {
            System.err.println("[DB] Error updating " + column + " for " + uuid);
            e.printStackTrace();
            return false;
        }
    }
}
