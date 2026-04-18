## Why

When editing the name field of a Task node in the editor, an error occurs:
```
Cannot create property 'zh-CN' on string ''
```

This happens because the name field is being treated as a string instead of a localized object structure.

## What Changes

- Fix the name field binding in NodePropertiesPanel to properly handle localized text object
- Ensure name is always initialized as `{ 'zh-CN': '', 'en-US': '' }` object, not a plain string

## Capabilities

### New Capabilities
- `fix-task-name-input-error`: Fix TypeError when editing Task node name

## Impact

- **Frontend**: NodePropertiesPanel.vue - fix name field v-model binding
- **Frontend**: useQuestEditor.ts - ensure name is always a localized object
