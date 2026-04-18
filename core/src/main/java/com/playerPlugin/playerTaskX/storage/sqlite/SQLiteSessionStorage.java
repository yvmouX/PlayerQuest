package com.playerPlugin.playerTaskX.storage.sqlite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.storage.SessionStorage;

import java.io.File;
import java.sql.*;
import java.util.*;

public class SQLiteSessionStorage implements SessionStorage {
    private final File dbFile;
    private final ObjectMapper mapper;
    private Connection connection;
    
    public SQLiteSessionStorage(File dbFile) {
        this.dbFile = dbFile;
        this.mapper = new ObjectMapper();
        initTables();
    }
    
    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        }
        return connection;
    }
    
    private void initTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS quest_sessions (
                    player_uuid TEXT NOT NULL,
                    quest_id TEXT NOT NULL,
                    current_node_id TEXT,
                    completed_nodes TEXT,
                    context_data TEXT,
                    start_time INTEGER,
                    last_active INTEGER,
                    status TEXT,
                    PRIMARY KEY (player_uuid, quest_id)
                )
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init session tables", e);
        }
    }
    
    @Override
    public void save(QuestSession session) {
        String sql = """ 
            INSERT OR REPLACE INTO quest_sessions 
            (player_uuid, quest_id, current_node_id, completed_nodes, context_data, start_time, last_active, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = getConnection();
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
        try (Connection conn = getConnection();
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
        try (Connection conn = getConnection();
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
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
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
        try (Connection conn = getConnection();
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
            session.setStatus(com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus.valueOf(statusStr));
        }
        return session;
    }
}
