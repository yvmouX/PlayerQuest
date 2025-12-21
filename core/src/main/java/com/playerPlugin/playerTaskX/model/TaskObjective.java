package com.playerPlugin.playerTaskX.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playerPlugin.playerTaskX.api.Enum.PTXActionType;

public class TaskObjective {
    private final PTXActionType action;
    private final String target;
    private final boolean finished;
    private final int currentAmount;
    private final int targetAmount;
    private final String createAt;
    private final String updateAt;

    public PTXActionType getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public boolean isFinished() {
        return finished;
    }

    public int getCurrentAmount() {
        return currentAmount;
    }

    public int getTargetAmount() {
        return targetAmount;
    }

    public String getCreateAt() {
        return createAt;
    }

    public String getUpdateAt() {
        return updateAt;
    }

    @JsonCreator
    public TaskObjective(
            @JsonProperty("action") PTXActionType action,
            @JsonProperty("target") String target,
            @JsonProperty("finished") boolean finished,
            @JsonProperty("currentAmount") int currentAmount,
            @JsonProperty("targetAmount") int targetAmount,
            @JsonProperty("createAt") String createAt,
            @JsonProperty("updateAt") String updateAt
    ) {
        this.action = action;
        this.target = target;
        this.finished = finished;
        this.currentAmount = currentAmount;
        this.targetAmount = targetAmount;
        this.createAt = createAt;
        this.updateAt = updateAt;
    }
}
