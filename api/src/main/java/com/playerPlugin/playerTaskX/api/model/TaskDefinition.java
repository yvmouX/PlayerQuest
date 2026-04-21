package com.playerPlugin.playerTaskX.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskDefinition {
    private final String id;
    private final String name;
    private final String description;
    private final String category;
    private final QuestGraph graph;

    @JsonCreator
    public TaskDefinition(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("category") String category,
            @JsonProperty("graph") QuestGraph graph
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.graph = graph;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
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
