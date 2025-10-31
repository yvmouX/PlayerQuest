package com.playerPlugin.playerTaskX.dataManager.impl;

import com.playerPlugin.playerTaskX.PlayerTask.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.PlayerTaskStatus;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget;
import com.playerPlugin.playerTaskX.PlayerTask.TaskTargets;
import com.playerPlugin.playerTaskX.PlayerTask.TaskTypes;
import com.playerPlugin.playerTaskX.dataManager.Storge;

import java.io.File;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.playerPlugin.playerTaskX.PlayerTaskX.getYLib;
import static com.playerPlugin.playerTaskX.PlayerTaskX.log;
import static com.playerPlugin.playerTaskX.consts.common.DATABASE;
import static com.playerPlugin.playerTaskX.dataManager.sql.SQLiteSQL.sqlite_players_table;
import static com.playerPlugin.playerTaskX.dataManager.sql.SQLiteSQL.sqlite_players_tasks_table;

// TODO 异常将由 StorgeManager.java 类进行处理
// TODO 这部分AI写的，有空可以检查一下 2025.10.31
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

        // 确保目录存在
        if (!dataFolder.exists()) {
            boolean ok = dataFolder.mkdirs();
            if (!ok) {
                getYLib().getLoggerTools().warn("Failed to create data folder: " + dataFolder.getAbsolutePath());
            }
        }

        File dbFile = new File(dataFolder, DATABASE);
        String path = dbFile.getAbsolutePath();
        getYLib().getLoggerTools().info("===== SQLite 数据库绝对路径：" + path + " =====");

        String url = "jdbc:sqlite:" + path;

        // 可以在 url 后加上参数，例如 busy_timeout=5000
        conn = DriverManager.getConnection(url);

        // 推荐设置一些 pragmas
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
            st.execute("PRAGMA busy_timeout = 5000;"); // 等待写锁的最长毫秒数
        }
    }

    /**
     * 初始化数据库：建表、索引、触发器等
     *
     * 如果你使用的是我之前给的 sqlite 专用常量 (SQLITE_...)，把下面的 players_sql/players_tasks_sql 替换为对应常量即可。
     *
     * @throws SQLException sql异常
     */
    public void initDatabase() throws SQLException {
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection is not open. Call connect() first.");
        }

        conn.setAutoCommit(false);
        try {
            // 如果你的 players_sql / players_tasks_sql 中只包含 CREATE TABLE 语句，
            // 直接执行即可；若你把索引写在同一常量里（多语句），executeMultipleSql 会拆分执行。
            executeMultipleSql(sqlite_players_table);
            executeMultipleSql(sqlite_players_tasks_table);

            // 如果你在 SQL 类中添加了索引/触发器常量（比如 SQLITE_PLAYERS_INDEXES 等），
            // 也在这里执行它们（示例： executeMultipleSql(SQL.SQLITE_PLAYERS_INDEXES); ）
            conn.commit();
            getYLib().getLoggerTools().info("SQLite: tables initialized.");
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /**
     * 创建新玩家（使用 PreparedStatement 绑定参数）
     *
     * @param task 任务
     * @throws SQLException sql异常
     */
    public void createNewPlayer(PlayerTask task) throws SQLException {
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection is not open. Call connect() first.");
        }
        String createdAt = getCurrentTime();
        String updatedAt = createdAt;
        UUID playerUuid = task.getUUID();
        String taskId = task.getTask().getId();
        TaskTypes taskType = task.getTask().getType();
        TaskTargets taskTarget = task.getTask().getTarget().getAction();
        String taskStatus = PlayerTaskStatus.IN_PROGRESS.toString();

        // conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO players_tasks (created_at, updated_at, player_uuid, task_id, task_type, task_target, task_status) VALUES (?, ?, ?, ?, ?, ?, ?);"
            )) {
            ps.setString(1, createdAt);
            ps.setString(2, updatedAt);
            ps.setString(3, playerUuid.toString());
            ps.setString(4, taskId);
            ps.setString(5, taskType.toString());
            ps.setString(6, taskTarget.toString());
            ps.setString(7, taskStatus);
            int affectedRows = ps.executeUpdate(); // 获取影响行数
            
            // conn.commit();
            if (affectedRows > 0) {
                log.info("成功插入玩家任务数据：" + playerUuid + "，任务ID：" + taskId + "，影响行数：" + affectedRows);
            } else {
                log.info("玩家任务数据已存在（未重复插入）：" + playerUuid + "，任务ID：" + taskId); // INSERT OR IGNORE 会返回 0
            }
        }
        // } catch (SQLException e) {
        //     try {
        //         conn.rollback();
        //     } catch (SQLException rollbackEx) {
        //         log.err("回滚事务失败：" + rollbackEx.getMessage());
        //     }
        //     log.err("插入玩家任务数据失败：" + e.getMessage());
        //     throw e;
        // } finally {
        //     try {
        //         conn.setAutoCommit(true);
        //     } catch (SQLException autoCommitEx) {
        //         log.err("重置自动提交失败：" + autoCommitEx.getMessage());
        //     }
        // }
    }

    /**
     * 执行可能包含多条语句的 SQL 文本（按分号拆分并逐条执行）
     *
     * 简单实现：如果你使用更复杂的脚本，建议把每条语句放到独立字符串或使用脚本执行器。
     */
    private void executeMultipleSql(String sqlScript) throws SQLException {
        if (sqlScript == null || sqlScript.trim().isEmpty()) return;
        // 简单切分；注意：若脚本里包含字符串字面量中也有分号，此方法会出问题。对于当前建表/索引场景够用。
        String[] parts = sqlScript.split(";");
        for (String part : parts) {
            String s = part.trim();
            if (s.isEmpty()) continue;
            try (Statement st = conn.createStatement()) {
                st.execute(s);
            }
        }
    }

    /**
     * 关闭连接
     */
    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            log.err("Failed to close connection: " + e.getMessage());
        } finally {
            conn = null;
        }
    }
}
