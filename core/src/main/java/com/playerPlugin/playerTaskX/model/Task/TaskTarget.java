package com.playerPlugin.playerTaskX.model.Task;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playerPlugin.playerTaskX.api.Enum.PTXActionType;

public class TaskTarget {
    private int targetIndex;
    private PTXActionType action;
    private Requirement requirement;
    private int current;
    private boolean finished = false;

    @JsonCreator
    public TaskTarget(
            @JsonProperty("action") PTXActionType action,
            @JsonProperty("targetIndex") int targetIndex,
            @JsonProperty("requirement") Requirement requirement) {
        this.action = action;
        this.targetIndex = targetIndex;
        this.requirement = requirement;
    }

    public boolean isFinished() {
        return finished;
    }
    public void setFinished(boolean finished) {}

    public int getCurrent() {
        return current;
    }
    public void setCurrent(int current) {
        this.current = current;
    }

    public int getIndex() {
        return targetIndex;
    }
    public void setIndex(int targetIndex) {
        this.targetIndex = targetIndex;
    }

    public PTXActionType getAction() {
        return action;
    }
    public void setAction(PTXActionType action) {
        this.action = action;
    }

    public Requirement getRequirement() {
        return requirement;
    }
    public void setRequirement(Requirement requirement) {
        this.requirement = requirement;
    }


    public boolean incrementCurrent(int amount) {
        if (current >= requirement.getAmount()) {
            finished = true;
            return true;
        }
        current += amount;
        return false;
    }
}
