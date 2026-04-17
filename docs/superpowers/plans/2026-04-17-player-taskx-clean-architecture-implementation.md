# PlayerTaskX Clean Architecture 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 PlayerTaskX 插件，采用 Clean Architecture，实现任务系统 v1.0（杀怪目标、破坏方块目标、物品奖励、命令奖励）

**Architecture:** 采用 Clean Architecture 分层：api 层定义核心接口（不依赖实现），core 层实现具体逻辑。存储层完全解耦，支持 YAML/SQLite/MySQL 可插拔替换。

**Tech Stack:** Java 21, Gradle, Paper API 1.21.8, Jackson, SQLite/MySQL

---

## 文件结构

### 新建（api 模块）

```
api/src/main/java/com/playerPlugin/playerTaskX/api/
├── model/
│   ├── objective/
│   │   ├── Objective.java          # 目标接口
│   │   ├── KillMobObjective.java   # 杀怪目标
│   │   └── BreakBlockObjective.java # 破坏方块目标
│   ├── reward/
│   │   ├── Reward.java             # 奖励接口
│   │   ├── ItemReward.java         # 物品奖励
│   │   └── CommandReward.java      # 命令奖励
│   ├── condition/
│   │   ├── Condition.java          # 条件接口
│   │   └── PermissionCondition.java # 权限条件
│   ├── TaskDefinition.java         # 任务定义（重构）
│   └── TaskProgress.java           # 任务进度（重构）
├── event/
│   ├── TaskCompleteEvent.java      # 任务完成事件
│   └── TaskProgressUpdateEvent.java # 进度更新事件
└── service/
    ├── TaskService.java            # 任务服务接口
    └── StorageService.java         # 存储服务接口
```

### 新建（core 模块）

```
core/src/main/java/com/playerPlugin/playerTaskX/
├── command/
│   ├── TaskCommand.java            # /task 命令
│   └── TaskAdminCommand.java       # /taskadmin 命令
├── listener/
│   ├── EntityListener.java         # 实体事件监听（杀怪）
│   └── BlockListener.java          # 方块事件监听（挖矿）
├── manager/
│   ├── TaskManager.java            # 任务管理器
│   └── RewardManager.java          # 奖励管理器
├── storage/
│   ├── StorageFactory.java         # 存储工厂
│   ├── yaml/
│   │   ├── YamlTaskStorage.java
│   │   └── YamlProgressStorage.java
│   ├── sqlite/
│   │   ├── SQLiteTaskStorage.java
│   │   └── SQLiteProgressStorage.java
│   └── mysql/
│       ├── MySQLTaskStorage.java
│       └── MySQLProgressStorage.java
└── web/
    ├── EditorServer.java           # Web 服务器
    └── TaskEditorController.java   # API 控制器
```

### 修改

```
api/src/main/java/com/playerPlugin/playerTaskX/api/
├── model/TaskDefinition.java       # 重构：支持 Objective/ Reward 接口
├── model/TaskProgress.java         # 重构：支持新状态枚举
└── Enum/PTXTaskStatus.java         # 更新状态枚举

core/src/main/java/com/playerPlugin/playerTaskX/
├── PlayerTaskX.java                 # 插件入口，重构初始化逻辑
├── TaskAPI.java                    # 删除（迁移到 TaskService）
├── TaskAPIImpl.java                 # 删除（迁移到 core/manager）
├── cache/TaskCache.java             # 删除（替换为 TaskManager）
├── event/TaskRouter.java            # 重构：事件分发
├── event/PlayerJoinHandler.java     # 重构：异步加载进度
├── storage/*.java                   # 删除旧实现，使用新存储接口
├── Trigger/*.java                   # 删除（v1.0 不需要）
└── configuration/*.java            # 保留，仅做配置读取
```

---

## 实现阶段

### Phase 1: API 层基础接口

#### Task 1: 创建 Objective 接口和实现

**Files:**
- Create: `api/src/main/java/com/playerPlugin/playerTaskX/api/model/objective/Objective.java`
- Create: `api/src/main/java/com/playerPlugin/playerTaskX/api/model/objective/KillMobObjective.java`
- Create: `api/src/main/java/com/playerPlugin/playerTaskX/api/model/objective/BreakBlockObjective.java`

- [ ] **Step 1: 创建 Objective 接口**

```java
package com.playerPlugin.playerTaskX.api.model.objective;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public interface Objective {
    String getId();
    int getTargetAmount();
    boolean matchesEvent(Event event);
    void applyProgress(Player player, int amount);
    boolean isCompleted(Player player);
}
```

- [ ] **Step 2: 创建 KillMobObjective**

```java
package com.playerPlugin.playerTaskX.api.model.objective;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;

public class KillMobObjective implements Objective {
    private final String id;
    private final EntityType mobType;
    private final int amount;
    private final Map<UUID, Integer> progress = new ConcurrentHashMap<>();

    public KillMobObjective(String id, EntityType mobType, int amount) {
        this.id = id;
        this.mobType = mobType;
        this.amount = amount;
    }

    @Override
    public String getId() { return id; }
    @Override
    public int getTargetAmount() { return amount; }

    @Override
    public boolean matchesEvent(Event event) {
        if (event instanceof EntityDeathEvent e) {
            return e.getEntity().getType() == mobType
                && e.getEntity().getKiller() != null;
        }
        return false;
    }

    @Override
    public void applyProgress(Player player, int amount) {
        progress.merge(player.getUniqueId(), amount, Integer::sum);
    }

    @Override
    public boolean isCompleted(Player player) {
        return progress.getOrDefault(player.getUniqueId(), 0) >= amount;
    }
}
```

- [ ] **Step 3: 创建 BreakBlockObjective**

类似 KillMobObjective，监听 BlockBreakEvent

- [ ] **Step 4: Commit**

---

#### Task 2: 创建 Reward 接口和实现

**Files:**
- Create: `api/src/main/java/com/playerPlugin/playerTaskX/api/model/reward/Reward.java`
- Create: `api/src/main/java/com/playerPlugin/playerTaskX/api/model/reward/ItemReward.java`
- Create: `api/src/main/java/com/playerPlugin/playerTaskX/api/model/reward/CommandReward.java`

- [ ] **Step 1: 创建 Reward 接口**

```java
package com.playerPlugin.playerTaskX.api.model.reward;

import org.bukkit.entity.Player;

public interface Reward {
    void grant(Player player);
}
```

- [ ] **Step 2: 创建 ItemReward**

```java
package com.playerPlugin.playerTaskX.api.model.reward;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ItemReward implements Reward {
    private final ItemStack item;

    public ItemReward(ItemStack item) {
        this.item = item;
    }

    @Override
    public void grant(Player player) {
        player.getInventory().addItem(item.clone());
    }
}
```

- [ ] **Step 3: 创建 CommandReward**

```java
package com.playerPlugin.playerTaskX.api.model.reward;

import org.bukkit.entity.Player;

public class CommandReward implements Reward {
    private final String command;

    public CommandReward(String command) {
        this.command = command;
    }

    @Override
    public void grant(Player player) {
        String cmd = command.replace("{player}", player.getName());
        player.getServer().dispatchCommand(player.getServer().getConsoleSender(), cmd);
    }
}
```

- [ ] **Step 4: Commit**

---

#### Task 3: 创建 Condition 接口

**Files:**
- Create: `api/src/main/java/com/playerPlugin/playerTaskX/api/model/condition/Condition.java`
- Create: `api/src/main/java/com/playerPlugin/playerTaskX/api/model/condition/PermissionCondition.java`

- [ ] **Step 1: 创建 Condition 接口**

```java
package com.playerPlugin.playerTaskX.api.model.condition;

import org.bukkit.entity.Player;

public interface Condition {
    boolean isMet(Player player);
}
```

- [ ] **Step 2: 创建 PermissionCondition**

```java
package com.playerPlugin.playerTaskX.api.model.condition;

import org.bukkit.entity.Player;

public class PermissionCondition implements Condition {
    private final String permission;

    public PermissionCondition(String permission) {
        this.permission = permission;
    }

    @Override
    public boolean isMet(Player player) {
        return player.hasPermission(permission);
    }
}
```

- [ ] **Step 3: Commit**

---

### Phase 2: 重构 TaskDefinition 和 TaskProgress

#### Task 4: 重构 TaskDefinition

**Files:**
- Modify: `api/src/main/java/com/playerPlugin/playerTaskX/api/model/TaskDefinition.java`

- [ ] **Step 1: 重写 TaskDefinition**

```java
package com.playerPlugin.playerTaskX.api.model;

import com.playerPlugin.playerTaskX.api.model.objective.Objective;
import com.playerPlugin.playerTaskX.api.model.reward.Reward;
import com.playerPlugin.playerTaskX.api.model.condition.Condition;

import java.util.List;

public class TaskDefinition {
    private final String id;
    private final String name;
    private final String description;
    private final List<Objective> objectives;
    private final List<Reward> rewards;
    private final List<Condition> conditions;

    public TaskDefinition(String id, String name, String description,
                          List<Objective> objectives, List<Reward> rewards,
                          List<Condition> conditions) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.objectives = objectives;
        this.rewards = rewards;
        this.conditions = conditions;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<Objective> getObjectives() { return objectives; }
    public List<Reward> getRewards() { return rewards; }
    public List<Condition> getConditions() { return conditions; }
}
```

- [ ] **Step 2: Commit**

---

#### Task 5: 重构 TaskProgress 和 TaskStatus

**Files:**
- Modify: `api/src/main/java/com/playerPlugin/playerTaskX/api/model/TaskProgress.java`
- Modify: `api/src/main/java/com/playerPlugin/playerTaskX/api/Enum/PTXTaskStatus.java`

- [ ] **Step 1: 更新 PTXTaskStatus**

```java
package com.playerPlugin.playerTaskX.api.Enum;

public enum PTXTaskStatus {
    IN_PROGRESS,   // 进行中
    COMPLETED,      // 已完成（可领取奖励）
    CLAIMED,        // 已领取奖励
    ABANDONED       // 已放弃
}
```

- [ ] **Step 2: 重写 TaskProgress**

```java
package com.playerPlugin.playerTaskX.api.model;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TaskProgress {
    private final UUID playerId;
    private final String taskId;
    private PTXTaskStatus status;
    private final long acceptedAt;
    private long completedAt;
    private long claimedAt;
    // Key: objectiveId, Value: current progress amount
    private final Map<String, Integer> objectiveProgress;

    public TaskProgress(UUID playerId, String taskId) {
        this.playerId = playerId;
        this.taskId = taskId;
        this.status = PTXTaskStatus.IN_PROGRESS;
        this.acceptedAt = System.currentTimeMillis();
        this.completedAt = 0;
        this.claimedAt = 0;
        this.objectiveProgress = new ConcurrentHashMap<>();
    }

    public UUID getPlayerId() { return playerId; }
    public String getTaskId() { return taskId; }
    public PTXTaskStatus getStatus() { return status; }
    public void setStatus(PTXTaskStatus status) { this.status = status; }
    public long getAcceptedAt() { return acceptedAt; }
    public Map<String, Integer> getObjectiveProgress() { return objectiveProgress; }

    public int getProgress(String objectiveId) {
        return objectiveProgress.getOrDefault(objectiveId, 0);
    }

    public void setProgress(String objectiveId, int amount) {
        objectiveProgress.put(objectiveId, amount);
    }

    public void incrementProgress(String objectiveId, int delta) {
        objectiveProgress.merge(objectiveId, delta, Integer::sum);
    }
    public long getCompletedAt() { return completedAt; }
    public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
    public long getClaimedAt() { return claimedAt; }
    public void setClaimedAt(long claimedAt) { this.claimedAt = claimedAt; }

    public boolean isCompleted() {
        return status == PTXTaskStatus.COMPLETED;
    }

    public boolean isClaimed() {
        return status == PTXTaskStatus.CLAIMED;
    }
}
```

- [ ] **Step 3: Commit**

---

### Phase 3: 存储层

#### Task 6: 创建存储接口

**Files:**
- Create: `api/src/main/java/com/playerPlugin/playerTaskX/api/service/TaskStorage.java`
- Create: `api/src/main/java/com/playerPlugin/playerTaskX/api/service/ProgressStorage.java`

- [ ] **Step 1: 创建 TaskStorage 接口**

```java
package com.playerPlugin.playerTaskX.api.service;

import com.playerPlugin.playerTaskX.api.model.TaskDefinition;

import java.util.List;
import java.util.Optional;

public interface TaskStorage {
    void save(TaskDefinition task);
    Optional<TaskDefinition> findById(String id);
    List<TaskDefinition> findAll();
    void delete(String id);
}
```

- [ ] **Step 2: 创建 ProgressStorage 接口**

```java
package com.playerPlugin.playerTaskX.api.service;

import com.playerPlugin.playerTaskX.api.model.TaskProgress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgressStorage {
    void save(UUID playerId, TaskProgress progress);
    Optional<TaskProgress> findByPlayerAndTask(UUID playerId, String taskId);
    List<TaskProgress> findByPlayer(UUID playerId);
    void delete(UUID playerId, String taskId);
}
```

- [ ] **Step 3: Commit**

---

#### Task 7: 实现 YAML 存储

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/storage/yaml/YamlTaskStorage.java`
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/storage/yaml/YamlProgressStorage.java`

- [ ] **Step 1: 创建 YamlTaskStorage**

```java
package com.playerPlugin.playerTaskX.storage.yaml;

import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class YamlTaskStorage implements TaskStorage {
    private final File dataFolder;
    private final ObjectMapper mapper;

    public YamlTaskStorage(File dataFolder) {
        this.dataFolder = new File(dataFolder, "tasks");
        this.mapper = new ObjectMapper(new YAMLFactory());
        this.dataFolder.mkdirs();
    }

    @Override
    public void save(TaskDefinition task) {
        // 实现保存逻辑
    }

    @Override
    public Optional<TaskDefinition> findById(String id) {
        File file = new File(dataFolder, id + ".yml");
        if (!file.exists()) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(file, TaskDefinition.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<TaskDefinition> findAll() {
        // 实现查找所有逻辑
        return new ArrayList<>();
    }

    @Override
    public void delete(String id) {
        new File(dataFolder, id + ".yml").delete();
    }
}
```

- [ ] **Step 2: 创建 YamlProgressStorage**

类似实现，使用 `players/{uuid}/{taskId}.yml` 结构

- [ ] **Step 3: Commit**

---

#### Task 8: 实现 SQLite 存储

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/storage/sqlite/SQLiteTaskStorage.java`
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/storage/sqlite/SQLiteProgressStorage.java`

- [ ] **Step 1: 创建 SQLiteTaskStorage**

使用 JDBC 和 SQLite

- [ ] **Step 2: 创建 SQLiteProgressStorage**

- [ ] **Step 3: Commit**

---

#### Task 9: 实现 MySQL 存储

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/storage/mysql/MySQLTaskStorage.java`
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/storage/mysql/MySQLProgressStorage.java`

- [ ] **Step 1: 创建 MySQLTaskStorage**

使用 HikariCP 连接池

- [ ] **Step 2: 创建 MySQLProgressStorage**

- [ ] **Step 3: Commit**

---

#### Task 10: 创建 StorageFactory

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/storage/StorageFactory.java`

- [ ] **Step 1: 创建 StorageFactory**

```java
package com.playerPlugin.playerTaskX.storage;

import com.playerPlugin.playerTaskX.api.Enum.PTXStorgeType;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;
import com.playerPlugin.playerTaskX.api.service.ProgressStorage;
import com.playerPlugin.playerTaskX.storage.yaml.*;
import com.playerPlugin.playerTaskX.storage.sqlite.*;
import com.playerPlugin.playerTaskX.storage.mysql.*;

import java.io.File;

public class StorageFactory {
    public static TaskStorage createTaskStorage(PTXStorgeType type, File dataFolder, MySQLConfig mysqlConfig) {
        return switch (type) {
            case YAML -> new YamlTaskStorage(dataFolder);
            case SQLITE -> new SQLiteTaskStorage(dataFolder);
            case MYSQL -> new MySQLTaskStorage(mysqlConfig);
            default -> throw new IllegalArgumentException("Unknown storage type");
        };
    }

    public static ProgressStorage createProgressStorage(PTXStorgeType type, File dataFolder, MySQLConfig mysqlConfig) {
        return switch (type) {
            case YAML -> new YamlProgressStorage(dataFolder);
            case SQLITE -> new SQLiteProgressStorage(dataFolder);
            case MYSQL -> new MySQLProgressStorage(mysqlConfig);
            default -> throw new IllegalArgumentException("Unknown storage type");
        };
    }

    public static class MySQLConfig {
        public String host;
        public int port;
        public String database;
        public String username;
        public String password;
    }
}
```

- [ ] **Step 2: Commit**

---

### Phase 4: 核心管理器

#### Task 11: 创建 TaskManager

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/manager/TaskManager.java`

- [ ] **Step 1: 创建 TaskManager**

```java
package com.playerPlugin.playerTaskX.manager;

import com.playerPlugin.playerTaskX.api.model.*;
import com.playerPlugin.playerTaskX.api.model.objective.Objective;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;
import com.playerPlugin.playerTaskX.api.service.ProgressStorage;

import org.bukkit.entity.Player;
import java.util.*;

public class TaskManager {
    private final TaskStorage taskStorage;
    private final ProgressStorage progressStorage;
    private final Map<String, TaskDefinition> taskCache = new HashMap<>();
    private final Map<UUID, Map<String, TaskProgress>> playerProgress = new HashMap<>();

    public TaskManager(TaskStorage taskStorage, ProgressStorage progressStorage) {
        this.taskStorage = taskStorage;
        this.progressStorage = progressStorage;
    }

    public void loadTasks() {
        taskStorage.findAll().forEach(task -> taskCache.put(task.getId(), task));
    }

    public List<TaskDefinition> getAvailableTasks(Player player) {
        return taskCache.values().stream()
            .filter(task -> task.getConditions().stream()
                .allMatch(c -> c.isMet(player)))
            .toList();
    }

    public boolean acceptTask(Player player, String taskId) {
        TaskDefinition task = taskCache.get(taskId);
        if (task == null) return false;
        if (task.getConditions().stream().anyMatch(c -> !c.isMet(player))) {
            return false;
        }

        TaskProgress progress = new TaskProgress(player.getUniqueId(), taskId);
        progressStorage.save(player.getUniqueId(), progress);
        playerProgress.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
            .put(taskId, progress);
        return true;
    }

    public void updateProgress(Player player, Objective objective, int amount) {
        Map<String, TaskProgress> progressMap = playerProgress.get(player.getUniqueId());
        if (progressMap == null) return;

        for (TaskProgress progress : progressMap.values()) {
            if (progress.getStatus() != PTXTaskStatus.IN_PROGRESS) continue;
            // 更新对应 objective 的进度
            // 检查是否所有 objectives 都完成
            // 如果完成，设置状态为 COMPLETED
        }
        // 异步保存到 storage
    }

    public boolean claimReward(Player player, String taskId) {
        TaskProgress progress = playerProgress.get(player.getUniqueId())?.get(taskId);
        if (progress == null || !progress.isCompleted()) return false;

        TaskDefinition task = taskCache.get(taskId);
        task.getRewards().forEach(r -> r.grant(player));

        progress.setStatus(PTXTaskStatus.CLAIMED);
        progressStorage.save(player.getUniqueId(), progress);
        return true;
    }

    public TaskProgress getProgress(UUID playerId, String taskId) {
        return playerProgress.computeIfAbsent(playerId, k -> {
            Map<String, TaskProgress> map = new HashMap<>();
            progressStorage.findByPlayer(playerId).forEach(p -> map.put(p.getTaskId(), p));
            return map;
        }).get(taskId);
    }
}
```

- [ ] **Step 2: Commit**

---

#### Task 12: 创建 RewardManager

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/manager/RewardManager.java`

- [ ] **Step 1: 创建 RewardManager**

```java
package com.playerPlugin.playerTaskX.manager;

import com.playerPlugin.playerTaskX.api.model.reward.Reward;
import org.bukkit.entity.Player;
import java.util.List;

public class RewardManager {
    public void grantRewards(Player player, List<Reward> rewards) {
        rewards.forEach(reward -> reward.grant(player));
    }
}
```

- [ ] **Step 2: Commit**

---

### Phase 5: 事件监听

#### Task 13: 重构 TaskRouter

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/event/TaskRouter.java`

- [ ] **Step 1: 重写 TaskRouter**

```java
package com.playerPlugin.playerTaskX.event;

import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class TaskRouter implements Listener {
    private final TaskManager taskManager;

    public TaskRouter(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void dispatch(Event event) {
        // 获取所有在线玩家的所有进行中的任务
        // 检查每个 objective 是否 matchesEvent
        // 如果匹配，更新进度
    }
}
```

- [ ] **Step 2: Commit**

---

#### Task 14: 重构 PlayerJoinHandler（异步加载）

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/event/PlayerJoinHandler.java`

- [ ] **Step 1: 重写 PlayerJoinHandler**

```java
package com.playerPlugin.playerTaskX.event;

import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerJoinHandler implements Listener {
    private final TaskManager taskManager;

    public PlayerJoinHandler(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 异步加载玩家进度
        new BukkitRunnable() {
            @Override
            public void run() {
                taskManager.loadPlayerProgress(player.getUniqueId());
                // 回调主线程更新缓存
            }
        }.runTaskAsynchronously(/* plugin */);
    }
}
```

- [ ] **Step 2: Commit**

---

#### Task 15: 创建 EntityListener（杀怪事件）

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/listener/EntityListener.java`

- [ ] **Step 1: 创建 EntityListener**

```java
package com.playerPlugin.playerTaskX.listener;

import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class EntityListener implements Listener {
    private final TaskManager taskManager;

    public EntityListener(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        taskManager.handleEvent(killer, event);
    }
}
```

- [ ] **Step 2: Commit**

---

#### Task 16: 创建 BlockListener（破坏方块事件）

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/listener/BlockListener.java`

- [ ] **Step 1: 创建 BlockListener**

```java
package com.playerPlugin.playerTaskX.listener;

import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class BlockListener implements Listener {
    private final TaskManager taskManager;

    public BlockListener(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        taskManager.handleEvent(player, event);
    }
}
```

- [ ] **Step 2: Commit**

---

### Phase 6: 命令

#### Task 17: 实现 /task 命令

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/command/TaskCommand.java`

- [ ] **Step 1: 创建 TaskCommand**

```java
package com.playerPlugin.playerTaskX.command;

import com.playerPlugin.playerTaskX.manager.TaskManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class TaskCommand implements CommandExecutor, TabCompleter {
    private final TaskManager taskManager;

    public TaskCommand(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (args.length == 0) {
            // 显示帮助
            return true;
        }

        switch (args[0]) {
            case "list" -> taskManager.getAvailableTasks(player).forEach(task -> {
                player.sendMessage(task.getId() + ": " + task.getName());
            });
            case "accept" -> {
                if (args.length < 2) return false;
                boolean success = taskManager.acceptTask(player, args[1]);
                player.sendMessage(success ? "接受任务成功" : "接受任务失败");
            }
            case "progress" -> {
                // 显示当前进度
            }
            case "claim" -> {
                if (args.length < 2) return false;
                boolean success = taskManager.claimReward(player, args[1]);
                player.sendMessage(success ? "领取奖励成功" : "领取奖励失败");
            }
            case "abandon" -> {
                if (args.length < 2) return false;
                // 放弃任务
            }
        }
        return true;
    }
}
```

- [ ] **Step 2: Commit**

---

#### Task 16: 实现 /taskadmin 命令

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/command/TaskAdminCommand.java`

- [ ] **Step 1: 创建 TaskAdminCommand**

```java
package com.playerPlugin.playerTaskX.command;

public class TaskAdminCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) return false;

        if (args.length == 0) {
            sender.sendMessage("/taskadmin reload - 重载配置");
            return true;
        }

        switch (args[0]) {
            case "reload" -> {
                // 重载配置和任务
            }
        }
        return true;
    }
}
```

- [ ] **Step 2: Commit**

---

### Phase 7: Web 编辑器

#### Task 17: 实现 EditorServer

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/web/EditorServer.java`
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/web/TaskEditorController.java`

- [ ] **Step 1: 创建 EditorServer**

```java
package com.playerPlugin.playerTaskX.web;

import io.javalin.Javalin;
import com.playerPlugin.playerTaskX.manager.TaskManager;

public class EditorServer {
    private final Javalin app;
    private final TaskEditorController controller;

    public EditorServer(TaskManager taskManager) {
        this.controller = new TaskEditorController(taskManager);
        this.app = Javalin.create()
            .config(ctx -> ctx.registerServlet());
        // 配置路由
    }

    public void start(int port) {
        app.start(port);
    }
}
```

- [ ] **Step 2: 创建 TaskEditorController**

```java
package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.manager.TaskManager;
import io.javalin.http.Context;

public class TaskEditorController {
    private final TaskManager taskManager;

    public TaskEditorController(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void getAllTasks(Context ctx) {
        ctx.json(taskManager.getAllTasks());
    }

    public void createTask(Context ctx) {
        // 解析 body，创建任务
    }

    public void updateTask(Context ctx) {
        String id = ctx.pathParam("id");
        // 更新任务
    }

    public void deleteTask(Context ctx) {
        String id = ctx.pathParam("id");
        taskManager.deleteTask(id);
    }
}
```

- [ ] **Step 3: Commit**

---

### Phase 8: 插件入口和集成

#### Task 18: 重构 PlayerTaskX 插件入口

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/PlayerTaskX.java`

- [ ] **Step 1: 重写 PlayerTaskX**

```java
package com.playerPlugin.playerTaskX;

import com.playerPlugin.playerTaskX.api.Enum.PTXStorgeType;
import com.playerPlugin.playerTaskX.api.service.TaskStorage;
import com.playerPlugin.playerTaskX.api.service.ProgressStorage;
import com.playerPlugin.playerTaskX.command.TaskCommand;
import com.playerPlugin.playerTaskX.command.TaskAdminCommand;
import com.playerPlugin.playerTaskX.event.TaskRouter;
import com.playerPlugin.playerTaskX.event.PlayerJoinHandler;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import com.playerPlugin.playerTaskX.manager.RewardManager;
import com.playerPlugin.playerTaskX.storage.StorageFactory;
import com.playerPlugin.playerTaskX.web.EditorServer;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerTaskX extends JavaPlugin {
    private TaskManager taskManager;
    private RewardManager rewardManager;
    private EditorServer editorServer;

    @Override
    public void onEnable() {
        // 1. 加载配置
        saveDefaultConfig();
        PTXStorgeType storageType = PTXStorgeType.valueOf(getConfig().getString("storage.type", "SQLITE"));

        // 2. 初始化存储
        TaskStorage taskStorage;
        ProgressStorage progressStorage;
        if (storageType == PTXStorgeType.MYSQL) {
            StorageFactory.MySQLConfig mysqlConfig = new StorageFactory.MySQLConfig();
            mysqlConfig.host = getConfig().getString("storage.mysql.host", "localhost");
            mysqlConfig.port = getConfig().getInt("storage.mysql.port", 3306);
            mysqlConfig.database = getConfig().getString("storage.mysql.database", "playertaskx");
            mysqlConfig.username = getConfig().getString("storage.mysql.username", "root");
            mysqlConfig.password = getConfig().getString("storage.mysql.password", "");
            taskStorage = StorageFactory.createTaskStorage(storageType, getDataFolder(), mysqlConfig);
            progressStorage = StorageFactory.createProgressStorage(storageType, getDataFolder(), mysqlConfig);
        } else {
            taskStorage = StorageFactory.createTaskStorage(storageType, getDataFolder());
            progressStorage = StorageFactory.createProgressStorage(storageType, getDataFolder());
        }

        // 3. 初始化管理器
        this.taskManager = new TaskManager(taskStorage, progressStorage);
        this.rewardManager = new RewardManager();
        taskManager.loadTasks();

        // 4. 注册命令
        getCommand("task").setExecutor(new TaskCommand(taskManager));
        getCommand("taskadmin").setExecutor(new TaskAdminCommand());

        // 5. 注册事件监听
        getServer().getPluginManager().registerEvents(new EntityListener(taskManager), this);
        getServer().getPluginManager().registerEvents(new BlockListener(taskManager), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinHandler(taskManager), this);

        // 6. 启动 Web 服务器
        int editorPort = getConfig().getInt("editor.port", 8080);
        this.editorServer = new EditorServer(taskManager);
        editorServer.start(editorPort);
    }

    @Override
    public void onDisable() {
        if (editorServer != null) {
            editorServer.stop();
        }
    }
}
```

- [ ] **Step 2: Commit**

---

## 实施顺序

1. Phase 1: API 层基础接口（Objective, Reward, Condition）
2. Phase 2: 重构 TaskDefinition 和 TaskProgress
3. Phase 3: 存储层
4. Phase 4: 核心管理器
5. Phase 5: 事件监听
6. Phase 6: 命令
7. Phase 7: Web 编辑器
8. Phase 8: 插件入口和集成

---

## 验证步骤

每个 Phase 完成后：
1. 运行 `./gradlew build` 确保编译通过
2. 运行 `./gradlew runServer` 启动测试服务器
3. 测试核心流程：创建任务 → 接受任务 → 完成目标 → 领取奖励
