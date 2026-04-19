package com.playerPlugin.playerTaskX.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class ObjectiveTemplate {
    private final String id;
    private final String name;
    private final String description;
    private final String type;
    private final Map<String, Object> defaultConfig;

    @JsonCreator
    public ObjectiveTemplate(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("type") String type,
            @JsonProperty("defaultConfig") Map<String, Object> defaultConfig
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.defaultConfig = defaultConfig;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getDefaultConfig() {
        return defaultConfig;
    }
}
