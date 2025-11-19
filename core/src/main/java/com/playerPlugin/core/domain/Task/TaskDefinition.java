package com.playerPlugin.core.domain.Task;

import com.playerPlugin.core.domain.PlayerTask.Enum.PTXTaskType;

import java.util.List;
import java.util.Objects;

public class TaskDefinition {
    private final String id;
    private PTXTaskType type;
    private String name;
    private List<TaskTarget> targets;
    public List<TaskCondition> conditions; // TODO
    private TaskTrigger trigger;

    public TaskDefinition(String id, PTXTaskType type, String name, List<TaskTarget> targets, TaskTrigger trigger) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskDefinition that = (TaskDefinition) o;
        return Objects.equals(id, that.id) && type == that.type && Objects.equals(name, that.name) && Objects.equals(targets, that.targets) && Objects.equals(trigger, that.trigger);
    }
}
