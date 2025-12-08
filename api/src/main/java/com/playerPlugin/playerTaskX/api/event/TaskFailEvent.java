package com.playerPlugin.playerTaskX.api.event;

import java.util.UUID;

/**
 * 任务失败事件
 * 当玩家的任务失败时触发
 */
public class TaskFailEvent extends TaskEvent {
    private final String reason;

    public TaskFailEvent(UUID playerId, String taskId, String reason) {
        super(playerId, taskId);
        this.reason = reason;
    }

    /**
     * 获取失败原因
     * @return 失败原因
     */
    public String getReason() {
        return reason;
    }
}
