package com.playerPlugin.playerTaskX.api.task;

/**
 * 任务条件接口
 * 定义任务的触发和完成条件
 */
public interface ITaskCondition {

    /**
     * 获取条件类型
     * @return 条件类型
     */
    String getType();

    /**
     * 获取条件目标
     * @return 目标描述
     */
    String getTarget();

    /**
     * 获取条件数量要求
     * @return 数量要求
     */
    int getAmount();
}
