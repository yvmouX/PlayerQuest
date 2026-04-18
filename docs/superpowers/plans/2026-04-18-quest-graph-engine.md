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
    
    NextNodeResult execute(QuestSession session, Player player, Event event, QuestGraph graph);
    
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

- [ ] **Step 2: Implement for MySQL (create SessionStorageImpl in mysql package)**

Modify `core/src/main/java/com/playerPlugin/playerTaskX/storage/mysql/MySQLSessionStorage.java`:

```java
package com.playerPlugin.playerTaskX.storage.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
import com.playerPlugin.playerTaskX.storage.SessionStorage;
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
        // Simplified - full implementation similar to findByPlayer
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
    
    private QuestSession mapRow(ResultSet rs) throws SQLException {
        // Simplified - full implementation needed
        UUID playerId = UUID.fromString(rs.getString("player_uuid"));
        String questId = rs.getString("quest_id");
        String currentNodeId = rs.getString("current_node_id");
        QuestSession session = new QuestSession(playerId, questId, currentNodeId);
        // ... restore other fields from JSON columns
        return session;
    }
}
```

- [ ] **Step 3: Run build**

Run: `cd PlayerTaskX && ./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL (may have warnings about unchecked)

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/storage/SessionStorage.java
git add core/src/main/java/com/playerPlugin/playerTaskX/storage/mysql/MySQLSessionStorage.java
git commit -m "feat(storage): add SessionStorage interface and MySQL implementation"
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
import com.playerPlugin.playerTaskX.storage.SessionStorage;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.*;

public class QuestEngine {
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
            
            NextNodeResult result = handler.execute(session, player, event, graph);
            
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
        
        // Grant rewards
        for (var reward : task.getRewards()) {
            reward.grant(/* player */);
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

- [ ] **Step 1: Create StartNodeHandler.java**

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
import java.util.Objects;

public class StartNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "start"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Player player, Event event, QuestGraph graph) {
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
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class TaskNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "task"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Player player, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.wait();
        
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
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class CompletionNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "completion"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Player player, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.terminal(session.getCurrentNodeId());
        
        Map<String, Object> data = currentNode.getData();
        if (data == null) {
            session.markNodeCompleted(session.getCurrentNodeId());
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        List<Map<String, Object>> rewardsData = (List<Map<String, Object>>) data.get("rewards");
        if (rewardsData != null) {
            for (Map<String, Object> rewardData : rewardsData) {
                // Grant reward to player
                // rewardData contains type, value, etc.
            }
        }
        
        session.markNodeCompleted(session.getCurrentNodeId());
        return NextNodeResult.terminal(session.getCurrentNodeId());
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
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class ConditionNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "condition"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Player player, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.wait();
        
        Map<String, Object> data = currentNode.getData();
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) data.get("conditions");
        
        boolean allMet = true;
        if (conditions != null) {
            for (Map<String, Object> cond : conditions) {
                String conditionType = (String) cond.get("conditionType");
                Map<String, Object> params = (Map<String, Object>) cond.get("params");
                if (!evaluateCondition(player, conditionType, params)) {
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
    
    private boolean evaluateCondition(Player player, String type, Map<String, Object> params) {
        // Simplified - actual implementation would check player state
        return true;
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
    public NextNodeResult execute(QuestSession session, Player player, Event event, QuestGraph graph) {
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
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;

public class ActionNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "action"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Player player, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.wait();
        
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
        // Simplified - actual implementation based on actionType:
        // GIVE_ITEM, TAKE_ITEM, GIVE_MONEY, TAKE_MONEY, GIVE_XP, 
        // SEND_MESSAGE, BROADCAST, EXECUTE_COMMAND, PLAY_SOUND
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
    public NextNodeResult execute(QuestSession session, Player player, Event event, QuestGraph graph) {
        // Event node registers interest but waits for next event to trigger transition
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
    public NextNodeResult execute(QuestSession session, Player player, Event event, QuestGraph graph) {
        Map<String, Object> ctx = session.getContext();
        int counter = ((Number) ctx.getOrDefault("counter", 0)).intValue();
        session.updateContext(Map.of("counter", counter + 1));
        return NextNodeResult.next(/* next node based on threshold */);
    }
    
    @Override
    public boolean canHandle(String nodeType) { return "counter".equals(nodeType); }
}

// TimerNodeHandler.java - handles delays/cooldowns
public class TimerNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "timer"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Player player, Event event, QuestGraph graph) {
        // Schedules transition after delay
        return NextNodeResult.wait();
    }
    
    @Override
    public boolean canHandle(String nodeType) { return "timer".equals(nodeType); }
}

// StateNodeHandler.java - modifies player state
public class StateNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "state"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Player player, Event event, QuestGraph graph) {
        // Updates session context with player state
        return NextNodeResult.next(/* next node */);
    }
    
    @Override
    public boolean canHandle(String nodeType) { return "state".equals(nodeType); }
}

// SubtaskNodeHandler.java - handles nested quests
public class SubtaskNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "subtask"; }
    
    @Override
    public NextNodeResult execute(QuestSession session, Player player, Event event, QuestGraph graph) {
        // Delegates to sub-quest engine
        return NextNodeResult.next(/* next node */);
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

### Task 11: Integrate with TaskManager

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/manager/TaskManager.java`

- [ ] **Step 1: Modify TaskManager to delegate to QuestEngine**

Add QuestEngine field and modify handleEvent:

```java
public class TaskManager {
    private final TaskStorage taskStorage;
    private final ProgressStorage progressStorage;
    private final QuestEngine questEngine;  // NEW
    
    public TaskManager(TaskStorage taskStorage, ProgressStorage progressStorage, QuestEngine questEngine) {
        this.taskStorage = taskStorage;
        this.progressStorage = progressStorage;
        this.questEngine = questEngine;  // NEW
    }
    
    public void handleEvent(Player player, Event event) {
        // Delegate to quest engine for graph-based tasks
        questEngine.handleEvent(player, event);
        // Legacy handling for non-graph tasks continues below...
    }
}
```

- [ ] **Step 2: Run build**

Run: `cd PlayerTaskX && ./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/manager/TaskManager.java
git commit -m "refactor(engine): integrate QuestEngine into TaskManager"
```

---

### Task 12: Refactor EditorServer (remove GraphManager)

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

### Task 13: Create GraphEventListener

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/listener/GraphEventListener.java`

- [ ] **Step 1: Create GraphEventListener.java**

```java
package com.playerPlugin.playerTaskX.listener;

import com.playerPlugin.playerTaskX.engine.QuestEngine;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.inventory.InventoryEvent;
import // ... other event imports

public class GraphEventListener implements Listener {
    private final QuestEngine questEngine;
    
    public GraphEventListener(QuestEngine questEngine) {
        this.questEngine = questEngine;
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            questEngine.handleEvent(killer, event);
        }
    }
    
    // Add handlers for other event types that can advance quest progress
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

### Task 14: Initialize QuestEngine on server startup

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/PlayerTaskX.java`

- [ ] **Step 1: Initialize QuestEngine and register components**

```java
public class PlayerTaskX extends JavaPlugin {
    private TaskManager taskManager;
    private QuestEngine questEngine;  // NEW
    private SessionStorage sessionStorage;  // NEW
    
    @Override
    public void onEnable() {
        // Initialize storage
        ProgressStorage progressStorage = createProgressStorage();
        
        // Initialize session storage
        sessionStorage = createSessionStorage();
        
        // Initialize quest engine
        NodeHandlerRegistry handlerRegistry = new NodeHandlerRegistry();
        registerHandlers(handlerRegistry);
        
        QuestSessionManager sessionManager = new QuestSessionManager();
        
        questEngine = new QuestEngine(sessionManager, handlerRegistry, taskManager, sessionStorage);
        
        // Restore sessions on startup
        questEngine.restoreSessions();
        
        // Register listener
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
        registry.register(new SubtaskNodeHandler());
    }
}
```

- [ ] **Step 2: Run build**

Run: `cd PlayerTaskX && ./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/PlayerTaskX.java
git commit -m "feat(engine): initialize QuestEngine on server startup"
```

---

## Phase 5: Testing & Cleanup

### Task 15: Database migration script

**Files:**
- Create: `core/src/main/resources/migrations/add_quest_sessions.sql`

- [ ] **Step 1: Create migration script**

```sql
-- Migration: Add quest_sessions table for graph execution
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

-- Optional: Migrate existing progress entries to sessions
-- INSERT INTO quest_sessions (player_uuid, quest_id, current_node_id, start_time, last_active, status)
-- SELECT player_id, task_id, NULL, accepted_at, last_active, status FROM progress;
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/resources/migrations/add_quest_sessions.sql
git commit -m "db: add quest_sessions table migration"
```

---

### Task 16: Verify full build

- [ ] **Step 1: Run full build**

Run: `cd PlayerTaskX && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Commit**

```bash
git commit -m "chore: verify full build passes"
```

---

## Summary

**Total Tasks: 16**

| Phase | Tasks |
|-------|-------|
| Infrastructure | 3 |
| Core Engine | 4 |
| Node Handlers | 3 |
| Integration | 4 |
| Testing & Cleanup | 2 |

**Estimated Time:** 4-6 implementation cycles (based on spec complexity)
