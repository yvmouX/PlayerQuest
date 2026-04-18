## Why

Currently, users can only add tasks to the editor by dragging them from the sidebar quest list. There's no way to create a new quest directly from the editor interface. This forces users to go elsewhere to create quests first, then return to add them to the graph.

## What Changes

- Add a "新建任务" (New Quest) button in the editor header
- When clicked, create a new quest with a temporary ID and "未命名任务" name
- Automatically open the properties panel for the new quest for immediate editing
- Display the new quest in the sidebar quest list with "(未保存)" suffix

## Capabilities

### New Capabilities
- `new-quest-button`: Add UI button to create new quests directly in the editor with immediate edit mode

## Impact

- **Frontend**: EditorView.vue - add button and click handler
- **Frontend**: useQuestEditor.ts - add function to create new quest with unsaved status
- **Frontend**: Toast notification if user tries to save without naming
