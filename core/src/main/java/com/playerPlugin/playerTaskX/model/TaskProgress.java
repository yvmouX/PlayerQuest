package com.playerPlugin.playerTaskX.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskStatus;
import com.playerPlugin.playerTaskX.utils.TimeUtil;

import java.util.UUID;

public class TaskProgress {
    private final UUID uuid;
    private final TaskDefinition taskDefinition;
    private final PTXTaskStatus status;
    private final String createAt;
    private final String updateAt;

    @JsonCreator
    public TaskProgress(
            @JsonProperty("uuid") UUID playerId,
            @JsonProperty("taskDefinition") TaskDefinition taskDefinition,
            @JsonProperty("status") PTXTaskStatus status,
            @JsonProperty("createAt") String createAt,
            @JsonProperty("updateAt") String updateAt
    ) {
        this.uuid = playerId;
        this.taskDefinition = taskDefinition;
        this.status = PTXTaskStatus.IN_PROGRESS;
        this.createAt = TimeUtil.getTime();
        this.updateAt = TimeUtil.getTime();
    }

    public UUID getUuid() {
        return uuid;
    }

    public TaskDefinition getTaskDefinition() {
        return taskDefinition;
    }

    public PTXTaskStatus getStatus() {
        return status;
    }

    public String getCreateAt() {
        return createAt;
    }

    public String getUpdateAt() {
        return updateAt;
    }
}