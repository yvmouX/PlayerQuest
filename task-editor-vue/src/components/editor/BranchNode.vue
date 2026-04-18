<template>
  <div class="branch-node">
    <div class="node-header">
      <span class="node-icon">⬡</span>
      <span class="node-type">分支</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Branch' }}</span>
    </div>
    <div class="node-outputs">
      <div class="output-true">TRUE</div>
      <div class="output-false">FALSE</div>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle id="true" type="source" :position="Position.Right" style="top: 60%" />
    <Handle id="false" type="source" :position="Position.Right" style="top: 85%" />
  </div>
</template>

<script setup lang="ts">
import {Handle, Position} from '@vue-flow/core'

defineProps<{
  data: {
    name: string
    linkedConditionId?: string
  }
}>()
</script>

<style scoped>
.branch-node {
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
.node-outputs {
  display: flex;
  gap: 0.5rem;
  padding: 0 0.75rem 0.5rem;
}
.node-outputs > div {
  font-size: 0.65rem;
  font-weight: bold;
  padding: 0.1rem 0.3rem;
  border-radius: 3px;
}
.output-true { color: #22c55e; background: #dcfce7; }
.output-false { color: #ef4444; background: #fee2e2; }
</style>