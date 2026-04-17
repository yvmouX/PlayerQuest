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
