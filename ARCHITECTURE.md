# PlayerTaskX 重构架构

> 本文件是重构的设计依据。允许修改架构，不考虑旧版本兼容性。
> 核心原则：**代码简洁、扩展点收敛**——新增一种任务目标/奖励类型，应当只写一个类，
> 不改引擎、不改 GUI、不改网页编辑器。

---

## 1. 总体结构

```
PlayerTaskX/
├── api/          对外暴露的接口与数据模型（其它插件/扩展依赖此模块）
├── core/         实现 + 引擎 + 存储 + GUI + 命令 + 内置网页编辑器
├── task-editor-vue/   网页编辑器前端（Vue 3 + Vite）
└── YLib/         子模块：调度器 / 日志 / 配置 / 命令 / 消息 基础设施
```

`api` 不含实现，`core` 依赖 `api`。网页编辑器的静态资源由 `core:processResources`
从 `task-editor-vue/dist` 拷贝进 jar。

### 依赖策略

| 依赖 | 用途 | 作用域 |
|---|---|---|
| `spigot-api` | 服务端 API | compileOnly |
| YLib（复合构建） | 调度器/日志/配置/命令/消息 | implementation |
| `adventure-text-minimessage` + `serializer-legacy`/`plain` | MiniMessage 文本 | 由 YLib 以 `api` 提供（4.x，Java 8 字节码）；**shadow 时 relocate** 避免与 Paper 原生 Adventure 冲突 |
| `sqlite-jdbc` / `mysql-connector-java` | 存储 | implementation / compileOnly |
| `HikariCP` | MySQL 连接池 | implementation |
| `javalin` (+openapi/swagger/redoc) | 内置网页编辑器 HTTP 服务 | implementation |
| `VaultAPI` / `playerpoints` | 金币 / 点券 | compileOnly（软依赖） |
| `placeholderapi` | 变量 | compileOnly（软依赖） |

**不再使用 Jackson 作为存储序列化**：任务定义直接入库（关系表 + JSON 列由数据库方言处理），
消除「一份模型两套序列化」的重复。Javalin 自带 JSON 用于 Web 传输。

---

## 2. 数据模型（扁平：任务 = 多目标 + 多奖励）

```
Quest                    任务定义（静态，由配置/网页编辑器维护）
├── id, name, description, icon, category
├── type: DAILY | NORMAL
├── objectives: List<QuestObjective>
├── rewards:    List<QuestReward>
└── refreshCost（刷新费用，仅 DAILY）

QuestObjective           任务目标（一份「配置数据」，不是行为）
├── type: ObjectiveType   ← 字符串键，指向注册表
└── properties: Map<String, Object>   该类型自己的配置（schema 由类型自描述）

QuestReward              任务奖励
├── type: RewardType      ← 字符串键，指向注册表
└── properties: Map<String, Object>

PlayerQuest              玩家进行中的任务（运行期状态）
├── playerId, questId, assignedAt, expiresAt
├── type: DAILY | NORMAL
├── completed: boolean
└── progress: Map<Integer, Integer>   目标下标 → 当前计数
```

**为什么 properties 用 Map 而不是为每种类型建子类**：
类型是数据而非代码——配置、数据库、网页编辑器、GUI 四处都要表达「某类型的参数」，
统一成 `Map + 类型自描述的 schema` 后，这四处只需要一份通用实现。
`ObjectiveType` / `RewardType` 提供 `schema()`，前端与 GUI 据此**自动生成表单**，
因此新增类型无需改动任何 UI 代码。

---

## 3. 三个扩展点（重构的核心）

### 3.1 目标类型 `ObjectiveType`（api 模块）

```java
public interface ObjectiveType {
    String id();                        // 如 "break_block"
    String displayName();
    List<ConfigField> schema();         // 该类型的配置字段（供编辑器/GUI 生成表单）
    ObjectiveMatcher matcher();         // 判定逻辑
}

public interface ObjectiveMatcher {
    /** 事件是否命中该目标；返回递增量（0 表示不命中） */
    int match(ProgressContext context, Map<String, Object> properties);
}
```

内置 14 种目标：`craft` 合成、`break_block` 挖掘、`fish` 垂钓、`place_block` 放置、
`consume` 消耗、`kill` 击杀、`submit` 提交、`enchant` 附魔、`shear` 剪切、
`breed` 繁殖、`command` 命令、`interact` 交互、`chat` 发言。

### 3.2 奖励类型 `RewardType`

```java
public interface RewardType {
    String id();                        // 如 "money"
    String displayName();
    List<ConfigField> schema();
    void grant(RewardContext context);  // 发放
}
```

内置：`money` 金币(Vault)、`points` 点券(PlayerPoints)、`item` 物品、
`command` 自定义命令。任务币本期不实现。

### 3.3 进度事件 `ProgressContext`（core，唯一与 Bukkit 事件耦合处）

```java
public final class ProgressContext {   // 由 Bukkit 监听器构造，引擎只认它
    UUID playerId; Player player;
    Trigger trigger;                     // BREAK_BLOCK / CRAFT / FISH / ...
    String target;                       // 方块/实体/物品/命令名等，可为 null
    int amount;
    String extra;                        // 附加信息（如提交物品的槽位、发言内容）
}
```

`core/listener/` 下每个 Bukkit 事件对应一个极薄监听器，仅负责把事件翻译成
`ProgressContext` 并投递给 `ProgressService`。**判定逻辑一律不写在监听器里**。

数据流：

```
Bukkit 事件 → GameListener → ProgressContext
                                    ↓
                        ProgressService.match(player, ctx)
                                    ↓  遍历玩家进行中的任务 × 目标 × 类型匹配器
                        命中 → 累加进度 → 存库
                                    ↓
                     actionbar 推送进度；目标全满 → title 提醒 + 发放奖励 → 标记完成
```

---

## 4. 存储

### 4.1 抽象

```java
public interface Database extends AutoCloseable {
    void execute(String sql, Object... params);
    <T> List<T> query(String sql, RowMapper<T> mapper, Object... params);
    long insert(String sql, Object... params);          // 返回自增主键
    <T> T transaction(SqlFunction<T> work);
    Dialect dialect();                                   // MYSQL | SQLITE
}
```

两种实现（`JdbcDatabase` 由方言参数化）：`SqliteDatabase`、`MysqlDatabase`（HikariCP 池）。
方言负责 `AUTO_INCREMENT` vs `AUTOINCREMENT`、`ON DUPLICATE KEY` vs `ON CONFLICT`、
自增主键获取等差异。

### 4.2 表结构

```sql
quest(id TEXT PK, name, description, icon, category, type, refresh_cost, enabled, sort_order)
quest_objective(quest_id FK, idx INT, type TEXT, properties TEXT)      -- properties 为 JSON
quest_reward(quest_id FK, idx INT, type TEXT, properties TEXT)
player_quest(player_id, quest_id, type, assigned_at, expires_at, completed, progress TEXT,
             PRIMARY KEY(player_id, quest_id))
daily_state(player_id PK, assigned_at)          -- 记录当日是否已发放，用于跨天刷新
meta(key PK, value)                             -- schema 版本等
```

约定：**所有 SQL 收敛在 `storage/` 包**，其它包不得出现 SQL 字符串。

---

## 5. 多语言与文本

### 5.1 多语言：复用 YLib，不重复造

YLib 的 `MessageService` 已具备完整能力，**直接使用**：

```java
MessageService messages = ylib.createMessageService(MessageSettings.builder()
        .defaultLanguage("zh_CN")
        .availableLanguages("zh_CN", "en")
        .filePattern("lang_%s.yml")     // 默认是 lang_%s.yml，即 lang/lang_zh_CN.yml
        .languageFolder("lang")
        .useClientLocale(true)          // 玩家语言优先，控制台用全局默认
        .prefixKey("prefix")
        .build());
messages.send(player, "quest.completed", questName);
```

它自带：jar 内默认文件 ↔ 用户文件的**双向同步**（用户缺的键自动补齐并写回注释）、
按玩家客户端语言选语言、`{0} {1}` 数字占位符、`&` 颜色码转换。

⚠️ **不要为多语言再写一套 YAML 加载器**——那是重复实现。

### 5.2 文本渲染：MiniMessage 优先，兼容 `&` 与 `§`

**渲染在 YLib 里，不在本插件里**（`cn.yvmou.ylib.text.TextRenderer`）：

```java
String render(String raw);   // 输出 § 色码，MiniMessage 标签 / & 码 / § 码可任意混排
Component parse(String raw); // 需要 Adventure 原生组件时用
String strip(String raw);    // 去掉全部格式，仅留纯文本
```

YLib 的 `MessageService` 所有出口（`raw` / `send` / `prefix`）都已经是渲染好的 `§` 色码，
因此**插件侧不需要也不应该再包一层**——早期这里有一个 `LangMessageService` 包装器，
把 YLib 的输出再过一遍 `TextRenderer`，那是因为 YLib 只做 `&` → `§` 转换。
渲染能力上移到 YLib 后该包装器连同插件自己的 `TextRenderer`、`TextUpgrader` 一并删除。

Adventure 依赖由 **YLib 的 core 模块**以 `api` 依赖提供（版本 4.26.1）。
选择 4.x 而非 5.x 是刻意的：**4.x 全线是 Java 8 字节码，5.x 需要 Java 21**，
因此 YLib 的 `api`/`core` 得以继续停留在 Java 8，不必为了文本渲染抬升整个库的门槛。
插件侧仍在自己的 shadowJar 里 `relocate("net.kyori", ...)`（Spigot 无 Adventure；
Paper 自带，relocate 后不与服务端原生类冲突）。

**渲染必须一次性完成，不能分段**。三个实测事实决定了这一点：

1. MiniMessage 遇到 `§` 会抛 `ParsingExceptionImpl: Legacy formatting codes have been detected`，
   而且是**整串**放弃解析（不是跳过那一段）；
2. `LegacyComponentSerializer` 的 round-trip 会把 `§` 码原样写回，无法用来「清洗」残留颜色码；
3. 它对普通文本里的 `<yellow>` 一律当字面量。

因此 `TextRenderer` 先把 `&` / `§` 码（含 `&#RRGGBB` 与 `§x§R§R…` 两种十六进制写法）
统一翻译成 MiniMessage 标签，再用 MiniMessage 渲染一次。
另外 `legacySection()` 默认会把十六进制颜色**静默降级**成最近的 16 色之一，
所以序列化器必须显式开 `hexColors()`。

**失败时宁可显示原文，也不显示标签**：标签写法非法（未闭合、未知标签）时退化为纯文本，
不把 `<red>` 这样的内部语法泄漏给玩家。

### 5.3 约束

- 玩家可见文本：短句走 `MessageService`（可被用户改语言文件），
  任务名/描述/目标说明等**任务数据自带文本**走 `TextRenderer`。
- PlaceholderAPI 扩展暴露：当前任务数、某任务进度、进度百分比、可刷新次数等。

---

## 6. 每日任务

- 配置 `daily.pool`（任务 ID 列表）、`daily.amount`（每人每日抽取数量）、`daily.reset-at`（默认 04:00）。
- 发放规则：**全局池 + 按玩家抽取**，种子 = `hash(playerId, 日期)`，保证同一天重登结果一致。
- 刷新：`/ptx refresh`，消耗配置的货币（Vault 金币或点券），**只重抽该玩家自己的列表**，
  消耗与次数记录在 `daily_state`，可配置每日刷新上限与递增费用。
- 跨天检测：登录时与定时任务中检查 `daily_state.assigned_at`，过期则重抽。

---

## 7. 界面

### 7.1 玩家 GUI（`core/gui/`）

通用菜单框架（`Menu` / `MenuItem` / `ClickAction`，用 `InventoryHolder` 区分归属），
在此之上实现：每日任务列表、任务详情（多目标进度 + 多奖励预览）、任务分类浏览、领取奖励。

### 7.2 管理 GUI

任务列表分页浏览、任务详情、目标编辑、奖励编辑、启用/禁用、手动重载。
编辑能力由 `ConfigField` schema 驱动生成表单，新增目标/奖励类型无需改 GUI 代码。

### 7.3 网页编辑器

Javalin 提供 REST + 静态资源（`/` 返回 Vite 构建产物）：

```
GET    /api/quests            列表          POST   /api/quests          新建/覆盖
GET    /api/quests/{id}       详情          DELETE /api/quests/{id}      删除
GET    /api/schema             目标/奖励类型的字段 schema（驱动前端动态表单）
GET    /api/catalog            当前版本支持的物品与实体（图标/材质选择器，含中英文名）
GET    /api/presets            目标与奖励预设（编辑器的便利设施，引擎不认它）
POST   /api/presets/{kind}     保存预设，kind ∈ {objectives, rewards}
DELETE /api/presets/{kind}/{id} 删除预设
GET    /api/langs              语言文件读写
GET    /api/stats              统计
```

前端：任务列表 + 表单式编辑器（由 schema 动态渲染目标与奖励配置），
替代原先的「拖节点连线」图谱编辑器。

**素材目录的版本适配**：`/api/catalog` 遍历运行期的 `Material` / `EntityType` 枚举，
因此返回的天然就是「当前服务端支持的项」，不需要维护任何版本对照数据；
英文名读服务端 jar 里的 `en_us.json`，中文名是插件内置的精选表
（中文译名只存在于客户端资源里，服务端无从获取），未收录项回退英文名。

**预设不在数据库里**：预设只是编辑器的便利设施，运行时引擎完全不认识它，
因此放在 `plugins/playerTaskX/presets.json`——便于手工编辑、随配置备份，
也避免为了一个辅助功能去动数据库表结构。

---

## 8. 构建与验证

- 事件 → 进度累加 → 达标发奖 的核心链路必须有单元测试（不依赖服务端）。
- 存储层对 SQLite 做集成测试（临时文件库），MySQL 走同一套 SQL 生成逻辑。
- `./gradlew build` 必须通过；打包后由 `start-folia.ps1` 启动服务端做冒烟验证
  （构建 → 复制产物到 `run/plugins` → 启动 `run/folia-*.jar`）。

---

## 9. YLib 能力边界（源码审计结论，写代码前必读）

**YLib 提供**：跨平台调度器、注解配置、注解命令、多语言消息服务、极简 logger。

**YLib 不提供**（必须自写，不要再去找）：GUI / Inventory / ItemStack 构建器、
物品序列化、任何持久化（无 SQLite/MySQL/JSON 存储、无 cache/DAO）、事件总线、
PlaceholderAPI 支持、MiniMessage / Adventure、反射工具、计分板/BossBar/ActionBar 封装。

**必须绕开的坑（源码核实）**：

1. **`@Permission` 注解不存在**——YLib 文档里出现过，是错的。权限一律写
   `@Command(permission=...)` / `@SubCommand(permission=...)`。
2. **配置不支持顶层嵌套 POJO**：`@ConfigValue` 字段若直接是自定义类型会抛
   `IllegalArgumentException`。嵌套对象**只能放在 `Map<String, Pojo>` 里**。
   `Map<String, Map<String,V>>` 同样不支持。
3. **插件侧必须编译 `-parameters`**：`@Arg` 未显式命名时按参数名匹配，依赖此编译选项。
   本项目一律显式写 `@Arg("name")`，同时开启该选项双保险。
4. **调度器 `Plugin` 形参会被忽略**，一律归属 `YLib.init` 的那个插件。
5. **Folia/Canvas 下 `cancelAllTasks` 清不掉 region/entity 任务**——必须保存
   `UniversalTask` 逐个 `cancel()`。延迟/周期单位是 **tick**，且 `delay<=0` 会被改成 1。
   Entity 绑定任务才有 Folia 区域线程语义（周期性进度刷新要用实体绑定）。
6. **命令参数类型**只支持 `String/int/Integer/double/Double/boolean/Boolean/Player/World/Enum`，
   其余（`long`/`Material`/`OfflinePlayer` 等）会当 `String` 注入并在反射调用时抛异常。
   需要别的类型就自己 `@Arg String` 再转换，或补全方法里处理。
7. **Tab 补全拿不到前置参数值**：`CommandDispatcher.tabComplete` 传的是空 map，
   补全器签名 `List<String> f(CommandSender, CommandContext, String current)`。
8. `chat` 目标的进度判定要注意：异步事件里**不要**碰 Bukkit API，
   需要发消息/改物品时回到主线程（`scheduler.runTask`）。

---

## 10. 实施顺序与进度

| # | 阶段 | 状态 |
|---|---|---|
| 1 | `api` 模型与三个扩展点接口 | ✅ 完成 |
| 2 | 构建配置：依赖、shadow 重定位、`-parameters` | ✅ 完成 |
| 3 | `core` 存储层（SQLite/MySQL + 方言 + 仓储） | ✅ 完成（15 项 SQLite 集成测试） |
| 4 | 引擎：`ProgressService` + 14 种目标类型 + 4 个监听器 | ✅ 完成（12 项引擎单元测试） |
| 5 | 奖励类型 + 发放（金币/点券/经验/物品/命令） | ✅ 完成 |
| 6 | 多语言（YLib 消息服务，文本渲染内置于 YLib） | ✅ 完成 |
| 7 | 每日任务（全局池 + 确定性抽取 + 刷新扣费 + 跨天） | ✅ 完成（10 项抽取不变量测试） |
| 8 | 内置网页编辑器（REST + 静态资源 + 令牌校验） | ✅ 完成 |
| 9 | 网页编辑器前端（schema 驱动表单） | ✅ 完成（构建通过、类型检查 0 诊断） |
| 10 | 玩家 GUI + 管理 GUI | ✅ 完成 |
| 11 | 命令层（玩家 7 个 / 管理员 10 个子命令 + 补全） | ✅ 完成 |
| 12 | PlaceholderAPI 变量扩展（反射接入） | ✅ 完成 |
| 13 | 真机冒烟验证（`start-folia.ps1` + Folia 26.1.2-8） | ✅ 完成（插件成功启用） |
| 14 | 编辑器：图标/材质选择器（`/api/catalog`，中英文搜索） | ✅ 完成 |
| 15 | 编辑器：目标与奖励预设（`/api/presets`） | ✅ 完成 |

**测试总量：112 项全部通过**（存储 15 / 引擎 12 / 命令帮助 12 / 每日 10 / 奖励 19 /
任务管理 8 + 示例任务 6 + 监听器计数 6 / 字段一致性 7 / 编辑器素材 6 / GUI 图标 6 /
进度渲染 5），`clean build` 全绿。

文本渲染的测试**不在本插件**，而在 YLib 侧（`YLib/core/src/test`，15 项）：
渲染能力既然上移到了 YLib，它的行为就该在 YLib 钉住，否则每个消费方只能各测各的。

### 真机验证结论（Folia 26.1.2-8）

启动日志实证：

```
[playerTaskX] 存储已就绪: SQLite: data/playerTaskX.db
[playerTaskX] 已载入 1 个任务
[playerTaskX] Registered command: playertaskx
[playerTaskX] Registered command: playertaskxadmin
[playerTaskX] PlayerTaskX 已启用（1 个任务，14 种目标，5 种奖励）
[io.javalin.Javalin] Started Server@… @13862ms
```

网页编辑器接口实测（全部 HTTP 200，中文正确）：

```
GET /api/stats    → {"quests":1,"dailyQuests":1,"objectives":14,"rewards":5,
                     "storage":"SQLite: data/playerTaskX.db","categories":["每日"]}
GET /api/catalog  → 1506 项材质 + 157 项实体（服务端 26.1.2），中英文名正确
GET /api/presets  → 7 个目标预设 + 4 个奖励预设（含中文名，UTF-8 落盘正确）
GET /api/schema   → TARGET 字段类型与 required=false 如实下发
POST/DELETE /api/presets/...  → 增删生效；缺 type 或非法 kind 返回 400
```

gzip 已生效（Javalin 对超过 1500 字节的响应自动压缩）：

```
/api/catalog              121 KB → 20.7 KB
/assets/index-*.js        238 KB → 85.7 KB
```

**清空数据库后重新初始化**：只建 6 张表（quest / quest_objective / quest_reward /
player_quest / daily_state / meta），`PRAGMA integrity_check` 为 ok。

同时验证了两条重要的健壮性行为：

- **软依赖缺失时优雅降级**：未安装 Vault/PlayerPoints 时，对应奖励类型标记为不可用并写入
  启动警告，示例任务被校验标红，但插件照常启用、命令与编辑器均正常——不会因软依赖缺失而崩。
- **中文编码**：日志文件与生成的语言文件均为正确 UTF-8（控制台乱码只是 Windows 终端显示问题，
  插件内部与落盘内容无误）。

### 尚未验证的部分（如实记录）

- **MySQL 路径未做真机验证**：SQL 由 `Dialect` 统一生成、与 SQLite 共用同一套仓储代码，
  但 `ON DUPLICATE KEY UPDATE` 分支、HikariCP 连接与 `CREATE INDEX` 的容错路径
  只在代码与 SQLite 测试层面覆盖，没有连过真实 MySQL 实例。
- **玩家实际游玩路径未验证**：需要真人进服（挖掘/合成/击杀等）才能确认进度累加、
  actionbar 推送、GUI 点击等表现层行为；本次只验证到「插件启用 + 命令注册 + HTTP 接口」。
- **网页编辑器的界面操作未做浏览器端人工确认**：接口层已实测，但选择器、预设这类
  纯前端的交互（搜索、多选、拖拽/排序）只保证构建与类型检查通过。
- **未安装 Vault / PlayerPoints 的服务器**：刷新费用会按「金币 → 点券 → 经验」自动
  兜底到经验；该回退路径有单元测试覆盖，但没有在缺少经济插件的真机上跑过全流程。




### 已落地的关键实现细节

- **目标类型**：13 个类位于 `core/objective/`，全部复用 `ObjectiveType.targetMatches`
  （忽略大小写、逗号多值、空或 `*` 为任意）；`chat` 用包含匹配，`interact` 先校验 `mode` 再校验 `target`。
  注册在 `PlayerTaskX#registerBuiltInObjectives` 显式列出（不扫描包，保证「新增类型必须登记」的确定性）。
- **进度热路径**：`ProgressService` 只缓存「任务 → 目标下标 → 类型」静态映射，
  运行期进度不常驻内存（避免缓存一致性），未命中目标不产生任何 IO。
- **可测试性**：`ProgressContext` 允许 `player == null`（以 `playerId` 为准），
  因此引擎可完全脱离服务端单元测试，无需 MockBukkit。
- **actionbar 兼容**：Spigot 的 `Player` 既无 Adventure 也无 `sendActionBar`，
  `PlayerNotifier` 运行时探测 Paper 原生 API，失败退回 `sendTitle("", text, …)` 方案。
- **软依赖**：Vault / PlayerPoints 均以运行时探测方式使用（缺失时对应奖励类型标记为不可用并在启动日志提示），
  避免 `NoClassDefFoundError` 让插件整体无法加载。

