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
    private final String createAt;
    private final String updateAt;

    public String getId() {
        return id;
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

    public String getCreateAt() {
        return createAt;
    }

    public String getUpdateAt() {
        return updateAt;
    }

    @JsonCreator
    public TaskDefinition(
            @JsonProperty("id") String id,
            @JsonProperty("type") PTXTaskType type,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("objectives") List<TaskObjective> objectives,
            @JsonProperty("createAt") String createAt,
            @JsonProperty("updateAt") String updateAt
    ) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.description = description;
        this.objectives = objectives;
        this.createAt = createAt;
        this.updateAt = updateAt;
    }
}
