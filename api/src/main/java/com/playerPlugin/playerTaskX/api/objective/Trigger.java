package com.playerPlugin.playerTaskX.api.objective;

/**
 * 游戏内触发动作：目标匹配时用于快速判别，避免每个目标类型都遍历所有事件。
 * <p>
 * 一个 Bukkit 事件可能对应多个 Trigger（如 {@code PlayerInteractEvent} 可能是
 * 交互、放置或提交），由监听器负责区分。
 */
public enum Trigger {
    /** 破坏方块 */
    BREAK_BLOCK,
    /** 放置方块 */
    PLACE_BLOCK,
    /** 合成物品 */
    CRAFT,
    /** 钓鱼 */
    FISH,
    /** 击杀生物/玩家 */
    KILL,
    /** 消耗物品 */
    CONSUME,
    /** 附魔 */
    ENCHANT,
    /** 剪切（剪羊毛等） */
    SHEAR,
    /** 繁殖 */
    BREED,
    /** 驯服 */
    TAME,
    /** 交互（右键方块/实体） */
    INTERACT,
    /** 发言 */
    CHAT,
    /** 提交物品（GUI 中主动提交） */
    SUBMIT,
    /** 执行命令 */
    COMMAND
}
