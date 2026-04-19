<template>
  <span class="badge" :class="status">{{ label }}</span>
</template>

<script setup lang="ts">
const props = defineProps<{
  status: string
}>()

const labelMap: Record<string, string> = {
  'not_started': '未开始',
  'in_progress': '进行中',
  'completed': '已完成',
  'claimed': '已领取'
}

const label = computed(() => labelMap[props.status] || props.status)
</script>

<style scoped>
.badge {
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 500;
}
.not_started { background: #e9ecef; color: #6c757d; }
.in_progress { background: #cfe2ff; color: #0d6efd; }
.completed { background: #d1e7dd; color: #198754; }
.claimed { background: #d1e7dd; color: #198754; }
</style>
