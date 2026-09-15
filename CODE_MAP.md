# 代码地图

这份文件回答**「哪个类在哪、它负责什么」**，用来在改代码前先找对地方。
设计与取舍（为什么这么拆）见 [`ARCHITECTURE.md`](ARCHITECTURE.md)；面向使用者的文档在 `docs/`。

约定：**一个类只写一句话**，写「它负责什么」，不写它怎么实现。

**包名要能当索引**：`core/<域>` 是该域的东西，**契约与实现分开放**——
契约在 `api/`，实现在 `core/` 的对应子包里；一个契约有多家实现时，实现单独成包
（`storage/jdbc`、`storage/yaml`、`integration/customcontent`、`integration/mythicmobs`、`integration/customfishing`）。
因此看到包名就知道里面是「契约 / 某一家的实现 / 某一层的服务」，不必先点开文件。

**界面按布局表写**：菜单在 `build()` 开头用 `layout(...)` 声明一张文本图（见 `SlotLayout`），
之后 `set("名字", 物品)` 按名字摆位——槽位就是那张图里的格子，不用心算数字；
翻页列表那种整片区域仍用数字下标（45 个格子写成文本图没有可读性）。

---

## 规模（本次统计：116 个主代码类 / 11954 行）

| 模块 | 类 / 行 | 说明 |
|---|---|---|
| `api` | 18 / 773 | 模型与扩展点契约（给扩展作者看的公共 API） |
| `core` | 98 / 11181 | 全部实现 |
| 测试 | 33 / 4829 | `core/src/test`（其中 31 个测试类 + 2 个测试替身，234 项测试） |

行数是「含空行按文件行数累加」，会随提交变动；重新统计见文末。

---

## 包总览

| 包 | 类 / 行 | 一句话职责 |
|---|---|---|
| （根） | 1 / 359 | 插件入口与装配根 |
| `api/model` | 8 / 390 | 数据模型（任务、目标、奖励、预设、玩家记录） |
| `api/objective` | 3 / 135 | 目标扩展点与动作契约 |
| `api/registry` | 3 / 92 | 三张注册表的接口 |
| `api/reward` | 1 / 22 | 奖励扩展点 |
| `api/schema` | 3 / 134 | 「类型自描述」：字段声明（含控件形状）与值域词汇表 |
| `core/command` | 2 / 646 | 玩家 / 管理员命令 |
| `core/config` | 2 / 360 | `config.yml` 与周期配置 |
| `core/display` | 1 / 174 | 进度展示（actionbar / title） |
| `core/engine` | 4 / 478 | 进度引擎、奖励发放与结构指纹 |
| `core/gui` | 4 / 437 | 箱子菜单框架（含布局表 `SlotLayout`：按文本图摆槽位） |
| `core/gui/editor` | 13 / 1939 | **游戏内任务编辑器**（列表 / 面板 / 草稿 / 字段编辑 / 聊天输入 / 候选清单） |
| `core/gui/menu` | 2 / 456 | 两个玩家侧菜单（任务详情 / 周期） |
| `core/integration` | 2 / 83 | 软依赖接入的公共设施（探测插件、反射小工具） |
| `core/integration/customcontent` | 4 / 461 | 自定义内容契约 + 汇总 + 两家实现（ItemsAdder / CraftEngine） |
| `core/integration/customfishing` | 4 / 233 | CustomFishing 接入（Hook / 监听器 / 清单 / 战利品） |
| `core/integration/mythicmobs` | 2 / 220 | MythicMobs 契约 + 5.x 实现 |
| `core/listener` | 6 / 466 | Bukkit 事件 → `ProgressContext` |
| `core/objective` | 5 / 327 | 目标类型实现 + 内置清单 |
| `core/period` | 3 / 560 | 周期任务的抽取、过期与刷新；刷新费用的货币 |
| `core/placeholder` | 2 / 223 | PlaceholderAPI 变量 |
| `core/preset` | 1 / 154 | 预设引用的展开、校验与瘦身 |
| `core/quest` | 1 / 204 | 任务定义维护入口 |
| `core/registry` | 3 / 141 | 三张注册表的实现 |
| `core/reward` | 4 / 339 | 奖励类型实现 + 内置清单 |
| `core/schema` | 1 / 225 | 值域的运行期判定 |
| `core/storage` | 10 / 396 | 存储契约、方言与装配入口 |
| `core/storage/codec` | 3 / 359 | JSON 编解码与「文档 → 模型」映射 |
| `core/storage/jdbc` | 7 / 1018 | JDBC 实现（SQLite / MySQL 共用） |
| `core/storage/yaml` | 9 / 784 | 只读 YAML 定义目录与出厂示例 |
| `core/text` | 2 / 139 | 文本装配与推送 |

---

## 逐包逐类

### （根）（1）

- `PlayerTaskX` (359) — 插件入口：只做装配与启停，业务逻辑都在下面各子系统

### api/model（8）

- `Quest` (47) — 任务定义（静态数据，由命令、编辑器或只读 YAML 文件维护）
- `QuestObjective` (47) — 任务目标：一份配置数据；引用预设时带 `properties`（生效值）与 `authored`（作者那份）
- `QuestReward` (47) — 任务奖励：同上，发放行为由 `RewardType` 提供
- `ConfigMap` (54) — 目标与奖励共用的配置表读取（不可变拷贝、预设键、宽松取字符串 / 整数）
- `PlayerQuest` (140) — 玩家进行中的任务（运行期状态）：进度按「目标下标 → 计数」存，附结构摘要
- `Preset` (33) — 目标 / 奖励预设：一组「类型 + 属性」，只有 `kind` / `id` / `type` / `properties`
- `QuestType` (22) — 任务类型（NORMAL / DAILY / WEEKLY / MONTHLY / CUSTOM）
- `QuestStatus` (13) — 玩家任务状态（进行中 / 待领取 / 已领取 / 已放弃）

### api/objective（3）

- `ObjectiveType` (50) — **目标扩展点**：新增一种目标只实现它，GUI 与校验都读它的 `schema()`
- `ProgressContext` (51) — 一次游戏内动作的通用表示：引擎与监听器之间唯一的契约
- `Trigger` (38) — 动作类型（破坏 / 放置 / 合成 / 击杀 …），热路径快速判别用

### api/registry（3）

- `ObjectiveRegistry` (28) — 目标类型注册表（注册、按 id 查、列全部、取显示名）
- `RewardRegistry` (25) — 奖励类型注册表（同上）
- `QuestRegistry` (42) — 任务定义注册表（内存视图：按 id、按类型、按启用状态查询）

### api/reward（1）

- `RewardType` (22) — **奖励扩展点**：字段由 `schema()` 声明、发放由 `grant()` 完成

### api/schema（3）

- `ConfigField` (71) — 单个字段描述（键 / 显示名 / 说明 / **控件形状** / **值域**）；`text`/`integer`/`decimal`/`bool` 无值域，`of(...)` 有值域（形状与值域必须一致，构造器直接挡）
- `ConfigurableType` (16) — 「由配置驱动」的类型共有形状（`id` + 显示名 + 字段表）
- `ValueKind` (49) — 值域词汇表（方块 / 可放置 / 物品 / 实体 / 活体 / 可繁殖 / 可驯服 / 可剪毛 / 鱼 / 附魔）

### core/command（2）

- `PlayerCommand` (309) — 玩家命令 `/ptx`（看任务、领取、刷新周期任务）
- `AdminCommand` (352) — 管理员命令 `/ptxa`（列表 / 详情 / 启停 / 发放 / 重置周期 / 重载 / 菜单）

### core/config（2）

- `PluginConfig` (226) — 插件主配置（`config.yml` 的全部字段与默认值）
- `PeriodSettings` (141) — 一种周期任务的配置（启用 / 数量 / 重置时刻 / 费用 / 上限 / 池子）

### core/engine（4）

- `ProgressService` (297) — **进度引擎**：唯一修改玩家进度的入口（匹配、累加、完成、待领取）
- `RewardService` (150) — **奖励发放**：领取校验、逐个发放、状态落库（单个奖励失败不影响其它）
- `StructureFingerprint` (45) — 目标结构指纹：目标列表算成短摘要存进玩家记录，顺序变化靠它识别
- `ApplyResult` (10) — 一次动作处理的结果（改了什么），表现层据此提示

### core/gui（4）

- `Menu` (179, abstract) — 箱子菜单框架：建容器、登记槽位、刷新、标记归属；`layout(...)` + `set("名字", 物品)` 按布局表摆位
- `SlotLayout` (72) — 界面布局表：文本图里的格子 → 槽位下标；重名 / 越界 / 未知名字当场抛
- `MenuItem` (97) — 菜单项：图标 + 点击动作
- `MenuListener` (89) — 把「容器点击」翻译成菜单项的 action

### core/gui/menu（2）

- `QuestDetailMenu` (254) — 任务详情：多目标进度 + 多奖励预览（也用于编辑器的只读预览）
- `PeriodicQuestMenu` (189) — 周期任务界面（四种周期切换、刷新按钮、剩余次数）

### core/gui/editor（13）——游戏内任务编辑器

- `QuestBrowserMenu` — 入口：任务分页列表、开关、删除（连按两次 Shift+右键）、新建、重载
- `QuestEditMenu` — 任务面板：基本信息逐项改 + 目标/奖励入口 + 实时校验 + 保存
- `QuestDraft` — 编辑中的任务草稿（可变，`toQuest()` 才交给 `QuestAdminService` 保存）
- `NodeListMenu` — 一个任务的目标（或奖励）列表：增删改 + Shift 上下移动
- `TypePickMenu` — 新增时选类型：字段摘要、可用性、类型图标
- `NodeEditMenu` — 一个目标/奖励的字段编辑：按 `ConfigField.Shape` 决定控件，预设引用可展开/更换
- `CandidateMenu` — 分页候选选择器（字段值、预设都用它）
- `CandidateCatalog` — 候选值清单：按值域从原版枚举与 CustomFishing 战利品里列
- `EditorInput` — 聊天栏一问一答（超时 / 取消 / 退服清理，回调切主线程）
- `ChatInputListener` — 拦下等待输入玩家的聊天并转交 `EditorInput`
- `FieldValue` — 聊天输入 ↔ 字段值的解析与回显（纯逻辑，可测）
- `FieldLore` — 字段在界面里的描述口径（形状 / 值域 / 命中判定）
- `EditorLookup` — 编辑器问注册表的那几件事（找类型、显示名、图标、按类别取预设）

### core/integration（2）

- `SoftDependency` (51) — 软依赖探测（插件是否加载、版本多少）
- `Reflect` (34) — 接入软依赖用的反射小工具（按名字取类取方法，取不到一律 null）

### core/integration/customcontent（4）

- `CustomContentHook` (28) — 一个自定义内容来源的契约（ItemsAdder / CraftEngine）
- `ItemsAdderHook` (106) — ItemsAdder 实现（纯反射，不依赖其构件）
- `CraftEngineHook` (133) — CraftEngine 实现（纯反射）
- `CustomContentHooks` (197) — 全部已接入来源的汇总，以及围绕它们的查询与校验

### core/integration/mythicmobs（2）

- `MythicMobsHook` (116) — MythicMobs 接入点的契约（保留接口是为了能塞假身测试）
- `MythicMobs5Hook` (104) — MythicMobs 5.x 的实现（懒解析 `MobManager`，处理 POSTWORLD 时序）

### core/integration/customfishing（4）

- `CustomFishingHook` (105) — CustomFishing 接入点：注册钓获监听器
- `CustomFishingListener` (63) — 把一次自定义钓获翻译成 `Trigger.CUSTOM_FISH`
- `CustomFishingCatalog` (61) — 读 CustomFishing 的战利品清单，供校验比对鱼 id
- `FishLoot` (13) — 一条自定义鱼战利品

### core/listener（6）

- `ProgressListener` (37, abstract) — 监听器基类：只做「事件 → 动作」的翻译与投递（判定逻辑禁止写在这里）
- `BlockListener` (102) — 方块域：挖掘、放置、对方块交互
- `EntityListener` (160) — 实体域：击杀、垂钓、剪切、繁殖、驯服、与实体交互
- `ItemListener` (111) — 物品域：合成、消耗、附魔
- `TextListener` (56) — 文本域：发言、执行命令
- `PlayerListener` (55) — 生命周期：进服载入索引 + 补发周期任务，退服释放内存

### core/objective（5）

- `ObjectiveBuiltIns` (70) — 内置目标类型清单（「插件自带哪些目标」的唯一事实来源），顺带给每个字段定值域
- `CustomFishObjective` (93) — 自定义钓鱼目标（按鱼 id 与尺寸统计）
- `InteractObjective` (75) — 交互目标（额外判 `mode`：左键 / 右键 / 对方块 / 对实体）
- `ChatObjective` (73) — 发言目标（自定义匹配）
- `TargetObjective` (24) — 「一个目标字段 + 一个数量」这一类目标的统一实现（多数目标共用）

### core/period（3）

- `PeriodicService` (311) — 周期任务：抽取、发放、过期、刷新（含扣费与次数）
- `CurrencyType` (129, enum) — 刷新费用可用的货币（金币 / 点券；都不可用时刷新明确提示不可用）
- `Periods` (126) — 周期标识与下次重置时刻的计算（纯函数，便于测试）

### core/placeholder（2）

- `QuestPlaceholderExpansion` (180) — 变量扩展本体（`%playertaskx_daily_progress%` 之类）
- `PlaceholderHook` (52) — PlaceholderAPI 反射接入点（把编译期依赖关在这一个类里）

### core/preset（1）

- `PresetRefs` (159) — 预设引用的展开、校验与瘦身（唯一一处判定「什么最终生效」）

### core/display（1）

- `ProgressDisplay` (174) — 进度展示：actionbar 推送进度、完成时发 title

### core/quest（1）

- `QuestAdminService` (204) — 任务定义维护入口：保存、删除、重载、校验、启停（保存时先 `trim` 再 `resolve`）

### core/registry（3）

- `QuestRegistryImpl` (59) — 内存任务注册表（引擎与 GUI 的任务查询都走它）
- `ObjectiveRegistryImpl` (51) — 目标类型注册表实现（id → 类型）
- `RewardRegistryImpl` (35) — 奖励类型注册表实现

### core/reward（4）

- `RewardBuiltIns` (24) — 内置奖励类型清单（「插件自带哪些奖励」的唯一事实来源）
- `PointsReward` (144) — 点券奖励（反射调用 PlayerPoints）
- `MoneyReward` (116) — 金币奖励（判据是「经济服务在册」，不是插件名）
- `CommandReward` (57) — 命令奖励（控制台执行，支持 `%player%`）

### core/schema（1）

- `ValueKinds` (236) — 值域的**运行期**判定：某个值属于哪些值域、某个配置值能不能命中

### core/storage（10）

- `DatabaseFactory` (145) — 按配置开库、建表、装配四份仓储（存储层唯一入口）
- `Dialect` (89, enum) — 方言：SQLite 与 MySQL 的语法差异全部集中在这里
- `PlayerQuestRepository` (51) — 玩家任务仓储（进行中的任务、周期状态、刷新次数）
- `DefinitionRepository` (43) — 「内容类」仓储契约（任务定义与预设共用）
- `Database` (38) — 极简数据库门面（查询 / 更新 / 事务，统一参数占位符）
- `StorageException` (13) — 把 `SQLException` 包成运行时异常
- `DefinitionReadOnlyException` (12) — 试图修改「文件里的定义」时抛出（命令与 GUI 把原因讲给管理员）
- `RowMapper` (11) — `ResultSet` 当前行 → 对象的映射函数
- `PresetRepository` (10) — 预设仓储（`DefinitionRepository<Preset>` 的别名接口）
- `QuestRepository` (7) — 任务定义仓储（`DefinitionRepository<Quest>` 的别名接口）

### core/storage/codec（3）

- `JsonCodec` (206) — JSON 编解码；**读时绝不抛异常**（脏数据降级为空并记日志）
- `QuestJson` (120) — 文档 → 任务（库的 `properties` 列与 `quests/*.yml` 共用一份字段定义）
- `PresetJson` (38) — 文档 → 预设（同上）

### core/storage/jdbc（7）

- `JdbcQuestRepository` (282) — 任务定义仓储实现（子表整表取出后在内存按 `quest_id` 分组）
- `JdbcPlayerQuestRepository` (259) — 玩家任务仓储实现（含 `period_state` 读写；脏行逐条跳过）
- `JdbcDatabase` (226) — JDBC 存储引擎：SQLite 单连接 + WAL、MySQL 走 HikariCP，执行逻辑只一份
- `Schema` (112) — 建表语句的唯一来源（6 张表 + 2 个索引）
- `JdbcPresetRepository` (76) — 预设仓储实现
- `Sql` (46) — JDBC 参数绑定小工具
- `EnumText` (40) — 枚举列的容错解析（缺失 / 非法退回默认值并告警，不抛异常）

### core/storage/yaml（9）

- `DefinitionFolder` (208) — 一个 YAML 定义文件夹（`quests/` / `presets/`）：递归扫描、解析、只在空目录铺一次示例
- `ExampleDefinitions` (149) — 出厂示例：从 jar 内资源铺到数据目录（只在空目录时）
- `YamlText` (139) — YAML 文本 → 普通对象，自带 1.2-core 语义（防 `NO` 被当布尔、`1.20` 被当浮点）
- `YamlSources` (105) — YAML 源读取与缓存（扫目录 → 解析 → 转模型 → 记 id 位置）
- `MergedSources` (79) — 合并规则：库优先 + 文件补充 + 冲突告警一次 + 只读判定
- `MergedDefinitionRepository` (59) — 合并仓储的通用实现（读取走合并、写入落库、只读 id 抛异常）
- `YamlDefinitions` (55) — `quests/` 与 `presets/` 两个只读来源的构造（文档 → 模型）
- `MergedQuestRepository` (17) — 任务定义的对外仓储（库 + `quests/`，把类型绑到 `Quest`）
- `MergedPresetRepository` (14) — 预设的对外仓储（库 + `presets/`，把类型绑到 `Preset`）

### core/text（2）

- `PlayerNotifier` (88) — 向玩家发送 actionbar 与 title
- `Texts` (64) — 装配玩家可见文本的小工具（类型显示名、属性摘要等）

---

## 测试（27 个测试类 + 2 个测试替身 / 4432 行）

测试与它要测的类**同包**（包内可见的成员才测得动），目录结构与主代码一一对应。按「钉住什么」分组：

| 组 | 类 |
|---|---|
| 存储 | `StorageIntegrationTest`（内存 SQLite 上跑真 SQL：方言、JSON 列、事务、脏数据） |
| 引擎 | `ProgressServiceTest`、`StructureFingerprintTest`、`StructureFingerprintFormatTest`（指纹格式：换实现即静默重置全部进度）、`RewardServiceTest` |
| YAML 定义 | `YamlTextTest`、`YamlDefinitionSourceTest`、`MergedDefinitionRepositoryTest`、`ExampleDefinitionsTest` |
| 任务维护 | `QuestAdminServiceTest`、`PresetRefsTest` |
| 奖励与货币 | `MoneyRewardTest`、`CurrencyTypeTest` |
| 周期 | `PeriodsTest` |
| schema / 值域 | `ValueKindsTest`、`ObjectiveFieldDomainTest`（每个字段必须声明值域，且没有无人声明的值域） |
| 界面 | `QuestDetailMenuTest`、`ProgressDisplayRenderTest`、`SlotLayoutTest`（布局表：下标映射、重名/越界/未知名字必须炸） |
| 编辑器 | `QuestDraftTest`（草稿读写语义：预设引用原样保住、节点增删移）、`FieldValueTest`（聊天输入 ↔ 字段值）、`CandidateCatalogTest`（按值域列候选） |
| 目标与联动 | `TargetMatchAliasTest`、`CustomFishObjectiveTest`、`CustomContentHooksTest`、`MythicMobsHookTest`、`CustomFishingListenerTest` |
| 监听器 | `EntityListenerTest`、`ItemListenerCraftAmountTest` |
| 其它 | `LanguageFileTest`、`YLibCommandHelpTest` |
| 测试替身 | `InMemoryPlayerQuestRepository`（`core/storage`）、`FakeMythicMobsHook`（`core/integration/mythicmobs`） |

---

## 改代码前先看这三条数据流

1. **一次游戏动作 → 进度**：`core/listener/*` 把事件翻成 `ProgressContext` → `engine/ProgressService.apply` → 改 `PlayerQuest` → `ApplyResult` → `display/ProgressDisplay` 与 `gui/menu/*` 提示。
2. **任务定义从哪来**：`quests/*.yml`（只读，`storage/yaml`）⊕ 数据库（`storage/jdbc`）→ `MergedQuestRepository` → `quest/QuestAdminService.reload`（顺带 `preset/PresetRefs.resolve` 展开预设引用）→ `registry/QuestRegistryImpl` → 引擎 / GUI / 命令都读它。
3. **改一条定义**：`/ptxa` 命令或编辑器（`/ptxa menu`）→ `QuestAdminService.save`（`trim` → 落库 → `resolve` → 重建玩家索引）；文件里的定义只读，改文件后 `/ptxa reload`。

---

## 维护约定

- **新增 / 删除 / 改名一个类（或换包）时，同步更新本文件**（一行一句话即可）。
- 行数会变动，不必每次改；下次大改时用下面这条口令重新统计（在仓库根目录执行）：

```powershell
Get-ChildItem api\src\main\java,core\src\main\java -Recurse -Filter *.java |
  ForEach-Object {
    $t = [System.IO.File]::ReadAllLines($_.FullName)
    "{0}`t{1}`t{2}" -f $_.BaseName, $t.Length, (($t | Where-Object { $_ -match '^\s*\*\s+\S' } | Select-Object -First 1) -replace '^\s*\*\s*','')
  }
```

（输出：类名 / 行数 / 类 javadoc 的第一句。）
