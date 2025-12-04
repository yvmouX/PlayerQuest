package com.playerPlugin.playerTaskX.model.Task;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;

import java.util.Objects;
import java.util.UUID;

public class TaskProgress {
    private UUID uuid;
    private TaskDefinition taskDefinition;
    private PTXTaskStatus status;

    public TaskProgress(UUID playerId, TaskDefinition taskDefinition) {
        this.uuid = playerId;
        this.taskDefinition = taskDefinition;
        this.status = PTXTaskStatus.IN_PROGRESS;
    }

    // Getter and Setter
    public UUID getUUID() { return this.uuid; }
    public void setUUID(UUID uuid) { this.uuid = uuid; }

    public TaskDefinition getTask() { return this.taskDefinition; }
    public void setTask(TaskDefinition taskDefinition) { this.taskDefinition = taskDefinition; }

    public PTXTaskStatus getStatus() { return this.status; }
    public void setStatus(PTXTaskStatus status) { this.status = status; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskProgress that = (TaskProgress) o;
        return Objects.equals(uuid, that.uuid) && Objects.equals(taskDefinition, that.taskDefinition) && status == that.status;
    }
}