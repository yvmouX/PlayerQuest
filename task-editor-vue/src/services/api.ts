import axios from 'axios'
import type {
    ActionTemplate,
    ApiResponse,
    ObjectiveTemplate,
    PlayerProgress,
    Quest,
    StatsActivity,
    StatsCompletion
} from '../types'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

api.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export const QuestService = {
  getAll: () => api.get<ApiResponse<Quest[]>>('/quests'),
  getById: (id: string) => api.get<ApiResponse<Quest>>(`/quests/${id}`),
  create: (quest: Quest) => api.post<ApiResponse<Quest>>('/quests', quest),
  batchCreate: (quests: Quest[]) => api.post<ApiResponse<{count: number}>>('/quests/batch', quests),
  update: (id: string, quest: Quest) => api.put<ApiResponse<Quest>>(`/quests/${id}`, quest),
  delete: (id: string) => api.delete(`/quests/${id}`)
}

export const ObjectiveService = {
  getTemplates: () => api.get<ApiResponse<ObjectiveTemplate[]>>('/objectives/templates'),
  saveTemplate: (template: ObjectiveTemplate) => api.post<ApiResponse<ObjectiveTemplate>>('/objectives/templates', template),
  deleteTemplate: (id: string) => api.delete(`/objectives/templates/${id}`)
}

export const ActionService = {
  getTemplates: () => api.get<ApiResponse<ActionTemplate[]>>('/actions/templates'),
  saveTemplate: (template: ActionTemplate) => api.post<ApiResponse<ActionTemplate>>('/actions/templates', template),
  deleteTemplate: (id: string) => api.delete(`/actions/templates/${id}`)
}

export const PlayerService = {
  getProgress: (params?: { search?: string; status?: string; limit?: number; offset?: number }) =>
    api.get<ApiResponse<{ total: number; data: PlayerProgress[] }>>('/players/progress', { params })
}

export const StatsService = {
  getCompletion: () => api.get<ApiResponse<StatsCompletion[]>>('/stats/completion'),
  getActivity: (range: '7d' | '30d' = '7d') =>
    api.get<ApiResponse<StatsActivity[]>>('/stats/activity', { params: { range } })
}