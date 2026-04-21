package com.playerPlugin.playerTaskX.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playerPlugin.playerTaskX.api.Enum.PTXTaskType;
import com.playerPlugin.playerTaskX.api.model.condition.Condition;
import com.playerPlugin.playerTaskX.api.model.objective.Objective;

import java.util.ArrayList;
import java.util.List;

public class TaskDefinition {
    private final String id;
    private final String name;
    private final String description;
    private final String category;
    private final PTXTaskType taskType;
    private final List<Objective> objectives;
    private final List<Condition> conditions;
    private final QuestGraph graph;

    @JsonCreator
    public TaskDefinition(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("category") String category,
            @JsonProperty("type") PTXTaskType taskType,
            @JsonProperty("objectives") List<Objective> objectives,
            @JsonProperty("conditions") List<Condition> conditions,
            @JsonProperty("graph") QuestGraph graph
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.taskType = taskType != null ? taskType : PTXTaskType.FOREVER;
        this.objectives = objectives != null ? new ArrayList<>(objectives) : new ArrayList<>();
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
        this.graph = graph;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public PTXTaskType getTaskType() { return taskType; }
    public List<Objective> getObjectives() { return objectives; }
    public List<Condition> getConditions() { return conditions; }
    public QuestGraph getGraph() { return graph; }
    public boolean hasGraph() { return graph != null && !graph.getNodes().isEmpty(); }

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
}
