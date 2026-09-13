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
├── quests/             # 任务定义，一个任务一个 JSON（definitions.type=JSON 时的默认后端）
├── presets.json        # 目标与奖励预设（编辑器的便利设施）
└── data/
    └── playerTaskX.db  # SQLite 数据库（玩家进度）
```

启动日志里应能看到：

```
[playerTaskX] 玩家数据存储已就绪: SQLite: data/playerTaskX.db
[playerTaskX] Registered command: playertaskx
[playerTaskX] Registered command: playertaskxadmin
[playerTaskX] PlayerTaskX 已启用（12 个任务，14 种目标，5 种奖励）
```

**数据库为空时会自动写入 12 个示例任务**（6 个每日 + 6 个常驻，统一用 `example_` 前缀），
覆盖了大部分目标类型的写法，方便你对照格式、立刻看到效果：每日任务默认每次抽 3 个，
登录 `/ptx` 就能玩；常驻任务在编辑器或 `/ptxa list` 里查看。不需要时逐个删除，
或用 `/ptxa disable example_daily_mine` 之类的命令关掉。

---

## 2. 检查依赖

插件能启动不等于所有功能都可用。装好后看一眼日志，或执行 `/ptxa list`：

| 日志提示 | 含义 | 处理 |
|---|---|---|
| `奖励类型 money 当前不可用: 未安装 Vault` | 金币奖励发不出去（刷新费用会自动改用点券/经验，不受影响） | 安装 Vault + 经济插件，或改用物品/命令/经验奖励 |
| `奖励类型 points 当前不可用: 未安装 PlayerPoints` | 点券奖励发不出去 | 安装 PlayerPoints |
| `网页编辑器未设置访问令牌` | 编辑器端口对所有人开放 | 在配置里设置 `editor.token` |

**这些都不影响插件运行**，只是对应奖励会失效——`/ptxa list` 会把这些任务标出来。

---

## 3. 创建第一个任务

推荐用**网页编辑器**（比手写 YAML 快得多）：

1. 浏览器打开 `http://<你的服务器地址>:28080`
2. 点「新建任务」，填写 id、名称、图标、分类
3. 选类型 `DAILY`（每日任务）
4. 在「目标」区点「添加目标」，选择「挖掘方块」，填入 `target` 与数量
5. 在「奖励」区添加一个「金币」奖励（需要 Vault；没有经济插件时可用「经验」「物品」或「自定义命令」）
6. 保存

若在服务器本机操作，可直接执行 `/ptxa editor` 拿到地址与令牌。

> 编辑器保存的任务**立即写入存储**，玩家侧执行 `/ptxa reload` 前不会生效。

---

## 4. 让玩家拿到任务

每日任务在玩家**登录时**自动发放，数量由 `daily.amount` 决定（默认 3 个）。

想手动验证，用管理员命令给某位玩家重抽一批：

```
/ptxa resetdaily <玩家名>
```

---

## 5. 玩家视角验证

让玩家执行 `/ptx`：

- 打开**每日任务界面**，看到任务列表与进度
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
| 改每日任务数量 | `daily.amount` |
| 改重置时间 | `daily.reset-hour`（早于该时刻算前一天） |
| 让刷新免费 | `daily.refresh-cost: 0`（无需经济插件） |
| 改刷新费用 | `daily.refresh-cost`（按「金币 → 点券 → 经验」自动选可用货币） |
| 关掉动作栏进度 | `progress.actionbar: false` |
| 关掉完成标题 | `progress.title-on-complete: false` |
| 改文案 | 编辑 `lang/zh_CN.yml`，然后 `/ptxa reload` |
| 换存储为 MySQL | `storage.type: MYSQL` 并填写连接信息（需重启）；任务定义侧另有 `definitions.type` |

改完配置执行 `/ptxa reload` 即可生效（存储类型与编辑器端口需重启服务器）。

---

## 7. 下一步

- [命令](commands)：完整的命令清单
- [任务目标](objectives)：14 种目标类型与配置字段
- [任务奖励](rewards)：5 种奖励类型
- [网页编辑器](editor)：批量管理与导入导出
- [变量](placeholders)：在计分板、菜单里展示任务信息
- [常见问题](faq)：任务不涨进度 / 奖励拿不到怎么办
