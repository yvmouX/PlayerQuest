# 节点系统重构设计

## 概述

精简并重构 PlayerTaskX 编辑器的节点系统，从当前 8 种节点类型简化为 6 种，建立更清晰的节点职责边界和流程结构。

## 节点类型

### 1. Start 节点（入口）

**外观**
- 圆形，绿色(#22c55e)，60x60px

**规则**
- 每个任务流程图**只能有 1 个**
- 可连出：Trigger（无 Trigger 时直接连 Task）

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 节点名称 |

---

### 2. Trigger 节点（触发条件）

**外观**
- 菱形，黄色(#eab308)，60x60px

**位置约束**
- **只能位于 Start 和 Task 之间**
- 每个任务流程图**最多 1 个**

**职责**
- 判断玩家是否满足接取任务的前置条件
- 例如：完成某个前置任务、与 NPC 对话、拥有权限等

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 节点名称 |
| conditionType | string | 条件类型（如 quest_complete, permission, NPC对话） |
| conditionConfig | object | 条件配置（具体参数） |

**可扩展**：当前仅实现"前置任务完成"条件，后续可拓展其他条件类型。

---

### 3. Task 节点（任务）

**外观**
- 卡片样式，180x80px，白色背景，蓝色边框

**规则**
- 每个任务流程图**必须有 1 个**

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 唯一标识 |
| name | Record<string,string> | 多语言名称 |
| description | Record<string,string> | 多语言描述 |
| taskType | enum | CYCLE / TIMER / FOREVER / LIMIT / NONE |

**taskType 动态字段**

| taskType | 字段 | 说明 |
|----------|------|------|
| CYCLE | resetInterval | 重置间隔（秒） |
| TIMER | resetInterval | 重置时间间隔（秒） |
| FOREVER | 无 | 永久任务 |
| LIMIT | timeLimit, expiredAction | 限时（秒）、超时动作 |
| NONE | 无 | 无特殊逻辑 |

**流程控制**
- Task 节点连接 0+ 个 Objective 节点
- 如果 Task 无 Objective 连接，则连接 Completion 节点

---

### 4. Objective 节点（目标）

**外观**
- 菱形，紫色(#8b5cf6)，60x60px

**规则**
- **禁止串联**：Objective 节点之间不能连接
- 一个 Task 节点可连接多个并联的 Objective 节点
- 每个 Objective 节点可连接 0+ 个 Action 节点

**职责**
- 引用目标库模板，判断玩家是否达成目标
- 目标完成时触发其连接的 Action 节点

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 节点名称 |
| templateId | string | 引用目标库模板 ID（可选） |
| customConfig | object | 自定义目标配置（当无 templateId 时使用） |

**目标库模板**
预设模板存储在目标库中，Objective 节点通过 templateId 引用。
模板包含：目标类型（击杀、收集、对话等）、默认参数。

---

### 5. Action 节点（行为）

**外观**
- 矩形，橙色(#f97316)，80x60px

**规则**
- **禁止串联**：Action 节点之间不能连接
- 一个 Objective 或 Completion 节点可连接多个 Action 节点
- 可引用行为库预设，也可完全自定义配置

**职责**
- 执行具体行为（给予物品、执行命令、发送消息等）

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 节点名称 |
| templateId | string | 引用行为库模板 ID（可选） |
| customConfig | object | 自定义行为配置（当无 templateId 时使用） |

**行为库模板**
预设模板存储在行为库中，Action 节点通过 templateId 引用。
模板包含：行为类型（给予物品、执行命令、发送消息、播放粒子等）、默认参数。

---

### 6. Completion 节点（完成）

**外观**
- 菱形，红色(#ef4444)，60x60px

**规则**
- 每个任务流程图**只有 1 个**
- 位于流程终点
- 可连接 0+ 个 Action 节点

**职责**
- 标记任务流程结束
- 根据关联的 Task 节点的 taskType 执行相应逻辑：
  - **CYCLE**：重置任务状态，保留进度
  - **TIMER**：重置任务状态，保留进度
  - **FOREVER**：任务保持完成状态
  - **LIMIT**：任务结束，不再可接取
  - **NONE**：无特殊逻辑

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 节点名称 |
| callbackMessage | string | 完成回调消息（可选） |

---

## 流程结构

```
┌─────────────────────────────────────────────────────────┐
│                         Start                           │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
                  Trigger (可选)
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                         Task                            │
└───────┬─────────────────┬─────────────────┬─────────────┘
        │                 │                 │
        ▼                 ▼                 ▼
   Objective1         Objective2        Objective3
        │                 │                 │
        ▼                 ▼                 ▼
   Action1,Action2   Action3,Action4   Action5,Action6
        │                 │                 │
        └────────┬────────┴────────┬────────┘
                 │                 │
                 └─────▼─────────▼─────┐
                                      │
                               Completion
                                      │
                                      ▼
                               Action7,Action8...
```

**流程规则：**
- Start → Trigger（无 Trigger 时直接）→ Task
- Task → 0+ 个并联 Objective（禁止串联）
- Task → Completion（如果无 Objective）
- Objective → 0+ 个 Action
- Completion → 0+ 个 Action
- Action 禁止串联

---

## 数据模型

### NodeDefinition

```typescript
interface NodeDefinition {
  id: string
  type: 'start' | 'trigger' | 'task' | 'objective' | 'action' | 'completion'
  position: { x: number, y: number }
  data: StartData | TriggerData | TaskData | ObjectiveData | ActionData | CompletionData
}
```

### StartData

```typescript
interface StartData {
  name: string
}
```

### TriggerData

```typescript
interface TriggerData {
  name: string
  conditionType: string  // 如 'quest_complete', 'permission', 'npc_interact'
  conditionConfig: object
}
```

### TaskData

```typescript
interface TaskData {
  id: string
  name: Record<string, string>
  description: Record<string, string>
  taskType: 'CYCLE' | 'TIMER' | 'FOREVER' | 'LIMIT' | 'NONE'
  // 动态字段
  resetInterval?: number    // CYCLE / TIMER
  timeLimit?: number       // LIMIT
  expiredAction?: string   // LIMIT
}
```

### ObjectiveData

```typescript
interface ObjectiveData {
  name: string
  templateId?: string      // 引用目标库模板
  customConfig?: object    // 自定义配置（无 templateId 时使用）
}
```

### ActionData

```typescript
interface ActionData {
  name: string
  templateId?: string      // 引用行为库模板
  customConfig?: object    // 自定义配置（无 templateId 时使用）
}
```

### CompletionData

```typescript
interface CompletionData {
  name: string
  callbackMessage?: string
}
```

---

## 节点移除

以下节点类型将从编辑器中移除：
- ~~Branch 节点~~ - 分支逻辑由多个并联 Objective 实现
- ~~Counter 节点~~ - 计数器功能可由 Action 实现
- ~~Timer 节点~~ - 计时器功能可由 Action 实现
- ~~Subtask 节点~~ - 子任务逻辑由串联 Objective 实现（需扩展）

---

## 前端修改

### 组件变更

| 组件 | 操作 |
|------|------|
| StartNode.vue | 保留 |
| TaskNode.vue | 保留，扩展 taskType 动态字段 |
| CompletionNode.vue | 保留，简化（移除 rewards） |
| TriggerNode.vue | 重命名自 ConditionNode.vue |
| ObjectiveNode.vue | 新增（替代原 TaskNode 的 objectives 功能） |
| ActionNode.vue | 重构（支持引用库模板 + 自定义） |

### 移除组件

- BranchNode.vue
- CounterNode.vue
- TimerNode.vue
- SubtaskNode.vue

### 类型定义

```typescript
type NodeType = 'start' | 'trigger' | 'task' | 'objective' | 'action' | 'completion'

interface EditorNode {
  id: string
  nodeType: NodeType
  position: { x: number, y: number }
  data: StartData | TriggerData | TaskData | ObjectiveData | ActionData | CompletionData
}
```

---

## 后端 Handler 变更

### 新增 Handler

| Handler | 职责 |
|---------|------|
| TriggerNodeHandler | 处理触发条件判断 |
| ObjectiveNodeHandler | 处理目标完成判断，引用目标库 |
| ActionNodeHandler | 处理行为执行，支持行为库和自定义 |

### 修改 Handler

| Handler | 变更 |
|---------|------|
| TaskNodeHandler | 移除 objectives 处理逻辑，专注 taskType 和流程控制 |
| CompletionNodeHandler | 简化（移除 rewards 处理），根据 taskType 处理重置逻辑 |

### 移除 Handler

- BranchNodeHandler
- CounterNodeHandler
- TimerNodeHandler
- SubtaskNodeHandler

---

## 目标库与行为库

### 目标库（Objective Library）

预设目标模板存储在后端，提供 API 供前端引用：
- `/api/objectives/templates` - 获取所有模板
- `/api/objectives/templates/{id}` - 获取单个模板

### 行为库（Action Library）

预设行为模板存储在后端，提供 API 供前端引用：
- `/api/actions/templates` - 获取所有模板
- `/api/actions/templates/{id}` - 获取单个模板

---

## 实现步骤

### Phase 1: 核心节点（本期）

1. 创建 TriggerNodeHandler、ObjectiveNodeHandler、ActionNodeHandler
2. 修改 TaskNodeHandler（移除 objectives 逻辑）
3. 修改 CompletionNodeHandler（简化 + taskType 重置逻辑）
4. 创建/重构前端组件：TriggerNode、ObjectiveNode、ActionNode
5. 简化 NodePropertiesPanel 支持新节点类型
6. 更新连线规则验证

### Phase 2: 库系统（后续）

1. 创建目标库 API 和存储
2. 创建行为库 API 和存储
3. 前端目标库/行为库管理界面
4. ObjectiveNode/ActionNode 支持选择库模板

### Phase 3: 清理（后续）

1. 移除废弃节点类型和组件
2. 更新文档和示例
