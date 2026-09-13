<!--
  任务列表（管理台核心页面）。

  功能：搜索 / 多维筛选 / 表头排序 / 分页 / 统计条 / 批量启用禁用删除 /
  复制任务 / 单行启用开关 / 校验问题标红与 hover 详情 / 导入导出。

  <p>列表数据全部来自 GET /api/quests，其中 problems 由后端计算；
  批量操作是按条调用既有接口（后端没有批量接口），因此失败时会逐条报告。
-->
<template>
  <section class="view">
    <header class="view-head">
      <div>
        <h2>任务列表</h2>
        <p class="hint">
          共 {{ quests.length }} 个任务<template v-if="storage"> · 存储：{{ storage }}</template>
          · 数据来自 /api/quests
        </p>
      </div>
      <div class="view-actions">
        <button class="btn" type="button" :disabled="loading || busy" @click="refresh">
          {{ loading ? '加载中…' : '刷新' }}
        </button>
        <button class="btn" type="button" :disabled="loading || busy || exporting" @click="exportQuests">
          {{ exporting ? '导出中…' : '导出 JSON' }}
        </button>
        <button class="btn" type="button" :disabled="loading || busy || importing" @click="pickImportFile">
          {{ importing ? '导入中…' : '导入 JSON' }}
        </button>
        <RouterLink class="btn btn-primary" :to="{ name: 'quest-new' }">新建任务</RouterLink>
      </div>
    </header>

    <p v-if="error" class="panel error-panel">{{ error }}</p>
    <UnauthorizedHint :show="unauthorized" />

    <!-- 统计条 -->
    <div class="stat-bar">
      <span class="stat-item">共 <b>{{ quests.length }}</b> 个任务</span>
      <span class="stat-item ok">启用 <b>{{ enabledCount }}</b></span>
      <span class="stat-item">每日 <b>{{ dailyCount }}</b></span>
      <span class="stat-item" :class="{ danger: problemCount > 0 }">
        有问题 <b>{{ problemCount }}</b>
      </span>
      <span class="hint">筛选后 {{ filtered.length }} 条</span>
    </div>

    <!-- 搜索与筛选 -->
    <div class="card">
      <div class="filter-bar">
        <label class="filter-item search">
          <span>搜索</span>
          <input
            v-model="search"
            type="search"
            placeholder="按任务 id 或名称模糊搜索"
            autocomplete="off"
          />
        </label>

        <label class="filter-item">
          <span>类型</span>
          <select v-model="typeFilter">
            <option value="ALL">全部</option>
            <option value="DAILY">每日（DAILY）</option>
            <option value="NORMAL">普通（NORMAL）</option>
          </select>
        </label>

        <label class="filter-item">
          <span>分类</span>
          <select v-model="categoryFilter">
            <option value="ALL">全部</option>
            <option v-for="item in categoryChoices" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>

        <label class="filter-item">
          <span>启用状态</span>
          <select v-model="enabledFilter">
            <option value="ALL">全部</option>
            <option value="ON">仅已启用</option>
            <option value="OFF">仅已禁用</option>
          </select>
        </label>

        <label class="filter-item">
          <span>校验</span>
          <select v-model="problemFilter">
            <option value="ALL">全部</option>
            <option value="ONLY">只看有问题的任务</option>
            <option value="NONE">只看正常的任务</option>
          </select>
        </label>

        <button class="btn btn-small" type="button" @click="resetFilters">重置筛选</button>
      </div>
    </div>

    <!-- 批量操作条：有选中项时才出现 -->
    <div v-if="selected.size" class="bulk-bar">
      <strong>已选中 {{ selected.size }} 个任务</strong>
      <div class="spacer"></div>
      <button class="btn btn-small" type="button" :disabled="busy" @click="bulkToggle(true)">批量启用</button>
      <button class="btn btn-small" type="button" :disabled="busy" @click="bulkToggle(false)">批量禁用</button>
      <button class="btn btn-small btn-danger" type="button" :disabled="busy" @click="askBulkDelete">
        批量删除（{{ selected.size }}）
      </button>
      <button class="btn btn-small" type="button" :disabled="busy" @click="clearSelection">取消选择</button>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="panel">正在加载任务列表…</div>

    <!-- 空状态：区分「一个任务都没有」与「筛选后为空」 -->
    <div v-else-if="!quests.length" class="card empty-state">
      <strong>还没有任何任务</strong>
      <p class="hint">点击右上角「新建任务」创建第一个，或从已有备份导入 JSON。</p>
      <div class="view-actions">
        <RouterLink class="btn btn-primary" :to="{ name: 'quest-new' }">新建任务</RouterLink>
        <button class="btn" type="button" @click="pickImportFile">导入 JSON</button>
      </div>
    </div>

    <div v-else-if="!filtered.length" class="card empty-state">
      <strong>没有符合条件的任务</strong>
      <p class="hint">当前筛选条件下没有结果，试试放宽条件或重置筛选。</p>
      <button class="btn" type="button" @click="resetFilters">重置筛选</button>
    </div>

    <template v-else>
      <DataTable
        :columns="columns"
        :rows="paged"
        :row-key="row => row.id"
        :row-class="rowClass"
        :sort-key="sortKey"
        :sort-direction="sortDirection"
        @sort="toggleSort"
      >
        <template #select="{ row }">
          <EditCheckbox
            :model-value="selected.has(row.id)"
            :aria-label="`选择任务 ${row.id}`"
            @update:model-value="value => toggleSelect(row.id, value)"
          />
        </template>

        <template #id="{ row }">
          <span class="mono">{{ row.id }}</span>
        </template>

        <template #name="{ row }">
          <div class="cell-name">
            <span class="raw" :title="row.name">{{ row.name }}</span>
            <span v-if="plainIfDifferent(row.name)" class="hint">{{ plainIfDifferent(row.name) }}</span>
          </div>
        </template>

        <template #type="{ row }">
          <span class="badge" :class="row.type === 'DAILY' ? 'badge-blue' : 'badge-gray'">
            {{ row.type === 'DAILY' ? '每日' : '普通' }}
          </span>
        </template>

        <template #category="{ row }">
          <span>{{ row.category || '—' }}</span>
        </template>

        <template #objectives="{ row }">
          <span class="mono">{{ row.objectives.length }}</span>
        </template>

        <template #rewards="{ row }">
          <span class="mono">{{ row.rewards.length }}</span>
        </template>

        <template #prerequisites="{ row }">
          <span v-if="!row.prerequisites?.length" class="hint">—</span>
          <span v-else class="mono" :title="row.prerequisites.join('\n')">
            {{ row.prerequisites.length }}
          </span>
        </template>

        <template #state="{ row }">
          <button
            class="switch"
            :class="{ on: row.enabled }"
            type="button"
            :disabled="busy"
            :title="row.enabled ? '点击禁用' : '点击启用'"
            @click="toggleEnabled(row)"
          >
            <span class="switch-dot"></span>
            {{ row.enabled ? '已启用' : '已禁用' }}
          </button>
        </template>

        <template #problems="{ row }">
          <span v-if="!row.problems.length" class="ok-text">正常</span>
          <span v-else class="problem-badge" :title="problemTooltip(row)">
            ⚠ {{ row.problems.length }} 个问题
          </span>
        </template>

        <template #actions="{ row }">
          <div class="row-actions">
            <RouterLink class="btn btn-small" :to="{ name: 'quest-edit', params: { id: row.id } }">编辑</RouterLink>
            <button class="btn btn-small" type="button" :disabled="busy" @click="duplicate(row)">复制</button>
            <button class="btn btn-small btn-danger" type="button" :disabled="busy" @click="askDelete(row)">
              删除
            </button>
          </div>
        </template>
      </DataTable>

      <PaginationBar
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="filtered.length"
      />
    </template>

    <!-- 单条删除确认 -->
    <ConfirmDialog
      :show="pendingDelete !== null"
      title="删除任务"
      :message="deleteMessage"
      confirm-text="删除"
      danger
      @confirm="confirmDelete"
      @cancel="pendingDelete = null"
    />

    <!-- 批量删除确认 -->
    <ConfirmDialog
      :show="bulkDeletePending"
      title="批量删除任务"
      :message="bulkDeleteMessage"
      :confirm-text="`删除 ${selected.size} 个任务`"
      danger
      @confirm="confirmBulkDelete"
      @cancel="bulkDeletePending = false"
    />

    <!-- 导入：选择合并 / 替换 -->
    <ConfirmDialog
      :show="pendingImport !== null"
      title="导入任务"
      :message="importMessage"
      :confirm-text="replaceMode ? '替换导入' : '合并导入'"
      :danger="replaceMode"
      @confirm="confirmImport"
      @cancel="cancelImport"
    >
      <template #options>
        <label class="checkbox-line">
          <input v-model="replaceMode" type="checkbox" />
          <span class="checkbox-text">替换模式：先清空现有任务再导入</span>
        </label>
        <p class="hint">
          不勾选为「合并」：保留现有任务，id 相同的由文件里的版本覆盖。
          勾选后会先删除当前全部 {{ quests.length }} 个任务，不可撤销。
        </p>
      </template>
    </ConfirmDialog>

    <div v-if="importResult" class="card">
      <header class="card-head">
        <h3>上次导入结果</h3>
        <button class="btn btn-small" type="button" @click="importResult = null">关闭</button>
      </header>
      <p class="ok-text">成功导入 {{ importResult.imported }} 个任务，导入后共 {{ importResult.total }} 个。</p>
      <template v-if="importResult.skipped.length">
        <p class="warn-line">跳过 {{ importResult.skipped.length }} 条：</p>
        <ul class="problems">
          <li v-for="(reason, index) in importResult.skipped" :key="index">{{ reason }}</li>
        </ul>
      </template>
    </div>

    <!-- 隐藏的文件选择框：只接受 json -->
    <input
      ref="fileInput"
      class="hidden-file"
      type="file"
      accept="application/json,.json"
      @change="onFilePicked"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import DataTable from '../components/DataTable.vue'
import EditCheckbox from '../components/EditCheckbox.vue'
import PaginationBar from '../components/PaginationBar.vue'
import UnauthorizedHint from '../components/UnauthorizedHint.vue'
import { useToast } from '../composables/useToast'
import { QuestApi, StatsApi, errorMessage, isUnauthorized, normalizeImportedQuest } from '../services/api'
import type {
  Quest,
  QuestImportResult,
  SortDirection,
  TableColumn
} from '../types'
import { plainIfDifferent } from '../utils/text'

const toast = useToast()
const router = useRouter()

/** 各筛选下拉的取值类型。 */
type TypeFilter = 'ALL' | 'DAILY' | 'NORMAL'
type EnabledFilter = 'ALL' | 'ON' | 'OFF'
type ProblemFilter = 'ALL' | 'ONLY' | 'NONE'

const quests = ref<Quest[]>([])
const storage = ref('')
const loading = ref(false)
const busy = ref(false)
const exporting = ref(false)
const importing = ref(false)
const error = ref('')
const unauthorized = ref(false)

/* ---------------- 搜索 / 筛选 / 排序 / 分页 ---------------- */

const search = ref('')
const typeFilter = ref<TypeFilter>('ALL')
const categoryFilter = ref('ALL')
const enabledFilter = ref<EnabledFilter>('ALL')
const problemFilter = ref<ProblemFilter>('ALL')

const sortKey = ref('id')
const sortDirection = ref<SortDirection>('asc')
const page = ref(1)
const pageSize = ref(20)

/** 磁盘上出现过的分类（来自 /api/stats），用于分类下拉。 */
const categories = ref<string[]>([])

const columns: TableColumn[] = [
  { key: 'id', label: 'ID', sortable: true, width: '12rem' },
  { key: 'name', label: '名称', sortable: true },
  { key: 'type', label: '类型', sortable: true, width: '5.5rem' },
  { key: 'category', label: '分类', width: '7rem' },
  { key: 'objectives', label: '目标', sortable: true, align: 'right', width: '4.5rem' },
  { key: 'rewards', label: '奖励', align: 'right', width: '4.5rem' },
  // 前置只显示数量 + 悬停看 id：列表里的重点是「有没有任务链」，具体关系在编辑页看
  { key: 'prerequisites', label: '前置', align: 'right', width: '4.5rem' },
  { key: 'state', label: '启用', width: '6.5rem' },
  { key: 'problems', label: '校验', width: '9rem' },
  { key: 'actions', label: '操作', width: '14rem' }
]

/** 分类下拉的候选项：磁盘上已有的分类 + 当前列表里用到的分类。 */
const categoryChoices = computed(() => {
  const set = new Set<string>()
  for (const quest of quests.value) {
    if (quest.category) {
      set.add(quest.category)
    }
  }
  for (const item of categories.value) {
    if (item) {
      set.add(item)
    }
  }
  return [...set].sort((left, right) => left.localeCompare(right, 'zh-Hans-CN'))
})

const enabledCount = computed(() => quests.value.filter(quest => quest.enabled).length)
const dailyCount = computed(() => quests.value.filter(quest => quest.type === 'DAILY').length)
const problemCount = computed(() => quests.value.filter(quest => quest.problems.length > 0).length)

/** 中文环境下的自然比较，避免「任务10」排在「任务2」前面。 */
function compareText(left: string, right: string): number {
  return left.localeCompare(right, 'zh-Hans-CN', { numeric: true, sensitivity: 'base' })
}

function sortRows(rows: Quest[]): Quest[] {
  const direction = sortDirection.value === 'asc' ? 1 : -1
  const key = sortKey.value
  return [...rows].sort((left, right) => {
    let result = 0
    if (key === 'name') {
      // 按管理员实际看到的文案排序：名称带颜色标签时用剥离后的纯文本
      const leftName = plainIfDifferent(left.name) || left.name
      const rightName = plainIfDifferent(right.name) || right.name
      result = compareText(leftName, rightName)
    } else if (key === 'type') {
      result = compareText(left.type, right.type)
    } else if (key === 'objectives') {
      result = left.objectives.length - right.objectives.length
    } else {
      result = compareText(left.id, right.id)
    }
    // 主键相同时用 id 兜底，保证排序稳定、翻页不会串位
    return (result !== 0 ? result : compareText(left.id, right.id)) * direction
  })
}

const filtered = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  const rows = quests.value.filter(quest => {
    if (keyword) {
      // 前置 id 也进搜索：管理员常常是「谁依赖了这个任务」反过来找，列表里只看得到数量
      const prerequisites = (quest.prerequisites ?? []).join('\n')
      const haystack = `${quest.id}\n${quest.name}\n${plainIfDifferent(quest.name)}\n${prerequisites}`.toLowerCase()
      if (!haystack.includes(keyword)) {
        return false
      }
    }
    if (typeFilter.value !== 'ALL' && quest.type !== typeFilter.value) {
      return false
    }
    if (categoryFilter.value !== 'ALL' && quest.category !== categoryFilter.value) {
      return false
    }
    if (enabledFilter.value === 'ON' && !quest.enabled) {
      return false
    }
    if (enabledFilter.value === 'OFF' && quest.enabled) {
      return false
    }
    if (problemFilter.value === 'ONLY' && quest.problems.length === 0) {
      return false
    }
    if (problemFilter.value === 'NONE' && quest.problems.length > 0) {
      return false
    }
    return true
  })
  return sortRows(rows)
})

const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / Math.max(1, pageSize.value))))
const paged = computed(() => {
  const start = (Math.min(page.value, pageCount.value) - 1) * pageSize.value
  return filtered.value.slice(start, start + pageSize.value)
})

/** 筛选条件变化后回到第一页，否则会停在一个空白页上。 */
watch([search, typeFilter, categoryFilter, enabledFilter, problemFilter, pageSize], () => {
  page.value = 1
})

// 数据变化（刷新、删除）后页码可能越界，收敛回最后一页
watch(pageCount, count => {
  if (page.value > count) {
    page.value = count
  }
})

function toggleSort(key: string): void {
  if (sortKey.value === key) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key
    sortDirection.value = 'asc'
  }
}

function resetFilters(): void {
  search.value = ''
  typeFilter.value = 'ALL'
  categoryFilter.value = 'ALL'
  enabledFilter.value = 'ALL'
  problemFilter.value = 'ALL'
  page.value = 1
}

/* ---------------- 选中与批量操作 ---------------- */

const selected = ref<Set<string>>(new Set())

function toggleSelect(id: string, value: boolean): void {
  // Set 是浅层响应式的，必须整体替换才能触发视图更新
  const next = new Set(selected.value)
  if (value) {
    next.add(id)
  } else {
    next.delete(id)
  }
  selected.value = next
}

function clearSelection(): void {
  selected.value = new Set()
}

/** 校验问题的 hover 详情：鼠标停在标记上即可看到全部原因。 */
function problemTooltip(quest: Quest): string {
  return [`任务 ${quest.id} 存在 ${quest.problems.length} 个校验问题：`]
    .concat(quest.problems.map((problem, index) => `${index + 1}. ${problem}`))
    .join('\n')
}

function rowClass(quest: Quest): string {
  const classes: string[] = []
  if (quest.problems.length) {
    classes.push('row-bad')
  }
  if (selected.value.has(quest.id)) {
    classes.push('row-selected')
  }
  return classes.join(' ')
}

/**
 * 批量启用/禁用。
 *
 * <p>后端只有单个保存接口，因此逐条 POST；problems 会被后端忽略，
 * 保存后重新拉取列表，让校验结果由后端重算。
 */
async function bulkToggle(enabled: boolean): Promise<void> {
  const targets = quests.value.filter(quest => selected.value.has(quest.id))
  if (!targets.length || busy.value) {
    return
  }
  busy.value = true
  error.value = ''
  let ok = 0
  const failures: string[] = []
  for (const quest of targets) {
    try {
      await QuestApi.save({ ...quest, enabled, problems: [] })
      ok++
    } catch (e) {
      failures.push(`${quest.id}：${errorMessage(e)}`)
    }
  }
  busy.value = false
  await refresh()
  if (failures.length) {
    error.value = `${ok} 个任务已${enabled ? '启用' : '禁用'}，${failures.length} 个失败：${failures.join('；')}`
    toast.error(`批量操作完成，但有 ${failures.length} 个失败`)
  } else {
    toast.success(`已${enabled ? '启用' : '禁用'} ${ok} 个任务`)
  }
  clearSelection()
}

const bulkDeletePending = ref(false)

const bulkDeleteMessage = computed(() => {
  const ids = [...selected.value]
  const preview = ids.slice(0, 8).map(id => `· ${id}`).join('\n')
  const rest = ids.length > 8 ? `\n… 以及另外 ${ids.length - 8} 个任务` : ''
  return `确定删除选中的 ${ids.length} 个任务吗？该操作会立即写入任务文件，不可撤销。\n\n${preview}${rest}`
})

function askBulkDelete(): void {
  if (selected.value.size) {
    bulkDeletePending.value = true
  }
}

async function confirmBulkDelete(): Promise<void> {
  const ids = [...selected.value]
  bulkDeletePending.value = false
  if (!ids.length || busy.value) {
    return
  }
  busy.value = true
  error.value = ''
  let ok = 0
  const failures: string[] = []
  for (const id of ids) {
    try {
      await QuestApi.remove(id)
      ok++
    } catch (e) {
      failures.push(`${id}：${errorMessage(e)}`)
    }
  }
  busy.value = false
  clearSelection()
  await refresh()
  if (failures.length) {
    error.value = `${ok} 个任务已删除，${failures.length} 个失败：${failures.join('；')}`
    toast.error(`批量删除完成，但有 ${failures.length} 个失败`)
  } else {
    toast.success(`已删除 ${ok} 个任务`)
  }
}

/* ---------------- 单行操作 ---------------- */

const pendingDelete = ref<Quest | null>(null)
const deleting = ref(false)

const deleteMessage = computed(() =>
  pendingDelete.value
    ? `确定删除任务「${pendingDelete.value.id}」吗？该操作会立即写入任务文件，不可撤销。`
    : ''
)

function askDelete(quest: Quest): void {
  pendingDelete.value = quest
}

async function confirmDelete(): Promise<void> {
  const target = pendingDelete.value
  if (!target || deleting.value) {
    return
  }
  deleting.value = true
  try {
    const result = await QuestApi.remove(target.id)
    toast.success(result.ok ? `任务 ${result.id} 已删除` : `后端未找到任务 ${result.id}`)
    pendingDelete.value = null
    const next = new Set(selected.value)
    next.delete(target.id)
    selected.value = next
    await refresh()
  } catch (e) {
    const message = `删除失败：${errorMessage(e)}`
    error.value = message
    unauthorized.value = isUnauthorized(e)
    toast.error(message)
  } finally {
    deleting.value = false
  }
}

/** 单行启用/禁用：整条任务回存，避免后端做「部分更新」。 */
async function toggleEnabled(quest: Quest): Promise<void> {
  if (busy.value) {
    return
  }
  busy.value = true
  error.value = ''
  try {
    await QuestApi.save({ ...quest, enabled: !quest.enabled, problems: [] })
    toast.success(`任务 ${quest.id} 已${quest.enabled ? '禁用' : '启用'}`)
    await refresh()
  } catch (e) {
    const message = `切换启用状态失败：${errorMessage(e)}`
    error.value = message
    unauthorized.value = isUnauthorized(e)
    toast.error(message)
  } finally {
    busy.value = false
  }
}

/**
 * 复制任务：读原任务 → 改 id 为 <原 id>_copy → 保存 → 进入编辑器。
 *
 * <p>若 <原 id>_copy 已存在，继续追加 _2、_3… 直到不冲突，
 * 避免静默覆盖掉已有任务。
 */
async function duplicate(quest: Quest): Promise<void> {
  if (busy.value) {
    return
  }
  busy.value = true
  error.value = ''
  try {
    const existing = new Set(quests.value.map(item => item.id))
    let targetId = `${quest.id}_copy`
    let suffix = 1
    while (existing.has(targetId)) {
      suffix++
      targetId = `${quest.id}_copy${suffix}`
    }
    const source = await QuestApi.get(quest.id)
    const result = await QuestApi.save({ ...source, id: targetId, problems: [] })
    await refresh()
    toast.success(`已复制为 ${result.id}`)
    await openEditor(result.id)
  } catch (e) {
    const message = `复制失败：${errorMessage(e)}`
    error.value = message
    unauthorized.value = isUnauthorized(e)
    toast.error(message)
  } finally {
    busy.value = false
  }
}

/** 复制成功后跳进编辑器，省去管理员再去列表里找一次。 */
async function openEditor(id: string): Promise<void> {
  await router.push({ name: 'quest-edit', params: { id } })
}

/* ---------------- 导出 / 导入 ---------------- */

const fileInput = ref<HTMLInputElement | null>(null)
const pendingImport = ref<{ quests: Quest[]; fileName: string } | null>(null)
const importResult = ref<QuestImportResult | null>(null)
/** 导入方式：默认「合并」（保留现有任务），勾选后为「替换」（先清空）。 */
const replaceMode = ref(false)

const importMessage = computed(() => {
  if (!pendingImport.value) {
    return ''
  }
  return `文件「${pendingImport.value.fileName}」中解析出 ${pendingImport.value.quests.length} 个任务，`
    + '请确认导入方式。'
})

/** 导出：直接下载后端返回的原始 JSON（含 version 字段，便于日后兼容）。 */
async function exportQuests(): Promise<void> {
  if (exporting.value) {
    return
  }
  exporting.value = true
  error.value = ''
  try {
    const data = await QuestApi.exportAll()
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'quests.json'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    // 交给浏览器读完再释放，立即 revoke 在部分浏览器上会得到空文件
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    toast.success(`已导出 ${data.quests.length} 个任务到 quests.json`)
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

async function onFilePicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement | null
  const file = input?.files?.[0]
  // 清空 value，允许连续两次选择同一个文件
  if (input) {
    input.value = ''
  }
  if (!file) {
    return
  }
  error.value = ''
  try {
    const text = await file.text()
    const parsed: unknown = JSON.parse(text)
    const rawList = Array.isArray(parsed)
      ? parsed
      // 兼容导出文件 {version, quests:[...]} 与裸数组两种形态
      : (parsed && typeof parsed === 'object' && Array.isArray((parsed as { quests?: unknown }).quests))
        ? (parsed as { quests: unknown[] }).quests
        : null
    if (!rawList) {
      error.value = '导入失败：文件里没有任务数组（期望 {"version":1,"quests":[...]} 或直接是数组）'
      toast.error('导入失败：文件格式不正确')
      return
    }
    const list: Quest[] = []
    let dropped = 0
    rawList.forEach(item => {
      const quest = normalizeImportedQuest(item, '')
      if (quest) {
        list.push(quest)
      } else {
        dropped++
      }
    })
    if (!list.length) {
      error.value = '导入失败：文件里没有可用的任务记录（每条都必须有 id）'
      toast.error('导入失败：没有可用记录')
      return
    }
    replaceMode.value = false
    pendingImport.value = { quests: list, fileName: file.name }
    if (dropped) {
      toast.info(`已读取 ${list.length} 条记录，另有 ${dropped} 条因缺少 id 被忽略`)
    }
  } catch (e) {
    error.value = `导入失败：无法解析 JSON（${e instanceof Error ? e.message : String(e)}）`
    toast.error('导入失败：JSON 解析错误')
  }
}

function cancelImport(): void {
  pendingImport.value = null
  replaceMode.value = false
}

/**
 * 执行导入。
 *
 * <p>replace=true 会先删光现有任务，风险已经在上一步的对话框里用复选框
 * 明确告知，这里不再重复确认。
 */
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
    const result = await QuestApi.importQuests({ quests: payload.quests, replace })
    importResult.value = result
    await refresh()
    if (result.skipped.length) {
      toast.info(`已导入 ${result.imported} 个任务，跳过 ${result.skipped.length} 条`)
    } else {
      toast.success(`已${replace ? '替换' : '合并'}导入 ${result.imported} 个任务`)
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

/* ---------------- 数据加载 ---------------- */

onMounted(() => {
  void refresh()
})

async function refresh(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [list, stats] = await Promise.all([QuestApi.list(), StatsApi.get()])
    quests.value = list
    categories.value = stats.categories ?? []
    storage.value = stats.storage
    unauthorized.value = false
    // 清理已经不存在的选中项
    const ids = new Set(list.map(quest => quest.id))
    selected.value = new Set([...selected.value].filter(id => ids.has(id)))
  } catch (e) {
    error.value = `加载任务列表失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.hidden-file {
  display: none;
}
</style>
