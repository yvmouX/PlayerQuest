package com.playerPlugin.playerTaskX.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskType;

import java.util.List;

public class TaskDefinition {
    private final String id; // 唯一ID
    private final PTXTaskType type; // 不可变类型
    private final String name; // 名称
    private final String description;
    private final List<TaskObjective> objectives;

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskDefinition that = (TaskDefinition) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public PTXTaskType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<TaskObjective> getObjectives() {
        return objectives;
    }

    @JsonCreator
    public TaskDefinition(
            @JsonProperty("id") String id,
            @JsonProperty("type") PTXTaskType type,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("objectives") List<TaskObjective> objectives
    ) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.description = description;
        this.objectives = objectives;
    }
}
