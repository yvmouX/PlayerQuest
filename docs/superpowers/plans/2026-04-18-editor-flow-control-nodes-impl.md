# 第二期：流程控制节点实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为网页编辑器添加 Condition、Branch、Action 三种流程控制节点

**Architecture:** 前端 Vue Flow 节点编辑器扩展，新增 3 种节点组件和属性面板，连线规则扩展支持条件分支流转

**Tech Stack:** Vue 3, TypeScript, Vue Flow

---

## 文件结构

```
Frontend (task-editor-vue/):
├── src/
│   ├── components/editor/
│   │   ├── ConditionNode.vue    # 新增
│   │   ├── BranchNode.vue       # 新增
│   │   ├── ActionNode.vue       # 新增
│   │   └── NodePropertiesPanel.vue # 修改
│   ├── composables/
│   │   └── useQuestEditor.ts    # 修改
│   ├── types/
│   │   └── index.ts             # 修改
│   └── views/
│       └── EditorView.vue       # 修改
```

---

## Task 1: 扩展前端类型定义

**Files:**
- Modify: `task-editor-vue/src/types/index.ts`

- [ ] **Step 1: 添加新节点类型到 NodeType**

```typescript
export type NodeType = 'start' | 'task' | 'completion' | 'condition' | 'branch' | 'action'
```

- [ ] **Step 2: 添加 ConditionItem 类型**

```typescript
export type ConditionType = 'PERMISSION' | 'HAS_ITEM' | 'KILL_MOB' | 'COLLECT_ITEM' | 'PLAYER_LEVEL' | 'TIME_RANGE' | 'IN_REGION'

export interface ConditionItem {
  conditionType: ConditionType
  params: Record<string, any>
}
```

- [ ] **Step 3: 添加 ConditionData 接口**

```typescript
export interface ConditionData {
  type: 'condition'
  name: string
  conditions: ConditionItem[]
}
```

- [ ] **Step 4: 添加 BranchData 接口**

```typescript
export interface BranchData {
  type: 'branch'
  name: string
  linkedConditionId: string
}
```

- [ ] **Step 5: 添加 ActionType 和 ActionData 接口**

```typescript
export type ActionType = 'GIVE_ITEM' | 'TAKE_ITEM' | 'GIVE_MONEY' | 'TAKE_MONEY' | 'GIVE_XP' | 'SEND_MESSAGE' | 'BROADCAST' | 'EXECUTE_COMMAND' | 'PLAY_SOUND'

export interface ActionData {
  type: 'action'
  name: string
  actionType: ActionType
  actionParams: Record<string, any>
}
```

- [ ] **Step 6: 更新 EditorNodeData 联合类型**

```typescript
export type EditorNodeData = StartNodeData | TaskNodeData | CompletionNodeData | ConditionData | BranchData | ActionData
```

- [ ] **Step 7: 提交**

```bash
git add task-editor-vue/src/types/index.ts
git commit -m "feat(editor): add Condition, Branch, Action types for phase 2"
```

---

## Task 2: 创建 ConditionNode.vue

**Files:**
- Create: `task-editor-vue/src/components/editor/ConditionNode.vue`
- Modify: `task-editor-vue/src/views/EditorView.vue`（注册节点类型）

- [ ] **Step 1: 创建 ConditionNode.vue**

```vue
<template>
  <div class="condition-node">
    <div class="node-diamond">
      <span class="node-icon">?</span>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Bottom" />
  </div>
</template>

<script setup lang="ts">
import {Handle, Position} from '@vue-flow/core'

defineProps<{
  data: {
    name: string
    conditions?: any[]
  }
}>()
</script>

<style scoped>
.condition-node {
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.node-diamond {
  width: 50px;
  height: 50px;
  background: #8b5cf6;
  transform: rotate(45deg);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(139, 92, 246, 0.3);
}
.node-icon {
  color: white;
  font-size: 1.2rem;
  font-weight: bold;
  transform: rotate(-45deg);
}
</style>
```

- [ ] **Step 2: 在 EditorView.vue 中导入并注册 ConditionNode**

在 import 语句中添加:
```typescript
import ConditionNode from '../components/editor/ConditionNode.vue'
```

在 `<VueFlow>` 组件中添加:
```vue
<template #node-condition="{ data }">
  <ConditionNode :data="data" />
</template>
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/components/editor/ConditionNode.vue task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): add ConditionNode component"
```

---

## Task 3: 创建 BranchNode.vue

**Files:**
- Create: `task-editor-vue/src/components/editor/BranchNode.vue`
- Modify: `task-editor-vue/src/views/EditorView.vue`

- [ ] **Step 1: 创建 BranchNode.vue**

```vue
<template>
  <div class="branch-node">
    <div class="node-hexagon">
      <span class="node-label">BRANCH</span>
    </div>
    <div class="branch-labels">
      <span class="branch-true">TRUE</span>
      <span class="branch-false">FALSE</span>
    </div>
    <Handle type="target" :position="Position.Top" style="top: -10px" />
    <Handle id="true" type="source" :position="Position.Bottom" style="left: 30%" />
    <Handle id="false" type="source" :position="Position.Bottom" style="left: 70%" />
  </div>
</template>

<script setup lang="ts">
import {Handle, Position} from '@vue-flow/core'

defineProps<{
  data: {
    name: string
    linkedConditionId?: string
  }
}>()
</script>

<style scoped>
.branch-node {
  width: 100px;
  height: 80px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
}
.node-hexagon {
  width: 60px;
  height: 35px;
  background: #8b5cf6;
  clip-path: polygon(25% 0%, 75% 0%, 100% 50%, 75% 100%, 25% 100%, 0% 50%);
  display: flex;
  align-items: center;
  justify-content: center;
}
.node-label {
  color: white;
  font-size: 0.6rem;
  font-weight: bold;
}
.branch-labels {
  display: flex;
  justify-content: space-between;
  width: 80px;
  margin-top: 5px;
  font-size: 0.6rem;
  font-weight: bold;
}
.branch-true { color: #22c55e; }
.branch-false { color: #ef4444; }
</style>
```

- [ ] **Step 2: 在 EditorView.vue 中注册 BranchNode**

```typescript
import BranchNode from '../components/editor/BranchNode.vue'
```

```vue
<template #node-branch="{ data }">
  <BranchNode :data="data" />
</template>
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/components/editor/BranchNode.vue task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): add BranchNode component with dual outputs"
```

---

## Task 4: 创建 ActionNode.vue

**Files:**
- Create: `task-editor-vue/src/components/editor/ActionNode.vue`
- Modify: `task-editor-vue/src/views/EditorView.vue`

- [ ] **Step 1: 创建 ActionNode.vue**

```vue
<template>
  <div class="action-node">
    <div class="node-header">
      <span class="node-icon">{{ actionIcon }}</span>
      <span class="node-type">{{ actionLabel }}</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Action' }}</span>
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

const actionIcon = computed(() => {
  const icons: Record<string, string> = {
    GIVE_ITEM: '📦',
    TAKE_ITEM: '📤',
    GIVE_MONEY: '💰',
    TAKE_MONEY: '💸',
    GIVE_XP: '⭐',
    SEND_MESSAGE: '💬',
    BROADCAST: '📢',
    EXECUTE_COMMAND: '⚡',
    PLAY_SOUND: '🎵'
  }
  return icons[props.data.actionType] || '⚙️'
})

const actionLabel = computed(() => {
  const labels: Record<string, string> = {
    GIVE_ITEM: '发放物品',
    TAKE_ITEM: '扣除物品',
    GIVE_MONEY: '发放货币',
    TAKE_MONEY: '扣除货币',
    GIVE_XP: '发放经验',
    SEND_MESSAGE: '发送消息',
    BROADCAST: '全服广播',
    EXECUTE_COMMAND: '执行命令',
    PLAY_SOUND: '播放音效'
  }
  return labels[props.data.actionType] || props.data.actionType
})
</script>

<style scoped>
.action-node {
  min-width: 120px;
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

- [ ] **Step 2: 在 EditorView.vue 中注册 ActionNode**

```typescript
import ActionNode from '../components/editor/ActionNode.vue'
```

```vue
<template #node-action="{ data }">
  <ActionNode :data="data" />
</template>
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/components/editor/ActionNode.vue task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): add ActionNode component"
```

---

## Task 5: 更新 useQuestEditor 添加新节点类型和连线验证

**Files:**
- Modify: `task-editor-vue/src/composables/useQuestEditor.ts`

- [ ] **Step 1: 更新 QuestNodeData 的 nodeType**

```typescript
export interface QuestNodeData {
  id: string
  quest: Quest
  position: { x: number; y: number }
  nodeType: 'start' | 'task' | 'completion' | 'condition' | 'branch' | 'action'  // 扩展
}
```

- [ ] **Step 2: 更新 addEdge 函数添加新连线规则**

新规则：
- Start → Task / Condition
- Task → Task / Completion / Action
- Completion → Action
- Condition → Branch
- Branch → TRUE(分支A) / FALSE(分支B)
- Action → Task / Completion

替换 `addEdge` 函数:

```typescript
function addEdge(source: string, target: string, label?: string) {
  if (source === target) return
  
  const sourceNode = nodes.value.find(n => n.id === source)
  const targetNode = nodes.value.find(n => n.id === target)
  
  if (!sourceNode || !targetNode) return
  
  const sourceType = sourceNode.nodeType
  const targetType = targetNode.nodeType
  
  // 连线规则验证
  const validConnections: Record<string, string[]> = {
    start: ['task', 'condition'],
    task: ['task', 'completion', 'action'],
    completion: ['action'],
    condition: ['branch'],
    branch: ['task'],  // branch 有 TRUE/FALSE 两个出口，实际由前端控制
    action: ['task', 'completion']
  }
  
  if (!validConnections[sourceType]?.includes(targetType)) {
    console.warn(`Cannot connect: ${sourceType} cannot connect to ${targetType}`)
    return
  }
  
  // 环路检测
  const visited = new Set<string>()
  const stack = [source]
  while (stack.length > 0) {
    const current = stack.pop()!
    if (current === target) continue
    if (visited.has(current)) continue
    visited.add(current)
    edges.value
      .filter(e => e.source === current)
      .forEach(e => stack.push(e.target))
  }
  
  if (visited.has(target)) {
    console.warn('Cannot create edge: would create a cycle')
    return
  }
  
  const id = `${source}-${target}`
  if (edges.value.some(e => e.id === id)) return
  edges.value.push({ id, source, target, label })
}
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/composables/useQuestEditor.ts
git commit -m "feat(editor): extend edge validation for Condition, Branch, Action nodes"
```

---

## Task 6: 更新 NodePropertiesPanel 支持新节点类型

**Files:**
- Modify: `task-editor-vue/src/components/editor/NodePropertiesPanel.vue`

- [ ] **Step 1: 添加 Condition 配置表单**

在模板的 `<template v-else-if="selectedNode.type === 'task'">` 之后添加:

```vue
<!-- Condition Node -->
<template v-else-if="selectedNode.type === 'condition'">
  <div class="form-group">
    <label>名称</label>
    <input v-model="editedNode.name" placeholder="条件节点名称" />
  </div>

  <div class="section-divider">
    <h4>条件 ({{ editedNode.conditions?.length || 0 }})</h4>
    <button @click="addCondition">+ 添加</button>
  </div>
  <div class="conditions-list">
    <div v-for="(cond, index) in editedNode.conditions" :key="index" class="condition-item">
      <select v-model="cond.conditionType" @change="updateConditionParams(cond)">
        <option value="PERMISSION">权限检查</option>
        <option value="HAS_ITEM">物品持有</option>
        <option value="KILL_MOB">击杀计数</option>
        <option value="COLLECT_ITEM">收集计数</option>
        <option value="PLAYER_LEVEL">玩家等级</option>
        <option value="TIME_RANGE">时间范围</option>
        <option value="IN_REGION">坐标区域</option>
      </select>
      <button @click="removeCondition(index)">×</button>
    </div>
  </div>
</template>

<!-- Branch Node -->
<template v-else-if="selectedNode.type === 'branch'">
  <div class="form-group">
    <label>名称</label>
    <input v-model="editedNode.name" placeholder="分支节点名称" />
  </div>
  <div class="form-group">
    <label>关联 Condition</label>
    <select v-model="editedNode.linkedConditionId">
      <option value="">-- 选择 Condition --</option>
      <option v-for="n in conditionNodes" :key="n.id" :value="n.id">
        {{ n.name || n.id }}
      </option>
    </select>
  </div>
</template>

<!-- Action Node -->
<template v-else-if="selectedNode.type === 'action'">
  <div class="form-group">
    <label>名称</label>
    <input v-model="editedNode.name" placeholder="动作节点名称" />
  </div>
  <div class="form-group">
    <label>动作类型</label>
    <select v-model="editedNode.actionType">
      <option value="GIVE_ITEM">发放物品</option>
      <option value="TAKE_ITEM">扣除物品</option>
      <option value="GIVE_MONEY">发放货币</option>
      <option value="TAKE_MONEY">扣除货币</option>
      <option value="GIVE_XP">发放经验</option>
      <option value="SEND_MESSAGE">发送消息</option>
      <option value="BROADCAST">全服广播</option>
      <option value="EXECUTE_COMMAND">执行命令</option>
      <option value="PLAY_SOUND">播放音效</option>
    </select>
  </div>
  <!-- 动态参数字段 -->
  <template v-if="editedNode.actionType === 'GIVE_ITEM' || editedNode.actionType === 'TAKE_ITEM'">
    <div class="form-group">
      <label>物品ID</label>
      <input v-model="editedNode.actionParams.itemId" placeholder="minecraft:diamond" />
    </div>
    <div class="form-group">
      <label>数量</label>
      <input v-model.number="editedNode.actionParams.count" type="number" placeholder="1" />
    </div>
  </template>
  <template v-else-if="editedNode.actionType === 'GIVE_MONEY' || editedNode.actionType === 'TAKE_MONEY' || editedNode.actionType === 'GIVE_XP'">
    <div class="form-group">
      <label>数量</label>
      <input v-model.number="editedNode.actionParams.amount" type="number" placeholder="100" />
    </div>
  </template>
  <template v-else-if="editedNode.actionType === 'SEND_MESSAGE' || editedNode.actionType === 'BROADCAST'">
    <div class="form-group">
      <label>消息</label>
      <textarea v-model="editedNode.actionParams.message" rows="2" placeholder="消息内容..." />
    </div>
  </template>
  <template v-else-if="editedNode.actionType === 'EXECUTE_COMMAND'">
    <div class="form-group">
      <label>命令</label>
      <input v-model="editedNode.actionParams.command" placeholder="/say Hello" />
    </div>
  </template>
  <template v-else-if="editedNode.actionType === 'PLAY_SOUND'">
    <div class="form-group">
      <label>音效ID</label>
      <input v-model="editedNode.actionParams.sound" placeholder="entity.player.levelup" />
    </div>
  </template>
</template>
```

- [ ] **Step 2: 添加相关数据和函数**

在 `<script setup>` 中:

```typescript
import type { ConditionData, BranchData, ActionData, ConditionItem } from '../../types'

// 添加 computed 获取 condition 节点列表
const conditionNodes = computed(() => {
  // 从 nodes 或其他方式获取
  return []
})

function addCondition() {
  if (editedNode.value.type === 'condition') {
    (editedNode.value as ConditionData).conditions.push({
      conditionType: 'PERMISSION',
      params: { permission: '' }
    })
  }
}

function removeCondition(index: number) {
  if (editedNode.value.type === 'condition') {
    (editedNode.value as ConditionData).conditions.splice(index, 1)
  }
}

function updateConditionParams(cond: ConditionItem) {
  // 根据条件类型重置 params
  switch (cond.conditionType) {
    case 'PERMISSION':
      cond.params = { permission: '' }
      break
    case 'HAS_ITEM':
    case 'KILL_MOB':
    case 'COLLECT_ITEM':
      cond.params = { itemId: '', count: 1 }
      break
    case 'PLAYER_LEVEL':
      cond.params = { level: 1, operator: '>=' }
      break
    case 'TIME_RANGE':
      cond.params = { startHour: 0, endHour: 23 }
      break
    case 'IN_REGION':
      cond.params = { regionName: '' }
      break
  }
}
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/components/editor/NodePropertiesPanel.vue
git commit -m "feat(editor): add Condition, Branch, Action config forms to NodePropertiesPanel"
```

---

## Task 7: 修复 EditorView.vue 的 flowNodes 映射

**Files:**
- Modify: `task-editor-vue/src/views/EditorView.vue`

- [ ] **Step 1: 修复 flowNodes 的 type 映射**

当前代码:
```typescript
const flowNodes = computed({
  get: () => editorNodes.value.map(n => ({
    id: n.id,
    type: 'quest',  // 错误！应该是 n.nodeType
    position: n.position,
    data: n
  })),
  // ...
})
```

修改为:
```typescript
const flowNodes = computed({
  get: () => editorNodes.value.map(n => ({
    id: n.id,
    type: n.nodeType,  // 使用 nodeType
    position: n.position,
    data: n
  })),
  // ...
})
```

- [ ] **Step 2: 提交**

```bash
git add task-editor-vue/src/views/EditorView.vue
git commit -m "fix(editor): map flowNodes type to nodeType instead of hardcoded 'quest'"
```

---

## Task 8: 添加侧边栏节点创建项

**Files:**
- Modify: `task-editor-vue/src/views/EditorView.vue`

- [ ] **Step 1: 添加内置节点类型到侧边栏**

在 `<aside class="sidebar">` 中添加:

```vue
<h3>流程节点</h3>
<div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'condition')">
  <span>◇ Condition</span>
</div>
<div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'branch')">
  <span>⬡ Branch</span>
</div>
<div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'action')">
  <span>▢ Action</span>
</div>

<h3>任务列表</h3>
```

- [ ] **Step 2: 添加 handleNodeDragStart 函数**

```typescript
function handleNodeDragStart(event: DragEvent, nodeType: string) {
  event.dataTransfer?.setData('application/node-type', nodeType)
}
```

- [ ] **Step 3: 修改 handleDrop 处理内置节点类型**

```typescript
function handleDrop(event: DragEvent) {
  event.preventDefault()
  const questData = event.dataTransfer?.getData('application/json')
  const nodeType = event.dataTransfer?.getData('application/node-type')
  
  if (nodeType) {
    // 创建内置节点
    const rect = (event.target as HTMLElement).getBoundingClientRect()
    const position = project({
      x: event.clientX - rect.left,
      y: event.clientY - rect.top
    })
    // 调用 addNode 时传入 nodeType
    const nodeData = { id: `${nodeType}_${Date.now()}`, nodeType, name: '' }
    addNode(nodeData as any, position)
    return
  }
  
  if (questData) {
    try {
      const quest: Quest = JSON.parse(questData)
      const rect = (event.target as HTMLElement).getBoundingClientRect()
      const position = project({
        x: event.clientX - rect.left,
        y: event.clientY - rect.top
      })
      addNode(quest, position)
    } catch (e) {
      console.error('Failed to parse dropped quest data:', e)
    }
  }
}
```

- [ ] **Step 4: 添加样式**

```css
.node-item { border-left: 3px solid #8b5cf6; }
```

- [ ] **Step 5: 提交**

```bash
git add task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): add sidebar items for Condition, Branch, Action nodes"
```

---

## Task 9: 集成测试

- [ ] **Step 1: 构建前端**

```bash
cd task-editor-vue && npm run build
```

- [ ] **Step 2: 验证构建产物**

检查 `dist/` 目录包含所有新组件

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat(editor): complete phase 2 flow control nodes - Condition, Branch, Action"
```
