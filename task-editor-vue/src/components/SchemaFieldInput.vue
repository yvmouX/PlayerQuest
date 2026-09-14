<!--
  单个 schema 字段的输入控件。

  完全由 FieldSchema 决定渲染什么：这里没有、也不允许有任何
  针对具体目标/奖励类型的硬编码分支。

  <h2>MATERIAL / ENTITY / TARGET / FISH 用选择器</h2>
  枚举名（DEEPSLATE_DIAMOND_ORE 这种）靠人记忆不现实，因此这几类字段渲染
  MaterialPicker。MATERIAL 还能多选（逗号分隔）。选择器内部仍然是文本框，
  原文照旧可手打，只是多了一个可按中文/英文/枚举名搜索、按插件来源筛选的浮层。

  <h2>布局</h2>
  标签左对齐固定宽度、控件占满剩余空间、hint 作为次要文字挂在控件下方
  （与标签同列，避免把行高撑得忽宽忽窄）。
-->
<template>
  <div class="schema-field">
    <span class="field-label">
      {{ field.label || field.key }}
      <em v-if="field.required === true" class="required" title="后端标记为必填">*</em>
    </span>

    <div class="field-control">
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

      <!-- PICKER：素材选择器（值域决定列出什么，仍允许手打原文） -->
      <MaterialPicker
        v-else-if="field.type === 'PICKER'"
        :model-value="text"
        :kinds="field.kinds ?? []"
        :multi="allowMulti"
        :empty-hint="allowsEmpty"
        @update:model-value="onPickerValue"
      />

      <!-- 其余（STRING）：文本输入 -->
      <input v-else type="text" :value="text" @input="onText" />

      <small v-if="field.hint" class="hint">{{ field.hint }}</small>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { FieldSchema, PropertyValue } from '../types'
import { KIND_BLOCK, KIND_ITEM } from '../utils/catalog'
import { parseNumberInput, toInputText } from '../utils/schema'
import MaterialPicker from './MaterialPicker.vue'

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

/**
 * 是否允许多选（逗号分隔）。
 *
 * <p>由值域决定：方块与物品类的字段天然可以写多个（「挖钻石矿或深层钻石矿」），
 * 而实体、鱼、附魔这类是「一个具体对象」，多选只会让人写出更绕的配置。
 */
const allowMulti = computed(() => {
  const kinds = props.field.kinds ?? []
  return kinds.includes(KIND_BLOCK) || kinds.includes(KIND_ITEM)
})


/**
 * 字段是否允许留空。
 *
 * <p>直接读 schema 的 {@code required}：后端对「任意鱼」「任意生物」这类字段
 * 用的是 optionalMaterial / optionalEntity / optionalBlockOrEntity，
 * 会如实把 required 标成 false。因此这里不需要、也不应该去猜 hint 文案——
 * 文案随时可以改，猜错了就会给管理员一个与实际行为不符的红色星号。
 */
const allowsEmpty = computed(() => props.field.required === false)

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

/** 选择器直接回传字符串（含逗号分隔的多值），无需再读事件目标。 */
function onPickerValue(value: string): void {
  emit('update:modelValue', value)
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
/* 与 main.css 的 .form-row 保持同一套栅格，保证各类表单看起来是一套东西 */
.schema-field {
  display: grid;
  grid-template-columns: 7.5rem minmax(0, 1fr);
  align-items: start;
  gap: 0.4rem 0.75rem;
}

.schema-field > .field-label {
  /* 单行标签与右侧控件首行对齐 */
  padding-top: 0.4rem;
}

.field-control {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
}

.checkbox-line {
  height: 2.1rem;
}

@media (max-width: 720px) {
  .schema-field {
    grid-template-columns: minmax(0, 1fr);
  }

  .schema-field > .field-label {
    padding-top: 0;
  }
}
</style>
