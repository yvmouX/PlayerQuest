<!--
  图标 / 材质 / 实体 / 目标 选择器。

  <h2>为什么不是纯下拉框</h2>
  1000+ 条材质塞进原生 select 既没法搜索，也只能显示 id。这里做成
  「文本框 + 搜索浮层」：文本框里始终是逗号分隔的原始枚举名，高级用户可以直接
  手打或粘贴，普通用户点开浮层按中文/英文/枚举名搜索——两种输入方式并存，
  互不遮挡。

  <h2>性能取舍</h2>
  不做虚拟滚动（那需要引入依赖或写一套麻烦的滚动计算）：搜索后最多渲染
  {@link MAX_VISIBLE_ITEMS} 条，末尾明确提示「还有 N 项未显示，请细化搜索」。
  对「输入关键词 → 命中个位数」的真实用法来说，这比虚拟滚动更简单可靠。

  <h2>数据来源</h2>
  目录由 App.vue 在启动时预加载（utils/catalog.ts 里的单例），因此打开浮层
  不会出现请求等待；万一预加载失败，这里也会在首次展开时重试一次。
-->
<template>
  <div class="picker">
    <!-- 已选中的多个条目：MATERIAL 支持逗号分隔多值，逐个可移除 -->
    <div v-if="multi && chips.length" class="picker-chips">
      <span
        v-for="chip in chips"
        :key="chip.raw"
        class="picker-chip"
        :class="{ unknown: chip.unknown }"
        :title="chip.unknown ? '目录里没有这个枚举名，将按原文提交' : chip.raw"
      >
        <span class="picker-chip-label">{{ chip.label }}</span>
        <code class="mono">{{ chip.raw }}</code>
        <button
          class="picker-chip-remove"
          type="button"
          :aria-label="`移除 ${chip.raw}`"
          @click="removeChip(chip.raw)"
        >×</button>
      </span>
      <button class="btn btn-small" type="button" @click="clearAll">清空</button>
    </div>

    <div class="picker-input">
      <input
        ref="inputRef"
        type="text"
        :value="text"
        :placeholder="placeholder"
        :aria-label="ariaLabel"
        autocomplete="off"
        spellcheck="false"
        role="combobox"
        :aria-expanded="open"
        @input="onInput"
        @focus="onFocus"
        @keydown="onKeydown"
      />
      <button
        v-if="text"
        class="picker-clear"
        type="button"
        title="清空（必填字段请重新选择）"
        aria-label="清空"
        @click="clearAll"
      >×</button>
      <button
        class="picker-toggle"
        type="button"
        :title="open ? '收起列表' : '展开列表'"
        :aria-label="open ? '收起列表' : '展开列表'"
        @click="toggle"
      >{{ open ? '▲' : '▼' }}</button>
    </div>

    <div v-if="open" ref="panelRef" class="picker-panel">
      <!-- 来源（插件）筛选：装了哪些插件就出现哪些标签，只有一个来源时整排不显示 -->
      <div v-if="sources.length" class="picker-tabs picker-tabs-source">
        <button
          v-for="tab in sources"
          :key="tab.value"
          class="picker-tab"
          :class="{ active: source === tab.value }"
          type="button"
          @click="selectSource(tab.value)"
        >{{ tab.label }}</button>
      </div>

      <div class="picker-tabs">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          class="picker-tab"
          :class="{ active: category === tab.value }"
          type="button"
          @click="selectCategory(tab.value)"
        >{{ tab.label }}</button>
      </div>

      <div class="picker-search-hint">
        <span v-if="loading" class="hint">正在加载目录…</span>
        <span v-else-if="loadFailed" class="warn-line">
          目录加载失败，可继续手动输入枚举名。
          <button class="btn btn-small" type="button" @click="reload">重试</button>
        </span>
        <span v-else-if="catalog && !catalog.hasChinese" class="warn-line">
          当前只有英文名：Minecraft 服务端不自带中文语言文件。用英文名或枚举名同样能搜到。
        </span>
        <span v-else class="hint">
          共 {{ pool.length }} 项，可搜中文名 / 英文名 / 枚举名（如 钻石、diamond、DIAMOND_ORE）。
        </span>
      </div>

      <p v-if="!visible.items.length && !loading" class="guide-line">
        {{ pool.length ? '没有匹配的条目，换个关键词或切回「全部」。' : '这份目录里没有可选项。' }}
      </p>

      <ul v-else class="picker-list" role="listbox">
        <li
          v-for="(item, index) in visible.items"
          :key="item.id"
          class="picker-item"
          :class="{ active: index === activeIndex, picked: isPicked(item) }"
          role="option"
          :aria-selected="isPicked(item)"
          @mouseenter="activeIndex = index"
          @click="pick(item)"
        >
          <code class="picker-id mono">{{ item.id }}</code>
          <span class="picker-label">{{ entryLabel(item) }}</span>
          <span v-if="entrySubLabel(item)" class="picker-sub">{{ entrySubLabel(item) }}</span>
          <span v-if="item.category" class="badge badge-gray">{{ categoryLabel(item.category) }}</span>
          <!-- 非原版条目额外标出来源：同一个列表里混着原版与几家插件的内容，一眼要能分辨 -->
          <span v-if="sourceOf(item) !== SOURCE_MINECRAFT" class="badge badge-source">
            {{ sourceLabel(sourceOf(item), catalog) }}
          </span>
        </li>
      </ul>

      <p v-if="visible.truncated" class="picker-more hint">
        还有 {{ visible.total - visible.items.length }} 项未显示，请细化搜索关键词。
      </p>
      <p v-else-if="visible.items.length" class="picker-more hint">已显示全部 {{ visible.total }} 条结果。</p>

      <p class="picker-foot hint">
        <template v-if="multi">点击条目可连续多选，再次点击取消；</template>
        <template v-else>点击条目即选中；</template>
        回车选中第一项，Esc 关闭。
        <template v-if="emptyHint">留空表示任意。</template>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { CatalogEntry } from '../types'
import {
  CATEGORY_ALL,
  SOURCE_ALL,
  SOURCE_MINECRAFT,
  type CatalogScope,
  categoryLabel,
  categoryTabs,
  entriesForScope,
  entryLabel,
  entrySubLabel,
  joinMaterialValue,
  loadCatalog,
  normalizeCatalog,
  peekCatalog,
  resolveValueText,
  sourceLabel,
  sourceOf,
  sourceTabs,
  splitMaterialValue,
  visibleEntries
} from '../utils/catalog'

const props = withDefaults(
  defineProps<{
    /** 逗号分隔的原始文本；MATERIAL 多值时形如 "STONE,DIRT" */
    modelValue: string
    /** 选择范围：材质 / 实体 / 两者 */
    scope?: CatalogScope
    /** 是否允许多选（MATERIAL 字段为真） */
    multi?: boolean
    placeholder?: string
    /** 是否提示「留空表示任意」 */
    emptyHint?: boolean
  }>(),
  {
    scope: 'material',
    multi: false,
    placeholder: '留空或点击右侧展开列表',
    emptyHint: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const panelRef = ref<HTMLDivElement | null>(null)
const open = ref(false)
const loading = ref(false)
const loadFailed = ref(false)
const keyword = ref('')
const category = ref(CATEGORY_ALL)
const source = ref(SOURCE_ALL)
const activeIndex = ref(0)
/** 目录数据；预加载已完成时直接取缓存，避免展开时闪一下「加载中」。 */
const catalog = ref(peekCatalog())

const text = computed(() => props.modelValue ?? '')
const ariaLabel = computed(() => (props.multi ? '选择材质，可多选' : '选择条目'))
const pool = computed(() => entriesForScope(catalog.value, props.scope))
const tabs = computed(() => categoryTabs(catalog.value, props.scope))
const sources = computed(() => sourceTabs(catalog.value, props.scope))
const visible = computed(() =>
  visibleEntries(pool.value, keyword.value, category.value, source.value)
)
const chips = computed(() =>
  props.multi ? resolveValueText(text.value, pool.value) : []
)

/** 切换范围/分类/来源时把高亮项收敛回第一条，否则回车可能选中看不见的项。 */
watch([() => props.scope, category, source, keyword], () => {
  activeIndex.value = 0
})

/**
 * 范围变化时把来源收窄回「全部」：从材质切到实体时留着 craftengine，
 * 列表会直接空掉，看起来像「这里什么都没有」。
 */
watch(() => props.scope, () => {
  const available = sources.value.map(tab => tab.value)
  if (source.value !== SOURCE_ALL && !available.includes(source.value)) {
    source.value = SOURCE_ALL
  }
})

onMounted(() => {
  document.addEventListener('mousedown', onDocumentMouseDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentMouseDown)
})

/** 点击组件外部关闭浮层；浮层内的点击由自身处理。 */
function onDocumentMouseDown(event: MouseEvent): void {
  if (!open.value) {
    return
  }
  const target = event.target as Node | null
  if (target && (panelRef.value?.contains(target) || inputRef.value?.parentElement?.contains(target))) {
    return
  }
  open.value = false
}

function onFocus(): void {
  void ensureCatalog()
  open.value = true
}

/** 展开浮层：目录可能还没预加载完，这里兜一次。 */
function openPanel(): void {
  void ensureCatalog()
  open.value = true
  void nextTick(() => inputRef.value?.focus())
}

function toggle(): void {
  if (open.value) {
    open.value = false
    return
  }
  openPanel()
}

/** 首次展开时确保目录到位；已预加载则立即返回。 */
async function ensureCatalog(): Promise<void> {
  if (catalog.value) {
    return
  }
  loading.value = true
  loadFailed.value = false
  const data = await loadCatalog()
  loading.value = false
  if (data) {
    catalog.value = normalizeCatalog(data)
  } else {
    loadFailed.value = true
  }
}

function reload(): void {
  // 清掉本地引用，让 loadCatalog 的失败重试逻辑重新发起请求
  catalog.value = null
  void ensureCatalog()
}

/**
 * 输入框永远保持可手打：这里原样回传文本，
 * 选择器只是「帮忙填」，不做任何格式校验或拦截。
 */
function onInput(event: Event): void {
  const target = event.target as HTMLInputElement | null
  keyword.value = target ? target.value : ''
  emit('update:modelValue', keyword.value)
  if (!open.value) {
    void ensureCatalog()
    open.value = true
  }
}

function isPicked(entry: CatalogEntry): boolean {
  return splitMaterialValue(text.value).some(value => value.toLowerCase() === entry.id.toLowerCase())
}

/** 单选：直接替换文本并关闭；多选：切换该条目后保持展开。 */
function pick(entry: CatalogEntry): void {
  if (!props.multi) {
    emit('update:modelValue', entry.id)
    keyword.value = ''
    open.value = false
    return
  }
  const values = splitMaterialValue(text.value)
  const index = values.findIndex(value => value.toLowerCase() === entry.id.toLowerCase())
  if (index >= 0) {
    values.splice(index, 1)
  } else {
    values.push(entry.id)
  }
  emit('update:modelValue', joinMaterialValue(values))
  // 多选时清空关键词：连续挑选不同分类的条目时不用反复删搜索词
  keyword.value = ''
  void nextTick(() => inputRef.value?.focus())
}

function removeChip(raw: string): void {
  const values = splitMaterialValue(text.value).filter(value => value !== raw)
  emit('update:modelValue', joinMaterialValue(values))
}

function clearAll(): void {
  keyword.value = ''
  emit('update:modelValue', '')
  void nextTick(() => inputRef.value?.focus())
}

function selectCategory(value: string): void {
  category.value = value
}

function selectSource(value: string): void {
  source.value = value
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    if (open.value) {
      event.stopPropagation()
      open.value = false
    }
    return
  }
  if (!open.value) {
    if (event.key === 'ArrowDown' || event.key === 'Enter') {
      void ensureCatalog()
      open.value = true
    }
    return
  }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    const count = visible.value.items.length
    if (!count) {
      return
    }
    const offset = event.key === 'ArrowDown' ? 1 : -1
    activeIndex.value = (activeIndex.value + offset + count) % count
    scrollActiveIntoView()
    return
  }
  if (event.key === 'Enter') {
    event.preventDefault()
    // 没有关键词时列表是「全部条目」，回车选中第一项只会让人莫名其妙地
    // 拿到 ACACIA_BOAT 之类的条目；这种情况下回车只收起列表。
    if (!keyword.value.trim()) {
      open.value = false
      return
    }
    const item = visible.value.items[activeIndex.value] ?? visible.value.items[0]
    if (item) {
      pick(item)
    }
  }
}

/** 键盘移动高亮项时把它滚进可视区，否则高亮会跑到浮层外面。 */
function scrollActiveIntoView(): void {
  void nextTick(() => {
    panelRef.value
      ?.querySelector('.picker-item.active')
      ?.scrollIntoView({ block: 'nearest' })
  })
}
</script>

<style scoped>
.picker {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.picker-input {
  position: relative;
  display: flex;
  align-items: center;
}

/* 给右侧两个按钮留出位置，避免长枚举名被按钮盖住 */
.picker-input input {
  padding-right: 3.4rem;
}

.picker-clear,
.picker-toggle {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 1.5rem;
  height: 1.5rem;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--text-dim);
  font-size: 0.7rem;
  font-family: inherit;
  line-height: 1;
  cursor: pointer;
}

.picker-clear {
  right: 1.7rem;
  font-size: 0.95rem;
}

.picker-toggle {
  right: 0.2rem;
}

.picker-clear:hover,
.picker-toggle:hover {
  background: var(--bg-hover);
  color: var(--text);
}

.picker-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
}

.picker-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.15rem 0.3rem 0.15rem 0.55rem;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--bg-input);
  font-size: 0.78rem;
}

.picker-chip.unknown {
  border-color: #5b4620;
}

.picker-chip code {
  color: var(--text-dim);
  font-size: 0.72rem;
}

.picker-chip-remove {
  border: none;
  background: transparent;
  color: var(--text-dim);
  font-size: 0.9rem;
  line-height: 1;
  cursor: pointer;
}

.picker-chip-remove:hover {
  color: var(--danger);
}

.picker-panel {
  position: absolute;
  z-index: 40;
  top: calc(100% + 0.25rem);
  left: 0;
  right: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 0.5rem;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-elevated);
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.5);
}

.picker-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

/* 来源那一排与分类视觉上分开：上面是「哪个插件」，下面是「哪一类东西」 */
.picker-tabs-source {
  padding-bottom: 0.3rem;
  border-bottom: 1px solid var(--border-soft);
}

.badge-source {
  border-color: var(--accent);
  color: var(--accent);
}

.picker-tab {
  padding: 0.15rem 0.55rem;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--bg-input);
  color: var(--text-dim);
  font-size: 0.75rem;
  font-family: inherit;
  cursor: pointer;
}

.picker-tab:hover {
  color: var(--text);
}

.picker-tab.active {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent);
}

.picker-search-hint {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.picker-list {
  list-style: none;
  max-height: 15rem;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

.picker-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 0.4rem;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
}

.picker-item.active {
  background: var(--bg-hover);
}

.picker-item.picked {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.picker-id {
  min-width: 12rem;
  color: var(--accent);
}

.picker-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picker-sub {
  color: var(--text-dim);
  font-size: 0.75rem;
}

.picker-more,
.picker-foot {
  margin: 0;
}

.picker-foot {
  border-top: 1px solid var(--border-soft);
  padding-top: 0.35rem;
}
</style>
