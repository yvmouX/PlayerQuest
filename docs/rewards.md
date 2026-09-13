# 任务奖励

一个任务可以配置**任意多个奖励**，玩家领取时全部发放。

奖励 = `类型` + `配置`。若某个奖励发放失败，**不影响其它奖励**——玩家不会因为一个配置错误
（比如 Vault 没装）而连其它奖励也拿不到。

---

## 奖励类型一览

| id | 显示名 | 依赖 | 主要配置 |
|---|---|---|---|
| `money` | 金币 | Vault + 经济插件 | `amount` |
| `points` | 点券 | PlayerPoints | `amount` |
| `exp` | 经验 | 无（原版） | `amount` |
| `item` | 物品 | 无 | `material` `amount` `name` `lore` |
| `command` | 自定义命令 | 无 | `command` `as-player` |

> 依赖缺失时该奖励类型会被标记为「不可用」：启动日志会警告，
> 管理员界面与网页编辑器里都会显示具体原因，不会静默失效。
> 注意这只影响**奖励**；刷新费用的货币是自动兜底的（见下），不受影响。

---

## `money` 金币

```yaml
type: money
amount: 1000
```

经 Vault 发放，需要服务器装有 Vault 与任一经济插件（EssentialsX、CMI 等）。
金额支持小数。

它也是刷新每日任务的**首选**费用来源（见 `daily.refresh-cost`）：
装了经济插件就扣金币，没装则自动改用点券或经验。

---

## `points` 点券

```yaml
type: points
amount: 100
```

经 PlayerPoints 发放，只接受整数。

---

## `exp` 经验

```yaml
type: exp
amount: 100
```

发放经验**点数**（不是等级），内部用 `giveExp` 处理，因此跨级、升级特效都正常。

- 无需任何依赖，原版资源
- 同时是刷新费用的**最终兜底货币**：服务器既没装 Vault 也没装 PlayerPoints 时，
  刷新费用从经验里扣（扣除逻辑与原版一致：先降当前等级，不足再从上一级扣）
- 经验余额按原版公式精确计算，不依赖已废弃的 `getTotalExperience()`

---

## `item` 物品

```yaml
type: item
material: DIAMOND
amount: 5
name: ""                              # 留空 = 用物品默认名
lore: ""                              # 多行用 | 分隔
```

- `material` 用 Bukkit 材质名（如 `DIAMOND` `GOLDEN_APPLE` `NETHERITE_INGOT`）
- `name` 与 `lore` 支持 MiniMessage 标签与 `&` 颜色码（两者可混排，见 [配置](configuration)）
- 背包放不下时，多出的物品会**掉落在玩家脚下**，而不是凭空消失

示例：带名称与描述的奖励物品

```yaml
type: item
material: NETHERITE_SWORD
amount: 1
name: "<gold>勇者之剑"
lore: "<gray>完成每日任务的证明|<yellow>攻击力 +10"
```

---

## `command` 自定义命令

```yaml
type: command
command: "give %player% diamond 1"
as-player: false
```

- 命令**不含前导斜杠**
- `%player%` 会替换为玩家名（也支持 `{player}`）
- `as-player: false`（默认）由**控制台**执行，因此不受玩家权限限制
- `as-player: true` 以玩家身份执行，会受其权限限制

常见用法：

| 目的 | 命令示例 |
|---|---|
| 给物品 | `give %player% diamond 1` |
| 给经验 | `xp add %player% 100 points` |
| 执行其它插件命令 | `crate give %player% daily 1` |
| 发送公告 | `broadcast %player% 完成了每日任务！` |

---

## 组合示例

**简单的每日奖励**：金币 + 一点物品

```yaml
rewards:
  - type: money
    amount: 500
  - type: item
    material: EXPERIENCE_BOTTLE
    amount: 3
```

**稀有任务奖励**：特殊物品 + 点券 + 全服公告

```yaml
rewards:
  - type: item
    material: NETHERITE_INGOT
    amount: 1
    name: "<aqua>每周之星"
  - type: points
    amount: 500
  - type: command
    command: "broadcast <yellow>%player%</yellow> 完成了每周挑战！"
    as-player: false
```

> 公告类命令里的颜色标签由**被执行的那个插件**解析，因此写法取决于目标插件，
> 上面的 `<yellow>` 是 MiniMessage 写法；若目标插件只认 `&`，就写 `&e`。

---

## 关于「领取」与「发放」

| 场景 | 行为 |
|---|---|
| 玩家在 GUI 或 `/ptx claim` 领取 | 发放全部奖励，任务状态变为**已领取**，不可重复领取 |
| 管理员 `/ptxa grant <玩家> <任务>` | 只发放奖励，**不改变任务状态**（用于补发） |

任务完成后**不会自动发放**奖励，需要玩家主动领取——这样玩家能清楚地知道
「任务完成了」与「奖励到手了」是两件事，也避免离线时奖励无处可去。
