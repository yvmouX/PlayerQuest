package com.playerPlugin.playerTaskX.api.model;

import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TaskProgress {
    private final UUID playerId;
    private final String taskId;
    private PTXTaskStatus status;
    private final long acceptedAt;
    private long completedAt;
    private long claimedAt;
    private final Map<String, Integer> objectiveProgress;

    public TaskProgress(UUID playerId, String taskId) {
        this.playerId = playerId;
        this.taskId = taskId;
        this.status = PTXTaskStatus.IN_PROGRESS;
        this.acceptedAt = System.currentTimeMillis();
        this.completedAt = 0;
        this.claimedAt = 0;
        this.objectiveProgress = new ConcurrentHashMap<>();
    }

    public UUID getPlayerId() { return playerId; }
    public String getTaskId() { return taskId; }
    public PTXTaskStatus getStatus() { return status; }
    public void setStatus(PTXTaskStatus status) { this.status = status; }
    public long getAcceptedAt() { return acceptedAt; }
    public long getCompletedAt() { return completedAt; }
    public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
    public long getClaimedAt() { return claimedAt; }
    public void setClaimedAt(long claimedAt) { this.claimedAt = claimedAt; }
    public Map<String, Integer> getObjectiveProgress() { return objectiveProgress; }

    public int getProgress(String objectiveId) {
        return objectiveProgress.getOrDefault(objectiveId, 0);
    }

    public void setProgress(String objectiveId, int amount) {
        objectiveProgress.put(objectiveId, amount);
    }

    public void incrementProgress(String objectiveId, int delta) {
        objectiveProgress.merge(objectiveId, delta, Integer::sum);
    }

    public boolean isCompleted() {
        return status == PTXTaskStatus.COMPLETED;
    }

    public boolean isClaimed() {
        return status == PTXTaskStatus.CLAIMED;
    }
}
