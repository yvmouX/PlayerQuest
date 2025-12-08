package com.playerPlugin.playerTaskX.api.task;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskType;

import java.util.List;

/**
 * 任务定义接口
 * 定义了任务的基本属性和行为
 */
public interface ITaskDefinition {

    /**
     * 获取任务 ID
     * @return 任务 ID
     */
    String getId();

    /**
     * 获取任务类型
     * @return 任务类型
     */
    PTXTaskType getType();

    /**
     * 获取任务名称
     * @return 任务名称
     */
    String getName();

    /**
     * 设置任务名称
     * @param name 任务名称
     */
    void setName(String name);

    /**
     * 获取任务描述
     * @return 任务描述
     */
    String getDescription();

    /**
     * 设置任务描述
     * @param description 描述
     */
    void setDescription(String description);

//    /**
//     * 获取任务目标数量
//     * @return 目标数量
//     */
//    int getTargetAmount();
//
//    /**
//     * 获取任务条件列表
//     * @return 条件列表
//     */
//    List<ITaskCondition> getConditions();
//
//    /**
//     * 获取任务奖励列表
//     * @return 奖励列表
//     */
//    List<ITaskReward> getRewards();
//
//    /**
//     * 是否可重复完成
//     * @return 是否可重复
//     */
//    boolean isRepeatable();
//
//    /**
//     * 获取任务优先级
//     * @return 优先级
//     */
//    int getPriority();
}
