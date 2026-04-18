import {computed, ref} from 'vue'
import type {Quest} from '../types'

export interface QuestNodeData {
  id: string
  quest: Quest
  position: { x: number; y: number }
  nodeType: 'start' | 'task' | 'completion'  // 必填
}

export function useQuestEditor() {
  const nodes = ref<QuestNodeData[]>([])
  const edges = ref<{ id: string; source: string; target: string; label?: string }[]>([])
  const selectedNodeId = ref<string | null>(null)

  const selectedNode = computed(() => 
    nodes.value.find(n => n.id === selectedNodeId.value)
  )

  function addNode(quest: Quest, position: { x: number; y: number }) {
    nodes.value.push({
      id: quest.id,
      quest,
      position
    })
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
    // 禁止自身连接
    if (source === target) return
    
    // 根据节点类型验证连线规则
    const sourceNode = nodes.value.find(n => n.id === source)
    const targetNode = nodes.value.find(n => n.id === target)
    
    if (!sourceNode || !targetNode) return
    
    // 获取节点类型
    const sourceType = sourceNode.nodeType || 'task'  // 默认为 task（向后兼容）
    const targetType = targetNode.nodeType || 'task'
    
    // 验证连线规则:
    // Start → Task
    // Task → Task / Completion
    // Completion → (nothing)
    if (sourceType === 'start' && targetType !== 'task') {
      console.warn('Cannot connect: Start can only connect to Task')
      return
    }
    if (sourceType === 'task' && targetType === 'start') {
      console.warn('Cannot connect: Task cannot connect to Start')
      return
    }
    if (sourceType === 'completion') {
      console.warn('Cannot connect: Completion cannot be a source')
      return
    }
    
    // 禁止环路 - 简单的 DFS 检查
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
