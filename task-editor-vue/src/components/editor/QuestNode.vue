<template>
  <div class="quest-node" :class="`type-${data.type}`">
    <div class="node-header">
      <span class="node-type">{{ typeLabel }}</span>
      <button class="delete-btn" @click.stop="$emit('delete', data.id)">×</button>
    </div>
    <div class="node-body">
      <h3 class="node-name">{{ questName }}</h3>
      <p class="node-desc">{{ objectiveSummary }}</p>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import type { Quest } from '../../types'

const props = defineProps<{
  data: {
    id: string
    quest: Quest
  }
}>()

defineEmits<{
  delete: [id: string]
}>()

const questName = computed(() => {
  const names = props.data.quest?.name || {}
  return names['zh-CN'] || names['en-US'] || props.data.id
})

const typeLabel = computed(() => {
  const labels = { single: '单一', multi: '多阶段', series: '系列' }
  return labels[props.data.quest?.type] || '任务'
})

const objectiveSummary = computed(() => {
  const objectives = props.data.quest?.objectives || []
  if (objectives.length === 0) return '无目标'
  if (objectives.length === 1) return `1 个目标`
  return `${objectives.length} 个目标`
})
</script>

<style scoped>
.quest-node {
  min-width: 180px;
  background: white;
  border: 2px solid #3b82f6;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}
.quest-node.type-multi {
  border-color: #8b5cf6;
}
.quest-node.type-series {
  border-color: #f59e0b;
}
.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0.75rem;
  background: #f3f4f6;
  border-bottom: 1px solid #e5e7eb;
}
.node-type {
  font-size: 0.75rem;
  color: #6b7280;
}
.delete-btn {
  background: none;
  border: none;
  font-size: 1.2rem;
  cursor: pointer;
  color: #9ca3af;
}
.delete-btn:hover {
  color: #ef4444;
}
.node-body {
  padding: 0.75rem;
}
.node-name {
  font-size: 0.95rem;
  font-weight: 600;
  margin-bottom: 0.25rem;
}
.node-desc {
  font-size: 0.8rem;
  color: #6b7280;
}
</style>
