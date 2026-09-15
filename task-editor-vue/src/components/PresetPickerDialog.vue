<!--
  「添加目标 / 添加奖励」弹层。

  <h2>为什么先列预设</h2>
  建任务时反复填写「挖 64 个石头」这类相同配置很费事，而预设正是为此存在的。
  因此这个弹层的第一屏是预设列表（点一下直接插入），「从空白新建」作为次选入口
  保留在底部——两条路径都留着，才不会出现「想用预设想不起来在哪」的情况。

  <h2>无效预设</h2>
  预设引用的类型可能已经不存在（管理员改过代码）。这类预设置灰、标注原因、
  且不可点击，但不影响同列表里其它预设的使用，也不会让弹层报错。
-->
<template>
  <Teleport to="body">
    <div v-if="show" class="dialog-overlay" @click.self="close">
      <div class="dialog preset-dialog" role="dialog" aria-modal="true">
        <header class="preset-head">
          <div>
            <h3>{{ title }}</h3>
            <p class="hint">
              点击预设即<b>引用</b>它：类型与字段由预设提供，以后改预设，引用它的任务一起变。
              要一份不跟随的副本，用条目右侧的「复制」。
            </p>
          </div>
          <button class="btn btn-small" type="button" @click="close">关闭</button>
        </header>

        <p v-if="loading" class="panel">正在加载预设…</p>

        <template v-else>
          <p v-if="loadError" class="panel error-panel">{{ loadError }}</p>

          <div v-if="!views.length" class="empty-state">
            <strong>还没有预设</strong>
            <p class="hint">
              可以在「预设管理」页新建常用配置，或在编辑器里把已填好的
              {{ kind === 'objectives' ? '目标' : '奖励' }}「存为预设」。
            </p>
          </div>

          <template v-else>
            <!-- 预设一多，一屏放不下：搜索框直接把候选缩到几条，比滚轮快 -->
            <input
              v-model="filter"
              class="preset-search"
              type="search"
              :placeholder="`搜索预设：名称 / id / 类型（共 ${views.length} 条）`"
            />

            <p v-if="!visibleViews.length" class="hint">没有匹配「{{ filter }}」的预设。</p>
            <ul v-else class="preset-list">
              <li
                v-for="view in visibleViews"
                :key="view.preset.id || view.preset.name"
                class="preset-item"
                :class="{ invalid: !view.valid }"
                :title="view.valid ? '点击引用该预设' : view.invalidReason"
                @click="use(view, 'reference')"
              >
                <div class="preset-item-head">
                  <strong>{{ view.preset.name }}</strong>
                  <span class="badge badge-blue">{{ view.typeLabel }}</span>
                  <span
                    v-if="view.preset.source === 'file'"
                    class="badge badge-gray"
                    title="预设本身来自 presets/ 下的 YAML 文件（只读），但引用它不受影响"
                  >只读 · YAML</span>
                  <span v-if="!view.valid" class="badge badge-warn">无效</span>
                  <button
                    v-if="view.valid"
                    class="btn btn-small preset-copy"
                    type="button"
                    title="插入一份独立副本：之后改预设不再影响它"
                    @click.stop="use(view, 'copy')"
                  >复制</button>
                </div>
                <code class="mono preset-item-type">{{ view.preset.type }}</code>
                <p class="hint">{{ view.summary }}</p>
                <p v-if="view.preset.description" class="hint preset-item-desc">{{ view.preset.description }}</p>
                <p v-if="!view.valid" class="warn-line">{{ view.invalidReason }}</p>
              </li>
            </ul>
          </template>
        </template>

        <footer class="preset-foot">
          <span class="hint">
            <template v-if="blankAvailable">引用 = 跟随预设；复制 = 独立配置，可以随意改。也可以从空白新建。</template>
            <template v-else>后端没有注册任何类型，无法新建。</template>
          </span>
          <div class="preset-foot-actions">
            <button class="btn btn-small" type="button" @click="openPresetManage">管理预设</button>
            <button
              class="btn btn-primary btn-small"
              type="button"
              :disabled="!blankAvailable"
              @click="emit('blank')"
            >从空白新建</button>
          </div>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '../composables/useToast'
import type { Preset, PresetKind, PresetMap, TypeSchema } from '../types'
import { loadPresets, normalizePresetMap, peekPresets, presetView } from '../utils/presets'
import type { PresetView } from '../utils/presets'

const props = defineProps<{
  show: boolean
  kind: PresetKind
  /** 当前可用的类型定义；预设引用的类型不在其中即视为无效 */
  schemas: Record<string, TypeSchema>
}>()

const emit = defineEmits<{
  /** 引用一个有效预设（写 preset 键，字段由预设提供） */
  pick: [preset: Preset]
  /** 插入一份独立副本（把预设当前的字段值复制过来，之后与预设无关） */
  copy: [preset: Preset]
  /** 用户选择「从空白新建」 */
  blank: []
  cancel: []
}>()

const router = useRouter()
const toast = useToast()

const loading = ref(false)
const loadError = ref('')
/**
 * 预设表；未加载时给一份空表而不是 null。
 *
 * <p>缓存里的值本来就经过 {@link normalizePresetMap}，这里只在「还没读过」时补一份同形状的
 * 空表——渲染路径因此不必再为 null 分支，也不必每次 computed 重算时重复规范化。
 */
const presets = ref<PresetMap>(peekPresets() ?? normalizePresetMap(null))
const filter = ref('')

const title = computed(() => (props.kind === 'objectives' ? '添加目标' : '添加奖励'))
const views = computed(() => {
  // presets 里的值已经过 normalizePresetMap（见 loadPresets），这里只按类别取一份
  const list = props.kind === 'objectives' ? presets.value.objectives : presets.value.rewards
  return list.map(preset => presetView(preset, props.schemas))
})

const visibleViews = computed(() => {
  const keyword = filter.value.trim().toLowerCase()
  if (!keyword) {
    return views.value
  }
  return views.value.filter(({ preset }) =>
    [preset.name, preset.id, preset.type].some(value => (value ?? '').toLowerCase().includes(keyword))
  )
})
/** 一个类型都没有时，「从空白新建」也无从下手，直接禁用并说明原因。 */
const blankAvailable = computed(() => Object.keys(props.schemas).length > 0)

// immediate：组件挂载时 show 可能已经是 true（父组件用 v-if 之外的方式控制显隐），
// 只监听「从 false 变 true」会漏掉这种情况，弹层就会一直是空的
watch(() => props.show, visible => {
  loadError.value = ''
  if (visible) {
    void ensureLoaded()
  }
}, { immediate: true })

async function ensureLoaded(): Promise<void> {
  const cached = peekPresets()
  if (cached) {
    presets.value = cached
    return
  }
  loading.value = true
  loadError.value = ''
  const data = await loadPresets()
  loading.value = false
  if (data) {
    presets.value = data
  } else {
    loadError.value = '加载预设失败：无法连接后端，可先用「从空白新建」。'
  }
}

/** 无效预设不可选用：引用或复制后只会得到一个后端不认识的目标。 */
function use(view: PresetView, mode: 'reference' | 'copy'): void {
  if (!view.valid) {
    // 用 toast 而不是往弹层里塞错误行：无效原因已经标在该条目上了，
    // 再插一行提示会把它往下顶，反而更难看清
    toast.error(`无法使用预设「${view.preset.name}」：${view.invalidReason}`)
    return
  }
  if (mode === 'copy') {
    emit('copy', view.preset)
  } else {
    emit('pick', view.preset)
  }
}

function openPresetManage(): void {
  close()
  void router.push({ name: 'presets' })
}

function close(): void {
  emit('cancel')
}
</script>

<style scoped>
/* .dialog.preset-dialog 提高优先级：全局 .dialog 也是单类选择器，
   宽度覆写不能靠打包顺序决定胜负 */
.dialog.preset-dialog {
  width: min(44rem, 94vw);
  max-height: 86vh;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.preset-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}

.preset-head h3 {
  font-size: 1rem;
}

.preset-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  overflow: auto;
  padding-right: 0.2rem;
}

.preset-search {
  width: 100%;
}

.preset-item {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.6rem 0.75rem;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-input);
  cursor: pointer;
}

.preset-item:hover {
  border-color: var(--accent);
  background: var(--bg-hover);
}

.preset-item.invalid {
  opacity: 0.55;
  cursor: not-allowed;
}

.preset-item.invalid:hover {
  border-color: var(--border);
  background: var(--bg-input);
}

.preset-item-head {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

/* 复制按钮推到行尾：条目本体是「引用」，这个按钮是另一条路径，别混在一起点 */
.preset-copy {
  margin-left: auto;
}

.preset-item-type {
  color: var(--text-dim);
  font-size: 0.72rem;
}

.preset-item-desc {
  border-left: 2px solid var(--border);
  padding-left: 0.45rem;
}

.preset-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  border-top: 1px solid var(--border-soft);
  padding-top: 0.6rem;
}

.preset-foot-actions {
  display: flex;
  gap: 0.4rem;
}
</style>
