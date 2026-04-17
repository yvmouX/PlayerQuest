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
