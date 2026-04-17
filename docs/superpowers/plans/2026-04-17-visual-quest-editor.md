# Visual Quest Editor (Vue Flow) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现可视化任务编辑器，通过连线管理任务依赖关系

**Architecture:** 基于 Vue Flow 的节点编辑器，支持拖拽创建任务节点、连线表达依赖、属性编辑面板

**Tech Stack:** Vue 3, Vue Flow, @vue-flow/core, @vue-flow/controls, @vue-flow/background

---

## 概述

可视化编辑器核心：
- **节点** = 任务（显示名称、类型、目标数量）
- **连线** = 任务依赖/前置条件
- **画布** = 可缩放/平移，支持网格背景

---

## 文件结构

```
task-editor-vue/src/
├── components/editor/
│   ├── QuestCanvas.vue       # Vue Flow 画布主组件
│   ├── QuestNode.vue         # 自定义任务节点
│   ├── NodeToolbar.vue       # 节点工具栏（编辑/删除）
│   ├── EdgeLabel.vue         # 连线标签
│   └── PropertiesPanel.vue  # 右侧属性编辑面板
├── composables/
│   └── useQuestEditor.ts     # 编辑器状态管理
```

---

## Task 1: 创建 QuestNode 自定义节点

**Files:**
- Create: `task-editor-vue/src/components/editor/QuestNode.vue`
- Create: `task-editor-vue/src/components/editor/NodeToolbar.vue`

- [ ] **Step 1: 创建 QuestNode.vue**

```vue
<template>
  <div class="quest-node" :class="`type-${data.type}`">
    <div class="node-header">
      <span class="node-type">{{ typeLabel }}</span>
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
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import type { Quest } from '../../types'

const props = defineProps<{
  data: {
    id: string
    quest: Quest
  }
}>()

defineEmits<{
  delete: [id: string]
}>()

const questName = computed(() => {
  const names = props.data.quest?.name || {}
  return names['zh-CN'] || names['en-US'] || props.data.id
})

const typeLabel = computed(() => {
  const labels = { single: '单一', multi: '多阶段', series: '系列' }
  return labels[props.data.quest?.type] || '任务'
})

const objectiveSummary = computed(() => {
  const objectives = props.data.quest?.objectives || []
  if (objectives.length === 0) return '无目标'
  if (objectives.length === 1) return `1 个目标`
  return `${objectives.length} 个目标`
})
</script>

<style scoped>
.quest-node {
  min-width: 180px;
  background: white;
  border: 2px solid #3b82f6;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}
.quest-node.type-multi {
  border-color: #8b5cf6;
}
.quest-node.type-series {
  border-color: #f59e0b;
}
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
.delete-btn:hover {
  color: #ef4444;
}
.node-body {
  padding: 0.75rem;
}
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

- [ ] **Step 2: Commit**

```bash
git add task-editor-vue/src/components/editor/QuestNode.vue
git commit -m "feat(editor): 创建QuestNode自定义节点组件"
```

---

## Task 2: 创建 useQuestEditor Composable

**Files:**
- Create: `task-editor-vue/src/composables/useQuestEditor.ts`

- [ ] **Step 1: 创建 useQuestEditor.ts**

```typescript
import { ref, computed } from 'vue'
import type { Quest, QuestObjective } from '../types'

export interface QuestNodeData {
  id: string
  quest: Quest
  position: { x: number; y: number }
}

export function useQuestEditor() {
  const nodes = ref<QuestNodeData[]>([])
  const edges = ref<{ id: string; source: string; target: string; label?: string }[]>([])
  const selectedNodeId = ref<string | null>(null)

  const selectedNode = computed(() => 
    nodes.value.find(n => n.id === selectedNodeId.value)
  )

  function addNode(quest: Quest, position: { x: number; y: number }) {
    nodes.value.push({
      id: quest.id,
      quest,
      position
    })
  }

  function removeNode(nodeId: string) {
    nodes.value = nodes.value.filter(n => n.id !== nodeId)
    edges.value = edges.value.filter(e => e.source !== nodeId && e.target !== nodeId)
  }

  function updateNode(nodeId: string, quest: Partial<Quest>) {
    const node = nodes.value.find(n => n.id === nodeId)
    if (node) {
      node.quest = { ...node.quest, ...quest }
    }
  }

  function addEdge(source: string, target: string, label?: string) {
    const id = `${source}-${target}`
    if (edges.value.some(e => e.id === id)) return
    edges.value.push({ id, source, target, label })
  }

  function removeEdge(edgeId: string) {
    edges.value = edges.value.filter(e => e.id !== edgeId)
  }

  function selectNode(nodeId: string | null) {
    selectedNodeId.value = nodeId
  }

  function loadQuests(quests: Quest[]) {
    // 将 Quest 数组转换为节点
    // 暂时放在画布中央，可扩展为自动布局
    nodes.value = quests.map((quest, index) => ({
      id: quest.id,
      quest,
      position: {
        x: 100 + (index % 4) * 250,
        y: 100 + Math.floor(index / 4) * 150
      }
    }))
  }

  function exportData() {
    return {
      nodes: nodes.value.map(n => n.quest),
      edges: edges.value
    }
  }

  return {
    nodes,
    edges,
    selectedNode,
    selectedNodeId,
    addNode,
    removeNode,
    updateNode,
    addEdge,
    removeEdge,
    selectNode,
    loadQuests,
    exportData
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add task-editor-vue/src/composables/useQuestEditor.ts
git commit -m "feat(editor): 创建useQuestEditor状态管理composable"
```

---

## Task 3: 创建 PropertiesPanel 属性编辑面板

**Files:**
- Create: `task-editor-vue/src/components/editor/PropertiesPanel.vue`

- [ ] **Step 1: 创建 PropertiesPanel.vue**

```vue
<template>
  <aside class="properties-panel" v-if="selectedNode">
    <div class="panel-header">
      <h3>任务属性</h3>
      <button class="close-btn" @click="$emit('close')">×</button>
    </div>
    <div class="panel-body">
      <div class="form-group">
        <label>任务 ID</label>
        <input v-model="editedQuest.id" readonly />
      </div>
      <div class="form-group">
        <label>类型</label>
        <select v-model="editedQuest.type">
          <option value="single">单一任务</option>
          <option value="multi">多阶段任务</option>
          <option value="series">系列任务</option>
        </select>
      </div>
      <div class="form-group">
        <label>名称 (中文)</label>
        <input v-model="editedQuest.name['zh-CN']" placeholder="任务名称" />
      </div>
      <div class="form-group">
        <label>名称 (英文)</label>
        <input v-model="editedQuest.name['en-US']" placeholder="Quest Name" />
      </div>
      <div class="form-group">
        <label>描述 (中文)</label>
        <textarea v-model="editedQuest.description['zh-CN']" rows="3" />
      </div>
      
      <div class="section-divider">
        <h4>目标 ({{ editedQuest.objectives.length }})</h4>
        <button @click="addObjective">+ 添加目标</button>
      </div>
      <div class="objectives-list">
        <div v-for="(obj, index) in editedQuest.objectives" :key="index" class="objective-item">
          <input v-model="obj.type" placeholder="类型" />
          <input v-model="obj.target" placeholder="目标" />
          <input v-model.number="obj.count" type="number" placeholder="数量" />
          <button @click="removeObjective(index)">删除</button>
        </div>
      </div>
      
      <div class="section-divider">
        <h4>奖励 ({{ editedQuest.rewards.length }})</h4>
        <button @click="addReward">+ 添加奖励</button>
      </div>
      <div class="rewards-list">
        <div v-for="(reward, index) in editedQuest.rewards" :key="index" class="reward-item">
          <select v-model="reward.type">
            <option value="item">物品</option>
            <option value="xp">经验</option>
            <option value="money">货币</option>
            <option value="command">指令</option>
          </select>
          <input v-model="reward.value" placeholder="值" />
          <button @click="removeReward(index)">删除</button>
        </div>
      </div>
    </div>
    <div class="panel-footer">
      <button class="save-btn" @click="saveChanges">保存</button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { Quest, QuestObjective, QuestReward } from '../../types'

const props = defineProps<{
  selectedNode: { id: string; quest: Quest } | null
}>()

const emit = defineEmits<{
  close: []
  update: [id: string, quest: Partial<Quest>]
}>()

const editedQuest = ref<Quest>({
  id: '',
  name: { 'zh-CN': '', 'en-US': '' },
  description: { 'zh-CN': '', 'en-US': '' },
  type: 'single',
  objectives: [],
  rewards: [],
  createdAt: Date.now(),
  updatedAt: Date.now()
})

watch(() => props.selectedNode, (node) => {
  if (node) {
    editedQuest.value = JSON.parse(JSON.stringify(node.quest))
  }
}, { immediate: true })

function addObjective() {
  editedQuest.value.objectives.push({
    id: `obj_${Date.now()}`,
    type: '',
    target: '',
    count: 1,
    finished: false
  })
}

function removeObjective(index: number) {
  editedQuest.value.objectives.splice(index, 1)
}

function addReward() {
  editedQuest.value.rewards.push({
    id: `reward_${Date.now()}`,
    type: 'item',
    value: ''
  })
}

function removeReward(index: number) {
  editedQuest.value.rewards.splice(index, 1)
}

function saveChanges() {
  if (props.selectedNode) {
    editedQuest.value.updatedAt = Date.now()
    emit('update', props.selectedNode.id, editedQuest.value)
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
.panel-header h3 {
  margin: 0;
  font-size: 1rem;
}
.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
}
.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
}
.form-group {
  margin-bottom: 1rem;
}
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
}
.section-divider {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 1.5rem 0 1rem;
}
.section-divider h4 {
  margin: 0;
  font-size: 0.9rem;
}
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
.panel-footer {
  padding: 1rem;
  border-top: 1px solid #e5e7eb;
}
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

- [ ] **Step 2: Commit**

```bash
git add task-editor-vue/src/components/editor/PropertiesPanel.vue
git commit -m "feat(editor): 创建PropertiesPanel属性编辑面板"
```

---

## Task 4: 创建 QuestCanvas 主画布组件

**Files:**
- Modify: `task-editor-vue/src/views/EditorView.vue`

- [ ] **Step 1: 重写 EditorView.vue**

```vue
<template>
  <div class="editor-view">
    <Header title="任务编辑器">
      <template #actions>
        <button @click="handleSave" class="btn-primary">保存</button>
        <button @click="handleAddQuest" class="btn-secondary">新建任务</button>
      </template>
    </Header>
    
    <div class="canvas-wrapper">
      <VueFlow
        v-model:nodes="flowNodes"
        v-model:edges="flowEdges"
        :default-viewport="{ zoom: 1 }"
        @node-click="handleNodeClick"
        @pane-click="handlePaneClick"
        @connect="handleConnect"
        @edge-click="handleEdgeClick"
      >
        <Background pattern-color="#aaa" :gap="16" />
        <Controls />
        
        <template #node-quest=" { data } ">
          <QuestNode 
            :data="data" 
            @delete="handleDeleteNode" 
          />
        </template>
      </VueFlow>
      
      <PropertiesPanel
        v-if="selectedNode"
        :selected-node="selectedNode"
        @close="selectedNode = null"
        @update="handleUpdateQuest"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { VueFlow, Background, Controls, useVueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import Header from '../components/layout/Header.vue'
import QuestNode from '../components/editor/QuestNode.vue'
import PropertiesPanel from '../components/editor/PropertiesPanel.vue'
import { useQuestEditor } from '../composables/useQuestEditor'
import { QuestService } from '../services/api'
import type { Quest } from '../types'

const {
  nodes: editorNodes,
  edges: editorEdges,
  selectedNode: editorSelectedNode,
  addNode,
  removeNode,
  updateNode,
  addEdge,
  removeEdge,
  loadQuests,
  exportData
} = useQuestEditor()

const { project } = useVueFlow()

// Vue Flow 需要的数据格式
const flowNodes = computed({
  get: () => editorNodes.value.map(n => ({
    id: n.id,
    type: 'quest',
    position: n.position,
    data: n
  })),
  set: (val) => {
    val.forEach(v => {
      const node = editorNodes.value.find(n => n.id === v.id)
      if (node) {
        node.position = v.position
      }
    })
  }
})

const flowEdges = computed({
  get: () => editorEdges.value.map(e => ({
    id: e.id,
    source: e.source,
    target: e.target,
    label: e.label,
    type: 'smoothstep'
  })),
  set: (val) => {
    // 只读，从 editorEdges 同步
  }
})

const selectedNode = computed(() => editorSelectedNode.value)

async function loadData() {
  try {
    const response = await QuestService.getAll()
    if (response.code === 0 && response.data) {
      loadQuests(response.data)
    }
  } catch (error) {
    console.error('Failed to load quests:', error)
  }
}

function handleNodeClick(event) {
  editorSelectedNode.value = event.node.id
}

function handlePaneClick() {
  editorSelectedNode.value = null
}

function handleConnect(params) {
  addEdge(params.source, params.target)
}

function handleEdgeClick(event) {
  // 可选：点击边显示删除选项
}

function handleDeleteNode(nodeId: string) {
  removeNode(nodeId)
}

function handleUpdateQuest(nodeId: string, quest: Partial<Quest>) {
  updateNode(nodeId, quest)
}

function handleAddQuest() {
  const newQuest: Quest = {
    id: `quest_${Date.now()}`,
    name: { 'zh-CN': '新任务', 'en-US': 'New Quest' },
    description: { 'zh-CN': '', 'en-US': '' },
    type: 'single',
    objectives: [],
    rewards: [],
    createdAt: Date.now(),
    updatedAt: Date.now()
  }
  const center = project({ x: 400, y: 300 })
  addNode(newQuest, center)
}

async function handleSave() {
  const data = exportData()
  for (const quest of data.nodes) {
    try {
      await QuestService.update(quest.id, quest)
    } catch (error) {
      console.error(`Failed to save quest ${quest.id}:`, error)
    }
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.editor-view {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.canvas-wrapper {
  flex: 1;
  position: relative;
  background: #f5f5f5;
}
.btn-primary {
  padding: 0.5rem 1rem;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
.btn-secondary {
  padding: 0.5rem 1rem;
  background: #6b7280;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): 实现QuestCanvas主画布组件"
```

---

## Task 5: 添加拖拽创建节点支持

- [ ] **Step 1: 修改 EditorView.vue 添加拖拽处理**

在 canvas 上添加 @drop @dragover 处理，允许从侧边栏拖拽创建任务

- [ ] **Step 2: Commit**

```bash
git add task-editor-vue/src/views/EditorView.vue
git commit -m "feat(editor): 添加从侧边栏拖拽创建节点支持"
```

---

## Task 6: 集成测试

- [ ] **Step 1: 启动后端服务**

```bash
./gradlew run
```

- [ ] **Step 2: 启动前端开发服务器**

```bash
cd task-editor-vue && npm run dev
```

- [ ] **Step 3: 验证功能**
- 加载任务列表显示为节点
- 点击节点显示属性面板
- 修改属性并保存
- 拖拽创建新节点
- 连接两个节点创建依赖关系
- 删除节点和连线

---

## Task 7: 添加节点创建对话框

**Files:**
- Create: `task-editor-vue/src/components/editor/CreateQuestDialog.vue`

- [ ] **Step 1: 创建 CreateQuestDialog.vue**

```vue
<template>
  <div class="dialog-overlay" @click.self="$emit('close')">
    <div class="dialog">
      <h3>创建新任务</h3>
      <div class="form-group">
        <label>任务类型</label>
        <select v-model="newQuest.type">
          <option value="single">单一任务</option>
          <option value="multi">多阶段任务</option>
          <option value="series">系列任务</option>
        </select>
      </div>
      <div class="form-group">
        <label>名称 (中文)</label>
        <input v-model="newQuest.name['zh-CN']" />
      </div>
      <div class="form-group">
        <label>名称 (英文)</label>
        <input v-model="newQuest.name['en-US']" />
      </div>
      <div class="dialog-actions">
        <button @click="$emit('close')">取消</button>
        <button @click="handleCreate" class="btn-primary">创建</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { Quest } from '../../types'

const emit = defineEmits<{
  close: []
  create: [quest: Quest]
}>()

const newQuest = ref<Quest>({
  id: `quest_${Date.now()}`,
  name: { 'zh-CN': '', 'en-US': '' },
  description: { 'zh-CN': '', 'en-US': '' },
  type: 'single',
  objectives: [],
  rewards: [],
  createdAt: Date.now(),
  updatedAt: Date.now()
})

function handleCreate() {
  emit('create', { ...newQuest.value })
}
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.dialog {
  background: white;
  padding: 1.5rem;
  border-radius: 8px;
  width: 400px;
}
.dialog h3 {
  margin-top: 0;
}
.form-group {
  margin-bottom: 1rem;
}
.form-group label {
  display: block;
  margin-bottom: 0.25rem;
  font-size: 0.9rem;
}
.form-group input,
.form-group select {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-top: 1.5rem;
}
.btn-primary {
  padding: 0.5rem 1rem;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add task-editor-vue/src/components/editor/CreateQuestDialog.vue
git commit -m "feat(editor): 添加创建任务对话框"
```
