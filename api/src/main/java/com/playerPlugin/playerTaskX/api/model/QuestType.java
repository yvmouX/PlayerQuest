package com.playerPlugin.playerTaskX.api.model;

/**
 * 任务类型：{@link #NORMAL} 之外的四种都是周期任务，各自在配置里有独立一段（数量 / 重置锚点 / 费用 / 上限）。
 */
public enum QuestType {
    /** 每日任务：按玩家从全局池抽取，跨天失效 */
    DAILY,
    /** 每周任务：到配置的星期几（含重置小时）换一批 */
    WEEKLY,
    /** 每月任务：到配置的日期（含重置小时）换一批 */
    MONTHLY,
    /** 自定义周期任务：周期长度写在配置里（如 {@code 3d} / {@code 12h}），按固定锚点取整 */
    CUSTOM,
    /** 普通任务：常驻，可重复完成（受冷却限制） */
    NORMAL;

    /** 是不是周期任务（会被抽取、会过期）。 */
    public boolean isPeriodic() {
        return this != NORMAL;
    }
}
