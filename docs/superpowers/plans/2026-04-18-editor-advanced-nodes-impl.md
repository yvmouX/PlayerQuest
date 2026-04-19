# 第三期：高级节点实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为网页编辑器添加 Event、Counter、Timer、State、Subtask 五种高级节点

**Architecture:** 前端 Vue Flow 节点编辑器扩展，新增 5 种节点组件和属性面板，连线规则扩展支持事件监听、计数、定时、状态管理

**Tech Stack:** Vue 3, TypeScript, Vue Flow

**执行顺序:**
1. Task 1 (类型定义)
2. Task 2 (useQuestEditor - 新节点类型和连线验证)
3. Task 3 (EventNode.vue)
4. Task 4 (CounterNode.vue)
5. Task 5 (TimerNode.vue)
6. Task 6 (StateNode.vue)
7. Task 7 (SubtaskNode.vue)
8. Task 8 (NodePropertiesPanel - 更新配置表单)
9. Task 9 (EditorView - 注册节点和侧边栏)
10. Task 10 (集成测试)

---

## 文件结构

```
Frontend (task-editor-vue/):
├── src/
│   ├── components/editor/
│   │   ├── EventNode.vue      # 新增
│   │   ├── CounterNode.vue    # 新增
│   │   ├── TimerNode.vue      # 新增
│   │   ├── StateNode.vue      # 新增
│   │   └── SubtaskNode.vue    # 新增
│   ├── composables/
│   │   └── useQuestEditor.ts  # 修改
│   ├── types/
│   │   └── index.ts           # 修改
│   └── views/
│       └── EditorView.vue     # 修改
```

---

## Task 1: 扩展前端类型定义

**Files:**
- Modify: `task-editor-vue/src/types/index.ts`

- [ ] **Step 1: 添加新节点类型到 NodeType**

```typescript
export type NodeType = 'start' | 'task' | 'completion' | 'condition' | 'branch' | 'action' | 'event' | 'counter' | 'timer' | 'state' | 'subtask'
```

- [ ] **Step 2: 添加 EventType 枚举**

```typescript
export type EventType = 
  | 'LOGIN' | 'LOGOUT' | 'CHAT' | 'COMMAND' | 'JUMP' | 'SNEAK' | 'SPRINT' | 'DROP_ITEM' | 'PICKUP_ITEM'
  | 'PLAYER_KILL' | 'ENTITY_KILL' | 'PLAYER_DEATH' | 'PVP_KILL'
  | 'BLOCK_BREAK' | 'BLOCK_PLACE' | 'BLOCK_INTERACT'
  | 'ITEM_CRAFT' | 'ITEM_USE' | 'ITEM_CONSUME'
  | 'PLAYER_MOVE' | 'PLAYER_TELEPORT' | 'ENTER_REGION' | 'LEAVE_REGION'
  | 'ENTITY_DAMAGE' | 'ENTITY_DEATH' | 'ENTITY_SPAWN'
  | 'PLAYER_LEVEL_UP' | 'PLAYER_RESPAWN' | 'VILLAGER_TRADE' | 'PLAYER_BOUNT'
```

- [ ] **Step 3: 添加 EventData 接口**

```typescript
export interface EventData {
  type: 'event'
  name: string
  eventTypes: EventType[]
}
```

- [ ] **Step 4: 添加 CounterResetType 和 CounterData 接口**

```typescript
export type CounterResetType = 'NONE' | 'TASK_COMPLETE' | 'DAILY' | 'MANUAL'

export interface CounterData {
  type: 'counter'
  name: string
  resetOn: CounterResetType
}
```

- [ ] **Step 5: 添加 TimerType 和 TimerData 接口**

```typescript
export type TimerType = 'DELAY' | 'COOLDOWN' | 'INTERVAL'

export interface TimerData {
  type: 'timer'
  name: string
  timerType: TimerType
  delaySeconds?: number
  cooldownSeconds?: number
  intervalSeconds?: number
  repeatCount?: number
}
```

- [ ] **Step 6: 添加 StateOperation 和 StateData 接口**

```typescript
export type StateOperation = 'COMPLETE_TASK' | 'FAIL_TASK' | 'RESET_TASK' | 'SET_PLAYER_STATE'

export interface StateData {
  type: 'state'
  name: string
  operation: StateOperation
}
```

- [ ] **Step 7: 添加 SubtaskData 接口**

```typescript
export interface SubtaskData {
  type: 'subtask'
  name: string
}
```

- [ ] **Step 8: 更新 EditorNodeData 联合类型**

```typescript
export type EditorNodeData = StartNodeData | TaskNodeData | CompletionNodeData | ConditionData | BranchData | ActionData | EventData | CounterData | TimerData | StateData | SubtaskData
```

- [ ] **Step 9: 提交**

```bash
git add task-editor-vue/src/types/index.ts
git commit -m "feat(editor): add Event, Counter, Timer, State, Subtask types for phase 3"
```

---

## Task 2: 更新 useQuestEditor 添加新节点类型和连线验证

**Files:**
- Modify: `task-editor-vue/src/composables/useQuestEditor.ts`

- [ ] **Step 1: 更新 nodeType 并导出 editorNodes**

确保 `editorNodes` 在模块级别可访问（如果第二期已完成则跳过）

- [ ] **Step 2: 更新 addEdge 函数的连线验证**

新增规则：
- Event → Task
- Counter → Task
- Timer → Task
- State → 任意节点（Task、Completion）
- Subtask → 无（仅容器）

```typescript
// 连线规则验证扩展
const validConnections: Record<string, string[]> = {
  start: ['task', 'condition', 'event'],
  task: ['task', 'completion', 'action', 'timer'],
  completion: ['action'],
  condition: ['branch'],
  branch: ['task', 'completion'],
  action: ['task', 'completion'],
  event: ['task'],
  counter: ['task'],
  timer: ['task'],
  state: ['task', 'completion']
  // subtask: [] - 无连线
}
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/composables/useQuestEditor.ts
git commit -m "feat(editor): extend edge validation for Event, Counter, Timer, State nodes"
```

---

## Task 3: 创建 EventNode.vue

**Files:**
- Create: `task-editor-vue/src/components/editor/EventNode.vue`
- Modify: `task-editor-vue/src/views/EditorView.vue`

- [ ] **Step 1: 创建 EventNode.vue**

```vue
<template>
  <div class="event-node">
    <div class="node-circle">
      <span class="node-icon">⚡</span>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import {Handle, Position} from '@vue-flow/core'

defineProps<{
  data: {
    name: string
    eventTypes?: string[]
  }
}>()
</script>

<style scoped>
.event-node {
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.node-circle {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: #3b82f6;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}
.node-icon {
  font-size: 1.2rem;
}
</style>
```

- [ ] **Step 2: 在 EditorView.vue 中注册 EventNode**

```typescript
import EventNode from '../components/editor/EventNode.vue'
```

```vue
<template #node-event="{ data }">
  <EventNode :data="data" />
</template>
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/components/editor/EventNode.vue task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): add EventNode component"
```

---

## Task 4: 创建 CounterNode.vue

**Files:**
- Create: `task-editor-vue/src/components/editor/CounterNode.vue`
- Modify: `task-editor-vue/src/views/EditorView.vue`

- [ ] **Step 1: 创建 CounterNode.vue**

```vue
<template>
  <div class="counter-node">
    <div class="node-header">
      <span class="node-icon">🔢</span>
      <span class="node-type">计数器</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Counter' }}</span>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import {Handle, Position} from '@vue-flow/core'

defineProps<{
  data: {
    name: string
    resetOn?: string
  }
}>()
</script>

<style scoped>
.counter-node {
  width: 120px;
  height: 60px;
  background: white;
  border: 2px solid #22c55e;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(34, 197, 94, 0.2);
}
.node-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: #dcfce7;
  border-bottom: 1px solid #bbf7d0;
}
.node-icon { font-size: 1rem; }
.node-type {
  font-size: 0.7rem;
  color: #166534;
}
.node-body {
  padding: 0.4rem 0.75rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #15803d;
}
</style>
```

- [ ] **Step 2: 在 EditorView.vue 中注册 CounterNode**

```typescript
import CounterNode from '../components/editor/CounterNode.vue'
```

```vue
<template #node-counter="{ data }">
  <CounterNode :data="data" />
</template>
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/components/editor/CounterNode.vue task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): add CounterNode component"
```

---

## Task 5: 创建 TimerNode.vue

**Files:**
- Create: `task-editor-vue/src/components/editor/TimerNode.vue`
- Modify: `task-editor-vue/src/views/EditorView.vue`

- [ ] **Step 1: 创建 TimerNode.vue**

```vue
<template>
  <div class="timer-node">
    <div class="node-header">
      <span class="node-icon">⏱️</span>
      <span class="node-type">{{ timerTypeLabel }}</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Timer' }}</span>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {Handle, Position} from '@vue-flow/core'

const props = defineProps<{
  data: {
    name: string
    timerType?: string
  }
}>()

const timerTypeLabel = computed(() => {
  const labels: Record<string, string> = {
    DELAY: '延迟',
    COOLDOWN: '冷却',
    INTERVAL: '周期'
  }
  return labels[props.data.timerType] || '定时'
})
</script>

<style scoped>
.timer-node {
  width: 120px;
  height: 60px;
  background: white;
  border: 2px solid #f59e0b;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.2);
}
.node-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: #fef3c7;
  border-bottom: 1px solid #fde68a;
}
.node-icon { font-size: 1rem; }
.node-type {
  font-size: 0.7rem;
  color: #92400e;
}
.node-body {
  padding: 0.4rem 0.75rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #78350f;
}
</style>
```

- [ ] **Step 2: 在 EditorView.vue 中注册 TimerNode**

```typescript
import TimerNode from '../components/editor/TimerNode.vue'
```

```vue
<template #node-timer="{ data }">
  <TimerNode :data="data" />
</template>
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/components/editor/TimerNode.vue task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): add TimerNode component"
```

---

## Task 6: 创建 StateNode.vue

**Files:**
- Create: `task-editor-vue/src/components/editor/StateNode.vue`
- Modify: `task-editor-vue/src/views/EditorView.vue`

- [ ] **Step 1: 创建 StateNode.vue**

```vue
<template>
  <div class="state-node">
    <div class="node-header">
      <span class="node-icon">🔧</span>
      <span class="node-type">{{ operationLabel }}</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'State' }}</span>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {Handle, Position} from '@vue-flow/core'

const props = defineProps<{
  data: {
    name: string
    operation?: string
  }
}>()

const operationLabel = computed(() => {
  const labels: Record<string, string> = {
    COMPLETE_TASK: '完成',
    FAIL_TASK: '失败',
    RESET_TASK: '重置',
    SET_PLAYER_STATE: '设置状态'
  }
  return labels[props.data.operation] || '状态'
})
</script>

<style scoped>
.state-node {
  width: 120px;
  height: 60px;
  background: white;
  border: 2px solid #6b7280;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(107, 114, 128, 0.2);
}
.node-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: #f3f4f6;
  border-bottom: 1px solid #e5e7eb;
}
.node-icon { font-size: 1rem; }
.node-type {
  font-size: 0.7rem;
  color: #374151;
}
.node-body {
  padding: 0.4rem 0.75rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #4b5563;
}
</style>
```

- [ ] **Step 2: 在 EditorView.vue 中注册 StateNode**

```typescript
import StateNode from '../components/editor/StateNode.vue'
```

```vue
<template #node-state="{ data }">
  <StateNode :data="data" />
</template>
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/components/editor/StateNode.vue task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): add StateNode component"
```

---

## Task 7: 创建 SubtaskNode.vue

**Files:**
- Create: `task-editor-vue/src/components/editor/SubtaskNode.vue`
- Modify: `task-editor-vue/src/views/EditorView.vue`

- [ ] **Step 1: 创建 SubtaskNode.vue**

```vue
<template>
  <div class="subtask-node">
    <div class="node-header">
      <span class="node-label">SUBTASK</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Subtask Group' }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  data: {
    name: string
  }
}>()
</script>

<style scoped>
.subtask-node {
  width: 200px;
  min-height: 80px;
  background: rgba(249, 250, 251, 0.5);
  border: 2px dashed #9ca3af;
  border-radius: 8px;
  overflow: hidden;
}
.node-header {
  padding: 0.5rem;
  background: #f9fafb;
  border-bottom: 1px dashed #d1d5db;
}
.node-label {
  font-size: 0.7rem;
  color: #6b7280;
  font-weight: bold;
}
.node-body {
  padding: 0.5rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #374151;
}
</style>
```

- [ ] **Step 2: 在 EditorView.vue 中注册 SubtaskNode**

```typescript
import SubtaskNode from '../components/editor/SubtaskNode.vue'
```

```vue
<template #node-subtask="{ data }">
  <SubtaskNode :data="data" />
</template>
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/components/editor/SubtaskNode.vue task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): add SubtaskNode component"
```

---

## Task 8: 更新 NodePropertiesPanel 支持新节点类型

**Files:**
- Modify: `task-editor-vue/src/components/editor/NodePropertiesPanel.vue`

- [ ] **Step 1: 添加 Event 配置表单**

```vue
<!-- Event Node -->
<template v-else-if="selectedNode.type === 'event'">
  <div class="form-group">
    <label>名称</label>
    <input v-model="editedNode.name" placeholder="事件节点名称" />
  </div>
  <div class="form-group">
    <label>监听事件</label>
    <select v-model="editedNode.eventTypes" multiple>
      <optgroup label="玩家动作">
        <option value="LOGIN">登录</option>
        <option value="LOGOUT">登出</option>
        <option value="CHAT">聊天</option>
        <option value="COMMAND">执行命令</option>
        <option value="JUMP">跳跃</option>
        <option value="SNEAK">蹲下</option>
        <option value="SPRINT">疾跑</option>
        <option value="DROP_ITEM">丢弃物品</option>
        <option value="PICKUP_ITEM">拾取物品</option>
      </optgroup>
      <optgroup label="战斗">
        <option value="PLAYER_KILL">玩家击杀</option>
        <option value="ENTITY_KILL">实体击杀</option>
        <option value="PLAYER_DEATH">玩家死亡</option>
        <option value="PVP_KILL">PVP击杀</option>
      </optgroup>
      <optgroup label="方块交互">
        <option value="BLOCK_BREAK">破坏方块</option>
        <option value="BLOCK_PLACE">放置方块</option>
        <option value="BLOCK_INTERACT">交互方块</option>
      </optgroup>
      <optgroup label="物品">
        <option value="ITEM_CRAFT">合成物品</option>
        <option value="ITEM_USE">使用物品</option>
        <option value="ITEM_CONSUME">消耗物品</option>
      </optgroup>
      <optgroup label="移动">
        <option value="PLAYER_MOVE">移动</option>
        <option value="PLAYER_TELEPORT">传送</option>
        <option value="ENTER_REGION">进入区域</option>
        <option value="LEAVE_REGION">离开区域</option>
      </optgroup>
      <optgroup label="实体">
        <option value="ENTITY_DAMAGE">实体受伤</option>
        <option value="ENTITY_DEATH">实体死亡</option>
        <option value="ENTITY_SPAWN">实体生成</option>
      </optgroup>
      <optgroup label="其他">
        <option value="PLAYER_LEVEL_UP">升级</option>
        <option value="PLAYER_RESPAWN">重生</option>
        <option value="VILLAGER_TRADE">村民交易</option>
        <option value="PLAYER_BOUNT">悬赏</option>
      </optgroup>
    </select>
  </div>
</template>
```

- [ ] **Step 2: 添加 Counter 配置表单**

```vue
<!-- Counter Node -->
<template v-else-if="selectedNode.type === 'counter'">
  <div class="form-group">
    <label>名称</label>
    <input v-model="editedNode.name" placeholder="计数器名称" />
  </div>
  <div class="form-group">
    <label>重置时机</label>
    <select v-model="editedNode.resetOn">
      <option value="NONE">不重置</option>
      <option value="TASK_COMPLETE">任务完成时</option>
      <option value="DAILY">每日重置</option>
      <option value="MANUAL">手动重置</option>
    </select>
  </div>
</template>
```

- [ ] **Step 3: 添加 Timer 配置表单**

```vue
<!-- Timer Node -->
<template v-else-if="selectedNode.type === 'timer'">
  <div class="form-group">
    <label>名称</label>
    <input v-model="editedNode.name" placeholder="定时器名称" />
  </div>
  <div class="form-group">
    <label>定时类型</label>
    <select v-model="editedNode.timerType">
      <option value="DELAY">延迟执行</option>
      <option value="COOLDOWN">冷却时间</option>
      <option value="INTERVAL">周期执行</option>
    </select>
  </div>
  <template v-if="editedNode.timerType === 'DELAY'">
    <div class="form-group">
      <label>延迟秒数</label>
      <input v-model.number="editedNode.delaySeconds" type="number" placeholder="60" />
    </div>
  </template>
  <template v-else-if="editedNode.timerType === 'COOLDOWN'">
    <div class="form-group">
      <label>冷却秒数</label>
      <input v-model.number="editedNode.cooldownSeconds" type="number" placeholder="300" />
    </div>
  </template>
  <template v-else-if="editedNode.timerType === 'INTERVAL'">
    <div class="form-group">
      <label>周期秒数</label>
      <input v-model.number="editedNode.intervalSeconds" type="number" placeholder="60" />
    </div>
    <div class="form-group">
      <label>重复次数 (-1=无限)</label>
      <input v-model.number="editedNode.repeatCount" type="number" placeholder="-1" />
    </div>
  </template>
</template>
```

- [ ] **Step 4: 添加 State 配置表单**

```vue
<!-- State Node -->
<template v-else-if="selectedNode.type === 'state'">
  <div class="form-group">
    <label>名称</label>
    <input v-model="editedNode.name" placeholder="状态节点名称" />
  </div>
  <div class="form-group">
    <label>操作类型</label>
    <select v-model="editedNode.operation">
      <option value="COMPLETE_TASK">强制完成任务</option>
      <option value="FAIL_TASK">强制任务失败</option>
      <option value="RESET_TASK">重置任务进度</option>
      <option value="SET_PLAYER_STATE">设置玩家状态</option>
    </select>
  </div>
</template>
```

- [ ] **Step 5: 添加 Subtask 配置表单**

```vue
<!-- Subtask Node -->
<template v-else-if="selectedNode.type === 'subtask'">
  <div class="form-group">
    <label>分组名称</label>
    <input v-model="editedNode.name" placeholder="子任务分组" />
  </div>
</template>
```

- [ ] **Step 6: 提交**

```bash
git add task-editor-vue/src/components/editor/NodePropertiesPanel.vue
git commit -m "feat(editor): add Event, Counter, Timer, State, Subtask config to NodePropertiesPanel"
```

---

## Task 9: 更新 EditorView.vue 注册节点和侧边栏

**Files:**
- Modify: `task-editor-vue/src/views/EditorView.vue`

- [ ] **Step 1: 添加新节点到侧边栏**

在 `<aside class="sidebar">` 中添加:

```vue
<h3>高级节点</h3>
<div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'event')">
  <span>⚡ Event</span>
</div>
<div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'counter')">
  <span>🔢 Counter</span>
</div>
<div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'timer')">
  <span>⏱️ Timer</span>
</div>
<div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'state')">
  <span>🔧 State</span>
</div>
<div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'subtask')">
  <span>📁 Subtask</span>
</div>
```

- [ ] **Step 2: 注册新节点组件**

确保以下导入和模板已添加（第二期已完成则跳过）:
```typescript
import EventNode from '../components/editor/EventNode.vue'
import CounterNode from '../components/editor/CounterNode.vue'
import TimerNode from '../components/editor/TimerNode.vue'
import StateNode from '../components/editor/StateNode.vue'
import SubtaskNode from '../components/editor/SubtaskNode.vue'
```

```vue
<template #node-event="{ data }">
  <EventNode :data="data" />
</template>
<template #node-counter="{ data }">
  <CounterNode :data="data" />
</template>
<template #node-timer="{ data }">
  <TimerNode :data="data" />
</template>
<template #node-state="{ data }">
  <StateNode :data="data" />
</template>
<template #node-subtask="{ data }">
  <SubtaskNode :data="data" />
</template>
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): register Event, Counter, Timer, State, Subtask nodes"
```

---

## Task 10: 集成测试

- [ ] **Step 1: 构建前端**

```bash
cd task-editor-vue && npm run build
```

- [ ] **Step 2: 验证构建产物**

检查 `dist/` 目录包含所有新组件

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/ && git commit -m "feat(editor): complete phase 3 advanced nodes - Event, Counter, Timer, State, Subtask"
```
