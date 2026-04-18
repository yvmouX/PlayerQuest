<template>
  <div class="condition-node">
    <div class="node-diamond">
      <span class="node-icon">COND</span>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Bottom" />
  </div>
</template>

<script setup lang="ts">
import {Handle, Position} from '@vue-flow/core'

defineProps<{
  data: {
    name: string
    conditions?: any[]
  }
}>()
</script>

<style scoped>
.condition-node {
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.node-diamond {
  width: 50px;
  height: 50px;
  background: #8b5cf6;
  transform: rotate(45deg);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(139, 92, 246, 0.3);
}
.node-icon {
  color: white;
  font-size: 0.7rem;
  font-weight: bold;
  transform: rotate(-45deg);
}
</style>
