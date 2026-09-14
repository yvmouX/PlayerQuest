/**
 * 与后端 REST 契约一一对应的类型定义。
 *
 * <p>权威来源：{@code core/src/main/java/.../core/web/EditorServer.java} 与
 * {@code QuestJson.java}；字段名与 JSON 保持一致，改名时必须同步后端。
 *
 * <p>模型是「任务 = 多目标 + 多奖励」的扁平结构：目标与奖励都只有
 * {@code type + properties} 两部分，具体字段由 /api/schema 描述，
 * 因此本文件里<b>不存在</b>任何具体目标/奖励类型的字段定义。
 */

/**
 * 任务类型。
 *
 * <p>DAILY / WEEKLY / MONTHLY / CUSTOM 都是**周期任务**：会被抽取、会过期、可消耗货币刷新，
 * 刷新费用与次数上限由 config.yml 里 `periodic.<type>` 那一段决定；NORMAL 是常驻任务。
 */
export type QuestType = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'CUSTOM' | 'NORMAL'

/** 周期任务的类型（顺序即界面上的显示顺序）。 */
export const PERIODIC_TYPES: QuestType[] = ['DAILY', 'WEEKLY', 'MONTHLY', 'CUSTOM']

/** 类型的中文显示名；后端有权威定义（Periods.label），前端只在徽标与筛选里用。 */
export const QUEST_TYPE_LABELS: Record<QuestType, string> = {
  DAILY: '每日',
  WEEKLY: '每周',
  MONTHLY: '每月',
  CUSTOM: '自定义周期',
  NORMAL: '普通'
}

export function isPeriodicType(type: string): boolean {
  return type !== 'NORMAL'
}

/**
 * schema 里字段的输入类型：决定渲染成什么控件（后端 FieldType）。
 *
 * <p>「这个控件里能填什么值」不在这一层，而在 {@link FieldSchema.kinds}——
 * 加一个值域（只有可剪毛的生物）不需要动这里，也不需要动任何组件。
 */
export type FieldType =
  | 'STRING'
  | 'INTEGER'
  | 'DECIMAL'
  | 'BOOLEAN'
  /** 选择器：候选来自素材目录，值域见 {@link FieldSchema.kinds} */
  | 'PICKER'
  | 'ENUM'

/** properties 里允许的取值：JSON 能表达的基础类型。 */
export type PropertyValue = string | number | boolean | null

/** 目标/奖励的属性表，键名由对应 TypeSchema 的 fields 决定。 */
export type Properties = Record<string, PropertyValue>

/** 单个配置字段的描述（后端 ConfigField）。 */
export interface FieldSchema {
  /** 配置键，对应 properties 中的键名 */
  key: string
  /** 显示名 */
  label: string
  /** 控件类型 */
  type: FieldType
  /** 是否必填（仅用于界面提示，是否强制由后端决定） */
  required: boolean
  /** 默认值 */
  defaultValue: PropertyValue
  /** ENUM 的候选项 */
  options: string[]
  /** 帮助文本 */
  hint: string
  /**
   * PICKER 的取值域（后端 `ValueKind` 的小写 id，多个之间是「或」）。
   *
   * <p>选择器只列满足这些值域的候选：`["block"]` 只列方块、`["shearable"]` 只列羊与蘑菇牛。
   * 同一份声明也是服务端校验的依据，因此「选不到」与「会标红」永远一致。
   */
  kinds?: string[]
}

/** 一种目标或奖励类型的描述（后端 ObjectiveType / RewardType）。 */
export interface TypeSchema {
  /** 类型 id，例如 break_block */
  id: string
  /** 显示名，例如 挖掘方块 */
  displayName: string
  /** 该类型的字段列表 */
  fields: FieldSchema[]
  /** 仅奖励类型提供：false 表示软依赖缺失（如未装经济插件） */
  available?: boolean
  /** 仅奖励类型提供：不可用的原因 */
  unavailableReason?: string
}

/** GET /api/schema 的响应。 */
export interface SchemaResponse {
  objectives: Record<string, TypeSchema>
  rewards: Record<string, TypeSchema>
}

/**
 * 一条「目标 / 奖励」配置。
 *
 * <p>引用预设时两样东西同时存在：
 * <ul>
 *   <li>{@code preset} = 引用的预设 id（定义里只写它，类型与字段都由预设提供）；</li>
 *   <li>{@code resolved} = 预设给的 <b>生效值</b>，界面直接显示它。</li>
 * </ul>
 * 这种条目的 {@code properties} 一定是空的：引用不带覆盖项，要单独调值先「展开为独立配置」。
 * 独立配置的条目则相反：{@code properties} 有值、没有 {@code preset}。
 */
export interface QuestObjective {
  type: string
  properties: Properties
  preset?: string | null
  resolved?: Properties | null
}

/** 一个具体奖励实例；字段含义同 {@link QuestObjective}。 */
export interface QuestReward {
  type: string
  properties: Properties
  preset?: string | null
  resolved?: Properties | null
}

/** 任务。列表与详情接口返回的对象都会带上 problems。 */
export interface Quest {
  id: string
  name: string
  /** 描述，一行一条 */
  description: string[]
  /** Bukkit 材质名 */
  icon: string
  category: string
  type: QuestType
  /** 周期任务的刷新费用（NORMAL 无意义） */
  refreshCost: number
  enabled: boolean
  objectives: QuestObjective[]
  rewards: QuestReward[]
  /** 后端校验问题；非空表示该任务配置有误 */
  problems: string[]
  /**
   * 定义来源：`database` = 存在数据库里（游戏内与编辑器可改）；
   * `file` = 来自 `quests/` 下的 YAML（只读，要改就去改文件）。
   *
   * <p>后端没给这个字段时按 `database` 处理（旧版后端）。
   */
  source?: QuestSource
}

/** 定义来源，见 {@link Quest.source} 与 {@link Preset.source}。 */
export type QuestSource = 'database' | 'file'

/** POST /api/quests 的响应。 */
export interface SaveQuestResult {
  ok: boolean
  id: string
  problems: string[]
}

/** DELETE /api/quests/{id} 的响应。 */
export interface DeleteQuestResult {
  ok: boolean
  id: string
}

/** GET /api/stats 的响应。 */
export interface Stats {
  quests: number
  /** 周期任务总数（四种周期合起来） */
  periodicQuests: number
  /** 每种类型的任务数，键是 QuestType 的名字 */
  questsByType: Record<string, number>
  objectives: number
  rewards: number
  players: number
  /** 存储类型描述，例如 SQLite / MySQL */
  storage: string
  categories: string[]
}

/** POST /api/reload 的响应。 */
export interface ReloadResult {
  ok: boolean
  quests: number
}

/* ------------------------------------------------------------------ *
 * 素材目录（图标 / 材质 / 实体选择器）
 * ------------------------------------------------------------------ */

/**
 * 目录条目。
 *
 * <p>{@code zh} 是中文译名，<b>可能为空串</b>：服务端只带英文语言文件，中文由后端
 * 另行获取（读本地文件或下载），拿不到时全部为空。此时界面必须回退显示 {@code en}，
 * 搜索也必须仍然能按 id 或英文名命中——否则会出现「搜到了却看不见」。
 */
export interface CatalogEntry {
  /** Bukkit 枚举名，例如 DIAMOND_ORE（自定义内容则是带前缀的 id，如 craftengine:default:bench） */
  id: string
  /** 英文显示名，例如 Diamond Ore；来自服务端自带语言文件，永不依赖它为空的兜底 */
  en: string
  /** 中文显示名，可能为空串 */
  zh: string
  /** 分类（block / item / food / fish / entity / enchantment） */
  category?: string
  /**
   * 值域：这条候选项属于哪几类值（后端 `ValueKind` 的小写 id，如 `["block","placeable"]`）。
   *
   * <p>选择器按字段声明的值域过滤，因此「挖掘方块」不会列出苹果、
   * 「剪切」不会列出猪。后端没给时按所在列表粗判（见 catalog.ts 的 fallbackKinds）。
   */
  kinds?: string[]
  /**
   * 来源：`minecraft` / `mythicmobs` / `itemsadder` / `craftengine` / `customfishing`。
   *
   * <p>后端没给这个字段时按 `minecraft` 处理（旧版后端），否则按插件筛选会整片空白。
   */
  source?: string
}

/** 目录里出现过的来源（界面用来做「按插件筛选」的标签）。 */
export interface CatalogSource {
  id: string
  label: string
}

/** GET /api/catalog 的响应；一次性返回全部条目，由前端本地搜索。 */
export interface MaterialCatalog {
  materials: CatalogEntry[]
  entities: CatalogEntry[]
  /**
   * CustomFishing 的战利品 id（「鱼 id」字段用）。
   *
   * <p>它与材质、实体互不相通，因此单独一栏：把鱼 id 填进方块目标的 `target` 只会永远
   * 命中不了，而那正是最难排查的一类错配。没装 CustomFishing 时是空数组。
   */
  fish: CatalogEntry[]
  /** 原版附魔（「附魔」字段用）：id 是 Bukkit 附魔名，如 SHARPNESS */
  enchantments: CatalogEntry[]
  /** 分类展示顺序，界面按它排列分组，不要在前端硬编码 */
  categories: string[]
  /** 本次真的有内容的来源，顺序由后端定；界面按它渲染筛选标签 */
  sources: CatalogSource[]
  serverVersion: string
  /** 后端是否拿到了中文译名；为 false 时可在界面上说明「当前显示英文名」 */
  hasChinese?: boolean
}

/* ------------------------------------------------------------------ *
 * 目标 / 奖励预设
 * ------------------------------------------------------------------ */

/** 预设的两类归属，对应接口路径与请求体分组。 */
export type PresetKind = 'objectives' | 'rewards'

/** 一个目标/奖励预设：本质就是「类型 + 一组属性值 + 便于识别的名称」。 */
export interface Preset {
  /** 省略或为空时由后端生成 8 位随机 id */
  id: string
  name: string
  /** 必填：引用 /api/schema 里的一个类型；不存在时界面标记为无效但不报错 */
  type: string
  description: string
  properties: Properties
  /** 定义来源：`file` = 来自 `presets/` 下的 YAML（只读） */
  source?: QuestSource
}

/** GET /api/presets 的响应。 */
export interface PresetMap {
  objectives: Preset[]
  rewards: Preset[]
}

/** POST /api/presets/{kind} 的响应。 */
export interface SavePresetResult {
  ok: boolean
  /** 保存后的预设（含后端补齐的 id 与 name） */
  preset: Preset
}

/** DELETE /api/presets/{kind}/{id} 的响应；ok=false 表示没删到。 */
export interface DeletePresetResult {
  ok: boolean
  id: string
}

/* ------------------------------------------------------------------ *
 * 导入 / 导出
 * ------------------------------------------------------------------ */

/** POST /api/quests/import 的响应；skipped 是未导入的原因列表。 */
export interface QuestImportResult {
  ok: boolean
  imported: number
  skipped: string[]
  /** 导入结束后服务端任务总数 */
  total: number
}

/* ------------------------------------------------------------------ *
 * 玩家进度（纯只读）
 * ------------------------------------------------------------------ */

/** GET /api/players 的列表项。 */
export interface PlayerSummary {
  uuid: string
  name: string
  /** 玩家当前是否在线 */
  online: boolean
  /** 该玩家的任务记录条数 */
  quests: number
}

/** 玩家某一目标槽位的进度。 */
export interface PlayerObjectiveProgress {
  /** 目标在任务 objectives 数组中的下标，从 0 开始 */
  index: number
  /** 目标类型 id，显示名由 /api/schema 决定 */
  type: string
  current: number
  required: number
}

/** 玩家的一条任务记录。 */
export interface PlayerQuestProgress {
  questId: string
  /** 已剥离颜色标签的任务名；任务被删除时为空串 */
  questName: string
  type: QuestType
  /** 后端状态枚举名，界面按原样展示并映射颜色 */
  status: string
  /** 接取时间；可能为 null */
  assignedAt: number | null
  /** 过期时间；可能为 null 表示不过期 */
  expiresAt: number | null
  /** 完成度百分比（0-100），后端已取整 */
  percent: number
  objectives: PlayerObjectiveProgress[]
}

/** GET /api/players/{uuid} 的响应。 */
export interface PlayerDetail {
  uuid: string
  name: string
  progress: PlayerQuestProgress[]
}

/* ------------------------------------------------------------------ *
 * 通用表格
 * ------------------------------------------------------------------ */

/** 通用数据表的列描述。 */
export interface TableColumn {
  /** 列标识；排序时回传给父组件 */
  key: string
  /** 表头文案 */
  label: string
  /** 是否可点击排序 */
  sortable?: boolean
  /** 对齐方式 */
  align?: 'left' | 'right' | 'center'
  /** 列宽（含单位的 CSS 值），不传则由内容决定 */
  width?: string
  /** 表头单元格的 title 提示 */
  title?: string
}

/** 排序方向：升序 / 降序。 */
export type SortDirection = 'asc' | 'desc'
