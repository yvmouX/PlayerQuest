## ADDED Requirements

### Requirement: Help button in editor header
The system SHALL display a "Help" button in the editor header that opens a help dialog when clicked.

#### Scenario: Open help dialog
- **WHEN** user clicks the "Help" button in the editor header
- **THEN** a modal dialog SHALL appear showing the editor usage guide

#### Scenario: Close help dialog
- **WHEN** user clicks the close button or outside the dialog
- **THEN** the dialog SHALL be dismissed

### Requirement: Help content structure
The help dialog SHALL display content organized into at least two sections: Quick Start guide and Node Type descriptions.

#### Scenario: Display quick start section
- **WHEN** the help dialog is open
- **THEN** a "Quick Start" section SHALL be displayed with step-by-step instructions

#### Scenario: Display node types section
- **WHEN** the help dialog is open
- **THEN** a "Node Types" section SHALL be displayed explaining each node type

### Requirement: Help content source
The help content SHALL be loaded from a JSON file stored in the `public/help/` directory.

#### Scenario: Load help content
- **WHEN** the help dialog is opened
- **THEN** the system SHALL fetch and display content from `public/help/editor-guide.json`
