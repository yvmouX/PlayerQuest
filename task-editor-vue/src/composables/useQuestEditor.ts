import {computed, ref, toRef} from 'vue'
import {useVueFlow} from '@vue-flow/core'
import {useToast} from './useToast'
import type {
  ActionData,
  CompletionNodeData,
  EditorNodeData,
  GraphNode,
  NodeConnection,
  NodeType,
  ObjectiveData,
  QuestGraph,
  StartNodeData,
  TaskNodeData,
  TriggerData
} from '../types'

export interface QuestNodeData {
  id: string
  type: NodeType
  position: { x: number; y: number }
  data: EditorNodeData
}

export interface QuestEdgeData {
  id: string
  source: string
  target: string
  label?: string
}

const vueFlowStore = useVueFlow()

const nodes = vueFlowStore.nodes as unknown as import('vue').Ref<QuestNodeData[]>
const edges = vueFlowStore.edges as unknown as import('vue').Ref<QuestEdgeData[]>
const setNodes = (newNodes: QuestNodeData[]) => {
  const graphNodes = newNodes.map(n => ({
    id: n.id,
    type: n.type,
    position: n.position,
    data: n.data,
    draggable: true,
    selectable: true
  }))
  vueFlowStore.setNodes(graphNodes)
}
const setEdges = (newEdges: QuestEdgeData[]) => {
  const graphEdges = newEdges.map(e => ({
    id: e.id,
    source: e.source,
    target: e.target,
    label: e.label,
    type: 'smoothstep'
  }))
  vueFlowStore.setEdges(graphEdges)
}
const onNodesChange = vueFlowStore.onNodesChange
const onEdgesChange = vueFlowStore.onEdgesChange
const currentQuestId = ref<string | null>(null)

const NODE_TYPE_NAMES = { start: '开始', task: '任务', completion: '完成' } as const

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
    if ((nodeType === 'start' || nodeType === 'task' || nodeType === 'completion') && 
        nodes.value.some(n => n.type === nodeType)) {
      const typeName = nodeType === 'start' ? NODE_TYPE_NAMES.start : nodeType === 'task' ? NODE_TYPE_NAMES.task : NODE_TYPE_NAMES.completion
      useToast().error(`${typeName}节点已存在，每个流程只能有一个`)
      return
    }
    const id = crypto.randomUUID()
    const newNode = {
      id,
      type: nodeType,
      position,
      data: createDefaultNodeData(nodeType, id)
    }
    setNodes([...nodes.value, newNode])
    return id
  }

  function removeNode(nodeId: string) {
    setNodes(nodes.value.filter(n => n.id !== nodeId))
    setEdges(edges.value.filter(e => e.source !== nodeId && e.target !== nodeId))
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

    const sourceType = sourceNode.type
    const targetType = targetNode.type

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
    setEdges([...edges.value, { id, source, target, label }])
  }

  function removeEdge(edgeId: string) {
    setEdges(edges.value.filter(e => e.id !== edgeId))
  }

  function selectNode(nodeId: string | null) {
    selectedNodeId.value = nodeId
  }

  function clearEditor() {
    setNodes([])
    setEdges([])
    currentQuestId.value = null
    selectedNodeId.value = null
  }

  function loadGraph(questId: string, graph: QuestGraph) {
    currentQuestId.value = questId
    const mappedNodes = graph.nodes.map((n: GraphNode) => ({
      id: n.id,
      type: n.nodeType as NodeType,
      position: { x: n.x, y: n.y },
      data: n.data as EditorNodeData
    }))
    const mappedEdges = graph.edges.map((e: NodeConnection) => ({
      id: e.id,
      source: e.sourceId,
      target: e.targetId,
      label: e.label
    }))
    setNodes(mappedNodes)
    setEdges(mappedEdges)
  }

  function exportGraph(): QuestGraph | null {
    if (!currentQuestId.value) return null
    return {
      id: currentQuestId.value,
      name: 'Quest Graph',
      nodes: nodes.value.map(n => ({
        id: n.id,
        nodeType: n.type,
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
    onNodesChange,
    onEdgesChange,
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
