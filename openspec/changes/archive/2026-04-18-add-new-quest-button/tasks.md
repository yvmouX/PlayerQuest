## 1. UI Changes

- [x] 1.1 Add "新建任务" button to EditorView.vue header actions
- [x] 1.2 Add click handler `handleCreateNewQuest()`
- [x] 1.3 Add unsaved indicator styling for sidebar quests

## 2. Logic Changes

- [x] 2.1 Add `createNewQuest()` function in useQuestEditor.ts
- [x] 2.2 Update sidebar display to show "(未保存)" suffix
- [x] 2.3 Update save logic to handle temp_ IDs and clear unsaved flag
