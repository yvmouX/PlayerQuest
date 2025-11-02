package com.playerPlugin.playerTaskX.PlayerTask.Task;

import com.playerPlugin.playerTaskX.PlayerTask.Enum.TaskTypes;
import com.playerPlugin.playerTaskX.PlayerTask.Task.TaskTarget.TaskTarget;

public class Task {
    private String id;
    private TaskTypes type;
    private String name;
    private TaskTarget target;
    public String condition;
    private TaskTrigger trigger;

    public Task(String taskId, String name, TaskTypes taskType, TaskTarget taskTarget, String taskCondition, TaskTrigger taskTrigger) {
        this.id = taskId;
        this.name = name;
        this.type = taskType;
        this.target = taskTarget;
        this.condition = taskCondition;
        this.trigger = taskTrigger;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public TaskTypes getType() { return type; }
    public void setType(TaskTypes type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public TaskTarget getTarget() { return target; }
    public void setTarget(TaskTarget target) { this.target = target; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public TaskTrigger getTrigger() { return trigger; }
    public void setTrigger(TaskTrigger trigger) { this.trigger = trigger; }
}
