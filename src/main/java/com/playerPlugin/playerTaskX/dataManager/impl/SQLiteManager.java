package com.playerPlugin.playerTaskX.dataManager.impl;

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

        String dbPath = new File(dataFolder, DATABASE).getPath();
        String url = "jdbc:sqlite:" + dbPath;

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
     * @param uuid uuid
     * @throws SQLException sql异常
     */
    public void createNewPlayer(UUID uuid) throws SQLException {
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection is not open. Call connect() first.");
        }
        String createdAt = getCurrentTime();
        String updatedAt = createdAt;
        String playerUuid = uuid.toString();

        String sql = create_player_sql(); // 返回带 ? 的 SQL

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, createdAt);
            ps.setString(2, updatedAt);
            ps.setString(3, playerUuid);
            ps.executeUpdate();
        }
    }

    /**
     * 返回插入玩家的 SQL（使用占位符）
     */
    private String create_player_sql() {
        // 使用 INSERT OR IGNORE 可以避免重复主键/unique 导致异常（若你希望抛错可以换成普通 INSERT）
        return "INSERT OR IGNORE INTO players (created_at, updated_at, player_uuid) VALUES (?, ?, ?);";
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
