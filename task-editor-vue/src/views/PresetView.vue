<!--
  预设管理页。

  <h2>为什么是「列表 + 编辑区」而不是表格</h2>
  预设的字段完全由类型决定（一个类型可能只有 1 个字段，也可能有 5 个），
  表格化的列根本对不齐。左侧列表负责扫读与切换，右侧负责编辑，
  与「玩家进度」「语言文件」两页的骨架保持一致。

  <h2>无效预设</h2>
  预设引用的类型可能已被移除。这类条目在列表里置灰并标注原因，
  点开也能看到属性原文，只是不允许保存成有效配置——把无效预设直接隐藏
  或让它把整页搞崩，都会让管理员无从判断「我的预设去哪了」。
-->
<template>
  <section class="view">
    <header class="view-head">
      <div>
        <h2>预设管理</h2>
        <p class="hint">
          预设是目标 / 奖励的常用组合，在任务编辑器点「添加目标 / 奖励」时可直接套用。
          保存在与任务定义同一个数据库里。
        </p>
      </div>
      <div class="view-actions">
        <button class="btn" type="button" :disabled="loading || busy" @click="refresh">
          {{ loading ? '加载中…' : '刷新' }}
        </button>
        <button class="btn" type="button" :disabled="loading || busy || exporting" @click="exportPresets">
          {{ exporting ? '导出中…' : '导出 YAML' }}
        </button>
        <button class="btn" type="button" :disabled="loading || busy || importing" @click="pickImportFile">
          {{ importing ? '导入中…' : '导入 YAML' }}
        </button>
        <button class="btn btn-primary" type="button" :disabled="busy" @click="createDraft('objectives')">
          + 新建目标预设
        </button>
        <button class="btn btn-primary" type="button" :disabled="busy" @click="createDraft('rewards')">
          + 新建奖励预设
        </button>
      </div>
    </header>

    <input
      ref="fileInput"
      class="hidden-file-input"
      type="file"
      accept=".yml,.yaml,.zip,text/yaml,application/x-yaml,application/zip"
      @change="onFilePicked"
    />

    <p v-if="error" class="panel error-panel">{{ error }}</p>
    <UnauthorizedHint :show="unauthorized" />
    <p v-if="loading && !loaded" class="panel">正在读取预设…</p>

    <div v-else-if="!totalCount && !draft" class="card empty-state">
      <strong>还没有任何预设</strong>
      <p class="hint">
        可以用右上角的按钮新建一个，或在任务编辑器里把已填好的目标 / 奖励「存为预设」。
        后端首次启动时会写入一批默认预设。
      </p>
      <div class="view-actions">
        <button class="btn btn-primary" type="button" @click="createDraft('objectives')">新建目标预设</button>
        <button class="btn" type="button" @click="createDraft('rewards')">新建奖励预设</button>
      </div>
    </div>

    <div v-else class="preset-layout">
      <!-- 左：分组列表。两类预设用标签页切换（而不是上下堆在一起）：
           预设一多，一列长列表要滚很久，而且「目标」和「奖励」本来就很少同时找 -->
      <aside class="card preset-side">
        <div class="preset-tabs" role="tablist" aria-label="预设类别">
          <button
            v-for="group in groups"
            :key="group.kind"
            class="preset-tab"
            :class="{ active: group.kind === activeKind }"
            type="button"
            role="tab"
            :aria-selected="group.kind === activeKind"
            @click="activeKind = group.kind"
          >
            {{ group.label }}<span class="tab-count">{{ group.items.length }}</span>
          </button>
        </div>

        <input
          v-model="filter"
          class="preset-search"
          type="search"
          :placeholder="`搜索${activeGroup.label}预设：名称 / id / 类型`"
        />

        <p v-if="!activeGroup.items.length" class="hint preset-group-empty">
          还没有{{ activeGroup.label }}预设。
        </p>
        <p v-else-if="!visibleItems.length" class="hint preset-group-empty">
          没有匹配「{{ filter }}」的预设。
        </p>
        <ul v-else class="preset-items">
          <li v-for="view in visibleItems" :key="view.preset.id || view.preset.name">
            <button
              class="list-item"
              type="button"
              :class="{
                active: isSelected(activeGroup.kind, view),
                invalid: !view.valid,
                readonly: view.preset.source === 'file'
              }"
              @click="select(activeGroup.kind, view)"
            >
              <span class="item-head">
                <span class="name">{{ view.preset.name }}</span>
                <span
                  v-if="view.preset.source === 'file'"
                  class="badge badge-gray"
                  title="来自 presets/ 目录的 YAML 文件，只读：改文件后 /ptxa reload"
                >只读 · YAML</span>
                <span class="badge" :class="view.valid ? 'badge-blue' : 'badge-warn'">
                  {{ view.valid ? view.typeLabel : '无效' }}
                </span>
              </span>
              <span class="item-sub">{{ view.preset.type }} · {{ view.summary }}</span>
            </button>
          </li>
        </ul>

        <div class="preset-side-foot">
          <button class="btn btn-small" type="button" :disabled="busy" @click="createDraft(activeGroup.kind)">
            + 新建{{ activeGroup.label }}预设
          </button>
        </div>
      </aside>

      <!-- 右：编辑区 -->
      <div class="preset-main">
        <section v-if="!draft" class="card empty-state">
          <strong>从左侧选择一个预设</strong>
          <p class="hint">选中后可修改名称、描述与属性字段，改完点「保存预设」。</p>
        </section>

        <section v-else class="card">
          <header class="card-head">
            <h3>{{ draft.isNew ? '新建预设' : `编辑预设：${draft.name || draft.id}` }}</h3>
            <div class="view-actions">
              <span v-if="dirty" class="dirty-flag" title="内容与上次保存的不一致">● 未保存修改</span>
              <span v-else class="clean-flag">已同步</span>
              <!-- 视图开关：可视化表单 / YAML 文本，两边编辑的是同一条预设 -->
              <div class="mode-switch" role="group" aria-label="编辑视图">
                <button
                  class="btn btn-small"
                  :class="{ 'btn-primary': yaml.mode.value === 'visual' }"
                  type="button"
                  @click="showVisual"
                >可视化</button>
                <button
                  class="btn btn-small"
                  :class="{ 'btn-primary': yaml.mode.value === 'yaml' }"
                  type="button"
                  @click="showYaml"
                >YAML</button>
              </div>
              <button class="btn btn-small" type="button" :disabled="busy" @click="closeDraft">关闭</button>
              <button
                v-if="!draft.isNew"
                class="btn btn-small btn-danger"
                type="button"
                :disabled="busy || draftReadOnly"
                :title="draftReadOnly ? '该预设由 presets/ 下的 YAML 文件定义，只读' : ''"
                @click="deletePending = true"
              >删除</button>
              <button
                class="btn btn-small btn-primary"
                type="button"
                :disabled="busy || draftReadOnly"
                :title="draftReadOnly ? '该预设由 presets/ 下的 YAML 文件定义，只读：改文件后 /ptxa reload' : ''"
                @click="save"
              >
                {{ busy ? '保存中…' : '保存预设' }}
              </button>
            </div>
          </header>

          <p v-if="draftReadOnly" class="panel warn-panel">
            该预设定义在 <code class="mono">presets/</code> 下的 YAML 文件里，<b>只读</b>：
            要改它请直接改文件并执行 <code class="mono">/ptxa reload</code>。
          </p>

          <p v-if="draft.isNew" class="hint instance-note">
            新预设的 id 由后端自动生成；保存后即可在任务编辑器里套用。
          </p>

          <!-- YAML 视图：适合从文档/聊天里粘一段配置，或批量改属性 -->
          <YamlTextField
            v-if="yaml.mode.value === 'yaml'"
            v-model="yaml.text.value"
            label="预设 YAML"
            :rows="14"
            :readonly="draftReadOnly"
            :error="yaml.error.value"
            :warnings="yaml.warnings.value"
            hint="顶层是预设字段：kind / name / type / description / properties（id 留空即新建，由后端生成）。"
            @regenerate="yaml.syncFromSource()"
          />

          <template v-else>
          <!-- 只读定义（presets/ 下的 YAML）：整块变灰且不可交互。
               头部（视图切换 / 关闭 / 删除 / 保存）在 fieldset 之外，按钮另有自己的禁用逻辑 -->
          <fieldset class="readonly-block" :disabled="draftReadOnly">
          <div class="form-rows">
            <label class="field field-stack">
              <span class="field-label">名称 <em class="required">*</em></span>
              <input v-model="draft.name" type="text" maxlength="60" placeholder="例如 挖 64 个石头" />
              <small class="hint">显示在「添加目标 / 奖励」的预设列表里。</small>
            </label>

            <label class="field field-stack">
              <span class="field-label">{{ kindLabel(draft.kind) }}类型 <em class="required">*</em></span>
              <select :value="draft.type" @change="onTypeChange">
                <option v-if="!draft.type" value="">（请选择类型）</option>
                <option
                  v-for="item in options"
                  :key="item.id"
                  :value="item.id"
                  :disabled="item.disabled"
                >{{ item.label }}</option>
              </select>
              <small class="hint">切换类型会按新类型重置下面的属性字段，避免残留上一个类型的配置。</small>
            </label>

            <label class="field field-stack field-wide">
              <span class="field-label">描述</span>
              <textarea v-model="draft.description" rows="2" placeholder="这条预设适合什么场景（可留空）"></textarea>
            </label>
          </div>

          <h4 class="preset-fields-title">
            属性字段
            <span class="hint">由该类型的 schema 决定，材质类字段可直接搜索选择。</span>
          </h4>

          <p v-if="!draftSchema" class="warn-line">
            ⚠ 类型「{{ draft.type || '（未选择）' }}」不在当前 schema 里，无法编辑字段。
            请改选一个仍然存在的类型，或删除这条预设。
          </p>
          <div v-else-if="!draftSchema.fields.length" class="hint">该类型没有可配置字段。</div>
          <div v-else class="fields-grid">
            <SchemaFieldInput
              v-for="field in draftSchema.fields"
              :key="field.key"
              :field="field"
              :model-value="draft.properties[field.key] ?? null"
              @update:model-value="value => setProperty(field.key, value)"
            />
          </div>

          <p class="hint preset-id-line">
            id：<code class="mono">{{ draft.id || '（保存后由后端生成）' }}</code>
          </p>
          </fieldset>
          </template>
        </section>
      </div>
    </div>

    <ConfirmDialog
      :show="deletePending"
      title="删除预设"
      :message="deleteMessage"
      confirm-text="删除"
      danger
      @confirm="confirmDelete"
      @cancel="deletePending = false"
    />

    <!-- 导入：选择合并 / 替换 -->
    <ConfirmDialog
      :show="pendingImport !== null"
      title="导入预设"
      :message="importMessage"
      :confirm-text="replaceMode ? '替换导入' : '合并导入'"
      :danger="replaceMode"
      @confirm="confirmImport"
      @cancel="cancelImport"
    >
      <template #options>
        <label class="checkbox-line">
          <input v-model="replaceMode" type="checkbox" />
          <span class="checkbox-text">替换模式：先清空数据库里的预设再导入</span>
        </label>
        <p class="hint">
          不勾选为「合并」：保留现有预设，id 相同的由文件里的版本覆盖。
          勾选后会先删除数据库里的 {{ totalCount }} 条预设，不可撤销；
          <code class="mono">presets/</code> 目录里定义的只读预设不在替换范围内。
        </p>
      </template>
    </ConfirmDialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import SchemaFieldInput from '../components/SchemaFieldInput.vue'
import UnauthorizedHint from '../components/UnauthorizedHint.vue'
import YamlTextField from '../components/YamlTextField.vue'
import { useToast } from '../composables/useToast'
import { useYamlMode } from '../composables/useYamlMode'
import { PresetApi, SchemaApi, errorMessage, isUnauthorized } from '../services/api'
import type { Preset, PresetKind, PresetMap, Properties, PropertyValue, TypeSchema } from '../types'
import { loadCatalog } from '../utils/catalog'
import {
  invalidatePresets,
  loadPresets,
  normalizePresetMap,
  presetView,
  removePreset,
  upsertPreset
} from '../utils/presets'
import { defaultProperties, schemaOptions, withDefaults } from '../utils/schema'
import { decodeBytes, downloadBytes, isZipBytes } from '../utils/files'
import { presetFromYaml, presetToYaml, previewPresetImport } from '../utils/yaml'

const toast = useToast()

/** 正在编辑的预设（副本）。isNew 决定保存时是否启用「新建」语义。 */
interface Draft {
  id: string
  kind: PresetKind
  name: string
  type: string
  description: string
  properties: Properties
  /** 列表里对应的原始预设快照，用来算「未保存修改」 */
  baseline: string
  isNew: boolean
  /** 来自 presets/ 下的 YAML：只读，保存与删除都被禁用 */
  readOnly: boolean
}

const presets = ref<PresetMap>({ objectives: [], rewards: [] })
const objectiveSchemas = ref<Record<string, TypeSchema>>({})
const rewardSchemas = ref<Record<string, TypeSchema>>({})
const loading = ref(false)
/** 是否已经成功加载过一次；用于区分「首次加载中」与「刷新中」 */
const loaded = ref(false)
const busy = ref(false)
const error = ref('')
const unauthorized = ref(false)
const draft = ref<Draft | null>(null)
const deletePending = ref(false)
/** 来自 presets/ 下的 YAML 的草稿只读：保存/删除按钮据此禁用。 */
const draftReadOnly = computed(() => draft.value?.readOnly === true)

const totalCount = computed(() => presets.value.objectives.length + presets.value.rewards.length)

const groups = computed(() => [
  {
    kind: 'objectives' as PresetKind,
    label: '目标',
    items: presets.value.objectives.map(preset => presetView(preset, objectiveSchemas.value))
  },
  {
    kind: 'rewards' as PresetKind,
    label: '奖励',
    items: presets.value.rewards.map(preset => presetView(preset, rewardSchemas.value))
  }
])

/** 当前显示哪一类；两类预设放在同一列里会变成一条长列表，切换比滚动省事。 */
const activeKind = ref<PresetKind>('objectives')
/** 名称 / id / 类型的即时过滤，只作用于当前类别。 */
const filter = ref('')

const activeGroup = computed(() => groups.value.find(group => group.kind === activeKind.value) ?? groups.value[0])

const visibleItems = computed(() => {
  const keyword = filter.value.trim().toLowerCase()
  if (!keyword) {
    return activeGroup.value.items
  }
  return activeGroup.value.items.filter(({ preset }) =>
    [preset.name, preset.id, preset.type].some(value => (value ?? '').toLowerCase().includes(keyword))
  )
})

const draftSchema = computed<TypeSchema | undefined>(() => {
  const current = draft.value
  if (!current) {
    return undefined
  }
  return schemasOf(current.kind)[current.type]
})

const dirty = computed(() => !!draft.value && serialize(draft.value) !== draft.value.baseline)

const deleteMessage = computed(() => {
  const current = draft.value
  if (!current) {
    return ''
  }
  return `确定删除预设「${current.name || current.id}」吗？\n\n`
    + '该操作会立即写入数据库，已使用过这条预设的任务不受影响，但无法恢复这条预设。'
})

onMounted(() => {
  // 属性里的材质字段要用选择器，目录一起预加载
  void loadCatalog()
  void refresh()
})

function schemasOf(kind: PresetKind): Record<string, TypeSchema> {
  return kind === 'objectives' ? objectiveSchemas.value : rewardSchemas.value
}

/** 当前草稿所属那一组类型；没有草稿时给空表，免得下拉计算出无意义的候选项。 */
const currentSchemas = computed<Record<string, TypeSchema>>(() =>
  draft.value ? schemasOf(draft.value.kind) : {}
)

/** 类型下拉候选项；不可用的奖励类型由 schemaOptions 统一标注原因并禁用。 */
const options = computed(() => schemaOptions(currentSchemas.value))

function kindLabel(kind: PresetKind): string {
  return kind === 'objectives' ? '目标' : '奖励'
}

/** 序列化用于比较；字段顺序固定的字面量，避免键序变化被误判成修改。 */
function serialize(value: Draft): string {
  return JSON.stringify({
    id: value.id,
    name: value.name,
    type: value.type,
    description: value.description,
    properties: value.properties
  })
}

async function refresh(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [schema, list] = await Promise.all([SchemaApi.get(), loadPresets(true)])
    objectiveSchemas.value = schema.objectives ?? {}
    rewardSchemas.value = schema.rewards ?? {}
    if (list) {
      presets.value = normalizePresetMap(list)
      loaded.value = true
      unauthorized.value = false
      // 正在编辑的那条可能已被删除或改名，这里以服务端结果为准刷新基准
      syncDraftWithServer()
    } else {
      error.value = '加载预设失败：无法连接后端'
    }
  } catch (e) {
    error.value = `加载预设失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
  } finally {
    loading.value = false
  }
}

/** 刷新后把打开的草稿重新对齐到服务端内容。 */
function syncDraftWithServer(): void {
  const current = draft.value
  if (!current || current.isNew) {
    return
  }
  const found = findPreset(current.kind, current.id)
  if (!found) {
    // 这条预设已经在别处被删掉了，直接收起编辑区而不是留一个幽灵表单
    draft.value = null
    return
  }
  const next = toDraft(current.kind, found)
  if (serialize(current) !== current.baseline) {
    // 有未保存修改：保留用户的输入，只把基准对齐到服务端，避免误判为「已修改」
    draft.value = { ...current, baseline: next.baseline }
    return
  }
  draft.value = next
  yaml.syncFromSource()
}

function findPreset(kind: PresetKind, id: string): Preset | undefined {
  return (kind === 'objectives' ? presets.value.objectives : presets.value.rewards)
    .find(item => item.id === id)
}

/** 预设 → 可编辑草稿；属性按 schema 补齐缺省字段，让显示与保存结果一致。 */
function toDraft(kind: PresetKind, preset: Preset): Draft {
  const value: Draft = {
    id: preset.id,
    kind,
    name: preset.name,
    type: preset.type,
    description: preset.description,
    properties: { ...preset.properties },
    baseline: '',
    isNew: false,
    readOnly: preset.source === 'file'
  }
  const schema = schemasOf(kind)[preset.type]
  if (schema) {
    value.properties = withDefaults(schema, value.properties)
  }
  value.baseline = serialize(value)
  return value
}

function isSelected(kind: PresetKind, view: { preset: Preset }): boolean {
  const current = draft.value
  return !!current && current.kind === kind && current.id !== '' && current.id === view.preset.id
}

function select(kind: PresetKind, view: { preset: Preset; valid: boolean }): void {
  if (!confirmDiscard()) {
    return
  }
  activeKind.value = kind
  draft.value = toDraft(kind, view.preset)
  yaml.mode.value = 'visual'
  yaml.syncFromSource()
}

function createDraft(kind: PresetKind): void {
  if (!confirmDiscard()) {
    return
  }
  activeKind.value = kind
  const schemas = schemasOf(kind)
  const first = Object.keys(schemas)[0] ?? ''
  const value: Draft = {
    id: '',
    kind,
    name: '',
    type: first,
    description: '',
    properties: defaultProperties(schemas[first]),
    baseline: '',
    isNew: true,
    readOnly: false
  }
  value.baseline = serialize(value)
  draft.value = value
  yaml.mode.value = 'visual'
  yaml.syncFromSource()
}

function closeDraft(): void {
  if (!confirmDiscard()) {
    return
  }
  draft.value = null
}

/** 丢弃未保存修改前问一次；只在真的改过时才打扰。 */
function confirmDiscard(): boolean {
  if (!dirty.value) {
    return true
  }
  return window.confirm('当前预设有未保存的修改，确定丢弃吗？')
}

/**
 * 切换类型：属性表整体换成新类型的默认值。
 *
 * <p>需求明确要求「不残留上一个类型的字段」，而且同名键（如 target）
 * 在不同类型下语义完全不同，保留旧值只会制造难以发现的错误配置。
 */
function onTypeChange(event: Event): void {
  const target = event.target as HTMLSelectElement | null
  const next = target ? target.value : ''
  const current = draft.value
  if (!current || next === current.type) {
    return
  }
  current.type = next
  // 整体换成新类型的默认值，刻意不保留同名键：类型切换后同名键的语义多半也变了
  // （例如 break_block.target 是方块，kill.target 是实体），保留旧值只会制造难以察觉的错误配置
  current.properties = defaultProperties(schemasOf(current.kind)[next])
}

function setProperty(key: string, value: PropertyValue): void {
  const current = draft.value
  if (!current) {
    return
  }
  current.properties = { ...current.properties, [key]: value }
}

/* ---------------- 可视化 / YAML 双视图 ---------------- */

/**
 * YAML 视图：解析成功后写回同一条草稿，保存、脏标记、schema 校验都还是原来那一套。
 *
 * <p>草稿的 id 是后端生成的：已存在的预设不允许在 YAML 里改 id（只警告），
 * 新建草稿本来就是空 id，写进去也会被保存逻辑忽略。
 */
const yaml = useYamlMode<Preset>({
  render: () => presetToYaml(draftToPreset(draft.value)),
  parse: presetFromYaml,
  apply: applyYamlPreset,
  extraWarnings: preset => preset.id && preset.id !== (draft.value?.id ?? '')
    ? [`预设 id 由后端生成，YAML 里的「${preset.id}」已忽略`]
    : []
})

/** 当前草稿 → 预设对象（YAML 渲染与保存用的同一形状）。 */
function draftToPreset(value: Draft | null): Preset {
  return {
    id: value?.id ?? '',
    name: value?.name ?? '',
    type: value?.type ?? '',
    description: value?.description ?? '',
    properties: { ...(value?.properties ?? {}) }
  }
}

/**
 * YAML 解析结果 → 草稿。
 *
 * <p>刻意不整体替换草稿对象：{@code baseline} 与 {@code isNew} 是「这条草稿对应服务端哪条记录」
 * 的状态，YAML 只描述内容，不该动它们。
 */
function applyYamlPreset(preset: Preset): void {
  const current = draft.value
  if (!current) {
    return
  }
  current.name = preset.name
  current.type = preset.type
  current.description = preset.description
  // 属性按 schema 补齐缺省：YAML 里漏写的字段缺省值要与可视化视图一致
  current.properties = withDefaults(schemasOf(current.kind)[preset.type], preset.properties)
}

function showYaml(): void {
  yaml.toYaml()
}

function showVisual(): void {
  if (!yaml.toVisual()) {
    toast.error('YAML 还有语法错误，先修好再切回可视化')
  }
}

/* ---------------- 导出 / 导入 ---------------- */

const fileInput = ref<HTMLInputElement | null>(null)
const exporting = ref(false)
const importing = ref(false)
/** 待确认的导入：文件内容 + 预检出的条数 + 文件名。 */
const pendingImport = ref<{ bytes: ArrayBuffer; count: number; fileName: string; zip: boolean } | null>(null)
/** 导入方式：默认「合并」（保留现有预设），勾选后为「替换」（先清空数据库里的）。 */
const replaceMode = ref(false)

const importMessage = computed(() => {
  const pending = pendingImport.value
  if (!pending) {
    return ''
  }
  if (pending.zip) {
    return `压缩包「${pending.fileName}」里每个文件一条预设定义（具体条数以后端导入结果为准），请确认导入方式。`
  }
  return `文件「${pending.fileName}」中解析出 ${pending.count} 条预设定义，请确认导入方式。`
})

/** 导出：一条预设给 yml，多条打包成 zip（与任务导出一致，避免一个文件塞十几条）。 */
async function exportPresets(): Promise<void> {
  if (exporting.value) {
    return
  }
  exporting.value = true
  error.value = ''
  try {
    const bytes = await PresetApi.exportYaml()
    if (isZipBytes(bytes)) {
      downloadBytes(bytes, 'playerTaskX-presets.zip', 'application/zip')
    } else {
      const only = activeGroup.value.items[0]?.preset.id ?? 'preset'
      downloadBytes(bytes, `${only}.yml`, 'application/x-yaml;charset=utf-8')
    }
    toast.success(`已导出 ${totalCount.value} 条预设`)
  } catch (e) {
    const message = `导出失败：${errorMessage(e)}`
    error.value = message
    unauthorized.value = isUnauthorized(e)
    toast.error(message)
  } finally {
    exporting.value = false
  }
}

function pickImportFile(): void {
  fileInput.value?.click()
}

/**
 * 选好文件后本地预检：数出有几条定义、YAML 语法是否成立。
 *
 * <p>真正的字段校验与入库都在后端；这里只为让确认框能说清「要导入几条」——
 * 「替换」是破坏性操作，值得在动手前把数字摆在眼前。
 */
async function onFilePicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement | null
  const file = input?.files?.[0]
  if (input) {
    input.value = ''
  }
  if (!file) {
    return
  }
  error.value = ''
  try {
    const bytes = await file.arrayBuffer()
    if (isZipBytes(bytes)) {
      replaceMode.value = false
      pendingImport.value = { bytes, count: 0, fileName: file.name, zip: true }
      return
    }
    const preview = previewPresetImport(decodeBytes(bytes))
    if (preview.error) {
      error.value = `导入失败：${preview.error}`
      toast.error('导入失败：YAML 解析错误')
      return
    }
    if (!preview.count) {
      error.value = '导入失败：文件里没有可用的预设定义（每项至少要有 type）'
      toast.error('导入失败：没有可用定义')
      return
    }
    replaceMode.value = false
    pendingImport.value = { bytes, count: preview.count, fileName: file.name, zip: false }
  } catch (e) {
    error.value = `导入失败：无法读取文件（${e instanceof Error ? e.message : String(e)}）`
    toast.error('导入失败：读取文件出错')
  }
}

function cancelImport(): void {
  pendingImport.value = null
  replaceMode.value = false
}

async function confirmImport(): Promise<void> {
  const payload = pendingImport.value
  if (!payload) {
    return
  }
  const replace = replaceMode.value
  pendingImport.value = null
  importing.value = true
  error.value = ''
  try {
    // kind 只在文件里没写 kind 时兜底；导出的文件本身每条都带 kind
    const result = await PresetApi.importYaml(payload.bytes, 'objectives', replace)
    await refresh()
    if (result.skipped.length) {
      toast.info(`已导入 ${result.imported} 条预设，跳过 ${result.skipped.length} 条`)
    } else {
      toast.success(`已${replace ? '替换' : '合并'}导入 ${result.imported} 条预设`)
    }
  } catch (e) {
    const message = `导入失败：${errorMessage(e)}`
    error.value = message
    unauthorized.value = isUnauthorized(e)
    toast.error(message)
  } finally {
    importing.value = false
  }
}

async function save(): Promise<void> {
  const current = draft.value
  if (!current || busy.value) {
    return
  }
  if (current.readOnly) {
    // 按钮已禁用，这里再挡一次：只读定义不能在库里覆盖
    toast.error('该预设由 presets/ 下的 YAML 文件定义，只读：请改文件后 /ptxa reload')
    return
  }
  // YAML 视图下先把文本落地（防抖窗口内的改动可能还没进草稿）；解析失败就拒绝保存
  if (yaml.mode.value === 'yaml' && !yaml.applyNow()) {
    toast.error('YAML 有语法错误，未保存')
    return
  }
  if (!current.name.trim()) {
    toast.error('请填写预设名称')
    return
  }
  if (!current.type.trim()) {
    toast.error('请选择预设类型')
    return
  }
  busy.value = true
  error.value = ''
  try {
    const payload: Preset = {
      id: current.id,
      name: current.name.trim(),
      type: current.type,
      description: current.description,
      properties: current.properties
    }
    const result = await PresetApi.save(current.kind, payload)
    invalidatePresets()
    // 后端会回显补齐 id 后的对象，直接以它为准刷新本地列表，不必再请求一次
    const saved = result.preset
    presets.value = upsertPreset(presets.value, current.kind, saved)
    draft.value = toDraft(current.kind, saved)
    yaml.syncFromSource()
    toast.success(`预设「${saved.name}」已保存`)
  } catch (e) {
    error.value = `保存预设失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
    toast.error(error.value)
  } finally {
    busy.value = false
  }
}

async function confirmDelete(): Promise<void> {
  const current = draft.value
  deletePending.value = false
  if (!current || busy.value) {
    return
  }
  busy.value = true
  error.value = ''
  try {
    const result = await PresetApi.remove(current.kind, current.id)
    invalidatePresets()
    presets.value = removePreset(presets.value, current.kind, current.id)
    draft.value = null
    toast.success(result.ok ? `预设 ${current.id} 已删除` : `后端未找到预设 ${current.id}`)
  } catch (e) {
    error.value = `删除预设失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
    toast.error(error.value)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.preset-layout {
  display: grid;
  grid-template-columns: 21rem minmax(0, 1fr);
  gap: 0.9rem;
  align-items: start;
}

.preset-side {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  position: sticky;
  top: 1rem;
  max-height: calc(100vh - 3rem);
  overflow: hidden;
}

/* 类别切换：一次只显示一类，省掉在长列表里滚很久 */
.preset-tabs {
  display: flex;
  gap: 0.3rem;
  padding-bottom: 0.3rem;
  border-bottom: 1px solid var(--border);
}

.preset-tab {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  padding: 0.3rem 0.5rem;
  border: 1px solid transparent;
  border-radius: var(--radius);
  background: transparent;
  color: var(--text-dim);
  font-family: inherit;
  font-size: 0.85rem;
  cursor: pointer;
}

.preset-tab:hover {
  background: var(--bg-hover);
  color: var(--text);
}

.preset-tab.active {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent);
}

.preset-tab .tab-count {
  font-size: 0.72rem;
  opacity: 0.8;
}

.preset-search {
  width: 100%;
}

/* 列表自己滚：侧栏固定高度，条目再多也不会把整页撑长 */
.preset-items {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.preset-side-foot {
  padding-top: 0.4rem;
  border-top: 1px solid var(--border);
}

.preset-side-foot .btn {
  width: 100%;
}

.preset-group-empty {
  padding: 0.3rem 0;
}

.list-item.invalid .name {
  text-decoration: line-through;
  text-decoration-color: var(--text-dim);
}

.preset-main {
  min-width: 0;
}

.preset-fields-title {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  margin: 1rem 0 0.6rem;
  font-size: 0.88rem;
  font-weight: 600;
}

.preset-id-line {
  margin-top: 0.9rem;
  border-top: 1px dashed var(--border-soft);
  padding-top: 0.5rem;
}

@media (max-width: 1080px) {
  .preset-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .preset-side {
    position: static;
    max-height: none;
  }
}
</style>
