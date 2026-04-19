## Why

The current new quest button implementation has several issues:
1. When creating a new quest, all existing nodes get added to the canvas
2. The new quest button is in the header, making it less intuitive
3. Sidebar width is fixed and doesn't adapt to content
4. Quest list shows IDs instead of names, making it hard to identify quests

## What Changes

- Move "新建任务" button to below the quest list in the sidebar
- Fix new quest creation to only add ONE node (the new quest's task node)
- Make sidebar width auto-adjust based on content
- Show quest name in sidebar, not ID

## Capabilities

### New Capabilities
- `fix-new-quest-behavior`: Fix UX issues with new quest creation

## Impact

- **Frontend**: EditorView.vue - move button, fix creation logic, update sidebar display
- **Frontend**: CSS - auto-width sidebar
