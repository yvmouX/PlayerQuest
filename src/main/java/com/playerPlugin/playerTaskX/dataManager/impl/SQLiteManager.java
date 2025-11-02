package com.playerPlugin.playerTaskX.dataManager.impl;

import com.playerPlugin.playerTaskX.PlayerTask.Task.PlayerTask;
import com.playerPlugin.playerTaskX.PlayerTask.Task.Task;
import com.playerPlugin.playerTaskX.PlayerTask.Enum.PlayerTaskStatus;
import com.playerPlugin.playerTaskX.PlayerTask.Enum.TaskActions;
import com.playerPlugin.playerTaskX.PlayerTask.Enum.TaskTypes;
import com.playerPlugin.playerTaskX.PlayerTask.TaskManager;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.dataManager.Storge;

import java.io.File;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static com.playerPlugin.playerTaskX.PlayerTaskX.getYLib;
import static com.playerPlugin.playerTaskX.PlayerTaskX.log;
import static com.playerPlugin.playerTaskX.consts.common.DATABASE;
import static com.playerPlugin.playerTaskX.dataManager.sql.NewSQLiteSQL.sqlite_task_statistics_sql;
import static com.playerPlugin.playerTaskX.dataManager.sql.NewSQLiteSQL.sqlite_players_tasks_sql;

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
            executeMultipleSql(sqlite_task_statistics_sql);
            executeMultipleSql(sqlite_players_tasks_sql);
            executeMultipleSql(sqlite_task_item_progress_sql);

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

    @Override
    public void createNewPlayer(PlayerTask task) throws SQLException {

    }
    


    /**
     * 创建新玩家（使用 PreparedStatement 绑定参数）
     *
     * @param task 任务
     * @throws SQLException sql异常
     */
    public void newCreateNewPlayer(PlayerTask task) throws SQLException {
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection is not open. Call connect() first.");
        }
        String createdAt = getCurrentTime();
        String updatedAt = createdAt;
        UUID playerUuid = task.getUUID();
        String taskId = task.getTask().getId();
        String taskStatus = PlayerTaskStatus.IN_PROGRESS.toString();

        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO players_tasks (created_at, updated_at, player_uuid, task_id, task_status) VALUES (?, ?, ?, ?, ?);"
            )) {
            ps.setString(1, createdAt);
            ps.setString(2, updatedAt);
            ps.setString(3, playerUuid.toString());
            ps.setString(4, taskId);
            ps.setString(5, taskStatus);
            int affectedRows = ps.executeUpdate(); // 获取影响行数

            // conn.commit();
            if (affectedRows > 0) {
                log.info("成功插入玩家任务数据：" + playerUuid + "，任务ID：" + taskId + "，影响行数：" + affectedRows);
            } else {
                log.info("玩家任务数据已存在（未重复插入）：" + playerUuid + "，任务ID：" + taskId); // INSERT OR IGNORE 会返回 0
            }
        }
    }
//    public void createNewPlayer(PlayerTask task) throws SQLException {
//        if (conn == null || conn.isClosed()) {
//            throw new SQLException("Connection is not open. Call connect() first.");
//        }
//        String createdAt = getCurrentTime();
//        String updatedAt = createdAt;
//        UUID playerUuid = task.getUUID();
//        String taskId = task.getTask().getId();
//        TaskTypes taskType = task.getTask().getType();
//        TaskActions taskTarget = task.getTask().getTarget().getAction();
//        String taskStatus = PlayerTaskStatus.IN_PROGRESS.toString();
//
//        // conn.setAutoCommit(false);
//        try (PreparedStatement ps = conn.prepareStatement(
//            "INSERT INTO players_tasks (created_at, updated_at, player_uuid, task_id, task_type, task_target, task_status) VALUES (?, ?, ?, ?, ?, ?, ?);"
//            )) {
//            ps.setString(1, createdAt);
//            ps.setString(2, updatedAt);
//            ps.setString(3, playerUuid.toString());
//            ps.setString(4, taskId);
//            ps.setString(5, taskType.toString());
//            ps.setString(6, taskTarget.toString());
//            ps.setString(7, taskStatus);
//            int affectedRows = ps.executeUpdate(); // 获取影响行数
//
//            // conn.commit();
//            if (affectedRows > 0) {
//                log.info("成功插入玩家任务数据：" + playerUuid + "，任务ID：" + taskId + "，影响行数：" + affectedRows);
//            } else {
//                log.info("玩家任务数据已存在（未重复插入）：" + playerUuid + "，任务ID：" + taskId); // INSERT OR IGNORE 会返回 0
//            }
//        }
//    }

    /**
     * 加载玩家任务
     *
     * @param uuid 玩家 UUID
     * @return 玩家任务列表
     * @throws SQLException sql异常
     */
    public List<PlayerTask> loadPlayerTasks(UUID uuid) throws SQLException {
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection is not open. Call connect() first.");
        }
        List<PlayerTask> playerTaskList = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM players_tasks WHERE player_uuid = ?;"
            )) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int playerTaskId = rs.getInt("id");
                    Task task = TaskManager.getInstance().getTask(rs.getString("task_id"));
                    if (task == null) continue;
                    PlayerTask playerTask = new PlayerTask(
                        UUID.fromString(rs.getString("player_uuid")),
                        task
                    );
                    // 设置任务进度和状态
                    playerTask.setProgress(rs.getInt("task_progress"));
                    playerTask.setStatus(PlayerTaskStatus.valueOf(rs.getString("task_status")));
                    
                    // 加载每个项目的进度
                    loadItemProgress(playerTaskId, playerTask);
                    
                    playerTaskList.add(playerTask);
                }
            }
        }
        return playerTaskList;
    }
    
    /**
     * 加载任务项目进度
     * 
     * @param playerTaskId 玩家任务ID
     * @param playerTask 玩家任务对象
     * @throws SQLException sql异常
     */
    private void loadItemProgress(int playerTaskId, PlayerTask playerTask) throws SQLException {
        String sql = "SELECT item_type, progress FROM task_item_progress WHERE player_task_id = ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerTaskId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                String itemType = rs.getString("item_type");
                int itemProgress = rs.getInt("progress");
                playerTask.setItemProgress(itemType, itemProgress);
            }
        }
    }


    /**
     * 更新玩家任务数据
     *
     * @param task 任务
     * @throws SQLException sql异常
     */
    public void updatePlayerTask(PlayerTask task) throws SQLException {
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection is not open. Call connect() first.");
        }
        String updatedAt = getCurrentTime();
        UUID playerUuid = task.getUUID();
        String taskId = task.getTask().getId();
        String taskStatus = task.getStatus().toString();
        int progress = task.getProgress();

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE players_tasks SET updated_at = ?, task_status = ?, task_progress = ? WHERE player_uuid = ? AND task_id = ?;"
        )) {
            ps.setString(1, updatedAt);
            ps.setString(2, taskStatus);
            ps.setInt(3, progress);
            ps.setString(4, playerUuid.toString());
            ps.setString(5, taskId);
            int affectedRows = ps.executeUpdate();

            if (affectedRows > 0) {
                // 获取玩家任务ID
                int playerTaskId = getPlayerTaskId(playerUuid, taskId);
                if (playerTaskId > 0) {
                    // 更新项目进度
                    updateItemProgress(playerTaskId, task);
                }
                log.info("成功更新玩家任务数据：" + playerUuid + "，任务ID：" + taskId + "，状态：" + taskStatus + "，进度：" + progress);
            } else {
                log.warn("未找到要更新的玩家任务数据：" + playerUuid + "，任务ID：" + taskId);
            }
        }
    }
    
    /**
     * 获取玩家任务ID
     * 
     * @param playerUuid 玩家UUID
     * @param taskId 任务ID
     * @return 玩家任务ID
     * @throws SQLException sql异常
     */
    private int getPlayerTaskId(UUID playerUuid, String taskId) throws SQLException {
        String sql = "SELECT id FROM players_tasks WHERE player_uuid = ? AND task_id = ?";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, taskId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        
        return -1;
    }
    
    /**
     * 更新项目进度
     * 
     * @param playerTaskId 玩家任务ID
     * @param playerTask 玩家任务
     * @throws SQLException sql异常
     */
    private void updateItemProgress(int playerTaskId, PlayerTask playerTask) throws SQLException {
        // 获取所有项目进度
        Map<String, Integer> itemProgressMap = playerTask.getItemProgressMap();
        if (itemProgressMap == null || itemProgressMap.isEmpty()) {
            return;
        }
        
        // 使用事务确保所有更新都成功或都失败
        conn.setAutoCommit(false);
        try {
            // 准备插入或更新语句
            String upsertSql = "INSERT INTO task_item_progress (player_task_id, item_type, progress) VALUES (?, ?, ?) " +
                              "ON CONFLICT (player_task_id, item_type) DO UPDATE SET progress = ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(upsertSql)) {
                for (Map.Entry<String, Integer> entry : itemProgressMap.entrySet()) {
                    String itemType = entry.getKey();
                    int progress = entry.getValue();
                    
                    pstmt.setInt(1, playerTaskId);
                    pstmt.setString(2, itemType);
                    pstmt.setInt(3, progress);
                    pstmt.setInt(4, progress);
                    
                    pstmt.addBatch();
                }
                
                pstmt.executeBatch();
                conn.commit();
            }
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }


    /**
     * 获取所有任务中玩家的uuid （players_tasks 表中）
     * @return
     * @throws SQLException
     */
    public List<UUID> getAllPlayerUUID() throws SQLException {
        if (conn == null || conn.isClosed()) {
            throw new SQLException("Connection is not open. Call connect() first.");
        }
        List<UUID> uuidList = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT player_uuid FROM players_tasks;"
            )) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    uuidList.add(UUID.fromString(rs.getString("player_uuid")));
                }
            }
        }
        return uuidList;
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
