## Context

The task editor web interface is a Vue 3 + Javalin SPA for managing Minecraft quest configurations visually using Vue Flow (node-based editor).

**Current Issues Identified:**
1. CreateQuestDialog missing Teleport, causing rendering issues
2. Vue Router history mode incompatible with Javalin static file serving
3. Header component slot usage incorrect, hiding action buttons
4. Edge connections UI incomplete
5. Import/export functionality missing
6. Javalin path syntax outdated (`:param` vs `{param}`)

## Goals / Non-Goals

**Goals:**
- Fix all identified UI/UX bugs preventing task creation
- Complete edge connection management (create/delete with labels)
- Add import/export quest configuration functionality
- Ensure all CRUD operations work via API

**Non-Goals:**
- Redesigning the visual appearance (keep existing styling)
- Adding new quest types or validation rules
- Backend persistence changes (reuse existing TaskStorage)

## Decisions

### 1. Vue Router Hash Mode
**Decision:** Use `createWebHashHistory()` instead of `createWebHistory()`
**Rationale:** Hash mode doesn't require server-side SPA fallback. Javalin's static file serving only handles exact file paths; hash routes (`/#/editor`) are processed entirely client-side.
**Alternative:** Configure Javalin with SPA fallback middleware - adds complexity and potential edge cases.

### 2. Teleport for Modals
**Decision:** Wrap all modal dialogs with `<Teleport to="body">`
**Rationale:** Ensures modals render at document body level, avoiding parent element overflow/z-index constraints.
**Alternative:** CSS fixes (z-index, overflow) - fragile and case-by-case.

### 3. Named Slot for Header Actions
**Decision:** Use explicit `v-slot:actions` for header action buttons
**Rationale:** Vue 3 slot handling requires explicit slot reference when using named slots with `<template>`.
**Alternative:** Use default slot with manual slot prop passing - less idiomatic.

### 4. Edge Management via Vue Flow Events
**Decision:** Use `@connect` and `@edge-click` events for edge lifecycle
**Rationale:** Vue Flow provides built-in connect/edge events with source/target data.
**Alternative:** Custom edge component - unnecessary complexity.

## Risks / Trade-offs

[Low Risk] **SPA Routing**: Hash routing changes URL format (`/#/editor` vs `/editor`). Users with bookmarks to old URLs will need updates.

[Medium Risk] **API Compatibility**: Changes to API endpoints (`:id` → `{id}`) are already made. Existing API consumers must update path syntax.

## Open Questions

1. Should import/export support file upload or textarea paste?
2. Should auto-save be enabled by default or opt-in?
