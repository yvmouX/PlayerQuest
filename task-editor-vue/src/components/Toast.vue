<template>
  <Teleport to="body">
    <Transition name="toast">
      <div v-if="visible" :class="['toast', type]" @click="dismiss">
        {{ message }}
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import {ref} from 'vue'

const visible = ref(false)
const message = ref('')
const type = ref<'info' | 'error' | 'success'>('info')

let timeoutId: ReturnType<typeof setTimeout> | null = null

function show(msg: string, toastType: 'info' | 'error' | 'success' = 'info', duration = 3000) {
  if (timeoutId) clearTimeout(timeoutId)
  message.value = msg
  type.value = toastType
  visible.value = true
  timeoutId = setTimeout(() => {
    visible.value = false
  }, duration)
}

function dismiss() {
  visible.value = false
}

defineExpose({ show })
</script>

<style scoped>
.toast {
  position: fixed;
  top: 20px;
  right: 20px;
  padding: 12px 24px;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  z-index: 9999;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}
.toast.info {
  background: #3b82f6;
  color: white;
}
.toast.error {
  background: #ef4444;
  color: white;
}
.toast.success {
  background: #22c55e;
  color: white;
}
.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(100%);
}
</style>
