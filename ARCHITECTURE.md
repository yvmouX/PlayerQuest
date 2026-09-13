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

`api` 不含实现，`core` 依赖 `api`。网页编辑器的静态资源由 vite 直接构建到
`core/src/main/resources/web`（`core:frontendBuild`，即 `npm run build`，先跑 `vue-tsc` 类型检查），
再由 `core:processResources` 一并打进 jar（`dist/` 已不再产生）。

### 依赖策略

| 依赖 | 用途 | 作用域 |
|---|---|---|
| `spigot-api` | 服务端 API | compileOnly |
| YLib（复合构建） | 调度器/日志/配置/命令/消息 | implementation |
| `adventure-text-minimessage` + `serializer-legacy`/`plain` | MiniMessage 文本 | 由 YLib 以 `api` 提供（4.x，Java 8 字节码）；**shadow 时 relocate** 避免与 Paper 原生 Adventure 冲突 |
| `sqlite-jdbc` / `mysql-connector-java` | 存储 | implementation / compileOnly |
| `HikariCP` | MySQL 连接池 | implementation |
| `javalin` | 内置网页编辑器 HTTP 服务 | implementation |
| `VaultAPI` / `playerpoints` | 金币 / 点券 | compileOnly（软依赖） |
| `placeholderapi` | 变量 | compileOnly（软依赖） |

**JSON 编解码统一走 `JsonCodec` 的单个 `ObjectMapper`**（jackson-databind，见
`build.gradle.kts`）：`properties` / `progress` 列与编辑器 HTTP 传输都用它，
不再出现「一份模型两套序列化」。

---

## 2. 数据模型（扁平：任务 = 多目标 + 多奖励）

```
Quest                    任务定义（静态，由配置/网页编辑器维护）
├── id, name, description, icon, category
├── type: DAILY | NORMAL
├── prerequisites: List<String>   前置任务 id（全部**领奖**后才解锁，空 = 无前置）
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
├── status: IN_PROGRESS | COMPLETED | CLAIMED | ABANDONED
├── progress: Map<Integer, Integer>   目标下标 → 当前计数
└── structureHash                     接手时的目标结构摘要（见 4.4）
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

内置 15 种目标：`craft` 合成、`break_block` 挖掘、`fish` 垂钓、`custom_fish` 自定义钓鱼、
`place_block` 放置、`consume` 消耗、`kill` 击杀、`submit` 提交、`enchant` 附魔、`shear` 剪切、
`breed` 繁殖、`tame` 驯服、`command` 命令、`interact` 交互、`chat` 发言。

其中 12 种的行为完全一致（`target` 命中就加本次数量），它们**不是 12 个类**，
而是 `BuiltIns` 里的 12 行 `TargetObjective` 数据：类型之间只差 id、响应动作与
`target` 字段的语义类型（材质 / 实体 / 自由文本）。真正有自己判定逻辑的只有
`InteractObjective`（`mode` 匹配）、`ChatObjective`（关键词包含匹配）与
`CustomFishObjective`（鱼 id + 最小尺寸，且依赖 CustomFishing，见 4.6）。
「类型自描述」没有损失——`schema()` 仍由类型自己给出，编辑器与 GUI 照旧自动生成表单。

### 3.2 奖励类型 `RewardType`

```java
public interface RewardType extends ConfigurableType {
    void grant(Player player, QuestReward reward);
    default boolean available() { return true; }
    default String unavailableReason() { return ""; }
}
```

内置：`money` 金币(Vault)、`points` 点券(PlayerPoints)、`exp` 经验（原版，总是可用）、
`item` 物品、`command` 自定义命令。

### 3.3 进度事件 `ProgressContext`（api，唯一与 Bukkit 事件耦合处）

```java
public record ProgressContext(UUID playerId, Player player, Trigger trigger,
                              String target, int amount, String extra) {
    // 由 Bukkit 监听器构造，引擎只认它
    // trigger: BREAK_BLOCK / CRAFT / FISH / ...；target: 方块/实体/物品/命令名等，可为 null
    // amount: 本次动作数量（<=0 会被归一成 1）；extra: 附加信息（如交互的具体动作类型）
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
                     actionbar 推送进度；目标全满 → title 提醒 + 标记完成（奖励由玩家主动 /ptx claim 领取）
```

---

## 4. 存储

### 4.1 两种数据、一个库、一个选择入口

数据按性质分成两类，**契约刻意分开**，但**落在同一个库里**：

| | 内容类 | 状态类 |
|---|---|---|
| 内容 | 任务定义、目标/奖励预设 | 玩家进度、每日刷新状态 |
| 契约 | `DefinitionRepository<T>`（`QuestRepository` / `PresetRepository`） | `PlayerQuestRepository` |
| 写入频率 | 极低（管理员改动） | **每个游戏事件** |
| 需要事务 | 否 | **是**（每日刷新要删旧写新原子完成） |
| 需要跨服 | 是（同一份定义） | 是（共享玩家数据） |
| 落在哪张表 | `quest` / `quest_objective` / `quest_reward` / `preset` | `player_quest` / `daily_state` |

**为什么不是一个接口**：玩家侧需要 `findActiveByPlayer`（在进度热路径上）、
`countPlayers`、`distinctPlayerIds` 与 `transaction`，这些定义侧都不需要。
合并的后果是二选一——要么玩家侧丢掉索引查询与事务，要么内容侧被迫实现
一个「键控 + 可查询 + 事务」的存储，也就是拿内容表当数据库用。

**契约分开 ≠ 后端分开**：两类数据存在同一个库里，`storage.type` 一处决定用
SQLite 还是 MySQL。`JdbcDatabase` 只有「连接从哪来」不同（两个工厂表达），
差异全部由 `Dialect` 承担，因此「支持两种数据库」不必写两套仓储。

```
storage.type = SQLITE → JdbcQuestRepository + JdbcPresetRepository
                      + JdbcPlayerQuestRepository + JdbcQuestClaimRepository
                      （同一个 JdbcDatabase）
storage.type = MYSQL  → 同上，Dialect 决定方言
```

装配入口是 `DatabaseFactory`（`Handle` 一次给出四份仓储）；未知类型回退 SQLite 并告警，
而不是让插件启动失败。**没有从旧格式搬运数据的迁移代码**：项目未发布，
不存在「数据只在旧存储里」的部署，为它保留一次性代码没有收益。

> **曾经有文件后端**（JSON：一任务一文件、一玩家一文件），已整体删除。
> 定义进库之后「文件与库哪个是权威」的问题就不存在了；玩家侧的文件方案每次进度变化
> 都要重写该玩家整份文件、聚合要列目录、且无法跨服共享，相对 SQLite 只剩劣势。
> 需要 diff 或进版本控制时，用编辑器的整份任务导出/导入。

### 4.2 为什么结构化数据统一用 JSON 而不是 YAML

YAML 1.1 会把 `target: NO`（`NO` 是合法的方块材质名「一氧化氮」）解析成布尔 `false`，
把 `1.20` 解析成浮点 `1.2`——都是**静默数据损坏**。任务的 `properties` 列要原样保存
用户填的参数，这类值迟早会撞上。

干净的解法是 YAML 1.2 风格的解析器（布尔只认 `true/false`）。实测该解法有效，
但 **Jackson 2.15.2 的 `YAMLFactoryBuilder` 不暴露 resolver**（只有 `stringQuotingChecker`
与 `yamlVersionToWrite`），Jackson 内部自行构造 `Yaml`，无法替换其解析器。
剩下两条路都更重：放弃 Jackson 直接用 snakeyaml 手写序列化，
或额外引入 `snakeyaml-engine` 并处理它与 Jackson 内置 snakeyaml 1.x 的类名冲突。

JSON 没有隐式类型转换，零成本消除整类问题。因此**凡是我们自己定格式的地方
（`properties` 列、进度列、编辑器 HTTP 传输）一律 JSON**；只有用户手写的配置文件与
语言文件是 YAML——那是给人看的界面，不是数据交换格式，而且由 YLib 负责读写。

### 4.3 目标结构指纹（防静默错配）

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

### 4.4 表结构

```sql
-- 玩家数据（状态类）
player_quest(player_id, quest_id, type, assigned_at, expires_at, status,
             progress TEXT,              -- {"0":32,"1":5} 目标下标 → 计数
             structure_hash,             -- 目标列表摘要，见 4.3
             PRIMARY KEY(player_id, quest_id))
daily_state(player_id PK, period, refresh_count, assigned_at)
-- 永久账本：只记「领取过」，永不删除（每日任务记录会被整批删掉，前置判定不能依赖它）
quest_claim(player_id, quest_id, claimed_at, PRIMARY KEY(player_id, quest_id))

-- 任务定义与预设（内容类）
quest(id PK, name, description, icon, category, type, refresh_cost, enabled)
quest_objective(quest_id, idx, type, properties TEXT)   -- properties 为 JSON
quest_reward(quest_id, idx, type, properties TEXT)
quest_prerequisite(quest_id, prerequisite_id, PRIMARY KEY(quest_id, prerequisite_id))
preset(kind, id PK, name, type, properties TEXT, description)
```

约定：**所有 SQL 收敛在 `storage/` 包**，其它包不得出现 SQL 字符串。
`quest_prerequisite` 刻意没有 `idx` 列：判定是「全部满足」，顺序没有意义，
存下来只会暗示它有意义（而 `quest_objective` 必须有，玩家进度按它记录）。

### 4.5 前置任务（任务链）

**判定标准是「已领取奖励」**，不是「已完成」：依据 `quest_claim` 永久账本。
若不另立账本、直接用 `player_quest` 的状态判断，每日任务跨天/刷新时记录被整批删除，
任务链第二天就断了——这是本功能唯一必须新增一张表的原因。

三个落点由 `PrerequisiteService` 一处给出结论，调用方不做第二次判断：

| 落点 | 行为 | 为什么在这里 |
|---|---|---|
| 每日抽取（`DailyService.availablePool`） | 前置未满足的任务不进候选池 | 抽到再做不了才是真的坑；玩家侧只看得到「没这个任务」 |
| 领取奖励（`RewardService.claim`） | 前置未满足 → 领不到，并列出还差哪几个 | 已发给玩家的任务会因管理员改定义而变成锁定状态，把关不嫌多 |
| 界面与诊断（详情 GUI、`/ptxa list`/`info`、编辑器 problems） | 列出前置与达成状态、报出配置问题 | 配置问题必须让管理员看到，不能只表现为「任务一直不出现」 |

配置校验（`PrerequisiteService.problems`）覆盖四类会让任务**永远解锁不了**的写法：
前置不存在、自己当前置、成环、前置已禁用。成环检测用「起点用待校验任务自己的配置、
其余节点取注册表」的 DFS——编辑器保存前的任务还没进注册表，而那时恰恰最需要检出新配的环。
每轮抽取前取一次「已领取 id 快照」再逐个任务判定，避免按任务数打 N 次查询。

### 4.6 游戏内容插件联动（MythicMobs / CustomFishing）

`core/integration/` 是**软依赖接入点**：这些类只在装了对应插件时才被创建/加载，
没装的服务器上完全不参与运行。接入方式与三条不变量：

| 插件 | 接法 | 目标语法 / 目标类型 |
|---|---|---|
| MythicMobs 5.x | 反射调用 `MythicBukkit#inst → getMobManager → getMythicMobInstance → getMobType` | `kill` 的 `target` 写 `mythic:<内部名>`（不需要新类型） |
| CustomFishing | `compileOnly` API + 反射创建监听器，监听 `FishingLootSpawnEvent` | 新的目标类型 `custom_fish`（原版垂钓事件看不到它的战利品） |

**为什么一个用反射、一个用依赖**：MythicMobs 的 API 类继承自另一个 Lumine 构件，
编译期引用它会连带要求 `LumineUtils` 之类的依赖（实测报错 `无法访问 LuminePlugin`），
而我们只用三个方法——反射的代价更小，还能容忍 5.x 内部改名；
CustomFishing 的 API jar 自包含，直接编译进来更清晰。
两者都不是「碰运气」：检测不到插件就完全跳过，接入失败只记一条 warn。

三条不变量：

1. **没装插件时行为与从前完全一致**。`MythicMobsHook.create()` 返回 `null`、
   CustomFishing 的监听器根本不注册；`custom_fish` 目标类型照常存在，但
   `available()` 为 false，编辑器会标为不可用。
2. **同一个对象只能计一次**。一只自定义怪同时是 `ZOMBIE` 与 `mythic:SkeletalKnight`；
   若为两个名字各推一次动作，「击杀任意生物」会被计成两次。因此
   `ProgressContext` 带上 `aliases`：一个动作、多个等价标识，判定时任一命中即可。
3. **配置问题必须露面**。`mythic:` 目标在没装 MythicMobs 的服务端上永远命中不了，
   由 `MythicMobsHook.targetProblems(quest)` 报进 `QuestAdminService.validate`——
   编辑器标红、`/ptxa list`/`info` 与启动日志同时给出，而不是等玩家来问「杀了不涨」。

编辑器侧：MythicMobs 的怪物会被追加进 `/api/catalog` 的实体列表（id 形如
`mythic:SkeletalKnight`，也就是要写进 `target` 的值本身），因此选择器直接可用；
`/api/schema` 对**目标类型**也开始下发 `available` / `unavailableReason`
（与奖励同一套字段），缺 CustomFishing 时下拉里就选不了它。

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

⚠️ **键名撞上 YAML 布尔字面量会被改名**：Bukkit 的 YAML 是 1.1 语义，裸写的 `yes:` / `no:` / `on:` / `off:`
读进来键名就变成了 `common.true`，代码查 `common.yes` 只会得到一句「缺少语言键」
（内置文件曾因此同时丢掉 `common.yes` 与 `common.no`，源文件看不出任何异常）。
内置文件里这类键一律加引号；`LanguageFileTest` 直接对比「源文件声明的键」与
「`YamlConfiguration` 加载出的键」，把这类静默改名钉死在测试里。

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

- 配置 `daily.pool`（任务 ID 列表）、`daily.amount`（每人每日抽取数量）、`daily.reset-hour`（默认 `4`，早于该时刻算前一天）。
- 发放规则：**全局池 + 按玩家抽取**，种子 = `hash(playerId, 周期, 刷新次数)`，保证同一天重登结果一致、刷新后换一批。
- 刷新：`/ptx refresh`，按 `daily.refresh-currency` 的顺序消耗第一种可用货币
  （金币 / 点券 / 经验，经验是永远可用的兜底），**只重抽该玩家自己的列表**，
  消耗与次数记录在 `daily_state`，可配置每日刷新上限与刷新费用。
- 跨天检测：登录时与定时任务中检查 `daily_state.assigned_at`，过期则重抽。

---

## 7. 界面

### 7.1 玩家 GUI（`core/gui/`）

通用菜单框架（`Menu` / `MenuItem`，用 `InventoryHolder` 区分归属），
在此之上实现：每日任务列表、任务详情（多目标进度 + 多奖励预览）、点击领取奖励。
（任务**分类**目前只作为任务的一个字段用于筛选与展示，没有按分类分页浏览的界面。）

### 7.2 管理 GUI

任务列表分页浏览、只读预览（复用任务详情菜单）、启用/禁用、手动重载。
管理 GUI **不提供**目标/奖励编辑——那部分由网页编辑器承担，避免两套表单实现各自漂移。

### 7.3 网页编辑器

Javalin 提供 REST + 静态资源（`/` 返回 Vite 构建产物）：

```
GET    /api/quests            列表          POST   /api/quests          新建/覆盖
GET    /api/quests/export     导出全部定义   POST   /api/quests/import   导入（replace=true 先清空）
GET    /api/quests/{id}       详情          DELETE /api/quests/{id}      删除
GET    /api/players           有记录的玩家   GET    /api/players/{uuid} 该玩家的任务记录与逐目标进度
GET    /api/schema             目标/奖励类型的字段 schema（驱动前端动态表单）
GET    /api/catalog            当前版本支持的物品与实体（图标/材质选择器，含中英文名）
GET    /api/presets            目标与奖励预设（编辑器的便利设施，引擎不认它）
POST   /api/presets/{kind}     保存预设，kind ∈ {objectives, rewards}
DELETE /api/presets/{kind}/{id} 删除预设
GET    /api/langs              读取可用的语言文件
PUT    /api/langs/{code}       写入某种语言（先校验 YAML 合法性）
GET    /api/stats              统计          POST   /api/reload         重载任务定义
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
  中文译名只存在于**客户端**资源里。因此优先读 `plugins/playerTaskX/editor/zh_cn.json`，
  没有则在开启下载时从 Mojang 资源 CDN 取一份并缓存到该路径（管理员也可手动放置，
  离线服这么做即可），都拿不到就回退英文名——功能不受影响，只是没有中文。
  放在 `editor/` 而不是 `lang/`：后者是**插件自己的语言文件**（`zh_CN.yml`，发给玩家的文案），
  这份是 **Minecraft 的译名数据**，只服务编辑器图标列表，混在一起既容易误删也容易误解；
- 下载走「版本清单 → 版本元数据 → 资源清单 → CDN」四步**小请求**，自动跟随服务端版本，
  而不是写死某个版本的哈希（换版本后会取到不匹配的文件）。全程有超时、失败只记日志、
  不阻断插件启用、不重试轰炸；可用 `editor.fetch-chinese-names` 关闭。
  首次请求目录时会短暂等待下载完成（上限 3 秒），让管理员第一次打开编辑器就能看到中文名。

**REST 接口与 HTTP 服务分成两个类**：`EditorServer` 管服务本身（端口、启停、静态资源、
访问令牌），`EditorApi` 管 `/api/*` 的业务处理。混在一起时「端口被占用要 +1 重试」
这种运维逻辑会和「任务保存后要重建索引」这种业务逻辑挤在一个文件里。

**`EditorApi` 只依赖窄接口 `EditorServices`，不依赖插件单例**：它原先持有 `PlayerTaskX`，
于是每条路由都要求「插件已启用 + 服务端在跑」，14 条路由一条也进不了单测——这一层过去
六轮改动全靠手工起服打请求验证。接口只声明它真正调用到的能力（任务注册表、任务维护入口、
预设与玩家仓储、两个类型注册表、语言文件的读写位置、消息重载、存储描述），由 `PlayerTaskX`
**直接实现**（不做适配器类）。
例外只有两条：`/api/players` 要 Bukkit 的离线玩家名与在线状态、`/api/catalog` 要枚举服务端
的 `Material`/`EntityType`，两者都没有可注入的余地，因此它们的**响应形状**没有自动化覆盖
（路由本身未变，仍由真机验证）。

> 早期版本内置过一份手工中文表（约 175 行，材质覆盖率仅约两成），
> 已随本方案删除。实测替换后材质与实体的中文覆盖率均为 100%。

**预设不是引擎概念**：预设只是编辑器的便利设施，运行时引擎完全不认识它。
预设与任务定义同库（`preset` 表），不存在单独一份预设文件需要备份或同步。

出厂默认预设（`core/seed/ExamplePresets`，7 个目标 + 4 个奖励）在 `preset` 表为空时写入一次，
与示例任务同一时机（`PlayerTaskX` 启用流程里的 `guard`）。它原先藏在文件后端的 `load()` 里，
后端删除后必须显式接上——否则不报任何错，只是编辑器打开时预设列表变成空的。

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
| 4 | 引擎：`ProgressService` + 14 种目标类型 + 5 个监听器 | ✅ 完成（12 项引擎单元测试） |
| 5 | 奖励类型 + 发放（金币/点券/经验/物品/命令） | ✅ 完成 |
| 6 | 多语言（YLib 消息服务，文本渲染内置于 YLib） | ✅ 完成 |
| 7 | 每日任务（全局池 + 确定性抽取 + 刷新扣费 + 跨天） | ✅ 完成（10 项抽取不变量测试） |
| 8 | 内置网页编辑器（REST + 静态资源 + 令牌校验） | ✅ 完成 |
| 9 | 网页编辑器前端（schema 驱动表单） | ✅ 完成（构建通过、类型检查 0 诊断） |
| 10 | 玩家 GUI + 管理 GUI | ✅ 完成 |
| 11 | 命令层（玩家 7 个 / 管理员 11 个子命令 + 补全） | ✅ 完成 |
| 12 | PlaceholderAPI 变量扩展（反射接入） | ✅ 完成 |
| 13 | 真机冒烟验证（`start-folia.ps1` + Folia 26.1.2-8） | ✅ 完成（插件成功启用） |
| 14 | 编辑器：图标/材质选择器（`/api/catalog`，中英文搜索） | ✅ 完成 |
| 15 | 编辑器：目标与奖励预设（`/api/presets`） | ✅ 完成 |
| 16 | 编辑器：译名改为「读服务端语言文件 + 下载中文」，删除手工译名表 | ✅ 完成（材质/实体中文覆盖 100%） |
| 17 | 存储后端：SQLite / MySQL 共用一个库（`storage.type` 一处决定） | ✅ 完成 |
| 18 | 删除 JSON 文件后端（定义侧 + 玩家侧三个实现类），只留数据库 | ✅ 完成 |
| 19 | 目标结构指纹：定义变化导致进度错位时重置并告警 | ✅ 完成（8 项测试） |
| 20 | 编辑器 REST 层解耦（`EditorServices`）+ 接口级测试 | ✅ 完成（16 项 HTTP 测试） |
| 21 | 语言键 `common.yes` / `common.no` 被 YAML 布尔语义改名：加引号 + 钉住键的测试 | ✅ 完成（3 项测试） |
| 22 | 前置任务（任务链）：模型 + 定义子表 + 永久领取账本 + 抽取/领取门禁 + 编辑器 | ✅ 完成（29 项测试） |
| 23 | 游戏内容插件联动：MythicMobs（`mythic:` 击杀目标）+ CustomFishing（`custom_fish` 目标） | ✅ 完成（34 项测试） |

**测试总量：210 项全部通过**（25 个测试类，全部 failures=0 / errors=0）：
存储 20（`StorageIntegrationTest`）+ 编辑器接口 17（`EditorApiTest`）+
奖励 17（`CurrencyTypeTest` 8 + `ExpUtilTest` 9）+ 引擎 12（`ProgressServiceTest`）+
命令帮助 12（`YLibCommandHelpTest`）+ 前置判定 12（`PrerequisiteServiceTest`）+
任务管理 11（`QuestAdminServiceTest`）+ 每日 10（`DailyServiceTest`）+
自定义钓鱼 9（`CustomFishObjectiveTest`）+ 素材 9（`MaterialCatalogTest`）+
结构指纹 8（`StructureFingerprintTest`）+ 字段一致性 8（`ObjectiveFieldTypeConsistencyTest`）+
奖励领取 7（`RewardServiceTest`）+ 示例任务 7（`ExampleQuestsTest`）+
监听器 6（`ItemListenerCraftAmountTest`）+ GUI 图标 6（`QuestDetailMenuTest`）+
别名匹配 6（`TargetMatchAliasTest`）+ 示例预设 5（`ExamplePresetsTest`）+
每日抽取池 5（`DailyPoolPrerequisiteTest`）+ 进度渲染 5（`ProgressDisplayRenderTest`）+
CustomFishing 监听 5（`CustomFishingListenerTest`）+ MythicMobs 目标 5（`MythicMobsHookTest`）+
击杀监听 5（`EntityListenerTest`）+ 语言文件 3（`LanguageFileTest`）。
统计口径：`.\gradlew.bat :core:test -x :core:frontendBuild` 之后读
`core/build/test-results/test/*.xml` 逐套件累加（25 个 XML），不是靠日志里的汇总行。

**代码规模**（含空行，按文件行数累加）：后端主代码 `api/src/main` 923 行 + `core/src/main` 10892 行
＝ **11815 行 / 96 个 java 文件**；测试 `core/src/test` **5064 行 / 28 个文件**
（`api/src/test` 为空，api 只放模型与接口，行为测试都在 core）；
前端 `task-editor-vue/src` **5526 行 `.vue` + 1420 行 `.ts`/`.js` ＝ 6946 行 / 28 个文件**。

文本渲染的测试**不在本插件**，而在 YLib 侧（`YLib/core/src/test`，15 项 =
`TextRendererTest` 11 + `RealWorldMessageTest` 4）：
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

**清空数据库后重新初始化**：建表清单为 8 张（quest / quest_objective / quest_reward /
quest_prerequisite / player_quest / daily_state / quest_claim / preset）；
早期一次真机验证里 `PRAGMA integrity_check` 为 ok。
（`meta` 表已随一次性迁移代码删除，见文末「删除一次性迁移代码」。）

**前置任务的真机冒烟（Folia 26.1.2-8）**：空库启动 → 写入 12 个示例任务（含任务链
「添砖加瓦」以「挖矿日常」为前置）→ `GET /api/quests` 读回
`prerequisites: ["example_daily_mine"]` 且 `problems: []`，即
「编辑器 JSON → 模型 → SQLite 子表 → 读回 → JSON」整条链路在真机上成立；
`quest_prerequisite` 与 `quest_claim` 两张新表也确实落在库里（建表无异常，插件正常启用）。
**未验证**：真人进服后的抽取排除与领取门禁表现——需要玩家在线才能触发，
本轮只到「定义读写 + 建表 + 编辑器接口」这一层。

**插件联动的真机冒烟（同一台测试服，未安装 MythicMobs / CustomFishing）**：
启动日志为「已启用（12 个任务，**15 种目标**，5 种奖励）」，没有任何异常，
也没有出现接入失败日志——即两个软依赖缺失时行为与从前完全一致；
`GET /api/schema` 的 `objectives.custom_fish` 如实下发 `available: false` /
`unavailableReason: 未安装 CustomFishing`，`kill` 为可用；
`GET /api/catalog` 正常返回 1506 材质 + 157 实体、且没有 `mythic:` 条目（没装就一条都不加）；
再用编辑器接口存一条同时含 `target: mythic:Boss` 与 `custom_fish` 目标的任务，
`problems` 恰好给出这两条：

```
目标类型 custom_fish 不可用（未安装 CustomFishing）
目标 mythic:Boss 需要 MythicMobs 5.x（当前未安装）
```

随后删除该探针任务，库回到 12 个示例。（装上这两个插件的正向链路只有单测覆盖，见文末已知限制。）

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
- **`NORMAL` 任务目前没有发放入口**：玩家拿到的任务只有每日任务一条来源
  （`DailyService` 直接写 `player_quest`，`ProgressService.assign` 在生产代码里无人调用）。
  普通任务因此只存在于定义与编辑器里；给它配前置不会报错，但游戏内看不到效果。
  前置判定本身与任务类型无关（每日任务链已完整生效），缺的是「接取常驻任务」这一步。
- **MythicMobs / CustomFishing 只做了「未安装」这一支的真机验证**：本机测试服没有这两个插件，
  验证到的是「插件照常启用、目标类型标为不可用、`mythic:` 目标被校验拦下」；
  装上插件后的实际击杀/钓获计数只有替身事件与反射入口的单测覆盖，
  真实插件版本（MythicMobs 5.x 的具体小版本、CustomFishing 2.3.x）未在真机上跑过。




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
  逻辑的 `InteractObjective` / `ChatObjective` / `CustomFishObjective`；15 种内置类型的清单在 `BuiltIns` 里显式列出
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

