import {computed, ref} from 'vue'
import type {Quest} from '../types'

export interface QuestNodeData {
  id: string
  quest: Quest | null
  position: { x: number; y: number }
  nodeType: 'start' | 'task' | 'completion' | 'condition' | 'branch' | 'action'
}

export function useQuestEditor() {
  const nodes = ref<QuestNodeData[]>([])
  const edges = ref<{ id: string; source: string; target: string; label?: string }[]>([])
  const selectedNodeId = ref<string | null>(null)

  const selectedNode = computed(() => 
    nodes.value.find(n => n.id === selectedNodeId.value)
  )

  function addNode(questOrNodeType: Quest | string, position: { x: number; y: number }, nodeType?: 'start' | 'task' | 'completion' | 'condition' | 'branch' | 'action') {
    if (typeof questOrNodeType === 'string') {
      nodes.value.push({
        id: `${questOrNodeType}_${Date.now()}`,
        quest: null,
        position,
        nodeType: questOrNodeType
      })
    } else {
      nodes.value.push({
        id: questOrNodeType.id,
        quest: questOrNodeType,
        position,
        nodeType: nodeType || 'task'
      })
    }
  }

  function removeNode(nodeId: string) {
    nodes.value = nodes.value.filter(n => n.id !== nodeId)
    edges.value = edges.value.filter(e => e.source !== nodeId && e.target !== nodeId)
  }

  function updateNode(nodeId: string, quest: Partial<Quest>) {
    const node = nodes.value.find(n => n.id === nodeId)
    if (node) {
      node.quest = { ...node.quest, ...quest }
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
      start: ['task', 'condition'],
      task: ['task', 'completion', 'action'],
      completion: ['action'],
      condition: ['branch'],
      branch: ['task', 'completion'],
      action: ['task', 'completion']
    }

    if (!validConnections[sourceType]?.includes(targetType)) {
      console.warn(`Cannot connect: ${sourceType} cannot connect to ${targetType}`)
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
      console.warn('Cannot create edge: would create a cycle')
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

  function loadQuests(quests: Quest[]) {
    nodes.value = quests.map((quest, index) => ({
      id: quest.id,
      quest,
      position: {
        x: 100 + (index % 4) * 250,
        y: 100 + Math.floor(index / 4) * 150
      }
    }))
  }

  function exportData() {
    return {
      nodes: nodes.value.map(n => n.quest),
      edges: edges.value
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
    exportData
  }
}
