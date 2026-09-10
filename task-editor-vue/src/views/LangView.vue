<!-- 语言文件编辑：左侧语言列表，右侧 YAML 文本域。 -->
<template>
  <section class="view">
    <header class="view-head">
      <div>
        <h2>语言文件</h2>
        <p class="hint">对应服务端 lang/&lt;语言&gt;.yml，保存时后端会校验 YAML 是否合法。</p>
      </div>
      <div class="view-actions">
        <button class="btn" type="button" :disabled="loading || saving" @click="reload">
          {{ loading ? '加载中…' : '重新加载' }}
        </button>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="!selected || saving || !dirty"
          @click="save"
        >
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="panel error-panel">{{ error }}</p>
    <UnauthorizedHint :show="unauthorized" />

    <div class="lang-layout">
      <aside class="card lang-list">
        <button
          v-for="code in codes"
          :key="code"
          type="button"
          class="lang-item"
          :class="{ active: code === selected }"
          @click="selected = code"
        >
          <span class="mono">{{ code }}</span>
          <span v-if="isDirty(code)" class="dirty-dot" title="有未保存的修改">●</span>
        </button>
        <p v-if="!codes.length && !loading" class="hint">没有可用语言。</p>
      </aside>

      <div class="card lang-editor">
        <template v-if="selected">
          <header class="card-head">
            <h3 class="mono">{{ selected }}.yml</h3>
            <span class="hint">{{ dirty ? '有未保存的修改' : '与服务端一致' }}</span>
          </header>
          <textarea
            v-model="draft"
            class="mono lang-textarea"
            spellcheck="false"
            wrap="off"
            placeholder="messages:&#10;  prefix: '&lt;gray&gt;[任务]&lt;/gray&gt;'"
          ></textarea>
        </template>
        <p v-else class="hint">在左侧选择一种语言开始编辑。</p>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import UnauthorizedHint from '../components/UnauthorizedHint.vue'
import { useToast } from '../composables/useToast'
import { LangApi, errorMessage, isUnauthorized } from '../services/api'

const toast = useToast()

/** 服务端原文，用于判断是否有未保存修改。 */
const originals = ref<Record<string, string>>({})
/** 本地草稿：按语言分别保存，切换语言不会丢改动。 */
const drafts = ref<Record<string, string>>({})
const selected = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const unauthorized = ref(false)

const codes = computed(() => Object.keys(originals.value))

const draft = computed<string>({
  get: () => drafts.value[selected.value] ?? '',
  set: value => {
    drafts.value = { ...drafts.value, [selected.value]: value }
  }
})

const dirty = computed(() => !!selected.value && isDirty(selected.value))

function isDirty(code: string): boolean {
  return (drafts.value[code] ?? '') !== (originals.value[code] ?? '')
}

onMounted(() => {
  void reload()
})

async function reload(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const langs = await LangApi.list()
    originals.value = { ...langs }
    drafts.value = { ...langs }
    unauthorized.value = false
    if (!selected.value || !(selected.value in langs)) {
      selected.value = Object.keys(langs)[0] ?? ''
    }
  } catch (e) {
    error.value = `加载语言文件失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  const code = selected.value
  if (!code || saving.value) {
    return
  }
  saving.value = true
  error.value = ''
  const content = drafts.value[code] ?? ''
  try {
    const result = await LangApi.save(code, content)
    originals.value = { ...originals.value, [result.code || code]: content }
    unauthorized.value = false
    toast.success(`语言文件 ${result.code || code} 已保存`)
  } catch (e) {
    // 后端会因为 YAML 非法而拒绝写入，这里把原因原样展示出来
    error.value = `保存 ${code}.yml 失败：${errorMessage(e)}`
    unauthorized.value = isUnauthorized(e)
    toast.error('保存失败，请检查 YAML 缩进与格式')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.lang-layout {
  display: grid;
  grid-template-columns: 12rem 1fr;
  gap: 0.75rem;
  align-items: start;
}

.lang-list {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.5rem;
}

.lang-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.4rem;
  width: 100%;
  padding: 0.45rem 0.6rem;
  border: 1px solid transparent;
  border-radius: var(--radius);
  background: transparent;
  color: var(--text-dim);
  font-size: 0.88rem;
  text-align: left;
  cursor: pointer;
}

.lang-item:hover {
  background: var(--bg-hover);
  color: var(--text);
}

.lang-item.active {
  border-color: var(--accent);
  color: var(--accent);
}

.dirty-dot {
  color: var(--warn);
  font-size: 0.6rem;
}

.lang-editor {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.lang-textarea {
  min-height: 60vh;
  resize: vertical;
  white-space: pre;
  overflow-wrap: normal;
  overflow-x: auto;
}
</style>
