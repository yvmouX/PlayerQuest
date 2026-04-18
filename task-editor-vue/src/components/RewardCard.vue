<template>
  <div class="card">
    <div class="card-icon">{{ iconMap[template.type] }}</div>
    <div class="card-body">
      <h3>{{ template.name || '未命名' }}</h3>
      <p class="type-label">{{ typeLabelMap[template.type] }}</p>
      <p class="value-label">值: {{ template.value || template.meta?.content || '-' }}</p>
    </div>
    <div class="card-actions">
      <button @click="$emit('edit')">编辑</button>
      <button class="danger" @click="$emit('delete')">删除</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type {RewardTemplate} from '../types'

defineProps<{ template: RewardTemplate }>()
defineEmits<{ edit: []; delete: [] }>()

const iconMap: Record<string, string> = {
  item: '📦', exp: '⭐', money: '💰', command: '⚡'
}
const typeLabelMap: Record<string, string> = {
  item: '物品', exp: '经验', money: '金币', command: '命令'
}
</script>

<style scoped>
.card {
  background: white;
  border-radius: 8px;
  padding: 1rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  display: flex;
  align-items: center;
  gap: 1rem;
}
.card-icon { font-size: 2rem; }
.card-body { flex: 1; }
.card-body h3 { margin: 0; font-size: 1rem; }
.type-label { margin: 0.25rem 0 0; color: #666; font-size: 0.875rem; }
.card-actions { display: flex; gap: 0.5rem; }
.card-actions button {
  padding: 0.25rem 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}
.card-actions button.danger {
  background: #dc3545;
  color: white;
  border-color: #dc3545;
}
</style>
