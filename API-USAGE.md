# PlayerTaskX API 使用指南

## 📖 简介

PlayerTaskX 提供了完整的 API 接口，允许其他插件与 PlayerTaskX 进行交互，创建任务、管理进度、监听事件等。

## 🔧 依赖配置

### Maven

```xml
<dependencies>
    <dependency>
        <groupId>com.playerPlugin</groupId>
        <artifactId>playertaskx-api</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Gradle (Groovy)

```groovy
dependencies {
    compileOnly 'com.playerPlugin:playertaskx-api:1.0.0'
}
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    compileOnly("com.playerPlugin:playertaskx-api:1.0.0")
}
```

### 本地安装

在 PlayerTaskX 项目根目录运行：

```bash
./gradlew :api:publishToMavenLocal
```

## 📝 在 plugin.yml 中声明依赖

```yaml
name: YourPlugin
version: 1.0.0
depend: [PlayerTaskX]  # 硬依赖
# 或
softdepend: [PlayerTaskX]  # 软依赖
```

## 🚀 基本使用

### 1. 获取 API 实例

```java
import com.playerPlugin.playerTaskX.api.PlayerTaskXAPI;

public class YourPlugin extends JavaPlugin {
    
    private PlayerTaskXAPI taskAPI;
    
    @Override
    public void onEnable() {
        // 检查 PlayerTaskX 是否已加载
        if (!getServer().getPluginManager().isPluginEnabled("PlayerTaskX")) {
            getLogger().warning("PlayerTaskX 未安装或未启用!");
            return;
        }
        
        // 获取 API 实例
        try {
            taskAPI = PlayerTaskXAPI.getInstance();
            getLogger().info("成功连接到 PlayerTaskX API v" + taskAPI.getApiVersion());
        } catch (IllegalStateException e) {
            getLogger().severe("无法获取 PlayerTaskX API: " + e.getMessage());
            return;
        }
    }
}
```

### 2. 为玩家创建任务进度

```java
import org.bukkit.entity.Player;

public void startTaskForPlayer(Player player, String taskId) {
    boolean success = taskAPI.createTaskProgress(player, taskId);
    if (success) {
        player.sendMessage("§a任务已开始!");
    } else {
        player.sendMessage("§c无法开始任务，任务可能不存在。");
    }
}
```

### 3. 更新任务进度

```java
import java.util.UUID;

// 设置进度为特定值
public void setProgress(UUID playerId, String taskId, int progress) {
    boolean success = taskAPI.updateTaskProgress(playerId, taskId, progress);
    if (success) {
        getLogger().info("任务进度已更新: " + progress);
    }
}

// 增加进度
public void addProgress(UUID playerId, String taskId, int amount) {
    boolean success = taskAPI.incrementTaskProgress(playerId, taskId, amount);
    if (success) {
        getLogger().info("任务进度已增加: " + amount);
    }
}
```

### 4. 查询任务信息

```java
import com.playerPlugin.playerTaskX.api.task.ITaskDefinition;
import com.playerPlugin.playerTaskX.api.task.ITaskProgress;
import java.util.Optional;

// 获取任务定义
public void getTaskInfo(String taskId) {
    Optional<ITaskDefinition> taskDef = taskAPI.getTaskDefinition(taskId);
    taskDef.ifPresent(task -> {
        getLogger().info("任务名称: " + task.getName());
        getLogger().info("任务描述: " + task.getDescription());
        getLogger().info("目标数量: " + task.getTargetAmount());
    });
}

// 获取玩家任务进度
public void checkPlayerProgress(UUID playerId, String taskId) {
    Optional<ITaskProgress> progress = taskAPI.getTaskProgress(playerId, taskId);
    progress.ifPresent(p -> {
        getLogger().info("当前进度: " + p.getCurrentProgress());
        getLogger().info("任务状态: " + p.getStatus());
        getLogger().info("是否完成: " + p.isCompleted());
    });
}

// 检查任务是否完成
public boolean isTaskDone(UUID playerId, String taskId) {
    return taskAPI.isTaskCompleted(playerId, taskId);
}
```

### 5. 完成和重置任务

```java
// 完成任务
public void finishTask(UUID playerId, String taskId) {
    boolean success = taskAPI.completeTask(playerId, taskId);
    if (success) {
        getLogger().info("任务已完成!");
    }
}

// 重置任务
public void resetPlayerTask(UUID playerId, String taskId) {
    boolean success = taskAPI.resetTask(playerId, taskId);
    if (success) {
        getLogger().info("任务已重置!");
    }
}
```

## 🔔 事件监听

### 创建事件监听器

```java
import com.playerPlugin.playerTaskX.api.event.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class MyTaskListener implements TaskEventListener {
    
    @Override
    public void onTaskStart(TaskStartEvent event) {
        Player player = Bukkit.getPlayer(event.getPlayerId());
        if (player != null) {
            player.sendMessage("§a你开始了任务: " + event.getTaskId());
        }
    }
    
    @Override
    public void onTaskProgress(TaskProgressEvent event) {
        Player player = Bukkit.getPlayer(event.getPlayerId());
        if (player != null) {
            double percentage = event.getProgressPercentage();
            player.sendMessage(String.format("§e任务进度: %.1f%% (%d/%d)", 
                percentage, 
                event.getNewProgress(), 
                event.getTargetProgress()));
        }
    }
    
    @Override
    public void onTaskComplete(TaskCompleteEvent event) {
        Player player = Bukkit.getPlayer(event.getPlayerId());
        if (player != null) {
            player.sendMessage("§6恭喜! 你完成了任务: " + event.getTaskId());
            // 给予额外奖励
            player.sendMessage("§a你获得了额外奖励!");
        }
    }
    
    @Override
    public void onTaskFail(TaskFailEvent event) {
        Player player = Bukkit.getPlayer(event.getPlayerId());
        if (player != null) {
            player.sendMessage("§c任务失败: " + event.getReason());
        }
    }
}
```

### 注册事件监听器

```java
public class YourPlugin extends JavaPlugin {
    
    private PlayerTaskXAPI taskAPI;
    private MyTaskListener taskListener;
    
    @Override
    public void onEnable() {
        taskAPI = PlayerTaskXAPI.getInstance();
        
        // 创建并注册监听器
        taskListener = new MyTaskListener();
        taskAPI.registerEventListener(taskListener);
        
        getLogger().info("任务事件监听器已注册!");
    }
    
    @Override
    public void onDisable() {
        // 注销监听器
        if (taskAPI != null && taskListener != null) {
            taskAPI.unregisterEventListener(taskListener);
        }
    }
}
```

## 🎯 高级用法

### 批量操作

```java
import com.playerPlugin.playerTaskX.api.task.ITaskDefinition;
import java.util.List;

// 获取所有任务
public void listAllTasks() {
    List<ITaskDefinition> tasks = taskAPI.getAllTaskDefinitions();
    getLogger().info("共有 " + tasks.size() + " 个任务:");
    for (ITaskDefinition task : tasks) {
        getLogger().info("- " + task.getId() + ": " + task.getName());
    }
}

// 获取玩家所有任务进度
public void listPlayerTasks(UUID playerId) {
    List<ITaskProgress> progressList = taskAPI.getAllTaskProgress(playerId);
    getLogger().info("玩家 " + playerId + " 有 " + progressList.size() + " 个任务:");
    for (ITaskProgress progress : progressList) {
        getLogger().info("- 任务 " + progress.getTaskId() + 
                       ": " + progress.getCurrentProgress() + 
                       " (" + progress.getStatus() + ")");
    }
}
```

### 自定义任务进度跟踪

```java
public class TaskTracker implements TaskEventListener {
    
    private final Map<UUID, Map<String, Integer>> playerProgress = new HashMap<>();
    
    @Override
    public void onTaskProgress(TaskProgressEvent event) {
        UUID playerId = event.getPlayerId();
        String taskId = event.getTaskId();
        int newProgress = event.getNewProgress();
        
        // 记录进度
        playerProgress
            .computeIfAbsent(playerId, k -> new HashMap<>())
            .put(taskId, newProgress);
        
        // 检查是否达到特定里程碑
        if (newProgress >= event.getTargetProgress() * 0.5 
            && event.getOldProgress() < event.getTargetProgress() * 0.5) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage("§e你已经完成了任务的一半!");
            }
        }
    }
}
```

### 任务奖励拦截

```java
public class RewardBooster implements TaskEventListener {
    
    private final PlayerTaskXAPI api;
    
    public RewardBooster(PlayerTaskXAPI api) {
        this.api = api;
    }
    
    @Override
    public void onTaskComplete(TaskCompleteEvent event) {
        Player player = Bukkit.getPlayer(event.getPlayerId());
        if (player == null) return;
        
        // 检查玩家是否有特殊权限
        if (player.hasPermission("youpplugin.vip")) {
            // VIP 玩家获得双倍奖励
            player.sendMessage("§6[VIP] 你获得了双倍任务奖励!");
            
            // 这里可以给予额外奖励
            // 例如：给钱、物品等
        }
    }
}
```

## 📊 API 接口概览

### PlayerTaskXAPI 主要方法

| 方法 | 描述 |
|------|------|
| `createTask(ITaskDefinition)` | 创建新任务 |
| `deleteTask(String)` | 删除任务 |
| `getTaskDefinition(String)` | 获取任务定义 |
| `getAllTaskDefinitions()` | 获取所有任务定义 |
| `createTaskProgress(Player, String)` | 为玩家创建任务进度 |
| `getTaskProgress(UUID, String)` | 获取玩家任务进度 |
| `getAllTaskProgress(UUID)` | 获取玩家所有任务进度 |
| `updateTaskProgress(UUID, String, int)` | 更新任务进度 |
| `incrementTaskProgress(UUID, String, int)` | 增加任务进度 |
| `completeTask(UUID, String)` | 完成任务 |
| `resetTask(UUID, String)` | 重置任务 |
| `isTaskCompleted(UUID, String)` | 检查任务是否完成 |
| `registerEventListener(TaskEventListener)` | 注册事件监听器 |
| `unregisterEventListener(TaskEventListener)` | 注销事件监听器 |
| `reload()` | 重新加载配置 |
| `getApiVersion()` | 获取 API 版本 |

### 事件类型

| 事件类 | 触发时机 |
|--------|----------|
| `TaskStartEvent` | 玩家开始任务时 |
| `TaskProgressEvent` | 任务进度更新时 |
| `TaskCompleteEvent` | 任务完成时 |
| `TaskFailEvent` | 任务失败时 |

## ⚠️ 注意事项

1. **依赖检查**: 始终在 `onEnable()` 中检查 PlayerTaskX 是否已加载
2. **异常处理**: API 调用可能返回 `false` 或空的 `Optional`，请妥善处理
3. **线程安全**: 所有 API 调用都是线程安全的
4. **事件注销**: 在插件禁用时记得注销事件监听器
5. **版本兼容**: 检查 API 版本以确保兼容性

## 🔗 完整示例

```java
package com.example.myplugin;

import com.playerPlugin.playerTaskX.api.PlayerTaskXAPI;
import com.playerPlugin.playerTaskX.api.event.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MyPlugin extends JavaPlugin implements TaskEventListener {
    
    private PlayerTaskXAPI taskAPI;
    
    @Override
    public void onEnable() {
        // 检查依赖
        if (!setupTaskAPI()) {
            getLogger().severe("无法加载 PlayerTaskX API，禁用插件!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // 注册事件监听
        taskAPI.registerEventListener(this);
        
        getLogger().info("MyPlugin 已启用!");
    }
    
    @Override
    public void onDisable() {
        if (taskAPI != null) {
            taskAPI.unregisterEventListener(this);
        }
    }
    
    private boolean setupTaskAPI() {
        if (!getServer().getPluginManager().isPluginEnabled("PlayerTaskX")) {
            return false;
        }
        
        try {
            taskAPI = PlayerTaskXAPI.getInstance();
            getLogger().info("已连接到 PlayerTaskX API v" + taskAPI.getApiVersion());
            return true;
        } catch (Exception e) {
            getLogger().severe("获取 API 失败: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public void onTaskComplete(TaskCompleteEvent event) {
        Player player = getServer().getPlayer(event.getPlayerId());
        if (player != null) {
            player.sendMessage("§a恭喜完成任务!");
        }
    }
    
    @Override
    public void onTaskProgress(TaskProgressEvent event) {
        Player player = getServer().getPlayer(event.getPlayerId());
        if (player != null && event.getProgressPercentage() >= 100) {
            player.sendMessage("§e任务即将完成!");
        }
    }
}
```

## 📞 支持

如有问题或建议，请在 GitHub 提交 Issue。
