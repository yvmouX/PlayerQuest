package com.playerPlugin.playerTaskX.api.event;

import java.util.UUID;

/**
 * 任务开始事件
 * 当玩家开始一个任务时触发
 */
public class TaskStartEvent extends TaskEvent {
    
    public TaskStartEvent(UUID playerId, String taskId) {
        super(playerId, taskId);
    }
}
