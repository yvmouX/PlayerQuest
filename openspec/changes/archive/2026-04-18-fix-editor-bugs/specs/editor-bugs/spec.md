## ADDED Requirements

### Requirement: Node selection shows properties panel
The system SHALL display the node properties panel when a node is clicked.

#### Scenario: Click node shows properties
- **WHEN** user clicks on a node in the editor canvas
- **THEN** the properties panel SHALL appear showing the node's editable properties

### Requirement: Core nodes available in sidebar
The system SHALL display core nodes (Start, Task, Completion) in the editor sidebar.

#### Scenario: Display core nodes section
- **WHEN** user views the editor sidebar
- **THEN** a "核心节点" section SHALL be visible with Start, Task, and Completion node options

### Requirement: Help content includes delete operations
The system SHALL include instructions for deleting nodes and edges in the help content.

#### Scenario: Delete node instruction
- **WHEN** user reads the quick start guide
- **THEN** instructions SHALL include how to delete a node

#### Scenario: Delete edge instruction
- **WHEN** user reads the quick start guide
- **THEN** instructions SHALL include how to delete a connection between nodes
