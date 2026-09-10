package com.playerPlugin.playerTaskX.api.model;

/**
 * 任务类型。
 */
public enum QuestType {
    /** 每日任务：按玩家从全局池抽取，跨天失效 */
    DAILY,
    /** 普通任务：常驻，可重复完成（受冷却限制） */
    NORMAL
}
