# 配置

配置文件位于 `plugins/playerTaskX/config.yml`，首次启动自动生成。
文件里每一项都带注释，这里补充**取值含义与常见组合**。

修改后执行 `/ptxa reload` 重载（涉及存储类型、端口等启动项需要重启服务器）。

---

## 语言

```yaml
language:
  default: "zh_CN"              # 默认语言
  available: [ "zh_CN", "en" ]  # 可用语言列表
  use-client-locale: true       # 优先使用玩家客户端语言
```

语言文件位于 `plugins/playerTaskX/lang/<语言代码>.yml`。首次启动会从插件内置文件释放一份，
可以直接编辑；**插件升级新增的语言键会自动补进你的文件**，不会覆盖你改过的内容。

> **键名与 `yes` / `no` / `on` / `off` 同名时必须加引号**：YAML 1.1 会把裸写的 `yes:` 当布尔值读，
> 键名实际变成 `common.true`，插件查 `common.yes` 时就只会报「缺少语言键」。
> 内置文件的 `common` 段因此写成 `"yes": "是"`；自己加键时遇到这类名字照此加引号即可
> （`LanguageFileTest` 会拦住重新写成裸键的情况）。

`use-client-locale: true` 时，玩家看到的是自己客户端语言对应的文本（例如英文客户端看到英文），
控制台始终使用 `default` 指定的语言。

### 文本格式：MiniMessage 为主，兼容 `&`

语言文件、任务名、物品名与描述里都可以用下面三种写法，而且**可以混排**：

```yaml
# 三种写法效果相同，也可以写在同一行里
a: "<green>已完成</green>"
b: "&a已完成"
c: "§a已完成"

# 十六进制颜色两种写法都支持
hex1: "<#ff8800>橙色</#ff8800>"
hex2: "&#ff8800橙色"
```

常用标签：`<red>` `<green>` `<yellow>` `<gray>` `<white>` `<bold>` `<italic>`
`<underlined>` `<reset>`；颜色同样可以用 `&` 码简写（`&c` `&a` `&e` `&7` `&f`）。

> **写错了不会显示标签**：标签未闭合或拼错时，这段文字会退化成纯文本显示，
> 玩家不会看到 `<red>` 这样的内部语法。

**唯一例外是奖励里的命令**：`command` 奖励发出的命令文本由**被执行的那个插件/原版**
去解析颜色，因此写什么取决于对方支持什么（常见是 `&` 码）。

---

## 存储

任务定义、预设与玩家进度**都存在同一个数据库里**，由 `storage.type` 一处决定：

| 取值 | 说明 |
|---|---|
| `SQLITE` | **默认**。零配置、单文件，适合单机与小型服务器 |
| `MYSQL` | **多服共享玩家数据时必须使用**；连接池由 HikariCP 管理 |

```yaml
storage:
  type: SQLITE                  # SQLITE | MYSQL
  sqlite:
    file: "data/playerTaskX.db" # 相对插件数据目录
  mysql:
    default:
      host: "127.0.0.1"
      port: 3306
      database: "playerTaskX"
      username: "root"
      password: ""
      parameters: "useSSL=false&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
      pool-size: 8
```

两类数据虽然同库，但落在不同的表上，互不干扰：

| 表 | 内容 |
|---|---|
| `quest` / `quest_objective` / `quest_reward` | 任务定义（**内容**：人写、改动少） |
| `preset` | 目标与奖励预设（编辑器的便利设施） |
| `player_quest` / `daily_state` | 玩家进度与每日刷新次数（**状态**：高频写、需要事务） |

> 数据库打开或建表失败时插件会被禁用，并在控制台写明原因——避免以「零任务」状态静默运行。
> `storage.type` 写错单词（如 `MYSQL5`）时会回退 SQLite 并在日志里告警。

### 任务定义怎么改

任务定义存在数据库里，改它走三条路：

- **网页编辑器**（默认开启，见 [网页编辑器](editor)）：可视化或 YAML 文本编辑，支持整份清单的导入/导出；
- **游戏内指令**：`/ptxa` 系列（见 [命令](commands)）；
- **`quests/` 目录下的 YAML 文件**：**只读**来源，适合随插件发布、进版本控制（见下节）。

前两条**保存即生效**（不需要重启，也不需要 reload）。

需要 diff、进版本控制或跨服搬运时，用编辑器把任务**导出成 YAML**，整理好再导入。
数据库里的定义始终是权威：同一个 id 两边都有时以数据库为准，文件里那份被忽略并在日志里告警。

> 只有在换了存储后端、改了 `quests/` 里的文件、或在插件外直接改了数据库之后，
> 才需要 `/ptxa reload` 重新载入。

切换后端**不会自动搬运已有数据**：SQLite 与 MySQL 之间要换，用编辑器导出再导入。

> SQLite 库在服务端运行期间不要用外部工具去改：外部连接的 DDL 会被 WAL 回滚，
> 得出的结论是错的。要改就停服再改。

### 用 YAML 文件写定义

```yaml
definitions:
  read-files: true              # 默认开启
```

开启后插件会读取数据目录下的这两个文件夹（不存在时自动创建）：

```
plugins/playerTaskX/
├── quests/      # 任务定义，一个文件一个任务
└── presets/     # 目标 / 奖励预设
```

**文件夹空着时插件会铺一份示例进去**（12 个任务 + 11 个预设，id 统一是 `example_file_` 前缀），
让你直接看到格式、复制一份改改就能用。库里那套示例（`example_` 前缀）是另外一份、
可以在编辑器里随便改；文件里这套是只读的、改文件才生效，两套并存、互不干扰。
不想要示例：删掉文件即可——**只要目录里还有任何一个 YAML，插件就不会再补**；
想彻底不读文件就设 `definitions.read-files: false`。

`quests/example_file_daily_mine.yml`（文件名就是 id，因此正文里不写 `id`）：

```yaml
name: <yellow>挖矿日常
icon: DIAMOND_PICKAXE
category: 每日
type: DAILY
refreshCost: 1000
objectives:
  - type: break_block
    properties:
      target: STONE
      amount: 64
rewards:
  - type: exp
    properties:
      amount: 300
```

约定：

- **一个文件一个定义**；两个目录都会**递归扫描子目录**，方便按主题分组；
- **文件名（去掉扩展名）就是 id**，上例的 id 即 `example_file_daily_mine`；
  文件内容里写了 `id:` 则以 `id:` 为准（复制文件改内容时不会撞 id）；
- 只认 `.yml` 与 `.yaml`，其它文件（`README`、备份）一律忽略；
- **顶层必须是「键: 值」**：写成列表（一个文件里塞多条定义）会被跳过并告警；
- 字段名与编辑器的 YAML 视图、导出的 YAML **完全一致**，可以互相粘贴；
- **可以引用预设**（`preset: <id>`），同一份配置不必在多个任务里各抄一遍：
  ```yaml
  objectives:
    - preset: mine-stone      # 类型与字段来自预设
      properties:
        amount: 128           # 任务自己写的字段覆盖预设里的同名值
  rewards:
    - preset: reward-exp      # 奖励同理
  ```
  改预设会让所有引用它的任务一起变（文件里的定义在 `/ptxa reload` 后生效）；
  引用不存在的预设、或类别用错（拿奖励预设当目标用）都会被校验标出来；
- 预设的类别取内容里的 `kind: objectives|rewards`；没写 `kind` 时按所在子目录名判断
  （`presets/rewards/exp.yml`）；两者都判断不出来就跳过并告警——猜错会把奖励预设混进目标列表；
- 单个文件写坏（缩进、缺引号）**只跳过它并记一条告警**，不会让插件起不来；
  只有注释或内容为空的文件被**静默**忽略（留个空文件写笔记是合理用法）。

`presets/example_file_mine-stone.yml`：

```yaml
kind: objectives
name: 挖 64 个石头
type: break_block
properties:
  amount: 64
  target: STONE
description: 最基础的挖掘目标
```

#### 与数据库的关系：库优先，文件只读

| 情况 | 结果 |
|---|---|
| id 只在文件里 | 生效，但**只读**：编辑器与游戏内命令都改不了它 |
| id 只在数据库里 | 正常，可读可写 |
| 两边都有 | **以数据库为准**，文件里那份被忽略；日志里记一条告警，同一个 id 只报一次 |

插件**只在这两个文件夹空着时写一次示例**，之后再不写入。游戏内命令与网页编辑器的保存、
删除一律只改数据库——因此文件里的定义在编辑器里是只读的（任务列表会标「文件」徽标，
任务编辑页顶部有黄色提示条）。要改它们就改文件，然后 `/ptxa reload`（或重启）；
想搬进数据库就在列表页「导出 YAML」，删掉原文件后再「导入 YAML」。

> **MySQL 多服时尤其注意**：写在 YAML 文件里的定义**不会跨服同步**，各服会看到不同的任务。
> 只有「有意让不同服务器的任务存在差异」时才这样用，否则请把定义放进数据库——
> `storage.type: MYSQL` 本来就是为多服共享准备的。

---

## 进度提示

```yaml
progress:
  actionbar: true               # 是否用动作栏显示进度
  actionbar-interval: 20        # 刷新间隔（tick，20 = 1 秒）
  title-on-complete: true       # 任务完成时弹出标题提醒
```

动作栏显示的内容形如：

```
挖矿日常 |████████░░░░░░░░░░░░| 40% » 挖掘 26/64
```

> 在不支持动作栏 API 的服务端（非 Paper/Folia 系）上，进度会改用副标题位置显示，
> 启动日志里会明确写出走的是哪条路径。

---

## 周期任务（每日 / 每周 / 每月 / 自定义）

**四种周期各一段配置**，`daily` 默认开启，其余默认关闭（开了才会凭空多出一批任务）：

```yaml
# 刷新费用的货币顺序（四种周期共用；它说的是「这台服务器有什么货币」）
refresh-currency: [ MONEY, POINTS, EXP ]

periodic:
  daily:                        # 每日任务
    enabled: true
    amount: 3                   # 每位玩家每周期抽取数量
    reset-hour: 4               # 重置时刻（0-23）
    refresh-cost: 1000.0        # 刷新费用；0 = 免费
    refresh-limit: 3            # 每周期最多刷新次数
    pool: []                    # 任务池（任务 id）；留空 = 取所有 type=DAILY 的任务
  weekly:                       # 每周任务
    enabled: false
    amount: 2
    reset-hour: 4
    reset-weekday: MONDAY       # 到这一天（含重置时刻）换一批
    refresh-cost: 2000.0
    refresh-limit: 1
    pool: []
  monthly:                      # 每月任务
    enabled: false
    amount: 2
    reset-hour: 4
    reset-month-day: 1          # 到这一天换一批（写 1-28，避免 2 月没有 29/30/31 号）
    refresh-cost: 5000.0
    refresh-limit: 1
    pool: []
  custom:                       # 自定义周期
    enabled: false
    amount: 2
    period: 3d                  # 周期长度：<n>d（天）或 <n>h（小时）
    refresh-cost: 0
    refresh-limit: 0
    pool: []
```

任务属于哪种周期由任务自己的 `type` 决定（`DAILY` / `WEEKLY` / `MONTHLY` / `CUSTOM`），
配置只决定「这种周期怎么跑」。**每种周期独立发放**：一个玩家可以同时有 3 个每日任务和 2 个每周任务。

### 周期怎么算

| 类型 | 换一批的时机 | 周期标识（内部） |
|---|---|---|
| 每日 | 每天的重置时刻 | `2026-09-14` |
| 每周 | 每 `reset-weekday` 的重置时刻 | `W2026-09-14`（本周起始日） |
| 每月 | 每 `reset-month-day` 的重置时刻 | `2026-09` |
| 自定义 | 每 `period` 长度（按固定锚点取整，**跨服一致**） | `C3d#6893` |

`reset-hour` 的语义是「早于该时刻算上一个周期」：设为 `4` 时，某日凌晨 3:30 与前一天 23:00
属于同一个周期，符合「熬夜到凌晨还算今天」的直觉；设为 `0` 则按自然日划分。
自定义周期没有「几点重置」这回事，长度一到就换（例如 `12h` 就是每 12 小时一批）。

### 抽取规则

任务都是**按玩家**抽取的，结果由 `(玩家, 周期, 刷新次数)` 决定——同一玩家在同一周期内重登、
换服、掉线重连都会看到同一批任务，也不会因为反复重连而刷出不同任务。

### `refresh-currency`：货币顺序

这是一个**有序列表**，插件按顺序取第一个可用的货币：

| 取值 | 货币 | 何时可用 |
|---|---|---|
| `MONEY` | 金币 | 有经济插件在册（EssentialsX、CMI 等；Vault / VaultUnlocked 只是接口层） |
| `POINTS` | 点券 | 装了 PlayerPoints |
| `EXP` | 经验 | **总是可用**（原版资源，无需任何插件） |

默认是 `[MONEY, POINTS, EXP]`。常见改法：

```yaml
# 优先扣经验，经验不够才考虑金币（活动期不想消耗玩家金币）
refresh-currency: [EXP, MONEY]
```

```yaml
# 只用经验：完全不碰经济插件，也不受 Vault 是否存在影响
refresh-currency: [EXP]
```

```yaml
# 列表里没写的货币视为禁用，因此这里只会扣金币；没有可用经济服务时退回默认顺序
refresh-currency: [MONEY]
```

规则：

- 列表里**没写**的货币视为禁用
- 配的货币在当前服务器上**都不可用**时，退回内置顺序（最终一定落到经验），
  因此刷新功能不会因为配置问题而失效
- 无法识别的名字会被**跳过**并继续往下找，不会因为一个拼写错误整体失效
- 名字大小写不敏感，`exp` / `EXP` 等价

### `refresh-cost` 与提示文案

- 设为 `0` 表示免费刷新，此时完全不涉及货币
- 玩家刷新后，提示里会说明**实际扣的是哪种货币**，例如
  「本次刷新消耗 1,000 金币」或「本次刷新消耗 1000 经验」
- 余额不足时提示带货币名与当前余额，例如「经验不足，需要 1000（当前 320）」
- 管理员用 `/ptxa resetperiod` 重置**不扣费也不消耗次数**（它是排障工具，不是消费入口）

> 早期版本有「任务币」内建货币，现已移除：插件不再引入玩家需要额外理解的第二套货币，
> 费用只用上表三种之一。

---

## 网页编辑器

```yaml
editor:
  enabled: true
  port: 28080
  token: ""                     # 访问令牌，留空表示不校验
  fetch-chinese-names: true     # 编辑器图标列表是否下载中文译名
```

浏览器打开 `http://<服务器地址>:28080` 即可。端口被占用时自动 +1 重试（最多 10 个），实际端口以启动日志为准。详见 [网页编辑器](editor)。

### `fetch-chinese-names`：中文译名从哪来

Minecraft 服务端**不自带中文语言文件**（只有英文），因此编辑器图标列表的中文名
需要额外获取。插件按下述顺序尝试，任一成功即可：

1. 读 `plugins/playerTaskX/editor/zh_cn.json`（**推荐离线服手动放一份**）
2. 该文件不存在时，后台从官方资源下载一次并缓存到同一路径
3. 都失败则显示英文名——**不影响使用**，用英文名或枚举名照样能搜到

只要第 2 步成功过一次，之后即使服务器不能访问外网也一直有中文名。
若你的服务器策略不允许插件访问外网，把这项设为 `false` 并手动放置语言文件即可。
两种情况下启动日志都会明确写出当前状态与文件路径。

> 它在 `editor/` 而不是 `lang/`：`lang/` 放的是**插件自己的语言文件**（`zh_CN.yml`，发给玩家看的文案），
> 而这份 `zh_cn.json` 是 **Minecraft 的译名数据**，只服务编辑器的图标列表，两者互不相干。

> **安全提示**：未设置 `token` 时，任何能访问该端口的人都能修改任务。
> 对公网开放的服务器**务必**设置令牌，或用防火墙限制来源 IP。
> 未设置时插件会在启动日志里给出警告。

---

## 完整示例：MySQL + 免费刷新

```yaml
storage:
  type: MYSQL
  mysql:
    default:
      host: "10.0.0.5"
      port: 3306
      database: "minecraft"
      username: "mc"
      password: "your-password"

daily:
  amount: 5
  reset-hour: 0
  refresh-cost: 0                # 免费刷新：无需经济插件
  refresh-limit: 5

progress:
  actionbar-interval: 10         # 每 0.5 秒刷新一次，更跟手
```
