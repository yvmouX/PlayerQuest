package com.playerPlugin.playerTaskX.PlayerTask;

import com.playerPlugin.playerTaskX.PlayerTask.Task.Task;

import java.util.UUID;

public class PlayerTask {
    private UUID uuid;
    private Task task;
    private int progress;
    private int targetCount;
    private PlayerTaskStatus status;

    public PlayerTask(UUID playerId, Task task) {
        this.uuid = playerId;
        this.task = task;
        this.progress = 0;
        this.targetCount = task.getTarget().getCount();
        this.status = PlayerTaskStatus.IN_PROGRESS;
    }

    // Getter and Setter
    public UUID getUUID() { return this.uuid; }
    public void setUUID(UUID uuid) { this.uuid = uuid; }

    public Task getTask() { return this.task; }
    public void setTask(Task task) { this.task = task; }

    public int getProgress() { return this.progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public int getTargetNumber() { return this.targetCount; }
    public void setTargetNumber(int targetNumber) { this.targetCount = targetNumber; }

    public PlayerTaskStatus getStatus() { return this.status; }
    public void setStatus(PlayerTaskStatus status) { this.status = status; }


    /**
     * 添加进度
     *
     * @param amount 量
     */
    public void addProgress(int amount) {
        this.progress += amount;
    }


    /**
     * 任务是否完成
     *
     * @return boolean
     */
    public boolean isComplete() {
        return progress >= targetCount;
    }
}