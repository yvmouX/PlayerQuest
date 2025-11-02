package com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget;

import com.playerPlugin.playerTaskX.PlayerTask.Enum.TaskActions;

import java.util.Map;
import java.util.Set;

public class TaskTarget {
    private Set<String> target_id;
    private Map<String, TaskActions> action;
    private Map<String, TaskRequireList> require;

    // Getters and Setters
    public Set<String> getTarget_id() { return target_id; }
    public void setTarget_id(Set<String> target_id) { this.target_id = target_id; }

    public Map<String, TaskActions> getAction() { return action; }
    public void setAction(Map<String, TaskActions> action) { this.action = action; }

    public Map<String, TaskRequireList> getRequire() { return require; }
    public void setRequire(Map<String, TaskRequireList> require) { this.require = require; }
}
