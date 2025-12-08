package com.playerPlugin.playerTaskX.api.event;

import java.util.UUID;

/**
 * 任务进度更新事件
 * 当玩家的任务进度发生变化时触发
 */
public class TaskProgressEvent extends TaskEvent {
    private final int oldProgress;
    private final int newProgress;
    private final int targetProgress;

    public TaskProgressEvent(UUID playerId, String taskId, int oldProgress, int newProgress, int targetProgress) {
        super(playerId, taskId);
        this.oldProgress = oldProgress;
        this.newProgress = newProgress;
        this.targetProgress = targetProgress;
    }

    /**
     * 获取旧进度
     * @return 旧进度值
     */
    public int getOldProgress() {
        return oldProgress;
    }

    /**
     * 获取新进度
     * @return 新进度值
     */
    public int getNewProgress() {
        return newProgress;
    }

    /**
     * 获取目标进度
     * @return 目标进度值
     */
    public int getTargetProgress() {
        return targetProgress;
    }

    /**
     * 获取进度百分比
     * @return 进度百分比（0-100）
     */
    public double getProgressPercentage() {
        if (targetProgress <= 0) return 0;
        return (double) newProgress / targetProgress * 100;
    }
}
