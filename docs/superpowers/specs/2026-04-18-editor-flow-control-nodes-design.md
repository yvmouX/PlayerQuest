# 网页编辑器 - 第二期：流程控制节点设计

## 概述

为 PlayerTaskX 网页编辑器引入三种流程控制节点（Condition、Branch、Action），支持条件判断、分支流转和动作执行。

## 节点类型

### 1. Condition 节点

**外观**
- 菱形，紫色（#8b5cf6），60x60px
- 顶部显示 "COND" 文字

**可连出**：Branch
**可连入**：Start、Task

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 节点名称 |
| conditions | array | 条件列表（与/或组合） |

**Condition 条件类型**

| 条件类型 | 参数 | 说明 |
|----------|------|------|
| PERMISSION | permission: string | 权限检查 |
| HAS_ITEM | itemId: string, count: number | 物品持有检查 |
| KILL_MOB | mobType: string, count: number | 击杀计数 |
| COLLECT_ITEM | itemId: string, count: number | 收集计数 |
| PLAYER_LEVEL | level: number, operator: '>=' / '<=' / '==' | 玩家等级 |
| TIME_RANGE | startHour: number, endHour: number | 时间范围 |
| IN_REGION | regionName: string | 坐标区域 |

**条件组合逻辑**
- 同一条件类型内：OR 关系
- 不同条件类型间：AND 关系
- 例：`权限 A AND (物品 X OR 物品 Y) AND 等级 >= 5`

### 2. Branch 节点

**外观**
- 六边形，紫色（#8b5cf6），60x60px
- 顶部显示 "BRANCH" 文字

**可连出**：TRUE 分支 / FALSE 分支
**可连入**：Condition

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 节点名称 |
| linkedCondition | string | 关联的 Condition 节点 ID |

**连线规则**
- 固定两个出口：TRUE（绿色）、FALSE（红色）
- TRUE 连向满足条件后的下一个节点
- FALSE 连向不满足条件时的处理节点

### 3. Action 节点

**外观**
- 矩形，橙色（#f59e0b），180x60px
- 顶部显示动作类型图标

**可连出**：1 个
**可连入**：任意节点（Task、Completion、Branch 等）

**配置属性**
| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 节点名称 |
| actionType | enum | 动作类型 |
| actionParams | object | 动作参数 |

**Action 类型**

| 动作类型 | 参数 | 说明 |
|----------|------|------|
| GIVE_ITEM | itemId: string, count: number | 发放物品 |
| TAKE_ITEM | itemId: string, count: number | 扣除物品 |
| GIVE_MONEY | amount: number | 发放货币 |
| TAKE_MONEY | amount: number | 扣除货币 |
| GIVE_XP | amount: number | 发放经验 |
| SEND_MESSAGE | message: string | 发送消息 |
| BROADCAST | message: string | 全服广播 |
| EXECUTE_COMMAND | command: string | 执行命令 |
| PLAY_SOUND | sound: string | 播放音效 |

## 连线规则扩展

| 节点 | 可连出 | 可连入 |
|------|--------|--------|
| Start | Task、Condition | 无 |
| Task | Task、Completion、Action | Start、Branch |
| Completion | Action | Task |
| Condition | Branch | Start、Task |
| Branch | TRUE / FALSE | Condition |
| Action | Task、Completion | 任意节点 |

**约束**
- 禁止形成环路
- TRUE/FALSE 分支必须同时存在

## 数据模型

### ConditionData

```typescript
interface ConditionData {
  type: 'condition'
  name: string
  conditions: ConditionItem[]
}

interface ConditionItem {
  conditionType: 'PERMISSION' | 'HAS_ITEM' | 'KILL_MOB' | 'COLLECT_ITEM' | 'PLAYER_LEVEL' | 'TIME_RANGE' | 'IN_REGION'
  params: Record<string, any>
}
```

### BranchData

```typescript
interface BranchData {
  type: 'branch'
  name: string
  linkedConditionId: string
}
```

### ActionData

```typescript
interface ActionData {
  type: 'action'
  name: string
  actionType: 'GIVE_ITEM' | 'TAKE_ITEM' | 'GIVE_MONEY' | 'TAKE_MONEY' | 'GIVE_XP' | 'SEND_MESSAGE' | 'BROADCAST' | 'EXECUTE_COMMAND' | 'PLAY_SOUND'
  actionParams: Record<string, any>
}
```

## 组件设计

### 新增组件

| 组件 | 路径 | 说明 |
|------|------|------|
| ConditionNode.vue | components/editor/ | Condition 节点渲染 |
| BranchNode.vue | components/editor/ | Branch 节点渲染（双出口） |
| ActionNode.vue | components/editor/ | Action 节点渲染 |

### 修改现有组件

| 组件 | 修改内容 |
|------|----------|
| NodePropertiesPanel.vue | 添加 Condition/Branch/Action 配置表单 |
| useQuestEditor.ts | 添加新节点类型和连线验证 |
| types/index.ts | 添加新节点类型定义 |

## 节点创建机制

从侧边栏创建：
- 侧边栏已有 "Start 节点" 选项
- 添加 "Condition 节点"、"Branch 节点"、"Action 节点" 选项
- 拖入画布后自动设置 `nodeType` 属性

## 后续扩展

第三期：Event/Trigger、Counter、Timer、State、Subtask 节点
