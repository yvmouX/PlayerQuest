## Why

Two bugs in the quest editor:
1. Clicking sidebar quest tries to GET `/api/graphs/{quest_id}` which returns 404 - graph doesn't exist for that quest ID, causing error
2. Clicking between nodes causes interface to freeze

## What Changes

- Fix sidebar quest click to handle missing graph gracefully (404 is OK)
- Fix node selection issue that causes freeze

## Capabilities

### New Capabilities
- `fix-quest-selection-crash`: Fix crash when selecting quests and nodes

## Impact

- **Frontend**: EditorView.vue - handle 404 for missing graphs
- **Frontend**: NodePropertiesPanel.vue - fix selection freeze issue
