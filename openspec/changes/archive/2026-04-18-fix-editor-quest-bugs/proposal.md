## Why

The task editor has multiple UX and persistence issues that make it unusable:
1. Task names don't persist after server restart
2. Multiple tasks in list cause all their nodes to appear on canvas
3. New quest button clears all quests from sidebar
4. Clicking sidebar quest doesn't switch canvas view
5. Properties panel has confusing save button

## What Changes

### Persistence Fix
- Backend TaskDefinition must accept and store localized name/description
- OR frontend converts localized format before sending to backend

### Canvas Loading Fix  
- Load quest graph (nodes/edges) but DON'T automatically load all quests as task nodes
- Quest list should only show quests, not add them to canvas

### New Quest Button Fix
- New quest should ADD to existing quest list, not replace
- Canvas should add ONE node for the new quest without clearing

### Sidebar Quest Click Fix
- Clicking a quest in sidebar should show ONLY that quest's nodes on canvas
- Load the graph data for selected quest

### Auto-Save Properties
- Remove save button from properties panel
- Emit update on every field change for auto-save

## Capabilities

### New Capabilities
- `fix-editor-quest-bugs`: Fix multiple UX and persistence issues in task editor

## Impact

- **Frontend**: EditorView.vue - fix new quest, sidebar click behavior
- **Frontend**: NodePropertiesPanel.vue - remove save button, auto-save
- **Backend**: TaskDefinition or API - support localized text or proper conversion
