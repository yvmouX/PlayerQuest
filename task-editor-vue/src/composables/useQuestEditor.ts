import {computed, ref} from 'vue'
import {useToast} from './useToast'
import type {
  ActionData,
  BranchData,
  CompletionNodeData,
  ConditionData,
  CounterData,
  EditorNodeData,
  GraphNode,
  NodeConnection,
  NodeType,
  ObjectiveData,
  Quest,
  QuestGraph,
  StartNodeData,
  StateData,
  SubtaskData,
  TaskNodeData,
  TimerData,
  TriggerData
} from '../types'

export interface QuestNodeData {
  id: string
  nodeType: NodeType
  position: { x: number; y: number }
  data: EditorNodeData
}

const nodes = ref<QuestNodeData[]>([])
const edges = ref<{ id: string; source: string; target: string; label?: string }[]>([])
const currentQuestId = ref<string | null>(null)

const validConnections: Record<string, string[]> = {
  start: ['trigger', 'task'],
  trigger: ['task'],
  task: ['objective', 'completion'],
  objective: ['action', 'completion'],
  action: ['action', 'completion'],
  completion: ['action']
}
export const editorNodes = nodes
export const editorEdges = edges

function createDefaultNodeData(nodeType: NodeType, id: string): EditorNodeData {
  switch (nodeType) {
    case 'start':
      return { type: 'start', description: '' } as StartNodeData
    case 'trigger':
      return { type: 'trigger', conditionType: 'quest_complete', conditionConfig: {} } as TriggerData
    case 'task':
      return {
        type: 'task',
        id,
        name: '',
        description: '',
        taskType: 'FOREVER'
      } as TaskNodeData
    case 'objective':
      return { type: 'objective', name: '', templateId: '', customConfig: {} } as ObjectiveData
    case 'action':
      return { type: 'action', name: '', templateId: '', customConfig: {} } as ActionData
    case 'completion':
      return { type: 'completion' } as CompletionNodeData
    default:
      return { type: 'start' } as StartNodeData
  }
}

export function useQuestEditor() {
  const selectedNodeId = ref<string | null>(null)

  const selectedNode = computed(() => {
    const node = nodes.value.find(n => n.id === selectedNodeId.value)
    if (!node) return null
    return node.data
  })

  function addNode(nodeType: NodeType, position: { x: number; y: number }) {
    const id = `${nodeType}_${Date.now()}`
    nodes.value.push({
      id,
      nodeType,
      position,
      data: createDefaultNodeData(nodeType, id)
    })
    return id
  }

  function removeNode(nodeId: string) {
    nodes.value = nodes.value.filter(n => n.id !== nodeId)
    edges.value = edges.value.filter(e => e.source !== nodeId && e.target !== nodeId)
    if (selectedNodeId.value === nodeId) {
      selectedNodeId.value = null
    }
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
      edges.value.filter(e => e.source === current).forEach(e => stack.push(e.target))
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

  function clearEditor() {
    nodes.value = []
    edges.value = []
    currentQuestId.value = null
    selectedNodeId.value = null
  }

  function loadGraph(questId: string, graph: QuestGraph) {
    currentQuestId.value = questId
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

  function exportGraph(): QuestGraph | null {
    if (!currentQuestId.value) return null
    return {
      id: currentQuestId.value,
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

  function getCurrentQuestGraph(): { questId: string; graph: QuestGraph } | null {
    const graph = exportGraph()
    if (!graph) return null
    return {
      questId: currentQuestId.value!,
      graph
    }
  }

  return {
    nodes,
    edges,
    currentQuestId,
    selectedNode,
    selectedNodeId,
    addNode,
    removeNode,
    updateNode,
    addEdge,
    removeEdge,
    selectNode,
    clearEditor,
    loadGraph,
    exportGraph,
    getCurrentQuestGraph
  }
}