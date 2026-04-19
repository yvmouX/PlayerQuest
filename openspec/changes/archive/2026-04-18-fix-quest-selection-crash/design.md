## Context

### Issue 1: 404 on Graph Load
When clicking a sidebar quest, `handleSelectQuest` calls `GraphService.getById(quest.id)`. But the graph API was designed to have one graph per editor, not one per quest. The quest ID is not a graph ID, so it returns 404.

**Fix**: When graph doesn't exist (404), just show empty canvas instead of error.

### Issue 2: Interface Freeze
The `watch` on `editedNode` emits update on every change, which triggers `updateNode`, which might cause infinite loops or performance issues.

**Fix**: Add debounce to the watch or prevent recursive updates.
