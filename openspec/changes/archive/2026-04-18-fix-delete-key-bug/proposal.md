## Why

任务编辑器存在以下问题影响使用体验：
1. 右上角"新建任务"按钮功能冗余（左侧核心节点已提供相同功能）
2. Delete 键无法删除节点（帮助文档说 Delete 可以删除）
3. 用 Backspace 删除连线后，节点无法重新连线（可能是连线删除不彻底或状态问题）

## What Changes

**1. 移除"新建任务"按钮：**
- 从 EditorView Header 中移除"新建任务"按钮

**2. 修复 Delete 键删除节点：**
- 注册 keydown 事件监听器（目前 handleKeyDelete 存在但未绑定）
- 确保按 Delete 或 Backspace 键能删除选中的节点

**3. 修复删除连线后无法重连：**
- 检查 removeEdge 是否正确执行
- 检查 edges 数组更新是否触发 VueFlow 重新渲染
- 可能需要在删除边后手动重置某些状态

## Capabilities

### Modified Capabilities

- `node-editor`: 移除冗余按钮，修复键盘删除功能

### New Capabilities

- （无）

## Impact

- 修改 `EditorView.vue`：
  - 移除"新建任务"按钮和相关处理函数
  - 添加 keydown 事件监听
  - 修复 edges 更新逻辑
- 修改 `useQuestEditor.ts`：检查 removeEdge 实现
