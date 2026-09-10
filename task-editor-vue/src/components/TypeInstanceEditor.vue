<!--
  一个目标 / 奖励实例的编辑卡片。

  只认识「类型 id + 属性表」，字段全部交给 SchemaFieldInput 按 schema 渲染，
  因此后端新增类型时这张卡片不需要任何改动。

  卡片头部提供序号（顺序有意义：它决定 setobjective 命令里的目标序号）
  以及上移/下移按钮，边界上的按钮自动禁用。
-->
<template>
  <div class="instance-card">
    <header class="instance-head">
      <span class="instance-index" :title="indexTitle">#{{ index + 1 }}</span>
      <select class="instance-type" :value="type" @change="onTypeChange">
        <option
          v-for="item in options"
          :key="item.id"
          :value="item.id"
          :disabled="item.disabled"
        >{{ item.label }}</option>
        <option v-if="!options.length" :value="type">{{ type || '（后端未提供任何类型）' }}</option>
      </select>
      <div class="instance-tools">
        <button
          class="btn btn-small"
          type="button"
          title="上移"
          :disabled="index === 0"
          @click="emit('move-up')"
        >↑</button>
        <button
          class="btn btn-small"
          type="button"
          title="下移"
          :disabled="last"
          @click="emit('move-down')"
        >↓</button>
        <button class="btn btn-small btn-danger" type="button" title="删除这一项" @click="emit('remove')">
          删除
        </button>
      </div>
    </header>

    <p v-if="unavailableReason" class="warn-line">
      ⚠ 该奖励当前不可用：{{ unavailableReason }}
    </p>
    <p v-else-if="!schema" class="warn-line">
      ⚠ 后端不认识类型「{{ type || '（空）' }}」，保存后会被标记为校验问题。
    </p>

    <div v-if="fields.length" class="fields-grid">
      <SchemaFieldInput
        v-for="field in fields"
        :key="field.key"
        :field="field"
        :model-value="properties[field.key] ?? null"
        @update:model-value="value => setProperty(field.key, value)"
      />
    </div>
    <p v-else-if="schema" class="hint">该类型没有可配置字段。</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Properties, PropertyValue, TypeSchema } from '../types'
import { defaultProperties, typeLabel } from '../utils/schema'
import SchemaFieldInput from './SchemaFieldInput.vue'

const props = defineProps<{
  /** 序号，从 0 开始；显示时 +1 */
  index: number
  /** 是否为最后一项（决定下移按钮是否可用） */
  last?: boolean
  /** 当前类型 id */
  type: string
  /** 当前属性表 */
  properties: Properties
  /** 全部可选类型，来自 /api/schema */
  schemas: Record<string, TypeSchema>
  /** 奖励需要额外标注「不可用」 */
  reward?: boolean
}>()

const emit = defineEmits<{
  'update:type': [value: string]
  'update:properties': [value: Properties]
  'move-up': []
  'move-down': []
  remove: []
}>()

const schema = computed<TypeSchema | undefined>(() => props.schemas[props.type])

const fields = computed(() => schema.value?.fields ?? [])

const unavailableReason = computed(() =>
  props.reward ? schema.value?.unavailableReason ?? '' : ''
)

const indexTitle = computed(() =>
  props.reward
    ? `第 ${props.index + 1} 个奖励，发放顺序自上而下`
    : `第 ${props.index + 1} 个目标，序号会出现在 setobjective 命令中`
)

/** 下拉选项：奖励若 available=false，标注原因并禁止选择。 */
const options = computed(() =>
  Object.values(props.schemas).map(item => {
    const unavailable = props.reward === true && item.available === false
    return {
      id: item.id,
      label: unavailable ? `${typeLabel(item)} — 不可用` : typeLabel(item),
      disabled: unavailable
    }
  })
)

/** 切换类型：属性表必须一起换掉，旧类型的键对新类型没有意义。 */
function onTypeChange(event: Event): void {
  const target = event.target as HTMLSelectElement | null
  const next = target ? target.value : ''
  if (next === props.type) {
    return
  }
  emit('update:type', next)
  emit('update:properties', defaultProperties(props.schemas[next]))
}

function setProperty(key: string, value: PropertyValue): void {
  emit('update:properties', { ...props.properties, [key]: value })
}
</script>

<style scoped>
.instance-card {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-elevated);
  padding: 0.75rem;
}

.instance-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.6rem;
}

.instance-index {
  min-width: 1.9rem;
  padding: 0.1rem 0.3rem;
  border: 1px solid var(--border);
  border-radius: 4px;
  background: var(--bg-input);
  color: var(--accent);
  font-size: 0.78rem;
  text-align: center;
}

.instance-type {
  flex: 1;
}

.instance-tools {
  display: flex;
  gap: 0.25rem;
  white-space: nowrap;
}

.warn-line {
  margin: 0 0 0.6rem;
  color: var(--warn);
  font-size: 0.85rem;
}
</style>
