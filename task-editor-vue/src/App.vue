<template>
  <div class="app-container">
    <aside class="sidebar">
      <h1>PlayerTaskX</h1>
      <nav>
        <router-link to="/editor">任务编辑器</router-link>
        <router-link to="/rewards">奖励库</router-link>
        <router-link to="/objectives">目标库</router-link>
        <router-link to="/actions">行为库</router-link>
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
