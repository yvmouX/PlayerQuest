# PlayerTaskX 重构架构

> 本文件是重构的设计依据。允许修改架构，不考虑旧版本兼容性。
> 核心原则：**代码简洁、扩展点收敛**——新增一种任务目标/奖励类型，应当只写一个类，
> 不改引擎、不改 GUI、不改存储。
>
> 想按「哪个类在哪、负责什么」查代码，看 [`CODE_MAP.md`](CODE_MAP.md)（逐包逐类一句话）；
> 本文件讲的是**为什么这样拆**。

---

## 1. 总体结构

```
PlayerTaskX/
├── api/          对外暴露的接口与数据模型（其它插件/扩展依赖此模块）
├── core/         实现 + 引擎 + 存储 + GUI + 命令
└── YLib/         子模块：调度器 / 日志 / 配置 / 命令 / 消息 基础设施
```

`api` 不含实现，`core` 依赖 `api`。

> **曾经有一个内置网页编辑器**（Vue 3 + Vite 前端、Javalin 提供的 `/api/*` 与静态资源、
> `MaterialCatalog` 素材目录、`LangFileStore` 译名下载），已**整体删除**；
> 它当年做的事现在由**游戏内任务编辑器**（`/ptxa menu`，见 7.2）与 `/ptxa` 命令分担。
> 本文件里凡提到「编辑器」的历史段落都已按现状改写；`CODE_MAP` 与 `docs/` 同步。

### 依赖策略

| 依赖 | 用途 | 作用域 |
|---|---|---|
| `spigot-api` | 服务端 API | compileOnly |
| YLib（复合构建） | 调度器/日志/配置/命令/消息 | implementation |
| `adventure-text-minimessage` + `serializer-legacy`/`plain` | MiniMessage 文本 | 由 YLib 以 `api` 提供（4.x，Java 8 字节码）；**shadow 时 relocate** 避免与 Paper 原生 Adventure 冲突 |
| `sqlite-jdbc` / `mysql-connector-java` | 存储 | implementation / compileOnly |
| `HikariCP` | MySQL 连接池 | implementation |
| `snakeyaml`（服务端自带，随 `spigot-api` 编译期可见） | `quests/` / `presets/` 只读 YAML 定义（4.6）；**不打包**，服务端本来就有 | 服务端提供 |
| `VaultAPI` / `playerpoints` | 金币 / 点券 | compileOnly（软依赖；VaultAPI 另有一份 testImplementation，见 8） |
| `placeholderapi` | 变量 | compileOnly（软依赖） |

**JSON 编解码统一走 `JsonCodec` 的单个 `ObjectMapper`**（jackson-databind，见
`build.gradle.kts`）：`properties` / `progress` 列都用它，不再出现「一份模型两套序列化」。

### 包边界与约定

这几条原先写在各自的 `package-info.java` 里。Javadoc-only 的文件编译产物是个空的
`package-info.class`、没有任何东西引用，因此把它们搬到这里、删掉文件——
约定仍然有效，只是不再随包一起走。

**包名要能当索引**（本节的总原则）：`core/<域>` 放该域的东西，**契约与实现分开放**——
契约在 `api/`，实现在 `core/` 的对应子包；一个契约有多家实现时，实现单独成包
（`storage/jdbc`、`storage/yaml`、`integration/customcontent`、`integration/mythicmobs`、
`integration/customfishing`、`gui/menu`）。看到包名就该知道里面是「契约 / 某一家的实现 / 某一层的服务」。

**`core/storage/`（根包 = 契约与装配入口）**

- 根包只放契约与装配入口：`Database`、`QuestRepository`、`PresetRepository`、
  `DefinitionRepository`、`PlayerQuestRepository`、`StorageException`、`DefinitionReadOnlyException`；
  `Dialect` 与 `RowMapper` 虽是实现味很浓的名字，但它们出现在 `Database` 的方法签名上，
  属于契约的一部分，因此也留在根包；以及唯一的装配点 `DatabaseFactory`。
  **包外代码只需要 import 这些。**
- 文档映射与编解码在 `codec` 子包（见下），JDBC / YAML 两种后端各自成子包。
- **SQL 字符串只允许出现在 `jdbc` 子包内**，且列名一律经方言转义。
- 未知存储类型回退 SQLite 而不是启动失败——写错一个单词不该让插件起不来。
- 不要在服务端运行期间用外部工具改 SQLite 文件：WAL 会回滚外部连接的 DDL，
  得出的结论是错的（踩过）。

**`core/storage/codec/`（编解码与文档映射）**
- `JsonCodec`（JSON 文本 ⇄ `Map`，**读时绝不抛异常**，脏数据降级为空并记日志）、
  `QuestJson` / `PresetJson`（文档 → 模型；「模型 ⇄ 文档」的字段名只此一份，
  `quests/*.yml` 与数据库的 `properties` 列共用）。
- 它们原先躺在 `core/storage` 根包里，把「契约包」污染成「契约 + 工具」——分包后
  根包的 import 清单就是「这个存储层对外承诺了什么」。

**`core/storage/jdbc/`（JDBC 实现细节）**
- **连接策略是刻意的两种**：SQLite 写入全局串行，用连接池反而制造 `SQLITE_BUSY`，
  因此走单连接长驻 + WAL；MySQL 走 HikariCP 池化，连接用完必须归还。两种策略都在
  `JdbcDatabase` 的两个工厂里，执行逻辑只有一份。
- **方言**：`Dialect`（根包，契约的一部分——`Database.dialect()` 暴露它）集中消化语法差异
  （upsert、标识符转义）；`Schema` 是建表 DDL 的唯一来源，所有语句经方言生成，
  同一份定义在两种库上都成立。
- `Sql` 收敛 JDBC 参数绑定样板，`EnumText` 负责枚举列的容错解析。

**`api/schema/`（类型自描述：字段的「声明」）**

- 这里是「一种目标/奖励类型长什么样」的声明，三个文件各管一件事：
  `ConfigurableType`（`id` + 显示名 + 字段表，目标与奖励共有的形状）、
  `ConfigField`（单个字段：键、显示名、说明、**控件形状**、**值域**）、
  `ValueKind`（值域词汇表：方块、可放置、物品、实体、活体、可繁殖、可驯服、可剪毛、鱼、附魔）。
- GUI 与配置校验**据此工作**：新增一种目标类型 = 写一个类 + 一行 schema，界面代码一行不改。
- **字段描述就是这五样**（`ConfigField` 是 5 个分量的 record）。控件形状（`Shape`：文本 / 整数 /
  小数 / 开关 / 候选）决定编辑器弹什么控件：`CANDIDATES` 进候选清单，其余走聊天输入。
  它和「值域」互相校验：候选字段必须有值域，其余形状不许有值域（record 的紧凑构造器直接挡）。
  阶段 41 曾把它删掉（当时唯一的读取方是网页编辑器），阶段 44 随游戏内编辑器回来；
  当年的「是否必填 / 默认值」**没有**回来——它们至今没有读取方。
- **没有值域的常量不存在**：`ValueKind` 里的每个成员都必须有字段声明它，
  否则运行期判定与它同在的那个分支永远不会被走到（`ObjectiveFieldDomainTest` 钉住这条）。
- **这个包不 import 任何 `org.bukkit` 类型**（`api` 里只有 `ProgressContext`、`RewardType`
  碰 Bukkit）：字段声明与版本无关，是给扩展点作者看的契约。
  「这个材质算不算方块」这类只有运行期能回答的问题不放这里，见下。

**`core/schema/`（同一份 schema 的「运行期语义」）**

- 目前只有 `ValueKinds`：把 `ValueKind` 落到**当前服务端**的枚举与接口上——
  某个候选项属于哪些值域（`Material.isBlock()`、`EntityType.getEntityClass()` 是不是
  `Animals` / `Tameable` / `Shearable`…），以及某个配置值能不能命中（校验标红的那条判定）。
  它要读 Bukkit 注册表，因此进不了 `api`；而它有两个方向的消费者——定义校验
  （`quest/QuestAdminService`）与表现层（`gui/menu/QuestDetailMenu` 按值域决定展示，
  `gui/editor/CandidateCatalog` 按值域列候选、`EditorLookup` 按值域推图标）。
- 这个包**故意只有薄薄一层**：它不持有状态、不装配任何东西，只回答两个纯问题
  「这个值属于哪些值域」「这个值能不能命中」。以后若还有「schema 的运行期语义」
  （字段值解析、默认值填充之类）也归这里。

**`core/listener/`（事件监听层）**

- 监听器只做一件事：把 Bukkit 事件翻译成 `ProgressContext` 投递给引擎。
  **判定逻辑不写在这里**：不允许出现针对具体目标类型的分支（见 `ProgressListener`）。
- **按事件域分组，而不是一个动作一个类**：动作语义已经组织在
  `core/objective/` 的各 ObjectiveType 里，GUI 的字段展示也由它的 schema 推导；
  监听器再按动作拆一套就会出现两套平行的结构要同步维护。分组与 `org.bukkit.event`
  的包分类一致：`BlockListener`（挖掘/放置/交互）、`EntityListener`（击杀/垂钓/剪切/繁殖/驯服/交互）、
  `ItemListener`（合成/消耗/附魔）、`TextListener`（发言/执行命令）、
  `PlayerListener`（会话生命周期，不推进度）。
- 每个动作的 handler 必须带 javadoc，写清动作语义与该事件特有的坑（异步、双触发、
  数量口径之类）——这是移植与排查时的第一手资料。
- 新增动作先按事件域归位，确实没有归属再新建域类，而不是默认新建动作类。

**`core/integration/`（软依赖接入，按「一家插件一个包」分）**

- 根包只放**接入用的公共设施**：`SoftDependency`（插件是否加载、版本多少）与
  `Reflect`（按名字取类取方法，取不到一律 `null`）。这两个是唯一被多个子包共用的东西，
  也因此是 `integration` 里仅有的两个 `public` 工具类。
- `customcontent/` = 契约 `CustomContentHook` + 它的两家实现（`ItemsAdderHook`、
  `CraftEngineHook`）+ 汇总门面 `CustomContentHooks`。加第三家（Oraxen / Nexo…）时
  **只在这个包里加一个类**，监听器与校验都不用改——这正是「实现单独成包」的好处。
- `mythicmobs/` = 契约 `MythicMobsHook` + 唯一的实现 `MythicMobs5Hook`
  （接口留着是为了能塞假身测试，`FakeMythicMobsHook` 与它同包）。
- `customfishing/` = `CustomFishingHook`（注册监听器）+ `CustomFishingListener` +
  `CustomFishingCatalog` + `FishLoot`：这一家的东西不出包，反射加载的边界也在包内。
- 四家接入都不互相 import：需要「谁装了、版本多少」时走根包那两个工具类。

**`core/` 其余子包的角色**
- `command` 命令、`config` 配置、`gui` 箱子菜单框架（**具体菜单在 `gui/menu`**）、
  `listener` 事件翻译、`display` 进度展示（actionbar / title）、`placeholder` 变量、
  `text` 文本装配与推送。
- `objective` 与 `reward` 各自**只放对应扩展点的实现类**，外加该域的内置清单
  （`ObjectiveBuiltIns` / `RewardBuiltIns`）——「这一家有哪些实现」在一个包里看得全，
  新增类型只改本包；两者与 `api/objective`、`api/reward` 的契约成对。
- `engine` 是「执行任务定义的两件事」：`ProgressService`（推进度）与 `RewardService`（发奖励），
  外加 `StructureFingerprint`（结构指纹）与 `ApplyResult`（引擎的返回类型）。
- `quest` 只有 `QuestAdminService`（定义维护入口），`preset` 只有 `PresetRefs`（预设引用展开）——
  两者原本同包，但一个是任务语义、一个是预设语义，拆开后各自名实相符。
- `period` 是周期任务及其专用件：`Periods`（纯算法）、`PeriodicService`（服务）、
  `CurrencyType`（刷新费用的货币，只有它用）。
- `registry` 只放三张注册表的实现；「插件自带哪些类型」不在这里，在各自的域包里（见上）。

---

## 2. 数据模型（扁平：任务 = 多目标 + 多奖励）

```
Quest                    任务定义（静态，由命令/管理界面或只读 YAML 文件维护）
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
类型是数据而非代码——配置、数据库、GUI 三处都要表达「某类型的参数」，
统一成 `Map + 类型自描述的 schema` 后，这三处只需要一份通用实现。
`ObjectiveType` / `RewardType` 提供 `schema()`，GUI 与校验据此工作，
因此新增类型无需改动任何界面代码。

---

## 3. 三个扩展点（重构的核心）

### 3.1 目标类型 `ObjectiveType`（api 模块）

```java
public interface ConfigurableType {     // 目标与奖励共有的形状
    String id();                        // 如 "break_block"
    String displayName();
    List<ConfigField> schema();         // 该类型的配置字段（供 GUI 展示与校验）
}

public interface ObjectiveType extends ConfigurableType {
    Trigger trigger();                  // 该类型响应的动作
    int match(ProgressContext context, Map<String, Object> properties);
    default boolean targetMatches(...); // 忽略大小写、逗号多值、空或 * 为任意
}
```

`ConfigurableType` 存在的理由：凡是**展示与校验**类型的地方（GUI 图标推导与字段说明、
配置校验、管理员命令的类型名回显）需要的都只是 `id/displayName/schema`
这三件事。抽出这一层后，这些地方可以只写一份实现，而不必给目标与奖励各写一份近乎相同的代码。

内置 15 种目标：`craft` 合成、`break_block` 挖掘、`fish` 垂钓、`custom_fish` 自定义钓鱼、
`place_block` 放置、`consume` 消耗、`kill` 击杀、`submit` 提交、`enchant` 附魔、`shear` 剪切、
`breed` 繁殖、`tame` 驯服、`command` 命令、`interact` 交互、`chat` 发言。

其中 12 种的行为完全一致（`target` 命中就加本次数量），它们**不是 12 个类**，
而是 `ObjectiveBuiltIns` 里的 12 行 `TargetObjective` 数据：类型之间只差 id、响应动作与
`target` 字段的语义类型（材质 / 实体 / 自由文本）。真正有自己判定逻辑的只有
`InteractObjective`（`mode` 匹配）、`ChatObjective`（关键词包含匹配）与
`CustomFishObjective`（鱼 id + 最小尺寸，且依赖 CustomFishing，见 4.5）。
「类型自描述」没有损失——`schema()` 仍由类型自己给出，GUI 与校验照旧读同一份声明。

### 3.2 奖励类型 `RewardType`

```java
public interface RewardType extends ConfigurableType {
    void grant(Player player, QuestReward reward);
    default boolean available() { return true; }
    default String unavailableReason() { return ""; }
}
```

内置三种：`money` 金币（经 Vault 的 `Economy` 服务）、`points` 点券(PlayerPoints)、
`command` 自定义命令（控制台执行，支持 `%player%`）。

**为什么没有「物品」与「经验」奖励**：这两样用命令奖励就够了（`give %player% diamond 3`、
`xp add %player% 200 points`），做成插件自己的类型则要把 `material` / `amount` / `name` / `lore`
（以及将来的附魔、组件、模型数据……）在插件里重做一遍，而 `/give` 早就做完了；命令还能顺带
用上别的插件的发放入口。经验与物品<b>既不是</b>奖励类型，<b>也不是</b>刷新费用的货币：
扣费只认金币与点券（见 6），两者都不可用时刷新直接不可用并明确提示——不留「免费刷新」或
「扣经验」的兜底，前者会让配错的 `refresh-cost` 看不出来，后者要把玩家的等级与经验进度当余额维护。

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
聚合查询（`countPlayers`）与 `transaction`，这些定义侧都不需要。
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
> 需要 diff 或进版本控制时，把定义写成数据目录下 `quests/` 与 `presets/` 里的 YAML（见 4.6）——
> 那是只读来源，库仍是唯一权威。

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
（`properties` 列、进度列）一律 JSON**；只有用户手写的配置文件、语言文件
与 `quests/` `presets/` 定义目录是 YAML——那是给人看的界面，不是数据交换格式。

### 4.3 目标结构指纹（防静默错配）

玩家进度按**目标下标**记录（`{"0": 32}`），因此调换目标顺序后，旧进度会被套到
别的目标上（挖了 32 个石头显示成「发言 32 次」），而语法校验查不出任何问题。

做法：玩家接手任务时记下目标列表的摘要（`StructureFingerprint.fingerprint`，对顺序敏感），
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
没装的服务器上完全不参与运行。一家插件一个子包（`mythicmobs` / `customfishing` /
`customcontent`），根包只留两家共用的探测与反射工具（见第 1 节的包约定）。
接入方式与三条不变量：

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
两家共用一个接口：加第三家（Oraxen / Nexo…）时监听器与校验都不用改。

三条不变量：

1. **没装插件时行为与从前完全一致**。`MythicMobsHook.create()` 返回 `null`、
   CustomFishing 的监听器根本不注册、`CustomContentHooks` 是空实现；
   `custom_fish` 目标类型照常存在，但 `available()` 为 false，校验会标为不可用。
2. **同一个对象只能计一次**。一只自定义怪同时是 `ZOMBIE` 与 `mythic:SkeletalKnight`；
   若为两个名字各推一次动作，「击杀任意生物」会被计成两次。因此
   `ProgressContext` 带上 `aliases`：一个动作、多个等价标识，判定时任一命中即可。
   自定义方块同理：材质名是主标识，自定义 id 是别名。
3. **配置问题必须露面**。`mythic:` / `itemsadder:` / `craftengine:` 目标在没装对应插件的
   服务端上永远命中不了，分别由 `MythicMobsHook.targetProblems` 与
   `CustomContentHooks.problems` 报进 `QuestAdminService.validate`——
   `/ptxa list`/`info`、管理界面与启动日志同时给出，而不是等玩家来问「挖了不涨」。

> **CustomFishing 的清单要反射读**：`CustomFishingCatalog` 引用了它的 API，被类加载时
> 就会解析那些类型，因此只能由 `CustomFishingHook` 在确认插件存在后反射调用
> （与监听器同一个模式）；返回的 `FishLoot` 本身不引用任何 CustomFishing 类型，
> 校验才能安全地拿它与任务里的 `custom_fish` id 对照。

> **MythicMobs 的怪物管理器必须延迟解析**：它的 plugin.yml 是 `load: POSTWORLD`，
> 启用得比本插件晚（实测同一秒：本插件 20:01:33 启用完，它才开始启用），
> 而 `getMobManager()` 是它在自己的 onEnable 里创建的。因此 `MythicMobs5Hook`
> 只在创建时取 `MythicBukkit.inst()`，管理器在第一次用到时解析并缓存。
> 早先「创建时读一次并存下来」的写法在真机上的结果就是：装了 MythicMobs 也永远接不上，
> 只有启动日志一行 warn，玩家侧 `mythic:` 目标永远不涨进度。

> 家具（furniture）不在支持范围内：那是实体而不是方块，需要各自的交互事件与持久化，
> 与「方块/物品目标」不是同一类需求。

#### 4.5.1 字段值域（`ValueKind`）：校验放行什么

**问题**：字段的声明曾经既表示「渲染成什么控件」又表示「列哪一份清单」
（`MATERIAL` / `ENTITY` / `TARGET` / `FISH`）。于是「挖掘方块只能填方块」这件事没有地方可写：
管理员写得出「挖 64 个苹果」，而这类配置**永远不会命中**、
也不会有任何报错——正是本项目最想根除的那类问题。

**做法**：值域是字段上的一份声明（`ConfigField.kinds`），两层各司其职：

| 层 | 位置 | 职责 |
|---|---|---|
| 值域枚举 | `api/schema/ValueKind` | 声明「哪一类值」：`BLOCK` / `PLACEABLE` / `ITEM` / `ENTITY` / `LIVING` / `BREEDABLE` / `TAMEABLE` / `SHEARABLE` / `FISH` / `ENCHANTMENT`。多个之间是**或**；需要「且」的场合另立一个值域（`PLACEABLE` = 是方块且拿得在手里） |
| 运行期判定 | `core/schema/ValueKinds` | 某个候选值属于哪些值域（`Material.isBlock()`、`EntityType.getEntityClass()` 是不是 `Animals`/`Shearable`…），以及一个配置值能不能命中 |
| 声明点 | 各目标类型的 `schema()` | 一行一个字段：`ConfigField.of("target", "目标方块", "方块名，如 DIAMOND_ORE", ValueKind.BLOCK)`；没有值域的自由字段用 `ConfigField.text(...)`（还有 `integer` / `decimal` / `bool`） |

由此校验与界面天然一致：**候选清单与图标怎么推**（`gui/editor/CandidateCatalog` 与
`EditorLookup.icon` 读的同一份 `kinds`）、**服务端标红什么**（`QuestAdminService.valueProblems`
用 `ValueKinds.check`）。
`ObjectiveFieldDomainTest` 把 15 个目标类型的值域逐个钉住——
新增目标类型时忘了声明值域会直接测试失败；**反过来，没有任何字段声明的值域也不允许存在**
（那样 `ValueKinds` 里对应的分支是永远走不到的死代码），`FOOD` 就是这样被删掉的。

**判断不了 ≠ 不满足**：`Material.isItem()` 与 `Enchantment.values()` 要读服务端注册表，
插件引导阶段与单元测试里会抛异常。那时结论是 `UNKNOWN` → **放行**；
把「我判断不了」当成「你写错了」会让校验在半个环境下满屏误报。
因此材质/附魔的值域只有真机能验，单测覆盖的是生物能力、CustomFishing 清单与各种放行规则。

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
| `ExampleDefinitions` | 出厂示例：从 classpath 资源读出 `quests/` + `presets/`，空目录时铺一份（见下面「示例定义」一段） |

**与已删除的 JSON 文件后端的边界**（4.1 末尾那条禁令依然有效，这里不是把它加回来）：

| | 已删除的 JSON 文件后端 | 现在的 YAML 目录 |
|---|---|---|
| 谁能写 | 插件（文件就是权威） | **几乎只有人**：插件只在目录空着时铺一次示例，之后从不写 |
| 权威 | 文件 | **数据库**（同 id 冲突时忽略文件那份并告警） |
| 覆盖范围 | 定义 + 玩家数据 | **只有定义**；玩家数据与进度永远在库里 |
| 跨服 | 做不到 | 仍然做不到——所以 MySQL 多服时明确警告不要这样用 |

合并规则一处定死，所有调用方（游戏内命令与管理 GUI）都走它：

| 情况 | 结果 |
|---|---|
| id 只在文件里 | 生效，`isReadOnly` = true |
| id 只在库里 | 正常读写 |
| 两边都有 | 库里的那条进视图，文件那份被忽略，记一条告警（`warnedConflicts` 去重，`all()` 在启动路径上会被调用多次） |

**只读是行为，不是提示**：`save` / `delete` 在合并仓储里抛
`DefinitionReadOnlyException`，`AdminCommand` 与编辑器（`QuestBrowserMenu` / `QuestEditMenu`）捕获后把
`readOnlyHint` 的原因讲给管理员（该去改哪个文件）。判定写在所有写入路径的共同入口上，
因此绕过命令直接调仓储也无效。

**示例定义**（`core/storage/yaml/ExampleDefinitions`）：目录里一个 YAML 都没有时，把随插件发布的
示例铺过去（`quests/*.yml` + `presets/*.yml`，12 + 11 个，带注释头）。三个要点：

- **示例是资源文件，不是代码**：早期有两套（代码里生成一套写进数据库、同一份内容再生成 YAML 铺到目录，
  靠 `example_file_` / `example_` 两个前缀避免同 id 冲突）。现在只有文件这一套，它同时是
  「开箱有个参照」与「文件格式的活文档」，测试直接读这些文件校验（`ExampleDefinitionsTest`）——
  所见即所得，也不会出现两套示例不一致；
- **只在空目录里铺**：管理员删掉几个示例、或放了自己的定义，重启时不该把它们变回来。
  代价是「整个目录清空后重启会重新铺一份」，这是刻意的（空目录 = 没配过）；
- **文件名即 id，正文不写 `id`**：复制文件改个名就是一个新任务，示例本身就该示范这一点。

读取要认两种环境：开发/单测时资源是磁盘上的目录，生产时在插件 jar 内（jar 里有目录条目，
因此 `getResources("quests")` 找得到；`getJarFile()` 返回的是类加载器共享的那个 jar，不能关，
要自己开一份）。库里**不再**播种示例，因此也没有「库为空才写」这类判空逻辑。

**为什么 YAML 在这里可以破例**：4.2 那条「自己定格式一律 JSON」管的是
**存储列的编码**（机器之间交换、不该有人手写）；`quests/*.yml`
是**给人写的配置界面**，与 `config.yml`、语言文件同类，正是 YAML 的适用场景。

代价是 4.2 里那个坑必须自己填：SnakeYAML 是 YAML 1.1，会把 `target: NO` 读成布尔、
把 `012` 读成八进制 10。`YamlText` 因此自带一套 **1.2-core 语义**：

- `StrictResolver`：布尔只认 `true/false`（`yes/no/on/off` 是字符串）；整数只认十进制、
  `0x`、`0o` 前缀（`012` → 12，不是 8）；浮点必须带小数点或指数；时间戳不解析，
  `2024-01-01` 保持字符串；不处理 `<<` 合并键；
- `CoreSchemaConstructor` 替掉 `Tag.INT` 的构造器，配合上面的 resolver 才真正生效。

这套语义由 `YamlTextTest` 钉住——这是本功能风险最高的地方，「`NO` 仍是字符串」进了测试。
只读目录**只读**，因此解析器只需要读的一半：原先为编辑器导出服务的
`YamlText.write` / `QuotingRepresenter`、`YamlDefinitions.writeQuest` / `readQuest`
（以及它们的往返测试）已随之删除。

**一个文件一条定义**：顶层是列表的整份清单被明确拒绝——宽松接受会让「放进去一个文件却多出十几条任务」
变成静默行为。目录扫描那边因此是「顶层不是映射就告警」：把列表文件放进 `quests/`
不会表现为「什么都没发生」。

---

### 4.7 预设引用：定义里写 `preset:`，载入时展开

预设的作用是「改一次预设、所有引用它的任务一起变」，因此引用**留在定义里**，
而不是在套用时展开成副本：

```yaml
objectives:
  - preset: mine-stone      # 类型与字段全部来自预设
```

四个设计点：

1. **一条配置带两份 map**（`QuestObjective` / `QuestReward` 各加了第三个分量 `authored`）：
   `properties` 是<b>生效值</b>（引用时就是预设给的值，引擎与界面都用它），`authored` 是
   <b>作者写的那份</b>（引用时就是 `{preset: id}`，落库与写文件按它写回）。只留生效值 → 保存一次
   就把预设的值变成本任务的显式配置，预设之后再也影响不到它；只留作者那份 → 引擎还得自己去查预设。
2. **展开只有一处**：`PresetRefs.resolve` 在 `QuestAdminService` 的 `reload()` 与 `save()` 里各调一次，
   因此「什么最终生效」只有一个结论。`save()` 前先 `trim()` 把引用清成只有 `preset` 键
   （手改的库记录可能多带字段），再 `resolve()` 进注册表。
   存储层只搬运 `authored`（DB 的 `properties` 列、`quests/*.yml` 都是它），不认识预设。
3. **引用不带覆盖项**：字段全由预设提供，要偏离就显式写出 `type` 与全部字段（解除引用）。
   曾经允许 `preset` 与 `properties` 并存（覆盖项赢过预设），代价是同一个
   字段有两个来源：「这个任务实际在做什么」要心算一遍预设 ⊕ 覆盖，也说不清哪些字段被改过。
   现在引用条目上多写的字段会被校验报出来、并在保存时被清掉——
   报错而不是静默忽略，因为这类写法在早期文档里出现过，会真实存在于 `quests/*.yml` 中。
4. **悬空引用不静默**：预设被删/改名后，展开时类型留空、生效值为空，并报一条校验问题
   （`引用的预设 x 不存在…`）；同一情形下不再额外报「未知目标类型 」（空名字）——
   两条问题讲同一件事时，管理员只该看到说得清楚的那条。校验信息进 `/ptxa list`
   与启动日志，和软依赖的校验同一处出口。

改动预设后如何生效：预设存在库里，`/ptxa reload` 会重新展开所有任务
（顺带重建在线玩家的进度索引，因为展开可能改变目标类型、进而改变结构指纹）；
文件里的预设同样走 `/ptxa reload`。
预设改动是低频操作，全量重载比维护「谁引用了它」的反向索引更简单，也不会漏。

文档映射（`QuestJson`）因此给引用节点 `preset`、`type` 与 `resolved`（生效值），
**不给** `properties`——引用条目没有「任务自己写的字段」。
`quests/*.yml` 同样只写 `preset:`（类型与字段写出来就是一份会过期的副本）。

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
  （金币 / 点券，取第一个可用的），**只重抽该玩家这一种周期**，
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

### 7.1 玩家 GUI（`core/gui/` 框架 + `core/gui/menu/` 具体菜单）

通用菜单框架（`Menu` / `MenuItem`，用 `InventoryHolder` 区分归属），
在此之上实现：周期任务列表（底部一排标签切换四种周期，只显示已启用的）、
任务详情（多目标进度 + 多奖励预览）、点击领取奖励。
刷新按钮永远只刷新「当前正在看的那种周期」——四种周期的费用与上限各不相同，
混在一起时「这个按钮扣哪份钱」根本说不清。
（任务**分类**目前只作为任务的一个字段用于筛选与展示，没有按分类分页浏览的界面。）

**槽位按布局表写**（`SlotLayout`）：菜单在 `build()` 开头用 `layout("文本图")` 声明界面，
之后 `set("名字", 物品)` —— 代码里那张图就是界面本身，不必心算「47 是第几行第几格」。
两条边界是刻意的：

- **翻页列表区不进布局表**：45 个格子写成文本图没有可读性，那里继续用数字下标；
  布局表只管头部、底部按钮这些**锚点**。
- **错误必须当场炸**：重名（两个功能抢同一个槽位，原先靠常量看运气）、一行超过 9 格、
  超出容器行数、名字没声明过，全部抛异常并指出是哪一格/有哪些可选名字。
  框架里 `set(int, ...)` 对越界是**静默忽略**（配置里的脏数据不该打断界面），
  但「名字写错」是代码 bug，两种错误刻意区别对待。

### 7.2 游戏内任务编辑器

`/ptxa menu` 打开编辑器（`core/gui/editor`），一层界面管一件事：

- **任务列表**：分页浏览 + 校验问题直读、启用/禁用、`Shift+右键` 连按两次删除、新建任务，
  底部还能切换排序（id / 名称 / 类型 / 启用，见 `QuestRegistry#all(Comparator)`）。
  文件来源（`quests/*.yml`）的任务左键进只读预览而不是编辑器——文件定义本来写不进去，不如不给点。
- **任务面板**：id、名称、描述、图标、分类、类型、刷新费用、启用逐项改，下面是目标/奖励入口与保存。
- **目标 / 奖励列表**：新增（先选类型再填字段）、改字段、删除、`Shift+左右键` 调整顺序（顺序就是发放与展示顺序）。
- **字段编辑**：一行一个 `ConfigField`，按它声明的 `ConfigField.Shape` 决定控件——候选字段进分页候选清单，
  其余在聊天栏打字；字段值域当场判定「配了也永远不会命中」并标红，不必等保存后看日志。

三处刻意的取舍：

- **文本输入走聊天栏**（`EditorInput` + `ChatInputListener`：一次一问、非法值重新提示、90 秒超时、`取消` 放弃、退服清理）。
  箱子界面本身没有文本输入，铁砧/告示牌界面要么依赖 NMS 要么在 Folia 上不成立；输入期间该玩家的聊天被拦下（没人会在配任务时同时公屏发言）。
- **候选清单只覆盖原版**（材质 / 实体 / 附魔 / CustomFishing 战利品，见 `CandidateCatalog`）：
  ItemsAdder / CraftEngine / MythicMobs 的 id 靠聊天栏手打，写法在字段说明里。要列它们，得把
  `CustomContentHook#itemIds/blockIds`、`MythicMobsHook#mobIds` 那几段反射清单装回来。
- **保存只挡两种「存下去也用不了」的情况**：id 为空、没有目标（`Quest.isUsable`）；其余问题（值域不命中、
  类型不可用、预设悬空）实时显示在面板上但照存——管理员可能正想先存一半。写只读定义由仓储抛
  `DefinitionReadOnlyException`，界面把原因原样说出来。

**预设引用**在字段编辑界面里可选、可换、可「展开为独立配置」；引用着预设的节点不给写字段
（写了也不生效，`PresetRefs` 会报出来，因此界面直接不给入口）。

> **曾经的 7.3「网页编辑器」已整体删除**：Vue 3 + Vite 前端（`task-editor-vue/`）、
> Javalin 提供的 `/api/*` 与静态资源托管、`EditorServices` / `PluginEditorServices` 窄接口与适配器、
> `MaterialCatalog` 素材目录、`LangFileStore` + `HttpText` 译名下载，以及 `editor.*` 配置项
> 与 `/ptxa editor` 子命令。删得干净是当初刻意留的余地：领域包从不依赖 `web`，
> 映射层（`QuestJson` / `PresetJson`）一开始就放在 `core/storage/`（如今在 `core/storage/codec/`）而不是 `web/`，
> 因此这次只删了一个包、一个前端目录与它们的装配点。
>
> 一并消失、**至今仍没有替代品**的能力：浏览器里的玩家进度查询页，
> 以及**整份定义的 YAML 导入导出**（把定义搬进/搬出文件只能手工复制，格式见 4.6）。
> 编辑器能力本身由 7.2 的游戏内版本接续（候选清单覆盖原版；别家插件的 id 手打）。

**预设不是引擎的执行期概念**：引擎不认识它，`PresetRefs` 在 `QuestAdminService` 的
reload / save 阶段就把引用展开成了任务自己的配置（见 4.7）。
预设与任务定义同库（`preset` 表），不存在单独一份预设文件需要备份或同步。

示例预设与示例任务同一来源：`presets/` 目录空着时从插件自带的资源铺一份（见 4.6），
它们是**只读**的；`preset` 表里不再播种任何默认预设。

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
| 5 | 奖励类型 + 发放（金币/点券/经验/物品/命令） | ✅ 完成（阶段 37 收敛为金币/点券/命令） |
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
| 25 | 目录空着时铺一份示例文件（`example_file_*`，与库里那套并存）；播种口径改为只看数据库 | ✅ 完成（5 项测试；阶段 39 改为从 jar 内资源铺，库里不再播种） |
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
| 37 | 奖励类型收敛为**金币 / 点券 / 命令**三种：删掉 `exp` 与 `item`（发物品/经验交给命令） | ✅ 完成（见 3.2；示例任务与预设改用 `give` / `xp` 命令） |
| 38 | 刷新费用的货币收敛为**金币 / 点券**：删掉 `EXP`（`ExpCurrency` / `ExpUtil` / `ExpUtilTest`）、`CurrencyType.select` 可返回 null，两种货币都没有时刷新明确提示不可用 | ✅ 完成（见 6，-9 项 ExpUtil 测试 + 2 项货币选择测试改写） |
| 39 | 出厂示例改为**只从 jar 内资源铺**：删掉库里的示例播种与 `core/seed` 包（`ExampleQuests` / `ExamplePresets` / `ExampleFiles`），新增 `ExampleDefinitions`，示例 id 去掉 `_file_` 前缀 | ✅ 完成（见 4.6，4 项 `ExampleDefinitionsTest` 直接校验资源文件；jar 内读取路径已实测） |
| 40 | **整体删除内置网页编辑器**（`core/web` 包、`task-editor-vue/` 前端、`editor.*` 配置、`/ptxa editor`、Javalin 依赖与前端构建任务），并清掉只为它存在的死代码与只读 YAML 的导出/导入路径 | ✅ 完成（见 7.2 的说明；同时删掉 `MaterialCatalog`/`LangFileStore`/`HttpText` 与 `/api/*` 的全部测试） |
| 41 | **清掉「为编辑器而生」的 schema 表层**：删 `FieldType`（控件种类）与 `ConfigField` 的 `type` / `required` / `defaultValue` / `options`（编辑器渲染表单用的，删除后无读取方），字段只剩「键 + 显示名 + 说明 + 值域」；连带删掉 `ValueKind.FOOD`（无字段声明）、`Preset.name` / `description`（无预设界面）、`QuestJson` 的写出一侧与 `ObjectiveFieldTypeConsistencyTest`→`ObjectiveFieldDomainTest` | ✅ 完成（见 §1 / 4.5.1；`defaultAmount`、无字段声明的值域、无调用方的 `count()` / `deletePeriodState` 一并删除） |
| 42 | **按「一个包 = 一个角色」重排包**：`integration` 按插件拆成根包（探测 / 反射）+ `customcontent` / `mythicmobs` / `customfishing`；`storage` 拆出 `codec`；`reward` 只留三个 `RewardType` 实现（`RewardService`→`engine`、`CurrencyType`→`period`）；`gui` 拆出 `gui/menu`；`PresetRefs`→`preset`；`Hash`→`engine` 并改名 `StructureFingerprint` | ✅ 完成（见 §1 的包约定；顺带把 `Reflect` 提为 `public`，测试与它测的类同包） |
| 43 | 两处收尾：`core/progress`→`core/display`（原名与 `engine/ProgressService` 互相错位）；`BuiltIns` 拆成 `ObjectiveBuiltIns` / `RewardBuiltIns` 各自回域包（`registry` 因此只剩三张注册表的实现） | ✅ 完成（见 §1 的包约定；`reward` 不再 import 目标类） |
| 44 | **游戏内任务编辑器**：新包 `core/gui/editor`（任务列表 / 面板 / 草稿 / 目标奖励列表 / 字段编辑 / 候选选择器 / 聊天栏输入）；`ConfigField` 补回控件形状 `Shape`（编辑器据此决定弹清单还是聊天输入）；`ValueKind` 值域仍只做校验，图标与候选清单由编辑器推 | ✅ 完成（见 7.2；新增 `QuestDraftTest` / `FieldValueTest` / `CandidateCatalogTest`，删掉被取代的 `AdminQuestMenu`） |
| 45 | **槽位按布局表写**：`core/gui/SlotLayout` + `Menu#layout/slot/set(String, …)`，7 个菜单的 `XXX_SLOT` 常量换成文本图里的名字；重名/越界/未知名字当场抛 | ✅ 完成（见 7.1；新增 `SlotLayoutTest` 6 项，列表区仍用数字下标） |
| 46 | **任务顺序改由注册表担保**：`QuestRegistry#all()` 返回按 id 升序的 `List`（实现换成 `TreeMap`），翻页界面与 `/ptxa list` 不再各自排序；另加 `all(Comparator)` 让调用方自己定顺序，编辑器列表据此有了排序开关（id / 名称 / 类型 / 启用） | ✅ 完成（新增 `QuestRegistryImplTest` 6 项；排序稳定，同分保持 id 序） |

> 阶段 8 / 9 / 14 / 15 / 16 / 20 / 23 / 26 / 27 / 28 / 33 / 35 / 36 做的都是**已被删除的网页编辑器**
> （阶段 40），本表保留它们作为历史记录——其中的 `/api/*`、`EditorServices`、
> 前端构建自检与「编辑器目录」测试都不再存在，读到时请以第 7 节与 4.6 的现状为准。

**测试总量：240 项全部通过**（32 个测试类，全部 failures=0 / errors=0）：
存储 16（`StorageIntegrationTest`）+ 结构指纹格式 4（`StructureFingerprintFormatTest`）+
YAML 定义来源 14（`YamlDefinitionSourceTest`）+ YAML 类型语义 5（`YamlTextTest`）+
合并仓储 5（`MergedDefinitionRepositoryTest`）+ 预设引用 9（`PresetRefsTest`）+
示例定义 4（`ExampleDefinitionsTest`）+ 周期算法 12（`PeriodsTest`）+
引擎 12（`ProgressServiceTest`）+ 命令帮助 12（`YLibCommandHelpTest`）+
任务管理 12（`QuestAdminServiceTest`）+ 注册表顺序 6（`QuestRegistryImplTest`）+ 值域 8（`ValueKindsTest`）+
奖励 10（`CurrencyTypeTest` 6 + `MoneyRewardTest` 4）+
自定义钓鱼 9（`CustomFishObjectiveTest`）+ 自定义内容接入 7（`CustomContentHooksTest`）+
结构指纹 8（`StructureFingerprintTest`）+ 字段值域 11（`ObjectiveFieldDomainTest`）+
奖励领取 4（`RewardServiceTest`）+ 监听器 6（`ItemListenerCraftAmountTest`）+
编辑器 25（`FieldValueTest` 13 + `QuestDraftTest` 9 + `CandidateCatalogTest` 3）+
界面框架 6（`SlotLayoutTest`）+ GUI 图标 6（`QuestDetailMenuTest`）+ 别名匹配 6（`TargetMatchAliasTest`）+
进度渲染 5（`ProgressDisplayRenderTest`）+
CustomFishing 监听 5（`CustomFishingListenerTest`）+ MythicMobs 目标 5（`MythicMobsHookTest`）+
击杀监听 5（`EntityListenerTest`）+ 语言文件 3（`LanguageFileTest`）。
统计口径：`.\gradlew.bat :core:test --rerun` 之后读 `core/build/test-results/test/*.xml`
逐套件累加（32 个 XML），不是靠日志里的汇总行。

**代码规模**（含空行，按文件行数累加）：后端主代码 `api/src/main` 788 行 + `core/src/main` 11175 行
＝ **11963 行 / 116 个 java 文件**；测试 `core/src/test` **4942 行 / 34 个文件**
（32 个测试类 + 2 个测试替身；`api/src/test` 为空，api 只放模型与接口，行为测试都在 core）。
删掉网页编辑器后，`core/web/`（7 个类 / 1933 行）与 `task-editor-vue/src`（31 个文件 / 8299 行）
及其全部测试都不在统计里。

文本渲染的测试**不在本插件**，而在 YLib 侧（`YLib/core/src/test`，15 项 =
`TextRendererTest` 11 + `RealWorldMessageTest` 4）：
渲染能力既然上移到了 YLib，它的行为就该在 YLib 钉住，否则每个消费方只能各测各的。

### 真机验证结论（Folia 26.1.2-8）

启动日志实证：

```
[playerTaskX] 存储已就绪: SQLite: data/playerTaskX.db
[playerTaskX] quests/ 是空的，已铺入 12 个示例任务文件（只读来源，可自由删改）
[playerTaskX] presets/ 是空的，已铺入 11 个示例预设文件（只读来源，可自由删改）
[playerTaskX] Registered command: playertaskx
[playerTaskX] Registered command: playertaskxadmin
[playerTaskX] PlayerTaskX 已启用（12 个任务，15 种目标，3 种奖励）
```

**清空数据库后重新初始化**：建表清单为 6 张（quest / quest_objective / quest_reward /
player_quest / period_state / preset）；
早期一次真机验证里 `PRAGMA integrity_check` 为 ok。
（`meta` 表已随一次性迁移代码删除，见文末「删除一次性迁移代码」。）

**插件联动的真机冒烟（同一台测试服，未安装 MythicMobs / CustomFishing）**：
启动日志为「已启用（12 个任务，**15 种目标**，5 种奖励）」（当时奖励还是 5 种；现已收敛为 3 种），没有任何异常，
也没有出现接入失败日志——即两个软依赖缺失时行为与从前完全一致；
用当时的接口存一条同时含 `target: mythic:Boss` 与 `custom_fish` 目标的任务，
`problems` 恰好给出这两条：

```
目标类型 custom_fish 不可用（未安装 CustomFishing）
目标 mythic:Boss 需要 MythicMobs 5.x（当前未安装）
```

（`QuestAdminService.validate` 到这两条问题文案的链路至今未变，只是输出口从 HTTP
换成了 `/ptxa list`、`/ptxa info` 与管理界面。装上这两个插件的正向链路只有单测覆盖，见文末已知限制。）

**只读 YAML 定义来源的真机冒烟（同一台测试服）**：在 `run/plugins/playerTaskX/quests/` 放两个文件——
一个文件名即 id 的 `smoke_yaml_probe.yml`、一个与库里示例任务同 id 的 `example_daily_mine.yml`，
重启后：

```
[playerTaskX] YAML 定义: 任务 example_daily_mine 同时定义在数据库与 quests/example_daily_mine.yml，
              已忽略文件里的那份（库优先）
```

告警恰好一条（`findAll` 在启动路径上被调用多次，`warnedConflicts` 去重生效）。
当时接口侧逐条确认过于只读 id 的写入被 **409** 拒绝、消息点名了要改哪个文件
（`MergedSources.readOnlyHint` 至今仍是那句消息，命令与管理界面直接把它讲给管理员）。
用**含 `target: NO` 与 `target: yes` 的目标**验证过类型语义：读回后两个值都是**字符串**
`"NO"` / `"yes"`——这正是 4.6 那套 1.2-core resolver 要解决的核心风险（修好前实测会变成布尔 `false`）。
探针文件随后已清理。

**铺示例文件的真机冒烟（同一台测试服，先清空 `quests/` 与 `presets/`）**：启动时两个目录
各被铺了一份，日志如实报数（那一轮示例还是「代码生成 + 写库」两套，因此任务数是 24；
现在只有文件这一套，清空目录后是 12 个）。再验一次「不重复补」：删掉
`quests/example_daily_torch.yml` 后重启，日志里没有「已铺入」、任务数减 1、
该文件没有被重新创建；把它放回去再 `/ptxa reload`，任务数回到原值——
即「只铺一次、之后尊重目录现状」与「reload 会重读文件」都成立。

**预设引用的真机冒烟（同一台测试服）**：建一个库里的预设、让另一个任务引用它，
再改这个预设（DIRT/8 → SAND/99），保存之后直接读任务，生效值已变成 SAND/99 ——
引用没有被写死，也不需要手动 reload。删掉被引用的预设：任务的目标类型变空，问题清单给出
「引用的预设 xxx 不存在（已删除或 id 写错），这个目标当前不生效」。探针任务与预设随后已清理。

> 引用契约的改动（覆盖项机制被移除，见 4.7 第 3 点）之后**没有**重跑真机冒烟，
> 改由 `PresetRefsTest` 与真库往返测试钉住。

同时验证了两条重要的健壮性行为：

- **软依赖缺失时优雅降级**：未安装 Vault/PlayerPoints 时，对应奖励类型标记为不可用并写入
  启动警告，示例任务被校验标红，但插件照常启用、命令与 GUI 均正常——不会因软依赖缺失而崩。
- **中文编码**：日志文件与生成的语言文件均为正确 UTF-8（控制台乱码只是 Windows 终端显示问题，
  插件内部与落盘内容无误）。

### 尚未验证的部分（如实记录）

- **MySQL 路径未做真机验证**：SQL 由 `Dialect` 统一生成、与 SQLite 共用同一套仓储代码，
  但 `ON DUPLICATE KEY UPDATE` 分支、HikariCP 连接与 `CREATE INDEX` 的容错路径
  只在代码与 SQLite 测试层面覆盖，没有连过真实 MySQL 实例。
- **ItemsAdder / CraftEngine 的接入只在单测层面验证过**：本机测试服没有装这两个插件，
  验证到的是「插件照常启用、写了 `itemsadder:` 的目标被校验报出来」；
  反射绑定的方法名（`CustomStack.byItemStack`、`CraftEngineBlocks.getCustomBlockState` 等）
  是照它们的公开 API 与源码写的，但**没有在真机上跑过**——装上去之后若签名对不上，
  日志里会给一条「接入 … 失败」的 warn，对应目标不可用而不会影响其它功能。
- **周期任务的发放/刷新只有单测覆盖**：四种周期的「换一批」判定由 `PeriodsTest` 钉住
  （重置小时、跨周、跨月、跨年、自定义分桶），存储往返由 `StorageIntegrationTest` 在真实
  SQLite 上跑；但本机没有可登录的客户端，因此「登录时发一批、跨周期换一批、扣费刷新」
  这条链路只验证到配置与命令层（启动无异常、`/ptxa resetperiod` 可跑）。
- **玩家实际游玩路径未验证**：需要真人进服（挖掘/合成/击杀等）才能确认进度累加、
  actionbar 推送、GUI 点击等表现层行为；本次只验证到「插件启用 + 命令注册」。
- **YAML 定义文件的边界情况只由单测覆盖**：递归子目录、`presets/rewards/` 目录兜底 `kind`、
  坏文件跳过、只读 id 被批量操作跳过、写坏的目录（只读权限）等分支都有测试，
  真机只走了主路径（铺示例、`example_*` 只读、缺文件不补、reload 重读）。
- **未安装 Vault / PlayerPoints 的服务器**：刷新会明确提示不可用（单元测试覆盖了
  「两种货币都不可用时 `select` 返回 null」与失败文案），但没有在缺少经济插件的真机上跑过全流程。
- **`NORMAL` 任务目前没有发放入口**：玩家拿到的任务只有周期任务一条来源
  （`PeriodicService` 直接写 `player_quest`，`ProgressService.assign` 在生产代码里无人调用）。
  普通任务因此只存在于定义里，缺的是「接取常驻任务」这一步。
- **MythicMobs / CustomFishing 只做了「未安装」这一支的真机验证**：本机测试服没有这两个插件，
  验证到的是「插件照常启用、目标类型标为不可用、`mythic:` 目标被校验拦下」；
  装上插件后的实际击杀/钓获计数只有替身事件与反射入口的单测覆盖，
  真实插件版本（MythicMobs 5.x 的具体小版本、CustomFishing 2.3.x）未在真机上跑过。




### 已落地的关键实现细节

- **一件事只有一个出处**：凡是「多个入口都要做同一件事」的地方都收敛到一处，
  避免同一个功能两种口径。已收敛的几处：
  - 任务校验（`/ptxa list` / 管理 GUI）→ `QuestAdminService.validate`；
  - 刷新结果的提示措辞（玩家命令 / 管理员命令 / GUI 按钮）→ `PeriodicService.RefreshResult`
    自带回复消息，调用方只调 `report(...)`，不再各自拼 success/cost/limit/error；
  - 启用状态切换（落库 + 同步注册表 + 重建索引）→ `QuestAdminService.setEnabled`；
  - 文本装配（渲染 / 数字去小数尾巴 / 配置表摊平 / 类型显示名）→ `core/text/Texts`；
  - 命令帮助清单 → `CommandHelp.ofAnnotations` 从注解生成，不再手写第二份；
  - 定义来源的合并与只读判定（库优先 / 冲突告警去重 / 只读写入抛异常）→
    `MergedSources` + 两个 `Merged*Repository`：游戏内命令与管理 GUI
    拿到的是同一个结论，不需要各自再判断一次「这个 id 能不能改」（见 4.6）。
- **目标类型**：`core/objective/` 只有四个类——数据形态的 `TargetObjective` 与三个自带判定
  逻辑的 `InteractObjective` / `ChatObjective` / `CustomFishObjective`；15 种内置类型的清单在 `ObjectiveBuiltIns` 里显式列出
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
  （删掉编辑器后连 Javalin 与它带来的 slf4j 也不再需要）。
- **YAML 定义只有一份字段定义**：`QuestJson` / `PresetJson` 就是「文档 → 模型」的唯一形状来源，
  因此 `quests/x.yml` 与数据库的 `properties` 列共用同一份字段名，新增目标类型时这里一行都不用改。
  `YamlDefinitions` 只负责把解析出来的文档交给它们。两者都只有**读**这一个方向——
  插件不生成文档（原先服务编辑器导出与新建的那一半已删除）。
- **出厂示例只有一套，且是资源文件**：`example_*` 既是「开箱有个参照」也是「文件格式的活文档」，
  随插件发布在 `core/src/main/resources/{quests,presets}/` 下，目录空着时由 `ExampleDefinitions`
  复制一份过去（只此一次，绝不覆盖）。`ExampleDefinitionsTest` 直接读这些资源校验（数量、类型已注册、
  材质/实体真实、奖励参数有效），因此改示例 = 改文件，不需要再同步一份 Java 代码——
  早期那套「代码生成一份写库 + 再生成一份铺文件」正是因为要维持两套一致才变得复杂，
  两个 id 前缀也只是为了不撞在一起。这几条属于「不报错但会让人困惑很久」的类型
  （示例没进包→新服一个任务都没有、反复补文件→删了又回来）。
- **包归位**：注册表的三个实现同处 `core/registry`（`api.registry` 也是这么分组的）；
  其余按「一个包 = 一个角色」摆：`objective` / `reward` 只放对应扩展点的实现类与内置清单，
  `engine` 放两个引擎（进度、奖励）与结构指纹，`quest` 只有定义维护入口、`preset` 只有预设引用展开，
  具体菜单在 `gui/menu`，两家的接入实现各自成包（详见第 1 节的包约定）。
- **两个数据库类合成一个**：`JdbcDatabase` 同时是「执行 SQL 的引擎」与「打开 SQLite 文件 /
  MySQL 连接池」的工厂。原先 `SqliteDatabase` / `MysqlDatabase` / `JdbcDatabase` 三个类
  靠一个 `ConnectionProvider` 接口串起来，而通用执行逻辑只有一个实现——策略差异其实只有
  「借出/归还」与「怎么关」三件事。
- **删除一次性迁移代码**：`DefinitionMigrator`（130 行）把「旧版本存在数据库里的任务定义」
  导出到 JSON。项目未发布，不存在这样的部署；连同 `Schema` 里的 `meta` 表、
  容错 `ALTER`、以及从未被写入过的 `quest.sort_order` 列一并删除。

