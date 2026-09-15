# 任务奖励

一个任务可以配置**任意多个奖励**，玩家领取时全部发放。

奖励 = `类型` + `配置`。若某个奖励发放失败，**不影响其它奖励**——玩家不会因为一个配置错误
（比如 Vault 没装）而连其它奖励也拿不到。

---

## 奖励类型一览

| id | 显示名 | 依赖 | 主要配置 |
|---|---|---|---|
| `money` | 金币 | 任一经济插件（EssentialsX、CMI 等，经 Vault 的 `Economy` 服务） | `amount` |
| `points` | 点券 | PlayerPoints | `amount` |
| `command` | 自定义命令 | 无 | `command` `as-player` |

> 依赖缺失时该奖励类型会被标记为「不可用」：启动日志会警告，
> `/ptxa list` 与编辑器（`/ptxa menu`）里都会显示具体原因，不会静默失效。
> 注意这只影响**奖励**；刷新费用的货币是另一回事（见下），也只看这两种货币。

## 为什么没有「物品」与「经验」奖励

这两样用**命令奖励**就够了，而且更灵活：

```yaml
type: command
command: "give %player% diamond 3"        # 物品
```

```yaml
type: command
command: "xp add %player% 200 points"     # 经验点
```

做成插件自己的奖励类型，就得把 `material` / `amount` / `name` / `lore`（以及将来的附魔、
NBT、自定义模型……）在插件里重做一遍，而 `/give` 早就把这些做完了；命令还能顺手用上其它
插件的东西（点券、钥匙、称号）。因此奖励只保留三种，物品与经验的发放交给命令。

> 经验与物品都**不是**货币也不是奖励类型：发它们用命令奖励就够了（见上），
> 刷新费用也不认经验（见下面「刷新费用的货币」）。

---

## `money` 金币

```yaml
type: money
amount: 1000
```

经 Vault 的 `Economy` 服务发放：光装 Vault（接口层）不够，还要有一个经济插件在册（EssentialsX、CMI 等）。判据是服务注册，不是插件名。
金额支持小数。

它也是刷新周期任务的**首选**费用来源（见 `refresh-currency`）：
装了经济插件就扣金币，没装则自动改用点券；两者都没有时刷新不可用。

---

## `points` 点券

```yaml
type: points
amount: 100
```

经 PlayerPoints 发放，只接受整数。

它同样是刷新费用的候选货币之一。

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

**简单的每日奖励**：金币 + 3 个经验瓶

```yaml
rewards:
  - type: money
    amount: 500
  - type: command
    command: "give %player% experience_bottle 3"
```

**稀有任务奖励**：点券 + 一把带名字的剑 + 全服公告

```yaml
rewards:
  - type: points
    amount: 500
  - type: command
    command: "give %player% netherite_ingot 1"
  - type: command
    command: "broadcast <yellow>%player%</yellow> 完成了每周挑战！"
    as-player: false
```

> 公告类命令里的颜色标签由**被执行的那个插件**解析，因此写法取决于目标插件，
> 上面的 `<yellow>` 是 MiniMessage 写法；若目标插件只认 `&`，就写 `&e`。
>
> 要给物品加名字、描述、附魔，直接写在 `give` 命令里（`/give` 支持组件参数），
> 或换成 `item` 系命令型插件（如 `crate give`、`items give`）——这些都只是命令奖励的参数。

---

## 刷新费用的货币

刷新周期任务要花钱（`refresh-cost`），钱从哪两种来源扣由 `refresh-currency` 决定（默认「金币 → 点券」）：

| 值 | 货币 | 可用性 |
|---|---|---|
| `MONEY` | 金币 | 需要任一经济插件在册 |
| `POINTS` | 点券 | 需要 PlayerPoints |

> 经验**不在**这条链上：它需要读写玩家的等级与经验进度，等于把一件小事做成一等公民。
> 两种货币一个都不可用时，刷新直接不可用并明确提示（「本服务器没有可用的货币
> （需要经济插件或 PlayerPoints）」），不会白送刷新——那会让配错的 `refresh-cost` 看不出来。
>
> 想把刷新做成免费的，就把 `refresh-cost` 设为 `0`：那时完全不涉及货币，两个插件都不装也能刷。

---

## 关于「领取」与「发放」

| 场景 | 行为 |
|---|---|
| 玩家在 GUI 或 `/ptx claim` 领取 | 发放全部奖励，任务状态变为**已领取**，不可重复领取 |
| 管理员 `/ptxa grant <玩家> <任务>` | 只发放奖励，**不改变任务状态**（用于补发） |

任务完成后**不会自动发放**奖励，需要玩家主动领取——这样玩家能清楚地知道
「任务完成了」与「奖励到手了」是两件事，也避免离线时奖励无处可去。
