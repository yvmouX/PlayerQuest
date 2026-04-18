# Quest Graph Engine Design

**Date**: 2026-04-18
**Status**: Approved
**Branch**: `feature/quest-graph-engine`

## Overview

Replace the linear `objectives` field in `TaskDefinition` with a graph-based execution engine. The `QuestGraph` (nodes + edges) will drive quest progression through an event-driven state machine.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      QuestEngine                             │
│  (Graph Execution Engine)                                     │
├─────────────────────────────────────────────────────────────┤
│  QuestSessionManager    │  NodeHandlerRegistry              │
│  Manages all sessions   │  Registers node type handlers     │
├─────────────────────────────────────────────────────────────┤
│  EventListener          │  TimerScheduler                   │
│  Event-driven triggers  │  Timer/Counter tick handling      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    NodeHandlers                               │
│  StartHandler │ TaskHandler │ ConditionHandler │ ...        │
│  (One handler per node type)                                 │
└─────────────────────────────────────────────────────────────┘
```

---

## Core Components

| Component | Responsibility |
|-----------|----------------|
| `QuestEngine` | Engine entry, manages sessions, startup/shutdown |
| `QuestSession` | Single player-quest session: current node, context |
| `QuestSessionManager` | CRUD for all sessions |
| `NodeHandlerRegistry` | Register/dispatch node handlers |
| `NodeHandler` | Execution logic interface for each node type |

---

## Data Flow

```
Player accepts quest
    │
    ▼
Create QuestSession(currentNode=Start)
    │
    ▼
Event triggers → GraphEventListener
    │
    ▼
QuestEngine.handleEvent()
    │
    ▼
Get current node's Handler
    │
    ▼
Handler.execute(session, event, graph)
    │
    ▼
Returns nextNodeId → Update session.currentNode
    │
    ▼
session.currentNode == Completion → Grant rewards → End
```

---

## QuestSession Model

```java
public class QuestSession {
    UUID playerId;
    String questId;
    String currentNodeId;
    Set<String> completedNodes;
    Map<String, Object> context;  // Shared variables between nodes
    long startTime;
    long lastActiveTime;
    PTXTaskStatus status;
}
```

---

## NodeHandler Interface

```java
public interface NodeHandler {
    String getNodeType();
    NextNodeResult execute(QuestSession session, Event event, QuestGraph graph);
    boolean canHandle(String nodeType);
}

public class NextNodeResult {
    String nextNodeId;             // null = wait for next event
    Map<String, Object> contextUpdate;
    boolean isTerminal;            // true = quest complete
}
```

---

## Node Handlers

| Handler | Logic |
|---------|-------|
| `StartHandler` | Returns the first node after Start |
| `TaskHandler` | Checks if objectives completed, returns next node if done |
| `CompletionHandler` | Grants rewards, sets `isTerminal=true` |
| `ConditionHandler` | Evaluates conditions, returns branch node |
| `BranchHandler` | Unconditionally routes to linked Condition |
| `ActionHandler` | Executes side effects (give items, etc.), returns next node |
| `EventHandler` | Registers event listener, does not transition yet |
| `CounterHandler` | Increments/decrements counter, transitions if threshold met |
| `TimerHandler` | Sets timer, transitions on timeout or completes |
| `StateHandler` | Modifies player state in session context |
| `SubtaskHandler` | Handles nested sub-quest logic |

---

## Storage

| Data | Location | Method |
|------|----------|--------|
| `QuestGraph` | `TaskDefinition.graph` | Stored with task |
| `QuestSession` | `quest_progress` table | JSON column |

### Database Schema

```sql
CREATE TABLE quest_progress (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    player_uuid     VARCHAR(36) NOT NULL,
    quest_id        VARCHAR(64) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    
    -- Graph execution fields
    current_node_id VARCHAR(64),
    completed_nodes JSON,
    context_data    JSON,
    start_time      BIGINT,
    last_active     BIGINT,
    
    -- Existing fields
    progress        JSON,
    
    UNIQUE KEY uk_player_quest (player_uuid, quest_id),
    INDEX idx_player (player_uuid)
);
```

---

## Compatibility

### Legacy Task Support

```java
public void startQuest(Player player, TaskDefinition task) {
    if (task.getGraph() == null || task.getGraph().isEmpty()) {
        handleLegacyTask(player, task);  // Use old objectives logic
        return;
    }
    questEngine.startQuest(player, task);
}
```

### Database Migration

```sql
ALTER TABLE quest_progress
ADD COLUMN current_node_id VARCHAR(64) DEFAULT NULL,
ADD COLUMN completed_nodes JSON DEFAULT NULL,
ADD COLUMN context_data JSON DEFAULT NULL,
ADD COLUMN start_time BIGINT DEFAULT NULL,
ADD COLUMN last_active BIGINT DEFAULT NULL;
```

---

## Directory Structure

```
api/src/main/java/com/playerPlugin/playerTaskX/api/
├── model/
│   ├── QuestGraph.java           (existing)
│   ├── GraphNode.java            (existing)
│   ├── NodeConnection.java       (existing)
│   └── session/
│       ├── QuestSession.java     (new)
│       └── NextNodeResult.java   (new)
└── handler/
    ├── NodeHandler.java          (new)
    └── NodeHandlerRegistry.java  (new)

core/src/main/java/com/playerPlugin/playerTaskX/
├── engine/
│   ├── QuestEngine.java          (new)
│   ├── QuestSessionManager.java  (new)
│   └── exception/
│       └── GraphExecutionException.java (new)
├── handler/
│   ├── StartNodeHandler.java    (new)
│   ├── TaskNodeHandler.java     (new)
│   ├── CompletionNodeHandler.java (new)
│   ├── ConditionNodeHandler.java (new)
│   ├── BranchNodeHandler.java    (new)
│   ├── ActionNodeHandler.java    (new)
│   ├── EventNodeHandler.java     (new)
│   ├── CounterNodeHandler.java   (new)
│   ├── TimerNodeHandler.java     (new)
│   ├── StateNodeHandler.java     (new)
│   └── SubtaskNodeHandler.java   (new)
├── storage/
│   └── SessionStorage.java       (new)
├── listener/
│   └── GraphEventListener.java   (refactor from EntityListener)
└── web/
    └── EditorServer.java         (refactor: remove GraphManager)
```

---

## Runtime Error Handling

On invalid connections (dead ends, orphaned nodes, cycles), the engine:
- Skips to next valid transition
- Logs a WARN with details
- Does NOT crash the server

---

## Implementation Order

**Phase 1: Infrastructure**
1.1 Add `graph` field to `TaskDefinition`
1.2 Create `QuestSession`, `NextNodeResult` models
1.3 Create `NodeHandler` interface, `NodeHandlerRegistry`

**Phase 2: Core Engine**
2.1 Implement `QuestSessionManager`
2.2 Implement `SessionStorage`
2.3 Implement `QuestEngine` main logic

**Phase 3: Node Handlers**
3.1 `StartHandler`, `TaskHandler`, `CompletionHandler` (core)
3.2 `ConditionHandler`, `BranchHandler` (branching)
3.3 `ActionHandler`, `EventHandler`, `CounterHandler`, `TimerHandler`, `StateHandler`, `SubtaskHandler`

**Phase 4: Integration**
4.1 Refactor `TaskManager` to delegate to `QuestEngine`
4.2 Refactor `EditorServer` (remove `GraphManager`)
4.3 Refactor `EntityListener` → `GraphEventListener`
4.4 Database migration script

**Phase 5: Testing**
5.1 Unit tests
5.2 Integration tests
5.3 Manual verification

---

## API Changes

### Task CRUD (unchanged)

```
GET    /api/quests           → Returns TaskDefinition (with graph)
POST   /api/quests           → Create (with graph)
PUT    /api/quests/{id}      → Update (with graph)
DELETE /api/quests/{id}      → Delete
```

### Graph Storage API (removed)

```
DELETE /api/graphs/*         → Removed (graph now embedded in TaskDefinition)
```

---

## Frontend-Backend Type Alignment

| Frontend TypeScript | Backend Java | Notes |
|---------------------|--------------|-------|
| `QuestGraph` | `QuestGraph` | Same |
| `GraphNode` | `GraphNode` | Same |
| `NodeConnection` | `NodeConnection` | Same |
| `EditorNodeData` | `Map<String, Object>` | Handler internal conversion |
| `TaskNodeData` | `TaskNodeHandler` internal class | Field-aligned |

---

## Decisions

| Decision | Choice |
|----------|--------|
| objectives/conditions replacement | **A. Replace** - QuestGraph completely replaces objectives |
| QuestGraph storage | **A. Embedded** - `TaskDefinition.graph` field |
| Session storage | **B. Extend Progress** - Add columns to quest_progress table |
| Event handling | **C. Hybrid** - Event-driven + timer for Timer/Counter |
| Invalid connection handling | **C. Runtime tolerance** - Skip and log |
