<template>
  <div class="card">
    <div class="card-icon">⚙️</div>
    <div class="card-body">
      <h3>{{ template.name || '未命名' }}</h3>
      <p class="type-label">{{ typeLabelMap[template.type] || template.type }}</p>
      <p class="desc-label" v-if="template.description">{{ template.description }}</p>
    </div>
    <div class="card-actions">
      <button @click="$emit('edit')">编辑</button>
      <button class="danger" @click="$emit('delete')">删除</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type {ActionTemplate} from '../types'

defineProps<{ template: ActionTemplate }>()
defineEmits<{ edit: []; delete: [] }>()

const typeLabelMap: Record<string, string> = {
  give_item: '发放物品',
  execute_command: '执行命令',
  send_message: '发送消息',
  play_effect: '播放特效',
  sound: '播放音效',
  give_xp: '发放经验',
  custom: '自定义'
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
.type-label { margin: 0.25rem 0 0; color: #f97316; font-size: 0.875rem; }
.desc-label { margin: 0.25rem 0 0; color: #666; font-size: 0.75rem; }
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
