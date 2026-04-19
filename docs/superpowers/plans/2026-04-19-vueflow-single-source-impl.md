# VueFlow 单一数据源重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 VueFlow 作为单一数据源，消除状态重复，简化代码

**Architecture:** useQuestEditor 内部使用 VueFlow 的 useNodesState/useEdgesState 管理状态，业务逻辑（验证、规则）在方法中调用，EditorView 直接绑定状态

**Tech Stack:** Vue 3, VueFlow (@vue-flow/core), TypeScript

---

## 文件结构

```
task-editor-vue/src/
├── composables/
│   └── useQuestEditor.ts          # 重写：使用 VueFlow hooks
├── views/
│   └── EditorView.vue             # 简化：移除 computed 转换层
```

---

## 实现步骤

### Task 1: 重写 useQuestEditor 使用 VueFlow hooks

**Files:**
- Modify: `task-editor-vue/src/composables/useQuestEditor.ts`

- [ ] **Step 1: 导入 VueFlow hooks**

在文件顶部添加：
```typescript
import {useNodesState, useEdgesState} from '@vue-flow/core'
```

- [ ] **Step 2: 替换状态定义**

替换：
```typescript
// Before
const nodes = ref<QuestNodeData[]>([])
const edges = ref<{ id: string; source: string; target: string; label?: string }[]>([])
```

为：
```typescript
// After - 使用 VueFlow 原生 hooks
const [nodes, setNodes, onNodesChange] = useNodesState([])
const [edges, setEdges, onEdgesChange] = useEdgesState([])
```

- [ ] **Step 3: 导出原始 hooks**

在 `return` 语句中导出 `onNodesChange` 和 `onEdgesChange`：
```typescript
return {
  nodes,
  edges,
  onNodesChange,  // 新增
  onEdgesChange,  // 新增
  // ... 其他方法
}
```

- [ ] **Step 4: 更新 addNode 方法**

替换 `nodes.value.push(...)` 为：
```typescript
function addNode(nodeType: NodeType, position: { x: number; y: number }) {
  const id = `${nodeType}_${Date.now()}`
  
  // 单例约束验证
  if ((nodeType === 'start' || nodeType === 'task' || nodeType === 'completion') && 
      nodes.value.some(n => n.nodeType === nodeType)) {
    useToast().error(`${nodeType === 'start' ? '开始' : nodeType === 'task' ? '任务' : '完成'}节点已存在，每个流程只能有一个`)
    return
  }
  
  const newNode = {
    id,
    nodeType,
    position,
    data: createDefaultNodeData(nodeType, id)
  }
  setNodes([...nodes.value, newNode])
  return id
}
```

- [ ] **Step 5: 更新 removeNode 方法**

替换 `nodes.value = nodes.value.filter(...)` 为：
```typescript
function removeNode(nodeId: string) {
  setNodes(nodes.value.filter(n => n.id !== nodeId))
  setEdges(edges.value.filter(e => e.source !== nodeId && e.target !== nodeId))
  if (selectedNodeId.value === nodeId) {
    selectedNodeId.value = null
  }
}
```

- [ ] **Step 6: 更新 addEdge 方法**

替换 `edges.value.push(...)` 为：
```typescript
function addEdge(source: string, target: string, label?: string) {
  if (source === target) return

  const sourceNode = nodes.value.find(n => n.id === source)
  const targetNode = nodes.value.find(n => n.id === target)
  if (!sourceNode || !targetNode) return

  // 连接规则验证
  if (!validConnections[sourceNode.nodeType]?.includes(targetNode.nodeType)) {
    useToast().error(`无法连接：${sourceNode.nodeType} 不能连接到 ${targetNode.nodeType}`)
    return
  }

  // 循环检测
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
```

- [ ] **Step 7: 更新 removeEdge 方法**

替换 `edges.value = edges.value.filter(...)` 为：
```typescript
function removeEdge(edgeId: string) {
  setEdges(edges.value.filter(e => e.id !== edgeId))
}
```

- [ ] **Step 8: 更新 loadGraph 方法**

替换 `nodes.value = ...` 和 `edges.value = ...` 为：
```typescript
function loadGraph(questId: string, graph: QuestGraph) {
  currentQuestId.value = questId
  const mappedNodes = graph.nodes.map((n: GraphNode) => ({
    id: n.id,
    nodeType: n.nodeType as NodeType,
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
```

- [ ] **Step 9: 更新 clearEditor 方法**

替换 `nodes.value = []` 和 `edges.value = []` 为：
```typescript
function clearEditor() {
  setNodes([])
  setEdges([])
  currentQuestId.value = null
  selectedNodeId.value = null
}
```

- [ ] **Step 10: 验证构建**

Run: `cd task-editor-vue && npm run build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 11: 提交**

```bash
git add task-editor-vue/src/composables/useQuestEditor.ts
git commit -m "refactor: useQuestEditor now uses VueFlow hooks as single source of truth"
```

---

### Task 2: 简化 EditorView.vue

**Files:**
- Modify: `task-editor-vue/src/views/EditorView.vue`

- [ ] **Step 1: 移除未使用的 computed 转换**

删除 `flowNodes` 和 `flowEdges` computed（约 30 行）

- [ ] **Step 2: 更新 VueFlow 模板绑定**

替换：
```vue
<VueFlow
  v-model:nodes="flowNodes"
  v-model:edges="flowEdges"
```

为：
```vue
<VueFlow
  v-model:nodes="nodes"
  v-model:edges="edges"
```

- [ ] **Step 3: 移除 onNodesChange 和 onEdgesChange 函数**

删除 `handleNodeDragStart` 之前的 `onNodesChange` 和 `onEdgesChange` 函数（约 20 行）

- [ ] **Step 4: 验证构建**

Run: `cd task-editor-vue && npm run build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add task-editor-vue/src/views/EditorView.vue
git commit -m "refactor: EditorView now directly binds to useQuestEditor state"
```

---

### Task 3: 测试所有功能

**验证以下功能正常工作：**
- [ ] 添加 Start/Task/Completion 节点（单例约束应阻止重复添加）
- [ ] 添加 Trigger/Objective/Action 节点
- [ ] 拖拽节点位置后保存/加载，位置应保持
- [ ] 连接节点（应遵守连接规则）
- [ ] 连接形成循环时应报错
- [ ] 删除节点（通过按钮和键盘 Delete 键）
- [ ] 删除边
- [ ] 保存任务后重新加载，图形应正确恢复
- [ ] 切换任务时图形应正确切换

- [ ] **Step 2: 提交**

```bash
git add -A
git commit -m "test: verify all editor functionality works after refactor"
```

---

## 验证清单

- [ ] useQuestEditor 内部使用 useNodesState/useEdgesState
- [ ] nodes 和 edges 直接绑定到 VueFlow v-model
- [ ] 所有业务逻辑（连接规则、循环检测、单例约束）仍然正常工作
- [ ] 节点拖拽位置在保存/加载后保持
- [ ] EditorView 代码简化，flowNodes/flowEdges computed 已删除
- [ ] 构建成功
