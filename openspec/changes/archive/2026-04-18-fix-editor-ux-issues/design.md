## Context

任务编辑器存在多个 UX 问题影响用户使用，且后端不支持存储节点连线。

## Goals / Non-Goals

**Goals:**
- 修复 Backspace 在输入框中误删节点
- 添加连线失败的 Toast 提示
- 移除 Start/Completion 节点不必要的 name 字段
- 确保节点删除功能正常工作
- 后端支持存储和加载图结构（节点+连线）

**Non-Goals:**
- 不大幅重构代码结构
- 不支持复杂的有向无环图（DAG）验证

## Decisions

### Issue 1: Backspace 误删节点

**问题分析**：
- `handleKeyDelete` 监听全局 `keydown` 事件
- 当用户在输入框中按 Backspace 时，浏览器默认行为是删除输入内容
- 但我们的 `handleKeyDelete` 同时也会删除节点

**修复方案**：
```javascript
function handleKeyDelete(event) {
  const target = event.target
  if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable) {
    return
  }
  // ... 原有逻辑
}
```

### Issue 2: 连线失败无提示

**问题分析**：
- `addEdge` 函数使用 `console.warn` 输出错误
- 用户看不到这个警告

**修复方案**：
- 添加全局 Toast/Notification 组件
- 将 `console.warn` 替换为用户可见的提示

### Issue 3: 移除不必要的 name 字段

**问题分析**：
- Start 节点和 Completion 节点的 name 字段不是必需的
- 这些节点的标识应该由其类型决定

**修复方案**：
- 从 `StartNodeData` 和 `CompletionNodeData` 类型定义中移除 name
- 更新 `createDefaultNodeData` 函数
- 更新 `NodePropertiesPanel` 中对应的表单

### Issue 4: 节点删除功能

**问题分析**：
- 需要检查 `removeNode` 是否正确工作
- 可能是选择状态没有正确清除导致视觉上看起来没删除

**修复方案**：
- 确保 `removeNode` 后调用 `selectNode(null)` 清除选择状态

### Issue 5: 后端图存储

**问题分析**：
- 当前后端只存储单个任务数据，不存储图结构
- 需要新增 API 来存储和加载完整的图（节点+连线）

**修复方案**：

**数据模型**：

```java
// QuestGraph.java
public class QuestGraph {
    private final String id;
    private final String name;
    private final List<GraphNode> nodes;
    private final List<NodeConnection> edges;
}

// NodeConnection.java  
public class NodeConnection {
    private final String id;
    private final String sourceId;
    private final String targetId;
    private final String label;
}

// GraphNode.java
public class GraphNode {
    private final String id;
    private final String nodeType;  // start, task, completion, etc.
    private final double x;
    private final double y;
    private final Object data;  // TaskDefinition or other node data
}
```

**API 端点**：

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/graphs | 获取所有图 |
| GET | /api/graphs/{id} | 获取单个图 |
| PUT | /api/graphs/{id} | 保存图 |
| DELETE | /api/graphs/{id} | 删除图 |

**前端适配**：

```typescript
// GraphService
interface GraphService {
  getAll(): Promise<QuestGraph[]>
  getById(id: string): Promise<QuestGraph>
  save(graph: QuestGraph): Promise<void>
  delete(id: string): Promise<void>
}
```

## Risks / Trade-offs

- Toast 通知可能需要引入通知组件或使用简单的 alert
- 图存储后端需要新增数据模型和 API
