## Context

任务编辑器存在键盘删除功能的 bug，以及删除连线后无法重连的问题。

## Goals / Non-Goals

**Goals:**
- 移除右上角冗余的"新建任务"按钮
- 修复 Delete 键无法删除节点的问题
- 修复删除连线后无法重连的问题

**Non-Goals:**
- 不改变节点数据模型
- 不重构整体架构

## Decisions

### Bug 1: Delete 键无法删除节点

**问题分析**：
- `handleKeyDelete` 函数存在但从未注册到任何事件监听器
- `handleKeyDelete` 只处理连线删除，未处理节点删除

**修复方案**：
1. 在 `onMounted` 中添加 `window.addEventListener('keydown', handleKeyDelete)`
2. 在 `onUnmounted` 中移除监听器
3. 扩展 `handleKeyDelete` 支持节点删除：
   - 如果有选中的边，删除边
   - 如果有选中的节点，删除节点

### Bug 2: 删除连线后无法重连

**问题分析**：
- `flowEdges` computed 的 setter 是空的：`set: (val) => {}`
- 当 VueFlow 删除边时，更新无法同步回 `editorEdges`
- 边的旧 ID 可能仍然存在于 `editorEdges` 中，导致重复检查失败

```javascript
const flowEdges = computed({
  get: () => editorEdges.value.map(...),
  set: (val) => {}  // 空 setter 导致同步丢失
})
```

**修复方案**：
1. 实现 `flowEdges` 的 setter，将 VueFlow 的边更新同步回 `editorEdges`
2. 确保 `removeEdge` 正确更新 `editorEdges`

### Bug 3: 移除"新建任务"按钮

**问题分析**：
- 左侧核心节点已提供 Start、Task、Completion 节点
- "新建任务"功能与拖拽节点到画布重复

**修复方案**：
1. 从 Header template 中移除按钮
2. 移除 `handleAddQuest` 函数
3. 移除 `showCreateDialog` 状态

## Risks / Trade-offs

- 无显著风险
