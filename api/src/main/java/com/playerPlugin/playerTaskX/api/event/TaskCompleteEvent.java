package com.playerPlugin.playerTaskX.api.event;

import java.util.UUID;

/**
 * 任务完成事件
 * 当玩家完成一个任务时触发
 */
public class TaskCompleteEvent extends TaskEvent {
    private final long completionTime;

    public TaskCompleteEvent(UUID playerId, String taskId, long completionTime) {
        super(playerId, taskId);
        this.completionTime = completionTime;
    }

    /**
     * 获取任务完成时间
     * @return 完成时间戳
     */
    public long getCompletionTime() {
        return completionTime;
    }
}
