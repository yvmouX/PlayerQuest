<!--
  整体布局：左侧导航（概览 / 任务 / 预设 / 玩家）+ 右侧内容区 + 访问令牌输入。

  令牌输入放在侧栏底部常驻：任何接口返回 401 时，管理员都能立刻在这里补上。
-->
<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="brand">
        <strong>PlayerTaskX</strong>
        <span>任务编辑器</span>
      </div>

      <nav class="app-nav">
        <span class="nav-group">管理</span>
        <RouterLink to="/">概览</RouterLink>
        <RouterLink to="/quests">任务列表</RouterLink>
        <RouterLink to="/presets">预设管理</RouterLink>
        <RouterLink to="/players">玩家进度</RouterLink>
      </nav>

      <div class="sidebar-foot">
        <label class="token-field">
          <span>访问令牌</span>
          <input
            v-model="token"
            type="password"
            placeholder="留空即可"
            autocomplete="off"
            @change="applyToken"
          />
        </label>
        <small class="hint">对应 config.yml 的 editor.token，仅保存在本机浏览器。</small>
      </div>
    </aside>

    <main class="app-main">
      <RouterView />
    </main>

    <Toast ref="toastRef" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import Toast from './components/Toast.vue'
import { registerToast, useToast } from './composables/useToast'
import { getEditorToken, setEditorToken } from './services/api'
import { loadCatalog } from './utils/catalog'

const toast = useToast()
const toastRef = ref<InstanceType<typeof Toast> | null>(null)
const token = ref(getEditorToken())

onMounted(() => {
  registerToast(toastRef.value)
  // 启动即预加载素材目录（单例缓存）：选择器打开时不再等一次几百 KB 的请求。
  // 失败也无需提示——选择器仍可手打枚举名，且首次展开时会自动重试。
  void loadCatalog()
})

function applyToken(): void {
  setEditorToken(token.value.trim())
  toast.info(token.value.trim() ? '访问令牌已保存' : '已清空访问令牌')
}
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
}

.app-sidebar {
  width: 13.5rem;
  flex: 0 0 13.5rem;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border);
  background: var(--bg-elevated);
  padding: 1rem 0.75rem;
  gap: 1rem;
}

.brand {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding: 0 0.5rem;
}

.brand strong {
  font-size: 1rem;
  letter-spacing: 0.02em;
}

.brand span {
  color: var(--text-dim);
  font-size: 0.78rem;
}

.app-nav {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.nav-group {
  padding: 0.5rem 0.6rem 0.2rem;
  color: var(--text-dim);
  font-size: 0.7rem;
  letter-spacing: 0.06em;
  opacity: 0.7;
}

.app-nav :deep(a) {
  padding: 0.45rem 0.6rem;
  border-radius: var(--radius);
  color: var(--text-dim);
  text-decoration: none;
  font-size: 0.9rem;
}

.app-nav :deep(a:hover) {
  background: var(--bg-hover);
  color: var(--text);
}

.app-nav :deep(a.router-link-active) {
  background: var(--accent-soft);
  color: var(--accent);
}

.sidebar-foot {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 0 0.4rem;
}

.token-field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.78rem;
  color: var(--text-dim);
}

.app-main {
  flex: 1;
  overflow: auto;
}
</style>
