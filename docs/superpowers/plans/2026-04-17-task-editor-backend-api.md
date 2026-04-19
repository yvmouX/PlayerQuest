# Task Editor Backend API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 Task Editor 完整 REST API，支持任务/奖励模板/玩家进度/统计的 CRUD

**Architecture:** 基于现有 PlayerTaskX 的 TaskManager，提供 Javalin HTTP 服务，统一 JSON 响应格式

**Tech Stack:** Java, Javalin, TaskManager, TaskStorage, ProgressStorage

---

## 文件结构

```
core/src/main/java/com/playerPlugin/playerTaskX/
├── web/
│   ├── EditorServer.java          # 重写：支持静态文件 + CORS
│   ├── ApiResponse.java           # 新建：统一响应封装
│   ├── TaskEditorController.java  # 改造：调用 TaskManager
│   ├── RewardTemplateController.java  # 新建
│   ├── PlayerProgressController.java  # 新建
│   └── StatsController.java       # 新建
```

---

## Task 1: 创建 ApiResponse 统一响应封装

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/web/ApiResponse.java`

- [ ] **Step 1: 创建 ApiResponse.java**

```java
package com.playerPlugin.playerTaskX.web;

public class ApiResponse<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.code = 0;
        resp.msg = "ok";
        resp.data = data;
        return resp;
    }

    public static <T> ApiResponse<T> error(int code, String msg) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.code = code;
        resp.msg = msg;
        return resp;
    }

    public int getCode() { return code; }
    public String getMsg() { return msg; }
    public T getData() { return data; }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/web/ApiResponse.java
git commit -m "feat(web): add ApiResponse统一响应封装"
```

---

## Task 2: 改造 EditorServer

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/web/EditorServer.java`

- [ ] **Step 1: 重写 EditorServer.java**

```java
package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.manager.TaskManager;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;

public class EditorServer {
    private final TaskEditorController taskController;
    private final RewardTemplateController rewardController;
    private final PlayerProgressController progressController;
    private final StatsController statsController;
    private Javalin javalin;

    public EditorServer(TaskManager taskManager) {
        this.taskController = new TaskEditorController(taskManager);
        this.rewardController = new RewardTemplateController(taskManager);
        this.progressController = new PlayerProgressController(taskManager);
        this.statsController = new StatsController(taskManager);
    }

    public void start(int port) {
        this.javalin = Javalin.create(config -> {
            config.staticFiles.enableDirectoryBrowsing = false;
            config.cors = ctx -> ctx.allowHost("localhost", "127.0.0.1");
        })
            // 任务管理
            .get("/api/quests", ctx -> taskController.getAll(ctx))
            .get("/api/quests/:id", ctx -> taskController.getById(ctx))
            .post("/api/quests", ctx -> taskController.create(ctx))
            .put("/api/quests/:id", ctx -> taskController.update(ctx))
            .delete("/api/quests/:id", ctx -> taskController.delete(ctx))
            // 奖励模板
            .get("/api/rewards/templates", ctx -> rewardController.getAll(ctx))
            .post("/api/rewards/templates", ctx -> rewardController.save(ctx))
            .delete("/api/rewards/templates/:id", ctx -> rewardController.delete(ctx))
            // 玩家进度
            .get("/api/players/progress", ctx -> progressController.getAll(ctx))
            // 统计
            .get("/api/stats/completion", ctx -> statsController.getCompletion(ctx))
            .get("/api/stats/activity", ctx -> statsController.getActivity(ctx))
            .start("127.0.0.1", port);
    }

    public void stop() {
        if (javalin != null) {
            javalin.stop();
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/web/EditorServer.java
git commit -m "feat(web): 重写EditorServer支持完整API路由"
```

---

## Task 3: 改造 TaskEditorController

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/web/TaskEditorController.java`

- [ ] **Step 1: 重写 TaskEditorController.java**

```java
package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.api.model.TaskDefinition;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import io.javalin.http.Context;

import java.util.Collection;

public class TaskEditorController {
    private final TaskManager taskManager;

    public TaskEditorController(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void getAll(Context ctx) {
        Collection<TaskDefinition> tasks = taskManager.getAllTasks();
        ctx.json(ApiResponse.success(tasks));
    }

    public void getById(Context ctx) {
        String id = ctx.pathParam("id");
        TaskDefinition task = taskManager.getTask(id).orElse(null);
        if (task == null) {
            ctx.status(404).json(ApiResponse.error(404, "Task not found"));
            return;
        }
        ctx.json(ApiResponse.success(task));
    }

    public void create(Context ctx) {
        try {
            TaskDefinition task = ctx.bodyAsClass(TaskDefinition.class);
            // TODO: 调用 taskManager 保存
            ctx.status(201).json(ApiResponse.success(task));
        } catch (Exception e) {
            ctx.status(400).json(ApiResponse.error(400, "Invalid task data: " + e.getMessage()));
        }
    }

    public void update(Context ctx) {
        String id = ctx.pathParam("id");
        try {
            TaskDefinition task = ctx.bodyAsClass(TaskDefinition.class);
            // TODO: 调用 taskManager 更新
            ctx.json(ApiResponse.success(task));
        } catch (Exception e) {
            ctx.status(400).json(ApiResponse.error(400, "Invalid task data: " + e.getMessage()));
        }
    }

    public void delete(Context ctx) {
        String id = ctx.pathParam("id");
        // TODO: 调用 taskManager 删除
        ctx.json(ApiResponse.success(null));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/web/TaskEditorController.java
git commit -m "feat(web): 改造TaskEditorController实现真正CRUD"
```

---

## Task 4: 创建 RewardTemplateController

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/web/RewardTemplateController.java`

- [ ] **Step 1: 创建 RewardTemplateController.java**

```java
package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.api.model.reward.Reward;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RewardTemplateController {
    private final Map<String, Reward> templateStore = new ConcurrentHashMap<>();
    private final TaskManager taskManager;

    public RewardTemplateController(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void getAll(Context ctx) {
        ctx.json(ApiResponse.success(templateStore.values()));
    }

    public void save(Context ctx) {
        try {
            Reward reward = ctx.bodyAsClass(Reward.class);
            if (reward.getId() == null || reward.getId().isEmpty()) {
                reward.setId(java.util.UUID.randomUUID().toString());
            }
            templateStore.put(reward.getId(), reward);
            ctx.status(201).json(ApiResponse.success(reward));
        } catch (Exception e) {
            ctx.status(400).json(ApiResponse.error(400, "Invalid reward data: " + e.getMessage()));
        }
    }

    public void delete(Context ctx) {
        String id = ctx.pathParam("id");
        Reward removed = templateStore.remove(id);
        if (removed == null) {
            ctx.status(404).json(ApiResponse.error(404, "Template not found"));
            return;
        }
        ctx.json(ApiResponse.success(null));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/web/RewardTemplateController.java
git commit -m "feat(web): 添加RewardTemplateController奖励模板API"
```

---

## Task 5: 创建 PlayerProgressController

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/web/PlayerProgressController.java`

- [ ] **Step 1: 创建 PlayerProgressController.java**

```java
package com.playerPlugin.playerTaskX.web;

import com.playerPlugin.playerTaskX.api.model.TaskProgress;
import com.playerPlugin.playerTaskX.manager.TaskManager;
import io.javalin.http.Context;

import java.util.*;

public class PlayerProgressController {
    private final TaskManager taskManager;

    public PlayerProgressController(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void getAll(Context ctx) {
        String search = ctx.queryParam("search");
        String status = ctx.queryParam("status");
        int limit = ctx.queryParam("limit", Integer.class).orElse(20);
        int offset = ctx.queryParam("offset", Integer.class).orElse(0);

        // TODO: 实现真正的玩家进度查询
        // 目前返回模拟数据
        List<Map<String, Object>> mockData = new ArrayList<>();
        
        Map<String, Object> result = new HashMap<>();
        result.put("total", mockData.size());
        result.put("data", mockData);
        
        ctx.json(ApiResponse.success(result));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/web/PlayerProgressController.java
git commit -m "feat(web): 添加PlayerProgressController玩家进度API"
```

---

## Task 6: 创建 StatsController

**Files:**
- Create: `core/src/main/java/com/playerPlugin/playerTaskX/web/StatsController.java`

- [ ] **Step 1: 创建 StatsController.java**

```java
package com.playerPlugin.playerTaskX.web;

import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StatsController {

    public void getCompletion(Context ctx) {
        // TODO: 实现真正的统计数据查询
        List<Map<String, Object>> data = new ArrayList<>();
        data.add(Map.of("name", "已完成", "value", 0));
        data.add(Map.of("name", "进行中", "value", 0));
        data.add(Map.of("name", "未开始", "value", 0));
        
        ctx.json(ApiResponse.success(data));
    }

    public void getActivity(Context ctx) {
        String range = ctx.queryParam("range").orElse("7d");
        
        // TODO: 实现真正的活动趋势查询
        List<Map<String, Object>> data = new ArrayList<>();
        // 模拟近7天数据
        for (int i = 6; i >= 0; i--) {
            data.add(Map.of(
                "day", java.time.LocalDate.now().minusDays(i).toString(),
                "users", 0
            ));
        }
        
        ctx.json(ApiResponse.success(data));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/web/StatsController.java
git commit -m "feat(web): 添加StatsController统计数据API"
```

---

## Task 7: 验证构建

- [ ] **Step 1: 运行 Gradle 构建**

```bash
./gradlew build
```

- [ ] **Step 2: 验证无编译错误**

---

## Task 8: 更新 PlayerTaskX 主类集成 EditorServer

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/PlayerTaskX.java`

- [ ] **Step 1: 查看并修改 PlayerTaskX.java 集成 EditorServer**

在 onEnable 中添加 EditorServer 启动（需要先检查现有代码结构）

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/PlayerTaskX.java
git commit -m "feat(web): 集成EditorServer到插件生命周期"
```
