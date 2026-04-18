## Context

The new quest button was added but has usability issues.

## Goals / Non-Goals

**Goals:**
- Only create ONE node when clicking new quest
- Move button to intuitive location below quest list
- Auto-adjust sidebar width
- Show quest names clearly

**Non-Goals:**
- Change existing quest loading behavior
- Modify node creation for other operations

## Decisions

### Button Location
Move from header to below quest list in sidebar for better discoverability.

### Node Creation Fix
Current `handleCreateNewQuest` has issue - `editorNodes.value.length` is counting existing nodes. Should only create the single new quest node.

### Sidebar Width
Change from fixed `width: 200px` to `width: max-content` with `min-width` for usability.

### Quest Display
Sidebar already has logic to show `quest.name['zh-CN'] || quest.name['en-US']` but falls back to ID. Ensure name is always shown.
