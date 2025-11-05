package com.playerPlugin.playerTaskX.PlayerTask.Task;

import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXTaskStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerTask {
    private UUID uuid;
    private Task task;
    private PTXTaskStatus status;

    public PlayerTask(UUID playerId, Task task) {
        this.uuid = playerId;
        this.task = task;
        this.status = PTXTaskStatus.IN_PROGRESS;
    }

    // Getter and Setter
    public UUID getUUID() { return this.uuid; }
    public void setUUID(UUID uuid) { this.uuid = uuid; }

    public Task getTask() { return this.task; }
    public void setTask(Task task) { this.task = task; }

    public PTXTaskStatus getStatus() { return this.status; }
    public void setStatus(PTXTaskStatus status) { this.status = status; }


}