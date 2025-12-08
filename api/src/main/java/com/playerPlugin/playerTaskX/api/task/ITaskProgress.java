package com.playerPlugin.playerTaskX.api.task;

import java.util.UUID;

/**
 * 任务进度接口
 * 记录玩家在特定任务中的进度
 */
public interface ITaskProgress {

    /**
     * 获取玩家 UUID
     * @return 玩家 UUID
     */
    UUID getPlayerId();

    /**
     * 获取任务 ID
     * @return 任务 ID
     */
    String getTaskId();

    /**
     * 获取当前进度
     * @return 当前进度
     */
    int getCurrentProgress();

    /**
     * 设置当前进度
     * @param progress 进度值
     */
    void setCurrentProgress(int progress);

    /**
     * 获取任务状态
     * @return 任务状态
     */
    TaskStatus getStatus();

    /**
     * 设置任务状态
     * @param status 任务状态
     */
    void setStatus(TaskStatus status);

    /**
     * 获取任务开始时间
     * @return 开始时间戳
     */
    long getStartTime();

    /**
     * 获取任务完成时间
     * @return 完成时间戳，未完成则返回 0
     */
    long getCompletionTime();

    /**
     * 检查是否已完成
     * @return 是否完成
     */
    boolean isCompleted();

    /**
     * 任务状态枚举
     */
    enum TaskStatus {
        /** 未开始 */
        NOT_STARTED,
        /** 进行中 */
        IN_PROGRESS,
        /** 已完成 */
        COMPLETED,
        /** 已失败 */
        FAILED,
        /** 已取消 */
        CANCELLED
    }
}
