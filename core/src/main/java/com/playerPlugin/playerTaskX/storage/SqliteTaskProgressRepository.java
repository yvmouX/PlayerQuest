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

import java.io.File;
import java.io.IOException;
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
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File dbFile = new File(dataFolder, "player_tasks.db");
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
                        "data TEXT NOT NULL, " +
                        "PRIMARY KEY (uuid, task_id))";
                stmt.execute(sql);
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
                taskDefinition,
                PTXTaskStatus.IN_PROGRESS,
                TimeUtil.getTime(),
                TimeUtil.getTime()
        );
        save(taskProgress);
        log.debug("Created task progress for player {} task {}.", player.getName(), taskDefinition.getId());
    }

    @Override
    public Optional<TaskProgress> find(Player player, String taskId) {
        String sql = "SELECT data FROM task_progress WHERE uuid = ? AND task_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, player.getUniqueId().toString());
            pstmt.setString(2, taskId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String data = rs.getString("data");
                    try {
                        TaskProgress progress = jsonMapper.readValue(data, TaskProgress.class);
                        return Optional.of(progress);
                    } catch (IOException e) {
                        log.error("Error deserializing task progress for player {}: {}", player.getName(), e.getMessage());
                    }
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
        String sql = "SELECT data FROM task_progress WHERE uuid = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, player.getUniqueId().toString());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String data = rs.getString("data");
                    try {
                        TaskProgress progress = jsonMapper.readValue(data, TaskProgress.class);
                        result.add(progress);
                    } catch (IOException e) {
                        log.error("Error deserializing task progress for player {}: {}", player.getName(), e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Error finding all task progress for player {}: {}", player.getName(), e.getMessage());
        }
        
        return result;
    }

    @Override
    public void update(TaskProgress progress) {
        save(progress);
    }

    @Override
    public void save(TaskProgress progress) {
        String sql = "INSERT OR REPLACE INTO task_progress(uuid, task_id, data) VALUES(?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, progress.getUuid().toString());
            pstmt.setString(2, progress.getTaskDefinition().getId());
            pstmt.setString(3, jsonMapper.writeValueAsString(progress));
            
            pstmt.executeUpdate();
            log.debug("Successfully saved task {} for player {}", progress.getTaskDefinition().getId(), progress.getUuid());
        } catch (SQLException | JsonProcessingException e) {
            log.error("Error saving task progress for player {}: {}", progress.getUuid(), e.getMessage());
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
