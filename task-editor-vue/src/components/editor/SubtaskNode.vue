<template>
  <div class="subtask-node">
    <div class="node-header">
      <span class="node-label">SUBTASK</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Subtask Group' }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  data: {
    name: string
  }
}>()
</script>

<style scoped>
.subtask-node {
  width: 200px;
  min-height: 80px;
  background: rgba(249, 250, 251, 0.5);
  border: 2px dashed #9ca3af;
  border-radius: 8px;
  overflow: hidden;
}
.node-header {
  padding: 0.5rem;
  background: #f9fafb;
  border-bottom: 1px dashed #d1d5db;
}
.node-label {
  font-size: 0.7rem;
  color: #6b7280;
  font-weight: bold;
}
.node-body {
  padding: 0.5rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #374151;
}
</style>