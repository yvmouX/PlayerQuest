<template>
  <div class="action-node">
    <div class="node-header">
      <span class="node-icon">⚙️</span>
      <span class="node-type">行为</span>
      <button class="delete-btn" @click.stop="$emit('delete', nodeId)">×</button>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Action' }}</span>
      <span class="node-template" v-if="data.templateId">📚 {{ data.templateId }}</span>
    </div>
    <Handle type="target" :position="Position.Left" class="handle-target" />
    <Handle type="source" :position="Position.Right" class="handle-source" />
  </div>
</template>

<script setup lang="ts">
import {Handle, Position} from '@vue-flow/core'
import type {ActionData} from '../../types'

const props = defineProps<{
  data: ActionData
  nodeId: string
}>()

defineEmits<{
  delete: [nodeId: string]
}>()
</script>

<style scoped>
.action-node {
  width: 160px;
  background: white;
  border: 2px solid #f97316;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(249, 115, 22, 0.2);
}
.node-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: #ffedd5;
  border-bottom: 1px solid #fed7aa;
}
.node-icon { font-size: 1rem; }
.node-type {
  font-size: 0.7rem;
  color: #9a3412;
}
.delete-btn {
  margin-left: auto;
  background: none;
  border: none;
  font-size: 1rem;
  cursor: pointer;
  color: #9a3412;
  padding: 0 2px;
  line-height: 1;
}
.delete-btn:hover { color: #ef4444; }
.node-body {
  padding: 0.5rem 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #7c2d12;
}
.node-template {
  font-size: 0.7rem;
  color: #c2410c;
}
.handle-target {
  width: 16px !important;
  height: 16px !important;
  background: #f97316 !important;
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
.handle-source {
  width: 16px !important;
  height: 16px !important;
  background: #f97316 !important;
  border: 2px solid #fff !important;
  border-radius: 50% !important;
}
.handle-source::after {
  content: '+';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #fff;
  font-size: 12px;
  font-weight: bold;
}
</style>
