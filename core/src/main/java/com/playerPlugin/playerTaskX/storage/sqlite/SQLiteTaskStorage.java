package com.playerPlugin.playerTaskX.storage.sqlite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SQLiteTaskStorage implements TaskStorage {
    private final Connection connection;
    private final ObjectMapper mapper;

    public SQLiteTaskStorage(File dataFolder) {
        this.mapper = new ObjectMapper();
        File sqliteFolder = new File(dataFolder, "sqlite");
        sqliteFolder.mkdirs();
        try {
            String url = "jdbc:sqlite:" + new File(sqliteFolder, "tasks.db").getAbsolutePath();
            this.connection = DriverManager.getConnection(url);
            initTables();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to SQLite", e);
        }
    }

    private void initTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY,
                    data TEXT NOT NULL
                )
            """);
        }
    }

    @Override
    public void save(TaskDefinition task) {
        String sql = "INSERT OR REPLACE INTO tasks (id, data) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, task.getId());
            stmt.setString(2, mapper.writeValueAsString(task));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save task: " + task.getId(), e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize task: " + task.getId(), e);
        }
    }

    @Override
    public Optional<TaskDefinition> findById(String id) {
        String sql = "SELECT data FROM tasks WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapper.readValue(rs.getString("data"), TaskDefinition.class));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find task: " + id, e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize task: " + id, e);
        }
        return Optional.empty();
    }

    @Override
    public List<TaskDefinition> findAll() {
        List<TaskDefinition> tasks = new ArrayList<>();
        String sql = "SELECT data FROM tasks";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    tasks.add(mapper.readValue(rs.getString("data"), TaskDefinition.class));
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    // Skip invalid entries
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all tasks", e);
        }
        return tasks;
    }

    @Override
    public void delete(String id) {
        String sql = "DELETE FROM tasks WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete task: " + id, e);
        }
    }

    public void close() throws SQLException {
        connection.close();
    }
}