# 网页编辑器三大模块完善实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完善奖励库、玩家进度、统计管理三个前端页面

**Architecture:** 复用现有 api.ts 服务层，使用 Chart.js 做图表，组件化卡片/表格/对话框

**Tech Stack:** Vue 3, Chart.js, vue-chartjs, TypeScript

---

## 文件结构

```
src/
├── views/
│   ├── RewardLibraryView.vue      # 奖励库 - 卡片网格
│   ├── PlayerProgressView.vue      # 玩家进度 - 表格+分页
│   └── StatisticsView.vue         # 统计面板 - 图表+摘要卡
├── components/
│   ├── RewardCard.vue             # 奖励卡片
│   ├── RewardForm.vue             # 奖励表单模态框
│   ├── ConfirmDialog.vue          # 确认对话框
│   └── StatusBadge.vue            # 状态标签
└── services/
    └── api.ts                     # 已有，验证完整性
```

---

## 任务清单

### 任务 1: 添加 Chart.js 依赖

**Files:**
- Modify: `package.json`

- [ ] **Step 1: 添加 chart.js 和 vue-chartjs 依赖**

```json
{
  "chart.js": "^4.4.0",
  "vue-chartjs": "^5.3.0"
}
```

- [ ] **Step 2: 安装依赖**

```bash
npm install
```

- [ ] **Step 3: 提交**

```bash
git add package.json package-lock.json
git commit -m "feat(editor): add chart.js for statistics"
```

---

### 任务 2: 创建 ConfirmDialog 组件

**Files:**
- Create: `src/components/ConfirmDialog.vue`
- Modify: `src/views/RewardLibraryView.vue` (实现删除确认)

- [ ] **Step 1: 创建 ConfirmDialog.vue**

```vue
<template>
  <Teleport to="body">
    <div v-if="show" class="dialog-overlay" @click.self="$emit('cancel')">
      <div class="dialog">
        <h3>{{ title }}</h3>
        <p>{{ message }}</p>
        <div class="actions">
          <button @click="$emit('cancel')">取消</button>
          <button class="danger" @click="$emit('confirm')">确认</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
defineProps<{
  show: boolean
  title: string
  message: string
}>()
defineEmits<{
  confirm: []
  cancel: []
}>()
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.dialog {
  background: white;
  border-radius: 8px;
  padding: 1.5rem;
  max-width: 400px;
  width: 90%;
}
.dialog h3 { margin: 0 0 0.5rem; }
.dialog p { color: #666; margin: 0 0 1.5rem; }
.actions { display: flex; gap: 0.5rem; justify-content: flex-end; }
.actions button {
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}
.actions button.danger {
  background: #dc3545;
  color: white;
  border-color: #dc3545;
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add src/components/ConfirmDialog.vue
git commit -m "feat(editor): add ConfirmDialog component"
```

---

### 任务 3: 创建 StatusBadge 组件

**Files:**
- Create: `src/components/StatusBadge.vue`
- Modify: `src/views/PlayerProgressView.vue` (使用状态标签)

- [ ] **Step 1: 创建 StatusBadge.vue**

```vue
<template>
  <span class="badge" :class="status">{{ label }}</span>
</template>

<script setup lang="ts">
const props = defineProps<{
  status: string
}>()

const labelMap: Record<string, string> = {
  'not_started': '未开始',
  'in_progress': '进行中',
  'completed': '已完成',
  'claimed': '已领取'
}

const label = computed(() => labelMap[props.status] || props.status)
</script>

<style scoped>
.badge {
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 500;
}
.not_started { background: #e9ecef; color: #6c757d; }
.in_progress { background: #cfe2ff; color: #0d6efd; }
.completed { background: #d1e7dd; color: #198754; }
.claimed { background: #d1e7dd; color: #198754; }
</style>
```

- [ ] **Step 2: 提交**

```bash
git add src/components/StatusBadge.vue
git commit -m "feat(editor): add StatusBadge component"
```

---

### 任务 4: 实现 RewardLibraryView 奖励库页面

**Files:**
- Modify: `src/views/RewardLibraryView.vue`
- Create: `src/components/RewardCard.vue`
- Create: `src/components/RewardForm.vue`

- [ ] **Step 1: 创建 RewardCard.vue**

```vue
<template>
  <div class="card">
    <div class="card-icon">{{ iconMap[template.type] }}</div>
    <div class="card-body">
      <h3>{{ template.name }}</h3>
      <p class="type-label">{{ typeLabelMap[template.type] }} | {{ template.value }}</p>
    </div>
    <div class="card-actions">
      <button @click="$emit('edit')">编辑</button>
      <button class="danger" @click="$emit('delete')">删除</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { RewardTemplate } from '../types'

defineProps<{ template: RewardTemplate }>()
defineEmits<{ edit: []; delete: [] }>()

const iconMap: Record<string, string> = {
  item: '📦', xp: '⭐', money: '💰', command: '⚡'
}
const typeLabelMap: Record<string, string> = {
  item: '物品', xp: '经验', money: '金币', command: '命令'
}
</script>

<style scoped>
.card {
  background: white;
  border-radius: 8px;
  padding: 1rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  display: flex;
  align-items: center;
  gap: 1rem;
}
.card-icon { font-size: 2rem; }
.card-body { flex: 1; }
.card-body h3 { margin: 0; font-size: 1rem; }
.type-label { margin: 0.25rem 0 0; color: #666; font-size: 0.875rem; }
.card-actions { display: flex; gap: 0.5rem; }
.card-actions button {
  padding: 0.25rem 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}
.card-actions button.danger {
  background: #dc3545;
  color: white;
  border-color: #dc3545;
}
</style>
```

- [ ] **Step 2: 创建 RewardForm.vue**

```vue
<template>
  <Teleport to="body">
    <div v-if="show" class="modal-overlay" @click.self="$emit('cancel')">
      <div class="modal">
        <h3>{{ template ? '编辑模板' : '新建模板' }}</h3>
        <form @submit.prevent="handleSubmit">
          <div class="form-group">
            <label>名称</label>
            <input v-model="form.name" required />
          </div>
          <div class="form-group">
            <label>类型</label>
            <select v-model="form.type" required>
              <option value="item">物品</option>
              <option value="xp">经验</option>
              <option value="money">金币</option>
              <option value="command">命令</option>
            </select>
          </div>
          <div class="form-group">
            <label>值</label>
            <input v-model="form.value" required />
          </div>
          <div class="actions">
            <button type="button" @click="$emit('cancel')">取消</button>
            <button type="submit">保存</button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import type { RewardTemplate } from '../types'

const props = defineProps<{
  show: boolean
  template?: RewardTemplate
}>()

const emit = defineEmits<{
  save: [template: RewardTemplate]
  cancel: []
}>()

const form = reactive({
  name: props.template?.name || '',
  type: props.template?.type || 'item',
  value: props.template?.value || ''
})

watch(() => props.template, (t) => {
  form.name = t?.name || ''
  form.type = t?.type || 'item'
  form.value = t?.value || ''
})

const handleSubmit = () => {
  emit('save', {
    id: props.template?.id || '',
    name: form.name,
    type: form.type as RewardTemplate['type'],
    value: form.value
  } as RewardTemplate)
}
</script>

<style scoped>
.modal-overlay {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
}
.modal {
  background: white; border-radius: 8px;
  padding: 1.5rem; width: 400px;
}
.modal h3 { margin: 0 0 1rem; }
.form-group { margin-bottom: 1rem; }
.form-group label { display: block; margin-bottom: 0.25rem; font-weight: 500; }
.form-group input, .form-group select {
  width: 100%; padding: 0.5rem;
  border: 1px solid #ddd; border-radius: 4px;
  box-sizing: border-box;
}
.actions { display: flex; gap: 0.5rem; justify-content: flex-end; margin-top: 1.5rem; }
.actions button {
  padding: 0.5rem 1rem; border: 1px solid #ddd; border-radius: 4px; cursor: pointer;
}
.actions button:last-child { background: #198754; color: white; border-color: #198754; }
</style>
```

- [ ] **Step 3: 实现 RewardLibraryView.vue**

```vue
<template>
  <div class="page">
    <Header title="奖励库">
      <button class="primary" @click="openCreate">+ 新建模板</button>
    </Header>
    
    <div class="content">
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="templates.length === 0" class="empty">
        暂无奖励模板，点击上方按钮创建
      </div>
      <div v-else class="grid">
        <RewardCard
          v-for="t in templates"
          :key="t.id"
          :template="t"
          @edit="openEdit(t)"
          @delete="confirmDelete(t)"
        />
      </div>
    </div>

    <RewardForm
      :show="showForm"
      :template="editingTemplate"
      @save="handleSave"
      @cancel="closeForm"
    />
    
    <ConfirmDialog
      :show="showDeleteConfirm"
      title="删除确认"
      message="确定要删除这个奖励模板吗？"
      @confirm="handleDelete"
      @cancel="showDeleteConfirm = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Header from '../components/layout/Header.vue'
import RewardCard from '../components/RewardCard.vue'
import RewardForm from '../components/RewardForm.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { RewardService } from '../services/api'
import type { RewardTemplate } from '../types'

const templates = ref<RewardTemplate[]>([])
const loading = ref(true)
const showForm = ref(false)
const editingTemplate = ref<RewardTemplate>()
const showDeleteConfirm = ref(false)
const deletingTemplate = ref<RewardTemplate>()

const loadTemplates = async () => {
  loading.value = true
  const res = await RewardService.getTemplates()
  templates.value = res.data
  loading.value = false
}

const openCreate = () => { editingTemplate.value = undefined; showForm.value = true }
const openEdit = (t: RewardTemplate) => { editingTemplate.value = t; showForm.value = true }
const closeForm = () => { showForm.value = false }

const handleSave = async (template: RewardTemplate) => {
  await RewardService.saveTemplate(template)
  closeForm()
  loadTemplates()
}

const confirmDelete = (t: RewardTemplate) => { deletingTemplate.value = t; showDeleteConfirm.value = true }
const handleDelete = async () => {
  if (deletingTemplate.value) {
    await RewardService.deleteTemplate(deletingTemplate.value.id)
    showDeleteConfirm.value = false
    loadTemplates()
  }
}

onMounted(loadTemplates)
</script>

<style scoped>
.page { display: flex; flex-direction: column; height: 100%; }
.content { flex: 1; padding: 1.5rem; overflow: auto; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 1rem; }
.loading, .empty { text-align: center; padding: 3rem; color: #666; }
.primary { background: #198754; color: white; border: none; padding: 0.5rem 1rem; border-radius: 4px; cursor: pointer; }
</style>
```

- [ ] **Step 4: 提交**

```bash
git add src/components/RewardCard.vue src/components/RewardForm.vue src/views/RewardLibraryView.vue
git commit -m "feat(editor): implement RewardLibraryView with cards and forms"
```

---

### 任务 5: 实现 PlayerProgressView 玩家进度页面

**Files:**
- Modify: `src/views/PlayerProgressView.vue`

- [ ] **Step 1: 实现 PlayerProgressView.vue**

```vue
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
```

- [ ] **Step 2: 提交**

```bash
git add src/views/PlayerProgressView.vue
git commit -m "feat(editor): implement PlayerProgressView with table and filters"
```

---

### 任务 6: 实现 StatisticsView 统计页面

**Files:**
- Modify: `src/views/StatisticsView.vue`

- [ ] **Step 1: 实现 StatisticsView.vue**

```vue
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
```

- [ ] **Step 2: 提交**

```bash
git add src/views/StatisticsView.vue
git commit -m "feat(editor): implement StatisticsView with charts"
```

---

### 任务 7: 验证构建

**Files:**
- Modify: `task-editor-vue/`

- [ ] **Step 1: 运行构建验证**

```bash
cd task-editor-vue && npm run build
```

- [ ] **Step 2: 如有错误，修复后重新构建**

- [ ] **Step 3: 提交所有更改**

```bash
git add -A
git commit -m "feat(editor): complete reward library, player progress, and statistics views"
```

---

## 依赖汇总

| 依赖 | 版本 | 用途 |
|------|------|------|
| chart.js | ^4.4.0 | 图表库 |
| vue-chartjs | ^5.3.0 | Vue Chart.js 封装 |
