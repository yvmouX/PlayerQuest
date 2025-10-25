# 开发文档

---

## 快速跳转

- [存储](#存储)
- [任务格式](#任务格式)
  - [任务类型](#任务类型-type)
  - [任务目标](#任务目标-target)
  - [任务条件](#任务条件-condition)
  - [任务触发器](#任务触发器-trigger)
- [任务编辑](#任务编辑)


---

### 存储
  - mysql+sqlite+json

---

### 任务格式

```yaml
task1: # 任务ID
  name: # 任务名称，展示给玩家
  type: # 任务类型
  target: # 任务目标
  condition: # 允许做该任务的条件
    -
  trigger:
    on_task_start:
      - 
    on_task_finish:
      - 
    on_task_cancel:
      - 
    on_task_fail:
      - 
```

#### 任务类型-type：
  - 周期任务
  - 长期任务
  - 短期任务
  - 触发任务（拓展功能）

#### 任务目标-target

  - 合成
  - 挖掘
  - 垂钓
  - 放置
  - 消耗
  - 击杀
  - 附魔
  - 剪刀
  - 繁殖
  - 驯服
  - ...

#### 任务条件-condition
  - condition_id ___条件id，识别用___
  - type
    - permission
      - 仅在拥有权限时才能接、做和结算任务，无权限不计入任务进度
        - playertaskx.task1
    - item
      - 仅在背包中拥有指定物品时才能接、做和结算任务，无物品不计
        - name
        - material
        - enchantments

#### 任务触发器-trigger
  - triggers:
    - on_task_start
      - 任务开始/接受任务时触发
    - on_task_finish
      - 任务完成时触发
    - on_task_cancel
      - 任务取消时触发
    - on_task_fail
      - 任务失败时触发
  - commands:
    - MESSAGE
      - 发送一条消息给玩家
      - `MESSAGE 这是一条消息`
    - BC
      - 发送广播给全服
      - `BC 这是一条广播`
    - KILL
      - 杀死玩家，并发送一条死亡信息给玩家(可选)
      - `KILL 你因为任务失败而死亡`
    - MONEY
      - 修改玩家的货币数量，为正则增加，为负则减少
      - `MONEY 10`
    - POINT
      - 修改玩家的点券数量，为正则增加，为负则减少
      - `POINT 10`
    - EXP
      - 修改玩家的经验，为正则增加，为负则减少
      - `EXP 100`
    - LEVEL
      - 修改玩家的等级，为正则增加，为负则减少
      - `LEVEL 100`
    - COMMAND
      - 以玩家身份执行一条命令
      - `COMMAND say 1`
    - ADMIN
      - 以OP身份执行一条命令
      - `ADMIN say 1`
    - CONSOLE
      - 以控制台身份执行一条命令
      - `CONSOLE say 1`
    - POTION
      - 令玩家获得指定效果
      - `POTION <效果> [秒(默认30)] [等级(默认0)] [是否隐藏粒子效果(默认false)]`
    - ITEM
      - 令玩家获得指定物品
      - `ITEM <物品名> [数量(默认1)] [名称] [标签] [附魔] [特殊属性]`
  - 其他
    - 在trigger中可使用常用占位符
    - 特殊属性:
      - DAMAGE
        - 造成的伤害
        - 数据类型：double
        - `D:10` 造成10点伤害
      - HEALTH
        - 额外获得的生命上限
        - 数据类型：int
        - `H:10` 10点额外生命上限
      - ARMOR
        - 获得额外护甲值
        - 数据类型：int
        - `A:10` 10点额外护甲值
      - 说明：
        - 特殊属性自适应物品，如给剑手持才生效，普通方块不生效，盔甲穿上才生效
### 任务编辑

  - web or 桌面端软件（高级功能）

  