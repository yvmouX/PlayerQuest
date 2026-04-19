package com.playerPlugin.playerTaskX.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NodeConnection {
    private final String id;
    private final String sourceId;
    private final String targetId;
    private final String label;

    @JsonCreator
    public NodeConnection(
            @JsonProperty("id") String id,
            @JsonProperty("sourceId") String sourceId,
            @JsonProperty("targetId") String targetId,
            @JsonProperty("label") String label
    ) {
        this.id = id;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.label = label;
    }

    public String getId() { return id; }
    public String getSourceId() { return sourceId; }
    public String getTargetId() { return targetId; }
    public String getLabel() { return label; }
}
