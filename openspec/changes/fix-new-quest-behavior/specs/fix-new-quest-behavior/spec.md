## ADDED Requirements

### Requirement: New Quest Creates Single Node
When user clicks "新建任务" button, the system SHALL create exactly ONE task node for the new quest and add it to the canvas.

#### Scenario: Create new quest
- **WHEN** user clicks "新建任务" button in sidebar
- **THEN** exactly one task node is created
- **AND** the new quest appears in sidebar with "(未保存)" suffix

### Requirement: Sidebar Auto Width
The sidebar SHALL automatically adjust width based on content.

#### Scenario: Long quest name
- **WHEN** a quest has a long name
- **THEN** sidebar width expands to fit the content

### Requirement: Quest List Shows Names
The quest list in sidebar SHALL display quest names, not IDs.

#### Scenario: Display quest by name
- **WHEN** quests are loaded
- **THEN** show quest.name['zh-CN'] or quest.name['en-US']
- **AND** fallback to "未命名任务" if no name is set
