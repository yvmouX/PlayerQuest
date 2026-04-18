## 1. Vue Router Fixes (Already Completed)

These fixes were completed prior to OpenSpec documentation:
- [x] 1.1 Change Vue Router to hash mode (`createWebHashHistory`)
- [x] 1.2 Add Teleport to CreateQuestDialog
- [x] 1.3 Fix Header slot usage with v-slot:actions

## 2. Edge Connection Management

- [x] 2.1 Handle edge click events in EditorView
- [x] 2.2 Add edge deletion on click with confirmation
- [ ] 2.3 Display edge labels on hover/select (requires custom Vue Flow edge component)
- [x] 2.4 Update useQuestEditor to support edge removal (already existed)

## 3. Import/Export Functionality

### Frontend
- [x] 3.1 Add Import button to EditorView header
- [x] 3.2 Add Export button to EditorView header
- [x] 3.3 Create ImportDialog component with file upload and paste options
- [x] 3.4 Implement JSON parsing for quest import
- [x] 3.5 Implement JSON generation for quest export

### Backend
- [x] 3.6 Verify POST /api/quests creates new quest correctly
- [x] 3.7 Add /api/quests/batch endpoint for bulk import

## 4. Integration Testing

- [ ] 4.1 Test creating a new quest via dialog
- [ ] 4.2 Test connecting two quests with an edge
- [ ] 4.3 Test importing quests from JSON
- [ ] 4.4 Test exporting quests to JSON
- [ ] 4.5 Test deleting a quest node
- [ ] 4.6 Test saving and reloading editor state
