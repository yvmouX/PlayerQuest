<template>
  <div class="editor-view">
    <Header title="任务编辑器">
      <template #actions>
        <button @click="showExamplesDialog = true" class="btn-outline">加载示例</button>
        <button @click="showHelpDialog = true" class="btn-outline">帮助</button>
        <button @click="handleSave" class="btn-primary">保存</button>
      </template>
    </Header>
    
    <div class="canvas-wrapper"
         @drop="handleDrop"
         @dragover.prevent="handleDragOver">
       <aside class="sidebar">
         <h3>核心节点</h3>
         <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'start')">
           <span>▶ Start</span>
         </div>
         <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'task')">
           <span>📋 Task</span>
         </div>
         <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'completion')">
           <span>✔ Completion</span>
         </div>

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

        <h3>任务列表</h3>
        <div
          v-for="quest in sidebarQuests"
          :key="quest.id"
          class="sidebar-item"
          :class="{ 'selected-quest': selectedQuestId === quest.id }"
          @click="handleSelectQuest(quest)"
        >
          <span class="quest-name">{{ quest.name['zh-CN'] || quest.name['en-US'] || '未命名任务' }}{{ unsavedQuestIds.has(quest.id) ? ' (未保存)' : '' }}</span>
        </div>
        <button @click="handleCreateNewQuest" class="btn-new-quest">+ 新建任务</button>
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
import {computed, onMounted, onUnmounted, ref} from 'vue'
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
import EventNode from '../components/editor/EventNode.vue'
import CounterNode from '../components/editor/CounterNode.vue'
import TimerNode from '../components/editor/TimerNode.vue'
import StateNode from '../components/editor/StateNode.vue'
import SubtaskNode from '../components/editor/SubtaskNode.vue'
import NodePropertiesPanel from '../components/editor/NodePropertiesPanel.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import ExampleQuestsDialog from '../components/editor/ExampleQuestsDialog.vue'
import EditorHelpDialog from '../components/editor/EditorHelpDialog.vue'
import Toast from '../components/Toast.vue'
import {useQuestEditor} from '../composables/useQuestEditor'
import {setToast} from '../composables/useToast'
import {QuestService, GraphService} from '../services/api'
import type {Quest, EditorNodeData, TaskNodeData} from '../types'

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
  loadGraph,
  saveGraph,
  selectNode
} = useQuestEditor()

const { project } = useVueFlow()

const showExamplesDialog = ref(false)
const showHelpDialog = ref(false)
const sidebarQuests = ref<Quest[]>([])
const unsavedQuestIds = ref<Set<string>>(new Set())
const selectedQuestId = ref<string | null>(null)
const showDeleteEdgeConfirm = ref(false)
const selectedEdgeForDelete = ref<string | null>(null)
const toastRef = ref<InstanceType<typeof Toast> | null>(null)

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
    name: { 'zh-CN': '未命名任务', 'en-US': 'Unnamed Quest' },
    description: { 'zh-CN': '', 'en-US': '' },
    type: 'FOREVER',
    objectives: [],
    rewards: []
  }
  sidebarQuests.value = [...sidebarQuests.value, newQuest]
  unsavedQuestIds.value = new Set([...unsavedQuestIds.value, tempId])
  
  const position = {
    x: 100 + (editorNodes.value.length % 4) * 250,
    y: 100 + Math.floor(editorNodes.value.length / 4) * 150
  }
  addNode(newQuest, position)
  selectNode(tempId)
}

function handleKeyDelete(event) {
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
      selectNode(null)
    }
  }
}

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
    const currentEdgeIds = new Set(editorEdges.value.map(e => e.id))
    if (newEdgeIds.size !== currentEdgeIds.size || [...newEdgeIds].some(id => !currentEdgeIds.has(id))) {
      editorEdges.value = editorEdges.value.filter(e => newEdgeIds.has(e.id))
    }
  }
})

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

const selectedNode = computed(() => editorSelectedNode.value)

async function loadData() {
  try {
    const questsResponse = await QuestService.getAll()
    if (questsResponse.code === 0 && questsResponse.data) {
      sidebarQuests.value = questsResponse.data
    }
    
    const graphsResponse = await GraphService.getAll()
    if (graphsResponse.code === 0 && graphsResponse.data && graphsResponse.data.length > 0) {
      loadGraph(graphsResponse.data[0])
    } else {
      editorNodes.value = []
      editorEdges.value = []
    }
  } catch (error) {
    console.error('Failed to load data:', error)
  }
}

function handleDragOver(event: DragEvent) {
  event.dataTransfer!.dropEffect = 'copy'
}

function handleDragStart(event: DragEvent, quest: Quest) {
  event.dataTransfer?.setData('application/json', JSON.stringify(quest))
}

function handleSelectQuest(quest: Quest) {
  selectedQuestId.value = quest.id
  const graphsResponse = GraphService.getById(quest.id)
  graphsResponse.then(res => {
    if (res.code === 0 && res.data) {
      loadGraph(res.data)
    } else {
      editorNodes.value = []
      editorEdges.value = []
    }
  }).catch(() => {
    editorNodes.value = []
    editorEdges.value = []
  })
}

function handleNodeDragStart(event: DragEvent, nodeType: string) {
  event.dataTransfer?.setData('application/node-type', nodeType)
}

function handleDrop(event: DragEvent) {
  event.preventDefault()
  const questData = event.dataTransfer?.getData('application/json')
  const nodeType = event.dataTransfer?.getData('application/node-type')
  
  if (nodeType) {
    const rect = (event.target as HTMLElement).getBoundingClientRect()
    const position = project({
      x: event.clientX - rect.left,
      y: event.clientY - rect.top
    })
    addNode(nodeType as any, position)
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

function handleNodeClick(event) {
  selectNode(event.node.id)
}

function handlePaneClick() {
  selectNode(null)
}

function handleConnect(params) {
  addEdge(params.source, params.target)
}

function handleDeleteNode(nodeId: string) {
  removeNode(nodeId)
}

function handleUpdateQuest(nodeId: string, nodeData: EditorNodeData) {
  updateNode(nodeId, nodeData)
}

function getLocalizedText(obj: Record<string, string> | string | undefined, fallback: string = ''): string {
  if (!obj) return fallback
  if (typeof obj === 'string') return obj
  return obj['zh-CN'] || obj['en-US'] || fallback
}

function questToBackendFormat(quest: Quest): any {
  return {
    id: quest.id,
    name: getLocalizedText(quest.name),
    description: getLocalizedText(quest.description),
    taskType: quest.type || 'FOREVER',
    objectives: quest.objectives || [],
    rewards: quest.rewards || []
  }
}

async function handleSave() {
  const tempIds = [...unsavedQuestIds.value]
  if (tempIds.length > 0) {
    for (const tempId of tempIds) {
      const quest = sidebarQuests.value.find(q => q.id === tempId)
      if (!quest) continue
      
      try {
        const created = await QuestService.create(questToBackendFormat(quest))
        if (created.code === 0 && created.data) {
          const newId = created.data.id
          const oldId = tempId
          
          editorNodes.value = editorNodes.value.map(n => {
            if (n.id === oldId) {
              return { ...n, id: newId }
            }
            return n
          })
          
          edges.value = edges.value.map(e => ({
            ...e,
            id: e.id.replace(oldId, newId),
            source: e.source === oldId ? newId : e.source,
            target: e.target === oldId ? newId : e.target
          }))
          
          sidebarQuests.value = sidebarQuests.value.map(q => 
            q.id === oldId ? { ...q, id: newId } : q
          )
          
          unsavedQuestIds.value = new Set([...unsavedQuestIds.value].filter(id => id !== oldId))
        }
      } catch (error) {
        console.error(`Failed to create quest ${tempId}:`, error)
      }
    }
  }
  
  await saveGraph()
}

function handleLoadExamples(quests: Quest[]) {
  let index = editorNodes.value.length
  for (const quest of quests) {
    const position = {
      x: 100 + (index % 4) * 250,
      y: 100 + Math.floor(index / 4) * 150
    }
    addNode(quest, position)
    index++
  }
  sidebarQuests.value = [...sidebarQuests.value, ...quests]
  showExamplesDialog.value = false
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
  max-width: 350px;
  background: white;
  border-right: 1px solid #e5e7eb;
  padding: 1rem;
  overflow-y: auto;
}
.sidebar h3 {
  margin-top: 0;
  font-size: 0.9rem;
  color: #6b7280;
}
.sidebar-item {
  padding: 0.5rem;
  margin-bottom: 0.5rem;
  background: #f3f4f6;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.sidebar-item:hover {
  background: #e5e7eb;
}
.sidebar-item.selected-quest {
  background: #dbeafe;
  border: 1px solid #3b82f6;
}
.quest-name {
  font-size: 0.85rem;
  font-weight: 500;
}
.quest-type {
  font-size: 0.7rem;
  color: #9ca3af;
}
.node-item { border-left: 3px solid #8b5cf6; }
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
