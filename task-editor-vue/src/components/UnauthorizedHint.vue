<!--
  401 引导条。

  <p>访问令牌只在 /api/* 上校验，令牌不对时后端返回 401，前端如果只提示
  「请求失败」管理员会一头雾水，因此这里给出一条明确的修复路径。
-->
<template>
  <div v-if="show" class="panel token-hint">
    <strong>访问被拒绝（HTTP 401）</strong>
    <p class="hint">
      当前请求未通过编辑器令牌校验。请打开插件目录下的 <code>config.yml</code>，
      把 <code>editor.token</code> 的值填入左下角的「访问令牌」输入框，
      然后重新加载本页。
    </p>
    <p class="hint">若 <code>editor.token</code> 为空，表示未启用令牌校验，请检查是否填了多余的字符。</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  /** 是否显示；通常传 isUnauthorized(error) 的结果 */
  show: boolean
}>()
</script>

<style scoped>
.token-hint {
  border-color: var(--danger);
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.token-hint strong {
  color: #ff9a92;
  font-size: 0.9rem;
}

.token-hint code {
  padding: 0.05rem 0.3rem;
  border-radius: 4px;
  background: var(--bg-input);
  color: var(--accent);
  font-family: 'Cascadia Mono', Consolas, 'Microsoft YaHei', monospace;
  font-size: 0.8rem;
}
</style>
