## Why

任务编辑器存在三个影响使用的 bug：
1. 点击节点后右侧属性编辑面板不显示
2. 左侧节点列表缺少核心节点（Start、Task、Completion）
3. 帮助文档缺少删除节点和删除连线的方法

## What Changes

**Bug 1 修复 - 属性面板不显示：**
- 检查 `handleNodeClick` 函数逻辑
- 检查 `selectedNode` 与 `NodePropertiesPanel` 的绑定

**Bug 2 修复 - 缺少核心节点：**
- 在左侧边栏添加"核心节点"分组
- 添加 Start、Task、Completion 三个节点的可拖拽项

**Bug 3 修复 - 帮助文档不完整：**
- 在快速开始步骤中添加删除节点的方法
- 在快速开始步骤中添加删除连线的方法

## Capabilities

### Modified Capabilities

- `editor-help`: 更新帮助文档，补充删除操作说明
- `node-editor`: 补充缺失的核心节点到侧边栏

### New Capabilities

- （无）

## Impact

- 修改 `EditorView.vue` 侧边栏结构
- 修改 `public/help/editor-guide.json` 帮助内容
- 可能需要检查 `handleNodeClick` 和 `NodePropertiesPanel` 的交互逻辑
