<!--
  「存为预设」对话框。

  只做一件事：给当前编辑中的目标/奖励起个名字。类型与属性直接取自已填好的卡片，
  因此这里不重复展示字段（避免出现「弹层里的值和背后的值不一致」的错觉），
  只提示一次将要保存的内容摘要。
-->
<template>
  <Teleport to="body">
    <div v-if="show" class="dialog-overlay" @click.self="emit('cancel')">
      <div class="dialog" role="dialog" aria-modal="true">
        <h3>存为预设</h3>
        <p class="hint">
          把这条{{ kind === 'objectives' ? '目标' : '奖励' }}的类型与当前字段值保存下来，
          以后在「添加{{ kind === 'objectives' ? '目标' : '奖励' }}」里一键套用。
        </p>

        <label class="field">
          <span class="field-label">预设名称 <em class="required">*</em></span>
          <input
            ref="inputRef"
            v-model="name"
            type="text"
            maxlength="60"
            placeholder="例如 挖 64 个石头"
            @keydown.enter.prevent="submit"
            @keydown.esc.prevent="emit('cancel')"
          />
        </label>

        <p class="hint preset-summary">
          类型：<code class="mono">{{ props.type || '（未选择）' }}</code> · {{ props.suggestedSummary }}
        </p>

        <div class="dialog-actions">
          <button class="btn" type="button" :disabled="busy" @click="emit('cancel')">取消</button>
          <button class="btn btn-primary" type="button" :disabled="busy || !name.trim()" @click="submit">
            {{ busy ? '保存中…' : '保存预设' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { PresetKind } from '../types'

const props = defineProps<{
  show: boolean
  kind: PresetKind
  /** 将要保存的类型 id */
  type: string
  /** 属性摘要，由父组件按 schema 生成 */
  suggestedSummary: string
  /** 建议名称：父组件用类型显示名 + 关键属性拼好，管理员通常直接回车即可 */
  suggestedName: string
  busy?: boolean
}>()

const emit = defineEmits<{
  save: [name: string]
  cancel: []
}>()

const name = ref('')
const inputRef = ref<HTMLInputElement | null>(null)

// 每次打开都用最新的建议名称重置并聚焦，省掉一次手动输入
watch(() => props.show, visible => {
  if (!visible) {
    return
  }
  name.value = props.suggestedName
  void nextTick(() => {
    inputRef.value?.focus()
    inputRef.value?.select()
  })
})

function submit(): void {
  const value = name.value.trim()
  if (value && !props.busy) {
    emit('save', value)
  }
}
</script>

<style scoped>
.field {
  margin: 0.75rem 0 0.5rem;
}

.preset-summary {
  margin-bottom: 1rem;
  word-break: break-word;
}
</style>
