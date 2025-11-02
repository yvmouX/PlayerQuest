package com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget;

import java.util.Set;

public class TaskRequireList {
    private Set<String> requireList;

    public TaskRequireList(Set<String> requireList) {
        this.requireList = requireList;
    }

    public Set<String> getRequireList() {
        return requireList;
    }
    public void setRequireList(Set<String> requireList) {
        this.requireList = requireList;
    }
}
