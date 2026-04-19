<template>
  <div class="objective-node">
    <div class="node-header">
      <span class="node-icon">🎯</span>
      <span class="node-type">目标</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Objective' }}</span>
      <span class="node-template" v-if="data.templateId">📚 {{ data.templateId }}</span>
    </div>
    <Handle type="target" :position="Position.Left" class="handle-target" />
    <Handle type="source" :position="Position.Right" class="handle-source" />
  </div>
</template>

<script setup lang="ts">
import {Handle, Position} from '@vue-flow/core'
import type {ObjectiveData} from '../../types'

defineProps<{
  data: ObjectiveData
}>()
</script>

<style scoped>
.objective-node {
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
  background: #f3e8ff;
  border-bottom: 1px solid #e9d5ff;
}
.node-icon { font-size: 1rem; }
.node-type {
  font-size: 0.7rem;
  color: #6b21a8;
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
  color: #581c87;
}
.node-template {
  font-size: 0.7rem;
  color: #7c3aed;
}
.handle-target {
  width: 16px !important;
  height: 16px !important;
  background: #8b5cf6 !important;
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
  background: #8b5cf6 !important;
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
