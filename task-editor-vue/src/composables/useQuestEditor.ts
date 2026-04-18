import {computed, ref} from 'vue'
import type {Quest, EditorNodeData, StartNodeData, TaskNodeData, CompletionNodeData, ConditionData, BranchData, ActionData, EventData, CounterData, TimerData, StateData, SubtaskData, NodeType, QuestGraph, GraphNode, NodeConnection} from '../types'
import {useToast} from './useToast'
import {GraphService} from '../services/api'

export interface QuestNodeData {
  id: string
  nodeType: NodeType
  position: { x: number; y: number }
  data: EditorNodeData
}

const nodes = ref<QuestNodeData[]>([])
const edges = ref<{ id: string; source: string; target: string; label?: string }[]>([])
const currentGraphId = ref<string | null>(null)
export const editorNodes = nodes

function createDefaultNodeData(nodeType: NodeType, id: string): EditorNodeData {
  switch (nodeType) {
    case 'start':
      return { type: 'start', description: '' } as StartNodeData
    case 'task':
      return {
        type: 'task',
        id,
        name: '',
        description: '',
        taskType: 'FOREVER',
        objectives: [],
        rewards: []
      } as TaskNodeData
    case 'completion':
      return { type: 'completion', rewards: [] } as CompletionNodeData
    case 'condition':
      return { type: 'condition', name: '', conditions: [] } as ConditionData
    case 'branch':
      return { type: 'branch', name: '', linkedConditionId: '' } as BranchData
    case 'action':
      return { type: 'action', name: '', actionType: 'GIVE_ITEM', actionParams: {} } as ActionData
    case 'event':
      return { type: 'event', name: '', eventTypes: [] } as EventData
    case 'counter':
      return { type: 'counter', name: '', resetOn: 'NONE' } as CounterData
    case 'timer':
      return { type: 'timer', name: '', timerType: 'DELAY' } as TimerData
    case 'state':
      return { type: 'state', name: '', operation: 'COMPLETE_TASK' } as StateData
    case 'subtask':
      return { type: 'subtask', name: '' } as SubtaskData
    default:
      return { type: 'start', description: '' } as StartNodeData
  }
}

export function useQuestEditor() {
  const selectedNodeId = ref<string | null>(null)

  const selectedNode = computed(() => {
    const node = nodes.value.find(n => n.id === selectedNodeId.value)
    if (!node) return null
    return node.data
  })

  function addNode(questOrNodeType: Quest | string, position: { x: number; y: number }, nodeType?: NodeType) {
    if (typeof questOrNodeType === 'string') {
      const id = `${questOrNodeType}_${Date.now()}`
      const type = questOrNodeType as NodeType
      nodes.value.push({
        id,
        nodeType: type,
        position,
        data: createDefaultNodeData(type, id)
      })
    } else {
      const quest = questOrNodeType
      const type = nodeType || 'task'
      const taskData: TaskNodeData = {
        type: 'task',
        id: quest.id,
        name: quest.name,
        description: quest.description,
        taskType: quest.taskType || 'FOREVER',
        objectives: quest.objectives,
        rewards: quest.rewards,
        resetInterval: quest.resetInterval,
        timeLimit: quest.timeLimit,
        expiredAction: quest.expiredAction
      }
      nodes.value.push({
        id: quest.id,
        nodeType: type,
        position,
        data: taskData
      })
    }
  }

  function removeNode(nodeId: string) {
    nodes.value = nodes.value.filter(n => n.id !== nodeId)
    edges.value = edges.value.filter(e => e.source !== nodeId && e.target !== nodeId)
  }

  function updateNode(nodeId: string, data: Partial<EditorNodeData>) {
    const node = nodes.value.find(n => n.id === nodeId)
    if (node) {
      node.data = { ...node.data, ...data } as EditorNodeData
    }
  }

  function addEdge(source: string, target: string, label?: string) {
    if (source === target) return

    const sourceNode = nodes.value.find(n => n.id === source)
    const targetNode = nodes.value.find(n => n.id === target)

    if (!sourceNode || !targetNode) return

    const sourceType = sourceNode.nodeType
    const targetType = targetNode.nodeType

    const validConnections: Record<string, string[]> = {
      start: ['task', 'condition', 'event'],
      task: ['task', 'completion', 'action', 'timer'],
      completion: ['action'],
      condition: ['branch'],
      branch: ['task', 'completion'],
      action: ['task', 'completion'],
      event: ['task'],
      counter: ['task'],
      timer: ['task'],
      state: ['task', 'completion']
    }

    if (!validConnections[sourceType]?.includes(targetType)) {
      useToast().error(`无法连接：${sourceType} 不能连接到 ${targetType}`)
      return
    }

    const visited = new Set<string>()
    const stack = [source]
    while (stack.length > 0) {
      const current = stack.pop()!
      if (current === target) continue
      if (visited.has(current)) continue
      visited.add(current)
      edges.value
        .filter(e => e.source === current)
        .forEach(e => stack.push(e.target))
    }

    if (visited.has(target)) {
      useToast().error('无法创建连接：会导致循环')
      return
    }

    const id = `${source}-${target}`
    if (edges.value.some(e => e.id === id)) return
    edges.value.push({ id, source, target, label })
  }

  function removeEdge(edgeId: string) {
    edges.value = edges.value.filter(e => e.id !== edgeId)
  }

  function selectNode(nodeId: string | null) {
    selectedNodeId.value = nodeId
  }

  function loadQuests(quests: Quest[], graphEdges: NodeConnection[] = []) {
    nodes.value = quests.map((quest, index) => {
      const type = 'task'
      const taskData: TaskNodeData = {
        type: 'task',
        id: quest.id,
        name: quest.name,
        description: quest.description,
        taskType: quest.taskType || 'FOREVER',
        objectives: quest.objectives,
        rewards: quest.rewards,
        resetInterval: quest.resetInterval,
        timeLimit: quest.timeLimit,
        expiredAction: quest.expiredAction
      }
      return {
        id: quest.id,
        nodeType: type,
        position: {
          x: 100 + (index % 4) * 250,
          y: 100 + Math.floor(index / 4) * 150
        },
        data: taskData
      }
    })
    edges.value = graphEdges.map(e => ({ id: e.id, source: e.sourceId, target: e.targetId, label: e.label }))
  }

  function exportGraph(): QuestGraph {
    return {
      id: currentGraphId.value || 'default',
      name: 'Quest Graph',
      nodes: nodes.value.map(n => ({
        id: n.id,
        nodeType: n.nodeType,
        x: n.position.x,
        y: n.position.y,
        data: n.data
      })),
      edges: edges.value.map(e => ({
        id: e.id,
        sourceId: e.source,
        targetId: e.target,
        label: e.label
      }))
    }
  }

  function loadGraph(graph: QuestGraph) {
    currentGraphId.value = graph.id
    nodes.value = graph.nodes.map((n: GraphNode) => ({
      id: n.id,
      nodeType: n.nodeType as NodeType,
      position: { x: n.x, y: n.y },
      data: n.data as EditorNodeData
    }))
    edges.value = graph.edges.map((e: NodeConnection) => ({
      id: e.id,
      source: e.sourceId,
      target: e.targetId,
      label: e.label
    }))
  }

  async function saveGraph() {
    try {
      const graph = exportGraph()
      await GraphService.save(graph)
      useToast().success('图保存成功')
    } catch (error) {
      useToast().error('保存图失败')
      console.error('Failed to save graph:', error)
    }
  }

  return {
    nodes,
    edges,
    selectedNode,
    selectedNodeId,
    addNode,
    removeNode,
    updateNode,
    addEdge,
    removeEdge,
    selectNode,
    loadQuests,
    loadGraph,
    exportGraph,
    saveGraph
  }
}