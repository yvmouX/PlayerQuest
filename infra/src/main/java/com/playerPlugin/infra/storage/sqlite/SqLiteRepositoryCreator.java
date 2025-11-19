package com.playerPlugin.infra.storage.sqlite;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static com.playerPlugin.common.Common.DATABASE;
import static com.playerPlugin.core.utils.Help.log;

public class SqLiteRepositoryCreator {
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
                    log.warn("Failed to create data folder: " + dataFolder.getAbsolutePath());
                }
            }

            File dbFile = new File(dataFolder, DATABASE);
            log.info("===== SQLite 数据库绝对路径：" + dbFile.getAbsolutePath() + " =====");
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
                CREATE TABLE IF NOT EXISTS definition_tasks (
                    task_id TEXT NOT NULL,
                    definition_json TEXT NOT NULL,
                    updated_at INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (task_id)
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_tasks (
                    player_uuid TEXT NOT NULL,
                    task_id TEXT NOT NULL,
                    definition_json  TEXT NOT NULL,
                    status INTEGER NOT NULL DEFAULT 0,
                    start_time INTEGER NOT NULL,
                    finish_time INTEGER,
                    PRIMARY KEY (player_uuid, task_id)
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_progress (
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
            log.error("关闭 SQLite 数据库连接时出错：" + e.getMessage());
        } finally {
            conn = null;
        }
    }
}
