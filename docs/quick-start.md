# 快速开始

## 1. 安装

把 `playerTaskX-<版本>-all.jar` 放进 `plugins/` 目录，重启服务器。

> **务必使用** `-all.jar`（shadowJar 产物）。同目录下还有一个体积为 0 的普通 jar 是空壳，
> 装它会导致插件无法加载。

首次启动会生成：

```
plugins/playerTaskX/
├── config.yml          # 主配置
├── commands.yml        # 命令配置（YLib 生成，可覆盖权限/别名/启用状态）
├── lang/               # 语言文件，可直接编辑
│   ├── zh_CN.yml
│   └── en.yml
├── quests/             # YAML 任务定义（只读来源，空着时会铺一份示例）
├── presets/            # YAML 目标/奖励预设（同上）
└── data/
    └── playerTaskX.db  # SQLite 数据库（任务定义、预设与玩家进度都在这里）
```

任务写在 `quests/` / `presets/` 里就能被插件读到，但那份定义**只读**（库优先），
游戏内命令只修改数据库里的定义；详见
[配置 · 用 YAML 文件写定义](configuration?id=用-yaml-文件写定义)。

启动日志里应能看到：

```
[playerTaskX] quests/ 是空的，已铺入 12 个示例任务文件（只读来源，可自由删改）
[playerTaskX] presets/ 是空的，已铺入 11 个示例预设文件（只读来源，可自由删改）
[playerTaskX] 存储已就绪: SQLite: data/playerTaskX.db（任务定义、预设与玩家数据在同一库）
[playerTaskX] Registered command: playertaskx
[playerTaskX] Registered command: playertaskxadmin
[playerTaskX] PlayerTaskX 已启用（12 个任务，15 种目标，3 种奖励）
```

示例只有**一套**，随插件发布（`quests/` 里 12 个任务 + `presets/` 里 11 个预设，`example_` 前缀），
**不影响正常使用，可随时清掉**：

- 它们是**只读**的：改用文件 + `/ptxa reload` 生效，游戏内命令只改数据库里的定义；
- 删掉就不会再补（目录非空即不再铺）；整个目录清空则下次启动重新铺一份。

示例覆盖了大部分目标类型的写法。每日任务默认每次抽 3 个，登录 `/ptx` 就能玩；
常驻任务在 `/ptxa list` 与 `/ptxa menu` 的编辑器里查看。数据库里不再播种任何示例。

---

## 2. 检查依赖

插件能启动不等于所有功能都可用。装好后看一眼日志，或执行 `/ptxa list`：

| 日志提示 | 含义 | 处理 |
|---|---|---|
| `奖励类型 money 当前不可用: 没有可用的经济服务` | 金币奖励发不出去（刷新费用会自动改用点券；连 PlayerPoints 也没有时刷新会提示不可用） | 装一个经济插件（Vault / VaultUnlocked + EssentialsX / CMI 等），或改用命令奖励（`give` / `xp` 发物品与经验） |
| `奖励类型 points 当前不可用: 未安装 PlayerPoints` | 点券奖励发不出去 | 安装 PlayerPoints |
| `目标类型 custom_fish 不可用（未安装 CustomFishing）` | 「自定义钓鱼」目标不会涨进度 | 安装 [CustomFishing](https://www.spigotmc.org/resources/100088/)，或改用原版 `fish` 目标 |
| `目标 mythic:xxx 需要 MythicMobs 5.x` | 击杀自定义怪的目标永远命中不了 | 安装 MythicMobs 5.x（4.x 不支持 1.21+），或改用原版实体名 |
| `目标 itemsadder:xxx 需要 ItemsAdder` | 自定义方块/物品的目标永远命中不了 | 安装 ItemsAdder（或 CraftEngine），或改用原版材质名 |

**这些都不影响插件运行**，只是对应奖励会失效——`/ptxa list` 会把这些任务标出来。

---

## 3. 创建第一个任务

任务定义有两条路：**写 YAML 文件**（推荐，能进版本控制、随插件发布）或**游戏内编辑器**
（`/ptxa menu`：新建 / 编辑 / 删除任务，改目标与奖励都在箱子界面里点，文本值在聊天栏输；适合临时加一条或调数值）。
以 YAML 为例，在 `plugins/playerTaskX/quests/` 下新建
`my_first.yml`（**文件名就是任务 id**）：

```yaml
name: <yellow>我的第一个任务
icon: DIAMOND_PICKAXE
category: 每日
type: DAILY
objectives:
  - type: break_block
    properties:
      target: STONE
      amount: 64
rewards:
  - type: command
    properties:
      command: give %player% diamond 3
```

然后 `/ptxa reload`（改文件要重载；游戏内保存则是立即生效）。
字段名与可填值见 [任务目标](objectives) 与 [任务奖励](rewards)，
`quests/` 里随插件铺下的示例就是最好的模板——复制一份改改命名即可。

---

## 4. 让玩家拿到任务

周期任务在玩家**登录时**自动发放，数量由 `periodic.<类型>.amount` 决定（每日默认 3 个）。

想手动验证，用管理员命令给某位玩家重抽一批：

```
/ptxa resetperiod <玩家名>
```

---

## 5. 玩家视角验证

让玩家执行 `/ptx`：

- 打开**周期任务界面**（底部标签切换每日 / 每周 / 每月 / 自定义），看到任务列表与进度
- 点击任务查看**详情**（各目标进度、奖励预览）
- 完成后点击任务**领取奖励**

挖掘指定方块时，屏幕底部的**动作栏**会实时显示进度：

```
挖矿日常 |████░░░░░░░░░░░░░░░░| 20% » 挖掘 13/64
```

全部目标达成时会弹出**标题提醒**，并在聊天栏提示可以领取。

---

## 6. 常见调整

| 想做什么 | 怎么做 |
|---|---|
| 改周期任务数量 | `periodic.<类型>.amount`（如 `periodic.daily.amount`） |
| 改重置时间 | `periodic.<类型>.reset-hour`（早于该时刻算上一个周期）；每周 / 每月另可配锚点日 |
| 让刷新免费 | `periodic.<类型>.refresh-cost: 0`（无需经济插件） |
| 改刷新费用 | `periodic.<类型>.refresh-cost`（按 `refresh-currency` 的顺序自动选可用货币） |
| 关掉动作栏进度 | `progress.actionbar: false` |
| 关掉完成标题 | `progress.title-on-complete: false` |
| 改文案 | 编辑 `lang/zh_CN.yml`，然后 `/ptxa reload` |
| 换存储为 MySQL | `storage.type: MYSQL` 并填写连接信息（需重启）；任务定义、预设与玩家数据一起换库 |
| 用文件管理任务定义 | 写进 `quests/` / `presets/`（只读来源），然后 `/ptxa reload`；不读文件就设 `definitions.read-files: false` |

改完配置执行 `/ptxa reload` 即可生效（存储类型需重启服务器）。

---

## 7. 下一步

- [命令](commands)：完整的命令清单
- [任务目标](objectives)：15 种目标类型与配置字段
- [任务奖励](rewards)：3 种奖励类型与刷新费用的货币
- [变量](placeholders)：在计分板、菜单里展示任务信息
- [常见问题](faq)：任务不涨进度 / 奖励拿不到怎么办
