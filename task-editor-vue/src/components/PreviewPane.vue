<!--
  实时预览：模拟玩家在游戏内看到的样子。

  <p>刻意<b>不做</b>像素级还原——真正的渲染在服务端（MiniMessage + 语言文件），
  前端只按 schema 拼出「大概长这样」的文本与进度条，帮助管理员确认目标数量、
  顺序和奖励内容是否合理。所有行文本由父组件基于 schema 计算后传入。
-->
<template>
  <aside class="preview">
    <header class="preview-head">
      <strong>玩家看到的样子</strong>
      <span class="hint">近似预览</span>
    </header>

    <div class="preview-body">
      <p class="preview-title">{{ title || '（未填写任务名称）' }}</p>
      <p v-if="subtitle" class="preview-subtitle">{{ subtitle }}</p>

      <p v-for="(line, index) in description" :key="`d${index}`" class="preview-desc">{{ line }}</p>

      <section class="preview-block">
        <span class="preview-label">目标（{{ objectives.length }}）</span>
        <p v-if="!objectives.length" class="hint">还没有目标，玩家接不到任何进度。</p>
        <ul v-else class="preview-list">
          <li v-for="item in objectives" :key="item.key">
            <div class="preview-line">
              <span class="preview-idx">{{ item.index }}</span>
              <span class="preview-name">{{ item.label }}</span>
              <span class="preview-count mono">{{ item.count }}</span>
            </div>
            <div v-if="item.percent !== null" class="progress">
              <div class="progress-fill" :style="{ width: `${item.percent}%` }"></div>
            </div>
            <p v-if="item.detail" class="preview-detail">{{ item.detail }}</p>
          </li>
        </ul>
      </section>

      <section class="preview-block">
        <span class="preview-label">奖励（{{ rewards.length }}）</span>
        <p v-if="!rewards.length" class="hint">没有奖励，玩家完成后拿不到任何东西。</p>
        <ul v-else class="preview-list">
          <li v-for="item in rewards" :key="item.key">
            <div class="preview-line">
              <span class="preview-idx">{{ item.index }}</span>
              <span class="preview-name">{{ item.label }}</span>
              <span v-if="item.count" class="preview-count mono">{{ item.count }}</span>
            </div>
            <p v-if="item.detail" class="preview-detail">{{ item.detail }}</p>
          </li>
        </ul>
      </section>

      <p class="hint">
        预览中的类型名与字段名来自后端 /api/schema，实际颜色与排版以游戏内为准。
      </p>
    </div>
  </aside>
</template>

<script setup lang="ts">
/**
 * 一行预览数据；父组件决定文案，这里只负责排版。
 * 用内联结构类型描述，父组件无需 import 即可结构化匹配。
 */
interface PreviewLine {
  key: string
  /** 序号，从 1 开始（对应 setobjective 命令里的目标序号） */
  index: number
  /** 主文案：类型显示名 */
  label: string
  /** 右侧计数，例如 "0 / 64" */
  count: string
  /** 第二行明细，例如 "目标方块=STONE" */
  detail: string
  /** 进度百分比；null 表示不画进度条 */
  percent: number | null
}

defineProps<{
  title: string
  subtitle: string
  description: string[]
  objectives: PreviewLine[]
  rewards: PreviewLine[]
}>()
</script>

<style scoped>
.preview {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-elevated);
  overflow: hidden;
}

.preview-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.6rem 0.8rem;
  border-bottom: 1px solid var(--border);
  background: #1f242c;
}

.preview-head strong {
  font-size: 0.9rem;
}

.preview-body {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.8rem;
  /* 模拟游戏内的聊天框观感 */
  background: #0f1114;
  font-size: 0.85rem;
}

.preview-title {
  color: #ffd479;
  font-weight: 600;
}

.preview-subtitle {
  color: var(--text-dim);
  font-size: 0.78rem;
}

.preview-desc {
  color: #a9b4c4;
}

.preview-block {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-top: 0.25rem;
}

.preview-label {
  color: var(--text-dim);
  font-size: 0.75rem;
  letter-spacing: 0.03em;
}

.preview-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.preview-line {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.preview-idx {
  min-width: 1.2rem;
  color: var(--text-dim);
  font-size: 0.75rem;
}

.preview-name {
  flex: 1;
  color: #d7e0ec;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-count {
  color: var(--ok);
}

.preview-detail {
  margin-left: 1.6rem;
  color: #6f7a8a;
  font-size: 0.75rem;
}
</style>
