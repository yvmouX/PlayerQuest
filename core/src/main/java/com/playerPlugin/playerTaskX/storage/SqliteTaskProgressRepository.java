package com.playerPlugin.playerTaskX.storage;

import cn.yvmou.ylib.api.logger.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerPlugin.playerTaskX.PlayerTaskX;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.api.storage.TaskProgressRepository;
import com.playerPlugin.playerTaskX.api.utils.TimeUtil;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SqliteTaskProgressRepository implements TaskProgressRepository {

    private final PlayerTaskX plugin;
    private final Logger log;
    private final ObjectMapper jsonMapper;
    private Connection connection;

    public SqliteTaskProgressRepository(PlayerTaskX plugin, Logger log) {
        this.plugin = plugin;
        this.log = log;
        this.jsonMapper = new ObjectMapper();
        initDatabase();
    }

    private void initDatabase() {
        try {
            java.io.File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            java.io.File dbFile = new java.io.File(dataFolder, "player_tasks.db");
            // Load the SQLite JDBC driver explicitly to ensure it's registered
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                log.error("SQLite JDBC driver not found!", e);
                return;
            }

            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            try (Statement stmt = connection.createStatement()) {
                String sql = "CREATE TABLE IF NOT EXISTS task_progress (" +
                        "uuid TEXT NOT NULL, " +
                        "task_id TEXT NOT NULL, " +
                        "status TEXT NOT NULL, " +
                        "create_at INTEGER NOT NULL, " +
                        "update_at INTEGER NOT NULL, " +
                        "PRIMARY KEY (uuid, task_id))";
                stmt.execute(sql);

                String sqlObj = "CREATE TABLE IF NOT EXISTS task_objective_progress (" +
                        "uuid TEXT NOT NULL, " +
                        "task_id TEXT NOT NULL, " +
                        "objective_id TEXT NOT NULL, " +
                        "current_amount INTEGER NOT NULL, " +
                        "PRIMARY KEY (uuid, task_id, objective_id), " +
                        "FOREIGN KEY (uuid, task_id) REFERENCES task_progress(uuid, task_id) ON DELETE CASCADE)";
                stmt.execute(sqlObj);
            }
        } catch (SQLException e) {
            log.error("Failed to initialize SQLite database", e);
        }
    }

    @Override
    public void create(Player player, TaskDefinition taskDefinition) {
        if (find(player, taskDefinition.getId()).isPresent()) {
            log.warn("Task progress repository for player {} already exists, skipping creation.", player.getName());
            return;
        }

        TaskProgress taskProgress = new TaskProgress(
                player.getUniqueId(),
                taskDefinition.getId()
        );
        save(taskProgress);
        log.debug("Created task progress for player {} task {}.", player.getName(), taskDefinition.getId());
    }

    @Override
    public Optional<TaskProgress> find(Player player, String taskId) {
        String sql = "SELECT status, create_at, update_at FROM task_progress WHERE uuid = ? AND task_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, player.getUniqueId().toString());
            pstmt.setString(2, taskId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(buildTaskProgress(player.getUniqueId(), taskId, rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error finding task progress for player {}: {}", player.getName(), e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<TaskProgress> findAll(Player player) {
        List<TaskProgress> result = new ArrayList<>();
        String sql = "SELECT task_id, status, create_at, update_at FROM task_progress WHERE uuid = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, player.getUniqueId().toString());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String taskId = rs.getString("task_id");
                    result.add(buildTaskProgress(player.getUniqueId(), taskId, rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error finding all task progress for player {}: {}", player.getName(), e.getMessage());
        }
        
        return result;
    }

    private TaskProgress buildTaskProgress(UUID uuid, String taskId, ResultSet rs) throws SQLException {
        String statusStr = rs.getString("status");
        long createAt = rs.getLong("create_at");
        long updateAt = rs.getLong("update_at");

        PTXTaskStatus status = PTXTaskStatus.valueOf(statusStr);
        
        java.util.Map<String, Integer> objectiveProgress = new java.util.HashMap<>();
        String sqlObj = "SELECT objective_id, current_amount FROM task_objective_progress WHERE uuid = ? AND task_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sqlObj)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, taskId);
            try (ResultSet rsObj = pstmt.executeQuery()) {
                while (rsObj.next()) {
                    objectiveProgress.put(rsObj.getString("objective_id"), rsObj.getInt("current_amount"));
                }
            }
        }
        
        return new TaskProgress(uuid, taskId, status, createAt, updateAt, objectiveProgress);
    }

    @Override
    public void update(TaskProgress progress) {
        save(progress);
    }

    @Override
    public void save(TaskProgress progress) {
        String sql = "INSERT OR REPLACE INTO task_progress(uuid, task_id, status, create_at, update_at) VALUES(?, ?, ?, ?, ?)";
        String sqlObj = "INSERT OR REPLACE INTO task_objective_progress(uuid, task_id, objective_id, current_amount) VALUES(?, ?, ?, ?)";
        
        try {
            connection.setAutoCommit(false);

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, progress.getUuid().toString());
                pstmt.setString(2, progress.getTaskId());
                pstmt.setString(3, progress.getStatus().name());
                pstmt.setLong(4, progress.getCreateAt());
                pstmt.setLong(5, progress.getUpdateAt());
                pstmt.executeUpdate();
            }

            try (PreparedStatement pstmtObj = connection.prepareStatement(sqlObj)) {
                for (var entry : progress.getObjectiveProgress().entrySet()) {
                    pstmtObj.setString(1, progress.getUuid().toString());
                    pstmtObj.setString(2, progress.getTaskId());
                    pstmtObj.setString(3, entry.getKey());
                    pstmtObj.setInt(4, entry.getValue());
                    pstmtObj.addBatch();
                }
                pstmtObj.executeBatch();
            }

            connection.commit();
            log.debug("Successfully saved task {} for player {}", progress.getTaskId(), progress.getUuid());
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ex) {
                log.error("Error rolling back transaction", ex);
            }
            log.error("Error saving task progress for player {}: {}", progress.getUuid(), e.getMessage());
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                log.error("Error resetting auto commit", e);
            }
        }
    }

    @Override
    public void delete(UUID uuid, String taskId) {
        String sql = "DELETE FROM task_progress WHERE uuid = ? AND task_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, taskId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error deleting task progress for player {}: {}", uuid, e.getMessage());
        }
    }
}
