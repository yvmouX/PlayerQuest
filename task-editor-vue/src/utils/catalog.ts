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
import type { CatalogEntry, CatalogSource, MaterialCatalog } from '../types'

/**
 * 值域（后端 {@code ValueKind} 的小写 id）：字段能填哪一类值。
 *
 * <p>这里只列前端需要认识的那几个——过滤时只做包含判断，因此新加值域不必改前端；
 * 需要界面文案（分类栏、图标等）时才在这里补一条。
 */
export const KIND_BLOCK = 'block'
export const KIND_ITEM = 'item'
export const KIND_FOOD = 'food'
export const KIND_ENTITY = 'entity'
export const KIND_FISH = 'fish'
export const KIND_ENCHANTMENT = 'enchantment'

/** 分类筛选值：'ALL' 或 catalog.categories 里的某个分类；实体、鱼各占一栏。 */
export const CATEGORY_ALL = 'ALL'
export const CATEGORY_ENTITY = 'entity'
export const CATEGORY_FISH = 'fish'
export const CATEGORY_ENCHANTMENT = 'enchantment'

/** 来源筛选值：'ALL' 或 catalog.sources 里的某个来源 id。 */
export const SOURCE_ALL = 'ALL'

/** 原版来源 id；后端没给 source 时的兜底。 */
export const SOURCE_MINECRAFT = 'minecraft'

/**
 * 来源显示名的兜底表。
 *
 * <p>正常情况下来自后端（`catalog.sources`），这里只为「旧版后端没有 sources 字段」
 * 兜一份，避免筛选标签显示成裸 id。
 */
const SOURCE_LABELS: Record<string, string> = {
  [SOURCE_MINECRAFT]: '原版',
  mythicmobs: 'MythicMobs',
  itemsadder: 'ItemsAdder',
  craftengine: 'CraftEngine',
  customfishing: 'CustomFishing'
}

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
 * <p>契约里这些字段总是存在，但缺字段会让选择器直接抛异常白屏，
 * 这里补空数组比让管理员面对一个坏掉的页面划算。
 *
 * <p>{@code sources} 缺失时（旧版后端）从条目里现推一份：按插件筛选是纯前端行为，
 * 后端没给元数据也不该让这一排标签整个消失。
 */
export function normalizeCatalog(raw: MaterialCatalog | null | undefined): MaterialCatalog {
  const materials = Array.isArray(raw?.materials) ? raw!.materials : []
  const entities = Array.isArray(raw?.entities) ? raw!.entities : []
  const fish = Array.isArray(raw?.fish) ? raw!.fish : []
  const enchantments = Array.isArray(raw?.enchantments) ? raw!.enchantments : []
  const categories = Array.isArray(raw?.categories) && raw!.categories.length
    ? raw!.categories
    // 后端没给顺序时给一份可用的默认顺序，保证分类栏仍然出现
    : ['block', 'item', 'food']
  const sources = Array.isArray(raw?.sources) && raw!.sources.length
    ? raw!.sources.filter(isSource)
    : sourcesFromEntries(materials.concat(entities, fish, enchantments))
  return {
    materials: materials.filter(isEntry),
    entities: entities.filter(isEntry),
    fish: fish.filter(isEntry),
    enchantments: enchantments.filter(isEntry),
    categories,
    sources,
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

function isSource(value: unknown): value is CatalogSource {
  return !!value && typeof value === 'object'
    && typeof (value as CatalogSource).id === 'string'
    && typeof (value as CatalogSource).label === 'string'
}

/** 从条目里推出出现过的来源（旧版后端的兜底）。保持首次出现的顺序。 */
function sourcesFromEntries(entries: CatalogEntry[]): CatalogSource[] {
  const seen = new Map<string, CatalogSource>()
  for (const entry of entries) {
    const id = sourceOf(entry)
    if (!seen.has(id)) {
      seen.set(id, { id, label: SOURCE_LABELS[id] ?? id })
    }
  }
  return [...seen.values()]
}

/** 条目来源；后端没给时按原版处理（旧版目录里只有原版与带前缀的自定义内容）。 */
export function sourceOf(entry: CatalogEntry): string {
  const source = (entry.source ?? '').trim()
  if (source) {
    return source
  }
  // 带前缀的自定义内容（itemsadder:xxx / craftengine:xxx）即便后端没标来源，也能从前缀认出来
  const colon = entry.id.indexOf(':')
  return colon > 0 ? entry.id.slice(0, colon) : SOURCE_MINECRAFT
}

/** 来源显示名；未知来源原样返回 id。 */
export function sourceLabel(source: string, catalog?: MaterialCatalog | null): string {
  const known = catalog?.sources.find(item => item.id === source)
  if (known) {
    return known.label
  }
  return SOURCE_LABELS[source] ?? source
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

/** 按来源（插件）筛选；source 为 SOURCE_ALL 时原样返回。 */
export function filterBySource(entries: CatalogEntry[], source: string): CatalogEntry[] {
  if (!source || source === SOURCE_ALL) {
    return [...entries]
  }
  return entries.filter(entry => sourceOf(entry) === source)
}

/**
 * 组装选择器要显示的列表：先按分类与来源收窄，再搜索，再截断到 {@link MAX_VISIBLE_ITEMS}。
 *
 * <p>返回 truncated 让界面提示「还有多少项未显示」，而不是静默吞掉结果——
 * 搜索 `_ORE` 这类宽泛关键词时命中几百条是正常的，管理员需要知道要细化搜索。
 */
export function visibleEntries(
  entries: CatalogEntry[],
  keyword: string,
  category: string,
  source: string = SOURCE_ALL,
  limit: number = MAX_VISIBLE_ITEMS
): { items: CatalogEntry[]; total: number; truncated: boolean } {
  const matched = searchEntries(filterBySource(filterByCategory(entries, category), source), keyword)
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

/**
 * 条目是否满足字段声明的值域。
 *
 * <p>语义是「或」：条目只要属于其中任意一个值域就能被选（后端 {@code ValueKind} 同义）。
 * 后端没给条目的 {@code kinds} 时（旧版后端）退回原来的粗粒度判断：材质列表里的都算
 * 方块/物品，实体列表里的都算实体——宁可放宽，也不要让选择器空掉。
 */
export function matchesKinds(entry: CatalogEntry, kinds: readonly string[]): boolean {
  if (!kinds.length) {
    return true
  }
  const entryKinds = entry.kinds?.length ? entry.kinds : fallbackKinds(entry)
  return kinds.some(kind => entryKinds.includes(kind))
}

/** 旧版后端（条目没有 kinds）时的兜底值域：按它所在的列表猜。 */
function fallbackKinds(entry: CatalogEntry): string[] {
  switch (entry.category) {
    case CATEGORY_ENTITY:
      return [KIND_ENTITY]
    case CATEGORY_FISH:
      return [KIND_FISH]
    case CATEGORY_ENCHANTMENT:
      return [KIND_ENCHANTMENT]
    case 'food':
      return [KIND_ITEM, KIND_FOOD]
    case 'block':
      return [KIND_BLOCK, KIND_ITEM]
    default:
      return entry.category ? [KIND_ITEM] : [KIND_ENTITY]
  }
}

/** 按字段声明的值域取出候选条目（实体的分类补成「实体」，好让分类栏与徽标一致）。 */
export function entriesForKinds(catalog: MaterialCatalog | null, kinds: readonly string[]): CatalogEntry[] {
  if (!catalog) {
    return []
  }
  const pool = catalog.materials
    .concat(catalog.entities.map(entry => ({ ...entry, category: entry.category ?? CATEGORY_ENTITY })))
    .concat(catalog.fish.map(entry => ({ ...entry, category: entry.category ?? CATEGORY_FISH })))
    .concat(catalog.enchantments.map(entry => ({ ...entry, category: entry.category ?? CATEGORY_ENCHANTMENT })))
  return pool.filter(entry => matchesKinds(entry, kinds))
}

/**
 * 选择器顶部的分类标签。
 *
 * <p>顺序来自 {@code catalog.categories}（不硬编码），实体、鱼、附魔各作为单独一栏，
 * 且只在当前值域真的含它们时出现——在方块字段里给一个「鱼」分类栏毫无意义。
 */
export function categoryTabs(catalog: MaterialCatalog | null, kinds: readonly string[]): { value: string; label: string }[] {
  const tabs: { value: string; label: string }[] = [{ value: CATEGORY_ALL, label: '全部' }]
  if (!catalog) {
    return tabs
  }
  const pool = entriesForKinds(catalog, kinds)
  const present = new Set(pool.map(entry => entry.category ?? CATEGORY_ENTITY))
  for (const category of catalog.categories) {
    if (category === CATEGORY_FISH || category === CATEGORY_ENCHANTMENT) {
      continue
    }
    if (present.has(category)) {
      tabs.push({ value: category, label: categoryLabel(category) })
    }
  }
  if (present.has(CATEGORY_ENTITY)) {
    tabs.push({ value: CATEGORY_ENTITY, label: categoryLabel(CATEGORY_ENTITY) })
  }
  if (present.has(CATEGORY_FISH)) {
    tabs.push({ value: CATEGORY_FISH, label: categoryLabel(CATEGORY_FISH) })
  }
  if (present.has(CATEGORY_ENCHANTMENT)) {
    tabs.push({ value: CATEGORY_ENCHANTMENT, label: categoryLabel(CATEGORY_ENCHANTMENT) })
  }
  return tabs
}

/**
 * 选择器顶部的来源（插件）标签。
 *
 * <p>只列出**当前值域里真的有条目**的来源：在实体字段里塞一个 ItemsAdder 标签，
 * 点进去只会是空列表。只有一个来源时返回空数组——没什么可筛的，
 * 那一排标签只会占地方（原版方块字段正是这种情况）。
 */
export function sourceTabs(catalog: MaterialCatalog | null, kinds: readonly string[]): { value: string; label: string }[] {
  const entries = entriesForKinds(catalog, kinds)
  if (!entries.length) {
    return []
  }
  const seen = new Set(entries.map(sourceOf))
  if (seen.size < 2) {
    return []
  }
  const tabs: { value: string; label: string }[] = [{ value: SOURCE_ALL, label: '全部来源' }]
  // 顺序跟随后端给的 sources（原版在前的固定顺序），再补后端没提到的
  const ordered = (catalog?.sources ?? []).map(source => source.id).filter(id => seen.has(id))
  for (const id of seen) {
    if (!ordered.includes(id)) {
      ordered.push(id)
    }
  }
  for (const id of ordered) {
    tabs.push({ value: id, label: sourceLabel(id, catalog) })
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
    case CATEGORY_FISH:
      return '鱼'
    case CATEGORY_ENCHANTMENT:
      return '附魔'
    default:
      return category
  }
}
