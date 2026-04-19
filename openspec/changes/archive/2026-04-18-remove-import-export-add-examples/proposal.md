## Why

导入/导出功能使用频率低，且已有版本控制系统（Git）管理配置。更重要的是，用户需要一个快速上手的方式——通过示例配置了解编辑器的用法，而不是从零开始摸索。

## What Changes

- **移除** 导入按钮和导入对话框（ImportDialog 组件）
- **移除** 导出按钮和导出功能
- **新增** "加载示例" 按钮，点击后显示示例列表弹窗
- **新增** 3-5 个预设示例配置（simple-quest、daily-quest、branching-quest 等）

## Capabilities

### New Capabilities

- `example-quests`: 内置示例配置系统，支持加载预设任务示例到编辑器

### Modified Capabilities

- （无）

## Impact

- 移除 `ImportDialog.vue` 组件
- 修改 `EditorView.vue` 的 Header 按钮区域
- 新增 `ExampleQuestsDialog.vue` 组件
- 在 `assets/examples/` 目录存放 JSON 格式的示例配置
