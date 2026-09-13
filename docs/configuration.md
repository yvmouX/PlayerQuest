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

```yaml
storage:
  type: SQLITE                  # SQLITE 或 MYSQL
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

| 取值 | 说明 |
|---|---|
| `SQLITE` | 默认。零配置，单文件，适合单机或小型服务器 |
| `MYSQL` | 多服共享数据时必须使用；连接池由 HikariCP 管理 |

切换存储类型**不会自动迁移已有数据**，需要自行导出导入。

> 数据库无法打开时插件会被禁用，并在控制台写明原因——避免以「零任务」状态静默运行。

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

## 每日任务

```yaml
daily:
  enabled: true
  pool: []                      # 任务池（任务 id 列表）；留空 = 取所有 type=DAILY 的任务
  amount: 3                     # 每位玩家每日抽取数量
  reset-hour: 4                 # 重置时间（小时，0-23）
  refresh-cost: 1000.0          # 刷新费用；0 = 免费
  refresh-currency:             # 费用货币，按顺序取第一个可用的
    - MONEY
    - POINTS
    - EXP
  refresh-limit: 3              # 每日最多刷新次数
```

### 抽取规则

任务是**按玩家**抽取的，结果由 `(玩家, 日期, 刷新次数)` 决定——同一玩家同一天重登、
换服、掉线重连都会看到同一批任务，也不会因为反复重连而刷出不同任务。

### `reset-hour` 的语义

早于该时刻的时间算作**前一天**。设为 `4` 时，某日凌晨 3:30 与前一天 23:00 属于同一个周期，
符合「熬夜到凌晨还算今天」的直觉。设为 `0` 则按自然日划分。

### `refresh-currency`：货币顺序

这是一个**有序列表**，插件按顺序取第一个可用的货币：

| 取值 | 货币 | 何时可用 |
|---|---|---|
| `MONEY` | 金币 | 装了 Vault + 任一经济插件（EssentialsX、CMI 等） |
| `POINTS` | 点券 | 装了 PlayerPoints |
| `EXP` | 经验 | **总是可用**（原版资源，无需任何插件） |

默认是 `[MONEY, POINTS, EXP]`。常见改法：

```yaml
daily:
  # 优先扣经验，经验不够才考虑金币（活动期不想消耗玩家金币）
  refresh-currency: [EXP, MONEY]
```

```yaml
daily:
  # 只用经验：完全不碰经济插件，也不受 Vault 是否存在影响
  refresh-currency: [EXP]
```

```yaml
daily:
  # 列表里没写的货币视为禁用，因此这里只会扣金币；没装 Vault 时退回默认顺序
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
- 管理员用 `/ptxa resetdaily` 重置**不扣费也不消耗次数**（它是排障工具，不是消费入口）

> 早期版本有「任务币」内建货币，现已移除：插件不再引入玩家需要额外理解的第二套货币，
> 费用只用上表三种之一。

---

## 网页编辑器

```yaml
editor:
  enabled: true
  port: 28080
  token: ""                     # 访问令牌，留空表示不校验
```

浏览器打开 `http://<服务器地址>:28080` 即可。端口被占用时自动 +1 重试（最多 10 个），实际端口以启动日志为准。详见 [网页编辑器](editor)。

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
