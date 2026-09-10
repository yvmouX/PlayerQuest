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
  refresh-cost: 1000.0          # 刷新费用（金币；0 = 免费）
  refresh-limit: 3              # 每日最多刷新次数
```

### 抽取规则

任务是**按玩家**抽取的，结果由 `(玩家, 日期, 刷新次数)` 决定——同一玩家同一天重登、
换服、掉线重连都会看到同一批任务，也不会因为反复重连而刷出不同任务。

### `reset-hour` 的语义

早于该时刻的时间算作**前一天**。设为 `4` 时，某日凌晨 3:30 与前一天 23:00 属于同一个周期，
符合「熬夜到凌晨还算今天」的直觉。设为 `0` 则按自然日划分。

### `refresh-cost` 与基础经济

刷新费用**只用服务器的基础经济**（经 Vault 扣除），因此：

- 需要安装 **Vault + 任一经济插件**（EssentialsX、CMI 等）
- 未安装时玩家执行 `/ptx refresh` 会收到「未安装经济插件（Vault），无法扣除刷新费用」，
  而**不是**含义模糊的「货币不足」
- 设为 `0` 则免费刷新，此时无需任何经济插件
- 管理员用 `/ptxa resetdaily` 重抽**不扣费**（它是排障工具，不是消费入口）

> 早期版本还有「任务币」内建货币与点券刷新，现已移除：插件不再引入
> 玩家需要额外理解的第二套货币，费用口径与服务器经济完全一致。

---

## 网页编辑器

```yaml
editor:
  enabled: true
  port: 8080
  token: ""                     # 访问令牌，留空表示不校验
```

浏览器打开 `http://<服务器地址>:8080` 即可。详见 [网页编辑器](editor)。

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
