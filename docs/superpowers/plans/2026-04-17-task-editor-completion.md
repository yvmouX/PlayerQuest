# Task Editor Completion Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 Task Editor 的所有未完成项，包括持久化、统计、部署配置

**Tech Stack:** Java + Javalin (后端), Vue 3 + Vue Flow (前端), Gradle + npm (构建)

---

## Task 1: TaskManager 添加 saveTask/deleteTask 方法

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/manager/TaskManager.java`

- [ ] **Step 1: 添加 saveTask 方法**

在 `getTask` 方法后添加：

```java
public void saveTask(TaskDefinition task) {
    taskStorage.save(task);
    taskCache.put(task.getId(), task);
}

public void deleteTask(String taskId) {
    taskStorage.delete(taskId);
    taskCache.remove(taskId);
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/manager/TaskManager.java
git commit -m "feat(manager): 添加saveTask和deleteTask方法"
```

---

## Task 2: TaskEditorController 改造为真正持久化

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/web/TaskEditorController.java`

- [ ] **Step 1: 改造 create/update/delete 方法**

将 TODO 注释替换为真正调用：

```java
public void create(Context ctx) {
    try {
        TaskDefinition task = ctx.bodyAsClass(TaskDefinition.class);
        taskManager.saveTask(task);
        ctx.status(201).json(ApiResponse.success(task));
    } catch (Exception e) {
        ctx.status(400).json(ApiResponse.error(400, "Invalid task data: " + e.getMessage()));
    }
}

public void update(Context ctx) {
    String id = ctx.pathParam("id");
    try {
        TaskDefinition task = ctx.bodyAsClass(TaskDefinition.class);
        taskManager.saveTask(task);
        ctx.json(ApiResponse.success(task));
    } catch (Exception e) {
        ctx.status(400).json(ApiResponse.error(400, "Invalid task data: " + e.getMessage()));
    }
}

public void delete(Context ctx) {
    String id = ctx.pathParam("id");
    taskManager.deleteTask(id);
    ctx.json(ApiResponse.success(null));
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/web/TaskEditorController.java
git commit -m "feat(web): TaskEditorController实现真正持久化"
```

---

## Task 3: PlayerProgressController 实现真正的玩家进度查询

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/web/PlayerProgressController.java`

- [ ] **Step 1: 实现真正的玩家进度查询**

需要：
1. 获取所有玩家进度（通过 ProgressStorage.findAll() 或遍历缓存）
2. 支持 search 参数（按玩家名称或 UUID 筛选）
3. 支持 status 参数筛选
4. 支持分页

```java
public void getAll(Context ctx) {
    String search = ctx.queryParam("search");
    String status = ctx.queryParam("status");
    
    int limit = 20;
    int offset = 0;
    try {
        String limitStr = ctx.queryParam("limit");
        if (limitStr != null) limit = Integer.parseInt(limitStr);
    } catch (NumberFormatException ignored) {}
    try {
        String offsetStr = ctx.queryParam("offset");
        if (offsetStr != null) offset = Integer.parseInt(offsetStr);
    } catch (NumberFormatException ignored) {}

    // TODO: 调用 progressStorage.findAll() 获取所有进度
    // 过滤 search 和 status
    // 分页处理
    
    List<Map<String, Object>> result = new ArrayList<>();
    // 构建分页结果
    
    ctx.json(ApiResponse.success(result));
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/web/PlayerProgressController.java
git commit -m "feat(web): PlayerProgressController实现真正玩家进度查询"
```

---

## Task 4: StatsController 实现真正的统计数据查询

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/web/StatsController.java`

- [ ] **Step 1: 实现 getCompletion 统计**

统计任务完成状态分布：
- 已完成数量
- 进行中数量
- 未开始数量

```java
public void getCompletion(Context ctx) {
    // TODO: 调用 taskManager 获取所有任务
    // 遍历 playerProgressCache 或 progressStorage 统计状态分布
    
    List<Map<String, Object>> data = new ArrayList<>();
    data.add(Map.of("name", "已完成", "value", completedCount));
    data.add(Map.of("name", "进行中", "value", inProgressCount));
    data.add(Map.of("name", "未开始", "value", notStartedCount));
    
    ctx.json(ApiResponse.success(data));
}
```

- [ ] **Step 2: 实现 getActivity 活动趋势**

```java
public void getActivity(Context ctx) {
    String range = ctx.queryParam("range").orElse("7d");
    int days = range.equals("30d") ? 30 : 7;
    
    // TODO: 统计每天的活跃玩家数（根据 progressStorage 或缓存）
    
    List<Map<String, Object>> data = new ArrayList<>();
    for (int i = days - 1; i >= 0; i--) {
        data.add(Map.of(
            "day", java.time.LocalDate.now().minusDays(i).toString(),
            "users", dailyActiveUsers
        ));
    }
    
    ctx.json(ApiResponse.success(data));
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/web/StatsController.java
git commit -m "feat(web): StatsController实现真正统计数据查询"
```

---

## Task 5: RewardTemplateController 持久化改造

**Files:**
- Modify: `core/src/main/java/com/playerPlugin/playerTaskX/web/RewardTemplateController.java`

- [ ] **Step 1: 实现持久化**

需要创建 RewardTemplateStorage 接口或使用文件存储：
- 在 dataFolder 下创建 `reward-templates.yml` 存储模板

```java
public class RewardTemplateController {
    private final Map<String, RewardDefinition> templateStore = new ConcurrentHashMap<>();
    private final Path templateFile;
    
    public RewardTemplateController() {
        // 初始化时加载已有模板
        this.templateFile = Paths.get(dataFolder, "reward-templates.yml");
        loadTemplates();
    }
    
    private void loadTemplates() {
        // 从文件加载
    }
    
    private void saveTemplates() {
        // 保存到文件
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/playerPlugin/playerTaskX/web/RewardTemplateController.java
git commit -m "feat(web): RewardTemplateController实现模板持久化"
```

---

## Task 6: 创建 nginx/caddy 部署配置示例

**Files:**
- Create: `docs/deployment/nginx.conf`
- Create: `docs/deployment/caddy/Caddyfile`

- [ ] **Step 1: 创建 nginx.conf**

```nginx
server {
    listen 443 ssl;
    server_name task-editor.example.com;
    
    ssl_certificate /etc/ssl/certs/server.crt;
    ssl_certificate_key /etc/ssl/private/server.key;
    
    # 认证
    auth_basic "PlayerTaskX Editor";
    auth_basic_user_file /etc/nginx/.htpasswd;
    
    location /api/ {
        proxy_pass http://127.0.0.1:22222;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    
    location / {
        proxy_pass http://127.0.0.1:22222;
        proxy_set_header Host $host;
    }
}
```

- [ ] **Step 2: 创建 Caddyfile**

```caddy
task-editor.example.com {
    basicauth /* {
        admin JDJhJDEwJ...
    }
    
    reverse_proxy /api/* localhost:22222
    reverse_proxy /* localhost:22222
}
```

- [ ] **Step 3: Commit**

```bash
git add docs/deployment/
git commit -m "docs: 添加nginx和caddy部署配置示例"
```

---

## Task 7: 前端构建产物嵌入插件

**Files:**
- Modify: `core/build.gradle.kts`
- Create: `core/src/main/resources/web/` (或类似目录)

- [ ] **Step 1: 修改 build.gradle.kts 添加复制任务**

```kotlin
tasks.register<Copy>("copyFrontendBuild") {
    from("${project.rootDir}/task-editor-vue/dist")
    into("${projectDir}/src/main/resources/web")
}
```

- [ ] **Step 2: 修改 EditorServer 添加静态文件服务**

```java
public void start(int port) {
    this.javalin = Javalin.create(config -> {
        config.staticFiles.add("/web");
    })
    // ... routes
    .start("127.0.0.1", port);
}
```

- [ ] **Step 3: Commit**

```bash
git add core/build.gradle.kts
git commit -m "feat: 前端构建产物嵌入插件"
```

---

## Task 8: 验证构建

- [ ] **Step 1: 运行 Gradle 构建**

```bash
./gradlew build
```

- [ ] **Step 2: 运行 npm build**

```bash
cd task-editor-vue && npm run build
```

- [ ] **Step 3: 验证构建产物在正确位置**
