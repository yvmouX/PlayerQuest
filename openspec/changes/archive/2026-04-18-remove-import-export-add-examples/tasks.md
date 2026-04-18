## 1. Remove Import/Export

- [x] 1.1 Remove import button and export button from EditorView.vue header
- [x] 1.2 Remove ImportDialog component import and usage
- [x] 1.3 Remove handleOpenImport, handleImportQuests, handleExport functions
- [x] 1.4 Remove ImportDialog.vue file

## 2. Create Example Files

- [x] 2.1 Create public/examples/ directory
- [x] 2.2 Create simple-quest.json (Start → Task → Completion)
- [x] 2.3 Create daily-quest.json (with resetInterval)
- [x] 2.4 Create branching-quest.json (with Branch node)
- [x] 2.5 Create event-quest.json (with Event listener)

## 3. Create ExampleQuestsDialog Component

- [x] 3.1 Create ExampleQuestsDialog.vue component
- [x] 3.2 Implement example list display with cards
- [x] 3.3 Implement Load button functionality
- [x] 3.4 Add loading examples from JSON files
- [x] 3.5 Add close/cancel functionality

## 4. Integrate into EditorView

- [x] 4.1 Add "Load Examples" button to header
- [x] 4.2 Import and mount ExampleQuestsDialog
- [x] 4.3 Connect handleExampleLoad to add examples to editor
