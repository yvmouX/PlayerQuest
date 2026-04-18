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
  taskType?: TaskSubType
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

export type NodeType = 'start' | 'task' | 'completion'

export type TaskSubType = 'CYCLE' | 'TIMER' | 'FOREVER' | 'LIMIT'

export interface StartNodeData {
  type: 'start'
  name: string
  description?: string
  startCondition?: object
}

export interface TaskNodeData {
  type: 'task'
  id: string
  name: Record<string, string>
  description: Record<string, string>
  taskType: TaskSubType
  objectives: QuestObjective[]
  rewards: QuestReward[]
  resetInterval?: number
  timeLimit?: number
  expiredAction?: string
}

export interface CompletionNodeData {
  type: 'completion'
  name: string
  rewards: QuestReward[]
  callbackMessage?: string
}

export type EditorNodeData = StartNodeData | TaskNodeData | CompletionNodeData
