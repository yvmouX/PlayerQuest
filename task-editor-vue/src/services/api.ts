import axios from 'axios'
import type { 
  Quest, 
  RewardTemplate, 
  PlayerProgress, 
  StatsCompletion, 
  StatsActivity,
  ApiResponse 
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
  update: (id: string, quest: Quest) => api.put<ApiResponse<Quest>>(`/quests/${id}`, quest),
  delete: (id: string) => api.delete(`/quests/${id}`)
}

export const RewardService = {
  getTemplates: () => api.get<ApiResponse<RewardTemplate[]>>('/rewards/templates'),
  saveTemplate: (template: RewardTemplate) => api.post<ApiResponse<RewardTemplate>>('/rewards/templates', template),
  deleteTemplate: (id: string) => api.delete(`/rewards/templates/${id}`)
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
