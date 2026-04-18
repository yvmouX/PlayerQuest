<template>
  <div class="branch-node">
    <div class="node-hexagon">
      <span class="node-label">BRANCH</span>
    </div>
    <div class="branch-labels">
      <span class="branch-true">TRUE</span>
      <span class="branch-false">FALSE</span>
    </div>
    <Handle type="target" :position="Position.Top" />
    <Handle id="true" type="source" :position="Position.Bottom" style="left: 25%" />
    <Handle id="false" type="source" :position="Position.Bottom" style="left: 75%" />
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
  width: 60px;
  height: 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
}
.node-hexagon {
  width: 45px;
  height: 45px;
  background: #8b5cf6;
  clip-path: polygon(25% 0%, 75% 0%, 100% 50%, 75% 100%, 25% 100%, 0% 50%);
  display: flex;
  align-items: center;
  justify-content: center;
}
.node-label {
  color: white;
  font-size: 0.5rem;
  font-weight: bold;
  z-index: 1;
}
.branch-labels {
  display: flex;
  justify-content: space-between;
  width: 50px;
  margin-top: 2px;
  font-size: 0.5rem;
  font-weight: bold;
}
.branch-true { color: #22c55e; }
.branch-false { color: #ef4444; }
</style>
