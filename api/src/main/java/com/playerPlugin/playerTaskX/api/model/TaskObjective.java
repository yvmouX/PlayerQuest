package com.playerPlugin.playerTaskX.api.model;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playerPlugin.playerTaskX.api.Enum.PTXActionType;

public class TaskObjective {
    private final PTXActionType action;
    private final String target;
    private final boolean finished;
    private final int currentAmount;
    private final int targetAmount;

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

    @JsonCreator
    public TaskObjective(
            @JsonProperty("action") PTXActionType action,
            @JsonProperty("target") String target,
            @JsonProperty("finished") boolean finished,
            @JsonProperty("currentAmount") int currentAmount,
            @JsonProperty("targetAmount") int targetAmount
    ) {
        this.action = action;
        this.target = target;
        this.finished = finished;
        this.currentAmount = currentAmount;
        this.targetAmount = targetAmount;
    }
}
