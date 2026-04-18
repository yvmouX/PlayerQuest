<template>
  <div class="condition-node">
    <div class="node-header">
      <span class="node-icon">◇</span>
      <span class="node-type">条件</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Condition' }}</span>
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
    conditions?: any[]
  }
}>()
</script>

<style scoped>
.condition-node {
  min-width: 160px;
  background: white;
  border: 2px solid #8b5cf6;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(139, 92, 246, 0.2);
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
  color: #6b7280;
}
.node-body {
  padding: 0.5rem 0.75rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #4c1d95;
}
</style>