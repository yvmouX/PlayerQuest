export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}

export interface Quest {
  id: string
  name: Record<string, string>
  description: Record<string, string>
  type: 'single' | 'multi' | 'series'
  objectives: QuestObjective[]
  rewards: QuestReward[]
  createdAt: number
  updatedAt: number
}

export interface QuestObjective {
  id: string
  type: string
  target: string
  count: number
  finished: boolean
}

export interface QuestReward {
  id: string
  type: 'item' | 'xp' | 'money' | 'command'
  value: string | number
  meta?: any
}

export interface RewardTemplate {
  id: string
  name: string
  type: 'item' | 'xp' | 'money' | 'command'
  value: string | number
  meta?: any
}

export interface PlayerProgress {
  playerUuid: string
  questId: string
  status: 'not_started' | 'in_progress' | 'completed' | 'claimed'
  progress: Record<string, number>
}

export interface StatsCompletion {
  name: string
  value: number
}

export interface StatsActivity {
  day: string
  users: number
}
