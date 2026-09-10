<!--
  玩家进度（纯只读页面）。

  左侧是「有任务记录」的玩家列表（GET /api/players），右侧是该玩家的
  任务记录与逐目标进度（GET /api/players/{uuid}）。
  本页面<b>不提供任何修改玩家数据的按钮</b>：它的用途是排查问题。

  任务记录里的「任务名 / 目标类型」都直接取自后端，非空目标名来自 schema，
  因此源码里没有针对具体目标类型的硬编码。
-->
<template>
  <section class="view">
    <header class="view-head">
      <div>
        <h2>玩家进度</h2>
        <p class="hint">只读视图：数据来自 /api/players，不会修改任何玩家数据。</p>
      </div>
      <div class="view-actions">
        <button class="btn" type="button" :disabled="loadingPlayers || loadingDetail" @click="reloadAll">
          {{ loadingPlayers ? '加载中…' : '刷新' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="panel error-panel">{{ error }}</p>
    <UnauthorizedHint :show="unauthorized" />

    <div class="split-layout">
      <!-- 左：玩家列表 -->
      <aside class="card side-list">
        <label class="filter-item search">
          <span>搜索玩家</span>
          <input
            v-model="search"
            type="search"
            placeholder="按名字或 UUID 搜索"
            autocomplete="off"
          />
        </label>

        <p class="hint">共 {{ players.length }} 名有任务记录的玩家，筛选后 {{ filteredPlayers.length }} 名。</p>

        <p v-if="loadingPlayers" class="hint">正在加载玩家列表…</p>
        <p v-else-if="!players.length" class="hint">还没有任何玩家任务记录。</p>
        <p v-else-if="!filteredPlayers.length" class="hint">没有匹配的玩家。</p>

        <button
          v-for="item in filteredPlayers"
          :key="item.uuid"
          class="list-item"
          :class="{ active: item.uuid === selectedUuid }"
          type="button"
          @click="selectPlayer(item.uuid)"
        >
          <span class="item-head">
            <span class="dot" :class="item.online ? 'dot-online' : 'dot-offline'" :title="item.online ? '在线' : '离线'"></span>
            <span class="name">{{ item.name || '(未知玩家)' }}</span>
            <span v-if="item.online" class="badge badge-green">在线</span>
            <span class="badge badge-gray">{{ item.quests }} 条</span>
          </span>
          <span class="item-sub mono">{{ item.uuid }}</span>
        </button>
      </aside>

      <!-- 右：玩家详情 -->
      <div class="player-detail">
        <div v-if="!selectedUuid" class="card empty-state">
          <strong>请选择左侧的一名玩家</strong>
          <p class="hint">选中后可以查看该玩家的任务币余额与每条任务的完成进度。</p>
        </div>

        <template v-else>
          <p v-if="loadingDetail" class="panel">正在加载玩家 {{ selectedUuid }} 的进度…</p>

          <template v-else-if="detail">
            <section class="card">
              <div class="player-head">
                <div class="player-id">
                  <strong>{{ detail.name || '(未知玩家)' }}</strong>
                  <span class="hint mono">{{ detail.uuid }}</span>
                </div>
                <div class="player-stats">
                  <span class="stat-item">任务币 <b>{{ formatAmount(detail.questCoin) }}</b></span>
                  <span class="stat-item">任务记录 <b>{{ detail.progress.length }}</b></span>
                </div>
              </div>
            </section>

            <div v-if="!detail.progress.length" class="card empty-state">
              <strong>该玩家还没有任务记录</strong>
              <p class="hint">玩家接取任务后，这里会显示每条任务的进度。</p>
            </div>

            <template v-else>
              <p class="hint">展开某一行可以查看各目标的「当前 / 需求」，用于定位卡住的目标。</p>

              <DataTable
                :columns="progressColumns"
                :rows="detail.progress"
                :row-key="row => row.questId"
              >
                <template #expand="{ row }">
                  <button
                    class="btn btn-small"
                    type="button"
                    :disabled="!row.objectives.length"
                    :title="row.objectives.length ? '展开 / 收起目标进度' : '该任务已不存在或无目标'"
                    @click="toggleRow(row.questId)"
                  >
                    {{ expanded.has(row.questId) ? '收起' : `目标 ${row.objectives.length}` }}
                  </button>
                </template>

                <template #questName="{ row }">
                  <div class="cell-name">
                    <span class="raw" :title="row.questName || row.questId">{{ row.questName || '（任务已不存在）' }}</span>
                    <span class="hint mono">{{ row.questId }}</span>
                  </div>
                </template>

                <template #type="{ row }">
                  <span class="badge" :class="row.type === 'DAILY' ? 'badge-blue' : 'badge-gray'">
                    {{ row.type === 'DAILY' ? '每日' : '普通' }}
                  </span>
                </template>

                <template #status="{ row }">
                  <span :class="['badge', statusClass(row.status)]" :title="row.status">
                    {{ statusText(row.status) }}
                  </span>
                </template>

                <template #percent="{ row }">
                  <div class="progress-line">
                    <div class="progress">
                      <div
                        class="progress-fill"
                        :class="{ done: clampPercent(row.percent) >= 100 }"
                        :style="{ width: `${clampPercent(row.percent)}%` }"
                      ></div>
                    </div>
                    <span class="progress-text">{{ clampPercent(row.percent) }}%</span>
                  </div>
                </template>

                <template #assignedAt="{ row }">
                  <span class="mono">{{ formatTime(row.assignedAt) }}</span>
                </template>

                <template #expiresAt="{ row }">
                  <span class="mono" :class="{ 'bad-text': isExpired(row.expiresAt) }">
                    {{ expiresText(row.expiresAt) }}
                  </span>
                </template>

                <!-- 展开行：逐目标进度 -->
                <template #objectives="{ row }">
                  <ul v-if="expanded.has(row.questId)" class="objective-list">
                    <li v-for="slot in row.objectives" :key="slot.index" class="objective-item">
                      <span class="objective-index">#{{ slot.index + 1 }}</span>
                      <span class="objective-name">{{ objectiveLabel(slot.type) }}</span>
                      <div class="progress">
                        <div
                          class="progress-fill"
                          :class="{ done: slotProgress(slot) >= 100 }"
                          :style="{ width: `${slotProgress(slot)}%` }"
                        ></div>
                      </div>
                      <span class="objective-count mono">{{ slot.current }} / {{ slot.required }}</span>
                    </li>
                  </ul>
                  <span v-else class="hint">—</span>
                </template>
              </DataTable>
            </template>
          </template>
        </template>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import DataTable from '../components/DataTable.vue'
import UnauthorizedHint from '../components/UnauthorizedHint.vue'
import { PlayerApi, SchemaApi, errorMessage, isUnauthorized } from '../services/api'
import type {
  PlayerDetail,
  PlayerObjectiveProgress,
  PlayerSummary,
  TableColumn,
  TypeSchema
} from '../types'
import { clampPercent, formatAmount, formatTime, isExpired } from '../utils/text'

/** 路由 /players/:uuid 传入的玩家；从侧栏进入时没有这个参数。 */
const props = defineProps<{ uuid?: string }>()

const players = ref<PlayerSummary[]>([])
const detail = ref<PlayerDetail | null>(null)
const objectiveSchemas = ref<Record<string, TypeSchema>>({})
const search = ref('')
const selectedUuid = ref('')
const loadingPlayers = ref(false)
const loadingDetail = ref(false)
const error = ref('')
const unauthorized = ref(false)

/** 已展开目标进度的任务 id（Set 浅层响应式，整体替换触发更新）。 */
const expanded = ref<Set<string>>(new Set())
/** 展开行所在的表格列：把全部内容塞进「任务」列会撑破布局。 */
const progressColumns: TableColumn[] = [
  { key: 'expand', label: '', width: '6.5rem' },
  { key: 'questName', label: '任务', width: '14rem' },
  { key: 'type', label: '类型', width: '5.5rem' },
  { key: 'status', label: '状态', width: '6.5rem' },
  { key: 'percent', label: '进度', width: '10rem' },
  { key: 'assignedAt', label: '接取时间', width: '9rem' },
  { key: 'expiresAt', label: '过期时间', width: '9rem' },
  { key: 'objectives', label: '各目标进度', title: '点击左侧「目标 N」按钮展开' }
]

const filteredPlayers = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) {
    return players.value
  }
  return players.value.filter(item =>
    item.uuid.toLowerCase().includes(keyword) || (item.name ?? '').toLowerCase().includes(keyword)
  )
})

onMounted(() => {
  void loadSchemas()
  void loadPlayers()
})

// 路由参数变化（例如从侧栏带 uuid 进入）时切换选中玩家
watch(() => props.uuid, uuid => {
  if (uuid && uuid !== selectedUuid.value) {
    selectedUuid.value = uuid
    expanded.value = new Set()
    void loadDetail(uuid)
  }
}, { immediate: true })

async function loadSchemas(): Promise<void> {
  try {
    const schema = await SchemaApi.get()
    // 目标类型的显示名只用于「更友好的标签」，取不到就退回类型 id
    objectiveSchemas.value = schema.objectives ?? {}
  } catch {
    // 只影响标签观感，不阻塞页面
  }
}

async function loadPlayers(): Promise<void> {
  loadingPlayers.value = true
  error.value = ''
  try {
    const list = await PlayerApi.list()
    players.value = list
    unauthorized.value = false
    // 没有指定 uuid 时默认选中第一名玩家，页面不至于空着
    if (!selectedUuid.value && list.length) {
      await selectPlayer(list[0].uuid)
    } else if (selectedUuid.value && !list.some(item => item.uuid === selectedUuid.value)) {
      // 选中的玩家已不在列表里（记录被清理），退回第一名或空态
      selectedUuid.value = ''
      detail.value = null
      if (list.length) {
        await selectPlayer(list[0].uuid)
      }
    }
  } catch (e) {
    error.value = `加载玩家列表失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
  } finally {
    loadingPlayers.value = false
  }
}

async function selectPlayer(uuid: string): Promise<void> {
  if (!uuid) {
    return
  }
  selectedUuid.value = uuid
  expanded.value = new Set()
  await loadDetail(uuid)
}

async function loadDetail(uuid: string): Promise<void> {
  loadingDetail.value = true
  error.value = ''
  try {
    const data = await PlayerApi.get(uuid)
    detail.value = data
    unauthorized.value = false
  } catch (e) {
    detail.value = null
    error.value = `加载玩家 ${uuid} 的进度失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
  } finally {
    loadingDetail.value = false
  }
}

async function reloadAll(): Promise<void> {
  await loadPlayers()
  if (selectedUuid.value) {
    await loadDetail(selectedUuid.value)
  }
}

function toggleRow(questId: string): void {
  const next = new Set(expanded.value)
  if (next.has(questId)) {
    next.delete(questId)
  } else {
    next.add(questId)
  }
  expanded.value = next
}

/* ---------------- 展示辅助 ---------------- */

/** 后端状态枚举名 → 中文文案；未知状态原样显示，避免被误读成「已完成」。 */
function statusText(status: string): string {
  switch (status) {
    case 'ACTIVE':
    case 'IN_PROGRESS':
    case 'ONGOING':
      return '进行中'
    case 'COMPLETED':
      return '已完成'
    case 'CLAIMED':
      return '已领取'
    case 'ABANDONED':
      return '已放弃'
    case 'EXPIRED':
      return '已过期'
    default:
      return status || '未知'
  }
}

/** 状态对应的颜色样式。 */
function statusClass(status: string): string {
  switch (status) {
    case 'ACTIVE':
    case 'IN_PROGRESS':
    case 'ONGOING':
      return 'badge-blue'
    case 'COMPLETED':
      return 'badge-green'
    case 'CLAIMED':
      return 'badge-purple'
    case 'ABANDONED':
    case 'EXPIRED':
      return 'badge-gray'
    default:
      return 'badge-gray'
  }
}

/** 目标类型显示名：优先用 schema 的显示名（含类型 id），否则退回类型 id。 */
function objectiveLabel(type: string): string {
  const schema = objectiveSchemas.value[type]
  const name = schema?.displayName
  return name && name !== type ? `${name}（${type}）` : type
}

function slotProgress(slot: PlayerObjectiveProgress): number {
  const required = Number(slot.required)
  if (!Number.isFinite(required) || required <= 0) {
    return 0
  }
  return clampPercent((Number(slot.current) / required) * 100)
}

function expiresText(value: number | null): string {
  if (value === null || value === undefined || value === 0) {
    return '不限期'
  }
  return `${formatTime(value)}${isExpired(value) ? '（已过期）' : ''}`
}
</script>

<style scoped>
.player-detail {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-width: 0;
}

.player-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}

.player-id {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.player-id strong {
  font-size: 1rem;
}

.player-stats {
  display: flex;
  gap: 1.25rem;
  flex-wrap: wrap;
}

.stat-item {
  display: flex;
  align-items: baseline;
  gap: 0.3rem;
  color: var(--text-dim);
  font-size: 0.85rem;
}

.stat-item b {
  color: var(--text);
  font-size: 1.05rem;
}

.objective-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  min-width: 18rem;
}

.objective-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.objective-index {
  min-width: 1.5rem;
  color: var(--text-dim);
  font-size: 0.75rem;
}

.objective-name {
  min-width: 8rem;
  font-size: 0.82rem;
}

.objective-item .progress {
  flex: 1;
  min-width: 4rem;
}

.objective-count {
  min-width: 5rem;
  text-align: right;
  color: var(--text-dim);
}
</style>
