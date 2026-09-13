# 任务目标

一个任务可以有**任意多个目标**，全部完成后任务才算完成。

目标 = `类型` + `配置`。类型决定「监听什么行为」，配置决定「具体要什么、要多少」。

所有目标类型都支持 `amount`（所需数量），在网页编辑器与 GUI 里会自动出现在表单末尾。

---

## 目标类型一览

| id | 显示名 | 监听的行为 | 主要配置 |
|---|---|---|---|
| `break_block` | 挖掘 | 破坏方块 | `target` 方块材质 |
| `place_block` | 放置 | 放置方块 | `target` 方块材质 |
| `craft` | 合成 | 合成物品 | `target` 产物材质 |
| `consume` | 消耗 | 食用/饮用药水 | `target` 物品材质 |
| `fish` | 垂钓 | 钓上东西 | `target` 物品材质（可空） |
| `kill` | 击杀 | 击杀生物或玩家 | `target` 实体类型 |
| `enchant` | 附魔 | 在附魔台附魔 | `target` 附魔名（可空） |
| `shear` | 剪切 | 剪羊毛 | `target` 实体类型（可空） |
| `breed` | 繁殖 | 喂养繁殖动物 | `target` 实体类型（可空） |
| `tame` | 驯服 | 驯服动物 | `target` 实体类型 |
| `interact` | 交互 | 右键方块或实体 | `target` 方块/实体 + `mode` 方式 |
| `chat` | 发言 | 在聊天栏发言 | `target` 关键词（可空） |
| `submit` | 提交 | ——（尚未实现，见下） | `target` 物品材质 |
| `command` | 执行命令 | 执行命令 | `target` 命令名 |

---

## 通用规则

### `amount`

完成该目标所需的次数，默认 `1`。

### `target` 的匹配方式

- **大小写不敏感**：`diamond_ore` 与 `DIAMOND_ORE` 等价
- **支持多值**：用英文逗号分隔，例如 `DIAMOND_ORE,DEEPSLATE_DIAMOND_ORE` 表示两种都算
- **通配**：留空或写 `*` 表示「任意」，例如 `kill` 的 `target` 留空表示击杀任何生物

### 数量的计算方式

| 行为 | 计数 |
|---|---|
| 挖掘 / 放置 | 每次 1 个 |
| 合成 | 按**产物数量**计（合成 4 个火把 = +4） |
| 消耗 | 每次 1 个 |
| 击杀 / 垂钓 / 附魔 / 剪切 / 繁殖 / 驯服 | 每次 1 次 |

---

## 各类型详解

### `break_block` 挖掘

```yaml
type: break_block
target: DIAMOND_ORE     # 方块材质名
amount: 64
```

> 草、花、火把这类「一点就碎」（硬度为 0）的方块，左键点击即算挖掘；
> 其余方块需要真正破坏。这是为了区分「挖」与「对着方块右键交互」。

### `place_block` 放置

```yaml
type: place_block
target: STONE
amount: 64
```

### `craft` 合成

```yaml
type: craft
target: DIAMOND          # 产物材质名
amount: 1
```

> 一次合成多个产物（如火把）会按产物数量累加。

### `consume` 消耗

```yaml
type: consume
target: BREAD            # 食物或药水
amount: 10
```

### `fish` 垂钓

```yaml
type: fish
target: ""               # 留空 = 钓到任何东西都算；也可写 COD / SALMON
amount: 10
```

### `kill` 击杀

```yaml
type: kill
target: ZOMBIE           # 实体类型名
amount: 20
```

常用实体类型名：`ZOMBIE` `SKELETON` `CREEPER` `SPIDER` `ENDERMAN` `BLAZE` `WITHER_SKELETON` `PIG` `COW`。
击杀玩家也算，写 `PLAYER`。

### `enchant` 附魔

```yaml
type: enchant
target: ""               # 留空 = 任意附魔；也可写 sharpness / efficiency
amount: 3
```

> 一次附魔附加多个魔咒时按 1 次计。`target` 用附魔的英文键名（小写）。

### `shear` 剪切

```yaml
type: shear
target: SHEEP             # 留空 = 剪任何可剪的生物
amount: 5
```

### `breed` 繁殖

```yaml
type: breed
target: COW               # 留空 = 繁殖任何动物
amount: 5
```

### `tame` 驯服

```yaml
type: tame
target: WOLF              # WOLF / CAT / PARROT / HORSE ...
amount: 1
```

### `interact` 交互

```yaml
type: interact
target: CHEST             # 方块或实体类型，留空 = 任意
mode: ANY                 # 交互方式
amount: 5
```

`mode` 取值：

| 取值 | 含义 |
|---|---|
| `ANY` | 不限（默认） |
| `LEFT_CLICK_BLOCK` | 左键点击方块 |
| `RIGHT_CLICK_BLOCK` | 右键点击方块 |
| `RIGHT_CLICK_ENTITY` | 右键点击实体 |
| `LEFT_CLICK_ENTITY` | 左键点击实体 |

### `chat` 发言

```yaml
type: chat
target: "你好"            # 消息需包含的关键词，留空 = 任意发言
amount: 3
```

- **包含匹配**（不是相等匹配）：消息里出现关键词即算
- 逗号分隔可写多个关键词，命中任意一个即可
- 大小写不敏感

> 该事件是异步的，插件只做纯内存的进度判定，不会在异步线程里碰世界数据。

### `submit` 提交

```yaml
type: submit
target: DIAMOND           # 需要提交的物品材质
amount: 32
```

> ⚠️ **该类型目前是预留的，还做不动。** 没有任何监听器推 `Trigger.SUBMIT`，
> GUI 里也还没有提交入口，因此进度永远不会增加——配了它的任务会一直停在 0。
> 类型定义与校验仍然保留，等提交界面落地后即可使用；在那之前请不要在正式任务里使用。
>
> 与其他类型不同，提交**不会自动扣除**背包物品——它需要玩家主动在任务界面提交。
> 详细的提交界面见 [网页编辑器](editor) 与后续版本说明。

### `command` 执行命令

```yaml
type: command
target: home              # 命令名，不含前导斜杠
amount: 3
```

- 只匹配**命令本身**，不含参数（`/home base` 只算 `home`）
- 留空或 `*` 表示执行任何命令都算

---

## 组合示例

**「早起的鸟儿」**：发言打招呼 + 钓 5 条鱼

```yaml
objectives:
  - type: chat
    target: "早上好"
    amount: 1
  - type: fish
    target: ""
    amount: 5
```

**「矿工的一天」**：挖两种矿石 + 合成火把

```yaml
objectives:
  - type: break_block
    target: IRON_ORE,DEEPSLATE_IRON_ORE
    amount: 32
  - type: break_block
    target: COAL_ORE,DEEPSLATE_COAL_ORE
    amount: 64
  - type: craft
    target: TORCH
    amount: 16
```

**「牧场主」**：繁殖 + 驯服 + 剪羊毛

```yaml
objectives:
  - type: breed
    target: COW
    amount: 5
  - type: tame
    target: WOLF
    amount: 1
  - type: shear
    target: SHEEP
    amount: 3
```
