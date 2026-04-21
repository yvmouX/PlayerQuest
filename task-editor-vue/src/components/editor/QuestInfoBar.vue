<template>
  <div v-if="quest" class="quest-info-bar">
    <div class="info-item">
      <label>任务 ID</label>
      <span class="quest-id">{{ quest.id }}</span>
    </div>
    <div class="info-item flex-2">
      <label>任务名称</label>
      <input
        type="text"
        :value="quest.name"
        @input="updateField('name', ($event.target as HTMLInputElement).value)"
        placeholder="输入任务名称"
      />
    </div>
    <div class="info-item flex-1">
      <label>任务分类</label>
      <input
        type="text"
        :value="quest.category || ''"
        @input="updateField('category', ($event.target as HTMLInputElement).value)"
        placeholder="输入分类"
      />
    </div>
    <div class="info-item flex-3">
      <label>任务描述</label>
      <input
        type="text"
        :value="quest.description"
        @input="updateField('description', ($event.target as HTMLInputElement).value)"
        placeholder="输入任务描述"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Quest } from '../../types'

const props = defineProps<{
  quest: Quest | null
}>()

const emit = defineEmits<{
  update: [quest: Quest]
}>()

function updateField(field: keyof Quest, value: string) {
  if (!props.quest) return
  emit('update', { ...props.quest, [field]: value })
}
</script>

<style scoped>
.quest-info-bar {
  background: #1e293b;
  padding: 12px 20px;
  display: flex;
  gap: 16px;
  align-items: center;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item.flex-1 { flex: 1; }
.info-item.flex-2 { flex: 2; }
.info-item.flex-3 { flex: 3; }

.info-item label {
  font-size: 10px;
  color: #94a3b8;
  text-transform: uppercase;
}

.quest-id {
  font-size: 12px;
  font-family: monospace;
  color: #e2e8f0;
  padding: 6px 0;
}

.info-item input {
  background: #334155;
  border: 1px solid #475569;
  border-radius: 4px;
  color: #e2e8f0;
  font-size: 13px;
  padding: 6px 10px;
  width: 100%;
}

.info-item input:focus {
  outline: none;
  border-color: #3b82f6;
}

.info-item input::placeholder {
  color: #64748b;
}
</style>
