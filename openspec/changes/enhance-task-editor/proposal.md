## Why

The task editor web interface has fundamental usability issues preventing users from creating and managing tasks. The "Create Quest" dialog doesn't render properly, routing configuration was incompatible with the backend server, and several core workflows (import/export, edge connections) are incomplete or broken.

## What Changes

### Frontend Fixes
- Fix CreateQuestDialog rendering (add Teleport to body)
- Fix Vue Router hash mode for SPA compatibility
- Fix Header slot usage for action buttons
- Complete edge/connection creation and deletion UI
- Add import/export functionality for quest configurations
- Improve quest node drag-and-drop from sidebar

### Backend Fixes
- Fix Javalin path syntax (`:param` → `{param}`)
- Add POST endpoint for creating new quests via API
- Ensure TaskEditorController CRUD operations work correctly

### UX Improvements
- Add visual feedback for edge connections
- Add confirmation for node/edge deletion
- Add auto-save option
- Improve error handling and user notifications

## Capabilities

### New Capabilities
- `quest-import-export`: Import/export quest configurations as JSON files
- `quest-connection-management`: Visual edge creation and deletion with labels

### Modified Capabilities
- `task-editor-ui`: The existing task-editor spec needs delta updates for dialog fixes and UX improvements

## Impact

### Files Affected
- `EditorServer.java` - API routing
- `TaskEditorController.java` - Backend CRUD
- `EditorView.vue` - Main editor canvas
- `QuestNode.vue` - Node display
- `PropertiesPanel.vue` - Property editing
- `CreateQuestDialog.vue` - Quest creation dialog
- `useQuestEditor.ts` - State management
- Vue Router configuration
