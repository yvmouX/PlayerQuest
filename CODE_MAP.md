# 代码地图

这份文件回答**「哪个类在哪、它负责什么」**，用来在改代码前先找对地方。
设计与取舍（为什么这么拆）见 [`ARCHITECTURE.md`](ARCHITECTURE.md)；面向使用者的文档在 `docs/`。

约定：**一个类只写一句话**，写「它负责什么」，不写它怎么实现。实现细节看类自己的 javadoc。

---

## 规模（本次统计：110 个类 / 14748 行）

| 模块 | 类 / 行 | 说明 |
|---|---|---|
| `api` | 19 / 956 | 模型与扩展点契约（给扩展作者看的公共 API） |
| `core` | 91 / 13792 | 全部实现 |
| 测试 | 34 / 6338 | `core/src/test`（另有 3 个测试替身） |

行数是「含空行按文件行数累加」，会随提交变动；重新统计见文末。

---

## 包总览

| 包 | 类 / 行 | 一句话职责 |
|---|---|---|
| （根） | 1 / 415 | 插件入口与装配根 |
| `api/model` | 8 / 490 | 数据模型（任务、目标、奖励、预设、玩家记录） |
| `api/objective` | 3 / 190 | 目标扩展点与动作契约 |
| `api/registry` | 3 / 115 | 三张注册表的接口 |
| `api/reward` | 1 / 36 | 奖励扩展点 |
| `api/schema` | 4 / 248 | 「类型自描述」：字段、控件、值域 |
| `core/command` | 2 / 788 | 玩家 / 管理员命令 |
| `core/config` | 2 / 414 | `config.yml` 与周期配置 |
| `core/engine` | 2 / 348 | 进度引擎（唯一改玩家进度的地方） |
| `core/gui` | 6 / 1202 | 箱子菜单框架与三个界面 |
| `core/integration` | 12 / 1345 | 四个软依赖接入（MythicMobs / CustomFishing / ItemsAdder / CraftEngine） |
| `core/listener` | 6 / 583 | Bukkit 事件 → `ProgressContext` |
| `core/objective` | 4 / 312 | 目标类型的实现 |
| `core/period` | 2 / 519 | 周期任务的抽取、过期与刷新 |
| `core/placeholder` | 2 / 266 | PlaceholderAPI 变量 |
| `core/progress` | 1 / 194 | 进度展示（actionbar / title） |
| `core/quest` | 2 / 450 | 任务定义维护 + 预设引用展开 |
| `core/registry` | 4 / 282 | 注册表实现与内置类型清单 |
| `core/reward` | 5 / 658 | 奖励发放与刷新费用货币 |
| `core/schema` | 1 / 282 | 值域的运行期判定 |
| `core/storage` | 14 / 1100 | 存储契约与编解码 |
| `core/storage/jdbc` | 7 / 1170 | JDBC 实现（SQLite / MySQL 共用） |
| `core/storage/yaml` | 9 / 1223 | 只读 YAML 定义目录、示例、导出导入 |
| `core/text` | 2 / 185 | 文本装配与推送 |
| `core/web` | 7 / 1933 | 网页编辑器（HTTP + 素材目录 + 译名） |

---

## 逐包逐类

### 根（1）

- `PlayerTaskX` (415) — 插件入口：只做装配与启停，业务逻辑都在下面各子系统

### api/model（8）

- `Quest` (60) — 任务定义（静态数据，由配置文件或网页编辑器维护）
- `QuestObjective` (66) — 任务目标：一份配置数据；引用预设时带 `properties`（生效值）与 `authored`（作者那份）
- `QuestReward` (58) — 任务奖励：同上，发放行为由 `RewardType` 提供
- `ConfigMap` (62) — 目标与奖励共用的配置表读取（不可变拷贝、预设键、宽松取字符串 / 整数）
- `PlayerQuest` (153) — 玩家进行中的任务（运行期状态）：进度按「目标下标 → 计数」存，附结构摘要
- `Preset` (50) — 目标 / 奖励预设：一组「类型 + 属性」
- `QuestType` (26) — 任务类型（NORMAL / DAILY / WEEKLY / MONTHLY / CUSTOM）
- `QuestStatus` (15) — 玩家任务状态（进行中 / 待领取 / 已领取 / 已放弃）

### api/objective（3）

- `ObjectiveType` (76) — **目标扩展点**：新增一种目标只实现它，表单由 `schema()` 自动生成
- `ProgressContext` (74) — 一次游戏内动作的通用表示：引擎与监听器之间唯一的契约
- `Trigger` (40) — 动作类型（破坏 / 放置 / 合成 / 击杀 …），热路径快速判别用

### api/registry（3）

- `ObjectiveRegistry` (33) — 目标类型注册表（按 id 查、列全部）
- `RewardRegistry` (27) — 奖励类型注册表
- `QuestRegistry` (55) — 任务定义注册表（内存视图：按类型、周期池、分类查询）

### api/reward（1）

- `RewardType` (36) — **奖励扩展点**：表单由 `schema()` 生成、发放由 `grant()` 完成

### api/schema（4）

- `ConfigField` (137) — 单个字段描述（键 / 标签 / 控件 / 必填 / 默认值 / 说明 / **值域**）
- `ConfigurableType` (23) — 「由配置驱动」的类型共有形状（`id` + 显示名 + 字段表）
- `FieldType` (31) — 控件种类（文本 / 整数 / 小数 / 开关 / 选择器 / 下拉）——只被编辑器读
- `ValueKind` (57) — 值域词汇表（方块 / 可放置 / 物品 / 食物 / 实体 / 活体 / 可繁殖 / 可驯服 / 可剪毛 / 鱼 / 附魔）

### core/command（2）

- `PlayerCommand` (349) — 玩家命令 `/ptx`（看任务、领取、刷新周期任务）
- `AdminCommand` (439) — 管理员命令 `/ptxa`（列表 / 详情 / 启停 / 删除 / 发放 / 重置周期 / 重载 / 编辑器地址）

### core/config（2）

- `PluginConfig` (264) — 插件主配置（`config.yml` 的全部字段与默认值）
- `PeriodSettings` (150) — 一种周期任务的配置（启用 / 数量 / 重置时刻 / 费用 / 上限 / 池子）

### core/engine（2）

- `ProgressService` (329) — **进度引擎**：唯一修改玩家进度的入口（匹配、累加、完成、待领取）
- `ApplyResult` (19) — 一次动作处理的结果（改了什么），表现层据此提示

### core/gui（6）

- `Menu` (202, abstract) — 箱子菜单框架：建容器、登记槽位、刷新、标记归属
- `MenuItem` (125) — 菜单项：图标 + 点击动作
- `MenuListener` (122) — 把「容器点击」翻译成菜单项的 action
- `QuestDetailMenu` (304) — 任务详情：多目标进度 + 多奖励预览（也用于管理员只读预览）
- `PeriodicQuestMenu` (212) — 周期任务界面（四种周期切换、刷新按钮、剩余次数）
- `AdminQuestMenu` (237) — 管理员任务管理：分页列表、启停、重载

### core/integration（12）

- `SoftDependency` (61) — 软依赖探测（插件是否加载、版本多少）
- `Reflect` (52) — 接入软依赖用的反射小工具（按名字取类取方法，取不到一律 null）
- `CustomContentHook` (50) — 一个自定义内容来源（ItemsAdder / CraftEngine）的契约
- `CustomContentHooks` (255) — 全部已接入来源的汇总，以及围绕它们的查询与校验
- `ItemsAdderHook` (162) — ItemsAdder 接入点（纯反射，不依赖其构件）
- `CraftEngineHook` (186) — CraftEngine 接入点（纯反射）
- `MythicMobsHook` (139) — MythicMobs 接入点的契约（保留接口是为了能塞假身测试）
- `MythicMobs5Hook` (162) — MythicMobs 5.x 的实现（懒解析 `MobManager`，处理 POSTWORLD 时序）
- `CustomFishingHook` (116) — CustomFishing 接入点：注册钓获监听器
- `CustomFishingListener` (74) — 把一次自定义钓获翻译成 `Trigger.CUSTOM_FISH`
- `CustomFishingCatalog` (69) — 读 CustomFishing 的战利品清单，供编辑器选鱼 id
- `FishLoot` (19) — 一条自定义鱼战利品

### core/listener（6）

- `ProgressListener` (43, abstract) — 监听器基类：只做「事件 → 动作」的翻译与投递（判定逻辑禁止写在这里）
- `BlockListener` (110) — 方块域：挖掘、放置、对方块交互
- `EntityListener` (174) — 实体域：击杀、垂钓、剪切、繁殖、驯服、与实体交互
- `ItemListener` (134) — 物品域：合成、消耗、附魔
- `TextListener` (66) — 文本域：发言、执行命令
- `PlayerListener` (56) — 生命周期：进服载入索引 + 补发周期任务，退服释放内存

### core/objective（4）

- `TargetObjective` (46) — 「一个目标字段 + 一个数量」这一类目标的统一实现（多数目标共用）
- `ChatObjective` (80) — 发言目标（自定义匹配）
- `InteractObjective` (72) — 交互目标（额外判 `mode`：左键 / 右键 / 对方块 / 对实体）
- `CustomFishObjective` (114) — 自定义钓鱼目标（按鱼 id 与尺寸统计）

### core/period（2）

- `PeriodicService` (375) — 周期任务：抽取、发放、过期、刷新（含扣费与次数）
- `Periods` (144) — 周期标识与下次重置时刻的计算（纯函数，便于测试）

### core/placeholder（2）

- `PlaceholderHook` (58) — PlaceholderAPI 反射接入点（把编译期依赖关在这一个类里）
- `QuestPlaceholderExpansion` (208) — 变量扩展本体（`%playertaskx_daily_progress%` 之类）

### core/progress（1）

- `ProgressDisplay` (194) — 进度展示：actionbar 推送进度、完成时发 title

### core/quest（2）

- `QuestAdminService` (260) — 任务定义维护入口：保存、删除、重载、校验、启停（保存时先 `trim` 再 `resolve`）
- `PresetRefs` (190) — 预设引用的展开、校验与瘦身（唯一一处判定「什么最终生效」）

### core/registry（4）

- `BuiltIns` (106) — 内置目标与奖励类型清单（「插件自带哪些类型」的唯一事实来源）
- `ObjectiveRegistryImpl` (53) — 目标类型注册表实现（id → 类型）
- `RewardRegistryImpl` (41) — 奖励类型注册表实现
- `QuestRegistryImpl` (82) — 内存任务注册表（引擎与 GUI 的任务查询都走它）

### core/reward（5）

- `RewardService` (165) — 奖励发放：领取校验、逐个发放、状态落库（单个奖励失败不影响其它）
- `MoneyReward` (133) — 金币奖励（判据是「经济服务在册」，不是插件名）
- `PointsReward` (147) — 点券奖励（反射调用 PlayerPoints）
- `CommandReward` (62) — 命令奖励（控制台执行，支持 `%player%`）
- `CurrencyType` (151, enum) — 刷新费用可用的货币（金币 / 点券；都不可用时刷新明确提示不可用）

### core/schema（1）

- `ValueKinds` (282) — 值域的**运行期**判定：某个值属于哪些值域、某个配置值能不能命中

### core/storage（14）

- `Database` (43) — 极简数据库门面（查询 / 更新 / 事务，统一参数占位符）
- `DatabaseFactory` (170) — 按配置开库、建表、装配四份仓储（存储层唯一入口）
- `Dialect` (93, enum) — 方言：SQLite 与 MySQL 的语法差异全部集中在这里
- `DefinitionRepository` (60) — 「内容类」仓储契约（任务定义与预设共用）
- `QuestRepository` (15) — 任务定义仓储（`DefinitionRepository<Quest>` 的别名接口）
- `PresetRepository` (16) — 预设仓储（同上）
- `PlayerQuestRepository` (86) — 玩家任务仓储（进行中的任务、周期状态、刷新次数）
- `RowMapper` (13) — `ResultSet` 当前行 → 对象的映射函数
- `StorageException` (15) — 把 `SQLException` 包成运行时异常
- `JsonCodec` (265) — JSON 编解码；**读时绝不抛异常**（脏数据降级为空并记日志）
- `QuestJson` (172) — 任务 ⇄ 文档映射（编辑器 JSON 与 `quests/*.yml` 共用一份字段定义）
- `PresetJson` (78) — 预设 ⇄ 文档映射（同上）
- `Hash` (56) — 目标结构指纹：目标列表算成短摘要存进玩家记录，顺序变化靠它识别
- `DefinitionReadOnlyException` (18) — 试图修改「文件里的定义」时抛出（编辑器回 409，命令打印原因）

### core/storage/jdbc（7）

- `JdbcDatabase` (235) — JDBC 存储引擎：SQLite 单连接 + WAL、MySQL 走 HikariCP，执行逻辑只一份
- `Schema` (118) — 建表语句的唯一来源（6 张表 + 2 个索引）
- `Sql` (61) — JDBC 参数绑定小工具
- `EnumText` (52) — 枚举列的容错解析（缺失 / 非法退回默认值并告警，不抛异常）
- `JdbcQuestRepository` (300) — 任务定义仓储实现（子表整表取出后在内存按 `quest_id` 分组）
- `JdbcPresetRepository` (89) — 预设仓储实现
- `JdbcPlayerQuestRepository` (315) — 玩家任务仓储实现（含 `period_state` 读写；脏行逐条跳过）

### core/storage/yaml（9）

- `DefinitionFolder` (239) — 一个 YAML 定义文件夹（`quests/` / `presets/`）：递归扫描、解析、只在空目录铺一次示例
- `YamlSources` (114) — YAML 源读取与缓存（扫目录 → 解析 → 转模型 → 记 id 位置）
- `MergedSources` (92) — 合并规则：库优先 + 文件补充 + 冲突告警一次 + 只读判定
- `MergedDefinitionRepository` (75) — 合并仓储的通用实现（读取走合并、写入落库、只读 id 抛异常）
- `MergedQuestRepository` (22) — 任务定义的对外仓储（库 + `quests/`，把类型绑到 `Quest`）
- `MergedPresetRepository` (19) — 预设的对外仓储（库 + `presets/`，把类型绑到 `Preset`）
- `YamlDefinitions` (203) — 任务 / 预设 ⇄ YAML 文档映射，以及导出 / 导入 / 只读来源的构造
- `YamlText` (296) — YAML 文本 ⇄ 普通对象，自带 1.2-core 语义（防 `NO` 被当布尔、`1.20` 被当浮点）
- `ExampleDefinitions` (163) — 出厂示例：从 jar 内资源铺到数据目录（只在空目录时）

### core/text（2）

- `Texts` (80) — 装配玩家可见文本的小工具（类型显示名、属性摘要等）
- `PlayerNotifier` (105) — 向玩家发送 actionbar 与 title

### core/web（7）

- `EditorServer` (242) — 编辑器服务本身：端口重试、令牌校验、静态资源托管
- `EditorApi` (691) — 全部 `/api/*` 路由与请求 / 响应约定（当前最大的一个类）
- `EditorServices` (75) — `EditorApi` 需要的宿主能力（为了能脱离服务端做真 HTTP 测试）
- `PluginEditorServices` (109) — 上面接口的插件侧实现：把主类持有的子系统转交过来
- `MaterialCatalog` (393) — 编辑器可选的材料 / 实体 / 附魔 / 鱼清单（含来源标签与值域）
- `LangFileStore` (363) — 译名来源：读服务端 `en_us.json`、可选下载或读本地 `zh_cn.json`
- `HttpText` (60) — 极简 HTTP 文本获取，只服务「下载语言文件」这一件事

---

## 测试（34 个类 / 6338 行）

按「钉住什么」分组：

| 组 | 类 |
|---|---|
| 存储 | `StorageIntegrationTest`（内存 SQLite 上跑真 SQL：方言、JSON 列、事务、脏数据）、`HashTest`（指纹格式，防止换哈希实现把进度全重置） |
| YAML 定义 | `YamlTextTest`、`YamlDefinitionsTest`、`YamlDefinitionSourceTest`、`MergedDefinitionRepositoryTest`、`ExampleDefinitionsTest` |
| 编辑器 | `EditorApiTest`（真起 Javalin 打 HTTP）、`MaterialCatalogTest`、`PluginEditorServicesTest` |
| 任务维护 | `QuestAdminServiceTest`、`PresetRefsTest` |
| 引擎 | `ProgressServiceTest`、`StructureFingerprintTest` |
| 奖励 | `RewardServiceTest`、`MoneyRewardTest`、`CurrencyTypeTest` |
| 周期 | `PeriodsTest` |
| schema / 值域 | `ValueKindsTest`、`ObjectiveFieldTypeConsistencyTest`（每个字段必须声明值域） |
| 界面 | `QuestDetailMenuTest`、`ProgressDisplayRenderTest` |
| 目标与联动 | `TargetMatchAliasTest`、`CustomFishObjectiveTest`、`CustomContentHooksTest`、`MythicMobsHookTest`、`CustomFishingListenerTest` |
| 监听器 | `EntityListenerTest`、`ItemListenerCraftAmountTest` |
| 其它 | `LanguageFileTest`、`YLibCommandHelpTest` |
| 测试替身 | `InMemoryPlayerQuestRepository`、`FakeMythicMobsHook`、`FakeCustomContentHook` |

---

## 改代码前先看这三条数据流

1. **一次游戏动作 → 进度**：`core/listener/*` 把事件翻成 `ProgressContext` → `ProgressService.apply` → 改 `PlayerQuest` → `ApplyResult` → `ProgressDisplay` / GUI 提示。
2. **任务定义从哪来**：`quests/*.yml`（只读，`storage/yaml`）⊕ 数据库（`storage/jdbc`）→ `MergedQuestRepository` → `QuestAdminService.reload`（顺带 `PresetRefs.resolve` 展开预设引用）→ `QuestRegistryImpl` → 引擎 / GUI / 编辑器都读它。
3. **改一条定义**：编辑器 `/api/quests` 或 `/ptxa` → `QuestAdminService.save`（`trim` → 落库 → `resolve` → 重建玩家索引）。

---

## 维护约定

- **新增 / 删除 / 改名一个类时，同步更新本文件**（一行一句话即可），否则它会比代码更早腐烂。
- 行数会变动，不必每次改；下次大改时用下面这条口令重新统计（在仓库根目录执行）：

```powershell
Get-ChildItem api\src\main\java,core\src\main\java -Recurse -Filter *.java |
  ForEach-Object {
    $t = [System.IO.File]::ReadAllLines($_.FullName)
    "{0}`t{1}`t{2}" -f $_.BaseName, $t.Length, (($t | Where-Object { $_ -match '^\s*\*\s+\S' } | Select-Object -First 1) -replace '^\s*\*\s*','')
  }
```

（输出：类名 / 行数 / 类 javadoc 的第一句。）
