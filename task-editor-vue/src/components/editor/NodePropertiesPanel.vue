<template>
  <aside class="properties-panel" v-if="selectedNode && hasProperties">
    <div class="panel-header">
      <h3>{{ panelTitle }}</h3>
      <button class="close-btn" @click="$emit('close')">×</button>
    </div>
    <div class="panel-body">
      <!-- Trigger Node -->
      <template v-if="selectedNode.type === 'trigger'">
        <div class="form-group">
          <label>条件类型</label>
          <select v-model="editedNode.conditionType">
            <option value="quest_complete">前置任务完成</option>
            <option value="permission">权限检查</option>
            <option value="npc_interact">NPC 对话</option>
          </select>
        </div>
        <template v-if="editedNode.conditionType === 'quest_complete'">
          <div class="form-group">
            <label>任务 ID</label>
            <input v-model="editedNode.conditionConfig.questId" placeholder="前置任务ID" />
          </div>
        </template>
        <template v-else-if="editedNode.conditionType === 'permission'">
          <div class="form-group">
            <label>权限节点</label>
            <input v-model="editedNode.conditionConfig.permission" placeholder="example.permission" />
          </div>
        </template>
        <template v-else-if="editedNode.conditionType === 'npc_interact'">
          <div class="form-group">
            <label>NPC ID</label>
            <input v-model="editedNode.conditionConfig.npcId" placeholder="NPC标识" />
          </div>
        </template>
      </template>

      <!-- Objective Node -->
      <template v-else-if="selectedNode.type === 'objective'">
        <div class="form-group">
          <label>名称</label>
          <input v-model="editedNode.name" placeholder="目标名称" />
        </div>
        <div class="form-group">
          <label>目标库模板</label>
          <select v-model="selectedObjectiveTemplate" @change="onObjectiveTemplateChange">
            <option value="">-- 选择模板（必选） --</option>
            <option v-for="t in objectiveTemplates" :key="t.id" :value="t.id">
              {{ t.name }} ({{ t.type }})
            </option>
          </select>
        </div>
      </template>

      <!-- Action Node -->
      <template v-else-if="selectedNode.type === 'action'">
        <div class="form-group">
          <label>名称</label>
          <input v-model="editedNode.name" placeholder="行为名称" />
        </div>
        <div class="form-group">
          <label>行为库模板</label>
          <select v-model="selectedActionTemplate" @change="onActionTemplateChange">
            <option value="">-- 选择模板（必选） --</option>
            <option v-for="t in actionTemplates" :key="t.id" :value="t.id">
              {{ t.name }} ({{ t.type }})
            </option>
          </select>
        </div>
      </template>
    </div>
  </aside>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import type {
  ActionData,
  ActionTemplate,
  CompletionNodeData,
  ObjectiveData,
  ObjectiveTemplate,
  StartNodeData,
  TriggerData
} from '../../types'
import {ActionService, ObjectiveService} from '../../services/api'

type NodeData = StartNodeData | TriggerData | ObjectiveData | ActionData | CompletionNodeData

const props = defineProps<{
  selectedNode: NodeData | null
  nodeId: string | null
}>()

const emit = defineEmits<{
  close: []
  update: [id: string, node: Partial<NodeData>]
}>()

const hasProperties = computed(() => {
  if (!props.selectedNode) return false
  const type = props.selectedNode.type
  return type !== 'start' && type !== 'completion'
})

const objectiveTemplates = ref<ObjectiveTemplate[]>([])
const actionTemplates = ref<ActionTemplate[]>([])
const selectedObjectiveTemplate = ref('')
const selectedActionTemplate = ref('')

onMounted(async () => {
  try {
    const objResp = await ObjectiveService.getTemplates()
    if (objResp.code === 0) {
      objectiveTemplates.value = objResp.data || []
    }
  } catch (e) {
    console.error('Failed to load objective templates:', e)
  }
  try {
    const actResp = await ActionService.getTemplates()
    if (actResp.code === 0) {
      actionTemplates.value = actResp.data || []
    }
  } catch (e) {
    console.error('Failed to load action templates:', e)
  }
})

function onObjectiveTemplateChange() {
  const template = objectiveTemplates.value.find(t => t.id === selectedObjectiveTemplate.value)
  if (template) {
    editedNode.value.templateId = template.id
  } else {
    editedNode.value.templateId = ''
  }
}

function onActionTemplateChange() {
  const template = actionTemplates.value.find(t => t.id === selectedActionTemplate.value)
  if (template) {
    editedNode.value.templateId = template.id
  } else {
    editedNode.value.templateId = ''
  }
}

const panelTitle = computed(() => {
  const titles: Record<string, string> = {
    start: 'Start 节点',
    trigger: '触发条件',
    objective: '目标',
    action: '行为',
    completion: '完成节点'
  }
  return titles[props.selectedNode?.type || ''] || '属性'
})

const editedNode = ref<NodeData>(createDefaultNode())
let saveTimeout: number | null = null
let lastEmittedNode: string | null = null

function createDefaultNode(): NodeData {
  return {
    type: 'start',
    description: ''
  } as NodeData
}

watch(() => props.selectedNode, (node) => {
  if (node) {
    editedNode.value = JSON.parse(JSON.stringify(node))
    lastEmittedNode = JSON.stringify(editedNode.value)
    if (node.type === 'objective') {
      selectedObjectiveTemplate.value = (node as ObjectiveData).templateId || ''
    } else if (node.type === 'action') {
      selectedActionTemplate.value = (node as ActionData).templateId || ''
    }
  }
}, { immediate: true })

watch(editedNode, () => {
  if (props.selectedNode) {
    const nodeData = JSON.stringify(editedNode.value)
    if (nodeData === lastEmittedNode) return
    
    if (saveTimeout) clearTimeout(saveTimeout)
    saveTimeout = window.setTimeout(() => {
      const newNode = editedNode.value
      const nodeId = props.nodeId || ''
      lastEmittedNode = JSON.stringify(editedNode.value)
      emit('update', nodeId, editedNode.value)
    }, 300)
  }
}, { deep: true })
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
</style>
