# 命令

插件注册两条命令：

| 命令 | 别名 | 权限 | 用途 |
|---|---|---|---|
| `/playertaskx` | `/ptx` | 无 | 玩家命令 |
| `/playertaskxadmin` | `/ptxa` | `playertaskx.admin`（默认 OP） | 管理员命令 |

所有命令都支持 **Tab 补全**（包括任务 id）。

---

## 玩家命令

| 命令 | 说明 |
|---|---|
| `/ptx` | 打开每日任务界面。控制台执行时改为显示帮助 |
| `/ptx menu` | 同 `/ptx` |
| `/ptx gui` | 同 `/ptx`（习惯叫法） |
| `/ptx help` | 显示玩家命令清单 |
| `/ptx list` | 在聊天里列出当前任务与完成度 |
| `/ptx progress` | 查看当前任务的进度详情 |
| `/ptx claim <任务id>` | 领取已完成任务的奖励 |
| `/ptx refresh` | 刷新每日任务（按配置的货币顺序扣费：金币 → 点券 → 经验，第一个可用的生效） |

### 例子

```
/ptx list                    # 看看今天有哪些任务
/ptx claim daily_mine        # 领取「挖矿日常」的奖励
/ptx refresh                 # 对今天的任务不满意？花钱换一批
```

> **刷新是个人行为**：只影响自己那一份每日任务，且有次数上限（默认 3 次/天）。
> 刷新后拿到的是**另一批**任务，不是同一批。

---

## 管理员命令

需要权限 `playertaskx.admin`（默认给予 OP）。

### 任务管理

| 命令 | 说明 |
|---|---|
| `/ptxa help` | 显示管理员命令清单 |
| `/ptxa list` | 列出全部任务，并标出配置有问题的地方 |
| `/ptxa info <任务id>` | 查看单个任务的完整信息（前置、目标、奖励、费用、校验结果） |
| `/ptxa enable <任务id>` | 启用任务 |
| `/ptxa disable <任务id>` | 禁用任务（不再被抽取，也不再累计进度） |
| `/ptxa reload` | 从数据库重新载入任务定义 |

### 界面与编辑

| 命令 | 说明 |
|---|---|
| `/ptxa menu` | 打开任务管理界面（分页浏览、切换启用、查看校验问题） |
| `/ptxa editor` | 显示网页编辑器的访问地址与令牌 |

### 调试与修复

| 命令 | 说明 |
|---|---|
| `/ptxa setobjective <玩家> <任务id> <目标序号> <进度>` | 直接设定某个目标的进度 |
| `/ptxa grant <玩家> <任务id>` | 直接发放任务奖励（**不改变任务状态**） |
| `/ptxa resetdaily <玩家>` | 重置该玩家的每日任务：**不扣费、不消耗刷新次数**（排障工具） |

**目标序号**从 `0` 开始，对应任务里目标的排列顺序，可用 `/ptxa info <id>` 查看。

### 例子

```
/ptxa list                              # 有哪些任务、哪些配置有问题
/ptxa info daily_mine                   # 这个任务的具体内容
/ptxa setobjective Steve daily_mine 0 64   # 把 Steve 的第一个目标直接设为 64
/ptxa grant Steve daily_mine            # 补发一次奖励（比如玩家反馈没收到）
/ptxa resetdaily Steve                      # 帮 Steve 换一批每日任务
```

---

## 命名约定

子命令一律用能表达真实动作的词，避免歧义：

| 子命令 | 为什么用这个名字（而不是更顺手的那个） |
|---|---|
| `grant` | `give` 在插件语境里通常指「给物品」，而这里发的是任务奖励 |
| `setobjective` | `progress` 看起来像「查看进度」，实际是写操作 |
| `resetdaily` | 与玩家的 `refresh` 区分：管理员重置不收费也不消耗次数 |

**不保留历史别名**：项目尚未发布，多一个入口就多一处要维护的文档与校验。

帮助清单由 `@Command` / `@SubCommand` 的注解自动生成（`CommandHelp.ofAnnotations`），
因此不存在「代码改了、帮助还写着旧名字」的情况。

---

## 权限节点

| 节点 | 默认 | 说明 |
|---|---|---|
| `playertaskx.admin` | OP | 管理员命令的总开关 |

管理员命令的权限挂在根节点上，所有子命令自动继承，不需要逐个分配。

玩家命令**没有权限门槛**——玩家只能操作自己的任务数据，互相之间不可见。

> 若要在权限插件里精细控制，可给特定玩家单独授予 `playertaskx.admin`。
> 注意：**一旦 `commands.yml` 里写入了 `permission` 字段，就会覆盖代码里的注解值**，
> 修改权限请以 `plugins/playerTaskX/commands.yml` 为准。
