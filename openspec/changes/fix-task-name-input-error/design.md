## Context

The TaskNodeData has a `name` field that should be a localized object:
```typescript
name: { 'zh-CN': string, 'en-US': string }
```

But when editing, the input is trying to do `name['zh-CN'] = value` on what's actually a string `''`.

## Goals / Non-Goals

**Goals:**
- Fix the TypeError when editing name field
- Ensure name is always a proper localized object

**Non-Goals:**
- Change the data structure format (it should stay localized object)

## Decisions

### Root Cause
The issue is in how the v-model binding works. When user types in the input:
1. The input is likely bound directly to `selectedNode.name`
2. When user types, it tries to do `selectedNode.name['zh-CN'] = value`
3. But if `name` is a string `''`, this fails

### Solution
In NodePropertiesPanel, when displaying/editing the name field:
1. Use a computed getter that returns the localized value
2. Use a setter that properly updates the localized object

OR

When the name field is a string, convert it to a localized object before binding.
