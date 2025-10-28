package com.playerPlugin.playerTaskX.dataManager;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.playerPlugin.playerTaskX.PlayerTaskX.getYLib;
import static com.playerPlugin.playerTaskX.PlayerTaskX.log;
import static com.playerPlugin.playerTaskX.consts.common.DATABASE;
import static com.playerPlugin.playerTaskX.dataManager.SQL.players_sql;
import static com.playerPlugin.playerTaskX.dataManager.SQL.players_tasks_sql;

// TODO 异常将由 StorgeManager.java 类进行处理
public class SQLiteManager implements Storge {
    private Connection conn;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private static String getCurrentTime() {
        return ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(formatter);
    }


    /**
     * 连接到 SQLite
     *
     * @throws SQLException           sql异常
     * @throws ClassNotFoundException class not found 异常
     */
    public void connect(File dataFolder) throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");

        String dbPath = dataFolder + "/" + DATABASE;

        String url = "jdbc:sqlite:" + dbPath;

        conn = DriverManager.getConnection(url);
    }

    /**
     * 创建表
     *
     * @throws SQLException sql异常
     */
    public void createTable() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(players_sql);
            stmt.execute(players_tasks_sql);
            getYLib().getLoggerTools().info("Table created successfully");
        }
    }

    /**
     * 创建新玩家
     *
     * @param uuid uuid
     * @throws SQLException sql异常
     */
    public void createNewPlayer(UUID uuid) throws SQLException {
        String current_time = getCurrentTime();
        String player_uuid = uuid.toString();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(create_player_sql(current_time, current_time, player_uuid));
        }
    }
    private String create_player_sql(String createdAt, String updatedAt, String playerUuid) {
        if (createdAt == null || updatedAt == null || playerUuid == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }
        return "INSERT INTO players (created_at, updated_at, player_uuid) VALUES (?, ?, ?);";
    }


    /**
     * 关闭
     *
     */
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
