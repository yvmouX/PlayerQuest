## Why

任务编辑器存在多个 UX 问题影响使用体验，且后端不支持存储节点连线：

1. **保存后加载不保留连线**：后端只存储任务数据，不存储节点连线信息
2. **部分节点无法删除**：可能是 `removeNode` 函数问题
3. **连线规则不明确**：用户不知道哪些节点可以互相连接
4. **连线失败无提示**：`addEdge` 的 `console.warn` 用户看不到
5. **部分节点有不必要的 name 参数**：如 Start、Completion 节点的 name 字段
6. **Backspace 误删节点**：在输入框中按 Backspace 会意外删除整个节点

## What Changes

**前端修复：**

1. **Backspace 误删修复**：在 `handleKeyDelete` 中检查是否在输入框中

2. **连线失败 Toast 提示**：添加 Toast 通知替代 `console.warn`

3. **移除不必要的 name 字段**：Start/Completion 节点移除 name

4. **连线数据结构调整**：前端适配新的后端 API

**后端修改：**

5. **新增 QuestGraph 模型**：存储完整的图结构（节点+连线）
   - 新增 `QuestGraph` 类，包含 `nodes` 和 `edges`
   - 新增 `NodeConnection` 类表示连线
   - 新增 `GraphStorageController` 处理图结构 API

6. **API 端点**：
   - `GET /api/graphs` - 获取所有图
   - `GET /api/graphs/{id}` - 获取单个图
   - `PUT /api/graphs/{id}` - 保存图
   - `DELETE /api/graphs/{id}` - 删除图

## Capabilities

### New Capabilities

- `quest-graph-storage`: 后端支持存储和加载任务图结构（节点+连线）

### Modified Capabilities

- `node-editor`: 修复多个 UX 问题，适配新后端 API

## Impact

**前端：**
- 修改 `useQuestEditor.ts`：适配新的图存储 API
- 修改 `EditorView.vue`：修复 Backspace 误删、添加 Toast 提示
- 修改各节点组件：移除不必要的 name 字段

**后端：**
- 新增 `api/src/main/java/.../model/QuestGraph.java`
- 新增 `api/src/main/java/.../model/NodeConnection.java`
- 新增 `GraphStorageController.java`
- 修改 `TaskEditorController.java` 移除图相关端点（如果有）
