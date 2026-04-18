<template>
  <div class="action-node">
    <div class="node-header">
      <span class="node-icon">{{ actionIcon }}</span>
      <span class="node-type">{{ actionLabel }}</span>
    </div>
    <div class="node-body">
      <span class="node-name">{{ data.name || 'Action' }}</span>
    </div>
    <Handle type="target" :position="Position.Left" />
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {Handle, Position} from '@vue-flow/core'
import type {ActionData} from '../../types'

const props = defineProps<{
  data: ActionData
}>()

const actionIcon = computed(() => {
  const icons: Record<string, string> = {
    GIVE_ITEM: '📦',
    TAKE_ITEM: '📤',
    GIVE_MONEY: '💰',
    TAKE_MONEY: '💸',
    GIVE_XP: '⭐',
    SEND_MESSAGE: '💬',
    BROADCAST: '📢',
    EXECUTE_COMMAND: '⚡',
    PLAY_SOUND: '🎵'
  }
  return icons[props.data.actionType] || '⚙️'
})

const actionLabel = computed(() => {
  const labels: Record<string, string> = {
    GIVE_ITEM: '发放物品',
    TAKE_ITEM: '扣除物品',
    GIVE_MONEY: '发放货币',
    TAKE_MONEY: '扣除货币',
    GIVE_XP: '发放经验',
    SEND_MESSAGE: '发送消息',
    BROADCAST: '全服广播',
    EXECUTE_COMMAND: '执行命令',
    PLAY_SOUND: '播放音效'
  }
  return labels[props.data.actionType] || props.data.actionType
})
</script>

<style scoped>
.action-node {
  width: 180px;
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
