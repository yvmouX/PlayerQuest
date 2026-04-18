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

export type NodeType = 'start' | 'task' | 'completion' | 'condition' | 'branch' | 'action'

export type TaskSubType = 'CYCLE' | 'TIMER' | 'FOREVER' | 'LIMIT'

export type ConditionType = 'PERMISSION' | 'HAS_ITEM' | 'KILL_MOB' | 'COLLECT_ITEM' | 'PLAYER_LEVEL' | 'TIME_RANGE' | 'IN_REGION'

export interface ConditionItem {
  conditionType: ConditionType
  params: Record<string, any>
}

export interface ConditionData {
  type: 'condition'
  name: string
  conditions: ConditionItem[]
}

export interface BranchData {
  type: 'branch'
  name: string
  linkedConditionId: string
}

export type ActionType = 'GIVE_ITEM' | 'TAKE_ITEM' | 'GIVE_MONEY' | 'TAKE_MONEY' | 'GIVE_XP' | 'SEND_MESSAGE' | 'BROADCAST' | 'EXECUTE_COMMAND' | 'PLAY_SOUND'

export interface ActionData {
  type: 'action'
  name: string
  actionType: ActionType
  actionParams: Record<string, any>
}

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

export type EditorNodeData = StartNodeData | TaskNodeData | CompletionNodeData | ConditionData | BranchData | ActionData
