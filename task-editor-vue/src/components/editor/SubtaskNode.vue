<template>
  <div class="subtask-node">
    <div class="node-header">
      <span class="node-icon">📁</span>
      <span class="node-type">子任务</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Subtask' }}</span>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import {Handle, Position} from '@vue-flow/core'

defineProps<{
  data: {
    name: string
  }
}>()
</script>

<style scoped>
.subtask-node {
  min-width: 160px;
  background: rgba(249, 250, 251, 0.5);
  border: 2px dashed #9ca3af;
  border-radius: 8px;
  overflow: hidden;
}
.node-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: #f9fafb;
  border-bottom: 1px dashed #d1d5db;
}
.node-icon { font-size: 1rem; }
.node-type {
  font-size: 0.7rem;
  color: #6b7280;
}
.node-body {
  padding: 0.5rem 0.75rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #374151;
}
</style>