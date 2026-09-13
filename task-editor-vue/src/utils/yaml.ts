/**
 * 任务 / 预设 ↔ YAML 文本。
 *
 * <h2>YAML 只是「同一个对象的另一种写法」</h2>
 * 键名与 JSON 契约（{@code QuestJson} / {@code PresetJson} 的字段名）完全一致，
 * 因此：整份导出 JSON 里的内容可以直接粘进 YAML 视图，反之亦然；
 * 字段含义也只有一套解释（后端的映射），前端不另立一套命名。
 *
 * <h2>为什么用 js-yaml 而不是让后端转</h2>
 * 后端转要多一组接口与一次往返；更重要的是 Bukkit 的 YAML 是 1.1 语义，
 * 裸写的 {@code yes:} / {@code NO} 会被读成布尔——本项目的语言文件已经在这上面栽过一次
 * （{@code common.yes} 从未生效）。js-yaml 4 默认按 YAML 1.2 core 解析，
 * 只有 {@code true}/{@code false} 才是布尔，用户写 {@code target: NO} 不会被悄悄改掉。
 * {@code scripts/yaml-check.mjs} 把这些字符串的往返钉在构建里。
 *
 * <h2>未知字段不静默丢弃</h2>
 * 解析后比对顶层键，多出来的键会以警告形式列出：写错一个键名（{@code reward:} 而不是
 * {@code rewards:}）在纯文本编辑里太容易发生，静默丢掉等于让管理员以为配置生效了。
 */
import { dump, load } from 'js-yaml'
import type { Preset, Properties, Quest } from '../types'
import { normalizeImportedQuest } from '../services/api'

/** 解析结果：值、错误、警告三者必有其一。失败时 {@code value} 为 null 且 {@code error} 非空。 */
export interface YamlParseResult<T> {
  value: T | null
  error: string
  warnings: string[]
}

/**
 * 序列化选项。
 *
 * <p>{@code lineWidth: -1}：不折行——长描述被折成多行后，手写的那份文本会变得难读，
 * 而且折行会改变字符串里空格的解释（多行折行会插入换行）。
 * {@code noRefs}：同一份配置里出现相同对象时不写 {@code &anchor} / {@code *alias}，
 * 那是给人看的文本，不是给人解引用的。
 */
const DUMP_OPTIONS = { lineWidth: -1, noRefs: true, sortKeys: false } as const

/** 顶层字段清单——与 {@link questDocument} 的键序同源，避免两处各写一份。 */
const QUEST_KEYS = keysOfQuestDocument()
const PRESET_KEYS = ['id', 'name', 'type', 'description', 'properties'] as const

// ---------------------------------------------------------------------------
// 任务
// ---------------------------------------------------------------------------

/** 任务 → YAML 文本。 */
export function questToYaml(quest: Quest): string {
  return dump(questDocument(quest), DUMP_OPTIONS)
}

/**
 * 任务 → 与 JSON 契约同形的普通对象（键序即 YAML 里的顺序）。
 *
 * <p>空值策略：空字符串、空列表、以及后端算出来的 {@code problems} 一律省略——
 * 手写视图里堆一串 `category: ''`、`prerequisites: []` 只会让人以为必须填。
 * 省掉它们不丢信息：解析时本来就是「缺省即默认」。
 */
export function questDocument(quest: Quest): Record<string, unknown> {
  const doc: Record<string, unknown> = { id: quest.id, name: quest.name }
  if (quest.description?.length) {
    doc.description = [...quest.description]
  }
  doc.icon = quest.icon || 'PAPER'
  if (quest.category) {
    doc.category = quest.category
  }
  doc.type = quest.type
  doc.refreshCost = Number(quest.refreshCost) || 0
  doc.enabled = quest.enabled !== false
  if (quest.prerequisites?.length) {
    doc.prerequisites = [...quest.prerequisites]
  }
  doc.objectives = (quest.objectives ?? []).map(objectiveDocument)
  doc.rewards = (quest.rewards ?? []).map(objectiveDocument)
  return doc
}

/** 目标/奖励实例 → 普通对象：{@code type} + {@code properties}（与 JSON 契约一致）。 */
function objectiveDocument(instance: { type: string; properties: Properties }) {
  return { type: instance.type, properties: { ...instance.properties } }
}

/** YAML 文本 → 任务。 */
export function questFromYaml(text: string, fallbackId: string): YamlParseResult<Quest> {
  // 没有目标的任务也算「解析成功」：可视化表单同样允许保存这种任务，
  // 由后端校验标红（「任务没有配置任何目标」），两条入口的口径必须一致
  return parseDocument(text, QUEST_KEYS, raw => normalizeImportedQuest(raw, fallbackId),
      '任务至少要能读出 id 与 objectives')
}

// ---------------------------------------------------------------------------
// 预设
// ---------------------------------------------------------------------------

/** 预设 → YAML 文本。 */
export function presetToYaml(preset: Preset): string {
  return dump(presetDocument(preset), DUMP_OPTIONS)
}

/** 预设 → 普通对象（键序即 YAML 里的顺序）。 */
export function presetDocument(preset: Preset): Record<string, unknown> {
  const doc: Record<string, unknown> = {}
  if (preset.id) {
    doc.id = preset.id
  }
  doc.name = preset.name
  doc.type = preset.type
  if (preset.description) {
    doc.description = preset.description
  }
  doc.properties = { ...preset.properties }
  return doc
}

/** YAML 文本 → 预设。 */
export function presetFromYaml(text: string): YamlParseResult<Preset> {
  return parseDocument(text, PRESET_KEYS, normalizePreset, '预设缺少必需字段 type');
}

/** 预设的形状校验：只要求 {@code type} 非空（后端也是如此）。 */
function normalizePreset(raw: Record<string, unknown>): Preset | null {
  const type = typeof raw.type === 'string' ? raw.type.trim() : ''
  if (!type) {
    return null
  }
  const properties = raw.properties
  return {
    id: typeof raw.id === 'string' ? raw.id.trim() : '',
    name: typeof raw.name === 'string' && raw.name.trim() ? raw.name : type,
    type,
    description: typeof raw.description === 'string' ? raw.description : '',
    properties: properties && typeof properties === 'object' && !Array.isArray(properties)
      ? properties as Properties
      : {}
  }
}

// ---------------------------------------------------------------------------
// 公共解析
// ---------------------------------------------------------------------------

/**
 * 解析一段 YAML：顶层必须是映射，未知键只警告不报错，形状交给 {@code normalize}。
 *
 * @param allowedKeys    顶层允许的键（多出来的会被列进警告）
 * @param normalize      把「不可信对象」整成模型；返回 null 表示缺少必需字段
 * @param missingMessage 缺少必需字段时给用户看的话（说清楚缺什么）
 */
function parseDocument<T>(
  text: string,
  allowedKeys: readonly string[],
  normalize: (raw: Record<string, unknown>) => T | null,
  missingMessage: string
): YamlParseResult<T> {
  if (!text.trim()) {
    return { value: null, error: '内容为空', warnings: [] }
  }
  let loaded: unknown
  try {
    loaded = load(text)
  } catch (e) {
    return { value: null, error: e instanceof Error ? e.message : String(e), warnings: [] }
  }
  if (!loaded || typeof loaded !== 'object' || Array.isArray(loaded)) {
    return { value: null, error: '顶层必须是一组「键: 值」', warnings: [] }
  }
  const raw = loaded as Record<string, unknown>
  const warnings = unknownKeyWarnings(raw, allowedKeys)
  const value = normalize(raw)
  if (value === null) {
    return { value: null, error: missingMessage, warnings }
  }
  return { value, error: '', warnings }
}

/**
 * 未知键警告。
 *
 * <p>列出「实际写了什么」和「允许写什么」：只报「有未知字段」的话，
 * 管理员还得自己去猜哪个键名拼错了。
 */
function unknownKeyWarnings(raw: Record<string, unknown>, allowedKeys: readonly string[]): string[] {
  const unknown = Object.keys(raw).filter(key => !allowedKeys.includes(key))
  if (!unknown.length) {
    return []
  }
  return [`忽略了未知字段：${unknown.join('、')}（可用字段：${allowedKeys.join('、')}）`]
}

/**
 * 任务文档的键序，取自同一份构造逻辑，避免与 {@link questDocument} 漂移。
 *
 * <p>样本必须<b>每个可选字段都填上</b>：{@code questDocument} 会把空的可选字段省掉，
 * 用空样本取键会把 description/category/prerequisites 判成「未知字段」。
 */
function keysOfQuestDocument(): readonly string[] {
  return Object.keys(questDocument({
    id: 'sample',
    name: 'sample',
    description: ['sample'],
    icon: 'PAPER',
    category: 'sample',
    type: 'NORMAL',
    prerequisites: ['sample'],
    objectives: [],
    rewards: [],
    refreshCost: 0,
    enabled: true,
    problems: []
  }))
}
