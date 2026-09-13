<!--
  统一样式的复选框。

  <p>只是给原生 input 套一层统一的 props/事件约定，样式全部来自全局 CSS。
-->
<template>
  <input
    type="checkbox"
    :checked="modelValue"
    :disabled="disabled"
    :aria-label="ariaLabel || undefined"
    @change="onChange"
  />
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    modelValue: boolean
    disabled?: boolean
    /** 无可见文字时的无障碍标签 */
    ariaLabel?: string
  }>(),
  {
    disabled: false,
    ariaLabel: ''
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

function onChange(event: Event): void {
  const target = event.target as HTMLInputElement | null
  emit('update:modelValue', target ? target.checked : false)
}
</script>
