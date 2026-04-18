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

export type NodeType = 'start' | 'task' | 'completion' | 'condition' | 'branch' | 'action' | 'event' | 'counter' | 'timer' | 'state' | 'subtask'

export type TaskSubType = 'CYCLE' | 'TIMER' | 'FOREVER' | 'LIMIT'

export type EventType = 
  | 'LOGIN' | 'LOGOUT' | 'CHAT' | 'COMMAND' | 'JUMP' | 'SNEAK' | 'SPRINT' | 'DROP_ITEM' | 'PICKUP_ITEM'
  | 'PLAYER_KILL' | 'ENTITY_KILL' | 'PLAYER_DEATH' | 'PVP_KILL'
  | 'BLOCK_BREAK' | 'BLOCK_PLACE' | 'BLOCK_INTERACT'
  | 'ITEM_CRAFT' | 'ITEM_USE' | 'ITEM_CONSUME'
  | 'PLAYER_MOVE' | 'PLAYER_TELEPORT' | 'ENTER_REGION' | 'LEAVE_REGION'
  | 'ENTITY_DAMAGE' | 'ENTITY_DEATH' | 'ENTITY_SPAWN'
  | 'PLAYER_LEVEL_UP' | 'PLAYER_RESPAWN' | 'VILLAGER_TRADE' | 'PLAYER_BOUNT'

export type CounterResetType = 'NONE' | 'TASK_COMPLETE' | 'DAILY' | 'MANUAL'

export type TimerType = 'DELAY' | 'COOLDOWN' | 'INTERVAL'

export type StateOperation = 'COMPLETE_TASK' | 'FAIL_TASK' | 'RESET_TASK' | 'SET_PLAYER_STATE'

export interface EventData {
  type: 'event'
  name: string
  eventTypes: EventType[]
}

export interface CounterData {
  type: 'counter'
  name: string
  resetOn: CounterResetType
}

export interface TimerData {
  type: 'timer'
  name: string
  timerType: TimerType
  delaySeconds?: number
  cooldownSeconds?: number
  intervalSeconds?: number
  repeatCount?: number
}

export interface StateData {
  type: 'state'
  name: string
  operation: StateOperation
}

export interface SubtaskData {
  type: 'subtask'
  name: string
}

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
  rewards: QuestReward[]
  callbackMessage?: string
}

export type EditorNodeData = StartNodeData | TaskNodeData | CompletionNodeData | ConditionData | BranchData | ActionData | EventData | CounterData | TimerData | StateData | SubtaskData

export interface NodeConnection {
  id: string
  sourceId: string
  targetId: string
  label?: string
}

export interface GraphNode {
  id: string
  nodeType: string
  x: number
  y: number
  data: EditorNodeData
}

export interface QuestGraph {
  id: string
  name: string
  nodes: GraphNode[]
  edges: NodeConnection[]
}
