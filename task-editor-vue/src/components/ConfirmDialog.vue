<template>
  <Teleport to="body">
    <div v-if="show" class="dialog-overlay" @click.self="$emit('cancel')">
      <div class="dialog">
        <h3>{{ title }}</h3>
        <p>{{ message }}</p>
        <div class="actions">
          <button @click="$emit('cancel')">取消</button>
          <button class="danger" @click="$emit('confirm')">确认</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
defineProps<{
  show: boolean
  title: string
  message: string
}>()
defineEmits<{
  confirm: []
  cancel: []
}>()
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.dialog {
  background: white;
  border-radius: 8px;
  padding: 1.5rem;
  max-width: 400px;
  width: 90%;
}
.dialog h3 { margin: 0 0 0.5rem; }
.dialog p { color: #666; margin: 0 0 1.5rem; }
.actions { display: flex; gap: 0.5rem; justify-content: flex-end; }
.actions button {
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}
.actions button.danger {
  background: #dc3545;
  color: white;
  border-color: #dc3545;
}
</style>
