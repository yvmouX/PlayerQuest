## Context

The editor currently requires quests to exist before they can be added to the graph. Users must create quests elsewhere, then drag them into the editor. This creates friction for the workflow.

## Goals / Non-Goals

**Goals:**
- Allow users to create new quests directly from the editor
- Immediately enter edit mode for the new quest
- Clearly indicate unsaved state in the UI

**Non-Goals:**
- Full quest creation wizard (keep it simple - just the basic task node)
- Saving to backend on create (user must explicitly save)

## Decisions

### Approach: Simple inline creation

1. Add "新建任务" button to the Header actions
2. Button calls `createNewQuest()` which:
   - Generates a temporary ID: `temp_${Date.now()}`
   - Sets name to "未命名任务"
   - Adds to sidebarQuests with `isUnsaved: true` flag
   - Calls `addNode()` to add the node to canvas
   - Immediately calls `selectNode()` to open properties panel
3. In sidebar, display `(未保存)` suffix for unsaved quests
4. On save, remove the unsaved flag and sync with backend

### Alternative: Modal dialog
- Would require more UI complexity
- Decided against for simplicity

## Risks / Trade-offs

- **Risk**: Temporary ID might conflict with real IDs → Mitigation: Use `temp_` prefix which is invalid for real quests
- **Risk**: User creates multiple unsaved quests → Acceptable, existing behavior allows this
