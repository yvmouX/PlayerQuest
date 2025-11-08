package com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget;

import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXActionType;
import com.playerPlugin.playerTaskX.PlayerTask.listener.TargetFinishListener;

public class TaskTarget {
    private int targetIndex;
    private PTXActionType action;
    private Requirement requirement;
    private int current;
    private boolean finished = false;

    private TargetFinishListener listener;

    public TaskTarget(PTXActionType action, int targetIndex, Requirement requirement) {
        this.action = action;
        this.targetIndex = targetIndex;
        this.requirement = requirement;
    }

    public boolean isFinished() {
        return finished;
    }
    public void setFinished(boolean finished) {
        if (!this.finished && finished) {
            if (listener != null) {
                listener.onFinish(this);
            }
        }
        this.finished = finished;
    }

    public void setListener(TargetFinishListener listener) {
        this.listener = listener;
    }

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
}
