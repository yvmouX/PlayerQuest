package com.playerPlugin.playerTaskX.PlayerTask.Task;

import com.playerPlugin.playerTaskX.PlayerTask.Enum.PTXTaskType;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.TaskTarget;

import java.util.List;

public class Task {
    private String id;
    private PTXTaskType type;
    private String name;
    private List<TaskTarget> targets;
    //public List<TaskCondition> conditions = null;
    private TaskTrigger trigger;

    public Task(String id, PTXTaskType type, String name, List<TaskTarget> targets, TaskTrigger trigger) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.targets = targets;
        this.trigger = trigger;
    }

    // Getter and Setter methods
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public PTXTaskType getType() { return type; }
    public void setType(PTXTaskType type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<TaskTarget> getTargets() { return targets; }
    public void setTargets(List<TaskTarget> targets) { this.targets = targets; }

    public TaskTrigger getTrigger() { return trigger; }
    public void setTrigger(TaskTrigger trigger) { this.trigger = trigger; }
}
