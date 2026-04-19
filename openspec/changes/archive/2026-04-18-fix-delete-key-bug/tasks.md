## 1. Remove New Quest Button

- [x] 1.1 Remove "新建任务" button from EditorView header
- [x] 1.2 Remove handleAddQuest function
- [x] 1.3 Remove showCreateDialog ref
- [x] 1.4 Remove CreateQuestDialog import and usage

## 2. Fix Keyboard Delete for Nodes

- [x] 2.1 Register handleKeyDelete as window keydown listener in onMounted
- [x] 2.2 Add cleanup in onUnmounted
- [x] 2.3 Extend handleKeyDelete to delete selected node when Delete/Backspace pressed

## 3. Fix Edge Reconnection Bug

- [x] 3.1 Implement flowEdges setter to sync VueFlow edges back to editorEdges
- [x] 3.2 Verify removeEdge properly updates editorEdges
- [x] 3.3 Test edge reconnection after deletion
