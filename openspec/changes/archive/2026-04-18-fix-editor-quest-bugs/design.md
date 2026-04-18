## Context

Multiple issues exist in the editor:

### Issue 1: Name doesn't persist
Backend `TaskDefinition.name` is `String`, but UI stores localized object `{ 'zh-CN': '', 'en-US': '' }`. When saving, the localized object is sent as JSON which backend rejects or ignores.

### Issue 2: Multiple quests load all nodes
`loadData()` calls `loadQuests()` which adds ALL quests as task nodes. This is wrong - only the loaded graph's nodes should appear.

### Issue 3: New quest clears list
`handleCreateNewQuest` sets `sidebarQuests.value = [newQuest]` instead of pushing to array.

### Issue 4: Sidebar click does nothing
`handleDragStart` only sets drag data, doesn't switch canvas view.

### Issue 5: Save button confusion
Properties panel has save button but changes should auto-save via `handleUpdateQuest`.

## Goals / Non-Goals

**Goals:**
- Fix all 5 issues
- Ensure proper data flow between frontend and backend

**Non-Goals:**
- Don't change backend data models (unless necessary for localization)

## Decisions

### For Issue 1 (Name persistence):
- Keep using localized format in frontend
- Use `getLocalizedText()` helper when displaying
- When saving, extract single string for backend

### For Issue 2 (Canvas loading):
- `loadData()` should load quests for SIDEBAR only
- Canvas should load from GRAPH data, not from quests
- Separate `sidebarQuests` (just list) from `editorNodes` (canvas nodes)

### For Issue 3 (New quest):
```javascript
// WRONG
sidebarQuests.value = [newQuest]
// CORRECT  
sidebarQuests.value = [...sidebarQuests.value, newQuest]
```

### For Issue 4 (Sidebar click):
- When clicking quest in sidebar, call `loadGraph()` with that quest's graph
- Need to store graph per quest OR load empty canvas

### For Issue 5 (Auto-save):
- Remove save button from NodePropertiesPanel
- Watch for changes and emit update immediately
