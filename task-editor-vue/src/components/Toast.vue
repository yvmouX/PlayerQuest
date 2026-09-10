<!--
  轻量 Toast：支持同时堆叠多条消息（保存失败 + 校验问题这类场景很常见）。
  组件挂载时通过 registerToast() 向全局登记，其它模块用 useToast() 调用。
-->
<template>
  <Teleport to="body">
    <div class="toast-stack">
      <TransitionGroup name="toast">
        <div
          v-for="item in items"
          :key="item.id"
          :class="['toast', item.type]"
          @click="dismiss(item.id)"
        >{{ item.message }}</div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ToastType } from '../composables/useToast'

interface ToastItem {
  id: number
  message: string
  type: ToastType
}

const items = ref<ToastItem[]>([])
let sequence = 0

/** 显示一条消息；错误默认停留更久，方便阅读后端返回的原因。 */
function show(message: string, type: ToastType = 'info', duration?: number): void {
  const id = ++sequence
  items.value = [...items.value, { id, message, type }]
  const timeout = duration ?? (type === 'error' ? 6000 : 3000)
  window.setTimeout(() => dismiss(id), timeout)
}

function dismiss(id: number): void {
  items.value = items.value.filter(item => item.id !== id)
}

defineExpose({ show })
</script>

<style scoped>
.toast-stack {
  position: fixed;
  top: 1rem;
  right: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  align-items: flex-end;
  z-index: 9999;
  pointer-events: none;
}

.toast {
  pointer-events: auto;
  max-width: 26rem;
  padding: 0.6rem 1rem;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--bg-elevated);
  color: var(--text);
  font-size: 0.88rem;
  line-height: 1.4;
  word-break: break-word;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
}

.toast.success {
  border-color: var(--ok);
  color: var(--ok);
}

.toast.error {
  border-color: var(--danger);
  color: #ffb4b0;
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(1rem);
}
</style>
