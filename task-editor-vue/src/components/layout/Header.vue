<template>
  <header class="header">
    <h2 class="page-title">{{ title }}</h2>
    <div class="actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
defineProps<{
  title: string
}>()
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  background: white;
  border-bottom: 1px solid #e5e5e5;
}
.page-title {
  font-size: 1.25rem;
  font-weight: 600;
}
.actions {
  display: flex;
  gap: 0.5rem;
}
</style>
