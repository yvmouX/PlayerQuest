# Quest Graph Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace linear objectives with a graph-based quest execution engine using event-driven state machine

**Architecture:** Event-driven state machine where quests traverse a `QuestGraph` (nodes + edges). Each node type has a `NodeHandler` that processes events and determines the next node. `QuestSession` tracks player progress through the graph.

**Tech Stack:** Java (Bukkit/Spigot plugin), Javalin HTTP API, MySQL/SQLite storage

---

## File Structure

```
api/src/main/java/com/playerPlugin/playerTaskX/api/
├── model/
│   ├── QuestGraph.java           (existing)
│   ├── GraphNode.java            (existing)
│   ├── NodeConnection.java       (existing)
│   ├── TaskDefinition.java       (MODIFY - add graph field)
│   └── session/                  (CREATE)
│       ├── QuestSession.java
│       └── NextNodeResult.java
└── handler/                      (CREATE)
    ├── NodeHandler.java
    └── NodeHandlerRegistry.java

core/src/main/java/com/playerPlugin/playerTaskX/
├── engine/                       (CREATE)
│   ├── QuestEngine.java
│   ├── QuestSessionManager.java
│   └── exception/
│       └── GraphExecutionException.java
├── handler/                      (CREATE)
│   ├── StartNodeHandler.java
│   ├── TaskNodeHandler.java
│   ├── CompletionNodeHandler.java
│   ├── ConditionNodeHandler.java
│   ├── BranchNodeHandler.java
│   ├── ActionNodeHandler.java
│   ├── EventNodeHandler.java
│   ├── CounterNodeHandler.java
│   ├── TimerNodeHandler.java
│   ├── StateNodeHandler.java
│   └── SubtaskNodeHandler.java
├── storage/
│   └── SessionStorage.java       (CREATE)
├── listener/
│   └── GraphEventListener.java   (REFACTOR from EntityListener)
└── web/
    └── EditorServer.java         (MODIFY - remove GraphManager)

task-editor-vue/src/              (MODIFY - align types)
├── types/index.ts                (existing - no change needed)
└── services/api.ts               (MODIFY - remove graph API calls)
```

---

## Phase 1: Infrastructure

### Task 1: Add `graph` field to `TaskDefinition`

**Files:**
- Modify: `api/src/main/java/com/playerPlugin/playerTaskX/api/model/TaskDefinition.java`

- [ ] **Step 1: Modify TaskDefinition to add graph field**

```java
public class TaskDefinition {
    private final String id;
    private final String name;
    private final String description;
    private final PTXTaskType taskType;
    private final List<Objective> objectives;  // Deprecated, use graph
    private final List<Reward> rewards;
    private final List<Condition> conditions;  // Deprecated, use graph
    private final QuestGraph graph;  // NEW
    
    @JsonCreator
    public TaskDefinition(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("taskType") PTXTaskType taskType,
            @JsonProperty("objectives") List<Objective> objectives,
            @JsonProperty("rewards") List<Reward> rewards,
            @JsonProperty("conditions") List<Condition> conditions,
            @JsonProperty("graph") QuestGraph graph  // NEW
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.taskType = taskType != null ? taskType : PTXTaskType.FOREVER;
        this.objectives = objectives != null ? new ArrayList<>(objectives) : new ArrayList<>();
        this.rewards = rewards != null ? new ArrayList<>(rewards) : new ArrayList<>();
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
        this.graph = graph;  // NEW
    }
    
    // NEW
    public QuestGraph getGraph() { return graph; }
    
    // NEW: Helper to check if using graph mode
    public boolean hasGraph() { return graph != null && !graph.getNodes().isEmpty(); }
}
```

- [ ] **Step 2: Run build to verify compilation**

Run: `cd PlayerTaskX && ./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add api/src/main/java/com/playerPlugin/playerTaskX/api/model/TaskDefinition.java
git commit -m "feat(api): add graph field to TaskDefinition"
```

---

### Task 2: Create session models (QuestSession, NextNodeResult)

**Files:**
- Create: `api/src/main/java/com/playerPlugin/playerTaskX/api/model/session/QuestSession.java`
- Create: `api/src/main/java/com/playerPlugin/playerTaskX/api/model/session/NextNodeResult.java`

- [ ] **Step 1: Create QuestSession.java**

```java
package com.playerPlugin.playerTaskX.api.model.session;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import java.util.*;

public class QuestSession {
    private final UUID playerId;
    private final String questId;
    private String currentNodeId;
    private final Set<String> completedNodes;
    private final Map<String, Object> context;
    private final long startTime;
    private long lastActiveTime;
    private PTXTaskStatus status;
    
    public QuestSession(UUID playerId, String questId, String currentNodeId) {
        this.playerId = playerId;
        this.questId = questId;
        this.currentNodeId = currentNodeId;
        this.completedNodes = new HashSet<>();
        this.context = new HashMap<>();
        this.startTime = System.currentTimeMillis();
        this.lastActiveTime = startTime;
        this.status = PTXTaskStatus.IN_PROGRESS;
    }
    
    // Getters
    public UUID getPlayerId() { return playerId; }
    public String getQuestId() { return questId; }
    public String getCurrentNodeId() { return currentNodeId; }
    public Set<String> getCompletedNodes() { return Collections.unmodifiableSet(completedNodes); }
    public Map<String, Object> getContext() { return Collections.unmodifiableMap(context); }
    public long getStartTime() { return startTime; }
    public long getLastActiveTime() { return lastActiveTime; }
    public PTXTaskStatus getStatus() { return status; }
    
    // Setters
    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
        this.lastActiveTime = System.currentTimeMillis();
    }
    
    public void markNodeCompleted(String nodeId) {
        this.completedNodes.add(nodeId);
        this.lastActiveTime = System.currentTimeMillis();
    }
    
    public void updateContext(Map<String, Object> updates) {
        this.context.putAll(updates);
        this.lastActiveTime = System.currentTimeMillis();
    }
    
    public void setStatus(PTXTaskStatus status) {
        this.status = status;
    }
}
```

- [ ] **Step 2: Create NextNodeResult.java**

```java
package com.playerPlugin.playerTaskX.api.model.session;

import java.util.Map;

public class NextNodeResult {
    private final String nextNodeId;
    private final Map<String, Object> contextUpdate;
    private final boolean terminal;
    
    public NextNodeResult(String nextNodeId, Map<String, Object> contextUpdate, boolean terminal) {
        this.nextNodeId = nextNodeId;
        this.contextUpdate = contextUpdate != null ? contextUpdate : Map.of();
        this.terminal = terminal;
    }
    
    public static NextNodeResult wait() {
        return new NextNodeResult(null, Map.of(), false);
    }
    
    public static NextNodeResult next(String nodeId) {
        return new NextNodeResult(nodeId, Map.of(), false);
    }
    
    public static NextNodeResult next(String nodeId, Map<String, Object> contextUpdate) {
        return new NextNodeResult(nodeId, contextUpdate, false);
    }
    
    public static NextNodeResult terminal(String nodeId) {
        return new NextNodeResult(nodeId, Map.of(), true);
    }
    
    public String getNextNodeId() { return nextNodeId; }
    public Map<String, Object> getContextUpdate() { return contextUpdate; }
    public boolean isTerminal() { return terminal; }
}
```

- [ ] **Step 3: Run build to verify compilation**

Run: `cd PlayerTaskX && ./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add api/src/main/java/com/playerPlugin/playerTaskX/api/model/session/
git commit -m "feat(api): add QuestSession and NextNodeResult models"
```

---

### Task 3: Create NodeHandler interface and Registry

**Files:**
- Create: `api/src/main/java/com/playerPlugin/playerTaskX/api/handler/NodeHandler.java`
- Create: `api/src/main/java/com/playerPlugin/playerTaskX/api/handler/NodeHandlerRegistry.java`

- [ ] **Step 1: Create NodeHandler.java**

```java
package com.playerPlugin.playerTaskX.api.handler;

import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public interface NodeHandler {
    String getNodeType();
    
    NextNodeResult execute(QuestSession session, Event event, QuestGraph graph);
    
    boolean canHandle(String nodeType);
}
```

- [ ] **Step 2: Create NodeHandlerRegistry.java**

```java
package com.playerPlugin.playerTaskX.api.handler;

import java.util.*;

public class NodeHandlerRegistry {
    private final Map<String, NodeHandler> handlers = new HashMap<>();
    
    public void register(NodeHandler handler) {
        handlers.put(handler.getNodeType(), handler);
    }
    
    public NodeHandler getHandler(String nodeType) {
        return handlers.get(nodeType);
    }
    
    public Collection<NodeHandler> getAllHandlers() {
        return handlers.values();
    }
}
```

- [ ] **Step 3: Run build to verify compilation**

Run: `cd PlayerTaskX && ./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add api/src/main/java/com/playerPlugin/playerTaskX/api/handler/
git commit -m "feat(api): add NodeHandler interface and NodeHandlerRegistry"
```

---

## Phase 2: Core Engine

### Task 4: Create GraphExecutionException

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/engine/exception/GraphExecutionException.java`

- [ ] **Step 1: Create GraphExecutionException.java**

```java
package com.playerPlugin.playerTaskX.engine.exception;

public class GraphExecutionException extends RuntimeException {
    public GraphExecutionException(String message) {
        super(message);
    }
    
    public GraphExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 2: Run build**

Run: `cd PlayerTaskX && ./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/engine/exception/
git commit -m "feat(engine): add GraphExecutionException"
```

---

### Task 5: Create QuestSessionManager

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/engine/QuestSessionManager.java`

- [ ] **Step 1: Create QuestSessionManager.java**

```java
package com.playerPlugin.playerTaskX.engine;

import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QuestSessionManager {
    private final Map<UUID, Map<String, QuestSession>> playerSessions = new ConcurrentHashMap<>();
    
    public void createSession(QuestSession session) {
        playerSessions
            .computeIfAbsent(session.getPlayerId(), k -> new ConcurrentHashMap<>())
            .put(session.getQuestId(), session);
    }
    
    public Optional<QuestSession> getSession(UUID playerId, String questId) {
        Map<String, QuestSession> sessions = playerSessions.get(playerId);
        if (sessions == null) return Optional.empty();
        return Optional.ofNullable(sessions.get(questId));
    }
    
    public Collection<QuestSession> getPlayerSessions(UUID playerId) {
        Map<String, QuestSession> sessions = playerSessions.get(playerId);
        if (sessions == null) return Collections.emptyList();
        return Collections.unmodifiableCollection(sessions.values());
    }
    
    public Collection<QuestSession> getAllSessions() {
        return playerSessions.values().stream()
            .flatMap(m -> m.values().stream())
            .toList();
    }
    
    public void removeSession(UUID playerId, String questId) {
        Map<String, QuestSession> sessions = playerSessions.get(playerId);
        if (sessions != null) {
            sessions.remove(questId);
        }
    }
    
    public void clearPlayerSessions(UUID playerId) {
        playerSessions.remove(playerId);
    }
}
```

- [ ] **Step 2: Run build**

Run: `cd PlayerTaskX && ./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/engine/QuestSessionManager.java
git commit -m "feat(engine): add QuestSessionManager"
```

---

### Task 6: Create SessionStorage

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/storage/SessionStorage.java`

- [ ] **Step 1: Create SessionStorage interface**

```java
package com.playerPlugin.playerTaskX.storage;

import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SessionStorage {
    void save(QuestSession session);
    Optional<QuestSession> find(UUID playerId, String questId);
    Collection<QuestSession> findByPlayer(UUID playerId);
    Collection<QuestSession> findAllActive();
    void delete(UUID playerId, String questId);
}
```

- [ ] **Step 2: Implement MySQLSessionStorage**

Create `core/src/main/java/com/playerPlugin/playerTaskX/storage/mysql/MySQLSessionStorage.java`:

```java
package com.playerPlugin.playerTaskX.storage.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.api.service.SessionStorage;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;

public class MySQLSessionStorage implements SessionStorage {
    private final HikariDataSource dataSource;
    private final ObjectMapper mapper;
    
    public MySQLSessionStorage(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.mapper = new ObjectMapper();
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
        } catch (SQLException e) {
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
        } catch (SQLException e) {
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
        } catch (SQLException e) {
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
        
        // Restore completed nodes
        String completedNodesJson = rs.getString("completed_nodes");
        if (completedNodesJson != null) {
            List<String> completedNodes = mapper.readValue(completedNodesJson, 
                mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            completedNodes.forEach(session::markNodeCompleted);
        }
        
        // Restore context
        String contextJson = rs.getString("context_data");
        if (contextJson != null) {
            Map<String, Object> context = mapper.readValue(contextJson,
                mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            session.updateContext(context);
        }
        
        // Restore timestamps and status
        session.setLastActiveTime(rs.getLong("last_active"));
        String statusStr = rs.getString("status");
        if (statusStr != null) {
            session.setStatus(PTXTaskStatus.valueOf(statusStr));
        }
        
        return session;
    }
}
```

- [ ] **Step 3: Implement SQLiteSessionStorage**

Create `core/src/main/java/com/playerPlugin/playerTaskX/storage/sqlite/SQLiteSessionStorage.java`:

```java
package com.playerPlugin.playerTaskX.storage.sqlite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.api.service.SessionStorage;

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
        } catch (SQLException e) {
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
        } catch (SQLException e) {
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
        } catch (SQLException e) {
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
    
    private QuestSession mapRow(ResultSet rs) throws SQLException {
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
```

- [ ] **Step 4: Run build**

Run: `cd PlayerTaskX && ./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL (may have warnings about unchecked)

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/storage/SessionStorage.java
git add core/src/main/java/com/playerPlugin/playerTaskX/storage/mysql/MySQLSessionStorage.java
git add core/src/main/java/com/playerPlugin/playerTaskX/storage/sqlite/SQLiteSessionStorage.java
git commit -m "feat(storage): add SessionStorage interface and implementations"
```

---

### Task 7: Create QuestEngine (main logic)

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/engine/QuestEngine.java`

- [ ] **Step 1: Create QuestEngine.java**

```java
package com.playerPlugin.playerTaskX.engine;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.handler.NodeHandlerRegistry;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.engine.exception.GraphExecutionException;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import com.playerPlugin.playerTaskX.api.service.SessionStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class QuestEngine {
    private static final Logger log = LoggerFactory.getLogger(QuestEngine.class);
    
    private final QuestSessionManager sessionManager;
    private final NodeHandlerRegistry handlerRegistry;
    private final TaskManager taskManager;
    private final SessionStorage sessionStorage;
    
    public QuestEngine(QuestSessionManager sessionManager,
                       NodeHandlerRegistry handlerRegistry,
                       TaskManager taskManager,
                       SessionStorage sessionStorage) {
        this.sessionManager = sessionManager;
        this.handlerRegistry = handlerRegistry;
        this.taskManager = taskManager;
        this.sessionStorage = sessionStorage;
    }
    
    public void startQuest(Player player, TaskDefinition task) {
        QuestGraph graph = task.getGraph();
        if (graph == null || !task.hasGraph()) {
            throw new GraphExecutionException("Task " + task.getId() + " has no graph");
        }
        
        String startNodeId = findStartNode(graph);
        if (startNodeId == null) {
            throw new GraphExecutionException("No start node found in graph");
        }
        
        QuestSession session = new QuestSession(player.getUniqueId(), task.getId(), startNodeId);
        sessionManager.createSession(session);
        sessionStorage.save(session);
    }
    
    public void handleEvent(Player player, Event event) {
        Collection<QuestSession> sessions = sessionManager.getPlayerSessions(player.getUniqueId());
        
        for (QuestSession session : sessions) {
            if (session.getStatus() != PTXTaskStatus.IN_PROGRESS) continue;
            
            TaskDefinition task = taskManager.getTask(session.getQuestId()).orElse(null);
            if (task == null || !task.hasGraph()) continue;
            
            QuestGraph graph = task.getGraph();
            String currentNodeId = session.getCurrentNodeId();
            
            GraphNode currentNode = findNode(graph, currentNodeId);
            if (currentNode == null) {
                log.warn("Session {} has invalid current node {}", session, currentNodeId);
                continue;
            }
            
            NodeHandler handler = handlerRegistry.getHandler(currentNode.getNodeType());
            if (handler == null) {
                log.warn("No handler for node type: {}", currentNode.getNodeType());
                continue;
            }
            
            NextNodeResult result = handler.execute(session, event, graph);
            
            if (result.getNextNodeId() != null) {
                session.setCurrentNodeId(result.getNextNodeId());
                session.updateContext(result.getContextUpdate());
                
                if (result.isTerminal()) {
                    completeQuest(session, task);
                }
                
                sessionStorage.save(session);
            }
        }
    }
    
    public void abandonQuest(Player player, String questId) {
        sessionManager.getSession(player.getUniqueId(), questId)
            .ifPresent(session -> {
                session.setStatus(PTXTaskStatus.ABANDONED);
                sessionStorage.save(session);
                sessionManager.removeSession(player.getUniqueId(), questId);
            });
    }
    
    public void restoreSessions() {
        Collection<QuestSession> activeSessions = sessionStorage.findAllActive();
        for (QuestSession session : activeSessions) {
            sessionManager.createSession(session);
        }
    }
    
    private void completeQuest(QuestSession session, TaskDefinition task) {
        session.setStatus(PTXTaskStatus.COMPLETED);
        session.markNodeCompleted(session.getCurrentNodeId());
        
        // Get player from Bukkit server
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) {
            log.warn("Cannot grant rewards - player {} not online", session.getPlayerId());
            return;
        }
        
        // Grant rewards
        for (var reward : task.getRewards()) {
            reward.grant(player);
        }
    }
    
    private String findStartNode(QuestGraph graph) {
        return graph.getNodes().stream()
            .filter(n -> "start".equals(n.getNodeType()))
            .map(GraphNode::getId)
            .findFirst()
            .orElseGet(() -> graph.getNodes().isEmpty() ? null : graph.getNodes().get(0).getId());
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    protected List<GraphNode> getOutgoingEdges(QuestGraph graph, String nodeId) {
        return graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(nodeId))
            .map(e -> findNode(graph, e.getTargetId()))
            .filter(Objects::nonNull)
            .toList();
    }
}
```

- [ ] **Step 2: Run build**

Run: `cd PlayerTaskX && ./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/engine/QuestEngine.java
git commit -m "feat(engine): add QuestEngine core logic"
```

---

## Phase 3: Node Handlers

### Task 8: Core Handlers (Start, Task, Completion)

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/handler/StartNodeHandler.java`
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/handler/TaskNodeHandler.java`
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/handler/CompletionNodeHandler.java`

**Note:** Player is obtained from session context via `Bukkit.getPlayer(session.getPlayerId())` since NodeHandler.execute() only receives the session.

- [ ] **Step 1: Create StartNodeHandler.java**

```java
package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class StartNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "start"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        session.markNodeCompleted(session.getCurrentNodeId());
        
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(session.getCurrentNodeId()))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.wait();
        }
        
        String nextNodeId = nextNodes.get(0);
        return NextNodeResult.next(nextNodeId);
    }
    
    @Override
    public boolean canHandle(String nodeType) {
        return "start".equals(nodeType);
    }
}
```

- [ ] **Step 2: Create TaskNodeHandler.java**

```java
package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.api.model.objective.Objective;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class TaskNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "task"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.wait();
        
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return NextNodeResult.wait();
        
        Map<String, Object> data = currentNode.getData();
        if (data == null) return NextNodeResult.wait();
        
        List<Objective> objectives = (List<Objective>) data.get("objectives");
        if (objectives == null || objectives.isEmpty()) {
            session.markNodeCompleted(session.getCurrentNodeId());
            return getNextNode(graph, session.getCurrentNodeId());
        }
        
        boolean allComplete = true;
        for (Objective obj : objectives) {
            if (!obj.isCompleted(player)) {
                allComplete = false;
                if (obj.matchesEvent(event)) {
                    obj.applyProgress(player, 1);
                }
            }
        }
        
        if (allComplete) {
            session.markNodeCompleted(session.getCurrentNodeId());
            return getNextNode(graph, session.getCurrentNodeId());
        }
        
        return NextNodeResult.wait();
    }
    
    private NextNodeResult getNextNode(QuestGraph graph, String currentNodeId) {
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(currentNodeId))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(currentNodeId);
        }
        
        return NextNodeResult.next(nextNodes.get(0));
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public boolean canHandle(String nodeType) {
        return "task".equals(nodeType);
    }
}
```

- [ ] **Step 3: Create CompletionNodeHandler.java**

```java
package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.api.model.reward.Reward;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class CompletionNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "completion"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.terminal(session.getCurrentNodeId());
        
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return NextNodeResult.terminal(session.getCurrentNodeId());
        
        Map<String, Object> data = currentNode.getData();
        if (data == null) {
            session.markNodeCompleted(session.getCurrentNodeId());
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        List<Map<String, Object>> rewardsData = (List<Map<String, Object>>) data.get("rewards");
        if (rewardsData != null) {
            for (Map<String, Object> rewardData : rewardsData) {
                grantReward(player, rewardData);
            }
        }
        
        session.markNodeCompleted(session.getCurrentNodeId());
        return NextNodeResult.terminal(session.getCurrentNodeId());
    }
    
    private void grantReward(Player player, Map<String, Object> rewardData) {
        String type = (String) rewardData.get("type");
        Object value = rewardData.get("value");
        
        switch (type) {
            case "item" -> {
                if (value instanceof String materialName) {
                    try {
                        org.bukkit.Material material = org.bukkit.Material.valueOf(materialName.toUpperCase());
                        int amount = ((Number) rewardData.getOrDefault("amount", 1)).intValue();
                        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(material, amount));
                    } catch (IllegalArgumentException e) {
                        org.slf4j.LoggerFactory.getLogger(CompletionNodeHandler.class)
                            .warn("Invalid material for reward: {}", value);
                    }
                }
            }
            case "xp" -> {
                if (value instanceof Number) {
                    player.giveExp(((Number) value).intValue());
                }
            }
            case "money" -> {
                // Economy plugin integration - requires Vault or similar
                // Implementation: EconomyResponse response = economy.depositPlayer(player, amount);
                org.slf4j.LoggerFactory.getLogger(CompletionNodeHandler.class)
                    .info("Money reward of {} pending economy plugin integration", value);
            }
            case "command" -> {
                if (value instanceof String command) {
                    if (command.startsWith("/")) command = command.substring(1);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                        command.replace("%player%", player.getName()));
                }
            }
        }
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public boolean canHandle(String nodeType) {
        return "completion".equals(nodeType);
    }
}
```

- [ ] **Step 4: Run build**

Run: `cd PlayerTaskX && ./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/StartNodeHandler.java
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/TaskNodeHandler.java
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/CompletionNodeHandler.java
git commit -m "feat(handlers): add core node handlers (Start, Task, Completion)"
```

---

### Task 9: Branching Handlers (Condition, Branch)

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/handler/ConditionNodeHandler.java`
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/handler/BranchNodeHandler.java`

- [ ] **Step 1: Create ConditionNodeHandler.java**

```java
package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class ConditionNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "condition"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.wait();
        
        Map<String, Object> data = currentNode.getData();
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) data.get("conditions");
        
        boolean allMet = true;
        if (conditions != null) {
            for (Map<String, Object> cond : conditions) {
                String conditionType = (String) cond.get("conditionType");
                Map<String, Object> params = (Map<String, Object>) cond.get("params");
                if (!evaluateCondition(conditionType, params, session)) {
                    allMet = false;
                    break;
                }
            }
        }
        
        session.markNodeCompleted(session.getCurrentNodeId());
        
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(session.getCurrentNodeId()))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        // Route to first branch based on condition result
        String targetNodeId = allMet ? nextNodes.get(0) : (nextNodes.size() > 1 ? nextNodes.get(1) : nextNodes.get(0));
        return NextNodeResult.next(targetNodeId, Map.of("conditionResult", allMet));
    }
    
    private boolean evaluateCondition(String type, Map<String, Object> params, QuestSession session) {
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return false;
        
        switch (type) {
            case "PERMISSION" -> {
                String permission = (String) params.get("permission");
                return permission != null && player.hasPermission(permission);
            }
            case "HAS_ITEM" -> {
                String itemId = (String) params.get("itemId");
                int count = ((Number) params.getOrDefault("count", 1)).intValue();
                org.bukkit.Material material = org.bukkit.Material.valueOf(
                    params.getOrDefault("material", "DIAMOND").toString().toUpperCase());
                return player.getInventory().containsAtLeast(new org.bukkit.inventory.ItemStack(material), count);
            }
            case "KILL_MOB" -> {
                String mobType = (String) params.get("mobType");
                int killCount = ((Number) session.getContext().getOrDefault("kills_" + mobType, 0)).intValue();
                int required = ((Number) params.getOrDefault("count", 1)).intValue();
                return killCount >= required;
            }
            case "COLLECT_ITEM" -> {
                String itemId = (String) params.get("itemId");
                int collected = ((Number) session.getContext().getOrDefault("collected_" + itemId, 0)).intValue();
                int required = ((Number) params.getOrDefault("count", 1)).intValue();
                return collected >= required;
            }
            case "PLAYER_LEVEL" -> {
                int level = ((Number) params.getOrDefault("level", 1)).intValue();
                String operator = (String) params.getOrDefault("operator", "GTE");
                return switch (operator) {
                    case "EQ" -> player.getLevel() == level;
                    case "GT" -> player.getLevel() > level;
                    case "GTE" -> player.getLevel() >= level;
                    case "LT" -> player.getLevel() < level;
                    case "LTE" -> player.getLevel() <= level;
                    default -> false;
                };
            }
            case "TIME_RANGE" -> {
                int currentHour = java.time.LocalTime.now().getHour();
                int startHour = ((Number) params.getOrDefault("startHour", 0)).intValue();
                int endHour = ((Number) params.getOrDefault("endHour", 24)).intValue();
                if (startHour <= endHour) {
                    return currentHour >= startHour && currentHour < endHour;
                } else {
                    return currentHour >= startHour || currentHour < endHour;
                }
            }
            case "IN_REGION" -> {
                String region = (String) params.get("region");
                return Boolean.TRUE.equals(session.getContext().get("in_region_" + region));
            }
            default -> {
                org.slf4j.LoggerFactory.getLogger(ConditionNodeHandler.class).warn("Unknown condition type: {}", type);
                return false;
            }
        }
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public boolean canHandle(String nodeType) {
        return "condition".equals(nodeType);
    }
}
```

- [ ] **Step 2: Create BranchNodeHandler.java**

```java
package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class BranchNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "branch"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        session.markNodeCompleted(session.getCurrentNodeId());
        
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(session.getCurrentNodeId()))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        return NextNodeResult.next(nextNodes.get(0));
    }
    
    @Override
    public boolean canHandle(String nodeType) {
        return "branch".equals(nodeType);
    }
}
```

- [ ] **Step 3: Run build**

Run: `cd PlayerTaskX && ./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/ConditionNodeHandler.java
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/BranchNodeHandler.java
git commit -m "feat(handlers): add branching handlers (Condition, Branch)"
```

---

### Task 10: Additional Handlers (Action, Event, Counter, Timer, State, Subtask)

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/handler/ActionNodeHandler.java`
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/handler/EventNodeHandler.java`
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/handler/CounterNodeHandler.java`
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/handler/TimerNodeHandler.java`
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/handler/StateNodeHandler.java`
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/handler/SubtaskNodeHandler.java`

- [ ] **Step 1: Create ActionNodeHandler.java**

```java
package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class ActionNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "action"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.wait();
        
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return NextNodeResult.wait();
        
        Map<String, Object> data = currentNode.getData();
        String actionType = (String) data.get("actionType");
        Map<String, Object> actionParams = (Map<String, Object>) data.get("actionParams");
        
        executeAction(player, actionType, actionParams);
        
        session.markNodeCompleted(session.getCurrentNodeId());
        
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(session.getCurrentNodeId()))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        return NextNodeResult.next(nextNodes.get(0));
    }
    
    private void executeAction(Player player, String actionType, Map<String, Object> params) {
        switch (actionType) {
            case "GIVE_ITEM" -> {
                String materialName = (String) params.getOrDefault("material", "DIAMOND");
                int amount = ((Number) params.getOrDefault("amount", 1)).intValue();
                org.bukkit.Material material = org.bukkit.Material.valueOf(materialName.toUpperCase());
                org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material, amount);
                player.getInventory().addItem(item);
            }
            case "TAKE_ITEM" -> {
                String materialName = (String) params.getOrDefault("material", "DIAMOND");
                int amount = ((Number) params.getOrDefault("amount", 1)).intValue();
                org.bukkit.Material material = org.bukkit.Material.valueOf(materialName.toUpperCase());
                org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material, amount);
                player.getInventory().removeItem(item);
            }
            case "GIVE_MONEY" -> {
                // Requires economy plugin integration (Vault or similar)
                double amount = ((Number) params.getOrDefault("amount", 0)).doubleValue();
                // Example: EconomyResponse response = economy.depositPlayer(player, amount);
            }
            case "TAKE_MONEY" -> {
                double amount = ((Number) params.getOrDefault("amount", 0)).doubleValue();
                // Example: EconomyResponse response = economy.withdrawPlayer(player, amount);
            }
            case "GIVE_XP" -> {
                int amount = ((Number) params.getOrDefault("amount", 0)).intValue();
                player.giveExp(amount);
            }
            case "SEND_MESSAGE" -> {
                String message = (String) params.getOrDefault("message", "");
                player.sendMessage(message);
            }
            case "BROADCAST" -> {
                String message = (String) params.getOrDefault("message", "");
                Bukkit.broadcastMessage(message);
            }
            case "EXECUTE_COMMAND" -> {
                String command = (String) params.getOrDefault("command", "");
                // Remove leading slash if present
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
            }
            case "PLAY_SOUND" -> {
                String soundName = (String) params.getOrDefault("sound", "ENTITY_PLAYER_LEVELUP");
                float volume = ((Number) params.getOrDefault("volume", 1.0f)).floatValue();
                float pitch = ((Number) params.getOrDefault("pitch", 1.0f)).floatValue();
                player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundName.toUpperCase()), volume, pitch);
            }
            default -> {
                org.slf4j.LoggerFactory.getLogger(ActionNodeHandler.class).warn("Unknown action type: {}", actionType);
            }
        }
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public boolean canHandle(String nodeType) {
        return "action".equals(nodeType);
    }
}
```

- [ ] **Step 2: Create remaining handlers (Event, Counter, Timer, State, Subtask)**

```java
// EventNodeHandler.java - listens for events but doesn't auto-transition
public class EventNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "event"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        // Event node registers interest but waits for next event to trigger transition
        // This is used to capture specific event types before advancing
        return NextNodeResult.wait();
    }
    
    @Override
    public boolean canHandle(String nodeType) { return "event".equals(nodeType); }
}

// CounterNodeHandler.java - increments/decrements counter
public class CounterNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "counter"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.wait();
        
        Map<String, Object> data = currentNode.getData();
        String counterName = (String) data.getOrDefault("name", "default");
        int delta = event != null ? 1 : 0; // Increment on event
        
        int currentCount = ((Number) session.getContext().getOrDefault("counter_" + counterName, 0)).intValue();
        int newCount = currentCount + delta;
        
        session.updateContext(Map.of("counter_" + counterName, newCount));
        
        // Check if threshold reached
        int threshold = ((Number) data.getOrDefault("threshold", Integer.MAX_VALUE)).intValue();
        if (newCount >= threshold) {
            session.markNodeCompleted(session.getCurrentNodeId());
            return getNextNode(graph, session.getCurrentNodeId());
        }
        
        return NextNodeResult.wait();
    }
    
    private NextNodeResult getNextNode(QuestGraph graph, String currentNodeId) {
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(currentNodeId))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(currentNodeId);
        }
        return NextNodeResult.next(nextNodes.get(0));
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public boolean canHandle(String nodeType) { return "counter".equals(nodeType); }
}

// TimerNodeHandler.java - handles delays/cooldowns
public class TimerNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "timer"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.wait();
        
        Map<String, Object> data = currentNode.getData();
        String timerType = (String) data.getOrDefault("timerType", "DELAY");
        
        switch (timerType) {
            case "DELAY" -> {
                long delaySeconds = ((Number) data.getOrDefault("delaySeconds", 60)).longValue();
                String timerKey = "timer_start_" + session.getCurrentNodeId();
                long startTime = ((Number) session.getContext().getOrDefault(timerKey, 0)).longValue();
                if (startTime == 0) {
                    session.updateContext(Map.of(timerKey, System.currentTimeMillis()));
                } else if (System.currentTimeMillis() - startTime >= delaySeconds * 1000) {
                    session.markNodeCompleted(session.getCurrentNodeId());
                    return getNextNode(graph, session.getCurrentNodeId());
                }
            }
            case "COOLDOWN" -> {
                long cooldownSeconds = ((Number) data.getOrDefault("cooldownSeconds", 300)).longValue();
                String cooldownKey = "cooldown_end_" + session.getCurrentNodeId();
                Long cooldownEnd = ((Number) session.getContext().getOrDefault(cooldownKey, 0)).longValue();
                long now = System.currentTimeMillis();
                if (cooldownEnd == 0) {
                    session.updateContext(Map.of(cooldownKey, now + cooldownSeconds * 1000));
                } else if (now >= cooldownEnd) {
                    session.updateContext(Map.of(cooldownKey, now + cooldownSeconds * 1000));
                    session.markNodeCompleted(session.getCurrentNodeId());
                    return getNextNode(graph, session.getCurrentNodeId());
                }
            }
            case "INTERVAL" -> {
                long intervalSeconds = ((Number) data.getOrDefault("intervalSeconds", 60)).longValue();
                int maxReps = ((Number) data.getOrDefault("repeatCount", 1)).intValue();
                String intervalKey = "interval_" + session.getCurrentNodeId();
                Map<String, Object> intervalData = (Map<String, Object>) session.getContext().get(intervalKey);
                long lastFire = intervalData != null ? ((Number) intervalData.getOrDefault("lastFire", 0)).longValue() : 0;
                int reps = intervalData != null ? ((Number) intervalData.getOrDefault("reps", 0)).intValue() : 0;
                long now = System.currentTimeMillis();
                if (lastFire == 0 || now - lastFire >= intervalSeconds * 1000) {
                    reps++;
                    session.updateContext(Map.of(intervalKey, Map.of("lastFire", now, "reps", reps)));
                    if (reps >= maxReps) {
                        session.markNodeCompleted(session.getCurrentNodeId());
                        return getNextNode(graph, session.getCurrentNodeId());
                    }
                }
            }
        }
        
        return NextNodeResult.wait();
    }
    
    private NextNodeResult getNextNode(QuestGraph graph, String currentNodeId) {
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(currentNodeId))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(currentNodeId);
        }
        return NextNodeResult.next(nextNodes.get(0));
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public boolean canHandle(String nodeType) { return "timer".equals(nodeType); }
}

// StateNodeHandler.java - modifies player state
public class StateNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "state"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.wait();
        
        Map<String, Object> data = currentNode.getData();
        String operation = (String) data.getOrDefault("operation", "SET_PLAYER_STATE");
        String stateKey = (String) data.getOrDefault("name", "default");
        Object stateValue = data.getOrDefault("value", true);
        
        session.updateContext(Map.of("state_" + stateKey, stateValue));
        session.markNodeCompleted(session.getCurrentNodeId());
        
        return getNextNode(graph, session.getCurrentNodeId());
    }
    
    private NextNodeResult getNextNode(QuestGraph graph, String currentNodeId) {
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(currentNodeId))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(currentNodeId);
        }
        return NextNodeResult.next(nextNodes.get(0));
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public boolean canHandle(String nodeType) { return "state".equals(nodeType); }
}

// SubtaskNodeHandler.java - handles nested quests
package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.engine.QuestSessionManager;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SubtaskNodeHandler implements NodeHandler {
    private final QuestSessionManager sessionManager;
    
    public SubtaskNodeHandler(com.playerPlugin.playerTaskX.engine.QuestSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
    
    @Override
    public String getNodeType() { return "subtask"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.wait();
        
        Map<String, Object> data = currentNode.getData();
        String subtaskId = (String) data.getOrDefault("subtaskId", "");
        String subSessionKey = "subtask_session_" + subtaskId;
        
        // Check if we already started this subtask
        String subSessionId = (String) session.getContext().get(subSessionKey);
        
        if (subSessionId == null) {
            // First time here - create sub-session
            QuestSession subSession = new QuestSession(session.getPlayerId(), subtaskId, "start");
            sessionManager.createSession(subSession);
            session.updateContext(Map.of(subSessionKey, subtaskId));
            return NextNodeResult.wait();
        }
        
        // Check if sub-session is complete
        java.util.Optional<QuestSession> subSessionOpt = sessionManager.getSession(session.getPlayerId(), subtaskId);
        if (subSessionOpt.isEmpty()) {
            session.markNodeCompleted(session.getCurrentNodeId());
            return getNextNode(graph, session.getCurrentNodeId());
        }
        
        QuestSession subSession = subSessionOpt.get();
        if (subSession.getStatus() == PTXTaskStatus.COMPLETED || subSession.getStatus() == PTXTaskStatus.CLAIMED) {
            session.markNodeCompleted(session.getCurrentNodeId());
            return getNextNode(graph, session.getCurrentNodeId());
        }
        
        return NextNodeResult.wait();
    }
    
    private NextNodeResult getNextNode(QuestGraph graph, String currentNodeId) {
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(currentNodeId))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(currentNodeId);
        }
        return NextNodeResult.next(nextNodes.get(0));
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public boolean canHandle(String nodeType) { return "subtask".equals(nodeType); }
}
```

- [ ] **Step 3: Run build**

Run: `cd PlayerTaskX && ./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/ActionNodeHandler.java
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/EventNodeHandler.java
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/CounterNodeHandler.java
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/TimerNodeHandler.java
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/StateNodeHandler.java
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/SubtaskNodeHandler.java
git commit -m "feat(handlers): add additional node handlers"
```

---

## Phase 4: Integration

### Task 11: Create GraphEventListener

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/listener/GraphEventListener.java`

- [ ] **Step 1: Create GraphEventListener.java**

```java
package com.playerPlugin.playerTaskX.listener;

import com.playerPlugin.playerTaskX.engine.QuestEngine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class GraphEventListener implements Listener {
    private final QuestEngine questEngine;
    
    public GraphEventListener(QuestEngine questEngine) {
        this.questEngine = questEngine;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            Bukkit.getScheduler().runTaskLater(null, () -> {
                questEngine.handleEvent(killer, event);
            }, 1L);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(null, () -> {
            questEngine.handleEvent(player, event);
        }, 1L);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(null, () -> {
            questEngine.handleEvent(player, event);
        }, 1L);
    }
    
    // Additional event handlers as needed
}
```

- [ ] **Step 2: Run build**

Run: `cd PlayerTaskX && ./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/listener/GraphEventListener.java
git commit -m "feat(listener): add GraphEventListener for quest graph events"
```

---

### Task 12: Initialize QuestEngine and wire dependencies

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/PlayerTaskX.java`

- [ ] **Step 1: Create PlayerTaskX initialization**

```java
public class PlayerTaskX extends JavaPlugin {
    private TaskManager taskManager;
    private QuestEngine questEngine;
    private SessionStorage sessionStorage;
    private QuestSessionManager sessionManager;
    
    @Override
    public void onEnable() {
        // Initialize session storage based on config
        sessionStorage = createSessionStorage();
        
        // Initialize session manager
        sessionManager = new QuestSessionManager();
        
        // Initialize handler registry and register all handlers
        NodeHandlerRegistry handlerRegistry = new NodeHandlerRegistry();
        registerHandlers(handlerRegistry);
        
        // Initialize task manager first (without questEngine to break circular dependency)
        TaskStorage taskStorage = createTaskStorage();
        taskManager = new TaskManager(taskStorage, progressStorage);
        
        // Initialize quest engine with dependencies
        questEngine = new QuestEngine(sessionManager, handlerRegistry, taskManager, sessionStorage);
        
        // Note: taskManager.setQuestEngine(questEngine) called in Task 13 after TaskManager is modified
        
        // Restore any active sessions from database
        questEngine.restoreSessions();
        
        // Register event listener
        getServer().getPluginManager().registerEvents(
            new GraphEventListener(questEngine), this);
    }
    
    private void registerHandlers(NodeHandlerRegistry registry) {
        registry.register(new StartNodeHandler());
        registry.register(new TaskNodeHandler());
        registry.register(new CompletionNodeHandler());
        registry.register(new ConditionNodeHandler());
        registry.register(new BranchNodeHandler());
        registry.register(new ActionNodeHandler());
        registry.register(new EventNodeHandler());
        registry.register(new CounterNodeHandler());
        registry.register(new TimerNodeHandler());
        registry.register(new StateNodeHandler());
        registry.register(new SubtaskNodeHandler(sessionManager));  // Pass sessionManager
    }
}
```

- [ ] **Step 2: Run build**

Run: `cd PlayerTaskX && ./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/PlayerTaskX.java
git commit -m "feat(engine): initialize QuestEngine and wire dependencies"
```

---

### Task 13: Refactor TaskManager to use QuestEngine

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/manager/TaskManager.java`

- [ ] **Step 1: Refactor TaskManager to accept and use QuestEngine**

```java
public class TaskManager {
    private final TaskStorage taskStorage;
    private final ProgressStorage progressStorage;
    private QuestEngine questEngine;  // Nullable, set via setter
    
    public TaskManager(TaskStorage taskStorage, ProgressStorage progressStorage) {
        this.taskStorage = taskStorage;
        this.progressStorage = progressStorage;
    }
    
    public void setQuestEngine(QuestEngine questEngine) {
        this.questEngine = questEngine;
    }
    
    public void handleEvent(Player player, Event event) {
        // Delegate to quest engine for graph-based tasks
        if (questEngine != null) {
            questEngine.handleEvent(player, event);
        }
        
        // Legacy handling for non-graph tasks (tasks without QuestGraph)
        Map<String, TaskProgress> progressMap = playerProgressCache.get(player.getUniqueId());
        if (progressMap == null) return;
        
        for (TaskProgress progress : progressMap.values()) {
            if (progress.getStatus() != PTXTaskStatus.IN_PROGRESS) continue;
            
            TaskDefinition task = taskCache.get(progress.getTaskId());
            if (task == null || task.hasGraph()) continue;  // Skip graph-based tasks
            
            // Legacy objective matching
            for (Objective objective : task.getObjectives()) {
                if (objective.matchesEvent(event)) {
                    objective.applyProgress(player, 1);
                    int currentProgress = progress.getProgress(objective.getId());
                    progress.setProgress(objective.getId(), currentProgress + 1);
                    
                    if (objective.isCompleted(player)) {
                        boolean allCompleted = task.getObjectives().stream()
                            .allMatch(obj -> obj.isCompleted(player));
                        if (allCompleted) {
                            progress.setStatus(PTXTaskStatus.COMPLETED);
                            progress.setCompletedAt(System.currentTimeMillis());
                        }
                    }
                    asyncSaveProgress(player.getUniqueId(), progress);
                }
            }
        }
    }
    
    public boolean acceptTask(Player player, String taskId) {
        TaskDefinition task = taskCache.get(taskId);
        if (task == null) return false;
        if (task.getConditions().stream().anyMatch(c -> !c.isMet(player))) {
            return false;
        }
        
        TaskProgress progress = new TaskProgress(player.getUniqueId(), taskId);
        progressStorage.save(player.getUniqueId(), progress);
        playerProgressCache.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
            .put(taskId, progress);
        
        // For graph-based tasks, create a quest session
        if (questEngine != null && task.hasGraph()) {
            Player actualPlayer = Bukkit.getPlayer(player.getUniqueId());
            if (actualPlayer != null) {
                questEngine.startQuest(actualPlayer, task);
            }
        }
        
        return true;
    }
}
```

- [ ] **Step 2: Update PlayerTaskX to use setter injection**

```java
// In PlayerTaskX.onEnable():
questEngine = new QuestEngine(sessionManager, handlerRegistry, taskManager, sessionStorage);
taskManager.setQuestEngine(questEngine);
```

- [ ] **Step 3: Run build**

Run: `cd PlayerTaskX && ./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/manager/TaskManager.java
git commit -m "refactor(engine): integrate QuestEngine into TaskManager"
```

---

### Task 14: Disable old EntityListener

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/PlayerTaskX.java`

- [ ] **Step 1: Comment out old EntityListener registration**

In `onEnable()`, comment out the old EntityListener since GraphEventListener replaces it:

```java
// Register event listener
getServer().getPluginManager().registerEvents(
    new GraphEventListener(questEngine), this);

// OLD listener - now handled by GraphEventListener:
// getServer().getPluginManager().registerEvents(new EntityListener(taskManager), this);
```

- [ ] **Step 2: Run build**

Run: `cd PlayerTaskX && ./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/PlayerTaskX.java
git commit -m "chore: disable old EntityListener in favor of GraphEventListener"
```

---

### Task 15: Refactor EditorServer (remove GraphManager)

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/web/EditorServer.java`

- [ ] **Step 1: Remove GraphManager and GraphStorageController references**

```java
public class EditorServer {
    private final TaskEditorController taskController;
    private final RewardTemplateController rewardController;
    private final PlayerProgressController progressController;
    private final StatsController statsController;
    // Remove: private final GraphStorageController graphController;
    private Javalin javalin;
    
    public EditorServer(TaskManager taskManager, Path dataFolder) {
        this.taskController = new TaskEditorController(taskManager);
        this.rewardController = new RewardTemplateController(taskManager, dataFolder);
        this.progressController = new PlayerProgressController(taskManager);
        this.statsController = new StatsController(taskManager);
        // Remove GraphManager and GraphStorageController
    }
    
    public void start(int port) {
        this.javalin = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.directory = "/web";
            });
        })
            // ... existing task/reward/progress/stats endpoints
            // Remove: .get("/api/graphs", ...)
            // Remove: .get("/api/graphs/{id}", ...)
            // Remove: .put("/api/graphs/{id}", ...)
            // Remove: .delete("/api/graphs/{id}", ...)
            .start("127.0.0.1", port);
    }
}
```

- [ ] **Step 2: Run build**

Run: `cd PlayerTaskX && ./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/web/EditorServer.java
git commit -m "refactor(api): remove GraphManager and graph storage endpoints"
```

---

## Phase 5: Testing & Cleanup

### Task 15: Database migration script

**Files:**
- Create: `core/src/main/resources/migrations/add_quest_sessions.sql`

**Note:** Design decision to use separate `quest_sessions` table instead of extending `quest_progress`. Rationale: keeps concerns separate (progress tracking vs graph traversal state), avoids schema bloating on the progress table.

- [ ] **Step 1: Create migration script**

```sql
-- Migration: Add quest_sessions table for graph execution
-- Separate table keeps graph traversal state distinct from progress tracking

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/resources/migrations/add_quest_sessions.sql
git commit -m "db: add quest_sessions table migration"
```

---

### Task 16: Unit tests for core engine

**Files:**
- Create: `core/src/test/java/com/playerPlugin/playerTaskX/engine/QuestEngineTest.java`
- Create: `core/src/test/java/com/playerPlugin/playerTaskX/engine/QuestSessionManagerTest.java`

- [ ] **Step 1: Create QuestSessionTest.java**

```java
package com.playerPlugin.playerTaskX.engine;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QuestSessionTest {
    @Test
    void testSessionCreation() {
        UUID playerId = UUID.randomUUID();
        QuestSession session = new QuestSession(playerId, "quest1", "start1");
        
        assertEquals(playerId, session.getPlayerId());
        assertEquals("quest1", session.getQuestId());
        assertEquals("start1", session.getCurrentNodeId());
        assertEquals(PTXTaskStatus.IN_PROGRESS, session.getStatus());
        assertTrue(session.getCompletedNodes().isEmpty());
    }
    
    @Test
    void testMarkNodeCompleted() {
        QuestSession session = new QuestSession(UUID.randomUUID(), "quest1", "start1");
        session.markNodeCompleted("start1");
        
        assertTrue(session.getCompletedNodes().contains("start1"));
    }
    
    @Test
    void testContextUpdate() {
        QuestSession session = new QuestSession(UUID.randomUUID(), "quest1", "start1");
        session.updateContext(java.util.Map.of("counter", 5));
        
        assertEquals(5, session.getContext().get("counter"));
    }
}
```

- [ ] **Step 2: Create QuestSessionManagerTest.java**

```java
package com.playerPlugin.playerTaskX.engine;

import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QuestSessionManagerTest {
    @Test
    void testCreateAndGetSession() {
        QuestSessionManager manager = new QuestSessionManager();
        UUID playerId = UUID.randomUUID();
        QuestSession session = new QuestSession(playerId, "quest1", "start1");
        
        manager.createSession(session);
        
        assertTrue(manager.getSession(playerId, "quest1").isPresent());
        assertEquals(session, manager.getSession(playerId, "quest1").get());
    }
    
    @Test
    void testRemoveSession() {
        QuestSessionManager manager = new QuestSessionManager();
        UUID playerId = UUID.randomUUID();
        QuestSession session = new QuestSession(playerId, "quest1", "start1");
        
        manager.createSession(session);
        manager.removeSession(playerId, "quest1");
        
        assertFalse(manager.getSession(playerId, "quest1").isPresent());
    }
}
```

- [ ] **Step 3: Create QuestEngineTest.java (basic flow test)**

```java
package com.playerPlugin.playerTaskX.engine;

import com.playerPlugin.playerTaskX.api.handler.NodeHandlerRegistry;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.service.SessionStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuestEngineTest {
    @Mock private TaskManager taskManager;
    @Mock private SessionStorage sessionStorage;
    private NodeHandlerRegistry registry;
    private QuestSessionManager sessionManager;
    private QuestEngine engine;
    
    @BeforeEach
    void setUp() {
        registry = new NodeHandlerRegistry();
        sessionManager = new QuestSessionManager();
        engine = new QuestEngine(sessionManager, registry, taskManager, sessionStorage);
    }
    
    @Test
    void testStartQuestWithGraph() {
        TaskDefinition task = createTestTask();
        // Verify engine starts quest and creates session
    }
    
    private TaskDefinition createTestTask() {
        List<GraphNode> nodes = List.of(
            new GraphNode("start1", "start", 0, 0, Map.of()),
            new GraphNode("task1", "task", 100, 0, Map.of())
        );
        List<NodeConnection> edges = List.of(
            new NodeConnection("e1", "start1", "task1", null)
        );
        QuestGraph graph = new QuestGraph("q1", "Test", nodes, edges);
        
        return TaskDefinition.builder()
            .id("q1")
            .name("Test Quest")
            .graph(graph)
            .build();
    }
}
```

- [ ] **Step 4: Run tests**

Run: `cd PlayerTaskX && ./gradlew :core:test`
Expected: Tests pass

- [ ] **Step 5: Commit**

```bash
git add core/src/test/java/com/playerPlugin/playerTaskX/engine/
git commit -m "test(engine): add unit tests for QuestEngine"
```

---

### Task 17: Integration test (end-to-end flow)

**Files:**
- Create: `core/src/test/java/com/playerPlugin/playerTaskX/integration/GraphExecutionIntegrationTest.java`

- [ ] **Step 1: Create integration test**

```java
package com.playerPlugin.playerTaskX.integration;

import com.playerPlugin.playerTaskX.api.handler.NodeHandlerRegistry;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.NodeConnection;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.service.ProgressStorage;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;
import com.playerPlugin.playerTaskX.engine.QuestEngine;
import com.playerPlugin.playerTaskX.engine.QuestSessionManager;
import com.playerPlugin.playerTaskX.handler.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphExecutionIntegrationTest {
    @Mock private Player player;
    @Mock private EntityDeathEvent event;
    @Mock private TaskManager taskManager;
    @Mock private SessionStorage sessionStorage;
    @Mock private TaskStorage taskStorage;
    @Mock private ProgressStorage progressStorage;
    
    private QuestEngine engine;
    private QuestSessionManager sessionManager;
    private NodeHandlerRegistry registry;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        sessionManager = new QuestSessionManager();
        registry = new NodeHandlerRegistry();
        
        // Register all handlers
        registry.register(new StartNodeHandler());
        registry.register(new TaskNodeHandler());
        registry.register(new CompletionNodeHandler());
        registry.register(new ConditionNodeHandler());
        registry.register(new BranchNodeHandler());
        registry.register(new ActionNodeHandler());
        
        // Create engine with mocked dependencies
        engine = new QuestEngine(sessionManager, registry, taskManager, sessionStorage);
        
        // Setup player mock
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }
    
    @Test
    void testStartToCompletionFlow() {
        // Create simple graph: Start -> Task -> Completion
        QuestGraph graph = createTestGraph("quest1", "start1", "task1", "completion1");
        TaskDefinition task = mock(TaskDefinition.class);
        when(task.getId()).thenReturn("quest1");
        when(task.getGraph()).thenReturn(graph);
        when(taskManager.getTask("quest1")).thenReturn(Optional.of(task));
        
        // Start quest
        engine.startQuest(player, task);
        
        // Verify session created
        assertTrue(sessionManager.getSession(player.getUniqueId(), "quest1").isPresent());
        
        // Simulate event to progress
        when(event.getEntity()).thenReturn(mock(org.bukkit.entity.LivingEntity.class));
        when(event.getEntity().getKiller()).thenReturn(player);
        engine.handleEvent(player, event);
        
        // Verify session updated
        // (Actual assertions depend on graph structure)
        assertNotNull(sessionManager.getSession(player.getUniqueId(), "quest1").get().getCurrentNodeId());
    }
    
    private QuestGraph createTestGraph(String questId, String startId, String taskId, String completionId) {
        List<GraphNode> nodes = List.of(
            new GraphNode(startId, "start", 0, 0, Map.of()),
            new GraphNode(taskId, "task", 100, 0, Map.of("objectives", List.of())),
            new GraphNode(completionId, "completion", 200, 0, Map.of("rewards", List.of()))
        );
        List<NodeConnection> edges = List.of(
            new NodeConnection("e1", startId, taskId, null),
            new NodeConnection("e2", taskId, completionId, null)
        );
        return new QuestGraph(questId, "Test Quest", nodes, edges);
    }
}
```

- [ ] **Step 2: Run integration tests**

Run: `cd PlayerTaskX && ./gradlew :core:test --tests "*IntegrationTest"`
Expected: Tests pass

- [ ] **Step 3: Commit**

```bash
git add core/src/test/java/com/playerPlugin/playerTaskX/integration/
git commit -m "test: add integration tests for graph execution"
```

---

### Task 18: Verify full build

- [ ] **Step 1: Run full build**

Run: `cd PlayerTaskX && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Disable old EntityListener**

Modify `core/src/main/java/com/playerPlugin/playerTaskX/PlayerTaskX.java`:
```java
// In onEnable(), after registering GraphEventListener:
// Disable old EntityListener since GraphEventListener replaces it
// getServer().getPluginManager().registerEvents(new EntityListener(taskManager), this);
```

- [ ] **Step 3: Manual verification**

Manual test checklist:
1. Start server with new build
2. Create a quest via web editor with graph: Start → Task → Completion
3. Player accepts quest, verify session created in database
4. Trigger events (kill mob, break block), verify quest progresses
5. Complete quest, verify rewards granted
6. Test condition branching: create quest with Condition node, verify routing
7. Test timer: create quest with Timer node, verify delayed transition
8. Server restart: verify sessions restored correctly

- [ ] **Step 4: Commit**

```bash
git commit -m "chore: verify full build passes and manual testing complete"
```

---

## Summary

**Total Tasks: 18**

| Phase | Tasks |
|-------|-------|
| Infrastructure | 3 |
| Core Engine | 4 |
| Node Handlers | 3 |
| Integration | 4 |
| Testing & Cleanup | 4 |

**Estimated Time:** 4-6 implementation cycles (based on spec complexity)
