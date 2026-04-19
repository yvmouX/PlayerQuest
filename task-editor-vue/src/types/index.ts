export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}

export interface Quest {
  id: string
  name: string
  description: string
  type: 'single' | 'multi' | 'series'
  objectives: QuestObjective[]
  createdAt: number
  updatedAt: number
  taskType?: TaskSubType
  graph?: QuestGraph
}

export interface QuestObjective {
  id: string
  type: string
  target: string
  count: number
  finished: boolean
}

export interface ObjectiveTemplate {
  id: string
  name: string
  type: 'item' | 'xp' | 'money' | 'command'
  value: string | number
  meta?: any
}

export interface ObjectiveTemplate {
  id: string
  name: string
  description: string
  type: 'kill_mob' | 'collect_item' | 'break_block' | 'talk_to_npc' | 'reach_location' | 'custom'
  defaultConfig: Record<string, any>
}

export interface ActionTemplate {
  id: string
  name: string
  description: string
  type: 'give_item' | 'execute_command' | 'send_message' | 'play_effect' | 'sound' | 'give_xp' | 'custom'
  defaultConfig: Record<string, any>
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

export type NodeType = 'start' | 'trigger' | 'task' | 'objective' | 'action' | 'completion'

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

export interface TriggerData {
  type: 'trigger'
  name: string
  conditionType: string
  conditionConfig: Record<string, any>
}

export interface ObjectiveData {
  type: 'objective'
  name: string
  templateId?: string
  customConfig?: {
    type: string
    target?: string
    amount?: number
    location?: { x: number, y: number, z: number, world: string }
    [key: string]: any
  }
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
  templateId?: string
  customConfig?: {
    type: string
    item?: string
    amount?: number
    command?: string
    message?: string
    effect?: string
    sound?: string
    volume?: number
    pitch?: number
    xp?: number
    [key: string]: any
  }
}

export interface StartNodeData {
  type: 'start'
  description?: string
  startCondition?: object
}

export interface TaskNodeData {
  type: 'task'
  id: string
  name: string
  description: string
  taskType: TaskSubType
  resetInterval?: number
  timeLimit?: number
  expiredAction?: string
}

export interface CompletionNodeData {
  type: 'completion'
  name: string
  callbackMessage?: string
}

export type EditorNodeData = StartNodeData | TriggerData | TaskNodeData | ObjectiveData | ActionData | CompletionNodeData

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
