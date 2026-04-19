## 1. Fix Frontend UX Issues

### 1.1 Fix Backspace Deletion
- [x] 1.1.1 Update handleKeyDelete to skip deletion when focused on input/textarea
- [x] 1.1.2 Test backspace in input fields does not delete nodes

### 1.2 Add Toast Notification
- [x] 1.2.1 Create Toast component or use simple alert
- [x] 1.2.2 Replace console.warn in addEdge with visible toast

### 1.3 Remove Unnecessary Name Fields
- [x] 1.3.1 Update StartNodeData type to remove name field
- [x] 1.3.2 Update CompletionNodeData type to remove name field
- [x] 1.3.3 Update createDefaultNodeData for start and completion
- [x] 1.3.4 Update NodePropertiesPanel to not show name input for Start/Completion

## 2. Backend Graph Storage

### 2.1 Create Backend Models
- [x] 2.1.1 Create NodeConnection.java for edge representation
- [x] 2.1.2 Create GraphNode.java for node representation
- [x] 2.1.3 Create QuestGraph.java to hold nodes and edges

### 2.2 Create Graph API Controller
- [x] 2.2.1 Create GraphStorageController.java
- [x] 2.2.2 Implement GET /api/graphs endpoint
- [x] 2.2.3 Implement GET /api/graphs/{id} endpoint
- [x] 2.2.4 Implement PUT /api/graphs/{id} endpoint
- [x] 2.2.5 Implement DELETE /api/graphs/{id} endpoint

### 2.3 Register Graph Endpoints
- [x] 2.3.1 Add graph routes in EditorServer.java

## 3. Frontend Graph API Integration

### 3.1 Create Graph Types
- [x] 3.1.1 Add GraphNode, NodeConnection, QuestGraph types to frontend

### 3.2 Create GraphService
- [x] 3.2.1 Add GraphService to api.ts
- [x] 3.2.2 Implement getAll, getById, save, delete methods

### 3.3 Update useQuestEditor
- [x] 3.3.1 Update loadQuests to also load edges from QuestGraph
- [x] 3.3.2 Update exportData to return QuestGraph structure
- [x] 3.3.3 Integrate GraphService for loading/saving graphs

### 3.4 Update EditorView
- [x] 3.4.1 Update handleSave to use saveGraph()
- [x] 3.4.2 Update loadData to load graph data
