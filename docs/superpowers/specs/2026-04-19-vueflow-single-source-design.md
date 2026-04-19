# VueFlow 单一数据源重构设计

**日期：** 2026-04-19  
**状态：** 已批准  
**类型：** 技术重构

## 背景

当前 `useQuestEditor` 和 VueFlow 各自维护一份节点/边的状态，通过 computed getter/setter 和 `onNodesChange`/`onEdgesChange` 回调手动同步。这种双重状态模型导致了：

- 代码复杂（flowNodes/flowEdges computed 转换层）
- 职责分散（位置变化在 setter 处理，删除在 onNodesChange 处理）
- 同步不一致风险
- 难以维护

## 目标

使用 VueFlow 原生的 `useNodesState()` / `useEdgesState()` 作为单一数据源，消除状态重复，简化代码。

## 设计

### 新架构

```
VueFlow ←→ useQuestEditor(nodes/edges) ←→ 业务逻辑
                    ↑
            VueFlow useNodesState/useEdgesState
```

### 核心变化

1. **删除转换层**
   - 删除 `flowNodes` / `flowEdges` computed
   - `nodes` 和 `edges` 直接暴露给 VueFlow 的 `v-model`

2. **修改 useQuestEditor**
   - 内部使用 `useNodesState()` 和 `useEdgesState()` 管理状态
   - `onNodesChange` / `onEdgesChange` 在内部处理
   - 验证逻辑（连接规则、循环检测、单例约束）封装在方法中

3. **EditorView 简化**
   - 移除手动状态同步逻辑
   - 直接使用 useQuestEditor 的状态和方法

### 新的 useQuestEditor API

```typescript
// 状态（直接由 VueFlow 管理）
const [nodes, setNodes, onNodesChange] = useNodesState([])
const [edges, setEdges, onEdgesChange] = useEdgesState([])

// 方法（验证 + 状态更新）
function addNode(nodeType: NodeType, position: { x: number; y: number }) {
  // 验证单例约束
  if (isSingletonNode(nodeType) && nodes.value.some(n => n.nodeType === nodeType)) {
    useToast().error(`${nodeType}节点已存在，每个流程只能有一个`)
    return
  }
  // 添加节点
  setNodes([...nodes.value, { id, nodeType, position, data: createDefaultNodeData(nodeType, id) }])
}

function addEdge(source: string, target: string) {
  // 验证连接规则
  if (!validConnections[sourceNode.nodeType]?.includes(targetNode.nodeType)) {
    useToast().error(`无法连接：${sourceNode.nodeType} 不能连接到 ${targetNode.nodeType}`)
    return
  }
  // 验证循环
  if (wouldCreateCycle(source, target)) {
    useToast().error('无法创建连接：会导致循环')
    return
  }
  // 添加边
  setEdges([...edges.value, { id: `${source}-${target}`, source, target }])
}

function removeNode(nodeId: string) {
  // 删除节点及其相关边
  setNodes(nodes.value.filter(n => n.id !== nodeId))
  setEdges(edges.value.filter(e => e.source !== nodeId && e.target !== nodeId))
}
```

### EditorView 简化

**Before:**
```typescript
// 60+ 行复杂代码
const flowNodes = computed({
  get: () => editorNodes.value.map(...),
  set: (val) => {...}
})

function onNodesChange(changes: any[]) {
  changes.forEach(change => {...})
}
```

**After:**
```typescript
// ~10 行
const { nodes, edges, addNode, addEdge, removeNode, loadGraph, getCurrentQuestGraph } = useQuestEditor()

// VueFlow 直接绑定
<VueFlow v-model:nodes="nodes" v-model:edges="edges" />
```

## 实现步骤

1. 修改 `useQuestEditor` 使用 VueFlow 原生 hooks
2. 更新 `EditorView.vue`，移除 computed 转换层
3. 确保连接验证、循环检测、单例约束仍然正常工作
4. 测试所有交互：添加、删除、拖拽、连接、保存、加载

## 保留的业务逻辑

- **连接规则验证** (`validConnections`)
- **循环检测** (DFS 算法)
- **单例约束** (start/task/completion 只能有一个)
- **节点工厂** (`createDefaultNodeData`)
- **图格式转换** (导入/导出)
