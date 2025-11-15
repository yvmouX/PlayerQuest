package com.playerPlugin.playerTaskX.domain.PlayerTask.Task;

import com.playerPlugin.playerTaskX.domain.PlayerTask.Enum.PTXActionType;

public class TaskTarget {
    private int targetIndex;
    private PTXActionType action;
    private Requirement requirement;
    private int current;
    private boolean finished = false;

    public TaskTarget(PTXActionType action, int targetIndex, Requirement requirement) {
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
