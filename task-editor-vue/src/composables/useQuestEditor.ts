import {computed, ref} from 'vue'
import type {Quest} from '../types'

export interface QuestNodeData {
  id: string
  quest: Quest
  position: { x: number; y: number }
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
