/**
 * 素材目录（/api/catalog）的缓存与检索。
 *
 * <p>目录一次返回 1000+ 条材质与全部实体，响应有几百 KB，因此这里做两件事：
 *
 * <ol>
 *   <li><b>单例缓存</b>：无论多少个选择器同时挂载，只会发一次请求。缓存的是
 *       Promise 而不是结果，因此并发调用也能自然合并，不会出现「两个组件各自
 *       请求一次」的竞态。</li>
 *   <li><b>纯函数检索</b>：搜索、筛选、取值解析全部是不依赖 Vue 的纯函数，
 *       既方便在 Node 里单独验证，也让组件只管渲染。</li>
 * </ol>
 *
 * <h2>中文名可能为空</h2>
 * 后端的中文名是精选映射（约 300 项），绝大多数条目 {@code zh} 为空串。
 * 因此：显示一律走 {@link entryLabel}（中文优先、回退英文），搜索一律用
 * {@link matchesKeyword} —— 它在 id / en / zh 三个字段上同时匹配，
 * <b>绝不会</b>因为 zh 为空就把条目漏掉。
 */
import { CatalogApi } from '../services/api'
import type { CatalogEntry, MaterialCatalog } from '../types'

/** 选择器要展示的条目范围。 */
export type CatalogScope = 'material' | 'entity' | 'both'

/** 分类筛选值：'ALL' 或 catalog.categories 里的某个分类；实体用 'entity'。 */
export const CATEGORY_ALL = 'ALL'
export const CATEGORY_ENTITY = 'entity'

/** 单次最多渲染的条目数：1000+ 个 DOM 节点会让低配机器明显卡顿。 */
export const MAX_VISIBLE_ITEMS = 200

/** 与 axios 默认返回值对齐：请求失败时返回 null 而不是抛异常。 */
let catalogPromise: Promise<MaterialCatalog | null> | null = null

/** 已经拿到的目录数据（同步可读，供渲染时兜底）。 */
let cached: MaterialCatalog | null = null

/**
 * 加载目录（单例）。
 *
 * <p>失败时把 Promise 清掉，下一次调用会重新请求——否则一次网络抖动就会让
 * 选择器在整个会话里永久不可用。
 */
export function loadCatalog(): Promise<MaterialCatalog | null> {
  if (!catalogPromise) {
    catalogPromise = CatalogApi.get()
      .then(data => {
        cached = normalizeCatalog(data)
        return cached
      })
      .catch(() => {
        catalogPromise = null
        return null
      })
  }
  return catalogPromise
}

/** 同步读取已缓存的目录；未加载完成时为 null。 */
export function peekCatalog(): MaterialCatalog | null {
  return cached
}

/**
 * 兜底一份形状正确的目录。
 *
 * <p>契约里四个字段总是存在，但缺字段会让选择器直接抛异常白屏，
 * 这里补空数组比让管理员面对一个坏掉的页面划算。
 */
export function normalizeCatalog(raw: MaterialCatalog | null | undefined): MaterialCatalog {
  const materials = Array.isArray(raw?.materials) ? raw!.materials : []
  const entities = Array.isArray(raw?.entities) ? raw!.entities : []
  const categories = Array.isArray(raw?.categories) && raw!.categories.length
    ? raw!.categories
    // 后端没给顺序时给一份可用的默认顺序，保证分类栏仍然出现
    : ['block', 'item', 'food']
  return {
    materials: materials.filter(isEntry),
    entities: entities.filter(isEntry),
    categories,
    serverVersion: typeof raw?.serverVersion === 'string' ? raw!.serverVersion : '',
    // 后端未声明时按「有条目就算有中文」推断，避免旧后端让界面误报「只有英文」
    hasChinese: typeof raw?.hasChinese === 'boolean'
      ? raw!.hasChinese
      : materials.some(entry => !!(entry as CatalogEntry).zh)
  }
}

function isEntry(value: unknown): value is CatalogEntry {
  return !!value && typeof value === 'object' && typeof (value as CatalogEntry).id === 'string'
}

/* ------------------------------------------------------------------ *
 * 搜索（纯函数：不依赖 Vue，可在 Node 里直接验证）
 * ------------------------------------------------------------------ */

/**
 * 关键词是否命中某条目。
 *
 * <p>规则：大小写不敏感，在<b>枚举 id、英文名、中文名</b>三个字段上做子串匹配。
 * 中文名缺失（空串）不影响 id / 英文名上的匹配，因此不会漏项。
 */
export function matchesKeyword(entry: CatalogEntry, keyword: string): boolean {
  const needle = keyword.trim().toLowerCase()
  if (!needle) {
    return true
  }
  return searchFields(entry).some(field => field.includes(needle))
}

/** 参与匹配的字段：顺序即优先级（id 最精确，中文名最后）。 */
function searchFields(entry: CatalogEntry): string[] {
  const id = (entry.id ?? '').toLowerCase()
  const en = (entry.en ?? '').toLowerCase()
  const zh = (entry.zh ?? '').toLowerCase()
  return zh ? [id, en, zh] : [id, en]
}

/**
 * 相关性打分，越小越靠前。
 *
 * <p>枚举 id 完全相等排最前（管理员精确输入 DEEPSLATE_DIAMOND_ORE 时一眼可见），
 * 其次是 id 前缀、英文名前缀、中文名前缀，最后是任意位置命中。
 * 中文使用者的输入多为中文，因此中文前缀排在「英文名包含」之前。
 */
export function relevance(entry: CatalogEntry, keyword: string): number {
  const needle = keyword.trim().toLowerCase()
  if (!needle) {
    return 0
  }
  const id = (entry.id ?? '').toLowerCase()
  const en = (entry.en ?? '').toLowerCase()
  const zh = (entry.zh ?? '').toLowerCase()
  if (id === needle) {
    return 0
  }
  if (id.startsWith(needle)) {
    return 1
  }
  if (en.startsWith(needle)) {
    return 2
  }
  if (zh && zh.startsWith(needle)) {
    return 3
  }
  if (id.includes(needle)) {
    return 4
  }
  if (en.includes(needle)) {
    return 5
  }
  return 6
}

/**
 * 在条目数组里搜索，返回新数组（不修改入参）。
 *
 * <p>无关键词时只按 id 排序，保证「全部」列表的顺序稳定、可预期。
 */
export function searchEntries(entries: CatalogEntry[], keyword: string): CatalogEntry[] {
  const needle = keyword.trim()
  if (!needle) {
    return [...entries]
  }
  const hits = entries.filter(entry => matchesKeyword(entry, needle))
  return hits.sort((left, right) => {
    const diff = relevance(left, needle) - relevance(right, needle)
    return diff !== 0 ? diff : left.id.localeCompare(right.id)
  })
}

/** 按分类筛选；category 为 CATEGORY_ALL 时原样返回。 */
export function filterByCategory(entries: CatalogEntry[], category: string): CatalogEntry[] {
  if (!category || category === CATEGORY_ALL) {
    return [...entries]
  }
  return entries.filter(entry => (entry.category ?? CATEGORY_ENTITY) === category)
}

/**
 * 组装选择器要显示的列表：先按分类收窄，再搜索，再截断到 {@link MAX_VISIBLE_ITEMS}。
 *
 * <p>返回 truncated 让界面提示「还有多少项未显示」，而不是静默吞掉结果——
 * 搜索 `_ORE` 这类宽泛关键词时命中几百条是正常的，管理员需要知道要细化搜索。
 */
export function visibleEntries(
  entries: CatalogEntry[],
  keyword: string,
  category: string,
  limit: number = MAX_VISIBLE_ITEMS
): { items: CatalogEntry[]; total: number; truncated: boolean } {
  const matched = searchEntries(filterByCategory(entries, category), keyword)
  const max = limit > 0 ? limit : matched.length
  return {
    items: matched.slice(0, max),
    total: matched.length,
    truncated: matched.length > max
  }
}

/* ------------------------------------------------------------------ *
 * 展示与取值解析
 * ------------------------------------------------------------------ */

/** 显示名：中文优先，无中文回退英文，都没有才回退枚举 id。 */
export function entryLabel(entry: CatalogEntry): string {
  const zh = (entry.zh ?? '').trim()
  if (zh) {
    return zh
  }
  const en = (entry.en ?? '').trim()
  return en || entry.id
}

/** 显示名与英文名不同时补一行英文，方便对照游戏内名称。 */
export function entrySubLabel(entry: CatalogEntry): string {
  const label = entryLabel(entry)
  const en = (entry.en ?? '').trim()
  if (!en || en === label) {
    return ''
  }
  return en
}

/** 一条可展示的取值：raw 用于提交，label 用于人看。 */
export interface CatalogValue {
  raw: string
  label: string
  /** 列表中找不到该枚举名（版本差异、手工填错） */
  unknown: boolean
}

/**
 * 把 MATERIAL 字段的原始文本拆成取值列表。
 *
 * <p>MATERIAL 支持逗号分隔多值（{@code "DIAMOND_ORE,DEEPSLATE_DIAMOND_ORE"}），
 * 因此解析时按英文/中文逗号切分并去空格；空段直接丢弃，
 * 让「留空表示任意」的字段不会显示出一个空 chip。
 */
export function splitMaterialValue(value: string): string[] {
  return value
    .split(/[,，]/)
    .map(part => part.trim())
    .filter(part => part.length > 0)
}

/** 把取值列表拼回 MATERIAL 字段的文本形式。 */
export function joinMaterialValue(values: string[]): string {
  return values.filter(value => value.length > 0).join(',')
}

/**
 * 在一份目录条目里解析单个枚举名。
 *
 * <p>解析不出来是正常情况：管理员可以手打任意字符串（例如 {@code *} 表示任意），
 * 因此这里返回 {@code unknown: true} 交给界面做弱提示，而不是丢弃该值。
 */
export function resolveValue(raw: string, entries: CatalogEntry[]): CatalogValue {
  const text = raw.trim()
  const found = entries.find(entry => entry.id.toLowerCase() === text.toLowerCase())
  if (!found) {
    return { raw: text, label: text, unknown: text.length > 0 }
  }
  return { raw: found.id, label: entryLabel(found), unknown: false }
}

/** 批量解析（MATERIAL 的多个取值）。 */
export function resolveValueText(value: string, entries: CatalogEntry[]): CatalogValue[] {
  return splitMaterialValue(value).map(raw => resolveValue(raw, entries))
}

/** 一个字段类型对应的选择范围。 */
export function scopeForFieldType(type: string): CatalogScope {
  if (type === 'ENTITY') {
    return 'entity'
  }
  if (type === 'TARGET') {
    return 'both'
  }
  return 'material'
}

/** 按范围取出条目：TARGET 需要同时列出方块材质与实体。 */
export function entriesForScope(catalog: MaterialCatalog | null, scope: CatalogScope): CatalogEntry[] {
  if (!catalog) {
    return []
  }
  const materials = catalog.materials
  const entities = catalog.entities
  if (scope === 'material') {
    return materials
  }
  if (scope === 'entity') {
    return entities.map(entry => ({ ...entry, category: entry.category ?? CATEGORY_ENTITY }))
  }
  return materials.concat(entities.map(entry => ({ ...entry, category: entry.category ?? CATEGORY_ENTITY })))
}

/**
 * 选择器顶部的分类标签。
 *
 * <p>顺序来自 {@code catalog.categories}（不硬编码），实体作为单独一栏，
 * 且只在选择范围包含实体时出现。
 */
export function categoryTabs(catalog: MaterialCatalog | null, scope: CatalogScope): { value: string; label: string }[] {
  const tabs: { value: string; label: string }[] = [{ value: CATEGORY_ALL, label: '全部' }]
  if (!catalog) {
    return tabs
  }
  if (scope !== 'entity') {
    for (const category of catalog.categories) {
      tabs.push({ value: category, label: categoryLabel(category) })
    }
  }
  if (scope !== 'material') {
    tabs.push({ value: CATEGORY_ENTITY, label: '实体' })
  }
  return tabs
}

/** 分类的显示文案；后端只给英文标识，这里给中文标签，未知分类原样显示。 */
export function categoryLabel(category: string): string {
  switch (category) {
    case 'block':
      return '方块'
    case 'item':
      return '物品'
    case 'food':
      return '食物'
    case CATEGORY_ENTITY:
      return '实体'
    default:
      return category
  }
}
