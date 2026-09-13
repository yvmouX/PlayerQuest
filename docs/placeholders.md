# 变量（PlaceholderAPI）

插件注册了标识符为 `playertaskx` 的 PlaceholderAPI 扩展。所有变量都以 `%playertaskx_` 开头。

> 需要服务器安装 [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)。
> 未安装时插件会静默跳过注册，其它功能不受影响。

---

## 当前任务

| 变量 | 返回 |
|---|---|
| `%playertaskx_active%` | 进行中的任务总数（含普通任务） |
| `%playertaskx_claimable%` | 可领取奖励的任务数量 |

## 每日任务

| 变量 | 返回 |
|---|---|
| `%playertaskx_daily_count%` | 今日每日任务总数 |
| `%playertaskx_daily_active%` | 其中进行中的数量 |
| `%playertaskx_daily_completed%` | 其中已完成待领取的数量 |
| `%playertaskx_daily_claimed%` | 其中已领取的数量 |
| `%playertaskx_daily_refresh_left%` | 今日剩余刷新次数 |
| `%playertaskx_daily_refresh_cost%` | 刷新费用（数字） |

## 指定任务的进度

把 `<任务id>` 替换为任务 id（例如 `daily_mine`）：

| 变量 | 返回 | 示例 |
|---|---|---|
| `%playertaskx_quest_name_<任务id>%` | 任务显示名（已去格式） | `挖矿日常` |
| `%playertaskx_quest_progress_<任务id>%` | 总体进度 | `13/64` |
| `%playertaskx_quest_percent_<任务id>%` | 完成百分比 | `20` |
| `%playertaskx_quest_status_<任务id>%` | 状态文案 | `进行中` |
| `%playertaskx_quest_type_<任务id>%` | 任务类型 | `DAILY` |
| `%playertaskx_quest_id_<任务id>%` | 任务 id | `daily_mine` |
| `%playertaskx_quest_category_<任务id>%` | 任务分类（未设置时为空） | `每日` |

> 玩家没有该任务的记录时，进度类变量返回 `0/<总数>` 或 `0`，状态返回空字符串。

---

## 使用示例

### 计分板（以 TAB / Scoreboard 类插件为例）

```
&e每日任务 &f%playertaskx_daily_completed%&7/&f%playertaskx_daily_count%
&7待领取: &a%playertaskx_claimable%
&7刷新次数: &f%playertaskx_daily_refresh_left%
```

### 聊天前缀：显示可领取提醒

```
&6[任务] &f你有 &e%playertaskx_claimable% &f个奖励可以领取！
```

### 菜单插件里的任务入口

```
&e每日任务 &7(%playertaskx_daily_active% 进行中)
&7点击打开
```

---

## 注意事项

- 变量的刷新跟随调用方（计分板插件）的刷新频率，插件本身不做推送
- 任务 id 区分大小写，需与配置中完全一致
- 若变量显示为原文（如 `%playertaskx_active%`），说明 PlaceholderAPI 未安装或扩展未加载，
  检查启动日志是否有 `已注册 PlaceholderAPI 变量` 这一行
