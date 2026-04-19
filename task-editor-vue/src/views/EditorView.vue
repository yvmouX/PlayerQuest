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
          @dblclick.stop="startEditQuestName(quest)"
        >
          <input
            v-if="editingQuestId === quest.id"
            v-model="editingQuestName"
            class="quest-name-input"
            @blur="saveQuestName"
            @keydown.enter="saveQuestName"
            @keydown.escape="cancelEditQuestName"
            @click.stop
            ref="questNameInput"
          />
          <span v-else class="quest-name" :class="{ 'unsaved': unsavedQuests.has(quest.id) }">
            {{ quest.name || '未命名任务' }}<template v-if="unsavedQuests.has(quest.id)">（未保存）</template>
          </span>
          <span v-if="selectedQuestId === quest.id" class="current-badge">当前</span>
        </div>
        <button @click="handleCreateNewQuest" class="btn-new-quest">+ 新建任务</button>

        <h3>节点工具</h3>
        <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'start')">
          <span>▶ Start</span>
        </div>
        <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'trigger')">
          <span>⚡ Trigger</span>
        </div>
        <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'task')">
          <span>📋 Task</span>
        </div>
        <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'objective')">
          <span>🎯 Objective</span>
        </div>
        <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'action')">
          <span>⚙️ Action</span>
        </div>
        <div class="sidebar-item node-item" draggable="true" @dragstart="(e) => handleNodeDragStart(e, 'completion')">
          <span>✔ Completion</span>
        </div>
        
        <h3>工具</h3>
        <div class="tool-selector">
          <button 
            class="tool-btn" 
            :class="{ active: currentTool === 'select' }"
            @click="setTool('select')"
          >
            🖐️ 选择
          </button>
          <button 
            class="tool-btn" 
            :class="{ active: currentTool === 'cut' }"
            @click="setTool('cut')"
          >
            ✂️ 切割
          </button>
        </div>
        <div class="tool-hint">
          <span v-if="currentTool === 'cut'">按住左键拖动切割连线</span>
          <span v-else>按住 Shift + 左键拖动切割连线</span>
        </div>
</aside>
       
      <VueFlow
        ref="vueFlowRef"
        v-model:nodes="nodes"
        v-model:edges="edges"
        :default-viewport="{ zoom: 1 }"
        :class="{ 'cut-mode': isCutMode }"
        @node-click="handleNodeClick"
        @pane-click="handlePaneClick"
        @connect="handleConnect"
        @node-drag-stop="handleNodeDragStop"
        @edge-click="(e) => { selectedEdgeForDelete = e.edge.id; showDeleteEdgeConfirm = true }"
        @pane-ready="onPaneReady"
      >
        <Background pattern-color="#aaa" :gap="16" />
        <Controls />
        
        <svg v-if="isCutDrawing && cutLineStart && cutLineEnd" class="cut-line-svg">
          <line 
            :x1="cutLineStart.x" 
            :y1="cutLineStart.y" 
            :x2="cutLineEnd.x" 
            :y2="cutLineEnd.y" 
            stroke="#ef4444" 
            stroke-width="2" 
            stroke-dasharray="5,5"
          />
        </svg>
        
        <div v-if="!selectedQuestId" class="empty-state-overlay">
          <div class="empty-state-text">点击左侧任务列表中的任务进入编辑</div>
        </div>
        
        <template #node-task="{ data, id }">
          <TaskNode :data="data" :node-id="id" @delete="handleDeleteNode" />
        </template>
        
        <template #node-start="{ data, id }">
          <StartNode :data="data" :node-id="id" @delete="handleDeleteNode" />
        </template>

        <template #node-completion="{ data, id }">
          <CompletionNode :data="data" :node-id="id" @delete="handleDeleteNode" />
        </template>

        <template #node-trigger="{ data, id }">
          <TriggerNode :data="data" :node-id="id" @delete="handleDeleteNode" />
        </template>

        <template #node-objective="{ data, id }">
          <ObjectiveNode :data="data" :node-id="id" @delete="handleDeleteNode" />
        </template>

        <template #node-action="{ data, id }">
          <ActionNode :data="data" :node-id="id" @delete="handleDeleteNode" />
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
import {computed, nextTick, onMounted, onUnmounted, ref, watch} from 'vue'
import {useRouter, onBeforeRouteLeave} from 'vue-router'
import {VueFlow, useVueFlow} from '@vue-flow/core'
import {Background} from '@vue-flow/background'
import {Controls} from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import Header from '../components/layout/Header.vue'
import TaskNode from '../components/editor/TaskNode.vue'
import StartNode from '../components/editor/StartNode.vue'
import CompletionNode from '../components/editor/CompletionNode.vue'
import TriggerNode from '../components/editor/TriggerNode.vue'
import ObjectiveNode from '../components/editor/ObjectiveNode.vue'
import ActionNode from '../components/editor/ActionNode.vue'
import NodePropertiesPanel from '../components/editor/NodePropertiesPanel.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import ExampleQuestsDialog from '../components/editor/ExampleQuestsDialog.vue'
import EditorHelpDialog from '../components/editor/EditorHelpDialog.vue'
import Toast from '../components/Toast.vue'
import {useQuestEditor} from '../composables/useQuestEditor'
import {setToast, useToast} from '../composables/useToast'
import {QuestService} from '../services/api'
import type {EditorNodeData, Quest, NodeType} from '../types'

const {
  nodes,
  edges,
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

const vueFlowRef = ref<any>(null)

const showExamplesDialog = ref(false)
const showHelpDialog = ref(false)
const sidebarQuests = ref<Quest[]>([])
const selectedQuestId = ref<string | null>(null)
const showDeleteEdgeConfirm = ref(false)
const selectedEdgeForDelete = ref<string | null>(null)
const toastRef = ref<InstanceType<typeof Toast> | null>(null)
const unsavedQuests = ref<Map<string, Quest>>(new Map())
const editingQuestId = ref<string | null>(null)
const editingQuestName = ref('')
const questNameInput = ref<HTMLInputElement | null>(null)

// Tool state
const currentTool = ref<'select' | 'cut'>('select')
const isShiftPressed = ref(false)
const isCutDrawing = ref(false)
const cutLineStart = ref<{ x: number; y: number } | null>(null)
const cutLineEnd = ref<{ x: number; y: number } | null>(null)

const isCutMode = computed(() => currentTool.value === 'cut' || isShiftPressed.value)

function setTool(tool: 'select' | 'cut') {
  currentTool.value = tool
}

function handleKeyDown(event: KeyboardEvent) {
  if (event.key === 'Shift') {
    isShiftPressed.value = true
  }
}

function handleKeyUp(event: KeyboardEvent) {
  if (event.key === 'Shift') {
    isShiftPressed.value = false
    isCutDrawing.value = false
    cutLineStart.value = null
    cutLineEnd.value = null
  }
}

const hasUnsavedChanges = computed(() => unsavedQuests.value.has(selectedQuestId.value || ''))

const unsavedQuestsSet = computed(() => new Set(unsavedQuests.value.keys()))

const router = useRouter()

let leaveConfirmed = false

onBeforeRouteLeave(() => {
  if (leaveConfirmed) return true
  if (unsavedQuests.value.size > 0) {
    const confirmed = window.confirm('有未保存的更改，确定要离开吗？')
    if (confirmed) {
      leaveConfirmed = true
      return true
    }
    return false
  }
  return true
})

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
  if (editingQuestId.value) {
    cancelEditQuestName()
  }
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
      nodes: nodes.value.map(n => ({
        id: n.id,
        nodeType: n.type,
        x: n.position.x,
        y: n.position.y,
        data: n.data
      })),
      edges: edges.value.map(e => ({
        id: e.id,
        sourceId: e.source,
        targetId: e.target,
        label: e.label
      }))
    })
  }
  
  unsavedQuests.value.delete(quest.id)
}

function startEditQuestName(quest: Quest) {
  editingQuestId.value = quest.id
  editingQuestName.value = quest.name || ''
  setTimeout(() => {
    questNameInput.value?.focus()
    questNameInput.value?.select()
  }, 10)
}

async function saveQuestName() {
  if (!editingQuestId.value) return
  
  const questId = editingQuestId.value
  const newName = editingQuestName.value.trim()
  const quest = sidebarQuests.value.find(q => q.id === questId)
  
  if (!quest) {
    cancelEditQuestName()
    return
  }
  
  if (newName === quest.name) {
    cancelEditQuestName()
    return
  }
  
  try {
    const updatedQuest: Quest = { ...quest, name: newName }
    const response = await QuestService.update(questId, updatedQuest)
    
    if (response.code === 0 && response.data) {
      sidebarQuests.value = sidebarQuests.value.map(q => 
        q.id === questId ? response.data : q
      )
      useToast().success('任务名称已更新')
    } else {
      useToast().error('更新失败: ' + response.msg)
    }
  } catch (error) {
    console.error('Failed to update quest name:', error)
    useToast().error('更新失败')
  }
  
  editingQuestId.value = null
  editingQuestName.value = ''
}

function cancelEditQuestName() {
  editingQuestId.value = null
  editingQuestName.value = ''
}

function handleNodeDragStart(event: DragEvent, nodeType: string) {
  event.dataTransfer?.setData('application/node-type', nodeType)
}

function handleDragOver(event: DragEvent) {
  event.dataTransfer.dropEffect = 'copy'
}

function handleDrop(event: DragEvent) {
  event.preventDefault()
  const nodeType = event.dataTransfer?.getData('application/node-type')
  
  if (!nodeType || !selectedQuestId.value) {
    useToast().error('请先选择一个任务')
    return
  }
  
  if ((nodeType === 'start' || nodeType === 'task' || nodeType === 'completion') && nodes.value.some(n => n.nodeType === nodeType)) {
    const names: Record<string, string> = { start: '开始', task: '任务', completion: '完成' }
    useToast().error(`${names[nodeType]}节点已存在，每个流程只能有一个`)
    return
  }
  
  const target = event.currentTarget as HTMLElement
  const vueFlowEl = target.querySelector('.vue-flow') as HTMLElement
  if (vueFlowEl) {
    const rect = vueFlowEl.getBoundingClientRect()
    const position = project({
      x: event.clientX - rect.left,
      y: event.clientY - rect.top
    })
    
    addNode(nodeType as NodeType, position)
    nextTick(() => {
      const graph = getCurrentQuestGraph()
      if (graph) {
        unsavedQuests.value.set(graph.questId, {
          ...sidebarQuests.value.find(q => q.id === graph.questId)!,
          graph: graph.graph
        })
      }
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
  nextTick(() => {
    const graph = getCurrentQuestGraph()
    if (graph) {
      unsavedQuests.value.set(graph.questId, {
        ...sidebarQuests.value.find(q => q.id === graph.questId)!,
        graph: graph.graph
      })
    }
  })
}

function handleNodeDragStop() {
  const questId = selectedQuestId.value
  if (!questId) return
  const graph = getCurrentQuestGraph()
  if (graph) {
    unsavedQuests.value.set(questId, {
      ...sidebarQuests.value.find(q => q.id === questId)!,
      graph: graph.graph
    })
  }
}

function onPaneReady() {
  if (!vueFlowRef.value) return
  const el = vueFlowRef.value.$el as HTMLElement
  el.addEventListener('mousedown', handleCutMouseDown)
  el.addEventListener('mousemove', handleCutMouseMove)
  el.addEventListener('mouseup', handleCutMouseUp)
}

function handleCutMouseDown(event: MouseEvent) {
  if (!isCutMode.value) return
  if ((event.target as HTMLElement).closest('.vue-flow-node')) return
  
  const rect = vueFlowRef.value.$el.getBoundingClientRect()
  const pos = project({
    x: event.clientX - rect.left,
    y: event.clientY - rect.top
  })
  
  isCutDrawing.value = true
  cutLineStart.value = pos
  cutLineEnd.value = pos
}

function handleCutMouseMove(event: MouseEvent) {
  if (!isCutDrawing.value) return
  
  const rect = vueFlowRef.value.$el.getBoundingClientRect()
  cutLineEnd.value = project({
    x: event.clientX - rect.left,
    y: event.clientY - rect.top
  })
}

function handleCutMouseUp() {
  if (!isCutDrawing.value || !cutLineStart.value || !cutLineEnd.value) {
    isCutDrawing.value = false
    return
  }
  
  const edgesToRemove: string[] = []
  
  edges.value.forEach(edge => {
    const sourceNode = nodes.value.find(n => n.id === edge.source)
    const targetNode = nodes.value.find(n => n.id === edge.target)
    if (!sourceNode || !targetNode) return
    
    if (linesIntersect(
      cutLineStart.value.x, cutLineStart.value.y,
      cutLineEnd.value.x, cutLineEnd.value.y,
      sourceNode.position.x + 75, sourceNode.position.y + 25,
      targetNode.position.x + 75, targetNode.position.y + 25
    )) {
      edgesToRemove.push(edge.id)
    }
  })
  
  if (edgesToRemove.length > 0) {
    edgesToRemove.forEach(id => removeEdge(id))
    useToast().success(`已切割 ${edgesToRemove.length} 条连线`)
    
    const questId = selectedQuestId.value
    if (questId) {
      const graph = getCurrentQuestGraph()
      if (graph) {
        unsavedQuests.value.set(questId, {
          ...sidebarQuests.value.find(q => q.id === questId)!,
          graph: graph.graph
        })
      }
    }
  }
  
  isCutDrawing.value = false
  cutLineStart.value = null
  cutLineEnd.value = null
}

function linesIntersect(x1: number, y1: number, x2: number, y2: number, x3: number, y3: number, x4: number, y4: number): boolean {
  const denom = (y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1)
  if (Math.abs(denom) < 0.0001) return false
  
  const ua = ((x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3)) / denom
  const ub = ((x2 - x1) * (y1 - y3) - (y2 - y1) * (x1 - x3)) / denom
  
  return ua > 0.01 && ua < 0.99 && ub > 0.01 && ub < 0.99
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

onMounted(() => {
  loadData()
  window.addEventListener('keydown', handleKeyDelete)
  window.addEventListener('keydown', handleKeyDown)
  window.addEventListener('keyup', handleKeyUp)
  if (toastRef.value) {
    setToast(toastRef)
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDelete)
  window.removeEventListener('keydown', handleKeyDown)
  window.removeEventListener('keyup', handleKeyUp)
  if (vueFlowRef.value) {
    const el = vueFlowRef.value.$el as HTMLElement
    el.removeEventListener('mousedown', handleCutMouseDown)
    el.removeEventListener('mousemove', handleCutMouseMove)
    el.removeEventListener('mouseup', handleCutMouseUp)
  }
  clearEditor()
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
.quest-name.unsaved {
  font-style: italic;
  color: #f97316;
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
.quest-name-input {
  flex: 1;
  padding: 2px 4px;
  border: 1px solid #3b82f6;
  border-radius: 4px;
  font-size: 0.85rem;
  outline: none;
  min-width: 0;
}
.empty-state-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  z-index: 5;
}
.empty-state-text {
  font-size: 1.5rem;
  color: #9ca3af;
  text-align: center;
  padding: 2rem;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 8px;
}
.tool-selector {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: #f3f4f6;
  border-radius: 6px;
}
.tool-btn {
  flex: 1;
  padding: 6px 8px;
  border: none;
  background: white;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.8rem;
  transition: all 0.2s;
}
.tool-btn:hover {
  background: #e5e7eb;
}
.tool-btn.active {
  background: #3b82f6;
  color: white;
}
.tool-hint {
  font-size: 0.7rem;
  color: #6b7280;
  text-align: center;
  margin-top: 4px;
}
.vue-flow.cut-mode {
  cursor: crosshair;
}
.vue-flow.cut-mode .vue-flow-edge {
  pointer-events: stroke;
}
.cut-line-svg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 10;
}
</style>