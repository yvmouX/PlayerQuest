# 网页编辑器 - 核心节点系统设计

## 概述

为 PlayerTaskX 网页编辑器引入三种核心节点类型（Start、Task、Completion），建立节点系统的基座，支持后续扩展（Condition、Branch、Action 等）。

## 节点类型

### 1. Start 节点

**外观**
- 圆形，绿色（#22c55e），60x60px
- 中间显示图标或文字

**可连出**：Task、Event
**可连入**：无（起点）

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 节点名称（显示用） |
| description | string | 描述 |
| startCondition | object | 起始条件（预留） |

### 2. Task 节点

**外观**
- 卡片样式，180x80px
- 白色背景，蓝色边框
- 顶部显示类型标签

**可连出**：Task、Completion
**可连入**：Start、Event

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 唯一标识 |
| name | Record<string,string> | 多语言名称 |
| description | Record<string,string> | 多语言描述 |
| taskType | enum | CYCLE / TIMER / FOREVER / LIMIT |
| objectives | array | 目标列表 |
| rewards | array | 奖励列表 |

**taskType 动态字段**

| taskType | 显示字段 |
|----------|----------|
| CYCLE | resetInterval（重置间隔，秒） |
| TIMER | resetInterval（重置时间间隔，秒） |
| FOREVER | 无特殊字段 |
| LIMIT | timeLimit（限时，秒）、expiredAction（超时动作） |

### 3. Completion 节点

**外观**
- 菱形，红色（#ef4444），60x60px
- 可旋转 45° 的方形

**可连出**：无（终点）
**可连入**：Task

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 节点名称 |
| rewards | array | 奖励组 |
| callbackMessage | string | 完成后回调消息 |

## 连线规则

| 节点 | 可连出 | 可连入 |
|------|--------|--------|
| Start | Task | 无 |
| Task | Task、Completion | Start |
| Completion | 无 | Task |

**约束**
- 禁止形成环路
- 禁止倒流（Completion 不可连出）
- 注：Event 节点在第三期实现，当前版本暂不连接 Event

## 数据模型

### NodeDefinition

```typescript
interface NodeDefinition {
  id: string
  type: 'start' | 'task' | 'completion'
  position: { x: number, y: number }
  data: StartData | TaskData | CompletionData
}
```

### StartData

```typescript
interface StartData {
  name: string
  description?: string
  startCondition?: object  // 预留
}
```

### TaskData

```typescript
interface TaskData {
  id: string
  name: Record<string, string>
  description: Record<string, string>
  taskType: 'CYCLE' | 'TIMER' | 'FOREVER' | 'LIMIT'
  objectives: QuestObjective[]
  rewards: QuestReward[]
  // 动态字段
  resetInterval?: number      // CYCLE / TIMER
  timeLimit?: number         // LIMIT
  expiredAction?: string     // LIMIT
}
```

### CompletionData

```typescript
interface CompletionData {
  name: string
  rewards: QuestReward[]
  callbackMessage?: string
}
```

## 组件设计

### 新增组件

| 组件 | 路径 | 说明 |
|------|------|------|
| StartNode.vue | components/editor/ | Start 节点渲染 |
| TaskNode.vue | components/editor/ | Task 节点渲染（复用现有 QuestNode） |
| CompletionNode.vue | components/editor/ | Completion 节点渲染 |
| NodePropertiesPanel.vue | components/editor/ | 统一属性面板 |

### 修改现有组件

| 组件 | 修改内容 |
|------|----------|
| QuestNode.vue | 扩展支持 taskType 动态字段显示 |
| PropertiesPanel.vue | 改为 NodePropertiesPanel，支持 3 种节点配置 |
| useQuestEditor.ts | 添加节点类型验证、连线规则检查 |

## 实现步骤

1. 创建 StartNode.vue、CompletionNode.vue
2. 重构 PropertiesPanel.vue 为 NodePropertiesPanel
3. 扩展 types/index.ts 添加新类型
4. 修改 useQuestEditor 添加连线规则验证
5. 更新 EditorView.vue 支持新节点类型注册
6. 后端 TaskDefinition 添加 taskType 字段支持

## 后续扩展

第二期：Condition、Branch 节点
第三期：Action、Event/Trigger 节点
第四期：Counter、Timer Control、State、Subtask 节点
