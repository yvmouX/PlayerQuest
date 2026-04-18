package com.playerPlugin.playerTaskX.storage.sqlite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.api.service.ProgressStorage;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SQLiteProgressStorage implements ProgressStorage {
    private final Connection connection;
    private final ObjectMapper mapper;

    public SQLiteProgressStorage(File dataFolder) {
        this.mapper = new ObjectMapper();
        File sqliteFolder = new File(dataFolder, "sqlite");
        sqliteFolder.mkdirs();
        try {
            String url = "jdbc:sqlite:" + new File(sqliteFolder, "progress.db").getAbsolutePath();
            this.connection = DriverManager.getConnection(url);
            initTables();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to SQLite", e);
        }
    }

    private void initTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS progress (
                    player_id TEXT NOT NULL,
                    task_id TEXT NOT NULL,
                    data TEXT NOT NULL,
                    PRIMARY KEY (player_id, task_id)
                )
            """);
        }
    }

    @Override
    public void save(UUID playerId, TaskProgress progress) {
        String sql = "INSERT OR REPLACE INTO progress (player_id, task_id, data) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, taskId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete progress for player: " + playerId + " task: " + taskId, e);
        }
    }

    public void close() throws SQLException {
        connection.close();
    }
}