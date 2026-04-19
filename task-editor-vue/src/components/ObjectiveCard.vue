<template>
  <div class="card">
    <div class="card-icon">🎯</div>
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
import type {ObjectiveTemplate} from '../types'

defineProps<{ template: ObjectiveTemplate }>()
defineEmits<{ edit: []; delete: [] }>()

const typeLabelMap: Record<string, string> = {
  kill_mob: '击杀生物',
  collect_item: '收集物品',
  break_block: '破坏方块',
  talk_to_npc: '与NPC对话',
  reach_location: '到达位置',
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
.type-label { margin: 0.25rem 0 0; color: #8b5cf6; font-size: 0.875rem; }
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
