<!--
  一个目标 / 奖励实例的编辑卡片。

  只认识「类型 id + 属性表」，字段全部交给 SchemaFieldInput 按 schema 渲染，
  因此后端新增类型时这张卡片不需要任何改动。

  <h2>卡片头承载「扫一眼就够」的信息</h2>
  序号 + 类型显示名 + 关键属性摘要 + 操作按钮放在头部，字段表单收在下面：
  一个任务往往有 4-6 个目标，全部展开会让页面长得看不见头。
  摘要与折叠按钮合并成一个按钮，点一下即可在「看摘要」和「改字段」之间切换。

  <h2>顺序</h2>
  顺序有意义：它决定 setobjective 命令里的目标序号与奖励发放顺序，
  因此用 ↑ ↓ 调整（不引入拖拽库——按钮更稳、键盘也能用，且不需要额外的
  拖拽状态与落点计算）。
-->
<template>
  <div class="instance-card">
    <header class="instance-head">
      <span class="instance-index" :title="indexTitle">#{{ index + 1 }}</span>

      <select
        class="instance-type"
        :value="type"
        :title="'切换类型会按新类型重置字段，原字段值不会保留'"
        @change="onTypeChange"
      >
        <option
          v-for="item in options"
          :key="item.id"
          :value="item.id"
          :disabled="item.disabled"
        >{{ item.label }}</option>
        <option v-if="!options.length" :value="type">{{ type || '（后端未提供任何类型）' }}</option>
      </select>

      <button
        class="instance-summary"
        type="button"
        :title="summary || '该类型没有可配置字段'"
        @click="expanded = !expanded"
      >
        <span class="instance-summary-text">{{ summary || '（无参数）' }}</span>
        <span class="instance-caret">{{ expanded ? '▲' : '▼' }}</span>
      </button>

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
        <button
          class="btn btn-small"
          type="button"
          title="把这条的类型与字段值保存为预设"
          @click="emit('save-as-preset')"
        >存为预设</button>
        <button class="btn btn-small btn-danger" type="button" title="删除这一项" @click="emit('remove')">
          删除
        </button>
      </div>
    </header>

    <p v-if="unavailableReason" class="warn-line">
      ⚠ 该{{ reward ? '奖励' : '目标' }}类型当前不可用：{{ unavailableReason }}
    </p>
    <p v-else-if="!schema" class="warn-line">
      ⚠ 后端不认识类型「{{ type || '（空）' }}」，保存后会被标记为校验问题。
    </p>

    <div v-if="expanded" class="instance-body">
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
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Properties, PropertyValue, TypeSchema } from '../types'
import { defaultProperties, schemaOptions, summarizeProperties } from '../utils/schema'
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
  /** 把当前卡片另存为预设 */
  'save-as-preset': []
  remove: []
}>()

const schema = computed<TypeSchema | undefined>(() => props.schemas[props.type])

const fields = computed(() => schema.value?.fields ?? [])

/**
 * 头部摘要：让折叠状态下也能一眼看出这条目标/奖励要做什么。
 * 完全按 schema 的字段顺序拼装，不认识具体类型。
 */
const summary = computed(() => summarizeProperties(props.properties, schema.value))

// 默认展开：新建后第一件事就是填字段，收起来反而多点一次
const expanded = ref(true)

/**
 * 类型不可用（软依赖缺失，如未装 CustomFishing / Vault）。
 *
 * <p>目标与奖励同一套判断：后端 /api/schema 对两者都给 available / unavailableReason，
 * 界面不该只对奖励提示——「自定义钓鱼」目标没装 CustomFishing 时同样永远不涨进度。
 */
const unavailableReason = computed(() => schema.value?.unavailableReason ?? '')

const indexTitle = computed(() =>
  props.reward
    ? `第 ${props.index + 1} 个奖励，发放顺序自上而下`
    : `第 ${props.index + 1} 个目标，序号会出现在 setobjective 命令中`
)

/** 下拉选项：available=false 的类型标注原因并禁止选择（目标与奖励同一规则）。 */
const options = computed(() => schemaOptions(props.schemas))

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
  padding: 0.7rem 0.75rem;
}

.instance-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
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
  flex: 0 1 14rem;
  width: auto;
  min-width: 9rem;
}

/* 摘要与折叠开关是同一个按钮：点摘要即可展开改字段 */
.instance-summary {
  flex: 1 1 12rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  min-width: 0;
  padding: 0.3rem 0.5rem;
  border: 1px solid transparent;
  border-radius: var(--radius);
  background: transparent;
  color: var(--text-dim);
  font-size: 0.78rem;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
}

.instance-summary:hover {
  border-color: var(--border);
  background: var(--bg-input);
}

.instance-summary-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.instance-caret {
  color: var(--text-dim);
  font-size: 0.65rem;
}

.instance-tools {
  display: flex;
  gap: 0.25rem;
  white-space: nowrap;
}

.instance-body {
  margin-top: 0.7rem;
  padding-top: 0.7rem;
  border-top: 1px dashed var(--border-soft);
}

@media (max-width: 900px) {
  /* 窄屏时把操作按钮换到第二行，避免类型下拉被挤成一条缝 */
  .instance-type,
  .instance-summary {
    flex: 1 1 100%;
  }

  .instance-tools {
    flex-wrap: wrap;
  }
}
</style>
