<template>
  <div class="page">
    <Header title="统计">
      <select v-model="range" @change="loadStats">
        <option value="7d">近7天</option>
        <option value="30d">近30天</option>
      </select>
    </Header>
    
    <div class="content">
      <div class="summary-cards">
        <div class="card">
          <div class="label">总完成任务</div>
          <div class="value">{{ completionData.find(c => c.name === '已完成')?.value || 0 }}</div>
        </div>
        <div class="card">
          <div class="label">进行中</div>
          <div class="value">{{ completionData.find(c => c.name === '进行中')?.value || 0 }}</div>
        </div>
        <div class="card">
          <div class="label">未开始</div>
          <div class="value">{{ completionData.find(c => c.name === '未开始')?.value || 0 }}</div>
        </div>
      </div>

      <div class="charts">
        <div class="chart-container">
          <h3>完成状态分布</h3>
          <Doughnut v-if="chartData" :data="chartData" />
        </div>
        <div class="chart-container wide">
          <h3>活动趋势</h3>
          <Line v-if="lineData" :data="lineData" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Chart as ChartJS, ArcElement, Tooltip, Legend, CategoryScale, LinearScale, PointElement, LineElement } from 'chart.js'
import { Doughnut, Line } from 'vue-chartjs'
import Header from '../components/layout/Header.vue'
import { StatsService } from '../services/api'
import type { StatsCompletion, StatsActivity } from '../types'

ChartJS.register(ArcElement, Tooltip, Legend, CategoryScale, LinearScale, PointElement, LineElement)

const range = ref<'7d' | '30d'>('7d')
const completionData = ref<StatsCompletion[]>([])
const activityData = ref<StatsActivity[]>([])

const chartData = computed(() => ({
  labels: completionData.value.map(c => c.name),
  datasets: [{
    data: completionData.value.map(c => c.value),
    backgroundColor: ['#198754', '#0d6efd', '#6c757d']
  }]
}))

const lineData = computed(() => ({
  labels: activityData.value.map(a => a.day.slice(5)),
  datasets: [{
    label: '活跃玩家',
    data: activityData.value.map(a => a.users),
    borderColor: '#0d6efd',
    tension: 0.3
  }]
}))

const loadStats = async () => {
  const [completion, activity] = await Promise.all([
    StatsService.getCompletion(),
    StatsService.getActivity(range.value)
  ])
  completionData.value = completion.data
  activityData.value = activity.data
}

onMounted(loadStats)
</script>

<style scoped>
.page { display: flex; flex-direction: column; height: 100%; }
.content { flex: 1; overflow: auto; padding: 1.5rem; }
.summary-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; margin-bottom: 2rem; }
.summary-cards .card { background: white; border-radius: 8px; padding: 1.5rem; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.summary-cards .label { color: #666; font-size: 0.875rem; margin-bottom: 0.5rem; }
.summary-cards .value { font-size: 2rem; font-weight: 600; }
.charts { display: grid; grid-template-columns: 1fr 2fr; gap: 1rem; }
.chart-container { background: white; border-radius: 8px; padding: 1rem; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.chart-container h3 { margin: 0 0 1rem; font-size: 1rem; }
</style>
