<!-- 统计信息与任务重载。 -->
<template>
  <section class="view">
    <header class="view-head">
      <div>
        <h2>运行统计</h2>
        <p class="hint">
          数据来自 /api/stats。任务与类型的详细清单请见「概览」页。
        </p>
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

    <div class="stat-grid">
      <div v-for="card in cards" :key="card.label" class="card stat-card">
        <span class="stat-value">{{ card.value }}</span>
        <span class="stat-label">{{ card.label }}</span>
      </div>
    </div>

    <section class="card">
      <h3>存储</h3>
      <p class="mono">{{ stats?.storage || '—' }}</p>
    </section>

    <section class="card">
      <h3>任务分类（{{ categories.length }}）</h3>
      <p v-if="!categories.length" class="hint">暂无分类。</p>
      <div v-else class="chips">
        <span v-for="category in categories" :key="category" class="chip">{{ category }}</span>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import UnauthorizedHint from '../components/UnauthorizedHint.vue'
import { useToast } from '../composables/useToast'
import { StatsApi, errorMessage, isUnauthorized } from '../services/api'
import type { Stats } from '../types'

const toast = useToast()

const stats = ref<Stats | null>(null)
const loading = ref(false)
const reloading = ref(false)
const error = ref('')
const unauthorized = ref(false)

const categories = computed(() => stats.value?.categories ?? [])

const cards = computed(() => [
  { label: '任务总数', value: stats.value?.quests ?? 0 },
  { label: '每日任务', value: stats.value?.dailyQuests ?? 0 },
  { label: '目标类型', value: stats.value?.objectives ?? 0 },
  { label: '奖励类型', value: stats.value?.rewards ?? 0 },
  { label: '玩家数据', value: stats.value?.players ?? 0 },
  { label: '存储类型', value: stats.value?.storage || '—' }
])

onMounted(() => {
  void refresh()
})

async function refresh(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    stats.value = await StatsApi.get()
    unauthorized.value = false
  } catch (e) {
    error.value = `加载统计失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
  } finally {
    loading.value = false
  }
}

async function reloadQuests(): Promise<void> {
  reloading.value = true
  error.value = ''
  try {
    const result = await StatsApi.reload()
    toast.success(`已重载 ${result.quests} 个任务`)
    await refresh()
  } catch (e) {
    error.value = `重载失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
    toast.error(error.value)
  } finally {
    reloading.value = false
  }
}
</script>
