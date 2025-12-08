# 后端 API 需求说明

基于前端的 `任务编辑器（QuestEditor）`、`玩家进度（PlayerProgress）` 和 `统计数据（Statistics）` 组件，需实现以下 API 端点以支持完整功能。

## 1. 任务管理（核心）

此类 API 支持任务创建与管理的核心编辑器功能。

### `GET /api/quests`
获取所有任务定义列表。

*   **响应**：`Quest[]`（任务数组）
*   **用途**：在侧边栏加载任务列表
*   **说明**：需支持完整的 `Quest` 数据结构，包括多语言字段和复杂目标配置

### `GET /api/quests/{id}`
通过 ID 获取单个任务定义。

*   **参数**：`id`（字符串类型）
*   **响应**：`Quest`（单个任务对象）
*   **用途**：（可选）若无需一次性加载全部任务，可在选择单个任务时使用

### `POST /api/quests`
创建新任务或更新现有任务。

*   **请求体**：`Quest` 对象
*   **响应**：`200 OK`（返回保存后的对象）或 `201 Created`（创建成功）
*   **用途**：点击编辑器中的「保存」按钮时调用

### `DELETE /api/quests/{id}`
删除指定任务定义。

*   **参数**：`id`（字符串类型）
*   **响应**：`200 OK`
*   **用途**：支持「删除任务」功能（当前 UI 已隐含该操作，但保存逻辑尚未完全实现）

---

## 2. 奖励库

此类 API 支持「库」标签页中可复用奖励模板的管理功能。

### `GET /api/rewards/templates`
获取所有可复用奖励模板。

*   **响应**：`RewardTemplate[]`（奖励模板数组）
*   **用途**：填充「库」标签页数据

### `POST /api/rewards/templates`
创建或更新奖励模板。

*   **请求体**：`RewardTemplate` 对象
*   **响应**：`200 OK`
*   **用途**：在「库」标签页中保存模板时调用

### `DELETE /api/rewards/templates/{id}`
删除指定奖励模板。

*   **参数**：`id`（字符串类型）
*   **响应**：`200 OK`
*   **用途**：从奖励库中移除模板

---

## 3. 玩家进度

此类 API 支持「玩家」视图中的用户进度跟踪功能。

### `GET /api/players/progress`
获取玩家进度数据。

*   **查询参数**：
    *   `search`：（可选）按玩家名称或 UUID 筛选
    *   `status`：（可选）按状态筛选（例如：`completed` 已完成、`in_progress` 进行中）
    *   `limit`：（可选）分页条数限制
    *   `offset`：（可选）分页偏移量
*   **响应**：`{ total: number, data: PlayerProgress[] }`（总条数 + 进度数据数组）
*   **用途**：填充玩家进度表格数据

---

## 4. 统计数据

此类 API 支持「统计」仪表盘功能。

### `GET /api/stats/completion`
获取任务完成状态分布数据。

*   **响应**：`Array<{ name: string, value: number }>`（例如：[{ "name": "已完成", "value": 400 }, { "name": "进行中", "value": 300 }]）
*   **用途**：渲染「任务完成状态」饼图

### `GET /api/stats/activity`
获取玩家一段时间内的活动趋势数据。

*   **查询参数**：
    *   `range`：时间范围（可选值：`7d` 近7天 | `30d` 近30天）
*   **响应**：`Array<{ day: string, users: number }>`（例如：[{ "day": "2024-01-01", "users": 50 }]）
*   **用途**：渲染用户活动柱状图/折线图

---

## 数据模型（参考）

### 任务（Quest）
```typescript
interface Quest {
  id: string; // 任务ID
  name: { [lang: string]: string }; // 多语言名称（例如：{ "zh-CN": "任务名称", "en-US": "Quest Name" }）
  description: { [lang: string]: string }; // 多语言描述
  type: 'single' | 'multi' | 'series'; // 任务类型：单一任务 | 多阶段任务 | 系列任务
  objectives: QuestObjective[]; // 任务目标列表
  rewards: QuestReward[]; // 任务奖励列表
  createdAt: number; // 创建时间戳（毫秒）
  updatedAt: number; // 更新时间戳（毫秒）
}
```

### 任务目标（QuestObjective）
```typescript
interface QuestObjective {
  id: string; // 目标ID
  type: string; // 目标类型（例如：'break' 破坏方块、'kill' 击杀实体）
  target: string; // 目标对象（例如：方块ID、实体类型）
  count: number; // 所需完成数量
  rewards: QuestReward[]; // 该目标的奖励
  finished?: boolean; // 是否已完成（可选字段）
}
```

### 任务奖励（QuestReward）
```typescript
interface QuestReward {
  id: string; // 奖励ID
  templateId?: string; // 关联的奖励模板ID（可选，关联奖励库）
  type: 'item' | 'xp' | 'money' | 'command'; // 奖励类型：物品 | 经验值 | 货币 | 指令
  value: string | number; // 奖励值（例如：物品ID、经验数量、货币金额、指令字符串）
}
```

---

### 补充说明
1. 所有接口响应均采用 JSON 格式
2. 多语言字段（`name`/`description`）需支持至少中英双语，可扩展其他语言
3. 分页参数（`limit`/`offset`）建议默认值：`limit=20`、`offset=0`
4. 时间戳字段（`createdAt`/`updatedAt`）统一使用 Unix 时间戳（毫秒级）
5. 错误处理建议：返回标准 HTTP 状态码（如 404 未找到、400 参数错误、500 服务器错误），并附带错误信息（例如：`{ "error": "任务不存在" }`）