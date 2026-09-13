/**
 * 「可视化 / YAML」两种视图的共用逻辑。
 *
 * <h2>为什么 YAML 视图不另存一份数据</h2>
 * YAML 里改完的内容会<b>实时解析并写回同一个表单状态</b>，因此：
 * 保存路径、校验、预览、脏标记全都还是原来那一套，没有第二条保存链路；
 * 也不会出现「YAML 里改了但保存的是表单里的旧值」这种最难查的不一致。
 *
 * <h2>解析失败时不碰表单</h2>
 * 手写 YAML 中途必然是非法文本（少一个缩进就废）。失败时只留下错误提示，
 * 表单与预览停在最后一次成功解析的状态，<b>绝不</b>把表单清空——
 * 那会让「正在编辑」变成「正在丢数据」。
 *
 * <h2>切回可视化前必须解析成功</h2>
 * 否则表单会显示与文本不一致的旧内容，而用户以为自己切过去看到的就是刚才写的。
 * 解析不过去就拒绝切换并给出原因，文本原样留着让人继续改。
 */
import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import type { YamlParseResult } from '../utils/yaml'

export type EditorViewMode = 'visual' | 'yaml'

/** 输入停顿多久后才解析：逐个字符解析既浪费又会让报错乱跳。 */
const PARSE_DEBOUNCE_MS = 250

export interface YamlModeSpec<T> {
  /** 表单 → YAML 文本 */
  render: () => string
  /** YAML 文本 → 值（含错误与警告） */
  parse: (text: string) => YamlParseResult<T>
  /** 解析成功时写回表单 */
  apply: (value: T) => void
  /** 解析成功但被刻意忽略的改动（例如已存在任务的 id），用于补充警告 */
  extraWarnings?: (value: T) => string[]
}

export interface YamlMode {
  mode: Ref<EditorViewMode>
  text: Ref<string>
  error: Ref<string>
  warnings: Ref<string[]>
  /** YAML 文本是否可用（没有语法错误）；保存前必须为 true */
  valid: Ref<boolean>
  /** 用当前表单重新生成文本（进入 YAML 视图、或切换编辑对象时调用） */
  syncFromSource: () => void
  /** 立即解析并写回表单；返回是否成功 */
  applyNow: () => boolean
  /** 切到 YAML 视图（顺带用表单内容重新生成文本） */
  toYaml: () => void
  /** 切回可视化视图；文本解析失败时返回 false 且不切换 */
  toVisual: () => boolean
}

export function useYamlMode<T>(spec: YamlModeSpec<T>): YamlMode {
  const mode = ref<EditorViewMode>('visual')
  const text = ref('')
  const error = ref('')
  const warnings = ref<string[]>([])
  const valid = ref(true)
  let timer: ReturnType<typeof setTimeout> | null = null

  function syncFromSource(): void {
    text.value = spec.render()
    error.value = ''
    warnings.value = []
    valid.value = true
  }

  function applyNow(): boolean {
    const result = spec.parse(text.value)
    if (result.value === null) {
      error.value = result.error
      warnings.value = result.warnings
      valid.value = false
      return false
    }
    spec.apply(result.value)
    error.value = ''
    warnings.value = [...result.warnings, ...(spec.extraWarnings?.(result.value) ?? [])]
    valid.value = true
    return true
  }

  watch(text, () => {
    if (mode.value !== 'yaml') {
      return
    }
    if (timer !== null) {
      clearTimeout(timer)
    }
    timer = setTimeout(() => {
      timer = null
      applyNow()
    }, PARSE_DEBOUNCE_MS)
  })

  onBeforeUnmount(() => {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  })

  return {
    mode,
    text,
    error,
    warnings,
    valid,
    syncFromSource,
    applyNow,
    toYaml(): void {
      syncFromSource()
      mode.value = 'yaml'
    },
    toVisual(): boolean {
      if (timer !== null) {
        // 还在防抖窗口里：先把最新文本落地，否则切过去看到的是半秒前的内容
        clearTimeout(timer)
        timer = null
      }
      if (!applyNow()) {
        return false
      }
      mode.value = 'visual'
      return true
    }
  }
}
