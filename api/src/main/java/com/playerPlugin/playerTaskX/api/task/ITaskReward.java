package com.playerPlugin.playerTaskX.api.task;

/**
 * 任务奖励接口
 * 定义任务完成后的奖励
 */
public interface ITaskReward {

    /**
     * 获取奖励类型
     * @return 奖励类型（例如：MONEY, ITEM, COMMAND 等）
     */
    String getType();

    /**
     * 获取奖励值
     * @return 奖励值（例如：金币数量、物品 ID 等）
     */
    String getValue();

    /**
     * 获取奖励数量
     * @return 奖励数量
     */
    int getAmount();
}
