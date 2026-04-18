<template>
  <div class="start-node">
    <div class="node-header">
      <span class="node-icon">▶</span>
      <span class="node-type">开始</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Start' }}</span>
    </div>
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import {Handle, Position} from '@vue-flow/core'

defineProps<{
  data: {
    name: string
    description?: string
  }
}>()
</script>

<style scoped>
.start-node {
  min-width: 140px;
  background: white;
  border: 2px solid #22c55e;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(34, 197, 94, 0.2);
}
.node-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: #dcfce7;
  border-bottom: 1px solid #bbf7d0;
}
.node-icon { font-size: 1rem; }
.node-type {
  font-size: 0.7rem;
  color: #166534;
}
.node-body {
  padding: 0.5rem 0.75rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #15803d;
}
</style>