<!--
  通用数据表。

  <p>只负责「表头 + 排序交互 + 行渲染」，筛选、排序算法、分页都由调用方
  决定：本组件通过 sort 事件把列标识抛出去，单元格内容一律由具名插槽提供
  （插槽名 = 列的 key），因此它对业务数据类型没有任何假设。

  <p>行的附加 class 由 rowClass 计算，用于「有问题的行标红」这类场景；
  这些 class 在全局 CSS 里定义，组件内用 :deep 放行。
-->
<template>
  <div class="table-wrap">
    <table class="data-table">
      <thead>
        <tr>
          <th v-if="$slots.select" class="col-select"></th>
          <th
            v-for="column in columns"
            :key="column.key"
            :class="headerClass(column)"
            :style="column.width ? { width: column.width } : undefined"
            :title="column.title || undefined"
          >
            <button
              v-if="column.sortable"
              class="th-sort"
              type="button"
              :title="`按「${column.label}」排序`"
              @click="emit('sort', column.key)"
            >
              <span>{{ column.label }}</span>
              <span class="sort-mark" :class="{ active: sortKey === column.key }">
                {{ sortKey === column.key ? (sortDirection === 'asc' ? '▲' : '▼') : '↕' }}
              </span>
            </button>
            <template v-else>{{ column.label }}</template>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(row, index) in rows"
          :key="rowKey ? rowKey(row) : index"
          :class="rowClass ? rowClass(row) : undefined"
        >
          <td v-if="$slots.select" class="col-select">
            <slot name="select" :row="row" :index="index"></slot>
          </td>
          <td
            v-for="column in columns"
            :key="column.key"
            :class="cellClass(column)"
          >
            <slot :name="column.key" :row="row" :index="index"></slot>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts" generic="T">
import type { SortDirection, TableColumn } from '../types'

const props = withDefaults(
  defineProps<{
    columns: TableColumn[]
    rows: T[]
    /** 行唯一键；不传则退化为下标 */
    rowKey?: (row: T) => string
    /** 行附加 class */
    rowClass?: (row: T) => string
    /** 当前排序列（用于显示箭头） */
    sortKey?: string
    sortDirection?: SortDirection
  }>(),
  {
    rowKey: undefined,
    rowClass: undefined,
    sortKey: '',
    sortDirection: 'asc'
  }
)
// 模板里要用到 rowKey / rowClass，这里显式引用一次，避免打包器摇树时误判
void props.rowKey
void props.rowClass

const emit = defineEmits<{
  sort: [key: string]
}>()

function headerClass(column: TableColumn): (string | false | undefined)[] {
  return [
    column.align === 'right' ? 'num' : column.align === 'center' ? 'center' : undefined,
    column.sortable ? 'sortable' : undefined
  ]
}

function cellClass(column: TableColumn): (string | undefined)[] {
  return [column.align === 'right' ? 'num' : column.align === 'center' ? 'center' : undefined]
}
</script>

<style scoped>
.col-select {
  width: 2.2rem;
  text-align: center;
}

.th-sort {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0;
  border: none;
  background: none;
  color: inherit;
  font: inherit;
  white-space: nowrap;
  cursor: pointer;
}

.th-sort:hover {
  color: var(--accent);
}

.sort-mark {
  color: #55607040;
  font-size: 0.7rem;
}

.sort-mark.active {
  color: var(--accent);
}
</style>
