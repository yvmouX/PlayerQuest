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

/** 任务类型：DAILY 需要刷新费用，NORMAL 不需要。 */
export type QuestType = 'DAILY' | 'NORMAL'

/** schema 里字段的输入类型，决定渲染什么控件（后端 FieldType）。 */
export type FieldType =
  | 'STRING'
  | 'INTEGER'
  | 'DECIMAL'
  | 'BOOLEAN'
  | 'MATERIAL'
  | 'ENTITY'
  /** 方块或实体类型名皆可（后端 FieldType.TARGET） */
  | 'TARGET'
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
  /** 输入类型 */
  type: FieldType
  /** 是否必填（仅用于界面提示，是否强制由后端决定） */
  required: boolean
  /** 默认值 */
  defaultValue: PropertyValue
  /** ENUM 的候选项 */
  options: string[]
  /** 帮助文本 */
  hint: string
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

/** 一个具体目标实例。 */
export interface QuestObjective {
  type: string
  properties: Properties
}

/** 一个具体奖励实例。 */
export interface QuestReward {
  type: string
  properties: Properties
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
  /** DAILY 刷新费用 */
  refreshCost: number
  enabled: boolean
  objectives: QuestObjective[]
  rewards: QuestReward[]
  /** 后端校验问题；非空表示该任务配置有误 */
  problems: string[]
}

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

/** GET /api/langs：语言代码 → YAML 文本。 */
export type LangMap = Record<string, string>

/** PUT /api/langs/{code} 的响应。 */
export interface SaveLangResult {
  ok: boolean
  code: string
}

/** GET /api/stats 的响应。 */
export interface Stats {
  quests: number
  dailyQuests: number
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
  /** Bukkit 枚举名，例如 DIAMOND_ORE */
  id: string
  /** 英文显示名，例如 Diamond Ore；来自服务端自带语言文件，永不依赖它为空的兜底 */
  en: string
  /** 中文显示名，可能为空串 */
  zh: string
  /** 分类（block / item / food）；实体没有这个字段 */
  category?: string
}

/** GET /api/catalog 的响应；一次性返回全部条目，由前端本地搜索。 */
export interface MaterialCatalog {
  materials: CatalogEntry[]
  entities: CatalogEntry[]
  /** 分类展示顺序，界面按它排列分组，不要在前端硬编码 */
  categories: string[]
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

/** GET /api/quests/export 的响应（version 目前恒为 1）。 */
export interface QuestExport {
  version: number
  quests: Quest[]
}

/** POST /api/quests/import 的请求体；replace=true 表示先清空再导入。 */
export interface QuestImportRequest {
  quests: Quest[]
  replace: boolean
}

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
  /** 任务币余额 */
  questCoin: number
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
