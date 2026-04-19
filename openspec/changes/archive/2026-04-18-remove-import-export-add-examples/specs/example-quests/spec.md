## ADDED Requirements

### Requirement: Example quests dialog
The system SHALL display a modal dialog listing available example quest configurations when user clicks the "Load Examples" button.

#### Scenario: Open examples dialog
- **WHEN** user clicks the "Load Examples" button in the editor header
- **THEN** a modal dialog SHALL appear showing a list of available examples

#### Scenario: Close examples dialog
- **WHEN** user clicks the close button or outside the dialog
- **THEN** the dialog SHALL be dismissed

### Requirement: Example list display
The system SHALL display each example with its name and description.

#### Scenario: Display example cards
- **WHEN** the examples dialog is open
- **THEN** each example SHALL be shown as a card with name and description

### Requirement: Load example into editor
The system SHALL load the selected example quest(s) into the editor when user clicks "Load" on an example.

#### Scenario: Load single example
- **WHEN** user clicks "Load" on an example card
- **THEN** the example quest(s) SHALL be added to the editor canvas
- **AND** the dialog SHALL be closed
- **AND** the sidebar quest list SHALL be updated

### Requirement: Example file location
Example quest files SHALL be stored as JSON in `assets/examples/` directory.

#### Scenario: Load example JSON
- **WHEN** system needs to display examples
- **THEN** it SHALL fetch JSON files from `assets/examples/` directory

## REMOVED Requirements

### Requirement: Import quest via file upload
**Reason**: Users rarely import custom configurations; Git provides version control for exported files.
**Migration**: Use built-in example quests to get started.

### Requirement: Export quest to file
**Reason**: Export functionality is redundant with Git version control.
**Migration**: Use Git to manage and backup quest configurations.
