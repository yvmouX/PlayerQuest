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
public interface ConfigurableType {     // 目标与奖励共有的形状
    String id();                        // 如 "break_block"
    String displayName();
    List<ConfigField> schema();         // 该类型的配置字段（供编辑器/GUI 生成表单）
}

public interface ObjectiveType extends ConfigurableType {
    Trigger trigger();                  // 该类型响应的动作
    int match(ProgressContext context, Map<String, Object> properties);
    default boolean targetMatches(...); // 忽略大小写、逗号多值、空或 * 为任意
}
```

`ConfigurableType` 存在的理由：凡是**展示与编辑**类型的地方（编辑器动态表单、
GUI 图标推导与字段说明、管理员命令的类型名回显）需要的都只是 `id/displayName/schema`
这三件事。抽出这一层后，这些地方可以只写一份实现，而不必给目标与奖励各写一份近乎相同的代码。

内置 14 种目标：`craft` 合成、`break_block` 挖掘、`fish` 垂钓、`place_block` 放置、
`consume` 消耗、`kill` 击杀、`submit` 提交、`enchant` 附魔、`shear` 剪切、
`breed` 繁殖、`tame` 驯服、`command` 命令、`interact` 交互、`chat` 发言。

其中 12 种的行为完全一致（`target` 命中就加本次数量），它们**不是 12 个类**，
而是 `BuiltIns` 里的 12 行 `TargetObjective` 数据：类型之间只差 id、响应动作与
`target` 字段的语义类型（材质 / 实体 / 自由文本）。真正有自己判定逻辑的只有
`InteractObjective`（`mode` 匹配）与 `ChatObjective`（关键词包含匹配）。
「类型自描述」没有损失——`schema()` 仍由类型自己给出，编辑器与 GUI 照旧自动生成表单。

### 3.2 奖励类型 `RewardType`

```java
public interface RewardType extends ConfigurableType {
    void grant(Player player, QuestReward reward);
    default boolean available() { return true; }
    default String unavailableReason() { return ""; }
}
```

内置：`money` 金币(Vault)、`points` 点券(PlayerPoints)、`exp` 经验、
`item` 物品、`command` 自定义命令。

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

### 4.1 两种数据、三个后端、一个选择入口

数据按性质分成两类，**各自独立选后端**，这是刻意的：

| | 内容类 | 状态类 |
|---|---|---|
| 内容 | 任务定义、目标/奖励预设 | 玩家进度、每日刷新状态 |
| 契约 | `DefinitionRepository<T>`（`QuestRepository` / `PresetRepository`） | `PlayerQuestRepository` |
| 写入频率 | 极低（管理员改动） | **每个游戏事件** |
| 需要事务 | 否 | **是**（每日刷新要删旧写新原子完成） |
| 需要跨服 | 是（同一份定义） | 是（共享玩家数据） |
| 默认后端 | JSON 文件 | SQLite |

**为什么不是一个接口**：玩家侧需要 `findActiveByPlayer`（在进度热路径上）、
`countPlayers`、`distinctPlayerIds` 与 `transaction`，这些定义侧都不需要。
合并的后果是二选一——要么玩家侧丢掉索引查询与事务，要么文件后端被迫实现
一个「键控 + 可查询 + 事务」的存储，也就是用文件重写一个数据库。

**三个后端，两份实现**：SQLite 与 MySQL 共用同一套 JDBC 实现（`JdbcDatabase` 只有
「连接从哪来」不同，两个工厂表达），差异全部由 `Dialect` 承担；文件后端（JSON）另有一份。
因此「支持三种存储」不需要写三套。

```
definitions.type = JSON    → QuestFileRepository + PresetFileRepository
definitions.type = SQLITE  → JdbcQuestRepository + JdbcPresetRepository
definitions.type = MYSQL   → 同上（Dialect 决定方言）
storage.type     = SQLITE  → JdbcPlayerQuestRepository
storage.type     = MYSQL   → 同上
storage.type     = JSON    → JsonPlayerQuestRepository（一玩家一文件）
```

选择入口是 `StorageFactory` 与 `DatabaseFactory`；未知类型回退默认值并告警，
而不是让插件启动失败。**没有从旧数据库自动搬运定义的迁移代码**：项目未发布，
不存在「定义只存在于旧表里」的部署，为它保留一百多行一次性代码没有收益。

### 4.2 文件后端的两条硬要求

**① 写入必须原子**：一律「写临时文件 → 原子改名」，绝不原地覆盖。
数据库的事务白送这个保证，文件方案必须自己补——原地写崩在中途会留下半截 JSON，
用户的全部任务因此读不出来。崩溃残留的 `.tmp` 在下次载入时清理。

**② 载入必须分级容错**：单个文件坏了**跳过它**并记警告（含文件名），其余照常载入；
缺 `id` 跳过且**不用文件名推断 id**；文件名与 `id` 不一致时以 `id` 字段为准。
整批全坏时保留内存里上一次成功的定义，而不是让任务列表变空。

**③ 「一个文件即一个事务」必须显式标记事务范围**：玩家 JSON 后端当初用
「暂存表非空」来判断自己是否处于事务中，而暂存表恰恰是 `save` 自己填的——
于是**事务里的第一次写入看不到事务、直接落盘**，`DailyService.assign` 的
「删旧任务 → 写新任务 → 记状态」在第一步就破了原子性：中途失败会留下
「旧任务已删、新任务没写」的空列表。现在用显式的 `inTransaction` 标记事务范围，
且暂存的是**整份文件**（任务记录与每日状态同文件，只攒一半会把另一半写空）。
三条事务测试（批处理、刷新原子性、异常丢弃）就是它的回归网。

### 4.3 为什么定义侧用 JSON 而不是 YAML

YAML 1.1 会把 `target: NO`（`NO` 是合法的方块材质名「一氧化氮」）解析成布尔 `false`，
把 `1.20` 解析成浮点 `1.2`——都是**静默数据损坏**。

干净的解法是 YAML 1.2 风格的解析器（布尔只认 `true/false`）。实测该解法有效，
但 **Jackson 2.15.2 的 `YAMLFactoryBuilder` 不暴露 resolver**（只有 `stringQuotingChecker`
与 `yamlVersionToWrite`），Jackson 内部自行构造 `Yaml`，无法替换其解析器。
剩下两条路都更重：放弃 Jackson 直接用 snakeyaml 手写序列化，
或额外引入 `snakeyaml-engine` 并处理它与 Jackson 内置 snakeyaml 1.x 的类名冲突。

JSON 没有隐式类型转换，零成本消除整类问题，因此选它。

### 4.4 目标结构指纹（防静默错配）

玩家进度按**目标下标**记录（`{"0": 32}`），因此调换目标顺序后，旧进度会被套到
别的目标上（挖了 32 个石头显示成「发言 32 次」），而语法校验查不出任何问题。

做法：玩家接手任务时记下目标列表的摘要（`Hash.fingerprint`，对顺序敏感），
每次载入进度时比对：

- 摘要一致 → 正常使用；
- 摘要不同 → **清空该任务进度、状态退回进行中，并记明确日志**；
- 记录里没有摘要（旧版本数据）→ 只补齐，**不重置**（没有依据不能清玩家进度）。

检测放在 `ProgressService.load()` 而不是只放在热度路径上：`findActiveByPlayer`
只返回进行中的记录，**已完成的记录同样会错位**，若只在热路径检测就永远发现不了，
玩家可能领到按错误进度判定的奖励。这一点由测试固化。

### 4.5 表结构

```sql
-- 玩家数据（默认后端）
player_quest(player_id, quest_id, type, assigned_at, expires_at, status,
             progress TEXT,              -- {"0":32,"1":5} 目标下标 → 计数
             structure_hash,             -- 目标列表摘要，见 4.4
             PRIMARY KEY(player_id, quest_id))
daily_state(player_id PK, period, refresh_count, assigned_at)

-- 仅当 definitions.type 选 SQL 后端时使用
quest(id PK, name, description, icon, category, type, refresh_cost, enabled)
quest_objective(quest_id, idx, type, properties TEXT)   -- properties 为 JSON
quest_reward(quest_id, idx, type, properties TEXT)
preset(kind, id PK, name, type, properties TEXT, description)
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
        // 刻意不设 prefixKey：多行输出（如 /ptxa list）走 sendRaw，而 sendRaw 会加前缀，
        // 启用后每一行都会被顶上一个 [PlayerTaskX]；语言文件里也因此没有 prefix 键
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

通用菜单框架（`Menu` / `MenuItem`，用 `InventoryHolder` 区分归属），
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
因此返回的天然就是「当前服务端支持的项」，不需要维护任何版本对照数据。
译名由 `LangFileStore` 提供，分工是刻意的：

- **英文名不下载**，读服务端 jar 里的 `assets/minecraft/lang/en_us.json`——
  用 **`Bukkit` 的类加载器**查询（插件类加载器不会把资源查询委派到服务端 jar 上，
  实测会直接落空），版本天然对齐、零网络依赖。
  **插件不打包这份文件**：曾经为了让开发期离线可跑而复制进插件资源，结果它永远停在复制那天
  （实测 26.1.2 的副本与 1.21.11 的服务端文件不同），新方块会显示成推导出来的枚举名。现已删除；
- **中文名服务端没有**：实测 26.1.2 的服务端 jar 内 lang 文件只有 `en_us.json` 一个，
  中文译名只存在于**客户端**资源里。因此优先读 `plugins/playerTaskX/lang/zh_cn.json`，
  没有则在开启下载时从 Mojang 资源 CDN 取一份并缓存到该路径（管理员也可手动放置，
  离线服这么做即可），都拿不到就回退英文名——功能不受影响，只是没有中文；
- 下载走「版本清单 → 版本元数据 → 资源清单 → CDN」四步**小请求**，自动跟随服务端版本，
  而不是写死某个版本的哈希（换版本后会取到不匹配的文件）。全程有超时、失败只记日志、
  不阻断插件启用、不重试轰炸；可用 `editor.fetch-chinese-names` 关闭。
  首次请求目录时会短暂等待下载完成（上限 3 秒），让管理员第一次打开编辑器就能看到中文名。

**REST 接口与 HTTP 服务分成两个类**：`EditorServer` 管服务本身（端口、启停、静态资源、
访问令牌），`EditorApi` 管 `/api/*` 的业务处理。混在一起时「端口被占用要 +1 重试」
这种运维逻辑会和「任务保存后要重建索引」这种业务逻辑挤在一个文件里。

> 早期版本内置过一份手工中文表（约 175 行，材质覆盖率仅约两成），
> 已随本方案删除。实测替换后材质与实体的中文覆盖率均为 100%。

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
| 16 | 编辑器：译名改为「读服务端语言文件 + 下载中文」，删除手工译名表 | ✅ 完成（材质/实体中文覆盖 100%） |
| 17 | 存储后端可插拔：定义与玩家数据各自选 JSON / SQLite / MySQL | ✅ 完成 |
| 18 | 任务定义与预设出库成 JSON 文件 | ✅ 完成 |
| 19 | 目标结构指纹：定义变化导致进度错位时重置并告警 | ✅ 完成（8 项测试） |

**测试总量：154 项全部通过**（存储 15 + 文件仓储 14 + 玩家 JSON 后端 21 / 引擎 12 + 结构指纹 8 / 命令帮助 12 / 每日 10 / 奖励 17 / 任务管理 8 + 示例任务 6 + 监听器计数 6 / 字段一致性 7 / 编辑器素材 7 / GUI 图标 6 / 进度渲染 5），`clean build` 全绿。

文本渲染的测试**不在本插件**，而在 YLib 侧（`YLib/core/src/test`，15 项）：
渲染能力既然上移到了 YLib，它的行为就该在 YLib 钉住，否则每个消费方只能各测各的。

### 真机验证结论（Folia 26.1.2-8）

**编辑器接口的端到端验证（含写路径）**：`GET /api/quests`（13 条，校验问题如实下发）、
`GET /api/quests/{id}`、`GET /api/schema`（14 目标 / 5 奖励）、`GET /api/catalog`
（1506 材质 + 157 实体）、`GET /api/presets`、`GET /api/langs`、`POST /api/reload` 全部正常；
写路径亦已跑通：`POST /api/presets/objectives` → 读回（8→9）→ `DELETE` 恢复；
`POST /api/quests`（存副本）→ 列表变 14 → `DELETE` → 回到 13。
软依赖缺失的提示也确实穿到了编辑器的 `problems` 字段。全程控制台无异常。

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

- **一件事只有一个出处**：凡是「多个入口都要做同一件事」的地方都收敛到一处，
  避免同一个功能两种口径。已收敛的几处：
  - 任务校验（编辑器标红 / `/ptxa list` / 管理 GUI）→ `QuestAdminService.validate`；
  - 刷新结果的提示措辞（玩家命令 / 管理员命令 / GUI 按钮）→ `DailyService.RefreshResult`
    自带回复消息，调用方只调 `report(...)`，不再各自拼 success/cost/limit/error；
  - 启用状态切换（落库 + 同步注册表 + 重建索引）→ `QuestAdminService.setEnabled`；
  - 文本装配（渲染 / 数字去小数尾巴 / 配置表摊平 / 类型显示名）→ `core/text/Texts`；
  - 命令帮助清单 → `CommandHelp.ofAnnotations` 从注解生成，不再手写第二份。
- **目标类型**：`core/objective/` 只有三个类——数据形态的 `TargetObjective` 与两个自带判定
  逻辑的 `InteractObjective` / `ChatObjective`；14 种内置类型的清单在 `BuiltIns` 里显式列出
  （不扫描包，保证「新增类型必须登记」的确定性）。共用的目标命中判定是
  `ObjectiveType.targetMatches`（忽略大小写、逗号多值、空或 `*` 为任意）。
- **进度热路径**：`ProgressService` 只缓存「任务 → 目标下标 → 类型」静态映射，
  运行期进度不常驻内存（避免缓存一致性），未命中目标不产生任何 IO。
- **可测试性**：`ProgressContext` 允许 `player == null`（以 `playerId` 为准），
  因此引擎可完全脱离服务端单元测试，无需 MockBukkit。
- **actionbar 兼容**：Spigot 的 `Player` 既无 Adventure 也无 `sendActionBar`，
  `PlayerNotifier` 运行时探测 Paper 原生 API，失败退回 `sendTitle("", text, …)` 方案。
- **软依赖**：Vault / PlayerPoints 均以运行时探测方式使用（缺失时对应奖励类型标记为不可用并在启动日志提示），
  避免 `NoClassDefFoundError` 让插件整体无法加载。
- **依赖只留用得上的**：`fastjson2`、`javalin-openapi` / swagger / redoc、`jackson-dataformat-yaml`
  从未被引用过，已从构建脚本删除；JSON 编解码全项目只有 `JsonCodec` 一个 `ObjectMapper`
  （编辑器曾自带第二个）。
- **包归位**：三个类型/任务注册表实现同处 `core/registry`（`api.registry` 也是这么分组的），
  `core/quest` 只留 `QuestAdminService` 这一处「任务定义维护入口」。
- **两个数据库类合成一个**：`JdbcDatabase` 同时是「执行 SQL 的引擎」与「打开 SQLite 文件 /
  MySQL 连接池」的工厂。原先 `SqliteDatabase` / `MysqlDatabase` / `JdbcDatabase` 三个类
  靠一个 `ConnectionProvider` 接口串起来，而通用执行逻辑只有一个实现——策略差异其实只有
  「借出/归还」与「怎么关」三件事。
- **删除一次性迁移代码**：`DefinitionMigrator`（130 行）把「旧版本存在数据库里的任务定义」
  导出到 JSON。项目未发布，不存在这样的部署；连同 `Schema` 里的 `meta` 表、
  容错 `ALTER`、以及从未被写入过的 `quest.sort_order` 列一并删除。

