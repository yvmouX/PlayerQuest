import axios from 'axios'
import type {ApiResponse, PlayerProgress, Quest, RewardTemplate, StatsActivity, StatsCompletion} from '../types'

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

export const RewardService = {
  getTemplates: () => api.get<ApiResponse<RewardTemplate[]>>('/rewards/templates'),
  saveTemplate: (template: RewardTemplate) => {
    const typeMap: Record<string, string> = {
      item: 'ITEM',
      xp: 'EXP',
      money: 'MONEY',
      command: 'COMMAND'
    }
    const amount = parseInt(String(template.value), 10)
    const backendData = {
      type: typeMap[template.type] || template.type,
      content: String(template.name),
      amount: isNaN(amount) ? 0 : amount
    }
    if (template.id) {
      backendData.id = template.id
    }
    return api.post('/rewards/templates', backendData)
  },
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
