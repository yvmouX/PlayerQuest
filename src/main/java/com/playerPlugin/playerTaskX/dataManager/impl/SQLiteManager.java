package com.playerPlugin.playerTaskX.dataManager.impl;

import com.playerPlugin.playerTaskX.dataManager.Storge;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static com.playerPlugin.playerTaskX.PlayerTaskX.log;
import static com.playerPlugin.playerTaskX.consts.common.DATABASE;
import static com.playerPlugin.playerTaskX.utils.Help.logger;

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
    public void connect(JavaPlugin plugin) throws SQLException, ClassNotFoundException {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                boolean ok = dataFolder.mkdirs();
                if (!ok) {
                    logger.warn("Failed to create data folder: " + dataFolder.getAbsolutePath());
                }
            }

            File dbFile = new File(dataFolder, DATABASE);
           logger.info("===== SQLite 数据库绝对路径：" + dbFile.getAbsolutePath() + " =====");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            conn = DriverManager.getConnection(url);

            if (conn != null) {
                plugin.getLogger().info("SQLite 已连接");
                // 开启外键约束
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON;");
                }
                createTables();
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite 数据库连接失败");
            e.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_tasks (
                    player_uuid TEXT NOT NULL,
                    task_id TEXT NOT NULL,
                    status INTEGER NOT NULL DEFAULT 0,
                    start_time INTEGER NOT NULL,
                    finish_time INTEGER,
                    PRIMARY KEY (player_uuid, task_id)
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_task_progress (
                    player_uuid TEXT NOT NULL,
                    task_id TEXT NOT NULL,
                    target_index INTEGER NOT NULL,
                    current_amount INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (player_uuid, task_id, target_index),
                    FOREIGN KEY (player_uuid, task_id)
                        REFERENCES player_tasks(player_uuid, task_id)
                        ON DELETE CASCADE
                        ON UPDATE CASCADE
                );
            """);
        }
    }

    public Connection getConnection() {
        return conn;
    }

    /**
     * 关闭连接
     */
    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                log.info("SQLite 数据库已关闭");
            }
        } catch (SQLException e) {
            log.err("关闭 SQLite 数据库连接时出错：" + e.getMessage());
        } finally {
            conn = null;
        }
    }
}
