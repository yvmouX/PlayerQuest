## 1. Fix Quest List Display

- [x] 1.1 Fix loadData to separate sidebar quests from canvas nodes
- [x] 1.2 Remove automatic loadQuests call that adds all quests to canvas

## 2. Fix New Quest Button

- [x] 2.1 Fix handleCreateNewQuest to ADD to list, not replace
- [x] 2.2 Add new node to canvas without clearing existing nodes

## 3. Fix Sidebar Quest Click

- [x] 3.1 Add click handler to load quest graph on sidebar quest click
- [x] 3.2 Implement graph loading per quest (or single graph for now)

## 4. Remove Save Button (Auto-Save)

- [x] 4.1 Remove save button from NodePropertiesPanel
- [x] 4.2 Watch for changes and emit update immediately on field change

## 5. Fix Name Persistence

- [x] 5.1 Ensure backend stores name properly or frontend converts correctly
- [x] 5.2 Verify name appears after server restart
