<template>
  <div class="completion-node">
    <div class="node-header">
      <span class="node-icon">✓</span>
      <span class="node-type">完成</span>
      <button class="delete-btn" @click.stop="$emit('delete', nodeId)">×</button>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Completion' }}</span>
    </div>
    <Handle type="target" :position="Position.Left" class="handle-target" />
  </div>
</template>

<script setup lang="ts">
import {Handle, Position} from '@vue-flow/core'

defineProps<{
  data: {
    name: string
    callbackMessage?: string
  }
  nodeId: string
}>()

defineEmits<{
  delete: [nodeId: string]
}>()
</script>

<style scoped>
.completion-node {
  min-width: 140px;
  background: white;
  border: 2px solid #ef4444;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.2);
}
.node-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: #fee2e2;
  border-bottom: 1px solid #fecaca;
}
.node-icon { font-size: 1rem; }
.node-type {
  font-size: 0.7rem;
  color: #991b1b;
}
.delete-btn {
  margin-left: auto;
  background: none;
  border: none;
  font-size: 1rem;
  cursor: pointer;
  color: #991b1b;
  padding: 0 2px;
  line-height: 1;
}
.delete-btn:hover { color: #ef4444; }
.node-body {
  padding: 0.5rem 0.75rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #b91c1c;
}
.handle-target {
  width: 16px !important;
  height: 16px !important;
  background: #ef4444 !important;
  border: 2px solid #fff !important;
  border-radius: 50% !important;
}
.handle-target::after {
  content: '−';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #fff;
  font-size: 14px;
  font-weight: bold;
}
</style>