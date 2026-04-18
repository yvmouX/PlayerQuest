## ADDED Requirements

### Requirement: New Quest Button
The system SHALL provide a "新建任务" button in the editor header that allows users to create a new quest directly within the editor interface.

#### Scenario: Click new quest button
- **WHEN** user clicks the "新建任务" button
- **THEN** a new quest node is created with temporary ID and name "未命名任务"
- **AND** the new quest is added to the sidebar quest list
- **AND** the properties panel opens for the new quest

#### Scenario: Unsaved quest indicator
- **WHEN** a new quest is created
- **THEN** the quest appears in sidebar with "(未保存)" suffix
- **AND** the quest has a temporary ID starting with "temp_"

#### Scenario: Save clears unsaved flag
- **WHEN** user saves a quest with temporary ID
- **THEN** the quest receives a permanent ID from the backend
- **AND** the "(未保存)" suffix is removed from the sidebar display
