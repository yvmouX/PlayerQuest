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
    </div>
    <div class="panel-footer">
      <button class="save-btn" @click="saveChanges">保存</button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import {ref, watch, computed} from 'vue'
import type {StartNodeData, TaskNodeData, CompletionNodeData, QuestObjective, QuestReward, ConditionData, BranchData, ActionData, ConditionItem} from '../../types'
import { editorNodes } from '../../composables/useQuestEditor'

type NodeData = StartNodeData | TaskNodeData | CompletionNodeData | ConditionData | BranchData | ActionData

const props = defineProps<{
  selectedNode: NodeData | null
}>()

const emit = defineEmits<{
  close: []
  update: [id: string, node: Partial<NodeData>]
}>()

const panelTitle = computed(() => {
  const titles: Record<string, string> = { start: 'Start 节点', task: '任务属性', completion: '完成节点', condition: '条件节点', branch: '分支节点', action: '动作节点' }
  return titles[props.selectedNode?.type || ''] || '属性'
})

const conditionNodes = computed(() => {
  return editorNodes.value.filter(n => n.nodeType === 'condition')
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
  switch (cond.conditionType) {
    case 'PERMISSION':
      cond.params = { permission: '' }
      break
    case 'HAS_ITEM':
      cond.params = { itemId: '', count: 1 }
      break
    case 'KILL_MOB':
      cond.params = { mobType: '', count: 1 }
      break
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
.reward-item,
.condition-item {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
  align-items: center;
}
.objective-item input,
.reward-item input,
.condition-item input,
.condition-item select {
  flex: 1;
  padding: 0.4rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 0.85rem;
}
.objective-item button,
.reward-item button,
.condition-item button {
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