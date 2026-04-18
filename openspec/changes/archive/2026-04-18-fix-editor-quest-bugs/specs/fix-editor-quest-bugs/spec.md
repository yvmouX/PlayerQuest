## ADDED Requirements

### Requirement: Quest List Shows All Quests
The sidebar quest list SHALL display all available quests without adding them to canvas.

#### Scenario: Load quests
- **WHEN** editor loads data
- **THEN** sidebar shows all quests
- **AND** canvas shows nodes from graph (not from quest list)

### Requirement: New Quest Adds to List
The new quest button SHALL add a new quest to existing list without clearing.

#### Scenario: Create new quest
- **WHEN** user clicks "新建任务"
- **THEN** new quest appears in sidebar below existing quests
- **AND** one new task node appears on canvas

### Requirement: Sidebar Quest Click Loads Graph
Clicking a quest in sidebar SHALL load that quest's graph onto canvas.

#### Scenario: Select quest from sidebar
- **WHEN** user clicks a quest in sidebar
- **THEN** canvas updates to show that quest's nodes and edges

### Requirement: Properties Auto-Save
The properties panel SHALL auto-save changes without save button.

#### Scenario: Edit property
- **WHEN** user edits a field in properties panel
- **THEN** change is immediately saved
- **AND** no save button is displayed

### Requirement: Quest Name Persists
Quest names SHALL persist after server restart.

#### Scenario: Save and reload
- **WHEN** user sets quest name and saves
- **THEN** name is stored in backend
- **AND** name appears after server restart
