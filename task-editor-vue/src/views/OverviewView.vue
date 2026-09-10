<!--
  概览仪表盘（首页）。

  取代原来的纯统计页：关键指标 + 目标/奖励类型清单 + 重载任务 + 令牌提示。
  类型清单直接来自 /api/schema，因此后端新增类型后这里会自动出现。
-->
<template>
  <section class="view">
    <header class="view-head">
      <div>
        <h2>概览</h2>
        <p class="hint">数据来自 /api/stats 与 /api/schema，反映插件当前运行状态。</p>
      </div>
      <div class="view-actions">
        <button class="btn" type="button" :disabled="loading || reloading" @click="refresh">
          {{ loading ? '加载中…' : '刷新' }}
        </button>
        <button class="btn btn-primary" type="button" :disabled="loading || reloading" @click="reloadQuests">
          {{ reloading ? '重载中…' : '重载任务' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="panel error-panel">{{ error }}</p>
    <UnauthorizedHint :show="unauthorized" />

    <div v-if="loading && !stats" class="panel">正在读取插件状态…</div>

    <template v-else>
      <!-- 关键指标 -->
      <div class="metric-grid">
        <div v-for="card in metrics" :key="card.label" class="metric-card">
          <span class="metric-value">{{ card.value }}</span>
          <span class="metric-label">{{ card.label }}</span>
        </div>
      </div>

      <p v-if="reloadMessage" class="panel" :class="{ 'error-panel': reloadFailed }">{{ reloadMessage }}</p>

      <div class="type-columns">
        <!-- 目标类型 -->
        <section class="card">
          <header class="card-head">
            <h3>目标类型（{{ objectiveTypes.length }}）</h3>
            <span class="hint">来自 /api/schema</span>
          </header>
          <p v-if="!objectiveTypes.length" class="guide-line">后端没有注册任何目标类型，请检查插件依赖。</p>
          <div v-else class="type-grid">
            <div v-for="item in objectiveTypes" :key="item.id" class="type-item">
              <strong>{{ item.displayName || item.id }}</strong>
              <span class="type-id mono">{{ item.id }}</span>
              <span class="hint">{{ fieldSummary(item) }}</span>
            </div>
          </div>
        </section>

        <!-- 奖励类型 -->
        <section class="card">
          <header class="card-head">
            <h3>奖励类型（{{ rewardTypes.length }}）</h3>
            <span class="hint">不可用的类型会在编辑器里被禁用</span>
          </header>
          <p v-if="!rewardTypes.length" class="guide-line">后端没有注册任何奖励类型。</p>
          <div v-else class="type-grid">
            <div
              v-for="item in rewardTypes"
              :key="item.id"
              class="type-item"
              :class="{ unavailable: item.available === false }"
            >
              <strong>{{ item.displayName || item.id }}</strong>
              <span class="type-id mono">{{ item.id }}</span>
              <span class="hint">{{ fieldSummary(item) }}</span>
              <span v-if="item.available === false" class="badge badge-warn">
                不可用：{{ item.unavailableReason || '原因未提供' }}
              </span>
            </div>
          </div>
        </section>
      </div>

      <section class="card">
        <h3>分类（{{ categories.length }}）</h3>
        <p v-if="!categories.length" class="hint">暂无分类。</p>
        <div v-else class="chips">
          <span v-for="category in categories" :key="category" class="chip">{{ category }}</span>
        </div>
      </section>

      <section class="card">
        <h3>使用提示</h3>
        <ul class="tips">
          <li>任务与语言文件的改动会立即写入插件数据目录，建议先用「导出 JSON」做一次备份。</li>
          <li>编辑完任务后如果游戏内没生效，点击上方「重载任务」让插件重新读取任务文件。</li>
          <li>若接口返回 401，请在左下角「访问令牌」填入 <code class="mono">config.yml</code> 里 <code class="mono">editor.token</code> 的值。</li>
          <li>玩家进度页为只读视图，不会修改任何玩家数据，可用于排查「某玩家为什么没进度」。</li>
        </ul>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import UnauthorizedHint from '../components/UnauthorizedHint.vue'
import { useToast } from '../composables/useToast'
import { SchemaApi, StatsApi, errorMessage, isUnauthorized } from '../services/api'
import type { Stats, TypeSchema } from '../types'

const toast = useToast()

const stats = ref<Stats | null>(null)
const objectiveTypes = ref<TypeSchema[]>([])
const rewardTypes = ref<TypeSchema[]>([])
const loading = ref(false)
const reloading = ref(false)
const error = ref('')
const unauthorized = ref(false)
const reloadMessage = ref('')
const reloadFailed = ref(false)

const categories = computed(() => stats.value?.categories ?? [])

const metrics = computed(() => [
  { label: '任务总数', value: stats.value?.quests ?? 0 },
  { label: '每日任务', value: stats.value?.dailyQuests ?? 0 },
  { label: '目标类型', value: stats.value?.objectives ?? objectiveTypes.value.length },
  { label: '奖励类型', value: stats.value?.rewards ?? rewardTypes.value.length },
  { label: '玩家数据', value: stats.value?.players ?? 0 },
  { label: '存储类型', value: stats.value?.storage || '—' }
])

/** 类型卡片上的字段摘要，例如 "目标方块 · 数量"。 */
function fieldSummary(schema: TypeSchema): string {
  const labels = (schema.fields ?? []).map(field => field.label || field.key)
  return labels.length ? labels.join(' · ') : '无可配置字段'
}

onMounted(() => {
  void refresh()
})

async function refresh(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [statsData, schema] = await Promise.all([StatsApi.get(), SchemaApi.get()])
    stats.value = statsData
    objectiveTypes.value = Object.values(schema.objectives ?? {})
    rewardTypes.value = Object.values(schema.rewards ?? {})
    unauthorized.value = false
  } catch (e) {
    error.value = `加载概览失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
  } finally {
    loading.value = false
  }
}

/** 重载任务：让插件重新读取任务文件与语言文件。 */
async function reloadQuests(): Promise<void> {
  reloading.value = true
  reloadMessage.value = ''
  reloadFailed.value = false
  try {
    const result = await StatsApi.reload()
    reloadMessage.value = `重载完成，插件当前载入 ${result.quests} 个任务。`
    toast.success(reloadMessage.value)
    await refresh()
  } catch (e) {
    reloadFailed.value = true
    unauthorized.value = isUnauthorized(e)
    reloadMessage.value = `重载失败：${errorMessage(e)}`
    toast.error(reloadMessage.value)
  } finally {
    reloading.value = false
  }
}
</script>

<style scoped>
.type-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.9rem;
  align-items: start;
}

.tips {
  margin: 0;
  padding-left: 1.1rem;
  color: var(--text-dim);
  font-size: 0.85rem;
  line-height: 1.7;
}

@media (max-width: 1080px) {
  .type-columns {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
