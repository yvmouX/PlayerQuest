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
import {ref} from 'vue'
import type {Quest} from '../../types'

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
