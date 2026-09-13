/**
 * 后端接口封装。
 *
 * <p>所有请求都走 /api 前缀（开发环境由 vite 代理到插件端口，
 * 生产环境由插件自身托管静态资源，同源直连）。
 *
 * <p>响应拦截器直接把 AxiosResponse 换成业务数据，因此这里包一层
 * {@link request} 让调用方拿到带类型的返回值。
 */
import axios, { AxiosError } from 'axios'
import type { AxiosRequestConfig } from 'axios'
import type {
  DeletePresetResult,
  DeleteQuestResult,
  LangMap,
  MaterialCatalog,
  PlayerDetail,
  PlayerSummary,
  Preset,
  PresetKind,
  PresetMap,
  Properties,
  Quest,
  QuestExport,
  QuestImportRequest,
  QuestImportResult,
  ReloadResult,
  SaveLangResult,
  SavePresetResult,
  SaveQuestResult,
  SchemaResponse,
  Stats
} from '../types'

/** 访问令牌的本地存储键；对应插件 config.yml 的 editor.token。 */
const TOKEN_KEY = 'ptx.editor.token'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

/** 读取访问令牌：未配置令牌的服务器留空即可。 */
export function getEditorToken(): string {
  try {
    return window.localStorage.getItem(TOKEN_KEY) ?? ''
  } catch {
    return ''
  }
}

/** 保存访问令牌。 */
export function setEditorToken(token: string): void {
  try {
    if (token) {
      window.localStorage.setItem(TOKEN_KEY, token)
    } else {
      window.localStorage.removeItem(TOKEN_KEY)
    }
  } catch {
    // 隐私模式下 localStorage 可能不可用；忽略即可，刷新后需重新填写
  }
}

http.interceptors.request.use(config => {
  const token = getEditorToken()
  if (token) {
    config.headers.set('X-Editor-Token', token)
  }
  return config
})

http.interceptors.response.use(
  response => response.data,
  error => Promise.reject(error)
)

/** 发起请求并直接返回响应体（拦截器已解包）。 */
async function request<T>(config: AxiosRequestConfig): Promise<T> {
  return http.request<T, T>(config)
}

/**
 * 把任意异常转成可以展示给用户的文案。
 *
 * <p>后端错误体有几种形态：自定义的 {@code {error}}、Javalin 默认的
 * {@code {title,status,detail}}、以及非 JSON 的纯文本，逐一兜底。
 */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<unknown>
    const data = axiosError.response?.data
    if (typeof data === 'string' && data.trim()) {
      return data.trim()
    }
    if (data && typeof data === 'object') {
      const record = data as Record<string, unknown>
      for (const key of ['error', 'message', 'detail', 'title']) {
        const value = record[key]
        if (typeof value === 'string' && value.trim()) {
          return value === 'Internal Server Error'
            ? `后端内部错误（HTTP ${axiosError.response?.status ?? 500}），具体原因见服务端日志`
            : value
        }
      }
    }
    if (axiosError.response) {
      if (axiosError.response.status === 401) {
        return '访问被拒绝：请在左下角填写正确的访问令牌（editor.token）'
      }
      return `请求失败（HTTP ${axiosError.response.status}）`
    }
    return `无法连接后端：${axiosError.message}`
  }
  if (error instanceof Error) {
    return error.message
  }
  return String(error)
}

/**
 * 补齐可能缺失的数组字段。
 *
 * <p>契约里这些字段总是存在；这里只是兜底，避免旧版后端返回的对象
 * 让界面直接抛异常——修复方式应该是升级后端，而不是让编辑器白屏。
 */
export function normalizeQuest(quest: Quest): Quest {
  return {
    ...quest,
    description: quest.description ?? [],
    prerequisites: quest.prerequisites ?? [],
    objectives: quest.objectives ?? [],
    rewards: quest.rewards ?? [],
    problems: quest.problems ?? []
  }
}

/**
 * 导入来源是不可信的本地文件，这里逐条做形状校验。
 *
 * <p>返回值 null 表示这条记录连 `objectives/rewards` 都凑不出来，
 * 交给后端只会得到一个奇怪的任务，不如直接跳过并告诉用户。
 */
export function normalizeImportedQuest(raw: unknown, fallbackId: string): Quest | null {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return null
  }
  const node = raw as Record<string, unknown>
  const id = typeof node.id === 'string' && node.id.trim() ? node.id.trim() : fallbackId
  if (!id) {
    return null
  }
  const instances = (value: unknown): { type: string; properties: Properties }[] => {
    if (!Array.isArray(value)) {
      return []
    }
    return value
      .filter(item => !!item && typeof item === 'object')
      .map(item => {
        const entry = item as Record<string, unknown>
        const properties = entry.properties
        return {
          type: typeof entry.type === 'string' ? entry.type : '',
          properties: properties && typeof properties === 'object' && !Array.isArray(properties)
            ? properties as Properties
            : {}
        }
      })
      .filter(item => item.type !== '')
  }
  const description = Array.isArray(node.description)
    ? node.description.filter((line): line is string => typeof line === 'string')
    : typeof node.description === 'string' && node.description
      ? [node.description]
      : []
  const type = node.type === 'DAILY' ? 'DAILY' : typeof node.type === 'string' && node.type ? node.type : 'NORMAL'
  const refreshCost = Number(node.refreshCost)
  const prerequisites = Array.isArray(node.prerequisites)
    ? node.prerequisites
        .filter((item): item is string => typeof item === 'string')
        .map(item => item.trim())
        .filter(item => item !== '')
    : []
  return {
    id,
    name: typeof node.name === 'string' && node.name ? node.name : id,
    description,
    icon: typeof node.icon === 'string' && node.icon ? node.icon : 'PAPER',
    category: typeof node.category === 'string' ? node.category : '',
    type: type === 'DAILY' ? 'DAILY' : 'NORMAL',
    refreshCost: Number.isFinite(refreshCost) && refreshCost >= 0 ? refreshCost : 0,
    enabled: node.enabled !== false,
    prerequisites,
    objectives: instances(node.objectives),
    rewards: instances(node.rewards),
    problems: []
  }
}

/** 任务增删改查。 */
export const QuestApi = {
  list: async () => (await request<Quest[]>({ url: '/quests', method: 'get' })).map(normalizeQuest),
  get: async (id: string) => normalizeQuest(
    await request<Quest>({ url: `/quests/${encodeURIComponent(id)}`, method: 'get' })
  ),
  save: (quest: Quest) => request<SaveQuestResult>({ url: '/quests', method: 'post', data: quest }),
  remove: (id: string) => request<DeleteQuestResult>({
    url: `/quests/${encodeURIComponent(id)}`,
    method: 'delete'
  }),
  /** 导出全部任务；返回体直接用于下载 JSON 文件。 */
  exportAll: () => request<QuestExport>({ url: '/quests/export', method: 'get' }),
  /** 导入任务；replace=true 时后端会先清空现有任务。 */
  importQuests: (payload: QuestImportRequest) =>
    request<QuestImportResult>({ url: '/quests/import', method: 'post', data: payload })
}

/** 目标与奖励类型定义——表单完全由它驱动。 */
export const SchemaApi = {
  get: () => request<SchemaResponse>({ url: '/schema', method: 'get' })
}

/**
 * 图标 / 材质 / 实体清单。
 *
 * <p>响应有几百 KB，因此这里只提供「原样取回」，单例缓存由
 * {@code utils/catalog.ts} 负责——缓存放在工具层，选择器组件才不用关心谁先请求。
 */
export const CatalogApi = {
  get: () => request<MaterialCatalog>({ url: '/catalog', method: 'get' })
}

/**
 * 目标 / 奖励预设。
 *
 * <p>预设只是编辑器的便利设施，后端不做校验（只要求 type 非空），
 * 因此引用已删除类型的预设必须由界面自己标记，见 {@code utils/presets.ts}。
 */
export const PresetApi = {
  list: () => request<PresetMap>({ url: '/presets', method: 'get' }),
  /** 新建或覆盖（id 相同即覆盖）；id 留空时后端生成随机 id。 */
  save: (kind: PresetKind, preset: Preset) => request<SavePresetResult>({
    url: `/presets/${kind}`,
    method: 'post',
    data: preset
  }),
  remove: (kind: PresetKind, id: string) => request<DeletePresetResult>({
    url: `/presets/${kind}/${encodeURIComponent(id)}`,
    method: 'delete'
  })
}

/** 语言文件读写。 */
export const LangApi = {
  list: () => request<LangMap>({ url: '/langs', method: 'get' }),
  save: (code: string, content: string) => request<SaveLangResult>({
    url: `/langs/${encodeURIComponent(code)}`,
    method: 'put',
    data: { content }
  })
}

/** 统计与重载。 */
export const StatsApi = {
  get: () => request<Stats>({ url: '/stats', method: 'get' }),
  reload: () => request<ReloadResult>({ url: '/reload', method: 'post' })
}

/** 玩家进度——纯只读，没有任何修改玩家数据的接口。 */
export const PlayerApi = {
  list: () => request<PlayerSummary[]>({ url: '/players', method: 'get' }),
  get: (uuid: string) => request<PlayerDetail>({
    url: `/players/${encodeURIComponent(uuid)}`,
    method: 'get'
  })
}

/** 判断异常是否由访问令牌错误（HTTP 401）导致，用于给出针对性的引导。 */
export function isUnauthorized(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 401
}
