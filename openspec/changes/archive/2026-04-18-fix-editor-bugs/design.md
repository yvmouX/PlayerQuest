## Context

任务编辑器存在三个 bug 影响使用体验。

## Goals / Non-Goals

**Goals:**
- 修复点击节点后属性面板不显示的问题
- 在侧边栏添加核心节点（Start、Task、Completion）
- 更新帮助文档，补充删除节点和删除连线的方法

**Non-Goals:**
- 不改变现有的节点数据模型
- 不重构代码结构

## Decisions

### Bug 1: 属性面板不显示

**问题分析**：
- `handleNodeClick(event)` 直接设置 `editorSelectedNode.value = event.node.id`
- 但 `editorSelectedNode` 是从 `useQuestEditor()` 解构出的 computed 属性（返回 node.data）
- 直接赋值给 computed 的 `.value` 无效

**修复方案**：
- `useQuestEditor` 已经导出了 `selectNode` 函数
- 将 `handleNodeClick` 改为调用 `selectNode(event.node.id)`

### Bug 2: 缺少核心节点

**问题分析**：
- 侧边栏只有"流程节点"和"高级节点"两组
- 缺少"核心节点"分组（Start、Task、Completion）

**修复方案**：
- 在侧边栏添加"核心节点"分组
- 添加 Start、Task、Completion 的可拖拽项

### Bug 3: 帮助文档不完整

**问题分析**：
- 当前帮助文档的快速开始只有 4 个步骤
- 缺少删除节点和删除连线的方法

**修复方案**：
- 在 `editor-guide.json` 的 quickStart.steps 中添加：
  - "点击节点后按 Delete 键或点击删除按钮删除节点"
  - "点击连线后按 Delete 键或点击确认删除连线"

## Risks / Trade-offs

- 无显著风险
