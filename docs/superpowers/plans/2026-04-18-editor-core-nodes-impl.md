# 核心节点系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为网页编辑器添加 Start、Task（4种子类型）、Completion 三种核心节点，建立节点系统基座

**Architecture:** 前端 Vue Flow 节点编辑器 + 后端 TaskDefinition 扩展。前端新增节点组件和属性面板，后端添加 taskType 字段

**Tech Stack:** Vue 3, TypeScript, Vue Flow, Java/Javalin, Jackson

---

## 文件结构

```
Frontend (task-editor-vue/):
├── src/
│   ├── components/editor/
│   │   ├── StartNode.vue        # 新增：Start 节点
│   │   ├── TaskNode.vue          # 新增：Task 节点（替换 QuestNode 部分职责）
│   │   ├── CompletionNode.vue    # 新增：Completion 节点
│   │   └── NodePropertiesPanel.vue # 重构：统一属性面板
│   ├── composables/
│   │   └── useQuestEditor.ts     # 修改：添加连线规则验证
│   ├── types/
│   │   └── index.ts              # 修改：添加新类型定义
│   └── views/
│       └── EditorView.vue        # 修改：注册新节点类型
└── package.json

Backend (api/):
└── src/main/java/.../api/model/
    └── TaskDefinition.java       # 修改：添加 taskType 字段
```

---

## Task 1: 扩展前端类型定义

**Files:**
- Modify: `task-editor-vue/src/types/index.ts`

- [ ] **Step 1: 添加节点类型枚举和子类型枚举**

```typescript
// 在 index.ts 末尾添加

export type NodeType = 'start' | 'task' | 'completion'

export type TaskSubType = 'CYCLE' | 'TIMER' | 'FOREVER' | 'LIMIT'

export interface StartNodeData {
  type: 'start'
  name: string
  description?: string
  startCondition?: object
}

export interface TaskNodeData {
  type: 'task'
  id: string
  name: Record<string, string>
  description: Record<string, string>
  taskType: TaskSubType
  objectives: QuestObjective[]
  rewards: QuestReward[]
  resetInterval?: number
  timeLimit?: number
  expiredAction?: string
}

export interface CompletionNodeData {
  type: 'completion'
  name: string
  rewards: QuestReward[]
  callbackMessage?: string
}

export type EditorNodeData = StartNodeData | TaskNodeData | CompletionNodeData
```

- [ ] **Step 2: 更新 Quest 类型以包含 taskType（向后兼容）**

在 `Quest` 接口中添加可选字段:
```typescript
export interface Quest {
  // ... existing fields
  taskType?: TaskSubType  // 新增：支持任务类型
  // ... existing fields
}
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/types/index.ts
git commit -m "feat(editor): add node types and task subtypes to frontend types"
```

---

## Task 2: 创建 StartNode.vue

**Files:**
- Create: `task-editor-vue/src/components/editor/StartNode.vue`
- Modify: `task-editor-vue/src/views/EditorView.vue`（注册节点类型）
- Modify: `task-editor-vue/src/types/index.ts`（已在 Task 1 中添加）

**节点创建机制:** Start 节点需要从侧边栏创建。在 `sidebarQuests` 中会有内置的"Start 节点"选项，拖入画布后自动设置为 `nodeType: 'start'`

- [ ] **Step 1: 创建 StartNode.vue**

```vue
<template>
  <div class="start-node">
    <div class="node-circle">
      <span class="node-icon">▶</span>
    </div>
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import {Handle, Position} from '@vue-flow/core'

defineProps<{
  data: {
    name: string
    description?: string
  }
}>()
</script>

<style scoped>
.start-node {
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
  background: #22c55e;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(34, 197, 94, 0.3);
}
.node-icon {
  color: white;
  font-size: 1.2rem;
}
</style>
```

- [ ] **Step 2: 在 EditorView.vue 中导入并注册 StartNode**

在 `<script setup>` 区域的 import 语句中添加（约第86-91行附近）:
```typescript
import StartNode from '../components/editor/StartNode.vue'
```

在 `<VueFlow>` 组件的 `template #node-quest` 之后添加（约第46行）:
```vue
<template #node-start="{ data }">
  <StartNode :data="data" />
</template>
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/components/editor/StartNode.vue task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): add StartNode component"
```

---

## Task 3: 创建 CompletionNode.vue

**Files:**
- Create: `task-editor-vue/src/components/editor/CompletionNode.vue`
- Modify: `task-editor-vue/src/views/EditorView.vue`

**节点创建机制:** Completion 节点同样从侧边栏创建

- [ ] **Step 1: 创建 CompletionNode.vue**

```vue
<template>
  <div class="completion-node">
    <div class="node-diamond">
      <span class="node-icon">✓</span>
    </div>
    <Handle type="target" :position="Position.Left" />
  </div>
</template>

<script setup lang="ts">
import {Handle, Position} from '@vue-flow/core'

defineProps<{
  data: {
    name: string
    rewards?: any[]
    callbackMessage?: string
  }
}>()
</script>

<style scoped>
.completion-node {
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.node-diamond {
  width: 45px;
  height: 45px;
  background: #ef4444;
  transform: rotate(45deg);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.3);
}
.node-icon {
  color: white;
  font-size: 1rem;
  transform: rotate(-45deg);
}
</style>
```

- [ ] **Step 2: 在 EditorView.vue 中注册 CompletionNode**

在 import 语句中添加:
```typescript
import CompletionNode from '../components/editor/CompletionNode.vue'
```

在 `<VueFlow>` 的 `#node-start` 之后添加:
```vue
<template #node-completion="{ data }">
  <CompletionNode :data="data" />
</template>
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/components/editor/CompletionNode.vue task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): add CompletionNode component"
```

---

## Task 4: 创建 TaskNode.vue（支持4种子类型）

**Files:**
- Create: `task-editor-vue/src/components/editor/TaskNode.vue`
- Modify: `task-editor-vue/src/views/EditorView.vue`

**Note:** TaskNode 替换现有的 QuestNode 作为 `task` 类型节点

- [ ] **Step 1: 创建 TaskNode.vue**

```vue
<template>
  <div class="task-node" :class="`type-${data.taskType?.toLowerCase()}`">
    <div class="node-header">
      <span class="node-type">{{ taskTypeLabel }}</span>
      <button class="delete-btn" @click.stop="$emit('delete', data.id)">×</button>
    </div>
    <div class="node-body">
      <h3 class="node-name">{{ questName }}</h3>
      <p class="node-desc">{{ objectiveSummary }}</p>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {Handle, Position} from '@vue-flow/core'
import type {TaskNodeData} from '../../types'

const props = defineProps<{
  data: TaskNodeData
}>()

defineEmits<{
  delete: [id: string]
}>()

const taskTypeLabel = computed(() => {
  const labels: Record<string, string> = {
    CYCLE: '循环',
    TIMER: '定时',
    FOREVER: '永久',
    LIMIT: '限时'
  }
  return labels[props.data.taskType] || '任务'
})

const questName = computed(() => {
  const names = props.data.name || {}
  return names['zh-CN'] || names['en-US'] || props.data.id
})

const objectiveSummary = computed(() => {
  const objectives = props.data.objectives || []
  if (objectives.length === 0) return '无目标'
  if (objectives.length === 1) return `1 个目标`
  return `${objectives.length} 个目标`
})
</script>

<style scoped>
.task-node {
  min-width: 180px;
  background: white;
  border: 2px solid #3b82f6;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}
.task-node.type-cycle { border-color: #3b82f6; }
.task-node.type-timer { border-color: #f59e0b; }
.task-node.type-forever { border-color: #22c55e; }
.task-node.type-limit { border-color: #ef4444; }
.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0.75rem;
  background: #f3f4f6;
  border-bottom: 1px solid #e5e7eb;
}
.node-type {
  font-size: 0.75rem;
  color: #6b7280;
}
.delete-btn {
  background: none;
  border: none;
  font-size: 1.2rem;
  cursor: pointer;
  color: #9ca3af;
}
.delete-btn:hover { color: #ef4444; }
.node-body { padding: 0.75rem; }
.node-name {
  font-size: 0.95rem;
  font-weight: 600;
  margin-bottom: 0.25rem;
}
.node-desc {
  font-size: 0.8rem;
  color: #6b7280;
}
</style>
```

- [ ] **Step 2: 在 EditorView.vue 中注册 TaskNode**

在 import 语句中添加:
```typescript
import TaskNode from '../components/editor/TaskNode.vue'
```

将现有的 `#node-quest` 模板替换为:
```vue
<template #node-task="{ data }">
  <TaskNode :data="data" @delete="handleDeleteNode" />
</template>
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/components/editor/TaskNode.vue task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): add TaskNode with subtype styling"
```

---

## Task 5: 创建 NodePropertiesPanel.vue（统一属性面板）

**Files:**
- Create: `task-editor-vue/src/components/editor/NodePropertiesPanel.vue`
- Modify: `task-editor-vue/src/views/EditorView.vue`（替换 PropertiesPanel）

- [ ] **Step 1: 创建 NodePropertiesPanel.vue**

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
          <label>名称</label>
          <input v-model="editedNode.name" placeholder="节点名称" />
        </div>
        <div class="form-group">
          <label>描述</label>
          <textarea v-model="editedNode.description" rows="3" placeholder="描述..." />
        </div>
      </template>

      <!-- Task Node -->
      <template v-else-if="selectedNode.type === 'task'">
        <div class="form-group">
          <label>任务 ID</label>
          <input v-model="editedNode.id" readonly />
        </div>
        <div class="form-group">
          <label>类型</label>
          <select v-model="editedNode.taskType">
            <option value="CYCLE">循环任务</option>
            <option value="TIMER">定时任务</option>
            <option value="FOREVER">永久任务</option>
            <option value="LIMIT">限时任务</option>
          </select>
        </div>
        <div class="form-group">
          <label>名称 (中文)</label>
          <input v-model="editedNode.name['zh-CN']" placeholder="任务名称" />
        </div>
        <div class="form-group">
          <label>名称 (英文)</label>
          <input v-model="editedNode.name['en-US']" placeholder="Quest Name" />
        </div>
        <div class="form-group">
          <label>描述 (中文)</label>
          <textarea v-model="editedNode.description['zh-CN']" rows="3" />
        </div>

        <!-- CYCLE / TIMER 动态字段 -->
        <template v-if="editedNode.taskType === 'CYCLE' || editedNode.taskType === 'TIMER'">
          <div class="form-group">
            <label>重置间隔 (秒)</label>
            <input v-model.number="editedNode.resetInterval" type="number" min="1" placeholder="60" />
          </div>
        </template>

        <!-- LIMIT 动态字段 -->
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

        <div class="section-divider">
          <h4>目标 ({{ editedNode.objectives?.length || 0 }})</h4>
          <button @click="addObjective">+ 添加</button>
        </div>
        <div class="objectives-list">
          <div v-for="(obj, index) in editedNode.objectives" :key="index" class="objective-item">
            <input v-model="obj.type" placeholder="类型" />
            <input v-model="obj.target" placeholder="目标" />
            <input v-model.number="obj.count" type="number" placeholder="数量" />
            <button @click="removeObjective(index)">×</button>
          </div>
        </div>

        <div class="section-divider">
          <h4>奖励 ({{ editedNode.rewards?.length || 0 }})</h4>
          <button @click="addReward">+ 添加</button>
        </div>
        <div class="rewards-list">
          <div v-for="(reward, index) in editedNode.rewards" :key="index" class="reward-item">
            <select v-model="reward.type">
              <option value="item">物品</option>
              <option value="xp">经验</option>
              <option value="money">货币</option>
              <option value="command">指令</option>
            </select>
            <input v-model="reward.value" placeholder="值" />
            <button @click="removeReward(index)">×</button>
          </div>
        </div>
      </template>

      <!-- Completion Node -->
      <template v-else-if="selectedNode.type === 'completion'">
        <div class="form-group">
          <label>名称</label>
          <input v-model="editedNode.name" placeholder="完成节点名称" />
        </div>
        <div class="form-group">
          <label>回调消息</label>
          <textarea v-model="editedNode.callbackMessage" rows="2" placeholder="完成后发送的消息..." />
        </div>

        <div class="section-divider">
          <h4>奖励 ({{ editedNode.rewards?.length || 0 }})</h4>
          <button @click="addReward">+ 添加</button>
        </div>
        <div class="rewards-list">
          <div v-for="(reward, index) in editedNode.rewards" :key="index" class="reward-item">
            <select v-model="reward.type">
              <option value="item">物品</option>
              <option value="xp">经验</option>
              <option value="money">货币</option>
              <option value="command">指令</option>
            </select>
            <input v-model="reward.value" placeholder="值" />
            <button @click="removeReward(index)">×</button>
          </div>
        </div>
      </template>
    </div>
    <div class="panel-footer">
      <button class="save-btn" @click="saveChanges">保存</button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import {ref, watch, computed} from 'vue'
import type {StartNodeData, TaskNodeData, CompletionNodeData, QuestObjective, QuestReward} from '../../types'

type NodeData = StartNodeData | TaskNodeData | CompletionNodeData

const props = defineProps<{
  selectedNode: NodeData | null
}>()

const emit = defineEmits<{
  close: []
  update: [id: string, node: Partial<NodeData>]
}>()

const panelTitle = computed(() => {
  const titles: Record<string, string> = { start: 'Start 节点', task: '任务属性', completion: '完成节点' }
  return titles[props.selectedNode?.type || ''] || '属性'
})

const editedNode = ref<NodeData>(createDefaultNode())

function createDefaultNode(): NodeData {
  return {
    type: 'start',
    name: '',
    description: ''
  } as StartNodeData
}

watch(() => props.selectedNode, (node) => {
  if (node) {
    editedNode.value = JSON.parse(JSON.stringify(node))
  }
}, { immediate: true })

function addObjective() {
  if (editedNode.value.type === 'task') {
    (editedNode.value as TaskNodeData).objectives.push({
      id: `obj_${Date.now()}`,
      type: '',
      target: '',
      count: 1,
      finished: false
    })
  }
}

function removeObjective(index: number) {
  if (editedNode.value.type === 'task') {
    (editedNode.value as TaskNodeData).objectives.splice(index, 1)
  }
}

function addReward() {
  const reward: QuestReward = { id: `reward_${Date.now()}`, type: 'item', value: '' }
  if (editedNode.value.type === 'task') {
    (editedNode.value as TaskNodeData).rewards.push(reward)
  } else if (editedNode.value.type === 'completion') {
    (editedNode.value as CompletionNodeData).rewards.push(reward)
  }
}

function removeReward(index: number) {
  if (editedNode.value.type === 'task') {
    (editedNode.value as TaskNodeData).rewards.splice(index, 1)
  } else if (editedNode.value.type === 'completion') {
    (editedNode.value as CompletionNodeData).rewards.splice(index, 1)
  }
}

function saveChanges() {
  if (props.selectedNode) {
    emit('update', props.selectedNode.id || (editedNode.value as any).id, editedNode.value)
  }
}
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
.section-divider button {
  padding: 0.25rem 0.5rem;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.8rem;
}
.objective-item,
.reward-item {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
  align-items: center;
}
.objective-item input,
.reward-item input {
  flex: 1;
  padding: 0.4rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 0.85rem;
}
.objective-item button,
.reward-item button {
  padding: 0.25rem 0.5rem;
  background: #ef4444;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.75rem;
}
.panel-footer { padding: 1rem; border-top: 1px solid #e5e7eb; }
.save-btn {
  width: 100%;
  padding: 0.75rem;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
}
</style>
```

- [ ] **Step 2: 更新 EditorView.vue 替换 PropertiesPanel**

替换 import:
```typescript
// 移除 PropertiesPanel
import NodePropertiesPanel from '../components/editor/NodePropertiesPanel.vue'
```

替换模板中的组件引用:
```vue
<NodePropertiesPanel
  v-if="selectedNode"
  :selected-node="selectedNode"
  @close="selectedNode = null"
  @update="handleUpdateQuest"
/>
```

- [ ] **Step 3: 提交**

```bash
git add task-editor-vue/src/components/editor/NodePropertiesPanel.vue task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): add NodePropertiesPanel supporting all 3 node types"
```

**清理遗留文件:**
- Task 完成后可删除 `QuestNode.vue` 和旧的 `PropertiesPanel.vue`（它们已被新组件替代）

---

## Task 6: 修改 useQuestEditor 添加连线规则验证

**Files:**
- Modify: `task-editor-vue/src/composables/useQuestEditor.ts`

- [ ] **Step 1: 添加节点类型到 QuestNodeData**

```typescript
export interface QuestNodeData {
  id: string
  quest: Quest
  position: { x: number; y: number }
  nodeType: 'start' | 'task' | 'completion'  // 必填
}
```

- [ ] **Step 2: 修改 addEdge 函数添加完整验证**

用以下完整实现替换 `useQuestEditor.ts` 中的 `addEdge` 函数:

```typescript
function addEdge(source: string, target: string, label?: string) {
  // 禁止自身连接
  if (source === target) return
  
  // 根据节点类型验证连线规则
  const sourceNode = nodes.value.find(n => n.id === source)
  const targetNode = nodes.value.find(n => n.id === target)
  
  if (!sourceNode || !targetNode) return
  
  // 获取节点类型
  const sourceType = sourceNode.nodeType || 'task'  // 默认为 task（向后兼容）
  const targetType = targetNode.nodeType || 'task'
  
  // 验证连线规则:
  // Start → Task
  // Task → Task / Completion
  // Completion → (nothing)
  if (sourceType === 'start' && targetType !== 'task') {
    console.warn('Cannot connect: Start can only connect to Task')
    return
  }
  if (sourceType === 'task' && targetType === 'start') {
    console.warn('Cannot connect: Task cannot connect to Start')
    return
  }
  if (sourceType === 'completion') {
    console.warn('Cannot connect: Completion cannot be a source')
    return
  }
  
  // 禁止环路 - 简单的 DFS 检查
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

**同时需要修改 `QuestNodeData` 的 `nodeType` 为必填:**

```typescript
export interface QuestNodeData {
  id: string
  quest: Quest
  position: { x: number; y: number }
  nodeType: 'start' | 'task' | 'completion'  // 改为必填
}
```

- [ ] **Step 2: 提交**

```bash
git add task-editor-vue/src/composables/useQuestEditor.ts
git commit -m "feat(editor): add cycle detection to edge validation"
```

---

## Task 7: 后端 TaskDefinition 添加 taskType 字段

**Files:**
- Modify: `api/src/main/java/com/playerPlugin/playerTaskX/api/model/TaskDefinition.java`

**前提:** `PTXTaskType` 枚举已存在于 `com.playerPlugin.playerTaskX.api.Enum.PTXTaskType`

- [ ] **Step 1: 修改 TaskDefinition 添加 taskType**

```java
// 添加 import
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskType;

public class TaskDefinition {
    private final String id;
    private final String name;
    private final String description;
    private final PTXTaskType taskType;  // 新增
    private final List<Objective> objectives;
    private final List<Reward> rewards;
    private final List<Condition> conditions;

    @JsonCreator
    public TaskDefinition(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("taskType") PTXTaskType taskType,  // 新增
            @JsonProperty("objectives") List<Objective> objectives,
            @JsonProperty("rewards") List<Reward> rewards,
            @JsonProperty("conditions") List<Condition> conditions
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.taskType = taskType != null ? taskType : PTXTaskType.FOREVER;  // 默认 FOREVER
        this.objectives = objectives != null ? new ArrayList<>(objectives) : new ArrayList<>();
        this.rewards = rewards != null ? new ArrayList<>(rewards) : new ArrayList<>();
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
    }

    // 新增 getter
    public PTXTaskType getTaskType() { return taskType; }
}
```

- [ ] **Step 2: 提交**

```bash
git add api/src/main/java/com/playerPlugin/playerTaskX/api/model/TaskDefinition.java
git commit -m "feat(api): add taskType field to TaskDefinition"
```

---

## Task 8: 集成测试 - 确保前端能正确渲染和保存节点

- [ ] **Step 1: 构建前端**

```bash
cd task-editor-vue && npm run build
```

- [ ] **Step 2: 验证构建产物**

检查 `dist/` 目录是否包含所有新组件

- [ ] **Step 3: 提交所有更改**

```bash
git add -A && git commit -m "feat(editor): complete core nodes system - Start, Task, Completion nodes"
```
