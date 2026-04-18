<template>
  <div class="state-node">
    <div class="node-header">
      <span class="node-icon">🔧</span>
      <span class="node-type">{{ operationLabel }}</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'State' }}</span>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {Handle, Position} from '@vue-flow/core'

const props = defineProps<{
  data: {
    name: string
    operation?: string
  }
}>()

const operationLabel = computed(() => {
  const labels: Record<string, string> = {
    COMPLETE_TASK: '完成',
    FAIL_TASK: '失败',
    RESET_TASK: '重置',
    SET_PLAYER_STATE: '设置状态'
  }
  return labels[props.data.operation] || '状态'
})
</script>

<style scoped>
.state-node {
  min-width: 140px;
  background: white;
  border: 2px solid #6b7280;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(107, 114, 128, 0.2);
}
.node-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: #f3f4f6;
  border-bottom: 1px solid #e5e7eb;
}
.node-icon { font-size: 1rem; }
.node-type {
  font-size: 0.7rem;
  color: #374151;
}
.node-body {
  padding: 0.5rem 0.75rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #4b5563;
}
</style>