<template>
  <div class="timer-node">
    <div class="node-header">
      <span class="node-icon">⏱️</span>
      <span class="node-type">{{ timerTypeLabel }}</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Timer' }}</span>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {Handle, Position} from '@vue-flow/core'

const props = defineProps<{
  data: {
    name: string
    timerType?: string
  }
}>()

const timerTypeLabel = computed(() => {
  const labels: Record<string, string> = {
    DELAY: '延迟',
    COOLDOWN: '冷却',
    INTERVAL: '周期'
  }
  return labels[props.data.timerType] || '定时'
})
</script>

<style scoped>
.timer-node {
  width: 120px;
  height: 60px;
  background: white;
  border: 2px solid #f59e0b;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.2);
}
.node-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.75rem;
  background: #fef3c7;
  border-bottom: 1px solid #fde68a;
}
.node-icon { font-size: 1rem; }
.node-type {
  font-size: 0.7rem;
  color: #92400e;
}
.node-body {
  padding: 0.4rem 0.75rem;
}
.node-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #78350f;
}
</style>