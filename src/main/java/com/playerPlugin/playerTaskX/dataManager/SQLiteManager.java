package com.playerPlugin.playerTaskX.dataManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.playerPlugin.playerTaskX.PlayerTaskX.log;
import static com.playerPlugin.playerTaskX.dataManager.SQL.players_sql;
import static com.playerPlugin.playerTaskX.dataManager.SQL.players_tasks_sql;

public class SQLiteManager {
    private Connection conn;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private static String getCurrentTime() {
        return ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(formatter);
    }

    public void connectToSQLite() {
        try {
            Class.forName("org.sqlite.JDBC");

            String url = "jdbc:sqlite:data.db";
            conn = DriverManager.getConnection(url);

        } catch (ClassNotFoundException | SQLException e) {
            log.err("Failed to connect to SQLite database: " + e.getMessage());
        }
    }

    public void createTable() {

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(players_sql);
            stmt.execute(players_tasks_sql);
            log.debug("Table created successfully");
        } catch (SQLException e) {
            log.err("Failed to create table: " + e.getMessage());
        }
    }

    public void createNewPlayer(UUID uuid) {
        String current_time = getCurrentTime();
        String player_uuid = uuid.toString();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(create_player_sql(current_time, current_time, player_uuid));
            log.debug("New player created successfully");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String create_player_sql(String createdAt, String updatedAt, String playerUuid) {
        if (createdAt == null || updatedAt == null || playerUuid == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }
        return "INSERT INTO players (created_at, updated_at, player_uuid) VALUES (?, ?, ?);";
    }


    public void close() {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            log.err("Failed to close connection: " + e.getMessage());
        }
    }
}
