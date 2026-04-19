# 网页编辑器 - 第三期：高级节点设计

## 概述

为 PlayerTaskX 网页编辑器引入五种高级节点（Event/Trigger、Counter、Timer Control、State、Subtask/Group），完善事件监听、进度累计、定时控制、状态管理和任务分组功能。

## 节点类型

### 1. Event/Trigger 节点

**外观**
- 圆形，蓝色（#3b82f6），带闪电图标 ⚡
- 60x60px

**可连出**：Task
**可连入**：Start、Task

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 节点名称 |
| eventTypes | array | 监听事件类型列表 |

**事件类型（完整支持）**

| 类别 | 事件类型 |
|------|----------|
| 玩家动作 | LOGIN, LOGOUT, CHAT, COMMAND, JUMP, SNEAK, SPRINT, DROP_ITEM, PICKUP_ITEM |
| 战斗 | PLAYER_KILL, ENTITY_KILL, PLAYER_DEATH, PVP_KILL |
| 方块交互 | BLOCK_BREAK, BLOCK_PLACE, BLOCK_INTERACT |
| 物品交互 | ITEM_CRAFT, ITEM_USE, ITEM_CONSUME |
| 移动 | PLAYER_MOVE, PLAYER_TELEPORT, ENTER_REGION, LEAVE_REGION |
| 实体 | ENTITY_DAMAGE, ENTITY_DEATH, ENTITY_SPAWN |
| 其他 | PLAYER_LEVEL_UP, PLAYER_RESPAWN, VILLAGER_TRADE, PLAYER_BOUNT |

### 2. Counter 节点

**外观**
- 矩形，绿色（#22c55e），带数字图标
- 120x60px

**可连出**：1 个
**可连入**：Trigger、Task

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 节点名称 |
| resetOn | enum | 重置时机（NONE / TASK_COMPLETE / DAILY / MANUAL） |

**行为**
- 每次连接到 Trigger 节点触发时自动 +1
- 计数器值存储在玩家任务进度中

### 3. Timer Control 节点

**外观**
- 矩形，黄色（#f59e0b），带时钟图标
- 120x60px

**可连出**：1 个
**可连入**：Task

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 节点名称 |
| timerType | enum | 定时类型（DELAY / COOLDOWN / INTERVAL） |

**timerType 动态字段**

| timerType | 显示字段 |
|------------|----------|
| DELAY | delaySeconds（延迟秒数） |
| COOLDOWN | cooldownSeconds（冷却秒数） |
| INTERVAL | intervalSeconds（周期秒数）、repeatCount（重复次数，-1 表示无限） |

### 4. State 节点

**外观**
- 矩形，灰色（#6b7280），带扳手图标
- 120x60px

**可连出**：1 个
**可连入**：任意节点

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 节点名称 |
| operation | enum | 操作类型 |

**operation 类型**

| operation | 说明 |
|-----------|------|
| COMPLETE_TASK | 强制完成任务 |
| FAIL_TASK | 强制任务失败 |
| RESET_TASK | 重置任务进度 |
| SET_PLAYER_STATE | 设置玩家状态值 |

### 5. Subtask/Group 节点

**外观**
- 虚线边框矩形，灰色（#9ca3af）
- 宽度自适应内容，高度 80px
- 仅用于视觉分组，不影响连线

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 分组名称 |

**行为**
- 不参与执行逻辑
- 仅用于视觉组织画布上的节点
- 连线规则不受影响

## 连线规则扩展

| 节点 | 可连出 | 可连入 |
|------|--------|--------|
| Start | Task、Condition、Event | 无 |
| Task | Task、Completion、Action、Timer | Start、Condition、Event、Branch |
| Completion | Action | Task |
| Condition | Branch | Start、Task |
| Branch | TRUE / FALSE | Condition |
| Action | Task、Completion | 任意节点 |
| Event | Task | Start、Task |
| Counter | Task | Trigger、Task |
| Timer | Task | Task |
| State | 任意节点 | 任意节点 |
| Subtask | 无 | 无（仅容器） |

## 数据模型

### EventData

```typescript
interface EventData {
  type: 'event'
  name: string
  eventTypes: string[]
}
```

### CounterData

```typescript
interface CounterData {
  type: 'counter'
  name: string
  resetOn: 'NONE' | 'TASK_COMPLETE' | 'DAILY' | 'MANUAL'
}
```

### TimerData

```typescript
interface TimerData {
  type: 'timer'
  name: string
  timerType: 'DELAY' | 'COOLDOWN' | 'INTERVAL'
  delaySeconds?: number       // DELAY
  cooldownSeconds?: number    // COOLDOWN
  intervalSeconds?: number    // INTERVAL
  repeatCount?: number        // INTERVAL, -1 = infinite
}
```

### StateData

```typescript
interface StateData {
  type: 'state'
  name: string
  operation: 'COMPLETE_TASK' | 'FAIL_TASK' | 'RESET_TASK' | 'SET_PLAYER_STATE'
}
```

### SubtaskData

```typescript
interface SubtaskData {
  type: 'subtask'
  name: string
}
```

## 组件设计

### 新增组件

| 组件 | 路径 | 说明 |
|------|------|------|
| EventNode.vue | components/editor/ | Event 节点渲染 |
| CounterNode.vue | components/editor/ | Counter 节点渲染 |
| TimerNode.vue | components/editor/ | Timer Control 节点渲染 |
| StateNode.vue | components/editor/ | State 节点渲染 |
| SubtaskNode.vue | components/editor/ | Subtask 容器渲染 |

### 修改现有组件

| 组件 | 修改内容 |
|------|----------|
| NodePropertiesPanel.vue | 添加 Event/Counter/Timer/State/Subtask 配置表单 |
| useQuestEditor.ts | 添加新节点类型和连线验证 |
| types/index.ts | 添加新节点类型定义 |
| EditorView.vue | 注册新节点类型和侧边栏项 |

## 后续扩展

无 - 三期完成后编辑器核心功能齐全
