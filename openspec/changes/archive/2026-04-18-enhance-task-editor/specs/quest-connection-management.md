## ADDED Requirements

### Requirement: Create edge connection
The system SHALL allow users to create connections between quest nodes by dragging from source to target.

#### Scenario: Successful edge creation via drag
- **WHEN** user drags from node A's right handle to node B's left handle
- **THEN** a directed edge appears connecting A to B

#### Scenario: Prevent duplicate edges
- **WHEN** user attempts to create an edge that already exists
- **THEN** system ignores the duplicate connection

### Requirement: Delete edge connection
The system SHALL allow users to delete edge connections.

#### Scenario: Delete edge via click
- **WHEN** user clicks on an edge
- **THEN** system highlights the edge and shows delete option

#### Scenario: Confirm edge deletion
- **WHEN** user clicks delete on a selected edge
- **THEN** system removes the edge from the canvas

### Requirement: Edge labels
The system SHALL allow users to add optional labels to edges to describe the relationship.

#### Scenario: Add label to edge
- **WHEN** user selects an edge and edits its label
- **THEN** the label is displayed on the edge

#### Scenario: Default edge without label
- **WHEN** user creates an edge without specifying a label
- **THEN** the edge displays without any label text
