## ADDED Requirements

### Requirement: Keyboard delete for nodes and edges
The system SHALL allow users to delete selected nodes and edges using keyboard shortcuts (Delete or Backspace).

#### Scenario: Delete selected edge
- **WHEN** user selects an edge and presses Delete or Backspace key
- **THEN** the edge SHALL be removed from the editor

#### Scenario: Delete selected node
- **WHEN** user selects a node and presses Delete or Backspace key
- **THEN** the node SHALL be removed from the editor
- **AND** all connected edges SHALL also be removed

### Requirement: Edge reconnection after deletion
The system SHALL allow users to reconnect two nodes after deleting the edge between them.

#### Scenario: Reconnect nodes after edge deletion
- **WHEN** user deletes an edge between two nodes
- **AND** user attempts to create a new edge between the same two nodes
- **THEN** the new edge SHALL be created successfully

## REMOVED Requirements

### Requirement: New quest button in header
**Reason**: Redundant with core nodes in sidebar (Start, Task, Completion can be dragged to canvas)
**Migration**: Use core nodes from sidebar to create new quests
