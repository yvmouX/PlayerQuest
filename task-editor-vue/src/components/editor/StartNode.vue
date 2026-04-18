<template>
  <div class="start-node">
    <div class="node-circle">
      <span class="node-icon">▶</span>
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
  background: #22c55e;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(34, 197, 94, 0.3);
}
.node-icon {
  color: white;
  font-size: 1.2rem;
}
</style>
