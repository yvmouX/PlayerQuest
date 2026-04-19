## ADDED Requirements

### Requirement: Prevent backspace from deleting nodes when editing
The system SHALL NOT delete nodes when the user presses Backspace while focused on an input field.

#### Scenario: Backspace in input does not delete node
- **WHEN** user presses Backspace while cursor is in an input field
- **THEN** the node SHALL NOT be deleted
- **AND** the input content SHALL be modified normally

### Requirement: Connection failure shows user feedback
The system SHALL display a visible notification when a node connection fails due to invalid connection rules.

#### Scenario: Show notification on invalid connection
- **WHEN** user attempts to connect two incompatible nodes
- **THEN** a visible notification SHALL be displayed explaining the connection is not allowed

### Requirement: Graph persistence via API
The system SHALL persist and load the complete graph structure (nodes + edges) via the backend API.

#### Scenario: Save graph with edges
- **WHEN** user clicks save button
- **THEN** the system SHALL send a PUT request to /api/graphs/{id} with nodes and edges data
- **AND** the server SHALL store the complete graph structure

#### Scenario: Load graph with edges
- **WHEN** user loads quests from the server
- **THEN** the system SHALL also load the graph edges
- **AND** all node connections SHALL be restored in the editor canvas

## MODIFIED Requirements

### Requirement: Start node does not require name
**FROM:** Start nodes have a configurable name field
**TO:** Start nodes do not have a name field (identifier is implicit from type)

### Requirement: Completion node does not require name
**FROM:** Completion nodes have a configurable name field
**TO:** Completion nodes do not have a name field (identifier is implicit from type)
