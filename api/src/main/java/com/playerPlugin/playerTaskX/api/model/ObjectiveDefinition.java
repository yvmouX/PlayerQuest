package com.playerPlugin.playerTaskX.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playerPlugin.playerTaskX.api.Enum.PTXActionType;

public class ObjectiveDefinition {
    private final String id;
    private final PTXActionType action;
    private final String target;
    private final int targetAmount;

    @JsonCreator
    public ObjectiveDefinition(
            @JsonProperty("id") String id,
            @JsonProperty("action") PTXActionType action,
            @JsonProperty("target") String target,
            @JsonProperty("targetAmount") int targetAmount
    ) {
        this.id = id;
        this.action = action;
        this.target = target;
        this.targetAmount = targetAmount;
    }

    public String getId() {
        return id;
    }

    public PTXActionType getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public int getTargetAmount() {
        return targetAmount;
    }

}