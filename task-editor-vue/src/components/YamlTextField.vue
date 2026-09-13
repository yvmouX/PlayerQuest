<!--
  YAML 文本视图的公共控件：一个等宽文本框 + 解析状态 + 「从表单重新生成」。

  <h2>为什么单独成组件</h2>
  任务编辑器与预设编辑器都要这一块，而两者的表单结构完全不同。把「文本框 + 错误/警告 +
  重新生成按钮」抽出来，两边只负责各自的解析与写回（见 composables/useYamlMode.ts），
  既不会出现两套措辞，也不会出现两种排版。

  <h2>错误优先于警告</h2>
  解析失败时表单停在最后一次成功的状态，此时再列「未知字段」这类警告只会分散注意力，
  因此两者互斥显示。
-->
<template>
  <div class="yaml-field">
    <p v-if="hint" class="hint">{{ hint }}</p>

    <textarea
      class="yaml-text mono"
      spellcheck="false"
      :rows="rows"
      :value="modelValue"
      :aria-label="label"
      @input="onInput"
    ></textarea>

    <p v-if="error" class="panel error-panel">YAML 解析失败：{{ error }}</p>
    <ul v-else-if="warnings?.length" class="yaml-warnings">
      <li v-for="(warning, index) in warnings" :key="index">⚠ {{ warning }}</li>
    </ul>

    <div class="yaml-actions">
      <button class="btn btn-small" type="button" @click="emit('regenerate')">
        用当前表单内容重新生成
      </button>
      <span class="hint">
        改动会实时解析并写回表单，因此「保存」走的仍是上面那个按钮；
        数据库里存的是字段值，<b>注释与排版不会被保留</b>。
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
const emit = defineEmits<{
  'update:modelValue': [value: string]
  /** 用表单当前内容覆盖文本（丢弃手改） */
  regenerate: []
}>()

// 模板直接按名字取 props，因此不接一个未使用的 props 变量（noUnusedLocals 会报错）
defineProps<{
  /** 文本框内容 */
  modelValue: string
  /** 解析错误；非空时禁用保存由调用方决定 */
  error?: string
  /** 解析警告（未知字段等） */
  warnings?: string[]
  /** 顶部说明 */
  hint?: string
  /** 无障碍标签与自述用 */
  label: string
  /** 文本域可见行数 */
  rows?: number
}>()

function onInput(event: Event): void {
  const target = event.target as HTMLTextAreaElement | null
  emit('update:modelValue', target ? target.value : '')
}
</script>
