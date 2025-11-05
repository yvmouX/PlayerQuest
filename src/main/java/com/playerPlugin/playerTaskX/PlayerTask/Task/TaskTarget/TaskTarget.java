package com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget;

import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXActionType;

import java.util.List;

public class TaskTarget {
    private PTXActionType action;
    private List<Requirement> requires;

    public TaskTarget(PTXActionType action, List<Requirement> requires) {
        this.action = action;
        this.requires = requires;
    }

    public PTXActionType getAction() {
        return action;
    }
    public void setAction(PTXActionType action) {
        this.action = action;
    }

    public List<Requirement> getRequires() {
        return requires;
    }
    public void setRequires(List<Requirement> requires) {
        this.requires = requires;
    }
}
