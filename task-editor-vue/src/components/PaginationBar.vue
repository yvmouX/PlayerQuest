<!--
  分页条：显示当前区间、每页条数选择、上一页/下一页与页码。

  <p>只做展示与事件抛出，页码越界由父组件负责收敛（本组件已做安全裁剪）。
-->
<template>
  <div class="pagination">
    <span class="hint">
      显示 {{ rangeStart }}–{{ rangeEnd }} / 共 {{ total }} 条
    </span>

    <label class="page-size">
      <span class="hint">每页</span>
      <select :value="pageSize" @change="onPageSizeChange">
        <option v-for="size in sizeOptions" :key="size" :value="size">{{ size }}</option>
      </select>
      <span class="hint">条</span>
    </label>

    <div class="page-buttons">
      <button class="btn btn-small" type="button" :disabled="current <= 1" @click="goto(1)">« 首页</button>
      <button class="btn btn-small" type="button" :disabled="current <= 1" @click="goto(current - 1)">上一页</button>
      <button
        v-for="item in pageItems"
        :key="item"
        class="btn btn-small page-number"
        :class="{ 'page-current': item === current }"
        type="button"
        @click="goto(item)"
      >{{ item }}</button>
      <button class="btn btn-small" type="button" :disabled="current >= pageCount" @click="goto(current + 1)">下一页</button>
      <button class="btn btn-small" type="button" :disabled="current >= pageCount" @click="goto(pageCount)">末页 »</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 当前页，从 1 开始 */
    page: number
    pageSize: number
    total: number
    sizeOptions?: number[]
  }>(),
  {
    sizeOptions: () => [20, 50, 100]
  }
)

const emit = defineEmits<{
  'update:page': [value: number]
  'update:pageSize': [value: number]
}>()

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / Math.max(1, props.pageSize))))
const current = computed(() => Math.min(Math.max(1, props.page), pageCount.value))
const rangeStart = computed(() => (props.total === 0 ? 0 : (current.value - 1) * props.pageSize + 1))
const rangeEnd = computed(() => Math.min(props.total, current.value * props.pageSize))

/** 页码按钮：最多 7 个，围绕当前页滑动，避免几百页时撑爆布局。 */
const pageItems = computed<number[]>(() => {
  const total = pageCount.value
  const window = 7
  if (total <= window) {
    return Array.from({ length: total }, (_, index) => index + 1)
  }
  let start = Math.max(1, current.value - Math.floor(window / 2))
  const end = Math.min(total, start + window - 1)
  start = Math.max(1, end - window + 1)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})

function goto(page: number): void {
  const next = Math.min(Math.max(1, page), pageCount.value)
  if (next !== props.page) {
    emit('update:page', next)
  }
}

function onPageSizeChange(event: Event): void {
  const target = event.target as HTMLSelectElement | null
  const size = target ? Number(target.value) : props.pageSize
  emit('update:pageSize', Number.isFinite(size) && size > 0 ? size : props.pageSize)
}
</script>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.page-size {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.page-size select {
  width: auto;
  padding: 0.2rem 0.4rem;
}

.page-buttons {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  flex-wrap: wrap;
}

.page-number {
  min-width: 2rem;
}

.page-current {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent);
}
</style>
