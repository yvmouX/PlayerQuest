package com.playerPlugin.playerTaskX.storage.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MySQLTaskStorage implements TaskStorage {
    private final HikariDataSource dataSource;
    private final ObjectMapper mapper;

    public MySQLTaskStorage(String host, int port, String database, String username, String password) {
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
                CREATE TABLE IF NOT EXISTS tasks (
                    id VARCHAR(255) PRIMARY KEY,
                    data TEXT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize MySQL tables", e);
        }
    }

    @Override
    public void save(TaskDefinition task) {
        String sql = "INSERT INTO tasks (id, data) VALUES (?, ?) ON DUPLICATE KEY UPDATE data = VALUES(data)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
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
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete task: " + id, e);
        }
    }

    public void close() {
        dataSource.close();
    }
}
