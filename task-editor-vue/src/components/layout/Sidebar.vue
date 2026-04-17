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
