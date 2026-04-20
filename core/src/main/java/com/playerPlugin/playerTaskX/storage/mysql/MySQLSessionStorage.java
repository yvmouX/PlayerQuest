package com.playerPlugin.playerTaskX.storage.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.api.service.SessionStorage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;

public class MySQLSessionStorage implements SessionStorage {
    private final HikariDataSource dataSource;
    private final ObjectMapper mapper;
    
    public MySQLSessionStorage(String host, int port, String database, String username, String password) {
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
                CREATE TABLE IF NOT EXISTS quest_sessions (
                    player_uuid VARCHAR(36) NOT NULL,
                    quest_id VARCHAR(255) NOT NULL,
                    current_node_id VARCHAR(255),
                    completed_nodes TEXT,
                    context_data TEXT,
                    start_time BIGINT,
                    last_active BIGINT,
                    status VARCHAR(20),
                    PRIMARY KEY (player_uuid, quest_id),
                    INDEX idx_player (player_uuid),
                    INDEX idx_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init session tables", e);
        }
    }
    
    @Override
    public void save(QuestSession session) {
        String sql = """ 
            INSERT INTO quest_sessions 
            (player_uuid, quest_id, current_node_id, completed_nodes, context_data, start_time, last_active, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
            current_node_id = VALUES(current_node_id),
            completed_nodes = VALUES(completed_nodes),
            context_data = VALUES(context_data),
            last_active = VALUES(last_active),
            status = VALUES(status)
        """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, session.getPlayerId().toString());
            stmt.setString(2, session.getQuestId());
            stmt.setString(3, session.getCurrentNodeId());
            stmt.setString(4, mapper.writeValueAsString(session.getCompletedNodes()));
            stmt.setString(5, mapper.writeValueAsString(session.getContext()));
            stmt.setLong(6, session.getStartTime());
            stmt.setLong(7, session.getLastActiveTime());
            stmt.setString(8, session.getStatus().name());
            stmt.executeUpdate();
        } catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to save session", e);
        }
    }
    
    @Override
    public Optional<QuestSession> find(UUID playerId, String questId) {
        String sql = "SELECT * FROM quest_sessions WHERE player_uuid = ? AND quest_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, questId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to find session", e);
        }
        return Optional.empty();
    }
    
    @Override
    public Collection<QuestSession> findByPlayer(UUID playerId) {
        List<QuestSession> sessions = new ArrayList<>();
        String sql = "SELECT * FROM quest_sessions WHERE player_uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sessions.add(mapRow(rs));
                }
            }
        } catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to find sessions", e);
        }
        return sessions;
    }
    
    @Override
    public Collection<QuestSession> findAllActive() {
        List<QuestSession> sessions = new ArrayList<>();
        String sql = "SELECT * FROM quest_sessions WHERE status = 'IN_PROGRESS'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                sessions.add(mapRow(rs));
            }
        } catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to find active sessions", e);
        }
        return sessions;
    }
    
    @Override
    public void delete(UUID playerId, String questId) {
        String sql = "DELETE FROM quest_sessions WHERE player_uuid = ? AND quest_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, questId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete session", e);
        }
    }
    
    private QuestSession mapRow(ResultSet rs) throws SQLException, com.fasterxml.jackson.core.JsonProcessingException {
        UUID playerId = UUID.fromString(rs.getString("player_uuid"));
        String questId = rs.getString("quest_id");
        String currentNodeId = rs.getString("current_node_id");
        QuestSession session = new QuestSession(playerId, questId, currentNodeId);
        
        String completedNodesJson = rs.getString("completed_nodes");
        if (completedNodesJson != null) {
            List<String> completedNodes = mapper.readValue(completedNodesJson, 
                mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            completedNodes.forEach(session::markNodeCompleted);
        }
        
        String contextJson = rs.getString("context_data");
        if (contextJson != null) {
            Map<String, Object> context = mapper.readValue(contextJson,
                mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            session.updateContext(context);
        }
        
        session.setLastActiveTime(rs.getLong("last_active"));
        String statusStr = rs.getString("status");
        if (statusStr != null) {
            session.setStatus(PTXTaskStatus.valueOf(statusStr));
        }
        
        return session;
    }
}
