<!--
  单个 schema 字段的输入控件。

  完全由 FieldSchema 决定渲染什么：这里没有、也不允许有任何
  针对具体目标/奖励类型的硬编码分支。
-->
<template>
  <label class="schema-field">
    <span class="field-label">
      {{ field.label || field.key }}
      <em v-if="field.required" class="required" title="后端标记为必填">*</em>
    </span>

    <!-- ENUM：下拉框，候选项来自 schema.options -->
    <select v-if="field.type === 'ENUM'" :value="text" @change="onText">
      <option v-for="option in enumOptions" :key="option" :value="option">{{ option }}</option>
    </select>

    <!-- BOOLEAN：复选框 -->
    <span v-else-if="field.type === 'BOOLEAN'" class="checkbox-line">
      <input type="checkbox" :checked="modelValue === true" @change="onCheck" />
      <span class="checkbox-text">{{ modelValue === true ? '是' : '否' }}</span>
    </span>

    <!-- INTEGER / DECIMAL：数字输入 -->
    <input
      v-else-if="isNumber"
      type="number"
      :step="field.type === 'INTEGER' ? '1' : 'any'"
      :value="text"
      @input="onNumber"
    />

    <!-- 其余（STRING / MATERIAL / ENTITY）：文本输入 -->
    <input v-else type="text" :value="text" @input="onText" />

    <small v-if="field.hint" class="hint">{{ field.hint }}</small>
  </label>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { FieldSchema, PropertyValue } from '../types'
import { parseNumberInput, toInputText } from '../utils/schema'

const props = defineProps<{
  field: FieldSchema
  modelValue: PropertyValue
}>()

const emit = defineEmits<{
  'update:modelValue': [value: PropertyValue]
}>()

const text = computed(() => toInputText(props.modelValue))

const isNumber = computed(
  () => props.field.type === 'INTEGER' || props.field.type === 'DECIMAL'
)

/** 下拉候选项：若当前值不在候选中（旧数据、后端改过选项），补进去以免被静默改掉。 */
const enumOptions = computed<string[]>(() => {
  const options = props.field.options ?? []
  const current = typeof props.modelValue === 'string' ? props.modelValue : ''
  if (current && !options.includes(current)) {
    return [current, ...options]
  }
  return options
})

function readValue(event: Event): string {
  const target = event.target as HTMLInputElement | HTMLSelectElement | null
  return target ? target.value : ''
}

function onText(event: Event): void {
  emit('update:modelValue', readValue(event))
}

function onNumber(event: Event): void {
  emit('update:modelValue', parseNumberInput(readValue(event), props.field.type === 'INTEGER'))
}

function onCheck(event: Event): void {
  const target = event.target as HTMLInputElement | null
  emit('update:modelValue', target ? target.checked : false)
}
</script>

<style scoped>
.schema-field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.checkbox-line {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  height: 2.1rem;
}

.checkbox-text {
  color: var(--text-dim);
  font-size: 0.85rem;
}
</style>
