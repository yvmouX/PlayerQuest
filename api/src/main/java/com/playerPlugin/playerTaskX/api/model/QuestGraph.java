package com.playerPlugin.playerTaskX.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class QuestGraph {
    private final String id;
    private final String name;
    private final List<GraphNode> nodes;
    private final List<NodeConnection> edges;

    @JsonCreator
    public QuestGraph(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("nodes") List<GraphNode> nodes,
            @JsonProperty("edges") List<NodeConnection> edges
    ) {
        this.id = id;
        this.name = name;
        this.nodes = nodes;
        this.edges = edges;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<GraphNode> getNodes() { return nodes; }
    public List<NodeConnection> getEdges() { return edges; }
}
