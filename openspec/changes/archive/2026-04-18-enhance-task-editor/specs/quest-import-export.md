## ADDED Requirements

### Requirement: Quest configuration import
The system SHALL allow users to import quest configurations from JSON format.

#### Scenario: Successful import via file upload
- **WHEN** user clicks "Import" button and selects a valid JSON file
- **THEN** system parses the file and adds quests to the canvas

#### Scenario: Successful import via paste
- **WHEN** user clicks "Import" and pastes JSON into textarea
- **THEN** system parses the JSON and adds quests to the canvas

#### Scenario: Invalid JSON format
- **WHEN** user imports malformed JSON
- **THEN** system displays error message "Invalid JSON format"

### Requirement: Quest configuration export
The system SHALL allow users to export quest configurations as JSON.

#### Scenario: Export all quests
- **WHEN** user clicks "Export" button
- **THEN** system downloads a JSON file containing all quest configurations

#### Scenario: Export selected quests
- **WHEN** user selects quests and clicks "Export Selected"
- **THEN** system downloads a JSON file containing only selected quest configurations
