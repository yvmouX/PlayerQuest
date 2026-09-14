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
| `custom_fish` | 自定义钓鱼 | 钓到 CustomFishing 的自定义鱼 | `target` 鱼 id（可空）+ `min-size` |
| `kill` | 击杀 | 击杀生物或玩家 | `target` 实体类型（支持 `mythic:` 前缀） |
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
- **可以写其它插件的自定义内容**（见下）

### 每个目标能填什么：值域

每个目标类型的 `target` 都有自己的**值域**（它能接受哪一类东西），编辑器只列值域里的候选，
服务端也按同一份声明校验。写错值域的后果是「配了却永远不命中」，因此这一层刻意做得很紧：

| 目标 | 值域 | 因此选不到 / 会被标红 |
|---|---|---|
| `break_block` | 方块 | 苹果、面包（物品不是方块） |
| `place_block` | 可放置的方块 | 只有方块形态、拿不到手里的材质 |
| `craft` / `consume` / `submit` / `fish` | 物品 | —— |
| `kill` | 活体生物 | 箭、船这类非生物实体 |
| `shear` | 能剪毛的生物 | 猪、僵尸 |
| `breed` | 能繁殖的动物 | 僵尸、村民 |
| `tame` | 能驯服的生物 | 牛、猪 |
| `interact` | 方块**或**实体 | 苹果（既不是方块也不是实体） |
| `enchant` | 原版附魔 | 拼错的附魔名 |
| `custom_fish` | CustomFishing 的战利品 | 写错的鱼 id（装了 CustomFishing 时） |

- 值域由**服务端当前的枚举与接口**算出来（方块看 `Material.isBlock()`，
  可剪毛看实体类是不是 `Shearable`…），因此新版本新增的内容自动进入对应值域，
  不需要维护清单；
- 留空、`*`、以及带命名空间的自定义 id（`itemsadder:` / `craftengine:` / `mythic:`）一律放行：
  前两者表示「任意」，后者离线判断不了（插件没装时另有专门的提示）；
- 值域只筛「哪一类东西」，不判断「这个 id 到底存不存在于你那台服务器」——
  自定义内容就是典型例子。

### 自定义内容（ItemsAdder / CraftEngine）

装了 **ItemsAdder** 或 **CraftEngine** 时，方块与物品类目标可以直接按它们的自定义 id 写：

```yaml
type: break_block
target: itemsadder:myitems:ruby_block    # 前缀 + 插件里的命名空间 id
amount: 64
```

- 支持的**前缀**是 `itemsadder:` 与 `craftengine:`，后面跟插件自己的 `命名空间:id`；
- **裸 id 也能写**（`target: myitems:ruby_block`）：它会与两家比对，命中任意一家都算——
  同一个 id 被两家都定义时不必纠结写哪个前缀；
- 一个 `target` 里可以混写，例如 `target: DIAMOND_ORE,itemsadder:myitems:ruby_block`；
- 自定义**方块**在服务端其实就是某个原版方块（靠方块状态与资源包呈现成别的样子），
  因此挖掘 / 放置 / 交互这些目标照常触发，插件会把自定义 id 与材质名一起参与匹配；
  **自定义物品**同理（合成 / 消耗 / 附魔 / 垂钓的产物）。
- 编辑器里的材质选择器会把这些 id 一并列出（显示成 `ItemsAdder: myitems:ruby_block`），
  搜索「itemsadder」或「craftengine」可以一次筛出来；
- 没装对应插件、或接口签名对不上时，这类目标**永远命中不了**：编辑器会标红，
  `/ptxa list` 与启动日志也会写明原因（与 `mythic:` 目标同一套处理）。

> 自定义**家具**（furniture）不在支持范围内：那是实体而不是方块，需要各自的交互事件。

### 数量的计算方式

| 行为 | 计数 |
|---|---|
| 挖掘 / 放置 | 每次 1 个 |
| 合成 | 按**产物数量**计（合成 4 个火把 = +4） |
| 消耗 | 每次 1 个 |
| 击杀 / 垂钓 / 附魔 / 剪切 / 繁殖 / 驯服 | 每次 1 次 |
| 自定义钓鱼 | 按**钓获物堆大小**计 |

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

> 装 CustomFishing 时，「钓到它配置的自定义鱼」请用下面的 `custom_fish`：
> 那种钓获由 CustomFishing 自己的战利品表产出，本类型（原版垂钓事件）看不到它。

### `custom_fish` 自定义钓鱼

**需要服务端安装 [CustomFishing](https://www.spigotmc.org/resources/100088/)。**
没装时该类型在编辑器里会标为「不可用」，配了也不会涨进度。

```yaml
type: custom_fish
target: my_custom_fish    # CustomFishing 战利品表里的 id；留空或 * = 任意自定义鱼
min-size: 30              # 只统计 ≥30 的钓获；0 或缺省 = 不限尺寸
amount: 3
```

- `target` 就是你在 CustomFishing 配置里给那条鱼起的 id（大小写不敏感）；
  编辑器里这个字段是**选择器**，直接列出 CustomFishing 已注册的战利品
  （显示的是配置里的 `nick`），也可以手打——没装 CustomFishing 时列表为空，但照样能填
- `min-size` 对应它的钓获尺寸；**没有尺寸信息的钓获不算达标**，
  因此只有确实需要「大物」时才填它
- 数量按钓获物堆大小计（钓上一组就记一组）

> 本类型与原版 `fish` 是两条独立的链路：前者监听 CustomFishing 的战利品生成事件，
> 后者监听原版 `PlayerFishEvent`。想「任意钓获都算」时可以两个目标都配，
> 但同一个动作不会被计两次。

### `kill` 击杀

```yaml
type: kill
target: ZOMBIE           # 实体类型名
amount: 20
```

常用实体类型名：`ZOMBIE` `SKELETON` `CREEPER` `SPIDER` `ENDERMAN` `BLAZE` `WITHER_SKELETON` `PIG` `COW`。
击杀玩家也算，写 `PLAYER`。

**击杀 MythicMobs 的自定义怪**：加上 `mythic:` 前缀写它的内部名（`/mm mobs list` 里那个 id）。

```yaml
type: kill
target: mythic:SkeletalKnight     # 需要服务端安装 MythicMobs 5.x
amount: 10
```

- 可以和原版名混写：`ZOMBIE,mythic:SkeletalKnight` 表示两者都算
- 自定义怪**同时**算作它的原版类型（上例的一只自定义僵尸也能推进 `target: ZOMBIE` 的任务），
  但一次击杀只计 1 次，`target` 留空的任务不会因此翻倍
- 编辑器里可以直接从实体选择器里搜 `mythic` 挑怪（装了 MythicMobs 时才会出现这些条目）
- 没装 MythicMobs（或装的是 4.x）时，含 `mythic:` 的目标会被标成校验问题——
  它们永远命中不了

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
- type: chat
  target: "早上好"
  amount: 1
- type: fish
  target: ""
  amount: 5
```

**「矿工的一天」**：挖两种矿石 + 合成火把

```yaml
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
