<template>
  <div class="editor-view">
    <Header title="任务编辑器">
      <template #actions>
        <button @click="handleSave" class="btn-primary">保存</button>
        <button @click="handleAddQuest" class="btn-secondary">新建任务</button>
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
          draggable="true"
          @dragstart="(e) => handleDragStart(e, quest)"
        >
          <span class="quest-name">{{ quest.name['zh-CN'] || quest.name['en-US'] || quest.id }}</span>
          <span class="quest-type">{{ quest.type }}</span>
        </div>
      </aside>
      
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
      
      <CreateQuestDialog
        v-if="showCreateDialog"
        @close="showCreateDialog = false"
        @create="handleCreateQuest"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useVueFlow, VueFlow} from '@vue-flow/core'
import {Background} from '@vue-flow/background'
import {Controls} from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import Header from '../components/layout/Header.vue'
import QuestNode from '../components/editor/QuestNode.vue'
import PropertiesPanel from '../components/editor/PropertiesPanel.vue'
import CreateQuestDialog from '../components/editor/CreateQuestDialog.vue'
import {useQuestEditor} from '../composables/useQuestEditor'
import {QuestService} from '../services/api'
import type {Quest} from '../types'

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

const showCreateDialog = ref(false)
const sidebarQuests = ref<Quest[]>([])

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
  }
})

const selectedNode = computed(() => editorSelectedNode.value)

async function loadData() {
  try {
    const response = await QuestService.getAll()
    if (response.code === 0 && response.data) {
      loadQuests(response.data)
      sidebarQuests.value = response.data
    }
  } catch (error) {
    console.error('Failed to load quests:', error)
  }
}

function handleDragOver(event: DragEvent) {
  event.dataTransfer!.dropEffect = 'copy'
}

function handleDragStart(event: DragEvent, quest: Quest) {
  event.dataTransfer?.setData('application/json', JSON.stringify(quest))
}

function handleDrop(event: DragEvent) {
  event.preventDefault()
  const questData = event.dataTransfer?.getData('application/json')
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
  editorSelectedNode.value = event.node.id
}

function handlePaneClick() {
  editorSelectedNode.value = null
}

function handleConnect(params) {
  addEdge(params.source, params.target)
}

function handleEdgeClick(event) {
}

function handleDeleteNode(nodeId: string) {
  removeNode(nodeId)
}

function handleUpdateQuest(nodeId: string, quest: Partial<Quest>) {
  updateNode(nodeId, quest)
}

function handleAddQuest() {
  showCreateDialog.value = true
}

function handleCreateQuest(quest: Quest) {
  const center = project({ x: 400, y: 300 })
  addNode(quest, center)
  showCreateDialog.value = false
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
  display: flex;
}
.sidebar {
  width: 200px;
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
  cursor: grab;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.sidebar-item:hover {
  background: #e5e7eb;
}
.quest-name {
  font-size: 0.85rem;
  font-weight: 500;
}
.quest-type {
  font-size: 0.7rem;
  color: #9ca3af;
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
