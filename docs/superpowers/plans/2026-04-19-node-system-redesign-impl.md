# 节点系统重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 8 种节点精简为 6 种（Start、Trigger、Task、Objective、Action、Completion），重构前后端逻辑

**Architecture:** 
- 后端：重构 NodeHandler 实现，TriggerNodeHandler 处理单一条件，ObjectiveNodeHandler 处理目标判断，ActionNodeHandler 处理行为执行
- 前端：更新类型定义、节点组件和连线规则

**Tech Stack:** Java (Spigot/Paper), Vue 3, TypeScript, VueFlow

---

## 后端变更

### 文件结构

```
core/src/main/java/com/playerPlugin/playerTaskX/
├── handler/
│   ├── StartNodeHandler.java      # 保持不变
│   ├── TaskNodeHandler.java       # 修改：移除 objectives 逻辑
│   ├── CompletionNodeHandler.java # 修改：简化 + taskType 重置
│   ├── TriggerNodeHandler.java   # 重写自 ConditionNodeHandler
│   ├── ObjectiveNodeHandler.java  # 新增
│   └── ActionNodeHandler.java     # 新增（替代原 ActionNodeHandler）
```

### Task 1: 创建 TriggerNodeHandler

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/handler/TriggerNodeHandler.java`
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/handler/ConditionNodeHandler.java` (删除或注释)

- [ ] **Step 1: 创建 TriggerNodeHandler.java**

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

public class TriggerNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "trigger"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.waiting();
        
        Map<String, Object> data = currentNode.getData();
        String conditionType = (String) data.get("conditionType");
        Map<String, Object> conditionConfig = (Map<String, Object>) data.get("conditionConfig");
        
        boolean conditionMet = evaluateCondition(conditionType, conditionConfig, session);
        
        session.markNodeCompleted(session.getCurrentNodeId());
        
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(session.getCurrentNodeId()))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        // trigger 只有满足条件才能进入任务
        if (!conditionMet) {
            // 条件不满足，任务不能接取，结束流程
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        return NextNodeResult.next(nextNodes.get(0));
    }
    
    private boolean evaluateCondition(String type, Map<String, Object> config, QuestSession session) {
        if (type == null) return true;
        
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return false;
        
        switch (type) {
            case "quest_complete" -> {
                String questId = (String) config.get("questId");
                // 检查玩家是否完成了指定任务
                return checkQuestCompleted(session, questId);
            }
            case "permission" -> {
                String permission = (String) config.get("permission");
                return permission != null && player.hasPermission(permission);
            }
            case "npc_interact" -> {
                String npcId = (String) config.get("npcId");
                return Boolean.TRUE.equals(session.getContext().get("npc_interact_" + npcId));
            }
            default -> {
                org.slf4j.LoggerFactory.getLogger(TriggerNodeHandler.class)
                    .warn("Unknown trigger condition type: {}", type);
                return true; // 未知类型默认通过
            }
        }
    }
    
    private boolean checkQuestCompleted(QuestSession session, String questId) {
        // 实际实现需要检查玩家任务进度
        // 暂时返回 false，后续与任务系统集成
        return false;
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public boolean canHandle(String nodeType) {
        return "trigger".equals(nodeType);
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/TriggerNodeHandler.java
git commit -m "feat(handler): add TriggerNodeHandler for quest trigger conditions"
```

---

### Task 2: 创建 ObjectiveNodeHandler

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/handler/ObjectiveNodeHandler.java`

- [ ] **Step 1: 创建 ObjectiveNodeHandler.java**

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

public class ObjectiveNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "objective"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.waiting();
        
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return NextNodeResult.waiting();
        
        Map<String, Object> data = currentNode.getData();
        String templateId = (String) data.get("templateId");
        Map<String, Object> customConfig = (Map<String, Object>) data.get("customConfig");
        
        boolean completed = checkObjectiveCompleted(player, session, templateId, customConfig, event);
        
        if (!completed) {
            // 目标未完成，等待
            return NextNodeResult.waiting();
        }
        
        session.markNodeCompleted(session.getCurrentNodeId());
        
        // 目标完成后，触发所有连接的 Action 节点
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(session.getCurrentNodeId()))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            // 没有连接的 Action，返回等待
            return NextNodeResult.waiting();
        }
        
        // 返回第一个连接的节点（通常是 Action）
        String nextNodeId = nextNodes.get(0);
        return NextNodeResult.next(nextNodeId);
    }
    
    private boolean checkObjectiveCompleted(Player player, QuestSession session, 
            String templateId, Map<String, Object> customConfig, Event event) {
        
        Map<String, Object> config = customConfig;
        if (templateId != null && !templateId.isEmpty()) {
            // 从目标库获取模板配置（后续实现）
            config = getTemplateConfig(templateId);
        }
        
        if (config == null) return true; // 无配置视为完成
        
        String objectiveType = (String) config.get("type");
        if (objectiveType == null) return true;
        
        switch (objectiveType) {
            case "kill_mob" -> {
                String mobType = (String) config.get("target");
                int required = ((Number) config.getOrDefault("amount", 1)).intValue();
                int current = ((Number) session.getContext().getOrDefault("kills_" + mobType, 0)).intValue();
                return current >= required;
            }
            case "collect_item" -> {
                String itemId = (String) config.get("target");
                int required = ((Number) config.getOrDefault("amount", 1)).intValue();
                int current = ((Number) session.getContext().getOrDefault("collected_" + itemId, 0)).intValue();
                return current >= required;
            }
            case "break_block" -> {
                String blockType = (String) config.get("target");
                int required = ((Number) config.getOrDefault("amount", 1)).intValue();
                int current = ((Number) session.getContext().getOrDefault("broken_" + blockType, 0)).intValue();
                return current >= required;
            }
            case "talk_to_npc" -> {
                String npcId = (String) config.get("target");
                return Boolean.TRUE.equals(session.getContext().get("npc_talked_" + npcId));
            }
            case "reach_location" -> {
                Map<String, Object> location = (Map<String, Object>) config.get("location");
                if (location == null) return true;
                return Boolean.TRUE.equals(session.getContext().get("reached_location"));
            }
            default -> {
                org.slf4j.LoggerFactory.getLogger(ObjectiveNodeHandler.class)
                    .warn("Unknown objective type: {}", objectiveType);
                return true;
            }
        }
    }
    
    private Map<String, Object> getTemplateConfig(String templateId) {
        // 后续从目标库加载
        return null;
    }
    
    private GraphNode findNode(QuestGraph graph, String nodeId) {
        return graph.getNodes().stream()
            .filter(n -> n.getId().equals(nodeId))
            .findFirst()
            .orElse(null);
    }
    
    @Override
    public boolean canHandle(String nodeType) {
        return "objective".equals(nodeType);
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/ObjectiveNodeHandler.java
git commit -m "feat(handler): add ObjectiveNodeHandler for objective tracking"
```

---

### Task 3: 创建 ActionNodeHandler

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/handler/ActionNodeHandler.java`

- [ ] **Step 1: 创建 ActionNodeHandler.java**

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
        if (currentNode == null) return NextNodeResult.waiting();
        
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return NextNodeResult.waiting();
        
        Map<String, Object> data = currentNode.getData();
        String templateId = (String) data.get("templateId");
        Map<String, Object> customConfig = (Map<String, Object>) data.get("customConfig");
        
        executeAction(player, templateId, customConfig);
        
        session.markNodeCompleted(session.getCurrentNodeId());
        
        // Action 节点执行完毕后，查找下一个节点
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(session.getCurrentNodeId()))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.waiting();
        }
        
        String nextNodeId = nextNodes.get(0);
        return NextNodeResult.next(nextNodeId);
    }
    
    private void executeAction(Player player, String templateId, Map<String, Object> customConfig) {
        Map<String, Object> config = customConfig;
        if (templateId != null && !templateId.isEmpty()) {
            config = getTemplateConfig(templateId);
        }
        
        if (config == null) return;
        
        String actionType = (String) config.get("type");
        if (actionType == null) return;
        
        switch (actionType) {
            case "give_item" -> {
                String itemId = (String) config.get("item");
                int amount = ((Number) config.getOrDefault("amount", 1)).intValue();
                try {
                    org.bukkit.Material material = org.bukkit.Material.valueOf(itemId.toUpperCase());
                    player.getInventory().addItem(new org.bukkit.inventory.ItemStack(material, amount));
                } catch (IllegalArgumentException e) {
                    org.slf4j.LoggerFactory.getLogger(ActionNodeHandler.class)
                        .warn("Invalid material: {}", itemId);
                }
            }
            case "execute_command" -> {
                String command = (String) config.get("command");
                if (command != null) {
                    if (command.startsWith("/")) command = command.substring(1);
                    command = command.replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
            }
            case "send_message" -> {
                String message = (String) config.get("message");
                if (message != null) {
                    player.sendMessage(message);
                }
            }
            case "play_effect" -> {
                String effect = (String) config.get("effect");
                if (effect != null) {
                    player.getWorld().playEffect(player.getLocation(), 
                        org.bukkit.Effect.valueOf(effect.toUpperCase()), 1);
                }
            }
            case "sound" -> {
                String sound = (String) config.get("sound");
                float volume = ((Number) config.getOrDefault("volume", 1.0f)).floatValue();
                float pitch = ((Number) config.getOrDefault("pitch", 1.0f)).floatValue();
                if (sound != null) {
                    player.playSound(player.getLocation(), sound, volume, pitch);
                }
            }
            case "give_xp" -> {
                int xp = ((Number) config.getOrDefault("xp", 0)).intValue();
                player.giveExp(xp);
            }
            default -> {
                org.slf4j.LoggerFactory.getLogger(ActionNodeHandler.class)
                    .warn("Unknown action type: {}", actionType);
            }
        }
    }
    
    private Map<String, Object> getTemplateConfig(String templateId) {
        // 后续从行为库加载
        return null;
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

- [ ] **Step 2: 提交**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/ActionNodeHandler.java
git commit -m "feat(handler): add ActionNodeHandler for action execution"
```

---

### Task 4: 修改 TaskNodeHandler

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/handler/TaskNodeHandler.java`

- [ ] **Step 1: 修改 TaskNodeHandler.java**

将原 `TaskNodeHandler.java` 内容替换为：

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

public class TaskNodeHandler implements NodeHandler {
    @Override
    public String getNodeType() { return "task"; }
    
    @Override
    @SuppressWarnings("unchecked")
    public NextNodeResult execute(QuestSession session, Event event, QuestGraph graph) {
        GraphNode currentNode = findNode(graph, session.getCurrentNodeId());
        if (currentNode == null) return NextNodeResult.waiting();
        
        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) return NextNodeResult.waiting();
        
        Map<String, Object> data = currentNode.getData();
        if (data == null) return NextNodeResult.waiting();
        
        // Task 节点不再处理 objectives，专注流程控制
        // 检查是否有连接的 Objective 节点
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(session.getCurrentNodeId()))
            .map(NodeConnection::getTargetId)
            .toList();
        
        if (nextNodes.isEmpty()) {
            // 没有后续节点，标记任务完成
            session.markNodeCompleted(session.getCurrentNodeId());
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        // 找到第一个 Objective 或 Completion 节点
        for (String nextNodeId : nextNodes) {
            GraphNode nextNode = findNode(graph, nextNodeId);
            if (nextNode != null) {
                String nodeType = nextNode.getNodeType();
                if ("objective".equals(nodeType) || "completion".equals(nodeType)) {
                    session.markNodeCompleted(session.getCurrentNodeId());
                    return NextNodeResult.next(nextNodeId);
                }
            }
        }
        
        // 没有找到 objective 或 completion，等待
        return NextNodeResult.waiting();
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

- [ ] **Step 2: 提交**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/TaskNodeHandler.java
git commit -m "refactor(handler): simplify TaskNodeHandler - remove objectives logic"
```

---

### Task 5: 修改 CompletionNodeHandler

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/handler/CompletionNodeHandler.java`

- [ ] **Step 1: 修改 CompletionNodeHandler.java**

将原 `CompletionNodeHandler.java` 内容替换为：

```java
package com.playerPlugin.playerTaskX.handler;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.handler.NodeHandler;
import com.playerPlugin.playerTaskX.api.model.GraphNode;
import com.playerPlugin.playerTaskX.api.model.QuestGraph;
import com.playerPlugin.playerTaskX.api.model.session.NextNodeResult;
import com.playerPlugin.playerTaskX.api.model.session.QuestSession;
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
        String taskId = (String) data.get("taskId");
        String taskType = getTaskType(graph, taskId);
        
        // 根据 taskType 处理重置逻辑
        handleTaskTypeReset(session, taskType);
        
        session.markNodeCompleted(session.getCurrentNodeId());
        
        // 执行 Completion 后续的 Action 节点
        List<String> nextNodes = graph.getEdges().stream()
            .filter(e -> e.getSourceId().equals(session.getCurrentNodeId()))
            .map(e -> e.getTargetId())
            .toList();
        
        if (nextNodes.isEmpty()) {
            return NextNodeResult.terminal(session.getCurrentNodeId());
        }
        
        // 返回第一个后续节点（通常是 Action）
        String nextNodeId = nextNodes.get(0);
        return NextNodeResult.next(nextNodeId);
    }
    
    private String getTaskType(QuestGraph graph, String taskId) {
        if (taskId == null) return "FOREVER";
        
        GraphNode taskNode = graph.getNodes().stream()
            .filter(n -> taskId.equals(n.getId()) && "task".equals(n.getNodeType()))
            .findFirst()
            .orElse(null);
        
        if (taskNode == null) return "FOREVER";
        
        Map<String, Object> data = taskNode.getData();
        if (data == null) return "FOREVER";
        
        Object taskType = data.get("taskType");
        return taskType != null ? taskType.toString() : "FOREVER";
    }
    
    private void handleTaskTypeReset(QuestSession session, String taskType) {
        switch (taskType) {
            case "CYCLE", "TIMER" -> {
                // 重置任务状态，保留进度
                session.resetProgress();
            }
            case "FOREVER" -> {
                // 任务保持完成状态
                session.setStatus(PTXTaskStatus.COMPLETED);
            }
            case "LIMIT" -> {
                // 任务结束，不再可接取
                session.setStatus(PTXTaskStatus.CLAIMED);
            }
            case "NONE" -> {
                // 无特殊逻辑
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

- [ ] **Step 2: 提交**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/handler/CompletionNodeHandler.java
git commit -m "refactor(handler): update CompletionNodeHandler with taskType reset logic"
```

---

## 前端变更

### 文件结构

```
task-editor-vue/src/
├── types/index.ts              # 修改：更新类型定义
├── composables/useQuestEditor.ts # 修改：更新连线规则
├── components/editor/
│   ├── TriggerNode.vue         # 重命名自 ConditionNode.vue
│   ├── ObjectiveNode.vue      # 新增
│   ├── ActionNode.vue          # 修改：支持 templateId + customConfig
│   └── NodePropertiesPanel.vue # 修改：支持新节点类型
└── views/EditorView.vue       # 修改：更新 sidebar 节点列表
```

---

### Task 6: 更新类型定义

**Files:**
- Modify: `task-editor-vue/src/types/index.ts`

- [ ] **Step 1: 更新 types/index.ts**

替换 NodeType 定义（约第60行）：

```typescript
// 移除: 'condition' | 'branch' | 'event' | 'counter' | 'timer' | 'state' | 'subtask'
export type NodeType = 'start' | 'trigger' | 'task' | 'objective' | 'action' | 'completion'
```

添加新的数据类型定义：

```typescript
// TriggerData - 触发条件
export interface TriggerData {
  type: 'trigger'
  name: string
  conditionType: string  // 'quest_complete' | 'permission' | 'npc_interact'
  conditionConfig: Record<string, any>
}

// ObjectiveData - 目标
export interface ObjectiveData {
  type: 'objective'
  name: string
  templateId?: string
  customConfig?: {
    type: string  // 'kill_mob' | 'collect_item' | 'break_block' | 'talk_to_npc' | 'reach_location'
    target?: string
    amount?: number
    location?: { x: number, y: number, z: number, world: string }
    [key: string]: any
  }
}

// ActionData - 行为（修改，支持 templateId）
export interface ActionData {
  type: 'action'
  name: string
  templateId?: string
  customConfig?: {
    type: string  // 'give_item' | 'execute_command' | 'send_message' | 'play_effect' | 'sound' | 'give_xp'
    item?: string
    amount?: number
    command?: string
    message?: string
    effect?: string
    sound?: string
    volume?: number
    pitch?: number
    xp?: number
    [key: string]: any
  }
}

// CompletionNodeData - 完成（修改，添加 taskId）
export interface CompletionNodeData {
  type: 'completion'
  name: string
  taskId: string  // 关联的 Task 节点 ID
  callbackMessage?: string
}

// 更新 EditorNodeData
export type EditorNodeData = StartNodeData | TriggerData | TaskNodeData | ObjectiveData | ActionData | CompletionNodeData
```

修改 TaskNodeData（移除 objectives 和 rewards）：

```typescript
// TaskNodeData - 任务（修改）
export interface TaskNodeData {
  type: 'task'
  id: string
  name: string
  description: string
  taskType: TaskSubType
  // 移除 objectives 和 rewards
  resetInterval?: number
  timeLimit?: number
  expiredAction?: string
}
```

- [ ] **Step 2: 提交**

```bash
git add task-editor-vue/src/types/index.ts
git commit -m "refactor(frontend): update types for new node system"
```

---

### Task 7: 更新 useQuestEditor 连线规则

**Files:**
- Modify: `task-editor-vue/src/composables/useQuestEditor.ts`

- [ ] **Step 1: 找到并替换 validConnections**

搜索 `validConnections:` 关键字（约第117行），将该对象替换为：

```typescript
const validConnections: Record<string, string[]> = {
  start: ['trigger', 'task'],        // Start 可连 Trigger 或 Task
  trigger: ['task'],                   // Trigger 只能连 Task
  task: ['objective', 'completion'],   // Task 可连多个 Objective 或直接连 Completion
  objective: ['action', 'completion'], // Objective 可连 Action 或 Completion
  action: ['action', 'completion'],    // Action 可连另一个 Action 或 Completion
  completion: ['action']              // Completion 可连 Action
}
```

- [ ] **Step 2: 找到并替换 createDefaultNodeData 函数**

搜索 `function createDefaultNodeData` 关键字（约第35行），将整个函数替换为：

```typescript
function createDefaultNodeData(nodeType: NodeType, id: string): EditorNodeData {
  switch (nodeType) {
    case 'start':
      return { type: 'start', description: '' } as StartNodeData
    case 'trigger':
      return { type: 'trigger', name: '', conditionType: 'quest_complete', conditionConfig: {} } as TriggerData
    case 'task':
      return {
        type: 'task',
        id,
        name: '',
        description: '',
        taskType: 'FOREVER'
      } as TaskNodeData
    case 'objective':
      return { type: 'objective', name: '', templateId: '', customConfig: {} } as ObjectiveData
    case 'action':
      return { type: 'action', name: '', templateId: '', customConfig: {} } as ActionData
    case 'completion':
      return { type: 'completion', name: '', taskId: '', callbackMessage: '' } as CompletionNodeData
    default:
      return { type: 'start', description: '' } as StartNodeData
  }
}
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/composables/useQuestEditor.ts
git commit -m "refactor(frontend): update connection rules for new node system"
```

---

### Task 8: 创建 TriggerNode.vue

**Files:**
- Create: `task-editor-vue/src/components/editor/TriggerNode.vue`

- [ ] **Step 1: 创建 TriggerNode.vue**

```vue
<template>
  <div class="trigger-node">
    <div class="node-header">
      <span class="node-icon">⚡</span>
      <span class="node-type">触发</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Trigger' }}</span>
      <span class="node-condition">{{ conditionLabel }}</span>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {Handle, Position} from '@vue-flow/core'
import type {TriggerData} from '../../types'

const props = defineProps<{
  data: TriggerData
}>()

const conditionLabel = computed(() => {
  const labels: Record<string, string> = {
    'quest_complete': '任务完成',
    'permission': '权限检查',
    'npc_interact': 'NPC 对话'
  }
  return labels[props.data.conditionType] || props.data.conditionType || ''
})
</script>

<style scoped>
.trigger-node {
  min-width: 160px;
  background: white;
  border: 2px solid #eab308;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(234, 179, 8, 0.2);
}
.node-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: #fef9c3;
  border-bottom: 1px solid #fde68a;
}
.node-icon { font-size: 1rem; }
.node-type {
  font-size: 0.7rem;
  color: #854d0e;
}
.node-body {
  padding: 0.5rem 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #713f12;
}
.node-condition {
  font-size: 0.7rem;
  color: #a16207;
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add task-editor-vue/src/components/editor/TriggerNode.vue
git commit -m "feat(frontend): add TriggerNode component"
```

---

### Task 9: 创建 ObjectiveNode.vue

**Files:**
- Create: `task-editor-vue/src/components/editor/ObjectiveNode.vue`

- [ ] **Step 1: 创建 ObjectiveNode.vue**

```vue
<template>
  <div class="objective-node">
    <div class="node-header">
      <span class="node-icon">🎯</span>
      <span class="node-type">目标</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Objective' }}</span>
      <span class="node-template" v-if="data.templateId">📚 {{ data.templateId }}</span>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import {Handle, Position} from '@vue-flow/core'
import type {ObjectiveData} from '../../types'

defineProps<{
  data: ObjectiveData
}>()
</script>

<style scoped>
.objective-node {
  min-width: 160px;
  background: white;
  border: 2px solid #8b5cf6;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(139, 92, 246, 0.2);
}
.node-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: #f3e8ff;
  border-bottom: 1px solid #e9d5ff;
}
.node-icon { font-size: 1rem; }
.node-type {
  font-size: 0.7rem;
  color: #6b21a8;
}
.node-body {
  padding: 0.5rem 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #581c87;
}
.node-template {
  font-size: 0.7rem;
  color: #7c3aed;
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add task-editor-vue/src/components/editor/ObjectiveNode.vue
git commit -m "feat(frontend): add ObjectiveNode component"
```

---

### Task 10: 更新 ActionNode.vue

**Files:**
- Modify: `task-editor-vue/src/components/editor/ActionNode.vue`

- [ ] **Step 1: 更新 ActionNode.vue**

替换整个文件内容：

```vue
<template>
  <div class="action-node">
    <div class="node-header">
      <span class="node-icon">⚙️</span>
      <span class="node-type">行为</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Action' }}</span>
      <span class="node-template" v-if="data.templateId">📚 {{ data.templateId }}</span>
      <span class="node-custom" v-else-if="data.customConfig?.type">{{ actionLabel }}</span>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {Handle, Position} from '@vue-flow/core'
import type {ActionData} from '../../types'

const props = defineProps<{
  data: ActionData
}>()

const actionLabel = computed(() => {
  const labels: Record<string, string> = {
    'give_item': '发放物品',
    'execute_command': '执行命令',
    'send_message': '发送消息',
    'play_effect': '播放特效',
    'sound': '播放音效',
    'give_xp': '发放经验'
  }
  const type = props.data.customConfig?.type
  return labels[type || ''] || type || ''
})
</script>

<style scoped>
.action-node {
  width: 160px;
  background: white;
  border: 2px solid #f97316;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(249, 115, 22, 0.2);
}
.node-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: #ffedd5;
  border-bottom: 1px solid #fed7aa;
}
.node-icon { font-size: 1rem; }
.node-type {
  font-size: 0.7rem;
  color: #9a3412;
}
.node-body {
  padding: 0.5rem 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #7c2d12;
}
.node-template, .node-custom {
  font-size: 0.7rem;
  color: #c2410c;
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add task-editor-vue/src/components/editor/ActionNode.vue
git commit -m "refactor(frontend): update ActionNode to support template system"
```

---

### Task 11: 更新 EditorView.vue

**Files:**
- Modify: `task-editor-vue/src/views/EditorView.vue`

- [ ] **Step 1: 找到并替换 sidebar 节点工具栏**

搜索 `<h3>节点工具</h3>` 关键字，删除其下方所有 `.sidebar-item.node-item` 的 div 元素，替换为：

```html
<h3>节点工具</h3>
<div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'start')">
  <span>▶ Start</span>
</div>
<div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'trigger')">
  <span>⚡ Trigger</span>
</div>
<div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'task')">
  <span>📋 Task</span>
</div>
<div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'objective')">
  <span>🎯 Objective</span>
</div>
<div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'action')">
  <span>⚙️ Action</span>
</div>
<div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'completion')">
  <span>✔ Completion</span>
</div>
```

- [ ] **Step 2: 找到 VueFlow 模板部分，添加新模板**

搜索 `<template #node-condition` 关键字，在其上方添加：

```html
<template #node-trigger="{ data }">
  <TriggerNode :data="data" />
</template>

<template #node-objective="{ data }">
  <ObjectiveNode :data="data" />
</template>
```

删除 `<template #node-condition`, `<template #node-branch`, `<template #node-counter`, `<template #node-timer`, `<template #node-state`, `<template #node-subtask` 这些旧模板。

- [ ] **Step 3: 更新 import 语句**

搜索 `import ConditionNode` 关键字，删除该行及其类似的导入（BranchNode, ActionNode, CounterNode, TimerNode, SubtaskNode）。

添加新导入：

```typescript
import TriggerNode from '../components/editor/TriggerNode.vue'
import ObjectiveNode from '../components/editor/ObjectiveNode.vue'
```

- [ ] **Step 4: 提交**

```bash
git add task-editor-vue/src/views/EditorView.vue
git commit -m "refactor(frontend): update EditorView for new node types"
```

---

### Task 12: 更新 NodePropertiesPanel.vue

**Files:**
- Modify: `task-editor-vue/src/components/editor/NodePropertiesPanel.vue`

- [ ] **Step 1: 替换整个文件内容**

将 `NodePropertiesPanel.vue` 整个文件内容替换为以下新结构：

```vue
<template>
  <aside class="properties-panel" v-if="selectedNode">
    <div class="panel-header">
      <h3>{{ panelTitle }}</h3>
      <button class="close-btn" @click="$emit('close')">×</button>
    </div>
    <div class="panel-body">
      <!-- Start Node -->
      <template v-if="selectedNode.type === 'start'">
        <div class="form-group">
          <label>描述</label>
          <textarea v-model="editedNode.description" rows="3" placeholder="描述..." />
        </div>
      </template>

      <!-- Trigger Node -->
      <template v-else-if="selectedNode.type === 'trigger'">
        <div class="form-group">
          <label>名称</label>
          <input v-model="editedNode.name" placeholder="触发条件名称" />
        </div>
        <div class="form-group">
          <label>条件类型</label>
          <select v-model="editedNode.conditionType">
            <option value="quest_complete">前置任务完成</option>
            <option value="permission">权限检查</option>
            <option value="npc_interact">NPC 对话</option>
          </select>
        </div>
        <template v-if="editedNode.conditionType === 'quest_complete'">
          <div class="form-group">
            <label>任务 ID</label>
            <input v-model="editedNode.conditionConfig.questId" placeholder="前置任务ID" />
          </div>
        </template>
        <template v-else-if="editedNode.conditionType === 'permission'">
          <div class="form-group">
            <label>权限节点</label>
            <input v-model="editedNode.conditionConfig.permission" placeholder="example.permission" />
          </div>
        </template>
        <template v-else-if="editedNode.conditionType === 'npc_interact'">
          <div class="form-group">
            <label>NPC ID</label>
            <input v-model="editedNode.conditionConfig.npcId" placeholder="NPC标识" />
          </div>
        </template>
      </template>

      <!-- Task Node -->
      <template v-else-if="selectedNode.type === 'task'">
        <div class="form-group">
          <label>任务 ID</label>
          <input v-model="editedNode.id" readonly />
        </div>
        <div class="form-group">
          <label>名称</label>
          <input v-model="editedNode.name" placeholder="任务名称" />
        </div>
        <div class="form-group">
          <label>描述</label>
          <textarea v-model="editedNode.description" rows="3" placeholder="描述..." />
        </div>
        <div class="form-group">
          <label>类型</label>
          <select v-model="editedNode.taskType">
            <option value="CYCLE">循环任务</option>
            <option value="TIMER">定时任务</option>
            <option value="FOREVER">永久任务</option>
            <option value="LIMIT">限时任务</option>
            <option value="NONE">无特殊</option>
          </select>
        </div>
        <template v-if="editedNode.taskType === 'CYCLE' || editedNode.taskType === 'TIMER'">
          <div class="form-group">
            <label>重置间隔 (秒)</label>
            <input v-model.number="editedNode.resetInterval" type="number" min="1" placeholder="60" />
          </div>
        </template>
        <template v-if="editedNode.taskType === 'LIMIT'">
          <div class="form-group">
            <label>限时 (秒)</label>
            <input v-model.number="editedNode.timeLimit" type="number" min="1" placeholder="300" />
          </div>
          <div class="form-group">
            <label>超时动作</label>
            <select v-model="editedNode.expiredAction">
              <option value="EXPIRE">任务过期</option>
              <option value="FAIL">任务失败</option>
            </select>
          </div>
        </template>
      </template>

      <!-- Objective Node -->
      <template v-else-if="selectedNode.type === 'objective'">
        <div class="form-group">
          <label>名称</label>
          <input v-model="editedNode.name" placeholder="目标名称" />
        </div>
        <div class="form-group">
          <label>目标库模板</label>
          <input v-model="editedNode.templateId" placeholder="模板ID（可选）" />
        </div>
        <div class="section-divider">
          <h4>自定义配置</h4>
        </div>
        <div class="form-group">
          <label>目标类型</label>
          <select v-model="editedNode.customConfig.type">
            <option value="kill_mob">击杀生物</option>
            <option value="collect_item">收集物品</option>
            <option value="break_block">破坏方块</option>
            <option value="talk_to_npc">与NPC对话</option>
            <option value="reach_location">到达位置</option>
          </select>
        </div>
        <template v-if="editedNode.customConfig.type === 'kill_mob' || editedNode.customConfig.type === 'collect_item' || editedNode.customConfig.type === 'break_block'">
          <div class="form-group">
            <label>目标标识</label>
            <input v-model="editedNode.customConfig.target" placeholder="如: ZOMBIE, DIAMOND, STONE" />
          </div>
          <div class="form-group">
            <label>数量</label>
            <input v-model.number="editedNode.customConfig.amount" type="number" min="1" placeholder="1" />
          </div>
        </template>
        <template v-else-if="editedNode.customConfig.type === 'talk_to_npc'">
          <div class="form-group">
            <label>NPC ID</label>
            <input v-model="editedNode.customConfig.target" placeholder="NPC标识" />
          </div>
        </template>
        <template v-else-if="editedNode.customConfig.type === 'reach_location'">
          <div class="form-group">
            <label>世界</label>
            <input v-model="editedNode.customConfig.location.world" placeholder="world" />
          </div>
          <div class="form-group">
            <label>X 坐标</label>
            <input v-model.number="editedNode.customConfig.location.x" type="number" placeholder="0" />
          </div>
          <div class="form-group">
            <label>Y 坐标</label>
            <input v-model.number="editedNode.customConfig.location.y" type="number" placeholder="64" />
          </div>
          <div class="form-group">
            <label>Z 坐标</label>
            <input v-model.number="editedNode.customConfig.location.z" type="number" placeholder="0" />
          </div>
        </template>
      </template>

      <!-- Action Node -->
      <template v-else-if="selectedNode.type === 'action'">
        <div class="form-group">
          <label>名称</label>
          <input v-model="editedNode.name" placeholder="行为名称" />
        </div>
        <div class="form-group">
          <label>行为库模板</label>
          <input v-model="editedNode.templateId" placeholder="模板ID（可选）" />
        </div>
        <div class="section-divider">
          <h4>自定义配置</h4>
        </div>
        <div class="form-group">
          <label>行为类型</label>
          <select v-model="editedNode.customConfig.type">
            <option value="give_item">发放物品</option>
            <option value="execute_command">执行命令</option>
            <option value="send_message">发送消息</option>
            <option value="play_effect">播放特效</option>
            <option value="sound">播放音效</option>
            <option value="give_xp">发放经验</option>
          </select>
        </div>
        <template v-if="editedNode.customConfig.type === 'give_item'">
          <div class="form-group">
            <label>物品ID</label>
            <input v-model="editedNode.customConfig.item" placeholder="minecraft:diamond" />
          </div>
          <div class="form-group">
            <label>数量</label>
            <input v-model.number="editedNode.customConfig.amount" type="number" min="1" placeholder="1" />
          </div>
        </template>
        <template v-else-if="editedNode.customConfig.type === 'execute_command'">
          <div class="form-group">
            <label>命令</label>
            <input v-model="editedNode.customConfig.command" placeholder="/say Hello %player%" />
          </div>
        </template>
        <template v-else-if="editedNode.customConfig.type === 'send_message'">
          <div class="form-group">
            <label>消息</label>
            <textarea v-model="editedNode.customConfig.message" rows="2" placeholder="消息内容..." />
          </div>
        </template>
        <template v-else-if="editedNode.customConfig.type === 'play_effect'">
          <div class="form-group">
            <label>特效类型</label>
            <input v-model="editedNode.customConfig.effect" placeholder="HEART" />
          </div>
        </template>
        <template v-else-if="editedNode.customConfig.type === 'sound'">
          <div class="form-group">
            <label>音效ID</label>
            <input v-model="editedNode.customConfig.sound" placeholder="entity.player.levelup" />
          </div>
          <div class="form-group">
            <label>音量</label>
            <input v-model.number="editedNode.customConfig.volume" type="number" placeholder="1.0" />
          </div>
          <div class="form-group">
            <label>音调</label>
            <input v-model.number="editedNode.customConfig.pitch" type="number" placeholder="1.0" />
          </div>
        </template>
        <template v-else-if="editedNode.customConfig.type === 'give_xp'">
          <div class="form-group">
            <label>经验值</label>
            <input v-model.number="editedNode.customConfig.xp" type="number" min="1" placeholder="100" />
          </div>
        </template>
      </template>

      <!-- Completion Node -->
      <template v-else-if="selectedNode.type === 'completion'">
        <div class="form-group">
          <label>名称</label>
          <input v-model="editedNode.name" placeholder="完成节点名称" />
        </div>
        <div class="form-group">
          <label>关联任务节点</label>
          <select v-model="editedNode.taskId">
            <option value="">-- 选择 Task --</option>
            <option v-for="n in taskNodes" :key="n.id" :value="n.id">
              {{ n.data.name || n.id }}
            </option>
          </select>
        </div>
        <div class="form-group">
          <label>回调消息</label>
          <textarea v-model="editedNode.callbackMessage" rows="2" placeholder="完成后发送的消息..." />
        </div>
      </template>
    </div>
  </aside>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import type {
  ActionData,
  CompletionNodeData,
  ObjectiveData,
  StartNodeData,
  TaskNodeData,
  TriggerData
} from '../../types'
import {editorNodes} from '../../composables/useQuestEditor'

type NodeData = StartNodeData | TriggerData | TaskNodeData | ObjectiveData | ActionData | CompletionNodeData

const props = defineProps<{
  selectedNode: NodeData | null
}>()

const emit = defineEmits<{
  close: []
  update: [id: string, node: Partial<NodeData>]
}>()

const panelTitle = computed(() => {
  const titles: Record<string, string> = {
    start: 'Start 节点',
    trigger: '触发条件',
    task: '任务属性',
    objective: '目标',
    action: '行为',
    completion: '完成节点'
  }
  return titles[props.selectedNode?.type || ''] || '属性'
})

const taskNodes = computed(() => {
  return editorNodes.value.filter(n => n.nodeType === 'task')
})

const editedNode = ref<NodeData>(createDefaultNode())
let saveTimeout: number | null = null
let lastEmittedNode: string | null = null

function createDefaultNode(): NodeData {
  return {
    type: 'start',
    description: ''
  } as NodeData
}

watch(() => props.selectedNode, (node) => {
  if (node) {
    editedNode.value = JSON.parse(JSON.stringify(node))
    lastEmittedNode = JSON.stringify(editedNode.value)
  }
}, { immediate: true })

watch(editedNode, () => {
  if (props.selectedNode) {
    const nodeData = JSON.stringify(editedNode.value)
    if (nodeData === lastEmittedNode) return
    
    if (saveTimeout) clearTimeout(saveTimeout)
    saveTimeout = window.setTimeout(() => {
      const newNode = editedNode.value
      const nodeId = newNode.type === 'task' ? (newNode as TaskNodeData).id : props.selectedNode.id || ''
      lastEmittedNode = JSON.stringify(editedNode.value)
      emit('update', nodeId, editedNode.value)
    }, 300)
  }
}, { deep: true })
</script>

<style scoped>
.properties-panel {
  width: 320px;
  background: white;
  border-left: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border-bottom: 1px solid #e5e7eb;
}
.panel-header h3 { margin: 0; font-size: 1rem; }
.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
}
.panel-body { flex: 1; overflow-y: auto; padding: 1rem; }
.form-group { margin-bottom: 1rem; }
.form-group label {
  display: block;
  font-size: 0.85rem;
  color: #6b7280;
  margin-bottom: 0.25rem;
}
.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 0.9rem;
  box-sizing: border-box;
}
.section-divider {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 1.5rem 0 1rem;
}
.section-divider h4 { margin: 0; font-size: 0.9rem; }
</style>
```

- [ ] **Step 2: 提交**

```bash
git add task-editor-vue/src/components/editor/NodePropertiesPanel.vue
git commit -m "refactor(frontend): update NodePropertiesPanel for new node types"
```

---

## 验证与测试

- [ ] 运行 `gradle build` 确认后端编译通过
- [ ] 运行 `npm run dev` 或 `npm run build` 确认前端编译通过
- [ ] 手动测试节点创建和连线
