<template>
  <div class="editor-view">
    <Header title="任务编辑器">
      <template #actions>
        <button @click="showExamplesDialog = true" class="btn-outline">加载示例</button>
        <button @click="showHelpDialog = true" class="btn-outline">帮助</button>
        <button @click="handleSave" class="btn-primary" :disabled="!hasUnsavedChanges">保存</button>
      </template>
    </Header>
    
    <div class="canvas-wrapper"
         @drop="handleDrop"
         @dragover.prevent="handleDragOver">
      <aside class="sidebar">
        <h3>任务列表</h3>
        <div
          v-for="quest in sidebarQuests"
          :key="quest.id"
          class="sidebar-item"
          :class="{ 'selected-quest': selectedQuestId === quest.id }"
          @click="handleSelectQuest(quest)"
        >
          <span class="quest-name">{{ quest.name || '未命名任务' }}</span>
          <span v-if="selectedQuestId === quest.id" class="current-badge">当前</span>
        </div>
        <button @click="handleCreateNewQuest" class="btn-new-quest">+ 新建任务</button>

        <h3>节点工具</h3>
        <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'start')">
          <span>▶ Start</span>
        </div>
        <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'task')">
          <span>📋 Task</span>
        </div>
        <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'completion')">
          <span>✔ Completion</span>
        </div>
        <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'condition')">
          <span>◇ Condition</span>
        </div>
        <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'branch')">
          <span>⬡ Branch</span>
        </div>
        <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'action')">
          <span>▢ Action</span>
        </div>
        <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'counter')">
          <span>🔢 Counter</span>
        </div>
        <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'timer')">
          <span>⏱️ Timer</span>
        </div>
      </aside>
      
      <VueFlow
        v-model:nodes="flowNodes"
        v-model:edges="flowEdges"
        :default-viewport="{ zoom: 1 }"
        @node-click="handleNodeClick"
        @pane-click="handlePaneClick"
        @connect="handleConnect"
        @edge-click="(e) => { selectedEdgeForDelete = e.edge.id; showDeleteEdgeConfirm = true }"
      >
        <Background pattern-color="#aaa" :gap="16" />
        <Controls />
        
        <template #node-task="{ data }">
          <TaskNode :data="data" @delete="handleDeleteNode" />
        </template>
        
        <template #node-start="{ data }">
          <StartNode :data="data" />
        </template>

        <template #node-completion="{ data }">
          <CompletionNode :data="data" />
        </template>

        <template #node-condition="{ data }">
          <ConditionNode :data="data" />
        </template>

        <template #node-branch="{ data }">
          <BranchNode :data="data" />
        </template>

        <template #node-action="{ data }">
          <ActionNode :data="data" />
        </template>

        <template #node-counter="{ data }">
          <CounterNode :data="data" />
        </template>

        <template #node-timer="{ data }">
          <TimerNode :data="data" />
        </template>
      </VueFlow>
      
      <NodePropertiesPanel
        v-if="selectedNode"
        :selected-node="selectedNode"
        @close="selectNode(null)"
        @update="handleUpdateQuest"
      />
      
      <ConfirmDialog
        v-if="showDeleteEdgeConfirm"
        title="删除连接"
        message="确定要删除这个连接吗？"
        @confirm="confirmDeleteEdge"
        @cancel="showDeleteEdgeConfirm = false"
      />
      
      <ExampleQuestsDialog
        v-if="showExamplesDialog"
        @close="showExamplesDialog = false"
        @load="handleLoadExamples"
      />
      
      <EditorHelpDialog
        v-if="showHelpDialog"
        @close="showHelpDialog = false"
      />
      
      <Toast ref="toastRef" />
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref, watch} from 'vue'
import {useVueFlow, VueFlow} from '@vue-flow/core'
import {Background} from '@vue-flow/background'
import {Controls} from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import Header from '../components/layout/Header.vue'
import TaskNode from '../components/editor/TaskNode.vue'
import StartNode from '../components/editor/StartNode.vue'
import CompletionNode from '../components/editor/CompletionNode.vue'
import ConditionNode from '../components/editor/ConditionNode.vue'
import BranchNode from '../components/editor/BranchNode.vue'
import ActionNode from '../components/editor/ActionNode.vue'
import CounterNode from '../components/editor/CounterNode.vue'
import TimerNode from '../components/editor/TimerNode.vue'
import NodePropertiesPanel from '../components/editor/NodePropertiesPanel.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import ExampleQuestsDialog from '../components/editor/ExampleQuestsDialog.vue'
import EditorHelpDialog from '../components/editor/EditorHelpDialog.vue'
import Toast from '../components/Toast.vue'
import {useQuestEditor} from '../composables/useQuestEditor'
import {setToast} from '../composables/useToast'
import {QuestService} from '../services/api'
import type {EditorNodeData, Quest, NodeType} from '../types'

const {
  nodes: editorNodes,
  edges: editorEdges,
  currentQuestId,
  selectedNode: editorSelectedNode,
  addNode,
  removeNode,
  updateNode,
  addEdge,
  removeEdge,
  selectNode,
  clearEditor,
  loadGraph,
  getCurrentQuestGraph
} = useQuestEditor()

const { project } = useVueFlow()

const showExamplesDialog = ref(false)
const showHelpDialog = ref(false)
const sidebarQuests = ref<Quest[]>([])
const selectedQuestId = ref<string | null>(null)
const showDeleteEdgeConfirm = ref(false)
const selectedEdgeForDelete = ref<string | null>(null)
const toastRef = ref<InstanceType<typeof Toast> | null>(null)
const unsavedQuests = ref<Map<string, Quest>>(new Map())

const hasUnsavedChanges = computed(() => unsavedQuests.value.has(selectedQuestId.value || ''))

const flowNodes = computed({
  get: () => editorNodes.value.map(n => ({
    id: n.id,
    type: n.nodeType,
    position: n.position,
    data: n.data
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
    const newEdgeIds = new Set(val.map(e => e.id))
    editorEdges.value = editorEdges.value.filter(e => newEdgeIds.has(e.id))
  }
})

const selectedNode = computed(() => editorSelectedNode.value)

function confirmDeleteEdge() {
  if (selectedEdgeForDelete.value) {
    removeEdge(selectedEdgeForDelete.value)
    selectedEdgeForDelete.value = null
  }
  showDeleteEdgeConfirm.value = false
}

function handleCreateNewQuest() {
  const tempId = `temp_${Date.now()}`
  const newQuest: Quest = {
    id: tempId,
    name: '未命名任务',
    description: '',
    type: 'FOREVER',
    objectives: [],
    rewards: [],
    taskType: 'FOREVER'
  }
  sidebarQuests.value = [...sidebarQuests.value, newQuest]
  unsavedQuests.value.set(tempId, newQuest)
  handleSelectQuest(newQuest)
}

function handleKeyDelete(event: KeyboardEvent) {
  if (event.key === 'Delete' || event.key === 'Backspace') {
    const target = event.target as HTMLElement
    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable) {
      return
    }
    if (selectedEdgeForDelete.value) {
      removeEdge(selectedEdgeForDelete.value)
      selectedEdgeForDelete.value = null
    } else if (editorSelectedNode.value) {
      removeNode(editorSelectedNode.value.id)
    }
  }
}

function handleSelectQuest(quest: Quest) {
  selectedQuestId.value = quest.id
  
  if (quest.graph && quest.graph.nodes.length > 0) {
    loadGraph(quest.id, quest.graph)
  } else {
    clearEditor()
    const startId = addNode('start', { x: 250, y: 50 })
    const taskId = addNode('task', { x: 250, y: 200 })
    const completionId = addNode('completion', { x: 250, y: 350 })
    addEdge(startId, taskId)
    addEdge(taskId, completionId)
    loadGraph(quest.id, {
      id: quest.id,
      name: quest.name,
      nodes: editorNodes.value.map(n => ({
        id: n.id,
        nodeType: n.nodeType,
        x: n.position.x,
        y: n.position.y,
        data: n.data
      })),
      edges: editorEdges.value.map(e => ({
        id: e.id,
        sourceId: e.source,
        targetId: e.target,
        label: e.label
      }))
    })
  }
  
  unsavedQuests.value.delete(quest.id)
}

function handleNodeDragStart(event: DragEvent, nodeType: string) {
  event.dataTransfer?.setData('application/node-type', nodeType)
}

function handleDrop(event: DragEvent) {
  event.preventDefault()
  const nodeType = event.dataTransfer?.getData('application/node-type')
  
  if (!nodeType || !selectedQuestId.value) {
    useToast().error('请先选择一个任务')
    return
  }
  
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const position = project({
    x: event.clientX - rect.left,
    y: event.clientY - rect.top
  })
  
  const nodeId = addNode(nodeType as NodeType, position)
  const graph = getCurrentQuestGraph()
  if (graph) {
    unsavedQuests.value.set(graph.questId, {
      ...sidebarQuests.value.find(q => q.id === graph.questId)!,
      graph: graph.graph
    })
  }
}

function handleNodeClick(event: { node: { id: string } }) {
  selectNode(event.node.id)
}

function handlePaneClick() {
  selectNode(null)
}

function handleConnect(params: { source: string; target: string }) {
  addEdge(params.source, params.target)
  const graph = getCurrentQuestGraph()
  if (graph) {
    unsavedQuests.value.set(graph.questId, {
      ...sidebarQuests.value.find(q => q.id === graph.questId)!,
      graph: graph.graph
    })
  }
}

function handleDeleteNode(nodeId: string) {
  removeNode(nodeId)
  const graph = getCurrentQuestGraph()
  if (graph) {
    unsavedQuests.value.set(graph.questId, {
      ...sidebarQuests.value.find(q => q.id === graph.questId)!,
      graph: graph.graph
    })
  }
}

function handleUpdateQuest(nodeId: string, nodeData: EditorNodeData) {
  updateNode(nodeId, nodeData)
  const graph = getCurrentQuestGraph()
  if (graph) {
    unsavedQuests.value.set(graph.questId, {
      ...sidebarQuests.value.find(q => q.id === graph.questId)!,
      graph: graph.graph
    })
  }
}

async function handleSave() {
  const questId = selectedQuestId.value
  if (!questId) return
  
  const questToSave = unsavedQuests.value.get(questId) || sidebarQuests.value.find(q => q.id === questId)
  if (!questToSave) return
  
  const graphData = getCurrentQuestGraph()
  if (!graphData) return
  
  try {
    const questWithGraph: Quest = {
      ...questToSave,
      graph: graphData.graph
    }
    
    const response = await QuestService.create(questWithGraph)
    
    if (response.code === 0 && response.data) {
      sidebarQuests.value = sidebarQuests.value.map(q => 
        q.id === questId ? response.data : q
      )
      unsavedQuests.value.delete(questId)
      useToast().success('保存成功')
    } else {
      useToast().error('保存失败: ' + response.msg)
    }
  } catch (error) {
    console.error('Failed to save quest:', error)
    useToast().error('保存失败')
  }
}

function handleLoadExamples(quests: Quest[]) {
  quests.forEach(quest => {
    if (!sidebarQuests.value.find(q => q.id === quest.id)) {
      sidebarQuests.value = [...sidebarQuests.value, quest]
    }
  })
  showExamplesDialog.value = false
}

async function loadData() {
  try {
    const questsResponse = await QuestService.getAll()
    if (questsResponse.code === 0 && questsResponse.data) {
      sidebarQuests.value = questsResponse.data
    }
  } catch (error) {
    console.error('Failed to load data:', error)
  }
}

onMounted(() => {
  loadData()
  window.addEventListener('keydown', handleKeyDelete)
  if (toastRef.value) {
    setToast(toastRef)
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDelete)
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
  display: flex;
}
.sidebar {
  width: max-content;
  min-width: 200px;
  max-width: 280px;
  background: white;
  border-right: 1px solid #e5e7eb;
  padding: 1rem;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.sidebar h3 {
  margin-top: 0.5rem;
  font-size: 0.8rem;
  color: #6b7280;
  text-transform: uppercase;
}
.sidebar-item {
  padding: 0.5rem;
  background: #f3f4f6;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.85rem;
}
.sidebar-item:hover {
  background: #e5e7eb;
}
.sidebar-item.selected-quest {
  background: #dbeafe;
  border: 1px solid #3b82f6;
}
.quest-name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 140px;
}
.current-badge {
  font-size: 0.7rem;
  background: #3b82f6;
  color: white;
  padding: 2px 6px;
  border-radius: 10px;
}
.node-item { 
  border-left: 3px solid #8b5cf6;
}
.btn-primary {
  padding: 0.5rem 1rem;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
.btn-primary:disabled {
  background: #9ca3af;
  cursor: not-allowed;
}
.btn-outline {
  padding: 0.5rem 1rem;
  background: white;
  color: #3b82f6;
  border: 1px solid #3b82f6;
  border-radius: 6px;
  cursor: pointer;
}
.btn-new-quest {
  width: 100%;
  padding: 0.5rem;
  margin-top: 0.5rem;
  background: #8b5cf6;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.85rem;
}
.btn-new-quest:hover {
  background: #7c3aed;
}
</style>