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
          <label>描述</label>
          <textarea v-model="editedNode.description" rows="3" placeholder="描述..." />
        </div>
      </template>

      <!-- Trigger Node -->
      <template v-else-if="selectedNode.type === 'trigger'">
        <div class="form-group">
          <label>名称</label>
          <input v-model="editedNode.name" placeholder="触发条件名称" />
        </div>
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

      <!-- Task Node -->
      <template v-else-if="selectedNode.type === 'task'">
        <div class="form-group">
          <label>任务 ID</label>
          <input v-model="editedNode.id" readonly />
        </div>
        <div class="form-group">
          <label>名称</label>
          <input v-model="editedNode.name" placeholder="任务名称" />
        </div>
        <div class="form-group">
          <label>描述</label>
          <textarea v-model="editedNode.description" rows="3" placeholder="描述..." />
        </div>
        <div class="form-group">
          <label>类型</label>
          <select v-model="editedNode.taskType">
            <option value="CYCLE">循环任务</option>
            <option value="TIMER">定时任务</option>
            <option value="FOREVER">永久任务</option>
            <option value="LIMIT">限时任务</option>
            <option value="NONE">无特殊</option>
          </select>
        </div>
        <template v-if="editedNode.taskType === 'CYCLE' || editedNode.taskType === 'TIMER'">
          <div class="form-group">
            <label>重置间隔 (秒)</label>
            <input v-model.number="editedNode.resetInterval" type="number" min="1" placeholder="60" />
          </div>
        </template>
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
            <option value="">-- 选择模板（可选） --</option>
            <option v-for="t in objectiveTemplates" :key="t.id" :value="t.id">
              {{ t.name }} ({{ t.type }})
            </option>
          </select>
        </div>
        <div class="section-divider">
          <h4>自定义配置</h4>
        </div>
        <div class="form-group">
          <label>目标类型</label>
          <select v-model="editedNode.customConfig.type">
            <option value="kill_mob">击杀生物</option>
            <option value="collect_item">收集物品</option>
            <option value="break_block">破坏方块</option>
            <option value="talk_to_npc">与NPC对话</option>
            <option value="reach_location">到达位置</option>
          </select>
        </div>
        <template v-if="editedNode.customConfig.type === 'kill_mob' || editedNode.customConfig.type === 'collect_item' || editedNode.customConfig.type === 'break_block'">
          <div class="form-group">
            <label>目标标识</label>
            <input v-model="editedNode.customConfig.target" placeholder="如: ZOMBIE, DIAMOND, STONE" />
          </div>
          <div class="form-group">
            <label>数量</label>
            <input v-model.number="editedNode.customConfig.amount" type="number" min="1" placeholder="1" />
          </div>
        </template>
        <template v-else-if="editedNode.customConfig.type === 'talk_to_npc'">
          <div class="form-group">
            <label>NPC ID</label>
            <input v-model="editedNode.customConfig.target" placeholder="NPC标识" />
          </div>
        </template>
        <template v-else-if="editedNode.customConfig.type === 'reach_location'">
          <div class="form-group">
            <label>世界</label>
            <input v-model="editedNode.customConfig.location.world" placeholder="world" />
          </div>
          <div class="form-group">
            <label>X 坐标</label>
            <input v-model.number="editedNode.customConfig.location.x" type="number" placeholder="0" />
          </div>
          <div class="form-group">
            <label>Y 坐标</label>
            <input v-model.number="editedNode.customConfig.location.y" type="number" placeholder="64" />
          </div>
          <div class="form-group">
            <label>Z 坐标</label>
            <input v-model.number="editedNode.customConfig.location.z" type="number" placeholder="0" />
          </div>
        </template>
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
            <option value="">-- 选择模板（可选） --</option>
            <option v-for="t in actionTemplates" :key="t.id" :value="t.id">
              {{ t.name }} ({{ t.type }})
            </option>
          </select>
        </div>
        <div class="section-divider">
          <h4>自定义配置</h4>
        </div>
        <div class="form-group">
          <label>行为类型</label>
          <select v-model="editedNode.customConfig.type">
            <option value="give_item">发放物品</option>
            <option value="execute_command">执行命令</option>
            <option value="send_message">发送消息</option>
            <option value="play_effect">播放特效</option>
            <option value="sound">播放音效</option>
            <option value="give_xp">发放经验</option>
          </select>
        </div>
        <template v-if="editedNode.customConfig.type === 'give_item'">
          <div class="form-group">
            <label>物品ID</label>
            <input v-model="editedNode.customConfig.item" placeholder="minecraft:diamond" />
          </div>
          <div class="form-group">
            <label>数量</label>
            <input v-model.number="editedNode.customConfig.amount" type="number" min="1" placeholder="1" />
          </div>
        </template>
        <template v-else-if="editedNode.customConfig.type === 'execute_command'">
          <div class="form-group">
            <label>命令</label>
            <input v-model="editedNode.customConfig.command" placeholder="/say Hello %player%" />
          </div>
        </template>
        <template v-else-if="editedNode.customConfig.type === 'send_message'">
          <div class="form-group">
            <label>消息</label>
            <textarea v-model="editedNode.customConfig.message" rows="2" placeholder="消息内容..." />
          </div>
        </template>
        <template v-else-if="editedNode.customConfig.type === 'play_effect'">
          <div class="form-group">
            <label>特效类型</label>
            <input v-model="editedNode.customConfig.effect" placeholder="HEART" />
          </div>
        </template>
        <template v-else-if="editedNode.customConfig.type === 'sound'">
          <div class="form-group">
            <label>音效ID</label>
            <input v-model="editedNode.customConfig.sound" placeholder="entity.player.levelup" />
          </div>
          <div class="form-group">
            <label>音量</label>
            <input v-model.number="editedNode.customConfig.volume" type="number" placeholder="1.0" />
          </div>
          <div class="form-group">
            <label>音调</label>
            <input v-model.number="editedNode.customConfig.pitch" type="number" placeholder="1.0" />
          </div>
        </template>
        <template v-else-if="editedNode.customConfig.type === 'give_xp'">
          <div class="form-group">
            <label>经验值</label>
            <input v-model.number="editedNode.customConfig.xp" type="number" min="1" placeholder="100" />
          </div>
        </template>
      </template>

      <!-- Completion Node -->
      <template v-else-if="selectedNode.type === 'completion'">
        <div class="form-group">
          <label>名称</label>
          <input v-model="editedNode.name" placeholder="完成节点名称" />
        </div>
        <div class="form-group">
          <label>关联任务节点</label>
          <select v-model="editedNode.taskId">
            <option value="">-- 选择 Task --</option>
            <option v-for="n in taskNodes" :key="n.id" :value="n.id">
              {{ n.data.name || n.id }}
            </option>
          </select>
        </div>
        <div class="form-group">
          <label>回调消息</label>
          <textarea v-model="editedNode.callbackMessage" rows="2" placeholder="完成后发送的消息..." />
        </div>
      </template>
    </div>
  </aside>
</template>

<script setup lang="ts">
import {computed, ref, watch, onMounted} from 'vue'
import type {
  ActionData,
  ActionTemplate,
  CompletionNodeData,
  ObjectiveData,
  ObjectiveTemplate,
  StartNodeData,
  TaskNodeData,
  TriggerData
} from '../../types'
import {ObjectiveService, ActionService} from '../../services/api'
import {editorNodes} from '../../composables/useQuestEditor'

type NodeData = StartNodeData | TriggerData | TaskNodeData | ObjectiveData | ActionData | CompletionNodeData

const props = defineProps<{
  selectedNode: NodeData | null
}>()

const emit = defineEmits<{
  close: []
  update: [id: string, node: Partial<NodeData>]
}>()

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
    editedNode.value.customConfig = { ...template.defaultConfig }
  } else {
    editedNode.value.templateId = ''
  }
}

function onActionTemplateChange() {
  const template = actionTemplates.value.find(t => t.id === selectedActionTemplate.value)
  if (template) {
    editedNode.value.templateId = template.id
    editedNode.value.customConfig = { ...template.defaultConfig }
  } else {
    editedNode.value.templateId = ''
  }
}

const panelTitle = computed(() => {
  const titles: Record<string, string> = {
    start: 'Start 节点',
    trigger: '触发条件',
    task: '任务属性',
    objective: '目标',
    action: '行为',
    completion: '完成节点'
  }
  return titles[props.selectedNode?.type || ''] || '属性'
})

const taskNodes = computed(() => {
  return editorNodes.value.filter(n => n.nodeType === 'task')
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
      const nodeId = newNode.type === 'task' ? (newNode as TaskNodeData).id : props.selectedNode.id || ''
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
