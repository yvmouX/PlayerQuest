/**
 * 预设（/api/presets）相关的纯函数。
 *
 * <p>预设是「类型 + 属性值 + 名称」，本身不含任何校验信息。因此有两个地方需要
 * 在这里判断，而不是散落在视图里：
 *
 * <ol>
 *   <li><b>类型是否仍然存在</b>：管理员可能删掉了某个目标类型的代码，此时预设
 *       引用了一个 /api/schema 里查不到的类型。这类预设只标记为无效，
 *       不能影响其它预设的渲染，也不能让页面抛异常。</li>
 *   <li><b>属性按新类型重置</b>：改类型时旧类型的键对新类型没有意义，
 *       必须整体换成新类型的默认值，否则会带着一堆无效字段提交给后端。</li>
 * </ol>
 */
import { PresetApi } from '../services/api'
import type { Preset, PresetKind, PresetMap, Properties, TypeSchema } from '../types'
import { summarizeProperties, withDefaults } from './schema'

/** 一份预设的展示状态：有效性与摘要一次算好，模板直接取用。 */
export interface PresetView {
  preset: Preset
  /** 类型是否存在于 schema */
  valid: boolean
  /** 无效时的原因文案（有效时为空串） */
  invalidReason: string
  /** 类型显示名：有效时用 schema 的 displayName，无效时回退类型 id */
  typeLabel: string
  /** 关键属性摘要，例如「目标方块=STONE · 所需数量=64」 */
  summary: string
}

/** 规范化一份预设：补齐可能缺失的字段，避免后端手写的 JSON 让界面抛异常。 */
export function normalizePreset(raw: unknown): Preset | null {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return null
  }
  const node = raw as Record<string, unknown>
  const type = typeof node.type === 'string' ? node.type.trim() : ''
  if (!type) {
    // 没有 type 的预设无法套用，也无法在界面上编辑类型，直接丢弃
    return null
  }
  const rawProperties = node.properties
  const properties: Properties = rawProperties && typeof rawProperties === 'object' && !Array.isArray(rawProperties)
    ? rawProperties as Properties
    : {}
  return {
    id: typeof node.id === 'string' ? node.id : '',
    name: typeof node.name === 'string' && node.name.trim() ? node.name : type,
    type,
    description: typeof node.description === 'string' ? node.description : '',
    properties
  }
}

/** 把 GET /api/presets 的响应规范成两组可用列表。 */
export function normalizePresetMap(raw: PresetMap | null | undefined): PresetMap {
  const read = (value: unknown): Preset[] =>
    Array.isArray(value)
      ? value.map(normalizePreset).filter((item): item is Preset => item !== null)
      : []
  return {
    objectives: read(raw?.objectives),
    rewards: read(raw?.rewards)
  }
}

/** 按 kind 取出对应的预设列表。 */
export function presetsOf(map: PresetMap, kind: PresetKind): Preset[] {
  return kind === 'objectives' ? map.objectives : map.rewards
}

/** 按 kind 替换对应的预设列表，返回新的 PresetMap（保持响应式更新的不可变风格）。 */
export function withPresets(map: PresetMap, kind: PresetKind, list: Preset[]): PresetMap {
  return kind === 'objectives'
    ? { objectives: list, rewards: map.rewards }
    : { objectives: map.objectives, rewards: list }
}

/** 写入一条预设（同 id 覆盖，id 为空视为追加）。 */
export function upsertPreset(map: PresetMap, kind: PresetKind, preset: Preset): PresetMap {
  const list = presetsOf(map, kind).filter(item => item.id !== preset.id || preset.id === '')
  return withPresets(map, kind, [...list, preset])
}

/** 按 id 删除一条预设。 */
export function removePreset(map: PresetMap, kind: PresetKind, id: string): PresetMap {
  return withPresets(map, kind, presetsOf(map, kind).filter(item => item.id !== id))
}

/**
 * 计算一份预设的展示状态。
 *
 * <p>类型不存在时<b>只</b>影响这一条：摘要回退成按 properties 的键原样拼接，
 * 界面据此置灰并提示，而不是整页报错。
 */
export function presetView(preset: Preset, schemas: Record<string, TypeSchema>): PresetView {
  const schema = schemas[preset.type]
  if (!schema) {
    return {
      preset,
      valid: false,
      invalidReason: `类型「${preset.type}」不存在（该类型可能已被移除）`,
      typeLabel: preset.type,
      summary: summarizeRaw(preset.properties)
    }
  }
  return {
    preset,
    valid: true,
    invalidReason: '',
    typeLabel: schema.displayName || schema.id,
    summary: summarizeProperties(preset.properties, schema) || '使用该类型的全部默认值'
  }
}

/** schema 不可用时的摘要兜底：直接列出键值，至少让人看出预设里存了什么。 */
function summarizeRaw(properties: Properties): string {
  const parts = Object.entries(properties)
    .filter(([, value]) => value !== null && value !== undefined && String(value).trim() !== '')
    .map(([key, value]) => `${key}=${String(value)}`)
  return parts.length ? parts.join(' · ') : '（没有属性值）'
}

/**
 * 预设 → 目标/奖励实例的属性表。
 *
 * <p>用 {@link withDefaults} 而不是直接复制：后端可能给预设新增了字段，
 * 老预设里没有这个键，补上默认值才能立刻可编辑。
 */
export function presetProperties(preset: Preset, schema: TypeSchema | undefined): Properties {
  return withDefaults(schema, preset.properties)
}

/** 生成一个「另存为预设」的默认名称：类型显示名 + 关键属性，尽量不用手打。 */
export function suggestPresetName(schema: TypeSchema | undefined, type: string, properties: Properties): string {
  const summary = summarizeProperties(properties, schema)
  if (summary) {
    return summary
  }
  return schema?.displayName || type || '新预设'
}

/* ------------------------------------------------------------------ *
 * 预设缓存
 * ------------------------------------------------------------------ */

/**
 * 预设列表的小缓存。
 *
 * <p>预设是低频改动的数据，但「添加目标」弹层会被反复打开；缓存避免每次
 * 打开都等一次网络往返。写操作（保存/删除）后由调用方显式失效，
 * 因此不会出现「改了预设但弹层里还是旧的」。
 */
let presetCache: PresetMap | null = null
let presetPromise: Promise<PresetMap | null> | null = null

/** 读取预设，force=true 时强制重新请求。 */
export function loadPresets(force = false): Promise<PresetMap | null> {
  if (force) {
    presetCache = null
    presetPromise = null
  }
  if (!presetPromise) {
    presetPromise = PresetApi.list()
      .then(data => {
        presetCache = normalizePresetMap(data)
        return presetCache
      })
      .catch(() => {
        // 失败后清掉 Promise，下次打开弹层还能重试
        presetPromise = null
        return null
      })
  }
  return presetPromise
}

/** 同步读取已缓存的预设；未加载时为 null。 */
export function peekPresets(): PresetMap | null {
  return presetCache
}

/** 让缓存失效，下次读取会重新请求。写操作后必须调用。 */
export function invalidatePresets(): void {
  presetCache = null
  presetPromise = null
}
