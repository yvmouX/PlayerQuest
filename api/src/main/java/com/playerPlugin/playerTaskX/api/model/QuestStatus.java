package com.playerPlugin.playerTaskX.api.model;

/** 玩家任务的状态。 */
public enum QuestStatus {
    /** 进行中 */
    IN_PROGRESS,
    /** 全部目标已完成，等待领取奖励 */
    COMPLETED,
    /** 奖励已领取 */
    CLAIMED,
    /** 已放弃 */
    ABANDONED
}
