## ADDED Requirements

### Requirement: Task Node Name Field
The system SHALL allow editing the name field of a Task node without throwing TypeError.

#### Scenario: Edit name field
- **WHEN** user clicks on a Task node and edits the name field
- **THEN** the name is properly saved as a localized object `{ 'zh-CN': value }`

#### Scenario: Name field initialization
- **WHEN** a new Task node is created
- **THEN** the name field is initialized as `{ 'zh-CN': '', 'en-US': '' }`
