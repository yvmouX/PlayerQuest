## Why

新用户首次使用任务编辑器时不知道如何操作，需要一个内置的帮助按钮来引导用户了解编辑器的使用方法。

## What Changes

- 新增"帮助"按钮在编辑器 Header 区域
- 点击按钮显示帮助对话框，包含：
  - 节点编辑器的使用流程
  - 各类节点的用途说明
  - 常用操作示例
- 帮助内容以 Markdown 格式或 JSON 存储，便于维护

## Capabilities

### New Capabilities

- `editor-help`: 任务编辑器使用帮助系统，提供内置的编辑指南

### Modified Capabilities

- （无）

## Impact

- 新增 `EditorHelpDialog.vue` 组件
- 修改 `EditorView.vue` Header 添加帮助按钮
- 新增帮助内容文件 `assets/help/editor-guide.md` 或 JSON
