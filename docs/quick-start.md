# 快速开始

## 1. 安装

把 `playerTaskX-<版本>-all.jar` 放进 `plugins/` 目录，重启服务器。

> **务必使用 `-all.jar`**（shadowJar 产物）。同目录下还有一个体积为 0 的普通 jar 是空壳，
> 装它会导致插件无法加载。

首次启动会生成：

```
plugins/playerTaskX/
├── config.yml          # 主配置
├── commands.yml        # 命令配置（YLib 生成，可覆盖权限/别名/启用状态）
├── lang/               # 语言文件，可直接编辑
│   ├── zh_CN.yml
│   └── en.yml
├── editor/
│   └── zh_cn.json      # 编辑器图标列表的中文译名（自动下载；离线服可手动放一份）
├── quests/             # YAML 任务定义（只读来源，空着时会铺一份示例）
├── presets/            # YAML 目标/奖励预设（同上）
└── data/
    └── playerTaskX.db  # SQLite 数据库（任务定义、预设与玩家进度都在这里）
```

任务写在 `quests/` / `presets/` 里就能被插件读到，但那份定义**只读**（库优先），
游戏内与编辑器只修改数据库里的定义；详见
[配置 · 用 YAML 文件写定义](configuration?id=用-yaml-文件写定义)。

启动日志里应能看到：

```
[playerTaskX] quests/ 是空的，已写入 12 个示例任务文件（只读来源，可自由删改）
[playerTaskX] presets/ 是空的，已写入 11 个示例预设文件（只读来源，可自由删改）
[playerTaskX] 存储已就绪: SQLite: data/playerTaskX.db（任务定义、预设与玩家数据在同一库）
[playerTaskX] Registered command: playertaskx
[playerTaskX] Registered command: playertaskxadmin
[playerTaskX] PlayerTaskX 已启用（24 个任务，15 种目标，5 种奖励）
```

示例一共有两套，**都不影响正常使用，可随时清掉**：

- **数据库里 12 个**（6 个每日 + 6 个常驻，`example_` 前缀）：可以在编辑器里随便改，
  也可以在游戏里用 `/ptxa disable example_daily_mine` 之类的命令关掉；
- **`quests/` 里 12 个 + `presets/` 里 11 个**（`example_file_` 前缀）：用来对照文件格式，
  只读、改文件才生效；删掉就不会再补（目录非空即不再铺）。

两套覆盖了大部分目标类型的写法。每日任务默认每次抽 3 个，登录 `/ptx` 就能玩；
常驻任务在编辑器或 `/ptxa list` 里查看。

---

## 2. 检查依赖

插件能启动不等于所有功能都可用。装好后看一眼日志，或执行 `/ptxa list`：

| 日志提示 | 含义 | 处理 |
|---|---|---|
| `奖励类型 money 当前不可用: 没有可用的经济服务` | 金币奖励发不出去（刷新费用会自动改用点券/经验，不受影响） | 装一个经济插件（Vault / VaultUnlocked + EssentialsX / CMI 等），或改用物品/命令/经验奖励 |
| `奖励类型 points 当前不可用: 未安装 PlayerPoints` | 点券奖励发不出去 | 安装 PlayerPoints |
| `目标类型 custom_fish 不可用（未安装 CustomFishing）` | 「自定义钓鱼」目标不会涨进度 | 安装 [CustomFishing](https://www.spigotmc.org/resources/100088/)，或改用原版 `fish` 目标 |
| `目标 mythic:xxx 需要 MythicMobs 5.x` | 击杀自定义怪的目标永远命中不了 | 安装 MythicMobs 5.x（4.x 不支持 1.21+），或改用原版实体名 |
| `目标 itemsadder:xxx 需要 ItemsAdder` | 自定义方块/物品的目标永远命中不了 | 安装 ItemsAdder（或 CraftEngine），或改用原版材质名 |
| `网页编辑器未设置访问令牌` | 编辑器端口对所有人开放 | 在配置里设置 `editor.token` |

**这些都不影响插件运行**，只是对应奖励会失效——`/ptxa list` 会把这些任务标出来。

---

## 3. 创建第一个任务

推荐用**网页编辑器**（比手写 YAML 快得多）：

1. 浏览器打开 `http://<你的服务器地址>:28080`
2. 点「新建任务」，填写 id、名称、图标、分类
3. 选类型 `DAILY`（每日任务）
4. 在「目标」区点「添加目标」，选择「挖掘方块」，填入 `target` 与数量
5. 在「奖励」区添加一个「金币」奖励（需要经济插件；没有时可用「经验」「物品」或「自定义命令」）
6. 保存

若在服务器本机操作，可直接执行 `/ptxa editor` 拿到地址与令牌。

> 编辑器保存的任务**立即生效**（写库的同时更新内存定义与玩家进度索引），不需要重载。

> 习惯手写配置的话，任务编辑页右上角可以切到 **YAML**：字段名与「导出 YAML」一致，
> 边写边生效，保存仍走同一个按钮。详见 [网页编辑器](editor?id=yaml-视图手写任务配置)。
> 想让任务以文件形态进版本控制，把它们放进 `plugins/playerTaskX/quests/`，
> 或从编辑器「导出 YAML」再自行拆分（见 [配置](configuration?id=用-yaml-文件写定义)）。

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

改完配置执行 `/ptxa reload` 即可生效（存储类型与编辑器端口需重启服务器）。

---

## 7. 下一步

- [命令](commands)：完整的命令清单
- [任务目标](objectives)：15 种目标类型与配置字段
- [任务奖励](rewards)：5 种奖励类型
- [网页编辑器](editor)：批量管理与导入导出
- [变量](placeholders)：在计分板、菜单里展示任务信息
- [常见问题](faq)：任务不涨进度 / 奖励拿不到怎么办
