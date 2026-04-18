<template>
  <div class="event-node">
    <div class="node-header">
      <span class="node-icon">⚡</span>
      <span class="node-type">事件</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Event' }}</span>
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
    eventTypes?: string[]
  }
}>()
</script>

<style scoped>
.event-node {
  min-width: 140px;
  background: white;
  border: 2px solid #3b82f6;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.2);
}
.node-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: #dbeafe;
  border-bottom: 1px solid #bfdbfe;
}
.node-icon { font-size: 1rem; }
.node-type {
  font-size: 0.7rem;
  color: #1e40af;
}
.node-body {
  padding: 0.5rem 0.75rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #1e3a8a;
}
</style>