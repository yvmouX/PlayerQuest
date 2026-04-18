<template>
  <div class="event-node">
    <div class="node-circle">
      <span class="node-icon">⚡</span>
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
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.node-circle {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: #3b82f6;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}
.node-icon {
  font-size: 1.2rem;
}
</style>