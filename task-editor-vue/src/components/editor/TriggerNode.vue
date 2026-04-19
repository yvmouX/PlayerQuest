<template>
  <div class="trigger-node">
    <div class="node-header">
      <span class="node-icon">⚡</span>
      <span class="node-type">触发</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Trigger' }}</span>
      <span class="node-condition">{{ conditionLabel }}</span>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {Handle, Position} from '@vue-flow/core'
import type {TriggerData} from '../../types'

const props = defineProps<{
  data: TriggerData
}>()

const conditionLabel = computed(() => {
  const labels: Record<string, string> = {
    'quest_complete': '任务完成',
    'permission': '权限检查',
    'npc_interact': 'NPC 对话'
  }
  return labels[props.data.conditionType] || props.data.conditionType || ''
})
</script>

<style scoped>
.trigger-node {
  min-width: 160px;
  background: white;
  border: 2px solid #eab308;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(234, 179, 8, 0.2);
}
.node-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: #fef9c3;
  border-bottom: 1px solid #fde68a;
}
.node-icon { font-size: 1rem; }
.node-type {
  font-size: 0.7rem;
  color: #854d0e;
}
.node-body {
  padding: 0.5rem 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #713f12;
}
.node-condition {
  font-size: 0.7rem;
  color: #a16207;
}
</style>
