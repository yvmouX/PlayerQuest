<template>
  <div class="page">
    <Header title="玩家进度">
      <button class="secondary" @click="loadProgress">刷新</button>
    </Header>
    
    <div class="filters">
      <input v-model="search" placeholder="搜索玩家UUID..." @keyup.enter="loadProgress" />
      <select v-model="statusFilter" @change="loadProgress">
        <option value="">全部状态</option>
        <option value="not_started">未开始</option>
        <option value="in_progress">进行中</option>
        <option value="completed">已完成</option>
        <option value="claimed">已领取</option>
      </select>
    </div>

    <div class="content">
      <table v-if="progress.length > 0">
        <thead>
          <tr>
            <th>玩家</th>
            <th>任务ID</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in progress" :key="p.playerUuid + p.questId">
            <td>{{ p.playerUuid.substring(0, 8) }}...</td>
            <td>{{ p.questId }}</td>
            <td><StatusBadge :status="p.status" /></td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">暂无数据</div>
    </div>

    <div class="pagination">
      <span>共 {{ total }} 条</span>
      <button :disabled="offset === 0" @click="prevPage">上一页</button>
      <button :disabled="offset + limit >= total" @click="nextPage">下一页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Header from '../components/layout/Header.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { PlayerService } from '../services/api'
import type { PlayerProgress } from '../types'

const progress = ref<PlayerProgress[]>([])
const total = ref(0)
const limit = 20
const offset = ref(0)
const search = ref('')
const statusFilter = ref('')

const loadProgress = async () => {
  const res = await PlayerService.getProgress({
    search: search.value || undefined,
    status: statusFilter.value || undefined,
    limit,
    offset: offset.value
  })
  progress.value = res.data.data
  total.value = res.data.total
}

const prevPage = () => { offset.value -= limit; loadProgress() }
const nextPage = () => { offset.value += limit; loadProgress() }

onMounted(loadProgress)
</script>

<style scoped>
.page { display: flex; flex-direction: column; height: 100%; }
.filters { display: flex; gap: 1rem; padding: 1rem 1.5rem; background: #f8f9fa; }
.filters input, .filters select { padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px; }
.filters input { flex: 1; }
.content { flex: 1; overflow: auto; padding: 0 1.5rem; }
table { width: 100%; border-collapse: collapse; }
th, td { padding: 0.75rem; text-align: left; border-bottom: 1px solid #eee; }
th { font-weight: 600; color: #666; font-size: 0.875rem; }
.empty { text-align: center; padding: 3rem; color: #666; }
.pagination { display: flex; gap: 1rem; align-items: center; padding: 1rem 1.5rem; border-top: 1px solid #eee; }
.pagination button { padding: 0.5rem 1rem; border: 1px solid #ddd; border-radius: 4px; cursor: pointer; }
.pagination button:disabled { opacity: 0.5; cursor: not-allowed; }
.secondary { background: white; }
</style>
