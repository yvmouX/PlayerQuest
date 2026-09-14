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

**前端的两道构建期自检**（都挂在 `npm run build` 上，类型检查之外再多一层）：

| 脚本 | 抓什么 |
|---|---|
| `scripts/yaml-check.mjs` | YAML 文本 ⇄ 任务/预设的往返：字符串不被解析成布尔/数字、未知字段不静默丢弃、非法 YAML 明确报错。用 vite 把 `src/utils/yaml.ts` 现打成临时 ESM 再断言，测的就是真正跑在浏览器里的那份代码 |
| `scripts/ssr-smoke.mjs` | 视图能否「装起来」：SSR 渲染一遍各视图，把 setup() 完整执行一次。`vue-tsc` 不执行 setup，因此「setup 期读了未初始化变量」（如 immediate 的 watch 撞上 const 死区）这类只有打开页面才会炸的错误，靠它变成构建失败 |

### 依赖策略

| 依赖 | 用途 | 作用域 |
|---|---|---|
| `spigot-api` | 服务端 API | compileOnly |
| YLib（复合构建） | 调度器/日志/配置/命令/消息 | implementation |
| `adventure-text-minimessage` + `serializer-legacy`/`plain` | MiniMessage 文本 | 由 YLib 以 `api` 提供（4.x，Java 8 字节码）；**shadow 时 relocate** 避免与 Paper 原生 Adventure 冲突 |
| `sqlite-jdbc` / `mysql-connector-java` | 存储 | implementation / compileOnly |
| `HikariCP` | MySQL 连接池 | implementation |
| `javalin` | 内置网页编辑器 HTTP 服务 | implementation |
| `snakeyaml`（服务端自带，随 `spigot-api` 编译期可见） | `quests/` / `presets/` 只读 YAML 定义（4.6）；**不打包**，服务端本来就有 | 服务端提供 |
| `VaultAPI` / `playerpoints` | 金币 / 点券 | compileOnly（软依赖；VaultAPI 另有一份 testImplementation，见 8） |
| `placeholderapi` | 变量 | compileOnly（软依赖） |

**JSON 编解码统一走 `JsonCodec` 的单个 `ObjectMapper`**（jackson-databind，见
`build.gradle.kts`）：`properties` / `progress` 列与编辑器 HTTP 传输都用它，
不再出现「一份模型两套序列化」。

### 包边界与约定

这几条原先写在各自的 `package-info.java` 里。Javadoc-only 的文件编译产物是个空的
`package-info.class`、没有任何东西引用，因此把它们搬到这里、删掉文件——
约定仍然有效，只是不再随包一起走。

**`core/storage/`（根包 = 契约与装配入口）**

- 根包放契约与装配入口：`Database`、`QuestRepository`、`PlayerQuestRepository`、
  `StorageException`；`Dialect` 与 `RowMapper` 虽是实现味很浓的名字，但它们出现在
  `Database` 的方法签名上，属于契约的一部分，因此也留在根包；以及唯一的装配点
  `DatabaseFactory`。**包外代码只需要 import 这些。**
- `jdbc` 子包是实现细节，**包外不得 import**：实际 import 它的只有父包里的装配点
  `DatabaseFactory` 与做真机 SQL 验证的 `StorageIntegrationTest`
  （`PeriodicService` 读 DailyState 曾是业务层的唯一例外，周期任务改成走
  `PlayerQuestRepository` 接口后这个例外没了）。
- **SQL 字符串只允许出现在本包（含子包）内**，且列名一律经方言转义。
- 未知存储类型回退 SQLite 而不是启动失败——写错一个单词不该让插件起不来。
- 不要在服务端运行期间用外部工具改 SQLite 文件：WAL 会回滚外部连接的 DDL，
  得出的结论是错的（踩过）。

**`core/storage/jdbc/`（JDBC 实现细节）**
- **连接策略是刻意的两种**：SQLite 写入全局串行，用连接池反而制造 `SQLITE_BUSY`，
  因此走单连接长驻 + WAL；MySQL 走 HikariCP 池化，连接用完必须归还。两种策略都在
  `JdbcDatabase` 的两个工厂里，执行逻辑只有一份。
- **方言**：`Dialect`（根包，契约的一部分——`Database.dialect()` 暴露它）集中消化语法差异
  （upsert、标识符转义）；`Schema` 是建表 DDL 的唯一来源，所有语句经方言生成，
  同一份定义在两种库上都成立。
- **容错**：`JsonCodec` 对库里脏数据一律降级为空集合而不是抛异常——存储层的容错
  优先级高于严格性；`Sql` 收敛 JDBC 参数绑定样板。

**`api/schema/`（类型自描述：字段的「声明」）**

- 这里是「一种目标/奖励类型长什么样」的声明，四个文件各管一件事：
  `ConfigurableType`（`id` + 显示名 + 字段表，目标与奖励共有的形状）、
  `ConfigField`（单个字段：键、控件、必填、默认值、说明、**值域**）、
  `FieldType`（渲染成什么控件：文本 / 数字 / 开关 / **选择器** / 下拉）、
  `ValueKind`（值域词汇表：方块、可放置、物品、食物、实体、活体、可繁殖、可驯服、可剪毛、鱼、附魔）。
- 编辑器与 GUI **据此自动生成表单**：新增一种目标类型 = 写一个类 + 一行 schema，界面代码一行不改。
- **这个包不 import 任何 `org.bukkit` 类型**（`api` 里只有 `ProgressContext`、`RewardType`
  碰 Bukkit）：字段声明与版本无关，是给扩展点作者看的契约。
  「这个材质算不算方块」这类只有运行期能回答的问题不放这里，见下。

**`core/schema/`（同一份 schema 的「运行期语义」）**

- 目前只有 `ValueKinds`：把 `ValueKind` 落到**当前服务端**的枚举与接口上——
  某个候选项属于哪些值域（`Material.isBlock()`、`EntityType.getEntityClass()` 是不是
  `Animals` / `Tameable` / `Shearable`…），以及某个配置值能不能命中（编辑器标红的那条校验）。
  它要读 Bukkit 注册表，因此进不了 `api`；而它有两个方向的消费者——编辑器目录
  （`web/MaterialCatalog` 给候选打 `kinds` 标签）与定义校验（`quest/QuestAdminService`），
  塞进任一侧都会让另一侧反向依赖（`quest` 不该依赖 `web`），所以单开一个中立包。
- 这个包**故意只有薄薄一层**：它不持有状态、不装配任何东西，只回答两个纯问题
  「这个值属于哪些值域」「这个值能不能命中」。以后若还有「schema 的运行期语义」
  （字段值解析、默认值填充之类）也归这里。

**`core/listener/`（事件监听层）**

- 监听器只做一件事：把 Bukkit 事件翻译成 `ProgressContext` 投递给引擎。
  **判定逻辑不写在这里**：不允许出现针对具体目标类型的分支（见 `ProgressListener`）。
- **按事件域分组，而不是一个动作一个类**：动作语义已经组织在
  `core/objective/` 的各 ObjectiveType 里，编辑器与 GUI 的表单也由它的 schema 自动生成；
  监听器再按动作拆一套就会出现两套平行的结构要同步维护。分组与 `org.bukkit.event`
  的包分类一致：`BlockListener`（挖掘/放置/交互）、`EntityListener`（击杀/垂钓/剪切/繁殖/驯服/交互）、
  `ItemListener`（合成/消耗/附魔）、`TextListener`（发言/执行命令）、
  `PlayerListener`（会话生命周期，不推进度）。
- 每个动作的 handler 必须带 javadoc，写清动作语义与该事件特有的坑（异步、双触发、
  数量口径之类）——这是移植与排查时的第一手资料。
- 新增动作先按事件域归位，确实没有归属再新建域类，而不是默认新建动作类。

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
`CustomFishObjective`（鱼 id + 最小尺寸，且依赖 CustomFishing，见 4.5）。
「类型自描述」没有损失——`schema()` 仍由类型自己给出，编辑器与 GUI 照旧自动生成表单。

### 3.2 奖励类型 `RewardType`

```java
public interface RewardType extends ConfigurableType {
    void grant(Player player, QuestReward reward);
    default boolean available() { return true; }
    default String unavailableReason() { return ""; }
}
```

内置：`money` 金币（经 Vault 的 `Economy` 服务）、`points` 点券(PlayerPoints)、`exp` 经验（原版，总是可用）、
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
| 落在哪张表 | `quest` / `quest_objective` / `quest_reward` / `preset` | `player_quest` / `period_state` |

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
period_state(player_id, type, period, refresh_count, assigned_at,
              PRIMARY KEY(player_id, type))   -- 四种周期各一行，见 6
-- 任务定义与预设（内容类）
quest(id PK, name, description, icon, category, type, refresh_cost, enabled)
quest_objective(quest_id, idx, type, properties TEXT)   -- properties 为 JSON
quest_reward(quest_id, idx, type, properties TEXT)
preset(kind, id PK, name, type, properties TEXT, description)
```

约定：**所有 SQL 收敛在 `storage/` 包**，其它包不得出现 SQL 字符串。
### 4.5 游戏内容插件联动（MythicMobs / CustomFishing）

`core/integration/` 是**软依赖接入点**：这些类只在装了对应插件时才被创建/加载，
没装的服务器上完全不参与运行。接入方式与三条不变量：

| 插件 | 接法 | 目标语法 / 目标类型 |
|---|---|---|
| MythicMobs 5.x | 反射调用 `MythicBukkit#inst → getMobManager → getMythicMobInstance → getMobType` | `kill` 的 `target` 写 `mythic:<内部名>`（不需要新类型） |
| CustomFishing | `compileOnly` API + 反射创建监听器，监听 `FishingLootSpawnEvent` | 新的目标类型 `custom_fish`（原版垂钓事件看不到它的战利品） |
| ItemsAdder | 反射绑定 `CustomStack.byItemStack → getNamespacedID` / `CustomBlock.byAlreadyPlaced`（旧静态 API `ItemsAdder.getCustomItemName` 作后备） | 方块/物品类目标的 `target` 写 `itemsadder:<命名空间id>` |
| CraftEngine | 反射绑定 `CraftEngineItems.getCustomItemId` / `CraftEngineBlocks.getCustomBlockState → owner().value().id()` | 同上，前缀 `craftengine:` |

**为什么一个用反射、一个用依赖**：MythicMobs 的 API 类继承自另一个 Lumine 构件，
编译期引用它会连带要求 `LumineUtils` 之类的依赖（实测报错 `无法访问 LuminePlugin`），
而我们只用三个方法——反射的代价更小，还能容忍 5.x 内部改名；
CustomFishing 的 API jar 自包含，直接编译进来更清晰。
ItemsAdder（API 在 JitPack 上，且新旧两套 API 并存）与 CraftEngine（构件与 Minecraft 版本绑定）
同样走反射：只用三四个方法，没装的服务器连类都不会加载。
四者都不是「碰运气」：检测不到插件就完全跳过，接入失败只记一条 warn。

**自定义物品/方块走「别名」，不新增目标类型**（`CustomContentHook` / `CustomContentHooks`）：
IA/CE 的自定义方块在服务端仍是原版方块（靠方块状态与资源包呈现），
因此 `BlockBreakEvent` / `BlockPlaceEvent` 照常触发——监听器照常推原版材质名，
只额外把自定义 id（带前缀与裸 id 各一份）塞进 `ProgressContext.aliases`，
于是「同一个方块的两个名字」由 `targetMatches` 一次命中。
两家共用一个接口：加第三家（Oraxen / Nexo…）时监听器、校验、编辑器目录都不用改。

三条不变量：

1. **没装插件时行为与从前完全一致**。`MythicMobsHook.create()` 返回 `null`、
   CustomFishing 的监听器根本不注册、`CustomContentHooks` 是空实现；
   `custom_fish` 目标类型照常存在，但 `available()` 为 false，编辑器会标为不可用。
2. **同一个对象只能计一次**。一只自定义怪同时是 `ZOMBIE` 与 `mythic:SkeletalKnight`；
   若为两个名字各推一次动作，「击杀任意生物」会被计成两次。因此
   `ProgressContext` 带上 `aliases`：一个动作、多个等价标识，判定时任一命中即可。
   自定义方块同理：材质名是主标识，自定义 id 是别名。
3. **配置问题必须露面**。`mythic:` / `itemsadder:` / `craftengine:` 目标在没装对应插件的
   服务端上永远命中不了，分别由 `MythicMobsHook.targetProblems` 与
   `CustomContentHooks.problems` 报进 `QuestAdminService.validate`——
   编辑器标红、`/ptxa list`/`info` 与启动日志同时给出，而不是等玩家来问「挖了不涨」。

编辑器侧：MythicMobs 的怪物会被追加进 `/api/catalog` 的实体列表（id 形如
`mythic:SkeletalKnight`，也就是要写进 `target` 的值本身）；IA/CE 的自定义方块与物品
追加进**材质**列表（id 形如 `itemsadder:myitems:ruby_block`）；CustomFishing 的战利品
追加进**独立的 `fish` 列表**（id 是裸的战利品 id，`custom_fish` 的 `target` 字段因此
从自由文本升级成 `FieldType.FISH` 选择器）。三者都不新增选择器组件，只多一份数据。
`/api/schema` 对**目标类型**也开始下发 `available` / `unavailableReason`
（与奖励同一套字段），缺 CustomFishing 时下拉里就选不了它。

**目录里的每条都带 `source`（`minecraft` / `mythicmobs` / `itemsadder` / `craftengine` /
`customfishing`）与 `kinds`（值域），响应另给一份 `sources`**：编辑器据此渲染「按插件筛选」
那一排标签，并按字段声明的值域筛候选。
两条约束写在这里，因为它们都是「不报错但会让人找不到东西」的类型：

1. **`sources` 只列这次真的有内容的来源**，顺序固定（原版 → MythicMobs → ItemsAdder →
   CraftEngine → CustomFishing），显示名由后端给：前端再抄一份名字表必然漂移，
   而列了没内容的来源则会出现「点了没结果的标签」。
2. **鱼单独一栏，绝不混进材质列表**：`custom_fish` 的 target 是战利品 id，
   把它填进 `break_block.target` 只会永远命中不了——正是本项目最想根除的「配了却不生效」。
   前端 `sourceTabs`/`entriesForScope` 按范围取条目，鱼只在 FISH（以及 TARGET 的并集）里出现。

> **CustomFishing 的清单要反射读**：`CustomFishingCatalog` 引用了它的 API，被类加载时
> 就会解析那些类型，因此只能由 `CustomFishingHook` 在确认插件存在后反射调用
> （与监听器同一个模式）；返回的 `FishLoot` 本身不引用任何 CustomFishing 类型，
> 这样素材目录在没装该插件的服务端上也能安全地处理它。

> **素材目录的三个来源缺一不可**：`MaterialCatalog` 由（原版枚举、MythicMobs、
> ItemsAdder/CraftEngine）三份拼成，构造器刻意<b>不</b>提供省略参数的版本。
> 曾经有过 `MaterialCatalog(langFiles, mythicMobs)` 这种便捷构造器，它把第三份当成空实现，
> 于是「装了 CraftEngine 也选不到它的方块」——编译期、运行期都不报错，
> 只有管理员发现列表里没自己的东西（真机实测：装 CraftEngine 时目录 0 条自定义内容）。

> **MythicMobs 的怪物管理器必须延迟解析**：它的 plugin.yml 是 `load: POSTWORLD`，
> 启用得比本插件晚（实测同一秒：本插件 20:01:33 启用完，它才开始启用），
> 而 `getMobManager()` 是它在自己的 onEnable 里创建的。因此 `MythicMobs5Hook`
> 只在创建时取 `MythicBukkit.inst()`，管理器在第一次用到时解析并缓存。
> 早先「创建时读一次并存下来」的写法在真机上的结果就是：装了 MythicMobs 也永远接不上，
> 只有启动日志一行 warn，玩家侧 `mythic:` 目标永远不涨进度。

> 家具（furniture）不在支持范围内：那是实体而不是方块，需要各自的交互事件与持久化，
> 与「方块/物品目标」不是同一类需求。
#### 4.5.1 字段值域（`ValueKind`）：选择器列什么 = 校验放行什么

**问题**：`FieldType` 曾经既表示「渲染成什么控件」又表示「列哪一份清单」
（`MATERIAL` / `ENTITY` / `TARGET` / `FISH`）。于是「挖掘方块只能选方块」这件事没有地方可写：
选择器把全部材质端上来，管理员点得出「挖 64 个苹果」，而这类配置**永远不会命中**、
也不会有任何报错——正是本项目最想根除的那类问题。

**做法**：把值域从控件类型里拆出来，变成字段上的一份声明（`ConfigField.kinds`），
`FieldType` 只剩 `PICKER` 等控件语义。三层各司其职：

| 层 | 位置 | 职责 |
|---|---|---|
| 值域枚举 | `api/schema/ValueKind` | 声明「哪一类值」：`BLOCK` / `PLACEABLE` / `ITEM` / `FOOD` / `ENTITY` / `LIVING` / `BREEDABLE` / `TAMEABLE` / `SHEARABLE` / `FISH` / `ENCHANTMENT`。多个之间是**或**；需要「且」的场合另立一个值域（`PLACEABLE` = 是方块且拿得在手里） |
| 运行期判定 | `core/schema/ValueKinds` | 某个候选值属于哪些值域（`Material.isBlock()`、`EntityType.getEntityClass()` 是不是 `Animals`/`Shearable`…），以及一个配置值能不能命中 |
| 声明点 | 各目标类型的 `schema()` | 一行一个字段：`ConfigField.blocks(...)` / `.items(...)` / `.optionalEntities(..., ValueKind.SHEARABLE)` / `.optionalEnchantments(...)` |

由此三件事天然一致，不会再各说各话：**编辑器选择器列出什么**（按 `kinds` 过滤目录条目）、
**图标怎么推**（`QuestDetailMenu.objectiveIcon` 读同一份 `kinds`）、
**服务端标红什么**（`QuestAdminService.valueProblems` 用 `ValueKinds.check`）。
`ObjectiveFieldTypeConsistencyTest` 把 15 个目标类型的值域逐个钉住——
新增目标类型时忘了声明值域会直接测试失败。

**判断不了 ≠ 不满足**：`Material.isItem()` 与 `Enchantment.values()` 要读服务端注册表，
插件引导阶段与单元测试里会抛异常。那时结论是 `UNKNOWN` → **放行**；
把「我判断不了」当成「你写错了」会让校验在半个环境下满屏误报。
因此材质/附魔的值域只有真机能验，单测覆盖的是生物能力、CustomFishing 清单与各种放行规则。

**两排筛选标签互为条件**（`categoryTabs(catalog, kinds, source)` / `sourceTabs(catalog, kinds, category)`）：
各自只列「另一个筛选器也满足时真的有内容」的选项。不这么做就会出现「单独看都有内容、
合起来一条都没有」的组合（物品字段里选 `CraftEngine` + `方块` 就是这种），
界面上表现为一句「没有匹配的条目」配着「共 1610 项」，看着像坏了；
组件里另有双向兜底，把已经选不到东西的那一项自动收窄回「全部」。
计数文案也必须是**过滤后**的条数（`visible.total`），不是值域全集——否则它会在列表空着时
继续报一个很大的数字。
**目录条目也带 `kinds`**：一次下发、按值域过滤，不做「一个值域一份切片」——
后者会把同一个材质在多个切片里重复塞一遍（流量翻倍），而过滤是纯前端的事。
顺带放宽一处旧限制：材质列表过去只列 `isItem()`，于是**只有方块形态、没有物品形态的材质**
在挖掘目标里选不到；现在列的是「方块 ∪ 物品」，由 `kinds` 决定谁能出现在哪个字段。

### 4.6 只读 YAML 定义来源（`quests/` 与 `presets/`）

管理员常想把任务定义随插件一起发布、或放进 git 做 diff。为此在数据库之外接了一层
**只读**的 YAML 定义来源：`definitions.read-files`（默认开启）打开时，扫描数据目录下的
`quests/`（任务）与 `presets/`（预设），与库里的定义合并成一个视图。代码全在
`core/storage/yaml/`：

| 类 | 职责 |
|---|---|
| `YamlText` | YAML 读写与**类型语义**（下详）；导出时的引号策略也在这里 |
| `DefinitionFolder` | 一个目录的扫描与解析：递归、只认 `.yml`/`.yaml`、文件名即默认 id、坏文件跳过并告警；**唯一的写入路径**是「空目录时铺一次示例」（`isEmpty` + `writeOnce`，只创建不覆盖） |
| `YamlSources<T>` | 解析结果的缓存视图（`all()` 重新读盘，逐条查询走上层缓存） |
| `YamlDefinitions` | 模型 ⇄ 文档映射、部分/整份导出与导入 |
| `MergedSources<T>` | **合并规则**：库优先、冲突告警一次、只读判定 |
| `MergedQuestRepository` / `MergedPresetRepository` | 把上面的规则包成 `QuestRepository` / `PresetRepository` |
| `ExampleFiles`（`core/seed/`） | 出厂示例 ⇄ 文件：id 前缀改写、注释头（见下面「示例文件」一段） |

**与已删除的 JSON 文件后端的边界**（4.1 末尾那条禁令依然有效，这里不是把它加回来）：

| | 已删除的 JSON 文件后端 | 现在的 YAML 目录 |
|---|---|---|
| 谁能写 | 插件（文件就是权威） | **几乎只有人**：插件只在目录空着时铺一次示例，之后从不写 |
| 权威 | 文件 | **数据库**（同 id 冲突时忽略文件那份并告警） |
| 覆盖范围 | 定义 + 玩家数据 | **只有定义**；玩家数据与进度永远在库里 |
| 跨服 | 做不到 | 仍然做不到——所以 MySQL 多服时明确警告不要这样用 |

合并规则一处定死，所有调用方（游戏内命令、GUI、编辑器 HTTP）都走它：

| 情况 | 结果 |
|---|---|
| id 只在文件里 | 生效，`isReadOnly` = true |
| id 只在库里 | 正常读写 |
| 两边都有 | 库里的那条进视图，文件那份被忽略，记一条告警（`warnedConflicts` 去重，`all()` 在启动路径上会被调用多次） |

**只读是行为，不是 UI 提示**：`save` / `delete` 在合并仓储里抛
`DefinitionReadOnlyException`，`EditorApi` 映射成 `409`，`AdminCommand` 与 `AdminQuestMenu`
打印原因为什么改不了。判定写在所有写入路径的共同入口上，因此绕过界面直接调接口也无效。
前端只是把结论显示出来（列表「文件」徽标、禁用开关/删除、编辑页黄色提示条）。

**示例文件**（`core/seed/ExampleFiles`）：目录里一个 YAML 都没有时，把出厂示例铺成
`quests/*.yml` + `presets/*.yml`（12 + 11 个，带注释头）。三个要点：

- **id 必须与库里那套不同**（`example_file_*` vs `example_*`）：同 id 会立刻撞上「库优先」，
  整套文件变成「被忽略的重复定义」还刷一屏告警。因此文件那套把 `file_` 插在 `example_` 之后，
  两套并存；漏改 id 会让文件里那套指向库里的定义；
- **只在空目录里铺**：管理员删掉几个示例、或放了自己的定义，重启时不该把它们变回来。
  代价是「整个目录清空后重启会重新铺一份」，这是刻意的（空目录 = 没配过）；
- **文件名即 id，正文不写 `id`**：复制文件改个名就是一个新任务，示例本身就该示范这一点。

由此带来一处**播种口径的修正**：出厂示例写不写，问的是 `DefinitionRepository.databaseEmpty()`
（只看库）而不是 `count()`（合并视图）。否则铺过一次示例文件之后，库里那套示例就永远不出现了
——而库里那套才是编辑器里能改、能禁用的那一套。`count()` 仍然是「插件实际能用多少」，
列表与统计照旧看它。

**为什么 YAML 在这里可以破例**：4.2 那条「自己定格式一律 JSON」管的是
**存储列的编码**与**编辑器 HTTP 传输**（机器之间交换、不该有人手写）；`quests/*.yml`
是**给人写的配置界面**，与 `config.yml`、语言文件同类，正是 YAML 的适用场景。

代价是 4.2 里那个坑必须自己填：SnakeYAML 是 YAML 1.1，会把 `target: NO` 读成布尔、
把 `012` 读成八进制 10。`YamlText` 因此自带一套 **1.2-core 语义**：

- `StrictResolver`：布尔只认 `true/false`（`yes/no/on/off` 是字符串）；整数只认十进制、
  `0x`、`0o` 前缀（`012` → 12，不是 8）；浮点必须带小数点或指数；时间戳不解析，
  `2024-01-01` 保持字符串；不处理 `<<` 合并键；
- `CoreSchemaConstructor` 替掉 `Tag.INT` 的构造器，配合上面的 resolver 才真正生效；
- 前端用 js-yaml 的 `CORE_SCHEMA`，与后端同语义；
- 导出侧 `QuotingRepresenter` 给「看起来像其它类型」的字符串加单引号，
  避免我们自己写出的文件再被别的 YAML 1.1 解析器读坏。

这三个方向（后端解析、前端解析、导出引号）由 `YamlTextTest` 与
`task-editor-vue/scripts/yaml-check.mjs` 三方对齐钉住——这是本功能风险最高的地方，
`round-trip` 与「`NO` 仍是字符串」都进了构建自检。已知的一处**刻意分歧**：
`.inf` / `.nan` 在后端保持字符串（前端 js-yaml 会解析成数值），因为它们不会出现在任何字段里。

导入/导出：**一个文件一条定义**。`/api/quests/export?ids=a,b` 给一条时回 `<id>.yml`、
给多条或全部时回 zip（一个定义一个文件）；`/api/quests/import` 按<b>魔数</b>区分 zip 与单文件，
zip 里逐条目解析、坏的那条只跳过它自己，单文件解析失败直接 400。
顶层是列表的整份清单被明确拒绝——宽松接受会让「导入一个文件却多出十几条任务」变成静默行为
（同一形状的宽松读取器 `YamlText.readDocuments` 与它上面两个包装已随之删除；
目录扫描那边改成「顶层不是映射就告警」：把列表文件放进 `quests/` 不再表现为「什么都没发生」）。
`replace` 只清数据库，**不碰文件**。预设同理（`kind` 兜底）。

---

### 4.7 预设引用：定义里写 `preset:`，载入时展开

预设原来只是编辑器的便利设施（点一下套用，值复制到任务里）。要让「改一次预设、所有用到它的任务
一起变」，引用就必须**留在定义里**，而不是在套用时展开成副本：

```yaml
objectives:
  - preset: mine-stone      # 类型与字段全部来自预设
```

四个设计点：

1. **一条配置带两份 map**（`QuestObjective` / `QuestReward` 各加了第三个分量 `authored`）：
   `properties` 是<b>生效值</b>（引用时就是预设给的值，引擎与界面都用它），`authored` 是
   <b>作者写的那份</b>（引用时就是 `{preset: id}`，落库与导出按它写回）。只留生效值 → 保存一次
   就把预设的值变成本任务的显式配置，预设之后再也影响不到它；只留作者那份 → 引擎还得自己去查预设。
2. **展开只有一处**：`PresetRefs.resolve` 在 `QuestAdminService` 的 `reload()` 与 `save()` 里各调一次，
   因此「什么最终生效」只有一个结论。`save()` 前先 `trim()` 把引用清成只有 `preset` 键
   （导入的文件、手工改库都可能多带字段），再 `resolve()` 进注册表。
   存储层只搬运 `authored`（DB 的 `properties` 列、`quests/*.yml` 都是它），不认识预设。
3. **引用不带覆盖项**：字段全由预设提供，要偏离去「展开为独立配置」（显式解除引用、把当前生效值
   变成任务自己的配置）。曾经允许 `preset` 与 `properties` 并存（覆盖项赢过预设），代价是同一个
   字段有两个来源：「这个任务实际在做什么」要心算一遍预设 ⊕ 覆盖，表单既没法安全编辑覆盖项，
   也说不清哪些字段被改过。现在引用条目上多写的字段会被校验报出来、并在保存时被清掉——
   报错而不是静默忽略，因为这类写法在早期文档里出现过，会真实存在于 `quests/*.yml` 中。
4. **悬空引用不静默**：预设被删/改名后，展开时类型留空、生效值为空，并报一条校验问题
   （`引用的预设 x 不存在…`）；同一情形下不再额外报「未知目标类型 」（空名字）——
   两条问题讲同一件事时，管理员只该看到说得清楚的那条。校验信息进编辑器、`/ptxa list`
   与启动日志，和软依赖的校验同一处出口。

改动预设后如何生效：编辑器保存预设会调一次 `QuestAdminService.reload()`（顺带重建在线玩家的
进度索引，因为展开可能改变目标类型、进而改变结构指纹）；文件里的预设仍走 `/ptxa reload`。
预设改动是低频操作，全量重载比维护「谁引用了它」的反向索引更简单，也不会漏。

编辑器契约（`QuestJson`）因此给引用节点 `preset`、`type` 与 `resolved`（生效值，界面直接显示），
**不给** `properties`——引用条目没有「任务自己写的字段」，送一个空表过去只会让人以为那里可以填。
`YamlDefinitions` 与前端 YAML 视图同样只写 `preset:`（类型与字段写出来就是一份会过期的副本）。
前端把引用条目显示成「引用预设 xxx」的只读卡片，类型下拉也锁住，出口只有一个：
「展开为独立配置」。

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

## 6. 周期任务（每日 / 每周 / 每月 / 自定义）

四种周期共用一套逻辑（`PeriodicService` + `Periods`），差别只有「周期怎么算」与「配置读哪一段」：

| 类型 | 周期标识 | 锚点配置 | 重置时刻 |
|---|---|---|---|
| `DAILY` | `2026-09-14` | — | `reset-hour`（早于它算前一天） |
| `WEEKLY` | `W2026-09-14`（本周起始日） | `reset-weekday`（MONDAY…SUNDAY） | 锚点日 + `reset-hour` |
| `MONTHLY` | `2026-09` | `reset-month-day`（1-28） | 锚点日 + `reset-hour` |
| `CUSTOM` | `C3d#6893`（周期长度 + 桶号） | `period`（`3d` / `12h`） | 按固定锚点（Unix 纪元）取整 |

- **配置**：`periodic.<type>` 一段一个周期（`enabled` / `amount` / `reset-hour` / 锚点 /
  `refresh-cost` / `refresh-limit` / `pool`），刷新货币顺序是所有周期共用的 `refresh-currency`
  ——它说的是「这台服务器有什么货币」，不是某个周期的属性。每种周期默认值不同：
  每日默认开启，其余默认关闭（开了才会凭空多出一批任务）。
- **发放规则**：全局池（或配置的 `pool`）+ 按玩家抽取，种子 = `hash(playerId, 周期, 刷新次数)`，
  保证同一周期内重登结果一致、刷新后换一批。四种周期各自独立发放，一个玩家可以同时有每日与每周任务。
- **刷新**：`/ptx refresh [类型]`，按 `refresh-currency` 的顺序消耗第一种可用货币
  （金币 / 点券 / 经验，经验是永远可用的兜底），**只重抽该玩家这一种周期**，
  消耗与次数记录在 `period_state`，上限与费用按该周期的配置。不给类型就刷新所有已启用的周期
  （各自扣费、各自提示）；管理员用 `/ptxa resetperiod <玩家> [类型]`，不扣费也不消耗次数。
- **跨期检测**：登录时与定时任务（每 5 分钟）检查 `period_state.period`，不是当前周期就重抽。
- **存储**：`period_state(player_id, type)` 复合主键，四种周期各一行。
  表名与旧版的 `daily_state` 不同是刻意的：主键从 `player_id` 变成 `(player_id, type)`，
  改名后新表自然建出来，不必对旧表做迁移（旧表留在库里不影响任何事）。

判定「什么时候换一批」的逻辑全在 `Periods`（纯函数，输入时刻 + 配置，输出周期标识与下次重置时刻），
因此跨周、跨月、跨年、重置小时这些边界都能用单测钉住，不必等到真实时间走到那一步。

---

## 7. 界面

### 7.1 玩家 GUI（`core/gui/`）

通用菜单框架（`Menu` / `MenuItem`，用 `InventoryHolder` 区分归属），
在此之上实现：周期任务列表（底部一排标签切换四种周期，只显示已启用的）、
任务详情（多目标进度 + 多奖励预览）、点击领取奖励。
刷新按钮永远只刷新「当前正在看的那种周期」——四种周期的费用与上限各不相同，
混在一起时「这个按钮扣哪份钱」根本说不清。
（任务**分类**目前只作为任务的一个字段用于筛选与展示，没有按分类分页浏览的界面。）

### 7.2 管理 GUI

任务列表分页浏览、只读预览（复用任务详情菜单）、启用/禁用、手动重载。
管理 GUI **不提供**目标/奖励编辑——那部分由网页编辑器承担，避免两套表单实现各自漂移。

### 7.3 网页编辑器

Javalin 提供 REST + 静态资源（`/` 返回 Vite 构建产物）：

```
GET    /api/quests            列表          POST   /api/quests          新建/覆盖
GET    /api/quests/export     导出全部定义为 YAML（按 id 排序）
POST   /api/quests/import     导入 YAML（replace=true 只清数据库，不碰 quests/ 里的文件）
GET    /api/quests/{id}       详情          DELETE /api/quests/{id}      删除
GET    /api/players           有记录的玩家   GET    /api/players/{uuid} 该玩家的任务记录与逐目标进度
GET    /api/schema             目标/奖励类型的字段 schema（驱动前端动态表单）
GET    /api/catalog            当前版本支持的物品与实体（图标/材质选择器，含中英文名）
GET    /api/presets            目标与奖励预设（编辑器的便利设施，引擎不认它）
GET    /api/presets/export     导出两类预设为 YAML
POST   /api/presets/import     导入 YAML（kind 只作兜底）
POST   /api/presets/{kind}     保存预设，kind ∈ {objectives, rewards}
DELETE /api/presets/{kind}/{id} 删除预设
GET    /api/stats              统计          POST   /api/reload         重载任务定义
```

> 语言文件不再有 HTTP 接口（`/api/langs` 已随编辑器里的语言页面一起删除）：
> 文案改动直接编辑 `plugins/playerTaskX/lang/<语言>.yml` + `/ptxa reload`，
> 少一条能改文件的远端写入口。

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
定义仓储、预设与玩家仓储、两个类型注册表、存储描述）。
例外只有两条：`/api/players` 要 Bukkit 的离线玩家名与在线状态、`/api/catalog` 要枚举服务端
的 `Material`/`EntityType`，两者都没有可注入的余地，因此它们的**响应形状**没有自动化覆盖
（路由本身未变，仍由真机验证）。

**谁实现这个接口：`PluginEditorServices`，不是主类**。主类曾经直接 `implements EditorServices`，
代价是「编辑器需要什么」成了主类公开契约的一部分——为了让编辑器拿到预设仓储与存储描述，
主类上多了 `presets()` 与 `describeStorage()` 两个只有 web 层会用的 getter。现在这两样由
`startEditor()` 在构造适配器时注入（主类里它们仍是私有字段），主类的公开面只回答
「游戏内功能（命令 / GUI / 变量）需要什么」。适配器的构造参数类型两两不同，
因此「装配时传错顺序」这种事编译期就会失败。

由此形成两层，各有各的理由：

| 类 | 拿到什么 | 为什么 |
|---|---|---|
| `EditorServer`（端口、启停、静态资源、令牌、日志） | **具体插件类** `PlayerTaskX` | 这些都是宿主/生命周期相关的东西，只存在于插件实例上 |
| `EditorApi`（`/api/*` 业务路由） | **窄接口** `EditorServices` | 只碰仓储与数据，因此能脱离服务端回归（`EditorApiTest` 用内存假身真起 Javalin 打 HTTP） |

> 代价如实记一笔：编辑器要新能力时，得同时改接口与适配器的构造参数（两个文件）。
> 换来的是主类不再承担 web 层的需求，以及「编辑器能拿到什么」被收在一个文件里看得见。
> 反过来（拿插件实例再转调它的 getter）少一个类，却把公开面又还了回去，因此没那么做。


> 早期版本内置过一份手工中文表（约 175 行，材质覆盖率仅约两成），
> 已随本方案删除。实测替换后材质与实体的中文覆盖率均为 100%。

**预设不是引擎概念**：预设只是编辑器的便利设施，运行时引擎完全不认识它。
预设与任务定义同库（`preset` 表），不存在单独一份预设文件需要备份或同步。

出厂默认预设（`core/seed/ExamplePresets`，7 个目标 + 4 个奖励）在 `preset` 表为空时写入一次，
与示例任务同一时机（`PlayerTaskX` 启用流程里的 `guard`）。它原先藏在文件后端的 `load()` 里，
后端删除后必须显式接上——否则不报任何错，只是编辑器打开时预设列表变成空的。
判空看的是**数据库**（`databaseEmpty()`，见 4.6）：`presets/` 目录里那份示例与库里这份是
并存的两套，拿合并数量判断会让库里这套永远不出现。

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
7. **Tab 补全拿不到前一个参数的值**：`CommandDispatcher.tabComplete` 传的是空 map，
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
| 7 | 每日任务（全局池 + 确定性抽取 + 刷新扣费 + 跨天） | ✅ 完成（10 项抽取不变量测试；阶段 30 扩成四种周期） |
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
| 20 | 编辑器 REST 层解耦（`EditorServices`）+ 接口级测试 | ✅ 完成（16 项 HTTP 测试；阶段 35 把实现方换成适配器） |
| 21 | 语言键 `common.yes` / `common.no` 被 YAML 布尔语义改名：加引号 + 钉住键的测试 | ✅ 完成（3 项测试） |
| 22 | 游戏内容插件联动：MythicMobs（`mythic:` 击杀目标）+ CustomFishing（`custom_fish` 目标） | ✅ 完成（34 项测试；阶段 31 又加了 ItemsAdder / CraftEngine） |
| 23 | 编辑器：可视化 / **YAML 文本**双视图（任务与预设），YAML 往返与组件渲染进构建自检 | ✅ 完成（12 项 YAML 往返 + 4 个视图渲染） |
| 24 | 只读 YAML 定义来源：`quests/` + `presets/` 目录、库优先合并、YAML 1.2-core 语义、导入/导出改 YAML | ✅ 完成（见 4.6） |
| 25 | 目录空着时铺一份示例文件（`example_file_*`，与库里那套并存）；播种口径改为只看数据库 | ✅ 完成（5 项测试） |
| 26 | 移除编辑器语言文件页面（前端页面/路由/导航 + 后端 `/api/langs` 与相关 4 个 `EditorServices` 方法） | ✅ 完成（删 2 项 HTTP 测试，文案改走文件 + `/ptxa reload`） |
| 27 | 编辑器只读体验：只读定义整行 / 整块压暗、表单用 `<fieldset disabled>` 整体停用；预设页改标签页 + 搜索 + 列表自滚 | ✅ 完成（构建期 SSR 渲染通过） |
| 28 | 导入/导出改为「一条定义一个 yml，多条打包 zip」；列表形状被拒；死掉的宽松读取器一并删除 | ✅ 完成（4 项 HTTP 测试 + 1 项前端预检自检） |
| 29 | 预设可被引用：定义里写 `preset:`，载入时展开；改预设自动重算引用它的任务 | ✅ 完成（见 4.7，9 项 PresetRefs 测试 + 2 项服务级测试 + 1 项 HTTP 契约测试；覆盖项机制见 36 已移除） |
| 30 | 周期任务：每日 / 每周 / 每月 / 自定义四种周期，各自配置与状态；命令、GUI、变量、编辑器全部按类型区分 | ✅ 完成（见 6，12 项周期算法测试） |
| 31 | 自定义内容联动：ItemsAdder + CraftEngine 的物品/方块可作 `target`（别名机制）、进编辑器选择器、缺失时校验报出 | ✅ 完成（见 4.5，8 项接入层测试 + 5 项目录测试） |
| 32 | 真机装上 CraftEngine / MythicMobs / Vault 后暴露的三处接入问题：素材目录没拿到自定义内容、MythicMobs 因 `POSTWORLD` 永远接不上、金币按插件名判 Vault 而非按经济服务在册判 | ✅ 完成（见 4.5，+2 项目录测试 + 4 项经济服务测试） |
| 33 | 编辑器素材目录按**插件来源**筛选：每条带 `source`、响应带 `sources`；CustomFishing 战利品进独立 `fish` 栏 | ✅ 完成（见 4.5，+5 项目录测试 + 1 个前端自检脚本） |
| 34 | **字段值域**：`ValueKind` + 每个目标类型逐一声明 `kinds`；选择器只列该值域、服务端按同一份声明校验「永远不可能命中」的值；`FieldType` 收敛为 `PICKER` | ✅ 完成（见 4.5.1，+9 项值域测试 + 11 项目标值域一致性测试 + 2 项校验接入测试） |
| 35 | `EditorServices` 改由 `PluginEditorServices` 适配器实现（主类不再承担 web 层契约，`presets()` 与 `describeStorage()` 两个「只有编辑器用」的 getter 随之删除） | ✅ 完成（见 7，+2 项转交测试） |
| 36 | 编辑器里能**真正建立**预设引用：弹层点预设 = 引用（另给「复制一份」）；**移除覆盖项机制**（引用只认 `preset`，多写的字段报校验问题并在保存时清掉） | ✅ 完成（见 4.7，+2 项 PresetRefs 测试 + 1 项 HTTP 契约测试 + 1 项真库往返测试） |

**测试总量：282 项全部通过**（33 个测试类，全部 failures=0 / errors=0）：
存储 19（`StorageIntegrationTest`）+ 编辑器接口 18（`EditorApiTest`）+
YAML 定义来源 14（`YamlDefinitionSourceTest`）+ YAML 文档映射 8（`YamlDefinitionsTest`）+
YAML 类型语义 8（`YamlTextTest`）+ 合并仓储 7（`MergedDefinitionRepositoryTest`）+
预设引用 9（`PresetRefsTest`）+ 示例文件 5（`ExampleFilesTest`）+
周期算法 12（`PeriodsTest`）+
引擎 12（`ProgressServiceTest`）+ 命令帮助 12（`YLibCommandHelpTest`）+
任务管理 14（`QuestAdminServiceTest`）+
素材 19（`MaterialCatalogTest`）+ 值域 9（`ValueKindsTest`）+ 奖励 21（`CurrencyTypeTest` 8 + `ExpUtilTest` 9 + `MoneyRewardTest` 4）+
编辑器宿主转交 2（`PluginEditorServicesTest`）+
自定义钓鱼 9（`CustomFishObjectiveTest`）+ 自定义内容接入 8（`CustomContentHooksTest`）+ 结构指纹 8（`StructureFingerprintTest`）+
字段值域一致性 11（`ObjectiveFieldTypeConsistencyTest`）+ 奖励领取 4（`RewardServiceTest`）+
示例任务 6（`ExampleQuestsTest`）+ 监听器 6（`ItemListenerCraftAmountTest`）+
GUI 图标 6（`QuestDetailMenuTest`）+ 别名匹配 6（`TargetMatchAliasTest`）+
示例预设 5（`ExamplePresetsTest`）+ 进度渲染 5（`ProgressDisplayRenderTest`）+
CustomFishing 监听 5（`CustomFishingListenerTest`）+ MythicMobs 目标 5（`MythicMobsHookTest`）+
击杀监听 5（`EntityListenerTest`）+ 语言文件 3（`LanguageFileTest`）。
统计口径：`.\gradlew.bat :core:test --rerun` 之后读 `core/build/test-results/test/*.xml`
逐套件累加（33 个 XML），不是靠日志里的汇总行。

**代码规模**（含空行，按文件行数累加）：后端主代码 `api/src/main` 1071 行 + `core/src/main` 14276 行
＝ **15347 行 / 111 个 java 文件**；测试 `core/src/test` **6614 行 / 36 个文件**
（`api/src/test` 为空，api 只放模型与接口，行为测试都在 core）；
前端 `task-editor-vue/src` **6021 行 `.vue` + 2193 行 `.ts`/`.js` ＝ 8214 行 / 31 个文件**
（另有 `scripts/` 下三个构建期自检脚本：YAML 往返与提交形态 15 项、素材目录筛选 15 项、4 个视图 SSR 渲染，不计入 src）。

文本渲染的测试**不在本插件**，而在 YLib 侧（`YLib/core/src/test`，15 项 =
`TextRendererTest` 11 + `RealWorldMessageTest` 4）：
渲染能力既然上移到了 YLib，它的行为就该在 YLib 钉住，否则每个消费方只能各测各的。

### 真机验证结论（Folia 26.1.2-8）

**编辑器接口的端到端验证（含写路径）**：`GET /api/quests`（13 条，校验问题如实下发）、
`GET /api/quests/{id}`、`GET /api/schema`（14 目标 / 5 奖励）、`GET /api/catalog`
（1506 材质 + 157 实体）、`GET /api/presets`、`POST /api/reload` 全部正常；
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

**清空数据库后重新初始化**：建表清单为 6 张（quest / quest_objective / quest_reward /
player_quest / period_state / preset）；
早期一次真机验证里 `PRAGMA integrity_check` 为 ok。
（`meta` 表已随一次性迁移代码删除，见文末「删除一次性迁移代码」。）

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

**只读 YAML 定义来源的真机冒烟（同一台测试服）**：在 `run/plugins/playerTaskX/quests/` 放两个文件——
一个文件名即 id 的 `smoke_yaml_probe.yml`、一个与库里示例任务同 id 的 `example_daily_mine.yml`，
重启后：

```
[playerTaskX] YAML 定义: 任务 example_daily_mine 同时定义在数据库与 quests/example_daily_mine.yml，
              已忽略文件里的那份（库优先）
```

告警恰好一条（`findAll` 在启动路径上被调用多次，`warnedConflicts` 去重生效）。
接口侧逐条确认：`GET /api/quests/smoke_yaml_probe` 的 `source` 为 `file`、
`GET /api/quests/example_daily_mine` 为 `database`（库里那份的名称胜出）；
对只读 id 的 `POST /api/quests` 与 `DELETE` 都是 **409**，消息点名了要改哪个文件；
`GET /api/quests/export` 输出干净（`refreshCost: 1000` 是整数，不是 `!!float`）。
导入侧用**含 `target: NO` 与 `target: yes` 的目标**验证类型语义：`POST /api/quests/import?replace=false`
返回 `{"total":14,"skipped":[],"ok":true,"imported":1}`，读回后两个值都是**字符串** `"NO"` / `"yes"`——
这正是 4.6 那套 1.2-core resolver 要解决的核心风险（修好前实测会变成布尔 `false`）。
探针文件与导入的任务随后已清理。

**铺示例文件的真机冒烟（同一台测试服，先清空 `quests/` 与 `presets/`）**：启动时两个目录
各被铺了一份，日志如实报数：

```
[playerTaskX] quests/ 是空的，已写入 12 个示例任务文件（只读来源，可自由删改）
[playerTaskX] presets/ 是空的，已写入 11 个示例预设文件（只读来源，可自由删改）
[PlayerTaskX] 已载入 24 个任务
[playerTaskX] PlayerTaskX 已启用（24 个任务，15 种目标，5 种奖励）
```

接口侧：`/api/quests` 24 条（12 条 `source=database` + 12 条 `source=file`）、
`/api/presets` 14 目标 + 8 奖励（库与文件各一半），**没有一条「库优先」冲突告警**——
两套 id 前缀不同正是为此。

再验一次「不重复补」：删掉 `quests/example_file_daily_torch.yml` 后重启，
日志里没有「已写入」、任务数 23、该文件没有被重新创建；把它放回去再 `POST /api/reload`，
任务数回到 24——即「只铺一次、之后尊重目录现状」与「reload 会重读文件」都成立。

**预设引用的真机冒烟（同一台测试服）**：导入一个引用预设的任务，`GET /api/quests/<id>` 如实给出
类型与生效值。这一轮冒烟做在「引用 + 覆盖项」还是合法写法的版本上，当时记录的形状是：

```json
{"preset":"example_file_mine-stone","type":"break_block",
 "properties":{"amount":128},"resolved":{"amount":128,"target":"STONE"}}
```

即类型来自预设、`resolved` 是合并后的生效值。随后又验了「改预设立即生效」：另建一个库里的预设、
让另一个任务引用它，再改这个预设（DIRT/8 → SAND/99），保存返回 200 之后直接读任务，
`resolved` 已变成 SAND/99、`properties` 仍是空的 —— 引用没有被写死，也不需要 reload
（保存预设会触发一次重载）。导出该任务时 YAML 里没有 `resolved`（派生数据不落盘）。
删掉被引用的预设：任务的目标类型变空，问题清单给出
「引用的预设 xxx 不存在（已删除或 id 写错），这个目标当前不生效」。探针任务与预设随后已清理。

> 覆盖项机制随后被移除（见 4.7 第 3 点）：引用条目只认 `preset`，`properties` 不再出现，
> 多写的字段报校验问题并在保存时清掉。改动后**没有**重跑真机冒烟，改由
> `EditorApiTest.presetReferenceRoundTrip`（真实 QuestAdminService + 假仓储 + 真 HTTP）
> 与 `PresetRefsTest` 钉住契约；浏览器里的点击路径（弹层点预设 = 引用、条目右侧「复制」）
> 未在真机上点过。

同时验证了两条重要的健壮性行为：

- **软依赖缺失时优雅降级**：未安装 Vault/PlayerPoints 时，对应奖励类型标记为不可用并写入
  启动警告，示例任务被校验标红，但插件照常启用、命令与编辑器均正常——不会因软依赖缺失而崩。
- **中文编码**：日志文件与生成的语言文件均为正确 UTF-8（控制台乱码只是 Windows 终端显示问题，
  插件内部与落盘内容无误）。

### 尚未验证的部分（如实记录）

- **MySQL 路径未做真机验证**：SQL 由 `Dialect` 统一生成、与 SQLite 共用同一套仓储代码，
  但 `ON DUPLICATE KEY UPDATE` 分支、HikariCP 连接与 `CREATE INDEX` 的容错路径
  只在代码与 SQLite 测试层面覆盖，没有连过真实 MySQL 实例。
- **ItemsAdder / CraftEngine 的接入只在单测层面验证过**：本机测试服没有装这两个插件，
  验证到的是「插件照常启用、`/api/catalog` 不变、写了 `itemsadder:` 的目标被校验报出来」；
  反射绑定的方法名（`CustomStack.byItemStack`、`CraftEngineBlocks.getCustomBlockState` 等）
  是照它们的公开 API 与源码写的，但**没有在真机上跑过**——装上去之后若签名对不上，
  日志里会给一条「接入 … 失败」的 warn，对应目标不可用而不会影响其它功能。
- **周期任务的发放/刷新只有单测覆盖**：四种周期的「换一批」判定由 `PeriodsTest` 钉住
  （重置小时、跨周、跨月、跨年、自定义分桶），存储往返由 `StorageIntegrationTest` 在真实
  SQLite 上跑；但本机没有可登录的客户端，因此「登录时发一批、跨周期换一批、扣费刷新」
  这条链路只验证到接口与配置层（导入 WEEKLY/CUSTOM 任务、`/api/stats` 计数正确、启动无异常）。
- **玩家实际游玩路径未验证**：需要真人进服（挖掘/合成/击杀等）才能确认进度累加、
  actionbar 推送、GUI 点击等表现层行为；本次只验证到「插件启用 + 命令注册 + HTTP 接口」。
- **网页编辑器的界面操作未做浏览器端人工确认**：接口层已实测；纯前端的交互
  （搜索、多选、拖拽/排序、可视化 ⇄ YAML 切换、**导出/导入 YAML 的弹窗与文件下载**）只做到
  「构建 + 类型检查 + YAML 往返断言 + SSR 渲染各视图各一遍」，浏览器里的实际手感与排版仍未人工确认。
- **YAML 定义文件的边界情况只由单测覆盖**：递归子目录、`presets/rewards/` 目录兜底 `kind`、
  坏文件跳过、只读 id 被批量操作跳过、写坏的目录（只读权限）等分支都有测试，
  真机只走了主路径（铺示例、`example_file_*` 只读、缺文件不补、reload 重读）。
- **未安装 Vault / PlayerPoints 的服务器**：刷新费用会按「金币 → 点券 → 经验」自动
  兜底到经验；该回退路径有单元测试覆盖，但没有在缺少经济插件的真机上跑过全流程。
- **`NORMAL` 任务目前没有发放入口**：玩家拿到的任务只有周期任务一条来源
  （`PeriodicService` 直接写 `player_quest`，`ProgressService.assign` 在生产代码里无人调用）。
  普通任务因此只存在于定义与编辑器里，缺的是「接取常驻任务」这一步。
- **MythicMobs / CustomFishing 只做了「未安装」这一支的真机验证**：本机测试服没有这两个插件，
  验证到的是「插件照常启用、目标类型标为不可用、`mythic:` 目标被校验拦下」；
  装上插件后的实际击杀/钓获计数只有替身事件与反射入口的单测覆盖，
  真实插件版本（MythicMobs 5.x 的具体小版本、CustomFishing 2.3.x）未在真机上跑过。




### 已落地的关键实现细节

- **一件事只有一个出处**：凡是「多个入口都要做同一件事」的地方都收敛到一处，
  避免同一个功能两种口径。已收敛的几处：
  - 任务校验（编辑器标红 / `/ptxa list` / 管理 GUI）→ `QuestAdminService.validate`；
  - 刷新结果的提示措辞（玩家命令 / 管理员命令 / GUI 按钮）→ `PeriodicService.RefreshResult`
    自带回复消息，调用方只调 `report(...)`，不再各自拼 success/cost/limit/error；
  - 启用状态切换（落库 + 同步注册表 + 重建索引）→ `QuestAdminService.setEnabled`；
  - 文本装配（渲染 / 数字去小数尾巴 / 配置表摊平 / 类型显示名）→ `core/text/Texts`；
  - 命令帮助清单 → `CommandHelp.ofAnnotations` 从注解生成，不再手写第二份；
  - 定义来源的合并与只读判定（库优先 / 冲突告警去重 / 只读写入抛异常）→
    `MergedSources` + 两个 `Merged*Repository`：游戏内命令、管理 GUI、编辑器 HTTP
    拿到的是同一个结论，不需要各自再判断一次「这个 id 能不能改」（见 4.6）。
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
  避免 `NoClassDefFoundError` 让插件整体无法加载。金币的判据是**经济服务注册**而不是插件名：
  Vault 只提供 API、钱由经济插件实现，因此「有没有经济插件」只有服务管理器答得上来——
  只装 Vault（或 VaultUnlocked）而没有任何经济插件时，插件名查得到、服务却是空的。
  `MoneyReward.economy()` 直接查服务，失败措辞也从「未安装 Vault 或没有经济插件」改成
  「未检测到经济插件」（原措辞把两种情形混在一起，真机排查时正是它把人带偏）。
- **依赖只留用得上的**：`fastjson2`、`javalin-openapi` / swagger / redoc、`jackson-dataformat-yaml`
  从未被引用过，已从构建脚本删除；JSON 编解码全项目只有 `JsonCodec` 一个 `ObjectMapper`
  （编辑器曾自带第二个）。
- **YAML 定义只有一份字段定义**：`YamlDefinitions` 的文档映射直接复用编辑器 JSON 契约
  （`QuestJson` / `PresetJson`），因此 `quests/x.yml`、导出的清单、编辑器的 YAML 视图
  三者可以互相粘贴，新增目标类型时这里一行都不用改。它只决定「写哪些键、按什么顺序、
  省略哪些空值」（空字符串与空列表不写出去：手写文件里堆一串 `category: ''` 只会让人以为必须填）。
- **出厂示例有两套，且刻意不同前缀**：库里 `example_*`（可编辑，编辑器里改）、
  文件里 `example_file_*`（只读，编辑器里对照格式）。`ExampleFilesTest` 钉住「两套只差前缀、
  内容等价」，`ExampleFiles` 只在目录为空时写一次——这几条都是
  「不报错但会让人困惑很久」的类型（id 撞库→整套被忽略、
  反复补文件→删了又回来）。
- **包归位**：三个类型/任务注册表实现同处 `core/registry`（`api.registry` 也是这么分组的），
  `core/quest` 只留 `QuestAdminService` 这一处「任务定义维护入口」。
- **两个数据库类合成一个**：`JdbcDatabase` 同时是「执行 SQL 的引擎」与「打开 SQLite 文件 /
  MySQL 连接池」的工厂。原先 `SqliteDatabase` / `MysqlDatabase` / `JdbcDatabase` 三个类
  靠一个 `ConnectionProvider` 接口串起来，而通用执行逻辑只有一个实现——策略差异其实只有
  「借出/归还」与「怎么关」三件事。
- **删除一次性迁移代码**：`DefinitionMigrator`（130 行）把「旧版本存在数据库里的任务定义」
  导出到 JSON。项目未发布，不存在这样的部署；连同 `Schema` 里的 `meta` 表、
  容错 `ALTER`、以及从未被写入过的 `quest.sort_order` 列一并删除。

