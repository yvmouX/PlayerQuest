# Task Editor Frontend Vue Project Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 Vue 3 + Vite + Vue Flow 前端项目，替代现有 React 实现

**Architecture:** 单页应用，Vue Router 路由，Axios 请求后端 API，Vue Flow 可视化编辑器

**Tech Stack:** Vue 3, Vite, Vue Router, Axios, Vue Flow, TailwindCSS (可选)

---

## 文件结构

```
task-editor-vue/
├── src/
│   ├── main.ts
│   ├── App.vue
│   ├── router/
│   │   └── index.ts
│   ├── views/
│   │   ├── EditorView.vue      # 可视化任务编辑器
│   │   ├── RewardLibraryView.vue
│   │   ├── PlayerProgressView.vue
│   │   └── StatisticsView.vue
│   ├── components/
│   │   ├── layout/
│   │   │   ├── Sidebar.vue
│   │   │   └── Header.vue
│   │   └── editor/
│   │       ├── QuestCanvas.vue   # Vue Flow 画布
│   │       └── QuestNode.vue     # 自定义节点
│   ├── services/
│   │   └── api.ts
│   ├── types/
│   │   └── index.ts
│   └── assets/
│       └── main.css
├── index.html
├── package.json
├── vite.config.ts
├── tsconfig.json
└── tailwind.config.js (可选)
```

---

## Task 1: 初始化 Vue 3 + Vite 项目

**Files:**
- Create: `task-editor-vue/` 目录及基础配置文件

- [ ] **Step 1: 创建 package.json**

```json
{
  "name": "task-editor-vue",
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.2.0",
    "@vue-flow/core": "^1.33.0",
    "axios": "^1.6.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.0.0",
    "typescript": "^5.3.0",
    "vite": "^5.0.0",
    "vue-tsc": "^1.8.0"
  }
}
```

- [ ] **Step 2: 创建 vite.config.ts**

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:22222',
        changeOrigin: true
      }
    }
  }
})
```

- [ ] **Step 3: 创建 tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "preserve",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true
  },
  "include": ["src/**/*.ts", "src/**/*.tsx", "src/**/*.vue"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

- [ ] **Step 4: 创建 tsconfig.node.json**

```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 5: 创建 index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>PlayerTaskX - Task Editor</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

- [ ] **Step 6: Commit**

```bash
git add task-editor-vue/
git commit -m "feat(task-editor-vue): 初始化Vue3+Vite项目基础配置"
```

---

## Task 2: 创建入口文件和路由

**Files:**
- Create: `task-editor-vue/src/main.ts`
- Create: `task-editor-vue/src/App.vue`
- Create: `task-editor-vue/src/router/index.ts`

- [ ] **Step 1: 创建 main.ts**

```typescript
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'

const app = createApp(App)
app.use(router)
app.mount('#app')
```

- [ ] **Step 2: 创建 router/index.ts**

```typescript
import { createRouter, createWebHistory } from 'vue-router'
import EditorView from '../views/EditorView.vue'
import RewardLibraryView from '../views/RewardLibraryView.vue'
import PlayerProgressView from '../views/PlayerProgressView.vue'
import StatisticsView from '../views/StatisticsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/editor' },
    { path: '/editor', name: 'editor', component: EditorView },
    { path: '/rewards', name: 'rewards', component: RewardLibraryView },
    { path: '/players', name: 'players', component: PlayerProgressView },
    { path: '/stats', name: 'stats', component: StatisticsView }
  ]
})

export default router
```

- [ ] **Step 3: 创建 App.vue**

```vue
<template>
  <div class="app-container">
    <aside class="sidebar">
      <h1>PlayerTaskX</h1>
      <nav>
        <router-link to="/editor">任务编辑器</router-link>
        <router-link to="/rewards">奖励库</router-link>
        <router-link to="/players">玩家进度</router-link>
        <router-link to="/stats">统计</router-link>
      </nav>
    </aside>
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-container {
  display: flex;
  height: 100vh;
}
.sidebar {
  width: 200px;
  background: #1a1a1a;
  color: white;
  padding: 1rem;
}
.main-content {
  flex: 1;
  overflow: auto;
}
nav a {
  display: block;
  color: #aaa;
  padding: 0.5rem;
  text-decoration: none;
}
nav a.router-link-active {
  color: white;
  background: #333;
}
</style>
```

- [ ] **Step 4: 创建基础 CSS**

```css
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
```

- [ ] **Step 5: Commit**

```bash
git add task-editor-vue/src/
git commit -m "feat(task-editor-vue): 创建入口文件和路由"
```

---

## Task 3: 创建 API 服务层

**Files:**
- Create: `task-editor-vue/src/types/index.ts`
- Create: `task-editor-vue/src/services/api.ts`

- [ ] **Step 1: 创建 types/index.ts**

```typescript
export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}

export interface Quest {
  id: string
  name: Record<string, string>
  description: Record<string, string>
  type: 'single' | 'multi' | 'series'
  objectives: QuestObjective[]
  rewards: QuestReward[]
  createdAt: number
  updatedAt: number
}

export interface QuestObjective {
  id: string
  type: string
  target: string
  count: number
  finished: boolean
}

export interface QuestReward {
  id: string
  type: 'item' | 'xp' | 'money' | 'command'
  value: string | number
  meta?: any
}

export interface RewardTemplate {
  id: string
  name: string
  type: 'item' | 'xp' | 'money' | 'command'
  value: string | number
  meta?: any
}

export interface PlayerProgress {
  playerUuid: string
  questId: string
  status: 'not_started' | 'in_progress' | 'completed' | 'claimed'
  progress: Record<string, number>
}

export interface StatsCompletion {
  name: string
  value: number
}

export interface StatsActivity {
  day: string
  users: number
}
```

- [ ] **Step 2: 创建 services/api.ts**

```typescript
import axios from 'axios'
import type { 
  Quest, 
  RewardTemplate, 
  PlayerProgress, 
  StatsCompletion, 
  StatsActivity,
  ApiResponse 
} from '../types'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

api.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export const QuestService = {
  getAll: () => api.get<ApiResponse<Quest[]>>('/quests'),
  getById: (id: string) => api.get<ApiResponse<Quest>>(`/quests/${id}`),
  create: (quest: Quest) => api.post<ApiResponse<Quest>>('/quests', quest),
  update: (id: string, quest: Quest) => api.put<ApiResponse<Quest>>(`/quests/${id}`, quest),
  delete: (id: string) => api.delete(`/quests/${id}`)
}

export const RewardService = {
  getTemplates: () => api.get<ApiResponse<RewardTemplate[]>>('/rewards/templates'),
  saveTemplate: (template: RewardTemplate) => api.post<ApiResponse<RewardTemplate>>('/rewards/templates', template),
  deleteTemplate: (id: string) => api.delete(`/rewards/templates/${id}`)
}

export const PlayerService = {
  getProgress: (params?: { search?: string; status?: string; limit?: number; offset?: number }) =>
    api.get<ApiResponse<{ total: number; data: PlayerProgress[] }>>('/players/progress', { params })
}

export const StatsService = {
  getCompletion: () => api.get<ApiResponse<StatsCompletion[]>>('/stats/completion'),
  getActivity: (range: '7d' | '30d' = '7d') =>
    api.get<ApiResponse<StatsActivity[]>>('/stats/activity', { params: { range } })
}
```

- [ ] **Step 3: Commit**

```bash
git add task-editor-vue/src/types/ task-editor-vue/src/services/
git commit -m "feat(task-editor-vue): 创建类型定义和API服务层"
```

---

## Task 4: 创建 Layout 组件

**Files:**
- Create: `task-editor-vue/src/components/layout/Sidebar.vue`
- Create: `task-editor-vue/src/components/layout/Header.vue`

- [ ] **Step 1: 创建 Sidebar.vue**

```vue
<template>
  <aside class="sidebar">
    <div class="logo">
      <h1>PlayerTaskX</h1>
    </div>
    <nav class="nav-menu">
      <router-link to="/editor" class="nav-item">
        <span class="icon">📋</span>
        <span>任务编辑器</span>
      </router-link>
      <router-link to="/rewards" class="nav-item">
        <span class="icon">🎁</span>
        <span>奖励库</span>
      </router-link>
      <router-link to="/players" class="nav-item">
        <span class="icon">👥</span>
        <span>玩家进度</span>
      </router-link>
      <router-link to="/stats" class="nav-item">
        <span class="icon">📊</span>
        <span>统计</span>
      </router-link>
    </nav>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 220px;
  background: #1e1e1e;
  color: white;
  display: flex;
  flex-direction: column;
}
.logo {
  padding: 1.5rem 1rem;
  border-bottom: 1px solid #333;
}
.logo h1 {
  font-size: 1.2rem;
  font-weight: 600;
}
.nav-menu {
  padding: 1rem 0;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  color: #a0a0a0;
  text-decoration: none;
  transition: all 0.2s;
}
.nav-item:hover {
  background: #2a2a2a;
  color: white;
}
.nav-item.router-link-active {
  background: #3b82f6;
  color: white;
}
.icon {
  font-size: 1.2rem;
}
</style>
```

- [ ] **Step 2: 创建 Header.vue**

```vue
<template>
  <header class="header">
    <h2 class="page-title">{{ title }}</h2>
    <div class="actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
defineProps<{
  title: string
}>()
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  background: white;
  border-bottom: 1px solid #e5e5e5;
}
.page-title {
  font-size: 1.25rem;
  font-weight: 600;
}
.actions {
  display: flex;
  gap: 0.5rem;
}
</style>
```

- [ ] **Step 3: Commit**

```bash
git add task-editor-vue/src/components/layout/
git commit -m "feat(task-editor-vue): 创建Layout组件"
```

---

## Task 5: 创建占位 View 页面

**Files:**
- Create: `task-editor-vue/src/views/EditorView.vue`
- Create: `task-editor-vue/src/views/RewardLibraryView.vue`
- Create: `task-editor-vue/src/views/PlayerProgressView.vue`
- Create: `task-editor-vue/src/views/StatisticsView.vue`

- [ ] **Step 1: 创建 EditorView.vue**

```vue
<template>
  <div class="editor-view">
    <Header title="任务编辑器">
      <template #actions>
        <button @click="saveQuest">保存</button>
        <button @click="addQuest">新建任务</button>
      </template>
    </Header>
    <div class="canvas-container">
      <VueFlow :nodes="nodes" :edges="edges" @node-click="onNodeClick" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { VueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import Header from '../components/layout/Header.vue'

const nodes = ref([])
const edges = ref([])

const saveQuest = () => { /* TODO */ }
const addQuest = () => { /* TODO */ }
const onNodeClick = (event) => { /* TODO */ }
</script>

<style scoped>
.editor-view {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.canvas-container {
  flex: 1;
}
</style>
```

- [ ] **Step 2: 创建其他 View 占位**

```vue
<!-- RewardLibraryView.vue -->
<template>
  <div>
    <Header title="奖励库" />
    <p>奖励库管理 - 待实现</p>
  </div>
</template>

<!-- PlayerProgressView.vue -->
<template>
  <div>
    <Header title="玩家进度" />
    <p>玩家进度 - 待实现</p>
  </div>
</template>

<!-- StatisticsView.vue -->
<template>
  <div>
    <Header title="统计" />
    <p>统计面板 - 待实现</p>
  </div>
</template>
```

- [ ] **Step 3: Commit**

```bash
git add task-editor-vue/src/views/
git commit -m "feat(task-editor-vue): 创建View页面占位符"
```

---

## Task 6: 安装依赖验证项目可运行

- [ ] **Step 1: 安装依赖**

```bash
cd task-editor-vue && npm install
```

- [ ] **Step 2: 启动开发服务器**

```bash
cd task-editor-vue && npm run dev
```

- [ ] **Step 3: 验证 http://localhost:5173 可访问**

---

## Task 7: 更新 App.vue 使用 Layout

- [ ] **Step 1: 修改 App.vue**

```vue
<template>
  <div class="app-layout">
    <Sidebar />
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import Sidebar from './components/layout/Sidebar.vue'
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
}
.main-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add task-editor-vue/src/App.vue
git commit -m "feat(task-editor-vue): App.vue使用Sidebar布局"
```
