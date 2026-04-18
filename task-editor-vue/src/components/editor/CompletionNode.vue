<template>
  <div class="completion-node">
    <div class="node-diamond">
      <span class="node-icon">✓</span>
    </div>
    <Handle type="target" :position="Position.Left" />
  </div>
</template>

<script setup lang="ts">
import {Handle, Position} from '@vue-flow/core'

defineProps<{
  data: {
    name: string
    rewards?: any[]
    callbackMessage?: string
  }
}>()
</script>

<style scoped>
.completion-node {
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.node-diamond {
  width: 45px;
  height: 45px;
  background: #ef4444;
  transform: rotate(45deg);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.3);
}
.node-icon {
  color: white;
  font-size: 1rem;
  transform: rotate(-45deg);
}
</style>
