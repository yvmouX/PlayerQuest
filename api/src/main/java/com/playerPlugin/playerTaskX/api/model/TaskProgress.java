package com.playerPlugin.playerTaskX.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.api.utils.TimeUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TaskProgress {
    private final UUID uuid;
    private final String taskId;
    private PTXTaskStatus status;
    private final long createAt;
    private long updateAt;
    
    // Key: objective ID, Value: current amount
    private final Map<String, Integer> objectiveProgress;

    @JsonCreator
    public TaskProgress(
            @JsonProperty("uuid") UUID uuid,
            @JsonProperty("taskId") String taskId,
            @JsonProperty("status") PTXTaskStatus status,
            @JsonProperty("createAt") Long createAt,
            @JsonProperty("updateAt") Long updateAt,
            @JsonProperty("objectiveProgress") Map<String, Integer> objectiveProgress
    ) {
        this.uuid = uuid;
        this.taskId = taskId;
        this.status = status != null ? status : PTXTaskStatus.IN_PROGRESS;
        
        long currentTime = System.currentTimeMillis();
        this.createAt = createAt != null ? createAt : currentTime;
        this.updateAt = updateAt != null ? updateAt : currentTime;
        
        this.objectiveProgress = objectiveProgress != null ? new ConcurrentHashMap<>(objectiveProgress) : new ConcurrentHashMap<>();
    }
    
    // 快捷构造新进度
    public TaskProgress(UUID uuid, String taskId) {
        this(uuid, taskId, PTXTaskStatus.IN_PROGRESS, null, null, null);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getTaskId() {
        return taskId;
    }

    public PTXTaskStatus getStatus() {
        return status;
    }

    public void setStatus(PTXTaskStatus status) {
        this.status = status;
        this.updateAt = System.currentTimeMillis();
    }

    public long getCreateAt() {
        return createAt;
    }

    public long getUpdateAt() {
        return updateAt;
    }

    public Map<String, Integer> getObjectiveProgress() {
        return objectiveProgress;
    }
    
    public int getObjectiveAmount(String objectiveId) {
        return objectiveProgress.getOrDefault(objectiveId, 0);
    }
    
    public void setObjectiveAmount(String objectiveId, int amount) {
        objectiveProgress.put(objectiveId, amount);
        this.updateAt = System.currentTimeMillis();
    }
    
    public void incrementObjectiveAmount(String objectiveId, int amount) {
        objectiveProgress.merge(objectiveId, amount, Integer::sum);
        this.updateAt = System.currentTimeMillis();
    }
}