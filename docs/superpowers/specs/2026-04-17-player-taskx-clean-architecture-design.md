# PlayerTaskX Clean Architecture 重构设计

## 1. 项目概述

**项目定位**：面向服主的 Minecraft 任务插件，简单易用，开箱即用。

**目标用户**：
- 服主（主要用户）：通过 Web UI 配置任务，无需编写代码
- 进阶服主：支持 MySQL 多服同步

**v1.0 范围（基础版）**：
- 任务系统：玩家接任务 → 完成目标 → 领取奖励
- 进度追踪：追踪玩家行为（杀怪数、挖矿数等）
- 任务类型：杀怪目标、破坏方块目标
- 奖励类型：物品奖励、命令奖励
- 条件类型：权限条件

## 2. 整体架构

```
PlayerTaskX
├── api/              ← 对外接口层（核心抽象，不依赖实现）
│   ├── model/         ← 数据模型（TaskDefinition, Objective, Reward, Condition）
│   ├── event/         ← 自定义事件
│   └── service/       ← 核心服务接口
│
└── core/              ← 核心实现层
    ├── command/       ← 命令处理
    ├── listener/      ← Bukkit 事件监听
    ├── manager/       ← 业务管理器
    ├── storage/       ← 存储实现（可插拔）
    └── web/           ← 内嵌 Web 服务器
```

**核心原则**：
- `api` 层不依赖 `core` 层
- `core` 层通过接口依赖 `api` 层
- 存储实现完全解耦，切换存储方式不影响业务逻辑

## 3. 核心数据模型

### 3.1 任务定义 (TaskDefinition)

v1.0 采用**手动接取**模式。

```java
public class TaskDefinition {
    private String id;                      // 唯一标识
    private String name;                    // 任务名称
    private String description;             // 任务描述
    private List<Objective> objectives;     // 目标列表（需全部完成）
    private List<Reward> rewards;           // 奖励列表
    private List<Condition> conditions;    // 接受条件（可选）
}
```

### 3.2 目标接口 (Objective)

```java
public interface Objective {
    String getId();
    int getTargetAmount();
    boolean checkCompletion(Player player, Event event);
}

public class KillMobObjective implements Objective {
    private String mobType;    // 如 "ZOMBIE"
    private int amount;        // 需击杀数量
}

public class BreakBlockObjective implements Objective {
    private Material blockType; // 方块类型
    private int amount;         // 需破坏数量
}
```

### 3.3 奖励接口 (Reward)

```java
public interface Reward {
    void grant(Player player);
}

public class ItemReward implements Reward {
    private ItemStack item;
}

public class CommandReward implements Reward {
    private String command;  // 如 "give {player} diamond 1"
}
```

### 3.4 玩家进度 (TaskProgress)

```java
public class TaskProgress {
    private UUID playerId;                    // 玩家 ID
    private String taskId;                    // 任务 ID
    private Map<String, Integer> objectiveProgress;  // 目标进度（objectiveId → 当前数量）
    private TaskStatus status;               // 任务状态
    private long acceptedAt;                 // 接受时间戳
    private long completedAt;                // 完成时间戳（0 表示未完成）
    private long claimedAt;                  // 领取奖励时间戳（0 表示未领取）

    public enum TaskStatus {
        IN_PROGRESS,   // 进行中
        COMPLETED,      // 已完成（可领取奖励）
        CLAIMED,        // 已领取奖励
        ABANDONED       // 已放弃
    }
}
```

### 3.5 条件接口 (Condition)

```java
public interface Condition {
    boolean isMet(Player player);
}

public class PermissionCondition implements Condition {
    private String permission;
}
```

## 4. 存储层设计

### 4.1 存储接口 (Storage)

```java
public interface TaskStorage {
    void saveTask(TaskDefinition task);
    TaskDefinition loadTask(String id);
    List<TaskDefinition> loadAllTasks();
    void deleteTask(String id);
}

public interface ProgressStorage {
    void saveProgress(UUID playerId, TaskProgress progress);
    TaskProgress loadProgress(UUID playerId);
    void deleteProgress(UUID playerId);
}
```

### 4.2 存储实现

| 实现 | 说明 | 适用场景 |
|------|------|----------|
| YamlStorage | YAML 文件存储 | 单机服，小规模 |
| SqliteStorage | SQLite 数据库 | 单机服，中规模 |
| MysqlStorage | MySQL 数据库 | 多服同步，大规模 |

### 4.3 异步持久化

- 进度数据变更时，异步写入存储，不阻塞主线程
- 使用 Bukkit 的 `runTaskAsynchronously` 或 `CompletableFuture`

## 5. 事件监听设计

### 5.1 TaskRouter（事件分发中心）

```java
public class TaskRouter {
    // 监听 Bukkit 事件
    // 根据事件类型筛选相关玩家的任务
    // 委托给 TaskManager 处理
}
```

### 5.2 TaskManager（任务管理器）

```java
public class TaskManager {
    void acceptTask(Player player, String taskId);
    void updateProgress(Player player, Objective objective, int amount);
    boolean checkCompletion(Player player, String taskId);
    void claimReward(Player player, String taskId);
}
```

### 5.3 RewardManager（奖励管理器）

```java
public class RewardManager {
    void grantReward(Player player, Reward reward);
}
```

## 6. 命令设计

### 6.1 玩家命令 (/task)

| 命令 | 说明 |
|------|------|
| /task list | 查看可接任务 |
| /task accept \<id\> | 接受任务 |
| /task progress | 查看当前进度 |
| /task abandon \<id\> | 放弃任务 |
| /task claim \<id\> | 领取奖励 |

### 6.2 管理员命令 (/taskadmin)

| 命令 | 说明 |
|------|------|
| /taskadmin reload | 重载配置 |
| /taskadmin debug | 调试模式 |

## 7. Web 编辑器设计

### 7.1 服务器配置

```yaml
editor:
  host: "0.0.0.0"
  port: 8080
  token: "your-secret-token"  # 访问令牌
```

### 7.2 RESTful API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/tasks | 获取所有任务 |
| POST | /api/tasks | 创建任务 |
| PUT | /api/tasks/{id} | 更新任务 |
| DELETE | /api/tasks/{id} | 删除任务 |
| POST | /api/upload | 导入任务配置 |

### 7.3 安全

- 仅限持有 Token 或 OP 玩家访问
- 请求体严格校验
- CORS 默认关闭

## 8. 包结构

```
com.playerPlugin.playerTaskX
├── api/
│   ├── model/
│   │   ├── TaskDefinition.java
│   │   ├── TaskProgress.java
│   │   ├── objective/
│   │   │   ├── Objective.java
│   │   │   ├── KillMobObjective.java
│   │   │   └── BreakBlockObjective.java
│   │   ├── reward/
│   │   │   ├── Reward.java
│   │   │   ├── ItemReward.java
│   │   │   └── CommandReward.java
│   │   └── condition/
│   │       └── PermissionCondition.java
│   ├── event/
│   │   ├── TaskCompleteEvent.java
│   │   └── TaskProgressUpdateEvent.java
│   └── service/
│       ├── TaskService.java
│       └── StorageService.java
│
└── core/
    ├── PlayerTaskX.java          # 插件入口
    ├── command/
    │   ├── TaskCommand.java
    │   └── TaskAdminCommand.java
    ├── listener/
    │   ├── EntityListener.java   # 杀怪事件
    │   └── BlockListener.java   # 挖矿事件
    ├── manager/
    │   ├── TaskManager.java
    │   └── RewardManager.java
    ├── storage/
    │   ├── StorageFactory.java
    │   ├── yaml/
    │   ├── sqlite/
    │   └── mysql/
    └── web/
        ├── EditorServer.java
        └── TaskEditorController.java
```

## 9. v1.0 功能范围

### 9.1 任务系统
- [x] 创建任务（通过 Web UI）
- [x] 接受任务
- [x] 更新进度
- [x] 完成任务
- [x] 领取奖励

### 9.2 目标类型（v1.0）
- KillMobObjective（杀怪）
- BreakBlockObjective（破坏方块）

### 9.3 奖励类型（v1.0）
- ItemReward（物品）
- CommandReward（命令）

### 9.4 条件类型（v1.0）
- PermissionCondition（权限）

## 10. 后续扩展方向

- CraftItemObjective（合成物品）
- InteractObjective（交互）
- ExpReward（经验）
- LevelCondition（等级）
- TaskCondition（前置任务）
- MySQL 多服同步
