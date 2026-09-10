<!--
  统一样式的复选框。

  <p>原生 input 的 indeterminate 只能通过 DOM 属性设置，所以这里用一个
  指向元素的 ref 在变更后同步；其它样式全部来自全局 CSS。
-->
<template>
  <input
    ref="inputRef"
    type="checkbox"
    :checked="modelValue"
    :disabled="disabled"
    :aria-label="ariaLabel || undefined"
    @change="onChange"
  />
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    /** 半选状态（表头「全选」用） */
    indeterminate?: boolean
    disabled?: boolean
    /** 无可见文字时的无障碍标签 */
    ariaLabel?: string
  }>(),
  {
    indeterminate: false,
    disabled: false,
    ariaLabel: ''
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const inputRef = ref<HTMLInputElement | null>(null)

/** 同步 indeterminate：它只是视觉状态，不会触发 change 事件。 */
function syncIndeterminate(): void {
  const element = inputRef.value
  if (element) {
    element.indeterminate = props.indeterminate && !props.modelValue
  }
}

onMounted(syncIndeterminate)
watch(() => [props.indeterminate, props.modelValue], syncIndeterminate)

function onChange(event: Event): void {
  const target = event.target as HTMLInputElement | null
  emit('update:modelValue', target ? target.checked : false)
}
</script>
