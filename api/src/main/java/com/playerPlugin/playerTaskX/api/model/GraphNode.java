package com.playerPlugin.playerTaskX.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class GraphNode {
    private final String id;
    private final String nodeType;
    private final double x;
    private final double y;
    private final Map<String, Object> data;

    @JsonCreator
    public GraphNode(
            @JsonProperty("id") String id,
            @JsonProperty("nodeType") String nodeType,
            @JsonProperty("x") double x,
            @JsonProperty("y") double y,
            @JsonProperty("data") Map<String, Object> data
    ) {
        this.id = id;
        this.nodeType = nodeType;
        this.x = x;
        this.y = y;
        this.data = data;
    }

    public String getId() { return id; }
    public String getNodeType() { return nodeType; }
    public double getX() { return x; }
    public double getY() { return y; }
    public Map<String, Object> getData() { return data; }
}
