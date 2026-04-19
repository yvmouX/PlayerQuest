package com.playerPlugin.playerTaskX.storage.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.api.service.ProgressStorage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MySQLProgressStorage implements ProgressStorage {
    private final HikariDataSource dataSource;
    private final ObjectMapper mapper;

    public MySQLProgressStorage(String host, int port, String database, String username, String password) {
        this.mapper = new ObjectMapper();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        this.dataSource = new HikariDataSource(config);
        initTables();
    }

    private void initTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS progress (
                    player_id VARCHAR(36) NOT NULL,
                    task_id VARCHAR(255) NOT NULL,
                    data TEXT NOT NULL,
                    PRIMARY KEY (player_id, task_id),
                    INDEX idx_player_id (player_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize MySQL tables", e);
        }
    }

    @Override
    public void create(UUID playerId, TaskDefinition taskDefinition) {
        String sql = "INSERT INTO progress (player_id, task_id, data) VALUES (?, ?, ?)";
        TaskProgress progress = new TaskProgress(playerId, taskDefinition.getId());
        progress.setStatus(PTXTaskStatus.IN_PROGRESS);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, progress.getTaskId());
            stmt.setString(3, mapper.writeValueAsString(progress));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create progress for player: " + playerId, e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize progress for player: " + playerId, e);
        }
    }

    @Override
    public void update(UUID playerId, TaskProgress progress) {
        String sql = "UPDATE progress SET data = ? WHERE player_id = ? AND task_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mapper.writeValueAsString(progress));
            stmt.setString(2, playerId.toString());
            stmt.setString(3, progress.getTaskId());
            if (stmt.executeUpdate() == 0) {
                throw new IllegalStateException("Progress does not exist for player: " + playerId + " task: " + progress.getTaskId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update progress for player: " + playerId, e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize progress for player: " + playerId, e);
        }
    }

    @Override
    public void save(UUID playerId, TaskProgress progress) {
        String sql = "INSERT INTO progress (player_id, task_id, data) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE data = VALUES(data)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, progress.getTaskId());
            stmt.setString(3, mapper.writeValueAsString(progress));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save progress for player: " + playerId, e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize progress for player: " + playerId, e);
        }
    }

    @Override
    public Optional<TaskProgress> findByPlayerAndTask(UUID playerId, String taskId) {
        String sql = "SELECT data FROM progress WHERE player_id = ? AND task_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, taskId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapper.readValue(rs.getString("data"), TaskProgress.class));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find progress for player: " + playerId + " task: " + taskId, e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize progress for player: " + playerId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<TaskProgress> findByPlayer(UUID playerId) {
        List<TaskProgress> progressList = new ArrayList<>();
        String sql = "SELECT data FROM progress WHERE player_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        progressList.add(mapper.readValue(rs.getString("data"), TaskProgress.class));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        // Skip invalid entries
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find progress for player: " + playerId, e);
        }
        return progressList;
    }

    @Override
    public void delete(UUID playerId, String taskId) {
        String sql = "DELETE FROM progress WHERE player_id = ? AND task_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, taskId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete progress for player: " + playerId + " task: " + taskId, e);
        }
    }

    public void close() {
        dataSource.close();
    }
}
