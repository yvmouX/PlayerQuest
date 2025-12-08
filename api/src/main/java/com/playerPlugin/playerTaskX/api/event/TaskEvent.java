package com.playerPlugin.playerTaskX.api.event;

import java.util.UUID;

/**
 * 任务事件基类
 * 所有任务相关事件的父类
 */
public abstract class TaskEvent {
    private final UUID playerId;
    private final String taskId;
    private final long timestamp;

    public TaskEvent(UUID playerId, String taskId) {
        this.playerId = playerId;
        this.taskId = taskId;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 获取玩家 UUID
     * @return 玩家 UUID
     */
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * 获取任务 ID
     * @return 任务 ID
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * 获取事件时间戳
     * @return 时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }
}
