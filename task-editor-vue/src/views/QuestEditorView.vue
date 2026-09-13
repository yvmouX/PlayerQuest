<!--
  任务编辑表单：固定操作栏 + 基础信息 + 目标区 + 奖励区 + 实时预览。

  目标与奖励的字段<b>完全由 /api/schema 驱动</b>：本文件里没有任何
  针对具体类型的分支，后端新增类型即可直接编辑。
  预览里的「类型名 / 字段名」也全部取自 schema。
-->
<template>
  <section class="view">
    <!-- 固定操作栏：滚动时始终可见 -->
    <header class="action-bar">
      <div class="bar-title">
        <strong>{{ persisted ? `编辑任务：${form.id}` : '新建任务' }}</strong>
        <span class="hint">{{ persisted ? '任务 id 保存后不可修改' : '填写 id 后保存即创建' }}</span>
      </div>

      <span v-if="dirty" class="dirty-flag" title="表单内容与上次保存的不一致">● 未保存修改</span>
      <span v-else class="clean-flag">已同步</span>

      <div class="spacer"></div>

      <button class="btn" type="button" :disabled="saving" @click="back">返回列表</button>
      <button
        class="btn"
        type="button"
        :disabled="loading || saving || savingCopy"
        title="以当前内容创建一份 id 以 _copy 结尾的新任务"
        @click="askSaveCopy"
      >
        {{ savingCopy ? '另存中…' : '另存为副本' }}
      </button>
      <button
        v-if="persisted"
        class="btn btn-danger"
        type="button"
        :disabled="loading || saving || deleting"
        @click="deletePending = true"
      >
        {{ deleting ? '删除中…' : '删除任务' }}
      </button>
      <button class="btn btn-primary" type="button" :disabled="loading || saving" @click="save">
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </header>

    <p v-if="error" class="panel error-panel">{{ error }}</p>
    <UnauthorizedHint :show="unauthorized" />
    <p v-if="loading" class="panel">正在加载任务…</p>

    <div v-else class="editor-layout">
      <div class="editor-main">
        <!-- 1. 基本信息 -->
        <section class="card">
          <h3>基本信息</h3>
          <!-- 表单行由 .form-rows 统一栅格：标签固定宽度左对齐，控件占满剩余空间 -->
          <div class="form-rows">
            <label class="field field-stack">
              <span class="field-label">任务 ID <em class="required">*</em></span>
              <input
                v-model="form.id"
                type="text"
                :readonly="persisted"
                :class="{ readonly: persisted }"
                placeholder="例如 daily_mine"
              />
              <small class="hint">
                {{ persisted ? '任务 ID 不可修改；需要新 id 请用「另存为副本」。' : '保存后不可修改，建议使用英文与下划线。' }}
              </small>
            </label>

            <label class="field field-stack">
              <span class="field-label">名称</span>
              <input v-model="form.name" type="text" placeholder="例如 <yellow>挖矿日常" />
              <small class="hint">支持颜色标签，按原文填写即可。</small>
            </label>

            <!-- 图标走素材选择器：枚举名（DIAMOND_ORE）不该靠人默写 -->
            <div class="field field-stack">
              <span class="field-label">图标</span>
              <div class="field-control">
                <MaterialPicker v-model="form.icon" placeholder="PAPER" />
                <small class="hint">点击展开搜索（支持中文名 / 英文名 / 枚举名），也可直接手打材质名。</small>
              </div>
            </div>

            <label class="field field-stack">
              <span class="field-label">分类</span>
              <input v-model="form.category" type="text" list="quest-category-options" placeholder="例如 每日" />
              <datalist id="quest-category-options">
                <option v-for="category in categories" :key="category" :value="category"></option>
              </datalist>
              <small class="hint">已在使用的分类，可留空。</small>
            </label>

            <label class="field field-stack">
              <span class="field-label">类型</span>
              <select v-model="form.type">
                <option value="NORMAL">普通任务（NORMAL）</option>
                <option value="DAILY">每日任务（DAILY）</option>
              </select>
              <small class="hint">每日任务按天重置，可被玩家消耗货币刷新。</small>
            </label>

            <!-- 刷新费用只对每日任务有意义 -->
            <label v-if="form.type === 'DAILY'" class="field field-stack">
              <span class="field-label">刷新费用</span>
              <input type="number" step="0.01" min="0" :value="form.refreshCost" @input="onRefreshCostInput" />
              <small class="hint">玩家手动刷新每日任务时扣除的金额，0 表示免费。</small>
            </label>

            <div class="field field-stack">
              <span class="field-label">启用</span>
              <div class="field-control">
                <span class="checkbox-line">
                  <input v-model="form.enabled" type="checkbox" />
                  <span class="checkbox-text">{{ form.enabled ? '已启用' : '已禁用' }}</span>
                </span>
                <small class="hint">禁用后玩家看不到该任务。</small>
              </div>
            </div>

            <label class="field field-stack field-wide">
              <span class="field-label">描述</span>
              <textarea v-model="descriptionText" rows="4" placeholder="每行一条描述"></textarea>
              <small class="hint">一行一条，保存时转成数组。</small>
            </label>
          </div>
        </section>

        <!-- 2. 目标 -->
        <section class="card">
          <header class="card-head">
            <h3>目标（{{ objectiveRows.length }}）</h3>
            <button class="btn btn-small btn-primary" type="button" @click="askAdd('objectives')">
              + 添加目标
            </button>
          </header>
          <p class="hint instance-note">
            顺序有意义：第 N 个目标对应 <code class="mono">/ptxa setobjective &lt;玩家&gt; &lt;任务&gt; N-1 &lt;数量&gt;</code>
            命令里的序号（该命令从 0 开始计数，卡片上的 #N 从 1 开始显示），用卡片上的 ↑ ↓ 调整。
          </p>
          <p v-if="noObjectiveTypes" class="warn-line">后端没有注册任何目标类型，请检查插件依赖。</p>
          <p v-else-if="!objectiveRows.length" class="guide-line">
            还没有目标。点击右上角「添加目标」：可以直接套用一个预设，也可以从空白新建。
            没有目标的任务在加载时会被跳过。
          </p>
          <div class="instance-list">
            <TypeInstanceEditor
              v-for="(row, index) in objectiveRows"
              :key="row.uid"
              :index="index"
              :last="index === objectiveRows.length - 1"
              :type="row.type"
              :properties="row.properties"
              :schemas="objectiveSchemas"
              @update:type="row.type = $event"
              @update:properties="row.properties = $event"
              @move-up="moveRow(objectiveRows, index, -1)"
              @move-down="moveRow(objectiveRows, index, 1)"
              @save-as-preset="askSavePreset('objectives', row)"
              @remove="removeObjective(index)"
            />
          </div>
        </section>

        <!-- 3. 奖励 -->
        <section class="card">
          <header class="card-head">
            <h3>奖励（{{ rewardRows.length }}）</h3>
            <button class="btn btn-small btn-primary" type="button" @click="askAdd('rewards')">
              + 添加奖励
            </button>
          </header>
          <p class="hint instance-note">奖励按从上到下的顺序发放，用卡片上的 ↑ ↓ 调整顺序。</p>
          <p v-if="noRewardTypes" class="warn-line">后端没有注册任何奖励类型。</p>
          <p v-else-if="!rewardRows.length" class="guide-line">
            还没有奖励。没有奖励的任务也能正常存在，但玩家完成后拿不到任何东西。
          </p>
          <div class="instance-list">
            <TypeInstanceEditor
              v-for="(row, index) in rewardRows"
              :key="row.uid"
              :index="index"
              :last="index === rewardRows.length - 1"
              :type="row.type"
              :properties="row.properties"
              :schemas="rewardSchemas"
              reward
              @update:type="row.type = $event"
              @update:properties="row.properties = $event"
              @move-up="moveRow(rewardRows, index, -1)"
              @move-down="moveRow(rewardRows, index, 1)"
              @save-as-preset="askSavePreset('rewards', row)"
              @remove="removeReward(index)"
            />
          </div>
        </section>
      </div>

      <!-- 右侧：校验问题 + 实时预览 -->
      <div class="editor-side">
        <section v-if="problems.length" class="card problem-card">
          <h3>⚠ 后端校验问题（{{ problems.length }}）</h3>
          <ul class="problems">
            <li v-for="(problem, index) in problems" :key="index">{{ problem }}</li>
          </ul>
          <p class="hint">
            保存没有失败，但这些问题会影响游戏内表现（例如奖励类型不可用）。
            建议修正后再次保存，否则任务可能无法正常发放奖励。
          </p>
        </section>

        <PreviewPane
          :title="preview.title"
          :subtitle="preview.subtitle"
          :description="preview.description"
          :objectives="preview.objectives"
          :rewards="preview.rewards"
        />
      </div>
    </div>

    <!-- 删除确认 -->
    <ConfirmDialog
      :show="deletePending"
      title="删除任务"
      :message="`确定删除任务「${form.id}」吗？该操作会立即写入任务文件，不可撤销。`"
      confirm-text="删除"
      danger
      @confirm="confirmDelete"
      @cancel="deletePending = false"
    />

    <!-- 另存为副本确认 -->
    <ConfirmDialog
      :show="copyPending"
      title="另存为副本"
      :message="copyMessage"
      :confirm-text="`创建副本 ${copyTargetId}`"
      @confirm="saveAsCopy"
      @cancel="copyPending = false"
    />

    <!-- 添加目标 / 奖励：先列预设，再给「从空白新建」的入口 -->
    <PresetPickerDialog
      :show="addKind !== null"
      :kind="addKind ?? 'objectives'"
      :schemas="addKind === 'rewards' ? rewardSchemas : objectiveSchemas"
      @pick="addFromPreset"
      @blank="addBlank"
      @cancel="addKind = null"
    />

    <!-- 把当前卡片另存为预设 -->
    <SavePresetDialog
      :show="presetTarget !== null"
      :kind="presetTarget?.kind ?? 'objectives'"
      :type="presetTarget?.row.type ?? ''"
      :suggested-summary="presetSuggested.summary"
      :suggested-name="presetSuggested.name"
      :busy="presetSaving"
      @save="savePreset"
      @cancel="presetTarget = null"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import MaterialPicker from '../components/MaterialPicker.vue'
import PresetPickerDialog from '../components/PresetPickerDialog.vue'
import PreviewPane from '../components/PreviewPane.vue'
import SavePresetDialog from '../components/SavePresetDialog.vue'
import TypeInstanceEditor from '../components/TypeInstanceEditor.vue'
import UnauthorizedHint from '../components/UnauthorizedHint.vue'
import { useToast } from '../composables/useToast'
import { PresetApi, QuestApi, SchemaApi, StatsApi, errorMessage, isUnauthorized } from '../services/api'
import type { Preset, PresetKind, Properties, Quest, QuestType, TypeSchema } from '../types'
import { loadCatalog } from '../utils/catalog'
import { invalidatePresets, loadPresets, presetProperties, suggestPresetName } from '../utils/presets'
import { defaultProperties, normalizeInstances, summarizeProperties, typeLabel, withDefaults } from '../utils/schema'
import { stripTags } from '../utils/text'

/** 路由传入的任务 id；新建路由没有这个参数。 */
const props = defineProps<{ id?: string }>()

const router = useRouter()
const toast = useToast()

/** 一行目标/奖励。uid 让 v-for 的 key 稳定，删除中间项不会串位。 */
interface InstanceRow {
  uid: number
  type: string
  properties: Properties
}

/** 表单里的基础信息（描述单独用文本域，目标/奖励单独用行数组）。 */
interface QuestForm {
  id: string
  name: string
  icon: string
  category: string
  type: QuestType
  refreshCost: number
  enabled: boolean
}

const form = reactive<QuestForm>({
  id: '',
  name: '',
  icon: 'PAPER',
  category: '',
  type: 'NORMAL',
  refreshCost: 0,
  enabled: true
})

const descriptionText = ref('')
const objectiveRows = ref<InstanceRow[]>([])
const rewardRows = ref<InstanceRow[]>([])
const objectiveSchemas = ref<Record<string, TypeSchema>>({})
const rewardSchemas = ref<Record<string, TypeSchema>>({})
const categories = ref<string[]>([])
const problems = ref<string[]>([])
const loading = ref(false)
const saving = ref(false)
const savingCopy = ref(false)
const deleting = ref(false)
const error = ref('')
const unauthorized = ref(false)

/** 表单当前对应的任务 id：null 表示还没加载过，'' 表示新建中。 */
const editingId = ref<string | null>(null)
/** 是否已经是服务端存在的任务（决定 id 是否只读、是否显示删除）。 */
const persisted = computed(() => !!editingId.value)

/** 已从服务端加载过的任务 id；用于避免路由变化时把未保存的改动冲掉。 */
let loadedId: string | null = null
/** 上次保存/加载后的表单快照，用来判断「未保存修改」。 */
const baseline = ref('')

const noObjectiveTypes = computed(() => Object.keys(objectiveSchemas.value).length === 0)
const noRewardTypes = computed(() => Object.keys(rewardSchemas.value).length === 0)

let uidSeq = 0
let schemaPromise: Promise<void> | null = null

/* ---------------- 表单组装 ---------------- */

function buildQuest(): Quest {
  // 兜底成字符串：表单状态异常时也不该让整个编辑器崩掉
  const id = String(form.id ?? '').trim()
  return {
    id,
    name: form.name.trim() || id,
    description: descriptionText.value
      .split(/\r?\n/)
      .map(line => line.trim())
      .filter(line => line.length > 0),
    icon: form.icon.trim() || 'PAPER',
    category: form.category.trim(),
    type: form.type,
    refreshCost: form.refreshCost,
    enabled: form.enabled,
    objectives: normalizeInstances(objectiveRows.value, objectiveSchemas.value),
    rewards: normalizeInstances(rewardRows.value, rewardSchemas.value),
    // problems 是后端算出来的派生信息，提交时会被忽略
    problems: []
  }
}

/** 当前表单的序列化结果；与 baseline 比较即可判断是否有未保存修改。 */
const serialized = computed(() => JSON.stringify(buildQuest()))
const dirty = computed(() => baseline.value !== '' && serialized.value !== baseline.value)

/* ---------------- 实时预览（全部基于 schema） ---------------- */

/**
 * 找出「数量」字段：目标/奖励类型里的第一个 INTEGER 字段。
 * 后端每种类型都需要一个数量（amount 之类），字段名由后端决定，
 * 这里不写死任何键名，找不到就不画进度条。
 */
function firstAmountField(schema: TypeSchema | undefined): string {
  return schema?.fields.find(field => field.type === 'INTEGER')?.key ?? ''
}

const preview = computed(() => {
  const numberText = (value: unknown): string => {
    const numeric = Number(value)
    return Number.isFinite(numeric) ? String(numeric) : '0'
  }

  const objectives = objectiveRows.value.map((row, index) => {
    const schema = objectiveSchemas.value[row.type]
    const amountKey = firstAmountField(schema)
    const amount = amountKey ? Number(row.properties[amountKey]) : Number.NaN
    return {
      key: `o${row.uid}`,
      index: index + 1,
      label: schema ? typeLabel(schema) : row.type || '（未选择类型）',
      count: amountKey ? `0 / ${numberText(row.properties[amountKey])}` : '',
      detail: summarizeProperties(row.properties, schema),
      // 玩家刚接取时进度必然是 0，因此有有效数量时画一条 0% 的进度条，否则不画
      percent: amountKey && Number.isFinite(amount) && amount > 0 ? 0 : null
    }
  })

  const rewards = rewardRows.value.map((row, index) => {
    const schema = rewardSchemas.value[row.type]
    return {
      key: `r${row.uid}`,
      index: index + 1,
      label: schema ? typeLabel(schema) : row.type || '（未选择类型）',
      count: '',
      detail: summarizeProperties(row.properties, schema),
      percent: null
    }
  })

  const id = String(form.id ?? '').trim()
  return {
    title: stripTags(form.name) || id || '',
    subtitle: [
      id ? `id: ${id}` : '',
      form.type === 'DAILY' ? '每日任务' : '普通任务',
      form.category ? `分类: ${form.category}` : '',
      form.enabled ? '' : '（已禁用，玩家看不到）'
    ].filter(Boolean).join(' · '),
    description: descriptionText.value
      .split(/\r?\n/)
      .map(line => stripTags(line))
      .filter(line => line.length > 0),
    objectives,
    rewards
  }
})

/* ---------------- 加载 ---------------- */

// 路由变化即重新装填表单；保存后跳转回同一 id 时不会重复加载
watch(() => props.id, id => {
  if (id && id === loadedId) {
    return
  }
  void open(id ?? '')
}, { immediate: true })

onMounted(() => {
  void loadCategories()
  // 预加载素材目录与预设：打开选择器/弹层时不用等网络，也不会在输入时才卡一下
  void loadCatalog()
  void loadPresets()
  window.addEventListener('beforeunload', onBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
})

/** 有未保存修改时，关闭/刷新标签页给一次浏览器原生提醒。 */
function onBeforeUnload(event: BeforeUnloadEvent): void {
  if (dirty.value) {
    event.preventDefault()
  }
}

/** 打开某个任务（'' = 新建）。schema 是动态表单的前提，先确保到位。 */
async function open(id: string): Promise<void> {
  if (!id) {
    editingId.value = ''
    loadedId = null
    if (!schemaPromise) {
      schemaPromise = loadSchemas()
    }
    await schemaPromise
    resetToNew()
    return
  }
  if (editingId.value === id && loadedId === id) {
    return
  }
  editingId.value = id
  if (!schemaPromise) {
    schemaPromise = loadSchemas()
  }
  await schemaPromise
  await load(id)
}

async function loadSchemas(): Promise<void> {
  try {
    const schema = await SchemaApi.get()
    objectiveSchemas.value = schema.objectives ?? {}
    rewardSchemas.value = schema.rewards ?? {}
    // 任务数据可能先于 schema 到达，补一次默认值让界面与提交内容都完整；
    // 这里要连同行上的 uid 一起铺回来，否则 v-for 的 key 会变，正在编辑的卡片会被重建
    const fill = (rows: InstanceRow[], schemas: Record<string, TypeSchema>): InstanceRow[] =>
      rows.map(row => ({ ...row, properties: withDefaults(schemas[row.type], row.properties) }))
    objectiveRows.value = fill(objectiveRows.value, objectiveSchemas.value)
    rewardRows.value = fill(rewardRows.value, rewardSchemas.value)
  } catch (e) {
    error.value = `加载类型定义失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
  }
}

async function loadCategories(): Promise<void> {
  try {
    const stats = await StatsApi.get()
    categories.value = stats.categories ?? []
  } catch {
    // 分类候选只是输入便利，失败静默处理
  }
}

async function load(id: string): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    applyQuest(await QuestApi.get(id))
    loadedId = id
  } catch (e) {
    error.value = `加载任务 ${id} 失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
    toast.error(error.value)
  } finally {
    loading.value = false
  }
}

function toRows(
  instances: { type: string; properties: Properties }[],
  schemas: Record<string, TypeSchema>
): InstanceRow[] {
  return normalizeInstances(instances, schemas).map(instance => ({ uid: ++uidSeq, ...instance }))
}

function applyQuest(quest: Quest): void {
  editingId.value = quest.id
  form.id = quest.id
  form.name = quest.name ?? ''
  form.icon = quest.icon || 'PAPER'
  form.category = quest.category ?? ''
  form.type = quest.type === 'DAILY' ? 'DAILY' : 'NORMAL'
  form.refreshCost = Number(quest.refreshCost) || 0
  form.enabled = quest.enabled !== false
  descriptionText.value = (quest.description ?? []).join('\n')
  objectiveRows.value = toRows(quest.objectives, objectiveSchemas.value)
  rewardRows.value = toRows(quest.rewards, rewardSchemas.value)
  problems.value = [...(quest.problems ?? [])]
  baseline.value = JSON.stringify(buildQuest())
}

/** 清空成新建状态。 */
function resetToNew(): void {
  form.id = ''
  form.name = ''
  form.icon = 'PAPER'
  form.category = ''
  form.type = 'NORMAL'
  form.refreshCost = 0
  form.enabled = true
  descriptionText.value = ''
  objectiveRows.value = []
  rewardRows.value = []
  problems.value = []
  baseline.value = JSON.stringify(buildQuest())
}

/* ---------------- 目标 / 奖励编辑 ---------------- */

/**
 * 添加流程：先弹预设列表（点一下直接套用），弹层里再给「从空白新建」。
 *
 * <p>addKind 同时决定弹层显示哪一组预设、以及套用时往哪个数组插入。
 */
const addKind = ref<PresetKind | null>(null)

function askAdd(kind: PresetKind): void {
  addKind.value = kind
}

function rowsOf(kind: PresetKind): InstanceRow[] {
  return kind === 'objectives' ? objectiveRows.value : rewardRows.value
}

function schemasOf(kind: PresetKind): Record<string, TypeSchema> {
  return kind === 'objectives' ? objectiveSchemas.value : rewardSchemas.value
}

/** 从空白新建：默认选第一个类型，并按 schema 铺好默认值。 */
function addBlank(): void {
  const kind = addKind.value
  addKind.value = null
  if (!kind) {
    return
  }
  const schemas = schemasOf(kind)
  const first = Object.keys(schemas)[0] ?? ''
  rowsOf(kind).push({ uid: ++uidSeq, type: first, properties: defaultProperties(schemas[first]) })
}

/**
 * 套用预设。
 *
 * <p>用 {@link presetProperties} 而不是直接复制 properties：预设可能是旧版本存的，
 * 缺字段时按 schema 补默认值，套用后立刻就是一条可编辑、可保存的完整配置。
 *
 * <p>仍然复查一次类型是否存在：弹层的判断用的是同一次 schema，但 schema 是异步
 * 加载的，多一道校验可以避免把无效类型塞进表单。
 */
function addFromPreset(preset: Preset): void {
  const kind = addKind.value
  if (!kind) {
    return
  }
  const schemas = schemasOf(kind)
  if (!schemas[preset.type]) {
    toast.error(`预设「${preset.name}」的类型 ${preset.type} 不存在，无法套用`)
    return
  }
  addKind.value = null
  rowsOf(kind).push({
    uid: ++uidSeq,
    type: preset.type,
    properties: presetProperties(preset, schemas[preset.type])
  })
  toast.success(`已套用预设「${preset.name}」`)
}

function removeObjective(index: number): void {
  objectiveRows.value.splice(index, 1)
}

function removeReward(index: number): void {
  rewardRows.value.splice(index, 1)
}

/* ---------------- 另存为预设 ---------------- */

const presetSaving = ref(false)
const presetTarget = ref<{ kind: PresetKind; row: InstanceRow } | null>(null)

/** 弹层里的建议名称：类型显示名 + 关键属性，管理员多半直接回车即可。 */
const presetSuggested = computed(() => {
  const target = presetTarget.value
  if (!target) {
    return { name: '', summary: '' }
  }
  const schema = schemasOf(target.kind)[target.row.type]
  return {
    name: suggestPresetName(schema, target.row.type, target.row.properties),
    summary: summarizeProperties(target.row.properties, schema) || '（使用该类型的默认值）'
  }
})

function askSavePreset(kind: PresetKind, row: InstanceRow): void {
  if (!row.type) {
    toast.error('请先为该条目选择类型')
    return
  }
  presetTarget.value = { kind, row }
}

/**
 * 保存预设。
 *
 * <p>刻意不带 id：每次都新建一条预设，避免把已有预设静默覆盖掉
 * （要改已有预设请去「预设管理」页）。
 */
async function savePreset(name: string): Promise<void> {
  const target = presetTarget.value
  if (!target || presetSaving.value) {
    return
  }
  presetSaving.value = true
  try {
    const preset: Preset = {
      id: '',
      name: name.trim(),
      type: target.row.type,
      description: '',
      properties: withDefaults(schemasOf(target.kind)[target.row.type], target.row.properties)
    }
    await PresetApi.save(target.kind, preset)
    // 预设列表变了：让缓存失效，下次打开「添加」弹层才会看到刚存的这条
    invalidatePresets()
    toast.success(`已保存预设「${preset.name}」`)
    presetTarget.value = null
  } catch (e) {
    toast.error(`保存预设失败：${errorMessage(e)}`)
  } finally {
    presetSaving.value = false
  }
}

/** 上下移动一行：顺序决定 setobjective 的目标序号与奖励发放顺序。 */
function moveRow(rows: InstanceRow[], index: number, offset: number): void {
  const target = index + offset
  if (target < 0 || target >= rows.length) {
    return
  }
  const [row] = rows.splice(index, 1)
  rows.splice(target, 0, row)
}

function onRefreshCostInput(event: Event): void {
  const target = event.target as HTMLInputElement | null
  const value = target ? Number(target.value) : 0
  form.refreshCost = Number.isFinite(value) && value >= 0 ? value : 0
}

/* ---------------- 保存 / 删除 ---------------- */

async function save(): Promise<void> {
  if (!String(form.id ?? '').trim()) {
    toast.error('任务 ID 不能为空')
    return
  }
  saving.value = true
  error.value = ''
  try {
    const quest = buildQuest()
    const result = await QuestApi.save(quest)
    problems.value = [...(result.problems ?? [])]
    baseline.value = JSON.stringify(buildQuest())
    unauthorized.value = false
    if (problems.value.length) {
      toast.info(`已保存，但存在 ${problems.value.length} 个校验问题（见右侧）`)
    } else {
      toast.success(`任务 ${result.id} 已保存`)
    }
    // 新建成功后切到编辑态：id 转为只读，避免再改 id 变成「另存一份」
    if (!persisted.value) {
      const savedId = result.id || quest.id
      editingId.value = savedId
      loadedId = savedId
      form.id = savedId
      if (props.id !== savedId) {
        await router.replace({ name: 'quest-edit', params: { id: savedId } })
      }
    }
  } catch (e) {
    error.value = `保存失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
    toast.error(error.value)
  } finally {
    saving.value = false
  }
}

const copyPending = ref(false)

/** 副本目标 id：<原 id>_copy，已存在时继续加序号，避免覆盖。 */
const copyTargetId = computed(() => `${String(form.id ?? '').trim() || 'quest'}_copy`)
const copyMessage = computed(() =>
  `将以当前表单内容创建一个新任务「${copyTargetId.value}」，原任务不会被修改。\n\n`
  + '若该 id 已存在，后端会直接覆盖同名任务。'
)

function askSaveCopy(): void {
  if (!String(form.id ?? '').trim()) {
    toast.error('请先填写任务 ID')
    return
  }
  copyPending.value = true
}

async function saveAsCopy(): Promise<void> {
  copyPending.value = false
  const targetId = copyTargetId.value
  savingCopy.value = true
  error.value = ''
  try {
    const result = await QuestApi.save({ ...buildQuest(), id: targetId })
    problems.value = [...(result.problems ?? [])]
    // 以实际提交的 id 为准：后端未回显 id 时也能正确切到副本
    const savedId = result.id || targetId
    // 切换为「编辑副本」状态，并同步基准快照
    editingId.value = savedId
    loadedId = savedId
    form.id = savedId
    baseline.value = JSON.stringify(buildQuest())
    toast.success(`已另存为副本 ${savedId}`)
    if (props.id !== savedId) {
      await router.replace({ name: 'quest-edit', params: { id: savedId } })
    }
  } catch (e) {
    error.value = `另存为副本失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
    toast.error(error.value)
  } finally {
    savingCopy.value = false
  }
}

const deletePending = ref(false)

async function confirmDelete(): Promise<void> {
  const id = editingId.value
  deletePending.value = false
  if (!id) {
    return
  }
  deleting.value = true
  try {
    const result = await QuestApi.remove(id)
    // 删除后不再有未保存内容，避免离开页面时弹出无用提醒
    baseline.value = serialized.value
    loadedId = null
    editingId.value = ''
    toast.success(result.ok ? `任务 ${result.id} 已删除` : `后端未找到任务 ${result.id}`)
    await router.push({ name: 'quest-list' })
  } catch (e) {
    error.value = `删除失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
    toast.error(error.value)
  } finally {
    deleting.value = false
  }
}

function back(): void {
  void router.push({ name: 'quest-list' })
}
</script>
